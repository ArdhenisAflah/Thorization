package thorization.ai;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration model for OpenAI-compatible AI Semantic Analysis endpoints.
 */
public class AiConfig {

    public static final String DEFAULT_OLLAMA_ENDPOINT = "http://localhost:11434/v1/chat/completions";
    public static final String DEFAULT_OPENAI_ENDPOINT = "https://api.openai.com/v1/chat/completions";
    public static final String DEFAULT_DEEPSEEK_ENDPOINT = "https://api.deepseek.com/chat/completions";
    public static final String DEFAULT_QWEN_ENDPOINT = "https://dashscope-intl.aliyuncs.com/compatible-mode/v1/chat/completions";
    public static final String DEFAULT_GEMINI_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
    public static final String DEFAULT_OPENCODE_ENDPOINT = "https://opencode.ai/zen/go/v1/chat/completions";

    public static final String DEFAULT_MODEL = "gpt-4o-mini";
    public static final String DEFAULT_OPENCODE_MODEL = "glm-5.3-flash";
    public static final int DEFAULT_TIMEOUT_SECONDS = 30;
    public static final double DEFAULT_TEMPERATURE = 0.1;

    private String endpointUrl;
    private String apiKey;
    private String modelName;
    private int timeoutSeconds;
    private double temperature;
    private boolean autoAnalyzeEnabled;
    private String rawHeaders;
    private Map<String, String> customHeaders;

    public AiConfig() {
        this.endpointUrl = DEFAULT_OLLAMA_ENDPOINT;
        this.apiKey = "";
        this.modelName = DEFAULT_MODEL;
        this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
        this.temperature = DEFAULT_TEMPERATURE;
        this.autoAnalyzeEnabled = false;
        this.rawHeaders = "";
        this.customHeaders = new LinkedHashMap<>();
    }

    /**
     * Copy constructor creating an isolated snapshot for background threads.
     */
    public AiConfig(AiConfig other) {
        if (other != null) {
            synchronized (other) {
                this.endpointUrl = other.endpointUrl;
                this.apiKey = other.apiKey;
                this.modelName = other.modelName;
                this.timeoutSeconds = other.timeoutSeconds;
                this.temperature = other.temperature;
                this.autoAnalyzeEnabled = other.autoAnalyzeEnabled;
                this.rawHeaders = other.rawHeaders != null ? other.rawHeaders : "";
                this.customHeaders = other.customHeaders != null ? new LinkedHashMap<>(other.customHeaders) : new LinkedHashMap<>();
            }
        } else {
            this.endpointUrl = DEFAULT_OLLAMA_ENDPOINT;
            this.apiKey = "";
            this.modelName = DEFAULT_MODEL;
            this.timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;
            this.temperature = DEFAULT_TEMPERATURE;
            this.autoAnalyzeEnabled = false;
            this.rawHeaders = "";
            this.customHeaders = new LinkedHashMap<>();
        }
    }

    public static AiConfig forOllama(String model) {
        AiConfig config = new AiConfig();
        config.setEndpointUrl(DEFAULT_OLLAMA_ENDPOINT);
        config.setApiKey("");
        config.setModelName(model != null && !model.isBlank() ? model : "llama3");
        config.setTimeoutSeconds(60);
        return config;
    }

    public static AiConfig forOpenAi(String apiKey, String model) {
        AiConfig config = new AiConfig();
        config.setEndpointUrl(DEFAULT_OPENAI_ENDPOINT);
        config.setApiKey(apiKey != null ? apiKey : "");
        config.setModelName(model != null && !model.isBlank() ? model : "gpt-4o-mini");
        return config;
    }

    public static AiConfig forDeepSeek(String apiKey, String model) {
        AiConfig config = new AiConfig();
        config.setEndpointUrl(DEFAULT_DEEPSEEK_ENDPOINT);
        config.setApiKey(apiKey != null ? apiKey : "");
        config.setModelName(model != null && !model.isBlank() ? model : "deepseek-chat");
        return config;
    }

