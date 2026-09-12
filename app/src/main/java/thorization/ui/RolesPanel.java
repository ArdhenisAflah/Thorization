package thorization.ui;

import thorization.core.EngineController;
import thorization.core.EngineListener;
import thorization.model.AuditRecord;
import thorization.model.FilterConfig;
import thorization.model.InterceptionMode;
import thorization.model.UserRole;
import thorization.model.pipeline.Pipeline;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Tab 1: Panel for managing user roles, custom header injections, cookies,
 * role execution priority ordering, and Safe Drop Control settings (USP 1).
 */
public class RolesPanel extends JPanel implements EngineListener {

    private final EngineController controller;

    private JTable rolesTable;
    private DefaultTableModel tableModel;

    private JTextField roleNameField;
    private JCheckBox roleEnabledCheckBox;
    private JTextArea headersArea;
    private JTextField cookiesField;

    // Safe Request Interception (USP 1) Controls
    private JComboBox<InterceptionMode> interceptionModeCombo;
    private JRadioButton dropStateChangingOnlyRadio;
    private JRadioButton dropAllRequestsRadio;
    private JCheckBox postMethodCheck;
    private JCheckBox putMethodCheck;
    private JCheckBox deleteMethodCheck;
    private JCheckBox patchMethodCheck;

    // Scope & Filter Controls
    private JCheckBox onlyInScopeCheckBox;
    private JCheckBox ignoreStaticCheckBox;
    private JCheckBox testUnauthCheckBox;
    private JTextField urlIncludeRegexField;
    private JTextField urlExcludeRegexField;

    private int selectedRoleIndex = -1;
    private boolean isUpdatingFromModel = false;

    public RolesPanel(EngineController controller) {
        this.controller = controller;
        this.controller.addListener(this);

        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, createRolesListPanel(), createConfigPanel());
        mainSplit.setResizeWeight(0.4);

