package com.client.mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Pure-Java layout and ordering helper for the compact multi-column module card grid
 * in {@link ModernClickGuiScreen}, ensuring rendering and click hitboxes share
 * identical coordinates and module lists are consistently ordered A-Z.
 */
final class ModernModuleGridLayout {

    static final int CARD_HEIGHT = 48;
    static final int CARD_CORNER_RADIUS = 10;
    static final int GRID_PAD_X = 12;
    static final int GRID_PAD_Y = 12;
    static final int GRID_GAP_X = 8;
    static final int GRID_GAP_Y = 8;

    static final int CARD_PAD_X = 10;
    static final int CARD_PADDING_X = 10;
    static final int TITLE_Y_OFFSET = 9;
    static final int STAR_BOX_RIGHT_INSET = 4;
    static final int STAR_BOX_Y_OFFSET = 6;
    static final int STAR_BOX_SIZE = 14;
    static final int STAR_ICON_SIZE = 9;
    static final int CONTROL_Y_OFFSET = 25;
    static final int CONTROL_HEIGHT = 15;
    static final int CONTROL_GAP = 4;
    static final int SWITCH_WIDTH = 28;
    static final int TUNE_WIDTH = 17;

    static final int ACCORDION_TOP_DIVIDER_PAD = 4;
    static final int ACCORDION_DESC_LINE_HEIGHT = 10;
    static final int ACCORDION_BOTTOM_PAD = 6;

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
        int starX(int cardX, int cardW) {
            return cardX + cardW - STAR_BOX_SIZE - STAR_BOX_RIGHT_INSET;
        }

        int starY(int cardY) {
            return cardY + STAR_BOX_Y_OFFSET;
        }

        int starW() {
            return STAR_BOX_SIZE;
        }

        int starH() {
            return STAR_BOX_SIZE;
        }

        int titleX(int cardX) {
            return cardX + CARD_PAD_X;
        }

