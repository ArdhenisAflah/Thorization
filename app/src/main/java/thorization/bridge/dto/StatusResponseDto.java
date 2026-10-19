package thorization.bridge.dto;

import java.util.Set;

/**
 * Data Transfer Object reporting extension status and active configuration to AI agents.
 */
public class StatusResponseDto {
    private String status;
    private String version;
    private boolean engineEnabled;
    private String interceptionMode;
    private boolean dropAllRequests;
    private int activeRolesCount;
    private int totalRecordsCount;
    private int pipelineCount;
    private FilterConfigSummary filterConfig;

    public static class FilterConfigSummary {
        private boolean onlyInScope;
        private boolean ignoreStaticFiles;
        private boolean testUnauthenticated;
        private Set<String> targetMethods;
        private Set<String> stateChangingMethods;
        private String urlIncludeRegex;
        private String urlExcludeRegex;

        public FilterConfigSummary() {}

        public FilterConfigSummary(boolean onlyInScope, boolean ignoreStaticFiles, boolean testUnauthenticated,
                                   Set<String> targetMethods, Set<String> stateChangingMethods,
                                   String urlIncludeRegex, String urlExcludeRegex) {
            this.onlyInScope = onlyInScope;
            this.ignoreStaticFiles = ignoreStaticFiles;
            this.testUnauthenticated = testUnauthenticated;
            this.targetMethods = targetMethods;
            this.stateChangingMethods = stateChangingMethods;
            this.urlIncludeRegex = urlIncludeRegex;
            this.urlExcludeRegex = urlExcludeRegex;
        }

        public boolean isOnlyInScope() { return onlyInScope; }
        public boolean isIgnoreStaticFiles() { return ignoreStaticFiles; }
        public boolean isTestUnauthenticated() { return testUnauthenticated; }
        public Set<String> getTargetMethods() { return targetMethods; }
        public Set<String> getStateChangingMethods() { return stateChangingMethods; }
        public String getUrlIncludeRegex() { return urlIncludeRegex; }
        public String getUrlExcludeRegex() { return urlExcludeRegex; }
    }

    public StatusResponseDto() {}

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public boolean isEngineEnabled() { return engineEnabled; }
    public void setEngineEnabled(boolean engineEnabled) { this.engineEnabled = engineEnabled; }

    public String getInterceptionMode() { return interceptionMode; }
    public void setInterceptionMode(String interceptionMode) { this.interceptionMode = interceptionMode; }

    public boolean isDropAllRequests() { return dropAllRequests; }
    public void setDropAllRequests(boolean dropAllRequests) { this.dropAllRequests = dropAllRequests; }

    public int getActiveRolesCount() { return activeRolesCount; }
    public void setActiveRolesCount(int activeRolesCount) { this.activeRolesCount = activeRolesCount; }

    public int getTotalRecordsCount() { return totalRecordsCount; }
    public void setTotalRecordsCount(int totalRecordsCount) { this.totalRecordsCount = totalRecordsCount; }

    public int getPipelineCount() { return pipelineCount; }
    public void setPipelineCount(int pipelineCount) { this.pipelineCount = pipelineCount; }

    public FilterConfigSummary getFilterConfig() { return filterConfig; }
    public void setFilterConfig(FilterConfigSummary filterConfig) { this.filterConfig = filterConfig; }
}
