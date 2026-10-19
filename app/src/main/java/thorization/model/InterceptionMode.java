package thorization.model;

/**
 * Defines how Thorization handles intercepted state-changing traffic.
 */
public enum InterceptionMode {
    DROP_ORIGINAL_AND_REPLAY("Drop Original (Safe)"),
    REPLAY_FIRST_THEN_ALLOW("Replay First, Then Allow"),
    PASS_THROUGH_AND_REPLAY("Pass Through (Standard)");

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

