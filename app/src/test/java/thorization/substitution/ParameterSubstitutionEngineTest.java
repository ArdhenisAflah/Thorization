package thorization.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import thorization.core.substitution.ParameterSubstitutionEngine;
import thorization.model.UserRole;
import thorization.model.substitution.ParameterSubstitutionRule;
import thorization.model.substitution.SubstitutionTargetType;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ParameterSubstitutionEngine Comprehensive Test Suite")
class ParameterSubstitutionEngineTest {

    private ParameterSubstitutionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ParameterSubstitutionEngine();
    }

    // =========================================================================
    // 1. HOST SCOPING EVALUATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Host Scoping Evaluation")
    class HostScopingTests {

        @Test
        @DisplayName("Matches exact host pattern case-insensitively")
        void testExactHostMatch() {
            assertTrue(engine.matchesHost("api.target.com", "api.target.com"));
            assertTrue(engine.matchesHost("API.TARGET.COM", "api.target.com"));
            assertTrue(engine.matchesHost("api.target.com", "API.TARGET.COM"));
            assertFalse(engine.matchesHost("other.target.com", "api.target.com"));
        }

        @Test
        @DisplayName("Matches wildcard prefix glob (*.target.com)")
        void testWildcardPrefixGlob() {
            assertTrue(engine.matchesHost("api.target.com", "*.target.com"));
            assertTrue(engine.matchesHost("auth.dev.target.com", "*.target.com"));
            assertFalse(engine.matchesHost("api.other.com", "*.target.com"));
        }

        @Test
        @DisplayName("Matches universal wildcard (*) and empty patterns")
        void testUniversalWildcard() {
            assertTrue(engine.matchesHost("anything.com", "*"));
            assertTrue(engine.matchesHost("anything.com", ""));
            assertTrue(engine.matchesHost("anything.com", null));
            assertTrue(engine.matchesHost("anything.com", "   "));
        }

        @Test
        @DisplayName("Strips port numbers before host matching")
        void testHostPortStripping() {
            assertTrue(engine.matchesHost("api.target.com:8443", "api.target.com"));
            assertTrue(engine.matchesHost("api.target.com:8080", "*.target.com"));
        }

        @Test
        @DisplayName("Matches regular expressions in host patterns")
        void testHostRegexMatch() {
            assertTrue(engine.matchesHost("api-prod.example.com", "^api-(prod|stage)\\.example\\.com$"));
            assertTrue(engine.matchesHost("api-stage.example.com", "^api-(prod|stage)\\.example\\.com$"));
            assertFalse(engine.matchesHost("api-test.example.com", "^api-(prod|stage)\\.example\\.com$"));
        }
    }

    // =========================================================================
    // 2. PATH SCOPING EVALUATION TESTS
    // =========================================================================

    @Nested
    @DisplayName("Path Scoping Evaluation")
    class PathScopingTests {

        @Test
        @DisplayName("Matches exact path without query")
        void testExactPathMatch() {
            assertTrue(engine.matchesPath("/api/v1/users", "/api/v1/users"));
            assertTrue(engine.matchesPath("/api/v1/users?filter=all", "/api/v1/users"));
            assertFalse(engine.matchesPath("/api/v2/users", "/api/v1/users"));
        }

        @Test
        @DisplayName("Matches REST path templates (/api/users/{id}/profile)")
        void testRestTemplateMatch() {
            assertTrue(engine.matchesPath("/api/users/100/profile", "/api/users/{id}/profile"));
            assertTrue(engine.matchesPath("/api/users/abc-uuid/profile", "/api/users/{id}/profile"));
            assertFalse(engine.matchesPath("/api/users/100/orders", "/api/users/{id}/profile"));
            assertFalse(engine.matchesPath("/api/v2/users/100/profile", "/api/users/{id}/profile"));
        }

        @Test
        @DisplayName("Matches REST path templates with wildcard suffix (/api/users/{id}/*)")
        void testRestTemplateWithWildcard() {
            assertTrue(engine.matchesPath("/api/users/100/profile", "/api/users/{id}/*"));
            assertTrue(engine.matchesPath("/api/users/100/orders/detail", "/api/users/{id}/*"));
            assertFalse(engine.matchesPath("/api/products/100/info", "/api/users/{id}/*"));
        }

        @Test
        @DisplayName("Matches glob prefix pattern (/api/v1/*)")
        void testGlobPrefixPattern() {
            assertTrue(engine.matchesPath("/api/v1/users", "/api/v1/*"));
            assertTrue(engine.matchesPath("/api/v1/orders/123", "/api/v1/*"));
            assertFalse(engine.matchesPath("/api/v2/users", "/api/v1/*"));
        }

        @Test
        @DisplayName("Matches regex path pattern")
        void testRegexPathPattern() {
            assertTrue(engine.matchesPath("/api/v1/orders", "^/api/v[12]/orders.*$"));
            assertTrue(engine.matchesPath("/api/v2/orders/55", "^/api/v[12]/orders.*$"));
            assertFalse(engine.matchesPath("/api/v3/orders", "^/api/v[12]/orders.*$"));
        }
    }

    // =========================================================================
    // 3. ROLE VALUE RESOLUTION HIERARCHY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Role Value Resolution Hierarchy")
    class RoleValueResolutionTests {

        private ParameterSubstitutionRule rule;
        private UserRole role;

        @BeforeEach
        void setUp() {
            rule = new ParameterSubstitutionRule("Test Rule", SubstitutionTargetType.REST_PATH, "id");
            rule.setDefaultValue("default-999");

            role = new UserRole("User B", "", "");
            role.setId("role-uuid-b");
        }

        @Test
        @DisplayName("Priority 1: Rule role mapping by Role ID takes top precedence")
        void testPriority1_RuleRoleIdMapping() {
            rule.setRoleValue("role-uuid-b", "override-by-id-100");
            rule.setRoleValue("User B", "override-by-name-200");
            role.setParameterValue("id", "role-param-300");

            String resolved = engine.resolveReplacementValue(rule, role);
            assertEquals("override-by-id-100", resolved);
        }

        @Test
        @DisplayName("Priority 2: Rule role mapping by Role Name takes second precedence")
        void testPriority2_RuleRoleNameMapping() {
            rule.setRoleValue("User B", "override-by-name-200");
            role.setParameterValue("id", "role-param-300");

            String resolved = engine.resolveReplacementValue(rule, role);
            assertEquals("override-by-name-200", resolved);
        }

        @Test
        @DisplayName("Priority 3: Role's internal parameter map takes third precedence")
        void testPriority3_RoleInternalParameterMap() {
            role.setParameterValue("id", "role-param-300");

            String resolved = engine.resolveReplacementValue(rule, role);
            assertEquals("role-param-300", resolved);
        }

        @Test
        @DisplayName("Priority 3: Role internal map resolves with normalized key ({id} vs id)")
        void testPriority3_NormalizedKey() {
            rule.setParameterKey("{userId}");
            role.setParameterValue("userId", "normalized-val-400");

            String resolved = engine.resolveReplacementValue(rule, role);
            assertEquals("normalized-val-400", resolved);
        }

        @Test
        @DisplayName("Priority 4: Rule default fallback value is used when no role mapping exists")
        void testPriority4_DefaultValueFallback() {
            String resolved = engine.resolveReplacementValue(rule, role);
            assertEquals("default-999", resolved);
        }

        @Test
        @DisplayName("Unauthenticated Test: Resolves 'unauthenticated' pseudo-role key")
        void testUnauthenticatedRoleMapping() {
            rule.setRoleValue("unauthenticated", "unauth-val-777");

            String resolved = engine.resolveReplacementValue(rule, null);
            assertEquals("unauth-val-777", resolved);
        }

        @Test
        @DisplayName("Unauthenticated Test: Falls back to default value if unauthenticated unmapped")
        void testUnauthenticatedDefaultFallback() {
            String resolved = engine.resolveReplacementValue(rule, null);
            assertEquals("default-999", resolved);
        }
    }

    // =========================================================================
    // 4. RULE DISPATCH & CHAINING TESTS
    // =========================================================================

    @Nested
    @DisplayName("Rule Dispatch & Chaining")
    class RuleDispatchTests {

        @Test
        @DisplayName("Disabled rules are skipped completely")
        void testDisabledRuleSkipped() {
            ParameterSubstitutionRule rule = new ParameterSubstitutionRule("Rule 1", SubstitutionTargetType.REST_PATH, "id");
            rule.setEnabled(false);
            rule.setPathPattern("/api/users/{id}/*");
            rule.setDefaultValue("200");

            HttpRequest request = MockHttpRequestHelper.createMockRequest("https://api.target.com/api/users/100/profile", "GET", "", null);
            assertFalse(engine.isRuleInScope(rule, request));
        }

        @Test
        @DisplayName("Out-of-scope host prevents rule evaluation")
        void testOutOfScopeHostRejected() {
            ParameterSubstitutionRule rule = new ParameterSubstitutionRule("Rule 1", SubstitutionTargetType.REST_PATH, "id");
            rule.setHostPattern("api.target.com");
            rule.setPathPattern("/api/users/{id}/*");

            HttpRequest request = MockHttpRequestHelper.createMockRequest("https://other.com/api/users/100/profile", "GET", "", null);
            assertFalse(engine.isRuleInScope(rule, request));
        }

        @Test
        @DisplayName("Chains multiple rules on the same request (Path and Query)")
        void testChainingMultipleRules() {
            ParameterSubstitutionRule pathRule = new ParameterSubstitutionRule("Path Rule", SubstitutionTargetType.REST_PATH, "id");
            pathRule.setPathPattern("/api/users/{id}/orders");
            pathRule.setDefaultValue("200");

            ParameterSubstitutionRule queryRule = new ParameterSubstitutionRule("Query Rule", SubstitutionTargetType.URL_QUERY, "filter");
            queryRule.setDefaultValue("inactive");

            UserRole role = new UserRole("Test Role", "", "");
            HttpRequest request = MockHttpRequestHelper.createMockRequest("https://api.target.com/api/users/100/orders?filter=active", "GET", "", null);

            HttpRequest substituted = engine.applySubstitutions(request, role, List.of(pathRule, queryRule));
            assertNotNull(substituted);
            assertEquals("/api/users/200/orders", substituted.pathWithoutQuery());
            assertEquals("filter=inactive", substituted.query());
        }
    }

    // =========================================================================
    // 5. CONCURRENCY & THREAD SAFETY TESTS
    // =========================================================================

    @Nested
    @DisplayName("Thread Safety & Concurrency")
    class ConcurrencyTests {

        @Test
        @DisplayName("Concurrent executions across multiple threads produce consistent results without race conditions")
        void testConcurrentSubstitutions() throws InterruptedException {
            int threadCount = 10;
            int iterationsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            ParameterSubstitutionRule rule = new ParameterSubstitutionRule("Shared Rule", SubstitutionTargetType.REST_PATH, "id");
            rule.setRoleValue("role-1", "user-100");
            rule.setRoleValue("role-2", "user-200");

            UserRole role1 = new UserRole("Role 1", "", "");
            role1.setId("role-1");

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            String resolved = engine.resolveReplacementValue(rule, role1);
                            assertEquals("user-100", resolved);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent tasks should finish within timeout");
            executor.shutdown();
        }
    }
}
