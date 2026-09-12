package thorization.model.pipeline;

import thorization.model.Verdict;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the complete result of a Pipeline execution across all its steps.
 */
public class PipelineExecutionResult {
    private String pipelineId;
    private String pipelineName;
    private Verdict finalVerdict;
    private boolean completed;
    private long totalDurationMs;
    private Map<String, String> resolvedVariables;
    private List<StepExecutionRecord> stepRecords;
    private String executionTrace;

    public PipelineExecutionResult(String pipelineId, String pipelineName) {
        this.pipelineId = pipelineId;
        this.pipelineName = pipelineName;
        this.finalVerdict = Verdict.INDETERMINABLE;
        this.completed = false;
        this.totalDurationMs = 0;
        this.resolvedVariables = new LinkedHashMap<>();
        this.stepRecords = new ArrayList<>();
        this.executionTrace = "";
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public Verdict getFinalVerdict() {
        return finalVerdict;
    }

    public void setFinalVerdict(Verdict finalVerdict) {
        this.finalVerdict = finalVerdict;
    }

    public boolean isCompleted() {
        return completed;
    }

    public void setCompleted(boolean completed) {
        this.completed = completed;
    }

    public long getTotalDurationMs() {
        return totalDurationMs;
    }

    public void setTotalDurationMs(long totalDurationMs) {
        this.totalDurationMs = totalDurationMs;
    }

    public Map<String, String> getResolvedVariables() {
        return resolvedVariables;
    }

    public void setResolvedVariables(Map<String, String> resolvedVariables) {
        this.resolvedVariables = resolvedVariables != null ? resolvedVariables : new LinkedHashMap<>();
    }

    public void addResolvedVariable(String name, String value) {
        this.resolvedVariables.put(name, value);
    }

    public List<StepExecutionRecord> getStepRecords() {
        return stepRecords;
    }

    public void setStepRecords(List<StepExecutionRecord> stepRecords) {
        this.stepRecords = stepRecords != null ? stepRecords : new ArrayList<>();
    }

    public void addStepRecord(StepExecutionRecord record) {
        if (record != null) {
            this.stepRecords.add(record);
        }
    }

    public String getExecutionTrace() {
        return executionTrace;
    }

    public void setExecutionTrace(String executionTrace) {
        this.executionTrace = executionTrace;
    }
}

