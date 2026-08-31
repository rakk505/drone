package com.modernity.drone.flight;

/** Flight-controller and airframe family. */
public enum DroneKind {
    /** Manual rate/acro controller for the fast first-person-view airframe. */
    MOSQUITO(
            0.580, 60.0, 1.10, 0.032, 100.0,
            1.5, 108.0,
            6, 3.3, 0.47157, 0.018,
            0.0, 0.0, 0.0, 0.0
    ),

    /** Self-levelling camera/payload platform with velocity and climb limits. */
    PAYLOAD(
            0.895, 22.0, 1.05, 0.045, 30.0,
            0.65, 44.0,
            4, 5.0, 0.3355, 0.030,
            15.0, 4.0, 3.0, Math.toRadians(30.0)
    );

    private final double dryMassKg;
    private final double nominalMaximumThrustNewtons;
    private final double dragCoefficient;
    private final double referenceAreaSquareMeters;
    private final double safetySpeedLimitMetersPerSecond;
    private final double avionicsCurrentAmps;
    private final double maximumMotorCurrentAmps;
    private final int defaultBatteryCells;
    private final double defaultBatteryAmpHours;
    private final double defaultBatteryMassKg;
    private final double defaultBatteryResistanceOhms;
    private final double controlledHorizontalSpeedMetersPerSecond;
    private final double maximumClimbSpeedMetersPerSecond;
    private final double maximumDescentSpeedMetersPerSecond;
    private final double maximumTiltRadians;

    DroneKind(
            double dryMassKg,
            double nominalMaximumThrustNewtons,
            double dragCoefficient,
            double referenceAreaSquareMeters,
            double safetySpeedLimitMetersPerSecond,
            double avionicsCurrentAmps,
            double maximumMotorCurrentAmps,
            int defaultBatteryCells,
            double defaultBatteryAmpHours,
            double defaultBatteryMassKg,
            double defaultBatteryResistanceOhms,
            double controlledHorizontalSpeedMetersPerSecond,
            double maximumClimbSpeedMetersPerSecond,
            double maximumDescentSpeedMetersPerSecond,
            double maximumTiltRadians
    ) {
        this.dryMassKg = dryMassKg;
        this.nominalMaximumThrustNewtons = nominalMaximumThrustNewtons;
        this.dragCoefficient = dragCoefficient;
        this.referenceAreaSquareMeters = referenceAreaSquareMeters;
        this.safetySpeedLimitMetersPerSecond = safetySpeedLimitMetersPerSecond;
        this.avionicsCurrentAmps = avionicsCurrentAmps;
        this.maximumMotorCurrentAmps = maximumMotorCurrentAmps;
        this.defaultBatteryCells = defaultBatteryCells;
        this.defaultBatteryAmpHours = defaultBatteryAmpHours;
        this.defaultBatteryMassKg = defaultBatteryMassKg;
        this.defaultBatteryResistanceOhms = defaultBatteryResistanceOhms;
        this.controlledHorizontalSpeedMetersPerSecond = controlledHorizontalSpeedMetersPerSecond;
        this.maximumClimbSpeedMetersPerSecond = maximumClimbSpeedMetersPerSecond;
        this.maximumDescentSpeedMetersPerSecond = maximumDescentSpeedMetersPerSecond;
        this.maximumTiltRadians = maximumTiltRadians;
    }

    public BatteryState defaultBattery() {
        return BatteryState.lipo(
                defaultBatteryCells,
                defaultBatteryAmpHours,
                defaultBatteryMassKg,
                defaultBatteryResistanceOhms
        );
    }

    public double referenceMassKg() {
        return dryMassKg + defaultBatteryMassKg;
    }

    public double dryMassKg() {
        return dryMassKg;
    }

    public double nominalMaximumThrustNewtons() {
        return nominalMaximumThrustNewtons;
    }

    public double dragCoefficient() {
        return dragCoefficient;
    }

    public double referenceAreaSquareMeters() {
        return referenceAreaSquareMeters;
    }

    public double safetySpeedLimitMetersPerSecond() {
        return safetySpeedLimitMetersPerSecond;
    }

    public double avionicsCurrentAmps() {
        return avionicsCurrentAmps;
    }

    public double maximumMotorCurrentAmps() {
        return maximumMotorCurrentAmps;
    }

    public double controlledHorizontalSpeedMetersPerSecond() {
        return controlledHorizontalSpeedMetersPerSecond;
    }

    public double maximumClimbSpeedMetersPerSecond() {
        return maximumClimbSpeedMetersPerSecond;
    }

    public double maximumDescentSpeedMetersPerSecond() {
        return maximumDescentSpeedMetersPerSecond;
    }

    public double maximumTiltRadians() {
        return maximumTiltRadians;
    }
}
