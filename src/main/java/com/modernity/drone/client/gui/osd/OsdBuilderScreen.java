package com.modernity.drone.client.gui.osd;

import com.modernity.drone.client.osd.OsdElement;
import com.modernity.drone.client.osd.OsdElementDefinition;
import com.modernity.drone.client.osd.OsdElementRegistry;
import com.modernity.drone.client.osd.OsdLayout;
import com.modernity.drone.client.osd.OsdLayoutStore;
import com.modernity.drone.entity.DroneEntity;
import com.modernity.drone.flight.DroneFlightConfig;
import com.modernity.drone.network.DroneNamePayload;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Betaflight Configurator-style editor for the complete 30x16 V1.1.4 OSD layout.
 * Changes stay isolated until Save is pressed.
 */
public final class OsdBuilderScreen extends Screen {
    static final int COLOR_BACKGROUND = 0xFF1E1E1E;
    static final int COLOR_SURFACE_100 = 0xFF141414;
    static final int COLOR_SURFACE_200 = 0xFF1F1F1F;
    static final int COLOR_SURFACE_300 = 0xFF242424;
    static final int COLOR_SURFACE_400 = 0xFF333333;
    static final int COLOR_PRIMARY = 0xFFFFBB00;
    static final int COLOR_PRIMARY_HOVER = 0xFFFFC877;
    static final int COLOR_PRIMARY_TRANSLUCENT = 0x33FFBB00;
    static final int COLOR_TEXT = 0xFFF2F2F2;
    static final int COLOR_TEXT_DIM = 0xFFBBBBBB;
    static final int COLOR_HOVER_OUTLINE = 0x80FFBB00;
    static final int COLOR_GRID_LINE = 0x80373737;

    private static final int LIST_ROW_HEIGHT = 14;
    private static final String[] SIDEBAR_ICONS = {"=", "⚡", "▶", "■", "★", ">"};
    private static final String[] SIDEBAR_TABS = {"Ports", "Powers", "Modes", "OSD", "Options", "CLI"};
    private static final String[] SETTINGS_SECTIONS = {
            "Video Format", "Units", "Timers", "Alarms", "Warnings", "Craft Name", "Pilot Name"
    };
    private static final String APP_VERSION = "App Version: 2026.3.1 (d8vc060)";

    private final @Nullable Screen previous;
    private final DroneEntity drone;
    private OsdLayout editingLayout;
    private final List<ElementRow> rows = new ArrayList<>();
    private List<ToolbarButton> toolbarButtons = List.of();
    private @Nullable OsdEditorGeometry geometry;
    private @Nullable EditBox craftNameField;
    private @Nullable EditBox pilotNameField;
    private @Nullable String draggedElement;
    private @Nullable String hoveredElement;
    private float dragOffsetGridX;
    private float dragOffsetGridY;
    private int listScroll;
    private @Nullable String statusMessage;
    private long statusMessageExpiry;
    private boolean debugOverlay;

    public OsdBuilderScreen(@Nullable Screen previous, DroneEntity drone, @Nullable OsdLayout layout) {
        super(Component.literal("OSD"));
        this.previous = previous;
        this.drone = drone;
        this.editingLayout = layout == null ? OsdLayout.defaults() : layout.copy();
        rebuildRows();
    }

    @Override
    protected void init() {
        geometry = new OsdEditorGeometry(
                width,
                height,
                minecraft.getWindow().getGuiScale(),
                font.lineHeight
        );
        draggedElement = null;
        hoveredElement = null;
        buildToolbarButtons();
        buildTextFields();
        clampListScroll();
    }

