package com.modernity.drone.entity;

import com.modernity.drone.DroneMod;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A planted hostile radio operator that owns one autonomous Mosquito drone.
 * Target selection remains server-side: the aircraft loiters until this mob
 * maintains a continuous visual track, then receives velocity-led waypoints.
 */
public final class DroneOperatorEntity extends Monster {
    public static final int LOCK_TICKS_REQUIRED = 30;
    public static final int TARGET_MEMORY_TICKS = 40;
    public static final int REDEPLOY_COOLDOWN_TICKS = 400;
    public static final int MISSING_DRONE_GRACE_TICKS = 200;
    public static final double ACQUISITION_RANGE = 96.0;
    public static final double RADIO_RANGE = 160.0;
    public static final double LOITER_RADIUS = 8.0;
    public static final double LOITER_ALTITUDE = 7.0;

    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(DroneOperatorEntity.class, EntityDataSerializers.INT);

    private boolean stationInitialized;
    private double stationX;
    private double stationY;
    private double stationZ;
    private UUID controlledDroneUuid;
    private boolean droneDeployed;
    private int missingDroneTicks;
    private int redeployCooldown;
    private UUID candidateUuid;
    private UUID targetUuid;
    private int lockTicks;
    private int targetLostTicks;
    private Vec3 lastSeenTarget;

    public DroneOperatorEntity(EntityType<? extends DroneOperatorEntity> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.FOLLOW_RANGE, ACQUISITION_RANGE)
                .add(Attributes.MOVEMENT_SPEED, 0.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
    }

    public static boolean checkSpawnRules(
            EntityType<? extends DroneOperatorEntity> type,
            ServerLevelAccessor level,
            EntitySpawnReason reason,
            BlockPos pos,
            RandomSource random
    ) {
        return Monster.checkSurfaceMonstersSpawnRules(type, level, reason, pos, random)
                && level.isEmptyBlock(pos.above(2))
                && level.isEmptyBlock(pos.above(3))
                && level.isEmptyBlock(pos.above(4));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, OperatorMode.SEARCHING.ordinal());
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level();
        initializeStationIfNeeded();
        holdStation();

        if (redeployCooldown > 0) {
            redeployCooldown--;
        }

        DroneEntity drone = resolveControlledDrone(serverLevel);
        if (drone == null && droneDeployed) {
            missingDroneTicks++;
            if (missingDroneTicks >= MISSING_DRONE_GRACE_TICKS) {
                controlledDroneUuid = null;
                droneDeployed = false;
                missingDroneTicks = 0;
                redeployCooldown = Math.max(redeployCooldown, 20);
            }
        } else if (drone != null) {
            missingDroneTicks = 0;
        }
        if (drone == null && !droneDeployed && redeployCooldown == 0) {
            drone = deployDrone(serverLevel);
        }

        if (drone == null) {
            clearTargeting();
            return;
        }

