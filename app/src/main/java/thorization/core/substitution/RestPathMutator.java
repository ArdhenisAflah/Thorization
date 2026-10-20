package thorization.core.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles REST path parameter substitutions with support for path templates,
 * segment replacement, query string preservation, and regex fallback.
 */
public final class RestPathMutator {

    private RestPathMutator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Mutates the path of an HttpRequest if a template or regex pattern matches.
     * Preserves the existing query string and path encoding.
     */
    public static HttpRequest mutate(HttpRequest request, String pathPattern, String paramKey, String replacement) {
        if (request == null || replacement == null) {
            return request;
        }

        String pathWithoutQuery = request.pathWithoutQuery();
        if (pathWithoutQuery == null || pathWithoutQuery.isEmpty()) {
            pathWithoutQuery = "/";
        }
        String query = request.query();

        String template = (pathPattern != null && pathPattern.contains("{")) ? pathPattern : paramKey;
        String newPathWithoutQuery = substitutePath(pathWithoutQuery, template, paramKey, replacement);

        if (newPathWithoutQuery.equals(pathWithoutQuery)) {
            return request;
        }

        // Preserve query string intact
        String fullPath = (query != null && !query.isEmpty())
                ? newPathWithoutQuery + "?" + query
                : newPathWithoutQuery;

        return request.withPath(fullPath);
    }

    /**
     * Pure string path substitution. Evaluates template segment matching first,
     * falling back to regex capture group substitution.
     */
    public static String substitutePath(String currentPath, String template, String paramKey, String replacement) {
        if (currentPath == null || replacement == null) {
            return currentPath;
        }

        // Clean paramKey: e.g. "{id}" -> "id"
        String cleanKey = (paramKey != null) ? paramKey.replace("{", "").replace("}", "").trim() : "";
        String targetPlaceholder = "{" + cleanKey + "}";

        // 1. Template Segment Matching
        if (template != null && template.contains("{") && !cleanKey.isEmpty()) {
            String substituted = matchAndSubstituteTemplate(currentPath, template, targetPlaceholder, replacement);
            if (substituted != null) {
                return substituted;
            }
        }

        // 2. Regex Pattern Matching Fallback
        String patternToUse = (pathPatternIsRegex(pathPatternOrKey(template, paramKey)))
                ? pathPatternOrKey(template, paramKey)
                : paramKey;

        if (patternToUse != null && !patternToUse.isEmpty()) {
            try {
                Pattern p = Pattern.compile(patternToUse);
                Matcher m = p.matcher(currentPath);
                if (m.find()) {
                    String encodedReplacement = encodePathValue(replacement);
                    if (m.groupCount() >= 1) {
                        return currentPath.substring(0, m.start(1)) + encodedReplacement + currentPath.substring(m.end(1));
                    } else {
                        return currentPath.substring(0, m.start()) + encodedReplacement + currentPath.substring(m.end());
                    }
                }
            } catch (Exception ignored) {
                // Invalid regex fails safe
            }
        }

        return currentPath;
    }

    private static String matchAndSubstituteTemplate(String path, String template, String targetPlaceholder, String replacement) {
        boolean hasTrailingSlash = path.endsWith("/") && path.length() > 1;
        String[] tSegs = template.split("/", -1);
        String[] pSegs = path.split("/", -1);

        if (tSegs.length != pSegs.length) {
            return null;
        }

        String[] resultSegs = new String[pSegs.length];
        boolean matchedParam = false;

        for (int i = 0; i < tSegs.length; i++) {
            String tSeg = tSegs[i];
            String pSeg = pSegs[i];

            if (tSeg.equals(targetPlaceholder)) {
                // Exact placeholder segment: {id}
                resultSegs[i] = encodePathValue(replacement);
                matchedParam = true;
            } else if (tSeg.contains(targetPlaceholder)) {
                // Embedded placeholder: prefix_{id}_suffix
                String prefix = tSeg.substring(0, tSeg.indexOf(targetPlaceholder));
                String suffix = tSeg.substring(tSeg.indexOf(targetPlaceholder) + targetPlaceholder.length());
                if (pSeg.startsWith(prefix) && pSeg.endsWith(suffix) && pSeg.length() >= prefix.length() + suffix.length()) {
                    resultSegs[i] = prefix + encodePathValue(replacement) + suffix;
                    matchedParam = true;
                } else {
                    return null;
                }
            } else if (tSeg.startsWith("{") && tSeg.endsWith("}")) {
                // Other placeholder variable (e.g. {tenantId}): matches any non-empty path segment
                if (pSeg.isEmpty() && i != 0) {
                    return null;
                }
                resultSegs[i] = pSeg;
            } else if (tSeg.equals("*")) {
                // Wildcard segment
                resultSegs[i] = pSeg;
            } else {
                // Literal fixed segment
                if (!tSeg.equalsIgnoreCase(pSeg)) {
                    return null;
                }
                resultSegs[i] = pSeg;
            }
        }

        if (!matchedParam) {
            return null;
        }

        String reconstructed = String.join("/", resultSegs);
        if (hasTrailingSlash && !reconstructed.endsWith("/")) {
            reconstructed += "/";
        }
        return reconstructed;
    }

    private static boolean pathPatternIsRegex(String str) {
        return str != null && (str.contains("(") || str.contains(")") || str.contains(".*") || str.contains("\\d") || str.contains("["));
    }

    private static String pathPatternOrKey(String template, String paramKey) {
        if (pathPatternIsRegex(template)) {
            return template;
        }
        if (pathPatternIsRegex(paramKey)) {
            return paramKey;
        }
        return paramKey;
    }

    private static String encodePathValue(String value) {
        if (value == null) {
            return "";
        }
        // If already percent-encoded (e.g. user%20name), preserve as-is
        if (value.matches(".*%[0-9a-fA-F]{2}.*")) {
            return value;
        }
        // Encode spaces and path-sensitive characters (%20 instead of +)
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
