package thorization.bridge;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.message.HttpRequestResponse;
import burp.api.montoya.http.message.requests.HttpRequest;
import thorization.bridge.dto.AnalyzeRequestDto;
import thorization.bridge.dto.AnalyzeResponseDto;
import thorization.bridge.dto.AuditRecordSummaryDto;
import thorization.bridge.dto.RoleDto;
import thorization.bridge.dto.StatusResponseDto;
import thorization.core.EngineController;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.FilterConfig;
import thorization.model.OriginalAction;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.util.HeaderUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Service bridging AI agents (Google Antigravity, Claude, scripts) with Thorization's
 * engine, rules, roles, and audit records.
 */
public class AiBridgeController {

    private final EngineController engineController;
    private final ExecutorService backgroundExecutor;

    public AiBridgeController(EngineController engineController) {
        this.engineController = engineController;
        this.backgroundExecutor = Executors.newFixedThreadPool(2);
    }

    public StatusResponseDto getStatus() {
        StatusResponseDto dto = new StatusResponseDto();
        dto.setStatus("online");
        dto.setVersion("1.3.0");
        dto.setEngineEnabled(engineController.isEnabled());
        dto.setInterceptionMode(engineController.getInterceptionMode() != null ? engineController.getInterceptionMode().name() : "");
        dto.setDropAllRequests(engineController.getFilterConfig().isDropAllRequests());
        dto.setActiveRolesCount((int) engineController.getRoles().stream().filter(UserRole::isEnabled).count());
        dto.setTotalRecordsCount(engineController.getRecords().size());
        dto.setPipelineCount(engineController.getPipelines().size());

        FilterConfig fc = engineController.getFilterConfig();
        StatusResponseDto.FilterConfigSummary fcs = new StatusResponseDto.FilterConfigSummary(
                fc.isOnlyInScope(),
                fc.isIgnoreStaticFiles(),
                fc.isTestUnauthenticated(),
                fc.getTargetMethods(),
                fc.getStateChangingMethods(),
                fc.getUrlIncludeRegex(),
                fc.getUrlExcludeRegex()
        );
        dto.setFilterConfig(fcs);
        return dto;
    }

    // --- Role Management ---

    public List<RoleDto> getRoles() {
        return engineController.getRoles().stream()
                .map(this::toRoleDto)
                .collect(Collectors.toList());
    }

    public RoleDto getRole(String id) {
        if (id == null) return null;
        return engineController.getRoles().stream()
                .filter(r -> id.equals(r.getId()))
                .findFirst()
                .map(this::toRoleDto)
                .orElse(null);
    }

    public RoleDto addRole(RoleDto dto) {
        if (dto == null || dto.getName() == null || dto.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Role name is required");
        }
        UserRole role = new UserRole(dto.getName().trim(), dto.getHeaders(), dto.getCookies());
        if (dto.getId() != null && !dto.getId().trim().isEmpty()) {
            role.setId(dto.getId().trim());
        }
        if (dto.getEnabled() != null) {
            role.setEnabled(dto.getEnabled());
        }
        engineController.addRole(role);
        return toRoleDto(role);
    }

