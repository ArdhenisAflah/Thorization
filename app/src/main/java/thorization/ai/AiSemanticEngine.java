package thorization.ai;

import burp.api.montoya.http.message.HttpHeader;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import thorization.core.EngineController;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.util.DiffUtils;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Orchestrates AI Semantic Analysis for authorization differentials, prompt assembly,
 * response parsing, SHA-256 caching, and asynchronous execution with EDT UI updates.
 */
public class AiSemanticEngine {

    private static final int MAX_BODY_CHARS = 3500;
    private static final int HEAD_TRUNCATE_CHARS = 3000;
    private static final int TAIL_TRUNCATE_CHARS = 500;

    private static final Set<String> SECURITY_HEADERS = Set.of(
            "content-type", "location", "content-length", "www-authenticate",
            "access-control-allow-origin", "x-frame-options", "set-cookie"
    );

    private final EngineController engineController;
    private volatile AiConfig config;
    private final AiEvaluationCache cache;
    private final OpenAiClient client;
    private final ExecutorService aiExecutor;

    public AiSemanticEngine(EngineController engineController, AiConfig config) {
        this(engineController, config, new AiEvaluationCache(), new OpenAiClient());
    }

    public AiSemanticEngine(EngineController engineController, AiConfig config, AiEvaluationCache cache, OpenAiClient client) {
        this.engineController = engineController;
        this.config = config != null ? config : new AiConfig();
        this.cache = Objects.requireNonNull(cache, "cache cannot be null");
        this.client = Objects.requireNonNull(client, "client cannot be null");
        this.aiExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public AiConfig getConfig() {
        return config;
    }

    public void setConfig(AiConfig config) {
        this.config = config != null ? config : new AiConfig();
    }

    public AiEvaluationCache getCache() {
        return cache;
    }

    public OpenAiClient getClient() {
        return client;
    }

    public void shutdown() {
        aiExecutor.shutdown();
        try {
            if (!aiExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                aiExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            aiExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds the authoritative system prompt instructing the model on its AppSec persona,
     * verdict classifications, and required JSON schema output.
     */
    public static String buildSystemPrompt() {
        return """
                You are an elite Application Security Auditor and Automated Access Control Validation Engine embedded in Thorization (a Burp Suite extension).
                Your task is to analyze HTTP request/response differentials between an authorized baseline user and a tested role (e.g., lower-privileged user, cross-tenant attacker, or unauthenticated user).
                You evaluate Broken Object Level Authorization (BOLA/IDOR), Broken Function Level Authorization (BFLA), and Privilege Escalation.

                ### VERDICT DEFINITIONS:
                1. "VALIDATED_ACCESS":
                   The tested role successfully accessed private, sensitive, or privileged data, or successfully performed a state-changing operation belonging to another user/tenant.
                   Characteristics:
                   - HTTP 200 OK with response content revealing private tenant/user data, object IDs, sensitive records, or administrative settings.
                   - High similarity to the authorized baseline response without access denial or tenant-filtered empty results.
                   - Successful write/update/delete operation executed without permission.

                2. "ACCESS_DENIED":
                   The application correctly enforced access control boundaries and blocked the request.
                   Characteristics:
                   - HTTP 401 Unauthorized, 403 Forbidden, 404 Not Found (object masked to prevent IDOR enumeration).
                   - HTTP 302/303 redirect to login or authentication challenge.
                   - HTTP 200 OK returning an application-level error payload (e.g., {"error": "Unauthorized"}, "Permission Denied", or empty collection {"items": []} due to tenant isolation).

                3. "INCONCLUSIVE":
                   The differential is ambiguous or insufficient to confirm whether access was granted or denied.
                   Characteristics:
                   - Server crash (HTTP 500, 502, 503).
                   - Public non-sensitive resource (e.g., public login page, public CSS/JS, health check) where identical responses are expected.
                   - Significant structural response variation without clear evidence of sensitive data exposure or authorization denial.

                ### OUTPUT FORMAT:
                You MUST respond with a single, raw, valid JSON object strictly adhering to this schema:
                {
                  "verdict": "VALIDATED_ACCESS" | "ACCESS_DENIED" | "INCONCLUSIVE",
                  "confidence": <number between 0.0 and 1.0>,
                  "reasoning": "<concise 1-2 sentence actionable executive summary>",
                  "differential_analysis": "<detailed explanation comparing status codes, headers, and body contents>",
                  "remediation_advice": "<specific remediation advice for developers>"
                }

                CRITICAL RULES:
                - Do NOT output markdown code blocks (no ```json or ```).
                - Do NOT include conversational greetings, explanations, or trailing commentary.
                - Output ONLY the JSON string.
                """;
    }

    /**
     * Constructs the rich contextual user prompt contrasting original vs role replay.
     */
    public static String buildUserPrompt(AuditRecord record, AuthorizationCheckResult roleResult) {
        StringBuilder sb = new StringBuilder();

        String method = record != null && record.getMethod() != null ? record.getMethod() : "GET";
        String url = record != null && record.getUrl() != null ? record.getUrl() : "";
        String path = record != null && record.getPath() != null ? record.getPath() : "";
        boolean isStateChanging = record != null && record.isStateChanging();

        // 1. Target Endpoint & Parameters
        sb.append("# ACCESS CONTROL DIFFERENTIAL AUDIT\n\n");
        sb.append("## 1. ENDPOINT TARGET\n");
        sb.append("- HTTP Method: ").append(method).append("\n");
        sb.append("- Target URL: ").append(url).append("\n");
        sb.append("- Path: ").append(path).append("\n");
        sb.append("- State-Changing Operation: ").append(isStateChanging ? "YES (POST/PUT/DELETE/PATCH)" : "NO (Read-only)").append("\n");
        sb.append("- Extracted Request Parameters:\n");
        sb.append(extractParametersDescription(record != null ? record.getOriginalRequest() : null)).append("\n\n");

        // 2. Tested Role & Injected Credentials
        String roleName = roleResult != null && roleResult.getRoleName() != null ? roleResult.getRoleName() : "Unknown Role";
        String roleId = roleResult != null && roleResult.getRoleId() != null ? roleResult.getRoleId() : "unknown";
        sb.append("## 2. TESTED ROLE\n");
        sb.append("- Role Name: ").append(roleName).append("\n");
        sb.append("- Role ID: ").append(roleId).append("\n");
        sb.append("- Replay Headers / Credentials Applied:\n");
        sb.append(extractRoleHeadersDescription(roleResult != null ? roleResult.getRequest() : null)).append("\n\n");

        // 3. Baseline (Original Authorized User)
        int origStatus = record != null ? record.getOriginalStatusCode() : 0;
        int origLen = record != null ? record.getOriginalResponseLength() : 0;
        HttpResponse origResp = record != null ? record.getOriginalResponse() : null;
        String origBody = origResp != null ? origResp.bodyToString() : (record != null && record.getSerializedOriginalResponse() != null ? record.getSerializedOriginalResponse() : "");

        sb.append("## 3. BASELINE (ORIGINAL AUTHORIZED USER)\n");
        sb.append("- Status Code: ").append(origStatus).append("\n");
        sb.append("- Response Length: ").append(origLen).append(" bytes\n");
        sb.append("- Key Response Headers:\n");
        sb.append(extractKeyHeaders(origResp)).append("\n");
        sb.append("- Response Body Excerpt:\n```\n");
        sb.append(truncateBody(origBody)).append("\n```\n\n");

        // 4. Replayed Role Response
        int roleStatus = roleResult != null ? roleResult.getStatusCode() : 0;
        int roleLen = roleResult != null ? roleResult.getResponseLength() : 0;
        HttpResponse roleResp = roleResult != null ? roleResult.getResponse() : null;
        String roleBody = roleResp != null ? roleResp.bodyToString() : (roleResult != null && roleResult.getSerializedResponse() != null ? roleResult.getSerializedResponse() : "");

        sb.append("## 4. REPLAYED ROLE RESPONSE\n");
        sb.append("- Status Code: ").append(roleStatus).append("\n");
        sb.append("- Response Length: ").append(roleLen).append(" bytes\n");
        sb.append("- Key Response Headers:\n");
        sb.append(extractKeyHeaders(roleResp)).append("\n");
        sb.append("- Response Body Excerpt:\n```\n");
        sb.append(truncateBody(roleBody)).append("\n```\n\n");

        // 5. Differential Metrics & Heuristics
        double lenDiffPercent = DiffUtils.calculateLengthDiffPercent(origLen, roleLen);
        double similarity = DiffUtils.computeSimilarity(origBody, roleBody);
        boolean statusMatch = (origStatus == roleStatus);

        sb.append("## 5. DIFFERENTIAL METRICS & HEURISTICS\n");
        sb.append("- Status Code Match: ").append(statusMatch ? "YES (" + origStatus + " == " + roleStatus + ")" : "NO (" + origStatus + " -> " + roleStatus + ")").append("\n");
        sb.append(String.format(Locale.ROOT, "- Length Difference: %d bytes (%.1f%% delta)\n", (roleLen - origLen), lenDiffPercent));
        sb.append(String.format(Locale.ROOT, "- Content Similarity Score: %.1f%%\n", similarity * 100.0));
        sb.append("- Heuristic Rule Verdict: ").append(roleResult != null && roleResult.getVerdict() != null ? roleResult.getVerdict() : "INDETERMINABLE").append("\n");
        sb.append("- Heuristic Rule Note: ").append(roleResult != null && roleResult.getNotes() != null ? roleResult.getNotes() : "N/A").append("\n\n");

        sb.append("## TASK\n");
        sb.append("Analyze the differential above. Determine if role '").append(roleName);
        sb.append("' succeeded in accessing unauthorized data or capabilities (VALIDATED_ACCESS), was properly blocked (ACCESS_DENIED), or if the evidence is INCONCLUSIVE. Output ONLY the specified JSON object.");

        return sb.toString();
    }

    public static String truncateBody(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "[EMPTY BODY]";
        }
        if (body.length() <= MAX_BODY_CHARS) {
            return body;
        }
        int truncatedChars = body.length() - (HEAD_TRUNCATE_CHARS + TAIL_TRUNCATE_CHARS);
        return body.substring(0, HEAD_TRUNCATE_CHARS)
                + "\n[... Truncated " + truncatedChars + " characters ...]\n"
                + body.substring(body.length() - TAIL_TRUNCATE_CHARS);
    }

    private static String extractParametersDescription(HttpRequest request) {
        if (request == null) return "  (None / Raw request)";
        try {
            if (request.hasParameters()) {
                List<ParsedHttpParameter> params = request.parameters();
                if (params != null && !params.isEmpty()) {
                    return params.stream()
                            .map(p -> "  - [" + p.type() + "] " + p.name() + " = " + p.value())
                            .collect(Collectors.joining("\n"));
                }
            }
        } catch (Throwable ignored) {}
        return "  (None identified)";
    }

    private static String extractRoleHeadersDescription(HttpRequest request) {
        if (request == null) return "  (Default role headers)";
        try {
            List<HttpHeader> headers = request.headers();
            if (headers != null && !headers.isEmpty()) {
                return headers.stream()
                        .filter(h -> isAuthOrCustomHeader(h.name()))
                        .map(h -> "  - " + h.name() + ": " + sanitizeToken(h.name(), h.value()))
                        .collect(Collectors.joining("\n"));
            }
        } catch (Throwable ignored) {}
        return "  (No custom headers)";
    }

    private static String extractKeyHeaders(HttpResponse response) {
        if (response == null) return "  (None)";
        try {
            List<HttpHeader> headers = response.headers();
            if (headers != null && !headers.isEmpty()) {
                String result = headers.stream()
                        .filter(h -> SECURITY_HEADERS.contains(h.name().toLowerCase(Locale.ROOT)))
                        .map(h -> "  - " + h.name() + ": " + h.value())
                        .collect(Collectors.joining("\n"));
                return result.isEmpty() ? "  (No security headers present)" : result;
            }
        } catch (Throwable ignored) {}
        return "  (None)";
    }

    private static boolean isAuthOrCustomHeader(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.contains("auth") || n.contains("token") || n.contains("cookie")
                || n.contains("jwt") || n.contains("role") || n.contains("tenant");
    }

    private static String sanitizeToken(String name, String value) {
        if (value == null) return "";
        if (value.length() > 30 && name.toLowerCase(Locale.ROOT).contains("auth")) {
            return value.substring(0, 15) + "..." + value.substring(value.length() - 6);
        }
        return value;
    }

    /**
     * Resilient multi-stage parsing pipeline that unbundles reasoning tags (<think>...</think>),
     * markdown fences (```json ... ```), and raw JSON objects.
     */
    public static AiEvaluationResult parseResponse(String rawResponse, String modelName, long executionTimeMs) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            return AiEvaluationResult.error("AI returned empty response or connection failed.", modelName, executionTimeMs);
        }

        String cleaned = rawResponse;

        // 1. Strip reasoning blocks (<think>...</think>)
        cleaned = cleaned.replaceAll("(?s)<think>.*?</think>", "").trim();

        // 2. Strip Markdown code fences
        if (cleaned.contains("```")) {
            cleaned = cleaned.replaceAll("(?s)^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceAll("(?s)\\s*```$", "");
            // Also handle internal fences
            cleaned = cleaned.replaceAll("(?s)```(?:json)?\\s*", "");
            cleaned = cleaned.replaceAll("(?s)\\s*```", "");
            cleaned = cleaned.trim();
        }

        // 3. Extract outermost JSON object { ... }
        int firstBrace = cleaned.indexOf('{');
        int lastBrace = cleaned.lastIndexOf('}');
        if (firstBrace != -1 && lastBrace > firstBrace) {
            cleaned = cleaned.substring(firstBrace, lastBrace + 1);
        }

        try {
            JsonObject json = JsonParser.parseString(cleaned).getAsJsonObject();

            String verdictStr = json.has("verdict") ? json.get("verdict").getAsString() : "";
            AiVerdict verdict = AiVerdict.fromString(verdictStr);

            String reasoning = json.has("reasoning") ? json.get("reasoning").getAsString() : "";
            String diffAnalysis = json.has("differential_analysis") ? json.get("differential_analysis").getAsString() : "";

            String fullReasoning = reasoning;
            if (!diffAnalysis.isEmpty() && !diffAnalysis.equals(reasoning)) {
                fullReasoning = fullReasoning.isEmpty() ? diffAnalysis : fullReasoning + " Analysis: " + diffAnalysis;
            }

            return AiEvaluationResult.success(verdict, fullReasoning, modelName, executionTimeMs);
        } catch (Exception e) {
            // Fallback: heuristic keyword extraction from text
            AiVerdict fallbackVerdict = AiVerdict.fromString(rawResponse);
            String fallbackReasoning = "Parsed from text: " + (rawResponse.length() > 200 ? rawResponse.substring(0, 200) + "..." : rawResponse);
            return AiEvaluationResult.success(fallbackVerdict, fallbackReasoning, modelName, executionTimeMs);
        }
    }

    /**
     * Asynchronously evaluates the access control differential of all active roles in an AuditRecord.
     * Transitions state to PENDING, dispatches to virtual threads, computes SHA-256 cache,
     * queries LLM on cache miss, and notifies observers on the Swing EDT.
     */
    public void evaluateRecordAsync(AuditRecord record, List<UserRole> roles, Runnable onComplete) {
        if (record == null) {
            return;
        }

        final AiConfig currentConfig = new AiConfig(this.config);
        final String modelName = currentConfig.getModelName();
        final long startTime = System.currentTimeMillis();

        // 1. Mark active roles and unauthenticated result as PENDING immediately
        AiEvaluationResult pendingResult = AiEvaluationResult.pending(modelName);

        List<AuthorizationCheckResult> targets = new ArrayList<>();
        // 1. Check enabled roles provided in arguments
        if (roles != null) {
            for (UserRole role : roles) {
                if (role != null && role.isEnabled()) {
                    AuthorizationCheckResult res = record.getRoleResults() != null ? record.getRoleResults().get(role.getId()) : null;
                    if (res != null && !targets.contains(res)) {
                        targets.add(res);
                    }
                }
            }
        }
        // 2. Check unauthenticated result if present
        if (record.getUnauthenticatedResult() != null && !targets.contains(record.getUnauthenticatedResult())) {
            targets.add(record.getUnauthenticatedResult());
        }

        // 3. Fallback: If no enabled roles and no unauth result matched, but the record HAS roleResults
        // (e.g. all roles disabled in proxy interceptor, or role IDs changed/imported), target all available role results!
        if (targets.isEmpty() && record.getRoleResults() != null && !record.getRoleResults().isEmpty()) {
            for (AuthorizationCheckResult res : record.getRoleResults().values()) {
                if (res != null && !targets.contains(res)) {
                    targets.add(res);
                }
            }
        }

        // If no replayed targets are available at all, fail fast with a descriptive error instead of hanging in PENDING
        if (targets.isEmpty()) {
            AiEvaluationResult noTargetsResult = AiEvaluationResult.error(
                    "No role replay or unauthenticated results found for this record. Re-test against a role before running AI evaluation.",
                    modelName,
                    0L
            );
            record.setAiResult(noTargetsResult);
            if (engineController != null) {
                SwingUtilities.invokeLater(() -> engineController.notifyRecordChange(record));
            }
            if (onComplete != null) {
                SwingUtilities.invokeLater(onComplete);
            }
            return;
        }

        for (AuthorizationCheckResult target : targets) {
            target.setAiResult(pendingResult);
        }
        record.setAiResult(pendingResult);

        // Notify EDT of PENDING status
        if (engineController != null) {
            SwingUtilities.invokeLater(() -> engineController.notifyRecordChange(record));
        }

        // 2. Dispatch evaluation on background virtual threads
        aiExecutor.submit(() -> {
            try {
                String systemPrompt = buildSystemPrompt();
                AiEvaluationResult lastResult = null;

                for (AuthorizationCheckResult target : targets) {
                    try {
                        String origBody = record.getOriginalResponse() != null ? record.getOriginalResponse().bodyToString() : (record.getSerializedOriginalResponse() != null ? record.getSerializedOriginalResponse() : "");
                        String origReq = record.getOriginalRequest() != null ? record.getOriginalRequest().bodyToString() : (record.getSerializedOriginalRequest() != null ? record.getSerializedOriginalRequest() : "");
                        String roleBody = target.getResponse() != null ? target.getResponse().bodyToString() : (target.getSerializedResponse() != null ? target.getSerializedResponse() : "");
                        String roleHeaders = target.getRequest() != null ? target.getRequest().headers().toString() : (target.getSerializedRequest() != null ? target.getSerializedRequest() : "");

                        String hash = AiEvaluationCache.computeHash(
                                record.getMethod(),
                                record.getUrl(),
                                origReq,
                                origBody,
                                roleHeaders,
                                roleBody,
                                modelName
                        );

                        AiEvaluationResult cachedResult = cache.get(hash);
                        if (cachedResult != null) {
                            target.setAiResult(cachedResult);
                            lastResult = cachedResult;
                            continue;
                        }

                        String userPrompt = buildUserPrompt(record, target);
                        long callStart = System.currentTimeMillis();
                        String llmReply = client.sendChatCompletionAsync(currentConfig, systemPrompt, userPrompt)
                                .get(Math.max(5, currentConfig.getTimeoutSeconds()), TimeUnit.SECONDS);
                        long latency = System.currentTimeMillis() - callStart;

                        AiEvaluationResult evalResult = parseResponse(llmReply, modelName, latency);
                        cache.put(hash, evalResult);
                        target.setAiResult(evalResult);
                        lastResult = evalResult;
                    } catch (Exception ex) {
                        String errMsg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                        AiEvaluationResult errorResult = AiEvaluationResult.error("AI evaluation failed: " + errMsg, modelName, System.currentTimeMillis() - startTime);
                        target.setAiResult(errorResult);
                        lastResult = errorResult;
                    }
                }

                if (lastResult != null) {
                    record.setAiResult(lastResult);
                } else {
                    record.setAiResult(AiEvaluationResult.error("No evaluation outcome produced.", modelName, System.currentTimeMillis() - startTime));
                }
            } catch (Throwable topEx) {
                String errMsg = topEx.getCause() != null ? topEx.getCause().getMessage() : topEx.getMessage();
                record.setAiResult(AiEvaluationResult.error("AI evaluation error: " + errMsg, modelName, System.currentTimeMillis() - startTime));
            } finally {
                // Update UI on Swing EDT
                SwingUtilities.invokeLater(() -> {
                    if (engineController != null) {
                        engineController.notifyRecordChange(record);
                    }
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        });
    }
}
