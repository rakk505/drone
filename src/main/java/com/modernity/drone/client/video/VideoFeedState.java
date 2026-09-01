package com.modernity.drone.client.video;

import com.modernity.drone.client.signal.SignalSettings;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Render-backend-neutral state for pixelation and frozen-frame behavior.
 * A world renderer can consume {@link #frameDecision} to capture or replay its framebuffer.
 */
public final class VideoFeedState {
    private static final VideoFeedState INSTANCE = new VideoFeedState();

    private final FrameFreezer freezer = new FrameFreezer();
    private VideoResolution selectedResolution = VideoResolution.RES_25;
    private @Nullable UUID droneId;
    private boolean hasCapturedFrame;
    private FrameDecision frameDecision = FrameDecision.INACTIVE;

    private VideoFeedState() {
    }

    public static VideoFeedState get() {
        return INSTANCE;
    }

    public void begin(UUID newDroneId, long nowMillis) {
        droneId = newDroneId;
        hasCapturedFrame = false;
        freezer.reset(nowMillis);
        frameDecision = FrameDecision.INACTIVE;
    }

    public FrameDecision update(boolean active, boolean creative, double distance, float freezeSignal,
                                SignalSettings signalSettings, long nowMillis) {
        if (!active) {
            frameDecision = FrameDecision.INACTIVE;
            return frameDecision;
        }
        float scale = creative ? selectedResolution.scale()
                : survivalResolution(distance, signalSettings.maximumRange()).scale();
        boolean noSignal = freezeSignal <= 0.0F;
        boolean advance = freezer.shouldAdvance(freezeSignal, signalSettings.freezeIntensity(), nowMillis);
        // V1.1.4 force-captures the first usable frame even if the hold timer has not elapsed.
        if (!hasCapturedFrame && freezeSignal > 0.0F) {
            advance = true;
        }
        if (advance) {
            hasCapturedFrame = true;
        }
        frameDecision = new FrameDecision(true, scale, advance, hasCapturedFrame && !advance, noSignal);
        return frameDecision;
    }

    public void end() {
        droneId = null;
        hasCapturedFrame = false;
        frameDecision = FrameDecision.INACTIVE;
    }

    public void cycleResolution() {
        selectedResolution = selectedResolution.next();
    }

    public void setResolution(VideoResolution resolution) {
        selectedResolution = resolution == null ? VideoResolution.RES_25 : resolution;
    }

    public VideoResolution selectedResolution() {
        return selectedResolution;
    }

    public @Nullable UUID droneId() {
        return droneId;
    }

    public FrameDecision frameDecision() {
        return frameDecision;
    }

    public static VideoResolution survivalResolution(double distance, int maximumRange) {
        float amount = Math.min(1.0F, (float) distance / Math.max(1, maximumRange));
        float target = 0.33F - 0.23F * amount;
        VideoResolution best = VideoResolution.RES_10;
        float bestDistance = Float.MAX_VALUE;
        for (VideoResolution resolution : VideoResolution.values()) {
            if (resolution.scale() > 0.33F) {
                continue;
            }
            float candidateDistance = Math.abs(resolution.scale() - target);
            if (candidateDistance < bestDistance) {
                bestDistance = candidateDistance;
                best = resolution;
            }
        }
        return best;
    }

    public record FrameDecision(
            boolean active,
            float renderScale,
            boolean captureCurrentFrame,
            boolean replayPreviousFrame,
            boolean noSignal
    ) {
        public static final FrameDecision INACTIVE = new FrameDecision(false, 1.0F, true, false, false);
    }
}
