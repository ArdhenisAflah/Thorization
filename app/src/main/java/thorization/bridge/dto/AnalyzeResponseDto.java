package thorization.bridge.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object representing the audit result returned to AI agents.
 * Provides high-level security verdicts (vulnerabilityDetected, vulnerableRoles, summary)
 * alongside granular role-by-role evaluations.
 */
public class AnalyzeResponseDto {
    private boolean success;
    private String message;
    private Long recordId;
    private long timestamp = System.currentTimeMillis();
    private String url;
    private String method;
    private int originalStatusCode;
    private int originalResponseLength;
    private boolean filteredOut;
    private String filterReason;

    // AI Agent high-level verdict flags
    private boolean vulnerabilityDetected;
    private List<String> vulnerableRoles = new ArrayList<>();
    private String summary;

    private Map<String, RoleVerdictDto> roleVerdicts = new LinkedHashMap<>();

    public static class RoleVerdictDto {
        private String roleId;
        private String roleName;
        private String verdict;
        private int statusCode;
        private int responseLength;
        private double similarityScore;
        private long responseTimeMs;
        private String note;
        private String aiVerdict;
        private String aiReason;

        public RoleVerdictDto() {}

        public RoleVerdictDto(String roleId, String roleName, String verdict, int statusCode,
                              int responseLength, double similarityScore, long responseTimeMs, String note) {
            this(roleId, roleName, verdict, statusCode, responseLength, similarityScore, responseTimeMs, note, null, null);
        }

        public RoleVerdictDto(String roleId, String roleName, String verdict, int statusCode,
                              int responseLength, double similarityScore, long responseTimeMs, String note,
                              String aiVerdict, String aiReason) {
            this.roleId = roleId;
            this.roleName = roleName;
            this.verdict = verdict;
            this.statusCode = statusCode;
            this.responseLength = responseLength;
            this.similarityScore = similarityScore;
            this.responseTimeMs = responseTimeMs;
            this.note = note;
            this.aiVerdict = aiVerdict;
            this.aiReason = aiReason;
        }

        public String getRoleId() { return roleId; }
        public String getRoleName() { return roleName; }
        public String getVerdict() { return verdict; }
        public int getStatusCode() { return statusCode; }
        public int getResponseLength() { return responseLength; }
        public double getSimilarityScore() { return similarityScore; }
        public long getResponseTimeMs() { return responseTimeMs; }
        public String getNote() { return note; }
        public String getAiVerdict() { return aiVerdict; }
        public void setAiVerdict(String aiVerdict) { this.aiVerdict = aiVerdict; }
        public String getAiReason() { return aiReason; }
        public void setAiReason(String aiReason) { this.aiReason = aiReason; }
    }

    public AnalyzeResponseDto() {}

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public int getOriginalStatusCode() { return originalStatusCode; }
    public void setOriginalStatusCode(int originalStatusCode) { this.originalStatusCode = originalStatusCode; }

    public int getOriginalResponseLength() { return originalResponseLength; }
    public void setOriginalResponseLength(int originalResponseLength) { this.originalResponseLength = originalResponseLength; }

    public boolean isFilteredOut() { return filteredOut; }
    public void setFilteredOut(boolean filteredOut) { this.filteredOut = filteredOut; }

    public String getFilterReason() { return filterReason; }
    public void setFilterReason(String filterReason) { this.filterReason = filterReason; }

    public boolean isVulnerabilityDetected() { return vulnerabilityDetected; }
    public void setVulnerabilityDetected(boolean vulnerabilityDetected) { this.vulnerabilityDetected = vulnerabilityDetected; }

    public List<String> getVulnerableRoles() { return vulnerableRoles; }
    public void setVulnerableRoles(List<String> vulnerableRoles) { this.vulnerableRoles = vulnerableRoles; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Map<String, RoleVerdictDto> getRoleVerdicts() { return roleVerdicts; }
    public void setRoleVerdicts(Map<String, RoleVerdictDto> roleVerdicts) { this.roleVerdicts = roleVerdicts; }

    public void addRoleVerdict(String key, RoleVerdictDto dto) {
        this.roleVerdicts.put(key, dto);
    }

    private AuditRecordSummaryDto.AiAnalysisSummaryDto aiAnalysis;

    public AuditRecordSummaryDto.AiAnalysisSummaryDto getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(AuditRecordSummaryDto.AiAnalysisSummaryDto aiAnalysis) { this.aiAnalysis = aiAnalysis; }
}
