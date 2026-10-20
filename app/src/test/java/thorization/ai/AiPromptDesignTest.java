package thorization.ai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AiPromptDesignTest {

    @Test
    void testSystemPromptVerdictsAndSchemaContract() {
        String systemPrompt = AiSemanticEngine.buildSystemPrompt();

        assertNotNull(systemPrompt);
        assertTrue(systemPrompt.contains("VALIDATED_ACCESS"));
        assertTrue(systemPrompt.contains("ACCESS_DENIED"));
        assertTrue(systemPrompt.contains("INCONCLUSIVE"));
        assertTrue(systemPrompt.contains("confidence"));
        assertTrue(systemPrompt.contains("reasoning"));
        assertTrue(systemPrompt.contains("differential_analysis"));
        assertTrue(systemPrompt.contains("remediation_advice"));
        assertTrue(systemPrompt.contains("Do NOT output markdown code blocks"));
    }

    @Test
    void testParseStrictCleanJson() {
        String json = """
                {
                  "verdict": "VALIDATED_ACCESS",
                  "confidence": 0.95,
                  "reasoning": "Attacker successfully retrieved victim orders.",
                  "differential_analysis": "Response 200 matches authorized baseline.",
                  "remediation_advice": "Enforce tenant checks in orders controller."
                }
                """;

        AiEvaluationResult result = AiSemanticEngine.parseResponse(json, "gpt-4o-mini", 250L);

        assertNotNull(result);
        assertEquals(AiVerdict.VALIDATED_ACCESS, result.getVerdict());
        assertTrue(result.getReasoning().contains("Attacker successfully retrieved victim orders"));
        assertTrue(result.getReasoning().contains("Analysis: Response 200 matches authorized baseline"));
        assertEquals("gpt-4o-mini", result.getModel());
        assertEquals(250L, result.getExecutionTimeMs());
        assertFalse(result.isCached());
    }

    @Test
    void testParseMarkdownWrappedJson() {
        String markdown = """
                ```json
                {
                  "verdict": "ACCESS_DENIED",
                  "confidence": 0.99,
                  "reasoning": "Endpoint blocked the request with 403 Forbidden.",
                  "differential_analysis": "Role replay returned 403 vs baseline 200.",
                  "remediation_advice": "No action needed, authorization is enforced."
                }
                ```
                """;

        AiEvaluationResult result = AiSemanticEngine.parseResponse(markdown, "deepseek-chat", 400L);

        assertNotNull(result);
        assertEquals(AiVerdict.ACCESS_DENIED, result.getVerdict());
        assertTrue(result.getReasoning().contains("Endpoint blocked the request"));
    }

    @Test
    void testParseDeepSeekThinkTagStripping() {
        String reasoningModelOutput = """
                <think>
                Comparing baseline and replay responses...
                The baseline returns 200 OK with order details.
                The replay with attacker cookie also returns 200 OK with identical data.
                Similarity is 98.5%. This is a clear BOLA/IDOR vulnerability.
                </think>
                ```json
                {
                  "verdict": "VALIDATED_ACCESS",
                  "confidence": 0.98,
                  "reasoning": "Critical BOLA vulnerability confirmed.",
                  "differential_analysis": "Attacker observed identical order data.",
                  "remediation_advice": "Validate owner ID."
                }
                ```
                """;

        AiEvaluationResult result = AiSemanticEngine.parseResponse(reasoningModelOutput, "deepseek-r1", 850L);

        assertNotNull(result);
        assertEquals(AiVerdict.VALIDATED_ACCESS, result.getVerdict());
        assertTrue(result.getReasoning().contains("Critical BOLA vulnerability confirmed"));
        assertFalse(result.getReasoning().contains("<think>"));
    }

    @Test
    void testParseMalformedJsonFallback() {
        String unstructuredText = "Based on my access control analysis, this differential indicates ACCESS_DENIED because the replayed request received a 302 login redirect.";

        AiEvaluationResult result = AiSemanticEngine.parseResponse(unstructuredText, "llama3", 300L);

        assertNotNull(result);
        assertEquals(AiVerdict.ACCESS_DENIED, result.getVerdict());
        assertTrue(result.getReasoning().contains("Parsed from text"));
    }

    @Test
    void testParseEmptyResponse() {
        AiEvaluationResult resultNull = AiSemanticEngine.parseResponse(null, "model", 100L);
        assertEquals(AiVerdict.ERROR, resultNull.getVerdict());

        AiEvaluationResult resultEmpty = AiSemanticEngine.parseResponse("   ", "model", 100L);
        assertEquals(AiVerdict.ERROR, resultEmpty.getVerdict());
    }
}
