package thorization.core.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import com.google.gson.*;
import thorization.model.substitution.SubstitutionDataType;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

/**
 * High-performance, resilient JSON body mutator built on Gson 2.11.0 AST.
 * Navigates JSONPaths (e.g. $.user.id, $.account.tenantId, items[0].id)
 * and mutates target values while strictly preserving primitive data types.
 */
public final class JsonBodyMutator {

    private static final Gson GSON = new GsonBuilder()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private JsonBodyMutator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Mutates a JSON request body on an HttpRequest.
     *
     * @param request     Original HttpRequest (can be null)
     * @param jsonPath    JSONPath expression (e.g. $.user.id, items[0].id)
     * @param replacement Replacement value as string
     * @param dataType    Data type handling override (AUTO, STRING, INTEGER, FLOAT, BOOLEAN)
     * @return New HttpRequest with mutated body, or original request if untouched/invalid
     */
    public static HttpRequest mutate(HttpRequest request, String jsonPath, String replacement, SubstitutionDataType dataType) {
        if (request == null || jsonPath == null || replacement == null) {
            return request;
        }

        String body = request.bodyToString();
        if (body == null || body.trim().isEmpty()) {
            return request;
        }

        String trimmed = body.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return request;
        }

        String newBody = substituteJson(body, jsonPath, replacement, dataType);
        if (newBody != null && !newBody.equals(body)) {
            HttpRequest updated = request.withBody(newBody);
            if (updated.hasHeader("Content-Length")) {
                int byteLength = newBody.getBytes(StandardCharsets.UTF_8).length;
                updated = updated.withUpdatedHeader("Content-Length", String.valueOf(byteLength));
            }
            return updated;
        }
        return request;
    }

    /**
     * Overload defaulting to SubstitutionDataType.AUTO.
     */
    public static HttpRequest mutate(HttpRequest request, String jsonPath, String replacement) {
        return mutate(request, jsonPath, replacement, SubstitutionDataType.AUTO);
    }

    /**
     * Mutates a JSON string standalone (for headless testing and pure string substitution).
     *
     * @param originalJson Original JSON string
     * @param jsonPath     JSONPath expression (e.g. $.user.id, items[0].id)
     * @param replacement  Replacement value as string
     * @param dataType     Data type handling mode
     * @return Mutated JSON string, or originalJson if path not found or on error
     */
    public static String substituteJson(String originalJson, String jsonPath, String replacement, SubstitutionDataType dataType) {
        if (originalJson == null || jsonPath == null || replacement == null) {
            return originalJson;
        }

        String trimmedJson = originalJson.trim();
        if (!trimmedJson.startsWith("{") && !trimmedJson.startsWith("[")) {
            return originalJson;
        }

        try {
            JsonElement root = JsonParser.parseString(trimmedJson);
            String cleanPath = jsonPath.trim();

            // Strip leading "$" or "$."
            if (cleanPath.startsWith("$.")) {
                cleanPath = cleanPath.substring(2);
            } else if (cleanPath.startsWith("$")) {
                cleanPath = cleanPath.substring(1);
            }

            if (cleanPath.isEmpty()) {
                return originalJson;
            }

            SubstitutionDataType effectiveType = dataType != null ? dataType : SubstitutionDataType.AUTO;
            boolean mutated = mutateElement(root, cleanPath, replacement, effectiveType);

            return mutated ? GSON.toJson(root) : originalJson;
        } catch (Exception e) {
            // Fail-safe: Malformed JSON, syntax error, or unparseable input returns original unmodified
            return originalJson;
        }
    }

    /**
     * Overload defaulting to SubstitutionDataType.AUTO.
     */
    public static String substituteJson(String originalJson, String jsonPath, String replacement) {
        return substituteJson(originalJson, jsonPath, replacement, SubstitutionDataType.AUTO);
    }

    /**
     * Recursive AST navigator and in-place mutator.
     */
    private static boolean mutateElement(JsonElement current, String path, String replacement, SubstitutionDataType dataType) {
        if (current == null || current.isJsonNull() || path == null || path.isEmpty()) {
            return false;
        }

        // 1. Check for array index accessor at the start of the path: e.g. [0] or [*]
        if (path.startsWith("[")) {
            int closeBracket = path.indexOf(']');
            if (closeBracket <= 1) {
                return false;
            }

            String indexToken = path.substring(1, closeBracket).trim();
            String remainder = path.substring(closeBracket + 1);
            if (remainder.startsWith(".")) {
                remainder = remainder.substring(1);
            }

            if (!current.isJsonArray()) {
                return false;
            }

            JsonArray arr = current.getAsJsonArray();

            // Wildcard array index: [*]
            if ("*".equals(indexToken)) {
                boolean anyMutated = false;
                for (int i = 0; i < arr.size(); i++) {
                    if (remainder.isEmpty()) {
                        arr.set(i, createTypedPrimitive(arr.get(i), replacement, dataType));
                        anyMutated = true;
                    } else {
                        if (mutateElement(arr.get(i), remainder, replacement, dataType)) {
                            anyMutated = true;
                        }
                    }
                }
                return anyMutated;
            }

            // Numeric array index: [0], [1], etc.
            int index;
            try {
                index = Integer.parseInt(indexToken);
            } catch (NumberFormatException e) {
                return false;
            }

            if (index < 0 || index >= arr.size()) {
                return false;
            }

            if (remainder.isEmpty()) {
                arr.set(index, createTypedPrimitive(arr.get(index), replacement, dataType));
                return true;
            } else {
                return mutateElement(arr.get(index), remainder, replacement, dataType);
            }
        }

        // 2. Object property navigation: determine next delimiter ('.' or '[')
        int dotIndex = path.indexOf('.');
        int bracketIndex = path.indexOf('[');

        String segment;
        String remainder;

        if (dotIndex != -1 && (bracketIndex == -1 || dotIndex < bracketIndex)) {
            segment = path.substring(0, dotIndex);
            remainder = path.substring(dotIndex + 1);
        } else if (bracketIndex != -1 && (dotIndex == -1 || bracketIndex < dotIndex)) {
            segment = path.substring(0, bracketIndex);
            remainder = path.substring(bracketIndex);
        } else {
            segment = path;
            remainder = "";
        }

        if (segment.isEmpty()) {
            return false;
        }

        if (!current.isJsonObject()) {
            return false;
        }

        JsonObject obj = current.getAsJsonObject();
        if (!obj.has(segment)) {
            return false;
        }

        if (remainder.isEmpty()) {
            JsonElement existing = obj.get(segment);
            obj.add(segment, createTypedPrimitive(existing, replacement, dataType));
            return true;
        } else {
            return mutateElement(obj.get(segment), remainder, replacement, dataType);
        }
    }

    /**
     * Creates a typed JsonPrimitive with strict type preservation and explicit overrides.
     */
    private static JsonElement createTypedPrimitive(JsonElement existing, String replacement, SubstitutionDataType dataType) {
        String cleanReplacement = replacement != null ? replacement.trim() : "";

        // Explicit Type Overrides
        if (dataType == SubstitutionDataType.STRING) {
            return new JsonPrimitive(replacement);
        }

        if (dataType == SubstitutionDataType.INTEGER) {
            try {
                return new JsonPrimitive(Long.parseLong(cleanReplacement));
            } catch (NumberFormatException e) {
                try {
                    return new JsonPrimitive(new BigInteger(cleanReplacement));
                } catch (Exception e2) {
                    return new JsonPrimitive(replacement);
                }
            }
        }

        if (dataType == SubstitutionDataType.FLOAT) {
            try {
                return new JsonPrimitive(Double.parseDouble(cleanReplacement));
            } catch (NumberFormatException e) {
                try {
                    return new JsonPrimitive(new BigDecimal(cleanReplacement));
                } catch (Exception e2) {
                    return new JsonPrimitive(replacement);
                }
            }
        }

        if (dataType == SubstitutionDataType.BOOLEAN) {
            return new JsonPrimitive(Boolean.parseBoolean(cleanReplacement));
        }

        // AUTO Mode: Inspect existing primitive type to strictly preserve it
        if (existing != null && existing.isJsonPrimitive()) {
            JsonPrimitive prim = existing.getAsJsonPrimitive();

            if (prim.isNumber()) {
                try {
                    if (cleanReplacement.contains(".") || cleanReplacement.toLowerCase().contains("e")) {
                        return new JsonPrimitive(Double.parseDouble(cleanReplacement));
                    } else {
                        return new JsonPrimitive(Long.parseLong(cleanReplacement));
                    }
                } catch (NumberFormatException e) {
                    try {
                        return new JsonPrimitive(new BigInteger(cleanReplacement));
                    } catch (Exception ignored) {
                        // ignore and return string
                    }
                    return new JsonPrimitive(replacement);
                }
            }

            if (prim.isBoolean()) {
                return new JsonPrimitive(Boolean.parseBoolean(cleanReplacement));
            }

            if (prim.isString()) {
                return new JsonPrimitive(replacement);
            }
        }

        // AUTO Fallback when existing is null or missing: Infer Number -> Boolean -> String
        try {
            if (cleanReplacement.contains(".") || cleanReplacement.toLowerCase().contains("e")) {
                return new JsonPrimitive(Double.parseDouble(cleanReplacement));
            }
            return new JsonPrimitive(Long.parseLong(cleanReplacement));
        } catch (NumberFormatException ignored) {
            // not a number
        }

        if ("true".equalsIgnoreCase(cleanReplacement) || "false".equalsIgnoreCase(cleanReplacement)) {
            return new JsonPrimitive(Boolean.parseBoolean(cleanReplacement));
        }

        return new JsonPrimitive(replacement);
    }
}
