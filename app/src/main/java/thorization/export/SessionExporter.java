package thorization.export;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.pipeline.ConditionBranch;
import thorization.model.pipeline.ExtractionRule;
import thorization.model.pipeline.Pipeline;
import thorization.model.pipeline.PipelineStep;
import thorization.model.pipeline.RequestStep;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Handles serializing audit session data, roles, pipeline scenarios, and results to JSON.
 */
public class SessionExporter {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static void exportToFile(File targetFile, List<AuditRecord> records, List<UserRole> roles) throws IOException {
        exportToFile(targetFile, records, roles, null);
    }

    public static void exportToFile(File targetFile, List<AuditRecord> records, List<UserRole> roles, List<Pipeline> pipelines) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", "1.2.0");
        root.addProperty("exportTimestamp", System.currentTimeMillis());

        // 1. Roles
        JsonArray rolesArray = new JsonArray();
        for (UserRole role : roles) {
            JsonObject roleObj = new JsonObject();
            roleObj.addProperty("id", role.getId());
            roleObj.addProperty("name", role.getName());
            roleObj.addProperty("enabled", role.isEnabled());
            roleObj.addProperty("rawHeaders", role.getRawHeaders());
            roleObj.addProperty("rawCookies", role.getRawCookies());
            rolesArray.add(roleObj);
        }
        root.add("roles", rolesArray);

        // 2. Pipelines: only export genuine user pipelines (omit unedited default sample pipeline)
        if (pipelines != null && !pipelines.isEmpty()) {
            JsonArray pipelinesArray = new JsonArray();
            for (Pipeline p : pipelines) {
                if (p.isSample()) {
                    continue; // Skip hallucinated/sample pipeline template
                }
                JsonObject pObj = new JsonObject();
                pObj.addProperty("id", p.getId());
                pObj.addProperty("name", p.getName());
                pObj.addProperty("description", p.getDescription());

                JsonArray stepsArray = new JsonArray();
                for (PipelineStep s : p.getSteps()) {
                    JsonObject stepObj = new JsonObject();
                    stepObj.addProperty("id", s.getId());
                    stepObj.addProperty("name", s.getName());
                    stepObj.addProperty("stepType", s.getStepType().name());
                    stepObj.addProperty("enabled", s.isEnabled());

                    if (s instanceof RequestStep) {
                        RequestStep req = (RequestStep) s;
                        stepObj.addProperty("method", req.getMethod());
                        stepObj.addProperty("urlTemplate", req.getUrlTemplate());
                        stepObj.addProperty("headersTemplate", req.getHeadersTemplate());
                        stepObj.addProperty("bodyTemplate", req.getBodyTemplate());
                        stepObj.addProperty("targetRoleId", req.getTargetRoleId());
                        stepObj.addProperty("isTeardown", req.isTeardown());

                        JsonArray extractArray = new JsonArray();
                        for (ExtractionRule r : req.getExtractions()) {
                            JsonObject rObj = new JsonObject();
                            rObj.addProperty("varName", r.getVariableName());
                            rObj.addProperty("source", r.getSource().name());
                            rObj.addProperty("type", r.getType().name());
                            rObj.addProperty("expression", r.getExpression());
                            rObj.addProperty("defaultValue", r.getDefaultValue());
                            extractArray.add(rObj);
                        }
                        stepObj.add("extractions", extractArray);
                    } else if (s instanceof ConditionBranch) {
                        ConditionBranch cb = (ConditionBranch) s;
                        stepObj.addProperty("conditionType", cb.getConditionType().name());
                        stepObj.addProperty("expectedValue", cb.getExpectedValue());
                        stepObj.addProperty("onTrueAction", cb.getOnTrueAction().name());
                        stepObj.addProperty("onFalseAction", cb.getOnFalseAction().name());
                        stepObj.addProperty("failureNote", cb.getFailureNote());
                    }
                    stepsArray.add(stepObj);
                }
                pObj.add("steps", stepsArray);
                pipelinesArray.add(pObj);
            }
            if (pipelinesArray.size() > 0) {
                root.add("pipelines", pipelinesArray);
            }
        }

        // 3. Records
        JsonArray recordsArray = new JsonArray();
        for (AuditRecord record : records) {
            JsonObject recObj = new JsonObject();
            recObj.addProperty("id", record.getId());
            recObj.addProperty("timestamp", record.getTimestamp());
            recObj.addProperty("sourceTool", record.getSourceTool());
            recObj.addProperty("method", record.getMethod());
            recObj.addProperty("url", record.getUrl());
            recObj.addProperty("originalStatusCode", record.getOriginalStatusCode());
            recObj.addProperty("originalResponseLength", record.getOriginalResponseLength());
            recObj.addProperty("originalAction", record.getOriginalAction() != null ? record.getOriginalAction().name() : "PASSED");

            // Pipeline Execution Details - only included if actual pipeline execution
            if (record.isPipelineExecution()) {
                recObj.addProperty("isPipelineExecution", true);
                if (record.getPipelineName() != null) {
                    recObj.addProperty("pipelineName", record.getPipelineName());
                }
                if (record.getPipelineTrace() != null) {
                    recObj.addProperty("pipelineTrace", record.getPipelineTrace());
                }
            }

            // Save requests / responses text if available
            String origReq = record.getOriginalRequest() != null ? record.getOriginalRequest().toString() : record.getSerializedOriginalRequest();
            if (origReq != null && !origReq.isEmpty()) {
                recObj.addProperty("originalRequest", origReq);
            }
            String origResp = record.getOriginalResponse() != null ? record.getOriginalResponse().toString() : record.getSerializedOriginalResponse();
            if (origResp != null && !origResp.isEmpty()) {
                recObj.addProperty("originalResponse", origResp);
            }

            // Roles Replay Results
            JsonObject roleResultsObj = new JsonObject();
            for (Map.Entry<String, AuthorizationCheckResult> entry : record.getRoleResults().entrySet()) {
                AuthorizationCheckResult res = entry.getValue();
                JsonObject resObj = serializeResult(res, false);
                roleResultsObj.add(entry.getKey(), resObj);
            }
            recObj.add("roleResults", roleResultsObj);

            // Unauthenticated Result
            if (record.getUnauthenticatedResult() != null) {
                recObj.add("unauthenticatedResult", serializeResult(record.getUnauthenticatedResult(), true));
            }

            recordsArray.add(recObj);
        }
        root.add("records", recordsArray);

        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static JsonObject serializeResult(AuthorizationCheckResult res, boolean isUnauth) {
        JsonObject resObj = new JsonObject();
        if (!isUnauth && res.getRoleName() != null) {
            resObj.addProperty("roleName", res.getRoleName());
        }
        resObj.addProperty("verdict", res.getVerdict() != null ? res.getVerdict().name() : "");
        resObj.addProperty("statusCode", res.getStatusCode());
        resObj.addProperty("responseLength", res.getResponseLength());
        resObj.addProperty("similarityScore", res.getSimilarityScore());
        resObj.addProperty("executionTimeMs", res.getExecutionTimeMs());
        if (res.getNotes() != null && !res.getNotes().isEmpty()) {
            resObj.addProperty("notes", res.getNotes());
        }
        String req = res.getRequest() != null ? res.getRequest().toString() : res.getSerializedRequest();
        if (req != null && !req.isEmpty()) {
            resObj.addProperty("request", req);
        }
        String resp = res.getResponse() != null ? res.getResponse().toString() : res.getSerializedResponse();
        if (resp != null && !resp.isEmpty()) {
            resObj.addProperty("response", resp);
        }
        return resObj;
    }
}
