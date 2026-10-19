package thorization.ui.components.pipeline;

import thorization.model.pipeline.ConditionBranch;
import thorization.util.UiUtils;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * Visual card representing an IF / ELSE Logic Branch or BOLA Assertion rule.
 */
public class BranchBlockCard extends JPanel {

    private final ConditionBranch branch;
    private final Consumer<BranchBlockCard> onRemove;
    private final Consumer<BranchBlockCard> onMoveUp;
    private final Consumer<BranchBlockCard> onMoveDown;

    private final JTextField nameField;
    private final JComboBox<ConditionBranch.ConditionType> conditionTypeCombo;
    private final JTextField expectedValueField;
    private final JComboBox<ConditionBranch.BranchAction> onTrueCombo;
    private final JComboBox<ConditionBranch.BranchAction> onFalseCombo;
    private final JTextField failureNoteField;

    public BranchBlockCard(ConditionBranch branch,
                           Consumer<BranchBlockCard> onMoveUp,
                           Consumer<BranchBlockCard> onMoveDown,
                           Consumer<BranchBlockCard> onRemove) {
        this.branch = branch;
        this.onMoveUp = onMoveUp;
        this.onMoveDown = onMoveDown;
        this.onRemove = onRemove;

        setLayout(new BorderLayout(5, 5));
        Color borderColor = new Color(230, 160, 0);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(borderColor, 1, true),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        setBackground(UiUtils.getCardBackground(false));

        // --- Header Bar ---
        JPanel headerPanel = new JPanel(new BorderLayout(5, 0));
        headerPanel.setOpaque(false);

        JLabel typeBadge = new JLabel(" IF / ELSE ");
        typeBadge.setFont(typeBadge.getFont().deriveFont(Font.BOLD, 11f));
        typeBadge.setOpaque(true);
        typeBadge.setBackground(borderColor);
        typeBadge.setForeground(Color.WHITE);
        typeBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        nameField = new JTextField(branch.getName(), 18);
        nameField.setFont(nameField.getFont().deriveFont(Font.BOLD, 12f));
        nameField.setPreferredSize(new Dimension(200, 26));
        nameField.setToolTipText("Descriptive assertion or branch name");

        JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleLeft.setOpaque(false);
        titleLeft.add(typeBadge);
        titleLeft.add(new JLabel("Branch:"));
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

        // Row 1: Condition Statement (IF <type> == <expected>)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("IF:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.4;
        conditionTypeCombo = new JComboBox<>(ConditionBranch.ConditionType.values());
        conditionTypeCombo.setPreferredSize(new Dimension(190, 26));
        conditionTypeCombo.setSelectedItem(branch.getConditionType());
        conditionTypeCombo.setToolTipText("Select condition criteria to evaluate");
        fieldsPanel.add(conditionTypeCombo, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("matches:"), gbc);

        gbc.gridx = 3; gbc.weightx = 0.6;
        expectedValueField = new JTextField(branch.getExpectedValue());
        expectedValueField.setPreferredSize(new Dimension(140, 26));
        conditionTypeCombo.addActionListener(e -> updateExpectedHint());
        updateExpectedHint();
        fieldsPanel.add(expectedValueField, gbc);

        // Row 2: THEN / ELSE Actions side by side
        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 1; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("THEN:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.5;
        onTrueCombo = new JComboBox<>(ConditionBranch.BranchAction.values());
        onTrueCombo.setPreferredSize(new Dimension(190, 26));
        onTrueCombo.setSelectedItem(branch.getOnTrueAction());
        onTrueCombo.setToolTipText("Action to take if condition evaluates to TRUE");
        fieldsPanel.add(onTrueCombo, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("ELSE:"), gbc);

        gbc.gridx = 3; gbc.weightx = 0.5;
        onFalseCombo = new JComboBox<>(ConditionBranch.BranchAction.values());
        onFalseCombo.setPreferredSize(new Dimension(190, 26));
        onFalseCombo.setSelectedItem(branch.getOnFalseAction());
        onFalseCombo.setToolTipText("Action to take if condition evaluates to FALSE");
        fieldsPanel.add(onFalseCombo, gbc);

        // Row 3: Note / Failure Explanation
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("Note:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        failureNoteField = new JTextField(branch.getFailureNote());
        failureNoteField.setPreferredSize(new Dimension(300, 26));
        failureNoteField.setToolTipText("Optional failure note logged when condition causes failure/stop");
        fieldsPanel.add(failureNoteField, gbc);

        add(fieldsPanel, BorderLayout.CENTER);
    }

    private void updateExpectedHint() {
        ConditionBranch.ConditionType ct = (ConditionBranch.ConditionType) conditionTypeCombo.getSelectedItem();
        if (ct == null) return;
        switch (ct) {
            case STATUS_CODE_EQUALS:
                expectedValueField.setToolTipText("Expected HTTP status code, e.g. 200, 201, 403");
                break;
            case BODY_CONTAINS:
                expectedValueField.setToolTipText("Text or variable to find in response body, e.g. success or {{VAR}}");
                break;
            case BODY_MATCHES_REGEX:
                expectedValueField.setToolTipText("Regex pattern to test response body, e.g. (token|auth)=[a-zA-Z0-9]+");
                break;
            case VARIABLE_NOT_EMPTY:
                expectedValueField.setToolTipText("Variable name to verify is non-empty, e.g. USER_ID or {{USER_ID}}");
                break;
        }
    }

    public ConditionBranch getBranchData() {
        branch.setName(nameField.getText().trim());
        branch.setConditionType((ConditionBranch.ConditionType) conditionTypeCombo.getSelectedItem());
        branch.setExpectedValue(expectedValueField.getText().trim());
        branch.setOnTrueAction((ConditionBranch.BranchAction) onTrueCombo.getSelectedItem());
        branch.setOnFalseAction((ConditionBranch.BranchAction) onFalseCombo.getSelectedItem());
        branch.setFailureNote(failureNoteField.getText().trim());
        return branch;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}

