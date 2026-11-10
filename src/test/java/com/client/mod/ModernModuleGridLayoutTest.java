package com.client.mod;

import java.util.List;

/**
 * Verifies {@link ModernModuleGridLayout} column breakpoints, card coordinates,
 * control ordering, alphabetical module sorting, non-overlapping compact tile bounds,
 * and inline row-anchored accordion grid plans.
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
        assert m.totalHeight() <= 330 : "All 23 modules in 4 columns should stay compact (<=330px), got " + m.totalHeight();

        ModernModuleGridLayout.GridMetrics empty = ModernModuleGridLayout.computeGridMetrics(gridW, 0);
        assert empty.totalHeight() == 100 : "Empty grid height fallback should be 100";
    }

    private static void assertControlRowOrderingAndBounds() {
        ModernModuleGridLayout.GridMetrics narrowFourCol = ModernModuleGridLayout.computeGridMetrics(510, 23);
        int cardX = narrowFourCol.cardX(100, 0);
        int cardY = 80;
        int cardW = narrowFourCol.colWidth();

        assert cardW >= 115 : "Narrow 4-col width should remain >= 115px, got " + cardW;
        ModernModuleGridLayout.CardControlLayout layout =
                ModernModuleGridLayout.computeCardControlLayout(cardX, cardY, cardW, 30);

        assert layout.titleMaxW() >= 95 : "Title max width must keep module names readable, got " + layout.titleMaxW();
        assert layout.tuneX() >= cardX + 6 : "Tune button leaked past card left bound: tuneX=" + layout.tuneX();
        assert layout.tuneX() + layout.tuneW() < layout.bindX() : "Tune icon must sit left of Bind pill";
        assert layout.bindX() + layout.bindW() < layout.switchX() : "Bind pill must sit left of Toggle switch";
        assert layout.switchX() + layout.switchW() <= cardX + cardW - 8 : "Toggle switch leaked past card right bound";
        assert layout.bindY() == layout.switchY() && layout.tuneY() == layout.bindY() : "Controls must align on same bottom row";
        assert layout.bindY() + layout.bindH() < cardY + ModernModuleGridLayout.CARD_HEIGHT : "Controls must fit inside card height";
    }

    private static void assertAlphabeticalFilteringAndSorting() {
        List<StubModule> unsorted = List.of(
                new StubModule("Abort Breaking", "exploit", "Exploit"),
                new StubModule("No Pitch Limit", "exploit", "Exploit"),
                new StubModule("Safe Walk", "movement", "Movement"),
                new StubModule("Auto Sprint", "movement", "Movement"),
                new StubModule("TrueSight", "render", "Render"),
                new StubModule("Dulkir", "render", "Render"),
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
        assert "TrueSight".equals(allSorted.get(allSorted.size() - 1).name());

        List<StubModule> renderSorted = ModernModuleGridLayout.filterAndSortModules(
                unsorted, StubModule::name, StubModule::catId, StubModule::catName, "render", "");
        assert List.of("Dulkir", "HUD Info", "Inventory HUD+", "TrueSight")
                .equals(renderSorted.stream().map(StubModule::name).toList());

        List<StubModule> exploitSorted = ModernModuleGridLayout.filterAndSortModules(
                unsorted, StubModule::name, StubModule::catId, StubModule::catName, "exploit", "");
        assert List.of("Abort Breaking", "No Pitch Limit")
                .equals(exploitSorted.stream().map(StubModule::name).toList());

        List<StubModule> searchSorted = ModernModuleGridLayout.filterAndSortModules(
                unsorted, StubModule::name, StubModule::catId, StubModule::catName, "render", "auto");
        assert List.of("Auto Break", "Auto Crafter", "Auto Sprint")
                .equals(searchSorted.stream().map(StubModule::name).toList());
    }

    private static void assertInlineAccordionGridPlan() {
        int gridX = 160;
        int startY = 60;
        int gridW = 694;
        int moduleCount = 23;
        int fullH = 140;
        ModernModuleGridLayout.GridMetrics metrics = ModernModuleGridLayout.computeGridMetrics(gridW, moduleCount);

        // Collapsed states: expandedIndex=-1, expandAnim=0.0f, fullAccordionHeight=0, tiny anim <= 6px, out-of-range index
        List<ModernModuleGridLayout.GridPlan> collapsedPlans = List.of(
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, -1, fullH, 1.0f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 2, fullH, 0.0f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 2, 0, 1.0f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 2, 100, 0.05f),
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, moduleCount, fullH, 1.0f)
        );
        for (ModernModuleGridLayout.GridPlan collapsed : collapsedPlans) {
            assert collapsed.accordion() == null : "Collapsed plan must have null accordion";
            assert collapsed.expandedIndex() == -1 && collapsed.expandedRow() == -1;
            assert collapsed.closeButtonBounds() == null : "Collapsed plan must have null close button";
            assert collapsed.totalHeight() == metrics.totalHeight() : "Collapsed totalHeight must equal base metrics";
            for (int i = 0; i < moduleCount; i++) {
                assert collapsed.cardY(startY, i) == metrics.cardY(startY, i) : "Collapsed cards must not shift";
            }
        }

        // Mid-animation: expandAnim = 0.5f
        ModernModuleGridLayout.GridPlan halfPlan =
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 2, fullH, 0.5f);
        ModernModuleGridLayout.AccordionBounds halfAcc = halfPlan.accordion();
        assert halfAcc != null;
        assert halfAcc.fullHeight() == fullH;
        assert halfAcc.visibleHeight() == 70 && halfAcc.visibleHeight() < halfAcc.fullHeight()
                : "Expected visibleHeight 70 < 140 at expandAnim=0.5f, got " + halfAcc.visibleHeight();
        int halfShift = ModernModuleGridLayout.ACCORDION_TOP_GAP + halfAcc.visibleHeight() + ModernModuleGridLayout.ACCORDION_POST_GAP;
        for (int i = 0; i < 4; i++) {
            assert halfPlan.cardY(startY, i) == startY : "Row 0 must remain unshifted during mid-animation";
        }
        for (int i = 4; i < moduleCount; i++) {
            assert halfPlan.cardY(startY, i) == metrics.cardY(startY, i) + halfShift
                    : "Row > expandedRow must shift by exact accordion delta during animation";
        }
        assert halfPlan.totalHeight() == metrics.totalHeight() + halfShift;

        // Fully expanded card in row 0 (expandedIndex = 2 in 4 cols, gridW = 694, startY = 60)
        ModernModuleGridLayout.GridPlan fullPlan =
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 2, fullH, 1.0f);
        ModernModuleGridLayout.AccordionBounds acc = fullPlan.accordion();
        assert acc != null;
        assert fullPlan.expandedIndex() == 2 && fullPlan.expandedRow() == 0 && acc.expandedRow() == 0;
        assert acc.visibleHeight() == fullH && acc.fullHeight() == fullH;

        for (int i = 0; i < 4; i++) {
            assert fullPlan.cardY(startY, i) == startY : "Row 0 card " + i + " must stay unshifted at startY";
        }
        assert acc.x() == gridX + ModernModuleGridLayout.GRID_PAD_X
                : "Accordion x must align with grid left pad, got " + acc.x();
        assert acc.w() == ModernModuleGridLayout.computeAccordionPanelWidth(gridW);
        assert acc.x() + acc.w() == fullPlan.cardX(gridX, 3) + metrics.colWidth()
                : "Accordion right edge must align flush with col 3 right edge";
        assert acc.y() == startY + ModernModuleGridLayout.CARD_HEIGHT + ModernModuleGridLayout.ACCORDION_TOP_GAP
                : "Accordion y must sit directly below row 0 + ACCORDION_TOP_GAP";
        assert acc.anchorCenterX() == fullPlan.cardX(gridX, 2) + metrics.colWidth() / 2
                : "Accordion anchorCenterX must center directly under expanded card 2";
        assert acc.controlsWidth() == acc.w() - 24 : "Accordion controlsWidth must reserve 12px inner padding each side";

        int fullShift = ModernModuleGridLayout.ACCORDION_TOP_GAP + acc.visibleHeight() + ModernModuleGridLayout.ACCORDION_POST_GAP;
        for (int i = 4; i <= 7; i++) {
            int cy = fullPlan.cardY(startY, i);
            assert cy > acc.y() + acc.visibleHeight()
                    : "Row 1 card " + i + " must sit cleanly below expanded accordion bottom";
            assert cy == metrics.cardY(startY, i) + fullShift
                    : "Row 1 card " + i + " shift mismatch";
        }

        int[] close = fullPlan.closeButtonBounds();
        assert close != null && close.length == 4;
        assert close[2] == 14 && close[3] == 14 : "Close button must be 14x14";
        assert close[0] >= acc.x() && close[0] + close[2] <= acc.x() + acc.w()
                : "Close button must stay horizontally inside accordion bounds";
        assert close[1] >= acc.y() && close[1] + close[3] <= acc.y() + ModernModuleGridLayout.ACCORDION_HEADER_HEIGHT
                : "Close button must stay vertically inside accordion header bar";

        assert fullPlan.totalHeight() == metrics.totalHeight() + fullShift
                : "totalHeight must exceed base metrics by exact ACCORDION_TOP_GAP + visibleHeight + ACCORDION_POST_GAP";

        // Expanded card in row 1 (expandedIndex = 5): row 0 & 1 unshifted, row 2+ shifted
        ModernModuleGridLayout.GridPlan row1Plan =
                ModernModuleGridLayout.computeGridPlan(gridX, startY, gridW, moduleCount, 5, fullH, 1.0f);
        ModernModuleGridLayout.AccordionBounds row1Acc = row1Plan.accordion();
        assert row1Acc != null && row1Plan.expandedRow() == 1;
        assert row1Acc.y() == metrics.cardY(startY, 5) + ModernModuleGridLayout.CARD_HEIGHT + ModernModuleGridLayout.ACCORDION_TOP_GAP;
        assert row1Acc.anchorCenterX() == row1Plan.cardX(gridX, 5) + metrics.colWidth() / 2;
        for (int i = 0; i < 8; i++) {
            assert row1Plan.cardY(startY, i) == metrics.cardY(startY, i) : "Rows <= expandedRow must stay unshifted";
        }
        for (int i = 8; i < moduleCount; i++) {
            assert row1Plan.cardY(startY, i) > row1Acc.y() + row1Acc.visibleHeight();
            assert row1Plan.cardY(startY, i) == metrics.cardY(startY, i) + fullShift;
        }
    }
}
