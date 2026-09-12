package thorization.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.OriginalAction;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.model.pipeline.ConditionBranch;
import thorization.model.pipeline.ExtractionRule;
import thorization.model.pipeline.ExtractionSource;
import thorization.model.pipeline.ExtractionType;
import thorization.model.pipeline.Pipeline;
import thorization.model.pipeline.PipelineStep;
import thorization.model.pipeline.RequestStep;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handles deserializing JSON audit session files into model objects, roles, and pipeline scenarios.
 */
public class SessionImporter {

    public static ImportResult importFromFile(File sourceFile) throws IOException {
        try (FileReader reader = new FileReader(sourceFile, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            // 1. Roles
            List<UserRole> roles = new ArrayList<>();
            if (root.has("roles")) {
                JsonArray rolesArray = root.getAsJsonArray("roles");
                for (JsonElement elem : rolesArray) {
                    JsonObject rObj = elem.getAsJsonObject();
                    UserRole role = new UserRole();
                    if (rObj.has("id")) role.setId(rObj.get("id").getAsString());
                    if (rObj.has("name")) role.setName(rObj.get("name").getAsString());
                    if (rObj.has("enabled")) role.setEnabled(rObj.get("enabled").getAsBoolean());
                    if (rObj.has("rawHeaders")) role.setRawHeaders(rObj.get("rawHeaders").getAsString());
                    if (rObj.has("rawCookies")) role.setRawCookies(rObj.get("rawCookies").getAsString());
                    roles.add(role);
                }
            }

            // 2. Pipelines (Batch 3)
            List<Pipeline> pipelines = new ArrayList<>();
            if (root.has("pipelines")) {
                JsonArray pipelinesArray = root.getAsJsonArray("pipelines");
                for (JsonElement elem : pipelinesArray) {
                    JsonObject pObj = elem.getAsJsonObject();
                    String id = pObj.has("id") ? pObj.get("id").getAsString() : "";
                    String name = pObj.has("name") ? pObj.get("name").getAsString() : "Imported Pipeline";
                    String desc = pObj.has("description") ? pObj.get("description").getAsString() : "";

                    Pipeline pipeline = new Pipeline(name, desc);
                    if (!id.isEmpty()) pipeline.setId(id);

                    if (pObj.has("steps")) {
                        JsonArray stepsArray = pObj.getAsJsonArray("steps");
                        for (JsonElement sElem : stepsArray) {
                            JsonObject sObj = sElem.getAsJsonObject();
                            String stepType = sObj.has("stepType") ? sObj.get("stepType").getAsString() : "REQUEST";
                            String sName = sObj.has("name") ? sObj.get("name").getAsString() : "Step";
                            boolean enabled = !sObj.has("enabled") || sObj.get("enabled").getAsBoolean();

                            if ("CONDITION_BRANCH".equalsIgnoreCase(stepType)) {
                                ConditionBranch.ConditionType cType = ConditionBranch.ConditionType.STATUS_CODE_EQUALS;
                                if (sObj.has("conditionType")) {
                                    try { cType = ConditionBranch.ConditionType.valueOf(sObj.get("conditionType").getAsString()); } catch (Exception ignored) {}
                                }
                                String expected = sObj.has("expectedValue") ? sObj.get("expectedValue").getAsString() : "200";
                                ConditionBranch.BranchAction onTrue = ConditionBranch.BranchAction.CONTINUE;
                                ConditionBranch.BranchAction onFalse = ConditionBranch.BranchAction.STOP_PIPELINE;
                                if (sObj.has("onTrueAction")) {
                                    try { onTrue = ConditionBranch.BranchAction.valueOf(sObj.get("onTrueAction").getAsString()); } catch (Exception ignored) {}
                                }
                                if (sObj.has("onFalseAction")) {
                                    try { onFalse = ConditionBranch.BranchAction.valueOf(sObj.get("onFalseAction").getAsString()); } catch (Exception ignored) {}
                                }
                                ConditionBranch cb = new ConditionBranch(sName, cType, expected, onTrue, onFalse);
                                if (sObj.has("failureNote")) cb.setFailureNote(sObj.get("failureNote").getAsString());
                                cb.setEnabled(enabled);
                                pipeline.addStep(cb);
                            } else {
                                String method = sObj.has("method") ? sObj.get("method").getAsString() : "GET";
                                String url = sObj.has("urlTemplate") ? sObj.get("urlTemplate").getAsString() : "";
                                String roleId = sObj.has("targetRoleId") ? sObj.get("targetRoleId").getAsString() : "";
                                RequestStep req = new RequestStep(sName, method, url, roleId);
                                if (sObj.has("headersTemplate")) req.setHeadersTemplate(sObj.get("headersTemplate").getAsString());
                                if (sObj.has("bodyTemplate")) req.setBodyTemplate(sObj.get("bodyTemplate").getAsString());
                                if (sObj.has("isTeardown")) req.setTeardown(sObj.get("isTeardown").getAsBoolean());
                                req.setEnabled(enabled);

                                if (sObj.has("extractions")) {
                                    JsonArray exArray = sObj.getAsJsonArray("extractions");
                                    for (JsonElement exElem : exArray) {
                                        JsonObject exObj = exElem.getAsJsonObject();
                                        String var = exObj.has("varName") ? exObj.get("varName").getAsString() : "VAR";
                                        ExtractionSource src = ExtractionSource.RESPONSE_BODY;
                                        ExtractionType t = ExtractionType.JSON_PATH;
                                        if (exObj.has("source")) {
                                            try { src = ExtractionSource.valueOf(exObj.get("source").getAsString()); } catch (Exception ignored) {}
                                        }
                                        if (exObj.has("type")) {
                                            try { t = ExtractionType.valueOf(exObj.get("type").getAsString()); } catch (Exception ignored) {}
                                        }
                                        String expr = exObj.has("expression") ? exObj.get("expression").getAsString() : "$.id";
                                        ExtractionRule rule = new ExtractionRule(var, src, t, expr);
                                        if (exObj.has("defaultValue")) rule.setDefaultValue(exObj.get("defaultValue").getAsString());
                                        req.addExtraction(rule);
                                    }
                                }
                                pipeline.addStep(req);
                            }
                        }
                    }
                    pipelines.add(pipeline);
                }
            }

            // 3. Records
            List<AuditRecord> records = new ArrayList<>();
            if (root.has("records")) {
                JsonArray recordsArray = root.getAsJsonArray("records");
                for (JsonElement elem : recordsArray) {
                    JsonObject recObj = elem.getAsJsonObject();
                    long id = recObj.has("id") ? recObj.get("id").getAsLong() : 1;
                    String method = recObj.has("method") ? recObj.get("method").getAsString() : "GET";
                    String url = recObj.has("url") ? recObj.get("url").getAsString() : "";
                    String path = recObj.has("path") ? recObj.get("path").getAsString() : "";
                    int origStatus = recObj.has("originalStatusCode") ? recObj.get("originalStatusCode").getAsInt() : 200;
                    int origLen = recObj.has("originalResponseLength") ? recObj.get("originalResponseLength").getAsInt() : 0;
                    String sourceTool = recObj.has("sourceTool") ? recObj.get("sourceTool").getAsString() : "Proxy";

                    AuditRecord record = createImportedRecord(id, method, url, path, origStatus, origLen, sourceTool);

                    if (recObj.has("originalAction")) {
                        try {
                            record.setOriginalAction(OriginalAction.valueOf(recObj.get("originalAction").getAsString()));
                        } catch (Exception ignored) {}
                    }

                    if (recObj.has("isPipelineExecution") && recObj.get("isPipelineExecution").getAsBoolean()) {
                        record.setPipelineExecution(true);
                        if (recObj.has("pipelineName")) record.setPipelineName(recObj.get("pipelineName").getAsString());
                        if (recObj.has("pipelineTrace")) record.setPipelineTrace(recObj.get("pipelineTrace").getAsString());
                    }

                    if (recObj.has("roleResults")) {
                        JsonObject roleResultsObj = recObj.getAsJsonObject("roleResults");
                        for (Map.Entry<String, JsonElement> entry : roleResultsObj.entrySet()) {
                            AuthorizationCheckResult result = deserializeResult(entry.getValue().getAsJsonObject());
                            record.addRoleResult(entry.getKey(), result);
                        }
                    }

                    if (recObj.has("unauthenticatedResult") && !recObj.get("unauthenticatedResult").isJsonNull()) {
                        AuthorizationCheckResult unauth = deserializeResult(recObj.getAsJsonObject("unauthenticatedResult"));
                        record.setUnauthenticatedResult(unauth);
                    }

                    records.add(record);
                }
            }

            return new ImportResult(records, roles, pipelines);
        }
    }

    private static AuditRecord createImportedRecord(long id, String method, String url, String path, int status, int len, String sourceTool) {
        return new ImportedAuditRecord(id, method, url, path, status, len, sourceTool);
    }

    private static AuthorizationCheckResult deserializeResult(JsonObject obj) {
        String roleId = obj.has("roleId") ? obj.get("roleId").getAsString() : "";
        String roleName = obj.has("roleName") ? obj.get("roleName").getAsString() : "";
        Verdict verdict = Verdict.INDETERMINABLE;
        if (obj.has("verdict")) {
            try {
                verdict = Verdict.valueOf(obj.get("verdict").getAsString());
            } catch (Exception ignored) {}
        }
        int statusCode = obj.has("statusCode") ? obj.get("statusCode").getAsInt() : 0;
        int responseLength = obj.has("responseLength") ? obj.get("responseLength").getAsInt() : 0;
        double similarityScore = obj.has("similarityScore") ? obj.get("similarityScore").getAsDouble() : 0.0;
        long execTime = obj.has("executionTimeMs") ? obj.get("executionTimeMs").getAsLong() : 0;
        String notes = obj.has("notes") ? obj.get("notes").getAsString() : "";

        return new AuthorizationCheckResult(
                roleId, roleName, verdict, statusCode, responseLength, similarityScore, execTime, notes, null, null
        );
    }

    public static class ImportResult {
        private final List<AuditRecord> records;
        private final List<UserRole> roles;
        private final List<Pipeline> pipelines;

        public ImportResult(List<AuditRecord> records, List<UserRole> roles) {
            this(records, roles, new ArrayList<>());
        }

        public ImportResult(List<AuditRecord> records, List<UserRole> roles, List<Pipeline> pipelines) {
            this.records = records;
            this.roles = roles;
            this.pipelines = pipelines != null ? pipelines : new ArrayList<>();
        }

        public List<AuditRecord> getRecords() {
            return records;
        }

        public List<UserRole> getRoles() {
            return roles;
        }

        public List<Pipeline> getPipelines() {
            return pipelines;
        }
    }

    private static class ImportedAuditRecord extends AuditRecord {
        private final long id;
        private final String method;
        private final String url;
        private final String path;
        private final int origStatus;
        private final int origLen;

        public ImportedAuditRecord(long id, String method, String url, String path, int status, int len, String sourceTool) {
            super(id, null, OriginalAction.PASSED, sourceTool);
            this.id = id;
            this.method = method;
            this.url = url;
            this.path = path;
            this.origStatus = status;
            this.origLen = len;
        }

        @Override public long getId() { return id; }
        @Override public String getMethod() { return method; }
        @Override public String getUrl() { return url; }
        @Override public String getPath() { return path; }
        @Override public int getOriginalStatusCode() { return origStatus; }
        @Override public int getOriginalResponseLength() { return origLen; }
    }
}
