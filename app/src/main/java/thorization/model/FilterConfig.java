package thorization.model;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Encapsulates traffic filtering, scope rules, and safe drop control settings.
 */
public class FilterConfig {
    private boolean onlyInScope;
    private boolean ignoreStaticFiles;
    private boolean testUnauthenticated;
    private Set<String> targetMethods;
    private String urlIncludeRegex;
    private String urlExcludeRegex;

    // Safe Request Interception & Drop Control
    private InterceptionMode interceptionMode;
    private boolean dropAllRequests;
    private Set<String> stateChangingMethods;

    private static final Set<String> STATIC_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".js", ".css", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".svg",
            ".woff", ".woff2", ".ttf", ".eot", ".map", ".mp4", ".webp"
    ));

    public FilterConfig() {
        this.onlyInScope = false;
        this.ignoreStaticFiles = true;
        this.testUnauthenticated = true;
        this.targetMethods = new HashSet<>(Arrays.asList("GET", "POST", "PUT", "DELETE", "PATCH"));
        this.urlIncludeRegex = "";
        this.urlExcludeRegex = "";

        // Default to standard pass-through mode, with standard state-changing methods
        this.interceptionMode = InterceptionMode.PASS_THROUGH_AND_REPLAY;
        this.dropAllRequests = false;
        this.stateChangingMethods = new HashSet<>(Arrays.asList("POST", "PUT", "DELETE", "PATCH"));
    }

    public boolean isOnlyInScope() {
        return onlyInScope;
    }

    public void setOnlyInScope(boolean onlyInScope) {
        this.onlyInScope = onlyInScope;
    }

    public boolean isIgnoreStaticFiles() {
        return ignoreStaticFiles;
    }

    public void setIgnoreStaticFiles(boolean ignoreStaticFiles) {
        this.ignoreStaticFiles = ignoreStaticFiles;
    }

    public boolean isTestUnauthenticated() {
        return testUnauthenticated;
    }

    public void setTestUnauthenticated(boolean testUnauthenticated) {
        this.testUnauthenticated = testUnauthenticated;
    }

    public Set<String> getTargetMethods() {
        return targetMethods;
    }

    public void setTargetMethods(Set<String> targetMethods) {
        this.targetMethods = targetMethods;
    }

    public String getUrlIncludeRegex() {
        return urlIncludeRegex;
    }

    public void setUrlIncludeRegex(String urlIncludeRegex) {
        this.urlIncludeRegex = urlIncludeRegex;
    }

    public String getUrlExcludeRegex() {
        return urlExcludeRegex;
    }

    public void setUrlExcludeRegex(String urlExcludeRegex) {
        this.urlExcludeRegex = urlExcludeRegex;
    }

    public InterceptionMode getInterceptionMode() {
        return interceptionMode;
    }

    public void setInterceptionMode(InterceptionMode interceptionMode) {
        this.interceptionMode = interceptionMode != null ? interceptionMode : InterceptionMode.PASS_THROUGH_AND_REPLAY;
    }

    public boolean isDropAllRequests() {
        return dropAllRequests;
    }

    public void setDropAllRequests(boolean dropAllRequests) {
        this.dropAllRequests = dropAllRequests;
    }

    public Set<String> getStateChangingMethods() {
        return stateChangingMethods;
    }

    public void setStateChangingMethods(Set<String> stateChangingMethods) {
        this.stateChangingMethods = stateChangingMethods;
    }

    public boolean isStateChanging(String method) {
        if (method == null) return false;
        return stateChangingMethods != null && stateChangingMethods.contains(method.toUpperCase());
    }

    /**
     * Determines whether a request with the given HTTP method should be targeted
     * by the drop/hold interception rules.
     */
    public boolean isDropTarget(String method) {
        if (method == null) return false;
        if (dropAllRequests) {
            return true;
        }
        return isStateChanging(method);
    }

    public boolean isStaticUrl(String url) {
        if (url == null) return false;
        String cleanUrl = url.toLowerCase().split("\\?")[0].split("#")[0];
        for (String ext : STATIC_EXTENSIONS) {
            if (cleanUrl.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    public boolean matchesUrl(String url) {
        if (url == null) return false;

        if (ignoreStaticFiles && isStaticUrl(url)) {
            return false;
        }

        if (urlIncludeRegex != null && !urlIncludeRegex.trim().isEmpty()) {
            try {
                Pattern p = Pattern.compile(urlIncludeRegex.trim(), Pattern.CASE_INSENSITIVE);
                if (!p.matcher(url).find()) {
                    return false;
                }
            } catch (Exception ignored) {
                return false;
            }
        }

        if (urlExcludeRegex != null && !urlExcludeRegex.trim().isEmpty()) {
            try {
                Pattern p = Pattern.compile(urlExcludeRegex.trim(), Pattern.CASE_INSENSITIVE);
                if (p.matcher(url).find()) {
                    return false;
                }
            } catch (Exception ignored) {
            }
        }

        return true;
    }
}
