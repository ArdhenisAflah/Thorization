package thorization.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import thorization.core.substitution.JsonBodyMutator;
import thorization.model.substitution.SubstitutionDataType;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("JsonBodyMutator & Strict Type Preservation Test Harness")
class JsonBodyMutatorTest {

    @Nested
    @DisplayName("Object Path Navigation Tests")
    class ObjectNavigationTests {

        @Test
        @DisplayName("Mutates top-level object property with leading $.")
        void testTopLevelPropertyWithDollarDot() {
            String json = "{\"id\": 100, \"name\": \"alice\"}";
            String result = JsonBodyMutator.substituteJson(json, "$.id", "200");
            assertEquals("{\"id\":200,\"name\":\"alice\"}", result);
        }

        @Test
        @DisplayName("Mutates top-level object property without leading $")
        void testTopLevelPropertyWithoutDollar() {
            String json = "{\"id\": 100, \"name\": \"alice\"}";
            String result = JsonBodyMutator.substituteJson(json, "id", "200");
            assertEquals("{\"id\":200,\"name\":\"alice\"}", result);
        }

        @Test
        @DisplayName("Mutates deeply nested object property")
        void testDeeplyNestedObjectProperty() {
            String json = "{\"user\": {\"profile\": {\"tenantId\": 42, \"role\": \"analyst\"}}}";
            String result = JsonBodyMutator.substituteJson(json, "$.user.profile.tenantId", "99");
            assertEquals("{\"user\":{\"profile\":{\"tenantId\":99,\"role\":\"analyst\"}}}", result);
        }

        @Test
        @DisplayName("Returns original JSON when path key does not exist")
        void testNonExistentKeyReturnsOriginal() {
            String json = "{\"user\": {\"id\": 100}}";
            String result = JsonBodyMutator.substituteJson(json, "$.user.missingField", "200");
            assertEquals(json, result);
        }

        @Test
        @DisplayName("Returns original JSON when intermediate object is missing")
        void testMissingIntermediateObjectReturnsOriginal() {
            String json = "{\"user\": {\"id\": 100}}";
            String result = JsonBodyMutator.substituteJson(json, "$.account.tenantId", "200");
            assertEquals(json, result);
        }
    }

    @Nested
    @DisplayName("Array Indexing Navigation Tests")
    class ArrayNavigationTests {

        @Test
        @DisplayName("Mutates object property inside nested array: items[0].id")
        void testNestedArrayObjectIndex() {
            String json = "{\"items\": [{\"id\": 10, \"name\": \"first\"}, {\"id\": 20, \"name\": \"second\"}]}";
            String result = JsonBodyMutator.substituteJson(json, "$.items[0].id", "99");
            assertEquals("{\"items\":[{\"id\":99,\"name\":\"first\"},{\"id\":20,\"name\":\"second\"}]}", result);
        }

        @Test
        @DisplayName("Mutates root array object property: $[0].id")
        void testRootArrayObjectIndex() {
            String json = "[{\"id\": 100, \"role\": \"admin\"}, {\"id\": 200, \"role\": \"user\"}]";
            String result = JsonBodyMutator.substituteJson(json, "$[0].id", "300");
            assertEquals("[{\"id\":300,\"role\":\"admin\"},{\"id\":200,\"role\":\"user\"}]", result);
        }

        @Test
        @DisplayName("Mutates primitive element in array: items[1]")
        void testPrimitiveInArray() {
            String json = "{\"codes\": [101, 102, 103]}";
            String result = JsonBodyMutator.substituteJson(json, "$.codes[1]", "999");
            assertEquals("{\"codes\":[101,999,103]}", result);
        }

        @Test
        @DisplayName("Mutates primitive element in root array: $[2]")
        void testPrimitiveInRootArray() {
            String json = "[10, 20, 30]";
            String result = JsonBodyMutator.substituteJson(json, "$[2]", "99");
            assertEquals("[10,20,99]", result);
        }

