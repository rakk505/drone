package com.modernity.drone.client.video;

/** Nearest-neighbour framebuffer scales exposed by the 1.1.4 video system. */
public enum VideoResolution {
    RES_10(0.10F, "10%"),
    RES_15(0.15F, "15%"),
    RES_20(0.20F, "20%"),
    RES_25(0.25F, "25%"),
    RES_33(0.33F, "33%"),
    RES_50(0.50F, "50%");

    private final float scale;
    private final String displayName;

    VideoResolution(float scale, String displayName) {
        this.scale = scale;
        this.displayName = displayName;
    }

    public float scale() {
        return scale;
    }

    public String displayName() {
        return displayName;
    }

    public int width(int screenWidth, int screenHeight) {
        int height = height(screenHeight);
        float aspect = (float) screenWidth / Math.max(1, screenHeight);
        return Math.max(16, (int) (height * aspect));
    }

    public int height(int screenHeight) {
        return Math.max(16, (int) (screenHeight * scale));
    }

    public VideoResolution next() {
        VideoResolution[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
