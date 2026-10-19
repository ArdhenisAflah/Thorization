package thorization.bridge.handlers;

import com.sun.net.httpserver.HttpExchange;
import thorization.bridge.AiBridgeController;
import thorization.bridge.dto.StatusResponseDto;

import java.io.IOException;

/**
 * Handles GET /api/status requests.
 * Returns health status, extension version, and active filtering rules.
 */
public class StatusHandler extends BaseHttpHandler {

    private final AiBridgeController controller;

    public StatusHandler(AiBridgeController controller) {
        this.controller = controller;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) {
            return;
        }

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed. Expected GET.");
            return;
        }

        try {
            StatusResponseDto status = controller.getStatus();
            sendJsonResponse(exchange, 200, status);
        } catch (Exception e) {
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
}
