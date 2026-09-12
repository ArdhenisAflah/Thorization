package thorization;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import thorization.core.EngineController;
import thorization.core.HttpTrafficHandler;
import thorization.ui.MainTabbedPane;

/**
 * Burp Suite Extension Entry Point for Thorization.
 */
public class ThorizationExtension implements BurpExtension {

    private EngineController engineController;

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

        // Register custom Suite Tab in Burp Suite
        MainTabbedPane mainTabbedPane = new MainTabbedPane(api, engineController);
        api.userInterface().registerSuiteTab("Thorization", mainTabbedPane);

        // Register shutdown hook for clean threadpool teardown
        api.extension().registerUnloadingHandler(() -> {
            if (engineController != null) {
                engineController.shutdown();
            }
            api.logging().logToOutput("Thorization extension unloaded successfully.");
        });

        // Log startup banner
        api.logging().logToOutput("=================================================");
        api.logging().logToOutput("  Thorization v1.0.0 Loaded Successfully!");
        api.logging().logToOutput("  Multi-Role Authorization & BOLA Testing Tool   ");
        api.logging().logToOutput("=================================================");
    }
}

