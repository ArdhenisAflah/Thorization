package thorization.substitution;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.core.ByteArray;
import burp.api.montoya.http.Http;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thorization.core.DetectionEngine;
import thorization.core.EngineController;
import thorization.core.ReplayEngine;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.OriginalAction;
import thorization.model.UserRole;
import thorization.model.substitution.ParameterSubstitutionRule;
import thorization.model.substitution.SubstitutionDataType;
import thorization.model.substitution.SubstitutionTargetType;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ReplayEngine & EngineController Parameter Substitution Integration Tests")
class ReplayEngineSubstitutionTest {

    @Test
    @DisplayName("ReplayEngine applies REST path and query substitutions before sendRequest for roles")
    void testReplayEngineAppliesSubstitutionsForRole() {
        AtomicReference<HttpRequest> sentRequest = new AtomicReference<>();

        MontoyaApi mockApi = createMockMontoyaApi((req) -> {
            sentRequest.set(req);
            return createMockResponse(200, "{\"status\": \"ok\"}");
        });

        DetectionEngine detectionEngine = new DetectionEngine();
        ReplayEngine replayEngine = new ReplayEngine(mockApi, detectionEngine);

        HttpRequest originalReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/api/users/100/profile?sort=asc",
                "GET",
                "",
                Map.of("Host", "api.target.com")
        );
        HttpResponse originalResp = createMockResponse(200, "{\"status\": \"ok\"}");
        AuditRecord record = new AuditRecord(1L, createMockHttpRequestResponse(originalReq, originalResp), OriginalAction.PASSED, "Proxy");

        UserRole roleB = new UserRole("User B", "X-Role: user-b", "");
        roleB.setId("role-b-id");

        ParameterSubstitutionRule pathRule = new ParameterSubstitutionRule("Path Rule", SubstitutionTargetType.REST_PATH, "id");
        pathRule.setHostPattern("api.target.com");
        pathRule.setPathPattern("/api/users/{id}/profile");
        pathRule.setRoleValue("role-b-id", "200");

        ParameterSubstitutionRule queryRule = new ParameterSubstitutionRule("Query Rule", SubstitutionTargetType.URL_QUERY, "sort");
        queryRule.setHostPattern("api.target.com");
        queryRule.setPathPattern("/api/users/{id}/profile");
        queryRule.setDefaultValue("desc");

        replayEngine.executeAudit(record, List.of(roleB), false, List.of(pathRule, queryRule));

        AuthorizationCheckResult roleResult = record.getRoleResults().get("role-b-id");
        assertNotNull(roleResult, "Role result should be recorded");
        assertNotNull(roleResult.getRequest(), "Replayed request should be recorded");

        assertEquals("/api/users/200/profile", roleResult.getRequest().pathWithoutQuery());
        assertEquals("sort=desc", roleResult.getRequest().query());

