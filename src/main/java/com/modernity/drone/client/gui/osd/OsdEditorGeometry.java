package com.modernity.drone.client.gui.osd;

import com.modernity.drone.client.osd.OsdLayout;

/** Responsive geometry used by the V1.1.4-style OSD builder. */
final class OsdEditorGeometry {
    static final int BOX_BORDER = 2;
    static final int BOX_TITLE_HEIGHT = 14;

    final int screenWidth;
    final int screenHeight;
    final int guiScale;
    final int fontLineHeight;
    final int minimumWidth = 192;
    final int minimumHeight = 120;
    final int headerHeight;
    final int sidebarWidth;
    final int toolbarHeight;
    final int logHeight;
    final int contentPadding;
    final int boxPadding;
    final int contentX;
    final int contentY;
    final int contentWidth;
    final int contentHeight;
    final int elementPanelX;
    final int elementPanelWidth;
    final int elementBoxTop;
    final int elementBoxHeight;
    final int previewX;
    final int previewWidth;
    final int previewHeight;
    final int settingsPanelX;
    final int settingsPanelWidth;
    final boolean settingsVisible;
    final int toolbarX;
    final int toolbarY;
    final int toolbarWidth;
    final int cellWidth;
    final int cellHeight;
    final int gridX;
    final int gridY;
    final int gridWidth;
    final int gridHeight;
    final int listX;
    final int listY;
    final int listWidth;
    final int listHeight;
    final int settingsSectionHeight;
    final int settingsGap;
    final boolean tooSmall;

    OsdEditorGeometry(int screenWidth, int screenHeight, int guiScale, int fontLineHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.guiScale = guiScale;
        this.fontLineHeight = fontLineHeight;
        headerHeight = clamp((int) (screenHeight * 0.08F), 28, 44);
        sidebarWidth = clamp((int) (screenWidth * 0.10F), 50, 80);
        toolbarHeight = clamp((int) (screenHeight * 0.05F), 20, 30);
        logHeight = clamp((int) (screenHeight * 0.03F), 12, 18);
        boxPadding = contentPadding = clamp((int) (screenWidth * 0.005F), 3, 8);

        contentX = sidebarWidth;
        contentY = headerHeight;
        contentWidth = Math.max(1, screenWidth - sidebarWidth);
        contentHeight = Math.max(1, screenHeight - headerHeight - toolbarHeight - logHeight);

        int innerX = contentX + contentPadding;
        int innerWidth = Math.max(1, contentWidth - contentPadding * 2);
        int titleBlockHeight = fontLineHeight + 3;
        elementBoxTop = contentY + 2 + titleBlockHeight + 4;
        int availablePreviewHeight = Math.max(1,
                contentY + contentHeight - contentPadding - BOX_TITLE_HEIGHT / 2 - BOX_BORDER
                        - (elementBoxTop + BOX_TITLE_HEIGHT / 2 + BOX_BORDER));

        int gaps = contentPadding * 2;
        int availableColumns = innerWidth - gaps;
        int idealPreviewWidth = availableColumns / 2;
        float idealCellWidth = idealPreviewWidth / (float) OsdLayout.GRID_COLUMNS;
        float idealCellHeight = idealCellWidth * 1.5F;
        if (idealCellHeight * OsdLayout.GRID_ROWS > availablePreviewHeight) {
            idealCellWidth = availablePreviewHeight / (float) OsdLayout.GRID_ROWS * (2.0F / 3.0F);
        }
        int computedCellWidth = Math.max(1, Math.round(idealCellWidth));
        int computedCellHeight = Math.max(1, Math.round(computedCellWidth * 1.5F));
        if (computedCellHeight * OsdLayout.GRID_ROWS > availablePreviewHeight) {
            computedCellHeight = Math.max(1, availablePreviewHeight / OsdLayout.GRID_ROWS);
            computedCellWidth = Math.max(1, Math.round(computedCellHeight * (2.0F / 3.0F)));
        }
        if (computedCellWidth * OsdLayout.GRID_COLUMNS > idealPreviewWidth && computedCellWidth > 1) {
            computedCellWidth = Math.max(1, (int) idealCellWidth);
            computedCellHeight = Math.max(1, Math.round(computedCellWidth * 1.5F));
        }
        cellWidth = computedCellWidth;
        cellHeight = computedCellHeight;
        gridWidth = cellWidth * OsdLayout.GRID_COLUMNS;
        gridHeight = cellHeight * OsdLayout.GRID_ROWS;

        elementPanelX = innerX;
        int availableSideColumns = innerWidth - gridWidth - gaps;
        boolean showSettings = availableSideColumns >= 140 && gridWidth >= 60;
        int computedElementWidth;
        int computedSettingsWidth;
        if (showSettings) {
            computedElementWidth = Math.max(70, availableSideColumns / 2);
            computedSettingsWidth = Math.max(70, availableSideColumns - computedElementWidth);
        } else {
            computedElementWidth = Math.max(70, innerWidth - contentPadding - gridWidth);
            computedSettingsWidth = 0;
        }
        elementPanelWidth = computedElementWidth;
        settingsPanelWidth = computedSettingsWidth;
        previewWidth = gridWidth;
        previewX = elementPanelX + elementPanelWidth + contentPadding;
        settingsPanelX = showSettings ? previewX + previewWidth + contentPadding : screenWidth + 10;
        settingsVisible = showSettings && settingsPanelX + settingsPanelWidth <= screenWidth + 1;

        gridX = previewX;
        gridY = elementBoxTop + BOX_TITLE_HEIGHT / 2 + BOX_BORDER;
        int gridFooterHeight = fontLineHeight + 8;
        previewHeight = Math.min(availablePreviewHeight, gridHeight + gridFooterHeight);
        elementBoxHeight = BOX_TITLE_HEIGHT / 2 + BOX_BORDER * 2 + previewHeight;

        listX = elementPanelX + BOX_BORDER;
        listY = elementBoxTop + BOX_TITLE_HEIGHT;
        listWidth = Math.max(1, elementPanelWidth - BOX_BORDER * 2);
        listHeight = Math.max(1, elementBoxHeight - BOX_TITLE_HEIGHT - BOX_BORDER);

        toolbarX = contentX + contentPadding;
        toolbarY = Math.max(0, screenHeight - toolbarHeight - logHeight);
        toolbarWidth = Math.max(1, contentWidth - contentPadding);

        int minimumSectionHeight = BOX_TITLE_HEIGHT + fontLineHeight + 4;
        int gap = 6;
        int availableForSections = elementBoxHeight - gap * 6;
        int sectionHeight = availableForSections / 7;
        if (sectionHeight < minimumSectionHeight) {
            int availableForGaps = elementBoxHeight - minimumSectionHeight * 7;
            gap = Math.max(1, availableForGaps / 6);
            sectionHeight = (elementBoxHeight - gap * 6) / 7;
        }
        settingsGap = gap;
        settingsSectionHeight = Math.max(16, Math.min(BOX_TITLE_HEIGHT + fontLineHeight + 10, sectionHeight));

        tooSmall = screenWidth < minimumWidth
                || screenHeight < minimumHeight
                || contentWidth < 133
                || contentHeight < 70
                || gridWidth > previewWidth + 1
                || gridHeight > previewHeight + 1;
    }

