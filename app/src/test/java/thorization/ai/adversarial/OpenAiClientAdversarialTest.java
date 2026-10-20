package thorization.ai.adversarial;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import thorization.ai.AiConfig;
import thorization.ai.OpenAiClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial and boundary stress tests for OpenAiClient.
 * Tests connection failure modes, corrupt payloads, HTTP error codes,
 * extreme timeouts, slow loris delays, and endpoint resolution quirks.
 */
class OpenAiClientAdversarialTest {

    private HttpServer server;
    private int port;
    private OpenAiClient client;

    private final AtomicInteger statusCode = new AtomicInteger(200);
    private final AtomicReference<String> responseBody = new AtomicReference<>("");
    private final AtomicReference<String> responseContentType = new AtomicReference<>("application/json");
    private final AtomicInteger delayMs = new AtomicInteger(0);
    private final AtomicInteger requestCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        HttpHandler handler = exchange -> {
            requestCount.incrementAndGet();
            int delay = delayMs.get();
            if (delay > 0) {
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ignored) {}
            }

            int status = statusCode.get();
            byte[] bytes = responseBody.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", responseContentType.get());
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        };

        server.createContext("/v1/chat/completions", handler);
        server.createContext("/chat/completions", handler);
        server.createContext("/custom/chat/completions", handler);
        server.start();

        client = new OpenAiClient();
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 429, 500, 502, 503, 504})
    void testHttpErrorStatusResponses(int errorStatus) {
        statusCode.set(errorStatus);
        responseBody.set("""
                {
                  "error": {
                    "message": "Simulated error for code %d",
                    "type": "server_error",
                    "code": "test_err_%d"
                  }
                }
                """.formatted(errorStatus, errorStatus));

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "System", "User").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("HTTP " + errorStatus),
                "Error message should mention HTTP " + errorStatus + " but got: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Simulated error for code " + errorStatus));
    }

    @Test
    void testHttp502WithHtmlNginxBody() {
        statusCode.set(502);
        responseContentType.set("text/html");
        responseBody.set("<html><head><title>502 Bad Gateway</title></head><body><center><h1>502 Bad Gateway</h1></center><hr><center>nginx/1.24.0</center></body></html>");

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "System", "User").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("HTTP 502"));
        assertTrue(ex.getMessage().contains("502 Bad Gateway"));
    }

    @Test
    void testCorruptedTruncatedJsonPayload() {
        statusCode.set(200);
        responseContentType.set("application/json");
        responseBody.set("{\"id\": \"chatcmpl-1\", \"choices\": [{\"message\": {\"content\": \"partial content");

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "System", "User").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("Failed to parse OpenAI API JSON response"));
    }

    @Test
    void testChoicesArrayWithNullMessageOrContent() {
        statusCode.set(200);
        responseBody.set("""
                {
                  "id": "cmpl-null",
                  "choices": [
                    {
                      "index": 0,
                      "message": null
                    }
                  ]
                }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "System", "User").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("empty message content") || ex.getMessage().contains("Failed to parse"));
    }

    @Test
    void testChoicesArrayWithNullContentString() {
        statusCode.set(200);
        responseBody.set("""
                {
                  "id": "cmpl-null",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": null
                      }
                    }
                  ]
                }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "System", "User").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("empty message content"));
    }

    @Test
    void testGiantResponsePayload() throws Exception {
        statusCode.set(200);
        // 500KB response payload
        String largeContent = "A".repeat(500_000);
        responseBody.set("""
                {
                  "id": "cmpl-giant",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "%s"
                      }
                    }
                  ]
                }
                """.formatted(largeContent));

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setTimeoutSeconds(10);

        String result = client.sendChatCompletionAsync(config, "System", "User").get(10, TimeUnit.SECONDS);
        assertEquals(largeContent, result);
    }

    @Test
    void testConcurrentBurstRequests() throws Exception {
        statusCode.set(200);
        responseBody.set("""
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "BURST_SUCCESS"
                      }
                    }
                  ]
                }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        int concurrentRequests = 30;
        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < concurrentRequests; i++) {
            futures.add(client.sendChatCompletionAsync(config, "System prompt " + i, "User prompt " + i));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        for (CompletableFuture<String> f : futures) {
            assertEquals("BURST_SUCCESS", f.get());
        }
        assertEquals(concurrentRequests, requestCount.get());
    }

    @Test
    void testConnectionDetailed_WithSlowServerTimeout() {
        delayMs.set(4000); // 4 seconds delay
        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setTimeoutSeconds(2); // Client timeout 2s

        OpenAiClient.ConnectionTestResult res = client.testConnectionDetailed(config);
        assertFalse(res.success(), "Connection should fail due to timeout");
        assertTrue(res.message().contains("failed") || res.message().contains("timed out") || res.message().contains("TimeoutException") || res.message().contains("Timeout"),
                "Message was: " + res.message());
        assertTrue(res.latencyMs() > 0);
    }

    @Test
    void testConnectionDetailed_WithNullConfig() {
        OpenAiClient.ConnectionTestResult res = client.testConnectionDetailed(null);
        assertFalse(res.success());
        assertEquals("Configuration is null", res.message());
        assertEquals(0, res.statusCode());
    }

    @Test
    void testConnectionDetailed_UnreachableHost() {
        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://192.0.2.1:12345"); // Non-routable TEST-NET-1 IP
        config.setTimeoutSeconds(1);

        OpenAiClient.ConnectionTestResult res = client.testConnectionDetailed(config);
        assertFalse(res.success());
        assertTrue(res.message().contains("Connection test failed:"));
    }

    @Test
    void testResolveEndpointUri_EdgeCases() {
        // Trailing slashes
        assertEquals("http://localhost:11434/v1/chat/completions",
                OpenAiClient.resolveEndpointUri("http://localhost:11434///").toString());

        // Already has /chat/completions
        assertEquals("https://my-proxy.com/chat/completions",
                OpenAiClient.resolveEndpointUri("https://my-proxy.com/chat/completions").toString());

        // With /v1 trailing
        assertEquals("https://my-proxy.com/v1/chat/completions",
                OpenAiClient.resolveEndpointUri("https://my-proxy.com/v1").toString());
        assertEquals("https://my-proxy.com/v1/chat/completions",
                OpenAiClient.resolveEndpointUri("https://my-proxy.com/v1/").toString());

        // Null or blank throws
        assertThrows(IllegalArgumentException.class, () -> OpenAiClient.resolveEndpointUri(null));
        assertThrows(IllegalArgumentException.class, () -> OpenAiClient.resolveEndpointUri("   "));
    }
}
