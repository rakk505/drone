package com.modernity.drone.client.render;

import com.geckolib.renderer.GeoReplacedEntityRenderer;
import com.geckolib.cache.model.cuboid.CuboidGeoBone;
import com.geckolib.cache.model.cuboid.GeoCube;
import com.geckolib.renderer.base.BoneSnapshots;
import com.geckolib.renderer.base.RenderPassInfo;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.flight.DroneKind;
import java.util.Set;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.jspecify.annotations.Nullable;

/**
 * Renders the mosquito with FPVtoMinecraft's original articulated GeckoLib
 * airframe. The payload drone keeps its existing hand-authored item model.
 */
public final class DroneEntityRenderer
        extends GeoReplacedEntityRenderer<FpvDroneVisual, DroneEntity, DroneRenderState> {
    private static final float PROP_FADE_START = 0.25F;
    private static final float PROP_FADE_END = 0.55F;
    private static final Set<String> PROP_BONES = Set.of(
            "vintleftfront", "vintrightfront", "vintleftback", "vintrightback"
    );
    private static final Set<String> BLUR_BONES = Set.of(
            "vintleftfrontblur", "vintrightfrontblur", "vintleftbackblur", "vintrightbackblur"
    );

    public DroneEntityRenderer(EntityRendererProvider.Context context) {
        super(context, new FpvDroneGeoModel(), new FpvDroneVisual());
        this.shadowRadius = 0.0F;
    }

    @Override
    public DroneRenderState createRenderState(FpvDroneVisual animatable, DroneEntity drone) {
        return new DroneRenderState();
    }

    @Override
    public void extractRenderState(DroneEntity drone, DroneRenderState state, float partialTick) {
        super.extractRenderState(drone, state, partialTick);

        Minecraft minecraft = Minecraft.getInstance();
        boolean localDrone = DroneControlClient.currentDrone() == drone;

        state.kind = drone.kind();
        state.yaw = drone.getYRot(partialTick);
        state.pitch = drone.getXRot(partialTick);
        state.roll = drone.rollDegrees(partialTick);
        state.propellerAngle = drone.getPropAngle(partialTick);
        state.batteryInstalled = drone.hasBatteryInstalled();
        state.rpgInstalled = drone.hasRpg();
        state.thermal = drone.isThermal();
        state.localFirstPerson = localDrone
                && minecraft.options.getCameraType() == CameraType.FIRST_PERSON;

        if (!drone.isArmed()) {
            state.throttle = 0.0F;
        } else if (localDrone) {
            state.throttle = Mth.clamp(DroneControlClient.requestedThrottle(), 0.0F, 1.0F);
        } else {
            // These are the original renderer's representative speeds for a
            // remotely controlled and an armed-but-idle aircraft.
            state.throttle = drone.isBeingControlled() ? 0.7F : 0.15F;
        }

        this.itemModelResolver.updateForNonLiving(
                state.item,
                drone.getItem(),
                ItemDisplayContext.FIXED,
                drone
        );
    }

    @Override
    public @Nullable RenderType getRenderType(DroneRenderState state, Identifier texture) {
        // A null GeckoLib pass leaves the payload aircraft to the item-model
        // branch in submit while preserving EntityRenderer's nameplate pass.
        return state.kind == DroneKind.PAYLOAD ? null : super.getRenderType(state, texture);
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<DroneRenderState> renderPassInfo) {
        DroneRenderState state = renderPassInfo.renderState();
        PoseStack poseStack = renderPassInfo.poseStack();

        // This order and 180-degree model-space correction match V1.1.4.
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - state.yaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
        poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
        poseStack.translate(0.0F, 0.01F, 0.0F);
    }

    @Override
    public void preRenderPass(
            RenderPassInfo<DroneRenderState> renderPassInfo,
            SubmitNodeCollector renderTasks
    ) {
        DroneRenderState state = renderPassInfo.renderState();
        if (state.kind != DroneKind.MOSQUITO || (state.thermal && state.localFirstPerson)) {
            return;
        }

        if (state.throttle > PROP_FADE_START && state.throttle <= PROP_FADE_END) {
            float transition = (state.throttle - PROP_FADE_START) / (PROP_FADE_END - PROP_FADE_START);
            registerFadedBones(renderPassInfo, PROP_BONES, 1.0F - transition);
        }

        if (state.throttle >= PROP_FADE_START) {
            float blurAlpha = state.throttle > PROP_FADE_END
                    ? 1.0F
                    : (state.throttle - PROP_FADE_START) / (PROP_FADE_END - PROP_FADE_START);
            registerFadedBones(renderPassInfo, BLUR_BONES, blurAlpha);
        }
    }

    @Override
    public void adjustModelBonesForRender(
            RenderPassInfo<DroneRenderState> renderPassInfo,
            BoneSnapshots snapshots
    ) {
        DroneRenderState state = renderPassInfo.renderState();

        setBoneVisibility(snapshots, "batka", state.batteryInstalled);
        setBoneVisibility(snapshots, "rpg", state.rpgInstalled);
        setBoneVisibility(snapshots, "kirpichiki", false);

        snapshots.ifPresent("camera", bone -> bone
                .skipRender(state.localFirstPerson)
                .setRotX((float) Math.toRadians(FpvClientConfig.cameraAngle())));

        // The reference renderer keeps the low-throttle physical blades,
        // fades them from 25-55%, and fully hides them above 55%.
        boolean showPhysicalProps = (state.thermal && state.localFirstPerson)
                || state.throttle <= PROP_FADE_START;
        for (String boneName : PROP_BONES) {
            snapshots.ifPresent(boneName, bone -> bone
                    .skipRender(!showPhysicalProps)
                    .setRotY(state.propellerAngle));
        }

        // Blur discs are emitted separately with an alpha ramp so their
        // zero-thickness quads use a translucent pass just like V1.1.4.
        for (String boneName : BLUR_BONES) {
            setBoneVisibility(snapshots, boneName, false);
        }
    }

    private static void setBoneVisibility(BoneSnapshots snapshots, String boneName, boolean visible) {
        snapshots.ifPresent(boneName, bone -> bone
                .skipRender(!visible)
                .skipChildrenRender(!visible));
    }

    private void registerFadedBones(
            RenderPassInfo<DroneRenderState> renderPassInfo,
            Set<String> boneNames,
            float alpha
    ) {
        int alphaByte = Mth.clamp(Math.round(alpha * 255.0F), 0, 255);
        if (alphaByte == 0) {
            return;
        }
        int fadeColor = (alphaByte << 24) | 0x00FFFFFF;

        for (String boneName : boneNames) {
            renderPassInfo.model().getBone(boneName)
                    .filter(CuboidGeoBone.class::isInstance)
                    .ifPresent(bone -> renderPassInfo.addPerBoneRender(
                            bone,
                            (pass, renderedBone, collector) -> submitFadedBone(
                                    pass,
                                    (CuboidGeoBone) renderedBone,
                                    collector,
                                    fadeColor
                            )
                    ));
        }
    }

    private void submitFadedBone(
            RenderPassInfo<DroneRenderState> renderPassInfo,
            CuboidGeoBone bone,
            SubmitNodeCollector renderTasks,
            int fadeColor
    ) {
        RenderType renderType = RenderTypes.entityTranslucent(getTextureLocation(renderPassInfo.renderState()));
        int renderColor = ARGB.multiply(renderPassInfo.renderColor(), fadeColor);

        renderTasks.submitCustomGeometry(renderPassInfo.poseStack(), renderType, (pose, vertexConsumer) -> {
            PoseStack poseStack = new PoseStack();
            poseStack.last().set(pose);
            bone.translateAwayFromPivotPoint(poseStack);

            for (GeoCube cube : bone.cubes) {
                poseStack.pushPose();
                cube.render(
                        poseStack,
                        vertexConsumer,
                        renderPassInfo.packedLight(),
                        renderPassInfo.packedOverlay(),
                        renderColor
                );
                poseStack.popPose();
            }
        });
    }

    @Override
    public void submit(
            DroneRenderState state,
            PoseStack poseStack,
            SubmitNodeCollector submitNodeCollector,
            CameraRenderState camera
    ) {
        if (state.kind == DroneKind.PAYLOAD && !state.isInvisible && !state.item.isEmpty()) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(-state.yaw));
            poseStack.mulPose(Axis.XP.rotationDegrees(state.pitch));
            poseStack.mulPose(Axis.ZP.rotationDegrees(state.roll));
            poseStack.scale(1.15F, 1.15F, 1.15F);
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