        int titleMaxWWithStar(int cardW) {
            return Math.max(24, cardW - CARD_PAD_X - STAR_BOX_SIZE - STAR_BOX_RIGHT_INSET - 2);
        }
    }

    record AccordionBounds(
            int x,
            int y,
            int w,
            int fullHeight,
            int visibleHeight,
            int expandedCol,
            int expandedRow,
            int controlsWidth
    ) {
    }

    record GridPlan(
            GridMetrics metrics,
            int moduleCount,
            int expandedIndex,
            int expandedRow,
            AccordionBounds accordion
    ) {
        int cardX(int gridX, int index) {
            return metrics.cardX(gridX, index);
        }

        int cardY(int startY, int index) {
            int col = index % metrics.columns();
            int row = index / metrics.columns();
            int extra = accordion != null && col == accordion.expandedCol() && row > expandedRow
                    ? accordion.visibleHeight()
                    : 0;
            return startY + row * (metrics.cardHeight() + metrics.gapY()) + extra;
        }

        int totalHeight() {
            if (accordion == null || moduleCount <= 0) {
                return metrics.totalHeight();
            }
            int maxColH = metrics.totalHeight();
            for (int col = 0; col < metrics.columns(); col++) {
                int colCount = col < moduleCount ? ((moduleCount - 1 - col) / metrics.columns() + 1) : 0;
                if (colCount == 0) {
                    continue;
                }
                int colH = metrics.padY() * 2
                        + colCount * metrics.cardHeight()
                        + Math.max(0, colCount - 1) * metrics.gapY()
                        + (col == accordion.expandedCol() ? accordion.visibleHeight() : 0);
                if (colH > maxColH) {
                    maxColH = colH;
                }
            }
            return maxColH;
        }
    }

    static int computeCardControlsWidth(int cardW) {
        return Math.max(60, cardW - CARD_PADDING_X * 2);
    }

    static int computeExpandedDescHeaderHeight(int descLineCount) {
        return computeExpandedDescHeaderHeight(descLineCount, 2);
    }

    static int computeExpandedDescHeaderHeight(int descLineCount, int maxLines) {
        if (descLineCount <= 0 || maxLines <= 0) {
            return 0;
        }
        return ACCORDION_TOP_DIVIDER_PAD
                + Math.min(maxLines, descLineCount) * ACCORDION_DESC_LINE_HEIGHT
                + ACCORDION_TOP_DIVIDER_PAD;
    }

    static int computeAccordionExtraHeight(
            int cardW,
            int descHeaderHeight,
            List<ModernModuleSettings.Control> controls
    ) {
        if (controls != null && !controls.isEmpty()) {
            return computeCardExtraHeight(cardW, descHeaderHeight, controls);
        }
        return descHeaderHeight > 0 ? 1 + descHeaderHeight : 0;
    }

    static int computeCardExtraHeight(
            int cardW,
            int descHeaderHeight,
            List<ModernModuleSettings.Control> controls
    ) {
        if (controls == null || controls.isEmpty()) {
            return 0;
        }
        int controlsW = computeCardControlsWidth(cardW);
        int flowH = ModernInspectorLayout.computeControlsFlow(0, 0, controlsW, controls).totalHeight();
        return 1 + Math.max(0, descHeaderHeight) + 1 + flowH + ACCORDION_BOTTOM_PAD;
    }

    static GridPlan computeGridPlan(
            int gridX,
            int startY,
            int gridW,
            int moduleCount,
            int expandedIndex,
            int fullExtraHeight,
            float expandAnim
    ) {
        GridMetrics base = computeGridMetrics(gridW, moduleCount);
        if (moduleCount <= 0 || expandedIndex < 0 || expandedIndex >= moduleCount
                || fullExtraHeight <= 0 || !(expandAnim > 0.005f)) {
            return new GridPlan(base, moduleCount, -1, -1, null);
        }
        float clamped = Math.max(0.0f, Math.min(1.0f, expandAnim));
        int visH = Math.round(fullExtraHeight * clamped);
        if (visH <= 6) {
            return new GridPlan(base, moduleCount, -1, -1, null);
        }
        int expCol = expandedIndex % base.columns();
        int expRow = expandedIndex / base.columns();
        int cardX = base.cardX(gridX, expandedIndex);
        int cardTopY = base.cardY(startY, expandedIndex);
        AccordionBounds bounds = new AccordionBounds(
                cardX,
                cardTopY + CARD_HEIGHT,
                base.colWidth(),
                fullExtraHeight,
                visH,
                expCol,
                expRow,
                computeCardControlsWidth(base.colWidth())
        );
        return new GridPlan(base, moduleCount, expandedIndex, expRow, bounds);
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
        return computeCardControlLayout(cardX, cardY, cardW, bindTextWidth, true);
    }

    /**
     * Balanced Pro bottom control row: anchors keybind pill (and optional settings
     * gear button for configurable modules) on bottom-left, toggle switch on bottom-right.
     */
    static CardControlLayout computeCardControlLayout(
            int cardX,
            int cardY,
            int cardW,
            int bindTextWidth,
            boolean hasSettings
    ) {
        int controlY = cardY + CONTROL_Y_OFFSET;
        int switchX = cardX + cardW - SWITCH_WIDTH - CARD_PAD_X;
        int bindX = cardX + CARD_PAD_X;
        int reservedTune = hasSettings ? TUNE_WIDTH + CONTROL_GAP : 0;
        int maxBindW = Math.max(24, switchX - CONTROL_GAP - reservedTune - bindX);
        int bindW = Math.min(bindTextWidth + 8, maxBindW);
        int tuneX = bindX + bindW + CONTROL_GAP;
        int tuneW = hasSettings ? TUNE_WIDTH : 0;
        int titleMaxW = Math.max(24, cardW - CARD_PAD_X * 2);
        return new CardControlLayout(
                switchX, controlY, SWITCH_WIDTH, CONTROL_HEIGHT,
                bindX, controlY, bindW, CONTROL_HEIGHT,
                tuneX, controlY, tuneW, CONTROL_HEIGHT,
                titleMaxW
        );
    }

    /** Pins favorite items before non-favorites while preserving A-Z order within each group. */
    static <T> void sortFavoritesFirstInPlace(
            List<T> items,
            Function<T, String> nameExtractor,
            Predicate<T> favoriteMatcher
    ) {
        if (items == null || items.size() <= 1 || favoriteMatcher == null) {
            return;
        }
        items.sort(
                Comparator.<T, Boolean>comparing(item -> !favoriteMatcher.test(item))
                        .thenComparing(
                                item -> {
                                    String name = nameExtractor.apply(item);
                                    return name == null ? "" : name;
                                },
                                String.CASE_INSENSITIVE_ORDER
                        )
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
        return filterAndSortModules(
                all, nameExtractor, catIdExtractor, catNameExtractor, selectedCategoryId, searchQuery, null);
    }

    /** Filters by search/category (including optional sub-setting matcher) and returns modules sorted A-Z by name. */
    static <T> List<T> filterAndSortModules(
            List<T> all,
            Function<T, String> nameExtractor,
            Function<T, String> catIdExtractor,
            Function<T, String> catNameExtractor,
            String selectedCategoryId,
            String searchQuery,
            BiPredicate<T, String> extraMatcher
    ) {
        return filterAndSortModules(
                all, nameExtractor, catIdExtractor, catNameExtractor, selectedCategoryId, searchQuery, extraMatcher, null);
    }

    /** Filters by search/category/favorites (including optional sub-setting matcher) and returns modules sorted A-Z by name. */
    static <T> List<T> filterAndSortModules(
            List<T> all,
            Function<T, String> nameExtractor,
            Function<T, String> catIdExtractor,
            Function<T, String> catNameExtractor,
            String selectedCategoryId,
            String searchQuery,
            BiPredicate<T, String> extraMatcher,
            Predicate<T> favoriteMatcher
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
                        || (catName != null && catName.toLowerCase(Locale.ROOT).contains(q))
                        || (extraMatcher != null && extraMatcher.test(m, q))) {
                    result.add(m);
                }
            }
        } else if (selectedCategoryId == null || "ALL".equals(selectedCategoryId)) {
            result.addAll(all);
        } else if ("FAVORITES".equalsIgnoreCase(selectedCategoryId)) {
            if (favoriteMatcher != null) {
                for (T m : all) {
                    if (favoriteMatcher.test(m)) {
                        result.add(m);
                    }
                }
            }
        } else {
            for (T m : all) {
                if (selectedCategoryId.equals(catIdExtractor.apply(m))) {
                    result.add(m);
                }
            }
        }
        if (favoriteMatcher != null) {
            sortFavoritesFirstInPlace(result, nameExtractor, favoriteMatcher);
        } else {
            sortByNameInPlace(result, nameExtractor);
        }
        return result;
    }
}
