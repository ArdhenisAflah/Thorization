package thorization.bridge.dto;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for HTTP request analysis submissions from AI agents.
 * Supports flexible parameter naming (camelCase, snake_case, and aliases)
 * for seamless integration with LLMs and automated scripts.
 */
public class AnalyzeRequestDto {

    @SerializedName(value = "url", alternate = {"targetUrl", "target_url"})
    private String url;

    @SerializedName(value = "method", alternate = {"httpMethod", "http_method"})
    private String method;

    @SerializedName(value = "headers", alternate = {"headersMap", "headers_map"})
    private Map<String, String> headers;

    @SerializedName(value = "rawHeaders", alternate = {"raw_headers"})
    private String rawHeaders;

    @SerializedName(value = "body", alternate = {"data", "payload", "request_body"})
    private String body;

    @SerializedName(value = "rawRequest", alternate = {"raw_request", "httpRequest", "http_request"})
    private String rawRequest;

    @SerializedName(value = "bypassFilter", alternate = {"bypass_filter", "bypassRules", "bypass_rules"})
    private Boolean bypassFilter;

    @SerializedName(value = "testUnauthenticated", alternate = {"test_unauthenticated", "checkUnauthenticated", "check_unauthenticated"})
    private Boolean testUnauthenticated;

    @SerializedName(value = "sourceTool", alternate = {"source_tool", "caller", "agentName", "agent_name"})
    private String sourceTool;

    @SerializedName(value = "sync", alternate = {"synchronous", "isSync"})
    private Boolean sync;

    @SerializedName(value = "targetRoleIds", alternate = {"target_role_ids", "roles", "roleIds", "role_ids"})
    private List<String> targetRoleIds;

    public AnalyzeRequestDto() {}

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMethod() {
        return method != null ? method : "GET";
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getRawHeaders() {
        return rawHeaders;
    }

    public void setRawHeaders(String rawHeaders) {
        this.rawHeaders = rawHeaders;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public String getRawRequest() {
        return rawRequest;
    }

    public void setRawRequest(String rawRequest) {
        this.rawRequest = rawRequest;
    }

    public boolean isBypassFilter() {
        return Boolean.TRUE.equals(bypassFilter);
    }

    public void setBypassFilter(Boolean bypassFilter) {
        this.bypassFilter = bypassFilter;
    }

    public Boolean getTestUnauthenticated() {
        return testUnauthenticated;
    }

    public void setTestUnauthenticated(Boolean testUnauthenticated) {
        this.testUnauthenticated = testUnauthenticated;
    }

    public String getSourceTool() {
        return sourceTool != null && !sourceTool.trim().isEmpty() ? sourceTool.trim() : "AI-Agent";
    }

    public void setSourceTool(String sourceTool) {
        this.sourceTool = sourceTool;
    }

    public boolean isSync() {
        return sync == null || Boolean.TRUE.equals(sync);
    }

    public void setSync(Boolean sync) {
        this.sync = sync;
    }

    public List<String> getTargetRoleIds() {
        return targetRoleIds;
    }

    public void setTargetRoleIds(List<String> targetRoleIds) {
        this.targetRoleIds = targetRoleIds;
    }
}
