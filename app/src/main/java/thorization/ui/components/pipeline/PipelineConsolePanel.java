package thorization.ui.components.pipeline;

import thorization.model.Verdict;
import thorization.model.pipeline.PipelineExecutionResult;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Map;

/**
 * Live console panel displaying step-by-step pipeline execution logs,
 * resolved dynamic variables table, and final BOLA authorization verdict.
 */
public class PipelineConsolePanel extends JPanel {

    private final JLabel statusLabel;
    private final JLabel verdictBadge;
    private final JTextArea traceArea;
    private final DefaultTableModel variablesTableModel;

    public PipelineConsolePanel() {
        setLayout(new BorderLayout(5, 5));
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        // --- Top Status Header ---
        JPanel topBar = new JPanel(new BorderLayout(5, 0));
        topBar.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        topBar.setBackground(new Color(240, 243, 246));

        statusLabel = new JLabel("Status: Idle — Select a pipeline and click [▶ Run Pipeline]");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 12f));

        verdictBadge = new JLabel(" VERDICT: PENDING ");
        verdictBadge.setOpaque(true);
        verdictBadge.setBackground(Color.GRAY);
        verdictBadge.setForeground(Color.WHITE);
        verdictBadge.setFont(verdictBadge.getFont().deriveFont(Font.BOLD, 12f));
        verdictBadge.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));

        topBar.add(statusLabel, BorderLayout.WEST);
        topBar.add(verdictBadge, BorderLayout.EAST);
        add(topBar, BorderLayout.NORTH);

        // --- Center Split: Trace Log + Variables Table ---
        traceArea = new JTextArea();
        traceArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        traceArea.setEditable(false);
        traceArea.setText("Thorization Pipeline Console ready.\nBuild steps above and execute to view live traces.\n");

        JScrollPane traceScroll = new JScrollPane(traceArea);
        traceScroll.setBorder(BorderFactory.createTitledBorder("Step-by-Step Execution Trace"));

        String[] varCols = {"Variable Name", "Resolved Value"};
        variablesTableModel = new DefaultTableModel(varCols, 0);
        JTable variablesTable = new JTable(variablesTableModel);
        JScrollPane varScroll = new JScrollPane(variablesTable);
        varScroll.setBorder(BorderFactory.createTitledBorder("Resolved Dynamic Variables"));

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, traceScroll, varScroll);
        splitPane.setResizeWeight(0.65);
        add(splitPane, BorderLayout.CENTER);
    }

    public void displayResult(PipelineExecutionResult result) {
        if (result == null) return;

        statusLabel.setText(String.format("Status: Completed in %d ms | Steps Executed: %d",
                result.getTotalDurationMs(), result.getStepRecords().size()));

        Verdict verdict = result.getFinalVerdict();
        verdictBadge.setText(" VERDICT: " + verdict.name() + " ");
        verdictBadge.setBackground(verdict.getBackgroundColor());
        verdictBadge.setForeground(verdict.getForegroundColor());

        traceArea.setText(result.getExecutionTrace());
        traceArea.setCaretPosition(0);

        variablesTableModel.setRowCount(0);
        if (result.getResolvedVariables() != null) {
            for (Map.Entry<String, String> entry : result.getResolvedVariables().entrySet()) {
                variablesTableModel.addRow(new Object[]{"{{" + entry.getKey() + "}}", entry.getValue()});
            }
        }
    }

    public void setRunning(String pipelineName) {
        statusLabel.setText("Status: Executing \"" + pipelineName + "\" in background...");
        verdictBadge.setText(" RUNNING... ");
        verdictBadge.setBackground(new Color(255, 152, 0));
        traceArea.setText("Executing pipeline: " + pipelineName + "...\n");
        variablesTableModel.setRowCount(0);
    }
}
