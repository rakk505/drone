package com.modernity.drone.client.gui;

import com.modernity.drone.client.config.ControllerConfig;
import com.modernity.drone.client.config.FpvClientConfig;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Editable Betaflight RC-rate, super-rate, and expo profile. */
public final class RatesScreen extends FpvScreen {
    private static final int[] AXES = {ControllerConfig.YAW, ControllerConfig.PITCH, ControllerConfig.ROLL};
    private static final String[] NAMES = {"Yaw", "Pitch", "Roll"};

    public RatesScreen(Screen previous) {
        super(Component.literal("Rates"), previous);
    }

    @Override
    protected void init() {
        ControllerConfig config = FpvClientConfig.controller();
        int left = centerX() - 154;
        for (int column = 0; column < AXES.length; column++) {
            int axis = AXES[column];
            int x = left + column * 104;
            addRenderableWidget(new ValueSlider(x, 58, 100, "Rate", config.rcRate(axis), 0.0, 2.55,
                    value -> { config.setRcRate(axis, (float) value); saveRates(); }, RatesScreen::twoDecimals));
            addRenderableWidget(new ValueSlider(x, 84, 100, "Super", config.superRate(axis), 0.0, 1.0,
                    value -> { config.setSuperRate(axis, (float) value); saveRates(); }, RatesScreen::twoDecimals));
            addRenderableWidget(new ValueSlider(x, 110, 100, "Expo", config.expo(axis), 0.0, 1.0,
                    value -> { config.setExpo(axis, (float) value); saveRates(); }, RatesScreen::twoDecimals));
        }
        addRenderableWidget(Button.builder(Component.literal("Slow reset"), button -> {
            config.resetRates(false);
            saveRates();
            rebuild();
        }).bounds(centerX() - 154, 140, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fast reset"), button -> {
            config.resetRates(true);
            saveRates();
            rebuild();
        }).bounds(centerX() + 4, 140, 150, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Back"), button -> returnToPrevious())
                .bounds(centerX() - 100, height - 34, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = centerX() - 154;
        for (int column = 0; column < NAMES.length; column++) {
            graphics.centeredText(font, NAMES[column], left + column * 104 + 50, 43, TEXT);
        }
        drawRateChart(graphics, centerX() - 150, 169, 300, Math.max(22, height - 212));
    }

    private void drawRateChart(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        if (height < 4) return;
        graphics.fill(x, y, x + width, y + height, 0xA0080D0B);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_EDGE);
        float maximum = 1.0F;
        ControllerConfig config = FpvClientConfig.controller();
        for (int axis : AXES) {
            maximum = Math.max(maximum, ControllerConfig.betaflightRate(1.0F,
                    config.rcRate(axis), config.superRate(axis), config.expo(axis)));
        }
        int[] colors = {0xFFFF7272, 0xFF72FF94, 0xFF78AEFF};
        for (int axisIndex = 0; axisIndex < AXES.length; axisIndex++) {
            int axis = AXES[axisIndex];
            for (int sample = 0; sample < width; sample++) {
                float input = sample / (float) (width - 1);
                float rate = ControllerConfig.betaflightRate(input,
                        config.rcRate(axis), config.superRate(axis), config.expo(axis));
                int py = y + height - 2 - Math.round(rate / maximum * (height - 3));
                graphics.fill(x + sample, py, x + sample + 1, Math.min(y + height - 1, py + 2), colors[axisIndex]);
            }
        }
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private static void saveRates() {
        FpvClientConfig.saveRates();
    }

    private static String twoDecimals(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
