package thorization.bridge;

import com.sun.net.httpserver.HttpServer;
import thorization.bridge.handlers.AnalyzeHandler;
import thorization.bridge.handlers.RecordsHandler;
import thorization.bridge.handlers.RoleHandler;
import thorization.bridge.handlers.StatusHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Embedded lightweight HTTP server binding exclusively to 127.0.0.1:1337
 * to provide a REST API bridge for AI agents (Google Antigravity, Claude, scripts).
 */
public class AiBridgeServer {

    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 1337;

    private final String host;
    private final int port;
    private final AiBridgeController controller;

    private HttpServer server;
    private ExecutorService serverExecutor;
    private volatile boolean running = false;

    public AiBridgeServer(AiBridgeController controller) {
        this(controller, DEFAULT_HOST, DEFAULT_PORT);
    }

    public AiBridgeServer(AiBridgeController controller, int port) {
        this(controller, DEFAULT_HOST, port);
    }

    public AiBridgeServer(AiBridgeController controller, String host, int port) {
        this.controller = controller;
        this.host = host != null ? host : DEFAULT_HOST;
        this.port = port > 0 ? port : DEFAULT_PORT;
    }

    /**
     * Starts the embedded HTTP server.
     * Binds strictly to localhost to prevent external access.
     */
    public synchronized void start() throws IOException {
        if (running) {
            return;
        }

        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        serverExecutor = Executors.newFixedThreadPool(4);
        server.setExecutor(serverExecutor);

        // Bind REST endpoints
        server.createContext("/api/status", new StatusHandler(controller));
        server.createContext("/api/roles", new RoleHandler(controller));
        server.createContext("/api/analyze", new AnalyzeHandler(controller));
        server.createContext("/api/records", new RecordsHandler(controller));

        server.start();
        running = true;
    }

    /**
     * Stops the embedded HTTP server and releases thread pool resources.
     */
    public synchronized void stop() {
        if (!running && server == null) {
            return;
        }

        running = false;

        if (server != null) {
            try {
                server.stop(0);
            } catch (Exception ignored) {
            }
            server = null;
        }

        if (serverExecutor != null && !serverExecutor.isShutdown()) {
            serverExecutor.shutdownNow();
            serverExecutor = null;
        }

        if (controller != null) {
            controller.shutdown();
        }
    }

    public boolean isRunning() {
        return running;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getBaseUrl() {
        return "http://" + host + ":" + port + "/api/";
    }

    public AiBridgeController getController() {
        return controller;
    }
}