        updateTargeting(serverLevel, drone);
        holdStation();
    }

    private void initializeStationIfNeeded() {
        if (!stationInitialized) {
            stationInitialized = true;
            stationX = getX();
            stationY = getY();
            stationZ = getZ();
        }
    }

    private void holdStation() {
        setPos(stationX, stationY, stationZ);
        setDeltaMovement(Vec3.ZERO);
        fallDistance = 0.0;
    }

    private DroneEntity deployDrone(ServerLevel level) {
        DroneEntity drone = DroneMod.DRONE_ENTITY.get().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (drone == null) {
            redeployCooldown = 20;
            return null;
        }

        drone.snapTo(stationX, stationY + 3.0, stationZ, getYRot(), 0.0F);
        drone.configureOperatorDrone(this);
        if (!level.addFreshEntity(drone)) {
            redeployCooldown = 20;
            return null;
        }

        controlledDroneUuid = drone.getUUID();
        droneDeployed = true;
        missingDroneTicks = 0;
        return drone;
    }

    private DroneEntity resolveControlledDrone(ServerLevel level) {
        if (controlledDroneUuid == null) {
            return null;
        }
        Entity entity = level.getEntityInAnyDimension(controlledDroneUuid);
        if (entity instanceof DroneEntity drone && drone.isAlive() && drone.isOperatedBy(this)) {
            return drone;
        }
        return null;
    }

    private void updateTargeting(ServerLevel level, DroneEntity drone) {
        ServerPlayer locked = findPlayer(level, targetUuid);
        if (locked != null && isValidLockedTarget(drone, locked)) {
            boolean visible = hasDroneLineOfSight(level, drone, locked);
            if (visible) {
                targetLostTicks = 0;
                lastSeenTarget = trackingPoint(locked, drone);
            } else {
                targetLostTicks++;
            }

            if (visible || targetLostTicks <= TARGET_MEMORY_TICKS) {
                setTarget(locked);
                setMode(OperatorMode.ATTACKING);
                setAggressive(true);
                getLookControl().setLookAt(locked, 30.0F, 30.0F);
                Vec3 destination = lastSeenTarget == null ? trackingPoint(locked, drone) : lastSeenTarget;
                drone.acceptOperatorDirective(this, destination, locked.getUUID(), true);
                return;
            }
        }

        if (targetUuid != null) {
            targetUuid = null;
            targetLostTicks = 0;
            lastSeenTarget = null;
            setTarget(null);
            drone.clearAutonomousTarget(this);
        }

        ServerPlayer candidate = findNearestVisiblePlayer(level, drone);
        if (candidate == null) {
            candidateUuid = null;
            lockTicks = 0;
            setTarget(null);
            setMode(OperatorMode.SEARCHING);
            setAggressive(false);
            getLookControl().setLookAt(drone, 12.0F, 12.0F);
            drone.acceptOperatorDirective(this, loiterPoint(), null, false);
            return;
        }

        if (candidate.getUUID().equals(candidateUuid)) {
            lockTicks++;
        } else {
            candidateUuid = candidate.getUUID();
            lockTicks = 1;
        }

        setMode(OperatorMode.LOCKING);
        setAggressive(false);
        getLookControl().setLookAt(candidate, 25.0F, 25.0F);
        drone.acceptOperatorDirective(this, loiterPoint(), null, false);

        if (lockTicks >= LOCK_TICKS_REQUIRED) {
            targetUuid = candidate.getUUID();
            candidateUuid = null;
            lockTicks = 0;
            targetLostTicks = 0;
            lastSeenTarget = trackingPoint(candidate, drone);
            setTarget(candidate);
            setMode(OperatorMode.ATTACKING);
            setAggressive(true);
            drone.acceptOperatorDirective(this, lastSeenTarget, targetUuid, true);
        }
    }

    private ServerPlayer findNearestVisiblePlayer(ServerLevel level, DroneEntity drone) {
        ServerPlayer nearest = null;
        double nearestDistanceSquared = ACQUISITION_RANGE * ACQUISITION_RANGE;
        for (ServerPlayer player : level.players()) {
            if (!isValidCandidate(drone, player) || !hasDroneLineOfSight(level, drone, player)) {
                continue;
            }
            double distanceSquared = drone.distanceToSqr(player);
            if (distanceSquared < nearestDistanceSquared) {
                nearest = player;
                nearestDistanceSquared = distanceSquared;
            }
        }
        return nearest;
    }

    private boolean isValidCandidate(DroneEntity drone, ServerPlayer player) {
        return player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && canAttack(player)
                && distanceToSqr(player) <= RADIO_RANGE * RADIO_RANGE
                && drone.distanceToSqr(player) <= ACQUISITION_RANGE * ACQUISITION_RANGE;
    }

    private boolean isValidLockedTarget(DroneEntity drone, ServerPlayer player) {
        return isValidCandidate(drone, player);
    }

    private static boolean hasDroneLineOfSight(ServerLevel level, DroneEntity drone, ServerPlayer player) {
        HitResult obstruction = level.clip(new ClipContext(
                drone.getEyePosition(),
                player.getEyePosition(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                drone
        ));
        return obstruction.getType() == HitResult.Type.MISS;
    }

    private static Vec3 trackingPoint(ServerPlayer player, DroneEntity drone) {
        Vec3 aimPoint = player.position().add(0.0, player.getBbHeight() * 0.62, 0.0);
        double distance = drone.position().distanceTo(aimPoint);
        double droneSpeed = Math.max(8.0, drone.flightSpeedMetersPerSecond());
        double leadSeconds = Mth.clamp(distance / droneSpeed, 0.0, 1.5);
        return aimPoint.add(player.getDeltaMovement().scale(20.0 * leadSeconds));
    }

    private Vec3 loiterPoint() {
        double phaseOffset = (getUUID().getLeastSignificantBits() & 1023L) / 1024.0 * Math.PI * 2.0;
        double phase = tickCount * 0.035 + phaseOffset;
        return new Vec3(
                stationX + Math.cos(phase) * LOITER_RADIUS,
                stationY + LOITER_ALTITUDE + Math.sin(phase * 0.5) * 0.8,
                stationZ + Math.sin(phase) * LOITER_RADIUS
        );
    }

    private ServerPlayer findPlayer(ServerLevel level, UUID uuid) {
        if (uuid == null) {
            return null;
        }
        for (ServerPlayer player : level.players()) {
            if (uuid.equals(player.getUUID())) {
                return player;
            }
        }
        return null;
    }

    private void clearTargeting() {
        candidateUuid = null;
        targetUuid = null;
        lockTicks = 0;
        targetLostTicks = 0;
        lastSeenTarget = null;
        setTarget(null);
        setMode(OperatorMode.SEARCHING);
        setAggressive(false);
    }

    public void onControlledDroneDestroyed(DroneEntity drone) {
        if (!level().isClientSide()
                && controlledDroneUuid != null
                && controlledDroneUuid.equals(drone.getUUID())) {
            controlledDroneUuid = null;
            droneDeployed = false;
            missingDroneTicks = 0;
            redeployCooldown = REDEPLOY_COOLDOWN_TICKS;
            clearTargeting();
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof DroneEntity drone && drone.isOperatedBy(this)) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        if (!level().isClientSide() && reason.shouldDestroy() && level() instanceof ServerLevel serverLevel) {
            DroneEntity drone = resolveControlledDrone(serverLevel);
            if (drone != null) {
                if (isDeadOrDying()) {
                    drone.onOperatorRemoved(this);
                } else {
                    drone.discardWithOperator(this);
                }
            }
        }
        super.onRemoval(reason);
    }

    @Override
    public void die(DamageSource source) {
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            DroneEntity drone = resolveControlledDrone(serverLevel);
            if (drone != null) {
                drone.onOperatorRemoved(this);
            }
        }
        clearTargeting();
        super.die(source);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    public OperatorMode operatorMode() {
        int ordinal = Mth.clamp(entityData.get(DATA_MODE), 0, OperatorMode.values().length - 1);
        return OperatorMode.values()[ordinal];
    }

    private void setMode(OperatorMode mode) {
        entityData.set(DATA_MODE, mode.ordinal());
    }

    public UUID controlledDroneUuidForTesting() {
        return controlledDroneUuid;
    }

    public boolean controlsDrone(DroneEntity drone) {
        return drone != null
                && controlledDroneUuid != null
                && controlledDroneUuid.equals(drone.getUUID());
    }

    public UUID targetUuidForTesting() {
        return targetUuid;
    }

    public UUID candidateUuidForTesting() {
        return candidateUuid;
    }

    public int lockTicksForTesting() {
        return lockTicks;
    }

    public Vec3 stationPositionForTesting() {
        return new Vec3(stationX, stationY, stationZ);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        stationInitialized = input.getBooleanOr("StationInitialized", false);
        stationX = input.getDoubleOr("StationX", getX());
        stationY = input.getDoubleOr("StationY", getY());
        stationZ = input.getDoubleOr("StationZ", getZ());
        controlledDroneUuid = parseUuid(input.getStringOr("ControlledDrone", ""));
        droneDeployed = input.getBooleanOr("DroneDeployed", controlledDroneUuid != null);
        missingDroneTicks = Math.max(0, input.getIntOr("MissingDroneTicks", 0));
        redeployCooldown = Math.max(0, input.getIntOr("RedeployCooldown", 0));

        // Locks are intentionally reacquired after reload; stale entity targets
        // must never resume an attack without a fresh visual track.
        clearTargeting();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("StationInitialized", stationInitialized);
        output.putDouble("StationX", stationX);
        output.putDouble("StationY", stationY);
        output.putDouble("StationZ", stationZ);
        output.putBoolean("DroneDeployed", droneDeployed);
        output.putInt("MissingDroneTicks", missingDroneTicks);
        output.putInt("RedeployCooldown", redeployCooldown);
        if (controlledDroneUuid != null) {
            output.putString("ControlledDrone", controlledDroneUuid.toString());
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return value.isBlank() ? null : UUID.fromString(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public enum OperatorMode {
        SEARCHING,
        LOCKING,
        ATTACKING
    }
}
