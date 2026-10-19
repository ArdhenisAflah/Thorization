package thorization.model;

import java.awt.Color;

/**
 * Represents the classification verdict for an authorization test.
 */
public enum Verdict {
    BYPASS("BYPASS", new Color(220, 53, 69), new Color(255, 235, 238)),       // Red
    ENFORCED("ENFORCED", new Color(40, 167, 69), new Color(232, 245, 233)),    // Green
    INDETERMINABLE("MANUAL", new Color(255, 152, 0), new Color(255, 243, 224));   // Orange

    private final String displayName;
    private final Color foregroundColor;
    private final Color backgroundColor;

    Verdict(String displayName, Color foregroundColor, Color backgroundColor) {
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

    @Override
    public String toString() {
        return displayName;
    }
}

