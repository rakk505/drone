package com.modernity.drone.client.osd;

import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.flight.BatteryState;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Snapshot of values consumed by the 30x16 OSD renderer. */
public record OsdTelemetry(
        DroneEntity drone,
        LocalPlayer pilot,
        Vec3 home,
        float signal,
        double distance,
        float batteryFraction,
        int batteryCells,
        int capacityMah,
        float cellVoltage,
        float totalVoltage,
        float currentAmps,
        int usedMah,
        float throttle,
        float headingDegrees,
        float pitchDegrees,
        float rollDegrees,
        float speedMetersPerSecond,
        float verticalSpeedMetersPerSecond,
        boolean noSignal,
        long nowMillis
) {
    public static OsdTelemetry capture(DroneEntity drone, LocalPlayer player, Vec3 home,
                                       float signal, double distance, boolean noSignal, long nowMillis) {
        BatteryState reference = drone.kind().defaultBattery();
        int cells = reference.cellCount();
        int capacity = (int) Math.round(reference.capacityWattHours() / reference.nominalVoltage() * 1000.0);
        float battery = Mth.clamp(drone.batteryFraction(), 0.0F, 1.0F);
        float throttle = Mth.clamp(drone.getSyncedThrottle(), 0.0F, 1.0F);
        float current = (float) drone.getCurrentAmps();
        if (!(current > 0.0F)) {
            current = estimatedCurrent(drone.getTotalWeightGrams(), throttle, drone.isArmed());
        }
        float openCellVoltage = 4.2F - (1.0F - battery) * 0.9F;
        float resistancePerCell = 0.008F + (1.0F - battery) * 0.012F;
        float cellVoltage = Math.max(3.3F, openCellVoltage - current * resistancePerCell);
        float voltage = (float) drone.getBatteryVoltage();
        if (!(voltage > 0.0F)) {
            voltage = cellVoltage * cells;
        } else {
            cellVoltage = voltage / Math.max(1, cells);
        }
        int used = drone.getUsedMah();
        if (used <= 0 && battery < 0.999F) {
            used = Math.round(capacity * (1.0F - battery));
        }
        float heading = Mth.wrapDegrees(drone.getYRot());
        if (heading < 0.0F) {
            heading += 360.0F;
        }
        return new OsdTelemetry(
                drone,
                player,
                home,
                signal,
                distance,
                battery,
                cells,
                capacity,
                cellVoltage,
                voltage,
                current,
                used,
                throttle,
                heading,
                -drone.getXRot(),
                drone.rollDegrees(1.0F),
                (float) drone.flightSpeedMetersPerSecond(),
                (float) (drone.getDeltaMovement().y * 20.0),
                noSignal,
                nowMillis
        );
    }

    public int batteryPercent() {
        return Math.round(batteryFraction * 100.0F);
    }

    /** Retains the last visible telemetry while allowing warning cells and timers to advance. */
    public OsdTelemetry asNoSignal(long nowMillis) {
        return new OsdTelemetry(
                drone, pilot, home, 0.0F, distance, batteryFraction, batteryCells, capacityMah,
                cellVoltage, totalVoltage, currentAmps, usedMah, throttle, headingDegrees,
                pitchDegrees, rollDegrees, speedMetersPerSecond, verticalSpeedMetersPerSecond,
                true, nowMillis
        );
    }

    public int remainingMah() {
        return Math.max(0, capacityMah - usedMah);
    }

    public float watts() {
        return currentAmps * totalVoltage;
    }

    public String pilotName() {
        return pilot == null ? "PILOT" : pilot.getName().getString().toUpperCase(Locale.ROOT);
    }

    private static float estimatedCurrent(float weightGrams, float throttle, boolean armed) {
        if (!armed || throttle < 0.01F) {
            return 1.5F;
        }
        float hover;
        float maximum;
        if (weightGrams <= 1050.0F) {
            hover = 12.0F * (float) Math.pow(weightGrams / 1050.0F, 1.5);
            maximum = 45.0F;
        } else if (weightGrams <= 1500.0F) {
            float amount = (weightGrams - 1050.0F) / 450.0F;
            hover = 12.0F + amount * 13.0F;
            maximum = 45.0F + amount * 30.0F;
        } else {
            float amount = Math.min(1.0F, (weightGrams - 1500.0F) / 2000.0F);
            hover = 25.0F + amount * 55.0F;
            maximum = 75.0F + amount * 45.0F;
        }
        return hover + throttle * throttle * (maximum - hover);
    }
}
