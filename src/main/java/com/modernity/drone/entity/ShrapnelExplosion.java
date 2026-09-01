package com.modernity.drone.entity;

import com.modernity.drone.Config;
import com.modernity.drone.network.ExplosionSoundPayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.common.extensions.ICommonPacketListener;

/** Blast-wave plus deterministic golden-spiral fragmentation used by the reference mod. */
public final class ShrapnelExplosion {
    private static final double LETHAL_RANGE = 1.5;

    private ShrapnelExplosion() {
    }

    public static void detonate(ServerLevel level, Vec3 center, float power, int fragments,
                                Entity source, UUID pilotId) {
        double fragmentRadius = Math.max(2.5, power * 3.0);
        double blastRadius = Config.EXPLOSION_BLAST_RADIUS.getAsDouble();
        ExplosionSoundPayload soundPayload = new ExplosionSoundPayload(
                center,
                isIndoors(level, center),
                level.getRandom().nextInt(2),
                level.getRandom().nextInt(2),
                power
        );
        double soundRangeSquared = 150.0 * 150.0;
        for (net.minecraft.server.level.ServerPlayer player : level.players()) {
            // GameTest/mock and vanilla connections do not negotiate our
            // clientbound channel. Sending anyway makes NeoForge reject the
            // packet and can crash the server during an operator impact.
            if (player.distanceToSqr(center) <= soundRangeSquared
                    && ((ICommonPacketListener) player.connection).hasChannel(ExplosionSoundPayload.TYPE)) {
                PacketDistributor.sendToPlayer(player, soundPayload);
            }
        }
        DamageSource damageSource = level.damageSources().explosion(source,
                pilotId == null ? null : level.getEntityInAnyDimension(pilotId));
        Map<LivingEntity, Float> damage = new HashMap<>();

        AABB fragmentBounds = new AABB(center, center).inflate(fragmentRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, fragmentBounds,
                e -> e.isAlive() && !e.getUUID().equals(pilotId))) {
            double distance = distanceToBox(center, target.getBoundingBox());
            if (distance <= fragmentRadius
                    && clearLine(level, center, target.getBoundingBox().getCenter(), source)) {
                float amount = fragmentDamage(distance, fragmentRadius, power);
                if (amount >= 0.5F) damage.put(target, amount);
            }
        }

        int count = Math.max(8, Math.min(512, fragments));
        for (int i = 0; i < count; i++) {
            Vec3 end = center.add(goldenSpiralDirection(i, count).scale(fragmentRadius));
            HitResult blockHit = level.clip(new ClipContext(center, end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, source));
            Vec3 clippedEnd = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
            AABB rayBounds = new AABB(center, clippedEnd).inflate(0.4);
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, rayBounds,
                    e -> e.isAlive() && !e.getUUID().equals(pilotId)
                            && !damage.containsKey(e))) {
                java.util.Optional<Vec3> hit = target.getBoundingBox().inflate(0.4).clip(center, clippedEnd);
                if (hit.isPresent()) {
                    float amount = fragmentDamage(center.distanceTo(hit.get()), fragmentRadius, power);
                    if (amount >= 0.5F) damage.put(target, amount);
                }
            }
        }

        AABB blastBounds = new AABB(center, center).inflate(blastRadius);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, blastBounds,
                e -> e.isAlive() && !e.getUUID().equals(pilotId))) {
            double distance = distanceToBox(center, target.getBoundingBox());
            if (distance > blastRadius
                    || !clearLine(level, center, target.getBoundingBox().getCenter(), source)) continue;
            double killZone = Config.EXPLOSION_KILL_ZONE.getAsDouble();
            float wave = distance <= killZone ? 1000.0F : (float) (Config.EXPLOSION_BLAST_DAMAGE.getAsDouble()
                    * Math.pow(1.0 - Math.min(1.0, Math.max(0.0,
                    (distance - killZone) / Math.max(0.001, blastRadius - killZone))), 3.0));
            if (wave >= 0.5F) damage.merge(target, wave, Math::max);
        }

        damage.forEach((target, amount) -> {
            target.hurtServer(level, damageSource, amount >= 1000.0F ? 1000.0F : Math.min(amount, 60.0F));
            Vec3 away = target.position().subtract(center);
            if (away.lengthSqr() > 1.0E-6) target.setDeltaMovement(target.getDeltaMovement().add(away.normalize().scale(power * 0.5)));
        });

        if (Config.EXPLOSION_OPENS_DOORS.getAsBoolean()) openDoors(level, center, blastRadius);
        float blockPower = (float) Config.EXPLOSION_BLOCK_POWER.getAsDouble();
        if (blockPower > 0.01F) {
            level.explode(source, center.x, center.y, center.z, blockPower,
                    Level.ExplosionInteraction.BLOCK);
        } else {
            level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0, 0, 0, 0);
        }
    }

    private static boolean clearLine(ServerLevel level, Vec3 start, Vec3 end, Entity source) {
        return level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, source)).getType() == HitResult.Type.MISS;
    }

    private static float fragmentDamage(double distance, double radius, float power) {
        float peak = 10.0F + 3.0F * power;
        if (distance <= LETHAL_RANGE) return peak;
        double t = Math.min(1.0, Math.max(0.0, (distance - LETHAL_RANGE) / Math.max(0.001, radius - LETHAL_RANGE)));
        return (float) (peak * (1.0 - t) * (1.0 - t));
    }

    private static double distanceToBox(Vec3 center, AABB box) {
        double x = Math.max(box.minX, Math.min(center.x, box.maxX));
        double y = Math.max(box.minY, Math.min(center.y, box.maxY));
        double z = Math.max(box.minZ, Math.min(center.z, box.maxZ));
        return center.distanceTo(new Vec3(x, y, z));
    }

    private static Vec3 goldenSpiralDirection(int index, int total) {
        double phi = Math.acos(1.0 - 2.0 * (index + 0.5) / total);
        double theta = Math.PI * 2.0 * index / ((1.0 + Math.sqrt(5.0)) / 2.0);
        return new Vec3(Math.sin(phi) * Math.cos(theta), Math.sin(phi) * Math.sin(theta), Math.cos(phi));
    }

    private static void openDoors(ServerLevel level, Vec3 center, double radius) {
        BlockPos origin = BlockPos.containing(center);
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
            if (dx * dx + dy * dy + dz * dz > radius * radius) continue;
            BlockPos pos = origin.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DoorBlock door
                    && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                    && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    && state.hasProperty(BlockStateProperties.OPEN)
                    && !state.getValue(BlockStateProperties.OPEN)) {
                door.setOpen(null, level, state, pos, true);
            } else if ((state.getBlock() instanceof TrapDoorBlock || state.getBlock() instanceof FenceGateBlock)
                    && state.hasProperty(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.OPEN)) {
                level.setBlock(pos, state.setValue(BlockStateProperties.OPEN, true), 10);
            }
        }
    }

    private static boolean isIndoors(ServerLevel level, Vec3 center) {
        BlockPos origin = BlockPos.containing(center);
        BlockPos[] samples = {
                origin, origin.north(), origin.south(), origin.east(), origin.west()
        };
        int ceilings = 0;
        for (BlockPos sample : samples) {
            for (int dy = 1; dy <= 16; dy++) {
                BlockPos above = sample.above(dy);
                BlockState state = level.getBlockState(above);
                if (!state.isAir() && !state.getCollisionShape(level, above).isEmpty()) {
                    ceilings++;
                    break;
                }
            }
        }
        return ceilings >= 4;
    }
}
