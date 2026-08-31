package com.modernity.drone.flight;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** New state plus force, electrical, and safety telemetry for one server tick. */
public record FlightStepResult(
        FlightState nextState,
        FlightVector accelerationMetersPerSecondSquared,
        FlightVector thrustForceNewtons,
        FlightVector aerodynamicDragForceNewtons,
        double totalMassKg,
        double motorCurrentAmps,
        double loadedBatteryVoltage,
        Set<FlightSafetyFlag> safetyFlags
) {
    public FlightStepResult {
        Objects.requireNonNull(nextState, "nextState");
        Objects.requireNonNull(accelerationMetersPerSecondSquared, "accelerationMetersPerSecondSquared");
        Objects.requireNonNull(thrustForceNewtons, "thrustForceNewtons");
        Objects.requireNonNull(aerodynamicDragForceNewtons, "aerodynamicDragForceNewtons");
        Objects.requireNonNull(safetyFlags, "safetyFlags");
        safetyFlags = safetyFlags.isEmpty()
                ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(safetyFlags));
    }

    public boolean wasSafetyLimited() {
        return !safetyFlags.isEmpty();
    }

    public boolean hasFlag(FlightSafetyFlag flag) {
        return safetyFlags.contains(flag);
    }
}
