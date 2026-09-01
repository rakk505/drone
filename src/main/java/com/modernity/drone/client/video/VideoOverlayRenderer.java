package com.modernity.drone.client.video;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Lightweight visual fallback used until the world framebuffer hook consumes VideoFeedState. */
public final class VideoOverlayRenderer {
    private VideoOverlayRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics, VideoFeedState.FrameDecision decision,
                              float signal, long nowMillis) {
        if (!decision.active()) {
            return;
        }
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();
        if (decision.noSignal() && !decision.replayPreviousFrame()) {
            graphics.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        if (signal >= 0.72F) {
            return;
        }
        int severity = Math.max(0, Math.min(72, Math.round((0.72F - signal) * 100.0F)));
        int phase = (int) (nowMillis / Math.max(16L, 16L + severity));
        int bars = 1 + severity / 12;
        for (int index = 0; index < bars; index++) {
            int hash = mix(phase, index);
            int y = Math.floorMod(hash, Math.max(1, height));
            int barHeight = 1 + Math.floorMod(hash >>> 8, 2 + severity / 18);
            int alpha = 12 + severity;
            graphics.fill(0, y, width, Math.min(height, y + barHeight), alpha << 24 | 0x00FFFFFF);
        }
        if (decision.replayPreviousFrame()) {
            graphics.fill(0, 0, width, height, Math.min(42, 8 + severity / 2) << 24);
        }
    }

    private static int mix(int phase, int index) {
        int value = phase * 0x45D9F3B + index * 0x119DE1F3;
        value = (value ^ value >>> 16) * 0x45D9F3B;
        return value ^ value >>> 16;
    }
}
