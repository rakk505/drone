package com.modernity.drone.flight;

import java.util.Objects;

/** Complete immutable state needed for one deterministic server flight step. */
public record FlightState(
        DroneKind kind,
        FlightVector positionMeters,
        FlightVector velocityMetersPerSecond,
        FlightAttitude attitude,
        FlightRates angularRates,
        BatteryState battery,
        double payloadMassKg,
        long simulationTick
) {
    public FlightState {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(positionMeters, "positionMeters");
        Objects.requireNonNull(velocityMetersPerSecond, "velocityMetersPerSecond");
        Objects.requireNonNull(attitude, "attitude");
        Objects.requireNonNull(angularRates, "angularRates");
        Objects.requireNonNull(battery, "battery");
    }

    public static FlightState atRest(DroneKind kind, FlightVector positionMeters) {
        Objects.requireNonNull(kind, "kind");
        return new FlightState(
                kind,
                Objects.requireNonNull(positionMeters, "positionMeters"),
                FlightVector.ZERO,
                FlightAttitude.IDENTITY,
                FlightRates.ZERO,
                kind.defaultBattery(),
                0.0,
                0L
        );
    }

    public double totalMassKg() {
        double payload = Math.max(0.0, FlightMath.finiteOr(payloadMassKg, 0.0));
        return kind.dryMassKg() + battery.massKg() + payload;
    }

    public FlightState withKinematics(FlightVector position, FlightVector velocity) {
        return new FlightState(kind, position, velocity, attitude, angularRates, battery, payloadMassKg, simulationTick);
    }

    public FlightState withAttitude(FlightAttitude newAttitude, FlightRates newRates) {
        return new FlightState(
                kind,
                positionMeters,
                velocityMetersPerSecond,
                newAttitude,
                newRates,
                battery,
                payloadMassKg,
                simulationTick
        );
    }

    public FlightState withBattery(BatteryState newBattery) {
        return new FlightState(
                kind,
                positionMeters,
                velocityMetersPerSecond,
                attitude,
                angularRates,
                newBattery,
                payloadMassKg,
                simulationTick
        );
    }

    public FlightState withPayloadMassKg(double newPayloadMassKg) {
        return new FlightState(
                kind,
                positionMeters,
                velocityMetersPerSecond,
                attitude,
                angularRates,
                battery,
                Math.max(0.0, FlightMath.finiteOr(newPayloadMassKg, 0.0)),
                simulationTick
        );
    }

    /** Removes payload mass without changing velocity, preserving drop momentum. */
    public FlightState releasePayload(double releasedMassKg) {
        double released = Math.max(0.0, FlightMath.finiteOr(releasedMassKg, 0.0));
        return withPayloadMassKg(Math.max(0.0, payloadMassKg - released));
    }
}
