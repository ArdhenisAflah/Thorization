package thorization.report;

import burp.api.montoya.http.HttpService;
import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.requests.HttpRequest;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.util.HeaderUtils;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates copyable, terminal-ready cURL commands with strict POSIX/Bash escaping.
 */
public final class CurlUtils {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "cookie", "proxy-authorization",
            "x-auth-token", "x-access-token", "x-api-key", "api-key",
            "session", "token", "x-csrf-token", "x-xsrf-token"
    );

    private CurlUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // -------------------------------------------------------------------------
    // Core Generation APIs
    // -------------------------------------------------------------------------

    public static String generateCurl(HttpRequest request) {
        return generateCurl(request, false, true);
    }

    public static String generateCurl(HttpRequest request, boolean maskSensitiveHeaders) {
        return generateCurl(request, maskSensitiveHeaders, true);
    }

    public static String generateCurlSingleLine(HttpRequest request) {
        return generateCurl(request, false, false);
    }

    public static String generateCurlSingleLine(HttpRequest request, boolean maskSensitiveHeaders) {
        return generateCurl(request, maskSensitiveHeaders, false);
    }

    /**
     * Alias for generateCurl(request) for API compatibility.
     */
    public static String toCurlCommand(HttpRequest request) {
        return generateCurl(request);
    }

    // -------------------------------------------------------------------------
    // AuditRecord & UserRole Overloads
    // -------------------------------------------------------------------------

    public static String generateCurl(AuditRecord record, UserRole role) {
        return generateCurl(record, role, false, true);
    }

    public static String generateCurl(AuditRecord record, UserRole role, boolean maskSensitiveHeaders) {
        return generateCurl(record, role, maskSensitiveHeaders, true);
    }

    public static String generateCurl(AuditRecord record, UserRole role, boolean maskSensitiveHeaders, boolean multiline) {
        if (record == null) {
            return "";
        }

        HttpRequest targetRequest = resolveRequestForRole(record, role);
        if (targetRequest == null) {
            return "";
        }

        return generateCurl(targetRequest, maskSensitiveHeaders, multiline);
    }

    // -------------------------------------------------------------------------
    // Internal Request Resolution & Command Builder
    // -------------------------------------------------------------------------

    public static HttpRequest resolveRequestForRole(AuditRecord record, UserRole role) {
        if (record == null) {
            return null;
        }

        if (role == null) {
            return record.getOriginalRequest();
        }

        boolean isUnauth = "unauthenticated".equalsIgnoreCase(role.getId())
                || (role.getName() != null && "unauthenticated".equalsIgnoreCase(role.getName()));

        if (isUnauth) {
            if (record.getUnauthenticatedResult() != null && record.getUnauthenticatedResult().getRequest() != null) {
                return record.getUnauthenticatedResult().getRequest();
            }
            if (record.getOriginalRequest() != null) {
                return HeaderUtils.stripAuthHeaders(record.getOriginalRequest());
            }
            return null;
        }

        if (record.getRoleResults() != null) {
            if (role.getId() != null && record.getRoleResults().containsKey(role.getId())) {
                AuthorizationCheckResult result = record.getRoleResults().get(role.getId());
                if (result != null && result.getRequest() != null) {
                    return result.getRequest();
                }
            }
            for (Map.Entry<String, AuthorizationCheckResult> entry : record.getRoleResults().entrySet()) {
                AuthorizationCheckResult res = entry.getValue();
                if (res != null && role.getName() != null && role.getName().equalsIgnoreCase(res.getRoleName())) {
                    if (res.getRequest() != null) {
                        return res.getRequest();
                    }
                }
            }
        }

        // Fallback: derive dynamically using role headers
        if (record.getOriginalRequest() != null) {
            return HeaderUtils.applyRoleHeaders(record.getOriginalRequest(), role);
        }

        return null;
    }

    public static String generateCurl(HttpRequest request, boolean maskSensitiveHeaders, boolean multiline) {
        if (request == null) {
            return "";
        }

        String sep = multiline ? " \\\n  " : " ";
        StringBuilder sb = new StringBuilder("curl --path-as-is -i -s -k");

        // Method
        String method = request.method() != null && !request.method().isBlank()
                ? request.method().toUpperCase(Locale.ROOT)
                : "GET";
        sb.append(sep).append("-X ").append(method);

        // Target URL
        String url = resolveUrl(request);
        sb.append(sep).append(escapeBashSingleQuotes(url));

        // Headers
        if (request.headers() != null) {
            for (HttpHeader header : request.headers()) {
                if (header == null) continue;
                String name = header.name();
                String value = header.value() != null ? header.value() : "";

                // Skip HTTP/2 pseudo-headers (:method, :path, etc.)
                if (name == null || name.startsWith(":")) {
                    continue;
                }

                // Omit Content-Length: cURL calculates it automatically
                if ("content-length".equalsIgnoreCase(name)) {
                    continue;
                }

                if (maskSensitiveHeaders && isSensitiveHeader(name)) {
                    value = maskHeaderValue(name, value);
                }

                String headerStr = name + ": " + value;
                sb.append(sep).append("-H ").append(escapeBashSingleQuotes(headerStr));
            }
        }

        // Request Body
        String body = request.bodyToString();
        if (body != null && !body.isEmpty()) {
            boolean isBinary = containsBinaryBytes(request.body() != null ? request.body().getBytes() : null);
            String dataFlag = isBinary ? "--data-binary " : "--data-raw ";
            sb.append(sep).append(dataFlag).append(escapeBashSingleQuotes(body));
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Shell Escaping & Masking Utilities
    // -------------------------------------------------------------------------

    public static String escapeBashSingleQuotes(String input) {
        if (input == null) {
            return "''";
        }
        return "'" + input.replace("'", "'\\''") + "'";
    }

    public static boolean isSensitiveHeader(String headerName) {
        return headerName != null && SENSITIVE_HEADERS.contains(headerName.trim().toLowerCase(Locale.ROOT));
    }

    public static String maskHeaderValue(String name, String value) {
        if (value == null || value.isBlank()) {
            return "[REDACTED]";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if ("authorization".equals(lower) || "proxy-authorization".equals(lower)) {
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                return "Bearer [REDACTED]";
            }
            if (value.regionMatches(true, 0, "Basic ", 0, 6)) {
                return "Basic [REDACTED]";
            }
        }
        return "[REDACTED]";
    }

    public static String resolveUrl(HttpRequest request) {
        if (request.url() != null && !request.url().isBlank()) {
            return request.url();
        }
        HttpService service = request.httpService();
        if (service != null && service.host() != null && !service.host().isBlank()) {
            String scheme = service.secure() ? "https" : "http";
            int port = service.port();
            boolean defaultPort = (service.secure() && port == 443) || (!service.secure() && port == 80) || port <= 0;
            String portStr = defaultPort ? "" : ":" + port;
            String path = request.path() != null ? request.path() : "/";
            return scheme + "://" + service.host() + portStr + (path.startsWith("/") ? path : "/" + path);
        }
        return request.path() != null && !request.path().isBlank() ? request.path() : "/";
    }

    private static boolean containsBinaryBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return false;
        int checkLen = Math.min(bytes.length, 1024);
        for (int i = 0; i < checkLen; i++) {
            byte b = bytes[i];
            if (b == 0 || (b < 32 && b != '\t' && b != '\n' && b != '\r')) {
                return true;
            }
        }
        return false;
    }
}
