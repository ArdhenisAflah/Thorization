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

    public UserRole() {
        this.id = UUID.randomUUID().toString();
        this.name = "New Role";
        this.enabled = true;
        this.rawHeaders = "";
        this.rawCookies = "";
        this.parsedHeaders = new LinkedHashMap<>();
    }

    public UserRole(String name, String rawHeaders, String rawCookies) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.enabled = true;
        this.rawHeaders = rawHeaders != null ? rawHeaders : "";
        this.rawCookies = rawCookies != null ? rawCookies : "";
        this.parsedHeaders = new LinkedHashMap<>();
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

    @Override
    public String toString() {
        return name + (enabled ? " [Active]" : " [Disabled]");
    }
}