    public RoleDto updateRole(String id, RoleDto dto) {
        if (id == null) {
            throw new IllegalArgumentException("Role id is required");
        }
        UserRole existing = engineController.getRoles().stream()
                .filter(r -> id.equals(r.getId()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Role not found with ID: " + id));

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            existing.setName(dto.getName().trim());
        }
        String resolvedHeaders = dto.getHeaders();
        if (resolvedHeaders != null && !resolvedHeaders.trim().isEmpty()) {
            existing.setRawHeaders(resolvedHeaders);
        }
        String resolvedCookies = dto.getCookies();
        if (resolvedCookies != null && !resolvedCookies.trim().isEmpty()) {
            existing.setRawCookies(resolvedCookies);
        }
        if (dto.getEnabled() != null) {
            existing.setEnabled(dto.getEnabled());
        }
        engineController.updateRole(existing);
        return toRoleDto(existing);
    }

    public boolean deleteRole(String id) {
        if (id == null) return false;
        boolean exists = engineController.getRoles().stream().anyMatch(r -> id.equals(r.getId()));
        if (exists) {
            engineController.removeRole(id);
            return true;
        }
        return false;
    }

    // --- Audit Records ---

    public List<AuditRecordSummaryDto> getRecords() {
        return getRecords(null, null);
    }

    public List<AuditRecordSummaryDto> getRecords(Integer limit, Boolean vulnerableOnly) {
        return engineController.getRecords().stream()
                .map(this::toSummaryDto)
                .filter(dto -> vulnerableOnly == null || !vulnerableOnly || dto.isVulnerabilityDetected())
                .limit(limit != null && limit > 0 ? limit : Long.MAX_VALUE)
                .collect(Collectors.toList());
    }

    public AnalyzeResponseDto getRecordDetail(long id) {
        AuditRecord record = engineController.getRecords().stream()
                .filter(r -> r.getId() == id)
                .findFirst()
                .orElse(null);

        if (record == null) {
            return null;
        }
        return buildAnalyzeResponseFromRecord(record, "Record retrieved successfully", true);
    }

    // --- Request Analysis ---

    public AnalyzeResponseDto analyzeRequest(AnalyzeRequestDto requestDto) {
        if (requestDto == null) {
            AnalyzeResponseDto res = new AnalyzeResponseDto();
            res.setSuccess(false);
            res.setMessage("Request payload is empty.");
            return res;
        }

        String[] urlAndMethod = extractUrlAndMethod(requestDto);
        String url = urlAndMethod[0];
        String method = urlAndMethod[1];

        if (url == null || url.trim().isEmpty()) {
            AnalyzeResponseDto res = new AnalyzeResponseDto();
            res.setSuccess(false);
            res.setMessage("Either 'url' or 'rawRequest' with a valid Host header must be provided.");
            return res;
        }

        FilterConfig fc = engineController.getFilterConfig();
        MontoyaApi api = engineController.getApi();

        // 1. Validate rules unless explicitly bypassed
        if (!requestDto.isBypassFilter()) {
            if (!engineController.isEnabled()) {
                AnalyzeResponseDto res = new AnalyzeResponseDto();
                res.setSuccess(false);
                res.setFilteredOut(true);
                res.setFilterReason("Thorization engine is currently disabled in the extension.");
                res.setUrl(url);
                res.setMethod(method);
                res.setMessage("Request dropped by Thorization rules.");
                return res;
            }

            if (fc.getTargetMethods() != null && !fc.getTargetMethods().contains(method.toUpperCase())) {
                AnalyzeResponseDto res = new AnalyzeResponseDto();
                res.setSuccess(false);
                res.setFilteredOut(true);
                res.setFilterReason("HTTP method '" + method + "' is not in target methods: " + fc.getTargetMethods());
                res.setUrl(url);
                res.setMethod(method);
                res.setMessage("Request filtered out by method rules.");
                return res;
            }

            if (fc.isOnlyInScope() && api != null && api.scope() != null) {
                if (!api.scope().isInScope(url)) {
                    AnalyzeResponseDto res = new AnalyzeResponseDto();
                    res.setSuccess(false);
                    res.setFilteredOut(true);
                    res.setFilterReason("URL '" + url + "' is not in Burp Suite target scope.");
                    res.setUrl(url);
                    res.setMethod(method);
                    res.setMessage("Request filtered out by scope rules.");
                    return res;
                }
            }

            if (fc.isIgnoreStaticFiles() && fc.isStaticUrl(url)) {
                AnalyzeResponseDto res = new AnalyzeResponseDto();
                res.setSuccess(false);
                res.setFilteredOut(true);
                res.setFilterReason("URL matches static file extension pattern.");
                res.setUrl(url);
                res.setMethod(method);
                res.setMessage("Request filtered out by static file rules.");
                return res;
            }

            if (!fc.matchesUrl(url)) {
                AnalyzeResponseDto res = new AnalyzeResponseDto();
                res.setSuccess(false);
                res.setFilteredOut(true);
                res.setFilterReason("URL does not match include/exclude regex filters.");
                res.setUrl(url);
                res.setMethod(method);
                res.setMessage("Request filtered out by regex rules.");
                return res;
            }
        }

        long recordId = engineController.nextRecordId();

        if (requestDto.isSync()) {
            return executeAnalysis(requestDto, url, method, recordId);
        } else {
            AnalyzeResponseDto asyncResponse = new AnalyzeResponseDto();
            asyncResponse.setSuccess(true);
            asyncResponse.setRecordId(recordId);
            asyncResponse.setUrl(url);
            asyncResponse.setMethod(method);
            asyncResponse.setMessage("Analysis task scheduled in background.");

            backgroundExecutor.submit(() -> executeAnalysis(requestDto, url, method, recordId));
            return asyncResponse;
        }
    }

    private AnalyzeResponseDto executeAnalysis(AnalyzeRequestDto requestDto, String url, String method, long recordId) {
        MontoyaApi api = engineController.getApi();
        HttpRequest httpRequest = null;
        HttpRequestResponse originalTx = null;

        if (api != null && api.http() != null) {
            try {
                httpRequest = buildHttpRequest(requestDto, url, method);
                originalTx = api.http().sendRequest(httpRequest);
            } catch (Exception e) {
                if (api.logging() != null) {
                    api.logging().logToError("AI Bridge base request error: " + e.getMessage());
                }
            }
        }

        AuditRecord record = new AuditRecord(recordId, originalTx, OriginalAction.PASSED, requestDto.getSourceTool());
        if (httpRequest != null) {
            record.setOriginalRequest(httpRequest);
        }
        record.setMethod(method);
        record.setUrl(url);
        record.setPath(extractPath(url));

        if (originalTx != null && originalTx.response() != null) {
            record.setOriginalStatusCode(originalTx.response().statusCode());
            record.setOriginalResponseLength(originalTx.response().body() != null ? originalTx.response().body().length() : 0);
        } else {
            record.setOriginalStatusCode(200);
            record.setOriginalResponseLength(0);
        }

        engineController.addRecord(record);

        boolean testUnauth = requestDto.getTestUnauthenticated() != null
                ? requestDto.getTestUnauthenticated()
                : engineController.getFilterConfig().isTestUnauthenticated();

        List<UserRole> activeRoles = new ArrayList<>(engineController.getRoles());
        if (requestDto.getTargetRoleIds() != null && !requestDto.getTargetRoleIds().isEmpty()) {
            List<String> targetIds = requestDto.getTargetRoleIds();
            activeRoles = activeRoles.stream()
                    .filter(r -> targetIds.contains(r.getId()) || targetIds.contains(r.getName()))
                    .collect(Collectors.toList());
        }

        if (engineController.getReplayEngine() != null) {
            engineController.getReplayEngine().executeAudit(record, activeRoles, testUnauth);
        }

        // Offline / testing fallback verdicts when running outside live Burp network
        if (api == null || api.http() == null) {
            for (UserRole r : activeRoles) {
                if (r.isEnabled() && !record.getRoleResults().containsKey(r.getId())) {
                    record.addRoleResult(r.getId(), new AuthorizationCheckResult(
                            r.getId(),
                            r.getName(),
                            Verdict.ENFORCED,
                            403,
                            0,
                            0.0,
                            10,
                            "Evaluated (Simulated/Offline)",
                            null,
                            null
                    ));
                }
            }
            if (testUnauth && record.getUnauthenticatedResult() == null) {
                record.setUnauthenticatedResult(new AuthorizationCheckResult(
                        "unauthenticated",
                        "Unauthenticated",
                        Verdict.ENFORCED,
                        401,
                        0,
                        0.0,
                        5,
                        "Evaluated (Simulated/Offline)",
                        null,
                        null
                ));
            }
        }

        engineController.notifyRecordChange(record);

        return buildAnalyzeResponseFromRecord(record, "Analysis completed successfully.", true);
    }

    private String[] extractUrlAndMethod(AnalyzeRequestDto dto) {
        String url = dto.getUrl();
        String method = dto.getMethod();

        if (dto.getRawRequest() != null && !dto.getRawRequest().trim().isEmpty()) {
            String raw = dto.getRawRequest().trim();
            String[] lines = raw.split("\\r?\\n");
            if (lines.length > 0) {
                String[] tokens = lines[0].split("\\s+");
                if (tokens.length > 0 && (method == null || "GET".equals(method))) {
                    method = tokens[0].trim();
                }
                String path = tokens.length > 1 ? tokens[1].trim() : "/";
                String host = null;
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.toLowerCase().startsWith("host:")) {
                        host = line.substring(5).trim();
                        break;
                    }
                }
                if (url == null || url.trim().isEmpty()) {
                    if (host != null) {
                        String scheme = host.contains("localhost") || host.contains("127.0.0.1") || host.endsWith(".local") ? "http://" : "https://";
                        url = scheme + host + (path.startsWith("/") ? path : "/" + path);
                    }
                }
            }
        }

