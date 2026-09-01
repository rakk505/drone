package com.modernity.drone.client.osd;

/** Immutable metadata for one Betaflight/MAX7456 OSD element. */
public record OsdElementDefinition(
        String id,
        String displayName,
        String category,
        String previewText,
        float defaultX,
        float defaultY,
        int widthCells,
        int heightCells,
        boolean liveData,
        boolean customRenderer
) {
    public OsdElementDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("OSD element id must not be blank");
        }
        displayName = displayName == null ? id : displayName;
        category = category == null ? "Other" : category;
        previewText = previewText == null ? "" : previewText;
        widthCells = Math.max(1, widthCells);
        heightCells = Math.max(1, heightCells);
    }

    public String getName() { return id; }
    public String getDisplayName() { return displayName; }
    public String getCategory() { return category; }
    public String getPreviewText() { return previewText; }
    public float getDefaultX() { return defaultX; }
    public float getDefaultY() { return defaultY; }
    public int getWidthChars() { return widthCells; }
    public int getHeightLines() { return heightCells; }
    public boolean hasLiveData() { return liveData; }
    public boolean hasCustomRenderer() { return customRenderer; }
}
