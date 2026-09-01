package com.modernity.drone.test;

import com.modernity.drone.Config;
import com.modernity.drone.DroneMod;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.entity.DroneOperatorEntity;
import com.modernity.drone.entity.DroppedPayloadEntity;
import com.modernity.drone.flight.DroneKind;
import com.modernity.drone.flight.DronePhysics;
import com.modernity.drone.flight.FlightControl;
import com.modernity.drone.flight.FlightState;
import com.modernity.drone.flight.FlightStepResult;
import com.modernity.drone.flight.FlightVector;
import com.modernity.drone.item.FpvGogglesItem;
import com.modernity.drone.item.RemoteControlItem;
import com.modernity.drone.network.DroneControlPayload;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server-side regression tests for flight, payload release, and trust boundaries. */
public final class DroneGameTests {
    private static final double EPSILON = 1.0E-8;

    private DroneGameTests() {
    }

    public static void mosquitoLiftAndBattery(GameTestHelper helper) {
        DronePhysics physics = new DronePhysics();
        FlightControl climb = new FlightControl(0.0, 0.0, 0.0, 0.72, true);
        FlightState firstRun = FlightState.atRest(DroneKind.MOSQUITO, FlightVector.ZERO);
        FlightState secondRun = FlightState.atRest(DroneKind.MOSQUITO, FlightVector.ZERO);
        double initialEnergy = firstRun.battery().remainingWattHours();

        for (int tick = 0; tick < 20; tick++) {
            FlightStepResult firstStep = physics.step(firstRun, climb);
            FlightStepResult secondStep = physics.step(secondRun, climb);
            helper.assertValueEqual(firstStep, secondStep, "identical inputs must produce deterministic flight steps");
            firstRun = firstStep.nextState();
            secondRun = secondStep.nextState();
        }

        helper.assertTrue(firstRun.positionMeters().y() > 1.0, "armed mosquito should generate meaningful lift");
        helper.assertTrue(firstRun.velocityMetersPerSecond().y() > 0.0, "mosquito climb velocity should be upward");
        helper.assertTrue(
                firstRun.battery().remainingWattHours() < initialEnergy,
                "motor load must consume battery energy"
        );
        helper.assertValueEqual(firstRun.simulationTick(), 20L, "physics must advance exactly one tick per step");

        DroneEntity drone = helper.spawn(DroneMod.DRONE_ENTITY.get(), new Vec3(0.5, 5.0, 0.5));
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        pilot.snapTo(drone.getX() + 1.0, drone.getY(), drone.getZ());
        drone.configurePlacedDrone(DroneKind.MOSQUITO, pilot);
        drone.installFullBattery();
        drone.setOwnerAndPilotForTesting(pilot);
        equipLinkedControls(pilot, drone);

        double startingY = drone.getY();
        double startingCharge = drone.flightStateForTesting().battery().stateOfCharge();
        drone.acceptPilotInput(pilot, new DroneControlPayload(
                drone.getId(),
                0.0F,
                0.0F,
                0.0F,
                0.72F,
                DroneControlPayload.ARMED
        ));

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(drone.isAlive(), "mosquito should remain alive during a controlled climb");
            helper.assertTrue(drone.isArmed(), "authorized arm command should be accepted");
            helper.assertTrue(drone.getY() > startingY + 0.25, "live server entity should climb under throttle");
            helper.assertTrue(
                    drone.flightStateForTesting().battery().stateOfCharge() < startingCharge,
                    "live server simulation should drain the installed battery"
            );
            helper.succeed();
        });
    }

    public static void payloadRelease(GameTestHelper helper) {
        DroneEntity drone = helper.spawn(DroneMod.DRONE_ENTITY.get(), new Vec3(0.5, 40.0, 0.5));
        drone.configurePlacedDrone(DroneKind.PAYLOAD, null);
        drone.installFullBattery();
        drone.setPayloadCountForTesting(2);

        // Keep the projectile inside the GameTest's ticking chunk while still
        // proving exact three-axis momentum inheritance.
        Vec3 carrierVelocity = new Vec3(0.005, 0.04, -0.004);
        drone.setDeltaMovement(carrierVelocity);
        double massBefore = drone.flightStateForTesting().payloadMassKg();

        helper.assertTrue(drone.dropPayload(helper.getLevel()), "loaded payload drone should release one round");
        helper.assertValueEqual(drone.payloadsLoaded(), 1, "one of two payloads should remain in the bay");
        assertNearlyEqual(
                helper,
                drone.flightStateForTesting().payloadMassKg(),
                massBefore - 0.25,
                "released payload mass must be removed from the airframe"
        );

        List<DroppedPayloadEntity> payloads = helper.getLevel().getEntitiesOfClass(
                DroppedPayloadEntity.class,
                new AABB(drone.position(), drone.position()).inflate(2.0)
        );
        helper.assertValueEqual(payloads.size(), 1, "one release command must create exactly one payload entity");
        DroppedPayloadEntity payload = payloads.getFirst();
        helper.assertTrue(payload.getOwner() == drone, "released payload should retain the carrier as its owner");
        assertNearlyEqual(helper, payload.getDeltaMovement().x, carrierVelocity.x, "payload must inherit carrier X momentum");
        assertNearlyEqual(helper, payload.getDeltaMovement().y, carrierVelocity.y - 0.015, "release rack should add a small downward impulse");
        assertNearlyEqual(helper, payload.getDeltaMovement().z, carrierVelocity.z, "payload must inherit carrier Z momentum");
        helper.assertValueEqual(DroppedPayloadEntity.ARMING_DELAY_TICKS, 30, "payload arming delay must remain 30 ticks");
        helper.assertFalse(payload.isArmed(), "payload must be inert immediately after release");

        helper.runAtTickTime(28, () -> {
            helper.assertTrue(payload.isAlive(), "payload should remain present before its arming delay expires");
            helper.assertFalse(payload.isArmed(), "payload must still be inert before tick 30");
        });
        helper.runAtTickTime(32, () -> {
            helper.assertTrue(payload.isAlive(), "high-altitude payload should survive through its arming delay");
            helper.assertTrue(
                    payload.isArmed(),
                    "payload must arm after 30 server ticks; alive=" + payload.isAlive()
                            + ", entityTick=" + payload.tickCount
                            + ", payloadAge=" + payload.payloadAgeForTesting()
            );
            helper.succeed();
        });
    }

    public static void serverAuthorityAndFailsafe(GameTestHelper helper) {
        ServerPlayer pilot = helper.makeMockServerPlayerInLevel();
        DroneEntity mosquito = helper.spawn(DroneMod.DRONE_ENTITY.get(), new Vec3(0.5, 5.0, 0.5));
        mosquito.configurePlacedDrone(DroneKind.MOSQUITO, pilot);
        mosquito.installFullBattery();
        pilot.snapTo(mosquito.getX() + 1.0, mosquito.getY(), mosquito.getZ());
        pilot.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneMod.FPV_CONTROLLER.get()));

        Vec3 positionBeforeRejectedInput = mosquito.position();
        FlightState stateBeforeRejectedInput = mosquito.flightStateForTesting();
        mosquito.acceptPilotInput(pilot, armedControl(mosquito, 1.0F, (byte) 0));
        helper.assertFalse(mosquito.isArmed(), "an owner who has not linked as pilot must not arm the drone");
        helper.assertValueEqual(mosquito.position(), positionBeforeRejectedInput, "rejected input must not move the entity");
        helper.assertValueEqual(
                mosquito.flightStateForTesting(),
                stateBeforeRejectedInput,
                "rejected input must not mutate authoritative flight state"
        );

        DroneEntity payloadDrone = helper.spawn(DroneMod.DRONE_ENTITY.get(), new Vec3(0.5, 10.0, 0.5));
        ServerPlayer payloadPilot = helper.makeMockServerPlayerInLevel();
        payloadPilot.snapTo(payloadDrone.getX() + 1.0, payloadDrone.getY(), payloadDrone.getZ());
        payloadDrone.configurePlacedDrone(DroneKind.PAYLOAD, payloadPilot);
        payloadDrone.installFullBattery();
        payloadDrone.setOwnerAndPilotForTesting(payloadPilot);

        helper.runAtTickTime(2, () -> {
            helper.assertFalse(mosquito.isArmed(), "unlinked input must remain rejected after server simulation ticks");
            assertNearlyEqual(helper, mosquito.getX(), positionBeforeRejectedInput.x, "rejected input must not cause X motion");
            assertNearlyEqual(helper, mosquito.getZ(), positionBeforeRejectedInput.z, "rejected input must not cause Z motion");
            helper.assertTrue(
                    mosquito.getY() <= positionBeforeRejectedInput.y + EPSILON,
                    "rejected throttle must not create lift"
            );

            mosquito.setOwnerAndPilotForTesting(pilot);
            equipLinkedControls(pilot, mosquito);
            mosquito.acceptPilotInput(pilot, armedControl(mosquito, 0.60F, (byte) 0));
            equipLinkedControls(payloadPilot, payloadDrone);
            payloadDrone.acceptPilotInput(
                    payloadPilot,
                    armedControl(payloadDrone, 0.50F, DroneControlPayload.HOVER)
            );
        });

        helper.runAtTickTime(4, () -> {
            helper.assertTrue(mosquito.isArmed(), "valid linked mosquito input should arm on the server");
            helper.assertTrue(payloadDrone.isArmed(), "valid linked payload-drone input should arm on the server");
            helper.assertFalse(
                    payloadDrone.isReturningHomeForTesting(),
                    "return-to-home must not engage while control input is fresh"
            );
        });

        int failsafeCheckTick = Config.CONTROL_TIMEOUT_TICKS.getAsInt() + 12;
        helper.runAtTickTime(failsafeCheckTick, () -> {
            helper.assertFalse(
                    mosquito.isArmed(),
                    "mosquito failsafe must disarm after control timeout; alive=" + mosquito.isAlive()
                            + ", removed=" + mosquito.isRemoved()
                            + ", entityTick=" + mosquito.tickCount
                            + ", controlAge=" + mosquito.controlAgeForTesting()
            );
            helper.assertFalse(payloadDrone.isArmed(), "payload drone must cut its motors after control timeout");
            helper.assertFalse(
                    payloadDrone.isReturningHomeForTesting(),
                    "reference-compatible signal loss must not invent a return-to-home mode"
            );
            helper.assertTrue(payloadDrone.isFalling(), "payload drone must enter its uncontrolled failsafe fall");
            helper.succeed();
        });
    }

    public static void operatorDeployment(GameTestHelper helper) {
        forceChunksAround(helper, helper.absoluteVec(new Vec3(0.5, 5.0, 0.5)), 2);
        DroneOperatorEntity operator = helper.spawn(
                DroneMod.DRONE_OPERATOR_ENTITY.get(),
                new Vec3(0.5, 5.0, 0.5)
        );
        Vec3 expectedStation = operator.position();
        DroneEntity[] deployedDrone = new DroneEntity[1];
        Vec3[] initialDronePosition = new Vec3[1];
        Vec3[] previousDronePosition = new Vec3[1];
        double[] maximumDroneDisplacement = new double[1];
        double[] horizontalPathLength = new double[1];
        int[] elapsedTicks = new int[1];
        double[] minimumSettledClearance = {Double.POSITIVE_INFINITY};
        double[] minimumSettledAltitude = {Double.POSITIVE_INFINITY};

        helper.runAtTickTime(3, () -> {
            DroneEntity drone = controlledDrone(helper, operator);
            deployedDrone[0] = drone;
            initialDronePosition[0] = drone.position();
            previousDronePosition[0] = drone.position();

            helper.assertValueEqual(
                    operator.controlledDroneUuidForTesting(),
                    drone.getUUID(),
                    "operator must retain the deployed drone UUID"
            );
            helper.assertValueEqual(
                    drone.operatorUuidForTesting(),
                    operator.getUUID(),
                    "deployed drone must retain the reciprocal operator UUID"
            );
            helper.assertValueEqual(
                    operatorDroneCount(helper, operator),
                    1,
                    "operator must deploy exactly one autonomous drone"
            );
            helper.assertTrue(drone.isAutonomous(), "operator drone must use autonomous server control");
            helper.assertTrue(drone.isArmed(), "operator drone must launch armed");
            helper.assertTrue(
                    operator.position().distanceToSqr(operator.stationPositionForTesting()) <= EPSILON,
                    "operator must hold its initialized station"
            );
        });

        helper.onEachTick(() -> {
            elapsedTicks[0]++;
            if (deployedDrone[0] != null && initialDronePosition[0] != null && deployedDrone[0].isAlive()) {
                maximumDroneDisplacement[0] = Math.max(
                        maximumDroneDisplacement[0],
                        deployedDrone[0].position().distanceTo(initialDronePosition[0])
                );
                if (previousDronePosition[0] != null) {
                    Vec3 step = deployedDrone[0].position().subtract(previousDronePosition[0]);
                    horizontalPathLength[0] += Math.hypot(step.x, step.z);
                }
                previousDronePosition[0] = deployedDrone[0].position();
                // Ignore the initial climb out of the launch hover and only
                // measure the altitude band the drone actually works in.
                if (elapsedTicks[0] >= 60) {
                    minimumSettledClearance[0] = Math.min(
                            minimumSettledClearance[0],
                            deployedDrone[0].heightAboveTerrain()
                    );
                    minimumSettledAltitude[0] = Math.min(minimumSettledAltitude[0], deployedDrone[0].getY());
                }
            }
        });

        helper.runAtTickTime(100, () -> {
            DroneEntity drone = controlledDrone(helper, operator);
            helper.assertTrue(drone == deployedDrone[0], "operator must keep its original living drone");
            helper.assertValueEqual(
                    operatorDroneCount(helper, operator),
                    1,
                    "sustained operation must not create duplicate drones"
            );
            helper.assertTrue(
                    operator.position().distanceToSqr(expectedStation) <= EPSILON,
                    "operator must remain planted while the drone loiters"
            );
            helper.assertTrue(
                    operator.getDeltaMovement().lengthSqr() <= EPSILON,
                    "stationary operator must not accumulate movement"
            );
            helper.assertTrue(drone.isAlive(), "loitering drone must remain alive");
            helper.assertTrue(drone.isAutonomous(), "loitering drone must remain autonomous");
            helper.assertTrue(drone.isArmed(), "valid operator directives must prevent control-timeout disarming");
            helper.assertTrue(
                    maximumDroneDisplacement[0] > 0.25,
                    "loitering drone must fly rather than remain motionless; maximum displacement="
                            + maximumDroneDisplacement[0]
            );
            helper.assertTrue(
                    horizontalPathLength[0] > 1.5,
                    "loitering must produce sustained horizontal flight; path=" + horizontalPathLength[0]
            );
            helper.assertTrue(
                    drone.getY() > expectedStation.y + 1.0,
                    "loitering drone must remain airborne above the operator"
            );
            helper.assertTrue(
                    drone.distanceTo(operator) < DroneOperatorEntity.LOITER_RADIUS + 16.0,
                    "loitering drone must stay near its stationary operator"
            );
            // The whole point of the hunting rework: an autonomous drone works a
            // high block of air, it does not skim the surface.
            helper.assertTrue(
                    minimumSettledAltitude[0] >= expectedStation.y + DroneOperatorEntity.LOITER_ALTITUDE - 4.0,
                    "settled loiter must hold its commanded altitude above the station; minimum="
                            + minimumSettledAltitude[0] + ", station=" + expectedStation.y
            );
            helper.assertTrue(
                    minimumSettledClearance[0] >= DroneEntity.CRUISE_GROUND_CLEARANCE - 2.0,
                    "loitering drone must never drop into the terrain-hugging band; minimum clearance="
                            + minimumSettledClearance[0]
            );

            // Operators are planted encounter fixtures; the standard distance
            // despawn would delete them and orphan the drone mid-flight.
            helper.assertFalse(
                    operator.removeWhenFarAway(1.0E6),
                    "operators must refuse the distance despawn no matter how far the nearest player is"
            );
            helper.assertTrue(
                    operator.requiresCustomPersistence(),
                    "operators must opt out of the generic mob despawn path"
            );
            if (helper.getLevel().getDifficulty() != Difficulty.PEACEFUL) {
                operator.checkDespawn();
                helper.assertTrue(
                        operator.isAlive(),
                        "a despawn check must never remove a deployed operator"
                );
                helper.assertValueEqual(
                        operator.controlledDroneUuidForTesting(),
                        drone.getUUID(),
                        "surviving the despawn check must leave the operator's drone link intact"
                );
            }

            helper.hurt(drone, helper.getLevel().damageSources().generic(), 1000.0F);
            helper.runAfterDelay(2, () -> {
                helper.assertFalse(drone.isAlive(), "destroyed operator drone must be removed");
                helper.assertTrue(
                        operator.controlledDroneUuidForTesting() == null,
                        "destroyed drone must notify its operator"
                );
                helper.assertValueEqual(
                        operatorDroneCount(helper, operator),
                        0,
                        "redeployment cooldown must prevent an immediate replacement"
                );
                List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
                        ItemEntity.class,
                        new AABB(operator.position(), operator.position()).inflate(32.0)
                );
                helper.assertTrue(drops.isEmpty(), "operator drones must not create renewable equipment drops");
                helper.succeed();
            });
        });
    }

    public static void operatorLockAndPursuit(GameTestHelper helper) {
        forceChunksAround(helper, helper.absoluteVec(new Vec3(0.5, 5.0, 0.5)), 4);
        DroneOperatorEntity operator = helper.spawn(
                DroneMod.DRONE_OPERATOR_ENTITY.get(),
                new Vec3(0.5, 5.0, 0.5)
        );
        ServerPlayer target = makeConnectedSurvivalPlayer(helper);
        helper.runBeforeTestEnd(() -> disconnectTestPlayer(helper, target));
        // Stand the target well outside the charge-entry radius so the drone has
        // to fly a genuine stalking leg before it commits to a diving strike.
        Vec3 targetPosition = helper.absoluteVec(new Vec3(0.5, 5.0, 38.5));
        target.snapTo(targetPosition.x, targetPosition.y, targetPosition.z);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        target.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(DroneMod.FPV_CONTROLLER.get()));

        DroneEntity[] deployedDrone = new DroneEntity[1];
        double[] distanceAtLock = new double[1];
        double[] minimumPursuitDistance = {Double.POSITIVE_INFINITY};
        double[] maximumPursuitSpeed = new double[1];
        int[] consecutiveClosingTicks = new int[1];
        boolean[] closingVerified = new boolean[1];
        boolean[] pursuitVerified = new boolean[1];
        double[] maximumStalkSpeed = new double[1];
        double[] maximumChargeSpeed = new double[1];
        double[] minimumStalkAltitudeMargin = {Double.POSITIVE_INFINITY};
        double[] minimumStalkClearance = {Double.POSITIVE_INFINITY};
        int[] stalkTicks = new int[1];
        int[] chargeTicks = new int[1];

        helper.runAtTickTime(3, () -> {
            DroneEntity drone = controlledDrone(helper, operator);
            deployedDrone[0] = drone;
            InteractionResult linkResult = drone.interact(target, InteractionHand.MAIN_HAND, Vec3.ZERO);
            drone.acceptPilotInput(target, new DroneControlPayload(
                    drone.getId(),
                    1.0F,
                    1.0F,
                    1.0F,
                    0.0F,
                    (byte) 0
            ));

            helper.assertValueEqual(linkResult, InteractionResult.FAIL, "players must not link to an operator-owned drone");
            helper.assertTrue(drone.isAutonomous(), "hijack attempt must preserve autonomous control");
            helper.assertTrue(drone.isArmed(), "rejected player input must not disarm the operator drone");
            helper.assertFalse(drone.isPilotedBy(target), "hostile operator drone must not accept a player pilot");
            helper.assertValueEqual(
                    drone.operatorUuidForTesting(),
                    operator.getUUID(),
                    "hijack attempt must not replace the operator relationship"
            );
            target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });

        helper.runAtTickTime(15, () -> {
            helper.assertValueEqual(
                    operator.operatorMode(),
                    DroneOperatorEntity.OperatorMode.LOCKING,
                    "visible survival player must enter the deliberate lock phase"
            );
            helper.assertValueEqual(
                    operator.candidateUuidForTesting(),
                    target.getUUID(),
                    "operator must continuously track the same lock candidate"
            );
            helper.assertTrue(
                    operator.lockTicksForTesting() < DroneOperatorEntity.LOCK_TICKS_REQUIRED,
                    "target lock must not complete before the required delay"
            );
            helper.assertTrue(operator.targetUuidForTesting() == null, "candidate must not become a target early");
            helper.assertTrue(
                    deployedDrone[0].autonomousTargetUuidForTesting() == null,
                    "drone must continue loitering until lock completes"
            );
        });

        helper.runAtTickTime(34, () -> {
            DroneEntity drone = controlledDrone(helper, operator);
            helper.assertTrue(drone == deployedDrone[0], "lock must be issued to the originally deployed drone");
            helper.assertValueEqual(
                    operator.operatorMode(),
                    DroneOperatorEntity.OperatorMode.ATTACKING,
                    "continuous visibility must complete target lock"
            );
            helper.assertValueEqual(operator.targetUuidForTesting(), target.getUUID(), "operator must lock the survival player");
            helper.assertValueEqual(
                    drone.autonomousTargetUuidForTesting(),
                    target.getUUID(),
                    "locked target must be transmitted to the drone"
            );
        });

        helper.runAtTickTime(40, () -> target.snapTo(
                targetPosition.x + 3.0,
                targetPosition.y,
                targetPosition.z,
                target.getYRot(),
                target.getXRot()
        ));

        helper.runAtTickTime(41, () -> {
            DroneEntity drone = controlledDrone(helper, operator);
            helper.assertValueEqual(
                    drone.autonomousTargetUuidForTesting(),
                    target.getUUID(),
                    "drone must retain lock after the target changes position"
            );
            distanceAtLock[0] = drone.position().distanceTo(target.position());
            minimumPursuitDistance[0] = distanceAtLock[0];
        });

        helper.onEachTick(() -> {
            if (pursuitVerified[0] || distanceAtLock[0] <= 0.0 || !operator.isAlive()) {
                return;
            }
            DroneEntity drone = deployedDrone[0];
            Vec3 horizontalTargetDirection = target.position().subtract(drone.position()).multiply(1.0, 0.0, 1.0);
            if (!drone.isAlive() || horizontalTargetDirection.lengthSqr() <= EPSILON) {
                return;
            }
            double closingVelocity = drone.getDeltaMovement().multiply(1.0, 0.0, 1.0)
                    .dot(horizontalTargetDirection.normalize());
            double currentDistance = drone.position().distanceTo(target.position());
            minimumPursuitDistance[0] = Math.min(minimumPursuitDistance[0], currentDistance);
            maximumPursuitSpeed[0] = Math.max(maximumPursuitSpeed[0], drone.flightSpeedMetersPerSecond());
            consecutiveClosingTicks[0] = closingVelocity > 0.003 ? consecutiveClosingTicks[0] + 1 : 0;
            switch (drone.autonomousPhase()) {
                case PURSUE -> {
                    stalkTicks[0]++;
                    maximumStalkSpeed[0] = Math.max(maximumStalkSpeed[0], drone.flightSpeedMetersPerSecond());
                    minimumStalkAltitudeMargin[0] = Math.min(
                            minimumStalkAltitudeMargin[0],
                            drone.getY() - target.getY()
                    );
                    minimumStalkClearance[0] = Math.min(minimumStalkClearance[0], drone.heightAboveTerrain());
                }
                case CHARGE -> {
                    chargeTicks[0]++;
                    maximumChargeSpeed[0] = Math.max(maximumChargeSpeed[0], drone.flightSpeedMetersPerSecond());
                }
                default -> {
                }
            }
            if (!closingVerified[0]
                    && consecutiveClosingTicks[0] >= 6
                    && minimumPursuitDistance[0] < distanceAtLock[0] - 1.0) {
                closingVerified[0] = true;
            }
            // Hold the verdict until the drone has both stalked and flown its
            // dive most of the way in. Judging the charge a few ticks after
            // commit would only catch the nose-over, before it has built speed.
            boolean diveMatured = chargeTicks[0] >= 12
                    && (currentDistance <= 7.0 || chargeTicks[0] >= 40);
            if (!closingVerified[0] || stalkTicks[0] < 10 || !diveMatured) {
                return;
            }

            pursuitVerified[0] = true;
            helper.assertTrue(drone.isArmed(), "hunter drone must remain armed during pursuit");
            helper.assertTrue(
                    maximumPursuitSpeed[0] <= 30.0,
                    "autonomous pursuit must respect its realistic speed envelope; max=" + maximumPursuitSpeed[0]
            );
            helper.assertTrue(
                    minimumStalkAltitudeMargin[0] >= 3.0,
                    "a stalking drone must chase from above its target, not at its level; minimum margin="
                            + minimumStalkAltitudeMargin[0]
            );
            helper.assertTrue(
                    minimumStalkClearance[0] >= DroneEntity.CRUISE_GROUND_CLEARANCE - 2.0,
                    "a stalking drone must not hug the terrain; minimum clearance=" + minimumStalkClearance[0]
            );
            helper.assertTrue(
                    maximumChargeSpeed[0] >= maximumStalkSpeed[0] + 1.5,
                    "a committed charge must be measurably faster than the stalk; stalk="
                            + maximumStalkSpeed[0] + ", charge=" + maximumChargeSpeed[0]
            );

            disconnectTestPlayer(helper, target);
            helper.hurt(operator, helper.getLevel().damageSources().generic(), 1000.0F);
            helper.runAfterDelay(2, () -> {
                helper.assertFalse(operator.isAlive(), "killed operator must remain dead");
                helper.assertFalse(drone.isArmed(), "operator death must immediately disarm its drone");
                helper.assertFalse(drone.isAutonomous(), "operator death must clear autonomous control ownership");
                helper.assertTrue(drone.operatorUuidForTesting() == null, "dead operator UUID must be cleared from the drone");
                helper.assertTrue(drone.autonomousTargetUuidForTesting() == null, "operator death must clear the attack target");
                helper.assertValueEqual(
                        drone.autonomousPhase(),
                        DroneEntity.AutonomousPhase.LOITER,
                        "losing the operator must reset the hunting state machine"
                );
                helper.succeed();
            });
        });

        helper.runAtTickTime(380, () -> {
            DroneEntity drone = deployedDrone[0];
            throw helper.assertionException(
                    "pursuit profile never completed: closing=" + closingVerified[0]
                            + ", stalkTicks=" + stalkTicks[0]
                            + ", chargeTicks=" + chargeTicks[0]
                            + ", phase=" + (drone == null ? null : drone.autonomousPhase())
                            + ", mode=" + operator.operatorMode()
                            + ", droneAlive=" + (drone != null && drone.isAlive())
                            + ", distance=" + (drone == null ? -1.0 : drone.distanceTo(target))
            );
        });
    }

    public static void operatorAttackImpact(GameTestHelper helper) {
        forceChunksAround(helper, helper.absoluteVec(new Vec3(0.5, 5.0, 0.5)), 2);
        DroneOperatorEntity operator = helper.spawn(
                DroneMod.DRONE_OPERATOR_ENTITY.get(),
                new Vec3(0.5, 5.0, 0.5)
        );
        ServerPlayer target = makeConnectedSurvivalPlayer(helper);
        helper.runBeforeTestEnd(() -> disconnectTestPlayer(helper, target));
        Vec3 targetPosition = helper.absoluteVec(new Vec3(0.5, 5.0, 10.5));
        target.snapTo(targetPosition.x, targetPosition.y, targetPosition.z);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        float initialHealth = target.getHealth();
        DroneEntity[] attackDrone = new DroneEntity[1];

        helper.runAtTickTime(3, () -> attackDrone[0] = controlledDrone(helper, operator));
        helper.runAtTickTime(34, () -> {
            helper.assertValueEqual(
                    operator.operatorMode(),
                    DroneOperatorEntity.OperatorMode.ATTACKING,
                    "operator must establish a lock before an attack can occur"
            );
            helper.assertValueEqual(
                    attackDrone[0].autonomousTargetUuidForTesting(),
                    target.getUUID(),
                    "attack drone must be assigned the locked player"
            );
        });

        helper.onEachTick(() -> {
            if (attackDrone[0] == null || target.getHealth() >= initialHealth) {
                return;
            }
            helper.assertFalse(attackDrone[0].isAlive(), "armed impact must consume the attacking drone");
            helper.assertTrue(
                    operator.controlledDroneUuidForTesting() == null,
                    "impact destruction must notify the operator"
            );
            disconnectTestPlayer(helper, target);
            helper.succeed();
        });

        helper.runAtTickTime(380, () -> {
            DroneEntity drone = attackDrone[0];
            throw helper.assertionException(
                    "attack did not reach the player: mode=" + operator.operatorMode()
                            + ", operatorTarget=" + operator.targetUuidForTesting()
                            + ", phase=" + (drone == null ? null : drone.autonomousPhase())
                            + ", misses=" + (drone == null ? -1 : drone.autonomousMissCount())
                            + ", droneAlive=" + (drone != null && drone.isAlive())
                            + ", dronePosition=" + (drone == null ? null : drone.position())
                            + ", targetPosition=" + target.position()
                            + ", velocity=" + (drone == null ? null : drone.getDeltaMovement())
                            + ", distance=" + (drone == null ? -1.0 : drone.distanceTo(target))
                            + ", targetHealth=" + target.getHealth()
            );
        });
    }

    /**
     * A drone whose diving strike misses must not simply give up or grind along
     * the ground: it has to climb away, swing around to a fresh bearing, and
     * mount a second attack run.
     */
    public static void operatorMissAndReengage(GameTestHelper helper) {
        forceChunksAround(helper, helper.absoluteVec(new Vec3(0.5, 5.0, 0.5)), 4);
        DroneOperatorEntity operator = helper.spawn(
                DroneMod.DRONE_OPERATOR_ENTITY.get(),
                new Vec3(0.5, 5.0, 0.5)
        );
        ServerPlayer target = makeConnectedSurvivalPlayer(helper);
        helper.runBeforeTestEnd(() -> disconnectTestPlayer(helper, target));
        Vec3 targetPosition = helper.absoluteVec(new Vec3(0.5, 5.0, 26.5));
        target.snapTo(targetPosition.x, targetPosition.y, targetPosition.z);
        target.setNoGravity(true);
        target.setDeltaMovement(Vec3.ZERO);
        target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        // Survive a grazing hit so the test measures the re-attack rather than
        // ending the moment the first pass clips the player.
        var maxHealth = target.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealth != null) {
            maxHealth.setBaseValue(1024.0);
            target.setHealth(1024.0F);
        }

        DroneEntity[] hunter = new DroneEntity[1];
        boolean[] dodged = new boolean[1];
        boolean[] verified = new boolean[1];
        double[] altitudeAtDodge = new double[1];
        double[] peakBreakOffAltitude = {Double.NEGATIVE_INFINITY};
        double[] maximumBreakOffRange = new double[1];
        double[] minimumBreakOffClearance = {Double.POSITIVE_INFINITY};
        int[] breakOffTicks = new int[1];
        int[] firstChargeTicks = new int[1];

        helper.runAtTickTime(3, () -> hunter[0] = controlledDrone(helper, operator));
        helper.runAtTickTime(34, () -> helper.assertValueEqual(
                operator.operatorMode(),
                DroneOperatorEntity.OperatorMode.ATTACKING,
                "operator must lock the visible survival player before the strike"
        ));

        helper.onEachTick(() -> {
            DroneEntity drone = hunter[0];
            if (verified[0] || drone == null || !drone.isAlive() || !operator.isAlive()) {
                return;
            }
            if (!dodged[0]) {
                if (drone.autonomousPhase() != DroneEntity.AutonomousPhase.CHARGE) {
                    return;
                }
                firstChargeTicks[0]++;
                // Sidestep only once the dive is genuinely committed and close,
                // so the drone has to recognise a real miss.
                if (drone.distanceTo(target) > 8.0) {
                    return;
                }
                dodged[0] = true;
                altitudeAtDodge[0] = drone.getY();
                target.snapTo(
                        targetPosition.x + 20.0,
                        targetPosition.y,
                        targetPosition.z,
                        target.getYRot(),
                        target.getXRot()
                );
                target.setDeltaMovement(Vec3.ZERO);
                return;
            }

            if (drone.autonomousPhase() == DroneEntity.AutonomousPhase.BREAK_OFF) {
                breakOffTicks[0]++;
                peakBreakOffAltitude[0] = Math.max(peakBreakOffAltitude[0], drone.getY());
                maximumBreakOffRange[0] = Math.max(maximumBreakOffRange[0], drone.distanceTo(target));
                minimumBreakOffClearance[0] = Math.min(minimumBreakOffClearance[0], drone.heightAboveTerrain());
                return;
            }
            if (drone.autonomousPhase() != DroneEntity.AutonomousPhase.CHARGE || breakOffTicks[0] == 0) {
                return;
            }

            verified[0] = true;
            helper.assertTrue(drone.isArmed(), "a drone setting up a second pass must stay armed");
            helper.assertTrue(drone.isAutonomous(), "re-engaging drone must remain under operator control");
            helper.assertValueEqual(
                    drone.autonomousTargetUuidForTesting(),
                    target.getUUID(),
                    "a missed strike must not drop the target track"
            );
            helper.assertTrue(
                    drone.autonomousMissCount() >= 1,
                    "the dodged strike must be recorded as a miss; count=" + drone.autonomousMissCount()
            );
            helper.assertTrue(
                    peakBreakOffAltitude[0] >= altitudeAtDodge[0] + 4.0,
                    "a missed strike must climb away rather than mush along at strike altitude; dodge="
                            + altitudeAtDodge[0] + ", peak=" + peakBreakOffAltitude[0]
            );
            helper.assertTrue(
                    maximumBreakOffRange[0] >= 20.0,
                    "breaking off must open the range before another run; maximum=" + maximumBreakOffRange[0]
            );
            helper.assertTrue(
                    minimumBreakOffClearance[0] >= DroneEntity.CRUISE_GROUND_CLEARANCE - 2.0,
                    "the re-attack arc must stay well above the terrain; minimum clearance="
                            + minimumBreakOffClearance[0]
            );
            helper.assertTrue(
                    breakOffTicks[0] >= 10,
                    "the re-attack manoeuvre must be a real arc, not a one-tick flicker; ticks=" + breakOffTicks[0]
            );
            helper.succeed();
        });

        helper.runAtTickTime(560, () -> {
            DroneEntity drone = hunter[0];
            throw helper.assertionException(
                    "drone never re-engaged after the miss: dodged=" + dodged[0]
                            + ", firstChargeTicks=" + firstChargeTicks[0]
                            + ", breakOffTicks=" + breakOffTicks[0]
                            + ", phase=" + (drone == null ? null : drone.autonomousPhase())
                            + ", misses=" + (drone == null ? -1 : drone.autonomousMissCount())
                            + ", mode=" + operator.operatorMode()
                            + ", droneAlive=" + (drone != null && drone.isAlive())
                            + ", dronePosition=" + (drone == null ? null : drone.position())
                            + ", targetPosition=" + target.position()
                            + ", distance=" + (drone == null ? -1.0 : drone.distanceTo(target))
            );
        });
    }

    private static DroneControlPayload armedControl(DroneEntity drone, float throttle, byte extraActions) {
        return new DroneControlPayload(
                drone.getId(),
                0.0F,
                0.0F,
                0.0F,
                throttle,
                (byte) (DroneControlPayload.ARMED | extraActions)
        );
    }

    private static void equipLinkedControls(ServerPlayer pilot, DroneEntity drone) {
        ItemStack controller = new ItemStack(DroneMod.FPV_CONTROLLER.get());
        RemoteControlItem.setLinkedDroneId(controller, drone.getUUID());
        RemoteControlItem.setChannel(controller, drone.getVideoChannel());
        pilot.setItemInHand(InteractionHand.MAIN_HAND, controller);

        ItemStack goggles = new ItemStack(DroneMod.FPV_GOGGLES.get());
        FpvGogglesItem.linkDroneOnChannel(goggles, drone.getVideoChannel(), drone.getUUID());
        pilot.setItemSlot(EquipmentSlot.HEAD, goggles);
    }

    private static DroneEntity controlledDrone(GameTestHelper helper, DroneOperatorEntity operator) {
        if (operator.controlledDroneUuidForTesting() == null) {
            throw helper.assertionException("operator has not deployed a drone");
        }
        var entity = helper.getLevel().getEntityInAnyDimension(operator.controlledDroneUuidForTesting());
        if (!(entity instanceof DroneEntity drone)) {
            throw helper.assertionException("operator's controlled drone is missing");
        }
        return drone;
    }

    private static int operatorDroneCount(GameTestHelper helper, DroneOperatorEntity operator) {
        return helper.getLevel().getEntitiesOfClass(
                DroneEntity.class,
                new AABB(operator.position(), operator.position()).inflate(32.0),
                drone -> drone.isOperatedBy(operator)
        ).size();
    }

    private static ServerPlayer makeConnectedSurvivalPlayer(GameTestHelper helper) {
        ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(player.getGameProfile(), false);
        helper.getLevel().getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }

    private static void disconnectTestPlayer(GameTestHelper helper, ServerPlayer player) {
        var playerList = helper.getLevel().getServer().getPlayerList();
        if (playerList.getPlayer(player.getUUID()) != player) {
            return;
        }
        if (player.connection != null) {
            player.connection.disconnect(Component.literal("Drone operator GameTest complete"));
        }
        if (playerList.getPlayer(player.getUUID()) == player) {
            playerList.remove(player);
        }
    }

    private static void forceChunksAround(GameTestHelper helper, Vec3 center, int radius) {
        int centerChunkX = Mth.floor(center.x) >> 4;
        int centerChunkZ = Mth.floor(center.z) >> 4;
        for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                helper.getLevel().setChunkForced(chunkX, chunkZ, true);
            }
        }
        helper.runBeforeTestEnd(() -> {
            for (int chunkX = centerChunkX - radius; chunkX <= centerChunkX + radius; chunkX++) {
                for (int chunkZ = centerChunkZ - radius; chunkZ <= centerChunkZ + radius; chunkZ++) {
                    helper.getLevel().setChunkForced(chunkX, chunkZ, false);
                }
            }
        });
    }

    private static void assertNearlyEqual(
            GameTestHelper helper,
            double actual,
            double expected,
            String message
    ) {
        helper.assertTrue(Math.abs(actual - expected) <= EPSILON, message + ": expected " + expected + ", got " + actual);
    }
}
