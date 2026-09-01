package com.modernity.drone.client.thermal;

import com.modernity.drone.client.video.FpvFrameProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Safe 26.2 fallback for the legacy framebuffer shader chain. It supplies the visible sensor tint,
 * scan/FPN texture, ROI and camera symbology while the copied shader resources remain available to
 * a renderer-level integration.
 */
public final class ThermalOverlayRenderer {
    private static final int OSD_COLOR = 0xFFCCCCCC;
    private static final int OUTLINE = 0xFF000000;
    private static final int RETICLE = 0xFFAAAAAA;
    private static final int ROI = 0x44AAAAAA;

    private ThermalOverlayRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, ThermalState state, float batteryFraction,
                              long nowMillis) {
        if (!state.active()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (FpvFrameProcessor.get().needsThermalOverlayFallback() && !state.nuc().frozen()) {
            renderPaletteTint(graphics, state.palette(), width, height);
            renderSensorTexture(graphics, state, width, height, nowMillis);
        }
        renderReticle(graphics, width, height);
        if (state.agcMode() == AgcMode.ROI) {
            renderRoi(graphics, width, height);
        }
        Font font = Minecraft.getInstance().font;
        outlined(graphics, font, "BAT " + Math.round(batteryFraction * 100.0F) + "%", 6,
                height - font.lineHeight - 6);
        String right = state.palette().label() + "  " + agcLabel(state.agcMode()) + "  " + focusLabel(state);
        outlined(graphics, font, right, width - font.width(right) - 6, height - font.lineHeight - 6);
        if (state.nuc().frozen()) {
            String nuc = "NUC";
            outlined(graphics, font, nuc, (width - font.width(nuc)) / 2, height / 2 + 14);
        }
    }

    private static void renderPaletteTint(GuiGraphicsExtractor graphics, ThermalPalette palette,
                                          int width, int height) {
        switch (palette) {
            case WHITE_HOT -> graphics.fill(0, 0, width, height, 0x20D8E2E0);
            case BLACK_HOT -> graphics.fill(0, 0, width, height, 0x5900080A);
            case IRONBOW -> {
                graphics.fillGradient(0, 0, width, height / 2, 0x34230052, 0x283E0D46);
                graphics.fillGradient(0, height / 2, width, height, 0x28480B23, 0x386E2600);
            }
        }
    }

    private static void renderSensorTexture(GuiGraphicsExtractor graphics, ThermalState state,
                                            int width, int height, long nowMillis) {
        int phase = (int) (nowMillis / 50L);
        float reduction = state.nuc().fpnReduction();
        int lineAlpha = Math.max(4, Math.round(18.0F * (1.0F - reduction)));
        for (int y = phase & 3; y < height; y += 4) {
            graphics.fill(0, y, width, y + 1, lineAlpha << 24);
        }
        for (int x = 0; x < width; x += 17) {
            int noise = mix(x, phase) & 7;
            int alpha = Math.max(1, lineAlpha / 3 + noise);
            graphics.fill(x, 0, x + 1, height, alpha << 24);
        }
        int border = Math.max(8, Math.min(width, height) / 18);
        graphics.fillGradient(0, 0, width, border, 0x50000000, 0x00000000);
        graphics.fillGradient(0, height - border, width, height, 0x00000000, 0x50000000);
        graphics.fill(0, 0, border, height, 0x26000000);
        graphics.fill(width - border, 0, width, height, 0x26000000);
    }

    private static int mix(int x, int phase) {
        int value = x * 0x45D9F3B + phase * 0x119DE1F3;
        value = (value ^ value >>> 16) * 0x45D9F3B;
        return value ^ value >>> 16;
    }

    private static void renderReticle(GuiGraphicsExtractor graphics, int width, int height) {
        int cx = width / 2;
        int cy = height / 2;
        graphics.fill(cx - 8, cy, cx - 2, cy + 1, RETICLE);
        graphics.fill(cx + 3, cy, cx + 9, cy + 1, RETICLE);
        graphics.fill(cx, cy - 8, cx + 1, cy - 2, RETICLE);
        graphics.fill(cx, cy + 3, cx + 1, cy + 9, RETICLE);
    }

    private static void renderRoi(GuiGraphicsExtractor graphics, int width, int height) {
        int roiWidth = width / 4;
        int roiHeight = height / 4;
        int x = (width - roiWidth) / 2;
        int y = (height - roiHeight) / 2;
        graphics.outline(x, y, roiWidth, roiHeight, ROI);
    }

    private static String agcLabel(AgcMode mode) {
        return switch (mode) {
            case AUTO -> "AUTO";
            case ROI -> "ROI";
            case MANUAL -> "MAN";
        };
    }

    private static String focusLabel(ThermalState state) {
        return switch (state.focusMode()) {
            case OFF -> "";
            case AUTO -> "AF";
            case MANUAL -> "MF " + (int) state.manualFocusDistance() + "m";
        };
    }

    private static void outlined(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
        graphics.text(font, text, x - 1, y, OUTLINE, false);
        graphics.text(font, text, x + 1, y, OUTLINE, false);
        graphics.text(font, text, x, y - 1, OUTLINE, false);
        graphics.text(font, text, x, y + 1, OUTLINE, false);
        graphics.text(font, text, x, y, OSD_COLOR, false);
    }
}
