package com.modernity.drone.client.audio;

import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.entity.DroneEntity;
import java.util.UUID;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/** The first-person motor/wind loop used while piloting the local drone. */
public final class DroneSoundInstance extends AbstractTickableSoundInstance {
    private final DroneEntity drone;

    public DroneSoundInstance(DroneEntity drone) {
        super(SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
        this.drone = drone;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.1F;
    }

    public UUID droneId() {
        return drone.getUUID();
    }

    @Override
    public void tick() {
        DroneEntity active = DroneControlClient.currentDrone();
        if (drone.isRemoved()
                || !drone.isAlive()
                || active == null
                || !active.getUUID().equals(drone.getUUID())
                || !drone.areMotorsArmed()
                || !drone.hasBattery()
                || drone.batteryFraction() <= 0.0F
                || drone.signalQuality() <= 0.0F) {
            stop();
            return;
        }

        x = drone.getX();
        y = drone.getY();
        z = drone.getZ();
        float throttle = drone.getSyncedThrottle();
        float speed = (float) drone.getDeltaMovement().length();
        float intensity = Math.max(throttle, speed * 0.3F);
        volume = Mth.clamp(0.15F + intensity * 0.85F, 0.15F, 1.0F);
        pitch = Mth.clamp(0.7F + intensity * 0.8F, 0.7F, 1.5F);
    }
}