    int settingsSectionY(int index) {
        return elementBoxTop + index * (settingsSectionHeight + settingsGap);
    }

    int settingsFieldX() {
        return settingsPanelX + boxPadding + 2;
    }

    int settingsFieldY(int index) {
        int sectionY = settingsSectionY(index);
        int contentTop = BOX_TITLE_HEIGHT / 2 + BOX_BORDER;
        int contentAreaHeight = settingsSectionHeight - contentTop - BOX_BORDER;
        int fieldHeight = fontLineHeight + 2;
        int innerY = contentTop + Math.max(0, (contentAreaHeight - fieldHeight) / 2);
        int maximumY = settingsSectionHeight - BOX_BORDER - fieldHeight;
        return sectionY + Math.min(innerY, Math.max(contentTop, maximumY));
    }

    int settingsFieldWidth() {
        return Math.max(10, settingsPanelWidth - boxPadding * 2 - 4);
    }

    float mouseToGridX(double mouseX) {
        return (float) ((mouseX - gridX) / cellWidth);
    }

    float mouseToGridY(double mouseY) {
        return (float) ((mouseY - gridY) / cellHeight);
    }

    float gridToScreenX(float gridColumn) {
        return gridX + gridColumn * cellWidth;
    }

    float gridToScreenY(float gridRow) {
        return gridY + gridRow * cellHeight;
    }

    boolean insideGrid(double mouseX, double mouseY) {
        return mouseX >= gridX && mouseX < gridX + gridWidth
                && mouseY >= gridY && mouseY < gridY + gridHeight;
    }

    boolean insideList(double mouseX, double mouseY) {
        return mouseX >= listX && mouseX < listX + listWidth
                && mouseY >= listY && mouseY < listY + listHeight;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
