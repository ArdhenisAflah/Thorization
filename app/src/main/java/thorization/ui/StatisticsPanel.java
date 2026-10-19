package thorization.ui;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PiePlot;
import org.jfree.data.general.DefaultPieDataset;
import thorization.core.EngineController;
import thorization.core.EngineListener;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;

/**
 * Tab 3: Visual statistics dashboard rendering an interactive JFreeChart pie chart
 * and metric cards with full native Dark Mode and Light Mode theme support.
 */
public class StatisticsPanel extends JPanel implements EngineListener {

    private final EngineController controller;

    private final DefaultPieDataset<String> dataset;
    private final JFreeChart chart;
    private final ChartPanel chartPanel;

    private JLabel totalAuditsLabel;
    private JLabel bypassCountLabel;
    private JLabel enforcedCountLabel;
    private JLabel manualCountLabel;

    public StatisticsPanel(EngineController controller) {
        this.controller = controller;
        this.controller.addListener(this);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 1. Metric Cards at the Top
        JPanel cardsPanel = createMetricCardsPanel();
        add(cardsPanel, BorderLayout.NORTH);

        // 2. Interactive JFreeChart Pie Chart in Center
        dataset = new DefaultPieDataset<>();
        dataset.setValue("Bypass (Vulnerable)", 0);
        dataset.setValue("Enforced (Protected)", 0);
        dataset.setValue("Check Manually", 0);

        chart = ChartFactory.createPieChart(
                "Authorization Test Distribution",
                dataset,
                true,   // Legend
                true,   // Tooltips
                false   // URLs
        );

        chartPanel = new ChartPanel(chart);
        chartPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder("Real-Time Verdict Analytics"),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)
        ));

        applyTheme();

        add(chartPanel, BorderLayout.CENTER);
        recalculateStatistics();
    }

    /**
     * Dynamically adapts the JFreeChart colors, plot background, labels, and legend
     * to match Burp Suite's active Look and Feel (Dark Mode or Light Mode).
     */
    public void applyTheme() {
        Color bg = UIManager.getColor("Panel.background");
        Color fg = UIManager.getColor("Label.foreground");
        if (bg == null) bg = new Color(43, 43, 43);
        if (fg == null) fg = new Color(220, 220, 220);

        // Chart Level Background and Title
        chart.setBackgroundPaint(bg);
        if (chart.getTitle() != null) {
            chart.getTitle().setPaint(fg);
            chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 14));
        }

        // Legend
        if (chart.getLegend() != null) {
            chart.getLegend().setBackgroundPaint(bg);
            chart.getLegend().setItemPaint(fg);
            chart.getLegend().setItemFont(new Font("SansSerif", Font.PLAIN, 12));
        }

        // Pie Plot Styling
        PiePlot<?> plot = (PiePlot<?>) chart.getPlot();
        plot.setBackgroundPaint(bg);
        plot.setOutlineVisible(false);
        plot.setShadowPaint(null);
        plot.setLabelBackgroundPaint(bg);
        plot.setLabelPaint(fg);
        plot.setLabelOutlinePaint(null);
        plot.setLabelShadowPaint(null);
        plot.setLabelLinkPaint(fg);
        plot.setLabelFont(new Font("SansSerif", Font.PLAIN, 12));

        // Consistent Security Verdict Slices
        plot.setSectionPaint("Bypass (Vulnerable)", new Color(220, 53, 69));      // Red
        plot.setSectionPaint("Enforced (Protected)", new Color(40, 167, 69));     // Green
        plot.setSectionPaint("Check Manually", new Color(255, 152, 0));          // Orange

        if (chartPanel != null) {
            chartPanel.setBackground(bg);
        }
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (chart != null) {
            applyTheme();
        }
    }

    private JPanel createMetricCardsPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 4, 10, 10));

        totalAuditsLabel = createMetricCard(panel, "Total Audited", "0", null);
        bypassCountLabel = createMetricCard(panel, "Bypass (Vulnerable)", "0 (0%)", new Color(220, 53, 69));
        enforcedCountLabel = createMetricCard(panel, "Enforced (Protected)", "0 (0%)", new Color(40, 167, 69));
        manualCountLabel = createMetricCard(panel, "Check Manually", "0 (0%)", new Color(255, 152, 0));

        return panel;
    }

    private JLabel createMetricCard(JPanel parent, String title, String initialVal, Color color) {
        JPanel card = new JPanel(new BorderLayout(5, 5));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        titleLabel.setForeground(Color.GRAY);

        JLabel valLabel = new JLabel(initialVal, SwingConstants.LEFT);
        valLabel.setFont(valLabel.getFont().deriveFont(Font.BOLD, 18f));
        if (color != null) {
            valLabel.setForeground(color);
        }

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.CENTER);

        parent.add(card);
        return valLabel;
    }

    public void recalculateStatistics() {
        int totalEvaluations = 0;
        int bypass = 0;
        int enforced = 0;
        int indeterminable = 0;

        List<AuditRecord> records = controller.getRecords();
        for (AuditRecord record : records) {
            for (AuthorizationCheckResult res : record.getRoleResults().values()) {
                totalEvaluations++;
                if (res.getVerdict() == Verdict.BYPASS) bypass++;
                else if (res.getVerdict() == Verdict.ENFORCED) enforced++;
                else indeterminable++;
            }
            if (record.getUnauthenticatedResult() != null) {
                totalEvaluations++;
                Verdict v = record.getUnauthenticatedResult().getVerdict();
                if (v == Verdict.BYPASS) bypass++;
                else if (v == Verdict.ENFORCED) enforced++;
                else indeterminable++;
            }
        }

        // Update Dataset
        dataset.setValue("Bypass (Vulnerable)", bypass);
        dataset.setValue("Enforced (Protected)", enforced);
        dataset.setValue("Check Manually", indeterminable);

        // Update Metric Cards
        totalAuditsLabel.setText(String.valueOf(records.size()));
        if (totalEvaluations > 0) {
            bypassCountLabel.setText(String.format("%d (%.1f%%)", bypass, (bypass * 100.0) / totalEvaluations));
            enforcedCountLabel.setText(String.format("%d (%.1f%%)", enforced, (enforced * 100.0) / totalEvaluations));
            manualCountLabel.setText(String.format("%d (%.1f%%)", indeterminable, (indeterminable * 100.0) / totalEvaluations));
        } else {
            bypassCountLabel.setText("0 (0%)");
            enforcedCountLabel.setText("0 (0%)");
            manualCountLabel.setText("0 (0%)");
        }
    }

    // --- EngineListener Callbacks ---

    @Override
    public void onAuditRecordAdded(AuditRecord record) {
        recalculateStatistics();
    }

    @Override
    public void onAuditRecordUpdated(AuditRecord record) {
        recalculateStatistics();
    }

    @Override
    public void onAuditRecordsRemoved(List<AuditRecord> removedRecords) {
        recalculateStatistics();
    }

    @Override
    public void onAuditRecordsCleared() {
        recalculateStatistics();
    }

    @Override
    public void onRolesChanged(List<UserRole> roles) {
        recalculateStatistics();
    }

    @Override
    public void onEngineStateChanged(boolean enabled) {
        // No visual dependency on active toggle in Statistics dashboard
    }

    @Override
    public void onFilterConfigChanged(thorization.model.FilterConfig config) {
        // No visual dependency on filter configuration in Statistics dashboard
    }

    @Override
    public void onPipelinesChanged(List<thorization.model.pipeline.Pipeline> pipelines) {
        // No visual dependency on pipelines in Statistics dashboard
    }
}
