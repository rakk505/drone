package com.modernity.drone.entity;

import com.modernity.drone.Config;
import com.modernity.drone.DroneMod;
import com.modernity.drone.flight.BatteryState;
import com.modernity.drone.flight.DroneKind;
import com.modernity.drone.flight.DronePhysics;
import com.modernity.drone.flight.FlightAttitude;
import com.modernity.drone.flight.FlightControl;
import com.modernity.drone.flight.FlightRates;
import com.modernity.drone.flight.FlightState;
import com.modernity.drone.flight.FlightStepResult;
import com.modernity.drone.flight.FlightVector;
import com.modernity.drone.network.DroneControlPayload;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
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
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class DroneEntity extends Entity implements ItemSupplier {
    private static final DronePhysics PHYSICS = new DronePhysics();
    private static final double PAYLOAD_MASS_KG = 0.25;
    private static final double RPG_MASS_KG = 1.0;

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
    private int payloadCount;
    private int armedTicks;
    private int signalLostTicks;
    private int lastControlTick = -1_000_000;
    private int lastDropTick = -1_000_000;
    private double homeX;
    private double homeY;
    private double homeZ;
    private float structuralHealth = 50.0F;
    private float clientPreviousRoll;
    private float clientRoll;

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
        structuralHealth = newKind == DroneKind.MOSQUITO ? 50.0F : 80.0F;
        batteryInstalled = false;
        armed = false;
        payloadCount = 0;
        rpgWarheadLoaded = false;
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
        structuralHealth = 50.0F;
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
                ? (rpgWarheadLoaded ? RPG_MASS_KG : 0.0)
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
        if (level().isClientSide()) {
            clientPreviousRoll = clientRoll;
            clientRoll = entityData.get(DATA_ROLL);
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level();
        if (flightState == null || flightState.kind() != kind) {
            flightState = createState(batteryInstalled ? kind.defaultBattery() : emptyBattery());
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
        FlightControl effectiveControl = effectiveControl();
        if (!batteryInstalled || flightState.battery().isDepleted()) {
            armed = false;
            effectiveControl = FlightControl.DISARMED;
        }

        if (armed) {
            armedTicks++;
        } else {
            armedTicks = 0;
        }

        FlightStepResult result = autonomousOperatorUuid == null
                ? PHYSICS.step(flightState, effectiveControl)
                : PHYSICS.stepAutonomous(flightState, effectiveControl);
        FlightState simulated = result.nextState();
        Vec3 start = position();
        Vec3 desiredMove = toVec3(simulated.positionMeters()).subtract(start);
        var entityHit = ProjectileUtil.getEntityHitResult(
                level(),
                this,
                start,
                start.add(desiredMove),
                getBoundingBox().expandTowards(desiredMove).inflate(0.12),
                entity -> entity.isAlive()
                        && entity.isPickable()
                        && (entity instanceof LivingEntity || entity instanceof DroneEntity)
                        && entity.getId() != entityData.get(DATA_PILOT_ID)
                        && !isAutonomousOperator(entity),
                0.12F
        );
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
        flightState = new FlightState(
                kind,
                toFlightVector(position()),
                correctedVelocity,
                simulated.attitude(),
                simulated.angularRates(),
                simulated.battery(),
                simulated.payloadMassKg(),
                simulated.simulationTick()
        );
        setDeltaMovement(toVec3(correctedVelocity).scale(1.0 / 20.0));
        setYRot((float) Math.toDegrees(flightState.attitude().yawRadians()));
        setXRot((float) -Math.toDegrees(flightState.attitude().pitchRadians()));

        double impactSpeed = entityImpact
                ? simulated.velocityMetersPerSecond().length()
                : simulated.velocityMetersPerSecond().subtract(correctedVelocity).length();
        if (impactSpeed > 2.0) {
            if (kind == DroneKind.MOSQUITO
                    && armed
                    && rpgWarheadLoaded
                    && armedTicks >= 10
                    && (autonomousOperatorUuid == null || autonomousAttackRun)) {
                detonateRpg(serverLevel);
                return;
            }
            if (entityHit != null) {
                Entity sourceOwner = ownerUuid == null ? null : serverLevel.getEntityInAnyDimension(ownerUuid);
                float kineticDamage = (float) Math.min(
                        20.0,
                        0.5 * simulated.totalMassKg() * impactSpeed * impactSpeed / 20.0
                );
                entityHit.getEntity().hurtServer(
                        serverLevel,
                        serverLevel.damageSources().thrown(this, sourceOwner),
                        kineticDamage
                );
                correctedVelocity = correctedVelocity.multiply(-0.2);
                flightState = flightState.withKinematics(
                        flightState.positionMeters(),
                        correctedVelocity
                );
                setDeltaMovement(toVec3(correctedVelocity).scale(1.0 / 20.0));
            }
            structuralHealth -= (float) Math.max(0.0, impactSpeed - 4.0) * 0.75F;
            if (structuralHealth <= 0.0F) {
                breakDrone(serverLevel);
                return;
            }
        }
        syncTelemetry();
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
        armed = false;
        pilotControl = FlightControl.DISARMED;
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
        boolean signalFailed = signalLostTicks >= 30;
        if (!armed || (!timedOut && !signalFailed)) {
            return;
        }
        if (autonomousOperatorUuid != null) {
            onOperatorSignalLost();
            return;
        }
        if (kind == DroneKind.PAYLOAD && batteryInstalled && !flightState.battery().isDepleted()) {
            returnHome = true;
            hoverMode = false;
        } else {
            armed = false;
            pilotControl = FlightControl.DISARMED;
        }
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
        double maximumRange = kind == DroneKind.MOSQUITO
                ? Config.MOSQUITO_CONTROL_RANGE.getAsDouble()
                : Config.PAYLOAD_CONTROL_RANGE.getAsDouble();
        double distance = pilot.distanceTo(this);
        double rangeFactor = Mth.clamp(1.0 - distance / maximumRange, 0.0, 1.0);
        HitResult obstruction = level.clip(new ClipContext(
                pilot.getEyePosition(),
                getEyePosition(),
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                pilot
        ));
        double obstacleFactor = obstruction.getType() == HitResult.Type.MISS ? 1.0 : 0.38;
        double altitudeBonus = Mth.clamp((getY() - pilot.getY()) / 180.0, 0.0, 0.15);
        float signal = rangeFactor <= 0.0
                ? 0.0F
                : (float) Mth.clamp(rangeFactor * obstacleFactor + altitudeBonus, 0.0, 1.0);
        entityData.set(DATA_SIGNAL, signal);
        if (signal <= 0.01F) {
            signalLostTicks++;
        } else {
            signalLostTicks = Math.max(0, signalLostTicks - 2);
        }
    }

    private FlightVector resolveCollisionVelocity(
            FlightVector simulatedVelocity,
            Vec3 desiredMove,
            Vec3 actualMove
    ) {
        double vx = simulatedVelocity.x();
        double vy = simulatedVelocity.y();
        double vz = simulatedVelocity.z();
        if (Math.abs(desiredMove.x - actualMove.x) > 1.0E-5) {
            vx *= -0.30;
        }
        if (Math.abs(desiredMove.z - actualMove.z) > 1.0E-5) {
            vz *= -0.30;
        }
        if (Math.abs(desiredMove.y - actualMove.y) > 1.0E-5) {
            vy = verticalCollisionBelow ? Math.max(0.0, vy * -0.20) : Math.min(0.0, vy * -0.20);
            vx *= 0.85;
            vz *= 0.85;
        }
        if (onGround() && !armed) {
            vx *= 0.87;
            vz *= 0.87;
        }
        return new FlightVector(vx, vy, vz);
    }

    public void acceptPilotInput(ServerPlayer player, DroneControlPayload payload) {
        if (autonomousOperatorUuid != null
                || !player.isAlive()
                || player.isSpectator()
                || !isOwnedBy(player)
                || entityData.get(DATA_PILOT_ID) != player.getId()
                || !isCorrectController(player)
                || player.distanceTo(this) > controlRange()) {
            return;
        }
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
        Item expected = kind == DroneKind.MOSQUITO
                ? DroneMod.FPV_CONTROLLER.get()
                : DroneMod.DJI_CONTROLLER.get();
        return player.getMainHandItem().getItem() == expected || player.getOffhandItem().getItem() == expected;
    }

    private boolean isOwnedBy(Player player) {
        return ownerUuid == null || ownerUuid.equals(player.getUUID());
    }

    private double controlRange() {
        return kind == DroneKind.MOSQUITO
                ? Config.MOSQUITO_CONTROL_RANGE.getAsDouble()
                : Config.PAYLOAD_CONTROL_RANGE.getAsDouble();
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand, Vec3 hitLocation) {
        ItemStack held = player.getItemInHand(hand);
        Item heldItem = held.getItem();
        boolean recognized = isBatteryItem(heldItem) || isPayloadItem(heldItem) || isControllerItem(heldItem)
                || (held.isEmpty() && player.isShiftKeyDown());
        if (level().isClientSide()) {
            return isAutonomous() ? InteractionResult.FAIL : recognized ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (isAutonomous() || !(level() instanceof ServerLevel serverLevel) || !isOwnedBy(player)) {
            return InteractionResult.FAIL;
        }

        if (isControllerItem(heldItem) && heldItem == expectedController()) {
            if (ownerUuid == null) {
                ownerUuid = player.getUUID();
            }
            entityData.set(DATA_PILOT_ID, player.getId());
            lastControlTick = tickCount;
            return InteractionResult.SUCCESS_SERVER;
        }
        if (isBatteryItem(heldItem) && heldItem == expectedBattery() && !armed && !batteryInstalled) {
            installBattery(held);
            consumeOne(player, held);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (isPayloadItem(heldItem) && !armed) {
            if (kind == DroneKind.MOSQUITO && heldItem == DroneMod.RPG_WARHEAD.get() && !rpgWarheadLoaded) {
                rpgWarheadLoaded = true;
                consumeOne(player, held);
                updatePayloadMass();
                return InteractionResult.SUCCESS_SERVER;
            }
            if (kind == DroneKind.PAYLOAD && heldItem == DroneMod.FORTY_MM_PAYLOAD.get() && payloadCount < 2) {
                payloadCount++;
                consumeOne(player, held);
                updatePayloadMass();
                return InteractionResult.SUCCESS_SERVER;
            }
        }
        if (held.isEmpty() && player.isShiftKeyDown() && !armed) {
            giveOrDrop(serverLevel, player, getItem());
            if (batteryInstalled) {
                giveOrDrop(serverLevel, player, batteryStack());
            }
            if (rpgWarheadLoaded) {
                giveOrDrop(serverLevel, player, new ItemStack(DroneMod.RPG_WARHEAD.get()));
            }
            for (int i = 0; i < payloadCount; i++) {
                giveOrDrop(serverLevel, player, new ItemStack(DroneMod.FORTY_MM_PAYLOAD.get()));
            }
            discard();
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
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

    private boolean isControllerItem(Item item) {
        return item == DroneMod.FPV_CONTROLLER.get() || item == DroneMod.DJI_CONTROLLER.get();
    }

    private boolean isBatteryItem(Item item) {
        return item == DroneMod.FPV_BATTERY.get() || item == DroneMod.DJI_BATTERY.get();
    }

    private boolean isPayloadItem(Item item) {
        return item == DroneMod.RPG_WARHEAD.get() || item == DroneMod.FORTY_MM_PAYLOAD.get();
    }

    private Item expectedController() {
        return kind == DroneKind.MOSQUITO ? DroneMod.FPV_CONTROLLER.get() : DroneMod.DJI_CONTROLLER.get();
    }

    private Item expectedBattery() {
        return kind == DroneKind.MOSQUITO ? DroneMod.FPV_BATTERY.get() : DroneMod.DJI_BATTERY.get();
    }

    public void installFullBattery() {
        batteryInstalled = true;
        BatteryState battery = kind.defaultBattery();
        flightState = flightState == null ? createState(battery) : flightState.withBattery(battery);
        syncTelemetry();
    }

    private void installBattery(ItemStack batteryStack) {
        batteryInstalled = true;
        BatteryState battery = kind.defaultBattery();
        if (batteryStack.isDamageableItem() && batteryStack.getMaxDamage() > 1) {
            double stateOfCharge = 1.0 - (double) batteryStack.getDamageValue() / (batteryStack.getMaxDamage() - 1);
            battery = battery.withStateOfCharge(stateOfCharge);
        }
        flightState = flightState == null ? createState(battery) : flightState.withBattery(battery);
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
        if (flightState != null && stack.isDamageableItem() && stack.getMaxDamage() > 1) {
            double consumed = 1.0 - flightState.battery().stateOfCharge();
            int damage = (int) Math.round(Mth.clamp(consumed, 0.0, 1.0) * (stack.getMaxDamage() - 1));
            stack.setDamageValue(damage);
        }
        return stack;
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
                    ? (rpgWarheadLoaded ? RPG_MASS_KG : 0.0)
                    : payloadCount * PAYLOAD_MASS_KG;
            flightState = flightState.withPayloadMassKg(mass);
        }
        syncTelemetry();
    }

    private void detonateRpg(ServerLevel level) {
        Vec3 origin = position();
        FlightVector forwardVector = flightState.attitude().bodyForward();
        Vec3 forward = toVec3(forwardVector).normalize();
        Entity sourceOwner = ownerUuid == null ? null : level.getEntityInAnyDimension(ownerUuid);
        DamageSource damage = level.damageSources().explosion(this, sourceOwner);
        AABB coneBounds = getBoundingBox().inflate(9.0);
        for (Entity target : level.getEntities(this, coneBounds, entity -> entity.isAlive() && entity != sourceOwner)) {
            Vec3 offset = target.getEyePosition().subtract(origin);
            double distance = offset.length();
            if (distance < 0.1 || distance > 9.0 || offset.normalize().dot(forward) < 0.72) {
                continue;
            }
            HitResult trace = level.clip(new ClipContext(
                    origin,
                    target.getEyePosition(),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    this
            ));
            if (trace.getType() == HitResult.Type.MISS) {
                float shapedDamage = (float) (80.0 * Math.pow(1.0 - distance / 9.0, 1.5));
                target.hurtServer(level, damage, shapedDamage);
            }
        }
        level.explode(
                this,
                getX(),
                getY(),
                getZ(),
                4.0F,
                Config.PAYLOAD_BLOCK_DAMAGE.getAsBoolean()
                        ? Level.ExplosionInteraction.BLOCK
                        : Level.ExplosionInteraction.NONE
        );
        discard();
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (!isAlive() || isInvulnerableToBase(source)) {
            return false;
        }
        structuralHealth -= Math.max(0.0F, amount);
        markHurt();
        if (structuralHealth <= 0.0F) {
            breakDrone(level);
        }
        return true;
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
        return new ItemStack(kind() == DroneKind.MOSQUITO ? DroneMod.MOSQUITO_DRONE.get() : DroneMod.PAYLOAD_DRONE.get());
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
        hoverMode = input.getBooleanOr("HoverMode", false);
        returnHome = input.getBooleanOr("ReturnHome", false);
        homeX = input.getDoubleOr("HomeX", getX());
        homeY = input.getDoubleOr("HomeY", getY());
        homeZ = input.getDoubleOr("HomeZ", getZ());
        structuralHealth = input.getFloatOr("StructuralHealth", kind == DroneKind.MOSQUITO ? 50.0F : 80.0F);
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
                kind == DroneKind.MOSQUITO ? (rpgWarheadLoaded ? RPG_MASS_KG : 0.0) : payloadCount * PAYLOAD_MASS_KG,
                0L
        );
        syncTelemetry();
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        output.putInt("Kind", kind.ordinal());
        output.putBoolean("BatteryInstalled", batteryInstalled);
        output.putBoolean("Armed", armed);
        output.putInt("PayloadCount", payloadCount);
        output.putBoolean("RpgLoaded", rpgWarheadLoaded);
        output.putBoolean("HoverMode", hoverMode);
        output.putBoolean("ReturnHome", returnHome);
        output.putDouble("HomeX", homeX);
        output.putDouble("HomeY", homeY);
        output.putDouble("HomeZ", homeZ);
        output.putFloat("StructuralHealth", structuralHealth);
        if (ownerUuid != null) {
            output.putString("Owner", ownerUuid.toString());
        }
        if (autonomousOperatorUuid != null) {
            output.putString("AutonomousOperator", autonomousOperatorUuid.toString());
            output.putInt("AutonomousLinkLostTicks", autonomousLinkLostTicks);
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
        super.onRemoval(reason);
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
