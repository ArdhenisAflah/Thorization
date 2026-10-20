package thorization.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Represents a simulated user role with custom headers, cookies, and tokens.
 */
public class UserRole {
    private String id;
    private String name;
    private boolean enabled;
    private String rawHeaders;
    private String rawCookies;
    private Map<String, String> parsedHeaders;
    private String rawParameters;
    private final Map<String, String> parameterValues;

    public UserRole() {
        this.id = UUID.randomUUID().toString();
        this.name = "New Role";
        this.enabled = true;
        this.rawHeaders = "";
        this.rawCookies = "";
        this.parsedHeaders = new LinkedHashMap<>();
        this.rawParameters = "";
        this.parameterValues = new LinkedHashMap<>();
    }

    public UserRole(String name, String rawHeaders, String rawCookies) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.enabled = true;
        this.rawHeaders = rawHeaders != null ? rawHeaders : "";
        this.rawCookies = rawCookies != null ? rawCookies : "";
        this.parsedHeaders = new LinkedHashMap<>();
        this.rawParameters = "";
        this.parameterValues = new LinkedHashMap<>();
        parseHeaders();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getRawHeaders() {
        return rawHeaders;
    }

    public void setRawHeaders(String rawHeaders) {
        this.rawHeaders = rawHeaders != null ? rawHeaders : "";
        parseHeaders();
    }

    public String getRawCookies() {
        return rawCookies;
    }

    public void setRawCookies(String rawCookies) {
        this.rawCookies = rawCookies != null ? rawCookies : "";
    }

    public Map<String, String> getParsedHeaders() {
        return Collections.unmodifiableMap(parsedHeaders);
    }

    public void parseHeaders() {
        parsedHeaders.clear();
        if (rawHeaders == null || rawHeaders.trim().isEmpty()) {
            return;
        }
        String[] lines = rawHeaders.split("\\r?\\n");
        for (String line : lines) {
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String headerName = line.substring(0, colonIndex).trim();
                String headerValue = line.substring(colonIndex + 1).trim();
                if (!headerName.isEmpty()) {
                    parsedHeaders.put(headerName, headerValue);
                }
            }
        }
    }

    public synchronized String getRawParameters() {
        return rawParameters;
    }

    public synchronized void setRawParameters(String rawParameters) {
        this.rawParameters = rawParameters != null ? rawParameters : "";
        parseParameters();
    }

    public synchronized Map<String, String> getParameterValues() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(parameterValues));
    }

    public synchronized String getParameterValue(String key) {
        if (key == null) {
            return null;
        }
        return parameterValues.get(key.trim());
    }

    public synchronized void setParameterValue(String key, String value) {
        if (key != null && !key.trim().isEmpty()) {
            if (value != null) {
                parameterValues.put(key.trim(), value.trim());
            } else {
                parameterValues.remove(key.trim());
            }
        }
    }

    public synchronized void parseParameters() {
        parameterValues.clear();
        if (rawParameters == null || rawParameters.trim().isEmpty()) {
            return;
        }
        String[] lines = rawParameters.split("\\r?\\n");
        for (String line : lines) {
            int eq = line.indexOf('=');
            int colon = line.indexOf(':');
            int sep = -1;
            if (eq > 0 && colon > 0) {
                sep = Math.min(eq, colon);
            } else if (eq > 0) {
                sep = eq;
            } else if (colon > 0) {
                sep = colon;
            }
            if (sep > 0) {
                String k = line.substring(0, sep).trim();
                String v = line.substring(sep + 1).trim();
                if (!k.isEmpty()) {
                    parameterValues.put(k, v);
                }
            }
        }
    }

    @Override
    public String toString() {
        return name + (enabled ? " [Active]" : " [Disabled]");
    }
}

