package thorization.bridge.handlers;

import com.sun.net.httpserver.HttpExchange;
import thorization.bridge.AiBridgeController;
import thorization.bridge.dto.RoleDto;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Handles /api/roles and /api/roles/{id} requests.
 * Supports GET (list or single), POST (create), PUT (update), and DELETE (remove).
 */
public class RoleHandler extends BaseHttpHandler {

    private final AiBridgeController controller;

    public RoleHandler(AiBridgeController controller) {
        this.controller = controller;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) {
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String subPath = path.length() > "/api/roles".length()
                ? path.substring("/api/roles".length())
                : "";

        if (subPath.startsWith("/")) {
            subPath = subPath.substring(1).trim();
        }

        String method = exchange.getRequestMethod().toUpperCase();

        try {
            if (subPath.isEmpty()) {
                handleCollection(exchange, method);
            } else {
                handleItem(exchange, method, subPath);
            }
        } catch (IllegalArgumentException e) {
            sendError(exchange, 400, e.getMessage());
        } catch (NoSuchElementException e) {
            sendError(exchange, 404, e.getMessage());
        } catch (Exception e) {
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    private void handleCollection(HttpExchange exchange, String method) throws IOException {
        switch (method) {
            case "GET":
                List<RoleDto> roles = controller.getRoles();
                sendJsonResponse(exchange, 200, roles);
                break;

            case "POST":
                String body = readRequestBody(exchange);
                if (body == null || body.trim().isEmpty()) {
                    sendError(exchange, 400, "Request body is empty.");
                    return;
                }
                RoleDto inputDto = gson.fromJson(body, RoleDto.class);
                if (inputDto == null || inputDto.getName() == null || inputDto.getName().trim().isEmpty()) {
                    sendError(exchange, 400, "Role name is required.");
                    return;
                }
                RoleDto created = controller.addRole(inputDto);
                sendJsonResponse(exchange, 201, created);
                break;

            default:
                sendError(exchange, 405, "Method not allowed on /api/roles. Expected GET or POST.");
                break;
        }
    }

    private void handleItem(HttpExchange exchange, String method, String roleId) throws IOException {
        switch (method) {
            case "GET":
                RoleDto role = controller.getRole(roleId);
                if (role == null) {
                    sendError(exchange, 404, "Role not found with ID: " + roleId);
                } else {
                    sendJsonResponse(exchange, 200, role);
                }
                break;

            case "PUT":
                String body = readRequestBody(exchange);
                if (body == null || body.trim().isEmpty()) {
                    sendError(exchange, 400, "Request body is empty.");
                    return;
                }
                RoleDto updateDto = gson.fromJson(body, RoleDto.class);
                RoleDto updated = controller.updateRole(roleId, updateDto);
                sendJsonResponse(exchange, 200, updated);
                break;

            case "DELETE":
                boolean deleted = controller.deleteRole(roleId);
                if (deleted) {
                    Map<String, Object> resp = new LinkedHashMap<>();
                    resp.put("success", true);
                    resp.put("message", "Role deleted successfully.");
                    resp.put("roleId", roleId);
                    sendJsonResponse(exchange, 200, resp);
                } else {
                    sendError(exchange, 404, "Role not found with ID: " + roleId);
                }
                break;

            default:
                sendError(exchange, 405, "Method not allowed on /api/roles/{id}. Expected GET, PUT, or DELETE.");
                break;
        }
    }
}
