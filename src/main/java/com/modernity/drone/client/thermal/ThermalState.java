package com.modernity.drone.client.thermal;

import com.modernity.drone.client.input.ThermalKeyState;

/** Client thermal-camera state and controls. */
public final class ThermalState {
    private static final ThermalState INSTANCE = new ThermalState();

    private final NucManager nuc = new NucManager();
    private boolean manualEnabled;
    private boolean automaticEnabled;
    private ThermalPalette palette = ThermalPalette.WHITE_HOT;
    private AgcMode agcMode = AgcMode.AUTO;
    private FocusMode focusMode = FocusMode.AUTO;
    private float manualAgcOffset;
    private float manualAgcGain = 0.5F;
    private float manualFocusDistance = 20.0F;
    private long observedNucSerial;
    private ThermalKeyState.AgcMode observedExternalAgc;
    private ThermalKeyState.FocusMode observedExternalFocus;
    private boolean externalStateInitialized;

    private ThermalState() {
    }

    public static ThermalState get() {
        return INSTANCE;
    }

    /** Bridges the key layer without making the render code own key registrations. */
    public void synchronizeKeyState(long nowMillis) {
        manualEnabled = ThermalKeyState.enabled();
        ThermalKeyState.AgcMode externalAgc = ThermalKeyState.agcMode();
        ThermalKeyState.FocusMode externalFocus = ThermalKeyState.focusMode();
        if (!externalStateInitialized) {
            observedExternalAgc = externalAgc;
            observedExternalFocus = externalFocus;
            externalStateInitialized = true;
        } else {
            if (externalAgc != observedExternalAgc) {
                agcMode = agcMode.next();
                observedExternalAgc = externalAgc;
            }
            if (externalFocus != observedExternalFocus) {
                focusMode = focusMode.next();
                observedExternalFocus = externalFocus;
            }
        }
        long serial = ThermalKeyState.nucRequestSerial();
        if (serial != observedNucSerial) {
            observedNucSerial = serial;
            nuc.trigger(nowMillis);
        }
        if (isActive()) {
            nuc.tick(nowMillis);
        }
    }

    public boolean active() {
        return manualEnabled || automaticEnabled;
    }

    public boolean isActive() {
        return active();
    }

    public void setManualEnabled(boolean enabled) {
        manualEnabled = enabled;
    }

    public void setAutomaticEnabled(boolean enabled) {
        automaticEnabled = enabled;
    }

    public boolean automaticallyEnabled() {
        return automaticEnabled;
    }

    public ThermalPalette palette() {
        return palette;
    }

    public ThermalPalette cyclePalette() {
        return palette = palette.next();
    }

    public AgcMode agcMode() {
        return agcMode;
    }

    public AgcMode cycleAgcMode() {
        return agcMode = agcMode.next();
    }

    public AgcMode acceptExternalAgcCycle() {
        observedExternalAgc = ThermalKeyState.agcMode();
        externalStateInitialized = true;
        return cycleAgcMode();
    }

    public FocusMode focusMode() {
        return focusMode;
    }

    public FocusMode cycleFocusMode() {
        return focusMode = focusMode.next();
    }

    public FocusMode acceptExternalFocusCycle() {
        observedExternalFocus = ThermalKeyState.focusMode();
        externalStateInitialized = true;
        return cycleFocusMode();
    }

    public float manualAgcOffset() {
        return manualAgcOffset;
    }

    public void adjustManualAgcOffset(float delta) {
        manualAgcOffset = clamp(manualAgcOffset + delta, 0.0F, 1.0F);
    }

    public float manualAgcGain() {
        return manualAgcGain;
    }

    public void adjustManualAgcGain(float delta) {
        manualAgcGain = clamp(manualAgcGain + delta, 0.05F, 0.5F);
    }

    public float manualAgcMinimum() {
        return Math.max(0.0F, manualAgcOffset - manualAgcGain);
    }

    public float manualAgcMaximum() {
        return Math.min(1.0F, manualAgcOffset + manualAgcGain);
    }

    public float manualFocusDistance() {
        return manualFocusDistance;
    }

    public void adjustManualFocusDistance(float delta) {
        manualFocusDistance = clamp(manualFocusDistance + delta, 1.0F, 256.0F);
    }

    public NucManager nuc() {
        return nuc;
    }

    public void reset(long nowMillis) {
        automaticEnabled = false;
        nuc.reset(nowMillis);
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
