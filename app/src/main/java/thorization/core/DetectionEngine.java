package thorization.core;

import burp.api.montoya.http.message.responses.HttpResponse;
import thorization.model.Verdict;
import thorization.util.DiffUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Hybrid detection engine that evaluates authorization verdicts using status codes,
 * body length difference thresholds, error/forbidden signatures, and similarity scoring.
 */
public class DetectionEngine {

    private double lengthDiffThresholdPercent = 5.0;
    private double similarityThreshold = 0.85;

    private static final List<String> DEFAULT_ERROR_SIGNATURES = Arrays.asList(
            "unauthorized", "forbidden", "access denied", "permission denied",
            "not authorized", "invalid token", "authentication required",
            "session expired", "token expired", "login required",
            "\"status\":401", "\"status\":403", "\"error\":\"unauthorized\"",
            "\"error\":\"forbidden\""
    );

    public DetectionEngine() {
    }

    public DetectionEngine(double lengthDiffThresholdPercent, double similarityThreshold) {
        this.lengthDiffThresholdPercent = lengthDiffThresholdPercent;
        this.similarityThreshold = similarityThreshold;
    }

    /**
     * Evaluates original vs replayed response and determines the Verdict.
     */
    public EvaluationResult evaluate(HttpResponse originalResponse, HttpResponse replayedResponse) {
        if (replayedResponse == null) {
            return new EvaluationResult(Verdict.INDETERMINABLE, 0, 0.0, "Replayed response was null / connection dropped.");
        }

        int originalStatus = originalResponse != null ? originalResponse.statusCode() : 0;
        int replayedStatus = replayedResponse.statusCode();

        String originalBody = originalResponse != null && originalResponse.body() != null ? originalResponse.bodyToString() : "";
        String replayedBody = replayedResponse.body() != null ? replayedResponse.bodyToString() : "";

        int originalLen = originalBody.length();
        int replayedLen = replayedBody.length();

        double lengthDiffPercent = DiffUtils.calculateLengthDiffPercent(originalLen, replayedLen);
        double similarity = DiffUtils.computeSimilarity(originalBody, replayedBody);

        // 1. Explicit Access Denied HTTP Status Codes -> ENFORCED
        if (replayedStatus == 401 || replayedStatus == 403) {
            return new EvaluationResult(Verdict.ENFORCED, replayedStatus, similarity,
                    "Enforced: HTTP " + replayedStatus + " (Access Denied).");
        }

        // 2. Check for explicit error/unauthorized string signatures in replayed body
        String replayedLower = replayedBody.toLowerCase();
        for (String sig : DEFAULT_ERROR_SIGNATURES) {
            if (replayedLower.contains(sig)) {
                return new EvaluationResult(Verdict.ENFORCED, replayedStatus, similarity,
                        "Enforced: Response body matched error signature '" + sig + "'.");
            }
        }

        // 3. Status Code Match & High Similarity & Low Length Diff -> BYPASS
        if (originalStatus >= 200 && originalStatus < 300 && replayedStatus == originalStatus) {
            if (lengthDiffPercent <= lengthDiffThresholdPercent || similarity >= similarityThreshold) {
                return new EvaluationResult(Verdict.BYPASS, replayedStatus, similarity,
                        String.format("Vulnerable: HTTP %d, length diff %.1f%%, similarity %.2f.",
                                replayedStatus, lengthDiffPercent, similarity));
            }
        }

        // 4. If status codes are both 2xx but body differs significantly without explicit error -> INDETERMINABLE
        if (replayedStatus >= 200 && replayedStatus < 300) {
            return new EvaluationResult(Verdict.INDETERMINABLE, replayedStatus, similarity,
                    String.format("Manual Review: HTTP %d matched, but body changed (len diff %.1f%%, sim %.2f).",
                            replayedStatus, lengthDiffPercent, similarity));
        }

        // 5. Default fallback
        return new EvaluationResult(Verdict.INDETERMINABLE, replayedStatus, similarity,
                "Manual Review: Status changed from " + originalStatus + " to " + replayedStatus + ".");
    }

    public static class EvaluationResult {
        private final Verdict verdict;
        private final int statusCode;
        private final double similarityScore;
        private final String note;

        public EvaluationResult(Verdict verdict, int statusCode, double similarityScore, String note) {
            this.verdict = verdict;
            this.statusCode = statusCode;
            this.similarityScore = similarityScore;
            this.note = note;
        }

        public Verdict getVerdict() {
            return verdict;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public double getSimilarityScore() {
            return similarityScore;
        }

        public String getNote() {
            return note;
        }
    }
}

