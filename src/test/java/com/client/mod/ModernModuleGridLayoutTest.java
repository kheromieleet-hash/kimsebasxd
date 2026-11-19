package com.client.mod;

import java.util.List;

/**
 * Verifies {@link ModernModuleGridLayout} column breakpoints, card coordinates,
 * control ordering, alphabetical module sorting, non-overlapping compact tile bounds,
 * and single-column masonry card accordion grid plans.
 */
public class ModernModuleGridLayoutTest {

    private record StubModule(String name, String catId, String catName) {}

    public static void main(String[] args) {
        assertResponsiveColumnBreakpoints();
        assertFourColumnGeometryAndFit();
        assertControlRowOrderingAndBounds();
        assertAlphabeticalFilteringAndSorting();
        assertInlineAccordionGridPlan();
        System.out.println("ALL ModernModuleGridLayoutTest ASSERTIONS PASSED!");
    }

    private static void assertResponsiveColumnBreakpoints() {
        assert ModernModuleGridLayout.computeColumnCount(730) == 4 : "Expected 4 columns at 1440p Scale 2";
        assert ModernModuleGridLayout.computeColumnCount(694) == 4 : "Expected 4 columns at 1080p Scale 2";
        assert ModernModuleGridLayout.computeColumnCount(554) == 4 : "Expected 4 columns at 900p Scale 2";
        assert ModernModuleGridLayout.computeColumnCount(510) == 4 : "Expected 4 columns at boundary 510px";
        assert ModernModuleGridLayout.computeColumnCount(430) == 3 : "Expected 3 columns when inspector is open";
        assert ModernModuleGridLayout.computeColumnCount(380) == 3 : "Expected 3 columns at boundary 380px";
        assert ModernModuleGridLayout.computeColumnCount(300) == 2 : "Expected 2 columns for compact viewport";
        assert ModernModuleGridLayout.computeColumnCount(220) == 1 : "Expected 1 column below 250px";
    }

    private static void assertFourColumnGeometryAndFit() {
        int gridX = 160;
        int gridW = 694;
        ModernModuleGridLayout.GridMetrics m = ModernModuleGridLayout.computeGridMetrics(gridW, 23);

        assert m.columns() == 4 : "Expected 4 columns, got " + m.columns();
        assert m.colWidth() >= 160 : "Expected generous card width >= 160px at 694px gridW, got " + m.colWidth();

        for (int i = 0; i < 4; i++) {
            int cx = m.cardX(gridX, i);
            int cy = m.cardY(50, i);
            assert cy == 50 : "First 4 modules must share row 0 (y=50), got " + cy;
            if (i > 0) {
                int prevRight = m.cardX(gridX, i - 1) + m.colWidth();
                assert cx - prevRight == m.gapX() : "Expected uniform gapX between columns";
            }
        }
        assert m.cardX(gridX, 3) + m.colWidth() <= gridX + gridW - m.padX() + 3 : "4th card must fit inside grid right margin";
        assert m.cardY(50, 4) == 50 + m.cardHeight() + m.gapY() : "5th module must wrap cleanly to row 1";

        int expectedRows = 6;
        int expectedTotalH = m.padY() * 2 + expectedRows * m.cardHeight() + (expectedRows - 1) * m.gapY();
        assert m.totalHeight() == expectedTotalH : "Unexpected totalHeight: " + m.totalHeight();
        assert m.totalHeight() <= 360 : "All 23 modules in 4 columns should stay compact (<=360px), got " + m.totalHeight();

        ModernModuleGridLayout.GridMetrics empty = ModernModuleGridLayout.computeGridMetrics(gridW, 0);
        assert empty.totalHeight() == 100 : "Empty grid height fallback should be 100";
    }

