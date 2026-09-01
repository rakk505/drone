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
    /** Matches the original simulator's maximum 1/128 second integration step. */
    public static final double MAX_SUBSTEP_SECONDS = 1.0 / 128.0;
    public static final double GRAVITY_METERS_PER_SECOND_SQUARED = 9.80665;
    public static final double SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER = 1.225;

    private static final double MAXIMUM_WORLD_COORDINATE = 29_999_984.0;
    private static final double MAXIMUM_PAYLOAD_MASS_KG = 50.0;
    private static final double MAXIMUM_TOTAL_MASS_KG = 100.0;
    // The validated V1.1.4 rate envelope can reach about 211k deg/s at the
    // deliberately extreme 2.55/1.0 settings. Keep a finite-value guard above
    // that envelope without changing any valid configured rate.
    private static final double MAXIMUM_ANGULAR_RATE_RADIANS_PER_SECOND = Math.toRadians(250_000.0);
    private static final double MAXIMUM_ACCELERATION_METERS_PER_SECOND_SQUARED = 2_000.0;

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
    private final double motorKv;
    private final double propDiameterInches;
    private final double propPitchInches;
    private final double configuredDragCoefficient;
    private final double thrustMultiplier;
    private final boolean flightMode3d;

    public DronePhysics() {
        this(DroneFlightConfig.DEFAULT, SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER);
    }

    public DronePhysics(BetaflightRateProfile mosquitoRates) {
        this(mosquitoRates, SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER,
                1300.0, 9.0, 4.5, 1.1, 1.0, false);
    }

    public DronePhysics(BetaflightRateProfile mosquitoRates, double airDensityKgPerCubicMeter) {
        this(mosquitoRates, airDensityKgPerCubicMeter, 1300.0, 9.0, 4.5, 1.1, 1.0, false);
    }

    public DronePhysics(DroneFlightConfig config) {
        this(config, SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER);
    }

    public DronePhysics(DroneFlightConfig config, double airDensityKgPerCubicMeter) {
        this(config.rateProfile(), airDensityKgPerCubicMeter,
                config.motorKv(), config.propDiameterInches(), config.propPitchInches(),
                config.dragCoefficient(), config.thrustMultiplier(), config.flightMode3d());
    }

    private DronePhysics(
            BetaflightRateProfile mosquitoRates,
            double airDensityKgPerCubicMeter,
            double motorKv,
            double propDiameterInches,
            double propPitchInches,
            double configuredDragCoefficient,
            double thrustMultiplier,
            boolean flightMode3d
    ) {
        this.mosquitoRates = Objects.requireNonNull(mosquitoRates, "mosquitoRates");
        this.airDensityKgPerCubicMeter = FlightMath.clamp(
                FlightMath.finiteOr(airDensityKgPerCubicMeter, SEA_LEVEL_AIR_DENSITY_KG_PER_CUBIC_METER),
                0.0,
                2.0
        );
        this.motorKv = FlightMath.clamp(motorKv, 800.0, 3000.0);
        this.propDiameterInches = FlightMath.clamp(propDiameterInches, 3.0, 12.0);
        this.propPitchInches = FlightMath.clamp(propPitchInches, 2.0, 8.0);
        this.configuredDragCoefficient = FlightMath.clamp(configuredDragCoefficient, 0.5, 2.0);
        this.thrustMultiplier = FlightMath.clamp(thrustMultiplier, 0.5, 2.0);
        this.flightMode3d = flightMode3d;
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
        FlightState substepState = state;
        FlightStepResult latest = null;
        EnumSet<FlightSafetyFlag> combinedFlags = EnumSet.noneOf(FlightSafetyFlag.class);
        ControlSolution manualMosquitoFrame = null;
        if (!autonomousStabilization && state.kind() == DroneKind.MOSQUITO) {
            FlightState safeFrameState = sanitizeState(state, combinedFlags);
            double frameMassKg = FlightMath.clamp(
                    safeFrameState.totalMassKg(),
                    0.05,
                    MAXIMUM_TOTAL_MASS_KG
            );
            manualMosquitoFrame = solveMosquitoControl(
                    safeFrameState,
                    control,
                    frameMassKg,
                    combinedFlags,
                    STEP_SECONDS
            );
        }
        double processed = 0.0;
        while (STEP_SECONDS - processed > 1.0e-9) {
            double dt = Math.min(MAX_SUBSTEP_SECONDS, STEP_SECONDS - processed);
            latest = stepSubstep(
                    substepState,
                    control,
                    windMetersPerSecond,
                    autonomousStabilization,
                    manualMosquitoFrame,
                    dt
            );
            combinedFlags.addAll(latest.safetyFlags());
            substepState = latest.nextState();
            processed += dt;
        }
        if (latest == null) throw new IllegalStateException("physics tick produced no substeps");
        FlightAttitude finalAttitude = manualMosquitoFrame == null
                ? substepState.attitude()
                : manualMosquitoFrame.attitude();
        FlightRates finalRates = manualMosquitoFrame == null
                ? substepState.angularRates()
                : manualMosquitoFrame.rates();
        FlightState advanced = new FlightState(
                substepState.kind(), substepState.positionMeters(), substepState.velocityMetersPerSecond(),
                finalAttitude, finalRates, substepState.battery(),
                substepState.payloadMassKg(), FlightMath.incrementSaturated(state.simulationTick())
        );
        return new FlightStepResult(
                advanced,
                latest.accelerationMetersPerSecondSquared(),
                latest.thrustForceNewtons(),
                latest.aerodynamicDragForceNewtons(),
                latest.totalMassKg(),
                latest.motorCurrentAmps(),
                latest.loadedBatteryVoltage(),
                combinedFlags
        );
    }

    private FlightStepResult stepSubstep(
            FlightState state,
            FlightControl control,
            FlightVector windMetersPerSecond,
            boolean autonomousStabilization,
            ControlSolution manualMosquitoFrame,
            double stepSeconds
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

        ControlSolution controlSolution;
        if (manualMosquitoFrame != null) {
            // DefaultPhysicsCore integrates translation against the attitude at
            // the start of the frame and rotates the craft once afterward.
            controlSolution = new ControlSolution(
                    safeState.attitude(),
                    safeState.angularRates(),
                    manualMosquitoFrame.requestedThrustNewtons(),
                    manualMosquitoFrame.motorFraction(),
                    manualMosquitoFrame.absoluteThrustRequest(),
                    manualMosquitoFrame.reverseThrust()
            );
        } else {
            controlSolution = kind == DroneKind.MOSQUITO
                    ? autonomousStabilization
                            ? solveAutonomousMosquitoControl(safeState, control, massKg, flags, stepSeconds)
                            : solveMosquitoControl(safeState, control, massKg, flags, stepSeconds)
                    : solvePayloadControl(safeState, control, massKg, flags, stepSeconds);
        }

        BatteryAndThrust batteryAndThrust = solveBatteryAndThrust(
                kind,
                safeState.battery(),
                control,
                controlSolution,
                massKg,
                flags
        );

        FlightVector bodyUp = controlSolution.attitude().bodyUp();
        if (controlSolution.reverseThrust()) bodyUp = bodyUp.multiply(-1.0);
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

        FlightVector nextVelocity = safeState.velocityMetersPerSecond().add(acceleration.multiply(stepSeconds));
        nextVelocity = limitVelocity(kind, control.armed(), nextVelocity, flags);
        FlightVector nextPosition = safeState.positionMeters().add(nextVelocity.multiply(stepSeconds));
        nextPosition = sanitizePosition(nextPosition, flags);

        BatteryState nextBattery = safeState.battery().drainCurrent(
                batteryAndThrust.currentAmps(),
                stepSeconds
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
                safeState.simulationTick()
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
            EnumSet<FlightSafetyFlag> flags,
            double stepSeconds
    ) {
        if (!control.armed()) {
            return solveMosquitoControl(state, control, massKg, flags, stepSeconds);
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
        double targetRoll = -Math.asin(FlightMath.clamp(localRight, -1.0, 1.0));
        double targetPitch = Math.atan2(-localForward, Math.max(1.0E-9, desiredUp.y()));
        double rollError = FlightMath.wrapRadians(targetRoll - state.attitude().rollRadians());
        double pitchError = FlightMath.wrapRadians(targetPitch - state.attitude().pitchRadians());
        double shapedYaw = control.yaw() * 0.75 + control.yaw() * control.yaw() * control.yaw() * 0.25;

        FlightRates targetRates = new FlightRates(
                FlightMath.clamp(rollError * 7.0, -Math.toRadians(250.0), Math.toRadians(250.0)),
                FlightMath.clamp(pitchError * 7.0, -Math.toRadians(250.0), Math.toRadians(250.0)),
                shapedYaw * Math.toRadians(180.0)
        );
        double response = stepSeconds / (stepSeconds + 0.055);
        FlightRates nextRates = limitAngularRates(state.angularRates().interpolate(targetRates, response), flags);
        FlightRates midpointRates = state.angularRates().add(nextRates).multiply(0.5);
        FlightAttitude nextAttitude = state.attitude().integrate(midpointRates, stepSeconds);

        double actualUpwardProjection = Math.max(0.22, nextAttitude.bodyUp().y());
        double requestedThrust = massKg * upwardSpecificForce / actualUpwardProjection;
        double motorFraction = requestedThrust / nominalMaximumThrustNewtons(state.kind(), state.battery());
        return new ControlSolution(nextAttitude, nextRates, requestedThrust, motorFraction, true, false);
    }

    private ControlSolution solveMosquitoControl(
            FlightState state,
            FlightControl control,
            double massKg,
            EnumSet<FlightSafetyFlag> flags,
            double stepSeconds
    ) {
        // The reference leaves its smoothed rates and attitude untouched while
        // disarmed.  Decaying and integrating them here made a parked or
        // mid-air disarmed craft continue rotating on its own.
        if (!control.armed()) {
            return new ControlSolution(state.attitude(), state.angularRates(), 0.0, 0.0, false, false);
        }

        FlightRates targetRates = mosquitoRates.targetRates(control);
        double massResponseScale = Math.sqrt(1.05 / massKg);
        double response = stepSeconds / (stepSeconds + MOSQUITO_RATE_TIME_CONSTANT_SECONDS);

        FlightRates nextRates = state.angularRates().interpolate(targetRates, response);
        nextRates = limitAngularRates(nextRates, flags);
        // V1.1.4 applies sqrt(1.05 / mass) to angular travel, not to the
        // low-pass response itself.  This preserves the heavier-airframe feel
        // at steady stick rather than eventually reaching the unloaded rate.
        FlightAttitude nextAttitude = state.attitude().integrateReferenceRates(
                nextRates.multiply(massResponseScale),
                stepSeconds
        );

        double throttle = control.throttle();
        boolean reverse = flightMode3d && throttle < 0.5;
        double motorFraction = control.armed()
                ? flightMode3d ? Math.abs(throttle * 2.0 - 1.0) : throttle
                : 0.0;
        double requestedThrust = nominalMaximumThrustNewtons(state.kind(), state.battery()) * motorFraction;
        if (reverse) {
            requestedThrust *= 0.65;
            motorFraction *= 0.65;
        }
        return new ControlSolution(nextAttitude, nextRates, requestedThrust, motorFraction, false, reverse);
    }

    private ControlSolution solvePayloadControl(
            FlightState state,
            FlightControl control,
            double massKg,
            EnumSet<FlightSafetyFlag> flags,
            double stepSeconds
    ) {
        if (!control.armed()) {
            double response = stepSeconds / (stepSeconds + PAYLOAD_RATE_TIME_CONSTANT_SECONDS);
            FlightRates nextRates = limitAngularRates(
                    state.angularRates().interpolate(FlightRates.ZERO, response),
                    flags
            );
            FlightRates midpointRates = state.angularRates().add(nextRates).multiply(0.5);
            return new ControlSolution(
                    state.attitude().integrate(midpointRates, stepSeconds),
                    nextRates,
                    0.0,
                    0.0,
                    true,
                    false
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
        double targetRoll = -Math.asin(FlightMath.clamp(localRight, -1.0, 1.0));
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
        double response = stepSeconds / (stepSeconds + PAYLOAD_RATE_TIME_CONSTANT_SECONDS);
        FlightRates nextRates = limitAngularRates(state.angularRates().interpolate(targetRates, response), flags);
        FlightRates midpointRates = state.angularRates().add(nextRates).multiply(0.5);
        FlightAttitude nextAttitude = state.attitude().integrate(midpointRates, stepSeconds);

        double actualUpwardProjection = Math.max(0.25, nextAttitude.bodyUp().y());
        double requestedThrust = massKg * upwardSpecificForce / actualUpwardProjection;
        double motorFraction = requestedThrust / nominalMaximumThrustNewtons(kind, state.battery());
        return new ControlSolution(nextAttitude, nextRates, requestedThrust, motorFraction, true, false);
    }

    private BatteryAndThrust solveBatteryAndThrust(
            DroneKind kind,
            BatteryState battery,
            FlightControl control,
            ControlSolution solution,
            double massKg,
            EnumSet<FlightSafetyFlag> flags
    ) {
        if (battery.isDepleted()) {
            return new BatteryAndThrust(0.0, 0.0, 0.0);
        }

        double motorFraction = FlightMath.clamp(solution.motorFraction(), 0.0, 1.0);
        double current;
        if (kind == DroneKind.MOSQUITO) {
            // This is the exact V1.1.4 BatteryManager load curve.  It uses the
            // normalized throttle channel and all-up weight, with a 1.5 A idle
            // draw even while disarmed.
            double throttle = control.throttle();
            if (!control.armed() || throttle < 0.01) {
                current = 1.5;
            } else {
                double weightGrams = massKg * 1_000.0;
                double hoverCurrent = mosquitoHoverCurrent(weightGrams);
                double maximumCurrent = mosquitoMaximumCurrent(weightGrams);
                current = hoverCurrent + throttle * throttle * (maximumCurrent - hoverCurrent);
            }
        } else {
            double standbyCurrent = control.armed() ? kind.avionicsCurrentAmps() : 0.05;
            double motorCurrent = control.armed()
                    ? kind.maximumMotorCurrentAmps() * Math.pow(motorFraction, 1.5)
                    : 0.0;
            current = standbyCurrent + motorCurrent;
        }
        double loadedVoltage = battery.voltageUnderLoad(current);
        double maximumThrust = nominalMaximumThrustNewtons(kind, battery);

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

    private static double mosquitoHoverCurrent(double totalWeightGrams) {
        if (totalWeightGrams <= 1_050.0) {
            double ratio = totalWeightGrams / 1_050.0;
            return 12.0 * Math.pow(ratio, 1.5);
        }
        if (totalWeightGrams <= 1_500.0) {
            double amount = (totalWeightGrams - 1_050.0) / 450.0;
            return 12.0 + amount * 13.0;
        }
        double amount = Math.min((totalWeightGrams - 1_500.0) / 2_000.0, 1.0);
        return 25.0 + amount * 55.0;
    }

    private static double mosquitoMaximumCurrent(double totalWeightGrams) {
        if (totalWeightGrams <= 1_050.0) return 45.0;
        if (totalWeightGrams <= 1_500.0) {
            double amount = (totalWeightGrams - 1_050.0) / 450.0;
            return 45.0 + amount * 30.0;
        }
        double amount = Math.min((totalWeightGrams - 1_500.0) / 2_000.0, 1.0);
        return 75.0 + amount * 45.0;
    }

    private FlightVector quadraticDrag(DroneKind kind, FlightVector relativeAirVelocity) {
        double speed = relativeAirVelocity.length();
        if (!Double.isFinite(speed) || speed < 1.0e-9 || airDensityKgPerCubicMeter <= 0.0) {
            return FlightVector.ZERO;
        }
        double coefficient = -0.5
                * airDensityKgPerCubicMeter
                * (kind == DroneKind.MOSQUITO ? configuredDragCoefficient : kind.dragCoefficient())
                * kind.referenceAreaSquareMeters()
                * speed;
        return relativeAirVelocity.multiply(coefficient).finiteOrZero();
    }

    private double nominalMaximumThrustNewtons(DroneKind kind, BatteryState battery) {
        if (kind != DroneKind.MOSQUITO) return kind.nominalMaximumThrustNewtons();
        double voltage = battery.cellCount() * 3.7;
        double kilogramsForcePerMotor = 2.04
                * (propDiameterInches / 9.0)
                * (propPitchInches / 4.5)
                * (motorKv / 1300.0)
                * (voltage / 22.2);
        return kilogramsForcePerMotor * 4.0 * GRAVITY_METERS_PER_SECOND_SQUARED * thrustMultiplier;
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
            boolean absoluteThrustRequest,
            boolean reverseThrust
    ) {
    }

    private record BatteryAndThrust(double thrustNewtons, double currentAmps, double loadedVoltage) {
    }
}