        @Test
        @DisplayName("Wildcard array index [*] updates all array elements")
        void testWildcardArrayIndex() {
            String json = "{\"items\": [{\"tenantId\": 1}, {\"tenantId\": 1}]}";
            String result = JsonBodyMutator.substituteJson(json, "$.items[*].tenantId", "5");
            assertEquals("{\"items\":[{\"tenantId\":5},{\"tenantId\":5}]}", result);
        }

        @Test
        @DisplayName("Array index out of bounds returns original without error")
        void testIndexOutOfBoundsReturnsOriginal() {
            String json = "{\"items\": [{\"id\": 1}]}";
            String result = JsonBodyMutator.substituteJson(json, "$.items[10].id", "99");
            assertEquals(json, result);
        }

        @Test
        @DisplayName("Negative array index returns original without error")
        void testNegativeIndexReturnsOriginal() {
            String json = "{\"items\": [{\"id\": 1}]}";
            String result = JsonBodyMutator.substituteJson(json, "$.items[-1].id", "99");
            assertEquals(json, result);
        }
    }

    @Nested
    @DisplayName("Strict Type Preservation Tests")
    class StrictTypePreservationTests {

        @Test
        @DisplayName("AUTO preserves numeric integer without quotes")
        void testAutoPreservesNumericInteger() {
            String json = "{\"user_id\": 100}";
            String result = JsonBodyMutator.substituteJson(json, "$.user_id", "200");
            assertEquals("{\"user_id\":200}", result);
            assertFalse(result.contains("\"200\""), "Integer must not be quoted");
        }

        @Test
        @DisplayName("AUTO preserves quoted string with quotes")
        void testAutoPreservesQuotedString() {
            String json = "{\"user_id\": \"100\"}";
            String result = JsonBodyMutator.substituteJson(json, "$.user_id", "200");
            assertEquals("{\"user_id\":\"200\"}", result);
            assertTrue(result.contains("\"200\""), "String must be quoted");
        }

        @Test
        @DisplayName("AUTO preserves floating point decimal")
        void testAutoPreservesFloat() {
            String json = "{\"price\": 19.99}";
            String result = JsonBodyMutator.substituteJson(json, "$.price", "49.50");
            assertEquals("{\"price\":49.5}", result);
        }

        @Test
        @DisplayName("AUTO preserves boolean primitive")
        void testAutoPreservesBoolean() {
            String json = "{\"is_admin\": false}";
            String result = JsonBodyMutator.substituteJson(json, "$.is_admin", "true");
            assertEquals("{\"is_admin\":true}", result);
            assertFalse(result.contains("\"true\""), "Boolean must not be quoted");
        }

        @Test
        @DisplayName("AUTO handles 64-bit Long / Snowflake ID without truncation")
        void testAutoPreservesLargeLong() {
            String json = "{\"snowflake_id\": 1729411200000}";
            String result = JsonBodyMutator.substituteJson(json, "$.snowflake_id", "1729411200999");
            assertEquals("{\"snowflake_id\":1729411200999}", result);
        }

        @Test
        @DisplayName("Explicit STRING override forces quotes on numeric target")
        void testExplicitStringOverride() {
            String json = "{\"user_id\": 100}";
            String result = JsonBodyMutator.substituteJson(json, "$.user_id", "200", SubstitutionDataType.STRING);
            assertEquals("{\"user_id\":\"200\"}", result);
        }

        @Test
        @DisplayName("Explicit INTEGER override strips quotes from string target")
        void testExplicitIntegerOverride() {
            String json = "{\"user_id\": \"100\"}";
            String result = JsonBodyMutator.substituteJson(json, "$.user_id", "200", SubstitutionDataType.INTEGER);
            assertEquals("{\"user_id\":200}", result);
        }

        @Test
        @DisplayName("Explicit BOOLEAN override converts string to boolean primitive")
        void testExplicitBooleanOverride() {
            String json = "{\"active\": \"yes\"}";
            String result = JsonBodyMutator.substituteJson(json, "$.active", "true", SubstitutionDataType.BOOLEAN);
            assertEquals("{\"active\":true}", result);
        }

        @Test
        @DisplayName("Explicit FLOAT override formats as floating point")
        void testExplicitFloatOverride() {
            String json = "{\"rate\": 10}";
            String result = JsonBodyMutator.substituteJson(json, "$.rate", "12.75", SubstitutionDataType.FLOAT);
            assertEquals("{\"rate\":12.75}", result);
        }
    }

