package com.modernity.drone.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;

/** Resource mapping for the exact FPVtoMinecraft 1.1.4 seven-inch airframe. */
public final class FpvDroneGeoModel extends GeoModel<FpvDroneVisual> {
    private static final Identifier MODEL =
            Identifier.fromNamespaceAndPath("fpvdrone", "geo/fpv_7d");
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("fpvdrone", "textures/entity/fpv_7d.png");
    private static final Identifier ANIMATION =
            Identifier.fromNamespaceAndPath("fpvdrone", "drone");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(FpvDroneVisual animatable) {
        return ANIMATION;
    }
}
