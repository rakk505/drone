package com.modernity.drone.client.input;

/** Shared key-controlled state consumed by the thermal renderer as it is ported. */
public final class ThermalKeyState {
    private static boolean enabled;
    private static AgcMode agcMode = AgcMode.AUTO;
    private static FocusMode focusMode = FocusMode.AUTO;
    private static long nucRequestSerial;

    private ThermalKeyState() {
    }

    public static boolean enabled() { return enabled; }
    public static boolean toggle() { return enabled = !enabled; }
    public static AgcMode agcMode() { return agcMode; }
    public static AgcMode cycleAgcMode() { return agcMode = agcMode.next(); }
    public static FocusMode focusMode() { return focusMode; }
    public static FocusMode cycleFocusMode() { return focusMode = focusMode.next(); }
    public static long nucRequestSerial() { return nucRequestSerial; }
    public static void requestNuc() { nucRequestSerial++; }

    public enum AgcMode {
        AUTO, ROI, MANUAL;
        private AgcMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public enum FocusMode {
        OFF, AUTO, MANUAL;
        private FocusMode next() { return values()[(ordinal() + 1) % values().length]; }
    }
}