    @Nested
    @DisplayName("Resilient Error Handling Tests")
    class ResilientErrorHandlingTests {

        @Test
        @DisplayName("Malformed truncated JSON returns original unmodified without throwing")
        void testTruncatedJsonHandledGracefully() {
            String malformed = "{\"user\": {\"id\": 100";
            String result = JsonBodyMutator.substituteJson(malformed, "$.user.id", "200");
            assertEquals(malformed, result);
        }

        @Test
        @DisplayName("Illegal trailing comma syntax error returns original unmodified")
        void testTrailingCommaHandledGracefully() {
            String malformed = "{\"id\": 100, \"active\": true,}";
            String result = JsonBodyMutator.substituteJson(malformed, "$.id", "200");
            assertEquals(malformed, result);
        }

        @Test
        @DisplayName("Non-JSON body (e.g. form-encoded) returns original unmodified")
        void testNonJsonBodyReturnsOriginal() {
            String formBody = "userId=100&action=submit";
            String result = JsonBodyMutator.substituteJson(formBody, "$.userId", "200");
            assertEquals(formBody, result);
        }

        @Test
        @DisplayName("Null and empty inputs return original unmodified")
        void testNullAndEmptyInputs() {
            assertEquals("{\"id\": 1}", JsonBodyMutator.substituteJson("{\"id\": 1}", null, "200"));
            assertEquals("{\"id\": 1}", JsonBodyMutator.substituteJson("{\"id\": 1}", "$.id", null));
            assertEquals("   ", JsonBodyMutator.substituteJson("   ", "$.id", "200"));
            assertNull(JsonBodyMutator.substituteJson(null, "$.id", "200"));
        }

        @Test
        @DisplayName("HTML and special characters are preserved without unicode mangling")
        void testSpecialCharactersNotMangled() {
            String json = "{\"url\": \"https://api.target.com/auth?a=1&b=2\", \"id\": 10}";
            String result = JsonBodyMutator.substituteJson(json, "$.id", "20");
            assertTrue(result.contains("a=1&b=2"), "Ampersands and equals must not be escaped to unicode");
        }

        @Test
        @DisplayName("Explicit null values in unrelated fields are preserved")
        void testNullValuesPreserved() {
            String json = "{\"middleName\": null, \"id\": 100}";
            String result = JsonBodyMutator.substituteJson(json, "$.id", "200");
            assertTrue(result.contains("\"middleName\":null"));
            assertTrue(result.contains("\"id\":200"));
        }
    }

    @Nested
    @DisplayName("Montoya HttpRequest Integration Tests")
    class HttpRequestIntegrationTests {

        @Test
        @DisplayName("Mutates request body and updates Content-Length header")
        void testHttpRequestMutation() {
            String initialBody = "{\"user\": {\"id\": 100}}";
            HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                    "https://api.target.com/submit",
                    "POST",
                    initialBody,
                    Map.of("Content-Type", "application/json", "Content-Length", String.valueOf(initialBody.length()))
            );

            HttpRequest mutated = JsonBodyMutator.mutate(mockReq, "$.user.id", "20000", SubstitutionDataType.AUTO);
            assertNotNull(mutated);
            assertEquals("{\"user\":{\"id\":20000}}", mutated.bodyToString());
            assertEquals(String.valueOf("{\"user\":{\"id\":20000}}".getBytes(java.nio.charset.StandardCharsets.UTF_8).length), mutated.headerValue("Content-Length"));
        }

        @Test
        @DisplayName("Returns exact same request reference when path is not matched (zero allocation)")
        void testHttpRequestUnchangedWhenPathNotMatched() {
            String initialBody = "{\"user\": {\"id\": 100}}";
            HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                    "https://api.target.com/submit",
                    "POST",
                    initialBody,
                    null
            );

            HttpRequest mutated = JsonBodyMutator.mutate(mockReq, "$.nonexistent", "200");
            assertSame(mockReq, mutated, "Must return original instance when body is unchanged");
        }
    }
}
