package com.client.mod;

/**
 * Pure-Java layout calculator for the compact multi-column module card grid
 * in {@link ModernClickGuiScreen}, ensuring rendering and click hitboxes share
 * identical coordinates and never truncate module titles.
 */
final class ModernModuleGridLayout {

    static final int CARD_HEIGHT = 44;
    static final int CARD_CORNER_RADIUS = 7;
    static final int GRID_PAD_X = 11;
    static final int GRID_PAD_Y = 10;
    static final int GRID_GAP_X = 7;
    static final int GRID_GAP_Y = 7;

    static final int CARD_PAD_X = 9;
    static final int TITLE_Y_OFFSET = 7;
    static final int CONTROL_Y_OFFSET = 22;
    static final int CONTROL_HEIGHT = 15;
    static final int CONTROL_GAP = 4;
    static final int SWITCH_WIDTH = 28;
    static final int TUNE_WIDTH = 17;

    private ModernModuleGridLayout() {
    }

    record GridMetrics(
            int columns,
            int colWidth,
            int padX,
            int padY,
            int gapX,
            int gapY,
            int cardHeight,
            int totalHeight
    ) {
        int cardX(int gridX, int index) {
            int col = index % columns;
            return gridX + padX + col * (colWidth + gapX);
        }

        int cardY(int startY, int index) {
            int row = index / columns;
            return startY + row * (cardHeight + gapY);
        }
    }

    record CardControlLayout(
            int switchX,
            int switchY,
            int switchW,
            int switchH,
            int bindX,
            int bindY,
            int bindW,
            int bindH,
            int tuneX,
            int tuneY,
            int tuneW,
            int tuneH,
            int titleMaxW
    ) {
    }

    static int computeColumnCount(int gridW) {
        if (gridW >= 510) return 4;
        if (gridW >= 380) return 3;
        if (gridW >= 250) return 2;
        return 1;
    }

    static GridMetrics computeGridMetrics(int gridW, int moduleCount) {
        int cols = computeColumnCount(gridW);
        int colW = Math.max(80, (gridW - GRID_PAD_X * 2 - (cols - 1) * GRID_GAP_X) / cols);
        int rows = moduleCount <= 0 ? 0 : (moduleCount + cols - 1) / cols;
        int totalH = rows == 0
                ? 100
                : GRID_PAD_Y * 2 + rows * CARD_HEIGHT + Math.max(0, rows - 1) * GRID_GAP_Y;
        return new GridMetrics(cols, colW, GRID_PAD_X, GRID_PAD_Y, GRID_GAP_X, GRID_GAP_Y, CARD_HEIGHT, totalH);
    }

    static CardControlLayout computeCardControlLayout(int cardX, int cardY, int cardW, int bindTextWidth) {
        int controlY = cardY + CONTROL_Y_OFFSET;
        int switchX = cardX + cardW - SWITCH_WIDTH - CARD_PAD_X;
        int bindW = bindTextWidth + 8;
        int bindX = switchX - bindW - CONTROL_GAP;
        int tuneX = bindX - TUNE_WIDTH - CONTROL_GAP;
        int titleMaxW = Math.max(24, cardW - CARD_PAD_X * 2);
        return new CardControlLayout(
                switchX, controlY, SWITCH_WIDTH, CONTROL_HEIGHT,
                bindX, controlY, bindW, CONTROL_HEIGHT,
                tuneX, controlY, TUNE_WIDTH, CONTROL_HEIGHT,
                titleMaxW
        );
    }
}
