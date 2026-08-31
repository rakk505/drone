package com.modernity.drone.entity;

import com.modernity.drone.Config;
import com.modernity.drone.DroneMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DroppedPayloadEntity extends ThrowableItemProjectile {
    public static final int ARMING_DELAY_TICKS = 30;
    public static final int MAXIMUM_LIFETIME_TICKS = 600;
    private static final double DRAG_FACTOR = 0.5 * 1.225 * 0.55 * 0.0013 / 0.25;

    private boolean detonated;
    private int payloadAge;
    private int rearmTick = ARMING_DELAY_TICKS;

    public DroppedPayloadEntity(EntityType<? extends DroppedPayloadEntity> type, Level level) {
        super(type, level);
    }

    public void configureRelease(DroneEntity source, Vec3 inheritedVelocityBlocksPerTick) {
        setOwner(source);
        setItem(new ItemStack(DroneMod.FORTY_MM_PAYLOAD.get()));
        setDeltaMovement(inheritedVelocityBlocksPerTick);
        payloadAge = 0;
        rearmTick = ARMING_DELAY_TICKS;
        detonated = false;
    }

    @Override
    public void tick() {
        payloadAge++;
        super.tick();

        Vec3 velocity = getDeltaMovement();
        double speedMetersPerSecond = velocity.length() * 20.0;
        if (speedMetersPerSecond > 0.0) {
            // For F_drag = k * v^2, the per-tick velocity multiplier is
            // 1 / (1 + k * |v| * dt). This remains stable at extreme speeds.
            double factor = 1.0 / (1.0 + DRAG_FACTOR * speedMetersPerSecond * 0.05);
            setDeltaMovement(velocity.scale(factor));
        }

        if (!level().isClientSide() && payloadAge >= MAXIMUM_LIFETIME_TICKS) {
            discard();
        }
    }

    @Override
    protected float getAirDrag() {
        return 1.0F;
    }

    @Override
    protected double getDefaultGravity() {
        return 0.0245;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (result instanceof EntityHitResult entityHit && entityHit.getEntity() == getOwner()) {
            return;
        }
        if (!(level() instanceof ServerLevel serverLevel) || detonated) {
            return;
        }
        if (isArmed()) {
            detonate(serverLevel);
        } else {
            detonated = true;
            spawnAtLocation(serverLevel, getItem().copy());
            discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (result.getEntity() != getOwner()) {
            super.onHitEntity(result);
        }
    }

    public boolean isArmed() {
        return payloadAge >= Math.max(ARMING_DELAY_TICKS, rearmTick);
    }

    public int payloadAgeForTesting() {
        return payloadAge;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        Entity attacker = source.getEntity();
        if (attacker == null || detonated) {
            return false;
        }
        Vec3 reflected = getDeltaMovement().scale(-0.45).add(attacker.getLookAngle().scale(0.08));
        if (reflected.lengthSqr() > 0.45 * 0.45) {
            reflected = reflected.normalize().scale(0.45);
        }
        setDeltaMovement(reflected);
        setOwner(attacker);
        rearmTick = payloadAge + 4;
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        payloadAge = Math.max(0, input.getIntOr("PayloadAge", 0));
        rearmTick = Math.max(ARMING_DELAY_TICKS, input.getIntOr("RearmTick", ARMING_DELAY_TICKS));
        detonated = false;
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("PayloadAge", payloadAge);
        output.putInt("RearmTick", rearmTick);
    }

    public void detonate(ServerLevel level) {
        if (detonated) {
            return;
        }
        detonated = true;
        Vec3 origin = position();
        Entity owner = getOwner();
        DamageSource damage = level.damageSources().explosion(this, owner);
        AABB bounds = getBoundingBox().inflate(8.0);
        for (Entity target : level.getEntities(this, bounds, Entity::isAlive)) {
            double distance = target.getEyePosition().distanceTo(origin);
            if (distance > 8.0) {
                continue;
            }
            HitResult obstruction = level.clip(new ClipContext(
                    origin,
                    target.getEyePosition(),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this
            ));
            if (obstruction.getType() == HitResult.Type.MISS) {
                double normalized = Math.max(0.0, 1.0 - distance / 8.0);
                float fragmentDamage = (float) (12.0 * normalized * normalized);
                if (fragmentDamage > 0.25F) {
                    target.hurtServer(level, damage, fragmentDamage);
                }
            }
        }
        level.explode(
                this,
                getX(),
                getY(),
                getZ(),
                1.5F,
                Config.PAYLOAD_BLOCK_DAMAGE.getAsBoolean()
                        ? Level.ExplosionInteraction.BLOCK
                        : Level.ExplosionInteraction.NONE
        );
        discard();
    }

    @Override
    protected Item getDefaultItem() {
        return DroneMod.FORTY_MM_PAYLOAD.get();
    }
}
