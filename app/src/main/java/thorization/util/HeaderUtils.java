package thorization.util;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import thorization.model.UserRole;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility functions for header and cookie modification.
 */
public class HeaderUtils {

    private static final Set<String> AUTH_HEADER_NAMES = new HashSet<>(Arrays.asList(
            "authorization", "cookie", "x-auth-token", "x-access-token",
            "token", "session", "x-csrf-token", "x-xsrf-token", "jwt"
    ));

    /**
     * Replaces authentication headers and cookies in the request with those from the given role.
     */
    public static HttpRequest applyRoleHeaders(HttpRequest original, UserRole role) {
        if (role == null || original == null) {
            return original;
        }

        HttpRequest request = original;
        Map<String, String> newHeaders = role.getParsedHeaders();

        // 1. Remove existing headers that are being overridden by role
        for (String headerName : newHeaders.keySet()) {
            if (request.hasHeader(headerName)) {
                request = request.withRemovedHeader(headerName);
            }
        }

        // 2. If role specifies cookies, replace or add Cookie header
        String customCookies = role.getRawCookies();
        if (customCookies != null && !customCookies.trim().isEmpty()) {
            if (request.hasHeader("Cookie")) {
                request = request.withRemovedHeader("Cookie");
            }
            request = request.withAddedHeader("Cookie", customCookies.trim());
        }

        // 3. Add all custom headers from the role
        for (Map.Entry<String, String> entry : newHeaders.entrySet()) {
            request = request.withAddedHeader(entry.getKey(), entry.getValue());
        }

        return request;
    }

    /**
     * Sets or replaces a specific header in the request.
     */
    public static HttpRequest setHeader(HttpRequest request, String name, String value) {
        if (request == null || name == null) return request;
        if (request.hasHeader(name)) {
            request = request.withRemovedHeader(name);
        }
        return request.withAddedHeader(name, value != null ? value : "");
    }

    /**
     * Strips all known authentication headers and cookies to test unauthenticated access.
     */
    public static HttpRequest stripAuthHeaders(HttpRequest original) {
        if (original == null) {
            return null;
        }

        HttpRequest request = original;
        List<HttpHeader> headers = original.headers();

        for (HttpHeader header : headers) {
            String nameLower = header.name().toLowerCase();
            if (AUTH_HEADER_NAMES.contains(nameLower) || nameLower.startsWith("x-auth-") || nameLower.startsWith("x-jwt-")) {
                request = request.withRemovedHeader(header.name());
            }
        }

        return request;
    }
}