    private static void assertControlRowOrderingAndBounds() {
        ModernModuleGridLayout.GridMetrics narrowFourCol = ModernModuleGridLayout.computeGridMetrics(510, 23);
        int cardX = narrowFourCol.cardX(100, 0);
        int cardY = 80;
        int cardW = narrowFourCol.colWidth();

        assert cardW >= 115 : "Narrow 4-col width should remain >= 115px, got " + cardW;
        ModernModuleGridLayout.CardControlLayout withSettings =
                ModernModuleGridLayout.computeCardControlLayout(cardX, cardY, cardW, 30, true);

        assert withSettings.titleMaxW() >= 95 : "Title max width must keep module names readable, got " + withSettings.titleMaxW();
        assert withSettings.bindX() == cardX + ModernModuleGridLayout.CARD_PAD_X : "Bind pill must anchor bottom-left";
        assert withSettings.bindX() + withSettings.bindW() < withSettings.tuneX() : "Bind pill must sit left of Tune button";
        assert withSettings.tuneW() == ModernModuleGridLayout.TUNE_WIDTH : "Tune button must keep full width on configurable modules";
        assert withSettings.tuneX() + withSettings.tuneW() < withSettings.switchX() : "Tune button must sit left of Toggle switch";
        assert withSettings.switchX() + withSettings.switchW() <= cardX + cardW - 8 : "Toggle switch leaked past card right bound";
        assert withSettings.bindY() == withSettings.switchY() && withSettings.tuneY() == withSettings.bindY() : "Controls must align on same bottom row";
        assert withSettings.bindY() + withSettings.bindH() < cardY + ModernModuleGridLayout.CARD_HEIGHT : "Controls must fit inside card height";

        int starLeft = withSettings.starX(cardX, cardW);
        assert starLeft >= withSettings.titleX(cardX) + withSettings.titleMaxWWithStar(cardW) : "Favorite star hitbox must not overlap truncated title";
        assert starLeft + withSettings.starW() <= cardX + cardW : "Favorite star hitbox leaked past card right bound";
        assert withSettings.titleMaxWWithStar(cardW) >= 85 : "Title max width with star must remain readable, got " + withSettings.titleMaxWWithStar(cardW);

        ModernModuleGridLayout.CardControlLayout noSettings =
                ModernModuleGridLayout.computeCardControlLayout(cardX, cardY, cardW, 30, false);
        assert noSettings.tuneW() == 0 : "Tune button must have 0 width when module has no settings";
        assert noSettings.bindX() + noSettings.bindW() < noSettings.switchX() : "Bind pill must stay clear of switch when no settings";

        ModernModuleGridLayout.CardControlLayout extremeBind =
                ModernModuleGridLayout.computeCardControlLayout(cardX, cardY, cardW, 60, true);
        assert extremeBind.tuneX() + extremeBind.tuneW() < extremeBind.switchX() : "Long bind pill must clamp cleanly before switch on narrow cards";
    }

    private static void assertAlphabeticalFilteringAndSorting() {
        List<StubModule> unsorted = List.of(
                new StubModule("Abort Breaking", "exploit", "Exploit"),
                new StubModule("No Pitch Limit", "exploit", "Exploit"),
                new StubModule("Safe Walk", "movement", "Movement"),
                new StubModule("Auto Sprint", "movement", "Movement"),
                new StubModule("TrueSight", "render", "Render"),
                new StubModule("Viewmodel", "render", "Render"),
                new StubModule("Inventory HUD+", "render", "Render"),
                new StubModule("HUD Info", "render", "Render"),
                new StubModule("FastBreak", "world", "World"),
                new StubModule("Auto Break", "world", "World"),
                new StubModule("Fast Place", "world", "World"),
                new StubModule("Inventory Tweaks", "player", "Player"),
                new StubModule("Auto Crafter", "player", "Player")
        );

        List<StubModule> allSorted = ModernModuleGridLayout.filterAndSortModules(
                unsorted, StubModule::name, StubModule::catId, StubModule::catName, "ALL", "");
        assert allSorted.size() == unsorted.size();
        assert "Abort Breaking".equals(allSorted.get(0).name());
        assert "Auto Break".equals(allSorted.get(1).name());
        assert "Viewmodel".equals(allSorted.get(allSorted.size() - 1).name());

        List<StubModule> renderSorted = ModernModuleGridLayout.filterAndSortModules(
                unsorted, StubModule::name, StubModule::catId, StubModule::catName, "render", "");
        assert List.of("HUD Info", "Inventory HUD+", "TrueSight", "Viewmodel")
                .equals(renderSorted.stream().map(StubModule::name).toList());

        List<StubModule> exploitSorted = ModernModuleGridLayout.filterAndSortModules(
                unsorted, StubModule::name, StubModule::catId, StubModule::catName, "exploit", "");
        assert List.of("Abort Breaking", "No Pitch Limit")
                .equals(exploitSorted.stream().map(StubModule::name).toList());

        List<StubModule> searchSorted = ModernModuleGridLayout.filterAndSortModules(
                unsorted, StubModule::name, StubModule::catId, StubModule::catName, "render", "auto");
        assert List.of("Auto Break", "Auto Crafter", "Auto Sprint")
                .equals(searchSorted.stream().map(StubModule::name).toList());

        List<StubModule> favSorted = ModernModuleGridLayout.filterAndSortModules(
                unsorted,
                StubModule::name,
                StubModule::catId,
                StubModule::catName,
                "FAVORITES",
                "",
                null,
                m -> "Viewmodel".equals(m.name()) || "Auto Sprint".equals(m.name())
        );
        assert List.of("Auto Sprint", "Viewmodel")
                .equals(favSorted.stream().map(StubModule::name).toList());

        List<StubModule> pinned = new java.util.ArrayList<>(allSorted);
        ModernModuleGridLayout.sortFavoritesFirstInPlace(
                pinned,
                StubModule::name,
                m -> "Viewmodel".equals(m.name()) || "FastBreak".equals(m.name())
        );
        assert "FastBreak".equals(pinned.get(0).name()) && "Viewmodel".equals(pinned.get(1).name())
                : "Favorites must pin to top A-Z";
        assert "Abort Breaking".equals(pinned.get(2).name())
                : "Non-favorites must follow favorites A-Z";

        List<StubModule> singlePassPinned = ModernModuleGridLayout.filterAndSortModules(
                unsorted,
                StubModule::name,
                StubModule::catId,
                StubModule::catName,
                "ALL",
                "",
                null,
                m -> "Viewmodel".equals(m.name()) || "FastBreak".equals(m.name())
        );
        assert "FastBreak".equals(singlePassPinned.get(0).name())
                && "Viewmodel".equals(singlePassPinned.get(1).name())
                && "Abort Breaking".equals(singlePassPinned.get(2).name())
                : "filterAndSortModules with favoriteMatcher must pin favorites in single pass";
    }

