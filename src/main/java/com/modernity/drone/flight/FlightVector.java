package com.modernity.drone.flight;

/**
 * Immutable vector in Minecraft world axes: +X east, +Y up, and +Z south.
 * Distances are metres (one Minecraft block is treated as one metre).
 */
public record FlightVector(double x, double y, double z) {
    public static final FlightVector ZERO = new FlightVector(0.0, 0.0, 0.0);
    public static final FlightVector UP = new FlightVector(0.0, 1.0, 0.0);

    public FlightVector add(FlightVector other) {
        return new FlightVector(x + other.x, y + other.y, z + other.z);
    }

    public FlightVector subtract(FlightVector other) {
        return new FlightVector(x - other.x, y - other.y, z - other.z);
    }

    public FlightVector multiply(double scalar) {
        return new FlightVector(x * scalar, y * scalar, z * scalar);
    }

    public double dot(FlightVector other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public FlightVector cross(FlightVector other) {
        return new FlightVector(
                y * other.z - z * other.y,
                z * other.x - x * other.z,
                x * other.y - y * other.x
        );
    }

    public double length() {
        return Math.hypot(x, Math.hypot(y, z));
    }

    public double horizontalLength() {
        return Math.hypot(x, z);
    }

    public FlightVector normalizedOrZero() {
        double magnitude = length();
        if (!Double.isFinite(magnitude) || magnitude < 1.0e-12) {
            return ZERO;
        }
        return multiply(1.0 / magnitude);
    }

    public FlightVector clampMagnitude(double maximum) {
        double safeMaximum = Math.max(0.0, FlightMath.finiteOr(maximum, 0.0));
        double magnitude = length();
        if (!Double.isFinite(magnitude)) {
            return ZERO;
        }
        if (magnitude <= safeMaximum || magnitude < 1.0e-12) {
            return this;
        }
        return multiply(safeMaximum / magnitude);
    }

    public FlightVector horizontal() {
        return new FlightVector(x, 0.0, z);
    }

    public FlightVector withY(double newY) {
        return new FlightVector(x, newY, z);
    }

    public boolean isFinite() {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }

    public FlightVector finiteOrZero() {
        return isFinite() ? this : ZERO;
    }
}
