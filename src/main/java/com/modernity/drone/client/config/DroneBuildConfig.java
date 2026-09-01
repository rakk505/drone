package com.modernity.drone.client.config;

/** Editable airframe values mirrored from the V1.1.4 model-settings UI. */
public final class DroneBuildConfig {
    private float massGrams = 250.0F;
    private int motorKv = 2400;
    private float motorWidthMm = 22.0F;
    private float motorHeightMm = 6.0F;
    private int batteryCells = 4;
    private float propDiameterInches = 5.0F;
    private float propPitchInches = 4.0F;
    private int propBlades = 3;
    private float frameWidthMm = 150.0F;
    private float frameHeightMm = 30.0F;
    private float frameLengthMm = 150.0F;
    private boolean showProCamera = true;
    private boolean heroCamera;
    private boolean toothpick;
    private int red = 255;
    private int green = 100;
    private int blue;

    public float massGrams() { return massGrams; }
    public void setMassGrams(float value) { massGrams = clamp(value, 50.0F, 1000.0F); }
    public int motorKv() { return motorKv; }
    public void setMotorKv(int value) { motorKv = clamp(value, 1000, 5000); }
    public float motorWidthMm() { return motorWidthMm; }
    public void setMotorWidthMm(float value) { motorWidthMm = clamp(value, 10.0F, 40.0F); }
    public float motorHeightMm() { return motorHeightMm; }
    public void setMotorHeightMm(float value) { motorHeightMm = clamp(value, 2.0F, 15.0F); }
    public int batteryCells() { return batteryCells; }
    public void setBatteryCells(int value) { batteryCells = clamp(value, 1, 8); }
    public float propDiameterInches() { return propDiameterInches; }
    public void setPropDiameterInches(float value) { propDiameterInches = clamp(value, 2.0F, 10.0F); }
    public float propPitchInches() { return propPitchInches; }
    public void setPropPitchInches(float value) { propPitchInches = clamp(value, 1.0F, 8.0F); }
    public int propBlades() { return propBlades; }
    public void setPropBlades(int value) { propBlades = clamp(value, 2, 6); }
    public float frameWidthMm() { return frameWidthMm; }
    public void setFrameWidthMm(float value) { frameWidthMm = clamp(value, 50.0F, 400.0F); }
    public float frameHeightMm() { return frameHeightMm; }
    public void setFrameHeightMm(float value) { frameHeightMm = clamp(value, 10.0F, 100.0F); }
    public float frameLengthMm() { return frameLengthMm; }
    public void setFrameLengthMm(float value) { frameLengthMm = clamp(value, 50.0F, 400.0F); }
    public boolean showProCamera() { return showProCamera; }
    public void setShowProCamera(boolean value) { showProCamera = value; }
    public boolean heroCamera() { return heroCamera; }
    public void setHeroCamera(boolean value) { heroCamera = value; }
    public boolean toothpick() { return toothpick; }
    public void setToothpick(boolean value) { toothpick = value; }
    public int red() { return red; }
    public void setRed(int value) { red = clamp(value, 0, 255); }
    public int green() { return green; }
    public void setGreen(int value) { green = clamp(value, 0, 255); }
    public int blue() { return blue; }
    public void setBlue(int value) { blue = clamp(value, 0, 255); }

    private static float clamp(float value, float minimum, float maximum) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : minimum;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
