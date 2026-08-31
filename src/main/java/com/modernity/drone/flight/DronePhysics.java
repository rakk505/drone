package com.modernity.drone.flight;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Deterministic, server-authoritative flight simulation. One call advances
 * exactly one 20 Hz Minecraft server tick (0.05 seconds).
 *
 * <p>All calculations use SI units. Position maps metres to blocks, while an
 * Entity adapter should convert Minecraft's blocks/tick velocity to and from
 * metres/second by multiplying or dividing by 20.</p>
 */
public final class DronePhysics {
    public static final double STEP_SECONDS = 0.05;
    public static final double GRAVITY_METERS_PER_SECOND_SQUARED = 9.80665;
    public static final double SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;

    private static final double MAXIMUM_WORLD_COORDINATE = 29_999_984.0;
    private static final double MAXIMUM_PAYLOAD_MASS_KG = 50.0;
    private static final double MAXIMUM_TOTAL_MASS_KG = 100.0;
    private static final double MAXIMUM_ANGULAR_RATE_RADIANS_PER_SECOND = Math.toRadians(2_000.0);
    private static final double MAXIMUM_ACCELERATION_METERS_PER_SECOND_SQUARED = 250.0;

    private static final double MOSQUITO_RATE_TIME_CONSTANT_SECONDS = 0.040;
    private static final double PAYLOAD_RATE_TIME_CONSTANT_SECONDS = 0.080;
    private static final double PAYLOAD_ANGLE_GAIN = 6.0;
    private static final double PAYLOAD_VELOCITY_GAIN = 3.0;
    private static final double PAYLOAD_BRAKE_GAIN = 4.0;
    private static final double PAYLOAD_VERTICAL_GAIN = 3.0;
    private static final double PAYLOAD_MAXIMUM_LEVEL_RATE = Math.toRadians(180.0);
    private static final double PAYLOAD_MAXIMUM_YAW_RATE = Math.toRadians(120.0);
    private static final double PAYLOAD_THROTTLE_DEADBAND = 0.08;
    private static final double AUTONOMOUS_HORIZONTAL_SPEED_METERS_PER_SECOND = 28.0;
    private static final double AUTONOMOUS_CLIMB_SPEED_METERS_PER_SECOND = 8.0;
    private static final double AUTONOMOUS_DESCENT_SPEED_METERS_PER_SECOND = 8.0;
    private static final double AUTONOMOUS_MAXIMUM_TILT_RADIANS = Math.toRadians(55.0);

    private final BetaflightRateProfile mosquitoRates;
    private final double airDensityKgPerCubicMeter;

    public DronePhysics() {
        this(BetaflightRateProfile.MOSQUITO_DEFAULT, SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER);
    }

    public DronePhysics(BetaflightRateProfile mosquitoRates) {
        this(mosquitoRates, SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER);
    }

    public DronePhysics(BetaflightRateProfile mosquitoRates, double airDensityKgPerCubicMeter) {
        this.mosquitoRates = Objects.requireNonNull(mosquitoRates, "mosquitoRates");
        this.airDensityKgPerCubicMeter = FlightMath.clamp(
                FlightMath.finiteOr(airDensityKgPerCubicMeter, SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER),
                0.0,
                2.0
        );
    }

    public FlightStepResult step(FlightState state, FlightControl control) {
        return step(state, control, FlightVector.ZERO);
    }

    /**
     * Advances one fixed server tick. Wind is a world-space velocity in m/s;
     * aerodynamic drag is calculated from velocity relative to that air mass.
     */
    public FlightStepResult step(FlightState state, FlightControl control, FlightVector windMetersPerSecond) {
        return stepInternal(state, control, windMetersPerSecond, false);
    }

