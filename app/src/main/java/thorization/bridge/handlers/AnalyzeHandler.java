package thorization.bridge.handlers;

import com.sun.net.httpserver.HttpExchange;
import thorization.bridge.AiBridgeController;
import thorization.bridge.dto.AnalyzeRequestDto;
import thorization.bridge.dto.AnalyzeResponseDto;

import java.io.IOException;

/**
 * Handles POST /api/analyze requests.
 * Evaluates HTTP requests against extension rules, sends original request,
 * and replays against active roles to detect authorization vulnerabilities.
 */
public class AnalyzeHandler extends BaseHttpHandler {

    private final AiBridgeController controller;

    public AnalyzeHandler(AiBridgeController controller) {
        this.controller = controller;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) {
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendError(exchange, 405, "Method not allowed. Expected POST.");
            return;
        }

        try {
            String body = readRequestBody(exchange);
            if (body == null || body.trim().isEmpty()) {
                sendError(exchange, 400, "Request body is empty.");
                return;
            }

            AnalyzeRequestDto requestDto = gson.fromJson(body, AnalyzeRequestDto.class);
            if (requestDto == null) {
                sendError(exchange, 400, "Invalid JSON payload.");
                return;
            }

            AnalyzeResponseDto response = controller.analyzeRequest(requestDto);
            sendJsonResponse(exchange, 200, response);

        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }
}