    private static void assertInlineAccordionGridPlan() {
        int gridX = 160;
        int startY = 60;
        int gridW = 694;
        int moduleCount = 22;
        int fullExtraH = 140;
        ModernModuleGridLayout.GridMetrics metrics = ModernModuleGridLayout.computeGridMetrics(gridW, moduleCount);
        assert metrics.columns() == 4 : "Expected 4 columns at gridW=694";
        assert metrics.colWidth() == 161 : "Expected colWidth=161 at gridW=694, got " + metrics.colWidth();
        // 22 modules in 4 cols -> cols 0 & 1 have 6 rows (352px), cols 2 & 3 have 5 rows (296px)
        int base6RowH = 352;
        int base5RowH = 296;
        int crossoverDiff = base6RowH - base5RowH;
        assert metrics.totalHeight() == base6RowH : "Expected base totalHeight=" + base6RowH + " for 6 rows, got " + metrics.totalHeight();

        // Collapsed states: expandedIndex=-1, expandAnim=0.0f, fullExtraHeight=0, visH <= 6, out-of-range index
        List<ModernModuleGridLayout.GridPlan> collapsedPlans = List.of(
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, -1, fullExtraH, 1.0f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, fullExtraH, 0.0f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, 0, 1.0f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, 100, 0.05f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, 100, 0.06f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, fullExtraH, 0.005f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, moduleCount, fullExtraH, 1.0f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, -2, fullExtraH, 1.0f)
        );
        for (ModernModuleGridLayout.GridPlan collapsed : collapsedPlans) {
            assert collapsed.accordion() == null : "Collapsed plan must have null accordion";
            assert collapsed.expandedIndex() == -1 && collapsed.expandedRow() == -1;
            assert collapsed.totalHeight() == metrics.totalHeight() : "Collapsed totalHeight must equal base metrics";
            for (int i = 0; i < moduleCount; i++) {
                assert collapsed.cardY(startY, i) == metrics.cardY(startY, i) : "Collapsed cards must not shift";
            }
        }