        // Verify that the request actually dispatched over Montoya HTTP was substituted
        assertNotNull(sentRequest.get(), "HTTP request should have been dispatched");
        assertEquals("/api/users/200/profile", sentRequest.get().pathWithoutQuery());
        assertEquals("sort=desc", sentRequest.get().query());
    }

    @Test
    @DisplayName("ReplayEngine applies substitutions for unauthenticated test branch")
    void testReplayEngineAppliesSubstitutionsForUnauthenticated() {
        AtomicReference<HttpRequest> sentRequest = new AtomicReference<>();

        MontoyaApi mockApi = createMockMontoyaApi((req) -> {
            sentRequest.set(req);
            return createMockResponse(401, "{\"error\": \"unauthorized\"}");
        });

        DetectionEngine detectionEngine = new DetectionEngine();
        ReplayEngine replayEngine = new ReplayEngine(mockApi, detectionEngine);

        HttpRequest originalReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/api/users/100/orders",
                "GET",
                "",
                Map.of("Authorization", "Bearer token123", "Host", "api.target.com")
        );
        HttpResponse originalResp = createMockResponse(200, "{\"orders\": []}");
        AuditRecord record = new AuditRecord(1L, createMockHttpRequestResponse(originalReq, originalResp), OriginalAction.PASSED, "Proxy");

        ParameterSubstitutionRule rule = new ParameterSubstitutionRule("Unauth Rule", SubstitutionTargetType.REST_PATH, "id");
        rule.setHostPattern("api.target.com");
        rule.setPathPattern("/api/users/{id}/orders");
        rule.setRoleValue("unauthenticated", "999");

        replayEngine.executeAudit(record, Collections.emptyList(), true, List.of(rule));

        AuthorizationCheckResult unauthResult = record.getUnauthenticatedResult();
        assertNotNull(unauthResult, "Unauthenticated result should be present");
        assertNotNull(unauthResult.getRequest());
        assertEquals("/api/users/999/orders", unauthResult.getRequest().pathWithoutQuery());
        assertFalse(unauthResult.getRequest().hasHeader("Authorization"));
    }

    @Test
    @DisplayName("ReplayEngine applies JSON body substitution preserving numeric integer type")
    void testReplayEngineAppliesJsonBodySubstitution() {
        AtomicReference<HttpRequest> sentRequest = new AtomicReference<>();

        MontoyaApi mockApi = createMockMontoyaApi((req) -> {
            sentRequest.set(req);
            return createMockResponse(200, "{\"success\": true}");
        });

        DetectionEngine detectionEngine = new DetectionEngine();
        ReplayEngine replayEngine = new ReplayEngine(mockApi, detectionEngine);

        String jsonBody = "{\"user\": {\"id\": 100, \"name\": \"victim\"}}";
        HttpRequest originalReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/api/update",
                "POST",
                jsonBody,
                Map.of("Content-Type", "application/json", "Host", "api.target.com")
        );
        HttpResponse originalResp = createMockResponse(200, "{\"success\": true}");
        AuditRecord record = new AuditRecord(1L, createMockHttpRequestResponse(originalReq, originalResp), OriginalAction.PASSED, "Proxy");

        UserRole role = new UserRole("Attacker", "X-Role: attacker", "");
        role.setId("attacker-id");

        ParameterSubstitutionRule rule = new ParameterSubstitutionRule("JSON Rule", SubstitutionTargetType.JSON_BODY, "$.user.id");
        rule.setHostPattern("api.target.com");
        rule.setPathPattern("/api/update");
        rule.setDataType(SubstitutionDataType.AUTO);
        rule.setRoleValue("attacker-id", "200");

        replayEngine.executeAudit(record, List.of(role), false, List.of(rule));

        AuthorizationCheckResult res = record.getRoleResults().get("attacker-id");
        assertNotNull(res);
        assertEquals("{\"user\":{\"id\":200,\"name\":\"victim\"}}", res.getRequest().bodyToString());
    }

    @Test
    @DisplayName("EngineController manages substitution rules and applies them during synchronous audit task")
    void testEngineControllerIntegration() {
        AtomicReference<HttpRequest> sentRequest = new AtomicReference<>();

        MontoyaApi mockApi = createMockMontoyaApi((req) -> {
            sentRequest.set(req);
            return createMockResponse(200, "{\"ok\": true}");
        });

        EngineController controller = new EngineController(mockApi);
        controller.setEnabled(true);

        UserRole role = new UserRole("Role A", "Authorization: Bearer roleA", "");
        role.setId("role-a");
        controller.setRoles(List.of(role));

        ParameterSubstitutionRule rule = new ParameterSubstitutionRule("Path Sub", SubstitutionTargetType.REST_PATH, "id");
        rule.setHostPattern("api.target.com");
        rule.setPathPattern("/api/tenants/{id}/data");
        rule.setRoleValue("role-a", "tenant-555");
        controller.addSubstitutionRule(rule);

        assertEquals(1, controller.getSubstitutionRules().size());

        HttpRequest originalReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/api/tenants/tenant-111/data",
                "GET",
                "",
                Map.of("Host", "api.target.com")
        );
        HttpResponse originalResp = createMockResponse(200, "{\"ok\": true}");

        controller.submitAuditTask(createMockHttpRequestResponse(originalReq, originalResp), OriginalAction.PASSED, true, "TestTool");

        assertEquals(1, controller.getRecords().size());
        AuditRecord record = controller.getRecords().get(0);
        AuthorizationCheckResult roleResult = record.getRoleResults().get("role-a");
        assertNotNull(roleResult);
        assertEquals("/api/tenants/tenant-555/data", roleResult.getRequest().pathWithoutQuery());

        controller.shutdown();
    }

    // =========================================================================
    // Mock Utilities for Integration Testing
    // =========================================================================

    interface RequestHandler {
        HttpResponse handle(HttpRequest request);
    }

    private static MontoyaApi createMockMontoyaApi(RequestHandler handler) {
        Http http = (Http) Proxy.newProxyInstance(
                Http.class.getClassLoader(),
                new Class<?>[]{Http.class},
                (proxy, method, args) -> {
                    if ("sendRequest".equals(method.getName()) && args != null && args.length >= 1) {
                        HttpRequest req = (HttpRequest) args[0];
                        HttpResponse resp = handler.handle(req);
                        return createMockHttpRequestResponse(req, resp);
                    }
                    return null;
                }
        );

        return (MontoyaApi) Proxy.newProxyInstance(
                MontoyaApi.class.getClassLoader(),
                new Class<?>[]{MontoyaApi.class},
                (proxy, method, args) -> {
                    if ("http".equals(method.getName())) return http;
                    return null;
                }
        );
    }

    private static HttpResponse createMockResponse(int statusCode, String body) {
        return (HttpResponse) Proxy.newProxyInstance(
                HttpResponse.class.getClassLoader(),
                new Class<?>[]{HttpResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("statusCode".equals(name)) return (short) statusCode;
                    if ("bodyToString".equals(name)) return body != null ? body : "";
                    if ("body".equals(name)) {
                        byte[] bytes = (body != null ? body : "").getBytes(StandardCharsets.UTF_8);
                        return (ByteArray) Proxy.newProxyInstance(
                                ByteArray.class.getClassLoader(),
                                new Class<?>[]{ByteArray.class},
                                (p, m, a) -> {
                                    if ("length".equals(m.getName())) return bytes.length;
                                    if ("getBytes".equals(m.getName())) return bytes;
                                    return null;
                                }
                        );
                    }
                    if ("headers".equals(name)) return Collections.emptyList();
                    return null;
                }
        );
    }

    private static HttpRequestResponse createMockHttpRequestResponse(HttpRequest request, HttpResponse response) {
        return (HttpRequestResponse) Proxy.newProxyInstance(
                HttpRequestResponse.class.getClassLoader(),
                new Class<?>[]{HttpRequestResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("request".equals(name)) return request;
                    if ("response".equals(name)) return response;
                    return null;
                }
        );
    }
}
