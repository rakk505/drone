package com.modernity.drone.client.osd;

import java.util.Objects;

/** Mutable placement state used by the OSD editor and persistence layer. */
public final class OsdElement {
    private final String id;
    private float x;
    private float y;
    private boolean visible;
    private String customText;

    public OsdElement(String id, float x, float y, boolean visible) {
        this(id, x, y, visible, null);
    }

    public OsdElement(String id, float x, float y, boolean visible, String customText) {
        this.id = Objects.requireNonNull(id, "id");
        this.x = x;
        this.y = y;
        this.visible = visible;
        this.customText = customText;
    }

    public OsdElement copy() {
        return new OsdElement(id, x, y, visible, customText);
    }

    public String id() {
        return id;
    }

    public String getName() { return id; }

    public float x() {
        return x;
    }

    public float getX() { return x; }

    public float y() {
        return y;
    }

    public float getY() { return y; }

    public boolean visible() {
        return visible;
    }

    public boolean isVisible() { return visible; }

    public String customText() {
        return customText;
    }

    public String getCustomText() { return customText; }

    public void setX(float x) { setPosition(x, y); }

    public void setY(float y) { setPosition(x, y); }

    public void setPosition(float x, float y) {
        OsdElementDefinition definition = OsdElementRegistry.get(id);
        float maxX = definition == null ? OsdLayout.GRID_COLUMNS - 1.0F
                : OsdLayout.GRID_COLUMNS - definition.widthCells();
        float maxY = definition == null ? OsdLayout.GRID_ROWS - 1.0F
                : OsdLayout.GRID_ROWS - definition.heightCells();
        this.x = Math.max(0.0F, Math.min(maxX, OsdLayout.snapToHalf(x)));
        this.y = Math.max(0.0F, Math.min(maxY, OsdLayout.snapToHalf(y)));
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void setCustomText(String customText) {
        this.customText = customText == null || customText.isBlank() ? null : customText;
    }
}
