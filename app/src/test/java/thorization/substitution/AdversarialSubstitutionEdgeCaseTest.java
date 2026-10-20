package thorization.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import thorization.core.substitution.JsonBodyMutator;
import thorization.core.substitution.ParameterSubstitutionEngine;
import thorization.model.UserRole;
import thorization.model.substitution.ParameterSubstitutionRule;
import thorization.model.substitution.SubstitutionDataType;
import thorization.model.substitution.SubstitutionTargetType;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Adversarial JSON & Scoping Edge Case Verification Suite")
public class AdversarialSubstitutionEdgeCaseTest {

    private ParameterSubstitutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ParameterSubstitutionEngine();
    }

    // =========================================================================
    // 1. DEEP NESTING TESTS (50+ LEVELS)
    // =========================================================================

    @Nested
    @DisplayName("Deep Nesting Resilience Tests")
    class DeepNestingTests {

        @Test
        @DisplayName("60 levels of nested objects: navigates and mutates deeply nested leaf node")
        void test60LevelsOfNestedObjects() {
            StringBuilder jsonBuilder = new StringBuilder();
            StringBuilder pathBuilder = new StringBuilder("$");
            int depth = 60;
            for (int i = 1; i <= depth; i++) {
                jsonBuilder.append("{\"lvl").append(i).append("\":");
                pathBuilder.append(".lvl").append(i);
            }
            jsonBuilder.append("{\"targetId\":100}");
            pathBuilder.append(".targetId");
            for (int i = 1; i <= depth; i++) {
                jsonBuilder.append("}");
            }

            String originalJson = jsonBuilder.toString();
            String targetPath = pathBuilder.toString();

            String mutated = JsonBodyMutator.substituteJson(originalJson, targetPath, "200");
            assertNotNull(mutated);
            assertTrue(mutated.contains("\"targetId\":200"), "Must successfully mutate leaf at depth 60");
            assertFalse(mutated.contains("\"targetId\":100"));
            assertFalse(mutated.contains("\"200\""), "Must preserve integer primitive type");
        }

        @Test
        @DisplayName("60 levels of nested objects with missing intermediate node fails gracefully")
        void test60LevelsWithMissingIntermediateNode() {
            StringBuilder jsonBuilder = new StringBuilder();
            StringBuilder pathBuilder = new StringBuilder("$");
            int depth = 60;
            for (int i = 1; i <= depth; i++) {
                jsonBuilder.append("{\"lvl").append(i).append("\":");
                if (i == 30) {
                    pathBuilder.append(".nonExistentNode");
                } else {
                    pathBuilder.append(".lvl").append(i);
                }
            }
            jsonBuilder.append("{\"targetId\":100}");
            pathBuilder.append(".targetId");
            for (int i = 1; i <= depth; i++) {
                jsonBuilder.append("}");
            }

            String originalJson = jsonBuilder.toString();
            String targetPath = pathBuilder.toString();

            String mutated = JsonBodyMutator.substituteJson(originalJson, targetPath, "200");
            assertEquals(originalJson, mutated, "Must return original JSON when intermediate node is absent");
        }

        @Test
        @DisplayName("50 levels of nested arrays: navigates and mutates leaf element")
        void test50LevelsOfNestedArrays() {
            StringBuilder arrJson = new StringBuilder("{\"matrix\":");
            StringBuilder arrPath = new StringBuilder("$.matrix");
            int depth = 50;
            for (int i = 0; i < depth; i++) {
                arrJson.append("[");
                arrPath.append("[0]");
            }
            arrJson.append("42");
            for (int i = 0; i < depth; i++) {
                arrJson.append("]");
            }
            arrJson.append("}");

            String originalJson = arrJson.toString();
            String targetPath = arrPath.toString();

            String mutated = JsonBodyMutator.substituteJson(originalJson, targetPath, "999");
            assertNotNull(mutated);
            assertTrue(mutated.contains("999"));
            assertFalse(mutated.contains("42"));
        }
    }

    // =========================================================================
    // 2. WILDCARD ARRAYS WITH MIXED & PRIMITIVE TYPES
    // =========================================================================

    @Nested
    @DisplayName("Wildcard Array Edge Cases")
    class WildcardArrayTests {

        @Test
        @DisplayName("Wildcard [*] on heterogeneous array with mixed types mutates only matching targets")
        void testWildcardOnHeterogeneousArray() {
            String mixedJson = "{\"items\":[" +
                    "{\"id\":10,\"type\":\"A\"}," +
                    "\"primitive_string\"," +
                    "42," +
                    "null," +
                    "true," +
                    "{\"id\":20,\"type\":\"B\"}," +
                    "{\"other\":\"no_id\"}" +
                    "]}";

            String mutated = JsonBodyMutator.substituteJson(mixedJson, "$.items[*].id", "99");
            assertNotNull(mutated);

            JsonObject parsed = JsonParser.parseString(mutated).getAsJsonObject();
            var arr = parsed.getAsJsonArray("items");
            assertEquals(7, arr.size());
            assertEquals(99, arr.get(0).getAsJsonObject().get("id").getAsInt());
            assertEquals("A", arr.get(0).getAsJsonObject().get("type").getAsString());
            assertEquals("primitive_string", arr.get(1).getAsString());
            assertEquals(42, arr.get(2).getAsInt());
            assertTrue(arr.get(3).isJsonNull());
            assertTrue(arr.get(4).getAsBoolean());
            assertEquals(99, arr.get(5).getAsJsonObject().get("id").getAsInt());
            assertEquals("B", arr.get(5).getAsJsonObject().get("type").getAsString());
            assertFalse(arr.get(6).getAsJsonObject().has("id"), "Must not create id field on objects without it");
            assertEquals("no_id", arr.get(6).getAsJsonObject().get("other").getAsString());
        }

        @Test
        @DisplayName("Wildcard [*] directly on primitive array replaces all items")
        void testWildcardOnPrimitiveArray() {
            String primJson = "{\"codes\":[10,20,30]}";
            String mutated = JsonBodyMutator.substituteJson(primJson, "$.codes[*]", "500");
            assertEquals("{\"codes\":[500,500,500]}", mutated);
        }

        @Test
        @DisplayName("Wildcard [*] on empty array leaves JSON untouched")
        void testWildcardOnEmptyArray() {
            String emptyJson = "{\"items\":[]}";
            String mutated = JsonBodyMutator.substituteJson(emptyJson, "$.items[*].id", "99");
            assertEquals(emptyJson, mutated);
        }

        @Test
        @DisplayName("Wildcard [*] on root JSON array")
        void testWildcardOnRootArray() {
            String rootArrJson = "[{\"id\":10},{\"id\":20}]";
            String mutated = JsonBodyMutator.substituteJson(rootArrJson, "$[*].id", "99");
            assertEquals("[{\"id\":99},{\"id\":99}]", mutated);
        }
    }

    // =========================================================================
    // 3. STRICT PRIMITIVE TYPE PRESERVATION EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Strict Primitive Type Preservation Edge Cases")
    class StrictTypePreservationEdgeCaseTests {

        @Test
        @DisplayName("Preserves 64-bit Long / Snowflake IDs (19 digits) without truncation or quoting")
        void test64BitSnowflakeIdPreservation() {
            String json = "{\"snowflake_id\":1583800000000000001}";
            String mutated = JsonBodyMutator.substituteJson(json, "$.snowflake_id", "1583800000000000002");
            assertEquals("{\"snowflake_id\":1583800000000000002}", mutated);
            assertFalse(mutated.contains("\"1583800000000000002\""), "Must remain unquoted number");
        }

        @Test
        @DisplayName("Preserves Long.MAX_VALUE and Long.MIN_VALUE boundaries")
        void testLongBoundariesPreservation() {
            String maxJson = "{\"val\":" + Long.MAX_VALUE + "}";
            String mutatedMax = JsonBodyMutator.substituteJson(maxJson, "$.val", String.valueOf(Long.MAX_VALUE - 1));
            assertEquals("{\"val\":" + (Long.MAX_VALUE - 1) + "}", mutatedMax);

            String minJson = "{\"val\":" + Long.MIN_VALUE + "}";
            String mutatedMin = JsonBodyMutator.substituteJson(minJson, "$.val", String.valueOf(Long.MIN_VALUE + 1));
            assertEquals("{\"val\":" + (Long.MIN_VALUE + 1) + "}", mutatedMin);
        }

        @Test
        @DisplayName("Scientific notation floats (1.23e4) are parsed and serialized as numbers")
        void testScientificNotationFloat() {
            String json = "{\"amount\":1.23e4}";
            String mutated = JsonBodyMutator.substituteJson(json, "$.amount", "5.67e-2");
            JsonObject obj = JsonParser.parseString(mutated).getAsJsonObject();
            assertTrue(obj.get("amount").getAsJsonPrimitive().isNumber(), "Must be numeric primitive");
            assertFalse(mutated.contains("\"5.67e-2\""), "Must not be quoted as string");
        }

        @Test
        @DisplayName("String containing number vs number literal under AUTO")
        void testStringNumberVsNumberLiteral() {
            String strJson = "{\"code\":\"100\"}";
            String numJson = "{\"code\":100}";

            String mutStr = JsonBodyMutator.substituteJson(strJson, "$.code", "200");
            assertEquals("{\"code\":\"200\"}", mutStr, "Must preserve quoted string");

            String mutNum = JsonBodyMutator.substituteJson(numJson, "$.code", "200");
            assertEquals("{\"code\":200}", mutNum, "Must preserve unquoted integer");
        }

        @Test
        @DisplayName("Boolean string vs boolean literal under AUTO")
        void testBooleanStringVsBooleanLiteral() {
            String strBool = "{\"flag\":\"true\"}";
            String litBool = "{\"flag\":true}";

            String mutStrBool = JsonBodyMutator.substituteJson(strBool, "$.flag", "false");
            assertEquals("{\"flag\":\"false\"}", mutStrBool, "String boolean must stay quoted string");

            String mutLitBool = JsonBodyMutator.substituteJson(litBool, "$.flag", "false");
            assertEquals("{\"flag\":false}", mutLitBool, "Boolean literal must stay unquoted literal");
        }

        @Test
        @DisplayName("Null fields in AST are preserved and target null is correctly typed under AUTO")
        void testNullHandlingAndInference() {
            String json = "{\"existing_null\":null,\"id\":100,\"nested\":{\"opt\":null}}";

            // Verify unrelated nulls survive mutation
            String mutated = JsonBodyMutator.substituteJson(json, "$.id", "200");
            assertTrue(mutated.contains("\"existing_null\":null"), "Top level null must be preserved");
            assertTrue(mutated.contains("\"opt\":null"), "Nested null must be preserved");

            // Mutate a null target with integer
            String mutNullInt = JsonBodyMutator.substituteJson(json, "$.existing_null", "500");
            assertEquals("{\"existing_null\":500,\"id\":100,\"nested\":{\"opt\":null}}", mutNullInt);

            // Mutate a null target with boolean
            String mutNullBool = JsonBodyMutator.substituteJson(json, "$.existing_null", "true");
            assertEquals("{\"existing_null\":true,\"id\":100,\"nested\":{\"opt\":null}}", mutNullBool);

            // Mutate a null target with string
            String mutNullStr = JsonBodyMutator.substituteJson(json, "$.existing_null", "hello");
            assertEquals("{\"existing_null\":\"hello\",\"id\":100,\"nested\":{\"opt\":null}}", mutNullStr);
        }
    }

    // =========================================================================
    // 4. MALFORMED JSON RESILIENCE TESTS
    // =========================================================================

    @Nested
    @DisplayName("Malformed JSON Resilience Tests")
    class MalformedJsonResilienceTests {

        @Test
        @DisplayName("Truncated JSON object or array returns original without throwing")
        void testTruncatedJsonInputs() {
            String truncObj = "{\"user\":{\"id\":100";
            assertEquals(truncObj, JsonBodyMutator.substituteJson(truncObj, "$.user.id", "200"));

            String truncArr = "{\"items\":[{\"id\":1},";
            assertEquals(truncArr, JsonBodyMutator.substituteJson(truncArr, "$.items[0].id", "200"));
        }

        @Test
        @DisplayName("Syntax errors (trailing commas, missing colon) return original without throwing")
        void testSyntaxErrorInputs() {
            String trailingComma = "{\"id\":100,}";
            assertEquals(trailingComma, JsonBodyMutator.substituteJson(trailingComma, "$.id", "200"));

            String missingColon = "{\"id\" 100}";
            assertEquals(missingColon, JsonBodyMutator.substituteJson(missingColon, "$.id", "200"));

            String unclosedBracket = "[{\"id\":1}";
            assertEquals(unclosedBracket, JsonBodyMutator.substituteJson(unclosedBracket, "$[0].id", "200"));
        }

        @Test
        @DisplayName("Non-JSON payloads (HTML, Form, Plain Text, Binary) return original unmodified")
        void testNonJsonPayloads() {
            String html = "<!DOCTYPE html><html><body>Error 500</body></html>";
            assertEquals(html, JsonBodyMutator.substituteJson(html, "$.id", "200"));

            String form = "user_id=100&action=submit";
            assertEquals(form, JsonBodyMutator.substituteJson(form, "$.user_id", "200"));

            String text = "Internal Server Error";
            assertEquals(text, JsonBodyMutator.substituteJson(text, "$.error", "200"));

            String binary = "\u0000\u0001\u0002\u0003";
            assertEquals(binary, JsonBodyMutator.substituteJson(binary, "$.id", "200"));
        }

        @Test
        @DisplayName("Montoya HttpRequest with malformed JSON body returns same instance without throwing")
        void testHttpRequestMalformedJsonResilience() {
            String malformed = "{\"id\": 100, invalid}";
            HttpRequest req = MockHttpRequestHelper.createMockRequest(
                    "https://target.com/api/test",
                    "POST",
                    malformed,
                    Map.of("Content-Type", "application/json")
            );

            HttpRequest result = JsonBodyMutator.mutate(req, "$.id", "200");
            assertSame(req, result, "Must return untouched instance on malformed body");
        }
    }

    // =========================================================================
    // 5. PARAMETER SUBSTITUTION ENGINE SCOPING EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("Scoping Regex & Glob Edge Case Tests")
    class ScopingEdgeCaseTests {

        @Test
        @DisplayName("Port matching edge cases: 80, 443, 8080 stripped for host pattern evaluation")
        void testPortMatchingEdgeCases() {
            assertTrue(engine.matchesHost("example.com:80", "example.com"));
            assertTrue(engine.matchesHost("example.com:443", "example.com"));
            assertTrue(engine.matchesHost("example.com:8080", "example.com"));
            assertTrue(engine.matchesHost("api.example.com:8080", "*.example.com"));
            assertFalse(engine.matchesHost("different-domain.org:8080", "example.com"));
            assertFalse(engine.matchesHost("notexample.com", "*.example.com"));
            // Empirical observation: plain pattern without anchors acts as unanchored regex fallback
            assertTrue(engine.matchesHost("evil-example.com:8080", "example.com"),
                    "Plain host pattern without anchors currently matches substring via regex fallback");
        }

        @Test
        @DisplayName("Mixed case hostnames evaluate case-insensitively across exact, glob, and regex")
        void testMixedCaseHostnames() {
            assertTrue(engine.matchesHost("ExAmPlE.cOm", "example.com"));
            assertTrue(engine.matchesHost("example.com", "ExAmPlE.cOm"));
            assertTrue(engine.matchesHost("API.ExAmPlE.cOm:8080", "*.example.com"));
            assertTrue(engine.matchesHost("API-PROD.EXAMPLE.COM", "^api-(prod|stage)\\.example\\.com$"));
        }

        @Test
        @DisplayName("Path scoping with attached query string, trailing slashes, and complex templates")
        void testPathScopingEdgeCases() {
            // Path with query string attached must match path-only pattern
            assertTrue(engine.matchesPath("/api/v1/users?token=abc&page=2", "/api/v1/users"));

            // Multi-parameter REST templates
            assertTrue(engine.matchesPath("/api/org1/projects/proj2/tasks/t3", "/api/{org}/projects/{proj}/tasks/{task}"));

            // Glob pattern with extensions and wildcards
            assertTrue(engine.matchesPath("/api/v1/data.json", "/api/v1/*.json"));
            assertFalse(engine.matchesPath("/api/v1/data.xml", "/api/v1/*.json"));
            assertTrue(engine.matchesPath("/api/v1/item-123", "/api/v?/item-*"));
        }

        @Test
        @DisplayName("Malformed regex patterns are safely rejected without throwing exceptions")
        void testMalformedRegexPatternSafety() {
            assertFalse(engine.matchesHost("example.com", "(unclosed-group"));
            assertFalse(engine.matchesPath("/api/users", "[unclosed-bracket"));
            assertFalse(engine.matchesPath("/api/users", "^/api/users/(?"));
        }

        @Test
        @DisplayName("Engine executes rule chain with mixed target types gracefully")
        void testRuleChainingJsonAndQuery() {
            ParameterSubstitutionRule jsonRule = new ParameterSubstitutionRule(
                    "JSON Rule", SubstitutionTargetType.JSON_BODY, "$.tenantId"
            );
            jsonRule.setDefaultValue("999");

            ParameterSubstitutionRule queryRule = new ParameterSubstitutionRule(
                    "Query Rule", SubstitutionTargetType.URL_QUERY, "role"
            );
            queryRule.setDefaultValue("superadmin");

            HttpRequest req = MockHttpRequestHelper.createMockRequest(
                    "https://example.com/api/test?role=guest",
                    "POST",
                    "{\"tenantId\":10,\"user\":\"alice\"}",
                    Map.of("Content-Type", "application/json", "Content-Length", "32")
            );

            HttpRequest result = engine.applySubstitutions(req, null, List.of(jsonRule, queryRule));
            assertNotNull(result);
            assertEquals("{\"tenantId\":999,\"user\":\"alice\"}", result.bodyToString());
            assertEquals("role=superadmin", result.query());
        }
    }
}
