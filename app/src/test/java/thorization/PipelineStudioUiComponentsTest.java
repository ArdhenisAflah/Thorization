package thorization;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import thorization.model.UserRole;
import thorization.model.pipeline.ConditionBranch;
import thorization.model.pipeline.ExtractionRule;
import thorization.model.pipeline.ExtractionSource;
import thorization.model.pipeline.ExtractionType;
import thorization.model.pipeline.RequestStep;
import thorization.ui.components.pipeline.BranchBlockCard;
import thorization.ui.components.pipeline.RequestBlockCard;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PipelineStudioUiComponentsTest {

    @BeforeAll
    static void setupHeadless() {
        System.setProperty("java.awt.headless", "true");
    }

    @Test
    void testParseExtractionSource() {
        // 1. Direct Enum instances
        assertEquals(ExtractionSource.URL_QUERY_PARAM, RequestBlockCard.parseExtractionSource(ExtractionSource.URL_QUERY_PARAM));
        assertEquals(ExtractionSource.RESPONSE_HEADER, RequestBlockCard.parseExtractionSource(ExtractionSource.RESPONSE_HEADER));
        assertEquals(ExtractionSource.REQUEST_HEADER, RequestBlockCard.parseExtractionSource(ExtractionSource.REQUEST_HEADER));

        // 2. String enum names
        assertEquals(ExtractionSource.REQUEST_BODY, RequestBlockCard.parseExtractionSource("REQUEST_BODY"));
        assertEquals(ExtractionSource.URL_QUERY_PARAM, RequestBlockCard.parseExtractionSource("URL_QUERY_PARAM"));

        // 3. Display names
        assertEquals(ExtractionSource.RESPONSE_HEADER, RequestBlockCard.parseExtractionSource("Response Header"));
        assertEquals(ExtractionSource.URL_QUERY_PARAM, RequestBlockCard.parseExtractionSource("URL Query Parameter"));
        assertEquals(ExtractionSource.RESPONSE_BODY, RequestBlockCard.parseExtractionSource("Response Body"));

        // 4. Case-insensitivity and whitespace
        assertEquals(ExtractionSource.REQUEST_HEADER, RequestBlockCard.parseExtractionSource("  request header  "));
        assertEquals(ExtractionSource.URL_QUERY_PARAM, RequestBlockCard.parseExtractionSource("url_query_param"));

        // 5. Fallback for null or unknown string
        assertEquals(ExtractionSource.RESPONSE_BODY, RequestBlockCard.parseExtractionSource(null));
        assertEquals(ExtractionSource.RESPONSE_BODY, RequestBlockCard.parseExtractionSource("UNKNOWN_SOURCE_XYZ"));
    }

    @Test
    void testParseExtractionType() {
        // 1. Direct Enum instances
        assertEquals(ExtractionType.REGEX, RequestBlockCard.parseExtractionType(ExtractionType.REGEX));
        assertEquals(ExtractionType.JSON_PATH, RequestBlockCard.parseExtractionType(ExtractionType.JSON_PATH));
        assertEquals(ExtractionType.EXACT_HEADER, RequestBlockCard.parseExtractionType(ExtractionType.EXACT_HEADER));

        // 2. String enum names
        assertEquals(ExtractionType.REGEX, RequestBlockCard.parseExtractionType("REGEX"));
        assertEquals(ExtractionType.JSON_PATH, RequestBlockCard.parseExtractionType("JSON_PATH"));
        assertEquals(ExtractionType.EXACT_HEADER, RequestBlockCard.parseExtractionType("EXACT_HEADER"));

        // 3. Display names
        assertEquals(ExtractionType.JSON_PATH, RequestBlockCard.parseExtractionType("JSONPath (e.g. $.id or $.data[0].id)"));
        assertEquals(ExtractionType.REGEX, RequestBlockCard.parseExtractionType("Regex with Group (e.g. id=([0-9a-zA-Z_-]+))"));
        assertEquals(ExtractionType.EXACT_HEADER, RequestBlockCard.parseExtractionType("Exact Header / Parameter Name"));

        // 4. Case-insensitivity and whitespace
        assertEquals(ExtractionType.REGEX, RequestBlockCard.parseExtractionType("  regex  "));
        assertEquals(ExtractionType.JSON_PATH, RequestBlockCard.parseExtractionType("json_path"));

        // 5. Fallback for null or unknown string
        assertEquals(ExtractionType.JSON_PATH, RequestBlockCard.parseExtractionType(null));
        assertEquals(ExtractionType.JSON_PATH, RequestBlockCard.parseExtractionType("UNKNOWN_TYPE_ABC"));
    }

    @Test
    void testRequestBlockCard_TableDataParsingAndEnums() {
        RequestStep step = new RequestStep("Step 1: Create Order", "POST", "https://api.local/orders", "ROLE_A");
        step.setHeadersTemplate("X-Custom-Token: {{TOKEN}}\nContent-Type: application/json");
        step.setBodyTemplate("{\"amount\": 100}");
        step.setTeardown(true);

        UserRole roleA = new UserRole("Admin", "Authorization: Bearer admin", "");
        roleA.setId("ROLE_A");

        RequestBlockCard card = new RequestBlockCard(step, List.of(roleA), c -> {}, c -> {}, c -> {});

        // Add 3 different extraction rule rows to the table model
        // Row 1: Direct enums
        // Row 2: String display names
        // Row 3: String enum names
        JTable table = findComponent(card, JTable.class);
        assertNotNull(table, "Extraction JTable must be present in RequestBlockCard");
        DefaultTableModel model = (DefaultTableModel) table.getModel();
        model.setRowCount(0);

        model.addRow(new Object[]{"VAR_ID", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.order_id", "0"});
        model.addRow(new Object[]{"VAR_COOKIE", "Response Header", "Regex with Group (e.g. id=([0-9a-zA-Z_-]+))", "Set-Cookie: session=([a-z0-9]+)", ""});
        model.addRow(new Object[]{"VAR_QUERY", "URL_QUERY_PARAM", "REGEX", "item_id=([0-9]+)", "123"});

        RequestStep updatedStep = card.getStepData();
        assertEquals("Step 1: Create Order", updatedStep.getName());
        assertEquals("POST", updatedStep.getMethod());
        assertEquals("https://api.local/orders", updatedStep.getUrlTemplate());
        assertEquals("ROLE_A", updatedStep.getTargetRoleId());
        assertTrue(updatedStep.isTeardown());

        List<ExtractionRule> rules = updatedStep.getExtractions();
        assertEquals(3, rules.size());

        // Assert Row 1
        assertEquals("VAR_ID", rules.get(0).getVariableName());
        assertEquals(ExtractionSource.RESPONSE_BODY, rules.get(0).getSource());
        assertEquals(ExtractionType.JSON_PATH, rules.get(0).getType());
        assertEquals("$.order_id", rules.get(0).getExpression());
        assertEquals("0", rules.get(0).getDefaultValue());

        // Assert Row 2 (parsed from display names)
        assertEquals("VAR_COOKIE", rules.get(1).getVariableName());
        assertEquals(ExtractionSource.RESPONSE_HEADER, rules.get(1).getSource());
        assertEquals(ExtractionType.REGEX, rules.get(1).getType());
        assertEquals("Set-Cookie: session=([a-z0-9]+)", rules.get(1).getExpression());

        // Assert Row 3 (parsed from string enum names)
        assertEquals("VAR_QUERY", rules.get(2).getVariableName());
        assertEquals(ExtractionSource.URL_QUERY_PARAM, rules.get(2).getSource());
        assertEquals(ExtractionType.REGEX, rules.get(2).getType());
        assertEquals("item_id=([0-9]+)", rules.get(2).getExpression());
        assertEquals("123", rules.get(2).getDefaultValue());
    }

    @Test
    void testRequestBlockCard_StopsCellEditingOnSave() {
        RequestStep step = new RequestStep("Step 1", "GET", "https://api.local/data", "UNAUTHENTICATED");
        step.addExtraction(new ExtractionRule("TOKEN", ExtractionSource.RESPONSE_BODY, ExtractionType.JSON_PATH, "$.old_token"));

        RequestBlockCard card = new RequestBlockCard(step, Collections.emptyList(), c -> {}, c -> {}, c -> {});
        JTable table = findComponent(card, JTable.class);
        assertNotNull(table);

        // Simulate active cell editing on column 3 (Expression)
        table.editCellAt(0, 3);
        Component editorComp = table.getEditorComponent();
        if (editorComp instanceof JTextField) {
            ((JTextField) editorComp).setText("$.new_saved_token");
        }

        assertTrue(table.isEditing(), "Table should be actively editing before getStepData()");

        // Calling getStepData() must stop cell editing and commit the edit
        RequestStep saved = card.getStepData();
        assertFalse(table.isEditing(), "Table must stop cell editing during getStepData()");
        assertEquals("$.new_saved_token", saved.getExtractions().get(0).getExpression());
    }

    @Test
    void testBranchBlockCard_DynamicTooltipHintsAndData() {
        ConditionBranch branch = new ConditionBranch("Check Status",
                ConditionBranch.ConditionType.STATUS_CODE_EQUALS,
                "200",
                ConditionBranch.BranchAction.CONTINUE,
                ConditionBranch.BranchAction.STOP_PIPELINE);
        branch.setFailureNote("Status did not match");

        BranchBlockCard card = new BranchBlockCard(branch, c -> {}, c -> {}, c -> {});

        JTextField expField = findTextFieldWithValue(card, "200");
        assertNotNull(expField, "Expected value field must exist in card");
        assertTrue(expField.getToolTipText().toLowerCase().contains("status code"));

        // Switch condition type to VARIABLE_NOT_EMPTY
        @SuppressWarnings("unchecked")
        JComboBox<ConditionBranch.ConditionType> typeCombo = findComponent(card, JComboBox.class);
        assertNotNull(typeCombo);
        typeCombo.setSelectedItem(ConditionBranch.ConditionType.VARIABLE_NOT_EMPTY);

        assertTrue(expField.getToolTipText().toLowerCase().contains("variable name"),
                "Tooltip must dynamically update to variable name hint");

        expField.setText("{{AUTH_TOKEN}}");
        ConditionBranch updated = card.getBranchData();
        assertEquals(ConditionBranch.ConditionType.VARIABLE_NOT_EMPTY, updated.getConditionType());
        assertEquals("{{AUTH_TOKEN}}", updated.getExpectedValue());
        assertEquals("Status did not match", updated.getFailureNote());
    }

    @Test
    void testCardDynamicMaximumSize() {
        RequestStep step = new RequestStep("Step 1", "GET", "https://api.local/data", "UNAUTHENTICATED");
        RequestBlockCard card = new RequestBlockCard(step, Collections.emptyList(), c -> {}, c -> {}, c -> {});

        Dimension pref = card.getPreferredSize();
        Dimension max = card.getMaximumSize();

        assertEquals(Integer.MAX_VALUE, max.width);
        assertEquals(pref.height, max.height);

        ConditionBranch branch = new ConditionBranch();
        BranchBlockCard branchCard = new BranchBlockCard(branch, c -> {}, c -> {}, c -> {});

        Dimension bPref = branchCard.getPreferredSize();
        Dimension bMax = branchCard.getMaximumSize();

        assertEquals(Integer.MAX_VALUE, bMax.width);
        assertEquals(bPref.height, bMax.height);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Component> T findComponent(Container container, Class<T> clazz) {
        for (Component c : container.getComponents()) {
            if (clazz.isInstance(c)) {
                return (T) c;
            }
            if (c instanceof Container) {
                T found = findComponent((Container) c, clazz);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static JTextField findTextFieldWithValue(Container container, String text) {
        for (Component c : container.getComponents()) {
            if (c instanceof JTextField && text.equals(((JTextField) c).getText())) {
                return (JTextField) c;
            }
            if (c instanceof Container) {
                JTextField found = findTextFieldWithValue((Container) c, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
