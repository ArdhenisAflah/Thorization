package thorization.core.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Modifies application/x-www-form-urlencoded body parameters while preserving
 * remaining form fields, URL encoding, and synchronizing the Content-Length header.
 */
public final class FormBodyMutator {

    private static final String FORM_CONTENT_TYPE = "application/x-www-form-urlencoded";

    private FormBodyMutator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Mutates form-encoded body parameters on an HttpRequest.
     */
    public static HttpRequest mutate(HttpRequest request, String paramKey, String replacement) {
        if (request == null || paramKey == null || replacement == null) {
            return request;
        }

        // Validate Content-Type header if present
        String contentType = request.headerValue("Content-Type");
        if (contentType != null && !isFormUrlEncoded(contentType)) {
            return request;
        }

        String body = request.bodyToString();
        if (body == null || body.trim().isEmpty()) {
            return request;
        }

        String newBody = substituteFormBody(body, paramKey, replacement);
        if (newBody.equals(body)) {
            return request;
        }

        HttpRequest updated = request.withBody(newBody);

        // Synchronize Content-Length header to prevent HTTP truncation or socket hanging
        if (updated.hasHeader("Content-Length")) {
            int byteLength = newBody.getBytes(StandardCharsets.UTF_8).length;
            updated = updated.withUpdatedHeader("Content-Length", String.valueOf(byteLength));
        }

        return updated;
    }

    /**
     * Pure string form body substitution.
     */
    public static String substituteFormBody(String currentBody, String paramKey, String replacement) {
        if (currentBody == null || currentBody.isEmpty() || paramKey == null || replacement == null) {
            return currentBody;
        }

        String cleanKey = paramKey.replace("{", "").replace("}", "").trim();
        if (cleanKey.isEmpty()) {
            return currentBody;
        }

        // Preserve trailing CRLF / newline if present
        String lineEnding = "";
        String body = currentBody;
        if (body.endsWith("\r\n")) {
            lineEnding = "\r\n";
            body = body.substring(0, body.length() - 2);
        } else if (body.endsWith("\n")) {
            lineEnding = "\n";
            body = body.substring(0, body.length() - 1);
        }

        String[] pairs = body.split("&", -1);
        List<String> newPairs = new ArrayList<>(pairs.length);
        boolean mutated = false;
        String encodedReplacement = encodeFormValue(replacement);

        for (String pair : pairs) {
            if (pair.isEmpty()) {
                newPairs.add(pair);
                continue;
            }

            int eq = pair.indexOf('=');
            String rawName;
            if (eq != -1) {
                rawName = pair.substring(0, eq);
            } else {
                rawName = pair;
            }

            if (isKeyMatch(rawName, cleanKey)) {
                newPairs.add(rawName + "=" + encodedReplacement);
                mutated = true;
            } else {
                // Retain untouched field verbatim
                newPairs.add(pair);
            }
        }

        if (!mutated) {
            return currentBody;
        }

        return String.join("&", newPairs) + lineEnding;
    }

    public static boolean isFormUrlEncoded(String contentType) {
        if (contentType == null) {
            return false;
        }
        return contentType.toLowerCase().contains(FORM_CONTENT_TYPE);
    }

    private static boolean isKeyMatch(String rawName, String targetKey) {
        if (rawName.equalsIgnoreCase(targetKey)) {
            return true;
        }
        try {
            String decodedName = URLDecoder.decode(rawName, StandardCharsets.UTF_8);
            return decodedName.equalsIgnoreCase(targetKey);
        } catch (Exception e) {
            return false;
        }
    }

    private static String encodeFormValue(String value) {
        if (value == null) {
            return "";
        }
        // If already percent-encoded, preserve as-is
        if (value.matches(".*%[0-9a-fA-F]{2}.*")) {
            return value;
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
