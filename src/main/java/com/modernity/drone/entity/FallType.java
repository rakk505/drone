package com.modernity.drone.entity;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

public enum FallType {
    DEAD_DROP(0, 5, 0, 5, 0, 5, 0.0F, 1.0F),
    SPIN_OUT(300, 720, 0, 15, 5, 30, 0.15F, 1.2F),
    TUMBLE(90, 360, 90, 360, 90, 360, 0.0F, 0.9F),
    GLIDE_DOWN(0, 10, 15, 45, 0, 10, 0.0F, 1.8F),
    FLIP_AND_DROP(0, 5, 0, 5, 0, 5, 0.0F, 1.0F);

    final float minYaw, maxYaw, minPitch, maxPitch, minRoll, maxRoll;
    final float thrustFactor, dragMultiplier;

    FallType(float minYaw, float maxYaw, float minPitch, float maxPitch,
             float minRoll, float maxRoll, float thrustFactor, float dragMultiplier) {
        this.minYaw = minYaw; this.maxYaw = maxYaw;
        this.minPitch = minPitch; this.maxPitch = maxPitch;
        this.minRoll = minRoll; this.maxRoll = maxRoll;
        this.thrustFactor = thrustFactor; this.dragMultiplier = dragMultiplier;
    }

    float randomYaw(net.minecraft.util.RandomSource random) { return between(random, minYaw, maxYaw); }
    float randomPitch(net.minecraft.util.RandomSource random) { return between(random, minPitch, maxPitch); }
    float randomRoll(net.minecraft.util.RandomSource random) { return between(random, minRoll, maxRoll); }

    private static float between(net.minecraft.util.RandomSource random, float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    public static FallType determine(DamageSource source, float amount, float speed, boolean fromAbove) {
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.is(DamageTypeTags.IS_FIRE)) return DEAD_DROP;
        if (amount > 7.0F) return TUMBLE;
        if (speed > 15.0F && amount < 5.0F) return GLIDE_DOWN;
        return fromAbove ? FLIP_AND_DROP : SPIN_OUT;
    }
}
