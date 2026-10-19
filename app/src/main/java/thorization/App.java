package thorization;

import thorization.bridge.AiBridgeController;
import thorization.bridge.AiBridgeServer;
import thorization.core.EngineController;
import thorization.ui.MainTabbedPane;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.GraphicsEnvironment;

/**
 * Standalone runner for development, offline UI testing, and headless AI Bridge execution.
 * Supports running in GUI preview mode or headless server mode (--server or -s).
 */
public class App {

    public static void main(String[] args) {
        boolean headless = GraphicsEnvironment.isHeadless();
        int port = AiBridgeServer.DEFAULT_PORT;

        for (int i = 0; i < args.length; i++) {
            if ("--headless".equalsIgnoreCase(args[i]) || "--server".equalsIgnoreCase(args[i]) || "-s".equalsIgnoreCase(args[i])) {
                headless = true;
            } else if ("--port".equalsIgnoreCase(args[i]) || "-p".equalsIgnoreCase(args[i])) {
                if (i + 1 < args.length) {
                    try {
                        port = Integer.parseInt(args[++i]);
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        EngineController controller = new EngineController(null);
        controller.setEnabled(true);

        AiBridgeController bridgeController = new AiBridgeController(controller);
        AiBridgeServer server = new AiBridgeServer(bridgeController, port);

        try {
            server.start();
            System.out.println("=================================================");
            System.out.println("  Thorization AI Agent Bridge (Standalone Mode)");
            System.out.println("  Listening at: " + server.getBaseUrl());
            System.out.println("  Health check: " + server.getBaseUrl() + "status");
            System.out.println("=================================================");
        } catch (Exception e) {
            System.err.println("[Thorization] Failed to start AI Bridge: " + e.getMessage());
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("[Thorization] Stopping AI Bridge and Engine...");
            server.stop();
            controller.shutdown();
        }));

        if (!headless) {
            SwingUtilities.invokeLater(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}

                JFrame frame = new JFrame("Thorization — Multi-Role Authorization & BOLA Testing Tool (Standalone Preview)");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setSize(1100, 750);
                frame.setLocationRelativeTo(null);

                MainTabbedPane mainTabbedPane = new MainTabbedPane(null, controller);
                frame.setContentPane(mainTabbedPane);
                frame.setVisible(true);
            });
        } else {
            System.out.println("[Thorization] Running in headless mode. Press Ctrl+C to terminate.");
            try {
                Thread.currentThread().join();
            } catch (InterruptedException ignored) {}
        }
    }
}
