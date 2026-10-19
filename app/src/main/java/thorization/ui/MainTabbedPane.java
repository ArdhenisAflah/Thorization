package thorization.ui;

import burp.api.montoya.MontoyaApi;
import thorization.core.EngineController;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import java.awt.BorderLayout;

/**
 * Top-level Burp Suite Extension UI container holding the 4 dedicated tabs.
 */
public class MainTabbedPane extends JPanel {

    private final JTabbedPane tabbedPane;
    private final RolesPanel rolesPanel;
    private final TablePanel tablePanel;
    private final PipelineStudioPanel pipelineStudioPanel;
    private final StatisticsPanel statisticsPanel;

    public MainTabbedPane(MontoyaApi api, EngineController controller) {
        setLayout(new BorderLayout());

        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        // Tab 1: Roles Manager
        rolesPanel = new RolesPanel(controller);
        tabbedPane.addTab("Roles Manager", rolesPanel);

        // Tab 2: Interception & Analysis Table
        tablePanel = new TablePanel(api, controller, this);
        tabbedPane.addTab("Interception & Analysis Table", tablePanel);

        // Tab 3: Pipeline Studio (WIP / Future Development)
        pipelineStudioPanel = new PipelineStudioPanel(api, controller);
        tabbedPane.addTab("Pipeline Studio (WIP)", pipelineStudioPanel);

        // Tab 4: Statistics Dashboard
        statisticsPanel = new StatisticsPanel(controller);
        tabbedPane.addTab("Statistics Dashboard", statisticsPanel);

        // Default to Tab 2 (Analysis Table)
        tabbedPane.setSelectedIndex(1);

        add(tabbedPane, BorderLayout.CENTER);
    }

    public void switchToPipelineStudio() {
        tabbedPane.setSelectedIndex(2);
    }

    public RolesPanel getRolesPanel() {
        return rolesPanel;
    }

    public TablePanel getTablePanel() {
        return tablePanel;
    }

    public PipelineStudioPanel getPipelineStudioPanel() {
        return pipelineStudioPanel;
    }

    public StatisticsPanel getStatisticsPanel() {
        return statisticsPanel;
    }
}
