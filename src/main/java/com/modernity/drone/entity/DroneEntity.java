package com.modernity.drone.entity;

import com.modernity.drone.Config;
import com.modernity.drone.DroneMod;
import com.modernity.drone.flight.BatteryState;
import com.modernity.drone.flight.BatteryData;
import com.modernity.drone.flight.DroneKind;
import com.modernity.drone.flight.DroneFlightConfig;
import com.modernity.drone.flight.DronePhysics;
import com.modernity.drone.flight.FlightAttitude;
import com.modernity.drone.flight.FlightControl;
import com.modernity.drone.flight.FlightRates;
import com.modernity.drone.flight.FlightState;
import com.modernity.drone.flight.FlightStepResult;
import com.modernity.drone.flight.FlightVector;
import com.modernity.drone.network.DroneControlPayload;
import com.modernity.drone.item.BatteryItem;
import com.modernity.drone.item.DroneItem;
import com.modernity.drone.item.FpvGogglesItem;
import com.modernity.drone.item.RemoteControlItem;
import com.modernity.drone.item.StackData;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DroneEntity extends Entity implements ItemSupplier {
    private static final double PAYLOAD_MASS_KG = 0.25;
    private static final double RPG_MASS_KG = 1.0;
    private static final double VOLTAGE_SMOOTHING_PER_TICK = 1.0 - Math.pow(1.0 - 0.008, 3.0);
    private static final double ENTITY_CONTACT_EPSILON = 1.0E-4;

    private static final EntityDataAccessor<Integer> DATA_KIND =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ARMED =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Float> DATA_ROLL =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_BATTERY =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_SIGNAL =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_PAYLOADS =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PILOT_ID =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_AUTONOMOUS =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HAS_BATTERY =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HAS_RPG =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_THERMAL =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_VIDEO_CHANNEL =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_THROTTLE =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> DATA_ADDED_WEIGHT =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_FALLING =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> DATA_FALL_TYPE =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<String> DATA_DRONE_NAME =
            SynchedEntityData.defineId(DroneEntity.class, EntityDataSerializers.STRING);

    private DroneKind kind = DroneKind.MOSQUITO;
    private FlightState flightState;
    private FlightControl pilotControl = FlightControl.DISARMED;
    private UUID ownerUuid;
    private UUID autonomousOperatorUuid;
    private UUID autonomousTargetUuid;
    private Vec3 autonomousDestination;
    private boolean autonomousAttackRun;
    private int autonomousLinkLostTicks;
    private boolean batteryInstalled;
    private boolean armed;
    private boolean dropLatch;
    private boolean hoverMode;
    private boolean returnHome;
    private boolean rpgWarheadLoaded;
    private boolean thermal;
    private int videoChannel = 1;
    private float addedWeightGrams;
    private BatteryData batteryData = BatteryData.defaults();
    private int payloadCount;
    private int armedTicks;
    private int signalLostTicks;
    private int lastControlTick = -1_000_000;
    private int lastDropTick = -1_000_000;
    private double homeX;
    private double homeY;
    private double homeZ;
    private float structuralHealth = 10.0F;
    private float clientPreviousRoll;
    private float clientRoll;
    private float previousPropAngle;
    private float propAngle;
    private double currentAmps;
    private double loadedVoltage;
    private float fallYawRate;
    private float fallPitchRate;
    private float fallRollRate;
    private float fallDragMultiplier = 1.0F;
    private int fallingGraceTicks;
    private boolean flipPhaseComplete;
    private float flipAccumulated;
    private int throttleCutTicks;
    private int unpoweredGogglesTicks;
    private DroneFlightConfig flightConfig = DroneFlightConfig.DEFAULT;
    private DronePhysics physics = new DronePhysics(flightConfig);
    private ChunkPos forcedChunk;

    public DroneEntity(EntityType<? extends DroneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_KIND, DroneKind.MOSQUITO.ordinal());
        builder.define(DATA_ARMED, false);
        builder.define(DATA_ROLL, 0.0F);
        builder.define(DATA_BATTERY, 0.0F);
        builder.define(DATA_SIGNAL, 0.0F);
        builder.define(DATA_PAYLOADS, 0);
        builder.define(DATA_PILOT_ID, -1);
        builder.define(DATA_AUTONOMOUS, false);
        builder.define(DATA_HAS_BATTERY, false);
        builder.define(DATA_HAS_RPG, false);
        builder.define(DATA_THERMAL, false);
        builder.define(DATA_VIDEO_CHANNEL, (byte) 1);
        builder.define(DATA_THROTTLE, (byte) 0);
        builder.define(DATA_ADDED_WEIGHT, 0.0F);
        builder.define(DATA_FALLING, false);
        builder.define(DATA_FALL_TYPE, (byte) FallType.DEAD_DROP.ordinal());
        builder.define(DATA_DRONE_NAME, DroneFlightConfig.DEFAULT.droneName());
    }

    public void configurePlacedDrone(DroneKind newKind, Player owner) {
        kind = newKind;
        entityData.set(DATA_KIND, newKind.ordinal());
        ownerUuid = owner == null ? null : owner.getUUID();
        autonomousOperatorUuid = null;
        autonomousTargetUuid = null;
        autonomousDestination = null;
        autonomousAttackRun = false;
        autonomousLinkLostTicks = 0;
        entityData.set(DATA_AUTONOMOUS, false);
        entityData.set(DATA_PILOT_ID, -1);
        homeX = getX();
        homeY = getY();
        homeZ = getZ();
        structuralHealth = newKind == DroneKind.MOSQUITO ? 10.0F : 80.0F;
        batteryInstalled = false;
        armed = false;
        payloadCount = 0;
        rpgWarheadLoaded = false;
        thermal = false;
        videoChannel = 1;
        addedWeightGrams = 0.0F;
        batteryData = BatteryData.defaults();
        flightConfig = DroneFlightConfig.DEFAULT;
        physics = new DronePhysics(flightConfig);
        flightState = createState(emptyBattery());
        syncTelemetry();
    }

    public void configureOperatorDrone(DroneOperatorEntity operator) {
        kind = DroneKind.MOSQUITO;
        entityData.set(DATA_KIND, kind.ordinal());
        ownerUuid = operator.getUUID();
        autonomousOperatorUuid = operator.getUUID();
        autonomousTargetUuid = null;
        autonomousDestination = operator.position().add(0.0, DroneOperatorEntity.LOITER_ALTITUDE, 0.0);
        autonomousAttackRun = false;
        autonomousLinkLostTicks = 0;
        homeX = operator.getX();
        homeY = operator.getY();
        homeZ = operator.getZ();
        structuralHealth = 10.0F;
        batteryInstalled = true;
        armed = true;
        payloadCount = 0;
        rpgWarheadLoaded = true;
        hoverMode = false;
        returnHome = false;
        entityData.set(DATA_PILOT_ID, -1);
        entityData.set(DATA_AUTONOMOUS, true);
        flightState = createState(kind.defaultBattery());
        pilotControl = new FlightControl(0.0, 0.0, 0.0, 0.36, true);
        lastControlTick = tickCount;
        syncTelemetry();
    }

    private FlightState createState(BatteryState battery) {
        double payloadMass = kind == DroneKind.MOSQUITO
                ? (rpgWarheadLoaded ? RPG_MASS_KG : 0.0) + addedWeightGrams / 1000.0
                : payloadCount * PAYLOAD_MASS_KG;
        return new FlightState(
                kind,
                toFlightVector(position()),
                toFlightVector(getDeltaMovement()).multiply(20.0),
                FlightAttitude.fromEulerRadians(
                        Math.toRadians(getYRot()),
                        Math.toRadians(-getXRot()),
                        Math.toRadians(entityData.get(DATA_ROLL))
                ),
                FlightRates.ZERO,
                battery,
                payloadMass,
                0L
        );
    }

    @Override
    public void tick() {
        super.tick();
        previousPropAngle = propAngle;
        propAngle = (propAngle + propSpinDelta()) % ((float) Math.PI * 2.0F);
        if (level().isClientSide()) {
            clientPreviousRoll = clientRoll;
            clientRoll = entityData.get(DATA_ROLL);
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level();
        validatePilot(serverLevel);
        tickUnpoweredGoggles(serverLevel);
        updateChunkLoading(serverLevel);
        if (flightState == null || flightState.kind() != kind) {
            flightState = createState(batteryInstalled ? kind.defaultBattery() : emptyBattery());
        }
        if (isFalling()) {
            tickFalling(serverLevel);
            return;
        }
        if (flightState.positionMeters().subtract(toFlightVector(position())).length() > 0.75) {
            flightState = new FlightState(
                    kind,
                    toFlightVector(position()),
                    toFlightVector(getDeltaMovement()).multiply(20.0),
                    flightState.attitude(),
                    flightState.angularRates(),
                    flightState.battery(),
                    flightState.payloadMassKg(),
                    flightState.simulationTick()
            );
        }

        if (!armed && onGround() && tickCount <= 20) {
            homeX = getX();
            homeY = getY();
            homeZ = getZ();
        }

        if (autonomousOperatorUuid != null) {
            updateAutonomousControl(serverLevel);
            if (isRemoved()) {
                return;
            }
        }
        updateSignal(serverLevel);
        applyFailsafe(serverLevel);
        boolean throttleCut = throttleCutTicks > 0;
        if (throttleCutTicks > 0) throttleCutTicks--;
        FlightControl effectiveControl = effectiveControl();
        if (!batteryInstalled || flightState.battery().isDepleted()) {
            armed = false;
            effectiveControl = FlightControl.DISARMED;
            if (batteryInstalled && flightState.battery().isDepleted()
                    && (autonomousOperatorUuid != null || entityData.get(DATA_PILOT_ID) >= 0)) {
                // The old client forces NO SIGNAL when the pack is exhausted;
                // its server-side result is an uncontrolled dead drop.  This
                // also lets an operator observe the loss and deploy a replacement.
                enterFallingState(FallType.DEAD_DROP);
                return;
            }
        } else if (throttleCut && armed) {
            // V1.1.4 cuts thrust and ignores sticks for 0.8 seconds after a
            // sufficiently hard impact without toggling the arm switch.
            effectiveControl = new FlightControl(0.0, 0.0, 0.0, 0.0, true);
        }

        if (armed) {
            armedTicks++;
        } else {
            armedTicks = 0;
        }

        boolean groundedBeforeStep = onGround();
        FlightState stateBeforeStep = flightState;
        FlightStepResult result = autonomousOperatorUuid == null
                ? physics.step(flightState, effectiveControl)
                : physics.stepAutonomous(flightState, effectiveControl);
        currentAmps = result.motorCurrentAmps();
        double targetLoadedVoltage = result.loadedBatteryVoltage();
        if (loadedVoltage <= 0.0 && flightState.battery().remainingWattHours() > 0.0) {
            loadedVoltage = flightState.battery().openCircuitVoltage();
        }
        loadedVoltage += (targetLoadedVoltage - loadedVoltage) * VOLTAGE_SMOOTHING_PER_TICK;
        FlightState simulated = result.nextState();
        Vec3 start = position();
        Vec3 desiredMove = toVec3(simulated.positionMeters()).subtract(start);
        EntityHitResult entityHit = findEntityImpact(start, desiredMove);
        boolean entityImpact = entityHit != null;
        if (entityHit != null) {
            desiredMove = entityHit.getLocation().subtract(start);
        }
        move(MoverType.SELF, desiredMove);
        Vec3 actualMove = position().subtract(start);

        FlightVector correctedVelocity = resolveCollisionVelocity(
                simulated.velocityMetersPerSecond(),
                desiredMove,
                actualMove
        );
        FlightAttitude correctedAttitude = simulated.attitude();
        if (groundedBeforeStep && !throttleCut) {
            correctedAttitude = stateBeforeStep.attitude().levelTowardWorldUp(0.60);
        }
        flightState = new FlightState(
                kind,
                toFlightVector(position()),
                correctedVelocity,
                correctedAttitude,
                simulated.angularRates(),
                simulated.battery(),
                simulated.payloadMassKg(),
                simulated.simulationTick()
        );
        setDeltaMovement(toVec3(correctedVelocity).scale(1.0 / 20.0));
        setYRot((float) Math.toDegrees(flightState.attitude().yawRadians()));
        setXRot((float) -Math.toDegrees(flightState.attitude().pitchRadians()));

        boolean hitX = Math.abs(desiredMove.x - actualMove.x) > 1.0E-5;
        boolean hitY = Math.abs(desiredMove.y - actualMove.y) > 1.0E-5;
        boolean hitZ = Math.abs(desiredMove.z - actualMove.z) > 1.0E-5;
        boolean blockImpact = hitX || hitY || hitZ;
        double impactSpeed = entityImpact || blockImpact
                ? simulated.velocityMetersPerSecond().length()
                : 0.0;
        double throttleCutThreshold = hitX || hitZ || entityImpact ? 8.0 : 10.0;
        if (armed && impactSpeed > throttleCutThreshold) {
            throttleCutTicks = 16;
        }
        boolean operatorTargetContact = entityHit != null
                && autonomousOperatorUuid != null
                && autonomousAttackRun
                && autonomousTargetUuid != null
                && autonomousTargetUuid.equals(entityHit.getEntity().getUUID());
        if (kind == DroneKind.MOSQUITO
                && armed
                && rpgWarheadLoaded
                && armedTicks >= 10
                && (autonomousOperatorUuid == null || autonomousAttackRun)
                && (impactSpeed > 2.0 || operatorTargetContact)) {
            detonateRpgOnControlledImpact(serverLevel);
            return;
        }
        if (impactSpeed > 2.0) {
            if (entityHit != null) {
                Entity sourceOwner = ownerUuid == null ? null : serverLevel.getEntityInAnyDimension(ownerUuid);
                if (impactSpeed >= 5.0 && Config.DRONE_COLLISION_DAMAGE.getAsBoolean()) {
                    float kineticDamage = (float) Math.min((impactSpeed - 5.0) * 1.58 * 2.0, 40.0);
                    entityHit.getEntity().hurtServer(
                            serverLevel,
                            serverLevel.damageSources().thrown(this, sourceOwner),
                            kineticDamage
                    );
                }
                correctedVelocity = resolveEntityImpactVelocity(
                        simulated.velocityMetersPerSecond(),
                        entityHit.getEntity()
                );
                flightState = flightState.withKinematics(
                        flightState.positionMeters(),
                        correctedVelocity
                );
                setDeltaMovement(toVec3(correctedVelocity).scale(1.0 / 20.0));
            }
        }
        syncTelemetry();
    }

    /**
     * Sweeps the drone's full physical volume against damageable entities.
     *
     * <p>{@code ProjectileUtil} sweeps a point through an inflated target box,
     * which is appropriate for arrows but not for this 0.85-block-wide entity.
     * In particular, an autonomous drone could physically overlap its target,
     * settle at the requested tracking point, and then produce a zero-length
     * ray that never reported the impact. This is the Minkowski-sum equivalent
     * of sweeping the drone AABB and also recognizes overlap at the beginning
     * of the tick.</p>
     */
    private EntityHitResult findEntityImpact(Vec3 start, Vec3 desiredMove) {
        Vec3 end = start.add(desiredMove);
        AABB droneBox = getBoundingBox();
        AABB sweptBox = droneBox.expandTowards(desiredMove).inflate(ENTITY_CONTACT_EPSILON);
        Entity nearestEntity = null;
        Vec3 nearestLocation = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (Entity entity : level().getEntities(this, sweptBox, candidate -> candidate.isAlive()
                && candidate.isPickable()
                && (candidate instanceof LivingEntity || candidate instanceof DroneEntity)
                && candidate.getId() != entityData.get(DATA_PILOT_ID)
                && !isAutonomousOperator(candidate))) {
            AABB targetBox = entity.getBoundingBox();
            AABB contactBox = new AABB(
                    targetBox.minX - (droneBox.maxX - start.x) - ENTITY_CONTACT_EPSILON,
                    targetBox.minY - (droneBox.maxY - start.y) - ENTITY_CONTACT_EPSILON,
                    targetBox.minZ - (droneBox.maxZ - start.z) - ENTITY_CONTACT_EPSILON,
                    targetBox.maxX - (droneBox.minX - start.x) + ENTITY_CONTACT_EPSILON,
                    targetBox.maxY - (droneBox.minY - start.y) + ENTITY_CONTACT_EPSILON,
                    targetBox.maxZ - (droneBox.minZ - start.z) + ENTITY_CONTACT_EPSILON
            );

            Vec3 contact;
            if (contactBox.contains(start)) {
                contact = start;
            } else {
                var clipped = contactBox.clip(start, end);
                if (clipped.isEmpty()) {
                    continue;
                }
                contact = clipped.get();
            }

            double distanceSquared = start.distanceToSqr(contact);
            if (distanceSquared < nearestDistanceSquared) {
                nearestEntity = entity;
                nearestLocation = contact;
                nearestDistanceSquared = distanceSquared;
            }
        }

        return nearestEntity == null ? null : new EntityHitResult(nearestEntity, nearestLocation);
    }

    private FlightControl effectiveControl() {
        if (kind == DroneKind.PAYLOAD && returnHome) {
            return returnToHomeControl();
        }
        if (kind == DroneKind.PAYLOAD && hoverMode) {
            return new FlightControl(0.0, 0.0, pilotControl.yaw(), 0.5, armed);
        }
        return pilotControl.withArmed(armed);
    }

    private void updateAutonomousControl(ServerLevel level) {
        Entity entity = level.getEntityInAnyDimension(autonomousOperatorUuid);
        if (entity instanceof DroneOperatorEntity operator && !operator.controlsDrone(this)) {
            discard();
            return;
        }
        if (!(entity instanceof DroneOperatorEntity operator)
                || !operator.isAlive()
                || operator.level() != level
                || operator.distanceTo(this) > DroneOperatorEntity.RADIO_RANGE) {
            onOperatorSignalLost();
            return;
        }

        autonomousLinkLostTicks = 0;
        if (autonomousDestination == null) {
            autonomousDestination = operator.position().add(0.0, DroneOperatorEntity.LOITER_ALTITUDE, 0.0);
        }
        pilotControl = autonomousFlightControl(level, autonomousDestination, autonomousAttackRun);
        armed = batteryInstalled && !flightState.battery().isDepleted();
        lastControlTick = tickCount;
        entityData.set(DATA_PILOT_ID, -1);
    }

    private FlightControl autonomousFlightControl(ServerLevel level, Vec3 requestedDestination, boolean attackRun) {
        Vec3 destination = obstacleAwareDestination(level, requestedDestination, attackRun);
        Vec3 offset = destination.subtract(position());
        double horizontalDistance = Math.hypot(offset.x, offset.z);
        double maximumSpeed = attackRun ? 28.0 : 10.0;
        double desiredSpeed = horizontalDistance < 0.35
                ? 0.0
                : Mth.clamp(horizontalDistance * (attackRun ? 1.6 : 0.9), 2.0, maximumSpeed);
        Vec3 horizontalDirection = horizontalDistance < 1.0E-6
                ? Vec3.ZERO
                : new Vec3(offset.x / horizontalDistance, 0.0, offset.z / horizontalDistance);
        Vec3 desiredHorizontalVelocity = horizontalDirection.scale(desiredSpeed);
        double currentYaw = flightState.attitude().yawRadians();
        double desiredYaw = desiredSpeed < 0.05
                ? currentYaw
                : Math.atan2(-desiredHorizontalVelocity.x, desiredHorizontalVelocity.z);
        FlightVector headingRight = new FlightVector(Math.cos(currentYaw), 0.0, Math.sin(currentYaw));
        FlightVector headingForward = new FlightVector(-Math.sin(currentYaw), 0.0, Math.cos(currentYaw));
        FlightVector desiredWorldVelocity = toFlightVector(desiredHorizontalVelocity);
        double rollInput = Mth.clamp(desiredWorldVelocity.dot(headingRight) / 28.0, -1.0, 1.0);
        double pitchInput = Mth.clamp(desiredWorldVelocity.dot(headingForward) / 28.0, -1.0, 1.0);
        double yawError = wrapRadians(desiredYaw - currentYaw);
        double yawInput = Mth.clamp(yawError / Math.toRadians(80.0), -1.0, 1.0);
        double desiredVerticalSpeed = Mth.clamp(offset.y * 1.4, -8.0, 8.0);
        double throttle = Mth.clamp(0.5 + desiredVerticalSpeed / 16.0, 0.02, 0.98);
        return new FlightControl(rollInput, pitchInput, yawInput, throttle, true);
    }

    private Vec3 obstacleAwareDestination(ServerLevel level, Vec3 destination, boolean attackRun) {
        Vec3 start = getEyePosition();
        Vec3 offset = destination.subtract(start);
        double distance = offset.length();
        if (distance < 1.0 || attackRun && distance < 5.0) {
            return destination;
        }
        Vec3 probeEnd = start.add(offset.scale(Math.min(distance, 7.0) / distance));
        HitResult obstruction = level.clip(new ClipContext(
                start,
                probeEnd,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));
        if (obstruction.getType() == HitResult.Type.BLOCK) {
            return new Vec3(destination.x, Math.max(destination.y, obstruction.getLocation().y + 4.0), destination.z);
        }
        return destination;
    }

    private static double wrapRadians(double value) {
        return Math.toRadians(Mth.wrapDegrees((float) Math.toDegrees(value)));
    }

    public void acceptOperatorDirective(
            DroneOperatorEntity operator,
            Vec3 destination,
            UUID targetUuid,
            boolean attackRun
    ) {
        if (level().isClientSide()
                || autonomousOperatorUuid == null
                || !autonomousOperatorUuid.equals(operator.getUUID())
                || !operator.controlsDrone(this)
                || !operator.isAlive()
                || operator.level() != level()
                || operator.distanceTo(this) > DroneOperatorEntity.RADIO_RANGE
                || !Double.isFinite(destination.x)
                || !Double.isFinite(destination.y)
                || !Double.isFinite(destination.z)) {
            return;
        }
        autonomousDestination = destination;
        autonomousTargetUuid = targetUuid;
        autonomousAttackRun = attackRun;
        autonomousLinkLostTicks = 0;
        armed = batteryInstalled && flightState != null && !flightState.battery().isDepleted();
        returnHome = false;
        hoverMode = false;
        lastControlTick = tickCount;
        syncTelemetry();
    }

    public void clearAutonomousTarget(DroneOperatorEntity operator) {
        if (autonomousOperatorUuid != null && autonomousOperatorUuid.equals(operator.getUUID())) {
            autonomousTargetUuid = null;
            autonomousAttackRun = false;
        }
    }

    public void onOperatorRemoved(DroneOperatorEntity operator) {
        if (autonomousOperatorUuid == null || !autonomousOperatorUuid.equals(operator.getUUID())) {
            return;
        }
        autonomousOperatorUuid = null;
        autonomousTargetUuid = null;
        autonomousDestination = null;
        autonomousAttackRun = false;
        autonomousLinkLostTicks = 0;
        enterFallingState(FallType.DEAD_DROP);
        entityData.set(DATA_AUTONOMOUS, false);
        entityData.set(DATA_PILOT_ID, -1);
        syncTelemetry();
    }

    public void discardWithOperator(DroneOperatorEntity operator) {
        if (autonomousOperatorUuid == null || !autonomousOperatorUuid.equals(operator.getUUID())) {
            return;
        }
        autonomousOperatorUuid = null;
        autonomousTargetUuid = null;
        autonomousDestination = null;
        autonomousAttackRun = false;
        autonomousLinkLostTicks = 0;
        armed = false;
        pilotControl = FlightControl.DISARMED;
        entityData.set(DATA_AUTONOMOUS, false);
        discard();
    }

    private void onOperatorSignalLost() {
        autonomousTargetUuid = null;
        autonomousDestination = null;
        autonomousAttackRun = false;
        armed = false;
        pilotControl = FlightControl.DISARMED;
        autonomousLinkLostTicks++;
        if (autonomousLinkLostTicks >= DroneOperatorEntity.MISSING_DRONE_GRACE_TICKS) {
            autonomousOperatorUuid = null;
            entityData.set(DATA_AUTONOMOUS, false);
            discard();
        }
    }

    private boolean isAutonomousOperator(Entity entity) {
        return autonomousOperatorUuid != null && autonomousOperatorUuid.equals(entity.getUUID());
    }

    private FlightControl returnToHomeControl() {
        double dx = homeX - getX();
        double dz = homeZ - getZ();
        double horizontalDistance = Math.hypot(dx, dz);
        double targetY = horizontalDistance > 2.0 ? homeY + 8.0 : homeY + 0.3;
        double desiredX = horizontalDistance < 0.25 ? 0.0 : dx / horizontalDistance;
        double desiredZ = horizontalDistance < 0.25 ? 0.0 : dz / horizontalDistance;
        double yaw = flightState.attitude().yawRadians();
        FlightVector right = new FlightVector(Math.cos(yaw), 0.0, Math.sin(yaw));
        FlightVector forward = new FlightVector(-Math.sin(yaw), 0.0, Math.cos(yaw));
        FlightVector desired = new FlightVector(desiredX, 0.0, desiredZ);
        double roll = Mth.clamp(desired.dot(right), -1.0, 1.0);
        double pitch = Mth.clamp(desired.dot(forward), -1.0, 1.0);
        double altitudeError = targetY - getY();
        double throttle = Mth.clamp(0.5 + altitudeError * 0.08, 0.20, 0.80);
        double desiredYaw = Math.atan2(-desiredX, desiredZ);
        double yawError = Mth.wrapDegrees((float) Math.toDegrees(desiredYaw - yaw));
        double yawInput = Mth.clamp(yawError / 60.0, -1.0, 1.0);
        if (horizontalDistance < 1.25 && getY() <= homeY + 0.45 && onGround()) {
            armed = false;
            returnHome = false;
            return FlightControl.DISARMED;
        }
        return new FlightControl(roll, pitch, yawInput, throttle, armed);
    }

    private void applyFailsafe(ServerLevel level) {
        int age = tickCount - lastControlTick;
        boolean timedOut = age >= Config.CONTROL_TIMEOUT_TICKS.getAsInt();
        boolean signalFailed = signalLostTicks >= Config.ZERO_SIGNAL_FRAMES_FOR_DISCONNECT.getAsInt();
        if (!armed || (!timedOut && !signalFailed)) {
            return;
        }
        if (autonomousOperatorUuid != null) {
            onOperatorSignalLost();
            return;
        }
        // V1.1.4 drops into an uncontrolled fall when the video link is gone.
        enterFallingState(FallType.DEAD_DROP);
    }

    private void updateSignal(ServerLevel level) {
        Entity pilot = autonomousOperatorUuid == null
                ? level.getEntity(entityData.get(DATA_PILOT_ID))
                : level.getEntityInAnyDimension(autonomousOperatorUuid);
        if (pilot == null) {
            entityData.set(DATA_SIGNAL, 0.0F);
            if (armed) {
                signalLostTicks++;
            }
            return;
        }
        double maximumRange = Config.effectiveMaxRange();
        double distance = pilot.distanceTo(this);
        double rangeFactor = Mth.clamp(1.0 - distance / maximumRange, 0.0, 1.0);
        double obstaclePenalty = 0.0;
        Vec3 start = pilot.getEyePosition();
        Vec3 direction = getEyePosition().subtract(start).normalize();
        int samples = (int) (distance / 4.0);
        int stride = samples > 50 ? Math.max(1, samples / 50) : 1;
        for (int i = 1; i < samples; i += stride) {
            Vec3 point = start.add(direction.scale(i * 4.0));
            BlockPos pos = BlockPos.containing(point);
            if (!level.hasChunkAt(pos)) {
                obstaclePenalty += 2.0;
            } else {
                obstaclePenalty += signalAttenuation(level.getBlockState(pos));
            }
        }
        double obstacleFactor = 1.0 / (1.0 + obstaclePenalty * 0.03 * Config.OBSTACLE_PENALTY_MULTIPLIER.getAsDouble());
        double lineOfSightFactor = obstaclePenalty < 1.0 ? 1.1 : 1.0;
        double altitudeBonus = Math.max(0.0, Math.min(getY(), pilot.getY()) - Config.HEIGHT_BASELINE.getAsInt())
                * Config.HEIGHT_BONUS_PER_BLOCK.getAsDouble() / 100.0;
        int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(getX()), Mth.floor(getZ()));
        double heightAboveGround = getY() - surface;
        double groundPenalty = 0.0;
        int groundDistanceStart = Config.effectiveGroundDistanceStart();
        int groundHeightThreshold = Config.effectiveGroundHeightThreshold();
        if (distance >= groundDistanceStart && heightAboveGround < groundHeightThreshold) {
            double distanceFactor = Math.min(1.0, (distance - groundDistanceStart) / 150.0);
            double lowFactor = 1.0 - heightAboveGround / groundHeightThreshold;
            groundPenalty = Config.GROUND_PROXIMITY_PENALTY_MAX.getAsDouble()
                    * distanceFactor * lowFactor * lowFactor;
        }
        float rawSignal = rangeFactor <= 0.0 ? 0.0F
                : (float) Mth.clamp(rangeFactor * obstacleFactor * (1.0 + altitudeBonus)
                        * lineOfSightFactor * (1.0 - groundPenalty / 100.0), 0.0, 1.0);
        float previous = entityData.get(DATA_SIGNAL);
        float signal = previous + (rawSignal - previous) * Config.SIGNAL_SMOOTHING.get().floatValue();
        if (rawSignal <= 0.01F) {
            signalLostTicks++;
        } else {
            signalLostTicks = 0;
        }
        entityData.set(DATA_SIGNAL, signalLostTicks >= Config.ZERO_SIGNAL_FRAMES_FOR_DISCONNECT.getAsInt()
                ? 0.0F : Math.max(0.01F, signal));
    }

    private static float signalAttenuation(net.minecraft.world.level.block.state.BlockState state) {
        if (state.isAir()) return 0.0F;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        if (containsAny(path, "torch", "lantern", "flower", "grass", "vine", "sapling", "mushroom")) return 0.0F;
        if (path.contains("leaves")) return 1.0F;
        if (containsAny(path, "glass", "pane", "bars", "fence")) return 0.5F;
        if (containsAny(path, "water", "ice")) return 2.0F;
        if (containsAny(path, "log", "wood", "bamboo")) return 3.0F;
        if (path.contains("planks")) return 4.0F;
        if (containsAny(path, "obsidian", "ancient_debris")) return 20.0F;
        if (path.contains("reinforced_deepslate")) return 15.0F;
        if (containsAny(path, "deepslate", "netherite")) return 12.0F;
        if (containsAny(path, "iron", "gold", "diamond", "emerald")) return 10.0F;
        if (path.contains("copper")) return 7.0F;
        if (containsAny(path, "stone", "brick", "concrete", "terracotta", "ore")) return 8.0F;
        return state.blocksMotion() ? 6.0F : 1.0F;
    }

    private static boolean containsAny(String value, String... fragments) {
        for (String fragment : fragments) if (value.contains(fragment)) return true;
        return false;
    }

    private void enterFallingState(FallType type) {
        if (isFalling()) return;
        entityData.set(DATA_FALLING, true);
        entityData.set(DATA_FALL_TYPE, (byte) type.ordinal());
        armed = false;
        pilotControl = FlightControl.DISARMED;
        entityData.set(DATA_PILOT_ID, -1);
        fallYawRate = type.randomYaw(random);
        fallPitchRate = type.randomPitch(random);
        fallRollRate = type.randomRoll(random);
        fallDragMultiplier = type.dragMultiplier;
        fallingGraceTicks = 5;
        flipPhaseComplete = false;
        flipAccumulated = 0.0F;
        throttleCutTicks = 0;
        syncTelemetry();
    }

    private void validatePilot(ServerLevel level) {
        int pilotId = entityData.get(DATA_PILOT_ID);
        if (pilotId < 0) return;
        Entity pilot = level.getEntity(pilotId);
        if (!(pilot instanceof Player player) || !canPlayerControl(player)) {
            entityData.set(DATA_PILOT_ID, -1);
            armed = false;
            pilotControl = FlightControl.DISARMED;
            syncTelemetry();
        }
    }

    /** The original removes powered-down goggles after a two-second grace period. */
    private void tickUnpoweredGoggles(ServerLevel level) {
        if (batteryInstalled || ownerUuid == null) {
            unpoweredGogglesTicks = 0;
            return;
        }
        if (!(level.getPlayerInAnyDimension(ownerUuid) instanceof ServerPlayer player)) {
            unpoweredGogglesTicks = 0;
            return;
        }
        ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean watchesThisDrone = helmet.getItem() instanceof FpvGogglesItem
                && FpvGogglesItem.getLinkedDroneId(helmet).filter(getUUID()::equals).isPresent();
        if (!watchesThisDrone) {
            unpoweredGogglesTicks = 0;
            return;
        }
        if (++unpoweredGogglesTicks < 40) return;
        unpoweredGogglesTicks = 0;
        ItemStack goggles = helmet.copy();
        player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        if (!player.addItem(goggles)) player.drop(goggles, false);
        endPilot(player);
        player.sendOverlayMessage(
                Component.translatable("item.fpvdrone.fpv_goggles.no_battery").withColor(0xFFAA36));
    }

    private void tickFalling(ServerLevel level) {
        if (fallingGraceTicks > 0) fallingGraceTicks--;
        if (getFallType() == FallType.FLIP_AND_DROP && !flipPhaseComplete) {
            float pitchDelta = fallPitchRate * 0.05F;
            flipAccumulated += pitchDelta;
            if (flipAccumulated >= 180.0F) {
                flipAccumulated = 180.0F;
                flipPhaseComplete = true;
                fallYawRate = 0.0F;
                fallPitchRate = 0.0F;
                fallRollRate = 0.0F;
            }
        }
        setYRot(getYRot() + fallYawRate * 0.05F);
        setXRot(getXRot() + fallPitchRate * 0.05F);
        entityData.set(DATA_ROLL, entityData.get(DATA_ROLL) + fallRollRate * 0.05F);
        Vec3 velocity = getDeltaMovement();
        double metresPerSecond = velocity.length() * 20.0;
        // Falling uses the original airframe's 0.0539 drag factor and 1.05 kg
        // reference mass. Convert the per-second deceleration back to blocks/tick.
        double drag = Math.min(0.95, metresPerSecond * 0.0539 / 1.05 * 0.05 * fallDragMultiplier);
        Vec3 next = velocity.scale(1.0 - drag).add(0.0, -9.80665 / 400.0, 0.0);
        float fallThrustFactor = getFallType().thrustFactor;
        if (fallThrustFactor > 0.0F && batteryInstalled) {
            double massKg = flightState == null ? 1.05 : Math.max(0.05, flightState.totalMassKg());
            double maximumThrustNewtons = 2.04 * 4.0 * 9.80665
                    * (flightConfig.propDiameterInches() / 9.0)
                    * (flightConfig.propPitchInches() / 4.5)
                    * (flightConfig.motorKv() / 1300.0)
                    * (batteryData.cells() * 3.7 / 22.2)
                    * flightConfig.thrustMultiplier();
            FlightAttitude fallingAttitude = FlightAttitude.fromEulerRadians(
                    Math.toRadians(getYRot()),
                    Math.toRadians(-getXRot()),
                    Math.toRadians(entityData.get(DATA_ROLL))
            );
            Vec3 thrustDelta = toVec3(fallingAttitude.bodyUp())
                    .scale(fallThrustFactor * maximumThrustNewtons / massKg / 400.0);
            next = next.add(thrustDelta);
        }
        Vec3 before = position();
        move(MoverType.SELF, next);
        setDeltaMovement(next);
        boolean collided = position().distanceToSqr(before.add(next)) > 1.0E-5
                || onGround() || isInWater() || isInLava();
        if (collided && fallingGraceTicks <= 0 || getY() < level.getMinY() - 64) {
            float speed = (float) (next.length() * 20.0);
            if (rpgWarheadLoaded) {
                detonateRpg(level);
                return;
            } else if (speed >= 20.0F) {
                ShrapnelExplosion.detonate(level, position(), 4.0F, 40, this, ownerUuid);
            } else if (speed > 10.0F) {
                ShrapnelExplosion.detonate(level, position(), 2.5F, 20, this, ownerUuid);
            } else if (speed >= 3.0F) {
                level.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF,
                        getX(), getY(), getZ(), 12, 0.25, 0.12, 0.25, 0.04);
            }
            discard();
        }
    }

    private void updateChunkLoading(ServerLevel level) {
        boolean shouldForce = isBeingControlled() || autonomousOperatorUuid != null || isFalling();
        ChunkPos current = chunkPosition();
        if (forcedChunk != null && (!shouldForce || !forcedChunk.equals(current))) {
            DroneMod.DRONE_CHUNK_TICKETS.forceChunk(
                    level, this, forcedChunk.x(), forcedChunk.z(), false, true);
            forcedChunk = null;
        }
        if (shouldForce && forcedChunk == null) {
            if (DroneMod.DRONE_CHUNK_TICKETS.forceChunk(level, this, current.x(), current.z(), true, true)) {
                forcedChunk = current;
            }
        }
    }

    private void releaseChunkLoading() {
        if (forcedChunk != null && level() instanceof ServerLevel serverLevel) {
            DroneMod.DRONE_CHUNK_TICKETS.forceChunk(
                    serverLevel, this, forcedChunk.x(), forcedChunk.z(), false, true);
            forcedChunk = null;
        }
    }

    private FlightVector resolveCollisionVelocity(
            FlightVector simulatedVelocity,
            Vec3 desiredMove,
            Vec3 actualMove
    ) {
        boolean hitX = Math.abs(desiredMove.x - actualMove.x) > 1.0E-5;
        boolean hitY = Math.abs(desiredMove.y - actualMove.y) > 1.0E-5;
        boolean hitZ = Math.abs(desiredMove.z - actualMove.z) > 1.0E-5;
        if (!hitX && !hitY && !hitZ) return simulatedVelocity;

        double nx = hitX ? Math.signum(simulatedVelocity.x()) : 0.0;
        double ny = hitY ? Math.signum(simulatedVelocity.y()) : 0.0;
        double nz = hitZ ? Math.signum(simulatedVelocity.z()) : 0.0;
        double normalLength = Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (normalLength > 1.0E-9) {
            nx /= normalLength;
            ny /= normalLength;
            nz /= normalLength;
        }

        double vx = simulatedVelocity.x();
        double vy = simulatedVelocity.y();
        double vz = simulatedVelocity.z();
        double velocityAlongNormal = vx * nx + vy * ny + vz * nz;
        if (velocityAlongNormal > 0.0) {
            double bounce = hitX || hitZ ? 0.30 : 0.20;
            vx -= (1.0 + bounce) * velocityAlongNormal * nx;
            vy -= (1.0 + bounce) * velocityAlongNormal * ny;
            vz -= (1.0 + bounce) * velocityAlongNormal * nz;
        }

        double afterAlongNormal = vx * nx + vy * ny + vz * nz;
        double tangentX = (vx - afterAlongNormal * nx) * 0.85;
        double tangentY = (vy - afterAlongNormal * ny) * 0.85;
        double tangentZ = (vz - afterAlongNormal * nz) * 0.85;
        afterAlongNormal *= 0.50;
        vx = tangentX + afterAlongNormal * nx;
        vy = tangentY + afterAlongNormal * ny;
        vz = tangentZ + afterAlongNormal * nz;

        if ((hitX || hitZ) && simulatedVelocity.length() < 3.0) {
            vy *= 0.40;
        }
        if (hitY && desiredMove.y <= 0.0) {
            vx *= 0.87;
            vz *= 0.87;
            if (Math.abs(vx) < 0.05) vx = 0.0;
            if (Math.abs(vz) < 0.05) vz = 0.0;
        }
        return new FlightVector(vx, vy, vz);
    }

    private FlightVector resolveEntityImpactVelocity(FlightVector velocity, Entity target) {
        double dx = target.getX() - getX();
        double dy = target.getY() + target.getBbHeight() * 0.5 - (getY() + 0.14);
        double dz = target.getZ() - getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.001) distance = 1.0;
        double nx = dx / distance;
        double ny = dy / distance;
        double nz = dz / distance;
        double alongNormal = velocity.x() * nx + velocity.y() * ny + velocity.z() * nz;
        if (alongNormal <= 0.0) return velocity;
        return new FlightVector(
                velocity.x() - 1.30 * alongNormal * nx,
                velocity.y() - 1.30 * alongNormal * ny,
                velocity.z() - 1.30 * alongNormal * nz
        ).multiply(0.60);
    }

    public void acceptPilotInput(ServerPlayer player, DroneControlPayload payload) {
        boolean linkedControl = RemoteControlItem.playerHasLinkedRemote(player, getUUID())
                && FpvGogglesItem.getLinkedDroneId(player.getItemBySlot(EquipmentSlot.HEAD))
                        .filter(getUUID()::equals).isPresent();
        if (autonomousOperatorUuid != null
                || !player.isAlive()
                || player.isSpectator()
                || !isOwnedBy(player)
                || !linkedControl
                || player.distanceTo(this) > controlRange()) {
            return;
        }
        if (ownerUuid == null) ownerUuid = player.getUUID();
        DroneViewSessions.start(player, getUUID());
        entityData.set(DATA_PILOT_ID, player.getId());
        if (flightState == null) {
            flightState = createState(batteryInstalled ? kind.defaultBattery() : emptyBattery());
        }
        byte actions = payload.actions();
        boolean requestedArmed = (actions & DroneControlPayload.ARMED) != 0;
        armed = requestedArmed && batteryInstalled && !flightState.battery().isDepleted();
        pilotControl = new FlightControl(
                payload.roll(),
                payload.pitch(),
                payload.yaw(),
                payload.throttle(),
                armed
        );
        hoverMode = kind == DroneKind.PAYLOAD && (actions & DroneControlPayload.HOVER) != 0;
        returnHome = kind == DroneKind.PAYLOAD && (actions & DroneControlPayload.RETURN_HOME) != 0;
        boolean wantsDrop = (actions & DroneControlPayload.DROP) != 0;
        if (wantsDrop
                && !dropLatch
                && armed
                && tickCount - lastDropTick >= 4
                && level() instanceof ServerLevel serverLevel) {
            dropPayload(serverLevel);
            lastDropTick = tickCount;
        }
        dropLatch = wantsDrop;
        lastControlTick = tickCount;
        syncTelemetry();
    }

    private boolean isCorrectController(Player player) {
        return RemoteControlItem.playerHasLinkedRemote(player, getUUID());
    }

    private boolean isOwnedBy(Player player) {
        return ownerUuid == null || ownerUuid.equals(player.getUUID());
    }

    public boolean canPlayerControl(Player player) {
        return player != null && isOwnedBy(player) && hasBattery()
                && RemoteControlItem.playerHasLinkedRemote(player, getUUID())
                && FpvGogglesItem.getLinkedDroneId(player.getItemBySlot(EquipmentSlot.HEAD))
                        .filter(getUUID()::equals).isPresent();
    }

    public void beginPilot(Player player) {
        if (!level().isClientSide() && canPlayerControl(player)) {
            ownerUuid = player.getUUID();
            entityData.set(DATA_PILOT_ID, player.getId());
            lastControlTick = tickCount;
            DroneLinkManager.link(getUUID(), ownerUuid);
        }
    }

    public void endPilot(Player player) {
        if (!level().isClientSide() && player != null && entityData.get(DATA_PILOT_ID) == player.getId()) {
            entityData.set(DATA_PILOT_ID, -1);
            armed = false;
            pilotControl = FlightControl.DISARMED;
            syncTelemetry();
        }
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public DroneFlightConfig getFlightConfig() {
        return flightConfig;
    }

    public void setFlightConfig(DroneFlightConfig config) {
        flightConfig = config == null ? DroneFlightConfig.DEFAULT : config;
        physics = new DronePhysics(flightConfig);
        entityData.set(DATA_DRONE_NAME, flightConfig.droneName());
    }

    public String getDroneName() {
        return entityData.get(DATA_DRONE_NAME);
    }

    private double controlRange() {
        return Config.effectiveMaxRange();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        ItemStack held = player.getItemInHand(hand);
        Item heldItem = held.getItem();
        if (level().isClientSide()) {
            return isAutonomous() ? InteractionResult.FAIL : InteractionResult.SUCCESS;
        }
        if (isAutonomous() || !(level() instanceof ServerLevel serverLevel)) {
            return InteractionResult.FAIL;
        }

        if (player.isShiftKeyDown() && rpgWarheadLoaded) {
            rpgWarheadLoaded = false;
            giveOrDrop(serverLevel, player, new ItemStack(DroneMod.RPG_WARHEAD.get()));
            updatePayloadMass();
            player.sendOverlayMessage(Component.translatable("entity.fpvdrone.drone.rpg_detached").withColor(0xFFAA36));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (player.isShiftKeyDown() && batteryInstalled) {
            giveOrDrop(serverLevel, player, batteryStack());
            batteryInstalled = false;
            armed = false;
            pilotControl = FlightControl.DISARMED;
            player.sendOverlayMessage(Component.translatable("entity.fpvdrone.drone.battery_detached").withColor(0xFFAA36));
            level().playSound(null, blockPosition(), DroneMod.FPV_CONNECT.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
            syncTelemetry();
            return InteractionResult.SUCCESS_SERVER;
        }
        if (heldItem == DroneMod.REMOTE_CONTROL.get()) {
            RemoteControlItem.getLinkedDroneId(held).ifPresent(DroneLinkManager::unlink);
            ownerUuid = player.getUUID();
            videoChannel = 1;
            assignUniqueChannel(player);
            RemoteControlItem.setLinkedDroneId(held, getUUID());
            RemoteControlItem.setChannel(held, videoChannel);
            DroneLinkManager.link(getUUID(), player.getUUID());
            player.sendOverlayMessage(Component.translatable("item.fpvdrone.remote_control.link_success", getVideoChannelName()).withColor(0xFFAA36));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (heldItem == DroneMod.FPV_GOGGLES.get()) {
            ownerUuid = player.getUUID();
            videoChannel = 1;
            assignUniqueChannel(player);
            FpvGogglesItem.linkDroneOnChannel(held, videoChannel, getUUID());
            DroneLinkManager.link(getUUID(), player.getUUID());
            player.sendOverlayMessage(Component.translatable("item.fpvdrone.fpv_goggles.link_success", getVideoChannelName()).withColor(0xFFAA36));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (heldItem == DroneMod.BETAFLIGHT.get()) {
            return InteractionResult.SUCCESS_SERVER;
        }
        if (heldItem == DroneMod.WEIGHT.get()) {
            addedWeightGrams += 250.0F;
            consumeOne(player, held);
            updatePayloadMass();
            player.sendOverlayMessage(Component.translatable("item.fpvdrone.weight.applied"));
            return InteractionResult.SUCCESS_SERVER;
        }
        if (heldItem == DroneMod.BATTERY.get()) {
            if (batteryInstalled) {
                player.sendOverlayMessage(Component.translatable(
                        "entity.fpvdrone.drone.battery_already_attached").withColor(0xFFAA36));
            } else {
                installBattery(held);
                consumeOne(player, held);
                level().playSound(null, blockPosition(), DroneMod.FPV_CONNECT.get(), SoundSource.PLAYERS, 0.6F, 1.0F);
                player.sendOverlayMessage(Component.translatable(
                        "entity.fpvdrone.drone.battery_attached").withColor(0xFFAA36));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        if (heldItem == DroneMod.RPG_WARHEAD.get()) {
            if (!rpgWarheadLoaded) {
                rpgWarheadLoaded = true;
                consumeOne(player, held);
                updatePayloadMass();
                player.sendOverlayMessage(Component.translatable("entity.fpvdrone.drone.rpg_attached").withColor(0xFFAA36));
            } else {
                player.sendOverlayMessage(Component.translatable("entity.fpvdrone.drone.rpg_already_attached").withColor(0xFFAA36));
            }
            return InteractionResult.SUCCESS_SERVER;
        }
        pickupDrone(serverLevel, player);
        return InteractionResult.SUCCESS_SERVER;
    }

    private void consumeOne(Player player, ItemStack held) {
        if (!player.hasInfiniteMaterials()) {
            held.shrink(1);
        }
    }

    private void giveOrDrop(ServerLevel level, Player player, ItemStack stack) {
        if (!player.addItem(stack)) {
            spawnAtLocation(level, stack);
        }
    }

    private void pickupDrone(ServerLevel level, Player player) {
        giveOrDrop(level, player, toItemStack());
        unlinkPlayerItems(player);
        DroneLinkManager.unlink(getUUID());
        discard();
    }

    private void unlinkPlayerItems(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            unlinkPlayerStack(player.getInventory().getItem(slot));
        }
        unlinkPlayerStack(player.getOffhandItem());
        unlinkPlayerStack(player.getItemBySlot(EquipmentSlot.HEAD));
    }

    private void unlinkPlayerStack(ItemStack stack) {
        if (stack.getItem() instanceof RemoteControlItem
                && RemoteControlItem.getLinkedDroneId(stack).filter(getUUID()::equals).isPresent()) {
            RemoteControlItem.clearLink(stack);
        }
        if (stack.getItem() instanceof FpvGogglesItem
                && FpvGogglesItem.getLinkedDroneId(stack).filter(getUUID()::equals).isPresent()) {
            FpvGogglesItem.clearLink(stack);
        }
    }

    private boolean isControllerItem(Item item) {
        return item == DroneMod.REMOTE_CONTROL.get();
    }

    private boolean isBatteryItem(Item item) {
        return item == DroneMod.BATTERY.get();
    }

    private boolean isPayloadItem(Item item) {
        return item == DroneMod.RPG_WARHEAD.get();
    }

    private Item expectedController() {
        return DroneMod.REMOTE_CONTROL.get();
    }

    private Item expectedBattery() {
        return DroneMod.BATTERY.get();
    }

    public void installFullBattery() {
        batteryInstalled = true;
        batteryData = BatteryData.defaults();
        BatteryState battery = kind.defaultBattery();
        flightState = flightState == null ? createState(battery) : flightState.withBattery(battery);
        loadedVoltage = battery.openCircuitVoltage();
        syncTelemetry();
    }

    private void installBattery(ItemStack batteryStack) {
        batteryInstalled = true;
        batteryData = BatteryItem.getBatteryData(batteryStack);
        BatteryState battery = BatteryState.lipo(
                batteryData.cells(), batteryData.capacityMah() / 1000.0,
                batteryData.weightGrams() / 1000.0, 0.008 * batteryData.cells())
                .withStateOfCharge((double) batteryData.remainingMah() / batteryData.capacityMah());
        flightState = flightState == null ? createState(battery) : flightState.withBattery(battery);
        loadedVoltage = battery.openCircuitVoltage();
        syncTelemetry();
    }

    private BatteryState emptyBattery() {
        BatteryState reference = kind.defaultBattery();
        return new BatteryState(
                reference.cellCount(),
                reference.capacityWattHours(),
                0.0,
                reference.internalResistanceOhms(),
                0.0
        );
    }

    private ItemStack batteryStack() {
        ItemStack stack = new ItemStack(expectedBattery());
        BatteryData saved = new BatteryData(batteryData.cells(), batteryData.capacityMah(), batteryData.cRating());
        double fraction = flightState == null ? 1.0 : flightState.battery().stateOfCharge();
        saved.setRemainingMah((int) Math.floor(batteryData.capacityMah() * fraction));
        BatteryItem.setBatteryData(stack, saved);
        return stack;
    }

    public void loadPayloadFromItem(ItemStack stack) {
        CompoundTag tag = StackData.copy(stack);
        thermal = thermal || stack.getItem() instanceof DroneItem droneItem && droneItem.thermal();
        tag.getCompound("DroneConfig").map(DroneFlightConfig::load).ifPresent(this::setFlightConfig);
        if (tag.getBooleanOr("HasBattery", false)) {
            batteryData = tag.getCompound("BatteryData").map(BatteryData::load).orElseGet(BatteryData::defaults);
            batteryInstalled = true;
            BatteryState battery = BatteryState.lipo(
                    batteryData.cells(), batteryData.capacityMah() / 1000.0,
                    batteryData.weightGrams() / 1000.0, 0.008 * batteryData.cells())
                    .withStateOfCharge((double) batteryData.remainingMah() / batteryData.capacityMah());
            flightState = flightState == null ? createState(battery) : flightState.withBattery(battery);
            loadedVoltage = battery.openCircuitVoltage();
        }
        rpgWarheadLoaded = tag.getBooleanOr("HasRpg", false);
        addedWeightGrams = Math.max(0.0F, tag.getFloatOr("AddedWeight", 0.0F));
        updatePayloadMass();
        syncTelemetry();
    }

    public ItemStack toItemStack() {
        ItemStack stack = new ItemStack(thermal ? DroneMod.THERMAL_DRONE.get() : DroneMod.DRONE.get());
        StackData.update(stack, tag -> {
            if (batteryInstalled) {
                tag.putBoolean("HasBattery", true);
                tag.put("BatteryData", batteryStackData());
            }
            if (rpgWarheadLoaded) tag.putBoolean("HasRpg", true);
            if (addedWeightGrams > 0.0F) tag.putFloat("AddedWeight", addedWeightGrams);
            tag.put("DroneConfig", flightConfig.save());
        });
        return stack;
    }

    private CompoundTag batteryStackData() {
        BatteryData saved = new BatteryData(batteryData.cells(), batteryData.capacityMah(), batteryData.cRating());
        double fraction = flightState == null ? 1.0 : flightState.battery().stateOfCharge();
        saved.setRemainingMah((int) Math.floor(batteryData.capacityMah() * fraction));
        return saved.save();
    }

    private void assignUniqueChannel(Player player) {
        Set<Integer> used = new HashSet<>();
        if (level() instanceof ServerLevel serverLevel) {
            for (Entity entity : serverLevel.getAllEntities()) {
                if (entity instanceof DroneEntity other && other != this
                        && other.ownerUuid != null && other.ownerUuid.equals(player.getUUID())) {
                    used.add(other.getVideoChannel());
                }
            }
        }
        if (used.contains(videoChannel)) {
            for (int channel = 1; channel <= 8; channel++) {
                if (!used.contains(channel)) {
                    videoChannel = channel;
                    entityData.set(DATA_VIDEO_CHANNEL, (byte) channel);
                    return;
                }
            }
        }
    }

    private float propSpinDelta() {
        float throttle = getSyncedThrottle();
        return isArmed() ? 0.35F + throttle * 1.5F : 0.0F;
    }

    public boolean dropPayload(ServerLevel level) {
        if (kind != DroneKind.PAYLOAD || payloadCount <= 0) {
            return false;
        }
        DroppedPayloadEntity payload = DroneMod.DROPPED_PAYLOAD_ENTITY.get().create(level, EntitySpawnReason.TRIGGERED);
        if (payload == null) {
            return false;
        }
        FlightAttitude attitude = flightState.attitude();
        double side = payloadCount % 2 == 0 ? -0.22 : 0.22;
        FlightVector mount = attitude.bodyRight().multiply(side).add(attitude.bodyUp().multiply(-0.28));
        Vec3 releasePosition = position().add(toVec3(mount));
        payload.snapTo(releasePosition.x, releasePosition.y, releasePosition.z, getYRot(), getXRot());
        payload.configureRelease(this, getDeltaMovement().add(0.0, -0.015, 0.0));
        if (!level.addFreshEntity(payload)) {
            return false;
        }
        payloadCount--;
        FlightRates kick = flightState.angularRates().add(new FlightRates(
                Math.toRadians(side * 7.0),
                Math.toRadians(-0.5),
                0.0
        ));
        flightState = flightState.releasePayload(PAYLOAD_MASS_KG)
                .withAttitude(flightState.attitude(), kick);
        syncTelemetry();
        return true;
    }

    private void updatePayloadMass() {
        if (flightState != null) {
            double mass = kind == DroneKind.MOSQUITO
                    ? (rpgWarheadLoaded ? RPG_MASS_KG : 0.0) + addedWeightGrams / 1000.0
                    : payloadCount * PAYLOAD_MASS_KG;
            flightState = flightState.withPayloadMassKg(mass);
        }
        syncTelemetry();
    }

    private void detonateRpgOnControlledImpact(ServerLevel level) {
        ShrapnelExplosion.detonate(level, position(),
                Config.KAMIKAZE_EXPLOSION_POWER.get().floatValue(),
                Config.SHRAPNEL_COUNT.getAsInt(), this, ownerUuid);
        discard();
    }

    /** V1.1.4 scales a damaged/falling warhead's impact by its current speed. */
    private void detonateRpg(ServerLevel level) {
        float speed = (float) (getDeltaMovement().length() * 20.0);
        float power = Math.min(2.5F + speed * 0.12F, 6.0F);
        int fragments = Math.min(20 + (int) (speed * 2.0F), 80);
        ShrapnelExplosion.detonate(level, position(), power, fragments, this, ownerUuid);
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!isAlive() || isInvulnerableToBase(source) || isFalling()) {
            return false;
        }
        if (source.getEntity() instanceof Player player
                && source.getDirectEntity() == player
                && !player.isPassenger()
                && !isBeingControlled()
                && isOwnedBy(player)) {
            pickupDrone(level, player);
            return false;
        }
        if (rpgWarheadLoaded) {
            detonateRpg(level);
            return false;
        }
        structuralHealth -= Math.max(0.0F, amount);
        markHurt();
        if (structuralHealth <= 0.0F) {
            Vec3 sourcePosition = source.getSourcePosition();
            boolean hitFromAbove = sourcePosition != null && sourcePosition.y > getY() + getBbHeight();
            enterFallingState(FallType.determine(
                    source,
                    amount,
                    (float) (getDeltaMovement().length() * 20.0),
                    hitFromAbove
            ));
        } else {
            applyDamageKick(source, amount);
        }
        return true;
    }

    private void applyDamageKick(DamageSource source, float amount) {
        Vec3 damagePosition = source.getSourcePosition();
        if (damagePosition == null) return;
        Vec3 direction = position().subtract(damagePosition).normalize();
        double strength = Math.min(amount * 0.6F, 3.0F);
        setDeltaMovement(getDeltaMovement().add(direction.scale(strength * 0.05)));
        float yawRadians = (float) Math.toRadians(getYRot());
        float kickYaw = (float) (direction.x * Math.cos(yawRadians) + direction.z * Math.sin(yawRadians))
                * amount * 40.0F;
        float kickPitch = (float) direction.y * amount * -30.0F;
        float kickRoll = (float) (-direction.x * Math.sin(yawRadians) + direction.z * Math.cos(yawRadians))
                * amount * 35.0F;
        setYRot(getYRot() + kickYaw * 0.05F);
        setXRot(getXRot() + kickPitch * 0.05F);
        entityData.set(DATA_ROLL, entityData.get(DATA_ROLL) + kickRoll * 0.05F);
    }

    private void breakDrone(ServerLevel level) {
        armed = false;
        if (autonomousOperatorUuid != null) {
            // Operator-issued aircraft are encounter entities, not renewable
            // sources of airframes, batteries, or armed warheads.
            discard();
            return;
        }
        spawnAtLocation(level, getItem());
        if (batteryInstalled) {
            spawnAtLocation(level, batteryStack());
        }
        if (rpgWarheadLoaded) {
            spawnAtLocation(level, DroneMod.RPG_WARHEAD.get());
        }
        for (int i = 0; i < payloadCount; i++) {
            spawnAtLocation(level, DroneMod.FORTY_MM_PAYLOAD.get());
        }
        discard();
    }

    private void syncTelemetry() {
        entityData.set(DATA_KIND, kind.ordinal());
        entityData.set(DATA_ARMED, armed);
        entityData.set(DATA_AUTONOMOUS, autonomousOperatorUuid != null);
        entityData.set(DATA_ROLL, flightState == null ? 0.0F : (float) Math.toDegrees(flightState.attitude().rollRadians()));
        entityData.set(DATA_BATTERY, flightState == null ? 0.0F : (float) flightState.battery().stateOfCharge());
        entityData.set(DATA_PAYLOADS, kind == DroneKind.MOSQUITO ? (rpgWarheadLoaded ? 1 : 0) : payloadCount);
        entityData.set(DATA_HAS_BATTERY, batteryInstalled);
        entityData.set(DATA_HAS_RPG, rpgWarheadLoaded);
        entityData.set(DATA_THERMAL, thermal);
        entityData.set(DATA_VIDEO_CHANNEL, (byte) videoChannel);
        float telemetryThrottle = throttleCutTicks > 0 ? 0.0F : (float) pilotControl.throttle();
        entityData.set(DATA_THROTTLE, (byte) Math.round(Mth.clamp(telemetryThrottle, 0.0F, 1.0F) * 255.0F));
        entityData.set(DATA_ADDED_WEIGHT, addedWeightGrams);
        entityData.set(DATA_DRONE_NAME, flightConfig.droneName());
    }

    public DroneKind kind() {
        if (level().isClientSide()) {
            int ordinal = Mth.clamp(entityData.get(DATA_KIND), 0, DroneKind.values().length - 1);
            return DroneKind.values()[ordinal];
        }
        return kind;
    }

    public boolean isArmed() {
        return entityData.get(DATA_ARMED);
    }

    public boolean areMotorsArmed() {
        return isArmed();
    }

    public boolean hasBattery() {
        return entityData.get(DATA_HAS_BATTERY);
    }

    public boolean hasBatteryInstalled() {
        return hasBattery();
    }

    public boolean hasRpg() {
        return entityData.get(DATA_HAS_RPG);
    }

    public boolean isThermal() {
        return entityData.get(DATA_THERMAL);
    }

    public void setThermal(boolean value) {
        thermal = value;
        entityData.set(DATA_THERMAL, value);
    }

    public int getVideoChannel() {
        return Math.max(1, Math.min(8, entityData.get(DATA_VIDEO_CHANNEL)));
    }

    public String getVideoChannelName() {
        return "R" + getVideoChannel();
    }

    public float getSyncedThrottle() {
        return (entityData.get(DATA_THROTTLE) & 0xFF) / 255.0F;
    }

    public float getPropAngle(float partialTick) {
        return Mth.lerp(partialTick, previousPropAngle, propAngle);
    }

    public float getAddedWeight() {
        return entityData.get(DATA_ADDED_WEIGHT);
    }

    public float getTotalWeightGrams() {
        return 580.0F + batteryData.weightGrams() + (hasRpg() ? 1000.0F : 0.0F) + getAddedWeight();
    }

    public double getCurrentAmps() {
        return currentAmps;
    }

    public double getBatteryVoltage() {
        if (!hasBattery()) return 0.0;
        return loadedVoltage > 0.0 || flightState == null
                ? loadedVoltage
                : flightState.battery().openCircuitVoltage();
    }

    public int getUsedMah() {
        return Math.max(0, batteryData.capacityMah() - Math.round(batteryData.capacityMah() * batteryFraction()));
    }

    public boolean isBeingControlled() {
        return entityData.get(DATA_PILOT_ID) >= 0;
    }

    public boolean isFalling() {
        return entityData.get(DATA_FALLING);
    }

    public FallType getFallType() {
        int ordinal = Mth.clamp(entityData.get(DATA_FALL_TYPE), 0, FallType.values().length - 1);
        return FallType.values()[ordinal];
    }

    public float getPropAngle() {
        return propAngle;
    }

    public float rollDegrees() {
        return entityData.get(DATA_ROLL);
    }

    public float rollDegrees(float partialTick) {
        if (!level().isClientSide()) {
            return entityData.get(DATA_ROLL);
        }
        return Mth.rotLerp(partialTick, clientPreviousRoll, clientRoll);
    }

    public float batteryFraction() {
        return entityData.get(DATA_BATTERY);
    }

    public float signalQuality() {
        return entityData.get(DATA_SIGNAL);
    }

    public int payloadsLoaded() {
        return entityData.get(DATA_PAYLOADS);
    }

    public boolean isAutonomous() {
        return entityData.get(DATA_AUTONOMOUS);
    }

    public boolean isOperatedBy(DroneOperatorEntity operator) {
        return operator != null
                && autonomousOperatorUuid != null
                && autonomousOperatorUuid.equals(operator.getUUID());
    }

    public double flightSpeedMetersPerSecond() {
        return flightState == null ? getDeltaMovement().length() * 20.0 : flightState.velocityMetersPerSecond().length();
    }

    public boolean isPilotedBy(Player player) {
        return player != null && entityData.get(DATA_PILOT_ID) == player.getId();
    }

    public FlightState flightStateForTesting() {
        return flightState;
    }

    public boolean isReturningHomeForTesting() {
        return returnHome;
    }

    public int controlAgeForTesting() {
        return tickCount - lastControlTick;
    }

    public void setPayloadCountForTesting(int count) {
        payloadCount = Mth.clamp(count, 0, 2);
        updatePayloadMass();
    }

    public void setOwnerAndPilotForTesting(ServerPlayer player) {
        autonomousOperatorUuid = null;
        autonomousTargetUuid = null;
        autonomousDestination = null;
        autonomousAttackRun = false;
        autonomousLinkLostTicks = 0;
        ownerUuid = player.getUUID();
        entityData.set(DATA_PILOT_ID, player.getId());
        entityData.set(DATA_AUTONOMOUS, false);
        lastControlTick = tickCount;
    }

    public UUID operatorUuidForTesting() {
        return autonomousOperatorUuid;
    }

    public UUID autonomousTargetUuidForTesting() {
        return autonomousTargetUuid;
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(isThermal() ? DroneMod.THERMAL_DRONE.get() : DroneMod.DRONE.get());
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        int ordinal = Mth.clamp(input.getIntOr("Kind", 0), 0, DroneKind.values().length - 1);
        kind = DroneKind.values()[ordinal];
        batteryInstalled = input.getBooleanOr("BatteryInstalled", false);
        armed = input.getBooleanOr("Armed", false);
        payloadCount = Mth.clamp(input.getIntOr("PayloadCount", 0), 0, 2);
        rpgWarheadLoaded = input.getBooleanOr("RpgLoaded", false);
        thermal = input.getBooleanOr("IsThermal", false);
        videoChannel = Mth.clamp(input.getIntOr("VideoChannel", 1), 1, 8);
        addedWeightGrams = Math.max(0.0F, input.getFloatOr("AddedWeight", 0.0F));
        hoverMode = input.getBooleanOr("HoverMode", false);
        returnHome = input.getBooleanOr("ReturnHome", false);
        homeX = input.getDoubleOr("HomeX", getX());
        homeY = input.getDoubleOr("HomeY", getY());
        homeZ = input.getDoubleOr("HomeZ", getZ());
        structuralHealth = input.getFloatOr("StructuralHealth", kind == DroneKind.MOSQUITO ? 10.0F : 80.0F);
        setFlightConfig(new DroneFlightConfig(
                input.getFloatOr("YawRcRate", 1.15F),
                input.getFloatOr("PitchRcRate", 1.15F),
                input.getFloatOr("RollRcRate", 1.15F),
                input.getFloatOr("YawSuperRate", 0.67F),
                input.getFloatOr("PitchSuperRate", 0.67F),
                input.getFloatOr("RollSuperRate", 0.67F),
                input.getFloatOr("YawExpo", 0.0F),
                input.getFloatOr("PitchExpo", 0.0F),
                input.getFloatOr("RollExpo", 0.0F),
                input.getFloatOr("MotorKv", 1300.0F),
                input.getFloatOr("PropDiameter", 9.0F),
                input.getFloatOr("PropPitch", 4.5F),
                input.getFloatOr("DragCoefficient", 1.1F),
                input.getFloatOr("ThrustMultiplier", 1.0F),
                input.getBooleanOr("FlightMode3d", false),
                input.getStringOr("DroneName", "KINDER")
        ));
        String owner = input.getStringOr("Owner", "");
        ownerUuid = owner.isBlank() ? null : parseUuid(owner);
        autonomousOperatorUuid = parseUuid(input.getStringOr("AutonomousOperator", ""));
        autonomousTargetUuid = null;
        autonomousDestination = null;
        autonomousAttackRun = false;
        autonomousLinkLostTicks = Math.max(0, input.getIntOr("AutonomousLinkLostTicks", 0));
        entityData.set(DATA_AUTONOMOUS, autonomousOperatorUuid != null);
        entityData.set(DATA_PILOT_ID, -1);

        BatteryState defaultBattery = kind.defaultBattery();
        BatteryState battery = new BatteryState(
                input.getIntOr("BatteryCells", defaultBattery.cellCount()),
                input.getDoubleOr("BatteryCapacityWh", defaultBattery.capacityWattHours()),
                input.getDoubleOr("BatteryRemainingWh", batteryInstalled ? defaultBattery.remainingWattHours() : 0.0),
                input.getDoubleOr("BatteryResistance", defaultBattery.internalResistanceOhms()),
                input.getDoubleOr("BatteryMass", batteryInstalled ? defaultBattery.massKg() : 0.0)
        );
        loadedVoltage = battery.openCircuitVoltage();
        batteryData = new BatteryData(
                battery.cellCount(),
                Math.max(300, (int) Math.round(battery.capacityWattHours() / Math.max(0.1, battery.nominalVoltage()) * 1000.0)),
                input.getIntOr("BatteryCRating", BatteryData.DEFAULT_C_RATING)
        );
        batteryData.setRemainingMah((int) Math.round(batteryData.capacityMah() * battery.stateOfCharge()));
        FlightAttitude attitude = new FlightAttitude(
                input.getDoubleOr("AttitudeX", 0.0),
                input.getDoubleOr("AttitudeY", 0.0),
                input.getDoubleOr("AttitudeZ", 0.0),
                input.getDoubleOr("AttitudeW", 1.0)
        );
        flightState = new FlightState(
                kind,
                toFlightVector(position()),
                toFlightVector(getDeltaMovement()).multiply(20.0),
                attitude,
                FlightRates.ZERO,
                battery,
                kind == DroneKind.MOSQUITO
                        ? (rpgWarheadLoaded ? RPG_MASS_KG : 0.0) + addedWeightGrams / 1000.0
                        : payloadCount * PAYLOAD_MASS_KG,
                0L
        );
        boolean falling = input.getBooleanOr("IsFalling", false);
        entityData.set(DATA_FALLING, falling);
        if (falling) {
            int fallOrdinal = Mth.clamp(input.getIntOr("FallTypeId", 0), 0, FallType.values().length - 1);
            entityData.set(DATA_FALL_TYPE, (byte) fallOrdinal);
            fallYawRate = input.getFloatOr("FallYawRate", 0.0F);
            fallPitchRate = input.getFloatOr("FallPitchRate", 0.0F);
            fallRollRate = input.getFloatOr("FallRollRate", 0.0F);
            fallDragMultiplier = Math.max(0.0F, input.getFloatOr("FallDragMultiplier", 1.0F));
            flipPhaseComplete = input.getBooleanOr("FlipPhaseComplete", false);
            flipAccumulated = Math.max(0.0F, input.getFloatOr("FlipAccumulated", 0.0F));
            fallingGraceTicks = 5;
            armed = false;
            pilotControl = FlightControl.DISARMED;
        }
        // Autonomous operator aircraft use ownerUuid for damage attribution,
        // not as a player-item link.
        if (ownerUuid != null && autonomousOperatorUuid == null && !level().isClientSide()) {
            DroneLinkManager.link(getUUID(), ownerUuid);
        }
        syncTelemetry();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Kind", kind.ordinal());
        output.putBoolean("BatteryInstalled", batteryInstalled);
        output.putBoolean("Armed", armed);
        output.putInt("PayloadCount", payloadCount);
        output.putBoolean("RpgLoaded", rpgWarheadLoaded);
        output.putBoolean("IsThermal", thermal);
        output.putInt("VideoChannel", videoChannel);
        output.putFloat("AddedWeight", addedWeightGrams);
        output.putBoolean("HoverMode", hoverMode);
        output.putBoolean("ReturnHome", returnHome);
        output.putDouble("HomeX", homeX);
        output.putDouble("HomeY", homeY);
        output.putDouble("HomeZ", homeZ);
        output.putFloat("StructuralHealth", structuralHealth);
        output.putInt("BatteryCRating", batteryData.cRating());
        output.putFloat("YawRcRate", flightConfig.yawRcRate());
        output.putFloat("PitchRcRate", flightConfig.pitchRcRate());
        output.putFloat("RollRcRate", flightConfig.rollRcRate());
        output.putFloat("YawSuperRate", flightConfig.yawSuperRate());
        output.putFloat("PitchSuperRate", flightConfig.pitchSuperRate());
        output.putFloat("RollSuperRate", flightConfig.rollSuperRate());
        output.putFloat("YawExpo", flightConfig.yawExpo());
        output.putFloat("PitchExpo", flightConfig.pitchExpo());
        output.putFloat("RollExpo", flightConfig.rollExpo());
        output.putFloat("MotorKv", flightConfig.motorKv());
        output.putFloat("PropDiameter", flightConfig.propDiameterInches());
        output.putFloat("PropPitch", flightConfig.propPitchInches());
        output.putFloat("DragCoefficient", flightConfig.dragCoefficient());
        output.putFloat("ThrustMultiplier", flightConfig.thrustMultiplier());
        output.putBoolean("FlightMode3d", flightConfig.flightMode3d());
        output.putString("DroneName", flightConfig.droneName());
        if (ownerUuid != null) {
            output.putString("Owner", ownerUuid.toString());
        }
        if (autonomousOperatorUuid != null) {
            output.putString("AutonomousOperator", autonomousOperatorUuid.toString());
            output.putInt("AutonomousLinkLostTicks", autonomousLinkLostTicks);
        }
        if (isFalling()) {
            output.putBoolean("IsFalling", true);
            output.putInt("FallTypeId", getFallType().ordinal());
            output.putFloat("FallYawRate", fallYawRate);
            output.putFloat("FallPitchRate", fallPitchRate);
            output.putFloat("FallRollRate", fallRollRate);
            output.putFloat("FallDragMultiplier", fallDragMultiplier);
            output.putBoolean("FlipPhaseComplete", flipPhaseComplete);
            output.putFloat("FlipAccumulated", flipAccumulated);
        }
        if (flightState != null) {
            BatteryState battery = flightState.battery();
            output.putInt("BatteryCells", battery.cellCount());
            output.putDouble("BatteryCapacityWh", battery.capacityWattHours());
            output.putDouble("BatteryRemainingWh", battery.remainingWattHours());
            output.putDouble("BatteryResistance", battery.internalResistanceOhms());
            output.putDouble("BatteryMass", battery.massKg());
            output.putDouble("AttitudeX", flightState.attitude().x());
            output.putDouble("AttitudeY", flightState.attitude().y());
            output.putDouble("AttitudeZ", flightState.attitude().z());
            output.putDouble("AttitudeW", flightState.attitude().w());
        }
    }

    @Override
    public void onRemoval(RemovalReason reason) {
        UUID operatorUuid = autonomousOperatorUuid;
        releaseChunkLoading();
        super.onRemoval(reason);
        if (!level().isClientSide()) DroneLinkManager.unlink(getUUID());
        if (level().isClientSide() || operatorUuid == null || !reason.shouldDestroy()) {
            return;
        }
        if (level() instanceof ServerLevel serverLevel
                && serverLevel.getEntityInAnyDimension(operatorUuid) instanceof DroneOperatorEntity operator) {
            operator.onControlledDroneDestroyed(this);
        }
    }

    private static UUID parseUuid(String value) {
        try {
            return UUID.fromString(value.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static FlightVector toFlightVector(Vec3 value) {
        return new FlightVector(value.x, value.y, value.z);
    }

    private static Vec3 toVec3(FlightVector value) {
        return new Vec3(value.x(), value.y(), value.z());
    }
}
