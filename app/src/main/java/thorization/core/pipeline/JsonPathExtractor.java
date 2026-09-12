package thorization.core.pipeline;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * High-performance JSONPath and Regex extractor for extracting dynamic variables
 * from HTTP request and response payloads.
 */
public class JsonPathExtractor {

    /**
     * Extracts a value from a JSON string using a JSONPath expression (e.g. $.id, $.data.id, $[0].id_unique).
     */
    public static String extractJsonPath(String json, String path) {
        if (json == null || json.trim().isEmpty() || path == null || path.trim().isEmpty()) {
            return null;
        }

        try {
            JsonElement root = JsonParser.parseString(json.trim());
            String cleanPath = path.trim();
            if (cleanPath.startsWith("$.")) {
                cleanPath = cleanPath.substring(2);
            } else if (cleanPath.startsWith("$")) {
                cleanPath = cleanPath.substring(1);
            }

            return evaluatePath(root, cleanPath);
        } catch (Exception e) {
            return null;
        }
    }

    private static String evaluatePath(JsonElement current, String path) {
        if (current == null || current.isJsonNull()) {
            return null;
        }

        if (path.isEmpty()) {
            return extractPrimitive(current);
        }

        // Handle array root syntax $[0] or [0]
        if (path.startsWith("[")) {
            int closeBracket = path.indexOf(']');
            if (closeBracket > 0) {
                int index = Integer.parseInt(path.substring(1, closeBracket));
                String remainder = path.substring(closeBracket + 1);
                if (remainder.startsWith(".")) {
                    remainder = remainder.substring(1);
                }
                if (current.isJsonArray()) {
                    JsonArray array = current.getAsJsonArray();
                    if (index >= 0 && index < array.size()) {
                        return evaluatePath(array.get(index), remainder);
                    }
                }
                return null;
            }
        }

        // Split by first dot or bracket
        int dotIndex = path.indexOf('.');
        int bracketIndex = path.indexOf('[');

        String segment;
        String remainder = "";

        if (dotIndex != -1 && (bracketIndex == -1 || dotIndex < bracketIndex)) {
            segment = path.substring(0, dotIndex);
            remainder = path.substring(dotIndex + 1);
        } else if (bracketIndex != -1) {
            segment = path.substring(0, bracketIndex);
            remainder = path.substring(bracketIndex);
        } else {
            segment = path;
        }

        if (!segment.isEmpty()) {
            if (current.isJsonObject()) {
                JsonObject obj = current.getAsJsonObject();
                if (obj.has(segment)) {
                    return evaluatePath(obj.get(segment), remainder);
                }
            }
            return null;
        } else if (!remainder.isEmpty()) {
            return evaluatePath(current, remainder);
        }

        return extractPrimitive(current);
    }

    private static String extractPrimitive(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return element.getAsJsonPrimitive().getAsString();
        }
        return element.toString();
    }

    /**
     * Extracts a value using a Regular Expression with capture groups.
     */
    public static String extractRegex(String content, String regexPattern) {
        if (content == null || regexPattern == null || regexPattern.trim().isEmpty()) {
            return null;
        }

        try {
            Pattern pattern = Pattern.compile(regexPattern);
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                if (matcher.groupCount() >= 1) {
                    return matcher.group(1);
                }
                return matcher.group(0);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}

