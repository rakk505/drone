package com.modernity.drone.client.thermal;

import java.util.List;
import net.minecraft.resources.Identifier;

/** Resource identifiers for the original thermal shader suite retained in the port. */
public final class ThermalResources {
    public static final int SENSOR_WIDTH = 384;
    public static final int SENSOR_HEIGHT = 288;

    public static final List<Identifier> WORLD_SHADERS = List.of(
            shader("rendertype_solid_thermal"),
            shader("rendertype_cutout_thermal"),
            shader("rendertype_translucent_thermal"),
            shader("rendertype_entity_solid_thermal"),
            shader("sky_thermal"),
            shader("cloud_thermal")
    );

    public static final List<Identifier> ACTIVE_POST_PASSES = List.of(
            post("fallback_thermal"), post("atmospheric_fog"), post("agc"), post("dde"),
            post("depth_of_field"), post("netd_noise"), post("fpn"), post("palette_lut"),
            post("vignette_distortion"),
            shaderPost("fpv_thermal"), shaderPost("fpv_bloom_extract"),
            shaderPost("fpv_bloom_blur_h"), shaderPost("fpv_bloom_blur_v"),
            shaderPost("fpv_bloom_composite")
    );

    public static final List<String> DORMANT_LOGICAL_PASSES = List.of(
            "legacy_downsample", "legacy_gaussian_blur", "legacy_upsample"
    );

    private ThermalResources() {
    }

    private static Identifier shader(String name) {
        return Identifier.fromNamespaceAndPath("fpvdrone", "shaders/core/" + name);
    }

    private static Identifier post(String name) {
        return Identifier.fromNamespaceAndPath("fpvdrone", "shaders/core/post/" + name);
    }

    private static Identifier shaderPost(String name) {
        return Identifier.fromNamespaceAndPath("fpvdrone", "shaders/post/" + name);
    }
}