    /**
     * Advances a Mosquito airframe with a stabilized autonomous flight
     * controller while preserving its mass, thrust, battery, drag, and safety
     * envelope. Player-piloted Mosquitoes continue to use acro/rate mode.
     */
    public FlightStepResult stepAutonomous(FlightState state, FlightControl control) {
        return stepInternal(state, control, FlightVector.ZERO, true);
    }

    private FlightStepResult stepInternal(
            FlightState state,
            FlightControl control,
            FlightVector windMetersPerSecond,
            boolean autonomousStabilization
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(windMetersPerSecond, "windMetersPerSecond");

        EnumSet<FlightSafetyFlag> flags = EnumSet.noneOf(FlightSafetyFlag.class);
        FlightState safeState = sanitizeState(state, flags);
        FlightVector safeWind = sanitizeWind(windMetersPerSecond, flags);
        DroneKind kind = safeState.kind();
        double massKg = FlightMath.clamp(safeState.totalMassKg(), 0.05, MAXIMUM_TOTAL_MASS_KG);
        if (Double.compare(massKg, safeState.totalMassKg()) != 0) {
            flags.add(FlightSafetyFlag.MASS_CLAMPED);
        }

        ControlSolution controlSolution = kind == DroneKind.MOSQUITO
                ? autonomousStabilization
                        ? solveAutonomousMosquitoControl(safeState, control, massKg, flags)
                        : solveMosquitoControl(safeState, control, massKg, flags)
                : solvePayloadControl(safeState, control, massKg, flags);

        BatteryAndThrust batteryAndThrust = solveBatteryAndThrust(
                kind,
                safeState.battery(),
                control,
                controlSolution,
                flags
        );

        FlightVector bodyUp = controlSolution.attitude().bodyUp();
        FlightVector thrustForce = bodyUp.multiply(batteryAndThrust.thrustNewtons()).finiteOrZero();
        FlightVector relativeAirVelocity = safeState.velocityMetersPerSecond().subtract(safeWind);
        FlightVector dragForce = quadraticDrag(kind, relativeAirVelocity);
        FlightVector gravityForce = new FlightVector(0.0, -massKg * GRAVITY_METERS_PER_SECOND_SQUARED, 0.0);
        FlightVector acceleration = thrustForce
                .add(dragForce)
                .add(gravityForce)
                .multiply(1.0 / massKg);

        if (!acceleration.isFinite()) {
            acceleration = FlightVector.ZERO;
            flags.add(FlightSafetyFlag.FINITE_VALUE_RECOVERED);
        }
        FlightVector limitedAcceleration = acceleration.clampMagnitude(MAXIMUM_ACCELERATION_METERS_PER_SECOND_SQUARED);
        if (!limitedAcceleration.equals(acceleration)) {
            acceleration = limitedAcceleration;
            flags.add(FlightSafetyFlag.ACCELERATION_CLAMPED);
        }

        FlightVector nextVelocity = safeState.velocityMetersPerSecond().add(acceleration.multiply(STEP_SECONDS));
        nextVelocity = limitVelocity(kind, control.armed(), nextVelocity, flags);
        FlightVector nextPosition = safeState.positionMeters().add(nextVelocity.multiply(STEP_SECONDS));
        nextPosition = sanitizePosition(nextPosition, flags);

        BatteryState nextBattery = safeState.battery().drainCurrent(
                batteryAndThrust.currentAmps(),
                STEP_SECONDS
        );
        if (safeState.battery().isDepleted() || nextBattery.isDepleted()) {
            flags.add(FlightSafetyFlag.BATTERY_DEPLETED);
        }

        FlightState nextState = new FlightState(
                kind,
                nextPosition,
                nextVelocity,
                controlSolution.attitude(),
                controlSolution.rates(),
                nextBattery,
                safeState.payloadMassKg(),
                FlightMath.incrementSaturated(safeState.simulationTick())
        );

        return new FlightStepResult(
                nextState,
                acceleration,
                thrustForce,
                dragForce,
                massKg,
                batteryAndThrust.currentAmps(),
                batteryAndThrust.loadedVoltage(),
                flags
        );
    }