        // Empty module count must also collapse cleanly
        ModernModuleGridLayout.GridPlan emptyPlan =
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, 0, 0, fullExtraH, 1.0f);
        assert emptyPlan.accordion() == null && emptyPlan.expandedIndex() == -1 && emptyPlan.totalHeight() == 100;

        // Single-column card accordion bounds at expandedIndex = 3 (col 3, row 0) with extra = 140
        ModernModuleGridLayout.GridPlan fullPlan =
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, fullExtraH, 1.0f);
        ModernModuleGridLayout.AccordionBounds acc = fullPlan.accordion();
        assert acc != null : "Expected non-null AccordionBounds when expanded";
        assert fullPlan.expandedIndex() == 3 && fullPlan.expandedRow() == 0;
        assert acc.expandedCol() == 3 && acc.expandedRow() == 0;
        assert acc.fullHeight() == fullExtraH && acc.visibleHeight() == fullExtraH;
        assert acc.w() == metrics.colWidth()
                : "Single-column accordion width must equal colWidth (" + metrics.colWidth() + "), got " + acc.w();
        assert acc.x() == fullPlan.cardX(gridX, 3)
                : "Accordion x must align with expanded card 3 x, got " + acc.x();
        assert acc.y() == startY + ModernModuleGridLayout.CARD_HEIGHT
                : "Accordion y must sit directly below card header with zero gap, got " + acc.y();
        assert acc.controlsWidth() == ModernModuleGridLayout.computeCardControlsWidth(metrics.colWidth())
                : "Accordion controlsWidth mismatch: " + acc.controlsWidth();

        // Per-column masonry shift: ONLY col == 3 && row > 0 (indices 7, 11, 15, 19) shift by acc.visibleHeight()
        List<Integer> shiftedCol3Indices = List.of(7, 11, 15, 19);
        for (int i = 0; i < moduleCount; i++) {
            int col = i % metrics.columns();
            int row = i / metrics.columns();
            int actualY = fullPlan.cardY(startY, i);
            if (col == 3 && row > 0) {
                assert shiftedCol3Indices.contains(i) : "Unexpected shifted index " + i;
                assert actualY == metrics.cardY(startY, i) + acc.visibleHeight()
                        : "Col 3 card " + i + " must shift down by exact visibleHeight";
            } else {
                assert actualY == metrics.cardY(startY, i)
                        : "Card " + i + " (col=" + col + ", row=" + row + ") must remain unshifted";
            }
        }
        assert fullPlan.cardY(startY, 7) == acc.y() + acc.visibleHeight() + metrics.gapY()
                : "First shifted card in col 3 must sit exactly gapY below expanded card bottom";

        // Uneven column totalHeight() test:
        // Col 0 & 1 have 6 rows (352px); col 2 & 3 have 5 rows (296px).
        // With extra=140 in col 3: col 3 = 296 + 140 = 436 > 352 -> totalHeight == 436
        assert fullPlan.totalHeight() == base5RowH + fullExtraH
                : "Expected totalHeight=" + (base5RowH + fullExtraH) + " when expanded col 3 exceeds col 0 height, got " + fullPlan.totalHeight();

        // With extra=30 in col 3: col 3 = 296 + 30 = 326 <= 352 -> totalHeight remains 352 (col 0 height)
        ModernModuleGridLayout.GridPlan smallPlan =
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, 30, 1.0f);
        assert smallPlan.accordion() != null && smallPlan.accordion().visibleHeight() == 30;
        assert smallPlan.totalHeight() == base6RowH
                : "Expected totalHeight=" + base6RowH + " when expanded col 3 stays <= col 0, got " + smallPlan.totalHeight();

        // Exact crossover boundary
        assert ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, crossoverDiff, 1.0f).totalHeight() == base6RowH;
        assert ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, crossoverDiff + 1, 1.0f).totalHeight() == base6RowH + 1;

        // Mid-animation expandAnim = 0.5f: scales visibleHeight to 70 and shifts ONLY col 3 && row > 0
        ModernModuleGridLayout.GridPlan halfPlan =
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 3, fullExtraH, 0.5f);
        ModernModuleGridLayout.AccordionBounds halfAcc = halfPlan.accordion();
        assert halfAcc != null;
        assert halfAcc.fullHeight() == fullExtraH;
        assert halfAcc.visibleHeight() == 70 && halfAcc.visibleHeight() < halfAcc.fullHeight()
                : "Expected visibleHeight=70 at expandAnim=0.5f, got " + halfAcc.visibleHeight();
        assert halfAcc.w() == metrics.colWidth() && halfAcc.x() == halfPlan.cardX(gridX, 3);
        assert halfAcc.y() == startY + ModernModuleGridLayout.CARD_HEIGHT;
        assert halfAcc.expandedCol() == 3 && halfAcc.expandedRow() == 0;
        for (int i = 0; i < moduleCount; i++) {
            int expectedShift = shiftedCol3Indices.contains(i) ? halfAcc.visibleHeight() : 0;
            assert halfPlan.cardY(startY, i) == metrics.cardY(startY, i) + expectedShift
                    : "Mid-animation shift mismatch at index " + i;
        }
        assert halfPlan.totalHeight() == base5RowH + 70
                : "Expected mid-animation totalHeight=" + (base5RowH + 70) + ", got " + halfPlan.totalHeight();

        // Expanding a tall column (col 0, row 1 -> index 4) shifts only indices 8, 12, 16, 20 and grows totalHeight
        ModernModuleGridLayout.GridPlan col0Row1Plan =
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 4, 30, 1.0f);
        ModernModuleGridLayout.AccordionBounds col0Acc = col0Row1Plan.accordion();
        assert col0Acc != null && col0Acc.expandedCol() == 0 && col0Acc.expandedRow() == 1;
        assert col0Acc.y() == metrics.cardY(startY, 4) + ModernModuleGridLayout.CARD_HEIGHT;
        for (int i = 0; i < moduleCount; i++) {
            boolean shouldShift = (i % 4 == 0) && (i / 4 > 1);
            assert col0Row1Plan.cardY(startY, i) == metrics.cardY(startY, i) + (shouldShift ? 30 : 0)
                    : "Col 0 row 1 shift mismatch at index " + i;
        }
        assert col0Row1Plan.totalHeight() == base6RowH + 30 : "Expected col 0 totalHeight=" + (base6RowH + 30) + ", got " + col0Row1Plan.totalHeight();
    }
}
