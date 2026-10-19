package thorization.ui.components.pipeline;

import thorization.model.UserRole;
import thorization.model.pipeline.ExtractionRule;
import thorization.model.pipeline.ExtractionSource;
import thorization.model.pipeline.ExtractionType;
import thorization.model.pipeline.RequestStep;
import thorization.util.UiUtils;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * Visual card representing an HTTP Request Step with role assignment,
 * dynamic placeholder templates, and embedded variable extraction rules.
 */
public class RequestBlockCard extends JPanel {

    private final RequestStep step;
    private final Consumer<RequestBlockCard> onRemove;
    private final Consumer<RequestBlockCard> onMoveUp;
    private final Consumer<RequestBlockCard> onMoveDown;

    private final JTextField nameField;
    private final JComboBox<String> methodCombo;
    private final JTextField urlField;
    private final JComboBox<RoleItem> roleCombo;
    private final JTextArea headersArea;
    private final JTextArea bodyArea;
    private final JCheckBox teardownCheck;
    private final DefaultTableModel extractionTableModel;
    private final JTable extractionTable;

    public static class RoleItem {
        public final String id;
        public final String name;
        public RoleItem(String id, String name) { this.id = id; this.name = name; }
        @Override public String toString() { return name; }
    }

    public RequestBlockCard(RequestStep step, List<UserRole> availableRoles,
                            Consumer<RequestBlockCard> onMoveUp,
                            Consumer<RequestBlockCard> onMoveDown,
                            Consumer<RequestBlockCard> onRemove) {
        this.step = step;
        this.onMoveUp = onMoveUp;
        this.onMoveDown = onMoveDown;
        this.onRemove = onRemove;

        setLayout(new BorderLayout(5, 5));
        Color borderColor = new Color(66, 133, 244);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        setBackground(UiUtils.getCardBackground(true));

        // --- Header Bar ---
        JPanel headerPanel = new JPanel(new BorderLayout(5, 0));
        headerPanel.setOpaque(false);

        JLabel typeBadge = new JLabel(" HTTP STEP ");
        typeBadge.setFont(typeBadge.getFont().deriveFont(Font.BOLD, 11f));
        typeBadge.setOpaque(true);
        typeBadge.setBackground(borderColor);
        typeBadge.setForeground(Color.WHITE);
        typeBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        nameField = new JTextField(step.getName(), 18);
        nameField.setFont(nameField.getFont().deriveFont(Font.BOLD, 12f));
        nameField.setPreferredSize(new Dimension(200, 26));
        nameField.setToolTipText("Step identifier label (string)");

        JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleLeft.setOpaque(false);
        titleLeft.add(typeBadge);
        titleLeft.add(new JLabel("Step:"));
        titleLeft.add(nameField);

        JPanel controlsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controlsRight.setOpaque(false);

        JButton upBtn = new JButton("▲");
        JButton downBtn = new JButton("▼");
        JButton removeBtn = new JButton("✕");
        removeBtn.setForeground(new Color(220, 53, 69));

        upBtn.setToolTipText("Move Step Up");
        downBtn.setToolTipText("Move Step Down");
        removeBtn.setToolTipText("Delete Step");

        upBtn.addActionListener(e -> onMoveUp.accept(this));
        downBtn.addActionListener(e -> onMoveDown.accept(this));
        removeBtn.addActionListener(e -> onRemove.accept(this));

        controlsRight.add(upBtn);
        controlsRight.add(downBtn);
        controlsRight.add(removeBtn);

        headerPanel.add(titleLeft, BorderLayout.WEST);
        headerPanel.add(controlsRight, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // --- Main Fields Panel ---
        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        fieldsPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 4, 3, 4);

        // Row 1: Method + URL + Role Selector
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        methodCombo = new JComboBox<>(new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"});
        methodCombo.setPreferredSize(new Dimension(85, 26));
        methodCombo.setSelectedItem(step.getMethod());
        methodCombo.setToolTipText("HTTP Request Method");
        fieldsPanel.add(methodCombo, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        urlField = new JTextField(step.getUrlTemplate());
        urlField.setPreferredSize(new Dimension(260, 26));
        urlField.setToolTipText("Target HTTP/HTTPS URL or path (string with optional {{VAR}} placeholders)");
        fieldsPanel.add(urlField, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("Role:"), gbc);

        gbc.gridx = 3; gbc.weightx = 0.0;
        roleCombo = new JComboBox<>();
        roleCombo.setPreferredSize(new Dimension(175, 26));
        roleCombo.setToolTipText("Role credential context to apply to this request");
        updateRolesList(availableRoles);
        fieldsPanel.add(roleCombo, gbc);

        // Row 2: Teardown checkbox
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 4; gbc.weightx = 1.0;
        JPanel teardownPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        teardownPanel.setOpaque(false);
        teardownCheck = new JCheckBox("Teardown Step (Cleanup request executed at end)", step.isTeardown());
        teardownCheck.setOpaque(false);
        teardownPanel.add(teardownCheck);
        fieldsPanel.add(teardownPanel, gbc);

        // Row 3: Tabs (Headers, Body, Extractions)
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 4; gbc.weightx = 1.0; gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;

        JTabbedPane payloadTabs = new JTabbedPane();

        headersArea = new JTextArea(step.getHeadersTemplate(), 3, 30);
        headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        headersArea.setToolTipText("Multiline HTTP headers to inject: Header-Name: value (supports {{VAR}})");
        JScrollPane headersScroll = new JScrollPane(headersArea);
        headersScroll.setPreferredSize(new Dimension(300, 90));
        payloadTabs.addTab("Headers (supports {{VAR}})", headersScroll);

        bodyArea = new JTextArea(step.getBodyTemplate(), 4, 30);
        bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        bodyArea.setToolTipText("Request payload content (string, JSON, or form-data with optional {{VAR}})");
        JScrollPane bodyScroll = new JScrollPane(bodyArea);
        bodyScroll.setPreferredSize(new Dimension(300, 90));
        payloadTabs.addTab("Body Template (supports {{VAR}})", bodyScroll);

        // Tab 3: Extraction Table
        JPanel extractionPanel = new JPanel(new BorderLayout(4, 4));
        extractionPanel.setOpaque(false);
        extractionPanel.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        String[] extractionCols = {"Var Name", "Source", "Type", "Expression / JSONPath", "Default"};
        extractionTableModel = new DefaultTableModel(extractionCols, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 1) return ExtractionSource.class;
                if (columnIndex == 2) return ExtractionType.class;
                return String.class;
            }
        };

        if (step.getExtractions() != null) {
            for (ExtractionRule r : step.getExtractions()) {
                extractionTableModel.addRow(new Object[]{
                        r.getVariableName(),
                        r.getSource(),
                        r.getType(),
                        r.getExpression(),
                        r.getDefaultValue()
                });
            }
        }

        extractionTable = new JTable(extractionTableModel);
        extractionTable.setRowHeight(24);

        // Column 0: Var Name
        extractionTable.getColumnModel().getColumn(0).setPreferredWidth(100);

        // Column 1: Source Dropdown Editor & Renderer
        JComboBox<ExtractionSource> sourceCombo = new JComboBox<>(ExtractionSource.values());
        sourceCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ExtractionSource) {
                    setText(((ExtractionSource) value).getDisplayName());
                }
                return this;
            }
        });
        extractionTable.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(sourceCombo));
        extractionTable.getColumnModel().getColumn(1).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value instanceof ExtractionSource) {
                    setText(((ExtractionSource) value).getDisplayName());
                } else if (value != null) {
                    setText(value.toString());
                } else {
                    setText("");
                }
            }
        });
        extractionTable.getColumnModel().getColumn(1).setPreferredWidth(130);

        // Column 2: Type Dropdown Editor & Renderer
        JComboBox<ExtractionType> typeCombo = new JComboBox<>(ExtractionType.values());
        typeCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof ExtractionType) {
                    setText(((ExtractionType) value).getDisplayName());
                }
                return this;
            }
        });
        extractionTable.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(typeCombo));
        extractionTable.getColumnModel().getColumn(2).setCellRenderer(new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            protected void setValue(Object value) {
                if (value instanceof ExtractionType) {
                    setText(((ExtractionType) value).getDisplayName());
                } else if (value != null) {
                    setText(value.toString());
                } else {
                    setText("");
                }
            }
        });
        extractionTable.getColumnModel().getColumn(2).setPreferredWidth(140);

        // Column 3: Expression
        extractionTable.getColumnModel().getColumn(3).setPreferredWidth(210);

        // Column 4: Default
        extractionTable.getColumnModel().getColumn(4).setPreferredWidth(80);

        JScrollPane extractScroll = new JScrollPane(extractionTable);
        extractScroll.setPreferredSize(new Dimension(300, 75));
        extractionPanel.add(extractScroll, BorderLayout.CENTER);

        JPanel extractionFooter = new JPanel(new BorderLayout(6, 0));
        extractionFooter.setOpaque(false);

        JPanel extractionControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        extractionControls.setOpaque(false);
        JButton addExtractBtn = new JButton("+ Add Rule");
        JButton removeExtractBtn = new JButton("Remove");

        addExtractBtn.addActionListener(e -> {
            extractionTableModel.addRow(new Object[]{
                    "NEW_VAR", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.id", ""
            });
        });

        removeExtractBtn.addActionListener(e -> {
            int row = extractionTable.getSelectedRow();
            if (row >= 0) extractionTableModel.removeRow(row);
        });

        extractionControls.add(addExtractBtn);
        extractionControls.add(removeExtractBtn);

        JLabel hintLabel = new JLabel("💡 Header Regex: Use 'Header: regex' (e.g. Set-Cookie: token=([a-zA-Z0-9]+)) or JSONPath (e.g. $.id)");
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 11f));
        hintLabel.setForeground(UIManager.getColor("Label.disabledForeground") != null ? UIManager.getColor("Label.disabledForeground") : Color.GRAY);

        extractionFooter.add(extractionControls, BorderLayout.WEST);
        extractionFooter.add(hintLabel, BorderLayout.CENTER);

        extractionPanel.add(extractionFooter, BorderLayout.SOUTH);
        payloadTabs.addTab("Dynamic Extractions", extractionPanel);

        fieldsPanel.add(payloadTabs, gbc);
        add(fieldsPanel, BorderLayout.CENTER);
    }

    public void updateRolesList(List<UserRole> roles) {
        roleCombo.removeAllItems();
        roleCombo.addItem(new RoleItem("UNAUTHENTICATED", "Unauthenticated (No Token)"));
        if (roles != null) {
            for (UserRole r : roles) {
                RoleItem item = new RoleItem(r.getId(), r.getName());
                roleCombo.addItem(item);
                if (r.getId().equals(step.getTargetRoleId())) {
                    roleCombo.setSelectedItem(item);
                }
            }
        }
    }

    public RequestStep getStepData() {
        if (extractionTable.isEditing() && extractionTable.getCellEditor() != null) {
            extractionTable.getCellEditor().stopCellEditing();
        }

        step.setName(nameField.getText().trim());
        step.setMethod((String) methodCombo.getSelectedItem());
        step.setUrlTemplate(urlField.getText().trim());
        RoleItem selectedRole = (RoleItem) roleCombo.getSelectedItem();
        step.setTargetRoleId(selectedRole != null ? selectedRole.id : "UNAUTHENTICATED");
        step.setHeadersTemplate(headersArea.getText());
        step.setBodyTemplate(bodyArea.getText());
        step.setTeardown(teardownCheck.isSelected());

        step.getExtractions().clear();
        for (int i = 0; i < extractionTableModel.getRowCount(); i++) {
            Object varObj = extractionTableModel.getValueAt(i, 0);
            String var = varObj != null ? varObj.toString().trim() : "";
            Object src = extractionTableModel.getValueAt(i, 1);
            Object type = extractionTableModel.getValueAt(i, 2);
            Object exprObj = extractionTableModel.getValueAt(i, 3);
            String expr = exprObj != null ? exprObj.toString().trim() : "";
            Object defObj = extractionTableModel.getValueAt(i, 4);
            String def = defObj != null ? defObj.toString().trim() : "";

            ExtractionSource sourceEnum = parseExtractionSource(src);
            ExtractionType typeEnum = parseExtractionType(type);

            ExtractionRule rule = new ExtractionRule(var, sourceEnum, typeEnum, expr);
            rule.setDefaultValue(def);
            step.addExtraction(rule);
        }

        return step;
    }

    public static ExtractionSource parseExtractionSource(Object src) {
        if (src instanceof ExtractionSource) {
            return (ExtractionSource) src;
        }
        if (src != null) {
            String str = src.toString().trim();
            for (ExtractionSource s : ExtractionSource.values()) {
                if (s.name().equalsIgnoreCase(str) || s.getDisplayName().equalsIgnoreCase(str)) {
                    return s;
                }
            }
        }
        return ExtractionSource.RESPONSE_BODY;
    }

    public static ExtractionType parseExtractionType(Object type) {
        if (type instanceof ExtractionType) {
            return (ExtractionType) type;
        }
        if (type != null) {
            String str = type.toString().trim();
            for (ExtractionType t : ExtractionType.values()) {
                if (t.name().equalsIgnoreCase(str) || t.getDisplayName().equalsIgnoreCase(str)) {
                    return t;
                }
            }
        }
        return ExtractionType.JSON_PATH;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}

