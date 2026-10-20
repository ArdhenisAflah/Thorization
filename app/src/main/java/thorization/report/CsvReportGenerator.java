package thorization.report;

import burp.api.montoya.http.message.requests.HttpRequest;
import thorization.ai.AiEvaluationResult;
import thorization.model.AuditRecord;
import thorization.model.AuthorizationCheckResult;
import thorization.model.UserRole;
import thorization.util.CurlUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * RFC 4180 compliant CSV export generator for Thorization access control audit records.
 */
public final class CsvReportGenerator {

    public static final String CRLF = "\r\n";

    static final String[] HEADERS = {
            "Request_ID",
            "Timestamp",
            "Host",
            "Method",
            "Path",
            "Original_Status",
            "Original_Length",
            "Role_Name",
            "Role_Status",
            "Replayed_Status",
            "Replayed_Length",
            "Enforced_Status",
            "AI_Verdict",
            "AI_Confidence",
            "AI_Reasoning",
            "cURL_Command"
    };

    public static List<String> getHeaders() {
        return List.of(HEADERS);
    }

    private CsvReportGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Primary entry point matching DISPATCH requirement: writeCsvReport.
     */
    public static void writeCsvReport(File targetFile, List<AuditRecord> records, List<UserRole> roles) throws IOException {
        exportCsv(targetFile, records, roles);
    }