    public static AiConfig forGemini(String apiKey, String model) {
        AiConfig config = new AiConfig();
        config.setEndpointUrl(DEFAULT_GEMINI_ENDPOINT);
        config.setApiKey(apiKey != null ? apiKey : "");
        config.setModelName(model != null && !model.isBlank() ? model : "gemini-1.5-flash");
        return config;
    }

    public static AiConfig forOpenCode(String apiKey, String model, String sessionId) {
        AiConfig config = new AiConfig();
        config.setEndpointUrl(DEFAULT_OPENCODE_ENDPOINT);
        config.setApiKey(apiKey != null ? apiKey : "");
        config.setModelName(model != null && !model.isBlank() ? model : DEFAULT_OPENCODE_MODEL);
        config.setTimeoutSeconds(45);
        String session = sessionId != null && !sessionId.isBlank() ? sessionId : "thorization-session";
        config.setRawHeaders("x-opencode-session: " + session);
        return config;
    }

    public synchronized List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (endpointUrl == null || endpointUrl.isBlank()) {
            errors.add("Endpoint URL must not be empty.");
        } else {
            try {
                URI uri = URI.create(endpointUrl.trim());
                if (uri.getScheme() == null || (!uri.getScheme().equalsIgnoreCase("http") && !uri.getScheme().equalsIgnoreCase("https"))) {
                    errors.add("Endpoint URL must use http:// or https:// protocol.");
                }
            } catch (Exception e) {
                errors.add("Endpoint URL is invalid: " + e.getMessage());
            }
        }

        if (modelName == null || modelName.isBlank()) {
            errors.add("Model name must not be empty.");
        }

        if (timeoutSeconds <= 0 || timeoutSeconds > 600) {
            errors.add("Timeout must be between 1 and 600 seconds.");
        }

        if (temperature < 0.0 || temperature > 2.0) {
            errors.add("Temperature must be between 0.0 and 2.0.");
        }

        return errors;
    }

    public boolean isValid() {
        return validate().isEmpty();
    }

    public synchronized String getEndpointUrl() {
        return endpointUrl;
    }

    public synchronized void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

    public synchronized String getApiKey() {
        return apiKey;
    }

    public synchronized void setApiKey(String apiKey) {
        this.apiKey = apiKey != null ? apiKey : "";
    }

    public synchronized String getModelName() {
        return modelName;
    }

    public synchronized void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public synchronized int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public synchronized void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public synchronized double getTemperature() {
        return temperature;
    }

    public synchronized void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public synchronized boolean isAutoAnalyzeEnabled() {
        return autoAnalyzeEnabled;
    }

    public synchronized void setAutoAnalyzeEnabled(boolean autoAnalyzeEnabled) {
        this.autoAnalyzeEnabled = autoAnalyzeEnabled;
    }

    public synchronized String getRawHeaders() {
        return rawHeaders;
    }

    public synchronized void setRawHeaders(String rawHeaders) {
        this.rawHeaders = rawHeaders != null ? rawHeaders : "";
        parseHeaders();
    }

    public synchronized Map<String, String> getCustomHeaders() {
        return Collections.unmodifiableMap(customHeaders);
    }

    public synchronized void setCustomHeaders(Map<String, String> customHeaders) {
        this.customHeaders = customHeaders != null ? new LinkedHashMap<>(customHeaders) : new LinkedHashMap<>();
        buildRawHeaders();
    }

    public synchronized void addCustomHeader(String name, String value) {
        if (name != null && !name.isBlank()) {
            this.customHeaders.put(name.trim(), value != null ? value.trim() : "");
            buildRawHeaders();
        }
    }

    private void parseHeaders() {
        this.customHeaders = new LinkedHashMap<>();
        if (rawHeaders == null || rawHeaders.isBlank()) {
            return;
        }
        String[] lines = rawHeaders.split("\\r?\\n");
        for (String line : lines) {
            int colonIdx = line.indexOf(':');
            if (colonIdx > 0) {
                String name = line.substring(0, colonIdx).trim();
                String value = line.substring(colonIdx + 1).trim();
                if (!name.isEmpty()) {
                    this.customHeaders.put(name, value);
                }
            }
        }
    }

    private void buildRawHeaders() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : customHeaders.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }
        this.rawHeaders = sb.toString().trim();
    }
}
