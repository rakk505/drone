package com.modernity.drone.client.video;

import com.modernity.drone.client.DroneClient;
import com.modernity.drone.client.DroneControlClient;
import com.modernity.drone.client.config.FpvClientConfig;
import com.modernity.drone.client.input.ThermalKeyState;
import com.modernity.drone.client.signal.SignalState;
import com.modernity.drone.client.thermal.AgcMode;
import com.modernity.drone.client.thermal.FocusMode;
import com.modernity.drone.client.thermal.ThermalState;
import com.modernity.drone.entity.DroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Narrow integration surface for the existing client event subscriber.
 * Call {@link #consumeKeyClicks(Minecraft)} once from the client post-tick event and
 * {@link #handleThermalScroll(double, boolean)} from the mouse-scroll event.
 */
public final class FpvVisualHooks {
    private static final int ACCENT = 0xFFAA36;

    private FpvVisualHooks() {
    }

    public static void consumeKeyClicks(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }
        DroneEntity drone = DroneControlClient.currentDrone();
        while (DroneClient.CYCLE_RESOLUTION.consumeClick()) {
            // Survival resolution is distance-driven in V1.1.4; only creative can select it.
            if (drone != null && minecraft.player.isCreative()) {
                FpvClientConfig.VideoResolution selected = FpvClientConfig.cycleVideoResolution();
                VideoFeedState.get().setResolution(VideoResolution.valueOf(selected.name()));
                minecraft.player.sendOverlayMessage(
                        Component.literal("Resolution: " + selected.displayName()).withColor(ACCENT)
                );
            }
        }
        while (DroneClient.THERMAL_TOGGLE.consumeClick()) {
            if (drone != null && minecraft.player.isCreative() && !drone.isThermal()) {
                boolean enabled = ThermalKeyState.toggle();
                minecraft.player.sendOverlayMessage(
                        Component.translatable(enabled ? "fpvdrone.thermal.on" : "fpvdrone.thermal.off")
                                .withColor(ACCENT)
                );
            }
        }
        while (DroneClient.THERMAL_NUC.consumeClick()) {
            if (thermalActive(drone)) {
                ThermalKeyState.requestNuc();
            }
        }
        while (DroneClient.THERMAL_AGC_MODE.consumeClick()) {
            if (thermalActive(drone)) {
                ThermalKeyState.cycleAgcMode();
                AgcMode mode = ThermalState.get().acceptExternalAgcCycle();
                minecraft.player.sendOverlayMessage(Component.literal("AGC: " + mode.name()).withColor(ACCENT));
            }
        }
        while (DroneClient.THERMAL_FOCUS_MODE.consumeClick()) {
            if (thermalActive(drone)) {
                ThermalState state = ThermalState.get();
                ThermalKeyState.cycleFocusMode();
                FocusMode mode = state.acceptExternalFocusCycle();
                String label = switch (mode) {
                    case OFF -> "Focus: OFF";
                    case AUTO -> "Focus: AUTO";
                    case MANUAL -> "Focus: MANUAL (" + (int) state.manualFocusDistance() + "m)";
                };
                minecraft.player.sendOverlayMessage(Component.literal(label).withColor(ACCENT));
            }
        }
    }

    /** @return true when the scroll event should be cancelled. */
    public static boolean handleThermalScroll(double scrollDelta, boolean shiftDown) {
        DroneEntity drone = DroneControlClient.currentDrone();
        if (!thermalActive(drone)) {
            return false;
        }
        ThermalState state = ThermalState.get();
        state.synchronizeKeyState(System.currentTimeMillis());
        float delta = (float) scrollDelta;
        if (state.agcMode() == AgcMode.MANUAL) {
            if (shiftDown) {
                state.adjustManualAgcGain(delta * 0.02F);
                return true;
            }
            if (state.focusMode() != FocusMode.MANUAL) {
                state.adjustManualAgcOffset(delta * 0.02F);
                return true;
            }
        }
        if (state.focusMode() == FocusMode.MANUAL) {
            state.adjustManualFocusDistance(delta * 2.0F);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.sendOverlayMessage(
                        Component.literal("Focus: " + (int) state.manualFocusDistance() + "m").withColor(ACCENT)
                );
            }
            return true;
        }
        return drone != null;
    }

    public static void onLogout() {
        SignalState.get().reset();
        VideoFeedState.get().end();
        ThermalState.get().reset(System.currentTimeMillis());
    }

    private static boolean thermalActive(DroneEntity drone) {
        return drone != null && (drone.isThermal() || ThermalKeyState.enabled());
    }
}