    private void buildTextFields() {
        craftNameField = null;
        pilotNameField = null;
        if (geometry == null || !geometry.settingsVisible) {
            return;
        }
        int fieldHeight = font.lineHeight + 2;
        craftNameField = new EditBox(
                font,
                geometry.settingsFieldX(),
                geometry.settingsFieldY(5),
                geometry.settingsFieldWidth(),
                fieldHeight,
                Component.literal("Craft Name")
        );
        craftNameField.setMaxLength(16);
        craftNameField.setBordered(false);
        craftNameField.setTextColor(COLOR_TEXT);
        craftNameField.setTextColorUneditable(COLOR_TEXT_DIM);
        craftNameField.setHint(Component.literal("KINDER"));
        craftNameField.setValue(drone.getDroneName());
        addRenderableWidget(craftNameField);

        pilotNameField = new EditBox(
                font,
                geometry.settingsFieldX(),
                geometry.settingsFieldY(6),
                geometry.settingsFieldWidth(),
                fieldHeight,
                Component.literal("Pilot Name")
        );
        pilotNameField.setMaxLength(16);
        pilotNameField.setBordered(false);
        pilotNameField.setTextColor(COLOR_TEXT);
        pilotNameField.setTextColorUneditable(COLOR_TEXT_DIM);
        pilotNameField.setHint(Component.literal("PILOT"));
        pilotNameField.setValue(elementText("PILOT_NAME", defaultPilotName()));
        pilotNameField.setResponder(value -> setElementText("PILOT_NAME", value));
        addRenderableWidget(pilotNameField);
    }

