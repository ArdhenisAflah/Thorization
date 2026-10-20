package thorization.bridge.handlers;

import com.sun.net.httpserver.HttpExchange;
import thorization.bridge.AiBridgeController;
import thorization.bridge.dto.AnalyzeResponseDto;
import thorization.bridge.dto.AuditRecordSummaryDto;

import java.io.IOException;
import java.util.List;

/**
 * Handles /api/records and /api/records/{id} requests.
 * Allows AI agents to query past audit records and retrieve detailed vulnerability verdicts.
 * Supports query parameters: ?limit=N and ?vulnerableOnly=true.
 */
public class RecordsHandler extends BaseHttpHandler {

    private final AiBridgeController controller;

    public RecordsHandler(AiBridgeController controller) {
        this.controller = controller;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (handleCors(exchange)) {
            return;
        }

        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
            sendError(exchange, 405, "Method not allowed. Expected GET or POST.");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        String rawQuery = exchange.getRequestURI().getQuery();
        String subPath = path.length() > "/api/records".length()
                ? path.substring("/api/records".length())
                : "";

        if (subPath.startsWith("/")) {
            subPath = subPath.substring(1).trim();
        }

        try {
            if ("GET".equalsIgnoreCase(method)) {
                if (subPath.isEmpty()) {
                    Integer limit = null;
                    Boolean vulnerableOnly = null;
                    Boolean aiAnalyzedOnly = null;

                    if (rawQuery != null && !rawQuery.isEmpty()) {
                        for (String param : rawQuery.split("&")) {
                            String[] pair = param.split("=", 2);
                            if (pair.length == 2) {
                                String k = pair[0].trim().toLowerCase();
                                String v = pair[1].trim();
                                if ("limit".equals(k)) {
                                    try {
                                        limit = Integer.parseInt(v);
                                    } catch (NumberFormatException ignored) {}
                                } else if ("vulnerableonly".equals(k) || "vulnerable_only".equals(k) || "vulnerable".equals(k)) {
                                    vulnerableOnly = "true".equalsIgnoreCase(v) || "1".equals(v);
                                } else if ("aianalyzedonly".equals(k) || "ai_analyzed_only".equals(k) || "aionly".equals(k)) {
                                    aiAnalyzedOnly = "true".equalsIgnoreCase(v) || "1".equals(v);
                                }
                            }
                        }
                    }

                    List<AuditRecordSummaryDto> records = controller.getRecords(limit, vulnerableOnly, aiAnalyzedOnly);
                    sendJsonResponse(exchange, 200, records);
                } else {
                    long recordId = parseRecordId(subPath);
                    AnalyzeResponseDto detail = controller.getRecordDetail(recordId);
                    if (detail == null) {
                        sendError(exchange, 404, "Audit record not found with ID: " + recordId);
                    } else {
                        sendJsonResponse(exchange, 200, detail);
                    }
                }
            } else if ("POST".equalsIgnoreCase(method)) {
                // POST /api/records/{id}/ai-audit or POST /api/records/{id}/ai or POST /api/records/{id}
                long recordId = parseRecordId(subPath);
                AnalyzeResponseDto result = controller.auditRecordWithAi(recordId);
                if (result == null) {
                    sendError(exchange, 404, "Audit record not found with ID: " + recordId);
                } else {
                    sendJsonResponse(exchange, 200, result);
                }
            }
        } catch (NumberFormatException e) {
            sendError(exchange, 400, "Invalid record ID: " + subPath);
        } catch (Exception e) {
            sendError(exchange, 500, "Internal server error: " + e.getMessage());
        }
    }

    private long parseRecordId(String subPath) {
        if (subPath == null || subPath.isEmpty()) {
            throw new NumberFormatException("Empty record ID");
        }
        String idStr = subPath;
        if (idStr.contains("/")) {
            idStr = idStr.substring(0, idStr.indexOf('/'));
        }
        return Long.parseLong(idStr.trim());
    }
}
