package thorization.ai;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * High-performance client targeting OpenAI-compatible endpoints using Java 21 java.net.http.HttpClient.
 */
public class OpenAiClient {

    private final HttpClient httpClient;
    private final Gson gson;

    public OpenAiClient() {
        this(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build(),
             new Gson());
    }

    public OpenAiClient(HttpClient httpClient, Gson gson) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.gson = Objects.requireNonNull(gson, "gson cannot be null");
    }

    /**
     * Normalizes the endpoint URL to point to the OpenAI chat completions path.
     */
    public static URI resolveEndpointUri(String inputUrl) {
        if (inputUrl == null || inputUrl.isBlank()) {
            throw new IllegalArgumentException("Endpoint URL cannot be null or blank");
        }
        String trimmed = inputUrl.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            trimmed = "http://" + trimmed;
        }
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.endsWith("/chat/completions")) {
            return URI.create(trimmed);
        }
        if (trimmed.endsWith("/v1")) {
            return URI.create(trimmed + "/chat/completions");
        }
        return URI.create(trimmed + "/v1/chat/completions");
    }

    /**
     * Asynchronously dispatches a chat completion request to the configured OpenAI-compatible endpoint.
     */
    public CompletableFuture<String> sendChatCompletionAsync(AiConfig config, String systemPrompt, String userPrompt) {
        if (config == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("AiConfig cannot be null"));
        }

        URI targetUri;
        try {
            targetUri = resolveEndpointUri(config.getEndpointUrl());
        } catch (Exception e) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Invalid endpoint URI: " + e.getMessage(), e));
        }

        List<ChatMessageDto> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new ChatMessageDto("system", systemPrompt));
        }
        messages.add(new ChatMessageDto("user", userPrompt != null ? userPrompt : ""));

        ChatCompletionRequestDto requestPayload = new ChatCompletionRequestDto(
                config.getModelName(),
                messages,
                config.getTemperature()
        );

        String jsonBody = gson.toJson(requestPayload);

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(targetUri)
                .timeout(Duration.ofSeconds(Math.max(1, config.getTimeoutSeconds())))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "Thorization-BurpExtension/1.3.0");

        String apiKey = config.getApiKey();
        if (apiKey != null && !apiKey.isBlank()) {
            reqBuilder.header("Authorization", "Bearer " + apiKey.trim());
        }

        // Apply custom HTTP headers (e.g. x-opencode-session, anthropic-version, custom routing headers)
        if (config.getCustomHeaders() != null) {
            for (java.util.Map.Entry<String, String> entry : config.getCustomHeaders().entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if (name != null && !name.isBlank() && !name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase("Host")) {
                    reqBuilder.header(name.trim(), value != null ? value.trim() : "");
                }
            }
        }

        // OpenCode Go compatibility requirement:
        // OpenCode Go requires 'x-opencode-session' for session routing and prompt caching (HTTP 400 if missing).
        // If targeting opencode.ai and the user hasn't specified x-opencode-session, auto-inject a stable session ID.
        boolean hasSessionHeader = config.getCustomHeaders() != null &&
                config.getCustomHeaders().keySet().stream().anyMatch(h -> h.equalsIgnoreCase("x-opencode-session"));
        if (!hasSessionHeader && targetUri.toString().contains("opencode.ai")) {
            reqBuilder.header("x-opencode-session", "thorization-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        }

        HttpRequest httpRequest = reqBuilder
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();

        return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    int statusCode = response.statusCode();
                    String responseBody = response.body();

                    if (statusCode >= 200 && statusCode < 300) {
                        try {
                            ChatCompletionResponseDto responseDto = gson.fromJson(responseBody, ChatCompletionResponseDto.class);
                            if (responseDto == null || responseDto.choices == null || responseDto.choices.isEmpty()) {
                                throw new IOException("OpenAI-compatible API returned HTTP " + statusCode + " with no choices.");
                            }
                            ChatMessageDto choiceMessage = responseDto.choices.get(0).message;
                            if (choiceMessage == null || choiceMessage.content == null) {
                                throw new IOException("OpenAI-compatible API returned empty message content.");
                            }
                            return choiceMessage.content.trim();
                        } catch (Exception e) {
                            throw new RuntimeException("Failed to parse OpenAI API JSON response: " + e.getMessage() + "\nRaw response: " + responseBody, e);
                        }
                    } else {
                        String errorDetail = extractErrorMessage(responseBody);
                        throw new RuntimeException(String.format("OpenAI API call failed [HTTP %d]: %s", statusCode, errorDetail));
                    }
                });
    }

    /**
     * Synchronously tests connectivity to the configured endpoint.
     */
    public boolean testConnection(AiConfig config) {
        try {
            ConnectionTestResult result = testConnectionDetailed(config);
            return result.success();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Tests connectivity and returns detailed diagnostic metrics.
     */
    public ConnectionTestResult testConnectionDetailed(AiConfig config) {
        if (config == null) {
            return new ConnectionTestResult(false, 0, "Configuration is null", 0L);
        }
        long startTime = System.currentTimeMillis();
        try {
            int testTimeout = Math.min(Math.max(3, config.getTimeoutSeconds()), 10);
            AiConfig testConfig = new AiConfig(config);
            testConfig.setTimeoutSeconds(testTimeout);

            String reply = sendChatCompletionAsync(testConfig, "You are a health-check responder.", "Respond with: OK")
                    .get(testTimeout, TimeUnit.SECONDS);

            long latency = System.currentTimeMillis() - startTime;
            return new ConnectionTestResult(true, 200, "Successfully connected to model '" + config.getModelName() + "': " + reply, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - startTime;
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            return new ConnectionTestResult(false, 0, "Connection test failed: " + msg, latency);
        }
    }

    private String extractErrorMessage(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "Empty response body from server.";
        }
        try {
            ChatErrorResponseDto errResp = gson.fromJson(responseBody, ChatErrorResponseDto.class);
            if (errResp != null && errResp.error != null && errResp.error.message != null && !errResp.error.message.isBlank()) {
                return errResp.error.message;
            }
        } catch (Exception ignored) {}
        return responseBody.length() > 200 ? responseBody.substring(0, 200) + "..." : responseBody;
    }

    public record ConnectionTestResult(boolean success, int statusCode, String message, long latencyMs) {}

    // Internal DTOs for OpenAI Protocol Serialization
    public static class ChatCompletionRequestDto {
        @SerializedName("model")
        private final String model;
        @SerializedName("messages")
        private final List<ChatMessageDto> messages;
        @SerializedName("temperature")
        private final double temperature;
        @SerializedName("stream")
        private final boolean stream = false;

        public ChatCompletionRequestDto(String model, List<ChatMessageDto> messages, double temperature) {
            this.model = model;
            this.messages = messages;
            this.temperature = temperature;
        }

        public String getModel() { return model; }
        public List<ChatMessageDto> getMessages() { return messages; }
        public double getTemperature() { return temperature; }
    }

    public static class ChatMessageDto {
        @SerializedName("role")
        private final String role;
        @SerializedName("content")
        private final String content;

        public ChatMessageDto(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    public static class ChatCompletionResponseDto {
        @SerializedName("id")
        private String id;
        @SerializedName("model")
        private String model;
        @SerializedName("choices")
        private List<ChoiceDto> choices;

        public String getId() { return id; }
        public String getModel() { return model; }
        public List<ChoiceDto> getChoices() { return choices; }
    }

    public static class ChoiceDto {
        @SerializedName("index")
        private int index;
        @SerializedName("message")
        private ChatMessageDto message;
        @SerializedName("finish_reason")
        private String finishReason;

        public int getIndex() { return index; }
        public ChatMessageDto getMessage() { return message; }
        public String getFinishReason() { return finishReason; }
    }

    public static class ChatErrorResponseDto {
        @SerializedName("error")
        private ErrorDetailDto error;

        public ErrorDetailDto getError() { return error; }
    }

    public static class ErrorDetailDto {
        @SerializedName("message")
        private String message;
        @SerializedName("type")
        private String type;
        @SerializedName("code")
        private String code;

        public String getMessage() { return message; }
        public String getType() { return type; }
        public String getCode() { return code; }
    }
}
