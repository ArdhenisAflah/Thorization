package thorization.model.substitution;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Encapsulates a parameter substitution rule, including target type,
 * host/path scoping constraints, parameter key, data type, role-specific mappings, and default fallback.
 */
public class ParameterSubstitutionRule {

    private String id;
    private String name;
    private boolean enabled;
    private SubstitutionTargetType targetType;
    private String hostPattern;     // e.g. "api.target.com", "*.target.com", or "*"
    private String pathPattern;     // e.g. "/api/users/{id}/*", "/api/v1/orders/{orderId}", or "*"
    private String parameterKey;    // e.g. "id", "{id}", "userId", "$.user.id"
    private SubstitutionDataType dataType;
    private final Map<String, String> roleValues; // roleId or roleName -> replacement value
    private String defaultValue;    // fallback value if no role-specific value is found

    public ParameterSubstitutionRule() {
        this.id = UUID.randomUUID().toString();
        this.name = "New Substitution Rule";
        this.enabled = true;
        this.targetType = SubstitutionTargetType.REST_PATH;
        this.hostPattern = "*";
        this.pathPattern = "*";
        this.parameterKey = "id";
        this.dataType = SubstitutionDataType.AUTO;
        this.roleValues = new LinkedHashMap<>();
        this.defaultValue = "";
    }

    public ParameterSubstitutionRule(String name, SubstitutionTargetType targetType, String parameterKey) {
        this();
        this.name = name != null ? name : "New Substitution Rule";
        this.targetType = targetType != null ? targetType : SubstitutionTargetType.REST_PATH;
        this.parameterKey = parameterKey != null ? parameterKey : "id";
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id != null ? id : UUID.randomUUID().toString();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public SubstitutionTargetType getTargetType() {
        return targetType;
    }

    public void setTargetType(SubstitutionTargetType targetType) {
        this.targetType = targetType != null ? targetType : SubstitutionTargetType.REST_PATH;
    }

    public String getHostPattern() {
        return hostPattern;
    }

    public void setHostPattern(String hostPattern) {
        this.hostPattern = hostPattern != null ? hostPattern.trim() : "*";
    }

    public String getPathPattern() {
        return pathPattern;
    }

    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern != null ? pathPattern.trim() : "*";
    }

    public String getParameterKey() {
        return parameterKey;
    }

    public void setParameterKey(String parameterKey) {
        this.parameterKey = parameterKey != null ? parameterKey.trim() : "";
    }

    public SubstitutionDataType getDataType() {
        return dataType;
    }

    public void setDataType(SubstitutionDataType dataType) {
        this.dataType = dataType != null ? dataType : SubstitutionDataType.AUTO;
    }

    public synchronized Map<String, String> getRoleValues() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(roleValues));
    }

    public synchronized void setRoleValues(Map<String, String> values) {
        this.roleValues.clear();
        if (values != null) {
            this.roleValues.putAll(values);
        }
    }

    public synchronized void setRoleValue(String roleIdentifier, String value) {
        if (roleIdentifier != null && !roleIdentifier.trim().isEmpty()) {
            if (value != null) {
                this.roleValues.put(roleIdentifier.trim(), value.trim());
            } else {
                this.roleValues.remove(roleIdentifier.trim());
            }
        }
    }

    public synchronized String getRoleValue(String roleIdentifier) {
        if (roleIdentifier == null) {
            return null;
        }
        return this.roleValues.get(roleIdentifier.trim());
    }

    public synchronized boolean hasRoleValue(String roleIdentifier) {
        if (roleIdentifier == null) {
            return false;
        }
        return this.roleValues.containsKey(roleIdentifier.trim());
    }

    public synchronized void removeRoleValue(String roleIdentifier) {
        if (roleIdentifier != null) {
            this.roleValues.remove(roleIdentifier.trim());
        }
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue != null ? defaultValue.trim() : "";
    }

    @Override
    public String toString() {
        return name + " (" + targetType.getDisplayName() + ": " + parameterKey + ")" + (enabled ? " [Enabled]" : " [Disabled]");
    }
}
