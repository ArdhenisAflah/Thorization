package thorization.ai;

import org.junit.jupiter.api.Test;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.Verdict;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiSemanticEngineContextTest {

    @Test
    void testContextExtractionFullRecord() {
        AuditRecord record = new AuditRecord(1, null);
        record.setSerializedOriginalResponse("HTTP/1.1 200 OK\r\n\r\n{\"user\": 100, \"balance\": 5000}");

        AuthorizationCheckResult roleResult = new AuthorizationCheckResult(
                "role-attacker",
                "Attacker",
                Verdict.BYPASS,
                200,
                35,
                0.98,
                42L,
                "Status match and high similarity",
                null,
                null
        );
        roleResult.setSerializedResponse("HTTP/1.1 200 OK\r\n\r\n{\"user\": 100, \"balance\": 5000}");

        String userPrompt = AiSemanticEngine.buildUserPrompt(record, roleResult);

        assertNotNull(userPrompt);
        assertTrue(userPrompt.contains("## 1. ENDPOINT TARGET"));
        assertTrue(userPrompt.contains("## 2. TESTED ROLE"));
        assertTrue(userPrompt.contains("Role Name: Attacker"));
        assertTrue(userPrompt.contains("Role ID: role-attacker"));
        assertTrue(userPrompt.contains("## 3. BASELINE (ORIGINAL AUTHORIZED USER)"));
        assertTrue(userPrompt.contains("## 4. REPLAYED ROLE RESPONSE"));
        assertTrue(userPrompt.contains("## 5. DIFFERENTIAL METRICS & HEURISTICS"));
        assertTrue(userPrompt.contains("## TASK"));
    }

    @Test
    void testBodyTruncationAtThreshold() {
        StringBuilder largeBody = new StringBuilder();
        for (int i = 0; i < 500; i++) {
            largeBody.append("Line ").append(i).append(": Sensitive customer data payload goes here.\n");
        }
        String body = largeBody.toString();
        assertTrue(body.length() > 3500);

        String truncated = AiSemanticEngine.truncateBody(body);
        assertTrue(truncated.length() < body.length());
        assertTrue(truncated.contains("[... Truncated "));
        assertTrue(truncated.startsWith(body.substring(0, 100)));
        assertTrue(truncated.endsWith(body.substring(body.length() - 100)));
    }

    @Test
    void testNullAndEmptyBodySafety() {
        assertEquals("[EMPTY BODY]", AiSemanticEngine.truncateBody(null));
        assertEquals("[EMPTY BODY]", AiSemanticEngine.truncateBody(""));
        assertEquals("[EMPTY BODY]", AiSemanticEngine.truncateBody("   \n\t  "));

        String small = "{\"status\": \"ok\"}";
        assertEquals(small, AiSemanticEngine.truncateBody(small));
    }

    @Test
    void testAiConfigValidation() {
        AiConfig config = new AiConfig();
        assertTrue(config.isValid());
        assertTrue(config.validate().isEmpty());

        // Test invalid URL
        config.setEndpointUrl("");
        assertFalse(config.isValid());
        assertTrue(config.validate().stream().anyMatch(e -> e.contains("must not be empty")));

        config.setEndpointUrl("ftp://localhost:11434");
        assertFalse(config.isValid());
        assertTrue(config.validate().stream().anyMatch(e -> e.contains("protocol")));

        config.setEndpointUrl("http://localhost:11434");
        assertTrue(config.isValid());

        // Test timeout limits
        config.setTimeoutSeconds(0);
        assertFalse(config.isValid());
        config.setTimeoutSeconds(601);
        assertFalse(config.isValid());
        config.setTimeoutSeconds(30);
        assertTrue(config.isValid());

        // Test temperature limits
        config.setTemperature(-0.1);
        assertFalse(config.isValid());
        config.setTemperature(2.1);
        assertFalse(config.isValid());
        config.setTemperature(0.7);
        assertTrue(config.isValid());

        // Test model name
        config.setModelName("   ");
        assertFalse(config.isValid());
        config.setModelName("gpt-4o");
        assertTrue(config.isValid());
    }
}
