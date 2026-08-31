package com.modernity.drone.flight;

/**
 * One immutable pilot input sample. Stick axes are clamped to [-1, 1] and
 * throttle to [0, 1]. Positive pitch means forward stick/nose down.
 */
public record FlightControl(
        double roll,
        double pitch,
        double yaw,
        double throttle,
        boolean armed
) {
    public static final FlightControl DISARMED = new FlightControl(0.0, 0.0, 0.0, 0.0, false);

    public FlightControl {
        roll = clampStick(roll);
        pitch = clampStick(pitch);
        yaw = clampStick(yaw);
        throttle = FlightMath.clamp(FlightMath.finiteOr(throttle, 0.0), 0.0, 1.0);
    }

    private static double clampStick(double value) {
        return FlightMath.clamp(FlightMath.finiteOr(value, 0.0), -1.0, 1.0);
    }

    /** Returns payload-drone vertical demand in [-1, 1], with 0 at hover. */
    public double centeredThrottle(double deadband) {
        double centered = throttle * 2.0 - 1.0;
        double safeDeadband = FlightMath.clamp(FlightMath.finiteOr(deadband, 0.0), 0.0, 0.95);
        if (Math.abs(centered) <= safeDeadband) {
            return 0.0;
        }
        return Math.copySign((Math.abs(centered) - safeDeadband) / (1.0 - safeDeadband), centered);
    }

    public FlightControl withArmed(boolean newArmed) {
        return new FlightControl(roll, pitch, yaw, throttle, newArmed);
    }
}
