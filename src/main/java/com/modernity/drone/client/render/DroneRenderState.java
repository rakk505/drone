package com.modernity.drone.client.render;

import com.modernity.drone.flight.DroneKind;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;

public final class DroneRenderState extends EntityRenderState {
    public final ItemStackRenderState item = new ItemStackRenderState();
    public DroneKind kind = DroneKind.MOSQUITO;
    public float yaw;
    public float pitch;
    public float roll;
}
