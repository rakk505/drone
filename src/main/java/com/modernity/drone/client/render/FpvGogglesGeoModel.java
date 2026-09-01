package com.modernity.drone.client.render;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import com.modernity.drone.item.FpvGogglesItem;
import net.minecraft.resources.Identifier;

/** The exact wearable goggles geometry and texture from V1.1.4. */
public final class FpvGogglesGeoModel extends GeoModel<FpvGogglesItem> {
    private static final Identifier MODEL =
            Identifier.fromNamespaceAndPath("fpvdrone", "geo/goggles");
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath("fpvdrone", "textures/entity/goggles.png");

    @Override
    public Identifier getModelResource(GeoRenderState renderState) {
        return MODEL;
    }

    @Override
    public Identifier getTextureResource(GeoRenderState renderState) {
        return TEXTURE;
    }

    @Override
    public Identifier getAnimationResource(FpvGogglesItem animatable) {
        return null;
    }
}
