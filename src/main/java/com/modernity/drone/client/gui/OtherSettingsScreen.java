package com.modernity.drone.client.gui;

import com.modernity.drone.client.config.FpvClientConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class OtherSettingsScreen extends FpvScreen {
    public OtherSettingsScreen(Screen previous) {
        super(Component.literal("Display & Camera"), previous);
    }

    @Override
    protected void init() {
        int x = centerX() - 130;
        int y = 46;
        addRenderableWidget(new ValueSlider(x, y, 260, "Default camera angle", FpvClientConfig.cameraAngle(), 0.0, 90.0,
                value -> FpvClientConfig.setCameraAngle((float) value), value -> Math.round(value) + "°"));
        addRenderableWidget(new ValueSlider(x, y + 28, 260, "FPV field of view", FpvClientConfig.fov(), 60.0, 180.0,
                value -> FpvClientConfig.setFov((float) value), value -> Math.round(value) + "°"));
        addRenderableWidget(Button.builder(forceKbmLabel(), button -> {
            FpvClientConfig.setForceKeyboardMouse(!FpvClientConfig.forceKeyboardMouse());
            button.setMessage(forceKbmLabel());
        }).bounds(x, y + 64, 260, 20).build());
        addRenderableWidget(Button.builder(stickOverlayLabel(), button -> {
            FpvClientConfig.setStickOverlay(!FpvClientConfig.stickOverlay());
            button.setMessage(stickOverlayLabel());
        }).bounds(x, y + 92, 260, 20).build());
        addRenderableWidget(Button.builder(resolutionLabel(), button -> {
            FpvClientConfig.cycleVideoResolution();
            button.setMessage(resolutionLabel());
        }).bounds(x, y + 120, 260, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> returnToPrevious())
                .bounds(centerX() - 100, height - 38, 200, 20).build());
    }

    private static Component forceKbmLabel() {
        return Component.literal("Force Keyboard & Mouse: " + onOff(FpvClientConfig.forceKeyboardMouse()));
    }

    private static Component stickOverlayLabel() {
        return Component.literal("Stick Overlay: " + onOff(FpvClientConfig.stickOverlay()));
    }

    private static Component resolutionLabel() {
        return Component.literal("Video Resolution: " + FpvClientConfig.videoResolution().displayName());
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }
}
