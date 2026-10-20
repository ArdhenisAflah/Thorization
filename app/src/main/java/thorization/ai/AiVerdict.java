package thorization.ai;

import java.awt.Color;
import java.util.Locale;

/**
 * Represents the AI Semantic Analysis classification verdict for an authorization check.
 */
public enum AiVerdict {

    PENDING("PENDING", new Color(0, 123, 255), new Color(235, 245, 255)),
    VALIDATED_ACCESS("VALIDATED_ACCESS", new Color(220, 53, 69), new Color(255, 235, 238)),
    ACCESS_DENIED("ACCESS_DENIED", new Color(40, 167, 69), new Color(232, 245, 233)),
    INCONCLUSIVE("INCONCLUSIVE", new Color(255, 152, 0), new Color(255, 243, 224)),
    ERROR("ERROR", new Color(178, 34, 34), new Color(255, 228, 225));

    private final String displayName;
    private final Color foregroundColor;
    private final Color backgroundColor;

    AiVerdict(String displayName, Color foregroundColor, Color backgroundColor) {
        this.displayName = displayName;
        this.foregroundColor = foregroundColor;
        this.backgroundColor = backgroundColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Color getForegroundColor() {
        return foregroundColor;
    }

    public Color getBackgroundColor() {
        return backgroundColor;
    }

    public boolean isFinal() {
        return this != PENDING;
    }

    public boolean isVulnerability() {
        return this == VALIDATED_ACCESS;
    }

    /**
     * Parses a raw string output from the LLM or UI input into an AiVerdict,
     * supporting synonyms and tolerant normalization.
     */
    public static AiVerdict fromString(String text) {
        if (text == null || text.isBlank()) {
            return INCONCLUSIVE;
        }

        String normalized = text.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[\"'`\\s_-]+", "_");

        if (normalized.contains("VALIDATED_ACCESS") ||
            normalized.contains("VALIDATED") ||
            normalized.contains("BYPASS") ||
            normalized.contains("VULNERABLE") ||
            normalized.contains("UNAUTHORIZED_ACCESS") ||
            normalized.contains("EXPLOITABLE")) {
            return VALIDATED_ACCESS;
        }

        if (normalized.contains("ACCESS_DENIED") ||
            normalized.contains("DENIED") ||
            normalized.contains("ENFORCED") ||
            normalized.contains("BLOCKED") ||
            normalized.contains("PROTECTED") ||
            normalized.contains("FORBIDDEN")) {
            return ACCESS_DENIED;
        }

        if (normalized.contains("PENDING") ||
            normalized.contains("RUNNING") ||
            normalized.contains("QUEUED") ||
            normalized.contains("PROCESSING")) {
            return PENDING;
        }

        if (normalized.contains("ERROR") ||
            normalized.contains("FAILED") ||
            normalized.contains("EXCEPTION") ||
            normalized.contains("TIMEOUT")) {
            return ERROR;
        }

        return INCONCLUSIVE;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