    private void buildToolbarButtons() {
        if (geometry == null) {
            toolbarButtons = List.of();
            return;
        }
        int buttonHeight = 18;
        int horizontalPadding = 6;
        int gap = 4;
        int y = geometry.toolbarY + (geometry.toolbarHeight - buttonHeight) / 2;
        String saveLabel = "Save";
        int saveWidth = font.width(saveLabel) + horizontalPadding * 2 + 4;
        int saveX = geometry.toolbarX + geometry.toolbarWidth - saveWidth - 8;
        List<ToolbarButton> buttons = new ArrayList<>();
        List<LabelAction> left = List.of(
                new LabelAction("Font Mgr", this::showFontManagerMessage),
                new LabelAction("Import", this::openImportScreen),
                new LabelAction("Export", this::exportLayout),
                new LabelAction("Reset", this::resetToDefaults)
        );
        int x = geometry.toolbarX + 8;
        for (LabelAction action : left) {
            int buttonWidth = font.width(action.label()) + horizontalPadding * 2;
            if (x + buttonWidth + gap > saveX) {
                break;
            }
            buttons.add(new ToolbarButton(action.label(), x, y, buttonWidth, buttonHeight, action.action()));
            x += buttonWidth + gap;
        }
        buttons.add(new ToolbarButton(saveLabel, saveX, y, saveWidth, buttonHeight, this::saveLayout));
        toolbarButtons = List.copyOf(buttons);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (geometry == null) {
            return;
        }
        graphics.fill(0, 0, width, height, COLOR_BACKGROUND);
        if (geometry.tooSmall) {
            renderTooSmall(graphics);
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }
        renderHeader(graphics);
        renderSidebar(graphics);
        renderContent(graphics, mouseX, mouseY);
        renderToolbar(graphics, mouseX, mouseY);
        renderLogBar(graphics);
        renderElementList(graphics, mouseX, mouseY);
        if (debugOverlay) {
            renderDebugOverlay(graphics);
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void renderHeader(GuiGraphicsExtractor graphics) {
        graphics.fill(0, 0, width, geometry.headerHeight, COLOR_SURFACE_200);
        graphics.fill(0, geometry.headerHeight - 1, width, geometry.headerHeight, COLOR_SURFACE_400);
        graphics.text(font, "BETAFLIGHT CONFIGURATOR", 10,
                (geometry.headerHeight - font.lineHeight) / 2, COLOR_TEXT, true);
    }

    private void renderSidebar(GuiGraphicsExtractor graphics) {
        int bottom = height - geometry.logHeight;
        graphics.fill(0, geometry.headerHeight, geometry.sidebarWidth, bottom, COLOR_SURFACE_100);
        graphics.fill(geometry.sidebarWidth - 1, geometry.headerHeight, geometry.sidebarWidth, bottom,
                COLOR_SURFACE_400);
        int top = geometry.headerHeight + 8;
        int available = bottom - top - 8;
        int step = SIDEBAR_TABS.length * 16 + 8 > available && available > 0
                ? Math.max(12, (available - 8) / SIDEBAR_TABS.length)
                : 16;
        graphics.fill(4, top, geometry.sidebarWidth - 5,
                Math.min(bottom, top + SIDEBAR_TABS.length * step + 8), COLOR_SURFACE_200);
        graphics.enableScissor(0, geometry.headerHeight, geometry.sidebarWidth, bottom);
        int y = top + 4;
        for (int index = 0; index < SIDEBAR_TABS.length; index++) {
            boolean selected = index == 3;
            int tabHeight = Math.min(14, step - 2);
            if (selected) {
                graphics.fill(6, y - 1, geometry.sidebarWidth - 7, y + tabHeight + 1,
                        COLOR_PRIMARY_TRANSLUCENT);
            }
            int textY = y + (tabHeight - font.lineHeight) / 2 + 1;
            graphics.text(font, SIDEBAR_ICONS[index], 12, textY,
                    selected ? COLOR_PRIMARY : COLOR_TEXT_DIM, true);
            graphics.enableScissor(22, y - 1, geometry.sidebarWidth - 7, y + tabHeight + 1);
            graphics.text(font, SIDEBAR_TABS[index], 22, textY, COLOR_TEXT, true);
            graphics.disableScissor();
            y += step;
        }
        graphics.disableScissor();
    }

    private void renderContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(
                geometry.contentX,
                geometry.contentY,
                geometry.contentX + geometry.contentWidth,
                geometry.contentY + geometry.contentHeight,
                COLOR_SURFACE_100
        );
        int titleY = geometry.contentY + 2;
        graphics.text(font, "OSD", geometry.contentX + geometry.contentPadding, titleY, COLOR_TEXT, true);
        int underlineY = titleY + font.lineHeight + 1;
        graphics.fill(geometry.contentX + geometry.contentPadding, underlineY,
                geometry.contentX + geometry.contentPadding + font.width("OSD") + 4,
                underlineY + 2, COLOR_PRIMARY);

        renderBox(graphics, geometry.elementPanelX, geometry.elementBoxTop,
                geometry.elementPanelWidth, geometry.elementBoxHeight, "Elements");
        renderBoxFrame(graphics, geometry.previewX, geometry.elementBoxTop,
                geometry.previewWidth, geometry.elementBoxHeight);
        renderPreview(graphics, mouseX, mouseY);
        renderBoxTitle(graphics, geometry.previewX, geometry.elementBoxTop,
                geometry.previewWidth, "Preview");
        if (geometry.settingsVisible) {
            renderSettings(graphics);
        }
    }

    private void renderPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(geometry.gridX, geometry.gridY,
                geometry.gridX + geometry.gridWidth, geometry.gridY + geometry.gridHeight, 0xFF0A141A);
        graphics.enableScissor(geometry.gridX, geometry.gridY,
                geometry.gridX + geometry.gridWidth, geometry.gridY + geometry.gridHeight);
        if (draggedElement != null) {
            for (int column = 1; column < OsdLayout.GRID_COLUMNS; column++) {
                int x = geometry.gridX + column * geometry.cellWidth;
                graphics.fill(x, geometry.gridY, x + 1, geometry.gridY + geometry.gridHeight, COLOR_GRID_LINE);
            }
            for (int row = 1; row < OsdLayout.GRID_ROWS; row++) {
                int y = geometry.gridY + row * geometry.cellHeight;
                graphics.fill(geometry.gridX, y, geometry.gridX + geometry.gridWidth, y + 1, COLOR_GRID_LINE);
            }
        }
        hoveredElement = draggedElement == null && geometry.insideGrid(mouseX, mouseY)
                ? hitTest(mouseX, mouseY)
                : null;
        OsdPreviewRenderer.render(
                graphics, geometry, editingLayout, hoveredElement, draggedElement, currentCraftName()
        );
        graphics.disableScissor();
        graphics.text(font, "Video format: PAL | 30×16", geometry.gridX,
                geometry.gridY + geometry.gridHeight + 4, COLOR_TEXT_DIM, true);
    }

