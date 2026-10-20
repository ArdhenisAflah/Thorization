package thorization.core.substitution;

import burp.api.montoya.http.message.requests.HttpRequest;
import thorization.model.UserRole;
import thorization.model.substitution.ParameterSubstitutionRule;
import thorization.model.substitution.SubstitutionDataType;
import thorization.model.substitution.SubstitutionTargetType;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * High-performance, stateless parameter substitution engine that evaluates
 * host and path scoping rules, resolves role values via a 4-tier hierarchy,
 * and dispatches modifications to the appropriate mutator.
 */
public class ParameterSubstitutionEngine {

    /**
     * Applies all in-scope substitution rules to the given HTTP request for the specified role.
     *
     * @param original The HTTP request before substitution.
     * @param role     The target user role being replayed (null for unauthenticated testing).
     * @param rules    The configured substitution rules.
     * @return The modified HTTP request with substitutions applied.
     */
    public HttpRequest applySubstitutions(HttpRequest original, UserRole role, List<ParameterSubstitutionRule> rules) {
        if (original == null || rules == null || rules.isEmpty()) {
            return original;
        }

        HttpRequest current = original;
        for (ParameterSubstitutionRule rule : rules) {
            try {
                if (!isRuleInScope(rule, current)) {
                    continue;
                }

                String replacementValue = resolveReplacementValue(rule, role);
                if (replacementValue == null || replacementValue.isEmpty()) {
                    continue;
                }

                current = applyRule(current, rule, replacementValue);
            } catch (Exception ignored) {
                // Fail-safe: continue to next rule without breaking audit pipeline
            }
        }

        return current;
    }

    /**
     * Evaluates whether a rule is enabled and whether the request matches both Host and Path scoping.
     */
    public boolean isRuleInScope(ParameterSubstitutionRule rule, HttpRequest request) {
        if (rule == null || !rule.isEnabled() || request == null) {
            return false;
        }

        // 1. Host Scoping Evaluation
        String host = extractHost(request);
        if (!matchesHost(host, rule.getHostPattern())) {
            return false;
        }

        // 2. Path Scoping Evaluation
        String path = extractPath(request);
        if (!matchesPath(path, rule.getPathPattern())) {
            return false;
        }

        return true;
    }

    /**
     * Resolves the replacement parameter value using the 4-tier resolution hierarchy.
     */
    public String resolveReplacementValue(ParameterSubstitutionRule rule, UserRole role) {
        if (rule == null) {
            return null;
        }

        Map<String, String> ruleRoleValues = rule.getRoleValues();

        // Tier 1: Check Rule's Role-Specific Mapping
        if (role != null) {
            if (ruleRoleValues != null && !ruleRoleValues.isEmpty()) {
                // 1a. Match by exact Role ID
                String valById = ruleRoleValues.get(role.getId());
                if (valById != null && !valById.trim().isEmpty()) {
                    return valById.trim();
                }

                // 1b. Match by Role Name (exact)
                String valByName = ruleRoleValues.get(role.getName());
                if (valByName != null && !valByName.trim().isEmpty()) {
                    return valByName.trim();
                }

                // 1c. Match by Role Name (case-insensitive)
                for (Map.Entry<String, String> entry : ruleRoleValues.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(role.getName())) {
                        String v = entry.getValue();
                        if (v != null && !v.trim().isEmpty()) {
                            return v.trim();
                        }
                    }
                }
            }

            // Tier 2: Check Role's Internal Parameter Map
            String paramKey = rule.getParameterKey();
            if (paramKey != null && !paramKey.trim().isEmpty()) {
                String cleanKey = paramKey.replace("{", "").replace("}", "").trim();
                String roleParamVal = role.getParameterValue(paramKey);
                if (roleParamVal == null || roleParamVal.trim().isEmpty()) {
                    roleParamVal = role.getParameterValue(cleanKey);
                }
                if (roleParamVal != null && !roleParamVal.trim().isEmpty()) {
                    return roleParamVal.trim();
                }
            }
        } else {
            // Unauthenticated Testing: check unauthenticated pseudo-role keys
            if (ruleRoleValues != null && !ruleRoleValues.isEmpty()) {
                for (String unauthKey : List.of("unauthenticated", "UNAUTHENTICATED", "anonymous", "ANONYMOUS", "none")) {
                    String val = ruleRoleValues.get(unauthKey);
                    if (val != null && !val.trim().isEmpty()) {
                        return val.trim();
                    }
                }
            }
        }

