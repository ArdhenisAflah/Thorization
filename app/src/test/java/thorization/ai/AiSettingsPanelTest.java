package thorization.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thorization.core.EngineController;
import thorization.ui.AiSettingsPanel;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AiSettingsPanelTest {

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
                String resp = """
                        {
                          "choices": [
                            { "message": { "role": "assistant", "content": "OK" } }
                          ]
                        }
                        """;
                byte[] body = resp.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        });
        server.start();

        controller = new EngineController(null);
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
    void testFormFieldsInitializationAndPresets() {
        AiSettingsPanel panel = new AiSettingsPanel(controller);

        assertEquals(AiConfig.DEFAULT_OLLAMA_ENDPOINT, panel.getEndpointUrlField().getText());
        assertEquals(AiConfig.DEFAULT_MODEL, panel.getModelNameField().getText());
        assertEquals(AiConfig.DEFAULT_TIMEOUT_SECONDS, ((Number) panel.getTimeoutSpinner().getValue()).intValue());
        assertFalse(panel.getAutoAnalyzeCheckBox().isSelected());

        // Select OpenAI preset (index 1)
        panel.getPresetCombo().setSelectedIndex(1);
        assertEquals(AiConfig.DEFAULT_OPENAI_ENDPOINT, panel.getEndpointUrlField().getText());
        assertEquals("gpt-4o-mini", panel.getModelNameField().getText());

        // Select DeepSeek preset (index 2)
        panel.getPresetCombo().setSelectedIndex(2);
        assertEquals(AiConfig.DEFAULT_DEEPSEEK_ENDPOINT, panel.getEndpointUrlField().getText());
        assertEquals("deepseek-chat", panel.getModelNameField().getText());

        // Select Gemini preset (index 3)
        panel.getPresetCombo().setSelectedIndex(3);
        assertEquals(AiConfig.DEFAULT_GEMINI_ENDPOINT, panel.getEndpointUrlField().getText());
        assertEquals("gemini-1.5-flash", panel.getModelNameField().getText());

        // Select OpenCode Go preset (index 5)
        panel.getPresetCombo().setSelectedIndex(5);
        assertEquals(AiConfig.DEFAULT_OPENCODE_ENDPOINT, panel.getEndpointUrlField().getText());
        assertEquals(AiConfig.DEFAULT_OPENCODE_MODEL, panel.getModelNameField().getText());
        assertTrue(panel.getCustomHeadersArea().getText().contains("x-opencode-session:"));
    }

    @Test
    void testSaveConfigValidation() {
        AiSettingsPanel panel = new AiSettingsPanel(controller);

        // Invalid URL
        panel.getEndpointUrlField().setText("");
        panel.saveConfig();
        assertTrue(panel.getTestStatusLabel().getText().contains("Validation Error"));

        // Valid settings save
        panel.getEndpointUrlField().setText("http://127.0.0.1:11434/v1/chat/completions");
        panel.getApiKeyField().setText("test-key");
        panel.getModelNameField().setText("llama3.2");
        panel.getCustomHeadersArea().setText("x-opencode-session: my-test-session\nX-Custom-Header: value123");
        panel.getTimeoutSpinner().setValue(45);
        panel.getAutoAnalyzeCheckBox().setSelected(true);

        panel.saveConfig();
        assertTrue(panel.getTestStatusLabel().getText().contains("saved successfully"));

        AiConfig saved = controller.getAiConfig();
        assertEquals("http://127.0.0.1:11434/v1/chat/completions", saved.getEndpointUrl());
        assertEquals("test-key", saved.getApiKey());
        assertEquals("llama3.2", saved.getModelName());
        assertEquals(45, saved.getTimeoutSeconds());
        assertTrue(saved.isAutoAnalyzeEnabled());
        assertEquals("my-test-session", saved.getCustomHeaders().get("x-opencode-session"));
        assertEquals("value123", saved.getCustomHeaders().get("X-Custom-Header"));
    }

    @Test
    void testClearCacheButton() {
        AiSettingsPanel panel = new AiSettingsPanel(controller);
        controller.getAiSemanticEngine().getCache().put("hash1", AiEvaluationResult.pending("m"));
        panel.updateCacheStats();
        assertTrue(panel.getCacheStatsLabel().getText().contains("1 entries"));

        panel.getClearCacheBtn().doClick();
        assertEquals(0, controller.getAiSemanticEngine().getCache().size());
        assertTrue(panel.getCacheStatsLabel().getText().contains("0 entries"));
    }

    @Test
    void testConnectionButtonSuccess() throws InterruptedException {
        AiSettingsPanel panel = new AiSettingsPanel(controller);
        panel.getEndpointUrlField().setText("http://127.0.0.1:" + port);
        panel.getModelNameField().setText("test-model");

        panel.getTestConnectionBtn().doClick();

        // Wait up to 3 seconds for async test connection to update status label
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            String text = panel.getTestStatusLabel().getText();
            if (text.contains("Success")) {
                break;
            }
            Thread.sleep(50);
        }

        assertTrue(panel.getTestStatusLabel().getText().contains("Success"));
    }
}
