package com.client.mod;

/**
 * Verifies {@link ModernModuleGridLayout} column breakpoints, card coordinates,
 * control ordering, and non-overlapping compact tile bounds.
 */
public class ModernModuleGridLayoutTest {

    public static void main(String[] args) {
        assertResponsiveColumnBreakpoints();
        assertFourColumnGeometryAndFit();
        assertControlRowOrderingAndBounds();
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
}
