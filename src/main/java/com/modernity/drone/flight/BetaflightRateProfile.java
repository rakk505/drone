package com.modernity.drone.flight;

import java.util.Objects;

/** Betaflight-style RC rate, super-rate, and expo shaping. */
public record BetaflightRateProfile(AxisRate roll, AxisRate pitch, AxisRate yaw) {
    public static final BetaflightRateProfile MOSQUITO_DEFAULT = new BetaflightRateProfile(
            new AxisRate(1.20, 0.70, 0.25),
            new AxisRate(1.20, 0.70, 0.25),
            new AxisRate(1.00, 0.65, 0.20)
    );

    public BetaflightRateProfile {
        Objects.requireNonNull(roll, "roll");
        Objects.requireNonNull(pitch, "pitch");
        Objects.requireNonNull(yaw, "yaw");
    }

    public FlightRates targetRates(FlightControl control) {
        Objects.requireNonNull(control, "control");
        return new FlightRates(
                roll.radiansPerSecond(control.roll()),
                -pitch.radiansPerSecond(control.pitch()),
                yaw.radiansPerSecond(control.yaw())
        );
    }

    public record AxisRate(double rcRate, double superRate, double expo) {
        public AxisRate {
            rcRate = FlightMath.clamp(FlightMath.finiteOr(rcRate, 1.0), 0.01, 3.0);
            superRate = FlightMath.clamp(FlightMath.finiteOr(superRate, 0.0), 0.0, 0.99);
            expo = FlightMath.clamp(FlightMath.finiteOr(expo, 0.0), 0.0, 1.0);
        }

        public double radiansPerSecond(double stick) {
            double input = FlightMath.clamp(FlightMath.finiteOr(stick, 0.0), -1.0, 1.0);
            double shaped = input * (1.0 - expo) + input * input * input * expo;
            double effectiveRcRate = rcRate;
            if (effectiveRcRate > 2.0) {
                effectiveRcRate += 14.54 * (effectiveRcRate - 2.0);
            }
            double degreesPerSecond = 200.0 * effectiveRcRate * shaped;
            degreesPerSecond /= Math.max(0.01, 1.0 - Math.abs(shaped) * superRate);
            return Math.toRadians(degreesPerSecond);
        }
    }
}
