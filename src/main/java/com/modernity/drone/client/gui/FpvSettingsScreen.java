package com.modernity.drone.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Top-level equivalent of the V1.1.4 Remote-Controller Settings screen. */
public final class FpvSettingsScreen extends FpvScreen {
    public FpvSettingsScreen(@Nullable Screen previous) {
        super(Component.literal("Remote-Controller Settings"), previous);
    }

    @Override
    protected void init() {
        int x = centerX() - 100;
        int y = 48;
        addRenderableWidget(Button.builder(Component.literal("Controller & Channel Mapping"),
                button -> minecraft.gui.setScreen(new ControllerSettingsScreen(this))).bounds(x, y, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Rates"),
                button -> minecraft.gui.setScreen(new RatesScreen(this))).bounds(x, y + 28, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Drone Build"),
                button -> minecraft.gui.setScreen(new DroneBuildScreen(this))).bounds(x, y + 56, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Other Settings"),
                button -> minecraft.gui.setScreen(new OtherSettingsScreen(this))).bounds(x, y + 84, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Run Calibration Wizard"),
                button -> minecraft.gui.setScreen(new FirstRunWizardScreen(this))).bounds(x, y + 112, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Done"), button -> returnToPrevious())
                .bounds(x, height - 38, 200, 20).build());
    }
}