    private void renderSettings(GuiGraphicsExtractor graphics) {
        int bottom = geometry.elementBoxTop + geometry.elementBoxHeight;
        for (int index = 0; index < SETTINGS_SECTIONS.length; index++) {
            int y = geometry.settingsSectionY(index);
            if (y + geometry.settingsSectionHeight > bottom) {
                break;
            }
            String section = SETTINGS_SECTIONS[index];
            renderBox(graphics, geometry.settingsPanelX, y, geometry.settingsPanelWidth,
                    geometry.settingsSectionHeight, section);
            String value = switch (index) {
                case 0 -> "PAL";
                case 1 -> "Metric";
                default -> null;
            };
            if (value != null) {
                int frameTop = y + OsdEditorGeometry.BOX_TITLE_HEIGHT;
                int frameBottom = y + geometry.settingsSectionHeight - OsdEditorGeometry.BOX_BORDER;
                int textY = frameTop + Math.max(0, (frameBottom - frameTop - font.lineHeight) / 2);
                graphics.text(font, value, geometry.settingsPanelX + geometry.boxPadding + 2,
                        textY, COLOR_TEXT_DIM, true);
            }
        }
    }

    private void renderElementList(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.enableScissor(geometry.listX, geometry.listY,
                geometry.listX + geometry.listWidth, geometry.listY + geometry.listHeight);
        for (int index = 0; index < rows.size(); index++) {
            ElementRow row = rows.get(index);
            int y = geometry.listY + index * LIST_ROW_HEIGHT - listScroll;
            if (y + LIST_ROW_HEIGHT <= geometry.listY || y >= geometry.listY + geometry.listHeight) {
                continue;
            }
            if (row.category()) {
                graphics.text(font, row.label().toUpperCase(Locale.ROOT), geometry.listX + 1,
                        y + (LIST_ROW_HEIGHT - font.lineHeight) / 2 + 1, COLOR_PRIMARY, true);
                continue;
            }
            boolean hovered = mouseX >= geometry.listX && mouseX < geometry.listX + geometry.listWidth
                    && mouseY >= y && mouseY < y + LIST_ROW_HEIGHT;
            if (hovered) {
                graphics.fill(geometry.listX, y, geometry.listX + geometry.listWidth,
                        y + LIST_ROW_HEIGHT, 0x20202020);
            }
            OsdElement element = editingLayout.get(row.id());
            int checkX = geometry.listX + 1;
            int checkY = y + (LIST_ROW_HEIGHT - 8) / 2;
            graphics.fill(checkX, checkY, checkX + 8, checkY + 8, 0xFF555555);
            graphics.fill(checkX + 1, checkY + 1, checkX + 7, checkY + 7, 0xFF2A2A2A);
            if (element != null && element.visible()) {
                graphics.fill(checkX + 2, checkY + 2, checkX + 6, checkY + 6, COLOR_PRIMARY);
            }
            String label = truncate(row.label(), Math.max(0, geometry.listWidth - 14));
            graphics.text(font, label, checkX + 11,
                    y + (LIST_ROW_HEIGHT - font.lineHeight) / 2 + 1, COLOR_TEXT, true);
        }
        renderListScrollbar(graphics);
        graphics.disableScissor();
    }

