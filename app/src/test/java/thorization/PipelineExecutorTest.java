package thorization;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import thorization.core.DetectionEngine;
import thorization.core.pipeline.JsonPathExtractor;
import thorization.core.pipeline.PipelineExecutor;
import thorization.core.pipeline.VariableResolver;
import thorization.export.SessionExporter;
import thorization.export.SessionImporter;
import thorization.model.AuditRecord;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.model.pipeline.ConditionBranch;
import thorization.model.pipeline.ExtractionRule;
import thorization.model.pipeline.ExtractionSource;
import thorization.model.pipeline.ExtractionType;
import thorization.model.pipeline.Pipeline;
import thorization.model.pipeline.PipelineExecutionResult;
import thorization.model.pipeline.RequestStep;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PipelineExecutorTest {

    @Test
    void testJsonPathExtractor() {
        String json = "{\n" +
                "  \"status\": \"success\",\n" +
                "  \"data\": [\n" +
                "    {\n" +
                "      \"id\": 523113,\n" +
                "      \"id_unique\": \"ID20231222100625\",\n" +
                "      \"kode_fasyankes\": \"P3578110203\"\n" +
                "    }\n" +
                "  ]\n" +
                "}";

        assertEquals("success", JsonPathExtractor.extractJsonPath(json, "$.status"));
        assertEquals("523113", JsonPathExtractor.extractJsonPath(json, "$.data[0].id"));
        assertEquals("ID20231222100625", JsonPathExtractor.extractJsonPath(json, "$.data[0].id_unique"));
        assertEquals("P3578110203", JsonPathExtractor.extractJsonPath(json, "$.data[0].kode_fasyankes"));

        String arrayRootJson = "[\n" +
                "  {\n" +
                "    \"id_unique\": \"ID20260824125737\",\n" +
                "    \"kode_fasyankes\": \"P3674020202\"\n" +
                "  }\n" +
                "]";

        assertEquals("ID20260824125737", JsonPathExtractor.extractJsonPath(arrayRootJson, "$[0].id_unique"));
        assertEquals("P3674020202", JsonPathExtractor.extractJsonPath(arrayRootJson, "$[0].kode_fasyankes"));
    }

    @Test
    void testRegexExtractor() {
        String url = "https://api.target.local/resource/list_data?upk=P3578110203&page=1";
        String extracted = JsonPathExtractor.extractRegex(url, "[?&]upk=([^&#]+)");
        assertEquals("P3578110203", extracted);

        String body = "{\"auth_token\": \"eyJhbGciOi...12345\"}";
        String token = JsonPathExtractor.extractRegex(body, "\"auth_token\"\\s*:\\s*\"([^\"]+)\"");
        assertEquals("eyJhbGciOi...12345", token);
    }

    @Test
    void testVariableResolver() {
        Map<String, String> vars = new HashMap<>();
        vars.put("PATIENT_ID", "ID20260824125737");
        vars.put("UPK_CODE", "P3674020202");

        String urlTemplate = "https://api.example.com/data?upk={{UPK_CODE}}&patient={{PATIENT_ID}}";
        String resolvedUrl = VariableResolver.resolve(urlTemplate, vars);
        assertEquals("https://api.example.com/data?upk=P3674020202&patient=ID20260824125737", resolvedUrl);

        String bodyTemplate = "{\"targetId\": \"{{PATIENT_ID}}\", \"fasyankes\": \"{{UPK_CODE}}\"}";
        String resolvedBody = VariableResolver.resolve(bodyTemplate, vars);
        assertEquals("{\"targetId\": \"ID20260824125737\", \"fasyankes\": \"P3674020202\"}", resolvedBody);
    }

    @Test
    void testPipelineExecutionFlow() {
        Pipeline pipeline = new Pipeline("BOLA Cross-Role Data Verification", "Tests if User 2 can read records created by User 1");

        RequestStep s1 = new RequestStep("Step 1: Create Record", "POST", "https://api.target.local/save", "ROLE_1");
        s1.setBodyTemplate("[\n  {\n    \"id_unique\": \"ID20260824125737\",\n    \"kode_fasyankes\": \"P3674020202\"\n  }\n]");

        ExtractionRule r1 = new ExtractionRule("PATIENT_ID", ExtractionSource.REQUEST_BODY, ExtractionType.JSON_PATH, "$[0].id_unique");
        ExtractionRule r2 = new ExtractionRule("UPK_CODE", ExtractionSource.REQUEST_BODY, ExtractionType.JSON_PATH, "$[0].kode_fasyankes");
        s1.addExtraction(r1);
        s1.addExtraction(r2);

        ConditionBranch branch = new ConditionBranch(
                "Step 2: Check Creation",
                ConditionBranch.ConditionType.VARIABLE_NOT_EMPTY,
                "PATIENT_ID",
                ConditionBranch.BranchAction.CONTINUE,
                ConditionBranch.BranchAction.STOP_PIPELINE
        );

        RequestStep s2 = new RequestStep("Step 3: Query Data (Attacker)", "GET", "https://api.target.local/list?upk={{UPK_CODE}}", "ROLE_2");

        pipeline.addStep(s1);
        pipeline.addStep(branch);
        pipeline.addStep(s2);

        List<UserRole> roles = new ArrayList<>();
        roles.add(new UserRole("Doctor A", "H: 1", ""));
        roles.add(new UserRole("Attacker B", "H: 2", ""));

        PipelineExecutor executor = new PipelineExecutor(null, new DetectionEngine());
        PipelineExecutionResult result = executor.execute(pipeline, roles, false);

        assertTrue(result.isCompleted());
        assertEquals(3, result.getStepRecords().size());
        assertEquals("ID20260824125737", result.getResolvedVariables().get("PATIENT_ID"));
        assertEquals("P3674020202", result.getResolvedVariables().get("UPK_CODE"));
    }

    @Test
    void testPipelineSessionExportAndImport(@TempDir Path tempDir) throws Exception {
        Pipeline pipeline = new Pipeline("Sample Pipeline", "Test scenario");
        RequestStep req = new RequestStep("Step 1", "POST", "https://api.com/items", "ROLE_1");
        req.setBodyTemplate("{\"id\": \"105\"}");
        req.addExtraction(new ExtractionRule("ITEM_ID", ExtractionSource.REQUEST_BODY, ExtractionType.JSON_PATH, "$.id"));

        ConditionBranch cb = new ConditionBranch("Check", ConditionBranch.ConditionType.STATUS_CODE_EQUALS, "200",
                ConditionBranch.BranchAction.MARK_VERDICT_BYPASS, ConditionBranch.BranchAction.STOP_PIPELINE);

        pipeline.addStep(req);
        pipeline.addStep(cb);

        List<Pipeline> pipelines = new ArrayList<>();
        pipelines.add(pipeline);

        List<UserRole> roles = new ArrayList<>();
        roles.add(new UserRole("Admin", "Cookie: adm=1", ""));

        List<AuditRecord> records = new ArrayList<>();
        AuditRecord rec = new AuditRecord(100, null, thorization.model.OriginalAction.PASSED, "Pipeline: Sample Pipeline");
        rec.setPipelineExecution(true);
        rec.setPipelineName("Sample Pipeline");
        records.add(rec);

        File target = tempDir.resolve("session_batch3.json").toFile();
        SessionExporter.exportToFile(target, records, roles, pipelines);

        SessionImporter.ImportResult imported = SessionImporter.importFromFile(target);
        assertNotNull(imported);
        assertEquals(1, imported.getPipelines().size());
        assertEquals("Sample Pipeline", imported.getPipelines().get(0).getName());
        assertEquals(2, imported.getPipelines().get(0).getSteps().size());

        RequestStep importedReq = (RequestStep) imported.getPipelines().get(0).getSteps().get(0);
        assertEquals("POST", importedReq.getMethod());
        assertEquals(1, importedReq.getExtractions().size());
        assertEquals("ITEM_ID", importedReq.getExtractions().get(0).getVariableName());

        assertEquals(1, imported.getRecords().size());
        assertTrue(imported.getRecords().get(0).isPipelineExecution());
        assertEquals("Sample Pipeline", imported.getRecords().get(0).getPipelineName());
    }
}

