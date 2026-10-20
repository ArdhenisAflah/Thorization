package thorization.model.substitution;

/**
 * Defines the data type enforcement for replaced parameter values,
 * particularly for JSON payloads to prevent schema mismatches.
 */
public enum SubstitutionDataType {
    AUTO("Auto-Detect / Match Original"),
    STRING("String (Quoted)"),
    INTEGER("Integer (Numeric)"),
    FLOAT("Floating Point (Numeric)"),
    BOOLEAN("Boolean (true/false)");

    private final String displayName;

    SubstitutionDataType(String displayName) {
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
