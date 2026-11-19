package com.client.mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java layout calculator for the responsive multi-column controls flow
 * inside inline accordion panels in {@link ModernClickGuiScreen}.
 */
final class ModernInspectorLayout {

    static final int CONTROLS_COL_GAP = 10;
    static final int CHIP_GAP_X = 6;

    private ModernInspectorLayout() {
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
            if (control instanceof ModernModuleSettings.ColorPaletteControl cp) {
                if (rowCol > 0) {
                    curY += rowMaxH + ModernModuleSettings.CONTROL_GAP;
                    rowCol = 0;
                    rowMaxH = 0;
                }
                int palH = cp.height();
                items.add(new PlacedControl(control, x, curY, w, palH, 1));
                curY += palH + ModernModuleSettings.CONTROL_GAP;
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
}