        if (method == null || method.trim().isEmpty()) {
            method = "GET";
        }
        return new String[]{url, method.toUpperCase()};
    }

    private String extractPath(String url) {
        if (url == null) return "/";
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();
            return path != null && !path.isEmpty() ? path : "/";
        } catch (Exception e) {
            return "/";
        }
    }

    private HttpRequest buildHttpRequest(AnalyzeRequestDto dto, String resolvedUrl, String resolvedMethod) {
        String raw = dto.getRawRequest();
        if (raw != null && !raw.trim().isEmpty()) {
            return parseRawHttpRequest(raw, dto, resolvedUrl, resolvedMethod);
        }

        HttpRequest req = HttpRequest.httpRequestFromUrl(resolvedUrl.trim()).withMethod(resolvedMethod);

        if (dto.getHeaders() != null) {
            for (Map.Entry<String, String> entry : dto.getHeaders().entrySet()) {
                req = HeaderUtils.setHeader(req, entry.getKey(), entry.getValue());
            }
        }

        if (dto.getRawHeaders() != null && !dto.getRawHeaders().trim().isEmpty()) {
            for (String line : dto.getRawHeaders().split("\\r?\\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty() && trimmed.contains(":")) {
                    String[] parts = trimmed.split(":", 2);
                    req = HeaderUtils.setHeader(req, parts[0].trim(), parts[1].trim());
                }
            }
        }

        if (dto.getBody() != null && !dto.getBody().isEmpty()) {
            req = req.withBody(dto.getBody());
        }

        return req;
    }

    private HttpRequest parseRawHttpRequest(String raw, AnalyzeRequestDto dto, String resolvedUrl, String resolvedMethod) {
        String[] parts = raw.split("(\\r?\\n){2}", 2);
        String headerSection = parts[0];
        String body = parts.length > 1 ? parts[1] : (dto.getBody() != null ? dto.getBody() : "");

        String[] headerLines = headerSection.split("\\r?\\n");
        Map<String, String> parsedHeaders = new LinkedHashMap<>();

        for (int i = 1; i < headerLines.length; i++) {
            String line = headerLines[i].trim();
            if (line.contains(":")) {
                String[] hParts = line.split(":", 2);
                String name = hParts[0].trim();
                String value = hParts[1].trim();
                if (!"Host".equalsIgnoreCase(name)) {
                    parsedHeaders.put(name, value);
                }
            }
        }

        HttpRequest req = HttpRequest.httpRequestFromUrl(resolvedUrl).withMethod(resolvedMethod);

        for (Map.Entry<String, String> entry : parsedHeaders.entrySet()) {
            req = HeaderUtils.setHeader(req, entry.getKey(), entry.getValue());
        }

        if (dto.getHeaders() != null) {
            for (Map.Entry<String, String> entry : dto.getHeaders().entrySet()) {
                req = HeaderUtils.setHeader(req, entry.getKey(), entry.getValue());
            }
        }

        if (body != null && !body.isEmpty()) {
            req = req.withBody(body);
        }

        return req;
    }

    private AnalyzeResponseDto buildAnalyzeResponseFromRecord(AuditRecord record, String message, boolean success) {
        AnalyzeResponseDto res = new AnalyzeResponseDto();
        res.setSuccess(success);
        res.setMessage(message);
        res.setRecordId(record.getId());
        res.setTimestamp(System.currentTimeMillis());
        res.setUrl(record.getUrl());
        res.setMethod(record.getMethod());
        res.setOriginalStatusCode(record.getOriginalStatusCode());
        res.setOriginalResponseLength(record.getOriginalResponseLength());
        res.setFilteredOut(false);

        boolean vulnDetected = false;
        List<String> vulnRoles = new ArrayList<>();

        if (record.getRoleResults() != null) {
            for (Map.Entry<String, AuthorizationCheckResult> entry : record.getRoleResults().entrySet()) {
                AuthorizationCheckResult r = entry.getValue();
                AnalyzeResponseDto.RoleVerdictDto vDto = new AnalyzeResponseDto.RoleVerdictDto(
                        r.getRoleId(),
                        r.getRoleName(),
                        r.getVerdict() != null ? r.getVerdict().name() : "INDETERMINABLE",
                        r.getStatusCode(),
                        r.getResponseLength(),
                        r.getSimilarityScore(),
                        r.getExecutionTimeMs(),
                        r.getNotes()
                );
                String key = r.getRoleName() != null && !r.getRoleName().trim().isEmpty() ? r.getRoleName() : entry.getKey();
                res.addRoleVerdict(key, vDto);

                if (r.getVerdict() == Verdict.BYPASS) {
                    vulnDetected = true;
                    vulnRoles.add(key);
                }
            }
        }

        if (record.getUnauthenticatedResult() != null) {
            AuthorizationCheckResult u = record.getUnauthenticatedResult();
            AnalyzeResponseDto.RoleVerdictDto uDto = new AnalyzeResponseDto.RoleVerdictDto(
                    "unauthenticated",
                    "Unauthenticated",
                    u.getVerdict() != null ? u.getVerdict().name() : "INDETERMINABLE",
                    u.getStatusCode(),
                    u.getResponseLength(),
                    u.getSimilarityScore(),
                    u.getExecutionTimeMs(),
                    u.getNotes()
            );
            res.addRoleVerdict("unauthenticated", uDto);

            if (u.getVerdict() == Verdict.BYPASS) {
                vulnDetected = true;
                vulnRoles.add("unauthenticated");
            }
        }

        res.setVulnerabilityDetected(vulnDetected);
        res.setVulnerableRoles(vulnRoles);

        if (vulnDetected) {
            res.setSummary("VULNERABILITY DETECTED: Authorization bypass identified for role(s): " + vulnRoles + ". Access control failed.");
        } else if (!res.getRoleVerdicts().isEmpty()) {
            boolean allEnforced = res.getRoleVerdicts().values().stream()
                    .allMatch(v -> "ENFORCED".equals(v.getVerdict()));
            if (allEnforced) {
                res.setSummary("PASSED: Access control successfully ENFORCED across all evaluated roles.");
            } else {
                res.setSummary("MANUAL REVIEW: Some verdicts require manual inspection (indeterminate match).");
            }
        } else {
            res.setSummary("No roles were evaluated for this request.");
        }

        return res;
    }

    private RoleDto toRoleDto(UserRole role) {
        return new RoleDto(
                role.getId(),
                role.getName(),
                role.getRawHeaders(),
                role.getRawCookies(),
                role.isEnabled()
        );
    }

    private AuditRecordSummaryDto toSummaryDto(AuditRecord r) {
        AuditRecordSummaryDto dto = new AuditRecordSummaryDto();
        dto.setId(r.getId());
        dto.setMethod(r.getMethod());
        dto.setUrl(r.getUrl());
        dto.setPath(r.getPath());
        dto.setSourceTool(r.getSourceTool());
        dto.setAction(r.getOriginalAction() != null ? r.getOriginalAction().name() : OriginalAction.PASSED.name());
        dto.setOriginalStatus(r.getOriginalStatusCode());
        dto.setOriginalLength(r.getOriginalResponseLength());

        boolean vulnDetected = false;
        List<String> vulnRoles = new ArrayList<>();
        Map<String, String> verdictMap = new LinkedHashMap<>();

        if (r.getRoleResults() != null) {
            for (Map.Entry<String, AuthorizationCheckResult> entry : r.getRoleResults().entrySet()) {
                String key = entry.getValue().getRoleName() != null && !entry.getValue().getRoleName().trim().isEmpty()
                        ? entry.getValue().getRoleName()
                        : entry.getKey();
                Verdict v = entry.getValue().getVerdict();
                String vName = v != null ? v.name() : "INDETERMINABLE";
                verdictMap.put(key, vName);
                if (v == Verdict.BYPASS) {
                    vulnDetected = true;
                    vulnRoles.add(key);
                }
            }
        }
        if (r.getUnauthenticatedResult() != null) {
            Verdict uVerdict = r.getUnauthenticatedResult().getVerdict();
            String uName = uVerdict != null ? uVerdict.name() : "INDETERMINABLE";
            verdictMap.put("unauthenticated", uName);
            if (uVerdict == Verdict.BYPASS) {
                vulnDetected = true;
                vulnRoles.add("unauthenticated");
            }
        }

        dto.setVerdicts(verdictMap);
        dto.setVulnerabilityDetected(vulnDetected);
        dto.setVulnerableRoles(vulnRoles);
        return dto;
    }

    public void shutdown() {
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdownNow();
        }
    }
}
