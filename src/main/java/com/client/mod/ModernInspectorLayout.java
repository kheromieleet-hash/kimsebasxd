package com.client.mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java layout calculator for the bottom-docked Settings Inspector Drawer
 * and its responsive multi-column controls grid in {@link ModernClickGuiScreen}.
 */
final class ModernInspectorLayout {

    static final int BOTTOM_GAP_TOP = 6;
    static final int BOTTOM_MARGIN_X = 10;
    static final int BOTTOM_MARGIN_Y = 10;
    static final int WIDE_HEADER_MIN_WIDTH = 360;
    static final int WIDE_HEADER_HEIGHT = 42;
    static final int COMPACT_HEADER_HEIGHT = 68;
    static final int COMPACT_PILL_ROW_OFFSET = 46;
    static final int CONTROLS_COL_GAP = 10;
    static final int CHIP_GAP_X = 6;

    private ModernInspectorLayout() {
    }

    record HeaderLayout(
            int headerHeight,
            boolean wide,
            int closeX,
            int closeY,
            int closeW,
            int closeH,
            int statX,
            int statY,
            int statW,
            int statH,
            int bindX,
            int bindY,
            int bindW,
            int bindH,
            int textMaxW
    ) {
    }

    record PlacedControl(
            ModernModuleSettings.Control control,
            int x,
            int y,
            int w,
            int h,
            int chipCols
    ) {
    }

    record ControlsFlow(
            List<PlacedControl> items,
            int totalHeight
    ) {
    }

    static HeaderLayout computeHeaderLayout(int x, int y, int w) {
        if (w >= WIDE_HEADER_MIN_WIDTH) {
            int closeW = 18;
            int closeH = 18;
            int closeX = x + w - 28;
            int btnY = y + 12;

            int bindW = w >= 480 ? 84 : 74;
            int bindH = 18;
            int bindX = closeX - bindW - 6;

            int statW = w >= 480 ? 66 : 58;
            int statH = 18;
            int statX = bindX - statW - 6;

            int textMaxW = Math.max(60, statX - (x + 12) - 10);
            return new HeaderLayout(
                    WIDE_HEADER_HEIGHT, true,
                    closeX, btnY, closeW, closeH,
                    statX, btnY, statW, statH,
                    bindX, btnY, bindW, bindH,
                    textMaxW
            );
        }

        int closeW = 18;
        int closeH = 18;
        int closeX = x + w - 28;
        int closeY = y + 9;
        int pillY = y + COMPACT_PILL_ROW_OFFSET;
        int pillW = Math.max(40, (w - 28) / 2);
        int textMaxW = Math.max(60, w - 44);
        return new HeaderLayout(
                COMPACT_HEADER_HEIGHT, false,
                closeX, closeY, closeW, closeH,
                x + 10, pillY, pillW, 17,
                x + 16 + pillW, pillY, pillW, 17,
                textMaxW
        );
    }

    static int computeHeaderHeight(int drawerW) {
        return drawerW >= WIDE_HEADER_MIN_WIDTH ? WIDE_HEADER_HEIGHT : COMPACT_HEADER_HEIGHT;
    }

    static int computeChipColumns(int width) {
        return width >= 480 ? 4 : 2;
    }

    static int computeChipWidth(int width, int chipCols) {
        return Math.max(20, (width - (chipCols - 1) * CHIP_GAP_X) / chipCols);
    }

    static int computeChipsHeight(ModernModuleSettings.ChipsControl chips, int chipCols) {
        int count = chips.options().size();
        if (count <= 0) {
            return ModernModuleSettings.CHIP_HEIGHT;
        }
        int rows = (count + chipCols - 1) / chipCols;
        return Math.max(
                ModernModuleSettings.CHIP_HEIGHT,
                rows * ModernModuleSettings.CHIP_HEIGHT + Math.max(0, rows - 1) * ModernModuleSettings.CHIP_GAP_Y
        );
    }

    static ControlsFlow computeControlsFlow(int x, int startY, int w, List<ModernModuleSettings.Control> controls) {
        if (controls == null || controls.isEmpty()) {
            return new ControlsFlow(List.of(), 0);
        }

        int cols = w >= 420 ? 2 : 1;
        int cellW = cols == 1 ? w : (w - CONTROLS_COL_GAP) / 2;
        int chipCols = computeChipColumns(w);

        List<PlacedControl> items = new ArrayList<>(controls.size());
        int curY = startY + ModernModuleSettings.SECTION_TOP_PAD;
        int rowCol = 0;
        int rowMaxH = 0;

        for (ModernModuleSettings.Control control : controls) {
            if (control instanceof ModernModuleSettings.ChipsControl cc) {
                if (rowCol > 0) {
                    curY += rowMaxH + ModernModuleSettings.CONTROL_GAP;
                    rowCol = 0;
                    rowMaxH = 0;
                }
                int chipH = computeChipsHeight(cc, chipCols);
                items.add(new PlacedControl(control, x, curY, w, chipH, chipCols));
                curY += chipH + ModernModuleSettings.CONTROL_GAP;
                continue;
            }

            int itemH = control.height();
            int itemX = x + rowCol * (cellW + CONTROLS_COL_GAP);
            items.add(new PlacedControl(control, itemX, curY, cellW, itemH, 1));
            rowMaxH = Math.max(rowMaxH, itemH);
            rowCol++;
            if (rowCol >= cols) {
                curY += rowMaxH + ModernModuleSettings.CONTROL_GAP;
                rowCol = 0;
                rowMaxH = 0;
            }
        }

        if (rowCol > 0) {
            curY += rowMaxH;
        } else if (!items.isEmpty()) {
            curY -= ModernModuleSettings.CONTROL_GAP;
        }
        int totalHeight = (curY - startY) + ModernModuleSettings.SECTION_BOTTOM_PAD;
        return new ControlsFlow(items, totalHeight);
    }

    static int computeTargetDrawerSlotHeight(int contentW, int bodyH, List<ModernModuleSettings.Control> controls) {
        int drawerW = Math.max(80, contentW - BOTTOM_MARGIN_X * 2);
        int headerH = computeHeaderHeight(drawerW);
        int controlsW = Math.max(80, drawerW - 24);
        ControlsFlow flow = computeControlsFlow(0, 0, controlsW, controls);
        int desiredH = BOTTOM_GAP_TOP + headerH + 2 + flow.totalHeight() + BOTTOM_MARGIN_Y;
        int minH = Math.min(bodyH, BOTTOM_GAP_TOP + headerH + 44 + BOTTOM_MARGIN_Y);
        int maxH = Math.max(minH, Math.min(250, (int) (bodyH * 0.56f)));
        return Math.max(minH, Math.min(desiredH, maxH));
    }

    /** Returns inset rounded bottom drawer bounds {@code [x, y, w, h]}, or null while collapsed. */
    static int[] computeBottomDrawerBounds(int contentX, int bodyY, int contentW, int bodyH, int animatedSlotH) {
        if (animatedSlotH <= BOTTOM_GAP_TOP + BOTTOM_MARGIN_Y + 12) {
            return null;
        }
        int gridH = Math.max(0, bodyH - animatedSlotH);
        int x = contentX + BOTTOM_MARGIN_X;
        int y = bodyY + gridH + BOTTOM_GAP_TOP;
        int w = Math.max(80, contentW - BOTTOM_MARGIN_X * 2);
        int h = Math.max(40, animatedSlotH - BOTTOM_GAP_TOP - BOTTOM_MARGIN_Y);
        return new int[]{x, y, w, h};
    }
}
