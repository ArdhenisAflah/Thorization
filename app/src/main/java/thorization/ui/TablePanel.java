package thorization.ui;

import burp.api.montoya.MontoyaApi;
import thorization.core.EngineController;
import thorization.core.EngineListener;
import thorization.export.SessionExporter;
import thorization.export.SessionImporter;
import thorization.model.AuditRecord;
import thorization.model.FilterConfig;
import thorization.model.InterceptionMode;
import thorization.model.UserRole;
import thorization.model.pipeline.Pipeline;
import thorization.ui.components.AuditTableModel;
import thorization.ui.components.RequestResponseViewerPanel;
import thorization.ui.components.RetestRolesDialog;
import thorization.ui.components.VerdictCellRenderer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import burp.api.montoya.http.message.requests.HttpRequest;
import thorization.model.AuthorizationCheckResult;
import thorization.report.CsvReportGenerator;
import thorization.report.HtmlReportGenerator;
import thorization.util.CurlUtils;

/**
 * Tab 2: Primary dashboard for real-time authorization testing, request table,
 * re-testing specific/all URLs for new/selected roles, and session export/import.
 */
public class TablePanel extends JPanel implements EngineListener {

    private final MontoyaApi api;
    private final EngineController controller;
    private final MainTabbedPane mainTabs;

    private JToggleButton powerToggleBtn;
    private JLabel statusBadgeLabel;
    private JComboBox<InterceptionMode> modeDropdown;
    private JCheckBox dropAllQuickCheck;
    private JLabel countLabel;
    private JTextField searchFilterField;
    private JButton htmlReportBtn;
    private JButton csvExportBtn;
    private JButton exportBtn;
    private JButton importBtn;
    private JPopupMenu contextMenu;

    private AuditTableModel tableModel;
    private JTable auditTable;
    private TableRowSorter<AuditTableModel> rowSorter;
    private RequestResponseViewerPanel viewerPanel;

    public TablePanel(MontoyaApi api, EngineController controller) {
        this(api, controller, null);
    }

    public TablePanel(MontoyaApi api, EngineController controller, MainTabbedPane mainTabs) {
        this.api = api;
        this.controller = controller;
        this.mainTabs = mainTabs;
        this.controller.addListener(this);

        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // 1. Top Control Bar
        JPanel topToolbar = createTopToolbar();
        add(topToolbar, BorderLayout.NORTH);

        // 2. Center Split View (Table on top, Request/Response Viewers on bottom)
        JSplitPane splitPane = createCenterSplitPane();
        add(splitPane, BorderLayout.CENTER);

        // Synchronize initial roles
        tableModel.setRoles(controller.getRoles());
        viewerPanel.syncRoles(controller.getRoles());
        updateCellRenderers();
    }

