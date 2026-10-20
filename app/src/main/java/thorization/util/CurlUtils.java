package thorization.util;

import burp.api.montoya.http.message.requests.HttpRequest;
import thorization.model.AuditRecord;
import thorization.model.UserRole;

/**
 * Delegate bridge to thorization.report.CurlUtils for backward compatibility.
 */
public final class CurlUtils {

    private CurlUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String toCurlCommand(HttpRequest request) {
        return thorization.report.CurlUtils.toCurlCommand(request);
    }

    public static String generateCurl(HttpRequest request) {
        return thorization.report.CurlUtils.generateCurl(request);
    }

    public static String generateCurl(HttpRequest request, boolean maskSensitiveHeaders) {
        return thorization.report.CurlUtils.generateCurl(request, maskSensitiveHeaders);
    }

    public static String generateCurlSingleLine(HttpRequest request) {
        return thorization.report.CurlUtils.generateCurlSingleLine(request);
    }

    public static String generateCurl(AuditRecord record, UserRole role) {
        return thorization.report.CurlUtils.generateCurl(record, role);
    }

    public static String generateCurl(AuditRecord record, UserRole role, boolean maskSensitiveHeaders) {
        return thorization.report.CurlUtils.generateCurl(record, role, maskSensitiveHeaders);
    }

    public static String escapeBashSingleQuotes(String input) {
        return thorization.report.CurlUtils.escapeBashSingleQuotes(input);
    }
}
