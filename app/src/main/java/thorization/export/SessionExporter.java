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

        // 2. Pipelines (Batch 3)
        if (pipelines != null && !pipelines.isEmpty()) {
            JsonArray pipelinesArray = new JsonArray();
            for (Pipeline p : pipelines) {
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
            root.add("pipelines", pipelinesArray);
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
            recObj.addProperty("path", record.getPath());
            recObj.addProperty("originalStatusCode", record.getOriginalStatusCode());
            recObj.addProperty("originalResponseLength", record.getOriginalResponseLength());
            recObj.addProperty("isStateChanging", record.isStateChanging());
            recObj.addProperty("originalAction", record.getOriginalAction() != null ? record.getOriginalAction().name() : "PASSED");

            // Pipeline Execution Details
            recObj.addProperty("isPipelineExecution", record.isPipelineExecution());
            if (record.isPipelineExecution()) {
                recObj.addProperty("pipelineName", record.getPipelineName());
                recObj.addProperty("pipelineTrace", record.getPipelineTrace());
            }

            // Save requests / responses text if available
            if (record.getOriginalRequest() != null) {
                recObj.addProperty("originalRequest", record.getOriginalRequest().toString());
            }
            if (record.getOriginalResponse() != null) {
                recObj.addProperty("originalResponse", record.getOriginalResponse().toString());
            }

            // Roles Replay Results
            JsonObject roleResultsObj = new JsonObject();
            for (Map.Entry<String, AuthorizationCheckResult> entry : record.getRoleResults().entrySet()) {
                AuthorizationCheckResult res = entry.getValue();
                JsonObject resObj = serializeResult(res);
                roleResultsObj.add(entry.getKey(), resObj);
            }
            recObj.add("roleResults", roleResultsObj);

            // Unauthenticated Result
            if (record.getUnauthenticatedResult() != null) {
                recObj.add("unauthenticatedResult", serializeResult(record.getUnauthenticatedResult()));
            }

            recordsArray.add(recObj);
        }
        root.add("records", recordsArray);

        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            GSON.toJson(root, writer);
        }
    }

    private static JsonObject serializeResult(AuthorizationCheckResult res) {
        JsonObject resObj = new JsonObject();
        resObj.addProperty("roleId", res.getRoleId());
        resObj.addProperty("roleName", res.getRoleName());
        resObj.addProperty("verdict", res.getVerdict() != null ? res.getVerdict().name() : "");
        resObj.addProperty("statusCode", res.getStatusCode());
        resObj.addProperty("responseLength", res.getResponseLength());
        resObj.addProperty("similarityScore", res.getSimilarityScore());
        resObj.addProperty("executionTimeMs", res.getExecutionTimeMs());
        resObj.addProperty("notes", res.getNotes());
        if (res.getRequest() != null) {
            resObj.addProperty("request", res.getRequest().toString());
        }
        if (res.getResponse() != null) {
            resObj.addProperty("response", res.getResponse().toString());
        }
        return resObj;
    }
}
