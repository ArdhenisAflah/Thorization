package thorization.ai;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thorization.ui.components.VerdictCellRenderer;

import javax.swing.JLabel;
import javax.swing.JTable;
import java.awt.Color;

import static org.junit.jupiter.api.Assertions.*;

class VerdictCellRendererAiTest {

    @BeforeAll
    static void setupHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void testRenderAiVerdictEnums() {
        VerdictCellRenderer renderer = new VerdictCellRenderer();
        JTable table = new JTable();

        // PENDING
        JLabel pendingLabel = (JLabel) renderer.getTableCellRendererComponent(table, AiVerdict.PENDING, false, false, 0, 0);
        assertEquals("⌛ PENDING", pendingLabel.getText());
        assertEquals(AiVerdict.PENDING.getForegroundColor(), pendingLabel.getForeground());
        assertEquals(AiVerdict.PENDING.getBackgroundColor(), pendingLabel.getBackground());

        // VALIDATED_ACCESS
        JLabel validatedLabel = (JLabel) renderer.getTableCellRendererComponent(table, AiVerdict.VALIDATED_ACCESS, false, false, 0, 0);
        assertEquals("VALIDATED_ACCESS", validatedLabel.getText());
        assertEquals(AiVerdict.VALIDATED_ACCESS.getForegroundColor(), validatedLabel.getForeground());
        assertEquals(AiVerdict.VALIDATED_ACCESS.getBackgroundColor(), validatedLabel.getBackground());

        // ACCESS_DENIED
        JLabel deniedLabel = (JLabel) renderer.getTableCellRendererComponent(table, AiVerdict.ACCESS_DENIED, false, false, 0, 0);
        assertEquals("ACCESS_DENIED", deniedLabel.getText());
        assertEquals(AiVerdict.ACCESS_DENIED.getForegroundColor(), deniedLabel.getForeground());
        assertEquals(AiVerdict.ACCESS_DENIED.getBackgroundColor(), deniedLabel.getBackground());

        // INCONCLUSIVE
        JLabel inconvLabel = (JLabel) renderer.getTableCellRendererComponent(table, AiVerdict.INCONCLUSIVE, false, false, 0, 0);
        assertEquals("INCONCLUSIVE", inconvLabel.getText());
        assertEquals(AiVerdict.INCONCLUSIVE.getForegroundColor(), inconvLabel.getForeground());
        assertEquals(AiVerdict.INCONCLUSIVE.getBackgroundColor(), inconvLabel.getBackground());

        // ERROR
        JLabel errorLabel = (JLabel) renderer.getTableCellRendererComponent(table, AiVerdict.ERROR, false, false, 0, 0);
        assertEquals("ERROR", errorLabel.getText());
        assertEquals(AiVerdict.ERROR.getForegroundColor(), errorLabel.getForeground());
        assertEquals(AiVerdict.ERROR.getBackgroundColor(), errorLabel.getBackground());
    }

    @Test
    void testRenderAiVerdictStrings() {
        VerdictCellRenderer renderer = new VerdictCellRenderer();
        JTable table = new JTable();

        JLabel pendingStr = (JLabel) renderer.getTableCellRendererComponent(table, "Pending...", false, false, 0, 0);
        assertEquals("⌛ PENDING", pendingStr.getText());
        assertEquals(new Color(0, 123, 255), pendingStr.getForeground());

        JLabel bypassStr = (JLabel) renderer.getTableCellRendererComponent(table, "VALIDATED_ACCESS", false, false, 0, 0);
        assertEquals(new Color(220, 53, 69), bypassStr.getForeground());

        JLabel deniedStr = (JLabel) renderer.getTableCellRendererComponent(table, "ACCESS_DENIED", false, false, 0, 0);
        assertEquals(new Color(40, 167, 69), deniedStr.getForeground());
    }
}
