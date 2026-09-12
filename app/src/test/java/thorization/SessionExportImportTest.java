package thorization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thorization.export.SessionExporter;
import thorization.export.SessionImporter;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionExportImportTest {

    @Test
    void testExportAndImportCycle(@TempDir Path tempDir) throws IOException {
        File jsonFile = tempDir.resolve("test_session.json").toFile();

        List<UserRole> roles = new ArrayList<>();
        UserRole role1 = new UserRole("User B", "X-Role: user", "sess=123");
        roles.add(role1);

        List<AuditRecord> records = new ArrayList<>();
        AuditRecord rec = new AuditRecord(1, null);
        rec.setMethod("POST");
        rec.setUrl("https://example.com/api/users/profile");
        rec.setPath("/api/users/profile");
        rec.setOriginalStatusCode(200);
        rec.setOriginalResponseLength(450);

        AuthorizationCheckResult res = new AuthorizationCheckResult(
                role1.getId(), "User B", Verdict.BYPASS, 200, 450, 0.98, 120, "Vulnerable", null, null
        );
        rec.addRoleResult(role1.getId(), res);

        records.add(rec);

        // Export
        SessionExporter.exportToFile(jsonFile, records, roles);
        assertTrue(jsonFile.exists());
        assertTrue(jsonFile.length() > 0);

        // Import
        SessionImporter.ImportResult imported = SessionImporter.importFromFile(jsonFile);
        assertEquals(1, imported.getRoles().size());
        assertEquals("User B", imported.getRoles().get(0).getName());

        assertEquals(1, imported.getRecords().size());
        AuditRecord importedRec = imported.getRecords().get(0);
        assertEquals("POST", importedRec.getMethod());
        assertEquals("https://example.com/api/users/profile", importedRec.getUrl());
        assertEquals(200, importedRec.getOriginalStatusCode());
        assertEquals(1, importedRec.getRoleResults().size());

        AuthorizationCheckResult importedResult = importedRec.getRoleResults().get(role1.getId());
        assertNotNull(importedResult);
        assertEquals(Verdict.BYPASS, importedResult.getVerdict());
        assertEquals(200, importedResult.getStatusCode());
        assertEquals(0.98, importedResult.getSimilarityScore(), 0.001);
    }
}

