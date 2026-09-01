package com.modernity.drone.client.render;

import com.geckolib.renderer.GeoArmorRenderer;
import com.modernity.drone.item.FpvGogglesItem;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;

/** GeckoLib armor renderer for the original wearable FPV goggles model. */
public final class FpvGogglesArmorRenderer
        extends GeoArmorRenderer<FpvGogglesItem, HumanoidRenderState> {
    public FpvGogglesArmorRenderer() {
        super(new FpvGogglesGeoModel());
    }
}
