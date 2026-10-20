package thorization.ai;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.ui.components.AuditTableModel;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuditTableModelAiTest {

    @BeforeAll
    static void setupHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void testBackwardCompatibilityColumnCountAndNames() {
        AuditTableModel model = new AuditTableModel();
        UserRole attackerRole = new UserRole("Attacker", "Header: val", "");
        attackerRole.setId("role-1");
        model.setRoles(List.of(attackerRole));

        // Contract from EngineControllerTest: 8 base + 1 role + 1 unauthenticated = 10 columns
        assertEquals(10, model.getColumnCount());
        assertEquals("Source", model.getColumnName(2));
        assertEquals("Action", model.getColumnName(3));
        assertEquals("Method", model.getColumnName(4));
        assertEquals("Attacker", model.getColumnName(8));
        assertEquals("Unauthenticated", model.getColumnName(9));
    }

    @Test
    void testRoleCellAndUnauthenticatedMaintainIndependentVerdicts() {
        AuditTableModel model = new AuditTableModel();
        UserRole role = new UserRole("Attacker", "H: v", "");
        role.setId("role-1");
        model.setRoles(List.of(role));

        AuditRecord record = new AuditRecord(1, null);
        AuthorizationCheckResult res = new AuthorizationCheckResult(
                "role-1", "Attacker", Verdict.BYPASS, 200, 100, 0.9, 50, "note", null, null
        );
        record.addRoleResult("role-1", res);

        AuthorizationCheckResult unauth = new AuthorizationCheckResult(
                "unauth", "Unauthenticated", Verdict.BYPASS, 200, 785, 0.94, 40, "note", null, null
        );
        record.setUnauthenticatedResult(unauth);

        model.addRecord(record);
        model.setShowAiColumn(true);

        // Before AI evaluation: Role = Verdict.BYPASS, Unauth = Verdict.BYPASS, AI = null
        assertEquals(Verdict.BYPASS, model.getValueAt(0, 8), "Role column must reflect its own HTTP check verdict");
        assertEquals(Verdict.BYPASS, model.getValueAt(0, 9), "Unauthenticated column must reflect its own HTTP check verdict");
        assertNull(model.getValueAt(0, 10), "AI Verdict column is null prior to evaluation");

        // When AI evaluation is PENDING: Role and Unauth must remain independent
        res.setAiResult(AiEvaluationResult.pending("gpt-4o"));
        unauth.setAiResult(AiEvaluationResult.pending("gpt-4o"));
        assertEquals(Verdict.BYPASS, model.getValueAt(0, 8), "Role column must remain independent during AI pending");
        assertEquals(Verdict.BYPASS, model.getValueAt(0, 9), "Unauthenticated column must remain independent during AI pending");
        assertEquals(AiVerdict.PENDING, model.getValueAt(0, 10), "AI column reflects PENDING");

        // When AI evaluation errors out (e.g. AI not configured properly):
        // Role and Unauthenticated columns must NEVER become ERROR!
        res.setAiResult(AiEvaluationResult.error("AI not configured", "gpt-4o", 100));
        unauth.setAiResult(AiEvaluationResult.error("AI not configured", "gpt-4o", 100));
        assertEquals(Verdict.BYPASS, model.getValueAt(0, 8), "Role column must NOT become ERROR when AI fails");
        assertEquals(Verdict.BYPASS, model.getValueAt(0, 9), "Unauthenticated column must NOT become ERROR when AI fails");
        assertEquals(AiVerdict.ERROR, model.getValueAt(0, 10), "AI column reflects ERROR");

        // When AI evaluation is successful:
        res.setAiResult(AiEvaluationResult.success(AiVerdict.VALIDATED_ACCESS, "BOLA", "gpt-4o", 200));
        unauth.setAiResult(AiEvaluationResult.success(AiVerdict.VALIDATED_ACCESS, "Unauth access", "gpt-4o", 200));
        assertEquals(Verdict.BYPASS, model.getValueAt(0, 8), "Role column remains independent");
        assertEquals(Verdict.BYPASS, model.getValueAt(0, 9), "Unauthenticated column remains independent");
        assertEquals(AiVerdict.VALIDATED_ACCESS, model.getValueAt(0, 10), "AI column reflects VALIDATED_ACCESS");
    }

    @Test
    void testShowAiColumnToggle() {
        AuditTableModel model = new AuditTableModel();
        UserRole role = new UserRole("Attacker", "H: v", "");
        role.setId("role-1");
        model.setRoles(List.of(role));

        assertFalse(model.isShowAiColumn());
        assertEquals(10, model.getColumnCount());

        model.setShowAiColumn(true);
        assertTrue(model.isShowAiColumn());
        assertEquals(11, model.getColumnCount());
        assertEquals("AI Verdict", model.getColumnName(10));

        AuditRecord record = new AuditRecord(1, null);
        record.setAiResult(AiEvaluationResult.success(AiVerdict.VALIDATED_ACCESS, "IDOR confirmed", "gpt-4o", 100));
        model.addRecord(record);

        assertEquals(AiVerdict.VALIDATED_ACCESS, model.getValueAt(0, 10));
    }

    @Test
    void testOverallAiVerdictPriority() {
        AuditRecord record = new AuditRecord(1, null);
        assertNull(record.getOverallAiVerdict());

        // Role 1 is ACCESS_DENIED
        AuthorizationCheckResult r1 = new AuthorizationCheckResult();
        r1.setAiResult(AiEvaluationResult.success(AiVerdict.ACCESS_DENIED, "Blocked", "m", 100));
        record.addRoleResult("r1", r1);
        assertEquals(AiVerdict.ACCESS_DENIED, record.getOverallAiVerdict());

        // Role 2 is PENDING -> overall becomes PENDING
        AuthorizationCheckResult r2 = new AuthorizationCheckResult();
        r2.setAiResult(AiEvaluationResult.pending("m"));
        record.addRoleResult("r2", r2);
        assertEquals(AiVerdict.PENDING, record.getOverallAiVerdict());

        // Role 3 is VALIDATED_ACCESS -> overall becomes VALIDATED_ACCESS (highest priority vulnerability)
        AuthorizationCheckResult r3 = new AuthorizationCheckResult();
        r3.setAiResult(AiEvaluationResult.success(AiVerdict.VALIDATED_ACCESS, "Bypass", "m", 100));
        record.addRoleResult("r3", r3);
        assertEquals(AiVerdict.VALIDATED_ACCESS, record.getOverallAiVerdict());
    }
}