        add(mainSplit, BorderLayout.CENTER);
        refreshRolesTable();
    }

    private JPanel createRolesListPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createTitledBorder("Configured Roles (Priority & Execution Sequence)"));

        tableModel = new DefaultTableModel(new String[]{"Priority", "Name", "Status", "Headers"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        rolesTable = new JTable(tableModel);
        rolesTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        rolesTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = rolesTable.getSelectedRow();
                if (row >= 0 && row < controller.getRoles().size()) {
                    selectedRoleIndex = row;
                    loadRoleToEditor(controller.getRoles().get(row));
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(rolesTable);

        // Action and Ordering Buttons
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 4));
        JButton addBtn = new JButton("Add Role");
        JButton removeBtn = new JButton("Remove");
        JButton duplicateBtn = new JButton("Duplicate");
        JButton moveUpBtn = new JButton("▲ Up");
        JButton moveDownBtn = new JButton("▼ Down");

        addBtn.addActionListener(e -> onAddNewRole());
        removeBtn.addActionListener(e -> onRemoveRole());
        duplicateBtn.addActionListener(e -> onDuplicateRole());

        moveUpBtn.setToolTipText("Move selected role higher in execution sequence");
        moveUpBtn.addActionListener(e -> {
            int row = rolesTable.getSelectedRow();
            if (row > 0) {
                controller.moveRoleUp(row);
                refreshRolesTable();
                rolesTable.setRowSelectionInterval(row - 1, row - 1);
            }
        });

        moveDownBtn.setToolTipText("Move selected role lower in execution sequence");
        moveDownBtn.addActionListener(e -> {
            int row = rolesTable.getSelectedRow();
            if (row >= 0 && row < controller.getRoles().size() - 1) {
                controller.moveRoleDown(row);
                refreshRolesTable();
                rolesTable.setRowSelectionInterval(row + 1, row + 1);
            }
        });

        buttonBar.add(addBtn);
        buttonBar.add(removeBtn);
        buttonBar.add(duplicateBtn);
        buttonBar.add(moveUpBtn);
        buttonBar.add(moveDownBtn);

        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonBar, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel createConfigPanel() {
        JPanel container = new JPanel(new BorderLayout(5, 5));

        // 1. Role Details Form
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder("Role Details & Injected Credentials"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        // Role Name
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel("Role Name:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        roleNameField = new JTextField(20);
        formPanel.add(roleNameField, gbc);

        // Role Enabled
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        formPanel.add(new JLabel("Active for Audit:"), gbc);
        gbc.gridx = 1;
        roleEnabledCheckBox = new JCheckBox("Enable this role during replay");
        roleEnabledCheckBox.setSelected(true);
        formPanel.add(roleEnabledCheckBox, gbc);

        // Custom Cookies
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel("Cookie Header:"), gbc);
        gbc.gridx = 1;
        cookiesField = new JTextField("session=user_b_token; auth=active", 20);
        formPanel.add(cookiesField, gbc);

        // Custom Headers Area
        gbc.gridx = 0; gbc.gridy = 3; gbc.anchor = GridBagConstraints.NORTHWEST;
        formPanel.add(new JLabel("Custom Headers:"), gbc);
        gbc.gridx = 1; gbc.weighty = 1.0; gbc.fill = GridBagConstraints.BOTH;
        headersArea = new JTextArea(5, 20);
        headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        headersArea.setText("Authorization: Bearer token_for_user_b\nX-User-Id: 102\nX-Role: user");
        formPanel.add(new JScrollPane(headersArea), gbc);

        // Save Role Button
        gbc.gridx = 1; gbc.gridy = 4; gbc.weighty = 0; gbc.fill = GridBagConstraints.NONE; gbc.anchor = GridBagConstraints.EAST;
        JButton saveRoleBtn = new JButton("Save Role Changes");
        saveRoleBtn.addActionListener(e -> onSaveCurrentRole());
        formPanel.add(saveRoleBtn, gbc);

        // 2. Settings Panel (USP 1 Safe Interception + Filters)
        JPanel settingsPanel = new JPanel(new GridBagLayout());
        settingsPanel.setBorder(BorderFactory.createTitledBorder("Safe Request Interception & Drop Control (USP 1)"));

        GridBagConstraints fgbc = new GridBagConstraints();
        fgbc.insets = new Insets(3, 4, 3, 4);
        fgbc.anchor = GridBagConstraints.WEST;
        fgbc.fill = GridBagConstraints.HORIZONTAL;

        FilterConfig initialConfig = controller.getFilterConfig();

        // Mode Dropdown
        fgbc.gridx = 0; fgbc.gridy = 0;
        settingsPanel.add(new JLabel("Interception Mode:"), fgbc);
        fgbc.gridx = 1; fgbc.weightx = 1.0;
        interceptionModeCombo = new JComboBox<>(InterceptionMode.values());
        interceptionModeCombo.setSelectedItem(initialConfig.getInterceptionMode());
        interceptionModeCombo.addActionListener(e -> {
            if (!isUpdatingFromModel) {
                controller.setInterceptionMode((InterceptionMode) interceptionModeCombo.getSelectedItem());
            }
        });
        settingsPanel.add(interceptionModeCombo, fgbc);

        // Drop Scope Radio Buttons
        fgbc.gridx = 0; fgbc.gridy = 1; fgbc.weightx = 0;
        settingsPanel.add(new JLabel("Drop Scope:"), fgbc);
        fgbc.gridx = 1;
        JPanel scopeRadioPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dropStateChangingOnlyRadio = new JRadioButton("State-Changing Only (POST/PUT/DELETE/PATCH)", !initialConfig.isDropAllRequests());
        dropAllRequestsRadio = new JRadioButton("Drop ALL Requests (Aggressive: including GET)", initialConfig.isDropAllRequests());
        ButtonGroup dropGroup = new ButtonGroup();
        dropGroup.add(dropStateChangingOnlyRadio);
        dropGroup.add(dropAllRequestsRadio);

        dropStateChangingOnlyRadio.addActionListener(e -> {
            if (!isUpdatingFromModel) {
                controller.setDropAllRequests(false);
            }
        });
        dropAllRequestsRadio.addActionListener(e -> {
            if (!isUpdatingFromModel) {
                controller.setDropAllRequests(true);
            }
        });

        scopeRadioPanel.add(dropStateChangingOnlyRadio);
        scopeRadioPanel.add(dropAllRequestsRadio);
        settingsPanel.add(scopeRadioPanel, fgbc);

        // State-changing methods checkboxes
        fgbc.gridx = 0; fgbc.gridy = 2; fgbc.weightx = 0;
        settingsPanel.add(new JLabel("State-Changing Methods:"), fgbc);
        fgbc.gridx = 1;
        JPanel methodsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        Set<String> activeMethods = initialConfig.getStateChangingMethods();
        postMethodCheck = new JCheckBox("POST", activeMethods.contains("POST"));
        putMethodCheck = new JCheckBox("PUT", activeMethods.contains("PUT"));
        deleteMethodCheck = new JCheckBox("DELETE", activeMethods.contains("DELETE"));
        patchMethodCheck = new JCheckBox("PATCH", activeMethods.contains("PATCH"));

        postMethodCheck.addActionListener(e -> autoSyncMethods());
        putMethodCheck.addActionListener(e -> autoSyncMethods());
        deleteMethodCheck.addActionListener(e -> autoSyncMethods());
        patchMethodCheck.addActionListener(e -> autoSyncMethods());

        methodsPanel.add(postMethodCheck);
        methodsPanel.add(putMethodCheck);
        methodsPanel.add(deleteMethodCheck);
        methodsPanel.add(patchMethodCheck);
        settingsPanel.add(methodsPanel, fgbc);

        // Scope & Static Files
        onlyInScopeCheckBox = new JCheckBox("Intercept In-Scope Requests Only", initialConfig.isOnlyInScope());
        ignoreStaticCheckBox = new JCheckBox("Ignore Static Files (.js, .css, .png, .jpg, .woff, etc.)", initialConfig.isIgnoreStaticFiles());
        testUnauthCheckBox = new JCheckBox("Test Unauthenticated Replay (No Auth Headers)", initialConfig.isTestUnauthenticated());

        onlyInScopeCheckBox.addActionListener(e -> {
            if (!isUpdatingFromModel) {
                controller.getFilterConfig().setOnlyInScope(onlyInScopeCheckBox.isSelected());
            }
        });
        ignoreStaticCheckBox.addActionListener(e -> {
            if (!isUpdatingFromModel) {
                controller.getFilterConfig().setIgnoreStaticFiles(ignoreStaticCheckBox.isSelected());
            }
        });
        testUnauthCheckBox.addActionListener(e -> {
            if (!isUpdatingFromModel) {
                controller.getFilterConfig().setTestUnauthenticated(testUnauthCheckBox.isSelected());
            }
        });

        fgbc.gridx = 0; fgbc.gridy = 3; fgbc.gridwidth = 2;
        settingsPanel.add(onlyInScopeCheckBox, fgbc);
        fgbc.gridy = 4;
        settingsPanel.add(ignoreStaticCheckBox, fgbc);
        fgbc.gridy = 5;
        settingsPanel.add(testUnauthCheckBox, fgbc);

        fgbc.gridy = 6;
        settingsPanel.add(new JLabel("URL Include Regex (optional):"), fgbc);
        fgbc.gridy = 7;
        urlIncludeRegexField = new JTextField(initialConfig.getUrlIncludeRegex(), 25);
        settingsPanel.add(urlIncludeRegexField, fgbc);

        fgbc.gridy = 8;
        settingsPanel.add(new JLabel("URL Exclude Regex (optional):"), fgbc);
        fgbc.gridy = 9;
        urlExcludeRegexField = new JTextField(initialConfig.getUrlExcludeRegex(), 25);
        settingsPanel.add(urlExcludeRegexField, fgbc);

        fgbc.gridy = 10; fgbc.anchor = GridBagConstraints.EAST; fgbc.fill = GridBagConstraints.NONE;
        JButton saveFilterBtn = new JButton("Apply Settings & Filters");
        saveFilterBtn.addActionListener(e -> onApplyFilters());
        settingsPanel.add(saveFilterBtn, fgbc);

        container.add(formPanel, BorderLayout.CENTER);
        container.add(settingsPanel, BorderLayout.SOUTH);
        return container;
    }

    private void autoSyncMethods() {
        if (isUpdatingFromModel) return;
        Set<String> stateChanging = new HashSet<>();
        if (postMethodCheck.isSelected()) stateChanging.add("POST");
        if (putMethodCheck.isSelected()) stateChanging.add("PUT");
        if (deleteMethodCheck.isSelected()) stateChanging.add("DELETE");
        if (patchMethodCheck.isSelected()) stateChanging.add("PATCH");
        controller.getFilterConfig().setStateChangingMethods(stateChanging);
    }

    private void refreshRolesTable() {
        tableModel.setRowCount(0);
        List<UserRole> roles = controller.getRoles();
        for (int i = 0; i < roles.size(); i++) {
            UserRole role = roles.get(i);
            tableModel.addRow(new Object[]{
                    "#" + (i + 1),
                    role.getName(),
                    role.isEnabled() ? "Active" : "Disabled",
                    role.getParsedHeaders().size() + " headers"
            });
        }
    }

    private void loadRoleToEditor(UserRole role) {
        if (role == null) return;
        roleNameField.setText(role.getName());
        roleEnabledCheckBox.setSelected(role.isEnabled());
        cookiesField.setText(role.getRawCookies());
        headersArea.setText(role.getRawHeaders());
    }

    private void onAddNewRole() {
        String name = JOptionPane.showInputDialog(this, "Enter Role Name (e.g. User C / Member):", "New Role", JOptionPane.PLAIN_MESSAGE);
        if (name != null && !name.trim().isEmpty()) {
            UserRole newRole = new UserRole(name.trim(), "Authorization: Bearer <TOKEN>\n", "");
            controller.addRole(newRole);
            refreshRolesTable();
            rolesTable.setRowSelectionInterval(rolesTable.getRowCount() - 1, rolesTable.getRowCount() - 1);
        }
    }

    private void onRemoveRole() {
        if (selectedRoleIndex >= 0 && selectedRoleIndex < controller.getRoles().size()) {
            if (controller.getRoles().size() <= 1) {
                JOptionPane.showMessageDialog(this, "At least one role is required.", "Warning", JOptionPane.WARNING_MESSAGE);
                return;
            }
            UserRole toRemove = controller.getRoles().get(selectedRoleIndex);
            controller.removeRole(toRemove.getId());
            refreshRolesTable();
            selectedRoleIndex = -1;
        }
    }

    private void onDuplicateRole() {
        if (selectedRoleIndex >= 0 && selectedRoleIndex < controller.getRoles().size()) {
            UserRole current = controller.getRoles().get(selectedRoleIndex);
            UserRole clone = new UserRole(current.getName() + " (Copy)", current.getRawHeaders(), current.getRawCookies());
            controller.addRole(clone);
            refreshRolesTable();
        }
    }

    private void onSaveCurrentRole() {
        if (selectedRoleIndex >= 0 && selectedRoleIndex < controller.getRoles().size()) {
            UserRole role = controller.getRoles().get(selectedRoleIndex);
            role.setName(roleNameField.getText().trim());
            role.setEnabled(roleEnabledCheckBox.isSelected());
            role.setRawCookies(cookiesField.getText().trim());
            role.setRawHeaders(headersArea.getText());
            controller.updateRole(role);
            refreshRolesTable();
            JOptionPane.showMessageDialog(this, "Role '" + role.getName() + "' saved successfully!", "Saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void onApplyFilters() {
        FilterConfig cfg = controller.getFilterConfig();
        cfg.setInterceptionMode((InterceptionMode) interceptionModeCombo.getSelectedItem());
        cfg.setDropAllRequests(dropAllRequestsRadio.isSelected());

        Set<String> stateChanging = new HashSet<>();
        if (postMethodCheck.isSelected()) stateChanging.add("POST");
        if (putMethodCheck.isSelected()) stateChanging.add("PUT");
        if (deleteMethodCheck.isSelected()) stateChanging.add("DELETE");
        if (patchMethodCheck.isSelected()) stateChanging.add("PATCH");
        cfg.setStateChangingMethods(stateChanging);

        cfg.setOnlyInScope(onlyInScopeCheckBox.isSelected());
        cfg.setIgnoreStaticFiles(ignoreStaticCheckBox.isSelected());
        cfg.setTestUnauthenticated(testUnauthCheckBox.isSelected());
        cfg.setUrlIncludeRegex(urlIncludeRegexField.getText());
        cfg.setUrlExcludeRegex(urlExcludeRegexField.getText());

        controller.setFilterConfig(cfg);
        JOptionPane.showMessageDialog(this, "Settings and filters applied successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
    }

    // --- EngineListener Callbacks ---

    @Override
    public void onAuditRecordAdded(AuditRecord record) {}

    @Override
    public void onAuditRecordUpdated(AuditRecord record) {}

    @Override
    public void onAuditRecordsRemoved(List<AuditRecord> removedRecords) {}

    @Override
    public void onAuditRecordsCleared() {}

    @Override
    public void onRolesChanged(List<UserRole> roles) {
        refreshRolesTable();
    }

    @Override
    public void onEngineStateChanged(boolean enabled) {}

    @Override
    public void onFilterConfigChanged(FilterConfig config) {
        if (config == null) return;
        isUpdatingFromModel = true;
        try {
            if (interceptionModeCombo != null) {
                interceptionModeCombo.setSelectedItem(config.getInterceptionMode());
            }
            if (dropAllRequestsRadio != null && dropStateChangingOnlyRadio != null) {
                if (config.isDropAllRequests()) {
                    dropAllRequestsRadio.setSelected(true);
                } else {
                    dropStateChangingOnlyRadio.setSelected(true);
                }
            }
            if (onlyInScopeCheckBox != null) onlyInScopeCheckBox.setSelected(config.isOnlyInScope());
            if (ignoreStaticCheckBox != null) ignoreStaticCheckBox.setSelected(config.isIgnoreStaticFiles());
            if (testUnauthCheckBox != null) testUnauthCheckBox.setSelected(config.isTestUnauthenticated());
        } finally {
            isUpdatingFromModel = false;
        }
    }

    @Override
    public void onPipelinesChanged(List<Pipeline> pipelines) {}
}
