package com.modernity.drone.client.thermal;

import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** Non-uniformity correction timing from the 1.1.4 thermal sensor simulation. */
public final class NucManager {
    public static final long MIN_INTERVAL_MILLIS = 180_000L;
    public static final long MAX_INTERVAL_MILLIS = 300_000L;
    public static final long FREEZE_MILLIS = 800L;
    public static final long FPN_RECOVERY_MILLIS = 180_000L;
    public static final float FPN_REDUCTION_AFTER_NUC = 0.4F;

    private long lastNucMillis = System.currentTimeMillis();
    private long nextIntervalMillis = randomInterval();
    private long freezeStartedMillis;
    private long fpnReductionStartedMillis;
    private boolean frozen;
    private float fpnReduction;

    public void tick(long nowMillis) {
        if (frozen) {
            if (nowMillis - freezeStartedMillis >= FREEZE_MILLIS) {
                frozen = false;
                fpnReduction = FPN_REDUCTION_AFTER_NUC;
                fpnReductionStartedMillis = nowMillis;
            }
            return;
        }
        if (fpnReduction > 0.0F) {
            long elapsed = nowMillis - fpnReductionStartedMillis;
            fpnReduction = elapsed >= FPN_RECOVERY_MILLIS
                    ? 0.0F
                    : FPN_REDUCTION_AFTER_NUC * (1.0F - (float) elapsed / FPN_RECOVERY_MILLIS);
        }
        if (nowMillis - lastNucMillis >= nextIntervalMillis) {
            trigger(nowMillis);
        }
    }

    public void trigger(long nowMillis) {
        lastNucMillis = nowMillis;
        nextIntervalMillis = randomInterval();
        freezeStartedMillis = nowMillis;
        frozen = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSoundManager() != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.LEVER_CLICK, 1.0F));
        }
    }

    public void reset(long nowMillis) {
        lastNucMillis = nowMillis;
        nextIntervalMillis = randomInterval();
        freezeStartedMillis = 0L;
        fpnReductionStartedMillis = 0L;
        frozen = false;
        fpnReduction = 0.0F;
    }

    public boolean frozen() {
        return frozen;
    }

    public float fpnReduction() {
        return fpnReduction;
    }

    private static long randomInterval() {
        return ThreadLocalRandom.current().nextLong(MIN_INTERVAL_MILLIS, MAX_INTERVAL_MILLIS + 1L);
    }
}
