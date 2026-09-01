package com.modernity.drone.client.config;

import java.util.Arrays;

/**
 * Persisted radio-controller mapping. Channel, calibration, inversion, and
 * device fields retain the V1.1.4 controller.json schema. The rate defaults
 * are aligned with the authoritative V1.1.4 per-airframe profile.
 */
public final class ControllerConfig {
    public static final int THROTTLE = 0;
    public static final int YAW = 1;
    public static final int PITCH = 2;
    public static final int ROLL = 3;
    public static final int AXIS_COUNT = 8;

    private final int[] channels = {1, 0, 3, 2};
    private final boolean[] inverted = new boolean[4];
    private final float[] axisRange = {
            -1.0F, 1.0F, -1.0F, 1.0F, -1.0F, 1.0F, -1.0F, 1.0F,
            -1.0F, 1.0F, -1.0F, 1.0F, -1.0F, 1.0F, -1.0F, 1.0F
    };
    private final float[] rcRate = {1.0F, 1.15F, 1.15F, 1.15F};
    private final float[] superRate = {0.7F, 0.67F, 0.67F, 0.67F};
    private final float[] expo = {0.0F, 0.0F, 0.0F, 0.0F};
    private int armChannel;
    private boolean armIsButton = true;
    private boolean invertArm;
    private float deadzone = 0.05F;
    private String preferredJoystickGuid = "";
    private String preferredJoystickName = "";

    public int channel(int function) {
        return validFunction(function) ? channels[function] : 0;
    }

    public void setChannel(int function, int channel) {
        if (validFunction(function)) {
            channels[function] = clamp(channel, 0, AXIS_COUNT - 1);
        }
    }

    public boolean inverted(int function) {
        return validFunction(function) && inverted[function];
    }

    public void setInverted(int function, boolean value) {
        if (validFunction(function)) {
            inverted[function] = value;
        }
    }

    public int armChannel() {
        return armChannel;
    }

    public void setArmChannel(int value) {
        armChannel = clamp(value, 0, 31);
    }

    public boolean armIsButton() {
        return armIsButton;
    }

    public void setArmIsButton(boolean value) {
        armIsButton = value;
    }

    public boolean invertArm() {
        return invertArm;
    }

    public void setInvertArm(boolean value) {
        invertArm = value;
    }

    public float deadzone() {
        return deadzone;
    }

    public void setDeadzone(float value) {
        deadzone = clampFinite(value, 0.0F, 0.95F, 0.05F);
    }

    public float axisMin(int axis) {
        return validAxis(axis) ? axisRange[axis * 2] : -1.0F;
    }

    public float axisMax(int axis) {
        return validAxis(axis) ? axisRange[axis * 2 + 1] : 1.0F;
    }

    public void setAxisRange(int axis, float minimum, float maximum) {
        if (!validAxis(axis) || !Float.isFinite(minimum) || !Float.isFinite(maximum) || maximum - minimum < 0.05F) {
            return;
        }
        axisRange[axis * 2] = Math.max(-1.0F, minimum);
        axisRange[axis * 2 + 1] = Math.min(1.0F, maximum);
    }

    public float[] axisRanges() {
        return Arrays.copyOf(axisRange, axisRange.length);
    }

    public void setAxisRanges(float[] ranges) {
        if (ranges == null || ranges.length != axisRange.length) {
            return;
        }
        for (int axis = 0; axis < AXIS_COUNT; axis++) {
            setAxisRange(axis, ranges[axis * 2], ranges[axis * 2 + 1]);
        }
    }

    public float rcRate(int function) {
        return validFunction(function) ? rcRate[function] : 1.0F;
    }

    public void setRcRate(int function, float value) {
        if (validFunction(function)) {
            rcRate[function] = clampFinite(value, 0.0F, 2.55F, rcRate[function]);
        }
    }

    public float superRate(int function) {
        return validFunction(function) ? superRate[function] : 0.0F;
    }

    public void setSuperRate(int function, float value) {
        if (validFunction(function)) {
            superRate[function] = clampFinite(value, 0.0F, 1.0F, superRate[function]);
        }
    }

    public float expo(int function) {
        return validFunction(function) ? expo[function] : 0.0F;
    }

    public void setExpo(int function, float value) {
        if (validFunction(function)) {
            expo[function] = clampFinite(value, 0.0F, 1.0F, expo[function]);
        }
    }

    public String preferredJoystickGuid() {
        return preferredJoystickGuid;
    }

    public void setPreferredJoystickGuid(String value) {
        preferredJoystickGuid = value == null ? "" : value;
    }

    public String preferredJoystickName() {
        return preferredJoystickName;
    }

    public void setPreferredJoystickName(String value) {
        preferredJoystickName = value == null ? "" : value;
    }

    public void resetRates(boolean fast) {
        // These are the two presets exposed by V1.1.4's RatesList.  They are
        // deliberately distinct from both the initial radio profile and the
        // per-airframe defaults stored on a built drone.
        float rate = fast ? 1.0F : 0.7F;
        float superValue = fast ? 0.7F : 0.5F;
        float expoValue = fast ? 0.0F : 0.2F;
        rcRate[YAW] = rcRate[PITCH] = rcRate[ROLL] = rate;
        superRate[YAW] = superRate[PITCH] = superRate[ROLL] = superValue;
        expo[YAW] = expo[PITCH] = expo[ROLL] = expoValue;
    }

    public static float betaflightRate(float input, float rate, float superRate, float expo) {
        float command = clampFinite(input, -1.0F, 1.0F, 0.0F);
        float absolute = Math.abs(command);
        float effectiveRate = rate > 2.0F ? rate + 14.54F * (rate - 2.0F) : rate;
        if (expo != 0.0F) {
            command = command * (float) Math.pow(absolute, 3.0) * expo + command * (1.0F - expo);
        }
        return 200.0F * effectiveRate * command
                / Math.max(0.01F, 1.0F - absolute * superRate);
    }

    private static boolean validFunction(int function) {
        return function >= 0 && function < 4;
    }

    private static boolean validAxis(int axis) {
        return axis >= 0 && axis < AXIS_COUNT;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float clampFinite(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }
}
