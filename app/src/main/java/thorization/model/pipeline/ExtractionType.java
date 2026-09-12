package thorization.model.pipeline;

/**
 * Defines the extraction strategy used to parse dynamic variable values.
 */
public enum ExtractionType {
    JSON_PATH("JSONPath (e.g. $.id or $.data[0].id)"),
    REGEX("Regex with Group (e.g. id=([0-9a-zA-Z_-]+))"),
    EXACT_HEADER("Exact Header / Parameter Name");

    private final String displayName;

    ExtractionType(String displayName) {
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

