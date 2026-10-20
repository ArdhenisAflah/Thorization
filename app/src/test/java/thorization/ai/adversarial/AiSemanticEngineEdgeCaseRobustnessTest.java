package thorization.ai.adversarial;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import thorization.ai.AiConfig;
import thorization.ai.AiEvaluationResult;
import thorization.ai.AiSemanticEngine;
import thorization.ai.AiVerdict;
import thorization.ai.OpenAiClient;
import thorization.core.EngineController;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial edge-case and robustness verification suite for Milestone 1:
 * In-GUI AI Semantic Analysis Engine.
 *
 * Specific Focus Areas:
 * 1. Malformed JSON, DeepSeek <think> reasoning blocks, markdown fences, and unstructured fallbacks in parseResponse.
 * 2. Extreme network conditions: 401 Unauthorized, 429 Rate Limit, 500 Server Error, slow connection timeouts, TCP drops.
 * 3. Huge payload truncation: 100KB+ bodies, memory exhaustion prevention, boundary precision, and context overflow guards.
 */
class AiSemanticEngineEdgeCaseRobustnessTest {

    private HttpServer server;
    private int port;
    private OpenAiClient client;
    private final AtomicInteger responseStatusCode = new AtomicInteger(200);
    private final AtomicReference<String> responsePayload = new AtomicReference<>("");
    private final AtomicInteger sleepDelayMs = new AtomicInteger(0);