    private ControlSolution solveAutonomousMosquitoControl(
            FlightState state,
            FlightControl control,
            double massKg,
            EnumSet<FlightSafetyFlag> flags
    ) {
        if (!control.armed()) {
            return solveMosquitoControl(state, control, massKg, flags);
        }

        double yaw = state.attitude().yawRadians();
        FlightVector headingRight = new FlightVector(Math.cos(yaw), 0.0, Math.sin(yaw));
        FlightVector headingForward = new FlightVector(-Math.sin(yaw), 0.0, Math.cos(yaw));
        FlightVector horizontalStick = new FlightVector(control.roll(), 0.0, control.pitch()).clampMagnitude(1.0);
        FlightVector desiredHorizontalVelocity = headingRight.multiply(horizontalStick.x())
                .add(headingForward.multiply(horizontalStick.z()))
                .multiply(AUTONOMOUS_HORIZONTAL_SPEED_METERS_PER_SECOND);
        FlightVector currentHorizontalVelocity = state.velocityMetersPerSecond().horizontal();
        double velocityGain = horizontalStick.horizontalLength() < 1.0E-6 ? 4.5 : 3.8;
        FlightVector desiredHorizontalAcceleration = desiredHorizontalVelocity
                .subtract(currentHorizontalVelocity)
                .multiply(velocityGain);

        double maximumHorizontalAcceleration = GRAVITY_METERS_PER_SECOND_SQUARED
                * Math.tan(AUTONOMOUS_MAXIMUM_TILT_RADIANS);
        desiredHorizontalAcceleration = desiredHorizontalAcceleration.clampMagnitude(maximumHorizontalAcceleration);

        double verticalDemand = control.centeredThrottle(0.03);
        double desiredVerticalSpeed = verticalDemand >= 0.0
                ? verticalDemand * AUTONOMOUS_CLIMB_SPEED_METERS_PER_SECOND
                : verticalDemand * AUTONOMOUS_DESCENT_SPEED_METERS_PER_SECOND;
        double desiredVerticalAcceleration = FlightMath.clamp(
                (desiredVerticalSpeed - state.velocityMetersPerSecond().y()) * 4.0,
                -6.0,
                9.0
        );
        double upwardSpecificForce = Math.max(1.5, GRAVITY_METERS_PER_SECOND_SQUARED + desiredVerticalAcceleration);
        double tiltLimitedAcceleration = upwardSpecificForce * Math.tan(AUTONOMOUS_MAXIMUM_TILT_RADIANS);
        desiredHorizontalAcceleration = desiredHorizontalAcceleration.clampMagnitude(tiltLimitedAcceleration);

        FlightVector desiredUp = new FlightVector(
                desiredHorizontalAcceleration.x(),
                upwardSpecificForce,
                desiredHorizontalAcceleration.z()
        ).normalizedOrZero();
        double localRight = desiredUp.dot(headingRight);
        double localForward = desiredUp.dot(headingForward);
        double targetRoll = Math.asin(FlightMath.clamp(localRight, -1.0, 1.0));
        double targetPitch = Math.atan2(-localForward, Math.max(1.0E-9, desiredUp.y()));
        double rollError = FlightMath.wrapRadians(targetRoll - state.attitude().rollRadians());
        double pitchError = FlightMath.wrapRadians(targetPitch - state.attitude().pitchRadians());
        double shapedYaw = control.yaw() * 0.75 + control.yaw() * control.yaw() * control.yaw() * 0.25;

        FlightRates targetRates = new FlightRates(
                FlightMath.clamp(rollError * 7.0, -Math.toRadians(250.0), Math.toRadians(250.0)),
                FlightMath.clamp(pitchError * 7.0, -Math.toRadians(250.0), Math.toRadians(250.0)),
                shapedYaw * Math.toRadians(180.0)
        );
        double response = STEP_SECONDS / (STEP_SECONDS + 0.055);
        FlightRates nextRates = limitAngularRates(state.angularRates().interpolate(targetRates, response), flags);
        FlightRates midpointRates = state.angularRates().add(nextRates).multiply(0.5);
        FlightAttitude nextAttitude = state.attitude().integrate(midpointRates, STEP_SECONDS);

        double actualUpwardProjection = Math.max(0.22, nextAttitude.bodyUp().y());
        double requestedThrust = massKg * upwardSpecificForce / actualUpwardProjection;
        double motorFraction = requestedThrust / state.kind().nominalMaximumThrustNewtons();
        return new ControlSolution(nextAttitude, nextRates, requestedThrust, motorFraction, true);
    }

