package com.modernity.drone.flight;

/** Observable safeguards applied during a flight step. */
public enum FlightSafetyFlag {
    FINITE_VALUE_RECOVERED,
    MASS_CLAMPED,
    POSITION_CLAMPED,
    SPEED_CLAMPED,
    ANGULAR_RATE_CLAMPED,
    ACCELERATION_CLAMPED,
    THRUST_CLAMPED,
    BATTERY_DEPLETED
}
