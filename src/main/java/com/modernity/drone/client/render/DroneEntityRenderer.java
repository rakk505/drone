package com.modernity.drone.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.flight.DroneKind;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

/** Renders the full 3D item model in airframe space rather than as a camera-facing sprite. */
public final class DroneEntityRenderer extends EntityRenderer<DroneEntity, DroneRenderState> {
    private final ItemModelResolver itemModelResolver;

    public DroneEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemModelResolver = context.getItemModelResolver();
        this.shadowRadius = 0.35F;
    }

    @Override
    public DroneRenderState createRenderState() {
        return new DroneRenderState();
    }

    @Override
    public void extractRenderState(DroneEntity drone, DroneRenderState state, float partialTicks) {
        super.extractRenderState(drone, state, partialTicks);
        state.kind = drone.kind();
        state.yaw = drone.getYRot(partialTicks);
        state.pitch = drone.getXRot(partialTicks);
        state.roll = drone.rollDegrees(partialTicks);
        this.itemModelResolver.updateForNonLiving(
                state.item,
                drone.getItem(),
                ItemDisplayContext.FIXED,
                drone
        );
    }

    @Override
    public void submit(
            DroneRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        if (!state.isInvisible && !state.item.isEmpty()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
            float scale = state.kind == DroneKind.MOSQUITO ? 1.05F : 1.15F;
            poseStack.scale(scale, scale, scale);
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
