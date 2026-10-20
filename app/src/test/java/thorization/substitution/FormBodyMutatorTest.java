package thorization.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import thorization.core.substitution.FormBodyMutator;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("FormBodyMutator Tests")
class FormBodyMutatorTest {

    @Test
    @DisplayName("Single form field replacement")
    void testSingleFormFieldReplacement() {
        String body = "userId=100&action=save";
        String result = FormBodyMutator.substituteFormBody(body, "userId", "200");
        assertEquals("userId=200&action=save", result);
    }

    @Test
    @DisplayName("Preserves untouched form fields and ordering")
    void testPreserveUntouchedFieldsAndOrder() {
        String body = "a=1&userId=100&b=2";
        String result = FormBodyMutator.substituteFormBody(body, "userId", "200");
        assertEquals("a=1&userId=200&b=2", result);
    }

    @Test
    @DisplayName("Special characters in replacement value are URL-encoded")
    void testSpecialCharactersInReplacementEncoded() {
        String body = "name=initial&role=user";
        String result = FormBodyMutator.substituteFormBody(body, "name", "Alice & Bob");
        assertEquals("name=Alice+%26+Bob&role=user", result);
    }

    @Test
    @DisplayName("Preserves trailing CRLF in form body")
    void testPreservesTrailingCrlf() {
        String body = "userId=100&action=save\r\n";
        String result = FormBodyMutator.substituteFormBody(body, "userId", "200");
        assertEquals("userId=200&action=save\r\n", result);
    }

    @Test
    @DisplayName("Preserves trailing newline in form body")
    void testPreservesTrailingNewline() {
        String body = "userId=100&action=save\n";
        String result = FormBodyMutator.substituteFormBody(body, "userId", "200");
        assertEquals("userId=200&action=save\n", result);
    }

    @Test
    @DisplayName("Matches parameter key configured with braces: {userId}")
    void testParamKeyWithBraces() {
        String body = "userId=100&action=save";
        String result = FormBodyMutator.substituteFormBody(body, "{userId}", "200");
        assertEquals("userId=200&action=save", result);
    }

    @Test
    @DisplayName("Synchronizes Content-Length header with exact new byte length")
    void testSynchronizesContentLengthHeader() {
        String body = "userId=100";
        HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/submit",
                "POST",
                body,
                Map.of(
                        "Content-Type", "application/x-www-form-urlencoded",
                        "Content-Length", String.valueOf(body.getBytes(StandardCharsets.UTF_8).length)
                )
        );

        HttpRequest mutated = FormBodyMutator.mutate(mockReq, "userId", "20050");
        assertNotNull(mutated);
        assertEquals("userId=20050", mutated.bodyToString());
        assertEquals("12", mutated.headerValue("Content-Length"));
    }

    @Test
    @DisplayName("Skips mutation if Content-Type is application/json")
    void testSkipsJsonContentType() {
        String body = "{\"userId\": 100}";
        HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/submit",
                "POST",
                body,
                Map.of("Content-Type", "application/json")
        );

        HttpRequest mutated = FormBodyMutator.mutate(mockReq, "userId", "200");
        assertSame(mockReq, mutated);
    }

    @Test
    @DisplayName("Skips mutation if Content-Type is multipart/form-data")
    void testSkipsMultipartContentType() {
        String body = "--boundary\r\nContent-Disposition: form-data; name=\"userId\"\r\n\r\n100\r\n--boundary--";
        HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/submit",
                "POST",
                body,
                Map.of("Content-Type", "multipart/form-data; boundary=boundary")
        );

        HttpRequest mutated = FormBodyMutator.mutate(mockReq, "userId", "200");
        assertSame(mockReq, mutated);
    }

    @Test
    @DisplayName("Returns original request when target field is not present")
    void testReturnsOriginalWhenFieldNotFound() {
        String body = "other=value";
        HttpRequest mockReq = MockHttpRequestHelper.createMockRequest(
                "https://api.target.com/submit",
                "POST",
                body,
                Map.of("Content-Type", "application/x-www-form-urlencoded")
        );

        HttpRequest mutated = FormBodyMutator.mutate(mockReq, "userId", "200");
        assertSame(mockReq, mutated);
    }
}
