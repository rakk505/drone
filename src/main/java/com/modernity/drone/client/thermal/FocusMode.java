package com.modernity.drone.client.thermal;

public enum FocusMode {
    OFF,
    AUTO,
    MANUAL;

    public FocusMode next() {
        FocusMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
