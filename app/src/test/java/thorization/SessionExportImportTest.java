package thorization;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import burp.api.montoya.internal.MontoyaObjectFactory;
import burp.api.montoya.internal.ObjectFactoryLocator;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thorization.core.EngineController;
import thorization.export.SessionExporter;
import thorization.export.SessionImporter;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.OriginalAction;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.model.pipeline.Pipeline;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SessionExportImportTest {

    @BeforeAll
    static void initMontoyaFactory() {
        ObjectFactoryLocator.FACTORY = (MontoyaObjectFactory) Proxy.newProxyInstance(
                MontoyaObjectFactory.class.getClassLoader(),
                new Class<?>[]{MontoyaObjectFactory.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("httpRequest".equals(name) && args != null && args.length == 1 && args[0] instanceof String) {
                        return createMockHttpRequest((String) args[0]);
                    }
                    if ("httpResponse".equals(name) && args != null && args.length == 1 && args[0] instanceof String) {
                        return createMockHttpResponse((String) args[0]);
                    }
                    return null;
                }
        );
    }

    private static HttpRequest createMockHttpRequest(String raw) {
        return (HttpRequest) Proxy.newProxyInstance(
                HttpRequest.class.getClassLoader(),
                new Class<?>[]{HttpRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("toString".equals(name)) return raw;
                    if ("bodyToString".equals(name)) {
                        int idx = raw.indexOf("\r\n\r\n");
                        return idx != -1 ? raw.substring(idx + 4) : "";
                    }
                    if ("method".equals(name)) {
                        int idx = raw.indexOf(" ");
                        return idx != -1 ? raw.substring(0, idx) : "GET";
                    }
                    return null;
                }
        );
    }

    private static HttpResponse createMockHttpResponse(String raw) {
        return (HttpResponse) Proxy.newProxyInstance(
                HttpResponse.class.getClassLoader(),
                new Class<?>[]{HttpResponse.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("toString".equals(name)) return raw;
                    if ("bodyToString".equals(name)) {
                        int idx = raw.indexOf("\r\n\r\n");
                        if (idx != -1) return raw.substring(idx + 4);
                        idx = raw.indexOf("\n\n");
                        return idx != -1 ? raw.substring(idx + 2) : "";
                    }
                    return null;
                }
        );
    }

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

        HttpRequest testReq = HttpRequest.httpRequest("POST /api/users/profile HTTP/1.1\r\nHost: example.com\r\n\r\nhello");
        HttpResponse testResp = HttpResponse.httpResponse("HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nworld");
        rec.setOriginalRequest(testReq);
        rec.setOriginalResponse(testResp);

        AuthorizationCheckResult res = new AuthorizationCheckResult(
                role1.getId(), "User B", Verdict.BYPASS, 200, 450, 0.98, 120, "Vulnerable", testReq, testResp
        );
        rec.addRoleResult(role1.getId(), res);

        records.add(rec);

        // Export
        SessionExporter.exportToFile(jsonFile, records, roles);
        assertTrue(jsonFile.exists());
        assertTrue(jsonFile.length() > 0);

        // Verify JSON does not contain bloat / redundant keys
        try (FileReader reader = new FileReader(jsonFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject firstRec = root.getAsJsonArray("records").get(0).getAsJsonObject();
            assertFalse(firstRec.has("path"), "pruned 'path' should not be present");
            assertFalse(firstRec.has("isStateChanging"), "pruned 'isStateChanging' should not be present");
            assertFalse(firstRec.has("isPipelineExecution"), "false isPipelineExecution should be omitted");

            JsonObject roleRes = firstRec.getAsJsonObject("roleResults").getAsJsonObject(role1.getId());
            assertFalse(roleRes.has("roleId"), "duplicated 'roleId' in map should be omitted");
            assertTrue(roleRes.has("request"));
            assertTrue(roleRes.has("response"));
        }

        // Import
        SessionImporter.ImportResult imported = SessionImporter.importFromFile(jsonFile);
        assertEquals(1, imported.getRoles().size());
        assertEquals("User B", imported.getRoles().get(0).getName());

        assertEquals(1, imported.getRecords().size());
        AuditRecord importedRec = imported.getRecords().get(0);
        assertEquals("POST", importedRec.getMethod());
        assertEquals("https://example.com/api/users/profile", importedRec.getUrl());
        assertEquals(200, importedRec.getOriginalStatusCode());
        assertNotNull(importedRec.getOriginalRequest());
        assertEquals("hello", importedRec.getOriginalRequest().bodyToString());
        assertNotNull(importedRec.getOriginalResponse());
        assertEquals("world", importedRec.getOriginalResponse().bodyToString());

        assertEquals(1, importedRec.getRoleResults().size());
        AuthorizationCheckResult importedResult = importedRec.getRoleResults().get(role1.getId());
        assertNotNull(importedResult);
        assertEquals(Verdict.BYPASS, importedResult.getVerdict());
        assertEquals(200, importedResult.getStatusCode());
        assertNotNull(importedResult.getRequest(), "Role request editor should have valid HttpRequest");
        assertNotNull(importedResult.getResponse(), "Role response editor should have valid HttpResponse");
        assertEquals("hello", importedResult.getRequest().bodyToString());
        assertEquals("world", importedResult.getResponse().bodyToString());
    }

    @Test
    void testImportUserSampleSessionFile() throws IOException {
        File sessionFile = new File("/home/aflahh/Burpsuite-Extension/Test-ThorizationExportImpory/thorization_session_1792429898751.json");
        assertTrue(sessionFile.exists(), "User sample session file should exist");

        SessionImporter.ImportResult imported = SessionImporter.importFromFile(sessionFile);
        assertEquals(1, imported.getRoles().size());
        assertEquals("User", imported.getRoles().get(0).getName());

        assertEquals(1, imported.getRecords().size());
        AuditRecord rec = imported.getRecords().get(0);
        assertEquals(119, rec.getId());
        assertEquals("00:11:02", rec.getTimestamp(), "Original timestamp should be preserved");
        assertEquals("GET", rec.getMethod());
        assertEquals("https://ethol.pens.ac.id/mahasiswa/beranda", rec.getUrl());
        assertEquals(OriginalAction.DROPPED, rec.getOriginalAction());

        // Verify original request is reconstructed and stored
        assertNotNull(rec.getOriginalRequest(), "Original request must not be null");
        assertTrue(rec.getOriginalRequest().toString().contains("GET /mahasiswa/beranda"));
        assertNotNull(rec.getSerializedOriginalRequest());

        // Verify role results request & response are reconstructed and stored
        assertNotNull(rec.getRoleResults());
        assertEquals(1, rec.getRoleResults().size());
        AuthorizationCheckResult roleRes = rec.getRoleResults().get("d11f3d88-d82e-4283-8fe2-a751462d0ee0");
        assertNotNull(roleRes);
        assertEquals(Verdict.INDETERMINABLE, roleRes.getVerdict());
        assertEquals(200, roleRes.getStatusCode());
        assertEquals(732, roleRes.getResponseLength());
        assertNotNull(roleRes.getRequest(), "Role result request must not be null");
        assertNotNull(roleRes.getResponse(), "Role result response must not be null");
        assertTrue(roleRes.getRequest().toString().contains("Cookie: True"));
        assertTrue(roleRes.getResponse().bodyToString().contains("ETHOL"));

        // Verify unauthenticated result request & response are reconstructed and stored
        AuthorizationCheckResult unauth = rec.getUnauthenticatedResult();
        assertNotNull(unauth);
        assertEquals(Verdict.INDETERMINABLE, unauth.getVerdict());
        assertEquals(200, unauth.getStatusCode());
        assertEquals(732, unauth.getResponseLength());
        assertNotNull(unauth.getRequest(), "Unauthenticated result request must not be null");
        assertNotNull(unauth.getResponse(), "Unauthenticated result response must not be null");
        assertTrue(unauth.getResponse().bodyToString().contains("<!doctype html>"));
    }

    @Test
    void testSamplePipelineOmittedFromExport(@TempDir Path tempDir) throws IOException {
        File jsonFile = tempDir.resolve("no_sample_pipeline.json").toFile();

        List<Pipeline> pipelines = new ArrayList<>();
        Pipeline sample = new Pipeline("BOLA Cross-Role Data Verification", "Tests if User 2 can read records");
        sample.setSample(true);
        pipelines.add(sample);

        List<UserRole> roles = new ArrayList<>();
        List<AuditRecord> records = new ArrayList<>();

        SessionExporter.exportToFile(jsonFile, records, roles, pipelines);

        try (FileReader reader = new FileReader(jsonFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            assertFalse(root.has("pipelines"), "Sample pipeline should NOT be exported");
        }
    }

    @Test
    void testEngineControllerImportReplaceAndAppend() {
        EngineController controller = new EngineController(null);

        List<UserRole> roles1 = new ArrayList<>();
        roles1.add(new UserRole("Role1", "", ""));

        List<AuditRecord> records1 = new ArrayList<>();
        AuditRecord rec1 = new AuditRecord(1, null);
        records1.add(rec1);

        controller.importRecords(records1, roles1, null, true);
        assertEquals(1, controller.getRecords().size());
        assertEquals(1, controller.getRoles().size());

        // Append mode with second batch
        List<UserRole> roles2 = new ArrayList<>();
        roles2.add(new UserRole("Role2", "", ""));

        List<AuditRecord> records2 = new ArrayList<>();
        AuditRecord rec2 = new AuditRecord(2, null);
        records2.add(rec2);

        controller.importRecords(records2, roles2, null, false);
        assertEquals(2, controller.getRecords().size(), "Append should add records without clearing");
        assertEquals(2, controller.getRoles().size(), "Append should add unique roles");

        // Replace mode with third batch
        List<AuditRecord> records3 = new ArrayList<>();
        AuditRecord rec3 = new AuditRecord(10, null);
        records3.add(rec3);

        controller.importRecords(records3, roles1, null, true);
        assertEquals(1, controller.getRecords().size(), "Replace should clear previous records");
        assertEquals(10, controller.getRecords().get(0).getId());
    }
}
