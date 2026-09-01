package com.modernity.drone.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the vanilla pose transforms so the thermal silhouette exactly follows the base model. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor<S extends LivingEntityRenderState> {
    @Invoker("setupRotations")
    void fpvdrone$setupThermalRotations(S state, PoseStack poseStack, float bodyRotation, float scale);

    @Invoker("scale")
    void fpvdrone$scaleThermalModel(S state, PoseStack poseStack);
}
