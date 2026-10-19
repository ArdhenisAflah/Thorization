package thorization;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import thorization.bridge.AiBridgeController;
import thorization.bridge.AiBridgeServer;
import thorization.core.EngineController;
import thorization.core.HttpTrafficHandler;
import thorization.ui.MainTabbedPane;

/**
 * Burp Suite Extension Entry Point for Thorization.
 */
public class ThorizationExtension implements BurpExtension {

    private EngineController engineController;
    private AiBridgeServer aiBridgeServer;

    @Override
    public void initialize(MontoyaApi api) {
        // Set extension name in Burp Suite Extender tab
        api.extension().setName("Thorization");

        // Initialize master engine controller
        this.engineController = new EngineController(api);

        // Register HTTP & Proxy Traffic Interceptors (Proxy Drop Control / Repeater)
        HttpTrafficHandler trafficHandler = new HttpTrafficHandler(engineController);
        api.http().registerHttpHandler(trafficHandler);
        if (api.proxy() != null) {
            api.proxy().registerRequestHandler(trafficHandler);
        }

        // Initialize and start embedded AI Bridge Server (127.0.0.1:1337)
        AiBridgeController bridgeController = new AiBridgeController(engineController);
        this.aiBridgeServer = new AiBridgeServer(bridgeController);
        try {
            aiBridgeServer.start();
            api.logging().logToOutput("[Thorization] AI Agent Bridge listening at " + aiBridgeServer.getBaseUrl());
        } catch (Exception e) {
            api.logging().logToError("[Thorization] Failed to start AI Agent Bridge: " + e.getMessage());
        }

        // Register custom Suite Tab in Burp Suite
        MainTabbedPane mainTabbedPane = new MainTabbedPane(api, engineController);
        api.userInterface().registerSuiteTab("Thorization", mainTabbedPane);

        // Register shutdown hook for clean threadpool teardown
        api.extension().registerUnloadingHandler(() -> {
            if (aiBridgeServer != null) {
                aiBridgeServer.stop();
            }
            if (engineController != null) {
                engineController.shutdown();
            }
            api.logging().logToOutput("Thorization extension unloaded successfully.");
        });

        // Log startup banner
        api.logging().logToOutput("=================================================");
        api.logging().logToOutput("  Thorization v1.3.0 Loaded Successfully!");
        api.logging().logToOutput("  Multi-Role Authorization & BOLA Testing Tool   ");
        api.logging().logToOutput("  AI Agent Bridge: http://127.0.0.1:1337/api/    ");
        api.logging().logToOutput("=================================================");
    }
}

