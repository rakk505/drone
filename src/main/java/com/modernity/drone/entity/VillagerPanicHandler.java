package com.modernity.drone.entity;

import com.modernity.drone.DroneMod;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Restores the original villager reaction to nearby FPV aircraft. */
@EventBusSubscriber(modid = DroneMod.MOD_ID)
public final class VillagerPanicHandler {
    private static final double PANIC_RANGE = 24.0;
    private static final int TICK_INTERVAL = 10;
    private static final int BELL_COOLDOWN_TICKS = 100;
    private static final int BELL_CACHE_TICKS = 200;
    private static final Map<BellCacheKey, CachedBell> BELL_CACHE = new HashMap<>();
    private static long lastBellRing = -BELL_COOLDOWN_TICKS - 1L;

    private VillagerPanicHandler() {
    }

    @SubscribeEvent
    public static void onVillagerTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Villager villager)
                || !(villager.level() instanceof ServerLevel level)
                || villager.tickCount % TICK_INTERVAL != 0) {
            return;
        }

        AABB searchBox = villager.getBoundingBox().inflate(PANIC_RANGE);
        DroneEntity drone = level.getEntitiesOfClass(
                        DroneEntity.class,
                        searchBox,
                        candidate -> candidate.isAlive())
                .stream()
                .min(Comparator.comparingDouble(villager::distanceToSqr))
                .orElse(null);
        if (drone == null) return;

        // The 1.20.1 drone was a LivingEntity and could occupy NEAREST_HOSTILE.
        // The 26.2 server-authoritative craft is a plain Entity, so use the
        // registered damage-source memory to select PANIC without dealing any
        // damage, then drive the same sixteen-block flee directly.
        villager.getBrain().setMemoryWithExpiry(
                MemoryModuleType.HURT_BY,
                level.damageSources().thrown(drone, drone),
                20L
        );
        Vec3 away = villager.position().subtract(drone.position());
        if (away.lengthSqr() < 1.0E-6) {
            double angle = villager.getRandom().nextDouble() * Math.PI * 2.0;
            away = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
        } else {
            away = away.normalize();
        }
        Vec3 target = villager.position().add(away.scale(16.0));
        villager.getNavigation().moveTo(target.x, target.y, target.z, 1.0);

        long gameTime = level.getGameTime();
        if (gameTime >= lastBellRing && gameTime - lastBellRing <= BELL_COOLDOWN_TICKS) return;
        BlockPos bellPos = cachedBell(level, villager.blockPosition(), gameTime);
        if (bellPos != null && level.getBlockState(bellPos).getBlock() instanceof BellBlock bell
                && bell.attemptToRing(level, bellPos, null)) {
            lastBellRing = gameTime;
        }
    }

    private static BlockPos cachedBell(ServerLevel level, BlockPos center, long gameTime) {
        BellCacheKey key = new BellCacheKey(
                level.dimension().identifier().toString(),
                center.getX() >> 4,
                center.getZ() >> 4);
        CachedBell cached = BELL_CACHE.get(key);
        if (cached != null && gameTime >= cached.checkedAt
                && gameTime - cached.checkedAt < BELL_CACHE_TICKS) {
            return cached.position;
        }
        BlockPos found = findBell(level, center);
        BELL_CACHE.put(key, new CachedBell(found, gameTime));
        return found;
    }

    private static BlockPos findBell(ServerLevel level, BlockPos center) {
        for (int y = -4; y <= 4; y++) {
            for (int x = -32; x <= 32; x += 4) {
                for (int z = -32; z <= 32; z += 4) {
                    BlockPos candidate = center.offset(x, y, z);
                    if (level.getBlockState(candidate).getBlock() instanceof BellBlock) {
                        return candidate.immutable();
                    }
                }
            }
        }
        return null;
    }

    static void clearCache() {
        BELL_CACHE.clear();
        lastBellRing = -BELL_COOLDOWN_TICKS - 1L;
    }

    private record BellCacheKey(String dimension, int chunkX, int chunkZ) {
    }

    private record CachedBell(BlockPos position, long checkedAt) {
    }
}