    private ControlSolution solveMosquitoControl(
            FlightState state,
            FlightControl control,
            double massKg,
            EnumSet<FlightSafetyFlag> flags
    ) {
        FlightRates targetRates = control.armed() ? mosquitoRates.targetRates(control) : FlightRates.ZERO;
        double massResponseScale = Math.sqrt(state.kind().referenceMassKg() / massKg);
        double response = STEP_SECONDS / (STEP_SECONDS + MOSQUITO_RATE_TIME_CONSTANT_SECONDS);
        response = FlightMath.clamp(response * massResponseScale, 0.0, 1.0);

        FlightRates nextRates = state.angularRates().interpolate(targetRates, response);
        nextRates = limitAngularRates(nextRates, flags);
        FlightRates midpointRates = state.angularRates().add(nextRates).multiply(0.5);
        FlightAttitude nextAttitude = state.attitude().integrate(midpointRates, STEP_SECONDS);

        double motorFraction = control.armed() ? Math.max(0.035, control.throttle()) : 0.0;
        double requestedThrust = state.kind().nominalMaximumThrustNewtons() * motorFraction;
        return new ControlSolution(nextAttitude, nextRates, requestedThrust, motorFraction, false);
    }

    private ControlSolution solvePayloadControl(
            FlightState state,
            FlightControl control,
            double massKg,
            EnumSet<FlightSafetyFlag> flags
    ) {
        if (!control.armed()) {
            double response = STEP_SECONDS / (STEP_SECONDS + PAYLOAD_RATE_TIME_CONSTANT_SECONDS);
            FlightRates nextRates = limitAngularRates(
                    state.angularRates().interpolate(FlightRates.ZERO, response),
                    flags
            );
            FlightRates midpointRates = state.angularRates().add(nextRates).multiply(0.5);
            return new ControlSolution(
                    state.attitude().integrate(midpointRates, STEP_SECONDS),
                    nextRates,
                    0.0,
                    0.0,
                    true
            );
        }

        DroneKind kind = state.kind();
        double yaw = state.attitude().yawRadians();
        FlightVector headingRight = new FlightVector(Math.cos(yaw), 0.0, Math.sin(yaw));
        FlightVector headingForward = new FlightVector(-Math.sin(yaw), 0.0, Math.cos(yaw));
        FlightVector horizontalStick = new FlightVector(control.roll(), 0.0, control.pitch()).clampMagnitude(1.0);
        FlightVector desiredHorizontalVelocity = headingRight.multiply(horizontalStick.x())
                .add(headingForward.multiply(horizontalStick.z()))
                .multiply(kind.controlledHorizontalSpeedMetersPerSecond());

        FlightVector currentHorizontalVelocity = state.velocityMetersPerSecond().horizontal();
        double velocityGain = horizontalStick.horizontalLength() < 1.0e-6
                ? PAYLOAD_BRAKE_GAIN
                : PAYLOAD_VELOCITY_GAIN;
        FlightVector desiredHorizontalAcceleration = desiredHorizontalVelocity
                .subtract(currentHorizontalVelocity)
                .multiply(velocityGain);

        double maximumHorizontalAcceleration = GRAVITY_METERS_PER_SECOND_SQUARED
                * Math.tan(kind.maximumTiltRadians());
        desiredHorizontalAcceleration = desiredHorizontalAcceleration.clampMagnitude(maximumHorizontalAcceleration);

        double verticalDemand = control.centeredThrottle(PAYLOAD_THROTTLE_DEADBAND);
        double desiredVerticalSpeed = verticalDemand >= 0.0
                ? verticalDemand * kind.maximumClimbSpeedMetersPerSecond()
                : verticalDemand * kind.maximumDescentSpeedMetersPerSecond();
        double desiredVerticalAcceleration = (desiredVerticalSpeed - state.velocityMetersPerSecond().y())
                * PAYLOAD_VERTICAL_GAIN;
        desiredVerticalAcceleration = FlightMath.clamp(desiredVerticalAcceleration, -4.0, 5.0);
        double upwardSpecificForce = Math.max(0.5, GRAVITY_METERS_PER_SECOND_SQUARED + desiredVerticalAcceleration);

        double tiltLimitedHorizontalAcceleration = upwardSpecificForce * Math.tan(kind.maximumTiltRadians());
        desiredHorizontalAcceleration = desiredHorizontalAcceleration.clampMagnitude(tiltLimitedHorizontalAcceleration);
        FlightVector desiredUp = new FlightVector(
                desiredHorizontalAcceleration.x(),
                upwardSpecificForce,
                desiredHorizontalAcceleration.z()
        ).normalizedOrZero();

        double localRight = desiredUp.dot(headingRight);
        double localForward = desiredUp.dot(headingForward);
        double targetRoll = Math.asin(FlightMath.clamp(localRight, -1.0, 1.0));
        double targetPitch = Math.atan2(-localForward, Math.max(1.0e-9, desiredUp.y()));
        double rollError = FlightMath.wrapRadians(targetRoll - state.attitude().rollRadians());
        double pitchError = FlightMath.wrapRadians(targetPitch - state.attitude().pitchRadians());
        double shapedYaw = control.yaw() * 0.8 + control.yaw() * control.yaw() * control.yaw() * 0.2;

        FlightRates targetRates = new FlightRates(
                FlightMath.clamp(
                        rollError * PAYLOAD_ANGLE_GAIN,
                        -PAYLOAD_MAXIMUM_LEVEL_RATE,
                        PAYLOAD_MAXIMUM_LEVEL_RATE
                ),
                FlightMath.clamp(
                        pitchError * PAYLOAD_ANGLE_GAIN,
                        -PAYLOAD_MAXIMUM_LEVEL_RATE,
                        PAYLOAD_MAXIMUM_LEVEL_RATE
                ),
                shapedYaw * PAYLOAD_MAXIMUM_YAW_RATE
        );
        double response = STEP_SECONDS / (STEP_SECONDS + PAYLOAD_RATE_TIME_CONSTANT_SECONDS);
        FlightRates nextRates = limitAngularRates(state.angularRates().interpolate(targetRates, response), flags);
        FlightRates midpointRates = state.angularRates().add(nextRates).multiply(0.5);
        FlightAttitude nextAttitude = state.attitude().integrate(midpointRates, STEP_SECONDS);

        double actualUpwardProjection = Math.max(0.25, nextAttitude.bodyUp().y());
        double requestedThrust = massKg * upwardSpecificForce / actualUpwardProjection;
        double motorFraction = requestedThrust / kind.nominalMaximumThrustNewtons();
        return new ControlSolution(nextAttitude, nextRates, requestedThrust, motorFraction, true);
    }

