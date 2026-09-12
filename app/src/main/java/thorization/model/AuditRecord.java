package thorization.model;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import thorization.model.pipeline.StepExecutionRecord;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Master record containing the original transaction, original action status (USP 1),
 * traffic source tool (Proxy/Repeater/Pipeline), and all role replay outcomes.
 */
public class AuditRecord {
    private long id;
    private String timestamp;
    private String sourceTool;
    private String method;
    private String url;
    private String path;
    private int originalStatusCode;
    private int originalResponseLength;
    private boolean stateChanging;
    private OriginalAction originalAction;

    // Pipeline Studio Metadata (Batch 3)
    private boolean isPipelineExecution;
    private String pipelineName;
    private Map<String, String> pipelineVariables;
    private String pipelineTrace;
    private List<StepExecutionRecord> pipelineStepRecords;

    private transient HttpRequestResponse originalHttpRequestResponse;
    private transient HttpRequest originalRequest;
    private transient HttpResponse originalResponse;

    private Map<String, AuthorizationCheckResult> roleResults;
    private AuthorizationCheckResult unauthenticatedResult;

    // For JSON Export/Import
    private String serializedOriginalRequest;
    private String serializedOriginalResponse;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public AuditRecord(long id, HttpRequestResponse original) {
        this(id, original, OriginalAction.PASSED, "Proxy");
    }

    public AuditRecord(long id, HttpRequestResponse original, OriginalAction originalAction) {
        this(id, original, originalAction, "Proxy");
    }

    public AuditRecord(long id, HttpRequestResponse original, OriginalAction originalAction, String sourceTool) {
        this.id = id;
        this.timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        this.sourceTool = sourceTool != null ? sourceTool : "Proxy";
        this.roleResults = new LinkedHashMap<>();
        this.originalAction = originalAction != null ? originalAction : OriginalAction.PASSED;

        this.isPipelineExecution = sourceTool != null && sourceTool.startsWith("Pipeline");
        this.pipelineVariables = new LinkedHashMap<>();
        this.pipelineStepRecords = new ArrayList<>();
        this.pipelineTrace = "";

        if (original != null) {
            this.originalHttpRequestResponse = original;
            this.originalRequest = original.request();
            this.originalResponse = original.response();
            this.method = original.request() != null ? original.request().method() : "GET";
            this.url = original.request() != null ? original.request().url() : "";
            this.path = original.request() != null ? original.request().path() : "";
            this.originalStatusCode = original.response() != null ? original.response().statusCode() : 0;
            this.originalResponseLength = original.response() != null && original.response().body() != null
                    ? original.response().body().length() : 0;
        } else {
            this.method = "GET";
            this.url = "";
            this.path = "";
            this.originalStatusCode = 0;
            this.originalResponseLength = 0;
        }
        this.stateChanging = isMethodStateChanging(this.method);
    }

    public static boolean isMethodStateChanging(String method) {
        if (method == null) return false;
        String m = method.toUpperCase();
        return m.equals("POST") || m.equals("PUT") || m.equals("DELETE") || m.equals("PATCH");
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getSourceTool() {
        return sourceTool;
    }

    public void setSourceTool(String sourceTool) {
        this.sourceTool = sourceTool;
        this.isPipelineExecution = sourceTool != null && sourceTool.startsWith("Pipeline");
    }

    public boolean isPipelineExecution() {
        return isPipelineExecution;
    }

    public void setPipelineExecution(boolean pipelineExecution) {
        isPipelineExecution = pipelineExecution;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public void setPipelineName(String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public Map<String, String> getPipelineVariables() {
        return pipelineVariables;
    }

    public void setPipelineVariables(Map<String, String> pipelineVariables) {
        this.pipelineVariables = pipelineVariables != null ? pipelineVariables : new LinkedHashMap<>();
    }

    public String getPipelineTrace() {
        return pipelineTrace;
    }

    public void setPipelineTrace(String pipelineTrace) {
        this.pipelineTrace = pipelineTrace;
    }

    public List<StepExecutionRecord> getPipelineStepRecords() {
        return pipelineStepRecords;
    }

    public void setPipelineStepRecords(List<StepExecutionRecord> pipelineStepRecords) {
        this.pipelineStepRecords = pipelineStepRecords != null ? pipelineStepRecords : new ArrayList<>();
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
        this.stateChanging = isMethodStateChanging(method);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public int getOriginalStatusCode() {
        return originalStatusCode;
    }

    public void setOriginalStatusCode(int originalStatusCode) {
        this.originalStatusCode = originalStatusCode;
    }

    public int getOriginalResponseLength() {
        return originalResponseLength;
    }

    public void setOriginalResponseLength(int originalResponseLength) {
        this.originalResponseLength = originalResponseLength;
    }

    public boolean isStateChanging() {
        return stateChanging;
    }

    public void setStateChanging(boolean stateChanging) {
        this.stateChanging = stateChanging;
    }

    public OriginalAction getOriginalAction() {
        return originalAction;
    }

    public void setOriginalAction(OriginalAction originalAction) {
        this.originalAction = originalAction;
    }

    public HttpRequestResponse getOriginalHttpRequestResponse() {
        return originalHttpRequestResponse;
    }

    public void setOriginalHttpRequestResponse(HttpRequestResponse originalHttpRequestResponse) {
        this.originalHttpRequestResponse = originalHttpRequestResponse;
    }

    public HttpRequest getOriginalRequest() {
        return originalRequest;
    }

    public void setOriginalRequest(HttpRequest originalRequest) {
        this.originalRequest = originalRequest;
    }

    public HttpResponse getOriginalResponse() {
        return originalResponse;
    }

    public void setOriginalResponse(HttpResponse originalResponse) {
        this.originalResponse = originalResponse;
    }

    public Map<String, AuthorizationCheckResult> getRoleResults() {
        return roleResults;
    }

    public void setRoleResults(Map<String, AuthorizationCheckResult> roleResults) {
        this.roleResults = roleResults;
    }

    public void addRoleResult(String roleId, AuthorizationCheckResult result) {
        this.roleResults.put(roleId, result);
    }

    public AuthorizationCheckResult getUnauthenticatedResult() {
        return unauthenticatedResult;
    }

    public void setUnauthenticatedResult(AuthorizationCheckResult unauthenticatedResult) {
        this.unauthenticatedResult = unauthenticatedResult;
    }

    public String getSerializedOriginalRequest() {
        return serializedOriginalRequest;
    }

    public void setSerializedOriginalRequest(String serializedOriginalRequest) {
        this.serializedOriginalRequest = serializedOriginalRequest;
    }

    public String getSerializedOriginalResponse() {
        return serializedOriginalResponse;
    }

    public void setSerializedOriginalResponse(String serializedOriginalResponse) {
        this.serializedOriginalResponse = serializedOriginalResponse;
    }
}
