package thorization.core;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import thorization.core.pipeline.PipelineExecutor;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.FilterConfig;
import thorization.model.InterceptionMode;
import thorization.model.OriginalAction;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.model.pipeline.Pipeline;
import thorization.model.pipeline.PipelineExecutionResult;
import thorization.model.pipeline.StepExecutionRecord;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Master controller coordinating interception, background worker threads,
 * safe drop actions (USP 1), role execution ordering, Pipeline Studio workflows (Batch 3), and UI observers.
 */
public class EngineController {

    private final MontoyaApi api;
    private final DetectionEngine detectionEngine;
    private final ReplayEngine replayEngine;
    private final PipelineExecutor pipelineExecutor;
    private final FilterConfig filterConfig;

    private final List<UserRole> roles = new CopyOnWriteArrayList<>();
    private final List<AuditRecord> records = new CopyOnWriteArrayList<>();
    private final List<Pipeline> pipelines = new CopyOnWriteArrayList<>();
    private final List<EngineListener> listeners = new CopyOnWriteArrayList<>();

    private final ExecutorService executorService;
    private final AtomicLong recordIdCounter = new AtomicLong(1);
    private volatile boolean enabled = false;

    public EngineController(MontoyaApi api) {
        this.api = api;
        this.detectionEngine = new DetectionEngine();
        this.replayEngine = new ReplayEngine(api, detectionEngine);
        this.pipelineExecutor = new PipelineExecutor(api, detectionEngine);
        this.filterConfig = new FilterConfig();
        this.executorService = Executors.newFixedThreadPool(4);
    }

    public MontoyaApi getApi() {
        return api;
    }

    public PipelineExecutor getPipelineExecutor() {
        return pipelineExecutor;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        notifyEngineStateChanged(enabled);
    }

    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(FilterConfig filterConfig) {
        if (filterConfig != null) {
            this.filterConfig.setOnlyInScope(filterConfig.isOnlyInScope());
            this.filterConfig.setIgnoreStaticFiles(filterConfig.isIgnoreStaticFiles());
            this.filterConfig.setTestUnauthenticated(filterConfig.isTestUnauthenticated());
            this.filterConfig.setTargetMethods(filterConfig.getTargetMethods());
            this.filterConfig.setUrlIncludeRegex(filterConfig.getUrlIncludeRegex());
            this.filterConfig.setUrlExcludeRegex(filterConfig.getUrlExcludeRegex());
            this.filterConfig.setInterceptionMode(filterConfig.getInterceptionMode());
            this.filterConfig.setDropAllRequests(filterConfig.isDropAllRequests());
            this.filterConfig.setStateChangingMethods(filterConfig.getStateChangingMethods());
            notifyFilterConfigChanged();
        }
    }

    public InterceptionMode getInterceptionMode() {
        return filterConfig.getInterceptionMode();
    }

    public void setInterceptionMode(InterceptionMode mode) {
        filterConfig.setInterceptionMode(mode);
        notifyFilterConfigChanged();
    }

    public void setDropAllRequests(boolean dropAll) {
        filterConfig.setDropAllRequests(dropAll);
        notifyFilterConfigChanged();
    }

    // --- Role Management ---

    public List<UserRole> getRoles() {
        return Collections.unmodifiableList(roles);
    }

    public synchronized void addRole(UserRole role) {
        if (role != null) {
            roles.add(role);
            notifyRolesChanged();
        }
    }

    public synchronized void updateRole(UserRole role) {
        for (int i = 0; i < roles.size(); i++) {
            if (roles.get(i).getId().equals(role.getId())) {
                roles.set(i, role);
                notifyRolesChanged();
                return;
            }
        }
    }

    public synchronized void removeRole(String roleId) {
        roles.removeIf(r -> r.getId().equals(roleId));
        notifyRolesChanged();
    }

    public synchronized void moveRoleUp(int index) {
        if (index > 0 && index < roles.size()) {
            UserRole role = roles.remove(index);
            roles.add(index - 1, role);
            notifyRolesChanged();
        }
    }

    public synchronized void moveRoleDown(int index) {
        if (index >= 0 && index < roles.size() - 1) {
            UserRole role = roles.remove(index);
            roles.add(index + 1, role);
            notifyRolesChanged();
        }
    }

    public synchronized void setRolesOrder(List<UserRole> orderedRoles) {
        if (orderedRoles != null) {
            roles.clear();
            roles.addAll(orderedRoles);
            notifyRolesChanged();
        }
    }

    public synchronized void setRoles(List<UserRole> newRoles) {
        roles.clear();
        if (newRoles != null) {
            roles.addAll(newRoles);
        }
        notifyRolesChanged();
    }

    // --- Pipeline Management (Batch 3) ---

    public List<Pipeline> getPipelines() {
        return Collections.unmodifiableList(pipelines);
    }

