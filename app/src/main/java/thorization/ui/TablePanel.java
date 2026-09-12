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
import javax.swing.RowFilter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        JPanel toolbar = new JPanel(new BorderLayout(8, 8));
        toolbar.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        // Left controls: ON/OFF toggle, status indicator, Drop Mode Dropdown, Drop All Checkbox, Clear
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        powerToggleBtn = new JToggleButton("Thorization Engine: OFF");
        powerToggleBtn.setFont(powerToggleBtn.getFont().deriveFont(Font.BOLD, 12f));
        powerToggleBtn.setBackground(new Color(230, 230, 230));
        powerToggleBtn.addActionListener(e -> {
            boolean active = powerToggleBtn.isSelected();
            controller.setEnabled(active);
            updatePowerButtonUI(active);
        });

        statusBadgeLabel = new JLabel("Status: Idle");
        statusBadgeLabel.setFont(statusBadgeLabel.getFont().deriveFont(Font.BOLD, 12f));
        statusBadgeLabel.setForeground(Color.GRAY);

        modeDropdown = new JComboBox<>(InterceptionMode.values());
        modeDropdown.setToolTipText("Interception Action Mode (USP 1 Safe Drop Control)");
        modeDropdown.setSelectedItem(controller.getInterceptionMode());
        modeDropdown.addActionListener(e -> controller.setInterceptionMode((InterceptionMode) modeDropdown.getSelectedItem()));

        dropAllQuickCheck = new JCheckBox("Drop All (inc. GET)");
        dropAllQuickCheck.setToolTipText("When checked, even GET requests are safely dropped in Burp Proxy while replaying");
        dropAllQuickCheck.setSelected(controller.getFilterConfig().isDropAllRequests());
        dropAllQuickCheck.addActionListener(e -> controller.setDropAllRequests(dropAllQuickCheck.isSelected()));

        JButton clearBtn = new JButton("Clear Table");
        clearBtn.addActionListener(e -> controller.clearRecords());

        leftPanel.add(powerToggleBtn);
        leftPanel.add(statusBadgeLabel);
        leftPanel.add(new JLabel("Mode:"));
        leftPanel.add(modeDropdown);
        leftPanel.add(dropAllQuickCheck);
        leftPanel.add(clearBtn);

        // Right controls: Search Filter, Export/Import, Count
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightPanel.add(new JLabel("Search Filter:"));
        searchFilterField = new JTextField(15);
        searchFilterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });
        rightPanel.add(searchFilterField);

        JButton exportBtn = new JButton("Export Session (.json)");
        exportBtn.addActionListener(e -> onExportSession());

        JButton importBtn = new JButton("Import Session (.json)");
        importBtn.addActionListener(e -> onImportSession());

        countLabel = new JLabel("Total: 0");
        countLabel.setFont(countLabel.getFont().deriveFont(Font.BOLD, 12f));

        rightPanel.add(exportBtn);
        rightPanel.add(importBtn);
        rightPanel.add(countLabel);

        toolbar.add(leftPanel, BorderLayout.WEST);
        toolbar.add(rightPanel, BorderLayout.EAST);
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

        // Right-Click Context Menu for re-testing and item removal
        JPopupMenu contextMenu = new JPopupMenu();
        JMenuItem sendToPipelineMenuItem = new JMenuItem("⚡ Send to Pipeline Studio");
        sendToPipelineMenuItem.setFont(sendToPipelineMenuItem.getFont().deriveFont(Font.BOLD));
        sendToPipelineMenuItem.addActionListener(e -> {
            List<AuditRecord> selected = getSelectedRecords();
            if (!selected.isEmpty() && mainTabs != null) {
                mainTabs.getPipelineStudioPanel().createPipelineFromRequests(selected);
                mainTabs.switchToPipelineStudio();
            }
        });

        JMenuItem retestSelectedMenuItem = new JMenuItem("Re-Test Selected Request(s)...");
        retestSelectedMenuItem.addActionListener(e -> onRetestSelected());

        JMenuItem retestAllMenuItem = new JMenuItem("Re-Test All Captured Requests...");
        retestAllMenuItem.addActionListener(e -> onRetestAll());

        JMenuItem removeMenuItem = new JMenuItem("Remove Selected Item(s)");
        removeMenuItem.addActionListener(e -> onRemoveSelectedRecords());

        JMenuItem clearMenuItem = new JMenuItem("Clear All Records");
        clearMenuItem.addActionListener(e -> controller.clearRecords());

        contextMenu.add(sendToPipelineMenuItem);
        contextMenu.addSeparator();
        contextMenu.add(retestSelectedMenuItem);
        contextMenu.add(retestAllMenuItem);
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
        viewerPanel = new RequestResponseViewerPanel(api);

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
            powerToggleBtn.setText("Thorization Engine: ACTIVE");
            powerToggleBtn.setBackground(new Color(40, 167, 69));
            powerToggleBtn.setForeground(Color.WHITE);
            statusBadgeLabel.setText("Status: Intercepting Traffic");
            statusBadgeLabel.setForeground(new Color(40, 167, 69));
        } else {
            powerToggleBtn.setText("Thorization Engine: OFF");
            powerToggleBtn.setBackground(new Color(230, 230, 230));
            powerToggleBtn.setForeground(Color.BLACK);
            statusBadgeLabel.setText("Status: Idle");
            statusBadgeLabel.setForeground(Color.GRAY);
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
            controller.retestRecords(allRecords, chosenRoles, testUnauth);
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
                controller.importRecords(result.getRecords(), result.getRoles(), result.getPipelines());
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
        tableModel.addRecord(record);
        countLabel.setText("Total: " + tableModel.getRowCount());
    }

    @Override
    public void onAuditRecordUpdated(AuditRecord record) {
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
