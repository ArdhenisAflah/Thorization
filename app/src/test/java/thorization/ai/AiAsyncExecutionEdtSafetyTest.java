package thorization.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class AiAsyncExecutionEdtSafetyTest {

    private HttpServer server;
    private int port;
    private EngineController controller;

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
                // Simulate network latency
                try {
                    Thread.sleep(150);
                } catch (InterruptedException ignored) {}

                String response = """
                        {
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "{\\"verdict\\": \\"VALIDATED_ACCESS\\", \\"reasoning\\": \\"BOLA confirmed\\"}"
                              }
                            }
                          ]
                        }
                        """;
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        });
        server.start();

        controller = new EngineController(null);
        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setModelName("test-model");
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
    void testAsyncPendingStateAndEdtSafety() throws InterruptedException {
        UserRole role = new UserRole("Attacker", "X-Role: attacker", "");
        role.setId("r1");
        controller.addRole(role);

        AuditRecord record = new AuditRecord(101, null);
        AuthorizationCheckResult res = new AuthorizationCheckResult(
                "r1", "Attacker", Verdict.BYPASS, 200, 150, 0.95, 20L, "Note", null, null
        );
        record.addRoleResult("r1", res);
        controller.addRecord(record);

        AtomicBoolean edtVerified = new AtomicBoolean(false);
        CountDownLatch updatedLatch = new CountDownLatch(2); // 1 for PENDING, 1 for completion

        controller.addListener(new EngineListener() {
            @Override
            public void onAuditRecordAdded(AuditRecord record) {}

            @Override
            public void onAuditRecordUpdated(AuditRecord record) {
                if (SwingUtilities.isEventDispatchThread()) {
                    edtVerified.set(true);
                }
                updatedLatch.countDown();
            }

            @Override
            public void onAuditRecordsRemoved(List<AuditRecord> removedRecords) {}

            @Override
            public void onAuditRecordsCleared() {}

            @Override
            public void onRolesChanged(List<UserRole> roles) {}

            @Override
            public void onEngineStateChanged(boolean enabled) {}

            @Override
            public void onFilterConfigChanged(FilterConfig filterConfig) {}

            @Override
            public void onPipelinesChanged(List<Pipeline> pipelines) {}
        });

        CountDownLatch completeLatch = new CountDownLatch(1);

        // Trigger AI evaluation
        controller.evaluateRecordWithAi(record, completeLatch::countDown);

        // Immediate check: state must be PENDING
        assertNotNull(record.getAiResult());
        assertEquals(AiVerdict.PENDING, record.getAiResult().getVerdict());
        assertEquals(AiVerdict.PENDING, res.getAiResult().getVerdict());

        // Wait for asynchronous completion
        assertTrue(completeLatch.await(5, TimeUnit.SECONDS), "AI evaluation should complete within 5 seconds");
        assertTrue(updatedLatch.await(5, TimeUnit.SECONDS), "UI updates should be received");

        // Verify final state
        assertEquals(AiVerdict.VALIDATED_ACCESS, record.getAiResult().getVerdict());
        assertEquals(AiVerdict.VALIDATED_ACCESS, res.getAiResult().getVerdict());
        assertTrue(edtVerified.get(), "Observer notifications must be dispatched on Swing EDT");
    }
}
