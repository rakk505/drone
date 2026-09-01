package com.modernity.drone.client.config;

import com.modernity.drone.flight.DroneFlightConfig;
import java.util.Arrays;

/**
 * Exact client mirror of V1.1.4's per-airframe flight-controller data.
 *
 * <p>This is intentionally separate from {@link DroneBuildConfig}: the latter
 * is the standalone build-planner UI, while these values are serialized onto
 * an actual drone and consumed by its flight simulation. It is also
 * intentionally not stored in {@link FpvClientConfig}: the server's entity NBT
 * is authoritative for each airframe, so a client editor must first seed this
 * object from the synchronized server profile before submitting a full update.</p>
 */
public final class DroneConfig {
    public static final int THROTTLE = 0;
    public static final int YAW = 1;
    public static final int PITCH = 2;
    public static final int ROLL = 3;

    private final float[] rcRate = {1.0F, 1.15F, 1.15F, 1.15F};
    private final float[] superRate = {0.7F, 0.67F, 0.67F, 0.67F};
    private final float[] expo = {0.0F, 0.0F, 0.0F, 0.0F};
    private float motorKv = 1300.0F;
    private float propDiameter = 9.0F;
    private float propPitch = 4.5F;
    private float dragCoefficient = 1.1F;
    private float thrustMultiplier = 1.0F;
    private boolean flightMode3d;
    private String droneName = "KINDER";

    public static DroneConfig fromFlightConfig(DroneFlightConfig source) {
        DroneConfig result = new DroneConfig();
        if (source == null) return result;
        result.setRcRate(YAW, source.yawRcRate());
        result.setRcRate(PITCH, source.pitchRcRate());
        result.setRcRate(ROLL, source.rollRcRate());
        result.setSuperRate(YAW, source.yawSuperRate());
        result.setSuperRate(PITCH, source.pitchSuperRate());
        result.setSuperRate(ROLL, source.rollSuperRate());
        result.setExpo(YAW, source.yawExpo());
        result.setExpo(PITCH, source.pitchExpo());
        result.setExpo(ROLL, source.rollExpo());
        result.setMotorKv(source.motorKv());
        result.setPropDiameter(source.propDiameterInches());
        result.setPropPitch(source.propPitchInches());
        result.setDragCoefficient(source.dragCoefficient());
        result.setThrustMultiplier(source.thrustMultiplier());
        result.setFlightMode3d(source.flightMode3d());
        result.setDroneName(source.droneName());
        return result;
    }

    public DroneFlightConfig toFlightConfig() {
        return new DroneFlightConfig(
                rcRate(YAW), rcRate(PITCH), rcRate(ROLL),
                superRate(YAW), superRate(PITCH), superRate(ROLL),
                expo(YAW), expo(PITCH), expo(ROLL),
                motorKv, propDiameter, propPitch,
                dragCoefficient, thrustMultiplier, flightMode3d, droneName
        );
    }

    public float rcRate(int axis) {
        return validAxis(axis) ? rcRate[axis] : 1.0F;
    }

    public void setRcRate(int axis, float value) {
        if (validAxis(axis)) rcRate[axis] = clamp(value, 0.0F, 2.55F, rcRate[axis]);
    }

    public float superRate(int axis) {
        return validAxis(axis) ? superRate[axis] : 0.7F;
    }

    public void setSuperRate(int axis, float value) {
        if (validAxis(axis)) superRate[axis] = clamp(value, 0.0F, 1.0F, superRate[axis]);
    }

    public float expo(int axis) {
        return validAxis(axis) ? expo[axis] : 0.0F;
    }

    public void setExpo(int axis, float value) {
        if (validAxis(axis)) expo[axis] = clamp(value, 0.0F, 1.0F, expo[axis]);
    }

    public float motorKv() { return motorKv; }
    public void setMotorKv(float value) { motorKv = clamp(value, 800.0F, 3000.0F, motorKv); }
    public float propDiameter() { return propDiameter; }
    public void setPropDiameter(float value) { propDiameter = clamp(value, 3.0F, 12.0F, propDiameter); }
    public float propPitch() { return propPitch; }
    public void setPropPitch(float value) { propPitch = clamp(value, 2.0F, 8.0F, propPitch); }
    public float dragCoefficient() { return dragCoefficient; }
    public void setDragCoefficient(float value) { dragCoefficient = clamp(value, 0.5F, 2.0F, dragCoefficient); }
    public float thrustMultiplier() { return thrustMultiplier; }
    public void setThrustMultiplier(float value) { thrustMultiplier = clamp(value, 0.5F, 2.0F, thrustMultiplier); }
    public boolean flightMode3d() { return flightMode3d; }
    public void setFlightMode3d(boolean value) { flightMode3d = value; }
    public String droneName() { return droneName; }

    public void setDroneName(String value) {
        if (value != null && value.length() <= 20) droneName = value.isEmpty() ? "KINDER" : value;
    }

    public float[] rcRates() { return Arrays.copyOf(rcRate, rcRate.length); }
    public float[] superRates() { return Arrays.copyOf(superRate, superRate.length); }
    public float[] expos() { return Arrays.copyOf(expo, expo.length); }

    private static boolean validAxis(int axis) {
        return axis >= 0 && axis < 4;
    }

    private static float clamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }
}
