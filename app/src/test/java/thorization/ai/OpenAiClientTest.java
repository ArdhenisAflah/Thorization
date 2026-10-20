package thorization.ai;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class OpenAiClientTest {

    private HttpServer server;
    private int port;
    private OpenAiClient client;
    private final AtomicInteger responseStatusCode = new AtomicInteger(200);
    private final AtomicReference<String> responsePayload = new AtomicReference<>("");
    private final AtomicReference<String> lastAuthHeader = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicReference<com.sun.net.httpserver.Headers> lastHeaders = new AtomicReference<>();
    private final AtomicInteger sleepDelayMs = new AtomicInteger(0);

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();

        HttpHandler handler = new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                lastHeaders.set(exchange.getRequestHeaders());
                lastAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
                lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));

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

    @Test
    void testSendChatCompletion_Success200() throws Exception {
        String mockResponse = """
                {
                  "id": "chatcmpl-123",
                  "model": "gpt-4o-mini",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"verdict\\": \\"VALIDATED_ACCESS\\", \\"reasoning\\": \\"BOLA confirmed\\"}"
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """;
        responseStatusCode.set(200);
        responsePayload.set(mockResponse);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setApiKey("test-secret-key");
        config.setModelName("gpt-4o-mini");

        CompletableFuture<String> future = client.sendChatCompletionAsync(config, "System prompt", "User prompt");
        String result = future.get(5, TimeUnit.SECONDS);

        assertNotNull(result);
        assertTrue(result.contains("VALIDATED_ACCESS"));
        assertEquals("Bearer test-secret-key", lastAuthHeader.get());
        assertEquals("application/json", lastContentType.get());
    }

    @Test
    void testSendChatCompletion_AnonymousApiKeyOmitsHeader() throws Exception {
        String mockResponse = """
                {
                  "choices": [
                    {
                      "message": {
                        "role": "assistant",
                        "content": "OK"
                      }
                    }
                  ]
                }
                """;
        responseStatusCode.set(200);
        responsePayload.set(mockResponse);

        AiConfig config = AiConfig.forOllama("llama3");
        config.setEndpointUrl("http://127.0.0.1:" + port + "/v1");
        config.setApiKey("");

        String result = client.sendChatCompletionAsync(config, "", "Test").get(5, TimeUnit.SECONDS);
        assertEquals("OK", result);
        assertNull(lastAuthHeader.get());
    }

    @Test
    void testUrlNormalization() {
        assertEquals("http://127.0.0.1:8080/v1/chat/completions",
                OpenAiClient.resolveEndpointUri("127.0.0.1:8080").toString());
        assertEquals("http://127.0.0.1:8080/v1/chat/completions",
                OpenAiClient.resolveEndpointUri("http://127.0.0.1:8080/").toString());
        assertEquals("http://127.0.0.1:8080/v1/chat/completions",
                OpenAiClient.resolveEndpointUri("http://127.0.0.1:8080/v1").toString());
        assertEquals("https://api.deepseek.com/chat/completions",
                OpenAiClient.resolveEndpointUri("https://api.deepseek.com/chat/completions").toString());
    }

    @Test
    void testSendChatCompletion_Http401Unauthorized() {
        responseStatusCode.set(401);
        responsePayload.set("""
                {
                  "error": {
                    "message": "Incorrect API key provided.",
                    "type": "invalid_request_error"
                  }
                }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setApiKey("bad-key");

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "", "Hi").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("HTTP 401"));
        assertTrue(ex.getMessage().contains("Incorrect API key provided"));
    }

    @Test
    void testSendChatCompletion_Http429RateLimit() {
        responseStatusCode.set(429);
        responsePayload.set("""
                {
                  "error": {
                    "message": "Rate limit reached for requests."
                  }
                }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "", "Hi").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("HTTP 429"));
        assertTrue(ex.getMessage().contains("Rate limit reached"));
    }

    @Test
    void testSendChatCompletion_Http500ServerError() {
        responseStatusCode.set(500);
        responsePayload.set("Internal server error occurred");

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "", "Hi").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("HTTP 500"));
    }

    @Test
    void testSendChatCompletion_TimeoutHandling() {
        sleepDelayMs.set(2500);
        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);
        config.setTimeoutSeconds(1);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "", "Hi").get(5, TimeUnit.SECONDS);
        });

        assertNotNull(ex.getCause());
    }

    @Test
    void testSendChatCompletion_MalformedJson() {
        responseStatusCode.set(200);
        responsePayload.set("<html><body>502 Bad Gateway</body></html>");

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "", "Hi").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("Failed to parse OpenAI API JSON response"));
    }

    @Test
    void testSendChatCompletion_EmptyChoices() {
        responseStatusCode.set(200);
        responsePayload.set("""
                {
                  "id": "123",
                  "choices": []
                }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        ExecutionException ex = assertThrows(ExecutionException.class, () -> {
            client.sendChatCompletionAsync(config, "", "Hi").get(5, TimeUnit.SECONDS);
        });

        assertTrue(ex.getMessage().contains("no choices"));
    }

    @Test
    void testConnection_ReturnsTrueOn200() {
        responseStatusCode.set(200);
        responsePayload.set("""
                {
                  "choices": [
                    {
                      "message": { "role": "assistant", "content": "OK" }
                    }
                  ]
                }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        assertTrue(client.testConnection(config));

        OpenAiClient.ConnectionTestResult res = client.testConnectionDetailed(config);
        assertTrue(res.success());
        assertEquals(200, res.statusCode());
        assertTrue(res.message().contains("OK"));
    }

    @Test
    void testConnection_ReturnsFalseOnPortClosed() {
        AiConfig config = new AiConfig();
        // Point to an unallocated port
        config.setEndpointUrl("http://127.0.0.1:65534");
        config.setTimeoutSeconds(1);

        assertFalse(client.testConnection(config));

        OpenAiClient.ConnectionTestResult res = client.testConnectionDetailed(config);
        assertFalse(res.success());
    }

    @Test
    void testConnection_ReturnsFalseOn401() {
        responseStatusCode.set(401);
        responsePayload.set("""
                { "error": { "message": "Unauthorized" } }
                """);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port);

        assertFalse(client.testConnection(config));
    }

    @Test
    void testAiVerdict_ParsingAndBadges() {
        assertEquals(AiVerdict.VALIDATED_ACCESS, AiVerdict.fromString("VALIDATED_ACCESS"));
        assertEquals(AiVerdict.VALIDATED_ACCESS, AiVerdict.fromString("bypass"));
        assertEquals(AiVerdict.VALIDATED_ACCESS, AiVerdict.fromString("vulnerable"));
        assertEquals(AiVerdict.ACCESS_DENIED, AiVerdict.fromString("ACCESS_DENIED"));
        assertEquals(AiVerdict.ACCESS_DENIED, AiVerdict.fromString("denied"));
        assertEquals(AiVerdict.ACCESS_DENIED, AiVerdict.fromString("enforced"));
        assertEquals(AiVerdict.PENDING, AiVerdict.fromString("PENDING"));
        assertEquals(AiVerdict.PENDING, AiVerdict.fromString("running"));
        assertEquals(AiVerdict.ERROR, AiVerdict.fromString("ERROR"));
        assertEquals(AiVerdict.ERROR, AiVerdict.fromString("failed"));
        assertEquals(AiVerdict.INCONCLUSIVE, AiVerdict.fromString("INCONCLUSIVE"));
        assertEquals(AiVerdict.INCONCLUSIVE, AiVerdict.fromString(null));
        assertEquals(AiVerdict.INCONCLUSIVE, AiVerdict.fromString(""));

        assertTrue(AiVerdict.VALIDATED_ACCESS.isVulnerability());
        assertFalse(AiVerdict.ACCESS_DENIED.isVulnerability());
        assertFalse(AiVerdict.PENDING.isFinal());
        assertTrue(AiVerdict.ACCESS_DENIED.isFinal());

        assertNotNull(AiVerdict.VALIDATED_ACCESS.getForegroundColor());
        assertNotNull(AiVerdict.VALIDATED_ACCESS.getBackgroundColor());
        assertNotNull(AiVerdict.ACCESS_DENIED.getForegroundColor());
        assertNotNull(AiVerdict.PENDING.getForegroundColor());
    }

    @Test
    void testAiEvaluationResult_RecordAndGsonRoundTrip() {
        AiEvaluationResult result = AiEvaluationResult.success(
                AiVerdict.VALIDATED_ACCESS,
                "IDOR vulnerability detected",
                "gpt-4o-mini",
                350L
        );

        assertEquals(AiVerdict.VALIDATED_ACCESS, result.getVerdict());
        assertEquals("IDOR vulnerability detected", result.getReasoning());
        assertEquals("gpt-4o-mini", result.getModel());
        assertEquals(350L, result.getExecutionTimeMs());
        assertFalse(result.isCached());

        AiEvaluationResult cachedCopy = result.withCached(true);
        assertTrue(cachedCopy.isCached());
        assertEquals(0L, cachedCopy.getExecutionTimeMs());

        // Gson round-trip
        Gson gson = new Gson();
        String json = gson.toJson(result);
        AiEvaluationResult deserialized = gson.fromJson(json, AiEvaluationResult.class);

        assertEquals(result.getVerdict(), deserialized.getVerdict());
        assertEquals(result.getReasoning(), deserialized.getReasoning());
        assertEquals(result.getModel(), deserialized.getModel());
    }

    @Test
    void testCustomHeadersTransmission() throws Exception {
        String mockResponse = """
                {
                  "id": "chatcmpl-custom",
                  "model": "glm-5.3-flash",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "{\\"verdict\\": \\"ACCESS_DENIED\\", \\"reasoning\\": \\"Enforced\\"}"
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """;
        responseStatusCode.set(200);
        responsePayload.set(mockResponse);

        AiConfig config = new AiConfig();
        config.setEndpointUrl("http://127.0.0.1:" + port + "/v1/chat/completions");
        config.setModelName("glm-5.3-flash");
        config.setApiKey("test-token-123");
        config.setRawHeaders("x-opencode-session: session-open-code-42\nX-Provider-Region: ap-southeast-1\nAnthropic-Version: 2023-06-01");

        String reply = client.sendChatCompletionAsync(config, "System prompt", "User prompt")
                .get(5, TimeUnit.SECONDS);

        assertNotNull(reply);
        assertTrue(reply.contains("ACCESS_DENIED"));

        com.sun.net.httpserver.Headers headers = lastHeaders.get();
        assertNotNull(headers);
        assertEquals("Bearer test-token-123", headers.getFirst("Authorization"));
        assertEquals("session-open-code-42", headers.getFirst("x-opencode-session"));
        assertEquals("ap-southeast-1", headers.getFirst("X-Provider-Region"));
        assertEquals("2023-06-01", headers.getFirst("Anthropic-Version"));
        assertTrue(headers.getFirst("User-Agent").contains("Thorization"));
    }
}
