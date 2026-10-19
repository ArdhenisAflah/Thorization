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

    private Object getField(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}
