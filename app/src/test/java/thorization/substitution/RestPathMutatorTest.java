package thorization.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thorization.core.substitution.RestPathMutator;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RestPathMutator Tests")
class RestPathMutatorTest {

    @Test
    @DisplayName("Single variable template substitution: /api/users/{id}/orders")
    void testSingleVariableTemplate() {
        String path = "/api/users/100/orders";
        String result = RestPathMutator.substitutePath(path, "/api/users/{id}/orders", "id", "200");
        assertEquals("/api/users/200/orders", result);
    }

    @Test
    @DisplayName("Single variable template with braces in paramKey: {id}")
    void testSingleVariableTemplateWithBracesInKey() {
        String path = "/api/users/100/orders";
        String result = RestPathMutator.substitutePath(path, "/api/users/{id}/orders", "{id}", "200");
        assertEquals("/api/users/200/orders", result);
    }

    @Test
    @DisplayName("Multi-variable template preserves other variables: /api/tenants/{tenantId}/users/{id}/orders")
    void testMultiVariableTemplate() {
        String path = "/api/tenants/acme/users/100/orders";
        String result = RestPathMutator.substitutePath(path, "/api/tenants/{tenantId}/users/{id}/orders", "id", "200");
        assertEquals("/api/tenants/acme/users/200/orders", result);
    }

    @Test
    @DisplayName("Embedded placeholder with prefix and suffix: prefix_{id}_suffix")
    void testEmbeddedPlaceholder() {
        String path = "/api/items/item_100_v1/details";
        String result = RestPathMutator.substitutePath(path, "/api/items/item_{id}_v1/details", "id", "200");
        assertEquals("/api/items/item_200_v1/details", result);
    }

    @Test
    @DisplayName("Wildcard segment in template matching: /api/*/users/{id}")
    void testWildcardSegment() {
        String path = "/api/v1/users/100";
        String result = RestPathMutator.substitutePath(path, "/api/*/users/{id}", "id", "200");
        assertEquals("/api/v1/users/200", result);
    }

    @Test
    @DisplayName("Preserves trailing slash if present in original path")
    void testPreserveTrailingSlash() {
        String path = "/api/users/100/";
        String result = RestPathMutator.substitutePath(path, "/api/users/{id}/", "id", "200");
        assertEquals("/api/users/200/", result);
    }

    @Test
    @DisplayName("Non-matching path returns original without modification")
    void testNonMatchingPathReturnsOriginal() {
        String path = "/api/products/50/details";
        String result = RestPathMutator.substitutePath(path, "/api/users/{id}/orders", "id", "200");
        assertEquals("/api/products/50/details", result);
    }

    @Test
    @DisplayName("Path segment with spaces encoded as %20")
    void testPathSegmentWithSpaceEncoding() {
        String path = "/files/old%20report/view";
        String result = RestPathMutator.substitutePath(path, "/files/{name}/view", "name", "new report");
        assertEquals("/files/new%20report/view", result);
    }

    @Test
    @DisplayName("Regex fallback with capture group: substitutes group 1")
    void testRegexFallbackWithCaptureGroup() {
        String path = "/api/v2/items/item-9999/status";
        String result = RestPathMutator.substitutePath(path, "/items/item-([0-9]+)/", "([0-9]+)", "8888");
        assertEquals("/api/v2/items/item-8888/status", result);
    }

    @Test
    @DisplayName("HttpRequest mutation preserves query string intact")
    void testHttpRequestMutationPreservesQueryString() {
        HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/api/users/100/orders?sort=desc&limit=20",
                "GET",
                "",
                null
        );

        HttpRequest mutated = RestPathMutator.mutate(mockReq, "/api/users/{id}/orders", "id", "200");
        assertNotNull(mutated);
        assertEquals("/api/users/200/orders", mutated.pathWithoutQuery());
        assertEquals("sort=desc&limit=20", mutated.query());
        assertTrue(mutated.url().contains("/api/users/200/orders?sort=desc&limit=20"));
    }

    @Test
    @DisplayName("HttpRequest mutation returns original instance when path does not match")
    void testHttpRequestUnchangedWhenNotMatched() {
        HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/api/products/50/info",
                "GET",
                "",
                null
        );

        HttpRequest mutated = RestPathMutator.mutate(mockReq, "/api/users/{id}/orders", "id", "200");
        assertSame(mockReq, mutated);
    }
}
