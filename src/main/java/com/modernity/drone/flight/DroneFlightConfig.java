package com.modernity.drone.flight;

import net.minecraft.nbt.CompoundTag;

/**
 * Per-airframe Betaflight and propulsion settings from FPV To Minecraft 1.1.4.
 * Values are validated here because they can arrive over the network.
 */
public record DroneFlightConfig(
        float yawRcRate,
        float pitchRcRate,
        float rollRcRate,
        float yawSuperRate,
        float pitchSuperRate,
        float rollSuperRate,
        float yawExpo,
        float pitchExpo,
        float rollExpo,
        float motorKv,
        float propDiameterInches,
        float propPitchInches,
        float dragCoefficient,
        float thrustMultiplier,
        boolean flightMode3d,
        String droneName
) {
    public static final DroneFlightConfig DEFAULT = new DroneFlightConfig(
            1.15F, 1.15F, 1.15F,
            0.67F, 0.67F, 0.67F,
            0.0F, 0.0F, 0.0F,
            1300.0F, 9.0F, 4.5F, 1.1F, 1.0F, false, "KINDER"
    );

    public DroneFlightConfig {
        yawRcRate = finiteClamp(yawRcRate, 0.0F, 2.55F, 1.15F);
        pitchRcRate = finiteClamp(pitchRcRate, 0.0F, 2.55F, 1.15F);
        rollRcRate = finiteClamp(rollRcRate, 0.0F, 2.55F, 1.15F);
        yawSuperRate = finiteClamp(yawSuperRate, 0.0F, 1.0F, 0.67F);
        pitchSuperRate = finiteClamp(pitchSuperRate, 0.0F, 1.0F, 0.67F);
        rollSuperRate = finiteClamp(rollSuperRate, 0.0F, 1.0F, 0.67F);
        yawExpo = finiteClamp(yawExpo, 0.0F, 1.0F, 0.0F);
        pitchExpo = finiteClamp(pitchExpo, 0.0F, 1.0F, 0.0F);
        rollExpo = finiteClamp(rollExpo, 0.0F, 1.0F, 0.0F);
        motorKv = finiteClamp(motorKv, 800.0F, 3000.0F, 1300.0F);
        propDiameterInches = finiteClamp(propDiameterInches, 3.0F, 12.0F, 9.0F);
        propPitchInches = finiteClamp(propPitchInches, 2.0F, 8.0F, 4.5F);
        dragCoefficient = finiteClamp(dragCoefficient, 0.5F, 2.0F, 1.1F);
        thrustMultiplier = finiteClamp(thrustMultiplier, 0.5F, 2.0F, 1.0F);
        droneName = droneName == null || droneName.isBlank() ? "KINDER"
                : droneName.substring(0, Math.min(20, droneName.length()));
    }

    public BetaflightRateProfile rateProfile() {
        return new BetaflightRateProfile(
                new BetaflightRateProfile.AxisRate(rollRcRate, rollSuperRate, rollExpo),
                new BetaflightRateProfile.AxisRate(pitchRcRate, pitchSuperRate, pitchExpo),
                new BetaflightRateProfile.AxisRate(yawRcRate, yawSuperRate, yawExpo)
        );
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("YawRcRate", yawRcRate);
        tag.putFloat("PitchRcRate", pitchRcRate);
        tag.putFloat("RollRcRate", rollRcRate);
        tag.putFloat("YawSuperRate", yawSuperRate);
        tag.putFloat("PitchSuperRate", pitchSuperRate);
        tag.putFloat("RollSuperRate", rollSuperRate);
        tag.putFloat("YawExpo", yawExpo);
        tag.putFloat("PitchExpo", pitchExpo);
        tag.putFloat("RollExpo", rollExpo);
        tag.putFloat("MotorKv", motorKv);
        tag.putFloat("PropDiameter", propDiameterInches);
        tag.putFloat("PropPitch", propPitchInches);
        tag.putFloat("DragCoefficient", dragCoefficient);
        tag.putFloat("ThrustMultiplier", thrustMultiplier);
        tag.putBoolean("FlightMode3d", flightMode3d);
        tag.putString("DroneName", droneName);
        return tag;
    }

    public static DroneFlightConfig load(CompoundTag tag) {
        DroneFlightConfig defaults = DEFAULT;
        return new DroneFlightConfig(
                tag.getFloatOr("YawRcRate", defaults.yawRcRate),
                tag.getFloatOr("PitchRcRate", defaults.pitchRcRate),
                tag.getFloatOr("RollRcRate", defaults.rollRcRate),
                tag.getFloatOr("YawSuperRate", defaults.yawSuperRate),
                tag.getFloatOr("PitchSuperRate", defaults.pitchSuperRate),
                tag.getFloatOr("RollSuperRate", defaults.rollSuperRate),
                tag.getFloatOr("YawExpo", defaults.yawExpo),
                tag.getFloatOr("PitchExpo", defaults.pitchExpo),
                tag.getFloatOr("RollExpo", defaults.rollExpo),
                tag.getFloatOr("MotorKv", defaults.motorKv),
                tag.getFloatOr("PropDiameter", defaults.propDiameterInches),
                tag.getFloatOr("PropPitch", defaults.propPitchInches),
                tag.getFloatOr("DragCoefficient", defaults.dragCoefficient),
                tag.getFloatOr("ThrustMultiplier", defaults.thrustMultiplier),
                tag.getBooleanOr("FlightMode3d", defaults.flightMode3d),
                tag.getStringOr("DroneName", defaults.droneName)
        );
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }
}