    public synchronized void addPipeline(Pipeline pipeline) {
        if (pipeline != null) {
            pipelines.add(pipeline);
            notifyPipelinesChanged();
        }
    }

    public synchronized void updatePipeline(Pipeline pipeline) {
        for (int i = 0; i < pipelines.size(); i++) {
            if (pipelines.get(i).getId().equals(pipeline.getId())) {
                pipelines.set(i, pipeline);
                notifyPipelinesChanged();
                return;
            }
        }
    }

    public synchronized void removePipeline(String pipelineId) {
        pipelines.removeIf(p -> p.getId().equals(pipelineId));
        notifyPipelinesChanged();
    }

    public synchronized void setPipelines(List<Pipeline> newPipelines) {
        pipelines.clear();
        if (newPipelines != null) {
            pipelines.addAll(newPipelines);
        }
        notifyPipelinesChanged();
    }

    public void executePipeline(Pipeline pipeline, Consumer<PipelineExecutionResult> onComplete) {
        if (pipeline == null) return;

        executorService.submit(() -> {
            try {
                PipelineExecutionResult result = pipelineExecutor.execute(
                        pipeline,
                        new ArrayList<>(roles),
                        filterConfig.isTestUnauthenticated()
                );

                // Create and publish an AuditRecord to the main table
                long id = recordIdCounter.getAndIncrement();
                HttpRequestResponse sampleTx = null;
                for (StepExecutionRecord stepRec : result.getStepRecords()) {
                    if (stepRec.getHttpRequestResponse() != null) {
                        sampleTx = stepRec.getHttpRequestResponse();
                    }
                }

                AuditRecord pipelineRecord = new AuditRecord(id, sampleTx, OriginalAction.PASSED, "Pipeline: " + pipeline.getName());
                pipelineRecord.setPipelineExecution(true);
                pipelineRecord.setPipelineName(pipeline.getName());
                pipelineRecord.setPipelineVariables(result.getResolvedVariables());
                pipelineRecord.setPipelineTrace(result.getExecutionTrace());
                pipelineRecord.setPipelineStepRecords(result.getStepRecords());

                // Set verdict across roles or generic verdict
                Verdict verdict = result.getFinalVerdict();
                for (UserRole r : roles) {
                    AuthorizationCheckResult checkResult = new AuthorizationCheckResult(
                            r.getId(),
                            r.getName(),
                            verdict,
                            0,
                            0,
                            0.0,
                            result.getTotalDurationMs(),
                            "Pipeline Studio: " + pipeline.getName() + " -> " + verdict.name(),
                            null,
                            null
                    );
                    pipelineRecord.addRoleResult(r.getId(), checkResult);
                }

                records.add(pipelineRecord);
                notifyRecordAdded(pipelineRecord);

                if (onComplete != null) {
                    SwingUtilities.invokeLater(() -> onComplete.accept(result));
                }
            } catch (Exception e) {
                if (api != null && api.logging() != null) {
                    api.logging().logToError("Error during Pipeline execution: " + e.getMessage());
                }
            }
        });
    }

    // --- Record Management ---

    public List<AuditRecord> getRecords() {
        return Collections.unmodifiableList(records);
    }

    public void clearRecords() {
        records.clear();
        notifyRecordsCleared();
    }

    public void removeRecords(List<AuditRecord> toRemove) {
        if (toRemove != null && !toRemove.isEmpty()) {
            records.removeAll(toRemove);
            notifyRecordsRemoved(toRemove);
        }
    }

    public void submitAuditTask(HttpRequestResponse original) {
        submitAuditTask(original, OriginalAction.PASSED, false, "Proxy");
    }

    public void submitAuditTask(HttpRequestResponse original, OriginalAction action, boolean sync) {
        submitAuditTask(original, action, sync, "Proxy");
    }

    public void submitAuditTask(HttpRequestResponse original, OriginalAction action, boolean sync, String sourceTool) {
        if (!enabled || original == null || original.request() == null) {
            return;
        }

        String url = original.request().url();
        String method = original.request().method();

        if (filterConfig.isOnlyInScope() && api != null && api.scope() != null) {
            if (!api.scope().isInScope(url)) {
                return;
            }
        }

        if (filterConfig.getTargetMethods() != null && !filterConfig.getTargetMethods().contains(method.toUpperCase())) {
            return;
        }

        if (!filterConfig.matchesUrl(url)) {
            return;
        }

        long id = recordIdCounter.getAndIncrement();
        AuditRecord record = new AuditRecord(id, original, action, sourceTool);
        records.add(record);
        notifyRecordAdded(record);

        Runnable auditTask = () -> {
            try {
                replayEngine.executeAudit(record, new ArrayList<>(roles), filterConfig.isTestUnauthenticated());
                notifyRecordUpdated(record);
            } catch (Exception e) {
                if (api != null && api.logging() != null) {
                    api.logging().logToError("Error during background audit: " + e.getMessage());
                }
            }
        };

        if (sync) {
            auditTask.run();
        } else {
            executorService.submit(auditTask);
        }
    }

