package com.modernity.drone.flight;

/** Immutable LiPo battery energy and electrical model. */
public record BatteryState(
        int cellCount,
        double capacityWattHours,
        double remainingWattHours,
        double internalResistanceOhms,
        double massKg
) {
    private static final double NOMINAL_CELL_VOLTAGE = 3.7;
    private static final double FULL_CELL_VOLTAGE = 4.2;
    private static final double EMPTY_CELL_VOLTAGE = 3.2;
    private static final double CUTOFF_CELL_VOLTAGE = 3.0;

    public BatteryState {
        cellCount = Math.max(1, Math.min(24, cellCount));
        capacityWattHours = Math.max(1.0e-6, FlightMath.finiteOr(capacityWattHours, 1.0e-6));
        remainingWattHours = FlightMath.clamp(
                FlightMath.finiteOr(remainingWattHours, 0.0),
                0.0,
                capacityWattHours
        );
        internalResistanceOhms = FlightMath.clamp(
                FlightMath.finiteOr(internalResistanceOhms, 0.0),
                0.0,
                2.0
        );
        massKg = FlightMath.clamp(FlightMath.finiteOr(massKg, 0.0), 0.0, 25.0);
    }

    public static BatteryState lipo(
            int cellCount,
            double capacityAmpHours,
            double massKg,
            double internalResistanceOhms
    ) {
        int cells = Math.max(1, Math.min(24, cellCount));
        double ampHours = Math.max(1.0e-6, FlightMath.finiteOr(capacityAmpHours, 1.0e-6));
        double energy = cells * NOMINAL_CELL_VOLTAGE * ampHours;
        return new BatteryState(cells, energy, energy, internalResistanceOhms, massKg);
    }

    public double stateOfCharge() {
        return FlightMath.clamp(remainingWattHours / capacityWattHours, 0.0, 1.0);
    }

    public double nominalVoltage() {
        return cellCount * NOMINAL_CELL_VOLTAGE;
    }

    public double fullVoltage() {
        return cellCount * FULL_CELL_VOLTAGE;
    }

    public double cutoffVoltage() {
        return cellCount * CUTOFF_CELL_VOLTAGE;
    }

    /** Approximate resting voltage from state of charge. */
    public double openCircuitVoltage() {
        if (remainingWattHours <= 0.0) {
            return 0.0;
        }
        double charge = stateOfCharge();
        double cellVoltage;
        if (charge < 0.1) {
            cellVoltage = EMPTY_CELL_VOLTAGE + 4.0 * charge;
        } else if (charge < 0.9) {
            cellVoltage = 3.6 + 0.5 * (charge - 0.1);
        } else {
            cellVoltage = 4.0 + 2.0 * (charge - 0.9);
        }
        return cellVoltage * cellCount;
    }

    /** Terminal voltage after deterministic V=OCV-I*R sag. */
    public double voltageUnderLoad(double currentAmps) {
        double current = Math.max(0.0, FlightMath.finiteOr(currentAmps, 0.0));
        return Math.max(0.0, openCircuitVoltage() - current * internalResistanceOhms);
    }

    public BatteryState drainEnergy(double wattHours) {
        double drain = Math.max(0.0, FlightMath.finiteOr(wattHours, 0.0));
        return new BatteryState(
                cellCount,
                capacityWattHours,
                Math.max(0.0, remainingWattHours - drain),
                internalResistanceOhms,
                massKg
        );
    }

    /** Drains chemical energy for a constant current over a time interval. */
    public BatteryState drainCurrent(double currentAmps, double seconds) {
        double current = Math.max(0.0, FlightMath.finiteOr(currentAmps, 0.0));
        double duration = Math.max(0.0, FlightMath.finiteOr(seconds, 0.0));
        double wattHours = openCircuitVoltage() * current * duration / 3600.0;
        return drainEnergy(wattHours);
    }

    public BatteryState withStateOfCharge(double stateOfCharge) {
        double charge = FlightMath.clamp(FlightMath.finiteOr(stateOfCharge, 0.0), 0.0, 1.0);
        return new BatteryState(
                cellCount,
                capacityWattHours,
                capacityWattHours * charge,
                internalResistanceOhms,
                massKg
        );
    }

    public boolean isDepleted() {
        return remainingWattHours <= 1.0e-9;
    }
}
