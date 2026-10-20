package thorization.report;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thorization.ai.AiEvaluationResult;
import thorization.ai.AiVerdict;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.substitution.MockHttpRequestHelper;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HtmlReportGeneratorTest {

    @BeforeAll
    static void init() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("Verify Standalone Offline HTML with Zero External CDN Dependencies")
    void testZeroExternalCdnDependencies() {
        AuditRecord rec = new AuditRecord(1L, null);
        rec.setUrl("https://api.example.com/v1/test");
        rec.setMethod("GET");

        String html = HtmlReportGenerator.generateHtml(List.of(rec), Collections.emptyList());

        assertNotNull(html);
        assertTrue(html.startsWith("<!DOCTYPE html>"), "Must be valid HTML5 document");
        assertTrue(html.contains("<style>"), "Must include inlined CSS styles");
        assertTrue(html.contains("<script>"), "Must include inlined vanilla JavaScript");

        // Verify zero external CDN links or scripts
        assertFalse(html.contains("<link rel=\"stylesheet\" href=\"http"), "Must not link to external CSS");
        assertFalse(html.contains("<script src=\"http"), "Must not load external scripts");
        assertFalse(html.contains("cdnjs.cloudflare.com"), "Zero CDN: cdnjs must not be present");
        assertFalse(html.contains("cdn.jsdelivr.net"), "Zero CDN: jsdelivr must not be present");
        assertFalse(html.contains("unpkg.com"), "Zero CDN: unpkg must not be present");
        assertFalse(html.contains("fonts.googleapis.com"), "Zero CDN: Google Fonts must not be present");
    }

    @Test
    @DisplayName("Verify Executive Metrics Dashboard and Posture Calculation")
    void testExecutiveMetricsDashboard() {
        UserRole roleAdmin = new UserRole("Admin", "X-Role: admin", "");
        roleAdmin.setId("role-admin");
        UserRole roleUser = new UserRole("StandardUser", "X-Role: user", "");
        roleUser.setId("role-user");

        // Record 1: Admin bypass (vulnerable), User enforced
        AuditRecord rec1 = new AuditRecord(10L, null);
        rec1.setUrl("https://api.example.com/orders");
        rec1.setMethod("POST");
        rec1.setOriginalStatusCode(200);
        rec1.setOriginalResponseLength(1000);

        AuthorizationCheckResult res1Admin = new AuthorizationCheckResult(
                "role-admin", "Admin", Verdict.BYPASS, 200, 1000, 1.0, 45, "Bypass confirmed", null, null
        );
        res1Admin.setAiResult(AiEvaluationResult.success(AiVerdict.VALIDATED_ACCESS, "Critical flaw: unprivileged user accessed admin resource", "gpt-4o", 1200L));

        AuthorizationCheckResult res1User = new AuthorizationCheckResult(
                "role-user", "StandardUser", Verdict.ENFORCED, 403, 80, 0.1, 30, "Access denied", null, null
        );
        res1User.setAiResult(AiEvaluationResult.success(AiVerdict.ACCESS_DENIED, "Access properly restricted", "gpt-4o", 900L));

        rec1.addRoleResult("role-admin", res1Admin);
        rec1.addRoleResult("role-user", res1User);

        // Record 2: Unauthenticated enforced
        AuditRecord rec2 = new AuditRecord(20L, null);
        rec2.setUrl("https://api.example.com/profile");
        rec2.setMethod("GET");
        rec2.setOriginalStatusCode(200);
        rec2.setOriginalResponseLength(500);

        AuthorizationCheckResult unauthRes = new AuthorizationCheckResult(
                "unauthenticated", "Unauthenticated", Verdict.ENFORCED, 401, 50, 0.05, 20, "Unauthorized", null, null
        );
        rec2.setUnauthenticatedResult(unauthRes);

        List<AuditRecord> records = List.of(rec1, rec2);
        List<UserRole> roles = List.of(roleAdmin, roleUser);

        ReportMetrics metrics = ReportMetrics.compute(records, roles);
        assertEquals(2, metrics.totalRequests());
        assertEquals(3, metrics.totalReplays()); // 2 from rec1 + 1 from rec2
        assertEquals(2, metrics.enforcedCount()); // res1User + unauthRes
        assertEquals(1, metrics.bypassCount());   // res1Admin
        assertEquals(0, metrics.indeterminableCount());
        assertEquals(1, metrics.aiValidatedAccessCount());
        assertEquals(1, metrics.aiAccessDeniedCount());
        // Posture score: 2 / (2 + 1) = 66.7%
        assertEquals(66.7, metrics.securityPostureScore(), 0.1);

        String html = HtmlReportGenerator.generateHtml(records, roles, "Security Posture Executive Report");

        assertTrue(html.contains("Security Posture Executive Report"));
        assertTrue(html.contains("66.7%"));
        assertTrue(html.contains("Total Replay Tests"));
        assertTrue(html.contains("<div class=\"metric-value\">3</div>")); // total replays
        assertTrue(html.contains("<div class=\"metric-value text-green\">2</div>")); // enforced
        assertTrue(html.contains("<div class=\"metric-value text-red\">1</div>")); // bypass
        assertTrue(html.contains("Critical flaw: unprivileged user accessed admin resource"));
    }

    @Test
    @DisplayName("Verify Context-Aware HTML Entity Escaping for XSS Prevention")
    void testXssEntityEscaping() {
        AuditRecord maliciousRec = new AuditRecord(999L, null);
        String xssPayload = "<script>alert('XSS-Thorization')</script>";
        String xssAttr = "\" onmouseover=\"alert('attr')\" <tag>";
        String xssUrl = "https://example.com/search?q=" + xssPayload + "&filter=" + xssAttr;

        maliciousRec.setUrl(xssUrl);
        maliciousRec.setMethod("POST");
        maliciousRec.setPath("/search");

        AuthorizationCheckResult res = new AuthorizationCheckResult(
                "role-xss", "Attacker<script>", Verdict.BYPASS, 200, 500, 1.0, 10,
                "Notes with <b>HTML</b> & 'quotes'", null, null
        );
        res.setAiResult(AiEvaluationResult.success(AiVerdict.VALIDATED_ACCESS, "Reasoning with <script> and \"quotes\"", "model", 100L));
        maliciousRec.addRoleResult("role-xss", res);

        String html = HtmlReportGenerator.generateHtml(List.of(maliciousRec), Collections.emptyList());

        // Dangerous characters must NOT appear unescaped in text/attribute nodes
        assertFalse(html.contains("<script>alert('XSS-Thorization')</script>"), "Raw script tag must be escaped");
        assertTrue(html.contains("&lt;script&gt;alert(&#39;XSS-Thorization&#39;)&lt;/script&gt;"), "Script tag must be entity encoded");
        assertTrue(html.contains("Attacker&lt;script&gt;"), "Role name with HTML must be encoded");
        assertTrue(html.contains("&lt;b&gt;HTML&lt;/b&gt; &amp; &#39;quotes&#39;"), "Notes must be HTML entity encoded");
    }

    @Test
    @DisplayName("Verify 1-Click Copyable cURL Integration in HTML Output")
    void testCopyableCurlCommandIntegration() {
        AuditRecord rec = new AuditRecord(55L, null);
        rec.setUrl("https://api.example.com/v1/resource");
        rec.setMethod("POST");

        var mockOrig = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/v1/resource",
                "POST",
                "{\"payload\":\"test\"}",
                Map.of("Authorization", "Bearer original")
        );
        rec.setOriginalRequest(mockOrig);

        var mockReplay = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/v1/resource",
                "POST",
                "{\"payload\":\"test\"}",
                Map.of("Authorization", "Bearer attacker", "X-Role", "auditor")
        );
        AuthorizationCheckResult res = new AuthorizationCheckResult(
                "auditor", "Auditor", Verdict.BYPASS, 200, 300, 1.0, 40, "Bypassed", mockReplay, null
        );
        rec.addRoleResult("auditor", res);

        String html = HtmlReportGenerator.generateHtml(List.of(rec), Collections.emptyList());

        assertTrue(html.contains("btn-curl"), "Must render copy cURL button");
        assertTrue(html.contains("data-curl="), "Must contain data-curl encoded attribute");
        assertTrue(html.contains("copyCurlCommand(this)"), "Must wire copy click handler");
    }

    @Test
    @DisplayName("Verify Side-by-Side Diff Panels and AI Reasoning Cards")
    void testSideBySideDiffAndAiCards() {
        AuditRecord rec = new AuditRecord(77L, null);
        rec.setUrl("https://api.example.com/api/v2/data");
        rec.setMethod("GET");
        rec.setOriginalStatusCode(200);
        rec.setOriginalResponseLength(1024);

        var mockOrig = MockHttpRequestHelper.createMockRequest("https://api.example.com/api/v2/data", "GET", "", Map.of("Host", "api.example.com"));
        rec.setOriginalRequest(mockOrig);

        var mockReplay = MockHttpRequestHelper.createMockRequest("https://api.example.com/api/v2/data", "GET", "", Map.of("Host", "api.example.com", "X-Role", "User"));
        AuthorizationCheckResult res = new AuthorizationCheckResult("user-id", "RegularUser", Verdict.ENFORCED, 403, 128, 0.15, 60, "Blocked", mockReplay, null);
        res.setAiResult(AiEvaluationResult.success(AiVerdict.ACCESS_DENIED, "Server correctly returned 403 Forbidden when accessed without admin privilege.", "deepseek-chat", 850L));
        rec.addRoleResult("user-id", res);

        String html = HtmlReportGenerator.generateHtml(List.of(rec), Collections.emptyList());

        assertTrue(html.contains("HTTP Request Comparison"));
        assertTrue(html.contains("HTTP Response Comparison"));
        assertTrue(html.contains("Original Request (Baseline)"));
        assertTrue(html.contains("Replayed Request (Role: RegularUser)"));
        assertTrue(html.contains("Verdict: <strong>ACCESS_DENIED</strong>"));
        assertTrue(html.contains("Model: deepseek-chat"));
        assertTrue(html.contains("850 ms"));
        assertTrue(html.contains("Server correctly returned 403 Forbidden when accessed without admin privilege."));
    }

    @Test
    @DisplayName("Verify File Output and StandardCharsets UTF-8 Encoding")
    void testGenerateReportToFile(@TempDir Path tempDir) throws IOException {
        AuditRecord rec = new AuditRecord(888L, null);
        rec.setUrl("https://api.example.com/test");
        rec.setMethod("GET");

        File targetFile = tempDir.resolve("audit-report.html").toFile();
        HtmlReportGenerator.writeHtmlReport(targetFile, List.of(rec), Collections.emptyList(), "Custom Security Audit");

        assertTrue(targetFile.exists(), "Target file must be written to disk");
        assertTrue(targetFile.length() > 0, "Target file must not be empty");

        String content = Files.readString(targetFile.toPath(), StandardCharsets.UTF_8);
        assertTrue(content.contains("Custom Security Audit"));
        assertTrue(content.contains("https://api.example.com/test"));
        assertTrue(content.contains("<!DOCTYPE html>"));

        // Also test generateReport overload
        File targetFile2 = tempDir.resolve("subdir/audit-report-2.html").toFile();
        HtmlReportGenerator.generateReport(targetFile2, List.of(rec), Collections.emptyList());
        assertTrue(targetFile2.exists(), "Should create parent directories if missing");
        assertTrue(targetFile2.length() > 0);
    }

    @Test
    @DisplayName("Verify Graceful Handling of Empty or Null Record Collections")
    void testEmptyAndNullSafety() {
        assertDoesNotThrow(() -> {
            String htmlEmpty = HtmlReportGenerator.generateHtml(Collections.emptyList(), Collections.emptyList());
            assertTrue(htmlEmpty.contains("No authorization test records available"));
            assertTrue(htmlEmpty.contains("0</strong> of <strong id=\"total-count\">0"));

            String htmlNull = HtmlReportGenerator.generateHtml(null, null);
            assertNotNull(htmlNull);
            assertTrue(htmlNull.contains("<!DOCTYPE html>"));
        });
    }
}
