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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CsvReportGeneratorTest {

    @BeforeAll
    static void init() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("Verify exact 16 RFC 4180 header columns and CRLF termination")
    void testHeadersAndCrlf() {
        String csv = CsvReportGenerator.generateCsv(Collections.emptyList(), Collections.emptyList());
        assertTrue(csv.endsWith("\r\n"), "Every CSV record must end with CRLF");

        List<List<String>> parsed = parseRfc4180Csv(csv);
        assertEquals(1, parsed.size(), "Empty list should produce only the header row");

        List<String> headers = parsed.get(0);
        assertEquals(16, headers.size(), "Must have exactly 16 columns");
        assertEquals("Request_ID", headers.get(0));
        assertEquals("Timestamp", headers.get(1));
        assertEquals("Host", headers.get(2));
        assertEquals("Method", headers.get(3));
        assertEquals("Path", headers.get(4));
        assertEquals("Original_Status", headers.get(5));
        assertEquals("Original_Length", headers.get(6));
        assertEquals("Role_Name", headers.get(7));
        assertEquals("Role_Status", headers.get(8));
        assertEquals("Replayed_Status", headers.get(9));
        assertEquals("Replayed_Length", headers.get(10));
        assertEquals("Enforced_Status", headers.get(11));
        assertEquals("AI_Verdict", headers.get(12));
        assertEquals("AI_Confidence", headers.get(13));
        assertEquals("AI_Reasoning", headers.get(14));
        assertEquals("cURL_Command", headers.get(15));
    }

    @Test
    @DisplayName("Verify strict escaping of commas, quotes, and newlines in AI reasoning and cURL")
    void testEscapingSpecialCharacters() {
        AuditRecord rec = new AuditRecord(1, null);
        rec.setUrl("https://api.example.com/v1/orders,list");
        rec.setPath("/v1/orders,list");
        rec.setMethod("GET");
        rec.setOriginalStatusCode(200);
        rec.setOriginalResponseLength(500);

        UserRole role = new UserRole("Attacker, \"Special\"", "X-Role: test", "");
        role.setId("role-1");

        String multilineReasoning = "Line 1: Status matched.\nLine 2: User gained unauthorized access, \"CONFIRMED\".\r\nLine 3: Impact is high.";
        AuthorizationCheckResult res = new AuthorizationCheckResult(
                "role-1", role.getName(), Verdict.BYPASS, 200, 500, 1.0, 100,
                "Notes", null, null
        );
        res.setAiResult(AiEvaluationResult.success(AiVerdict.VALIDATED_ACCESS, multilineReasoning, "gpt-4o", 1500L));
        rec.addRoleResult("role-1", res);

        String csv = CsvReportGenerator.generateCsv(List.of(rec), List.of(role));

        List<List<String>> parsed = parseRfc4180Csv(csv);
        assertEquals(2, parsed.size(), "Header + 1 data row");

        List<String> row = parsed.get(1);
        assertEquals(16, row.size(), "Data row must have exactly 16 columns");
        assertEquals("1", row.get(0));
        assertEquals("api.example.com", row.get(2));
        assertEquals("GET", row.get(3));
        assertEquals("/v1/orders,list", row.get(4), "Path with comma must be preserved without splitting columns");
        assertEquals("200", row.get(5));
        assertEquals("500", row.get(6));
        assertEquals("Attacker, \"Special\"", row.get(7), "Role name with comma and quotes must be preserved");
        assertEquals("Active", row.get(8));
        assertEquals("200", row.get(9));
        assertEquals("500", row.get(10));
        assertEquals("BYPASS", row.get(11));
        assertEquals("VALIDATED_ACCESS", row.get(12));
        assertEquals(multilineReasoning, row.get(14), "Multiline AI reasoning with quotes and commas must parse exactly");
    }

    @Test
    @DisplayName("Verify CSV output for multiple roles, unauthenticated tests, and cURL commands")
    void testMultipleRolesAndUnauthenticated() {
        AuditRecord rec = new AuditRecord(42, null);
        rec.setUrl("https://api.example.com/api/admin");
        rec.setPath("/api/admin");
        rec.setMethod("POST");
        rec.setOriginalStatusCode(200);
        rec.setOriginalResponseLength(1200);

        var origReq = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/api/admin",
                "POST",
                "{\"admin\":true}",
                Map.of("Authorization", "Bearer admin-token")
        );
        rec.setOriginalRequest(origReq);

        UserRole adminRole = new UserRole("Admin", "Authorization: Bearer admin-token", "");
        adminRole.setId("role-admin");
        UserRole userRole = new UserRole("User", "Authorization: Bearer user-token", "");
        userRole.setId("role-user");

        var userReq = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/api/admin",
                "POST",
                "{\"admin\":true}",
                Map.of("Authorization", "Bearer user-token")
        );
        AuthorizationCheckResult userRes = new AuthorizationCheckResult(
                "role-user", "User", Verdict.ENFORCED, 403, 100, 0.1, 50, "Forbidden", userReq, null
        );
        rec.addRoleResult("role-user", userRes);

        var unauthReq = MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/api/admin",
                "POST",
                "{\"admin\":true}",
                Map.of()
        );
        AuthorizationCheckResult unauthRes = new AuthorizationCheckResult(
                "unauthenticated", "Unauthenticated", Verdict.ENFORCED, 401, 80, 0.05, 30, "Unauthorized", unauthReq, null
        );
        rec.setUnauthenticatedResult(unauthRes);

        String csv = CsvReportGenerator.generateCsv(List.of(rec), List.of(adminRole, userRole));

        List<List<String>> parsed = parseRfc4180Csv(csv);
        // Header + Admin row + User row + Unauth row = 4 rows
        assertEquals(4, parsed.size(), "Should have header + 3 audit rows");

        // Check user row
        List<String> userRow = parsed.get(2);
        assertEquals("User", userRow.get(7));
        assertEquals("403", userRow.get(9));
        assertEquals("ENFORCED", userRow.get(11));
        assertTrue(userRow.get(15).contains("Bearer user-token"), "cURL command must match user replayed request");

        // Check unauth row
        List<String> unauthRow = parsed.get(3);
        assertEquals("Unauthenticated", unauthRow.get(7));
        assertEquals("401", unauthRow.get(9));
        assertEquals("ENFORCED", unauthRow.get(11));
        assertFalse(unauthRow.get(15).contains("Authorization:"), "Unauth cURL should have no auth headers");
    }

    @Test
    @DisplayName("Verify physical file write with writeCsvReport and exportCsv")
    void testWriteCsvReportToFile(@TempDir Path tempDir) throws IOException {
        AuditRecord rec = new AuditRecord(77, null);
        rec.setUrl("https://api.example.com/test");
        rec.setMethod("GET");

        File targetFile1 = tempDir.resolve("audit-report.csv").toFile();
        CsvReportGenerator.writeCsvReport(targetFile1, List.of(rec), Collections.emptyList());

        assertTrue(targetFile1.exists());
        assertTrue(targetFile1.length() > 0);

        String content1 = Files.readString(targetFile1.toPath(), StandardCharsets.UTF_8);
        assertTrue(content1.startsWith("Request_ID,Timestamp,Host,Method"));

        File targetFile2 = tempDir.resolve("subdir/export.csv").toFile();
        CsvReportGenerator.exportCsv(targetFile2, List.of(rec), Collections.emptyList());
        assertTrue(targetFile2.exists(), "Parent directories must be created automatically");
        assertTrue(targetFile2.length() > 0);
    }

    @Test
    @DisplayName("Verify escapeCsvField RFC 4180 corner cases")
    void testEscapeCsvField() {
        assertEquals("", CsvReportGenerator.escapeCsvField(null));
        assertEquals("simple", CsvReportGenerator.escapeCsvField("simple"));
        assertEquals("\"comma,separated\"", CsvReportGenerator.escapeCsvField("comma,separated"));
        assertEquals("\"quote\"\"inside\"", CsvReportGenerator.escapeCsvField("quote\"inside"));
        assertEquals("\"line1\r\nline2\"", CsvReportGenerator.escapeCsvField("line1\r\nline2"));
        assertEquals("\"line1\nline2\"", CsvReportGenerator.escapeCsvField("line1\nline2"));
    }

    @Test
    @DisplayName("Verify host and confidence extraction edge cases")
    void testExtractHostAndConfidence() {
        AuditRecord rec1 = new AuditRecord(1, null);
        rec1.setUrl("https://custom-host.corp.internal:8443/api/v1");
        assertEquals("custom-host.corp.internal", CsvReportGenerator.extractHost(rec1));

        AuditRecord rec2 = new AuditRecord(2, null);
        rec2.setUrl("/relative/path");
        assertEquals("", CsvReportGenerator.extractHost(rec2));

        assertEquals("", CsvReportGenerator.extractHost(null));

        assertEquals("", CsvReportGenerator.extractConfidence(null));
        AiEvaluationResult aiResult = AiEvaluationResult.success(AiVerdict.VALIDATED_ACCESS, "confidence: 0.95 and high impact", "model", 100);
        assertEquals("0.95", CsvReportGenerator.extractConfidence(aiResult));
    }

    /**
     * Strict RFC 4180 CSV parser used within test assertions.
     */
    private static List<List<String>> parseRfc4180Csv(String csvContent) {
        List<List<String>> records = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        int len = csvContent.length();

        for (int i = 0; i < len; i++) {
            char c = csvContent.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < len && csvContent.charAt(i + 1) == '"') {
                        currentField.append('"');
                        i++; // skip escaped quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    currentField.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                } else if (c == '\r') {
                    if (i + 1 < len && csvContent.charAt(i + 1) == '\n') {
                        i++; // consume \n
                    }
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                    records.add(new ArrayList<>(currentRecord));
                    currentRecord.clear();
                } else if (c == '\n') {
                    currentRecord.add(currentField.toString());
                    currentField.setLength(0);
                    records.add(new ArrayList<>(currentRecord));
                    currentRecord.clear();
                } else {
                    currentField.append(c);
                }
            }
        }
        if (currentField.length() > 0 || !currentRecord.isEmpty()) {
            currentRecord.add(currentField.toString());
            records.add(currentRecord);
        }
        return records;
    }
}
