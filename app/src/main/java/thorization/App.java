package thorization;

import thorization.core.EngineController;
import thorization.ui.MainTabbedPane;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Standalone GUI preview runner for development and offline UI testing.
 */
public class App {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            JFrame frame = new JFrame("Thorization — Multi-Role Authorization & BOLA Testing Tool (Standalone Preview)");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1100, 750);
            frame.setLocationRelativeTo(null);

            EngineController controller = new EngineController(null);
            MainTabbedPane mainTabbedPane = new MainTabbedPane(null, controller);

            frame.setContentPane(mainTabbedPane);
            frame.setVisible(true);
        });
    }
}
