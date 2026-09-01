package com.modernity.drone;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-authoritative settings matching FPV to Minecraft 1.1.4. */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue SIMPLE_RANGE = BUILDER
            .comment("Quick range setup. Zero uses the individual range values below.")
            .defineInRange("simpleRange", 0, 0, 10_000);
    public static final ModConfigSpec.IntValue MAX_RANGE = BUILDER
            .comment("Maximum FPV range in blocks.")
            .defineInRange("maxRange", 350, 50, 10_000);
    public static final ModConfigSpec.IntValue FREEZE_START_DISTANCE = BUILDER
            .comment("Distance where video stutter begins.")
            .defineInRange("freezeStartDistance", 100, 0, 10_000);
    public static final ModConfigSpec.DoubleValue OBSTACLE_PENALTY_MULTIPLIER = BUILDER
            .defineInRange("obstaclePenaltyMultiplier", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue GROUND_PROXIMITY_PENALTY_MAX = BUILDER
            .defineInRange("groundProximityPenaltyMax", 35.0, 0.0, 100.0);
    public static final ModConfigSpec.IntValue GROUND_PROXIMITY_DISTANCE_START = BUILDER
            .defineInRange("groundProximityDistanceStart", 30, 0, 10_000);
    public static final ModConfigSpec.IntValue GROUND_PROXIMITY_HEIGHT_THRESHOLD = BUILDER
            .defineInRange("groundProximityHeightThreshold", 15, 1, 256);
    public static final ModConfigSpec.DoubleValue HEIGHT_BONUS_PER_BLOCK = BUILDER
            .defineInRange("heightBonusPerBlock", 0.1, 0.0, 10.0);
    public static final ModConfigSpec.IntValue HEIGHT_BASELINE = BUILDER
            .defineInRange("heightBaseline", 64, -64, 320);
    public static final ModConfigSpec.DoubleValue SIGNAL_SMOOTHING = BUILDER
            .defineInRange("signalSmoothing", 0.15, 0.01, 1.0);
    public static final ModConfigSpec.IntValue ZERO_SIGNAL_FRAMES_FOR_DISCONNECT = BUILDER
            .defineInRange("zeroSignalFramesForDisconnect", 30, 1, 600);
    public static final ModConfigSpec.DoubleValue FREEZE_INTENSITY = BUILDER
            .defineInRange("freezeIntensity", 5.0, 0.0, 50.0);
    public static final ModConfigSpec.DoubleValue FAILSAFE_DESCENT_RATE = BUILDER
            .defineInRange("failsafeDescentRate", 0.5, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue FAILSAFE_HORIZONTAL_DAMPING = BUILDER
            .defineInRange("failsafeHorizontalDamping", 0.95, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue EXPLOSION_BLOCK_POWER = BUILDER
            .defineInRange("explosionBlockPower", 0.0, 0.0, 2.0);
    public static final ModConfigSpec.DoubleValue EXPLOSION_BLAST_RADIUS = BUILDER
            .defineInRange("explosionBlastRadius", 10.0, 1.0, 32.0);
    public static final ModConfigSpec.DoubleValue EXPLOSION_BLAST_DAMAGE = BUILDER
            .defineInRange("explosionBlastDamage", 40.0, 0.0, 200.0);
    public static final ModConfigSpec.DoubleValue EXPLOSION_KILL_ZONE = BUILDER
            .defineInRange("explosionKillZone", 2.0, 0.0, 16.0);
    public static final ModConfigSpec.BooleanValue EXPLOSION_OPENS_DOORS = BUILDER
            .define("explosionOpensDoors", true);
    public static final ModConfigSpec.DoubleValue KAMIKAZE_EXPLOSION_POWER = BUILDER
            .defineInRange("kamikazeExplosionPower", 4.0, 1.0, 10.0);
    public static final ModConfigSpec.IntValue SHRAPNEL_COUNT = BUILDER
            .defineInRange("shrapnelCount", 128, 8, 512);
    public static final ModConfigSpec.BooleanValue DRONE_COLLISION_DAMAGE = BUILDER
            .define("droneCollisionDamage", true);
    public static final ModConfigSpec.IntValue DRONE_OBSERVER_RANGE = BUILDER
            .defineInRange("droneObserverRange", 500, 64, 2048);

    /** Kept so worlds created with the early 26.2 prototype still load. */
    @Deprecated public static final ModConfigSpec.BooleanValue PAYLOAD_BLOCK_DAMAGE = BUILDER
            .define("payloadBlockDamage", false);
    @Deprecated public static final ModConfigSpec.DoubleValue MOSQUITO_CONTROL_RANGE = BUILDER
            .defineInRange("mosquitoControlRange", 350.0, 32.0, 2_000.0);
    @Deprecated public static final ModConfigSpec.DoubleValue PAYLOAD_CONTROL_RANGE = BUILDER
            .defineInRange("payloadControlRange", 800.0, 64.0, 4_000.0);
    @Deprecated public static final ModConfigSpec.IntValue CONTROL_TIMEOUT_TICKS = BUILDER
            .defineInRange("controlTimeoutTicks", 12, 4, 100);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static int effectiveMaxRange() {
        return SIMPLE_RANGE.getAsInt() > 0 ? SIMPLE_RANGE.getAsInt() : MAX_RANGE.getAsInt();
    }

    public static int effectiveFreezeStartDistance() {
        if (SIMPLE_RANGE.getAsInt() <= 0) {
            return FREEZE_START_DISTANCE.getAsInt();
        }
        return Math.max(0, Math.round(SIMPLE_RANGE.getAsInt() * (100.0F / 350.0F)));
    }

    public static int effectiveGroundDistanceStart() {
        return SIMPLE_RANGE.getAsInt() <= 0
                ? GROUND_PROXIMITY_DISTANCE_START.getAsInt()
                : Math.max(0, Math.round(SIMPLE_RANGE.getAsInt() * (30.0F / 350.0F)));
    }

    public static int effectiveGroundHeightThreshold() {
        return SIMPLE_RANGE.getAsInt() <= 0
                ? GROUND_PROXIMITY_HEIGHT_THRESHOLD.getAsInt()
                : Math.max(1, Math.min(256,
                        Math.round(SIMPLE_RANGE.getAsInt() * (15.0F / 350.0F))));
    }

    private Config() {
    }
}