    private void renderListScrollbar(GuiGraphicsExtractor graphics) {
        int contentHeight = rows.size() * LIST_ROW_HEIGHT;
        if (contentHeight <= geometry.listHeight) {
            return;
        }
        int trackX = geometry.listX + geometry.listWidth - 3;
        int thumbHeight = Math.max(10, geometry.listHeight * geometry.listHeight / contentHeight);
        int available = geometry.listHeight - thumbHeight;
        int thumbY = geometry.listY + Math.round(available * (listScroll / (float) maximumListScroll()));
        graphics.fill(trackX, geometry.listY, trackX + 2, geometry.listY + geometry.listHeight, 0x50333333);
        graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbHeight, COLOR_PRIMARY);
    }

    private void renderToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.fill(geometry.toolbarX, geometry.toolbarY - 2,
                geometry.toolbarX + geometry.toolbarWidth, geometry.toolbarY, 0x40000000);
        graphics.fill(geometry.toolbarX, geometry.toolbarY,
                geometry.toolbarX + geometry.toolbarWidth, geometry.toolbarY + geometry.toolbarHeight,
                COLOR_SURFACE_300);
        graphics.fill(geometry.toolbarX, geometry.toolbarY,
                geometry.toolbarX + geometry.toolbarWidth, geometry.toolbarY + 1, COLOR_SURFACE_400);
        for (ToolbarButton button : toolbarButtons) {
            boolean hovered = button.contains(mouseX, mouseY);
            graphics.fill(button.x(), button.y(), button.x() + button.width(), button.y() + button.height(),
                    hovered ? COLOR_PRIMARY_HOVER : COLOR_PRIMARY);
            int textX = button.x() + (button.width() - font.width(button.label())) / 2;
            int textY = button.y() + (button.height() - font.lineHeight) / 2 + 1;
            graphics.text(font, button.label(), textX, textY, 0xFF000000, false);
        }
    }

    private void renderLogBar(GuiGraphicsExtractor graphics) {
        int y = height - geometry.logHeight;
        graphics.fill(0, y, width, height, COLOR_SURFACE_100);
        graphics.fill(0, y, width, y + 1, COLOR_SURFACE_400);
        String left;
        int color;
        if (statusMessage != null && System.currentTimeMillis() < statusMessageExpiry) {
            left = statusMessage;
            color = COLOR_PRIMARY;
        } else {
            statusMessage = null;
            left = LocalDate.now() + " @00:00:00 — " + APP_VERSION;
            color = COLOR_TEXT_DIM;
        }
        int textY = y + (geometry.logHeight - font.lineHeight) / 2 + 1;
        graphics.text(font, truncate(left, Math.max(0, width - geometry.contentX - 80)),
                geometry.contentX + 8, textY, color, true);
        String showLog = "Show Log";
        graphics.text(font, showLog, width - font.width(showLog) - 12, textY, COLOR_TEXT_DIM, true);
    }

    private void renderTooSmall(GuiGraphicsExtractor graphics) {
        int panelWidth = Math.min(width - 20, 220);
        int panelHeight = 60;
        int x = (width - panelWidth) / 2;
        int y = (height - panelHeight) / 2;
        graphics.fill(x, y, x + panelWidth, y + panelHeight, COLOR_SURFACE_200);
        graphics.fill(x, y, x + panelWidth, y + 2, COLOR_PRIMARY);
        graphics.centeredText(font, "Window too small", width / 2, y + 8, COLOR_PRIMARY);
        graphics.centeredText(font, "Min: " + geometry.minimumWidth + "×" + geometry.minimumHeight,
                width / 2, y + font.lineHeight + 12, COLOR_TEXT_DIM);
        graphics.centeredText(font, "Current: " + width + "×" + height,
                width / 2, y + font.lineHeight * 2 + 14, COLOR_TEXT_DIM);
    }

    private void renderDebugOverlay(GuiGraphicsExtractor graphics) {
        graphics.outline(geometry.contentX, geometry.contentY, geometry.contentWidth,
                geometry.contentHeight, 0xFF4444FF);
        graphics.outline(geometry.elementPanelX, geometry.elementBoxTop,
                geometry.elementPanelWidth, geometry.elementBoxHeight, 0xFFFF0000);
        graphics.outline(geometry.previewX, geometry.elementBoxTop,
                geometry.previewWidth, geometry.elementBoxHeight, 0xFFFF00FF);
        graphics.outline(geometry.gridX, geometry.gridY, geometry.gridWidth, geometry.gridHeight, 0xFFFFFF00);
        String info = "F3=debug | " + width + "×" + height + " gs=" + geometry.guiScale;
        graphics.fill(width - font.width(info) - 6, 0, width, font.lineHeight + 4, 0xC0000000);
        graphics.text(font, info, width - font.width(info) - 3, 2, COLOR_TEXT, false);
    }

    private void renderBox(GuiGraphicsExtractor graphics, int x, int y, int boxWidth, int boxHeight,
                           String label) {
        renderBoxFrame(graphics, x, y, boxWidth, boxHeight);
        renderBoxTitle(graphics, x, y, boxWidth, label);
    }

    private void renderBoxFrame(GuiGraphicsExtractor graphics, int x, int y, int boxWidth, int boxHeight) {
        int bodyY = y + OsdEditorGeometry.BOX_TITLE_HEIGHT / 2;
        graphics.fill(x, bodyY, x + boxWidth, y + boxHeight, COLOR_SURFACE_400);
        graphics.fill(x + OsdEditorGeometry.BOX_BORDER, bodyY + OsdEditorGeometry.BOX_BORDER,
                x + boxWidth - OsdEditorGeometry.BOX_BORDER,
                y + boxHeight - OsdEditorGeometry.BOX_BORDER, COLOR_SURFACE_200);
    }

    private void renderBoxTitle(GuiGraphicsExtractor graphics, int x, int y, int boxWidth, String label) {
        int width = Math.min(font.width(label) + 10, Math.max(10, boxWidth - 4));
        int pillX = x + 8;
        if (pillX + width > this.width - 2) {
            width = this.width - 2 - pillX;
        }
        if (width < 10) {
            return;
        }
        graphics.fill(pillX, y, pillX + width, y + OsdEditorGeometry.BOX_TITLE_HEIGHT, COLOR_PRIMARY);
        graphics.enableScissor(pillX, y, pillX + width, y + OsdEditorGeometry.BOX_TITLE_HEIGHT);
        graphics.text(font, label, pillX + (width - font.width(label)) / 2,
                y + (OsdEditorGeometry.BOX_TITLE_HEIGHT - font.lineHeight) / 2 + 1,
                0xFF000000, false);
        graphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (geometry == null || geometry.tooSmall) {
            return super.mouseClicked(event, doubleClick);
        }
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return false;
        }
        setFocused(null);
        for (ToolbarButton button : toolbarButtons) {
            if (button.contains(event.x(), event.y())) {
                button.action().run();
                return true;
            }
        }
        if (geometry.insideList(event.x(), event.y())) {
            ElementRow row = rowAt(event.y());
            if (row != null && !row.category()) {
                OsdElement element = editingLayout.get(row.id());
                if (element != null) {
                    element.setVisible(!element.visible());
                }
            }
            return true;
        }
        if (geometry.insideGrid(event.x(), event.y())) {
            String hit = hitTest(event.x(), event.y());
            if (hit != null) {
                draggedElement = hit;
                OsdElement element = editingLayout.get(hit);
                if (element != null) {
                    dragOffsetGridX = geometry.mouseToGridX(event.x()) - element.x();
                    dragOffsetGridY = geometry.mouseToGridY(event.y()) - element.y();
                }
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (geometry != null && draggedElement != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            OsdElement element = editingLayout.get(draggedElement);
            if (element != null) {
                element.setPosition(
                        geometry.mouseToGridX(event.x()) - dragOffsetGridX,
                        geometry.mouseToGridY(event.y()) - dragOffsetGridY
                );
                return true;
            }
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (draggedElement != null && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            draggedElement = null;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (geometry != null && geometry.insideList(mouseX, mouseY)) {
            listScroll = clamp(listScroll - (int) Math.signum(scrollY) * LIST_ROW_HEIGHT,
                    0, maximumListScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_F3) {
            debugOverlay = !debugOverlay;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(previous);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private @Nullable String hitTest(double mouseX, double mouseY) {
        for (OsdElement element : editingLayout.visible()) {
            OsdElementDefinition definition = OsdElementRegistry.get(element.id());
            if (definition == null) {
                continue;
            }
            float x = geometry.gridToScreenX(element.x());
            float y = geometry.gridToScreenY(element.y());
            float right = x + definition.widthCells() * geometry.cellWidth;
            float bottom = y + definition.heightCells() * geometry.cellHeight;
            if (mouseX >= x && mouseX < right && mouseY >= y && mouseY < bottom) {
                return element.id();
            }
        }
        return null;
    }

    private @Nullable ElementRow rowAt(double mouseY) {
        int index = (int) Math.floor((mouseY - geometry.listY + listScroll) / LIST_ROW_HEIGHT);
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    private int maximumListScroll() {
        return geometry == null ? 0 : Math.max(0, rows.size() * LIST_ROW_HEIGHT - geometry.listHeight);
    }

    private void clampListScroll() {
        listScroll = clamp(listScroll, 0, maximumListScroll());
    }

    private void rebuildRows() {
        rows.clear();
        OsdElementRegistry.byCategory().forEach((category, definitions) -> {
            rows.add(ElementRow.category(category));
            for (OsdElementDefinition definition : definitions) {
                rows.add(ElementRow.element(definition));
            }
        });
    }

    private void saveLayout() {
        synchronizeTextFields();
        OsdLayoutStore.save(drone.getUUID(), editingLayout);
        saveCraftName();
        showStatus("Layout saved");
        minecraft.gui.setScreen(previous);
    }

    private void resetToDefaults() {
        editingLayout = OsdLayout.defaults();
        listScroll = 0;
        rebuildRows();
        rebuildWidgets();
        showStatus("Layout reset to defaults");
    }

    private void exportLayout() {
        synchronizeTextFields();
        try {
            Path exported = OsdLayoutStore.exportLayout(editingLayout, currentCraftName());
            showStatus("Exported: " + exported.getFileName());
        } catch (Exception exception) {
            showStatus("Export failed: " + rootMessage(exception));
        }
    }

    private void openImportScreen() {
        minecraft.gui.setScreen(new OsdImportScreen(this, imported -> {
            editingLayout = imported.copy();
            listScroll = 0;
            rebuildRows();
            showStatus("Imported layout applied");
        }));
    }

    private void showFontManagerMessage() {
        showStatus("Font Manager coming soon");
    }

    private void showStatus(String message) {
        statusMessage = message;
        statusMessageExpiry = System.currentTimeMillis() + 5_000L;
    }

    private void synchronizeTextFields() {
        if (craftNameField != null) {
            // V1.1.4 stores the craft name in the airframe config, not in the layout element.
            OsdElement craftName = editingLayout.get("CRAFT_NAME");
            if (craftName != null) craftName.setCustomText(null);
        }
        if (pilotNameField != null) {
            setElementText("PILOT_NAME", pilotNameField.getValue());
        }
    }

    private void saveCraftName() {
        String name = currentCraftName();
        DroneFlightConfig current = drone.getFlightConfig();
        DroneFlightConfig renamed = new DroneFlightConfig(
                current.yawRcRate(), current.pitchRcRate(), current.rollRcRate(),
                current.yawSuperRate(), current.pitchSuperRate(), current.rollSuperRate(),
                current.yawExpo(), current.pitchExpo(), current.rollExpo(),
                current.motorKv(), current.propDiameterInches(), current.propPitchInches(),
                current.dragCoefficient(), current.thrustMultiplier(), current.flightMode3d(), name
        );
        // Immediate local feedback. The server updates only the name so client-side defaults can
        // never overwrite a server-owned rate or airframe setting.
        drone.setFlightConfig(renamed);
        ClientPacketDistributor.sendToServer(new DroneNamePayload(drone.getId(), renamed.droneName()));
    }

    private String currentCraftName() {
        String name = craftNameField == null ? drone.getDroneName() : craftNameField.getValue().trim();
        return name.isEmpty() ? "KINDER" : name;
    }

    private void setElementText(String id, String value) {
        OsdElement element = editingLayout.get(id);
        if (element == null) {
            return;
        }
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        element.setCustomText(normalized.isEmpty() ? null : normalized);
    }

    private String elementText(String id, String fallback) {
        OsdElement element = editingLayout.get(id);
        return element != null && element.customText() != null && !element.customText().isBlank()
                ? element.customText()
                : fallback;
    }

    private String defaultPilotName() {
        return minecraft.player == null
                ? "PILOT"
                : minecraft.player.getName().getString().toUpperCase(Locale.ROOT);
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

    private static String rootMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record ElementRow(String label, @Nullable String id, boolean category) {
        static ElementRow category(String label) {
            return new ElementRow(label, null, true);
        }

        static ElementRow element(OsdElementDefinition definition) {
            return new ElementRow(definition.displayName(), definition.id(), false);
        }
    }

    private record LabelAction(String label, Runnable action) {
    }

    private record ToolbarButton(String label, int x, int y, int width, int height, Runnable action) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }
}
