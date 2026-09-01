package com.modernity.drone.client.thermal;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Persistent, cooling footprints deposited by grounded moving living entities. */
public final class FootprintSystem {
    public static final int LIFETIME_TICKS = 800;
    public static final int MAX_DECALS = 2048;
    private static final double MOVE_THRESHOLD_SQUARED = 4.0E-4;
    private static final float FOOT_SIZE_FACTOR = 0.3125F;
    private static final float FOOT_LATERAL_OFFSET = 0.25F;
    private static final float DEPOSIT_DISTANCE_FACTOR = 2.5F;
    private static final FootprintSystem INSTANCE = new FootprintSystem();

    private final Deque<Footprint> footprints = new ArrayDeque<>();
    private final Map<UUID, EntityFootState> entityStates = new HashMap<>();

    private FootprintSystem() {
    }

    public static FootprintSystem get() {
        return INSTANCE;
    }

    public void tick(ClientLevel level) {
        long gameTime = level.getGameTime();
        ageAndCull(level);
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living)
                    || !living.onGround()) {
                continue;
            }
            double dx = living.getX() - living.xo;
            double dz = living.getZ() - living.zo;
            if (dx * dx + dz * dz < MOVE_THRESHOLD_SQUARED) {
                continue;
            }

            EntityFootState footState = entityStates.computeIfAbsent(
                    living.getUUID(), ignored -> new EntityFootState());
            float footSize = living.getBbWidth() * FOOT_SIZE_FACTOR;
            float minimumDistance = footSize * DEPOSIT_DISTANCE_FACTOR;
            double lastDx = living.getX() - footState.lastX;
            double lastDz = living.getZ() - footState.lastZ;
            if (footState.lastTick != 0L
                    && lastDx * lastDx + lastDz * lastDz < minimumDistance * minimumDistance) {
                continue;
            }
            deposit(living, footState, footSize, gameTime);
        }

        if (gameTime % 100L == 0L) {
            entityStates.entrySet().removeIf(entry -> gameTime - entry.getValue().lastTick > LIFETIME_TICKS);
        }
    }

    public Iterable<Footprint> footprints() {
        return footprints;
    }

    public int size() {
        return footprints.size();
    }

    public void clear() {
        footprints.clear();
        entityStates.clear();
    }

    private void ageAndCull(ClientLevel level) {
        Iterator<Footprint> iterator = footprints.iterator();
        while (iterator.hasNext()) {
            Footprint footprint = iterator.next();
            footprint.age++;
            BlockPos supportingBlock = BlockPos.containing(
                    footprint.x(), footprint.y() - 0.01, footprint.z());
            if (footprint.age() >= LIFETIME_TICKS
                    || !level.isLoaded(supportingBlock)
                    || !level.getBlockState(supportingBlock).isSolid()) {
                iterator.remove();
            }
        }
    }

    private void deposit(LivingEntity entity, EntityFootState state, float size, long gameTime) {
        float bodyYaw = entity.yBodyRot;
        double directionX = -Math.sin(Math.toRadians(bodyYaw));
        double directionZ = Math.cos(Math.toRadians(bodyYaw));
        double perpendicularX = -directionZ;
        double perpendicularZ = directionX;
        state.left = !state.left;
        double side = state.left ? -1.0 : 1.0;
        double lateralOffset = entity.getBbWidth() * FOOT_LATERAL_OFFSET * side;
        Footprint footprint = new Footprint(
                entity.getX() + perpendicularX * lateralOffset,
                entity.getY() + 0.005,
                entity.getZ() + perpendicularZ * lateralOffset,
                size,
                size,
                (float) -Math.toRadians(bodyYaw),
                gameTime
        );
        footprints.addLast(footprint);
        while (footprints.size() > MAX_DECALS) {
            footprints.removeFirst();
        }
        state.lastTick = gameTime;
        state.lastX = entity.getX();
        state.lastZ = entity.getZ();
    }

    private static final class EntityFootState {
        private long lastTick;
        private double lastX;
        private double lastZ;
        private boolean left;
    }

    public static final class Footprint {
        private final double x;
        private final double y;
        private final double z;
        private final float width;
        private final float length;
        private final float angle;
        private final long birthTick;
        private int age;

        private Footprint(double x, double y, double z, float width, float length,
                          float angle, long birthTick) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.width = width;
            this.length = length;
            this.angle = angle;
            this.birthTick = birthTick;
        }

        public double x() { return x; }
        public double y() { return y; }
        public double z() { return z; }
        public float width() { return width; }
        public float length() { return length; }
        public float angle() { return angle; }
        public long birthTick() { return birthTick; }
        public int age() { return age; }
        public float intensity() { return Math.max(0.0F, 1.0F - age / (float) LIFETIME_TICKS); }
    }
}
