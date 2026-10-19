package thorization.bridge;

import com.google.gson.Gson;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thorization.bridge.dto.AnalyzeRequestDto;
import thorization.bridge.dto.AnalyzeResponseDto;
import thorization.bridge.dto.AuditRecordSummaryDto;
import thorization.bridge.dto.RoleDto;
import thorization.bridge.dto.StatusResponseDto;
import thorization.core.EngineController;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.FilterConfig;
import thorization.model.OriginalAction;
import thorization.model.Verdict;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class AiBridgeTest {

    private EngineController engineController;
    private AiBridgeController bridgeController;
    private AiBridgeServer server;
    private final int testPort = 1447;
    private final Gson gson = new Gson();

    @BeforeEach
    void setUp() {
        engineController = new EngineController(null);
        bridgeController = new AiBridgeController(engineController);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        if (engineController != null) {
            engineController.shutdown();
        }
    }

    @Test
    void testStatusReporting() {
        engineController.setEnabled(true);
        FilterConfig fc = engineController.getFilterConfig();
        fc.setUrlExcludeRegex(".*/logout.*");

        StatusResponseDto status = bridgeController.getStatus();
        assertNotNull(status);
        assertEquals("online", status.getStatus());
        assertEquals("1.3.0", status.getVersion());
        assertTrue(status.isEngineEnabled());
        assertNotNull(status.getFilterConfig());
        assertEquals(".*/logout.*", status.getFilterConfig().getUrlExcludeRegex());
    }

    @Test
    void testRoleCrudOperations() {
        // 1. Initial roles should be empty
        List<RoleDto> initialRoles = bridgeController.getRoles();
        assertTrue(initialRoles.isEmpty());

        // 2. Add role
        RoleDto newRole = new RoleDto(null, "Admin", "Authorization: Bearer admin-tok\nX-Custom: 123", "sess=abc", true);
        RoleDto created = bridgeController.addRole(newRole);
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals("Admin", created.getName());
        assertEquals("sess=abc", created.getCookies());
        assertTrue(created.getEnabled());

        // 3. Get single role
        RoleDto fetched = bridgeController.getRole(created.getId());
        assertNotNull(fetched);
        assertEquals(created.getId(), fetched.getId());
        assertEquals("Admin", fetched.getName());

        // 4. Update role
        RoleDto updateDto = new RoleDto();
        updateDto.setName("SuperAdmin");
        updateDto.setCookies("sess=xyz");
        RoleDto updated = bridgeController.updateRole(created.getId(), updateDto);
        assertNotNull(updated);
        assertEquals("SuperAdmin", updated.getName());
        assertEquals("sess=xyz", updated.getCookies());

        // 5. Delete role
        boolean deleted = bridgeController.deleteRole(created.getId());
        assertTrue(deleted);
        assertNull(bridgeController.getRole(created.getId()));

        // 6. Error handling
        assertThrows(IllegalArgumentException.class, () -> bridgeController.addRole(new RoleDto(null, "", "", "", true)));
        assertThrows(NoSuchElementException.class, () -> bridgeController.updateRole("non-existent", new RoleDto()));
        assertFalse(bridgeController.deleteRole("non-existent"));
    }

    @Test
    void testParameterAliasesAndSnakeCaseSupport() {
        // Test Gson deserializing snake_case and alias parameter names
        String jsonPayload = "{\n" +
                "  \"target_url\": \"https://api.crapi.local/identity/api/auth/v2/user\",\n" +
                "  \"http_method\": \"POST\",\n" +
                "  \"bypass_rules\": true,\n" +
                "  \"source_tool\": \"Google-Antigravity\",\n" +
                "  \"synchronous\": true,\n" +
                "  \"target_role_ids\": [\"role-attacker-1\", \"role-victim-2\"]\n" +
                "}";

        AnalyzeRequestDto dto = gson.fromJson(jsonPayload, AnalyzeRequestDto.class);
        assertNotNull(dto);
        assertEquals("https://api.crapi.local/identity/api/auth/v2/user", dto.getUrl());
        assertEquals("POST", dto.getMethod());
        assertTrue(dto.isBypassFilter());
        assertEquals("Google-Antigravity", dto.getSourceTool());
        assertTrue(dto.isSync());
        assertNotNull(dto.getTargetRoleIds());
        assertEquals(2, dto.getTargetRoleIds().size());
        assertTrue(dto.getTargetRoleIds().contains("role-attacker-1"));
    }

    @Test
    void testRoleDtoStructuredHeadersMapAndCookiesMap() {
        // Test role creation with structured headers_map and cookies_map
        String roleJson = "{\n" +
                "  \"role_name\": \"Attacker-API-Role\",\n" +
                "  \"headers_map\": {\n" +
                "    \"Authorization\": \"Bearer attacker-token-999\",\n" +
                "    \"X-Tenant-ID\": \"attacker-tenant\"\n" +
                "  },\n" +
                "  \"cookies_map\": {\n" +
                "    \"session_id\": \"attacker_cookie_val\"\n" +
                "  },\n" +
                "  \"is_enabled\": true\n" +
                "}";

        RoleDto roleDto = gson.fromJson(roleJson, RoleDto.class);
        assertNotNull(roleDto);
        assertEquals("Attacker-API-Role", roleDto.getName());
        assertTrue(roleDto.getHeaders().contains("Authorization: Bearer attacker-token-999"));
        assertTrue(roleDto.getHeaders().contains("X-Tenant-ID: attacker-tenant"));
        assertTrue(roleDto.getCookies().contains("session_id=attacker_cookie_val"));
        assertTrue(roleDto.getEnabled());

        RoleDto created = bridgeController.addRole(roleDto);
        assertNotNull(created.getId());
        assertEquals("Attacker-API-Role", created.getName());
    }

    @Test
    void testHighLevelVulnerabilityDetectionAndSummary() {
        engineController.setEnabled(true);

        // Create an audit record with simulated bypass
        long recordId = engineController.nextRecordId();
        AuditRecord record = new AuditRecord(recordId, null, OriginalAction.PASSED, "Unit-Test");
        record.setUrl("https://api.crapi.local/workshop/api/shop/orders/all");
        record.setMethod("GET");
        record.setOriginalStatusCode(200);

        record.addRoleResult("role-attacker", new AuthorizationCheckResult(
                "role-attacker",
                "Attacker",
                Verdict.BYPASS,
                200,
                512,
                98.0,
                25,
                "Bypass detected: 200 OK returned with matching schema",
                null,
                null
        ));

        record.addRoleResult("role-guest", new AuthorizationCheckResult(
                "role-guest",
                "Guest",
                Verdict.ENFORCED,
                403,
                42,
                10.0,
                15,
                "Protected: 403 Forbidden returned",
                null,
                null
        ));

        engineController.addRecord(record);

        // Fetch detail via bridge controller
        AnalyzeResponseDto detail = bridgeController.getRecordDetail(recordId);
        assertNotNull(detail);
        assertTrue(detail.isVulnerabilityDetected());
        assertEquals(1, detail.getVulnerableRoles().size());
        assertTrue(detail.getVulnerableRoles().contains("Attacker"));
        assertTrue(detail.getSummary().contains("VULNERABILITY DETECTED"));
        assertTrue(detail.getSummary().contains("Attacker"));

        // Fetch summary list via bridge controller
        List<AuditRecordSummaryDto> records = bridgeController.getRecords(10, true);
        assertEquals(1, records.size());
        assertTrue(records.get(0).isVulnerabilityDetected());
        assertTrue(records.get(0).getVulnerableRoles().contains("Attacker"));

        // Test vulnerableOnly filter returns empty if filtering for non-existent condition
        List<AuditRecordSummaryDto> none = bridgeController.getRecords(0, false);
        assertEquals(1, none.size());
    }

    @Test
    void testRequestFilteringRules() {
        // Test engine disabled
        engineController.setEnabled(false);
        AnalyzeRequestDto req = new AnalyzeRequestDto();
        req.setUrl("https://api.example.com/data");
        req.setMethod("GET");

        AnalyzeResponseDto respDisabled = bridgeController.analyzeRequest(req);
        assertFalse(respDisabled.isSuccess());
        assertTrue(respDisabled.isFilteredOut());
        assertTrue(respDisabled.getFilterReason().contains("disabled"));

        // Enable engine
        engineController.setEnabled(true);

        // Test static file filter
        req.setUrl("https://api.example.com/logo.png");
        AnalyzeResponseDto respStatic = bridgeController.analyzeRequest(req);
        assertFalse(respStatic.isSuccess());
        assertTrue(respStatic.isFilteredOut());
        assertTrue(respStatic.getFilterReason().contains("static"));

        // Test method filter
        req.setUrl("https://api.example.com/data");
        req.setMethod("TRACE");
        AnalyzeResponseDto respMethod = bridgeController.analyzeRequest(req);
        assertFalse(respMethod.isSuccess());
        assertTrue(respMethod.isFilteredOut());
        assertTrue(respMethod.getFilterReason().contains("target methods"));

        // Test regex exclude filter
        engineController.getFilterConfig().setUrlExcludeRegex(".*exclude-me.*");
        req.setUrl("https://api.example.com/exclude-me/now");
        req.setMethod("GET");
        AnalyzeResponseDto respRegex = bridgeController.analyzeRequest(req);
        assertFalse(respRegex.isSuccess());
        assertTrue(respRegex.isFilteredOut());
        assertTrue(respRegex.getFilterReason().contains("regex"));

        // Test bypass filter
        req.setBypassFilter(true);
        AnalyzeResponseDto respBypass = bridgeController.analyzeRequest(req);
        assertTrue(respBypass.isSuccess());
        assertFalse(respBypass.isFilteredOut());
    }

    @Test
    void testRawHttpRequestParsingAndAnalysis() {
        engineController.setEnabled(true);

        // Add a test role
        bridgeController.addRole(new RoleDto(null, "Auditor", "X-Role: Auditor", "session=1", true));

        String rawHttp = "POST /api/v1/transfer HTTP/1.1\r\n" +
                "Host: bank.local\r\n" +
                "Content-Type: application/json\r\n" +
                "Authorization: Bearer test\r\n" +
                "\r\n" +
                "{\"amount\": 100}";

        AnalyzeRequestDto analyzeReq = new AnalyzeRequestDto();
        analyzeReq.setRawRequest(rawHttp);
        analyzeReq.setSourceTool("Claude-Agent");
        analyzeReq.setBypassFilter(true);

        AnalyzeResponseDto response = bridgeController.analyzeRequest(analyzeReq);
        assertNotNull(response);
        assertTrue(response.isSuccess());
        assertEquals("http://bank.local/api/v1/transfer", response.getUrl());
        assertEquals("POST", response.getMethod());
        assertNotNull(response.getRecordId());
        assertTrue(response.getRoleVerdicts().containsKey("Auditor"));
        assertTrue(response.getRoleVerdicts().containsKey("unauthenticated"));

        // Verify record exists in records list
        assertEquals(1, bridgeController.getRecords().size());
        assertEquals("Claude-Agent", bridgeController.getRecords().get(0).getSourceTool());
    }

    @Test
    void testEndToEndHttpServer() throws IOException, InterruptedException {
        server = new AiBridgeServer(bridgeController, testPort);
        server.start();
        assertTrue(server.isRunning());
        assertEquals(testPort, server.getPort());

        HttpClient client = HttpClient.newHttpClient();
        String base = server.getBaseUrl();

        // 1. GET /api/status
        HttpRequest getStatusReq = HttpRequest.newBuilder()
                .uri(URI.create(base + "status"))
                .GET()
                .build();
        HttpResponse<String> statusResp = client.send(getStatusReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, statusResp.statusCode());
        StatusResponseDto statusObj = gson.fromJson(statusResp.body(), StatusResponseDto.class);
        assertEquals("online", statusObj.getStatus());

        // 2. POST /api/roles
        RoleDto createRole = new RoleDto(null, "TestUser", "X-Header: val", "cookie=1", true);
        HttpRequest createRoleReq = HttpRequest.newBuilder()
                .uri(URI.create(base + "roles"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(createRole)))
                .build();
        HttpResponse<String> createRoleResp = client.send(createRoleReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(201, createRoleResp.statusCode());
        RoleDto createdRoleObj = gson.fromJson(createRoleResp.body(), RoleDto.class);
        assertNotNull(createdRoleObj.getId());
        assertEquals("TestUser", createdRoleObj.getName());

        // 3. GET /api/roles
        HttpRequest getRolesReq = HttpRequest.newBuilder()
                .uri(URI.create(base + "roles"))
                .GET()
                .build();
        HttpResponse<String> rolesResp = client.send(getRolesReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, rolesResp.statusCode());
        assertTrue(rolesResp.body().contains("TestUser"));

        // 4. PUT /api/roles/{id}
        RoleDto updateRole = new RoleDto();
        updateRole.setName("TestUserRenamed");
        HttpRequest putRoleReq = HttpRequest.newBuilder()
                .uri(URI.create(base + "roles/" + createdRoleObj.getId()))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(gson.toJson(updateRole)))
                .build();
        HttpResponse<String> putRoleResp = client.send(putRoleReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, putRoleResp.statusCode());
        RoleDto updatedRoleObj = gson.fromJson(putRoleResp.body(), RoleDto.class);
        assertEquals("TestUserRenamed", updatedRoleObj.getName());

        // 5. POST /api/analyze
        AnalyzeRequestDto analyzeDto = new AnalyzeRequestDto();
        analyzeDto.setUrl("https://example.com/api/test");
        analyzeDto.setMethod("POST");
        analyzeDto.setBypassFilter(true);
        HttpRequest analyzeHttpReq = HttpRequest.newBuilder()
                .uri(URI.create(base + "analyze"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(analyzeDto)))
                .build();
        HttpResponse<String> analyzeResp = client.send(analyzeHttpReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, analyzeResp.statusCode());
        AnalyzeResponseDto analyzeObj = gson.fromJson(analyzeResp.body(), AnalyzeResponseDto.class);
        assertTrue(analyzeObj.isSuccess());
        assertNotNull(analyzeObj.getSummary());

        // 6. GET /api/records with query parameters (?limit=5)
        HttpRequest getRecordsReq = HttpRequest.newBuilder()
                .uri(URI.create(base + "records?limit=5&vulnerableOnly=false"))
                .GET()
                .build();
        HttpResponse<String> recordsResp = client.send(getRecordsReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, recordsResp.statusCode());
        assertTrue(recordsResp.body().contains("example.com/api/test"));

        // 7. DELETE /api/roles/{id}
        HttpRequest deleteRoleReq = HttpRequest.newBuilder()
                .uri(URI.create(base + "roles/" + createdRoleObj.getId()))
                .DELETE()
                .build();
        HttpResponse<String> deleteRoleResp = client.send(deleteRoleReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, deleteRoleResp.statusCode());

        // Stop server
        server.stop();
        assertFalse(server.isRunning());
    }
}
