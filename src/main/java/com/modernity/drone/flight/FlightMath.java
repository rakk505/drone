package com.modernity.drone.flight;

final class FlightMath {
    private FlightMath() {
    }

    static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    static double wrapRadians(double angle) {
        if (!Double.isFinite(angle)) {
            return 0.0;
        }
        return Math.atan2(Math.sin(angle), Math.cos(angle));
    }

    static long incrementSaturated(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }
}
