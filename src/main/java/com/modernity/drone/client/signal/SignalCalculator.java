package com.modernity.drone.client.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Cached signal propagation calculation matching the 1.1.4 client model. */
public final class SignalCalculator {
    private static final double SAMPLE_STEP = 4.0;
    private static final int MAX_SAMPLES = 50;
    private static final float UNLOADED_CHUNK_PENALTY = 2.0F;

    private Vec3 cachedDronePosition;
    private Vec3 cachedPilotPosition;
    private float cachedSignal = 1.0F;
    private float smoothedSignal = 1.0F;
    private long cachedTick = Long.MIN_VALUE;
    private float cachedObstaclePenalty;
    private long obstacleCacheTick = Long.MIN_VALUE;
    private BlockPos cachedGroundPosition;
    private int cachedGroundY;
    private long groundCacheTick = Long.MIN_VALUE;
    private int zeroSignalCounter;

    public double distance(Vec3 dronePosition, Vec3 pilotPosition) {
        if (dronePosition == null || pilotPosition == null) {
            return 0.0;
        }
        double result = dronePosition.distanceTo(pilotPosition);
        return Double.isFinite(result) ? result : 0.0;
    }

    public float calculate(Vec3 dronePosition, Vec3 pilotPosition, Level level, SignalSettings settings) {
        if (dronePosition == null || pilotPosition == null || level == null) {
            return 1.0F;
        }
        long gameTime = level.getGameTime();
        if (cachedTick != Long.MIN_VALUE
                && gameTime - cachedTick < 20L
                && cachedDronePosition != null
                && cachedPilotPosition != null
                && cachedDronePosition.distanceToSqr(dronePosition) < 9.0
                && cachedPilotPosition.distanceToSqr(pilotPosition) < 9.0
                && cachedSignal > 0.01F) {
            return cachedSignal;
        }

        cachedDronePosition = dronePosition;
        cachedPilotPosition = pilotPosition;
        cachedTick = gameTime;
        double distance = distance(dronePosition, pilotPosition);
        float distanceFactor = 1.0F - (float) Math.min(1.0, distance / settings.maximumRange());
        float obstaclePenalty;
        if (obstacleCacheTick != Long.MIN_VALUE && gameTime - obstacleCacheTick < 40L) {
            obstaclePenalty = cachedObstaclePenalty;
        } else {
            obstaclePenalty = sampleObstacles(dronePosition, pilotPosition, level);
            cachedObstaclePenalty = obstaclePenalty;
            obstacleCacheTick = gameTime;
        }
        float obstacleFactor = 1.0F
                / (1.0F + obstaclePenalty * 0.03F * settings.obstaclePenaltyMultiplier());
        float minimumHeight = (float) Math.min(dronePosition.y, pilotPosition.y);
        float heightBonus = Math.max(0.0F,
                (minimumHeight - settings.heightBaseline()) * settings.heightBonusPerBlock() * 0.01F);
        float lineOfSightFactor = obstaclePenalty < 1.0F ? 1.1F : 1.0F;
        float groundFactor = 1.0F - groundPenalty(dronePosition, level, distance, gameTime, settings) / 100.0F;
        float raw = Mth.clamp(distanceFactor * obstacleFactor * (1.0F + heightBonus)
                * lineOfSightFactor * groundFactor, 0.0F, 1.0F);
        smoothedSignal += (raw - smoothedSignal) * settings.smoothing();
        zeroSignalCounter = raw <= 0.01F ? zeroSignalCounter + 1 : 0;
        cachedSignal = zeroSignalCounter >= settings.zeroSignalFrames()
                ? 0.0F
                : Math.max(0.01F, smoothedSignal);
        return cachedSignal;
    }

    public float freezeSignal(double distance, SignalSettings settings) {
        if (distance < settings.freezeStartDistance()) {
            return 1.0F;
        }
        if (distance >= settings.maximumRange()) {
            return 0.0F;
        }
        double range = Math.max(1.0, settings.maximumRange() - settings.freezeStartDistance());
        return Mth.clamp((float) (1.0 - (distance - settings.freezeStartDistance()) / range), 0.0F, 1.0F);
    }

    public void reset() {
        cachedDronePosition = null;
        cachedPilotPosition = null;
        cachedSignal = 1.0F;
        smoothedSignal = 1.0F;
        cachedTick = Long.MIN_VALUE;
        cachedObstaclePenalty = 0.0F;
        obstacleCacheTick = Long.MIN_VALUE;
        cachedGroundPosition = null;
        groundCacheTick = Long.MIN_VALUE;
        cachedGroundY = 0;
        zeroSignalCounter = 0;
    }

    private float groundPenalty(Vec3 dronePosition, Level level, double distance,
                                long gameTime, SignalSettings settings) {
        if (distance < settings.groundProximityDistanceStart()) {
            return 0.0F;
        }
        BlockPos droneBlock = BlockPos.containing(dronePosition);
        int groundY;
        if (cachedGroundPosition != null
                && groundCacheTick != Long.MIN_VALUE
                && gameTime - groundCacheTick < 60L
                && Math.abs(cachedGroundPosition.getX() - droneBlock.getX()) < 8
                && Math.abs(cachedGroundPosition.getZ() - droneBlock.getZ()) < 8) {
            groundY = cachedGroundY;
        } else {
            groundY = findGround(level, droneBlock);
            cachedGroundY = groundY;
            cachedGroundPosition = droneBlock;
            groundCacheTick = gameTime;
        }
        float height = (float) dronePosition.y - groundY;
        if (height >= settings.groundProximityHeightThreshold()) {
            return 0.0F;
        }
        float distanceFactor = (float) Math.min(
                1.0, (distance - settings.groundProximityDistanceStart()) / 150.0
        );
        float heightFactor = 1.0F - height / settings.groundProximityHeightThreshold();
        return settings.groundProximityPenaltyMaximum() * distanceFactor * heightFactor * heightFactor;
    }

    private static int findGround(Level level, BlockPos position) {
        int minimumY = Math.max(level.getMinY(), position.getY() - 64);
        for (int y = position.getY(); y > minimumY; y -= 2) {
            BlockPos sample = new BlockPos(position.getX(), y, position.getZ());
            if (!level.hasChunkAt(sample)) {
                return position.getY() - 5;
            }
            if (level.getBlockState(sample).blocksMotion()) {
                return y;
            }
        }
        return minimumY;
    }

    private static float sampleObstacles(Vec3 from, Vec3 to, Level level) {
        double distance = from.distanceTo(to);
        if (distance < SAMPLE_STEP) {
            return 0.0F;
        }
        Vec3 direction = to.subtract(from).normalize();
        int samples = (int) (distance / SAMPLE_STEP);
        int stride = samples > MAX_SAMPLES ? Math.max(1, samples / MAX_SAMPLES) : 1;
        float penalty = 0.0F;
        for (int index = 1; index < samples; index += stride) {
            Vec3 point = from.add(direction.scale(index * SAMPLE_STEP));
            BlockPos position = BlockPos.containing(point);
            if (!level.hasChunkAt(position)) {
                penalty += UNLOADED_CHUNK_PENALTY;
                continue;
            }
            BlockState state = level.getBlockState(position);
            penalty += BlockAttenuation.forState(state);
        }
        return penalty;
    }
}
