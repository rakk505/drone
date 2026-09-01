package com.modernity.drone.client.gui.osd;

import com.modernity.drone.client.osd.OsdLayout;
import com.modernity.drone.client.osd.OsdLayoutStore;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** File picker for layouts exported to {@code cubeflight/osd_layouts}. */
public final class OsdImportScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int LIST_TOP = 40;
    private static final int LIST_WIDTH = 300;

    private final Screen previous;
    private final Consumer<OsdLayout> onImport;
    private List<Path> layoutFiles = List.of();
    private int scrollOffset;
    private int selectedIndex = -1;
    private @Nullable String errorMessage;

    public OsdImportScreen(Screen previous, Consumer<OsdLayout> onImport) {
        super(Component.literal("Import OSD Layout"));
        this.previous = previous;
        this.onImport = onImport;
    }

    @Override
    protected void init() {
        layoutFiles = OsdLayoutStore.listExports();
        scrollOffset = clamp(scrollOffset, 0, maximumScroll());
        if (selectedIndex >= layoutFiles.size()) {
            selectedIndex = -1;
        }
        int buttonWidth = Math.min(200, Math.max(80, width / 2 - 12));
        int gap = 8;
        int left = width / 2 - buttonWidth - gap / 2;
        int right = width / 2 + gap / 2;
        addRenderableWidget(Button.builder(Component.literal("Import Selected"), button -> importSelected())
                .bounds(left, height - 30, buttonWidth, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> onClose())
                .bounds(right, height - 30, buttonWidth, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, OsdBuilderScreen.COLOR_BACKGROUND);
        graphics.centeredText(font, "Import OSD Layout", width / 2, 12, OsdBuilderScreen.COLOR_TEXT);
        if (layoutFiles.isEmpty()) {
            graphics.centeredText(font, "No layout files found in cubeflight/osd_layouts/",
                    width / 2, height / 2, OsdBuilderScreen.COLOR_TEXT_DIM);
        } else {
            graphics.centeredText(font, layoutFiles.size() + " layout(s) available",
                    width / 2, 26, OsdBuilderScreen.COLOR_TEXT_DIM);
            renderRows(graphics, mouseX, mouseY);
        }
        if (errorMessage != null) {
            graphics.centeredText(font, errorMessage, width / 2, height - 43, 0xFFFF7777);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int listX = width / 2 - LIST_WIDTH / 2;
        int visible = visibleRows();
        int bottom = Math.min(height - 50, LIST_TOP + visible * ROW_HEIGHT);
        graphics.enableScissor(listX, LIST_TOP, listX + LIST_WIDTH, bottom);
        for (int visibleIndex = 0; visibleIndex < visible; visibleIndex++) {
            int index = visibleIndex + scrollOffset;
            if (index >= layoutFiles.size()) {
                break;
            }
            int y = LIST_TOP + visibleIndex * ROW_HEIGHT;
            boolean selected = index == selectedIndex;
            boolean hovered = mouseX >= listX && mouseX < listX + LIST_WIDTH
                    && mouseY >= y && mouseY < y + ROW_HEIGHT;
            int background = selected ? 0xFF3A3100 : hovered ? 0xFF2A2A2A : OsdBuilderScreen.COLOR_BACKGROUND;
            graphics.fill(listX, y, listX + LIST_WIDTH, y + ROW_HEIGHT, background);
            if (selected) {
                graphics.fill(listX, y, listX + 2, y + ROW_HEIGHT, OsdBuilderScreen.COLOR_PRIMARY);
            }
            String filename = truncate(layoutFiles.get(index).getFileName().toString(), LIST_WIDTH - 12);
            graphics.text(font, filename, listX + 6, y + (ROW_HEIGHT - font.lineHeight) / 2,
                    selected ? OsdBuilderScreen.COLOR_PRIMARY
                            : hovered ? OsdBuilderScreen.COLOR_TEXT : 0xFFCCCCCC,
                    false);
        }
        graphics.disableScissor();
        if (scrollOffset > 0) {
            graphics.centeredText(font, "▲ Scroll up", width / 2, 30, 0xFF888888);
        }
        if (scrollOffset + visible < layoutFiles.size()) {
            graphics.centeredText(font, "▼ Scroll down", width / 2,
                    LIST_TOP + visible * ROW_HEIGHT + 2, 0xFF888888);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || layoutFiles.isEmpty()) {
            return false;
        }
        int listX = width / 2 - LIST_WIDTH / 2;
        int visible = visibleRows();
        if (event.x() < listX || event.x() >= listX + LIST_WIDTH
                || event.y() < LIST_TOP || event.y() >= LIST_TOP + visible * ROW_HEIGHT) {
            return false;
        }
        int index = scrollOffset + (int) ((event.y() - LIST_TOP) / ROW_HEIGHT);
        if (index < 0 || index >= layoutFiles.size()) {
            return false;
        }
        selectedIndex = index;
        if (doubleClick) {
            importSelected();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int listX = width / 2 - LIST_WIDTH / 2;
        if (mouseX >= listX && mouseX < listX + LIST_WIDTH
                && mouseY >= LIST_TOP && mouseY < height - 45) {
            scrollOffset = clamp(scrollOffset - (int) Math.signum(scrollY), 0, maximumScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(previous);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void importSelected() {
        if (selectedIndex < 0 || selectedIndex >= layoutFiles.size()) {
            errorMessage = "Select a layout first";
            return;
        }
        try {
            onImport.accept(OsdLayoutStore.importLayout(layoutFiles.get(selectedIndex)));
            minecraft.gui.setScreen(previous);
        } catch (Exception exception) {
            String message = exception.getMessage();
            errorMessage = "Import failed: "
                    + (message == null || message.isBlank() ? exception.getClass().getSimpleName() : message);
        }
    }

    private int visibleRows() {
        return Math.max(1, (height - LIST_TOP - 50) / ROW_HEIGHT);
    }

    private int maximumScroll() {
        return Math.max(0, layoutFiles.size() - visibleRows());
    }

    private String truncate(String value, int maximumWidth) {
        if (font.width(value) <= maximumWidth) {
            return value;
        }
        String clipped = value;
        while (clipped.length() > 1 && font.width(clipped + "..") > maximumWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + "..";
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
