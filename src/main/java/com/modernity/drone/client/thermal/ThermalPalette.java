package com.modernity.drone.client.thermal;

public enum ThermalPalette {
    WHITE_HOT("WHOT"),
    BLACK_HOT("BHOT"),
    IRONBOW("IRON");

    private final String label;

    ThermalPalette(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public ThermalPalette next() {
        ThermalPalette[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