    private BatteryAndThrust solveBatteryAndThrust(
            DroneKind kind,
            BatteryState battery,
            FlightControl control,
            ControlSolution solution,
            EnumSet<FlightSafetyFlag> flags
    ) {
        if (battery.isDepleted()) {
            return new BatteryAndThrust(0.0, 0.0, 0.0);
        }

        double motorFraction = FlightMath.clamp(solution.motorFraction(), 0.0, 1.0);
        double standbyCurrent = control.armed() ? kind.avionicsCurrentAmps() : 0.05;
        double motorCurrent = control.armed()
                ? kind.maximumMotorCurrentAmps() * Math.pow(motorFraction, 1.5)
                : 0.0;
        double current = standbyCurrent + motorCurrent;
        double loadedVoltage = battery.voltageUnderLoad(current);
        double voltageRatio = FlightMath.clamp(loadedVoltage / battery.nominalVoltage(), 0.0, 1.15);
        double maximumThrust = kind.nominalMaximumThrustNewtons() * voltageRatio * voltageRatio;

        // Below 3.0 V/cell, fade power out rather than allowing NaNs or an
        // infinitely sagged pack to continue supplying nominal thrust.
        if (loadedVoltage < battery.cutoffVoltage()) {
            maximumThrust *= FlightMath.clamp(loadedVoltage / battery.cutoffVoltage(), 0.0, 1.0);
        }

        double actualThrust;
        if (!control.armed()) {
            actualThrust = 0.0;
        } else if (solution.absoluteThrustRequest()) {
            actualThrust = Math.min(solution.requestedThrustNewtons(), maximumThrust);
            if (actualThrust + 1.0e-9 < solution.requestedThrustNewtons()) {
                flags.add(FlightSafetyFlag.THRUST_CLAMPED);
            }
        } else {
            actualThrust = motorFraction * maximumThrust;
        }

        if (!Double.isFinite(actualThrust) || !Double.isFinite(current) || !Double.isFinite(loadedVoltage)) {
            flags.add(FlightSafetyFlag.FINITE_VALUE_RECOVERED);
            return new BatteryAndThrust(0.0, 0.0, 0.0);
        }
        return new BatteryAndThrust(Math.max(0.0, actualThrust), current, loadedVoltage);
    }