    public void retestRecord(AuditRecord record, List<UserRole> targetRoles, boolean testUnauth) {
        if (record == null) return;
        List<UserRole> activeRoles = targetRoles != null ? targetRoles : new ArrayList<>(roles);

        executorService.submit(() -> {
            try {
                replayEngine.executeAudit(record, activeRoles, testUnauth);
                notifyRecordUpdated(record);
            } catch (Exception e) {
                if (api != null && api.logging() != null) {
                    api.logging().logToError("Error during record re-test: " + e.getMessage());
                }
            }
        });
    }

    public void retestRecords(List<AuditRecord> targetRecords, List<UserRole> targetRoles, boolean testUnauth) {
        if (targetRecords == null) return;
        List<UserRole> activeRoles = targetRoles != null ? targetRoles : new ArrayList<>(roles);
        for (AuditRecord record : targetRecords) {
            executorService.submit(() -> {
                try {
                    replayEngine.executeAudit(record, activeRoles, testUnauth);
                    notifyRecordUpdated(record);
                } catch (Exception e) {
                    if (api != null && api.logging() != null) {
                        api.logging().logToError("Error during records re-test: " + e.getMessage());
                    }
                }
            });
        }
    }

    public void retestAll(List<UserRole> targetRoles, boolean testUnauth) {
        List<UserRole> activeRoles = targetRoles != null ? targetRoles : new ArrayList<>(roles);
        for (AuditRecord record : records) {
            executorService.submit(() -> {
                try {
                    replayEngine.executeAudit(record, activeRoles, testUnauth);
                    notifyRecordUpdated(record);
                } catch (Exception e) {
                    if (api != null && api.logging() != null) {
                        api.logging().logToError("Error during retest-all: " + e.getMessage());
                    }
                }
            });
        }
    }

    public synchronized void importRecords(List<AuditRecord> importedRecords, List<UserRole> importedRoles) {
        importRecords(importedRecords, importedRoles, null);
    }

    public synchronized void importRecords(List<AuditRecord> importedRecords, List<UserRole> importedRoles, List<Pipeline> importedPipelines) {
        if (importedRoles != null && !importedRoles.isEmpty()) {
            roles.clear();
            roles.addAll(importedRoles);
            notifyRolesChanged();
        }
        if (importedPipelines != null && !importedPipelines.isEmpty()) {
            pipelines.clear();
            pipelines.addAll(importedPipelines);
            notifyPipelinesChanged();
        }
        if (importedRecords != null && !importedRecords.isEmpty()) {
            records.clear();
            records.addAll(importedRecords);
            for (AuditRecord r : importedRecords) {
                notifyRecordAdded(r);
            }
        }
    }

    // --- Observers ---

    public void addListener(EngineListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public void removeListener(EngineListener listener) {
        listeners.remove(listener);
    }

    private void notifyRecordAdded(AuditRecord record) {
        SwingUtilities.invokeLater(() -> {
            for (EngineListener l : listeners) {
                l.onAuditRecordAdded(record);
            }
        });
    }

    private void notifyRecordUpdated(AuditRecord record) {
        SwingUtilities.invokeLater(() -> {
            for (EngineListener l : listeners) {
                l.onAuditRecordUpdated(record);
            }
        });
    }

    private void notifyRecordsRemoved(List<AuditRecord> removedRecords) {
        SwingUtilities.invokeLater(() -> {
            for (EngineListener l : listeners) {
                l.onAuditRecordsRemoved(removedRecords);
            }
        });
    }

    private void notifyRecordsCleared() {
        SwingUtilities.invokeLater(() -> {
            for (EngineListener l : listeners) {
                l.onAuditRecordsCleared();
            }
        });
    }

    private void notifyRolesChanged() {
        List<UserRole> snapshot = Collections.unmodifiableList(new ArrayList<>(roles));
        SwingUtilities.invokeLater(() -> {
            for (EngineListener l : listeners) {
                l.onRolesChanged(snapshot);
            }
        });
    }

    private void notifyPipelinesChanged() {
        List<Pipeline> snapshot = Collections.unmodifiableList(new ArrayList<>(pipelines));
        SwingUtilities.invokeLater(() -> {
            for (EngineListener l : listeners) {
                l.onPipelinesChanged(snapshot);
            }
        });
    }

    private void notifyEngineStateChanged(boolean enabled) {
        SwingUtilities.invokeLater(() -> {
            for (EngineListener l : listeners) {
                l.onEngineStateChanged(enabled);
            }
        });
    }

    private void notifyFilterConfigChanged() {
        SwingUtilities.invokeLater(() -> {
            for (EngineListener l : listeners) {
                l.onFilterConfigChanged(filterConfig);
            }
        });
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }
}
