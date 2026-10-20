package thorization.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thorization.core.substitution.QueryParamMutator;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("QueryParamMutator Tests")
class QueryParamMutatorTest {

    @Test
    @DisplayName("Single query parameter replacement")
    void testSingleQueryParamReplacement() {
        String query = "userId=100";
        String result = QueryParamMutator.substituteQuery(query, "userId", "200");
        assertEquals("userId=200", result);
    }

    @Test
    @DisplayName("Preserves remaining parameters and their original sequence")
    void testPreserveRemainingParamsAndOrder() {
        String query = "page=1&userId=100&sort=desc&limit=50";
        String result = QueryParamMutator.substituteQuery(query, "userId", "200");
        assertEquals("page=1&userId=200&sort=desc&limit=50", result);
    }

    @Test
    @DisplayName("Preserves verbatim custom encoding of other parameters without corruption")
    void testPreserveVerbatimEncodingOfOtherParams() {
        String query = "filter=%24select%3Dname%2Bemail&userId=100&lang=en-US";
        String result = QueryParamMutator.substituteQuery(query, "userId", "200");
        assertEquals("filter=%24select%3Dname%2Bemail&userId=200&lang=en-US", result);
    }

    @Test
    @DisplayName("Special characters in replacement value are properly URL-encoded")
    void testSpecialCharactersInReplacementEncoded() {
        String query = "search=test&tag=alpha";
        String result = QueryParamMutator.substituteQuery(query, "search", "shoes & socks");
        assertEquals("search=shoes+%26+socks&tag=alpha", result);
    }

    @Test
    @DisplayName("Multiple occurrences of target parameter are all replaced (HPP)")
    void testMultipleOccurrencesReplaced() {
        String query = "id=10&other=x&id=20";
        String result = QueryParamMutator.substituteQuery(query, "id", "30");
        assertEquals("id=30&other=x&id=30", result);
    }

    @Test
    @DisplayName("Matches parameter key configured with curly braces: {userId}")
    void testParamKeyWithCurlyBraces() {
        String query = "userId=100&action=view";
        String result = QueryParamMutator.substituteQuery(query, "{userId}", "200");
        assertEquals("userId=200&action=view", result);
    }

    @Test
    @DisplayName("Matches percent-encoded parameter name: filter%5Buser_id%5D")
    void testUrlEncodedParamKeyMatch() {
        String query = "filter%5Buser_id%5D=100&sort=asc";
        String result = QueryParamMutator.substituteQuery(query, "filter[user_id]", "200");
        assertEquals("filter%5Buser_id%5D=200&sort=asc", result);
    }

    @Test
    @DisplayName("Preserves URL fragment attached to query string")
    void testUrlFragmentPreserved() {
        String query = "userId=100&tab=info#overview";
        String result = QueryParamMutator.substituteQuery(query, "userId", "200");
        assertEquals("userId=200&tab=info#overview", result);
    }

    @Test
    @DisplayName("HttpRequest query parameter mutation end-to-end")
    void testHttpRequestMutationEndToEnd() {
        HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/api/v1/orders?userId=100&status=active",
                "GET",
                "",
                null
        );

        HttpRequest mutated = QueryParamMutator.mutate(mockReq, "userId", "200");
        assertNotNull(mutated);
        assertEquals("userId=200&status=active", mutated.query());
        assertEquals("/api/v1/orders", mutated.pathWithoutQuery());
    }

    @Test
    @DisplayName("Returns original HttpRequest instance when query parameter not found")
    void testHttpRequestUnchangedWhenKeyNotFound() {
        HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/api/v1/orders?other=123",
                "GET",
                "",
                null
        );

        HttpRequest mutated = QueryParamMutator.mutate(mockReq, "userId", "200");
        assertSame(mockReq, mutated);
    }
}
