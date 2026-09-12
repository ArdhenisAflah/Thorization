package thorization.core.pipeline;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves and replaces {{VARIABLE_NAME}} dynamic placeholders across URL paths,
 * query parameters, headers, and request bodies.
 */
public class VariableResolver {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([a-zA-Z0-9_.-]+)\\}\\}");

    /**
     * Replaces all occurrences of {{VAR_NAME}} in the template with values from the variables map.
     */
    public static String resolve(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty() || variables == null || variables.isEmpty()) {
            return template != null ? template : "";
        }

        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            String varName = matcher.group(1);
            String replacement = getVariableValue(varName, variables);
            if (replacement != null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            } else {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(matcher.group(0)));
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String getVariableValue(String varName, Map<String, String> variables) {
        if (variables.containsKey(varName)) {
            return variables.get(varName);
        }
        // Case-insensitive fallback
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(varName)) {
                return entry.getValue();
            }
        }
        return null;
    }
}

