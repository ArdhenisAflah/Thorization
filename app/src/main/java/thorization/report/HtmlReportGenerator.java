package thorization.report;

import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.http.message.responses.HttpResponse;
import thorization.ai.AiEvaluationResult;
import thorization.ai.AiVerdict;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.model.Verdict;
import thorization.util.CurlUtils;
import thorization.util.DiffUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generates standalone, beautifully styled, offline HTML compliance and audit reports
 * featuring executive summary metrics, interactive filterable findings table,
 * side-by-side request/response diff viewer, AI reasoning display, and 1-click cURL commands.
 *
 * Enforces zero external CDN dependencies and mathematical context-aware XSS escaping.
 */
public class HtmlReportGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Generates an HTML report and writes it to the designated file in UTF-8.
     */
    public static void writeHtmlReport(File targetFile, List<AuditRecord> records, List<UserRole> roles, String title) throws IOException {
        if (targetFile == null) {
            throw new IllegalArgumentException("Target report file cannot be null");
        }
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create parent directories for: " + targetFile.getAbsolutePath());
        }
        String html = generateHtml(records, roles, title);
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(targetFile), StandardCharsets.UTF_8)) {
            writer.write(html);
        }
    }

    public static void writeHtmlReport(File targetFile, List<AuditRecord> records, List<UserRole> roles) throws IOException {
        writeHtmlReport(targetFile, records, roles, "Thorization Access Control Audit Report");
    }

    public static void generateReport(File file, List<AuditRecord> records, List<UserRole> roles) throws IOException {
        writeHtmlReport(file, records, roles, "Thorization Access Control Audit Report");
    }

    public static void generateReport(File file, List<AuditRecord> records, List<UserRole> roles, String title) throws IOException {
        writeHtmlReport(file, records, roles, title);
    }

    /**
     * Generates standalone HTML document content in memory.
     */
    public static String generateHtml(List<AuditRecord> records, List<UserRole> roles) {
        return generateHtml(records, roles, "Thorization Access Control Audit Report");
    }

    /**
     * Generates standalone HTML document content with a custom title.
     */
    public static String generateHtml(List<AuditRecord> records, List<UserRole> roles, String title) {
        List<AuditRecord> safeRecords = records != null ? records : Collections.emptyList();
        List<UserRole> safeRoles = roles != null ? roles : Collections.emptyList();
        String safeTitle = (title != null && !title.trim().isEmpty()) ? title.trim() : "Thorization Access Control Audit Report";

        ReportMetrics metrics = ReportMetrics.compute(safeRecords, safeRoles);
        String timestamp = LocalDateTime.now().format(DATE_FORMATTER);

        StringBuilder sb = new StringBuilder(128 * 1024);
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        sb.append("<meta charset=\"UTF-8\">\n");
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        sb.append("<title>").append(escapeHtml(safeTitle)).append("</title>\n");

        renderInlineStyles(sb);

        sb.append("</head>\n<body>\n");
        sb.append("<div class=\"report-container\">\n");

        // 1. Header Bar
        renderHeader(sb, safeTitle, timestamp, metrics);

        // 2. Executive Metrics Dashboard
        renderExecutiveDashboard(sb, metrics);

        // 3. Interactive Filter Toolbar
        renderFilterToolbar(sb, safeRoles);

        // 4. Interactive Findings Table
        renderFindingsTable(sb, safeRecords, safeRoles);

        // 5. Footer
        renderFooter(sb, timestamp);

        sb.append("</div>\n"); // .report-container

        // 6. Interactive Vanilla JavaScript (Zero CDN)
        renderInlineScripts(sb);

        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    // --- Template Rendering Components ---

    private static void renderHeader(StringBuilder sb, String title, String timestamp, ReportMetrics metrics) {
        sb.append("<header class=\"report-header\">\n");
        sb.append("  <div class=\"header-brand\">\n");
        sb.append("    <div class=\"brand-badge\">&#x26A1; THORIZATION</div>\n");
        sb.append("    <h1 class=\"report-title\">").append(escapeHtml(title)).append("</h1>\n");
        sb.append("    <p class=\"report-meta\">Generated on ").append(escapeHtml(timestamp))
                .append(" &bull; Total Endpoints: <strong>").append(metrics.totalRequests()).append("</strong>")
                .append(" &bull; Total Replays: <strong>").append(metrics.totalReplays()).append("</strong></p>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"posture-score-box\">\n");
        sb.append("    <div class=\"score-label\">Security Posture Score</div>\n");
        String scoreColor = metrics.securityPostureScore() >= 90.0 ? "score-good" : (metrics.securityPostureScore() >= 70.0 ? "score-warn" : "score-bad");
        sb.append("    <div class=\"score-value ").append(scoreColor).append("\">")
                .append(String.format(Locale.ROOT, "%.1f%%", metrics.securityPostureScore())).append("</div>\n");
        sb.append("  </div>\n");
        sb.append("</header>\n");
    }

    private static void renderExecutiveDashboard(StringBuilder sb, ReportMetrics metrics) {
        sb.append("<section class=\"dashboard-grid\">\n");

        // KPI Card 1: Total
        sb.append("  <div class=\"metric-card card-total\">\n");
        sb.append("    <div class=\"metric-header\">Total Replay Tests</div>\n");
        sb.append("    <div class=\"metric-value\">").append(metrics.totalReplays()).append("</div>\n");
        sb.append("    <div class=\"metric-sub\">Across ").append(metrics.totalRequests()).append(" endpoints</div>\n");
        sb.append("  </div>\n");

        // KPI Card 2: Enforced (Protected)
        double enforcedPct = metrics.totalReplays() > 0 ? (metrics.enforcedCount() * 100.0 / metrics.totalReplays()) : 0.0;
        sb.append("  <div class=\"metric-card card-enforced\">\n");
        sb.append("    <div class=\"metric-header\">Access Enforced</div>\n");
        sb.append("    <div class=\"metric-value text-green\">").append(metrics.enforcedCount()).append("</div>\n");
        sb.append("    <div class=\"metric-sub\">").append(String.format(Locale.ROOT, "%.1f%%", enforcedPct)).append(" protected</div>\n");
        sb.append("  </div>\n");

        // KPI Card 3: Bypassed (Vulnerable)
        double bypassPct = metrics.totalReplays() > 0 ? (metrics.bypassCount() * 100.0 / metrics.totalReplays()) : 0.0;
        sb.append("  <div class=\"metric-card card-bypass\">\n");
        sb.append("    <div class=\"metric-header\">Authorization Bypasses</div>\n");
        sb.append("    <div class=\"metric-value text-red\">").append(metrics.bypassCount()).append("</div>\n");
        sb.append("    <div class=\"metric-sub\">").append(String.format(Locale.ROOT, "%.1f%%", bypassPct)).append(" critical flaws</div>\n");
        sb.append("  </div>\n");

        // KPI Card 4: Indeterminable
        double indetPct = metrics.totalReplays() > 0 ? (metrics.indeterminableCount() * 100.0 / metrics.totalReplays()) : 0.0;
        sb.append("  <div class=\"metric-card card-indet\">\n");
        sb.append("    <div class=\"metric-header\">Manual Review / Indeterminate</div>\n");
        sb.append("    <div class=\"metric-value text-orange\">").append(metrics.indeterminableCount()).append("</div>\n");
        sb.append("    <div class=\"metric-sub\">").append(String.format(Locale.ROOT, "%.1f%%", indetPct)).append(" need review</div>\n");
        sb.append("  </div>\n");

        // KPI Card 5: AI Semantic Breakdown
        sb.append("  <div class=\"metric-card card-ai-summary\">\n");
        sb.append("    <div class=\"metric-header\">AI Semantic Analysis</div>\n");
        sb.append("    <div class=\"ai-metrics-row\">\n");
        sb.append("      <span class=\"badge-ai-val\" title=\"AI Validated Access\">&#x2713; ").append(metrics.aiValidatedAccessCount()).append(" Validated</span>\n");
        sb.append("      <span class=\"badge-ai-denied\" title=\"AI Access Denied\">&#x1F6E1; ").append(metrics.aiAccessDeniedCount()).append(" Denied</span>\n");
        sb.append("      <span class=\"badge-ai-incon\" title=\"AI Inconclusive\">? ").append(metrics.aiInconclusiveCount()).append(" Inconclusive</span>\n");
        sb.append("    </div>\n");
        sb.append("    <div class=\"metric-sub mt-4\">AI Confirmed Flaws: <strong>").append(metrics.aiValidatedAccessCount()).append("</strong></div>\n");
        sb.append("  </div>\n");

        sb.append("</section>\n");

        // Progress Bar
        sb.append("<div class=\"progress-section\">\n");
        sb.append("  <div class=\"progress-label-bar\">\n");
        sb.append("    <span>Enforcement Breakdown</span>\n");
        sb.append("    <span>").append(metrics.enforcedCount()).append(" Enforced &bull; ")
                .append(metrics.bypassCount()).append(" Bypass &bull; ")
                .append(metrics.indeterminableCount()).append(" Indeterminate</span>\n");
        sb.append("  </div>\n");
        sb.append("  <div class=\"stacked-progress-bar\">\n");
        if (metrics.totalReplays() > 0) {
            sb.append("    <div class=\"progress-segment bg-green\" style=\"width: ")
                    .append(String.format(Locale.ROOT, "%.2f%%", enforcedPct)).append("\" title=\"Enforced: ").append(metrics.enforcedCount()).append("\"></div>\n");
            sb.append("    <div class=\"progress-segment bg-red\" style=\"width: ")
                    .append(String.format(Locale.ROOT, "%.2f%%", bypassPct)).append("\" title=\"Bypass: ").append(metrics.bypassCount()).append("\"></div>\n");
            sb.append("    <div class=\"progress-segment bg-orange\" style=\"width: ")
                    .append(String.format(Locale.ROOT, "%.2f%%", indetPct)).append("\" title=\"Indeterminate: ").append(metrics.indeterminableCount()).append("\"></div>\n");
        } else {
            sb.append("    <div class=\"progress-segment bg-muted\" style=\"width: 100%\"></div>\n");
        }
        sb.append("  </div>\n");
        sb.append("</div>\n");
    }

    private static void renderFilterToolbar(StringBuilder sb, List<UserRole> roles) {
        sb.append("<div class=\"filter-toolbar\">\n");
        sb.append("  <div class=\"filter-group search-group\">\n");
        sb.append("    <input type=\"text\" id=\"search-input\" class=\"filter-input\" placeholder=\"Search URL, method, status, role, AI reasoning...\" onkeyup=\"applyFilters()\">\n");
        sb.append("  </div>\n");

        // Role Filter Dropdown
        sb.append("  <div class=\"filter-group\">\n");
        sb.append("    <label for=\"role-filter\">Role:</label>\n");
        sb.append("    <select id=\"role-filter\" class=\"filter-select\" onchange=\"applyFilters()\">\n");
        sb.append("      <option value=\"ALL\">All Roles</option>\n");
        if (roles != null) {
            for (UserRole r : roles) {
                if (r == null || r.getName() == null) continue;
                sb.append("      <option value=\"").append(escapeAttribute(r.getName())).append("\">")
                        .append(escapeHtml(r.getName())).append("</option>\n");
            }
        }
        sb.append("      <option value=\"Unauthenticated\">Unauthenticated</option>\n");
        sb.append("    </select>\n");
        sb.append("  </div>\n");

        // Verdict Filter Dropdown
        sb.append("  <div class=\"filter-group\">\n");
        sb.append("    <label for=\"verdict-filter\">Verdict:</label>\n");
        sb.append("    <select id=\"verdict-filter\" class=\"filter-select\" onchange=\"applyFilters()\">\n");
        sb.append("      <option value=\"ALL\">All Verdicts</option>\n");
        sb.append("      <option value=\"BYPASS\">BYPASS (Vulnerable)</option>\n");
        sb.append("      <option value=\"ENFORCED\">ENFORCED (Protected)</option>\n");
        sb.append("      <option value=\"INDETERMINABLE\">MANUAL (Indeterminate)</option>\n");
        sb.append("    </select>\n");
        sb.append("  </div>\n");

        // AI Verdict Filter Dropdown
        sb.append("  <div class=\"filter-group\">\n");
        sb.append("    <label for=\"ai-filter\">AI Verdict:</label>\n");
        sb.append("    <select id=\"ai-filter\" class=\"filter-select\" onchange=\"applyFilters()\">\n");
        sb.append("      <option value=\"ALL\">All AI Results</option>\n");
        sb.append("      <option value=\"VALIDATED_ACCESS\">VALIDATED_ACCESS</option>\n");
        sb.append("      <option value=\"ACCESS_DENIED\">ACCESS_DENIED</option>\n");
        sb.append("      <option value=\"INCONCLUSIVE\">INCONCLUSIVE</option>\n");
        sb.append("      <option value=\"ERROR\">ERROR</option>\n");
        sb.append("    </select>\n");
        sb.append("  </div>\n");

        // Method Filter Dropdown
        sb.append("  <div class=\"filter-group\">\n");
        sb.append("    <label for=\"method-filter\">Method:</label>\n");
        sb.append("    <select id=\"method-filter\" class=\"filter-select\" onchange=\"applyFilters()\">\n");
        sb.append("      <option value=\"ALL\">All Methods</option>\n");
        sb.append("      <option value=\"GET\">GET</option>\n");
        sb.append("      <option value=\"POST\">POST</option>\n");
        sb.append("      <option value=\"PUT\">PUT</option>\n");
        sb.append("      <option value=\"DELETE\">DELETE</option>\n");
        sb.append("      <option value=\"PATCH\">PATCH</option>\n");
        sb.append("    </select>\n");
        sb.append("  </div>\n");

        // Counter & Reset
        sb.append("  <div class=\"filter-actions\">\n");
        sb.append("    <span class=\"findings-counter\">Showing <strong id=\"visible-count\">0</strong> of <strong id=\"total-count\">0</strong> findings</span>\n");
        sb.append("    <button type=\"button\" class=\"btn-reset\" onclick=\"resetFilters()\">Reset</button>\n");
        sb.append("  </div>\n");
        sb.append("</div>\n");
    }

    private static void renderFindingsTable(StringBuilder sb, List<AuditRecord> records, List<UserRole> roles) {
        sb.append("<div class=\"table-card\">\n");
        sb.append("<table class=\"findings-table\" id=\"findings-table\">\n");
        sb.append("  <thead>\n");
        sb.append("    <tr>\n");
        sb.append("      <th class=\"sortable\" onclick=\"sortTable(0, true)\"># <span class=\"sort-arrow\">&#x25BC;</span></th>\n");
        sb.append("      <th class=\"sortable\" onclick=\"sortTable(1, false)\">Method</th>\n");
        sb.append("      <th class=\"sortable\" onclick=\"sortTable(2, false)\">Endpoint / URL</th>\n");
        sb.append("      <th class=\"sortable\" onclick=\"sortTable(3, true)\">Orig Status</th>\n");
        sb.append("      <th class=\"sortable\" onclick=\"sortTable(4, false)\">Tested Role</th>\n");
        sb.append("      <th class=\"sortable\" onclick=\"sortTable(5, true)\">Replay Status</th>\n");
        sb.append("      <th class=\"sortable\" onclick=\"sortTable(6, false)\">Enforcement</th>\n");
        sb.append("      <th class=\"sortable\" onclick=\"sortTable(7, false)\">AI Verdict</th>\n");
        sb.append("      <th>Actions</th>\n");
        sb.append("    </tr>\n");
        sb.append("  </thead>\n");
        sb.append("  <tbody id=\"findings-tbody\">\n");

        int findingIndex = 0;

        for (AuditRecord rec : records) {
            if (rec == null) continue;
            boolean hasReplay = false;

            // Render finding rows for each configured role result
            if (rec.getRoleResults() != null) {
                for (Map.Entry<String, AuthorizationCheckResult> entry : rec.getRoleResults().entrySet()) {
                    AuthorizationCheckResult res = entry.getValue();
                    if (res == null) continue;
                    findingIndex++;
                    hasReplay = true;
                    renderFindingRow(sb, findingIndex, rec, res, false);
                }
            }

            // Render finding row for unauthenticated test if available
            if (rec.getUnauthenticatedResult() != null) {
                findingIndex++;
                hasReplay = true;
                renderFindingRow(sb, findingIndex, rec, rec.getUnauthenticatedResult(), true);
            }

            // If no replays exist yet for this captured record, render baseline original row
            if (!hasReplay) {
                findingIndex++;
                AuthorizationCheckResult baselineRes = new AuthorizationCheckResult(
                        "original", "Original / Baseline", Verdict.INDETERMINABLE,
                        rec.getOriginalStatusCode(), rec.getOriginalResponseLength(),
                        1.0, 0, "Original baseline request (no role replays)",
                        rec.getOriginalRequest(), rec.getOriginalResponse()
                );
                renderFindingRow(sb, findingIndex, rec, baselineRes, false);
            }
        }

        if (findingIndex == 0) {
            sb.append("    <tr id=\"no-findings-row\"><td colspan=\"9\" class=\"text-center empty-state\">No authorization test records available.</td></tr>\n");
        }

        sb.append("  </tbody>\n");
        sb.append("</table>\n");
        sb.append("</div>\n");
    }

    private static void renderFindingRow(StringBuilder sb, int index, AuditRecord record, AuthorizationCheckResult result, boolean isUnauth) {
        String roleName = isUnauth ? "Unauthenticated" : (result.getRoleName() != null ? result.getRoleName() : "Role");
        Verdict verdict = result.getVerdict() != null ? result.getVerdict() : Verdict.INDETERMINABLE;
        AiEvaluationResult aiResult = result.getAiResult() != null ? result.getAiResult() : record.getAiResult();
        AiVerdict aiVerdict = aiResult != null ? aiResult.getVerdict() : null;

        String method = record.getMethod() != null ? record.getMethod() : "GET";
        String url = record.getUrl() != null ? record.getUrl() : "";
        int origStatus = record.getOriginalStatusCode();
        int replayStatus = result.getStatusCode();
        int origLength = record.getOriginalResponseLength();
        int replayLength = result.getResponseLength();

        String rowId = "finding-" + index;
        String drawerId = "drawer-" + index;

        // Data attributes for ultra-fast, zero-recalculation client filtering
        sb.append("    <tr class=\"finding-row\" id=\"").append(rowId).append("\" ")
                .append("data-method=\"").append(escapeAttribute(method.toUpperCase(Locale.ROOT))).append("\" ")
                .append("data-role=\"").append(escapeAttribute(roleName)).append("\" ")
                .append("data-verdict=\"").append(escapeAttribute(verdict.name())).append("\" ")
                .append("data-ai=\"").append(aiVerdict != null ? escapeAttribute(aiVerdict.name()) : "NONE").append("\" ")
                .append("data-search=\"").append(escapeAttribute(
                        (method + " " + url + " " + roleName + " " + origStatus + " " + replayStatus + " "
                                + (result.getNotes() != null ? result.getNotes() : "") + " "
                                + (aiResult != null ? aiResult.getReasoning() : "")).toLowerCase(Locale.ROOT))).append("\">\n");

        // 1. Index
        sb.append("      <td class=\"col-id\">").append(index).append("</td>\n");

        // 2. Method
        sb.append("      <td class=\"col-method\"><span class=\"method-badge method-").append(escapeAttribute(method.toLowerCase(Locale.ROOT))).append("\">")
                .append(escapeHtml(method)).append("</span></td>\n");

        // 3. URL / Endpoint
        sb.append("      <td class=\"col-url\"><span class=\"url-text\" title=\"").append(escapeAttribute(url)).append("\">")
                .append(escapeHtml(url)).append("</span></td>\n");

        // 4. Orig Status
        sb.append("      <td class=\"col-orig-status\"><span class=\"status-code\">").append(origStatus).append("</span> ")
                .append("<span class=\"len-muted\">(").append(origLength).append("B)</span></td>\n");

        // 5. Tested Role
        sb.append("      <td class=\"col-role\"><span class=\"role-tag\">").append(escapeHtml(roleName)).append("</span></td>\n");

        // 6. Replay Status
        double lenDiff = DiffUtils.calculateLengthDiffPercent(origLength, replayLength);
        String lenDiffStr = String.format(Locale.ROOT, "%+.1f%%", (replayLength >= origLength ? lenDiff : -lenDiff));
        sb.append("      <td class=\"col-replay-status\"><span class=\"status-code\">").append(replayStatus).append("</span> ")
                .append("<span class=\"len-muted\">(").append(replayLength).append("B &bull; ").append(lenDiffStr).append(")</span></td>\n");

        // 7. Enforcement Verdict
        String vBadgeClass = verdict == Verdict.ENFORCED ? "badge-enforced" : (verdict == Verdict.BYPASS ? "badge-bypass" : "badge-indet");
        sb.append("      <td class=\"col-verdict\"><span class=\"badge ").append(vBadgeClass).append("\">")
                .append(escapeHtml(verdict.getDisplayName())).append("</span></td>\n");

        // 8. AI Verdict
        sb.append("      <td class=\"col-ai\">");
        if (aiVerdict != null) {
            String aiBadgeClass = aiVerdict == AiVerdict.VALIDATED_ACCESS ? "badge-ai-val" :
                    (aiVerdict == AiVerdict.ACCESS_DENIED ? "badge-ai-denied" :
                    (aiVerdict == AiVerdict.INCONCLUSIVE ? "badge-ai-incon" : "badge-ai-error"));
            sb.append("<span class=\"badge ").append(aiBadgeClass).append("\">").append(escapeHtml(aiVerdict.getDisplayName())).append("</span>");
        } else {
            sb.append("<span class=\"text-muted\">-</span>");
        }
        sb.append("</td>\n");

        // 9. Actions (Inspect / Toggle Drawer)
        sb.append("      <td class=\"col-actions\">\n");
        sb.append("        <button type=\"button\" class=\"btn-toggle-drawer\" onclick=\"toggleDrawer('").append(drawerId).append("')\">Details &#x25BC;</button>\n");
        sb.append("      </td>\n");

        sb.append("    </tr>\n");

        // Expandable Detail Drawer Row
        renderDetailDrawerRow(sb, drawerId, record, result, roleName, verdict, aiResult);
    }

    private static void renderDetailDrawerRow(StringBuilder sb, String drawerId, AuditRecord record,
                                              AuthorizationCheckResult result, String roleName,
                                              Verdict verdict, AiEvaluationResult aiResult) {
        sb.append("    <tr class=\"drawer-row\" id=\"").append(drawerId).append("\" style=\"display:none;\">\n");
        sb.append("      <td colspan=\"9\" class=\"drawer-cell\">\n");
        sb.append("        <div class=\"drawer-content\">\n");

        // Drawer Tabs Header
        sb.append("          <div class=\"drawer-tabs\">\n");
        sb.append("            <button class=\"tab-btn active\" onclick=\"switchTab(this, '").append(drawerId).append("-diff')\">Side-by-Side Diff</button>\n");
        sb.append("            <button class=\"tab-btn\" onclick=\"switchTab(this, '").append(drawerId).append("-ai')\">AI Semantic Explainability</button>\n");
        sb.append("            <button class=\"tab-btn\" onclick=\"switchTab(this, '").append(drawerId).append("-notes')\">Audit Notes &amp; Replay Details</button>\n");
        sb.append("          </div>\n");

        // Tab Pane 1: Side-by-Side Diff Panels
        sb.append("          <div class=\"tab-pane active\" id=\"").append(drawerId).append("-diff\">\n");
        renderDiffPanels(sb, record, result, roleName);
        sb.append("          </div>\n");

        // Tab Pane 2: AI Explainability Card
        sb.append("          <div class=\"tab-pane\" id=\"").append(drawerId).append("-ai\" style=\"display:none;\">\n");
        renderAiCard(sb, aiResult);
        sb.append("          </div>\n");

        // Tab Pane 3: Audit Notes
        sb.append("          <div class=\"tab-pane\" id=\"").append(drawerId).append("-notes\" style=\"display:none;\">\n");
        renderNotesPane(sb, record, result);
        sb.append("          </div>\n");

        sb.append("        </div>\n");
        sb.append("      </td>\n");
        sb.append("    </tr>\n");
    }

    private static void renderDiffPanels(StringBuilder sb, AuditRecord record, AuthorizationCheckResult result, String roleName) {
        HttpRequest origReq = record.getOriginalRequest();
        HttpResponse origResp = record.getOriginalResponse();
        HttpRequest replayReq = result.getRequest();
        HttpResponse replayResp = result.getResponse();

        String origReqStr = origReq != null ? origReq.toString() : "";
        String origRespStr = origResp != null ? origResp.toString() : "";
        String replayReqStr = replayReq != null ? replayReq.toString() : "";
        String replayRespStr = replayResp != null ? replayResp.toString() : "";

        String curlCmd = replayReq != null ? CurlUtils.toCurlCommand(replayReq) : (origReq != null ? CurlUtils.toCurlCommand(origReq) : "");
        String encodedCurl = "";
        try {
            encodedCurl = URLEncoder.encode(curlCmd, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {}

        // Discrepancy Badges Bar
        sb.append("            <div class=\"diff-meta-bar\">\n");
        sb.append("              <div class=\"diff-meta-item\">Status: <strong>").append(record.getOriginalStatusCode())
                .append("</strong> &rarr; <strong>").append(result.getStatusCode()).append("</strong></div>\n");
        sb.append("              <div class=\"diff-meta-item\">Length: <strong>").append(record.getOriginalResponseLength())
                .append("B</strong> &rarr; <strong>").append(result.getResponseLength()).append("B</strong></div>\n");
        sb.append("              <div class=\"diff-meta-item\">Body Similarity: <strong>")
                .append(String.format(Locale.ROOT, "%.1f%%", result.getSimilarityScore() * 100.0)).append("</strong></div>\n");
        if (!curlCmd.isEmpty()) {
            sb.append("              <button type=\"button\" class=\"btn-curl\" data-curl=\"").append(escapeAttribute(encodedCurl))
                    .append("\" onclick=\"copyCurlCommand(this)\">&#x1F4CB; Copy cURL</button>\n");
        }
        sb.append("            </div>\n");

        // Side-by-Side Request Diff
        sb.append("            <h4 class=\"diff-subtitle\">HTTP Request Comparison</h4>\n");
        sb.append("            <div class=\"diff-grid\">\n");
        sb.append("              <div class=\"diff-col\">\n");
        sb.append("                <div class=\"diff-col-header\">Original Request (Baseline)</div>\n");
        sb.append("                <pre class=\"code-block\"><code>").append(escapeHtml(origReqStr)).append("</code></pre>\n");
        sb.append("              </div>\n");
        sb.append("              <div class=\"diff-col\">\n");
        sb.append("                <div class=\"diff-col-header\">Replayed Request (Role: ").append(escapeHtml(roleName)).append(")</div>\n");
        sb.append("                <pre class=\"code-block\"><code>").append(escapeHtml(replayReqStr)).append("</code></pre>\n");
        sb.append("              </div>\n");
        sb.append("            </div>\n");

        // Side-by-Side Response Diff
        sb.append("            <h4 class=\"diff-subtitle mt-12\">HTTP Response Comparison</h4>\n");
        sb.append("            <div class=\"diff-grid\">\n");
        sb.append("              <div class=\"diff-col\">\n");
        sb.append("                <div class=\"diff-col-header\">Original Response (Status ").append(record.getOriginalStatusCode()).append(")</div>\n");
        sb.append("                <pre class=\"code-block\"><code>").append(escapeHtml(truncateBody(origRespStr))).append("</code></pre>\n");
        sb.append("              </div>\n");
        sb.append("              <div class=\"diff-col\">\n");
        sb.append("                <div class=\"diff-col-header\">Replayed Response (Status ").append(result.getStatusCode()).append(")</div>\n");
        sb.append("                <pre class=\"code-block\"><code>").append(escapeHtml(truncateBody(replayRespStr))).append("</code></pre>\n");
        sb.append("              </div>\n");
        sb.append("            </div>\n");
    }

    private static void renderAiCard(StringBuilder sb, AiEvaluationResult aiResult) {
        if (aiResult == null) {
            sb.append("            <div class=\"ai-card ai-untested\">\n");
            sb.append("              <div class=\"ai-card-title\">AI Semantic Analysis: Not Run</div>\n");
            sb.append("              <p class=\"ai-card-desc\">This transaction was classified using deterministic heuristic differential rules (status code and Sorensen-Dice body similarity).</p>\n");
            sb.append("            </div>\n");
            return;
        }

        AiVerdict verdict = aiResult.getVerdict();
        String cardClass = verdict == AiVerdict.VALIDATED_ACCESS ? "ai-vulnerable" :
                (verdict == AiVerdict.ACCESS_DENIED ? "ai-denied" :
                (verdict == AiVerdict.INCONCLUSIVE ? "ai-inconclusive" : "ai-error"));

        sb.append("            <div class=\"ai-card ").append(cardClass).append("\">\n");
        sb.append("              <div class=\"ai-card-header\">\n");
        sb.append("                <div class=\"ai-verdict-title\">Verdict: <strong>").append(escapeHtml(verdict.getDisplayName())).append("</strong></div>\n");
        sb.append("                <div class=\"ai-meta-tags\">\n");
        sb.append("                  <span class=\"meta-tag\">Model: ").append(escapeHtml(aiResult.getModel())).append("</span>\n");
        sb.append("                  <span class=\"meta-tag\">Latency: ").append(aiResult.getExecutionTimeMs()).append(" ms</span>\n");
        sb.append("                  <span class=\"meta-tag\">").append(aiResult.isCached() ? "&#x26A1; Cached" : "&#x25CF; Live Evaluation").append("</span>\n");
        sb.append("                </div>\n");
        sb.append("              </div>\n");
        sb.append("              <div class=\"ai-reasoning-body\">\n");
        sb.append("                <div class=\"reasoning-heading\">AI Semantic Differential Analysis:</div>\n");
        sb.append("                <div class=\"reasoning-text\">").append(escapeHtml(aiResult.getReasoning())).append("</div>\n");
        sb.append("              </div>\n");
        sb.append("            </div>\n");
    }

    private static void renderNotesPane(StringBuilder sb, AuditRecord record, AuthorizationCheckResult result) {
        sb.append("            <div class=\"notes-pane\">\n");
        sb.append("              <div class=\"notes-section\">\n");
        sb.append("                <strong>Replay Notes:</strong> ").append(escapeHtml(result.getNotes() != null ? result.getNotes() : "None")).append("\n");
        sb.append("              </div>\n");
        sb.append("              <div class=\"notes-section\">\n");
        sb.append("                <strong>Execution Time:</strong> ").append(result.getExecutionTimeMs()).append(" ms\n");
        sb.append("              </div>\n");
        if (record.isPipelineExecution()) {
            sb.append("              <div class=\"notes-section\">\n");
            sb.append("                <strong>Pipeline Source:</strong> ").append(escapeHtml(record.getPipelineName() != null ? record.getPipelineName() : "Pipeline")).append("\n");
            if (record.getPipelineTrace() != null && !record.getPipelineTrace().isEmpty()) {
                sb.append("                <pre class=\"code-block\"><code>").append(escapeHtml(record.getPipelineTrace())).append("</code></pre>\n");
            }
            sb.append("              </div>\n");
        }
        sb.append("            </div>\n");
    }

    private static void renderFooter(StringBuilder sb, String timestamp) {
        sb.append("<footer class=\"report-footer\">\n");
        sb.append("  <p>Report generated by <strong>Thorization Burp Suite Access Control Testing Extension</strong> &bull; ")
                .append(escapeHtml(timestamp)).append("</p>\n");
        sb.append("  <p class=\"footer-sub\">Standalone offline HTML report with zero external CDN dependencies.</p>\n");
        sb.append("</footer>\n");
    }

    // --- Helper Utilities & XSS Defense Engine ---

    private static String truncateBody(String body) {
        if (body == null) return "";
        if (body.length() > 25000) {
            return body.substring(0, 20000) + "\n\n[... Truncated for report performance: Showing first 20,000 of "
                    + body.length() + " characters ...]";
        }
        return body;
    }

    /**
     * Strict HTML body context escaping: &amp;, &lt;, &gt;, &quot;, &#39;.
     */
    public static String escapeHtml(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;"); break;
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                default:   sb.append(c); break;
            }
        }
        return sb.toString();
    }

    /**
     * Strict HTML attribute context escaping, removing ASCII control characters.
     */
    public static String escapeAttribute(String input) {
        if (input == null) return "";
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            switch (c) {
                case '&':  sb.append("&amp;"); break;
                case '<':  sb.append("&lt;"); break;
                case '>':  sb.append("&gt;"); break;
                case '"':  sb.append("&quot;"); break;
                case '\'': sb.append("&#39;"); break;
                case '\n': sb.append("&#10;"); break;
                case '\r': sb.append("&#13;"); break;
                case '\t': sb.append("&#9;"); break;
                default:
                    if (c >= 32 && c != 127) {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }

    // --- Inline CSS Styles (Zero CDN Dependencies) ---

    private static void renderInlineStyles(StringBuilder sb) {
        sb.append("<style>\n");
        sb.append("/* Thorization Standalone Offline Report Stylesheet */\n");
        sb.append(":root {\n");
        sb.append("  --bg-main: #0d1117;\n");
        sb.append("  --bg-surface: #161b22;\n");
        sb.append("  --bg-card: #21262d;\n");
        sb.append("  --bg-hover: #1f242c;\n");
        sb.append("  --border-color: #30363d;\n");
        sb.append("  --text-main: #e6edf3;\n");
        sb.append("  --text-muted: #8b949e;\n");
        sb.append("  --color-green: #3fb950;\n");
        sb.append("  --color-red: #f85149;\n");
        sb.append("  --color-orange: #d29922;\n");
        sb.append("  --color-blue: #58a6ff;\n");
        sb.append("  --color-purple: #bc8cff;\n");
        sb.append("  --font-sans: -apple-system, BlinkMacSystemFont, 'Segoe UI', Helvetica, Arial, sans-serif;\n");
        sb.append("  --font-mono: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, 'Liberation Mono', monospace;\n");
        sb.append("}\n");
        sb.append("* { box-sizing: border-box; margin: 0; padding: 0; }\n");
        sb.append("body { background-color: var(--bg-main); color: var(--text-main); font-family: var(--font-sans); line-height: 1.5; padding: 24px 16px; }\n");
        sb.append(".report-container { max-width: 1440px; margin: 0 auto; }\n");

        // Header
        sb.append(".report-header { display: flex; justify-content: space-between; align-items: center; padding-bottom: 24px; border-bottom: 1px solid var(--border-color); margin-bottom: 24px; }\n");
        sb.append(".brand-badge { display: inline-block; font-size: 11px; font-weight: 700; background: rgba(188, 140, 255, 0.15); color: var(--color-purple); border: 1px solid var(--color-purple); padding: 2px 8px; border-radius: 12px; margin-bottom: 8px; }\n");
        sb.append(".report-title { font-size: 26px; font-weight: 700; color: #fff; margin-bottom: 4px; }\n");
        sb.append(".report-meta { font-size: 13px; color: var(--text-muted); }\n");
        sb.append(".posture-score-box { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; padding: 12px 20px; text-align: center; }\n");
        sb.append(".score-label { font-size: 11px; text-transform: uppercase; letter-spacing: 0.5px; color: var(--text-muted); margin-bottom: 4px; }\n");
        sb.append(".score-value { font-size: 28px; font-weight: 800; }\n");
        sb.append(".score-good { color: var(--color-green); }\n");
        sb.append(".score-warn { color: var(--color-orange); }\n");
        sb.append(".score-bad { color: var(--color-red); }\n");

        // KPI Dashboard Grid
        sb.append(".dashboard-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; margin-bottom: 20px; }\n");
        sb.append(".metric-card { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; padding: 16px; }\n");
        sb.append(".metric-header { font-size: 12px; font-weight: 600; color: var(--text-muted); text-transform: uppercase; margin-bottom: 8px; }\n");
        sb.append(".metric-value { font-size: 32px; font-weight: 800; line-height: 1.1; margin-bottom: 6px; }\n");
        sb.append(".metric-sub { font-size: 12px; color: var(--text-muted); }\n");
        sb.append(".text-green { color: var(--color-green); }\n");
        sb.append(".text-red { color: var(--color-red); }\n");
        sb.append(".text-orange { color: var(--color-orange); }\n");
        sb.append(".ai-metrics-row { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 6px; }\n");
        sb.append(".mt-4 { margin-top: 4px; }\n");
        sb.append(".mt-12 { margin-top: 12px; }\n");

        // Progress Bar
        sb.append(".progress-section { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; padding: 14px 16px; margin-bottom: 24px; }\n");
        sb.append(".progress-label-bar { display: flex; justify-content: space-between; font-size: 12px; font-weight: 600; margin-bottom: 8px; color: var(--text-muted); }\n");
        sb.append(".stacked-progress-bar { display: flex; height: 12px; border-radius: 6px; overflow: hidden; background: #2d333b; }\n");
        sb.append(".progress-segment { height: 100%; transition: width 0.3s ease; }\n");
        sb.append(".bg-green { background: var(--color-green); }\n");
        sb.append(".bg-red { background: var(--color-red); }\n");
        sb.append(".bg-orange { background: var(--color-orange); }\n");
        sb.append(".bg-muted { background: var(--border-color); }\n");

        // Filter Toolbar
        sb.append(".filter-toolbar { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; padding: 12px 16px; margin-bottom: 20px; }\n");
        sb.append(".filter-group { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--text-muted); }\n");
        sb.append(".search-group { flex: 1; min-width: 250px; }\n");
        sb.append(".filter-input { width: 100%; background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-main); padding: 7px 12px; border-radius: 6px; font-size: 13px; outline: none; }\n");
        sb.append(".filter-input:focus { border-color: var(--color-blue); }\n");
        sb.append(".filter-select { background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-main); padding: 7px 10px; border-radius: 6px; font-size: 13px; outline: none; cursor: pointer; }\n");
        sb.append(".filter-actions { display: flex; align-items: center; gap: 12px; margin-left: auto; }\n");
        sb.append(".findings-counter { font-size: 13px; color: var(--text-muted); }\n");
        sb.append(".btn-reset { background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-main); padding: 6px 12px; border-radius: 6px; font-size: 12px; font-weight: 600; cursor: pointer; }\n");
        sb.append(".btn-reset:hover { background: var(--bg-hover); }\n");

        // Table
        sb.append(".table-card { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; overflow: hidden; margin-bottom: 32px; }\n");
        sb.append(".findings-table { width: 100%; border-collapse: collapse; font-size: 13px; text-align: left; }\n");
        sb.append(".findings-table th { background: #1c2128; color: var(--text-muted); font-weight: 600; padding: 10px 14px; border-bottom: 1px solid var(--border-color); user-select: none; }\n");
        sb.append(".findings-table th.sortable { cursor: pointer; }\n");
        sb.append(".findings-table th.sortable:hover { color: #fff; }\n");
        sb.append(".findings-table td { padding: 10px 14px; border-bottom: 1px solid var(--border-color); vertical-align: middle; }\n");
        sb.append(".finding-row:hover { background: var(--bg-hover); }\n");
        sb.append(".col-id { width: 45px; color: var(--text-muted); font-family: var(--font-mono); font-size: 12px; }\n");
        sb.append(".col-method { width: 75px; }\n");
        sb.append(".col-url { max-width: 380px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n");
        sb.append(".url-text { font-family: var(--font-mono); font-size: 12px; color: var(--color-blue); }\n");
        sb.append(".col-orig-status, .col-replay-status { font-family: var(--font-mono); font-size: 12px; }\n");
        sb.append(".len-muted { color: var(--text-muted); font-size: 11px; }\n");
        sb.append(".role-tag { display: inline-block; background: rgba(56, 139, 253, 0.12); color: var(--color-blue); border: 1px solid rgba(56, 139, 253, 0.4); padding: 1px 7px; border-radius: 10px; font-size: 11px; font-weight: 600; }\n");

        // Badges
        sb.append(".badge { display: inline-block; padding: 2px 8px; border-radius: 12px; font-size: 11px; font-weight: 700; }\n");
        sb.append(".badge-enforced { background: rgba(46, 160, 67, 0.15); color: var(--color-green); border: 1px solid var(--color-green); }\n");
        sb.append(".badge-bypass { background: rgba(248, 81, 73, 0.15); color: var(--color-red); border: 1px solid var(--color-red); }\n");
        sb.append(".badge-indet { background: rgba(210, 153, 34, 0.15); color: var(--color-orange); border: 1px solid var(--color-orange); }\n");
        sb.append(".badge-ai-val { background: rgba(248, 81, 73, 0.15); color: var(--color-red); border: 1px solid var(--color-red); padding: 2px 6px; border-radius: 10px; font-size: 10px; font-weight: 700; }\n");
        sb.append(".badge-ai-denied { background: rgba(46, 160, 67, 0.15); color: var(--color-green); border: 1px solid var(--color-green); padding: 2px 6px; border-radius: 10px; font-size: 10px; font-weight: 700; }\n");
        sb.append(".badge-ai-incon { background: rgba(210, 153, 34, 0.15); color: var(--color-orange); border: 1px solid var(--color-orange); padding: 2px 6px; border-radius: 10px; font-size: 10px; font-weight: 700; }\n");
        sb.append(".badge-ai-error { background: rgba(178, 34, 34, 0.2); color: #ff8080; border: 1px solid #b22222; padding: 2px 6px; border-radius: 10px; font-size: 10px; font-weight: 700; }\n");
        sb.append(".method-badge { display: inline-block; font-size: 11px; font-weight: 700; padding: 1px 6px; border-radius: 4px; font-family: var(--font-mono); }\n");
        sb.append(".method-get { background: rgba(56, 139, 253, 0.15); color: var(--color-blue); }\n");
        sb.append(".method-post { background: rgba(46, 160, 67, 0.15); color: var(--color-green); }\n");
        sb.append(".method-put { background: rgba(210, 153, 34, 0.15); color: var(--color-orange); }\n");
        sb.append(".method-delete { background: rgba(248, 81, 73, 0.15); color: var(--color-red); }\n");
        sb.append(".method-patch { background: rgba(188, 140, 255, 0.15); color: var(--color-purple); }\n");

        // Action Buttons
        sb.append(".btn-toggle-drawer { background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-main); font-size: 11px; font-weight: 600; padding: 4px 10px; border-radius: 4px; cursor: pointer; }\n");
        sb.append(".btn-toggle-drawer:hover { background: var(--bg-hover); border-color: var(--color-blue); }\n");
        sb.append(".btn-curl { background: #238636; color: #fff; border: 1px solid rgba(240, 246, 252, 0.1); font-size: 11px; font-weight: 600; padding: 4px 10px; border-radius: 4px; cursor: pointer; transition: background 0.2s; }\n");
        sb.append(".btn-curl:hover { background: #2ea043; }\n");
        sb.append(".btn-curl.copied { background: #1f6feb !important; }\n");

        // Drawer Content
        sb.append(".drawer-cell { background: #111418; padding: 16px 20px !important; }\n");
        sb.append(".drawer-tabs { display: flex; gap: 8px; border-bottom: 1px solid var(--border-color); padding-bottom: 8px; margin-bottom: 14px; }\n");
        sb.append(".tab-btn { background: transparent; border: none; color: var(--text-muted); font-size: 12px; font-weight: 600; padding: 6px 12px; border-radius: 6px; cursor: pointer; }\n");
        sb.append(".tab-btn:hover { color: var(--text-main); background: var(--bg-card); }\n");
        sb.append(".tab-btn.active { color: #fff; background: var(--bg-card); border-bottom: 2px solid var(--color-blue); }\n");
        sb.append(".diff-meta-bar { display: flex; align-items: center; gap: 16px; background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 6px; padding: 8px 12px; font-size: 12px; margin-bottom: 12px; }\n");
        sb.append(".diff-meta-item { color: var(--text-muted); }\n");
        sb.append(".diff-meta-item strong { color: var(--text-main); }\n");
        sb.append(".diff-subtitle { font-size: 12px; font-weight: 700; color: var(--text-muted); text-transform: uppercase; margin-bottom: 6px; }\n");
        sb.append(".diff-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }\n");
        sb.append(".diff-col { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 6px; overflow: hidden; }\n");
        sb.append(".diff-col-header { background: #1c2128; font-size: 11px; font-weight: 700; color: var(--text-muted); padding: 6px 10px; border-bottom: 1px solid var(--border-color); }\n");
        sb.append(".code-block { font-family: var(--font-mono); font-size: 11px; line-height: 1.45; padding: 10px; color: #c9d1d9; max-height: 350px; overflow: auto; white-space: pre-wrap; word-break: break-all; }\n");

        // AI Card
        sb.append(".ai-card { background: var(--bg-surface); border: 1px solid var(--border-color); border-radius: 8px; padding: 16px; }\n");
        sb.append(".ai-vulnerable { border-left: 4px solid var(--color-red); }\n");
        sb.append(".ai-denied { border-left: 4px solid var(--color-green); }\n");
        sb.append(".ai-inconclusive { border-left: 4px solid var(--color-orange); }\n");
        sb.append(".ai-error { border-left: 4px solid #b22222; }\n");
        sb.append(".ai-card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }\n");
        sb.append(".ai-verdict-title { font-size: 14px; color: #fff; }\n");
        sb.append(".ai-meta-tags { display: flex; gap: 8px; }\n");
        sb.append(".meta-tag { font-size: 11px; background: var(--bg-card); border: 1px solid var(--border-color); color: var(--text-muted); padding: 2px 8px; border-radius: 4px; }\n");
        sb.append(".reasoning-heading { font-size: 12px; font-weight: 700; color: var(--text-muted); margin-bottom: 6px; }\n");
        sb.append(".reasoning-text { font-family: var(--font-sans); font-size: 13px; color: #e6edf3; line-height: 1.6; white-space: pre-wrap; background: #0e1217; border: 1px solid var(--border-color); border-radius: 6px; padding: 12px; }\n");

        // Footer
        sb.append(".report-footer { text-align: center; font-size: 12px; color: var(--text-muted); padding-top: 24px; border-top: 1px solid var(--border-color); }\n");
        sb.append(".footer-sub { font-size: 11px; margin-top: 4px; color: #6e7681; }\n");
        sb.append("</style>\n");
    }

    // --- Inline Vanilla JavaScript (Zero CDN Dependencies) ---

    private static void renderInlineScripts(StringBuilder sb) {
        sb.append("<script>\n");
        sb.append("/* Thorization Standalone Offline Interactive Controller */\n");
        sb.append("function applyFilters() {\n");
        sb.append("  const query = document.getElementById('search-input').value.toLowerCase().trim();\n");
        sb.append("  const role = document.getElementById('role-filter').value;\n");
        sb.append("  const verdict = document.getElementById('verdict-filter').value;\n");
        sb.append("  const ai = document.getElementById('ai-filter').value;\n");
        sb.append("  const method = document.getElementById('method-filter').value;\n");
        sb.append("  \n");
        sb.append("  const rows = document.querySelectorAll('.finding-row');\n");
        sb.append("  let visibleCount = 0;\n");
        sb.append("  \n");
        sb.append("  rows.forEach(row => {\n");
        sb.append("    const rowMethod = row.getAttribute('data-method');\n");
        sb.append("    const rowRole = row.getAttribute('data-role');\n");
        sb.append("    const rowVerdict = row.getAttribute('data-verdict');\n");
        sb.append("    const rowAi = row.getAttribute('data-ai');\n");
        sb.append("    const rowSearch = row.getAttribute('data-search');\n");
        sb.append("    const drawerId = 'drawer-' + row.id.split('-')[1];\n");
        sb.append("    const drawer = document.getElementById(drawerId);\n");
        sb.append("    \n");
        sb.append("    let match = true;\n");
        sb.append("    if (method !== 'ALL' && rowMethod !== method) match = false;\n");
        sb.append("    if (role !== 'ALL' && rowRole !== role) match = false;\n");
        sb.append("    if (verdict !== 'ALL' && rowVerdict !== verdict) match = false;\n");
        sb.append("    if (ai !== 'ALL' && rowAi !== ai) match = false;\n");
        sb.append("    if (query.length > 0 && (!rowSearch || !rowSearch.includes(query))) match = false;\n");
        sb.append("    \n");
        sb.append("    if (match) {\n");
        sb.append("      row.style.display = '';\n");
        sb.append("      visibleCount++;\n");
        sb.append("    } else {\n");
        sb.append("      row.style.display = 'none';\n");
        sb.append("      if (drawer) drawer.style.display = 'none';\n");
        sb.append("    }\n");
        sb.append("  });\n");
        sb.append("  \n");
        sb.append("  const visibleCounter = document.getElementById('visible-count');\n");
        sb.append("  if (visibleCounter) visibleCounter.textContent = visibleCount;\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("function resetFilters() {\n");
        sb.append("  document.getElementById('search-input').value = '';\n");
        sb.append("  document.getElementById('role-filter').value = 'ALL';\n");
        sb.append("  document.getElementById('verdict-filter').value = 'ALL';\n");
        sb.append("  document.getElementById('ai-filter').value = 'ALL';\n");
        sb.append("  document.getElementById('method-filter').value = 'ALL';\n");
        sb.append("  applyFilters();\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("function toggleDrawer(drawerId) {\n");
        sb.append("  const drawer = document.getElementById(drawerId);\n");
        sb.append("  if (!drawer) return;\n");
        sb.append("  drawer.style.display = (drawer.style.display === 'none' || drawer.style.display === '') ? 'table-row' : 'none';\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("function switchTab(btn, paneId) {\n");
        sb.append("  const container = btn.closest('.drawer-content');\n");
        sb.append("  container.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));\n");
        sb.append("  container.querySelectorAll('.tab-pane').forEach(p => p.style.display = 'none');\n");
        sb.append("  btn.classList.add('active');\n");
        sb.append("  const target = document.getElementById(paneId);\n");
        sb.append("  if (target) target.style.display = 'block';\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("function copyCurlCommand(btn) {\n");
        sb.append("  const raw = btn.getAttribute('data-curl');\n");
        sb.append("  if (!raw) return;\n");
        sb.append("  const cmd = decodeURIComponent(raw);\n");
        sb.append("  if (navigator.clipboard && window.isSecureContext) {\n");
        sb.append("    navigator.clipboard.writeText(cmd).then(() => showCopied(btn)).catch(() => fallbackCopy(btn, cmd));\n");
        sb.append("  } else {\n");
        sb.append("    fallbackCopy(btn, cmd);\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("function fallbackCopy(btn, text) {\n");
        sb.append("  const ta = document.createElement('textarea');\n");
        sb.append("  ta.value = text;\n");
        sb.append("  ta.style.position = 'fixed';\n");
        sb.append("  ta.style.left = '-9999px';\n");
        sb.append("  document.body.appendChild(ta);\n");
        sb.append("  ta.focus();\n");
        sb.append("  ta.select();\n");
        sb.append("  try { document.execCommand('copy'); showCopied(btn); } catch (e) { console.error('Copy failed', e); }\n");
        sb.append("  document.body.removeChild(ta);\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("function showCopied(btn) {\n");
        sb.append("  const orig = btn.innerHTML;\n");
        sb.append("  btn.innerHTML = '&#x2713; Copied!';\n");
        sb.append("  btn.classList.add('copied');\n");
        sb.append("  setTimeout(() => { btn.innerHTML = orig; btn.classList.remove('copied'); }, 2000);\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("let sortDirections = {};\n");
        sb.append("function sortTable(colIndex, isNumeric) {\n");
        sb.append("  const tbody = document.getElementById('findings-tbody');\n");
        sb.append("  const rows = Array.from(tbody.querySelectorAll('.finding-row'));\n");
        sb.append("  const dir = sortDirections[colIndex] === 'asc' ? 'desc' : 'asc';\n");
        sb.append("  sortDirections[colIndex] = dir;\n");
        sb.append("  \n");
        sb.append("  rows.sort((a, b) => {\n");
        sb.append("    let aVal = a.children[colIndex].textContent.trim();\n");
        sb.append("    let bVal = b.children[colIndex].textContent.trim();\n");
        sb.append("    if (isNumeric) {\n");
        sb.append("      let aNum = parseFloat(aVal.replace(/[^0-9.-]/g, '')) || 0;\n");
        sb.append("      let bNum = parseFloat(bVal.replace(/[^0-9.-]/g, '')) || 0;\n");
        sb.append("      return dir === 'asc' ? aNum - bNum : bNum - aNum;\n");
        sb.append("    }\n");
        sb.append("    return dir === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal);\n");
        sb.append("  });\n");
        sb.append("  \n");
        sb.append("  rows.forEach(r => {\n");
        sb.append("    const drawerId = 'drawer-' + r.id.split('-')[1];\n");
        sb.append("    const drawer = document.getElementById(drawerId);\n");
        sb.append("    tbody.appendChild(r);\n");
        sb.append("    if (drawer) tbody.appendChild(drawer);\n");
        sb.append("  });\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("document.addEventListener('DOMContentLoaded', () => {\n");
        sb.append("  const total = document.querySelectorAll('.finding-row').length;\n");
        sb.append("  const totalElem = document.getElementById('total-count');\n");
        sb.append("  const visibleElem = document.getElementById('visible-count');\n");
        sb.append("  if (totalElem) totalElem.textContent = total;\n");
        sb.append("  if (visibleElem) visibleElem.textContent = total;\n");
        sb.append("});\n");
        sb.append("</script>\n");
    }
}
