package thorization.ui.components;

import thorization.model.UserRole;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Modal dialog displaying a dynamic checklist of configured roles (N users)
 * and unauthenticated state for selective re-testing.
 */
public class RetestRolesDialog extends JDialog {

    private final Map<UserRole, JCheckBox> roleCheckBoxes = new LinkedHashMap<>();
    private JCheckBox unauthCheckBox;

    private boolean confirmed = false;

    public RetestRolesDialog(Component parent, String title, String description, List<UserRole> roles, boolean defaultUnauth) {
        super(parent != null ? (Frame) SwingUtilities.getWindowAncestor(parent) : null, title, true);

        setLayout(new BorderLayout());
        setSize(520, 440);
        setLocationRelativeTo(parent);
        setResizable(false);

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));

        // 1. Native Header Labels (No raw HTML tags)
        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));

        JLabel titleLabel = new JLabel(description);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));

        JLabel subtitleLabel = new JLabel("Select which user role(s) to re-test against target requests:");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        subtitleLabel.setForeground(Color.GRAY);

        headerPanel.add(titleLabel);
        headerPanel.add(subtitleLabel);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        // 2. Select All / Deselect All Toolbar
        JPanel toggleBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        JButton selectAllBtn = new JButton("Select All");
        selectAllBtn.setFont(selectAllBtn.getFont().deriveFont(11f));
        selectAllBtn.addActionListener(e -> setAllSelected(true));

        JButton deselectAllBtn = new JButton("Deselect All");
        deselectAllBtn.setFont(deselectAllBtn.getFont().deriveFont(11f));
        deselectAllBtn.addActionListener(e -> setAllSelected(false));

        toggleBar.add(selectAllBtn);
        toggleBar.add(deselectAllBtn);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(headerPanel, BorderLayout.NORTH);
        topContainer.add(toggleBar, BorderLayout.SOUTH);
        mainPanel.add(topContainer, BorderLayout.NORTH);

        // 3. Center Checkbox List (Native theme colors)
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        if (roles != null && !roles.isEmpty()) {
            for (UserRole role : roles) {
                JCheckBox cb = new JCheckBox(role.getName() + (role.isEnabled() ? " (Active)" : " (Disabled)"));
                cb.setSelected(role.isEnabled());
                cb.setFont(cb.getFont().deriveFont(Font.PLAIN, 13f));
                cb.setBorder(BorderFactory.createEmptyBorder(3, 4, 3, 4));
                roleCheckBoxes.put(role, cb);
                listPanel.add(cb);
            }
        } else {
            JLabel emptyLabel = new JLabel("No roles configured in Roles Manager.");
            emptyLabel.setForeground(Color.GRAY);
            listPanel.add(emptyLabel);
        }

        // Unauthenticated Checkbox
        unauthCheckBox = new JCheckBox("Unauthenticated Test (No Auth Headers)");
        unauthCheckBox.setSelected(defaultUnauth);
        unauthCheckBox.setFont(unauthCheckBox.getFont().deriveFont(Font.BOLD, 12f));
        unauthCheckBox.setForeground(new Color(220, 53, 69));
        unauthCheckBox.setBorder(BorderFactory.createEmptyBorder(5, 4, 3, 4));
        listPanel.add(unauthCheckBox);

        JScrollPane scrollPane = new JScrollPane(listPanel);
        scrollPane.setPreferredSize(new Dimension(480, 200));
        scrollPane.setBorder(BorderFactory.createTitledBorder("Available Roles to Re-Test"));
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // 4. Bottom Action Buttons Panel (Clean alignment, no overlapping)
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 8));
        JButton runBtn = new JButton("Run Re-Test");
        runBtn.setFont(runBtn.getFont().deriveFont(Font.BOLD, 12f));
        runBtn.setPreferredSize(new Dimension(120, 30));
        runBtn.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setPreferredSize(new Dimension(90, 30));
        cancelBtn.addActionListener(e -> {
            confirmed = false;
            dispose();
        });

        bottomPanel.add(runBtn);
        bottomPanel.add(cancelBtn);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private void setAllSelected(boolean selected) {
        for (JCheckBox cb : roleCheckBoxes.values()) {
            cb.setSelected(selected);
        }
        unauthCheckBox.setSelected(selected);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public List<UserRole> getSelectedRoles() {
        List<UserRole> selected = new ArrayList<>();
        for (Map.Entry<UserRole, JCheckBox> entry : roleCheckBoxes.entrySet()) {
            if (entry.getValue().isSelected()) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    public boolean isTestUnauthenticatedSelected() {
        return unauthCheckBox.isSelected();
    }
}
