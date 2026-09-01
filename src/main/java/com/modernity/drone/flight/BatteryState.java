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
    private static final double EMPTY_CELL_VOLTAGE = 3.3;

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
        return cellCount * EMPTY_CELL_VOLTAGE;
    }

    /** Approximate resting voltage from state of charge. */
    public double openCircuitVoltage() {
        if (remainingWattHours <= 0.0) {
            return 0.0;
        }
        double charge = stateOfCharge();
        double cellVoltage = EMPTY_CELL_VOLTAGE
                + (FULL_CELL_VOLTAGE - EMPTY_CELL_VOLTAGE) * charge;
        return cellVoltage * cellCount;
    }

    /** Terminal voltage after deterministic V=OCV-I*R sag. */
    public double voltageUnderLoad(double currentAmps) {
        double current = Math.max(0.0, FlightMath.finiteOr(currentAmps, 0.0));
        if (isDepleted()) return 0.0;
        // The old battery manager grows per-cell IR from 8 mOhm fresh to
        // 20 mOhm empty. internalResistanceOhms stores the fresh pack total.
        double depletion = 1.0 - stateOfCharge();
        double effectiveResistance = internalResistanceOhms * (1.0 + 1.5 * depletion);
        return Math.max(cutoffVoltage(), openCircuitVoltage() - current * effectiveResistance);
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
        // Capacity in V1.1.4 is tracked in mAh.  Using the changing terminal
        // voltage here shortened runtime at full charge; nominal voltage keeps
        // this energy-backed representation exactly equivalent to Ah drain.
        double wattHours = nominalVoltage() * current * duration / 3600.0;
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
