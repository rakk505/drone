package com.modernity.drone.client.thermal;

/** Normalized material values consumed by the copied thermal shaders. */
public record ThermalProperties(float temperature, float emissivity, float materialType, float thermalMass) {
    public static final float MATERIAL_NORMAL = 0.0F;
    public static final float MATERIAL_GLASS = 0.25F;
    public static final float MATERIAL_METAL = 0.50F;
    public static final float MATERIAL_WATER = 0.75F;
    public static final float MATERIAL_FIRE = 1.0F;

    public static final ThermalProperties ATLAS_DEFAULT = new ThermalProperties(0, 0, 0, 0);
    public static final ThermalProperties DEFAULT = new ThermalProperties(0.12F, 0.9F, MATERIAL_NORMAL, 0.5F);
}
