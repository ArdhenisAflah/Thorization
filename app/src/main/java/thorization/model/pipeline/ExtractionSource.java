package thorization.model.pipeline;

/**
 * Defines where in the HTTP transaction to extract dynamic variable values.
 */
public enum ExtractionSource {
    REQUEST_BODY("Request Body"),
    RESPONSE_BODY("Response Body"),
    REQUEST_HEADER("Request Header"),
    RESPONSE_HEADER("Response Header"),
    URL_QUERY_PARAM("URL Query Parameter");

    private final String displayName;

    ExtractionSource(String displayName) {
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

