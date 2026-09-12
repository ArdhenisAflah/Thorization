package thorization.ui.components.pipeline;

import thorization.model.pipeline.ConditionBranch;

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
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(251, 188, 4), 2, true),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));
        setBackground(new Color(255, 253, 245));

        // --- Header Bar ---
        JPanel headerPanel = new JPanel(new BorderLayout(5, 0));
        headerPanel.setOpaque(false);

        JLabel typeBadge = new JLabel(" 🟨 LOGIC BRANCH (IF / ELSE) ");
        typeBadge.setFont(typeBadge.getFont().deriveFont(Font.BOLD, 12f));
        typeBadge.setOpaque(true);
        typeBadge.setBackground(new Color(230, 160, 0));
        typeBadge.setForeground(Color.WHITE);
        typeBadge.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

        nameField = new JTextField(branch.getName(), 18);
        nameField.setFont(nameField.getFont().deriveFont(Font.BOLD, 12f));

        JPanel titleLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        titleLeft.setOpaque(false);
        titleLeft.add(typeBadge);
        titleLeft.add(new JLabel("Branch Name:"));
        titleLeft.add(nameField);

        JPanel controlsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        controlsRight.setOpaque(false);

        JButton upBtn = new JButton("▲");
        JButton downBtn = new JButton("▼");
        JButton removeBtn = new JButton("✖");
        removeBtn.setForeground(new Color(220, 53, 69));

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

        // Row 1: Condition Statement (IF <type> == <expected>)
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("Condition: IF"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.4;
        conditionTypeCombo = new JComboBox<>(ConditionBranch.ConditionType.values());
        conditionTypeCombo.setSelectedItem(branch.getConditionType());
        fieldsPanel.add(conditionTypeCombo, gbc);

        gbc.gridx = 2; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("matches value:"), gbc);

        gbc.gridx = 3; gbc.weightx = 0.6;
        expectedValueField = new JTextField(branch.getExpectedValue());
        fieldsPanel.add(expectedValueField, gbc);

        // Row 2: THEN Action (On True)
        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("THEN (True):"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        onTrueCombo = new JComboBox<>(ConditionBranch.BranchAction.values());
        onTrueCombo.setSelectedItem(branch.getOnTrueAction());
        fieldsPanel.add(onTrueCombo, gbc);

        // Row 3: ELSE Action (On False)
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("ELSE (False):"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        onFalseCombo = new JComboBox<>(ConditionBranch.BranchAction.values());
        onFalseCombo.setSelectedItem(branch.getOnFalseAction());
        fieldsPanel.add(onFalseCombo, gbc);

        // Row 4: Note / Failure Explanation
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1; gbc.weightx = 0.0;
        fieldsPanel.add(new JLabel("Failure Note:"), gbc);

        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0;
        failureNoteField = new JTextField(branch.getFailureNote());
        fieldsPanel.add(failureNoteField, gbc);

        add(fieldsPanel, BorderLayout.CENTER);
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
}

