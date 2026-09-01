package com.modernity.drone.mixin.client;

import com.modernity.drone.client.video.FpvFrameProcessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs the camera-feed framebuffer pass after the world, but before vanilla draws the GUI. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private RenderTarget mainRenderTarget;

    @Inject(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/render/GuiRenderer;render()V"
            )
    )
    private void fpvdrone$processCameraFeed(DeltaTracker deltaTracker, boolean renderLevel,
                                            CallbackInfo callback) {
        if (renderLevel) {
            FpvFrameProcessor.get().process(mainRenderTarget);
        }
    }
}
