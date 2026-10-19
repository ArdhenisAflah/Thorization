package thorization.bridge.dto;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data Transfer Object for role creation and modification via AI Bridge API.
 * Supports both raw multiline string format and structured Map format for
 * headers and cookies, enabling seamless interaction for AI agents.
 */
public class RoleDto {

    @SerializedName(value = "id", alternate = {"roleId", "role_id"})
    private String id;

    @SerializedName(value = "name", alternate = {"roleName", "role_name"})
    private String name;

    @SerializedName(value = "headers", alternate = {"rawHeaders", "raw_headers"})
    private String headers;

    @SerializedName(value = "headersMap", alternate = {"headers_map"})
    private Map<String, String> headersMap;

    @SerializedName(value = "cookies", alternate = {"rawCookies", "raw_cookies"})
    private String cookies;

    @SerializedName(value = "cookiesMap", alternate = {"cookies_map"})
    private Map<String, String> cookiesMap;

    @SerializedName(value = "enabled", alternate = {"active", "isEnabled", "is_enabled"})
    private Boolean enabled;

    public RoleDto() {}

    public RoleDto(String id, String name, String headers, String cookies, Boolean enabled) {
        this.id = id;
        this.name = name;
        this.headers = headers;
        this.cookies = cookies;
        this.enabled = enabled;
        syncFromRaw();
    }

    /**
     * Resolves headers to raw string format (for UserRole engine).
     */
    public String getHeaders() {
        if (headers != null && !headers.trim().isEmpty()) {
            return headers;
        }
        if (headersMap != null && !headersMap.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : headersMap.entrySet()) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(e.getKey()).append(": ").append(e.getValue());
            }
            return sb.toString();
        }
        return headers != null ? headers : "";
    }

    public void setHeaders(String headers) {
        this.headers = headers;
        syncFromRaw();
    }

    /**
     * Resolves cookies to raw string format (for UserRole engine).
     */
    public String getCookies() {
        if (cookies != null && !cookies.trim().isEmpty()) {
            return cookies;
        }
        if (cookiesMap != null && !cookiesMap.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : cookiesMap.entrySet()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(e.getKey()).append("=").append(e.getValue());
            }
            return sb.toString();
        }
        return cookies != null ? cookies : "";
    }

    public void setCookies(String cookies) {
        this.cookies = cookies;
        syncFromRaw();
    }

    public Map<String, String> getHeadersMap() {
        if (headersMap == null) {
            syncFromRaw();
        }
        return headersMap;
    }

    public void setHeadersMap(Map<String, String> headersMap) {
        this.headersMap = headersMap;
    }

    public Map<String, String> getCookiesMap() {
        if (cookiesMap == null) {
            syncFromRaw();
        }
        return cookiesMap;
    }

    public void setCookiesMap(Map<String, String> cookiesMap) {
        this.cookiesMap = cookiesMap;
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

    public Boolean getEnabled() {
        return enabled == null || enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    private void syncFromRaw() {
        if (headers != null && !headers.trim().isEmpty()) {
            this.headersMap = new LinkedHashMap<>();
            for (String line : headers.split("\\r?\\n")) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    this.headersMap.put(line.substring(0, idx).trim(), line.substring(idx + 1).trim());
                }
            }
        }
        if (cookies != null && !cookies.trim().isEmpty()) {
            this.cookiesMap = new LinkedHashMap<>();
            for (String part : cookies.split(";")) {
                int idx = part.indexOf('=');
                if (idx > 0) {
                    this.cookiesMap.put(part.substring(0, idx).trim(), part.substring(idx + 1).trim());
                }
            }
        }
    }
}
