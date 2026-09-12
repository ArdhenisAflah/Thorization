package thorization.model.pipeline;

import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encapsulates the execution trace, HTTP transaction, and extracted values for a single pipeline step.
 */
public class StepExecutionRecord {
    private String stepId;
    private String stepName;
    private StepType stepType;
    private String roleName;
    private int statusCode;
    private int responseLength;
    private long executionTimeMs;
    private boolean success;
    private String traceLog;
    private Map<String, String> extractedVars;

    private transient HttpRequestResponse httpRequestResponse;
    private transient HttpRequest request;
    private transient HttpResponse response;

    public StepExecutionRecord(String stepId, String stepName, StepType stepType) {
        this.stepId = stepId;
        this.stepName = stepName;
        this.stepType = stepType;
        this.extractedVars = new LinkedHashMap<>();
        this.success = true;
        this.traceLog = "";
    }

    public String getStepId() {
        return stepId;
    }

    public String getStepName() {
        return stepName;
    }

    public StepType getStepType() {
        return stepType;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public int getResponseLength() {
        return responseLength;
    }

    public void setResponseLength(int responseLength) {
        this.responseLength = responseLength;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getTraceLog() {
        return traceLog;
    }

    public void setTraceLog(String traceLog) {
        this.traceLog = traceLog;
    }

    public Map<String, String> getExtractedVars() {
        return extractedVars;
    }

    public void setExtractedVars(Map<String, String> extractedVars) {
        this.extractedVars = extractedVars != null ? extractedVars : new LinkedHashMap<>();
    }

    public void addExtractedVar(String name, String value) {
        this.extractedVars.put(name, value);
    }

    public HttpRequestResponse getHttpRequestResponse() {
        return httpRequestResponse;
    }

    public void setHttpRequestResponse(HttpRequestResponse httpRequestResponse) {
        this.httpRequestResponse = httpRequestResponse;
        if (httpRequestResponse != null) {
            this.request = httpRequestResponse.request();
            this.response = httpRequestResponse.response();
            if (httpRequestResponse.response() != null) {
                this.statusCode = httpRequestResponse.response().statusCode();
                this.responseLength = httpRequestResponse.response().body() != null
                        ? httpRequestResponse.response().body().length() : 0;
            }
        }
    }

    public HttpRequest getRequest() {
        return request;
    }

    public HttpResponse getResponse() {
        return response;
    }
}

