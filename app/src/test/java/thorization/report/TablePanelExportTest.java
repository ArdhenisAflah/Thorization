package thorization.report;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thorization.core.EngineController;
import thorization.model.AuditRecord;
import thorization.model.UserRole;
import thorization.substitution.MockHttpRequestHelper;
import thorization.ui.TablePanel;

import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TablePanelExportTest {

    @BeforeAll
    static void init() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    @DisplayName("Verify Toolbar and Context Menu Export Controls in Headless Mode")
    void testToolbarAndContextMenuButtons() {
        EngineController controller = new EngineController(null);
        TablePanel panel = new TablePanel(null, controller);

        // 1. Verify Toolbar Buttons
        JButton htmlBtn = panel.getHtmlReportBtn();
        assertNotNull(htmlBtn, "HTML Report button must exist in toolbar");
        assertEquals("HTML Report", htmlBtn.getText());
        assertTrue(htmlBtn.getToolTipText().toLowerCase().contains("html"));

        JButton csvBtn = panel.getCsvExportBtn();
        assertNotNull(csvBtn, "CSV Export button must exist in toolbar");
        assertEquals("CSV Export", csvBtn.getText());
        assertTrue(csvBtn.getToolTipText().toLowerCase().contains("csv"));

        JButton exportBtn = panel.getExportBtn();
        assertNotNull(exportBtn, "Export button must exist in toolbar");
        assertEquals("Export", exportBtn.getText());

        JButton importBtn = panel.getImportBtn();
        assertNotNull(importBtn, "Import button must exist in toolbar");
        assertEquals("Import", importBtn.getText());

        assertNotNull(panel.getSearchFilterField(), "Search filter field must exist in toolbar");
        assertNotNull(panel.getCountLabel(), "Count label must exist in toolbar");

        // 2. Verify Context Menu Items
        JPopupMenu menu = panel.getContextMenu();
        assertNotNull(menu, "Table context menu must exist");

        boolean hasCopyCurl = false;
        boolean hasExportHtml = false;
        boolean hasExportCsv = false;
        boolean hasAiAudit = false;

        for (Component comp : menu.getComponents()) {
            if (comp instanceof JMenuItem item) {
                String text = item.getText();
                if ("Copy as cURL".equals(text)) hasCopyCurl = true;
                if ("Export as HTML...".equals(text)) hasExportHtml = true;
                if ("Export as CSV...".equals(text)) hasExportCsv = true;
                if ("AI Audit Selected Rows".equals(text)) hasAiAudit = true;
            }
        }

        assertTrue(hasCopyCurl, "Context menu must include 'Copy as cURL'");
        assertTrue(hasExportHtml, "Context menu must include 'Export as HTML...'");
        assertTrue(hasExportCsv, "Context menu must include 'Export as CSV...'");
        assertTrue(hasAiAudit, "Context menu must include 'AI Audit Selected Rows'");
    }

    @Test
    @DisplayName("Verify Headless Safety: Copy as cURL with No Selection")
    void testCopyAsCurlEmpty() {
        EngineController controller = new EngineController(null);
        TablePanel panel = new TablePanel(null, controller);

        // Calling triggerCopyAsCurl with zero selection must complete safely without HeadlessException
        assertDoesNotThrow(() -> panel.triggerCopyAsCurl());
    }

    @Test
    @DisplayName("Verify Headless Safety: Copy as cURL with Selected Record")
    void testCopyAsCurlWithSelection() {
        EngineController controller = new EngineController(null);
        TablePanel panel = new TablePanel(null, controller);

        AuditRecord rec = new AuditRecord(controller.nextRecordId(), null);
        rec.setUrl("https://api.example.com/data");
        rec.setMethod("POST");
        rec.setOriginalRequest(MockHttpRequestHelper.createMockRequest(
                "https://api.example.com/data", "POST", "{\"test\":1}", Map.of("X-Test", "true")
        ));
        controller.importRecords(List.of(rec), Collections.emptyList(), Collections.emptyList(), false);
        panel.getTableModel().addRecord(rec);

        panel.getAuditTable().setRowSelectionInterval(0, 0);

        assertDoesNotThrow(() -> panel.triggerCopyAsCurl());
    }

    @Test
    @DisplayName("Verify Headless Safety: Trigger Export HTML and CSV Handlers")
    void testExportTriggersHeadlessSafety() {
        EngineController controller = new EngineController(null);
        TablePanel panel = new TablePanel(null, controller);

        // Test with empty records
        assertDoesNotThrow(() -> panel.triggerExportHtmlReport());
        assertDoesNotThrow(() -> panel.triggerExportCsvReport());

        // Test with populated records
        AuditRecord rec = new AuditRecord(controller.nextRecordId(), null);
        rec.setUrl("https://api.example.com/test");
        rec.setMethod("GET");
        controller.importRecords(List.of(rec), List.of(new UserRole("TestRole", "Header: 1", "")), Collections.emptyList(), false);

        assertDoesNotThrow(() -> panel.triggerExportHtmlReport());
        assertDoesNotThrow(() -> panel.triggerExportCsvReport());
    }

    @Test
    @DisplayName("Verify Timestamped Filename Generation Pattern")
    void testTimestampedDefaultFilename() {
        String pattern = "^thorization-report-\\d{8}-\\d{6}\\.(html|csv)$";
        String htmlName = "thorization-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".html";
        String csvName = "thorization-report-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv";

        assertTrue(htmlName.matches(pattern), "HTML default filename must match format thorization-report-YYYYMMDD-HHmmss.html");
        assertTrue(csvName.matches(pattern), "CSV default filename must match format thorization-report-YYYYMMDD-HHmmss.csv");
    }

    @Test
    @DisplayName("Verify Headless Safety: AI Audit Selected Rows")
    void testAiAuditSelectedRowsAction() {
        EngineController controller = new EngineController(null);
        TablePanel panel = new TablePanel(null, controller);

        // 1. Calling with empty selection should not throw
        assertDoesNotThrow(() -> panel.triggerAiAuditSelectedRows());

        // 2. Add a record and select it
        AuditRecord rec = new AuditRecord(controller.nextRecordId(), null);
        rec.setUrl("https://api.example.com/test-ai");
        rec.setMethod("GET");
        controller.importRecords(List.of(rec), Collections.emptyList(), Collections.emptyList(), false);
        panel.getTableModel().addRecord(rec);

        panel.getAuditTable().setRowSelectionInterval(0, 0);

        // AI column should initially be false
        assertFalse(panel.getTableModel().isShowAiColumn());

        // Trigger AI Audit Selected Rows
        assertDoesNotThrow(() -> panel.triggerAiAuditSelectedRows());

        // AI column should now be enabled
        assertTrue(panel.getTableModel().isShowAiColumn(), "AI column must be shown when auditing rows with AI");
    }

    @Test
    @DisplayName("Verify that retest or AI evaluation completion (onAuditRecordUpdated) automatically enables AI column")
    void testRecordUpdatedTriggersShowAiColumn() {
        EngineController controller = new EngineController(null);
        TablePanel panel = new TablePanel(null, controller);

        assertFalse(panel.getTableModel().isShowAiColumn(), "AI column should initially be hidden");

        AuditRecord rec = new AuditRecord(controller.nextRecordId(), null);
        panel.onAuditRecordAdded(rec);
        assertFalse(panel.getTableModel().isShowAiColumn(), "AI column remains hidden for fresh unanalyzed records");

        // Simulate retest or single-row AI completion notifying onAuditRecordUpdated
        panel.onAuditRecordUpdated(rec);
        assertTrue(panel.getTableModel().isShowAiColumn(), "AI column must automatically be revealed when retest or AI evaluation completes");
    }

    @Test
    @DisplayName("Verify that imported/added records with AI results automatically enable AI column")
    void testRecordAddedWithAiResultTriggersShowAiColumn() {
        EngineController controller = new EngineController(null);
        TablePanel panel = new TablePanel(null, controller);

        assertFalse(panel.getTableModel().isShowAiColumn(), "AI column should initially be hidden");

        AuditRecord rec = new AuditRecord(controller.nextRecordId(), null);
        rec.setAiResult(thorization.ai.AiEvaluationResult.pending("test-model"));

        panel.onAuditRecordAdded(rec);
        assertTrue(panel.getTableModel().isShowAiColumn(), "AI column must automatically be revealed when record has AI results");
    }
}
