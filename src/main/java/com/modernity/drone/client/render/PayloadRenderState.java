package com.modernity.drone.client.render;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class PayloadRenderState extends EntityRenderState {
    public final ItemStackRenderState item = new ItemStackRenderState();
    public float yaw;
    public float pitch;
}
