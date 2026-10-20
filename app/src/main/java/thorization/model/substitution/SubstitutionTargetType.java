package thorization.model.substitution;

/**
 * Defines the HTTP request component targeted for parameter substitution.
 */
public enum SubstitutionTargetType {
    REST_PATH("REST Path Parameter", "Substitutes path segments (e.g., /api/users/{id}/profile)"),
    URL_QUERY("URL Query Parameter", "Substitutes query parameters (e.g., ?userId=100)"),
    JSON_BODY("JSON Body Field", "Substitutes JSON body fields (e.g., $.user.id)"),
    FORM_BODY("Form-Encoded Parameter", "Substitutes form fields (e.g., userId=100 in x-www-form-urlencoded)");

    private final String displayName;
    private final String description;

    SubstitutionTargetType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
