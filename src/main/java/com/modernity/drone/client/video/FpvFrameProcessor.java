package com.modernity.drone.client.video;

import com.modernity.drone.DroneMod;
import com.modernity.drone.client.thermal.ThermalState;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.jspecify.annotations.Nullable;

/**
 * Applies the FPV camera feed to the world framebuffer immediately before the GUI is drawn.
 *
 * <p>The 1.20.1 implementation used raw OpenGL framebuffer blits. Minecraft 26.2 owns those
 * framebuffers through its GPU abstraction, so this class performs the same two-stage nearest
 * neighbour downsample/upsample with {@link RenderTarget} and keeps a full-size last-good frame
 * for reception stalls and signal loss.</p>
 */
public final class FpvFrameProcessor implements AutoCloseable {
    private static final FpvFrameProcessor INSTANCE = new FpvFrameProcessor();
    private static final int THERMAL_UNIFORM_BYTES = 48;
    private static final int BLOOM_UNIFORM_BYTES = 16;

    private static final RenderPipeline THERMAL_PIPELINE = RenderPipeline.builder(
                    RenderPipelines.POST_PROCESSING_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "pipeline/fpv_thermal"))
            .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath(DroneMod.MOD_ID, "post/fpv_thermal"))
            .withBindGroupLayout(BindGroupLayout.builder()
                    .withSampler("InSampler")
                    .withSampler("DepthSampler")
                    .withUniform("ThermalConfig", UniformType.UNIFORM_BUFFER)
                    .build())
            .build();

    private static final RenderPipeline BLOOM_EXTRACT_PIPELINE = postPipeline(
            "fpv_thermal_bloom_extract", "fpv_bloom_extract", false, false);
    private static final RenderPipeline BLOOM_BLUR_HORIZONTAL_PIPELINE = postPipeline(
            "fpv_thermal_bloom_blur_h", "fpv_bloom_blur_h", false, false);
    private static final RenderPipeline BLOOM_BLUR_VERTICAL_PIPELINE = postPipeline(
            "fpv_thermal_bloom_blur_v", "fpv_bloom_blur_v", false, false);
    private static final RenderPipeline BLOOM_COMPOSITE_PIPELINE = postPipeline(
            "fpv_thermal_bloom_composite", "fpv_bloom_composite", true, true);

    private @Nullable TextureTarget lowResolutionTarget;
    private @Nullable TextureTarget frozenTarget;
    private @Nullable TextureTarget bloomPingTarget;
    private @Nullable TextureTarget bloomPongTarget;
    private @Nullable GpuBuffer thermalUniforms;
    private @Nullable GpuBuffer bloomUniforms;
    private @Nullable UUID sessionId;
    private @Nullable GpuFormat lowResolutionFormat;
    private @Nullable GpuFormat frozenFormat;
    private @Nullable GpuFormat bloomFormat;
    private boolean thermalPipelineFailed;
    private boolean bloomPipelineFailed;
    private boolean processedLastFrame;

    private FpvFrameProcessor() {
    }

    public static FpvFrameProcessor get() {
        return INSTANCE;
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(THERMAL_PIPELINE);
        event.registerPipeline(BLOOM_EXTRACT_PIPELINE);
        event.registerPipeline(BLOOM_BLUR_HORIZONTAL_PIPELINE);
        event.registerPipeline(BLOOM_BLUR_VERTICAL_PIPELINE);
        event.registerPipeline(BLOOM_COMPOSITE_PIPELINE);
    }

    /** Called from the GameRenderer mixin after the world and vanilla post effects, before GUI. */
    public void process(RenderTarget mainTarget) {
        RenderSystem.assertOnRenderThread();
        VideoFeedState video = VideoFeedState.get();
        VideoFeedState.FrameDecision decision = video.frameDecision();
        UUID droneId = video.droneId();
        if (!decision.active() || droneId == null || mainTarget == null
                || mainTarget.width <= 0 || mainTarget.height <= 0) {
            if (processedLastFrame) {
                releaseSessionTargets();
            }
            processedLastFrame = false;
            return;
        }

        processedLastFrame = true;
        if (!droneId.equals(sessionId)) {
            releaseSessionTargets();
            sessionId = droneId;
        }

        ThermalState thermal = ThermalState.get();
        boolean holdForNuc = thermal.active() && thermal.nuc().frozen() && frozenTarget != null;
        if ((decision.replayPreviousFrame() || holdForNuc) && frozenTarget != null) {
            if (frozenTarget.width == mainTarget.width && frozenTarget.height == mainTarget.height
                    && frozenTarget.getColorTexture().getFormat()
                    == mainTarget.getColorTexture().getFormat()) {
                copyColor(frozenTarget, mainTarget);
            } else {
                // Preserve a held image across a window resize; replace it on the next live frame.
                frozenTarget.blitAndBlendToTexture(
                        mainTarget.getColorTextureView(),
                        mainTarget.useDepth ? mainTarget.getDepthTextureView() : null
                );
            }
            return;
        }

        if (!decision.captureCurrentFrame() && !holdForNuc) {
            return;
        }

        TextureTarget lowResolution = ensureLowResolutionTarget(mainTarget, decision.renderScale());
        boolean thermalRendered = thermal.active()
                && !thermalPipelineFailed
                && renderThermal(mainTarget, lowResolution, thermal);
        if (!thermalRendered) {
            mainTarget.blitAndBlendToTexture(lowResolution.getColorTextureView(), null);
        }
        boolean bloomRendered = thermalRendered && !bloomPipelineFailed
                && renderBloom(lowResolution, mainTarget, thermal);
        if (!bloomRendered) {
            lowResolution.blitAndBlendToTexture(
                    mainTarget.getColorTextureView(),
                    mainTarget.useDepth ? mainTarget.getDepthTextureView() : null
            );
        }
        captureFrozenFrame(mainTarget);
    }

    /** True only when the 26.2 thermal shader could not be used and the HUD should tint instead. */
    public boolean needsThermalOverlayFallback() {
        return thermalPipelineFailed;
    }

    public boolean hasFrozenFrame() {
        return frozenTarget != null && sessionId != null;
    }

    private TextureTarget ensureLowResolutionTarget(RenderTarget mainTarget, float scale) {
        int height = Math.max(16, (int) (mainTarget.height * scale));
        float aspect = (float) mainTarget.width / Math.max(1, mainTarget.height);
        int width = Math.max(16, (int) (height * aspect));
        GpuFormat format = mainTarget.getColorTexture().getFormat();
        if (lowResolutionTarget == null
                || lowResolutionTarget.width != width
                || lowResolutionTarget.height != height
                || lowResolutionFormat != format) {
            if (lowResolutionTarget != null) {
                lowResolutionTarget.destroyBuffers();
            }
            lowResolutionTarget = new TextureTarget(
                    "FPV low-resolution feed", width, height, false, format);
            lowResolutionFormat = format;
        }
        return lowResolutionTarget;
    }

    private void ensureFrozenTarget(RenderTarget mainTarget) {
        GpuFormat format = mainTarget.getColorTexture().getFormat();
        if (frozenTarget == null
                || frozenTarget.width != mainTarget.width
                || frozenTarget.height != mainTarget.height
                || frozenFormat != format) {
            if (frozenTarget != null) {
                frozenTarget.destroyBuffers();
            }
            frozenTarget = new TextureTarget(
                    "FPV last-good frame", mainTarget.width, mainTarget.height, false, format);
            frozenFormat = format;
        }
    }

    private void captureFrozenFrame(RenderTarget mainTarget) {
        ensureFrozenTarget(mainTarget);
        copyColor(mainTarget, frozenTarget);
    }

    private static void copyColor(RenderTarget source, RenderTarget destination) {
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(
                source.getColorTexture(),
                destination.getColorTexture(),
                0,
                0,
                0,
                0,
                0,
                Math.min(source.width, destination.width),
                Math.min(source.height, destination.height)
        );
    }

    private boolean renderThermal(RenderTarget input, RenderTarget output, ThermalState state) {
        try {
            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            ByteBuffer values = thermalUniformData(state);
            if (thermalUniforms == null || thermalUniforms.isClosed()) {
                thermalUniforms = RenderSystem.getDevice().createBuffer(
                        () -> "FPV thermal uniforms",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        values
                );
            } else {
                encoder.writeToBuffer(thermalUniforms.slice(), values);
            }

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "FPV thermal camera",
                    output.getColorTextureView(),
                    Optional.empty(),
                    null,
                    OptionalDouble.empty())) {
                pass.setPipeline(THERMAL_PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.bindTexture(
                        "InSampler",
                        input.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                );
                pass.bindTexture(
                        "DepthSampler",
                        input.getDepthTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                );
                pass.setUniform("ThermalConfig", thermalUniforms);
                pass.draw(3, 1, 0, 0);
            }
            return true;
        } catch (RuntimeException throwable) {
            thermalPipelineFailed = true;
            DroneMod.LOGGER.error("Disabling FPV thermal shader after renderer failure", throwable);
            return false;
        }
    }

    private boolean renderBloom(RenderTarget thermalFrame, RenderTarget output, ThermalState state) {
        try {
            ensureBloomTargets(thermalFrame);
            TextureTarget ping = bloomPingTarget;
            TextureTarget pong = bloomPongTarget;
            if (ping == null || pong == null) return false;

            renderSingleInput(thermalFrame, ping, BLOOM_EXTRACT_PIPELINE, "FPV thermal bloom extract");
            renderSingleInput(ping, pong, BLOOM_BLUR_HORIZONTAL_PIPELINE, "FPV thermal bloom horizontal");
            renderSingleInput(pong, ping, BLOOM_BLUR_VERTICAL_PIPELINE, "FPV thermal bloom vertical");

            CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
            ByteBuffer values = ByteBuffer.allocateDirect(BLOOM_UNIFORM_BYTES)
                    .order(ByteOrder.nativeOrder());
            putVec4(values, state.palette().ordinal(), 0.60F, 0.0F, 0.0F);
            values.flip();
            if (bloomUniforms == null || bloomUniforms.isClosed()) {
                bloomUniforms = RenderSystem.getDevice().createBuffer(
                        () -> "FPV thermal bloom uniforms",
                        GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                        values
                );
            } else {
                encoder.writeToBuffer(bloomUniforms.slice(), values);
            }

            try (RenderPass pass = encoder.createRenderPass(
                    () -> "FPV thermal bloom composite",
                    output.getColorTextureView(),
                    Optional.empty(),
                    null,
                    OptionalDouble.empty())) {
                pass.setPipeline(BLOOM_COMPOSITE_PIPELINE);
                RenderSystem.bindDefaultUniforms(pass);
                pass.bindTexture(
                        "InSampler",
                        thermalFrame.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
                );
                pass.bindTexture(
                        "BloomSampler",
                        ping.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
                );
                pass.setUniform("BloomConfig", bloomUniforms);
                pass.draw(3, 1, 0, 0);
            }
            return true;
        } catch (RuntimeException throwable) {
            bloomPipelineFailed = true;
            DroneMod.LOGGER.error("Disabling FPV thermal bloom after renderer failure", throwable);
            return false;
        }
    }

    private static void renderSingleInput(RenderTarget input, RenderTarget output,
                                          RenderPipeline pipeline, String label) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        try (RenderPass pass = encoder.createRenderPass(
                () -> label,
                output.getColorTextureView(),
                Optional.empty(),
                null,
                OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.bindTexture(
                    "InSampler",
                    input.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            );
            pass.draw(3, 1, 0, 0);
        }
    }

    private void ensureBloomTargets(RenderTarget source) {
        GpuFormat format = source.getColorTexture().getFormat();
        if (bloomPingTarget == null
                || bloomPingTarget.width != source.width
                || bloomPingTarget.height != source.height
                || bloomFormat != format) {
            if (bloomPingTarget != null) bloomPingTarget.destroyBuffers();
            if (bloomPongTarget != null) bloomPongTarget.destroyBuffers();
            bloomPingTarget = new TextureTarget(
                    "FPV thermal bloom ping", source.width, source.height, false, format);
            bloomPongTarget = new TextureTarget(
                    "FPV thermal bloom pong", source.width, source.height, false, format);
            bloomFormat = format;
        }
    }

    private static ByteBuffer thermalUniformData(ThermalState state) {
        Minecraft minecraft = Minecraft.getInstance();
        float farPlane = Math.max(16.0F, minecraft.options.getEffectiveRenderDistance() * 16.0F);
        float gameTime = minecraft.level == null
                ? 0.0F
                : minecraft.level.getOverworldClockTime() % 24_000L / 24_000.0F;
        float dayHeat = Math.max(0.0F,
                (float) Math.cos((gameTime - 0.25F) * Math.PI * 2.0)) * 0.08F;
        float ambient = 0.08F + dayHeat;
        float frame = minecraft.level == null
                ? 0.0F
                : (float) (minecraft.level.getGameTime() & 0xFFFFL);

        ByteBuffer buffer = ByteBuffer.allocateDirect(THERMAL_UNIFORM_BYTES)
                .order(ByteOrder.nativeOrder());
        putVec4(buffer, state.palette().ordinal(), state.agcMode().ordinal(),
                state.manualAgcMinimum(), state.manualAgcMaximum());
        putVec4(buffer, state.focusMode().ordinal(), state.manualFocusDistance(),
                state.nuc().fpnReduction(), frame);
        putVec4(buffer, 0.01F, farPlane, ambient, 0.003F);
        buffer.flip();
        return buffer;
    }

    private static void putVec4(ByteBuffer buffer, float x, float y, float z, float w) {
        buffer.putFloat(x).putFloat(y).putFloat(z).putFloat(w);
    }

    private void releaseSessionTargets() {
        if (lowResolutionTarget != null) {
            lowResolutionTarget.destroyBuffers();
            lowResolutionTarget = null;
        }
        if (frozenTarget != null) {
            frozenTarget.destroyBuffers();
            frozenTarget = null;
        }
        if (bloomPingTarget != null) {
            bloomPingTarget.destroyBuffers();
            bloomPingTarget = null;
        }
        if (bloomPongTarget != null) {
            bloomPongTarget.destroyBuffers();
            bloomPongTarget = null;
        }
        lowResolutionFormat = null;
        frozenFormat = null;
        bloomFormat = null;
        sessionId = null;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        releaseSessionTargets();
        if (thermalUniforms != null) {
            thermalUniforms.close();
            thermalUniforms = null;
        }
        if (bloomUniforms != null) {
            bloomUniforms.close();
            bloomUniforms = null;
        }
        processedLastFrame = false;
    }

    private static RenderPipeline postPipeline(String pipelineName, String fragmentName,
                                               boolean bloomSampler, boolean bloomUniform) {
        BindGroupLayout.Builder bindings = BindGroupLayout.builder().withSampler("InSampler");
        if (bloomSampler) bindings.withSampler("BloomSampler");
        if (bloomUniform) bindings.withUniform("BloomConfig", UniformType.UNIFORM_BUFFER);
        return RenderPipeline.builder(RenderPipelines.POST_PROCESSING_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath(
                        DroneMod.MOD_ID, "pipeline/" + pipelineName))
                .withVertexShader(Identifier.fromNamespaceAndPath("minecraft", "core/screenquad"))
                .withFragmentShader(Identifier.fromNamespaceAndPath(
                        DroneMod.MOD_ID, "post/" + fragmentName))
                .withBindGroupLayout(bindings.build())
                .build();
    }
}
