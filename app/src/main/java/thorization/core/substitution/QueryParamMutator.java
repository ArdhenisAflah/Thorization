package thorization.core.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Modifies URL query parameters while preserving remaining parameters,
 * parameter ordering, and proper UTF-8 URL encoding.
 */
public final class QueryParamMutator {

    private QueryParamMutator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Mutates query parameters on an HttpRequest.
     */
    public static HttpRequest mutate(HttpRequest request, String paramKey, String replacement) {
        if (request == null || paramKey == null || replacement == null) {
            return request;
        }

        String query = request.query();
        if (query == null || query.isEmpty()) {
            return request;
        }

        String newQuery = substituteQuery(query, paramKey, replacement);
        if (newQuery.equals(query)) {
            return request;
        }

        String pathWithoutQuery = request.pathWithoutQuery();
        if (pathWithoutQuery == null || pathWithoutQuery.isEmpty()) {
            pathWithoutQuery = "/";
        }

        String fullPath = pathWithoutQuery + "?" + newQuery;
        return request.withPath(fullPath);
    }

    /**
     * Pure string query substitution.
     */
    public static String substituteQuery(String currentQuery, String paramKey, String replacement) {
        if (currentQuery == null || currentQuery.isEmpty() || paramKey == null || replacement == null) {
            return currentQuery;
        }

        String cleanKey = paramKey.replace("{", "").replace("}", "").trim();
        if (cleanKey.isEmpty()) {
            return currentQuery;
        }

        boolean hasLeadingQuestion = currentQuery.startsWith("?");
        String rawQuery = hasLeadingQuestion ? currentQuery.substring(1) : currentQuery;

        // Extract fragment if attached (e.g. query#section)
        String fragment = "";
        int hashIdx = rawQuery.indexOf('#');
        if (hashIdx != -1) {
            fragment = rawQuery.substring(hashIdx);
            rawQuery = rawQuery.substring(0, hashIdx);
        }

        String[] pairs = rawQuery.split("&", -1);
        List<String> newPairs = new ArrayList<>(pairs.length);
        boolean mutated = false;
        String encodedReplacement = encodeParamValue(replacement);

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
                // Retain untouched parameter verbatim (preserves exact original encoding)
                newPairs.add(pair);
            }
        }

        if (!mutated) {
            return currentQuery;
        }

        String result = String.join("&", newPairs) + fragment;
        return hasLeadingQuestion ? "?" + result : result;
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

    private static String encodeParamValue(String value) {
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
