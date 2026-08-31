package com.modernity.drone.flight;

/**
 * Body angular rates in radians/second using pilot-friendly signs.
 * Positive roll lowers the right side, positive pitch raises the nose, and
 * positive yaw turns right.
 */
public record FlightRates(
        double rollRadiansPerSecond,
        double pitchRadiansPerSecond,
        double yawRadiansPerSecond
) {
    public static final FlightRates ZERO = new FlightRates(0.0, 0.0, 0.0);

    public FlightRates add(FlightRates other) {
        return new FlightRates(
                rollRadiansPerSecond + other.rollRadiansPerSecond,
                pitchRadiansPerSecond + other.pitchRadiansPerSecond,
                yawRadiansPerSecond + other.yawRadiansPerSecond
        );
    }

    public FlightRates multiply(double scalar) {
        return new FlightRates(
                rollRadiansPerSecond * scalar,
                pitchRadiansPerSecond * scalar,
                yawRadiansPerSecond * scalar
        );
    }

    public FlightRates interpolate(FlightRates target, double amount) {
        double alpha = FlightMath.clamp(FlightMath.finiteOr(amount, 0.0), 0.0, 1.0);
        return new FlightRates(
                rollRadiansPerSecond + (target.rollRadiansPerSecond - rollRadiansPerSecond) * alpha,
                pitchRadiansPerSecond + (target.pitchRadiansPerSecond - pitchRadiansPerSecond) * alpha,
                yawRadiansPerSecond + (target.yawRadiansPerSecond - yawRadiansPerSecond) * alpha
        );
    }

    public boolean isFinite() {
        return Double.isFinite(rollRadiansPerSecond)
                && Double.isFinite(pitchRadiansPerSecond)
                && Double.isFinite(yawRadiansPerSecond);
    }

    public FlightRates finiteOrZero() {
        return isFinite() ? this : ZERO;
    }

    public FlightRates clamp(double maximumMagnitudePerAxis) {
        double limit = Math.max(0.0, FlightMath.finiteOr(maximumMagnitudePerAxis, 0.0));
        return new FlightRates(
                FlightMath.clamp(FlightMath.finiteOr(rollRadiansPerSecond, 0.0), -limit, limit),
                FlightMath.clamp(FlightMath.finiteOr(pitchRadiansPerSecond, 0.0), -limit, limit),
                FlightMath.clamp(FlightMath.finiteOr(yawRadiansPerSecond, 0.0), -limit, limit)
        );
    }
}
