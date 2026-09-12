package thorization;

import org.junit.jupiter.api.Test;
import thorization.core.EngineController;
import thorization.core.EngineListener;
import thorization.model.AuditRecord;
import thorization.model.FilterConfig;
import thorization.model.InterceptionMode;
import thorization.model.OriginalAction;
import thorization.model.UserRole;
import thorization.ui.components.AuditTableModel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class EngineControllerTest {

    @Test
    void testRecordRemovalAndListeners() {
        EngineController controller = new EngineController(null);
        AtomicBoolean removedCalled = new AtomicBoolean(false);

        controller.addListener(new EngineListener() {
            @Override public void onAuditRecordAdded(AuditRecord record) {}
            @Override public void onAuditRecordUpdated(AuditRecord record) {}
            @Override public void onAuditRecordsRemoved(List<AuditRecord> removedRecords) {
                removedCalled.set(true);
            }
            @Override public void onAuditRecordsCleared() {}
            @Override public void onRolesChanged(List<UserRole> roles) {}
            @Override public void onEngineStateChanged(boolean enabled) {}
            @Override public void onFilterConfigChanged(FilterConfig config) {}
            @Override public void onPipelinesChanged(List<thorization.model.pipeline.Pipeline> pipelines) {}
        });

        AuditRecord rec1 = new AuditRecord(1, null);
        AuditRecord rec2 = new AuditRecord(2, null);
        List<AuditRecord> list = new ArrayList<>();
        list.add(rec1);
        list.add(rec2);

        controller.importRecords(list, null);
        assertEquals(2, controller.getRecords().size());

        List<AuditRecord> toDelete = new ArrayList<>();
        toDelete.add(rec1);
        controller.removeRecords(toDelete);

        assertEquals(1, controller.getRecords().size());
        assertEquals(2, controller.getRecords().get(0).getId());
    }

    @Test
    void testRoleExecutionOrdering() {
        EngineController controller = new EngineController(null);
        UserRole r1 = new UserRole("Role 1", "Auth: 1", "");
        UserRole r2 = new UserRole("Role 2", "Auth: 2", "");
        UserRole r3 = new UserRole("Role 3", "Auth: 3", "");

        controller.addRole(r1);
        controller.addRole(r2);
        controller.addRole(r3);

        assertEquals("Role 1", controller.getRoles().get(0).getName());
        assertEquals("Role 2", controller.getRoles().get(1).getName());
        assertEquals("Role 3", controller.getRoles().get(2).getName());

        // Move Role 3 Up
        controller.moveRoleUp(2);
        assertEquals("Role 3", controller.getRoles().get(1).getName());
        assertEquals("Role 2", controller.getRoles().get(2).getName());

        // Move Role 1 Down
        controller.moveRoleDown(0);
        assertEquals("Role 3", controller.getRoles().get(0).getName());
        assertEquals("Role 1", controller.getRoles().get(1).getName());
        assertEquals("Role 2", controller.getRoles().get(2).getName());
    }

    @Test
    void testInterceptionModeAndFilterConfig() {
        FilterConfig config = new FilterConfig();
        assertEquals(InterceptionMode.PASS_THROUGH_AND_REPLAY, config.getInterceptionMode());

        config.setInterceptionMode(InterceptionMode.DROP_ORIGINAL_AND_REPLAY);
        assertEquals(InterceptionMode.DROP_ORIGINAL_AND_REPLAY, config.getInterceptionMode());

        assertTrue(config.isStateChanging("POST"));
        assertTrue(config.isStateChanging("PUT"));
        assertTrue(config.isStateChanging("DELETE"));
        assertTrue(config.isStateChanging("PATCH"));
        assertFalse(config.isStateChanging("GET"));
        assertFalse(config.isStateChanging("OPTIONS"));
    }

    @Test
    void testAuditRecordOriginalAction() {
        AuditRecord dropped = new AuditRecord(1, null, OriginalAction.DROPPED);
        assertEquals(OriginalAction.DROPPED, dropped.getOriginalAction());
        assertEquals("DROPPED", dropped.getOriginalAction().getLabel());

        AuditRecord passed = new AuditRecord(2, null, OriginalAction.PASSED);
        assertEquals(OriginalAction.PASSED, passed.getOriginalAction());
        assertEquals("PASSED", passed.getOriginalAction().getLabel());
    }

    @Test
    void testAuditTableModelWithActionColumn() {
        AuditTableModel model = new AuditTableModel();
        UserRole r1 = new UserRole("Attacker", "H: 1", "");
        List<UserRole> roles = new ArrayList<>();
        roles.add(r1);
        model.setRoles(roles);

        // Columns: #, Time, Source, Action, Method, URL, Orig Status, Orig Length, Attacker, Unauthenticated
        assertEquals(10, model.getColumnCount());
        assertEquals("Source", model.getColumnName(2));
        assertEquals("Action", model.getColumnName(3));
        assertEquals("Method", model.getColumnName(4));
        assertEquals("Attacker", model.getColumnName(8));
        assertEquals("Unauthenticated", model.getColumnName(9));

        AuditRecord rec = new AuditRecord(10, null, OriginalAction.DROPPED, "Repeater");
        rec.setMethod("DELETE");
        rec.setUrl("https://api.example.com/user/5");
        model.addRecord(rec);

        assertEquals("Repeater", model.getValueAt(0, 2));
        assertEquals(OriginalAction.DROPPED, model.getValueAt(0, 3));
        assertEquals("DELETE", model.getValueAt(0, 4));
    }
}