    private FlightVector quadraticDrag(DroneKind kind, FlightVector relativeAirVelocity) {
        double speed = relativeAirVelocity.length();
        if (!Double.isFinite(speed) || speed < 1.0e-9 || airDensityKgPerCubicMeter <= 0.0) {
            return FlightVector.ZERO;
        }
        double coefficient = -0.5
                * airDensityKgPerCubicMeter
                * kind.dragCoefficient()
                * kind.referenceAreaSquareMeters()
                * speed;
        return relativeAirVelocity.multiply(coefficient).finiteOrZero();
    }

    private FlightState sanitizeState(FlightState state, EnumSet<FlightSafetyFlag> flags) {
        FlightVector position = sanitizePosition(state.positionMeters(), flags);

        FlightVector velocity = state.velocityMetersPerSecond();
        if (!velocity.isFinite()) {
            velocity = FlightVector.ZERO;
            flags.add(FlightSafetyFlag.FINITE_VALUE_RECOVERED);
        }
        FlightVector limitedVelocity = velocity.clampMagnitude(state.kind().safetySpeedLimitMetersPerSecond());
        if (!limitedVelocity.equals(velocity)) {
            velocity = limitedVelocity;
            flags.add(FlightSafetyFlag.SPEED_CLAMPED);
        }

        FlightRates rates = state.angularRates();
        if (!rates.isFinite()) {
            rates = FlightRates.ZERO;
            flags.add(FlightSafetyFlag.FINITE_VALUE_RECOVERED);
        }
        rates = limitAngularRates(rates, flags);

        double payloadMass = state.payloadMassKg();
        if (!Double.isFinite(payloadMass)) {
            payloadMass = 0.0;
            flags.add(FlightSafetyFlag.FINITE_VALUE_RECOVERED);
        }
        double limitedPayloadMass = FlightMath.clamp(payloadMass, 0.0, MAXIMUM_PAYLOAD_MASS_KG);
        if (Double.compare(limitedPayloadMass, payloadMass) != 0) {
            payloadMass = limitedPayloadMass;
            flags.add(FlightSafetyFlag.MASS_CLAMPED);
        }

        long simulationTick = state.simulationTick();
        if (simulationTick < 0L) {
            simulationTick = 0L;
            flags.add(FlightSafetyFlag.FINITE_VALUE_RECOVERED);
        }
        return new FlightState(
                state.kind(),
                position,
                velocity,
                state.attitude(),
                rates,
                state.battery(),
                payloadMass,
                simulationTick
        );
    }

