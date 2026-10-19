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
}
