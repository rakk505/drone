package com.modernity.drone.client.video;

/** Deterministic frame-hold cadence used as reception degrades. */
public final class FrameFreezer {
    public static final long BASE_FRAME_INTERVAL_MILLIS = 16L;

    private long lastFrameMillis;
    private long currentHoldMillis = BASE_FRAME_INTERVAL_MILLIS;

    public static long holdDuration(float signal, long baseIntervalMillis, float freezeIntensity) {
        float clamped = Math.max(0.05F, Math.min(1.0F, signal));
        return (long) (baseIntervalMillis * (1.0F + (1.0F - clamped) * freezeIntensity));
    }

    public boolean shouldAdvance(float signal, float freezeIntensity, long nowMillis) {
        if (signal <= 0.0F) {
            return false;
        }
        if (signal >= 1.0F) {
            lastFrameMillis = nowMillis;
            currentHoldMillis = BASE_FRAME_INTERVAL_MILLIS;
            return true;
        }
        if (nowMillis - lastFrameMillis < currentHoldMillis) {
            return false;
        }
        lastFrameMillis = nowMillis;
        currentHoldMillis = holdDuration(signal, BASE_FRAME_INTERVAL_MILLIS, freezeIntensity);
        return true;
    }

    public void reset(long nowMillis) {
        lastFrameMillis = nowMillis;
        currentHoldMillis = BASE_FRAME_INTERVAL_MILLIS;
    }

    public long currentHoldMillis() {
        return currentHoldMillis;
    }
}