    private FlightVector sanitizeWind(FlightVector wind, EnumSet<FlightSafetyFlag> flags) {
        if (!wind.isFinite()) {
            flags.add(FlightSafetyFlag.FINITE_VALUE_RECOVERED);
            return FlightVector.ZERO;
        }
        FlightVector limited = wind.clampMagnitude(100.0);
        if (!limited.equals(wind)) {
            flags.add(FlightSafetyFlag.SPEED_CLAMPED);
        }
        return limited;
    }

    private FlightVector sanitizePosition(FlightVector position, EnumSet<FlightSafetyFlag> flags) {
        if (!position.isFinite()) {
            flags.add(FlightSafetyFlag.FINITE_VALUE_RECOVERED);
            return FlightVector.ZERO;
        }
        FlightVector limited = new FlightVector(
                FlightMath.clamp(position.x(), -MAXIMUM_WORLD_COORDINATE, MAXIMUM_WORLD_COORDINATE),
                FlightMath.clamp(position.y(), -MAXIMUM_WORLD_COORDINATE, MAXIMUM_WORLD_COORDINATE),
                FlightMath.clamp(position.z(), -MAXIMUM_WORLD_COORDINATE, MAXIMUM_WORLD_COORDINATE)
        );
        if (!limited.equals(position)) {
            flags.add(FlightSafetyFlag.POSITION_CLAMPED);
        }
        return limited;
    }

    private FlightRates limitAngularRates(FlightRates rates, EnumSet<FlightSafetyFlag> flags) {
        FlightRates limited = rates.clamp(MAXIMUM_ANGULAR_RATE_RADIANS_PER_SECOND);
        if (!limited.equals(rates)) {
            flags.add(FlightSafetyFlag.ANGULAR_RATE_CLAMPED);
        }
        return limited;
    }

    private FlightVector limitVelocity(
            DroneKind kind,
            boolean armed,
            FlightVector velocity,
            EnumSet<FlightSafetyFlag> flags
    ) {
        if (!velocity.isFinite()) {
            flags.add(FlightSafetyFlag.FINITE_VALUE_RECOVERED);
            return FlightVector.ZERO;
        }

        FlightVector limited = velocity;
        if (kind == DroneKind.PAYLOAD && armed) {
            FlightVector horizontal = limited.horizontal()
                    .clampMagnitude(kind.controlledHorizontalSpeedMetersPerSecond());
            double vertical = FlightMath.clamp(
                    limited.y(),
                    -kind.maximumDescentSpeedMetersPerSecond(),
                    kind.maximumClimbSpeedMetersPerSecond()
            );
            limited = new FlightVector(horizontal.x(), vertical, horizontal.z());
        }
        limited = limited.clampMagnitude(kind.safetySpeedLimitMetersPerSecond());
        if (!limited.equals(velocity)) {
            flags.add(FlightSafetyFlag.SPEED_CLAMPED);
        }
        return limited;
    }

    private record ControlSolution(
            FlightAttitude attitude,
            FlightRates rates,
            double requestedThrustNewtons,
            double motorFraction,
            boolean absoluteThrustRequest
    ) {
    }

    private record BatteryAndThrust(double thrustNewtons, double currentAmps, double loadedVoltage) {
    }
}
