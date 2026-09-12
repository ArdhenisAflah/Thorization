package thorization.model;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;

/**
 * Encapsulates the execution result and authorization verdict for a single role replay.
 */
public class AuthorizationCheckResult {
    private String roleId;
    private String roleName;
    private Verdict verdict;
    private int statusCode;
    private int responseLength;
    private double similarityScore;
    private long executionTimeMs;
    private String notes;
    private transient HttpRequest request;
    private transient HttpResponse response;

    public AuthorizationCheckResult() {
    }

    public AuthorizationCheckResult(String roleId, String roleName, Verdict verdict,
                                    int statusCode, int responseLength, double similarityScore,
                                    long executionTimeMs, String notes,
                                    HttpRequest request, HttpResponse response) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.verdict = verdict;
        this.statusCode = statusCode;
        this.responseLength = responseLength;
        this.similarityScore = similarityScore;
        this.executionTimeMs = executionTimeMs;
        this.notes = notes;
        this.request = request;
        this.response = response;
    }

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public void setVerdict(Verdict verdict) {
        this.verdict = verdict;
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

    public double getSimilarityScore() {
        return similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public HttpRequest getRequest() {
        return request;
    }

    public void setRequest(HttpRequest request) {
        this.request = request;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public void setResponse(HttpResponse response) {
        this.response = response;
    }
}

