package thorization.model;

import java.awt.Color;

/**
 * Status indicator for how the original client request was handled.
 */
public enum OriginalAction {
    DROPPED("DROPPED", new Color(220, 53, 69)),
    PASSED("PASSED", new Color(40, 167, 69)),
    HELD_THEN_PASSED("HELD->PASSED", new Color(255, 152, 0));

    private final String label;
    private final Color badgeColor;

    OriginalAction(String label, Color badgeColor) {
        this.label = label;
        this.badgeColor = badgeColor;
    }

    public String getLabel() {
        return label;
    }

    public Color getBadgeColor() {
        return badgeColor;
    }

    @Override
    public String toString() {
        return label;
    }
}

