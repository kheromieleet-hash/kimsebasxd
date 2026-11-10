package com.client.mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Pure-Java layout and ordering helper for the compact multi-column module card grid
 * in {@link ModernClickGuiScreen}, ensuring rendering and click hitboxes share
 * identical coordinates and module lists are consistently ordered A-Z.
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

    /** Sorts a mutable list of modules A-Z by display name using locale-independent ASCII folding. */
    static <T> void sortByNameInPlace(List<T> items, Function<T, String> nameExtractor) {
        if (items == null || items.size() <= 1) {
            return;
        }
        items.sort(Comparator.comparing(
                item -> {
                    String name = nameExtractor.apply(item);
                    return name == null ? "" : name;
                },
                String.CASE_INSENSITIVE_ORDER
        ));
    }

    /** Filters by search/category and returns modules sorted A-Z by name. */
    static <T> List<T> filterAndSortModules(
            List<T> all,
            Function<T, String> nameExtractor,
            Function<T, String> catIdExtractor,
            Function<T, String> catNameExtractor,
            String selectedCategoryId,
            String searchQuery
    ) {
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        List<T> result = new ArrayList<>();
        if (searchQuery != null && !searchQuery.isEmpty()) {
            String q = searchQuery.toLowerCase(Locale.ROOT);
            for (T m : all) {
                String name = nameExtractor.apply(m);
                String catName = catNameExtractor.apply(m);
                if ((name != null && name.toLowerCase(Locale.ROOT).contains(q))
                        || (catName != null && catName.toLowerCase(Locale.ROOT).contains(q))) {
                    result.add(m);
                }
            }
        } else if (selectedCategoryId == null || "ALL".equals(selectedCategoryId)) {
            result.addAll(all);
        } else {
            for (T m : all) {
                if (selectedCategoryId.equals(catIdExtractor.apply(m))) {
                    result.add(m);
                }
            }
        }
        sortByNameInPlace(result, nameExtractor);
        return result;
    }
}
