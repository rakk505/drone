package com.modernity.drone.client.audio;

import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Small lifecycle facade for wiring the V1.1.4 audio engines into client events. */
public final class FpvAudioEngine {
    private static DroneSoundInstance localMotorLoop;

    private FpvAudioEngine() {
    }

    /** Starts asynchronous decoding once the client resource manager exists. */
    public static void initIfNeeded() {
        ExplosionAudioEngine.get().initIfNeeded();
        DroneEngineAudioEngine.get().initIfNeeded();
    }

    /** Call from {@code ClientTickEvent.Post}. */
    public static void tick() {
        initIfNeeded();
        tickLocalMotorLoop();
        ExplosionAudioEngine.get().tick();
        DroneEngineAudioEngine.get().tick();
    }

    /** Call when the client leaves a level/server. Uploaded buffers stay reusable. */
    public static void onLogout() {
        stopLocalMotorLoop();
        ExplosionAudioEngine.get().onDisconnect();
        DroneEngineAudioEngine.get().onDisconnect();
    }

    /** Call only during final client shutdown, while the OpenAL context still exists. */
    public static void shutdown() {
        stopLocalMotorLoop();
        ExplosionAudioEngine.get().shutdown();
        DroneEngineAudioEngine.get().shutdown();
    }

    public static void playExplosion(Vec3 position, boolean indoors,
                                     int closeVariant, int distantVariant, float power) {
        ExplosionAudioEngine.get().playExplosion(position, indoors, closeVariant, distantVariant, power);
    }

    private static void tickLocalMotorLoop() {
        Minecraft minecraft = Minecraft.getInstance();
        DroneEntity drone = DroneControlClient.currentDrone();
        boolean shouldPlay = minecraft.level != null
                && drone != null
                && drone.areMotorsArmed()
                && drone.hasBattery()
                && drone.batteryFraction() > 0.0F
                && drone.signalQuality() > 0.0F;

        if (!shouldPlay) {
            stopLocalMotorLoop();
            return;
        }
        if (localMotorLoop != null
                && localMotorLoop.droneId().equals(drone.getUUID())
                && !localMotorLoop.isStopped()) {
            return;
        }

        stopLocalMotorLoop();
        localMotorLoop = new DroneSoundInstance(drone);
        minecraft.getSoundManager().play(localMotorLoop);
    }

    private static void stopLocalMotorLoop() {
        if (localMotorLoop == null) {
            return;
        }
        Minecraft.getInstance().getSoundManager().stop(localMotorLoop);
        localMotorLoop = null;
    }
}
