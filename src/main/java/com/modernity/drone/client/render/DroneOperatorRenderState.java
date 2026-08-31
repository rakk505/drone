package com.modernity.drone.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** Client-only snapshot used by the stationary drone-operator model. */
public final class DroneOperatorRenderState extends LivingEntityRenderState {
    /** True while the operator has an attack target and is actively steering its drone. */
    public boolean targetLocked;
}
