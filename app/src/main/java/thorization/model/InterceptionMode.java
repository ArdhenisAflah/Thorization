package thorization.model;

/**
 * Defines how Thorization handles intercepted state-changing traffic (USP 1).
 */
public enum InterceptionMode {
    DROP_ORIGINAL_AND_REPLAY("Drop Original & Replay (Safe)"),
    REPLAY_FIRST_THEN_ALLOW("Replay First, Then Allow Original"),
    PASS_THROUGH_AND_REPLAY("Pass Through & Replay (Standard)");

    private final String displayName;

    InterceptionMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

