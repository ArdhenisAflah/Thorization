package thorization.ai;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thorization.model.AuditRecord;
import thorization.ui.components.RequestResponseViewerPanel;

import javax.swing.JTabbedPane;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class RequestResponseViewerPanelAiTest {

    @BeforeAll
    static void setupHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void testAiTabInitialization() {
        RequestResponseViewerPanel panel = new RequestResponseViewerPanel(null, null);
        JTabbedPane tabbedPane = panel.getTabbedPane();

        boolean hasAiTab = false;
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if ("AI Semantic Analysis".equals(tabbedPane.getTitleAt(i))) {
                hasAiTab = true;
                break;
            }
        }
        assertTrue(hasAiTab, "RequestResponseViewerPanel must contain 'AI Semantic Analysis' tab");
        assertNotNull(panel.getAiTabPanel());
        assertNotNull(panel.getAiVerdictBadgeLabel());
        assertNotNull(panel.getAiModelTagLabel());
        assertNotNull(panel.getAiReasoningArea());
        assertNotNull(panel.getReanalyzeBtn());
        assertNotNull(panel.getCopyReasoningBtn());
    }

    @Test
    void testDisplayRecordWithAiEvaluation() {
        RequestResponseViewerPanel panel = new RequestResponseViewerPanel(null, null);

        AuditRecord record = new AuditRecord(1, null);
        AiEvaluationResult result = AiEvaluationResult.success(
                AiVerdict.VALIDATED_ACCESS,
                "BOLA confirmed: User 100 accessed User 200 account data.",
                "gpt-4o-mini",
                320L
        );
        record.setAiResult(result);

        panel.displayRecord(record, Collections.emptyList());

        assertTrue(panel.getAiVerdictBadgeLabel().getText().contains("VALIDATED_ACCESS"));
        assertEquals("Model: gpt-4o-mini", panel.getAiModelTagLabel().getText());
        assertTrue(panel.getAiLatencyLabel().getText().contains("320 ms"));
        assertTrue(panel.getAiReasoningArea().getText().contains("BOLA confirmed"));
        assertTrue(panel.getReanalyzeBtn().isEnabled());

        // Clear viewers
        panel.clearViewers();
        assertTrue(panel.getAiVerdictBadgeLabel().getText().contains("No Record Selected"));
        assertEquals("Model: -", panel.getAiModelTagLabel().getText());
        assertFalse(panel.getReanalyzeBtn().isEnabled());
    }

    @Test
    void testDisplayCachedResult() {
        RequestResponseViewerPanel panel = new RequestResponseViewerPanel(null, null);

        AiEvaluationResult cachedResult = AiEvaluationResult.success(
                AiVerdict.ACCESS_DENIED,
                "Request was blocked with 403 Forbidden.",
                "deepseek-chat",
                100L
        ).withCached(true);

        panel.displayAiResult(cachedResult);

        assertTrue(panel.getAiVerdictBadgeLabel().getText().contains("ACCESS_DENIED"));
        assertTrue(panel.getAiCacheBadgeLabel().getText().contains("Cached"));
        assertEquals("Latency: 0 ms", panel.getAiLatencyLabel().getText());
    }
}
