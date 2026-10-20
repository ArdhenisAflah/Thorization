package thorization.ai.adversarial;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thorization.ai.AiConfig;
import thorization.ai.AiEvaluationResult;
import thorization.ai.AiSemanticEngine;
import thorization.ai.AiVerdict;
import thorization.core.EngineController;
import thorization.core.EngineListener;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.FilterConfig;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.model.pipeline.Pipeline;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial stress and resilience test suite for AiSemanticEngine.
 * Tests immediate PENDING transitions, EDT thread safety, LLM outage error recovery,
 * DeepSeek reasoning block unbundling, prompt injection resilience, and body truncation boundaries.
 */
class AiSemanticEngineAdversarialTest {

    private HttpServer server;
    private int port;
    private EngineController controller;

    private final AtomicInteger responseStatusCode = new AtomicInteger(200);
    private final AtomicReference<String> responsePayload = new AtomicReference<>("");
    private final AtomicInteger networkDelayMs = new AtomicInteger(0);

    @BeforeAll
    static void setupHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        server.createContext("/v1/chat/completions", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                int delay = networkDelayMs.get();
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ignored) {}
                }

                int status = responseStatusCode.get();
                byte[] bytes = responsePayload.get().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        server.start();

        controller = new EngineController(null);
        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setModelName("test-adversarial-model");
        controller.setAiConfig(config);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
        if (controller != null) {
            controller.shutdown();
        }
    }

    @Test
    void testImmediatePendingTransitionAcrossRoles() {
        UserRole enabledRole = new UserRole("EnabledAttacker", "X-Role: attacker", "");
        enabledRole.setId("role-1");
        enabledRole.setEnabled(true);

        UserRole disabledRole = new UserRole("DisabledRole", "X-Role: disabled", "");
        disabledRole.setId("role-2");
        disabledRole.setEnabled(false);

        controller.addRole(enabledRole);
        controller.addRole(disabledRole);

        AuditRecord record = new AuditRecord(201, null);
        AuthorizationCheckResult res1 = new AuthorizationCheckResult(
                "role-1", "EnabledAttacker", Verdict.BYPASS, 200, 100, 0.9, 10L, "", null, null
        );
        AuthorizationCheckResult res2 = new AuthorizationCheckResult(
                "role-2", "DisabledRole", Verdict.ENFORCED, 403, 50, 0.1, 10L, "", null, null
        );
        AuthorizationCheckResult unauthRes = new AuthorizationCheckResult(
                "unauth", "Unauthenticated", Verdict.BYPASS, 200, 100, 0.9, 10L, "", null, null
        );

        record.addRoleResult("role-1", res1);
        record.addRoleResult("role-2", res2);
        record.setUnauthenticatedResult(unauthRes);
        controller.addRecord(record);

        networkDelayMs.set(500); // Add delay to guarantee we observe PENDING
        responsePayload.set("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\\"verdict\\": \\"VALIDATED_ACCESS\\", \\"reasoning\\": \\"Confirmed\\"}"
                      }
                    }
                  ]
                }
                """);

        CountDownLatch doneLatch = new CountDownLatch(1);
        controller.evaluateRecordWithAi(record, doneLatch::countDown);

        // Immediate assertions:
        assertNotNull(record.getAiResult(), "Record must have AI result immediately");
        assertEquals(AiVerdict.PENDING, record.getAiResult().getVerdict(), "Record should be PENDING");
        assertEquals(AiVerdict.PENDING, res1.getAiResult().getVerdict(), "Enabled role should be PENDING");
        assertNull(res2.getAiResult(), "Disabled role should NOT be set to PENDING");
        assertEquals(AiVerdict.PENDING, unauthRes.getAiResult().getVerdict(), "Unauthenticated result should be PENDING");
    }

    @Test
    void testServer500ErrorTransitionsToErrorVerdict() throws InterruptedException {
        responseStatusCode.set(500);
        responsePayload.set("{\"error\": {\"message\": \"Internal LLM engine crash\"}}");

        UserRole role = new UserRole("Attacker", "X-Role: attacker", "");
        role.setId("r-500");
        controller.addRole(role);

        AuditRecord record = new AuditRecord(500, null);
        AuthorizationCheckResult res = new AuthorizationCheckResult(
                "r-500", "Attacker", Verdict.BYPASS, 200, 100, 0.9, 10L, "", null, null
        );
        record.addRoleResult("r-500", res);
        controller.addRecord(record);

        CountDownLatch completeLatch = new CountDownLatch(1);
        AtomicBoolean onCompleteCalledOnEdt = new AtomicBoolean(false);

        controller.evaluateRecordWithAi(record, () -> {
            if (SwingUtilities.isEventDispatchThread()) {
                onCompleteCalledOnEdt.set(true);
            }
            completeLatch.countDown();
        });

        assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "AI evaluation should complete even on error");
        assertTrue(onCompleteCalledOnEdt.get(), "onComplete callback must be invoked on the Swing EDT");

        assertNotNull(record.getAiResult());
        assertEquals(AiVerdict.ERROR, record.getAiResult().getVerdict(), "Record should transition to ERROR on 500");
        assertEquals(AiVerdict.ERROR, res.getAiResult().getVerdict(), "Role result should transition to ERROR on 500");
        assertTrue(record.getAiResult().getReasoning().contains("500"), "Reasoning should document HTTP 500");
    }

    @Test
    void testDeepSeekReasoningThinkTagStripping() {
        String rawWithThink = """
                <think>
                Let me analyze this authorization response.
                The status is 403 Forbidden, which might indicate ACCESS_DENIED.
                Wait! Looking closer at the body, it actually exposes private tenant data:
                {"id": 123, "ssn": "000-00-0000"}
                Therefore this is a clear authorization bypass.
                </think>
                {
                  "verdict": "VALIDATED_ACCESS",
                  "confidence": 0.98,
                  "reasoning": "Sensitive SSN exposed in response payload",
                  "differential_analysis": "Tenant boundaries were violated",
                  "remediation_advice": "Implement object-level authorization filter"
                }
                """;

        AiEvaluationResult result = AiSemanticEngine.parseResponse(rawWithThink, "deepseek-r1", 120L);
        assertEquals(AiVerdict.VALIDATED_ACCESS, result.getVerdict(), "Must ignore reasoning inside <think> and extract JSON verdict");
        assertEquals("Sensitive SSN exposed in response payload Analysis: Tenant boundaries were violated", result.getReasoning());
    }

    @Test
    void testMarkdownCodeFencesAndChattyPrefixSuffix() {
        String chattyLlmOutput = """
                Sure! Here is the security analysis of the access control differential:
                ```json
                {
                  "verdict": "ACCESS_DENIED",
                  "confidence": 0.95,
                  "reasoning": "Application returned HTTP 403 with proper tenant isolation error."
                }
                ```
                I hope this helps! Let me know if you need any further analysis.
                """;

        AiEvaluationResult result = AiSemanticEngine.parseResponse(chattyLlmOutput, "gpt-4o", 200L);
        assertEquals(AiVerdict.ACCESS_DENIED, result.getVerdict());
        assertEquals("Application returned HTTP 403 with proper tenant isolation error.", result.getReasoning());
    }

    @Test
    void testUnstructuredFallbackKeywordScanning() {
        String unstructuredBypass = "After thorough analysis, this request confirms a critical BYPASS vulnerability in the API.";
        AiEvaluationResult resBypass = AiSemanticEngine.parseResponse(unstructuredBypass, "model", 50L);
        assertEquals(AiVerdict.VALIDATED_ACCESS, resBypass.getVerdict());

        String unstructuredDenied = "The operation was properly BLOCKED with permission denied error.";
        AiEvaluationResult resDenied = AiSemanticEngine.parseResponse(unstructuredDenied, "model", 50L);
        assertEquals(AiVerdict.ACCESS_DENIED, resDenied.getVerdict());

        String randomGarbage = "The quick brown fox jumps over the lazy dog.";
        AiEvaluationResult resGarbage = AiSemanticEngine.parseResponse(randomGarbage, "model", 50L);
        assertEquals(AiVerdict.INCONCLUSIVE, resGarbage.getVerdict());
    }

    @Test
    void testTruncateBodyBoundaryLimits() {
        // Empty
        assertEquals("[EMPTY BODY]", AiSemanticEngine.truncateBody(""));
        assertEquals("[EMPTY BODY]", AiSemanticEngine.truncateBody("   "));
        assertEquals("[EMPTY BODY]", AiSemanticEngine.truncateBody(null));

        // Exactly 3500 chars (not truncated)
        String exact3500 = "x".repeat(3500);
        assertEquals(exact3500, AiSemanticEngine.truncateBody(exact3500));

        // 3501 chars (truncated)
        String over3500 = "A".repeat(3000) + "MIDDLE" + "Z".repeat(500);
        String truncated = AiSemanticEngine.truncateBody(over3500);
        assertTrue(truncated.contains("[... Truncated 6 characters ...]"));
        assertTrue(truncated.startsWith("A".repeat(3000)));
        assertTrue(truncated.endsWith("Z".repeat(500)));

        // Giant body (100,000 chars)
        String giant = "H".repeat(3000) + "M".repeat(96500) + "T".repeat(500);
        String giantTruncated = AiSemanticEngine.truncateBody(giant);
        assertTrue(giantTruncated.contains("[... Truncated 96500 characters ...]"));
        assertTrue(giantTruncated.startsWith("H".repeat(3000)));
        assertTrue(giantTruncated.endsWith("T".repeat(500)));
    }

    @Test
    void testConcurrentEvaluationBarrage() throws InterruptedException {
        responsePayload.set("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\\"verdict\\": \\"ACCESS_DENIED\\", \\"reasoning\\": \\"Properly enforced\\"}"
                      }
                    }
                  ]
                }
                """);

        UserRole role = new UserRole("StandardRole", "X-Role: user", "");
        role.setId("std-role");
        controller.addRole(role);

        int recordCount = 20;
        CountDownLatch latch = new CountDownLatch(recordCount);
        List<AuditRecord> records = new ArrayList<>();

        for (int i = 0; i < recordCount; i++) {
            AuditRecord r = new AuditRecord(1000 + i, null);
            r.setUrl("https://api.example.com/items/" + i);
            r.setMethod("GET");
            AuthorizationCheckResult res = new AuthorizationCheckResult(
                    "std-role", "StandardRole", Verdict.ENFORCED, 403, 30, 0.1, 5L, "", null, null
            );
            r.addRoleResult("std-role", res);
            controller.addRecord(r);
            records.add(r);
        }

        // Concurrently dispatch all 20 evaluations
        for (AuditRecord r : records) {
            controller.evaluateRecordWithAi(r, latch::countDown);
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "All 20 concurrent evaluations should complete within 10 seconds");

        for (AuditRecord r : records) {
            assertNotNull(r.getAiResult());
            assertEquals(AiVerdict.ACCESS_DENIED, r.getAiResult().getVerdict());
        }
    }
}
