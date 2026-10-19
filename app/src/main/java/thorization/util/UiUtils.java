package thorization.util;

import javax.swing.UIManager;
import java.awt.Color;

/**
 * Utility for theme-aware Swing colors and UI helpers.
 */
public class UiUtils {

    /**
     * Detects if the active Look and Feel is in Dark Mode based on perceived panel luminance.
     */
    public static boolean isDarkMode() {
        Color bg = UIManager.getColor("Panel.background");
        if (bg == null) return true;
        double lum = bg.getRed() * 0.299 + bg.getGreen() * 0.587 + bg.getBlue() * 0.114;
        return lum < 128;
    }

    /**
     * Returns a theme-aware surface card color with subtle accent tinting for dark and light modes.
     *
     * @param isRequestCard true for blue-tinted HTTP request card, false for amber-tinted condition branch card
     */
    public static Color getCardBackground(boolean isRequestCard) {
        if (isDarkMode()) {
            return isRequestCard ? new Color(34, 40, 48) : new Color(42, 38, 30);
        } else {
            return isRequestCard ? new Color(248, 250, 254) : new Color(255, 253, 245);
        }
    }
}
