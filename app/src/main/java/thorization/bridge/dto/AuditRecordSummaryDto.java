package thorization.bridge.dto;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Summary DTO of an audit transaction returned when querying /api/records.
 * Includes top-level vulnerability indicators for fast filtering by AI agents.
 */
public class AuditRecordSummaryDto {
    private long id;
    private long timestamp = System.currentTimeMillis();
    private String method;
    private String url;
    private String path;
    private String sourceTool;
    private String action;
    private int originalStatus;
    private int originalLength;
    private boolean vulnerabilityDetected;
    private List<String> vulnerableRoles = new ArrayList<>();
    private Map<String, String> verdicts = new LinkedHashMap<>();
    private AiAnalysisSummaryDto aiAnalysis;

    public static class AiAnalysisSummaryDto {
        private String verdict;
        private String reasoning;
        private String model;
        private long executionTimeMs;
        private long timestamp;
        private boolean cached;
        private Map<String, String> roleVerdicts = new LinkedHashMap<>();

        public AiAnalysisSummaryDto() {}

        public AiAnalysisSummaryDto(String verdict, String reasoning, String model,
                                    long executionTimeMs, long timestamp, boolean cached,
                                    Map<String, String> roleVerdicts) {
            this.verdict = verdict;
            this.reasoning = reasoning;
            this.model = model;
            this.executionTimeMs = executionTimeMs;
            this.timestamp = timestamp;
            this.cached = cached;
            if (roleVerdicts != null) {
                this.roleVerdicts.putAll(roleVerdicts);
            }
        }

        public String getVerdict() { return verdict; }
        public void setVerdict(String verdict) { this.verdict = verdict; }

        public String getReasoning() { return reasoning; }
        public void setReasoning(String reasoning) { this.reasoning = reasoning; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public long getExecutionTimeMs() { return executionTimeMs; }
        public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public boolean isCached() { return cached; }
        public void setCached(boolean cached) { this.cached = cached; }

        public Map<String, String> getRoleVerdicts() { return roleVerdicts; }
        public void setRoleVerdicts(Map<String, String> roleVerdicts) { this.roleVerdicts = roleVerdicts; }
    }

    public AuditRecordSummaryDto() {}

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getSourceTool() { return sourceTool; }
    public void setSourceTool(String sourceTool) { this.sourceTool = sourceTool; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public int getOriginalStatus() { return originalStatus; }
    public void setOriginalStatus(int originalStatus) { this.originalStatus = originalStatus; }

    public int getOriginalLength() { return originalLength; }
    public void setOriginalLength(int originalLength) { this.originalLength = originalLength; }

    public boolean isVulnerabilityDetected() { return vulnerabilityDetected; }
    public void setVulnerabilityDetected(boolean vulnerabilityDetected) { this.vulnerabilityDetected = vulnerabilityDetected; }

    public List<String> getVulnerableRoles() { return vulnerableRoles; }
    public void setVulnerableRoles(List<String> vulnerableRoles) { this.vulnerableRoles = vulnerableRoles; }

    public Map<String, String> getVerdicts() { return verdicts; }
    public void setVerdicts(Map<String, String> verdicts) { this.verdicts = verdicts; }

    public AiAnalysisSummaryDto getAiAnalysis() { return aiAnalysis; }
    public void setAiAnalysis(AiAnalysisSummaryDto aiAnalysis) { this.aiAnalysis = aiAnalysis; }
}
