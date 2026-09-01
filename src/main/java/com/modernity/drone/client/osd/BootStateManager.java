package com.modernity.drone.client.osd;

/** Exact 1.1.4 OSD boot timing: logo for 3 s, calibration transition for 1 s. */
public final class BootStateManager {
    public static final long LOGO_DURATION_MILLIS = 3_000L;
    public static final long TRANSITION_DURATION_MILLIS = 1_000L;
    private static final BootStateManager INSTANCE = new BootStateManager();

    private long startedAtMillis;
    private boolean active;

    public static BootStateManager getInstance() { return INSTANCE; }

    public void start() { start(System.currentTimeMillis()); }

    public void start(long nowMillis) {
        startedAtMillis = nowMillis;
        active = true;
    }

    public void reset() {
        startedAtMillis = 0L;
        active = false;
    }

    public Phase phase(long nowMillis) {
        if (!active) {
            return Phase.IDLE;
        }
        long elapsed = Math.max(0L, nowMillis - startedAtMillis);
        if (elapsed < LOGO_DURATION_MILLIS) {
            return Phase.LOGO;
        }
        if (elapsed < LOGO_DURATION_MILLIS + TRANSITION_DURATION_MILLIS) {
            return Phase.TRANSITION;
        }
        active = false;
        return Phase.IDLE;
    }

    public Phase getPhase() { return phase(System.currentTimeMillis()); }

    public boolean isBootActive() { return getPhase() != Phase.IDLE; }

    public long transitionElapsedMillis(long nowMillis) {
        return phase(nowMillis) == Phase.TRANSITION
                ? Math.max(0L, nowMillis - startedAtMillis - LOGO_DURATION_MILLIS)
                : 0L;
    }

    public enum Phase {
        IDLE,
        LOGO,
        TRANSITION
    }
}
