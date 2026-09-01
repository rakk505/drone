package com.modernity.drone.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.modernity.drone.DroneMod;
import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.client.video.FpvVisualHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/** Hooks the FPV settings into vanilla UI and applies camera preferences. */
@EventBusSubscriber(modid = DroneMod.MOD_ID, value = Dist.CLIENT)
public final class FpvGuiEvents {
    private static boolean promptedThisSession;

    private FpvGuiEvents() {
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof ControlsScreen screen)) return;
        event.addListener(Button.builder(Component.literal("FPV Calibration"), button ->
                        Minecraft.getInstance().gui.setScreen(new FpvSettingsScreen(screen)))
                .bounds(screen.width / 2 - 155, Math.max(32, screen.height - 52), 310, 20)
                .build());
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean shiftDown = InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(minecraft.getWindow(), GLFW.GLFW_KEY_RIGHT_SHIFT);
        double delta = event.getScrollDeltaY();
        if (delta == 0.0) return;
        if (shiftDown && DroneControlClient.requestGogglesChannelStep(delta > 0.0 ? 1 : -1)) {
            event.setCanceled(true);
            return;
        }
        if (FpvVisualHooks.handleThermalScroll(delta, shiftDown)) {
            event.setCanceled(true);
        }
    }

    public static void maybeShowFirstRun(Minecraft minecraft) {
        FpvClientConfig.initialize();
        if (!promptedThisSession && !FpvClientConfig.setupComplete() && minecraft.gui.screen() == null) {
            promptedThisSession = true;
            minecraft.gui.setScreen(new FirstRunWizardScreen(null));
        }
    }

    public static void resetSessionPrompt() {
        promptedThisSession = false;
    }
}
