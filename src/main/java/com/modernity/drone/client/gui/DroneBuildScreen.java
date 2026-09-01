package com.modernity.drone.client.gui;

import com.modernity.drone.client.config.DroneBuildConfig;
import com.modernity.drone.client.config.FpvClientConfig;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Functional version of the V1.1.4 drone-build editor. */
public final class DroneBuildScreen extends FpvScreen {
    private Page page = Page.MOTORS;

    public DroneBuildScreen(Screen previous) {
        super(Component.literal("Drone Build"), previous);
    }

    @Override
    protected void init() {
        int left = centerX() - 155;
        addRenderableWidget(Button.builder(Component.literal("Motors"), button -> switchPage(Page.MOTORS))
                .bounds(left, 38, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Frame"), button -> switchPage(Page.FRAME))
                .bounds(left + 106, 38, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Appearance"), button -> switchPage(Page.APPEARANCE))
                .bounds(left + 212, 38, 98, 20).build());
        switch (page) {
            case MOTORS -> addMotorControls(left);
            case FRAME -> addFrameControls(left);
            case APPEARANCE -> addAppearanceControls(left);
        }
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> returnToPrevious())
                .bounds(centerX() - 100, height - 34, 200, 20).build());
    }

    private void addMotorControls(int left) {
        DroneBuildConfig build = FpvClientConfig.build();
        slider(left, 70, "Motor KV", build.motorKv(), 1000, 5000,
                value -> build.setMotorKv((int) Math.round(value)), value -> Integer.toString((int) Math.round(value)));
        slider(left + 158, 70, "Battery", build.batteryCells(), 1, 8,
                value -> build.setBatteryCells((int) Math.round(value)), value -> (int) Math.round(value) + "S");
        slider(left, 98, "Motor width", build.motorWidthMm(), 10, 40,
                value -> build.setMotorWidthMm((float) value), value -> oneDecimal(value) + " mm");
        slider(left + 158, 98, "Motor height", build.motorHeightMm(), 2, 15,
                value -> build.setMotorHeightMm((float) value), value -> oneDecimal(value) + " mm");
        slider(left, 126, "Prop diameter", build.propDiameterInches(), 2, 10,
                value -> build.setPropDiameterInches((float) value), value -> oneDecimal(value) + " in");
        slider(left + 158, 126, "Prop pitch", build.propPitchInches(), 1, 8,
                value -> build.setPropPitchInches((float) value), value -> oneDecimal(value) + " in");
        slider(left, 154, "Blades", build.propBlades(), 2, 6,
                value -> build.setPropBlades((int) Math.round(value)), value -> Integer.toString((int) Math.round(value)));
    }

    private void addFrameControls(int left) {
        DroneBuildConfig build = FpvClientConfig.build();
        slider(left, 70, "Mass", build.massGrams(), 50, 1000,
                value -> build.setMassGrams((float) value), value -> Math.round(value) + " g");
        slider(left + 158, 70, "Width", build.frameWidthMm(), 50, 400,
                value -> build.setFrameWidthMm((float) value), value -> Math.round(value) + " mm");
        slider(left, 98, "Height", build.frameHeightMm(), 10, 100,
                value -> build.setFrameHeightMm((float) value), value -> Math.round(value) + " mm");
        slider(left + 158, 98, "Length", build.frameLengthMm(), 50, 400,
                value -> build.setFrameLengthMm((float) value), value -> Math.round(value) + " mm");
    }

    private void addAppearanceControls(int left) {
        DroneBuildConfig build = FpvClientConfig.build();
        addRenderableWidget(Button.builder(Component.literal("Pro camera: " + onOff(build.showProCamera())), button -> {
            build.setShowProCamera(!build.showProCamera()); saveAndRebuild();
        }).bounds(left, 70, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Hero cam: " + onOff(build.heroCamera())), button -> {
            build.setHeroCamera(!build.heroCamera()); saveAndRebuild();
        }).bounds(left + 106, 70, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Toothpick: " + onOff(build.toothpick())), button -> {
            build.setToothpick(!build.toothpick()); saveAndRebuild();
        }).bounds(left + 212, 70, 98, 20).build());
        slider(left, 104, "Red", build.red(), 0, 255,
                value -> build.setRed((int) Math.round(value)), value -> Integer.toString((int) Math.round(value)));
        slider(left + 158, 104, "Green", build.green(), 0, 255,
                value -> build.setGreen((int) Math.round(value)), value -> Integer.toString((int) Math.round(value)));
        slider(left, 132, "Blue", build.blue(), 0, 255,
                value -> build.setBlue((int) Math.round(value)), value -> Integer.toString((int) Math.round(value)));
    }

    private void slider(int x, int y, String label, double value, double min, double max,
                        java.util.function.DoubleConsumer setter, java.util.function.DoubleFunction<String> formatter) {
        addRenderableWidget(new ValueSlider(x, y, 152, label, value, min, max,
                changed -> { setter.accept(changed); FpvClientConfig.saveBuild(); }, formatter));
    }

    private void switchPage(Page next) {
        page = next;
        clearWidgets();
        init();
    }

    private void saveAndRebuild() {
        FpvClientConfig.saveBuild();
        clearWidgets();
        init();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        DroneBuildConfig build = FpvClientConfig.build();
        int color = 0xFF000000 | build.red() << 16 | build.green() << 8 | build.blue();
        graphics.fill(centerX() - 38, 181, centerX() + 38, 193, color);
        graphics.centeredText(font, "Preview color", centerX(), 197, MUTED);
    }

    private static String oneDecimal(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String onOff(boolean value) {
        return value ? "ON" : "OFF";
    }

    private enum Page { MOTORS, FRAME, APPEARANCE }
}