    /**
     * Entry point matching PROJECT.md interface contract: exportCsv.
     */
    public static void exportCsv(File file, List<AuditRecord> records, List<UserRole> roles) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Target file cannot be null");
        }
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create parent directories for: " + file.getAbsolutePath());
        }
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
            exportCsv(writer, records, roles);
        }
    }

    public static String generateCsv(List<AuditRecord> records, List<UserRole> roles) {
        StringWriter sw = new StringWriter();
        try {
            exportCsv(sw, records, roles);
        } catch (IOException ignored) {
            // StringWriter does not throw IOException
        }
        return sw.toString();
    }

    public static void exportCsv(Writer writer, List<AuditRecord> records, List<UserRole> roles) throws IOException {
        if (writer == null) {
            throw new IllegalArgumentException("Writer cannot be null");
        }

        // 1. Write Header Row
        writeRow(writer, List.of(HEADERS));

        // 2. Write Data Rows
        if (records != null && !records.isEmpty()) {
            for (AuditRecord record : records) {
                if (record != null) {
                    writeRecordRows(writer, record, roles);
                }
            }
        }
        writer.flush();
    }

    private static void writeRecordRows(Writer writer, AuditRecord record, List<UserRole> roles) throws IOException {
        String reqId = String.valueOf(record.getId());
        String timestamp = record.getTimestamp() != null ? record.getTimestamp() : "";
        String host = extractHost(record);
        String method = record.getMethod() != null ? record.getMethod() : "GET";
        String path = record.getPath() != null ? record.getPath() : "/";
        String origStatus = String.valueOf(record.getOriginalStatusCode());
        String origLength = String.valueOf(record.getOriginalResponseLength());

        boolean hasRoleResults = false;

        // Iterate through configured roles
        if (roles != null && !roles.isEmpty()) {
            for (UserRole role : roles) {
                if (role == null) continue;
                AuthorizationCheckResult res = record.getRoleResults() != null ? record.getRoleResults().get(role.getId()) : null;
                if (res != null || role.isEnabled()) {
                    hasRoleResults = true;
                    writeSingleResultRow(writer, reqId, timestamp, host, method, path, origStatus, origLength,
                            role.getName(),
                            role.isEnabled() ? "Active" : "Disabled",
                            res, record, role);
                }
            }
        } else if (record.getRoleResults() != null && !record.getRoleResults().isEmpty()) {
            // Roles not explicitly provided, extract from record map
            for (AuthorizationCheckResult res : record.getRoleResults().values()) {
                if (res == null) continue;
                hasRoleResults = true;
                writeSingleResultRow(writer, reqId, timestamp, host, method, path, origStatus, origLength,
                        res.getRoleName() != null ? res.getRoleName() : res.getRoleId(),
                        "Active",
                        res, record, null);
            }
        }

        // Unauthenticated check
        if (record.getUnauthenticatedResult() != null) {
            hasRoleResults = true;
            writeSingleResultRow(writer, reqId, timestamp, host, method, path, origStatus, origLength,
                    "Unauthenticated",
                    "Active",
                    record.getUnauthenticatedResult(), record, null);
        }

        // Baseline row if no roles were audited
        if (!hasRoleResults) {
            List<String> row = new ArrayList<>(16);
            row.add(reqId);
            row.add(timestamp);
            row.add(host);
            row.add(method);
            row.add(path);
            row.add(origStatus);
            row.add(origLength);
            row.add("Original");
            row.add("N/A");
            row.add(""); // Replayed_Status
            row.add(""); // Replayed_Length
            row.add("N/A"); // Enforced_Status
            row.add(record.getOverallAiVerdict() != null ? record.getOverallAiVerdict().name() : "");
            row.add(extractConfidence(record.getAiResult()));
            row.add(record.getAiResult() != null ? record.getAiResult().reasoning() : "");
            row.add(resolveCurl(record.getOriginalRequest(), record, null));
            writeRow(writer, row);
        }
    }

    private static void writeSingleResultRow(Writer writer, String reqId, String timestamp, String host,
                                             String method, String path, String origStatus, String origLength,
                                             String roleName, String roleStatus,
                                             AuthorizationCheckResult res, AuditRecord record, UserRole role) throws IOException {
        List<String> row = new ArrayList<>(16);
        row.add(reqId);
        row.add(timestamp);
        row.add(host);
        row.add(method);
        row.add(path);
        row.add(origStatus);
        row.add(origLength);
        row.add(roleName != null ? roleName : "Unknown");
        row.add(roleStatus);
        row.add(res != null ? String.valueOf(res.getStatusCode()) : "");
        row.add(res != null ? String.valueOf(res.getResponseLength()) : "");
        row.add(res != null && res.getVerdict() != null ? res.getVerdict().name() : "N/A");

        AiEvaluationResult ai = res != null && res.getAiResult() != null ? res.getAiResult() : record.getAiResult();
        row.add(ai != null && ai.getVerdict() != null ? ai.getVerdict().name() : "");
        row.add(extractConfidence(ai));
        row.add(ai != null ? ai.reasoning() : "");

        HttpRequest curlReq = res != null ? res.getRequest() : null;
        row.add(resolveCurl(curlReq, record, role));

        writeRow(writer, row);
    }

    private static void writeRow(Writer writer, List<String> fields) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escapeCsvField(fields.get(i)));
        }
        sb.append(CRLF);
        writer.write(sb.toString());
    }

    /**
     * Strictly complies with RFC 4180:
     * - Encloses in quotes if the field contains comma, quote, CRLF, or LF.
     * - Escapes internal quotes as doubled quotes ("").
     */
    public static String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        boolean needsQuotes = false;
        int len = field.length();
        for (int i = 0; i < len; i++) {
            char c = field.charAt(i);
            if (c == ',' || c == '"' || c == '\r' || c == '\n') {
                needsQuotes = true;
                break;
            }
        }
        if (!needsQuotes) {
            return field;
        }

        StringBuilder sb = new StringBuilder(len + 16);
        sb.append('"');
        for (int i = 0; i < len; i++) {
            char c = field.charAt(i);
            if (c == '"') {
                sb.append("\"\"");
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public static String extractHost(AuditRecord record) {
        if (record == null) return "";
        try {
            if (record.getOriginalRequest() != null && record.getOriginalRequest().httpService() != null) {
                String h = record.getOriginalRequest().httpService().host();
                if (h != null && !h.isEmpty()) return h;
            }
        } catch (Throwable ignored) {}

        String url = record.getUrl();
        if (url != null && !url.isEmpty()) {
            try {
                URI uri = URI.create(url);
                if (uri.getHost() != null) return uri.getHost();
            } catch (Throwable ignored) {}
            Matcher m = Pattern.compile("^(?:[a-zA-Z]+://)?([^/:]+)").matcher(url);
            if (m.find()) {
                return m.group(1);
            }
        }
        return "";
    }

    public static String extractConfidence(AiEvaluationResult aiResult) {
        if (aiResult == null || aiResult.getVerdict() == null) {
            return "";
        }
        try {
            var m = aiResult.getClass().getMethod("confidence");
            Object val = m.invoke(aiResult);
            if (val != null) return String.valueOf(val);
        } catch (Throwable ignored) {}

        if (aiResult.reasoning() != null && aiResult.reasoning().contains("confidence")) {
            Matcher matcher = Pattern.compile("confidence[\"':\\s]+([0-9.]+)").matcher(aiResult.reasoning());
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return "N/A";
    }

    private static String resolveCurl(HttpRequest request, AuditRecord record, UserRole role) {
        try {
            if (request != null) {
                return CurlUtils.generateCurlSingleLine(request);
            }
            if (record != null && role != null) {
                return thorization.report.CurlUtils.generateCurl(record, role, false, false);
            }
            if (record != null && record.getOriginalRequest() != null) {
                return CurlUtils.generateCurlSingleLine(record.getOriginalRequest());
            }
        } catch (Throwable ignored) {}
        return "";
    }
}
