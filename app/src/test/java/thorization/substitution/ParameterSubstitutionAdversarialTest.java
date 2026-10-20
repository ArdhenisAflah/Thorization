package thorization.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import thorization.core.substitution.FormBodyMutator;
import thorization.core.substitution.ParameterSubstitutionEngine;
import thorization.core.substitution.QueryParamMutator;
import thorization.core.substitution.RestPathMutator;
import thorization.model.UserRole;
import thorization.model.substitution.ParameterSubstitutionRule;
import thorization.model.substitution.SubstitutionDataType;
import thorization.model.substitution.SubstitutionTargetType;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 Adversarial Stress & Boundary Test Suite")
public class ParameterSubstitutionAdversarialTest {

    // =========================================================================
    // 1. HIGH CONCURRENCY STRESS TESTS
    // =========================================================================

    @Nested
    @DisplayName("Concurrency & Contention Stress Tests")
    class ConcurrencyStressTests {

        @Test
        @DisplayName("Stress test: 50 concurrent threads simultaneously mutating paths, queries, and form bodies")
        void testHighConcurrencyMultiThreadStress() throws Exception {
            int threadCount = 50;
            int iterationsPerThread = 200;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch readyLatch = new CountDownLatch(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger errorCount = new AtomicInteger(0);

            ParameterSubstitutionEngine engine = new ParameterSubstitutionEngine();

            // Shared rules
            ParameterSubstitutionRule pathRule = new ParameterSubstitutionRule("PathRule", SubstitutionTargetType.REST_PATH, "id");
            pathRule.setPathPattern("/api/tenants/{tenantId}/users/{id}/details");
            pathRule.setHostPattern("*.target.com");
            pathRule.setRoleValue("admin", "admin-999");
            pathRule.setRoleValue("user", "user-222");

            ParameterSubstitutionRule queryRule = new ParameterSubstitutionRule("QueryRule", SubstitutionTargetType.URL_QUERY, "filter");
            queryRule.setHostPattern("*.target.com");
            queryRule.setDefaultValue("all");

            ParameterSubstitutionRule formRule = new ParameterSubstitutionRule("FormRule", SubstitutionTargetType.FORM_BODY, "action");
            formRule.setHostPattern("*.target.com");
            formRule.setDefaultValue("updatedAction");

            List<ParameterSubstitutionRule> rules = List.of(pathRule, queryRule, formRule);

            UserRole adminRole = new UserRole("Admin", "Authorization: Bearer admin", "");
            adminRole.setId("admin");

            UserRole userRole = new UserRole("User", "Authorization: Bearer user", "");
            userRole.setId("user");

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    readyLatch.countDown();
                    try {
                        startLatch.await();
                        UserRole targetRole = (threadId % 2 == 0) ? adminRole : userRole;
                        String expectedId = (threadId % 2 == 0) ? "admin-999" : "user-222";

                        for (int i = 0; i < iterationsPerThread; i++) {
                            // 1. Path & Query mutation
                            HttpRequest req = MockHttpRequestHelper.createMockRequest(
                                    "https://api.target.com/api/tenants/acme/users/100/details?filter=pending&sort=asc",
                                    "GET",
                                    "",
                                    Map.of("Host", "api.target.com")
                            );
                            HttpRequest result = engine.applySubstitutions(req, targetRole, rules);

                            if (!result.pathWithoutQuery().equals("/api/tenants/acme/users/" + expectedId + "/details")) {
                                errorCount.incrementAndGet();
                            }
                            if (!result.query().contains("filter=all") || !result.query().contains("sort=asc")) {
                                errorCount.incrementAndGet();
                            }

                            // 2. Form mutation
                            HttpRequest formReq = MockHttpRequestHelper.createMockRequest(
                                    "https://api.target.com/api/tenants/acme/users/100/details",
                                    "POST",
                                    "name=test&action=initialAction",
                                    Map.of("Content-Type", "application/x-www-form-urlencoded", "Host", "api.target.com")
                            );
                            HttpRequest formResult = engine.applySubstitutions(formReq, targetRole, rules);
                            if (!formResult.bodyToString().contains("action=updatedAction")) {
                                errorCount.incrementAndGet();
                            }
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            readyLatch.await(5, TimeUnit.SECONDS);
            startLatch.countDown();
            boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
            executor.shutdown();

            assertTrue(completed, "All concurrent mutation threads should finish within timeout");
            assertEquals(0, errorCount.get(), "Zero mutation errors should occur across concurrent threads");
        }
    }

    // =========================================================================
    // 2. REST PATH MUTATOR BOUNDARY TESTS
    // =========================================================================

    @Nested
    @DisplayName("RestPathMutator Boundary Tests")
    class RestPathMutatorBoundaryTests {

        @Test
        @DisplayName("Preserves complex query string with multiple params and escaped symbols")
        void testPreservesComplexQueryString() {
            HttpRequest req = MockHttpRequestHelper.createMockRequest(
                    "https://api.target.com/api/users/100/orders?filter=status%3Dactive%26type%3Dvip&limit=50&offset=0",
                    "GET",
                    "",
                    null
            );

            HttpRequest mutated = RestPathMutator.mutate(req, "/api/users/{id}/orders", "id", "200");
            assertNotNull(mutated);
            assertEquals("/api/users/200/orders", mutated.pathWithoutQuery());
            assertEquals("filter=status%3Dactive%26type%3Dvip&limit=50&offset=0", mutated.query());
        }

        @Test
        @DisplayName("URL-encoded path with UTF-8 multibyte characters: /api/v1/%E4%BD%A0%E5%A5%BD/{id}")
        void testUrlEncodedMultibytePath() {
            String path = "/api/v1/%E4%BD%A0%E5%A5%BD/user-100";
            String template = "/api/v1/%E4%BD%A0%E5%A5%BD/{id}";
            String result = RestPathMutator.substitutePath(path, template, "id", "user-200");
            assertEquals("/api/v1/%E4%BD%A0%E5%A5%BD/user-200", result);
        }

        @Test
        @DisplayName("Paths with multiple consecutive slashes")
        void testMultipleConsecutiveSlashes() {
            String path = "/api//v1///users/100";
            String template = "/api//v1///users/{id}";
            String result = RestPathMutator.substitutePath(path, template, "id", "200");
            assertEquals("/api//v1///users/200", result);
        }

        @Test
        @DisplayName("Preserves trailing slash in path when query string is present")
        void testTrailingSlashWithQueryString() {
            HttpRequest req = MockHttpRequestHelper.createMockRequest(
                    "https://api.target.com/api/users/100/?view=full",
                    "GET",
                    "",
                    null
            );

            HttpRequest mutated = RestPathMutator.mutate(req, "/api/users/{id}/", "id", "200");
            assertNotNull(mutated);
            assertEquals("/api/users/200/", mutated.pathWithoutQuery());
            assertEquals("view=full", mutated.query());
        }

        @Test
        @DisplayName("Template matching with duplicate variable names: substitutes both segments")
        void testDuplicateVariableNamesInTemplate() {
            String path = "/api/tenants/tenant1/users/tenant1";
            String template = "/api/tenants/{id}/users/{id}";
            String result = RestPathMutator.substitutePath(path, template, "id", "tenant2");
            assertEquals("/api/tenants/tenant2/users/tenant2", result);
        }
    }

    // =========================================================================
    // 3. QUERY PARAM MUTATOR BOUNDARY TESTS
    // =========================================================================

    @Nested
    @DisplayName("QueryParamMutator Boundary Tests")
    class QueryParamMutatorBoundaryTests {

        @Test
        @DisplayName("HTTP Parameter Pollution (HPP): multiple duplicate keys all replaced and order preserved")
        void testHttpParameterPollutionDuplicateKeys() {
            String query = "role=user&data=1&role=admin&other=2&role=guest";
            String result = QueryParamMutator.substituteQuery(query, "role", "attacker");
            assertEquals("role=attacker&data=1&role=attacker&other=2&role=attacker", result);
        }

        @Test
        @DisplayName("Empty query parameter values preserved and replaced accurately")
        void testEmptyQueryParamValues() {
            String query = "flag=&userId=100&empty=";
            String result = QueryParamMutator.substituteQuery(query, "userId", "200");
            assertEquals("flag=&userId=200&empty=", result);

            // Replace an empty parameter with a value
            String result2 = QueryParamMutator.substituteQuery(query, "flag", "true");
            assertEquals("flag=true&userId=100&empty=", result2);
        }

        @Test
        @DisplayName("Keys without equals sign (valueless flags) preserved")
        void testKeysWithoutEqualsSign() {
            String query = "debug&userId=100&verbose";
            String result = QueryParamMutator.substituteQuery(query, "userId", "200");
            assertEquals("debug&userId=200&verbose", result);

            // Mutating a key that had no equals sign adds =replacement
            String result2 = QueryParamMutator.substituteQuery(query, "debug", "true");
            assertEquals("debug=true&userId=100&verbose", result2);
        }

        @Test
        @DisplayName("Retains URL fragment attached at the end of query string")
        void testValuesWithFragment() {
            String query = "userId=100&action=view#details-section";
            String result = QueryParamMutator.substituteQuery(query, "userId", "200");
            assertEquals("userId=200&action=view#details-section", result);
        }
    }

    // =========================================================================
    // 4. FORM BODY MUTATOR BOUNDARY TESTS
    // =========================================================================

    @Nested
    @DisplayName("FormBodyMutator Boundary Tests")
    class FormBodyMutatorBoundaryTests {

        @Test
        @DisplayName("Large form payloads: handles thousands of parameters efficiently and accurately")
        void testLargeFormPayload() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5000; i++) {
                sb.append("field_").append(i).append("=val_").append(i).append("&");
            }
            sb.append("userId=100&last=true");
            String largeBody = sb.toString();

            String result = FormBodyMutator.substituteFormBody(largeBody, "userId", "999999");
            assertNotNull(result);
            assertTrue(result.contains("userId=999999"));
            assertTrue(result.contains("field_0=val_0"));
            assertTrue(result.contains("field_4999=val_4999"));
            assertTrue(result.endsWith("last=true"));
        }

        @Test
        @DisplayName("Binary-like garbage and exotic characters in form bodies handled gracefully")
        void testBinaryAndExoticCharactersInFormBody() {
            String body = "user=100&raw=%FF%FE%00%01&unicode=%E2%9C%93&other=test";
            String result = FormBodyMutator.substituteFormBody(body, "user", "200");
            assertEquals("user=200&raw=%FF%FE%00%01&unicode=%E2%9C%93&other=test", result);
        }

        @Test
        @DisplayName("Mismatched Content-Length header is corrected to exact UTF-8 byte count after mutation")
        void testMismatchedContentLengthHeaderCorrected() {
            String body = "userId=100&name=initial";
            // Deliberately incorrect initial Content-Length (1 instead of actual length)
            HttpRequest req = MockHttpRequestHelper.createMockRequest(
                    "https://api.target.com/submit",
                    "POST",
                    body,
                    Map.of(
                            "Content-Type", "application/x-www-form-urlencoded",
                            "Content-Length", "1"
                    )
            );

            HttpRequest mutated = FormBodyMutator.mutate(req, "userId", "200999");
            assertNotNull(mutated);
            assertEquals("userId=200999&name=initial", mutated.bodyToString());
            int actualBytes = mutated.bodyToString().getBytes(StandardCharsets.UTF_8).length;
            assertEquals(String.valueOf(actualBytes), mutated.headerValue("Content-Length"));
        }
    }
}