    @BeforeAll
    static void setupHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        HttpHandler handler = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                int delay = sleepDelayMs.get();
                if (delay > 0) {
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ignored) {}
                }

                byte[] body = responsePayload.get().getBytes(StandardCharsets.UTF_8);
                int status = responseStatusCode.get();
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        };

        server.createContext("/v1/chat/completions", handler);
        server.createContext("/chat/completions", handler);
        server.start();

        client = new OpenAiClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    // =========================================================================
    // SECTION 1: Malformed JSON, DeepSeek <think>, Markdown Fences, Fallbacks
    // =========================================================================

    @Test
    @DisplayName("DeepSeek multi-think blocks and embedded JSON braces within thoughts")
    void testDeepSeekMultiThinkBlocksAndInternalJson() {
        String output = """
                <think>
                Step 1: Check baseline. Baseline contains { "user": "alice", "role": "admin" }.
                Step 2: Check replay. Replay returns { "user": "alice" } with 200 OK.
                </think>
                Some intermediate chain-of-thought text.
                <think>
                Step 3: Confirm BOLA. Since status is 200 and role is unprivileged, access was granted.
                </think>
                ```json
                {
                  "verdict": "VALIDATED_ACCESS",
                  "confidence": 0.97,
                  "reasoning": "Unprivileged user accessed admin resource.",
                  "differential_analysis": "Replayed response matched baseline.",
                  "remediation_advice": "Check tenant ownership in controller."
                }
                ```
                """;

        AiEvaluationResult result = AiSemanticEngine.parseResponse(output, "deepseek-r1", 350L);

        assertNotNull(result);
        assertEquals(AiVerdict.VALIDATED_ACCESS, result.getVerdict());
        assertTrue(result.getReasoning().contains("Unprivileged user accessed admin resource"));
        assertTrue(result.getReasoning().contains("Analysis: Replayed response matched baseline"));
        assertFalse(result.getReasoning().contains("<think>"));
        assertFalse(result.getReasoning().contains("Step 1: Check baseline"));
    }

    @Test
    @DisplayName("DeepSeek unclosed <think> tag gracefully handles thought stripping")
    void testDeepSeekUnclosedThinkTagFallback() {
        String output = "<think>Model began thinking and was interrupted or never closed tag...\n"
                + "Verdict is VALIDATED_ACCESS because authorization was bypassed.";

        AiEvaluationResult result = AiSemanticEngine.parseResponse(output, "deepseek-r1", 200L);

        assertNotNull(result);
        assertEquals(AiVerdict.VALIDATED_ACCESS, result.getVerdict());
        assertTrue(result.getReasoning().contains("Parsed from text"));
    }

    @Test
    @DisplayName("Multiple markdown code blocks with bash curl example before JSON")
    void testNestedAndMultipleMarkdownFences() {
        String output = """
                Here is the reproduction command:
                ```bash
                curl -i -H "X-Role: attacker" http://example.com/api/admin
                ```
                And here is the security evaluation:
                ```json
                {
                  "verdict": "ACCESS_DENIED",
                  "confidence": 0.99,
                  "reasoning": "Endpoint rejected attacker with 403 Forbidden.",
                  "differential_analysis": "403 vs 200 baseline.",
                  "remediation_advice": "No remediation needed."
                }
                ```
                Hope this helps!
                """;

        AiEvaluationResult result = AiSemanticEngine.parseResponse(output, "gpt-4o", 450L);

        assertNotNull(result);
        assertEquals(AiVerdict.ACCESS_DENIED, result.getVerdict());
        assertTrue(result.getReasoning().contains("Endpoint rejected attacker with 403 Forbidden"));
    }

    @Test
    @DisplayName("Malformed JSON syntax (truncated, unquoted keys, trailing commas) falls back to text scanner")
    void testMalformedJsonSyntaxRecoversToFallbackText() {
        // Case A: Truncated JSON
        String truncated = "{\"verdict\": \"VALIDATED_ACCESS\", \"reasoning\": \"Bypass found";
        AiEvaluationResult resA = AiSemanticEngine.parseResponse(truncated, "model-a", 100L);
        assertNotNull(resA);
        assertEquals(AiVerdict.VALIDATED_ACCESS, resA.getVerdict());
        assertTrue(resA.getReasoning().contains("Parsed from text"));

        // Case B: Unquoted keys (Gson leniently parses keys and extracts fields)
        String unquoted = "{verdict: \"ACCESS_DENIED\", reasoning: \"Access is blocked\"}";
        AiEvaluationResult resB = AiSemanticEngine.parseResponse(unquoted, "model-b", 120L);
        assertNotNull(resB);
        assertEquals(AiVerdict.ACCESS_DENIED, resB.getVerdict());
        assertTrue(resB.getReasoning().contains("Access is blocked"));

        // Case C: Trailing comma in JSON
        String trailingComma = "{\"verdict\": \"INCONCLUSIVE\", \"reasoning\": \"Ambiguous\",}";
        AiEvaluationResult resC = AiSemanticEngine.parseResponse(trailingComma, "model-c", 110L);
        assertNotNull(resC);
        assertEquals(AiVerdict.INCONCLUSIVE, resC.getVerdict());

        // Case D: Genuinely unparseable syntax (triggers text scanner fallback)
        String unparseable = "{verdict: == 'ACCESS_DENIED', reasoning: @@broken}";
        AiEvaluationResult resD = AiSemanticEngine.parseResponse(unparseable, "model-d", 130L);
        assertNotNull(resD);
        assertEquals(AiVerdict.ACCESS_DENIED, resD.getVerdict());
        assertTrue(resD.getReasoning().contains("Parsed from text"));
    }

    @Test
    @DisplayName("JSON containing null fields or non-string values does not crash with unhandled exception")
    void testJsonWithNullOrWrongFieldTypes() {
        // Case A: verdict is null
        String nullVerdict = "{\"verdict\": null, \"reasoning\": \"something\"}";
        AiEvaluationResult resA = AiSemanticEngine.parseResponse(nullVerdict, "test-model", 80L);
        assertNotNull(resA);
        assertNotNull(resA.getVerdict());

        // Case B: verdict is a number
        String numericVerdict = "{\"verdict\": 12345, \"reasoning\": \"Invalid type\"}";
        AiEvaluationResult resB = AiSemanticEngine.parseResponse(numericVerdict, "test-model", 80L);
        assertNotNull(resB);
        assertNotNull(resB.getVerdict());

        // Case C: verdict is an array
        String arrayVerdict = "{\"verdict\": [\"VALIDATED_ACCESS\"], \"reasoning\": \"Array wrapped\"}";
        AiEvaluationResult resC = AiSemanticEngine.parseResponse(arrayVerdict, "test-model", 80L);
        assertNotNull(resC);
        assertEquals(AiVerdict.VALIDATED_ACCESS, resC.getVerdict());

        // Case D: reasoning is null
        String nullReasoning = "{\"verdict\": \"ACCESS_DENIED\", \"reasoning\": null}";
        AiEvaluationResult resD = AiSemanticEngine.parseResponse(nullReasoning, "test-model", 80L);
        assertNotNull(resD);
        assertEquals(AiVerdict.ACCESS_DENIED, resD.getVerdict());
    }

    @Test
    @DisplayName("Top-level array or primitive responses parsed without error")
    void testTopLevelArrayOrPrimitiveWrapping() {
        // Top-level array containing JSON object
        String arrayObj = "[{\"verdict\": \"VALIDATED_ACCESS\", \"reasoning\": \"Wrapped in array\"}]";
        AiEvaluationResult resA = AiSemanticEngine.parseResponse(arrayObj, "model-x", 90L);
        assertNotNull(resA);
        assertEquals(AiVerdict.VALIDATED_ACCESS, resA.getVerdict());

        // Bare quoted verdict string
        String bareQuoted = "\"ACCESS_DENIED\"";
        AiEvaluationResult resB = AiSemanticEngine.parseResponse(bareQuoted, "model-y", 90L);
        assertNotNull(resB);
        assertEquals(AiVerdict.ACCESS_DENIED, resB.getVerdict());

        // Bare unquoted keyword
        String bareWord = "VALIDATED_ACCESS";
        AiEvaluationResult resC = AiSemanticEngine.parseResponse(bareWord, "model-z", 90L);
        assertNotNull(resC);
        assertEquals(AiVerdict.VALIDATED_ACCESS, resC.getVerdict());
    }

    @Test
    @DisplayName("Natural language unstructured responses extract correct semantic verdict")
    void testNaturalLanguageUnstructuredResponses() {
        String bypassText = "Audit Summary: The replayed role successfully exfiltrated user records. This is a critical security vulnerability and unauthorized access was validated.";
        AiEvaluationResult res1 = AiSemanticEngine.parseResponse(bypassText, "nlp-model", 100L);
        assertEquals(AiVerdict.VALIDATED_ACCESS, res1.getVerdict());

        String deniedText = "Audit Summary: The application enforced authentication controls. The request was blocked with 401 Unauthorized.";
        AiEvaluationResult res2 = AiSemanticEngine.parseResponse(deniedText, "nlp-model", 100L);
        assertEquals(AiVerdict.ACCESS_DENIED, res2.getVerdict());

        String ambiguousText = "The server returned 500 Internal Server Error. Insufficient data to conclude.";
        AiEvaluationResult res3 = AiSemanticEngine.parseResponse(ambiguousText, "nlp-model", 100L);
        assertEquals(AiVerdict.ERROR, res3.getVerdict());

        String genericText = "Lorem ipsum dolor sit amet, consectetur adipiscing elit.";
        AiEvaluationResult res4 = AiSemanticEngine.parseResponse(genericText, "nlp-model", 100L);
        assertEquals(AiVerdict.INCONCLUSIVE, res4.getVerdict());
    }

    @Test
    @DisplayName("Case-insensitivity and synonym verdict mapping in JSON")
    void testCaseInsensitiveAndSynonymVerdicts() {
        assertEquals(AiVerdict.VALIDATED_ACCESS,
                AiSemanticEngine.parseResponse("{\"verdict\": \"validated_access\"}", "m", 1L).getVerdict());
        assertEquals(AiVerdict.VALIDATED_ACCESS,
                AiSemanticEngine.parseResponse("{\"verdict\": \"BYPASS\"}", "m", 1L).getVerdict());
        assertEquals(AiVerdict.VALIDATED_ACCESS,
                AiSemanticEngine.parseResponse("{\"verdict\": \"VULNERABLE\"}", "m", 1L).getVerdict());
        assertEquals(AiVerdict.ACCESS_DENIED,
                AiSemanticEngine.parseResponse("{\"verdict\": \"access_denied\"}", "m", 1L).getVerdict());
        assertEquals(AiVerdict.ACCESS_DENIED,
                AiSemanticEngine.parseResponse("{\"verdict\": \"BLOCKED\"}", "m", 1L).getVerdict());
        assertEquals(AiVerdict.ACCESS_DENIED,
                AiSemanticEngine.parseResponse("{\"verdict\": \"ENFORCED\"}", "m", 1L).getVerdict());
        assertEquals(AiVerdict.INCONCLUSIVE,
                AiSemanticEngine.parseResponse("{\"verdict\": \"UNKNOWN\"}", "m", 1L).getVerdict());
    }

    // =========================================================================
    // SECTION 2: Extreme Network Conditions (401, 429, 500, Timeouts, Drops)
    // =========================================================================

    @Test
    @DisplayName("Network 401 Unauthorized returns descriptive error without crashing")
    void testNetworkHttp401Unauthorized() {
        responseStatusCode.set(401);
        responsePayload.set("""
                {
                  "error": {
                    "message": "Invalid authentication token",
                    "type": "invalid_request_error",
                    "code": "invalid_api_key"
                  }
                }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setApiKey("invalid-token");

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                client.sendChatCompletionAsync(config, "", "Test").get(5, TimeUnit.SECONDS)
        );

        assertTrue(ex.getMessage().contains("HTTP 401"));
        assertTrue(ex.getMessage().contains("Invalid authentication token"));

        assertFalse(client.testConnection(config));
        OpenAiClient.ConnectionTestResult testResult = client.testConnectionDetailed(config);
        assertFalse(testResult.success());
        assertTrue(testResult.message().contains("HTTP 401"));
    }

    @Test
    @DisplayName("Network 429 Too Many Requests returns rate limit notification")
    void testNetworkHttp429RateLimit() {
        responseStatusCode.set(429);
        responsePayload.set("""
                {
                  "error": {
                    "message": "Rate limit exceeded: quota exhausted. Please try again later.",
                    "type": "insufficient_quota"
                  }
                }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                client.sendChatCompletionAsync(config, "", "Test").get(5, TimeUnit.SECONDS)
        );

        assertTrue(ex.getMessage().contains("HTTP 429"));
        assertTrue(ex.getMessage().contains("Rate limit exceeded"));

        assertFalse(client.testConnection(config));
    }

    @Test
    @DisplayName("Network 500 Internal Server Error with HTML payload is safely extracted")
    void testNetworkHttp500InternalServerErrorWithHtml() {
        responseStatusCode.set(500);
        responsePayload.set("<!DOCTYPE html><html><head><title>500 Internal Server Error</title></head><body>Crash</body></html>");

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                client.sendChatCompletionAsync(config, "", "Test").get(5, TimeUnit.SECONDS)
        );

        assertTrue(ex.getMessage().contains("HTTP 500"));
        assertTrue(ex.getMessage().contains("500 Internal Server Error"));
        assertFalse(client.testConnection(config));
    }

    @Test
    @DisplayName("Slow connection timeout is strictly enforced")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testNetworkSlowConnectionTimeout() {
        sleepDelayMs.set(4000); // Server pauses 4 seconds

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setTimeoutSeconds(1); // Client aborts after 1 second

        long start = System.currentTimeMillis();
        ExecutionException ex = assertThrows(ExecutionException.class, () ->
                client.sendChatCompletionAsync(config, "", "Test").get(4, TimeUnit.SECONDS)
        );
        long elapsed = System.currentTimeMillis() - start;

        assertTrue(elapsed < 3000, "Timeout should trigger within 3 seconds, elapsed: " + elapsed + "ms");
        assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("Abrupt TCP socket drop handles IOException cleanly")
    void testNetworkAbruptTcpSocketDrop() throws Exception {
        try (ServerSocket rawSocket = new ServerSocket(0)) {
            int rawPort = rawSocket.getLocalPort();

            Thread serverThread = new Thread(() -> {
                try {
                    Socket accepted = rawSocket.accept();
                    accepted.close();
                } catch (IOException ignored) {}
            });
            serverThread.setDaemon(true);
            serverThread.start();

            AiConfig config = new AiConfig();
            config.setEndpointUrl("http://127.0.0.1:" + rawPort);
            config.setTimeoutSeconds(2);

            ExecutionException ex = assertThrows(ExecutionException.class, () ->
                    client.sendChatCompletionAsync(config, "", "Test").get(3, TimeUnit.SECONDS)
            );

            assertNotNull(ex.getCause());
            assertTrue(ex.getCause() instanceof IOException);

            assertFalse(client.testConnection(config));
        }
    }

    @Test
    @DisplayName("AiSemanticEngine async execution marks ERROR on network failure without blocking EDT")
    void testEngineAsyncResilienceUnderNetworkFailures() throws Exception {
        responseStatusCode.set(500);
        responsePayload.set("Server unavailable");

        EngineController controller = new EngineController(null);
        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setModelName("error-test-model");
        controller.setAiConfig(config);

        try {
            UserRole role = new UserRole("Attacker", "X-Role: attacker", "");
            role.setId("role-1");
            controller.addRole(role);

            AuditRecord record = new AuditRecord(500, null);
            AuthorizationCheckResult res = new AuthorizationCheckResult(
                    "role-1", "Attacker", Verdict.INDETERMINABLE, 200, 100, 0.5, 10L, "Note", null, null
            );
            record.addRoleResult("role-1", res);
            controller.addRecord(record);

            CountDownLatch latch = new CountDownLatch(1);
            controller.evaluateRecordWithAi(record, latch::countDown);

            assertEquals(AiVerdict.PENDING, record.getAiResult().getVerdict());

            assertTrue(latch.await(5, TimeUnit.SECONDS), "AI evaluation should complete despite server error");

            assertEquals(AiVerdict.ERROR, record.getAiResult().getVerdict());
            assertEquals(AiVerdict.ERROR, res.getAiResult().getVerdict());
            assertTrue(res.getAiResult().getReasoning().contains("HTTP 500")
                    || res.getAiResult().getReasoning().contains("AI evaluation failed"));
        } finally {
            controller.shutdown();
        }
    }

    // =========================================================================
    // SECTION 3: Huge Payload Truncation (100KB+ bodies)
    // =========================================================================

    @Test
    @DisplayName("100KB payload safely truncated to ~3,500 characters with head/tail preserved")
    void testHugePayloadTruncation100KB() {
        StringBuilder sb = new StringBuilder(105000);
        sb.append("HEAD_DATA_START: ");
        for (int i = 0; i < 2500; i++) {
            sb.append("Line ").append(i).append(": Customer data payload 1234567890. ");
        }
        sb.append(" :TAIL_DATA_END");

        String hugeBody = sb.toString();
        int originalLen = hugeBody.length();
        assertTrue(originalLen > 100000, "Body must exceed 100KB, was: " + originalLen);

        String truncated = AiSemanticEngine.truncateBody(hugeBody);

        assertNotNull(truncated);
        assertTrue(truncated.length() < 4000, "Truncated length should be ~3540 chars, was: " + truncated.length());
        assertTrue(truncated.startsWith("HEAD_DATA_START: "));
        assertTrue(truncated.endsWith(" :TAIL_DATA_END"));

        int expectedOmitted = originalLen - 3500;
        assertTrue(truncated.contains("[... Truncated " + expectedOmitted + " characters ...]"));
    }

    @Test
    @DisplayName("500KB and 1MB payloads truncate without memory exhaustion in sub-millisecond time")
    void testHugePayloadTruncation500KBAnd1MB() {
        int[] sizes = {500_000, 1_000_000};

        for (int size : sizes) {
            char[] chars = new char[size];
            java.util.Arrays.fill(chars, 'A');
            chars[0] = 'H'; chars[1] = 'E'; chars[2] = 'A'; chars[3] = 'D';
            chars[size - 4] = 'T'; chars[size - 3] = 'A'; chars[size - 2] = 'I'; chars[size - 1] = 'L';
            String giantBody = new String(chars);

            long start = System.nanoTime();
            String truncated = AiSemanticEngine.truncateBody(giantBody);
            long durationNs = System.nanoTime() - start;

            assertNotNull(truncated);
            assertTrue(durationNs < 5_000_000, "Truncation must execute in < 5ms, took: " + (durationNs / 1_000_000.0) + "ms");
            assertTrue(truncated.startsWith("HEAD"));
            assertTrue(truncated.endsWith("TAIL"));
            assertTrue(truncated.length() < 4000);
            int omitted = size - 3500;
            assertTrue(truncated.contains("[... Truncated " + omitted + " characters ...]"));
        }
    }

    @Test
    @DisplayName("Exact boundary behavior around 3,500 characters threshold")
    void testTruncationExactBoundaries() {
        String s3499 = "A".repeat(3499);
        assertEquals(s3499, AiSemanticEngine.truncateBody(s3499));

        String s3500 = "B".repeat(3500);
        assertEquals(s3500, AiSemanticEngine.truncateBody(s3500));

        String s3501 = "C".repeat(3501);
        String res3501 = AiSemanticEngine.truncateBody(s3501);
        assertNotEquals(s3501, res3501);
        assertTrue(res3501.contains("[... Truncated 1 characters ...]"));
        assertEquals(3000, res3501.indexOf("\n[... Truncated"));
    }

    @Test
    @DisplayName("Full buildUserPrompt prompt generation with 150KB differential bodies executes without lag or overflow")
    void testHugePayloadInBuildUserPromptEndToEnd() {
        String orig150KB = "ORIGINAL_START: " + "X".repeat(150_000) + " :ORIGINAL_END";
        String role150KB = "REPLAY_START: " + "Y".repeat(150_000) + " :REPLAY_END";

        AuditRecord record = new AuditRecord(1001, null);
        record.setSerializedOriginalResponse("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + orig150KB);

        AuthorizationCheckResult roleResult = new AuthorizationCheckResult(
                "attacker-role", "Attacker", Verdict.INDETERMINABLE, 200, role150KB.length(), 0.0, 25L, "Notes", null, null
        );
        roleResult.setSerializedResponse("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n\r\n" + role150KB);

        long start = System.currentTimeMillis();
        String prompt = AiSemanticEngine.buildUserPrompt(record, roleResult);
        long elapsed = System.currentTimeMillis() - start;

        assertNotNull(prompt);
        assertTrue(prompt.length() < 15000, "Prompt should be compact, was: " + prompt.length() + " chars");
        assertTrue(elapsed < 200, "Prompt construction should take < 200ms, took: " + elapsed + "ms");

        assertTrue(prompt.contains("ORIGINAL_START: "));
        assertTrue(prompt.contains("REPLAY_START: "));
        assertTrue(prompt.contains("[... Truncated "));
        assertTrue(prompt.contains("## 5. DIFFERENTIAL METRICS & HEURISTICS"));
    }

    @Test
    @DisplayName("Evaluate record with disabled role still evaluates and does NOT get stuck in PENDING")
    void testEvaluateRecordWithDisabledRoleDoesNotHangInPending() throws Exception {
        responseStatusCode.set(200);
        responsePayload.set("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "{\\"verdict\\": \\"VALIDATED_ACCESS\\", \\"confidence\\": 0.9, \\"reasoning\\": \\"Bypass confirmed\\"}"
                      }
                    }
                  ]
                }
                """);

        EngineController controller = new EngineController(null);
        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setModelName("test-model");
        controller.setAiConfig(config);

        try {
            UserRole role = new UserRole("DisabledRole", "X-Role: test", "");
            role.setId("role-disabled");
            role.setEnabled(false); // Disabled in proxy interceptor!
            controller.addRole(role);

            AuditRecord record = new AuditRecord(888, null);
            AuthorizationCheckResult res = new AuthorizationCheckResult(
                    "role-disabled", "DisabledRole", Verdict.BYPASS, 200, 100, 0.9, 10L, "Note", null, null
            );
            record.addRoleResult("role-disabled", res);
            controller.addRecord(record);

            CountDownLatch latch = new CountDownLatch(1);
            controller.evaluateRecordWithAi(record, latch::countDown);

            assertTrue(latch.await(5, TimeUnit.SECONDS), "AI evaluation must complete for disabled role");
            assertNotEquals(AiVerdict.PENDING, record.getAiResult().getVerdict());
            assertEquals(AiVerdict.VALIDATED_ACCESS, record.getAiResult().getVerdict());
        } finally {
            controller.shutdown();
        }
    }

    @Test
    @DisplayName("Evaluate record with zero targets fails fast and never gets stuck in PENDING")
    void testEvaluateRecordWithZeroTargetsFailsFast() throws Exception {
        EngineController controller = new EngineController(null);
        AiConfig config = new AiConfig();
        config.setModelName("test-model");
        controller.setAiConfig(config);

        try {
            // Record with NO role results and NO unauth results
            AuditRecord record = new AuditRecord(999, null);
            controller.addRecord(record);

            CountDownLatch latch = new CountDownLatch(1);
            controller.evaluateRecordWithAi(record, latch::countDown);

            assertTrue(latch.await(2, TimeUnit.SECONDS), "AI evaluation must fail fast with zero targets");
            assertNotEquals(AiVerdict.PENDING, record.getAiResult().getVerdict());
            assertEquals(AiVerdict.ERROR, record.getAiResult().getVerdict());
            assertTrue(record.getAiResult().getReasoning().contains("No role replay"));
        } finally {
            controller.shutdown();
        }
    }
}
