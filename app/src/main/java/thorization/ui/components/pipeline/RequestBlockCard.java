package thorization.ui.components.pipeline;

import thorization.model.UserRole;
import thorization.model.pipeline.ExtractionRule;
import thorization.model.pipeline.ExtractionSource;
import thorization.model.pipeline.ExtractionType;
import thorization.model.pipeline.RequestStep;

import javax.swing.*;
import javax.swing.border.TitledBorder;
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
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(66, 133, 244), 2, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        setBackground(new Color(248, 250, 254));

        // --- Header Bar ---
        JPanel headerPanel = new JPanel(new BorderLayout(5, 0));
        headerPanel.setOpaque(false);

        JLabel typeBadge = new JLabel(" 🟦 HTTP REQUEST STEP ");
        typeBadge.setFont(typeBadge.getFont().deriveFont(Font.BOLD, 12f));
        typeBadge.setOpaque(true);
        typeBadge.setBackground(new Color(66, 133, 244));
        typeBadge.setForeground(Color.WHITE);
        typeBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        nameField = new JTextField(step.getName(), 18);
        nameField.setFont(nameField.getFont().deriveFont(Font.BOLD, 12f));

        JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleLeft.setOpaque(false);
        titleLeft.add(typeBadge);
        titleLeft.add(new JLabel("Step Name:"));
        titleLeft.add(nameField);

        JPanel controlsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controlsRight.setOpaque(false);

        JButton upBtn = new JButton("▲");
        JButton downBtn = new JButton("▼");
        JButton removeBtn = new JButton("✖");
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
        gbc.insets = new Insets(4, 4, 4, 4);

        // Row 1: Method + URL + Role Selector
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        methodCombo = new JComboBox<>(new String[]{"GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD"});
        methodCombo.setSelectedItem(step.getMethod());
        fieldsPanel.add(methodCombo, gbc);

        gbc.gridx = 1; gbc.weightx = 1.0;
        urlField = new JTextField(step.getUrlTemplate());
        fieldsPanel.add(urlField, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("Execute as:"), gbc);

        gbc.gridx = 3; gbc.weightx = 0.0;
        roleCombo = new JComboBox<>();
        updateRolesList(availableRoles);
        fieldsPanel.add(roleCombo, gbc);

        // Row 2: Headers & Body Tabs
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 4; gbc.weightx = 1.0; gbc.weighty = 0.5;
        gbc.fill = GridBagConstraints.BOTH;

        JTabbedPane payloadTabs = new JTabbedPane();
        headersArea = new JTextArea(step.getHeadersTemplate(), 3, 30);
        headersArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        payloadTabs.addTab("Custom Headers (Optional)", new JScrollPane(headersArea));

        bodyArea = new JTextArea(step.getBodyTemplate(), 4, 30);
        bodyArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        payloadTabs.addTab("Request Body Template (supports {{VAR}})", new JScrollPane(bodyArea));

        fieldsPanel.add(payloadTabs, gbc);

        // Row 3: Embedded Extractions Table
        gbc.gridy = 2; gbc.weighty = 0.5;
        JPanel extractionPanel = new JPanel(new BorderLayout(4, 4));
        extractionPanel.setOpaque(false);
        extractionPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                "Dynamic Variable Extraction Rules (Auto-stored to {{VAR_NAME}})",
                TitledBorder.LEFT, TitledBorder.TOP
        ));

        String[] extractionCols = {"Var Name", "Source", "Type", "Expression / JSONPath", "Default"};
        extractionTableModel = new DefaultTableModel(extractionCols, 0);
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

        JTable extractionTable = new JTable(extractionTableModel);
        extractionPanel.add(new JScrollPane(extractionTable), BorderLayout.CENTER);

        JPanel extractionControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        extractionControls.setOpaque(false);
        JButton addExtractBtn = new JButton("+ Add Variable Rule");
        JButton removeExtractBtn = new JButton("Remove Rule");

        addExtractBtn.addActionListener(e -> {
            extractionTableModel.addRow(new Object[]{
                    "NEW_VAR", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.id", ""
            });
        });

        removeExtractBtn.addActionListener(e -> {
            int row = extractionTable.getSelectedRow();
            if (row >= 0) extractionTableModel.removeRow(row);
        });

        teardownCheck = new JCheckBox("Is Teardown / Cleanup Request", step.isTeardown());
        teardownCheck.setOpaque(false);

        extractionControls.add(addExtractBtn);
        extractionControls.add(removeExtractBtn);
        extractionControls.add(Box.createHorizontalStrut(15));
        extractionControls.add(teardownCheck);

        extractionPanel.add(extractionControls, BorderLayout.SOUTH);
        fieldsPanel.add(extractionPanel, gbc);

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
            String var = (String) extractionTableModel.getValueAt(i, 0);
            Object src = extractionTableModel.getValueAt(i, 1);
            Object type = extractionTableModel.getValueAt(i, 2);
            String expr = (String) extractionTableModel.getValueAt(i, 3);
            String def = (String) extractionTableModel.getValueAt(i, 4);

            ExtractionSource sourceEnum = src instanceof ExtractionSource ? (ExtractionSource) src : ExtractionSource.RESPONSE_BODY;
            ExtractionType typeEnum = type instanceof ExtractionType ? (ExtractionType) type : ExtractionType.JSON_PATH;

            ExtractionRule rule = new ExtractionRule(var, sourceEnum, typeEnum, expr);
            rule.setDefaultValue(def != null ? def : "");
            step.addExtraction(rule);
        }

        return step;
    }
}

