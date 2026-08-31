package com.modernity.drone.client.render;

import com.modernity.drone.entity.DroppedPayloadEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/** Renders the projectile along its velocity-derived yaw/pitch instead of billboarding it. */
public final class PayloadEntityRenderer extends EntityRenderer<DroppedPayloadEntity, PayloadRenderState> {
    private final ItemModelResolver itemModelResolver;

    public PayloadEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        itemModelResolver = context.getItemModelResolver();
        shadowRadius = 0.12F;
    }

    @Override
    public PayloadRenderState createRenderState() {
        return new PayloadRenderState();
    }

    @Override
    public void extractRenderState(DroppedPayloadEntity payload, PayloadRenderState state, float partialTicks) {
        super.extractRenderState(payload, state, partialTicks);
        state.yaw = payload.getYRot(partialTicks);
        state.pitch = payload.getXRot(partialTicks);
        itemModelResolver.updateForNonLiving(
                state.item,
                payload.getItem(),
                ItemDisplayContext.FIXED,
                payload
        );
    }

    @Override
    public void submit(
            PayloadRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        if (!state.isInvisible && !state.item.isEmpty()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch + 90.0F));
            poseStack.scale(0.72F, 0.72F, 0.72F);
            state.item.submit(
                    poseStack,
                    submitNodeCollector,
                    state.lightCoords,
                    OverlayTexture.NO_OVERLAY,
                    state.outlineColor
            );
            poseStack.popPose();
        }
        super.submit(state, poseStack, submitNodeCollector, camera);
    }
}
