package com.modernity.drone.client.gui.osd;

import com.modernity.drone.client.osd.OsdElement;
import com.modernity.drone.client.osd.OsdElementDefinition;
import com.modernity.drone.client.osd.OsdElementRegistry;
import com.modernity.drone.client.osd.OsdLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;

/** Draws editor previews with the bundled Monocraft/MAX7456 glyph set. */
final class OsdPreviewRenderer {
    private static final FontDescription.Resource OSD_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("fpvdrone", "osd")
    );

    private OsdPreviewRenderer() {
    }

    static void render(GuiGraphicsExtractor graphics, OsdEditorGeometry geometry, OsdLayout layout,
                       String hoveredId, String draggedId, String craftName) {
        Font font = Minecraft.getInstance().font;
        for (OsdElement element : layout.visible()) {
            OsdElementDefinition definition = OsdElementRegistry.get(element.id());
            if (definition == null) {
                continue;
            }
            float x = geometry.gridToScreenX(element.x());
            float y = geometry.gridToScreenY(element.y());
            if (element.id().equals(hoveredId) || element.id().equals(draggedId)) {
                int left = (int) Math.floor(x) - 1;
                int top = (int) Math.floor(y) - 1;
                int right = (int) Math.ceil(x + definition.widthCells() * geometry.cellWidth) + 1;
                int bottom = (int) Math.ceil(y + definition.heightCells() * geometry.cellHeight) + 1;
                graphics.fill(left, top, right, bottom, OsdBuilderScreen.COLOR_HOVER_OUTLINE);
            }
            String preview = previewText(element, definition, craftName);
            if (definition.heightCells() > 1 && preview.length() > definition.widthCells()) {
                for (int row = 0; row < definition.heightCells(); row++) {
                    int start = row * definition.widthCells();
                    if (start >= preview.length()) {
                        break;
                    }
                    int end = Math.min(start + definition.widthCells(), preview.length());
                    drawGridText(graphics, font, preview.substring(start, end), x,
                            y + row * geometry.cellHeight, geometry.cellWidth, geometry.cellHeight);
                }
            } else {
                drawGridText(graphics, font, preview, x, y, geometry.cellWidth, geometry.cellHeight);
            }
        }
    }

    private static String previewText(OsdElement element, OsdElementDefinition definition, String craftName) {
        if ("CRAFT_NAME".equals(element.id())) {
            return craftName == null || craftName.isBlank() ? "KINDER" : craftName.toUpperCase(java.util.Locale.ROOT);
        }
        if ("PILOT_NAME".equals(element.id())
                && element.customText() != null && !element.customText().isBlank()) {
            return element.customText();
        }
        String preview = definition.previewText();
        return preview == null || preview.isEmpty() ? definition.displayName() : preview;
    }

    private static void drawGridText(GuiGraphicsExtractor graphics, Font font, String text,
                                     float x, float y, int cellWidth, int cellHeight) {
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == ' ') {
                continue;
            }
            Component glyph = Component.literal(String.valueOf(value))
                    .withStyle(style -> style.withFont(OSD_FONT));
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + index * cellWidth, y);
            graphics.pose().scale(cellWidth / 12.0F, cellHeight / 18.0F);
            graphics.text(font, glyph, 0, 0, 0xFFFFFFFF, false);
            graphics.pose().popMatrix();
        }
    }
}
