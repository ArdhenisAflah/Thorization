package thorization.ai;

/**
 * Immutable assessment record holding the AI semantic analysis outcome.
 */
public record AiEvaluationResult(
        AiVerdict verdict,
        String reasoning,
        String model,
        long executionTimeMs,
        long timestamp,
        boolean cached
) {

    public AiEvaluationResult {
        if (verdict == null) {
            verdict = AiVerdict.INCONCLUSIVE;
        }
        if (reasoning == null) {
            reasoning = "";
        }
        if (model == null) {
            model = "unknown";
        }
    }

    public static AiEvaluationResult pending(String model) {
        return new AiEvaluationResult(
                AiVerdict.PENDING,
                "Analysis in progress...",
                model != null ? model : "unknown",
                0L,
                System.currentTimeMillis(),
                false
        );
    }

    public static AiEvaluationResult success(AiVerdict verdict, String reasoning, String model, long executionTimeMs) {
        return new AiEvaluationResult(
                verdict != null ? verdict : AiVerdict.INCONCLUSIVE,
                reasoning != null ? reasoning : "",
                model != null ? model : "unknown",
                Math.max(0L, executionTimeMs),
                System.currentTimeMillis(),
                false
        );
    }

    public static AiEvaluationResult error(String errorMessage, String model, long executionTimeMs) {
        return new AiEvaluationResult(
                AiVerdict.ERROR,
                errorMessage != null ? errorMessage : "Unknown error during AI evaluation",
                model != null ? model : "unknown",
                Math.max(0L, executionTimeMs),
                System.currentTimeMillis(),
                false
        );
    }

    public AiEvaluationResult withCached(boolean isCached) {
        return new AiEvaluationResult(
                this.verdict,
                this.reasoning,
                this.model,
                0L,
                this.timestamp,
                isCached
        );
    }

    // JavaBeans-compatible getter aliases for Swing UI, TableModel, and Report Generators
    public AiVerdict getVerdict() {
        return verdict;
    }

    public String getReasoning() {
        return reasoning;
    }

    public String getModel() {
        return model;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isCached() {
        return cached;
    }
}
