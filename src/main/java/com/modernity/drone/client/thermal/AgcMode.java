package com.modernity.drone.client.thermal;

public enum AgcMode {
    AUTO,
    ROI,
    MANUAL;

    public AgcMode next() {
        AgcMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
