package thorization.model.pipeline;

/**
 * Identifies the functional type of a pipeline block step.
 */
public enum StepType {
    REQUEST("HTTP Request Step"),
    CONDITION_BRANCH("IF / ELSE Logic Branch"),
    TEARDOWN("Instance Cleanup / Teardown");

    private final String displayName;

    StepType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

