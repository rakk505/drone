package com.modernity.drone.client.thermal;

import com.modernity.drone.DroneMod;
import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.mixin.client.LivingEntityRendererAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/** Submits thermal material, living-body, and footprint geometry into the normal world frame. */
@EventBusSubscriber(modid = DroneMod.MOD_ID, value = Dist.CLIENT)
public final class ThermalWorldRenderer {
    private static final Identifier WHITE_TEXTURE =
            Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "textures/misc/white.png");
    private static final RenderType MATERIAL_RENDER_TYPE =
            RenderTypes.entityCutout(ThermalMaterialAtlas.LOCATION);
    private static final RenderType LIVING_HEAT_RENDER_TYPE = RenderTypes.entityCutout(WHITE_TEXTURE);
    private static final int FULL_BRIGHT = 15_728_880;
    private static final int LIVING_HEAT_COLOR = ARGB.color(225, 235, 255, 0);
    private static volatile LevelRenderState snapshotOwner;
    private static volatile SceneSnapshot latestSnapshot;

    private ThermalWorldRenderer() {
    }

    public static void tick(Minecraft minecraft) {
        ThermalState thermal = ThermalState.get();
        var drone = DroneControlClient.currentDrone();
        thermal.setAutomaticEnabled(drone != null && drone.isThermal());
        thermal.synchronizeKeyState(System.currentTimeMillis());
        if (thermal.active() && minecraft.level != null) {
            ThermalSceneTracker.get().tick(minecraft);
            FootprintSystem.get().tick(minecraft.level);
        }
    }

    public static void reset() {
        ThermalSceneTracker.get().clear();
        FootprintSystem.get().clear();
        snapshotOwner = null;
        latestSnapshot = null;
    }

    public static void shutdown() {
        reset();
        ThermalMaterialAtlas.close();
    }

    @SubscribeEvent
    public static void onExtractLevelRenderState(ExtractLevelRenderStateEvent event) {
        if (!ThermalState.get().active()) {
            snapshotOwner = null;
            latestSnapshot = null;
            return;
        }
        boolean atlasReady = ThermalMaterialAtlas.ensureReady();
        Vec3 camera = event.getCamera().position();
        List<FootprintSnapshot> footprints = new ArrayList<>(FootprintSystem.get().size());
        for (FootprintSystem.Footprint footprint : FootprintSystem.get().footprints()) {
            if (footprint.intensity() < 0.005F) continue;
            footprints.add(new FootprintSnapshot(
                    footprint.x(), footprint.y(), footprint.z(), footprint.width(), footprint.length(),
                    footprint.angle(), footprint.intensity()));
        }
        snapshotOwner = event.getRenderState();
        latestSnapshot = new SceneSnapshot(
                atlasReady ? ThermalSceneTracker.get().snapshot() : List.of(),
                List.copyOf(footprints), camera);
    }

    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        SceneSnapshot snapshot = snapshotOwner == event.getLevelRenderState() ? latestSnapshot : null;
        if (snapshot == null || !ThermalState.get().active()) {
            return;
        }
        submitMaterialBlocks(event, snapshot);
        submitFootprints(event, snapshot);
    }

    @SubscribeEvent
    public static <T extends LivingEntity, S extends LivingEntityRenderState,
            M extends EntityModel<? super S>> void onLivingRendered(RenderLivingEvent.Post<T, S, M> event) {
        if (!ThermalState.get().active() || event.getRenderState().isInvisibleToPlayer) {
            return;
        }
        LivingEntityRenderer<T, S, M> renderer = event.getRenderer();
        S state = event.getRenderState();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        if (state.hasPose(net.minecraft.world.entity.Pose.SLEEPING) && state.bedOrientation != null) {
            float offset = state.eyeHeight - 0.1F;
            poseStack.translate(
                    -state.bedOrientation.getStepX() * offset,
                    0.0F,
                    -state.bedOrientation.getStepZ() * offset
            );
        }
        float scale = state.scale;
        poseStack.scale(scale, scale, scale);
        @SuppressWarnings("unchecked")
        LivingEntityRendererAccessor<S> accessor = (LivingEntityRendererAccessor<S>) renderer;
        accessor.fpvdrone$setupThermalRotations(state, poseStack, state.bodyRot, scale);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        accessor.fpvdrone$scaleThermalModel(state, poseStack);
        poseStack.translate(0.0F, -1.501F, 0.0F);
        event.getSubmitNodeCollector().order(100).submitModel(
                renderer.getModel(),
                state,
                poseStack,
                LIVING_HEAT_RENDER_TYPE,
                FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                LIVING_HEAT_COLOR,
                null
        );
        poseStack.popPose();
    }

    private static void submitMaterialBlocks(SubmitCustomGeometryEvent event, SceneSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        RandomSource random = RandomSource.create(42L);
        OrderedSubmitNodeCollector collector = event.getSubmitNodeCollector().order(90);
        PoseStack poseStack = event.getPoseStack();
        for (ThermalSceneTracker.ThermalBlock block : snapshot.blocks()) {
            BlockStateModel model = minecraft.getModelManager().getBlockStateModelSet().get(block.state());
            ArrayList<BlockStateModelPart> parts = new ArrayList<>();
            random.setSeed(block.state().getSeed(block.pos()));
            model.collectParts(random, parts);
            if (parts.isEmpty()) continue;

            BlockPos pos = block.pos();
            poseStack.pushPose();
            poseStack.translate(
                    pos.getX() - snapshot.camera().x,
                    pos.getY() - snapshot.camera().y,
                    pos.getZ() - snapshot.camera().z
            );
            List<BlockStateModelPart> submittedParts = List.copyOf(parts);
            collector.submitCustomGeometry(poseStack, MATERIAL_RENDER_TYPE, (pose, vertices) -> {
                QuadInstance quad = new QuadInstance();
                quad.setColor(0xFFFFFFFF);
                quad.setLightCoords(FULL_BRIGHT);
                quad.setOverlayCoords(OverlayTexture.NO_OVERLAY);
                for (BlockStateModelPart part : submittedParts) {
                    for (Direction direction : Direction.values()) {
                        part.getQuads(direction).forEach(baked -> vertices.putBakedQuad(pose, baked, quad));
                    }
                    part.getQuads(null).forEach(baked -> vertices.putBakedQuad(pose, baked, quad));
                }
            });
            poseStack.popPose();
        }
    }

    private static void submitFootprints(SubmitCustomGeometryEvent event, SceneSnapshot snapshot) {
        if (snapshot.footprints().isEmpty()) return;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(-snapshot.camera().x, -snapshot.camera().y, -snapshot.camera().z);
        event.getSubmitNodeCollector().order(110).submitCustomGeometry(
                poseStack, RenderTypes.debugQuads(), (pose, vertices) -> {
                    for (FootprintSnapshot footprint : snapshot.footprints()) {
                        int value = Math.max(1, Math.min(255, Math.round(footprint.intensity() * 255.0F)));
                        int color = ARGB.color(value, value, value, value);
                        float halfWidth = footprint.width() * 0.5F;
                        float halfLength = footprint.length() * 0.5F;
                        float sine = (float) Math.sin(footprint.angle());
                        float cosine = (float) Math.cos(footprint.angle());
                        addFootVertex(vertices, pose, footprint, -halfWidth, -halfLength, sine, cosine, color);
                        addFootVertex(vertices, pose, footprint, halfWidth, -halfLength, sine, cosine, color);
                        addFootVertex(vertices, pose, footprint, halfWidth, halfLength, sine, cosine, color);
                        addFootVertex(vertices, pose, footprint, -halfWidth, halfLength, sine, cosine, color);
                    }
                });
        poseStack.popPose();
    }

    private static void addFootVertex(com.mojang.blaze3d.vertex.VertexConsumer vertices,
                                      PoseStack.Pose pose, FootprintSnapshot footprint,
                                      float localX, float localZ, float sine, float cosine, int color) {
        float rotatedX = localX * cosine - localZ * sine;
        float rotatedZ = localX * sine + localZ * cosine;
        vertices.addVertex(pose,
                        (float) footprint.x() + rotatedX,
                        (float) footprint.y(),
                        (float) footprint.z() + rotatedZ)
                .setColor(color);
    }

    private record SceneSnapshot(
            List<ThermalSceneTracker.ThermalBlock> blocks,
            List<FootprintSnapshot> footprints,
            Vec3 camera
    ) {
    }

    private record FootprintSnapshot(
            double x, double y, double z, float width, float length, float angle, float intensity
    ) {
    }
}
