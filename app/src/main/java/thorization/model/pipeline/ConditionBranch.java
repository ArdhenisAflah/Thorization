package thorization.model.pipeline;

/**
 * Pipeline step representing an IF/ELSE conditional branch or assertion rule.
 */
public class ConditionBranch extends PipelineStep {

    public enum ConditionType {
        STATUS_CODE_EQUALS("Status Code Equals"),
        BODY_CONTAINS("Body Contains Text/Variable"),
        BODY_MATCHES_REGEX("Body Matches Regex"),
        VARIABLE_NOT_EMPTY("Variable Is Not Empty");

        private final String label;
        ConditionType(String label) { this.label = label; }
        public String getLabel() { return label; }
        @Override public String toString() { return label; }
    }

    public enum BranchAction {
        CONTINUE("Proceed to Next Step"),
        MARK_VERDICT_BYPASS("Mark BYPASS & Stop"),
        MARK_VERDICT_ENFORCED("Mark ENFORCED & Stop"),
        STOP_PIPELINE("Abort Pipeline Execution");

        private final String label;
        BranchAction(String label) { this.label = label; }
        public String getLabel() { return label; }
        @Override public String toString() { return label; }
    }

    private ConditionType conditionType;
    private String expectedValue;
    private BranchAction onTrueAction;
    private BranchAction onFalseAction;
    private String failureNote;

    public ConditionBranch() {
        super("Condition Branch (IF / ELSE)", StepType.CONDITION_BRANCH);
        this.conditionType = ConditionType.STATUS_CODE_EQUALS;
        this.expectedValue = "200";
        this.onTrueAction = BranchAction.CONTINUE;
        this.onFalseAction = BranchAction.STOP_PIPELINE;
        this.failureNote = "Condition not met";
    }

    public ConditionBranch(String name, ConditionType type, String expectedValue, BranchAction onTrue, BranchAction onFalse) {
        super(name, StepType.CONDITION_BRANCH);
        this.conditionType = type != null ? type : ConditionType.STATUS_CODE_EQUALS;
        this.expectedValue = expectedValue != null ? expectedValue : "";
        this.onTrueAction = onTrue != null ? onTrue : BranchAction.CONTINUE;
        this.onFalseAction = onFalse != null ? onFalse : BranchAction.STOP_PIPELINE;
        this.failureNote = "Condition failed";
    }

    public ConditionType getConditionType() {
        return conditionType;
    }

    public void setConditionType(ConditionType conditionType) {
        this.conditionType = conditionType;
    }

    public String getExpectedValue() {
        return expectedValue;
    }

    public void setExpectedValue(String expectedValue) {
        this.expectedValue = expectedValue;
    }

    public BranchAction getOnTrueAction() {
        return onTrueAction;
    }

    public void setOnTrueAction(BranchAction onTrueAction) {
        this.onTrueAction = onTrueAction;
    }

    public BranchAction getOnFalseAction() {
        return onFalseAction;
    }

    public void setOnFalseAction(BranchAction onFalseAction) {
        this.onFalseAction = onFalseAction;
    }

    public String getFailureNote() {
        return failureNote;
    }

    public void setFailureNote(String failureNote) {
        this.failureNote = failureNote;
    }
}

