package thorization.ai;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;

import java.util.List;
import java.lang.reflect.Proxy;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class AiAuditContextBuilderTest {
    @Test void preservesSelectionOrderPairingAndRoleEvidence() {
        AuditRecord first = record(7, "GET /api/users/42");
        AuditRecord second = record(9, "GET /api/users/42");
        AuthorizationCheckResult role = new AuthorizationCheckResult("r1", "Analyst", Verdict.ENFORCED,
                403, 20, 0.1, 5, "blocked", requestMock(), responseMock());
        first.addRoleResult("r1", role);

        JsonObject json = AiAuditContextBuilder.build(List.of(first, second), List.of(new UserRole("Analyst", "", "")));
        assertEquals("1.0", json.get("schema_version").getAsString());
        assertEquals("7", json.getAsJsonArray("selected_record_ids_in_order").get(0).getAsString());
        assertEquals("9", json.getAsJsonArray("selected_record_ids_in_order").get(1).getAsString());
        JsonObject firstEndpoint = json.getAsJsonArray("endpoints").get(0).getAsJsonObject();
        assertEquals("7", firstEndpoint.get("record_id").getAsString());
        JsonObject observation = firstEndpoint.getAsJsonArray("role_observations").get(0).getAsJsonObject();
        assertEquals("ENFORCED", observation.get("deterministic_verdict").getAsString());
        assertEquals(403, observation.getAsJsonObject("response").get("status").getAsInt());
        assertTrue(observation.getAsJsonObject("request").toString().contains("[REDACTED]"));
        assertFalse(observation.getAsJsonObject("request").toString().contains("abcdefghijklmnopqrstuvwxyz"));
        assertEquals(1, json.getAsJsonArray("relationships").size());
        assertEquals("inferred_from_selected_evidence", json.getAsJsonArray("relationships").get(0).getAsJsonObject().get("basis").getAsString());
    }

    @Test void boundsAndMarksOversizedBodyEvidence() {
        AuditRecord record = record(11, "POST /api/items");
        record.setSerializedOriginalRequest("POST /api/items HTTP/1.1\r\nHost: example.test\r\n\r\n" + "x".repeat(5000));
        JsonObject json = AiAuditContextBuilder.build(List.of(record), List.of());
        String context = json.toString();
        assertTrue(context.contains("omitted"));
        assertTrue(context.length() < 6000);
    }

    @Test void contextIsValidJsonAndEmptySelectionIsExplicit() {
        JsonObject context = AiAuditContextBuilder.build(List.of(), List.of());
        assertEquals("0", JsonParser.parseString(context.toString()).getAsJsonObject().get("explicit_selection_count").getAsString());
        assertEquals(0, context.getAsJsonArray("endpoints").size());
    }

    private static AuditRecord record(long id, String request) {
        AuditRecord record = new AuditRecord(id, null);
        record.setMethod(request.substring(0, request.indexOf(' ')));
        record.setPath(request.substring(request.indexOf(' ') + 1));
        record.setUrl("https://example.test" + record.getPath());
        record.setSerializedOriginalRequest(request + " HTTP/1.1\r\nHost: example.test\r\n\r\n");
        return record;
    }

    private static HttpRequest requestMock() {
        HttpHeader auth = (HttpHeader) Proxy.newProxyInstance(HttpHeader.class.getClassLoader(), new Class<?>[]{HttpHeader.class},
                (p, m, a) -> "name".equals(m.getName()) ? "Authorization" : "value".equals(m.getName()) ? "Bearer abcdefghijklmnopqrstuvwxyz" : null);
        return (HttpRequest) Proxy.newProxyInstance(HttpRequest.class.getClassLoader(), new Class<?>[]{HttpRequest.class},
                (p, m, a) -> switch (m.getName()) {
                    case "headers" -> List.of(auth);
                    case "hasParameters" -> false;
                    case "bodyToString" -> "";
                    default -> null;
                });
    }

    private static HttpResponse responseMock() {
        HttpHeader contentType = (HttpHeader) Proxy.newProxyInstance(HttpHeader.class.getClassLoader(), new Class<?>[]{HttpHeader.class},
                (p, m, a) -> "value".equals(m.getName()) ? "application/json" : null);
        return (HttpResponse) Proxy.newProxyInstance(HttpResponse.class.getClassLoader(), new Class<?>[]{HttpResponse.class},
                (p, m, a) -> switch (m.getName()) {
                    case "header" -> contentType;
                    case "bodyToString" -> "{\"error\":\"denied\"}";
                    default -> null;
                });
    }
}