        // Tier 3: Rule Default Fallback Value
        if (rule.getDefaultValue() != null && !rule.getDefaultValue().trim().isEmpty()) {
            return rule.getDefaultValue().trim();
        }

        // Tier 4: No value resolved
        return null;
    }

    /**
     * Dispatches request modification to the appropriate mutator based on rule target type.
     */
    public HttpRequest applyRule(HttpRequest request, ParameterSubstitutionRule rule, String replacementValue) {
        if (request == null || rule == null || replacementValue == null) {
            return request;
        }

        SubstitutionTargetType targetType = rule.getTargetType();
        SubstitutionDataType dataType = rule.getDataType() != null ? rule.getDataType() : SubstitutionDataType.AUTO;

        switch (targetType) {
            case REST_PATH:
                return RestPathMutator.mutate(request, rule.getPathPattern(), rule.getParameterKey(), replacementValue);
            case URL_QUERY:
                return QueryParamMutator.mutate(request, rule.getParameterKey(), replacementValue);
            case JSON_BODY:
                return JsonBodyMutator.mutate(request, rule.getParameterKey(), replacementValue, dataType);
            case FORM_BODY:
                return FormBodyMutator.mutate(request, rule.getParameterKey(), replacementValue);
            default:
                return request;
        }
    }

    // --- Scoping Matchers ---

    public boolean matchesHost(String host, String pattern) {
        if (pattern == null || pattern.trim().isEmpty() || "*".equals(pattern.trim())) {
            return true;
        }
        if (host == null || host.trim().isEmpty()) {
            return false;
        }

        String cleanHost = host.trim();
        int colonIdx = cleanHost.indexOf(':');
        if (colonIdx > 0) {
            cleanHost = cleanHost.substring(0, colonIdx).trim();
        }

        String cleanPattern = pattern.trim();
        // 1. Direct case-insensitive equality
        if (cleanHost.equalsIgnoreCase(cleanPattern)) {
            return true;
        }

        // 2. Regular Expression
        if (isRegex(cleanPattern)) {
            try {
                return Pattern.compile(cleanPattern, Pattern.CASE_INSENSITIVE).matcher(cleanHost).find();
            } catch (PatternSyntaxException ignored) {
                return false;
            }
        }

        // 3. Glob wildcard conversion (e.g. *.target.com, api.*.com, *target*)
        if (isGlob(cleanPattern)) {
            try {
                String regex = globToRegex(cleanPattern);
                return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(cleanHost).matches();
            } catch (PatternSyntaxException ignored) {
                // fall through
            }
        }

        // 4. Regular Expression fallback
        try {
            return Pattern.compile(cleanPattern, Pattern.CASE_INSENSITIVE).matcher(cleanHost).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    public boolean matchesPath(String path, String pattern) {
        if (pattern == null || pattern.trim().isEmpty() || "*".equals(pattern.trim())) {
            return true;
        }
        if (path == null) {
            return false;
        }

        String cleanPath = path.trim();
        int qIdx = cleanPath.indexOf('?');
        if (qIdx >= 0) {
            cleanPath = cleanPath.substring(0, qIdx);
        }

        String cleanPattern = pattern.trim();
        // 1. Exact path equality
        if (cleanPath.equals(cleanPattern)) {
            return true;
        }

        // 2. REST Template Pattern (e.g. /api/users/{id}/profile or /api/users/{id}/*)
        if (cleanPattern.contains("{") && cleanPattern.contains("}")) {
            try {
                String templateRegex = templateToRegex(cleanPattern);
                return Pattern.compile(templateRegex).matcher(cleanPath).matches();
            } catch (PatternSyntaxException ignored) {
                // fall through
            }
        }

        // 3. Regular Expression
        if (isRegex(cleanPattern)) {
            try {
                return Pattern.compile(cleanPattern).matcher(cleanPath).find();
            } catch (PatternSyntaxException ignored) {
                return false;
            }
        }

        // 4. Glob Pattern (e.g. /api/v1/*)
        if (isGlob(cleanPattern)) {
            try {
                String regex = globToRegex(cleanPattern);
                return Pattern.compile(regex).matcher(cleanPath).matches();
            } catch (PatternSyntaxException ignored) {
                // fall through
            }
        }

        // 5. Regular Expression fallback
        try {
            return Pattern.compile(cleanPattern).matcher(cleanPath).find();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    // --- Helper Utilities ---

    public static String extractHost(HttpRequest request) {
        if (request == null) {
            return "";
        }
        if (request.httpService() != null && request.httpService().host() != null && !request.httpService().host().isEmpty()) {
            return request.httpService().host();
        }
        String hostHeader = request.headerValue("Host");
        if (hostHeader != null && !hostHeader.trim().isEmpty()) {
            int colonIdx = hostHeader.indexOf(':');
            return colonIdx > 0 ? hostHeader.substring(0, colonIdx).trim() : hostHeader.trim();
        }
        if (request.url() != null) {
            try {
                URI uri = URI.create(request.url());
                if (uri.getHost() != null) {
                    return uri.getHost();
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        return "";
    }

    public static String extractPath(HttpRequest request) {
        if (request == null) {
            return "";
        }
        String path = request.pathWithoutQuery();
        if (path == null || path.isEmpty()) {
            path = request.path();
        }
        if (path != null && path.contains("?")) {
            path = path.substring(0, path.indexOf('?'));
        }
        return path != null ? path : "";
    }

    private static boolean isRegex(String pattern) {
        if (pattern == null) {
            return false;
        }
        return pattern.startsWith("^") || pattern.endsWith("$")
                || pattern.contains(".*") || pattern.contains(".+")
                || pattern.contains("\\d") || pattern.contains("\\w") || pattern.contains("\\s")
                || pattern.contains("[") || pattern.contains("(") || pattern.contains("|");
    }

    private static boolean isGlob(String pattern) {
        if (pattern == null || isRegex(pattern)) {
            return false;
        }
        return pattern.contains("*") || pattern.contains("?");
    }

    public static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append(".");
                    break;
                case '.':
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '+':
                case '^':
                case '$':
                case '|':
                case '\\':
                    sb.append('\\').append(c);
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        sb.append("$");
        return sb.toString();
    }

    public static String templateToRegex(String template) {
        StringBuilder sb = new StringBuilder("^");
        int len = template.length();
        int i = 0;
        while (i < len) {
            int openBrace = template.indexOf('{', i);
            if (openBrace == -1) {
                appendEscapedPathSegment(sb, template.substring(i));
                break;
            } else {
                appendEscapedPathSegment(sb, template.substring(i, openBrace));
                int closeBrace = template.indexOf('}', openBrace);
                if (closeBrace == -1) {
                    appendEscapedPathSegment(sb, template.substring(openBrace));
                    break;
                } else {
                    sb.append("([^/?#]+)");
                    i = closeBrace + 1;
                }
            }
        }
        sb.append("$");
        return sb.toString();
    }

    private static void appendEscapedPathSegment(StringBuilder sb, String segment) {
        for (int j = 0; j < segment.length(); j++) {
            char c = segment.charAt(j);
            if (c == '*') {
                sb.append(".*");
            } else if (c == '?') {
                sb.append(".");
            } else if ("\\.[]()+^$|".indexOf(c) != -1) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
    }
}
