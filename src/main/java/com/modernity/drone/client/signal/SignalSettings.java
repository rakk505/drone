package com.modernity.drone.client.signal;

import com.modernity.drone.Config;

/** Tunables and defaults from FPVtoMinecraft 1.1.4's signal model. */
public record SignalSettings(
        int maximumRange,
        int freezeStartDistance,
        float obstaclePenaltyMultiplier,
        float groundProximityPenaltyMaximum,
        int groundProximityDistanceStart,
        int groundProximityHeightThreshold,
        float heightBonusPerBlock,
        int heightBaseline,
        float smoothing,
        int zeroSignalFrames,
        float freezeIntensity
) {
    public static final SignalSettings V1_1_4_DEFAULTS = new SignalSettings(
            350, 100, 1.0F, 35.0F, 30, 15, 0.1F, 64, 0.15F, 30, 5.0F
    );

    public SignalSettings {
        maximumRange = clamp(maximumRange, 50, 10_000);
        freezeStartDistance = clamp(freezeStartDistance, 0, 10_000);
        obstaclePenaltyMultiplier = clamp(obstaclePenaltyMultiplier, 0.0F, 10.0F);
        groundProximityPenaltyMaximum = clamp(groundProximityPenaltyMaximum, 0.0F, 100.0F);
        groundProximityDistanceStart = clamp(groundProximityDistanceStart, 0, 10_000);
        groundProximityHeightThreshold = clamp(groundProximityHeightThreshold, 1, 256);
        heightBonusPerBlock = clamp(heightBonusPerBlock, 0.0F, 10.0F);
        heightBaseline = clamp(heightBaseline, -64, 320);
        smoothing = clamp(smoothing, 0.01F, 1.0F);
        zeroSignalFrames = clamp(zeroSignalFrames, 1, 600);
        freezeIntensity = clamp(freezeIntensity, 0.0F, 50.0F);
    }

    public SignalSettings withSimpleRange(int range) {
        if (range <= 0) {
            return this;
        }
        int clamped = clamp(range, 50, 10_000);
        float scale = clamped / 350.0F;
        return new SignalSettings(
                clamped,
                Math.round(100.0F * scale),
                obstaclePenaltyMultiplier,
                groundProximityPenaltyMaximum,
                Math.round(30.0F * scale),
                clamp(Math.round(15.0F * scale), 1, 256),
                heightBonusPerBlock,
                heightBaseline,
                smoothing,
                zeroSignalFrames,
                freezeIntensity
        );
    }

    public static SignalSettings fromConfig() {
        SignalSettings settings = new SignalSettings(
                Config.MAX_RANGE.getAsInt(),
                Config.FREEZE_START_DISTANCE.getAsInt(),
                Config.OBSTACLE_PENALTY_MULTIPLIER.get().floatValue(),
                Config.GROUND_PROXIMITY_PENALTY_MAX.get().floatValue(),
                Config.GROUND_PROXIMITY_DISTANCE_START.getAsInt(),
                Config.GROUND_PROXIMITY_HEIGHT_THRESHOLD.getAsInt(),
                Config.HEIGHT_BONUS_PER_BLOCK.get().floatValue(),
                Config.HEIGHT_BASELINE.getAsInt(),
                Config.SIGNAL_SMOOTHING.get().floatValue(),
                Config.ZERO_SIGNAL_FRAMES_FOR_DISCONNECT.getAsInt(),
                Config.FREEZE_INTENSITY.get().floatValue()
        );
        return settings.withSimpleRange(Config.SIMPLE_RANGE.getAsInt());
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
