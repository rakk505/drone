package com.modernity.drone.client;

import com.modernity.drone.DroneMod;
import com.modernity.drone.client.audio.FpvAudioEngine;
import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.client.gui.osd.OsdScreens;
import com.modernity.drone.client.thermal.ThermalWorldRenderer;
import com.modernity.drone.client.video.FpvVisualHooks;
import com.modernity.drone.client.video.FpvFrameProcessor;
import com.modernity.drone.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStartedEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

@EventBusSubscriber(modid = DroneMod.MOD_ID, value = Dist.CLIENT)
public final class DroneClientEvents {
    private DroneClientEvents() {
    }

    @SubscribeEvent
    public static void onClientStarted(ClientStartedEvent event) {
        // Match V1.1.4's client-setup preload so an explosion on the first
        // playable tick is not lost while its OGGs are still decoding.
        FpvAudioEngine.initIfNeeded();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        DroneSurfHandler.tick(minecraft);
        DroneControlClient.tick(minecraft);
        ThermalWorldRenderer.tick(minecraft);
        FpvVisualHooks.consumeKeyClicks(minecraft);
        FpvAudioEngine.tick();
    }

    @SubscribeEvent
    public static void onUse(InputEvent.InteractionKeyMappingTriggered event) {
        if (DroneControlClient.isActive()) {
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (!event.isUseItem()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof DroneEntity drone)) {
            return;
        }
        if (minecraft.player.getItemInHand(event.getHand()).is(DroneMod.BETAFLIGHT.get())) {
            OsdScreens.openFor(drone);
            event.setSwingHand(false);
            event.setCanceled(true);
            return;
        }
        if (!DroneControlClient.isControllerFor(
                drone, minecraft.player.getItemInHand(event.getHand()))) return;
        DroneControlClient.requestActivation(drone);
    }

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        DroneEntity drone = DroneControlClient.currentDrone();
        if (drone == null) {
            return;
        }
        if (Minecraft.getInstance().options.getCameraType() != CameraType.FIRST_PERSON) {
            event.setYaw(drone.getYRot((float) event.getPartialTick()));
            event.setPitch(drone.getXRot((float) event.getPartialTick()));
            event.setRoll(0.0F);
            return;
        }
        FpvCameraTransform.Angles angles = FpvCameraTransform.angles(
                drone, (float) event.getPartialTick());
        event.setYaw(angles.yaw());
        event.setPitch(angles.pitch());
        event.setRoll(angles.roll());
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (DroneControlClient.isActive()) {
            event.setFOV(FpvClientConfig.fov());
        }
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (DroneControlClient.isActive()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRenderGuiLayer(RenderGuiLayerEvent.Pre event) {
        if (!DroneControlClient.isActive()) return;
        var layer = event.getName();
        if (layer.equals(VanillaGuiLayers.HOTBAR)
                || layer.equals(VanillaGuiLayers.SELECTED_ITEM_NAME)
                || layer.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR_BACKGROUND)
                || layer.equals(VanillaGuiLayers.CONTEXTUAL_INFO_BAR)
                || layer.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)
                || layer.equals(VanillaGuiLayers.FOOD_LEVEL)
                || layer.equals(VanillaGuiLayers.PLAYER_HEALTH)
                || layer.equals(VanillaGuiLayers.ARMOR_LEVEL)
                || layer.equals(VanillaGuiLayers.CROSSHAIR)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        DroneControlClient.clear(false);
        FpvVisualHooks.onLogout();
        FpvAudioEngine.onLogout();
        DroneSurfHandler.reset();
        ThermalWorldRenderer.reset();
    }

    @SubscribeEvent
    public static void onClientStopping(ClientStoppingEvent event) {
        // OpenAL still owns a live context during ClientStoppingEvent.
        FpvAudioEngine.shutdown();
        FpvFrameProcessor.get().close();
        ThermalWorldRenderer.shutdown();
    }
}
