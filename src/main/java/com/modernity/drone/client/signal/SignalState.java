package com.modernity.drone.client.signal;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Per-client flight-session signal state. */
public final class SignalState {
    public static final int RESET_GRACE_TICKS = 60;
    private static final SignalState INSTANCE = new SignalState();

    private final SignalCalculator calculator = new SignalCalculator();
    private SignalSettings settings = SignalSettings.V1_1_4_DEFAULTS;
    private @Nullable Vec3 origin;
    private double distance;
    private float strength = 1.0F;
    private float freezeStrength = 1.0F;
    private boolean forcedNoSignal;
    private int graceTicks;

    private SignalState() {
    }

    public static SignalState get() {
        return INSTANCE;
    }

    public void begin(Vec3 pilotOrigin) {
        origin = pilotOrigin;
        distance = 0.0;
        strength = 1.0F;
        freezeStrength = 1.0F;
        forcedNoSignal = false;
        graceTicks = RESET_GRACE_TICKS;
        calculator.reset();
    }

    public void update(Level level, Vec3 dronePosition) {
        if (forcedNoSignal) {
            strength = 0.0F;
            freezeStrength = 0.0F;
            return;
        }
        if (graceTicks > 0) {
            graceTicks--;
            distance = 0.0;
            strength = 1.0F;
            freezeStrength = 1.0F;
            return;
        }
        if (origin == null) {
            begin(dronePosition);
            return;
        }
        distance = calculator.distance(dronePosition, origin);
        if (distance >= settings.maximumRange()) {
            strength = 0.0F;
            freezeStrength = 0.0F;
            return;
        }
        strength = calculator.calculate(dronePosition, origin, level, settings);
        freezeStrength = calculator.freezeSignal(distance, settings);
    }

    public void reset() {
        origin = null;
        distance = 0.0;
        strength = 1.0F;
        freezeStrength = 1.0F;
        forcedNoSignal = false;
        graceTicks = RESET_GRACE_TICKS;
        calculator.reset();
    }

    public void forceNoSignal() {
        forcedNoSignal = true;
        strength = 0.0F;
        freezeStrength = 0.0F;
        graceTicks = 0;
    }

    public void setSettings(SignalSettings settings) {
        this.settings = settings == null ? SignalSettings.V1_1_4_DEFAULTS : settings;
        calculator.reset();
    }

    public SignalSettings settings() {
        return settings;
    }

    public @Nullable Vec3 origin() {
        return origin;
    }

    public double distance() {
        return distance;
    }

    public float strength() {
        return strength;
    }

    public float freezeStrength() {
        return freezeStrength;
    }

    public boolean controlBlocked() {
        return strength <= 0.01F;
    }

    public boolean forcedNoSignal() {
        return forcedNoSignal;
    }

    public boolean inGracePeriod() {
        return graceTicks > 0;
    }
}
