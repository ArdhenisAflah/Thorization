package thorization;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thorization.core.EngineController;
import thorization.model.UserRole;
import thorization.ui.RolesPanel;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RolesPanelTest {

    private EngineController controller;
    private RolesPanel rolesPanel;

    @BeforeEach
    void setUp() {
        controller = new EngineController(null);
        rolesPanel = new RolesPanel(controller);
    }

    @Test
    void testFormHasNoDefaultValuesOnStartup() throws Exception {
        JTextField roleNameField = (JTextField) getField(rolesPanel, "roleNameField");
        JTextField cookiesField = (JTextField) getField(rolesPanel, "cookiesField");
        JTextArea headersArea = (JTextArea) getField(rolesPanel, "headersArea");

        assertNotNull(roleNameField);
        assertNotNull(cookiesField);
        assertNotNull(headersArea);

        // Verify all fields are completely blank on initial startup
        assertEquals("", roleNameField.getText(), "Role name should be empty by default");
        assertEquals("", cookiesField.getText(), "Cookies should have no dummy default text");
        assertEquals("", headersArea.getText(), "Headers should have no dummy default text");
    }

    @Test
    void testSaveRoleChangesCreatesNewRoleWhenInitiallyEmpty() throws Exception {
        assertTrue(controller.getRoles().isEmpty(), "Initial roles should be empty");

        JTextField roleNameField = (JTextField) getField(rolesPanel, "roleNameField");
        JTextField cookiesField = (JTextField) getField(rolesPanel, "cookiesField");
        JTextArea headersArea = (JTextArea) getField(rolesPanel, "headersArea");
        JCheckBox roleEnabledCheckBox = (JCheckBox) getField(rolesPanel, "roleEnabledCheckBox");

        // Simulate user typing directly into the form on first load
        roleNameField.setText("Admin-Attacker");
        cookiesField.setText("session=admin_tok_123");
        headersArea.setText("Authorization: Bearer secret_123\nX-Role: admin");
        roleEnabledCheckBox.setSelected(true);

        // Invoke onSaveCurrentRole method (same as clicking 'Save Role Changes')
        Method saveMethod = RolesPanel.class.getDeclaredMethod("onSaveCurrentRole");
        saveMethod.setAccessible(true);
        saveMethod.invoke(rolesPanel);

        // Verify role was created and saved into controller without separate Add Role button
        List<UserRole> roles = controller.getRoles();
        assertEquals(1, roles.size(), "Should have exactly 1 role after saving");

        UserRole savedRole = roles.get(0);
        assertEquals("Admin-Attacker", savedRole.getName());
        assertEquals("session=admin_tok_123", savedRole.getRawCookies());
        assertEquals("Authorization: Bearer secret_123\nX-Role: admin", savedRole.getRawHeaders());
        assertTrue(savedRole.isEnabled());

        // Verify that subsequent save updates the existing role
        roleNameField.setText("Admin-Attacker-Updated");
        saveMethod.invoke(rolesPanel);

        assertEquals(1, controller.getRoles().size());
        assertEquals("Admin-Attacker-Updated", controller.getRoles().get(0).getName());
    }

    @Test
    void testGenerateDuplicateRoleName_SequentialOrdering() {
        List<UserRole> roles = new java.util.ArrayList<>();
        roles.add(new UserRole("admin", "", ""));

        // Duplicate admin -> admin1
        String dup1 = RolesPanel.generateDuplicateRoleName("admin", roles);
        assertEquals("admin1", dup1);
        roles.add(new UserRole(dup1, "", ""));

        // Duplicate admin again -> admin2
        String dup2 = RolesPanel.generateDuplicateRoleName("admin", roles);
        assertEquals("admin2", dup2);
        roles.add(new UserRole(dup2, "", ""));

        // Duplicate admin2 -> admin3
        String dup3 = RolesPanel.generateDuplicateRoleName("admin2", roles);
        assertEquals("admin3", dup3);
        roles.add(new UserRole(dup3, "", ""));

        // Verify full sequence matches user requirement: admin, admin1, admin2, admin3
        assertEquals("admin", roles.get(0).getName());
        assertEquals("admin1", roles.get(1).getName());
        assertEquals("admin2", roles.get(2).getName());
        assertEquals("admin3", roles.get(3).getName());

        // Legacy format clean-up: "admin (Copy)" -> "admin4" (since admin1-3 already exist)
        String legacyDup = RolesPanel.generateDuplicateRoleName("admin (Copy)", roles);
        assertEquals("admin4", legacyDup);
    }

    @Test
    void testDuplicateRoleActionIntegration() throws Exception {
        controller.addRole(new UserRole("admin", "H: v", "c=1"));
        rolesPanel.onRolesChanged(controller.getRoles());

        Field indexField = RolesPanel.class.getDeclaredField("selectedRoleIndex");
        indexField.setAccessible(true);
        indexField.set(rolesPanel, 0);

        Method dupMethod = RolesPanel.class.getDeclaredMethod("onDuplicateRole");
        dupMethod.setAccessible(true);

        // 1st duplicate: admin -> admin1
        dupMethod.invoke(rolesPanel);
        assertEquals(2, controller.getRoles().size());
        assertEquals("admin1", controller.getRoles().get(1).getName());

        // 2nd duplicate: duplicating admin again -> admin2
        indexField.set(rolesPanel, 0);
        dupMethod.invoke(rolesPanel);
        assertEquals(3, controller.getRoles().size());
        assertEquals("admin2", controller.getRoles().get(2).getName());

        // 3rd duplicate: duplicating admin2 -> admin3
        indexField.set(rolesPanel, 2);
        dupMethod.invoke(rolesPanel);
        assertEquals(4, controller.getRoles().size());
        assertEquals("admin3", controller.getRoles().get(3).getName());
    }

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
