package thorization.ui.components;

import thorization.model.OriginalAction;
import thorization.model.Verdict;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;

/**
 * Custom table cell renderer that displays authorization verdicts, OriginalAction statuses,
 * and Pipeline execution source tags as stylish color badges.
 */
public class VerdictCellRenderer extends DefaultTableCellRenderer {

    private static final Color PIPELINE_BG = new Color(243, 230, 255);
    private static final Color PIPELINE_FG = new Color(112, 34, 194);
    private static final Color REPEATER_FG = new Color(230, 115, 0);
    private static final Color PROXY_FG = new Color(26, 115, 232);

    public VerdictCellRenderer() {
        setHorizontalAlignment(SwingConstants.CENTER);
        setFont(getFont().deriveFont(Font.BOLD, 12f));
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
                                                   boolean isSelected, boolean hasFocus,
                                                   int row, int column) {
        JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

        if (value instanceof thorization.ai.AiVerdict aiVerdict) {
            label.setText(aiVerdict == thorization.ai.AiVerdict.PENDING ? "⌛ PENDING" : aiVerdict.getDisplayName());
            if (!isSelected) {
                label.setForeground(aiVerdict.getForegroundColor());
                label.setBackground(aiVerdict.getBackgroundColor());
            }
        } else if (value instanceof Verdict verdict) {
            label.setText(verdict.getDisplayName());
            if (!isSelected) {
                label.setForeground(verdict.getForegroundColor());
                label.setBackground(verdict.getBackgroundColor());
            }
        } else if (value instanceof OriginalAction action) {
            label.setText(action.getLabel());
            if (!isSelected) {
                label.setForeground(action.getBadgeColor());
                label.setBackground(table.getBackground());
            }
        } else if (value != null) {
            String text = value.toString();
            label.setText(text);

            if (!isSelected) {
                if ("Pending...".equalsIgnoreCase(text) || "PENDING".equalsIgnoreCase(text) || text.contains("PENDING")) {
                    label.setText("⌛ PENDING");
                    label.setForeground(new Color(0, 123, 255));
                    label.setBackground(new Color(235, 245, 255));
                } else if ("VALIDATED_ACCESS".equalsIgnoreCase(text)) {
                    label.setForeground(new Color(220, 53, 69));
                    label.setBackground(new Color(255, 235, 238));
                } else if ("ACCESS_DENIED".equalsIgnoreCase(text)) {
                    label.setForeground(new Color(40, 167, 69));
                    label.setBackground(new Color(232, 245, 233));
                } else if ("INCONCLUSIVE".equalsIgnoreCase(text)) {
                    label.setForeground(new Color(255, 152, 0));
                    label.setBackground(new Color(255, 243, 224));
                } else if ("ERROR".equalsIgnoreCase(text)) {
                    label.setForeground(new Color(178, 34, 34));
                    label.setBackground(new Color(255, 228, 225));
                } else if (text.startsWith("Pipeline") || text.contains("⚡")) {
                    label.setText("⚡ " + text);
                    label.setForeground(PIPELINE_FG);
                    label.setBackground(PIPELINE_BG);
                } else if ("Repeater".equalsIgnoreCase(text)) {
                    label.setForeground(REPEATER_FG);
                    label.setBackground(table.getBackground());
                } else if ("Proxy".equalsIgnoreCase(text)) {
                    label.setForeground(PROXY_FG);
                    label.setBackground(table.getBackground());
                } else if ("Disabled".equalsIgnoreCase(text)) {
                    label.setForeground(Color.GRAY);
                    label.setBackground(table.getBackground());
                } else {
                    label.setForeground(table.getForeground());
                    label.setBackground(table.getBackground());
                }
            }
        } else {
            label.setText("-");
            if (!isSelected) {
                label.setForeground(table.getForeground());
                label.setBackground(table.getBackground());
            }
        }

        return label;
    }
}
