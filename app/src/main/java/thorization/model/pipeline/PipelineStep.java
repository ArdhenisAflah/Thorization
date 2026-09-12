package thorization.model.pipeline;

import java.util.UUID;

/**
 * Base abstract class for any execution step block in a Pipeline.
 */
public abstract class PipelineStep {
    private String id;
    private String name;
    private StepType stepType;
    private boolean enabled;

    public PipelineStep(String name, StepType stepType) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.stepType = stepType;
        this.enabled = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public StepType getStepType() {
        return stepType;
    }

    public void setStepType(StepType stepType) {
        this.stepType = stepType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}