    private JPanel createTopToolbar() {
        JPanel toolbar = new JPanel();
        toolbar.setLayout(new BoxLayout(toolbar, BoxLayout.Y_AXIS));
        toolbar.setBorder(BorderFactory.createEmptyBorder(4, 6, 6, 6));

        // -----------------------------------------------------------------
        // Row 1: Engine Controls, Safe Drop Mode, Clear | Record Count
        // -----------------------------------------------------------------
        JPanel row1 = new JPanel(new BorderLayout(8, 0));

        JPanel row1Left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        powerToggleBtn = new JToggleButton("OFF");
        powerToggleBtn.setPreferredSize(new Dimension(65, 26));
        powerToggleBtn.setFont(powerToggleBtn.getFont().deriveFont(Font.BOLD, 12f));
        powerToggleBtn.setBackground(new Color(230, 230, 230));
        powerToggleBtn.setToolTipText("Toggle Thorization traffic interception and auto-replay");
        powerToggleBtn.addActionListener(e -> {
            boolean active = powerToggleBtn.isSelected();
            controller.setEnabled(active);
            updatePowerButtonUI(active);
        });

        statusBadgeLabel = new JLabel("● Idle");
        statusBadgeLabel.setFont(statusBadgeLabel.getFont().deriveFont(Font.BOLD, 12f));
        statusBadgeLabel.setForeground(Color.GRAY);
        statusBadgeLabel.setToolTipText("Engine status: Idle");

        modeDropdown = new JComboBox<>(InterceptionMode.values());
        modeDropdown.setToolTipText("Interception Action Mode (Safe Drop Control)");
        modeDropdown.setSelectedItem(controller.getInterceptionMode());
        modeDropdown.addActionListener(e -> controller.setInterceptionMode((InterceptionMode) modeDropdown.getSelectedItem()));

        dropAllQuickCheck = new JCheckBox("Drop All");
        dropAllQuickCheck.setToolTipText("When checked, even GET requests are safely dropped in Burp Proxy while replaying");
        dropAllQuickCheck.setSelected(controller.getFilterConfig().isDropAllRequests());
        dropAllQuickCheck.addActionListener(e -> controller.setDropAllRequests(dropAllQuickCheck.isSelected()));

        JButton clearBtn = new JButton("Clear");
        clearBtn.setToolTipText("Clear all captured records in the table");
        clearBtn.addActionListener(e -> controller.clearRecords());

        row1Left.add(powerToggleBtn);
        row1Left.add(statusBadgeLabel);
        row1Left.add(new JLabel("Mode:"));
        row1Left.add(modeDropdown);
        row1Left.add(dropAllQuickCheck);
        row1Left.add(clearBtn);

        JPanel row1Right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        countLabel = new JLabel("Total: 0");
        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD, 12f));
        row1Right.add(countLabel);

        row1.add(row1Left, BorderLayout.WEST);
        row1.add(row1Right, BorderLayout.EAST);

        // -----------------------------------------------------------------
        // Row 2: Search Filter | Reporting & Session Tools (HTML/CSV/Export/Import)
        // -----------------------------------------------------------------
        JPanel row2 = new JPanel(new BorderLayout(8, 0));

        JPanel row2Left = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        row2Left.add(new JLabel("Search:"));
        searchFilterField = new JTextField(15);
        searchFilterField.setPreferredSize(new Dimension(160, 26));
        searchFilterField.setToolTipText("Filter term matching URL, method, status, or tool (string)");
        searchFilterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        row2Left.add(searchFilterField);

        JPanel row2Right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
        htmlReportBtn = new JButton("HTML Report");
        htmlReportBtn.setToolTipText("Generate standalone interactive offline HTML audit report");
        htmlReportBtn.addActionListener(e -> onExportHtmlReport());

        csvExportBtn = new JButton("CSV Export");
        csvExportBtn.setToolTipText("Export audit records and role verdicts to RFC 4180 CSV");
        csvExportBtn.addActionListener(e -> onExportCsvReport());

        exportBtn = new JButton("Export");
        exportBtn.setToolTipText("Export session records and settings (.json)");
        exportBtn.addActionListener(e -> onExportSession());

        importBtn = new JButton("Import");
        importBtn.setToolTipText("Import session records and settings (.json)");
        importBtn.addActionListener(e -> onImportSession());

        row2Right.add(htmlReportBtn);
        row2Right.add(csvExportBtn);
        row2Right.add(exportBtn);
        row2Right.add(importBtn);

        row2.add(row2Left, BorderLayout.WEST);
        row2.add(row2Right, BorderLayout.EAST);

        toolbar.add(row1);
        toolbar.add(Box.createVerticalStrut(3));
        toolbar.add(row2);

        return toolbar;
    }

    private JSplitPane createCenterSplitPane() {
        tableModel = new AuditTableModel();
        rowSorter = new TableRowSorter<>(tableModel);

        auditTable = new JTable(tableModel);
        auditTable.setRowSorter(rowSorter);
        auditTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        auditTable.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        auditTable.setRowHeight(24);

        // Selection listener to display selected transaction in the bottom viewers
        auditTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int viewRow = auditTable.getSelectedRow();
                if (viewRow >= 0) {
                    int modelRow = auditTable.convertRowIndexToModel(viewRow);
                    AuditRecord record = tableModel.getRecordAt(modelRow);
                    if (record != null) {
                        viewerPanel.displayRecord(record, controller.getRoles());
                    } else {
                        viewerPanel.clearViewers();
                    }
                } else {
                    viewerPanel.clearViewers();
                }
            }
        });

        // Key Listener: Delete key removes selected items
        auditTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE || e.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
                    onRemoveSelectedRecords();
                }
            }
        });

        // Right-Click Context Menu for re-testing, cURL copy, reporting and item removal
        contextMenu = new JPopupMenu();
        JMenuItem retestSelectedMenuItem = new JMenuItem("Re-Test Selected Request(s)...");
        retestSelectedMenuItem.addActionListener(e -> onRetestSelected());

        JMenuItem retestAllMenuItem = new JMenuItem("Re-Test All Captured Requests...");
        retestAllMenuItem.addActionListener(e -> onRetestAll());

        JMenuItem aiAuditMenuItem = new JMenuItem("AI Audit Selected Rows");
        aiAuditMenuItem.addActionListener(e -> onAiAuditSelectedRows());

        JMenuItem copyCurlMenuItem = new JMenuItem("Copy as cURL");
        copyCurlMenuItem.addActionListener(e -> onCopyAsCurl());

        JMenuItem exportHtmlMenuItem = new JMenuItem("Export as HTML...");
        exportHtmlMenuItem.addActionListener(e -> onExportSelectedHtml());

        JMenuItem exportCsvMenuItem = new JMenuItem("Export as CSV...");
        exportCsvMenuItem.addActionListener(e -> onExportSelectedCsv());

        JMenuItem removeMenuItem = new JMenuItem("Remove Selected Item(s)");
        removeMenuItem.addActionListener(e -> onRemoveSelectedRecords());

        JMenuItem clearMenuItem = new JMenuItem("Clear All Records");
        clearMenuItem.addActionListener(e -> controller.clearRecords());

        contextMenu.add(retestSelectedMenuItem);
        contextMenu.add(retestAllMenuItem);
        contextMenu.add(aiAuditMenuItem);
        contextMenu.addSeparator();
        contextMenu.add(copyCurlMenuItem);
        contextMenu.addSeparator();
        contextMenu.add(exportHtmlMenuItem);
        contextMenu.add(exportCsvMenuItem);
        contextMenu.addSeparator();
        contextMenu.add(removeMenuItem);
        contextMenu.add(clearMenuItem);

        auditTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleContextMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                handleContextMenu(e);
            }

            private void handleContextMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = auditTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && !auditTable.isRowSelected(row)) {
                        auditTable.setRowSelectionInterval(row, row);
                    }
                    if (auditTable.getSelectedRowCount() > 0) {
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(auditTable);
        viewerPanel = new RequestResponseViewerPanel(api, controller);

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, viewerPanel);
        splitPane.setResizeWeight(0.4);
        return splitPane;
    }

    private void updateCellRenderers() {
        VerdictCellRenderer renderer = new VerdictCellRenderer();
        if (auditTable.getColumnCount() > 2) {
            auditTable.getColumnModel().getColumn(2).setCellRenderer(renderer); // Source column (Proxy / Repeater / Pipeline)
        }
        if (auditTable.getColumnCount() > 3) {
            auditTable.getColumnModel().getColumn(3).setCellRenderer(renderer); // Action column (DROPPED / PASSED)
        }
        for (int i = 8; i < tableModel.getColumnCount(); i++) {
            auditTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }
    }

    private void updatePowerButtonUI(boolean active) {
        if (active) {
            powerToggleBtn.setText("ON");
            powerToggleBtn.setBackground(new Color(40, 167, 69));
            powerToggleBtn.setForeground(Color.WHITE);
            statusBadgeLabel.setText("● Active");
            statusBadgeLabel.setForeground(new Color(40, 167, 69));
            statusBadgeLabel.setToolTipText("Engine status: Intercepting Traffic");
        } else {
            powerToggleBtn.setText("OFF");
            powerToggleBtn.setBackground(new Color(230, 230, 230));
            powerToggleBtn.setForeground(Color.BLACK);
            statusBadgeLabel.setText("● Idle");
            statusBadgeLabel.setForeground(Color.GRAY);
            statusBadgeLabel.setToolTipText("Engine status: Idle");
        }
    }

    private void applyFilter() {
        String text = searchFilterField.getText();
        if (text == null || text.trim().isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + text.trim()));
        }
    }

    private List<AuditRecord> getSelectedRecords() {
        List<AuditRecord> selectedRecords = new ArrayList<>();
        int[] selectedViewRows = auditTable.getSelectedRows();
        if (selectedViewRows != null) {
            for (int viewRow : selectedViewRows) {
                int modelRow = auditTable.convertRowIndexToModel(viewRow);
                AuditRecord record = tableModel.getRecordAt(modelRow);
                if (record != null) {
                    selectedRecords.add(record);
                }
            }
        }
        return selectedRecords;
    }

    private void onRetestSelected() {
        List<AuditRecord> selectedRecords = getSelectedRecords();
        if (selectedRecords.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select one or more items from the table to re-test.", "No Items Selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        RetestRolesDialog dialog = new RetestRolesDialog(
                this,
                "Re-Test Selected Requests",
                "Re-testing " + selectedRecords.size() + " selected request(s)",
                controller.getRoles(),
                controller.getFilterConfig().isTestUnauthenticated()
        );
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            List<UserRole> chosenRoles = dialog.getSelectedRoles();
            boolean testUnauth = dialog.isTestUnauthenticatedSelected();
            if (chosenRoles.isEmpty() && !testUnauth) {
                JOptionPane.showMessageDialog(this, "No roles or unauthenticated test selected. Re-test cancelled.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ensureAiColumnVisible();
            controller.retestRecords(selectedRecords, chosenRoles, testUnauth);
        }
    }

    private void onRetestAll() {
        List<AuditRecord> allRecords = controller.getRecords();
        if (allRecords == null || allRecords.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Table is empty. No requests available to re-test.", "Table Empty", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        RetestRolesDialog dialog = new RetestRolesDialog(
                this,
                "Re-Test All Captured Requests",
                "Re-testing all " + allRecords.size() + " captured request(s)",
                controller.getRoles(),
                controller.getFilterConfig().isTestUnauthenticated()
        );
        dialog.setVisible(true);

        if (dialog.isConfirmed()) {
            List<UserRole> chosenRoles = dialog.getSelectedRoles();
            boolean testUnauth = dialog.isTestUnauthenticatedSelected();
            if (chosenRoles.isEmpty() && !testUnauth) {
                JOptionPane.showMessageDialog(this, "No roles or unauthenticated test selected. Re-test cancelled.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            ensureAiColumnVisible();
            controller.retestRecords(allRecords, chosenRoles, testUnauth);
        }
    }

    public void ensureAiColumnVisible() {
        if (!tableModel.isShowAiColumn()) {
            tableModel.setShowAiColumn(true);
            updateCellRenderers();
        }
    }

    private void onAiAuditSelectedRows() {
        List<AuditRecord> selectedRecords = getSelectedRecords();
        if (selectedRecords.isEmpty()) {
            if (!java.awt.GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(this,
                        "Please select one or more items from the table for AI audit.",
                        "No Items Selected",
                        JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }

        ensureAiColumnVisible();

        for (AuditRecord record : selectedRecords) {
            controller.evaluateRecordWithAi(record, null);
        }
    }

    private void onRemoveSelectedRecords() {
        int[] selectedViewRows = auditTable.getSelectedRows();
        if (selectedViewRows == null || selectedViewRows.length == 0) {
            return;
        }

        List<AuditRecord> recordsToRemove = new ArrayList<>();
        for (int viewRow : selectedViewRows) {
            int modelRow = auditTable.convertRowIndexToModel(viewRow);
            AuditRecord record = tableModel.getRecordAt(modelRow);
            if (record != null) {
                recordsToRemove.add(record);
            }
        }

        if (!recordsToRemove.isEmpty()) {
            controller.removeRecords(recordsToRemove);
        }
    }

    private void onCopyAsCurl() {
        List<AuditRecord> selected = getSelectedRecords();
        if (selected.isEmpty()) {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(this, "Please select an item from the table to copy as cURL.", "No Item Selected", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        AuditRecord record = selected.get(0);
        HttpRequest reqToCopy = null;

        int viewCol = auditTable.getSelectedColumn();
        if (viewCol >= 8) {
            int roleIndex = viewCol - 8;
            List<UserRole> currentRoles = controller.getRoles();
            if (roleIndex < currentRoles.size()) {
                UserRole r = currentRoles.get(roleIndex);
                if (r != null && record.getRoleResults() != null) {
                    AuthorizationCheckResult res = record.getRoleResults().get(r.getId());
                    if (res != null && res.getRequest() != null) {
                        reqToCopy = res.getRequest();
                    }
                }
            } else if (roleIndex == currentRoles.size()) {
                AuthorizationCheckResult unauth = record.getUnauthenticatedResult();
                if (unauth != null && unauth.getRequest() != null) {
                    reqToCopy = unauth.getRequest();
                }
            }
        }
        if (reqToCopy == null) {
            reqToCopy = record.getOriginalRequest();
        }

        if (reqToCopy == null) {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(this, "No HTTP request data available for the selected transaction.", "Copy Failed", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        String curl = CurlUtils.toCurlCommand(reqToCopy);
        if (!GraphicsEnvironment.isHeadless()) {
            StringSelection selection = new StringSelection(curl);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        }
        if (api != null && api.logging() != null) {
            api.logging().logToOutput("[Thorization] Copied cURL command to clipboard.");
        }
    }

    private void onExportHtmlReport() {
        exportHtmlWithRecords(controller.getRecords());
    }

    private void onExportSelectedHtml() {
        List<AuditRecord> selected = getSelectedRecords();
        exportHtmlWithRecords(selected.isEmpty() ? controller.getRecords() : selected);
    }

    private void exportHtmlWithRecords(List<AuditRecord> records) {
        if (records == null || records.isEmpty()) {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(this, "Table is empty. No audit records available to export.", "No Records", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        String defaultName = "thorization-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".html";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Thorization Audit Report (HTML)");
        chooser.setFileFilter(new FileNameExtensionFilter("HTML Files (*.html)", "html"));
        chooser.setSelectedFile(new File(defaultName));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".html")) {
                file = new File(file.getAbsolutePath() + ".html");
            }
            try {
                HtmlReportGenerator.generateReport(file, records, controller.getRoles());
                JOptionPane.showMessageDialog(this, "HTML report exported successfully to:\n" + file.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export HTML report: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onExportCsvReport() {
        exportCsvWithRecords(controller.getRecords());
    }

    private void onExportSelectedCsv() {
        List<AuditRecord> selected = getSelectedRecords();
        exportCsvWithRecords(selected.isEmpty() ? controller.getRecords() : selected);
    }

    private void exportCsvWithRecords(List<AuditRecord> records) {
        if (records == null || records.isEmpty()) {
            if (!GraphicsEnvironment.isHeadless()) {
                JOptionPane.showMessageDialog(this, "Table is empty. No audit records available to export.", "No Records", JOptionPane.INFORMATION_MESSAGE);
            }
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }

        String defaultName = "thorization-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Thorization Audit Report (CSV)");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV Files (*.csv)", "csv"));
        chooser.setSelectedFile(new File(defaultName));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
                file = new File(file.getAbsolutePath() + ".csv");
            }
            try {
                CsvReportGenerator.exportCsv(file, records, controller.getRoles());
                JOptionPane.showMessageDialog(this, "CSV report exported successfully to:\n" + file.getAbsolutePath(), "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Failed to export CSV report: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Testing and programmatic accessors
    public JButton getHtmlReportBtn() { return htmlReportBtn; }
    public JButton getCsvExportBtn() { return csvExportBtn; }
    public JButton getExportBtn() { return exportBtn; }
    public JButton getImportBtn() { return importBtn; }
    public JTextField getSearchFilterField() { return searchFilterField; }
    public JLabel getCountLabel() { return countLabel; }
    public JPopupMenu getContextMenu() { return contextMenu; }
    public JTable getAuditTable() { return auditTable; }
    public AuditTableModel getTableModel() { return tableModel; }
    public void triggerCopyAsCurl() { onCopyAsCurl(); }
    public void triggerExportHtmlReport() { onExportHtmlReport(); }
    public void triggerExportCsvReport() { onExportCsvReport(); }
    public void triggerAiAuditSelectedRows() { onAiAuditSelectedRows(); }

    private void onExportSession() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Thorization Session to JSON");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files (*.json)", "json"));
        chooser.setSelectedFile(new File("thorization_session_" + System.currentTimeMillis() + ".json"));

        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File targetFile = chooser.getSelectedFile();
            if (!targetFile.getName().endsWith(".json")) {
                targetFile = new File(targetFile.getAbsolutePath() + ".json");
            }
            try {
                SessionExporter.exportToFile(targetFile, controller.getRecords(), controller.getRoles(), controller.getPipelines());
                JOptionPane.showMessageDialog(this, "Session exported successfully to:\n" + targetFile.getAbsolutePath(),
                        "Export Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onImportSession() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import Thorization Session from JSON");
        chooser.setFileFilter(new FileNameExtensionFilter("JSON Files (*.json)", "json"));

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File sourceFile = chooser.getSelectedFile();
            try {
                SessionImporter.ImportResult result = SessionImporter.importFromFile(sourceFile);

                boolean replace = true;
                if (!controller.getRecords().isEmpty()) {
                    String[] options = {"Replace", "Append", "Cancel"};
                    int choice = JOptionPane.showOptionDialog(
                            this,
                            "How would you like to import this session?\n\n"
                                    + "• Replace: Clear existing records and load imported session.\n"
                                    + "• Append: Keep existing records and add imported records.",
                            "Import Session",
                            JOptionPane.DEFAULT_OPTION,
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            options,
                            options[0]
                    );
                    if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
                        return; // User cancelled
                    }
                    replace = (choice == 0);
                }

                controller.importRecords(result.getRecords(), result.getRoles(), result.getPipelines(), replace);

                SwingUtilities.invokeLater(() -> {
                    try {
                        if (auditTable.getRowCount() > 0) {
                            auditTable.setRowSelectionInterval(0, 0);
                            int modelRow = auditTable.convertRowIndexToModel(0);
                            AuditRecord firstRecord = tableModel.getRecordAt(modelRow);
                            if (firstRecord != null) {
                                viewerPanel.displayRecord(firstRecord, controller.getRoles());
                            }
                        }
                    } catch (Exception ignored) {}
                });

                JOptionPane.showMessageDialog(this, "Imported " + result.getRecords().size() + " records, "
                                + result.getRoles().size() + " roles, and "
                                + result.getPipelines().size() + " pipeline scenarios successfully.",
                        "Import Complete", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // --- EngineListener Callbacks ---

    @Override
    public void onAuditRecordAdded(AuditRecord record) {
        if (record != null && (record.getAiResult() != null || record.getOverallAiVerdict() != null)) {
            ensureAiColumnVisible();
        }
        tableModel.addRecord(record);
        countLabel.setText("Total: " + tableModel.getRowCount());
    }

    @Override
    public void onAuditRecordUpdated(AuditRecord record) {
        ensureAiColumnVisible();
        tableModel.updateRecord(record);
        int viewRow = auditTable.getSelectedRow();
        if (viewRow >= 0) {
            int modelRow = auditTable.convertRowIndexToModel(viewRow);
            AuditRecord selected = tableModel.getRecordAt(modelRow);
            if (selected != null && selected.getId() == record.getId()) {
                viewerPanel.displayRecord(record, controller.getRoles());
            }
        }
    }

    @Override
    public void onAuditRecordsRemoved(List<AuditRecord> removedRecords) {
        tableModel.removeRecords(removedRecords);
        int viewRow = auditTable.getSelectedRow();
        if (viewRow >= 0) {
            int modelRow = auditTable.convertRowIndexToModel(viewRow);
            AuditRecord selected = tableModel.getRecordAt(modelRow);
            if (selected != null) {
                viewerPanel.displayRecord(selected, controller.getRoles());
            } else {
                viewerPanel.clearViewers();
            }
        } else {
            viewerPanel.clearViewers();
        }
        countLabel.setText("Total: " + tableModel.getRowCount());
    }

    @Override
    public void onAuditRecordsCleared() {
        tableModel.clear();
        auditTable.clearSelection();
        viewerPanel.clearViewers();
        countLabel.setText("Total: 0");
    }

    @Override
    public void onRolesChanged(List<UserRole> roles) {
        tableModel.setRoles(roles);
        viewerPanel.syncRoles(roles);
        updateCellRenderers();
    }

    @Override
    public void onEngineStateChanged(boolean enabled) {
        powerToggleBtn.setSelected(enabled);
        updatePowerButtonUI(enabled);
    }

    @Override
    public void onFilterConfigChanged(FilterConfig config) {
        if (config != null) {
            if (modeDropdown != null && modeDropdown.getSelectedItem() != config.getInterceptionMode()) {
                modeDropdown.setSelectedItem(config.getInterceptionMode());
            }
            if (dropAllQuickCheck != null && dropAllQuickCheck.isSelected() != config.isDropAllRequests()) {
                dropAllQuickCheck.setSelected(config.isDropAllRequests());
            }
        }
    }

    @Override
    public void onPipelinesChanged(List<Pipeline> pipelines) {}
}
