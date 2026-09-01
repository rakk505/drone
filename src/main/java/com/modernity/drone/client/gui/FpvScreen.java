package com.modernity.drone.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Shared, compact visual shell used by the FPV settings and setup wizard. */
public abstract class FpvScreen extends Screen {
    protected static final int PANEL = 0xE0121818;
    protected static final int PANEL_EDGE = 0xFF5C7567;
    protected static final int ACCENT = 0xFF9DFFB3;
    protected static final int TEXT = 0xFFE4F4E8;
    protected static final int MUTED = 0xFF9AA9A0;

    protected final @Nullable Screen previous;

    protected FpvScreen(Component title, @Nullable Screen previous) {
        super(title);
        this.previous = previous;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int left = Math.max(8, width / 2 - 170);
        int right = Math.min(width - 8, width / 2 + 170);
        graphics.fill(left, 8, right, height - 8, PANEL);
        graphics.fill(left, 8, right, 9, PANEL_EDGE);
        graphics.fill(left, height - 9, right, height - 8, PANEL_EDGE);
        graphics.centeredText(font, title, width / 2, 17, ACCENT);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    protected int centerX() {
        return width / 2;
    }

    protected void returnToPrevious() {
        minecraft.gui.setScreen(previous);
    }

    @Override
    public void onClose() {
        returnToPrevious();
    }
}
