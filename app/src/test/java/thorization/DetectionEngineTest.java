package thorization;

import org.junit.jupiter.api.Test;
import thorization.core.DetectionEngine;
import thorization.model.Verdict;
import thorization.util.DiffUtils;
import thorization.util.HeaderUtils;
import thorization.model.UserRole;

import static org.junit.jupiter.api.Assertions.*;

class DetectionEngineTest {

    @Test
    void testSimilarityCalculation() {
        String s1 = "{\"id\":101,\"name\":\"Alice\",\"status\":\"active\"}";
        String s2 = "{\"id\":101,\"name\":\"Alice\",\"status\":\"active\"}";
        assertEquals(1.0, DiffUtils.computeSimilarity(s1, s2), 0.001);

        String s3 = "{\"id\":999,\"error\":\"Access denied\"}";
        double sim = DiffUtils.computeSimilarity(s1, s3);
        assertTrue(sim < 0.6, "Similarity should be low for different responses");
    }

    @Test
    void testLengthDiffPercent() {
        assertEquals(0.0, DiffUtils.calculateLengthDiffPercent(100, 100), 0.001);
        assertEquals(10.0, DiffUtils.calculateLengthDiffPercent(100, 110), 0.001);
        assertEquals(50.0, DiffUtils.calculateLengthDiffPercent(100, 50), 0.001);
    }

    @Test
    void testUserRoleHeaderParsing() {
        UserRole role = new UserRole("Admin", "Authorization: Bearer my_jwt_token\nX-Role: admin\nCustom-Header: value123", "sess=abc");
        assertEquals(3, role.getParsedHeaders().size());
        assertEquals("Bearer my_jwt_token", role.getParsedHeaders().get("Authorization"));
        assertEquals("admin", role.getParsedHeaders().get("X-Role"));
        assertEquals("value123", role.getParsedHeaders().get("Custom-Header"));
    }
}

