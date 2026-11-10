package com.client.mod;

import java.util.List;

/**
 * Verifies {@link ModernInspectorLayout} bottom-drawer bounds, header geometry,
 * and responsive multi-column control placement.
 */
public class ModernInspectorLayoutTest {

    private static ModernModuleSettings.Actions stubActions() {
        ModernModuleSettings.InventoryBridge inv = new ModernModuleSettings.InventoryBridge() {
            @Override public boolean isActive() { return false; }
            @Override public void setActive(boolean value) {}
            @Override public boolean isArmor() { return false; }
            @Override public void setArmor(boolean value) {}
            @Override public boolean isPotions() { return false; }
            @Override public void setPotions(boolean value) {}
            @Override public boolean isMini() { return false; }
            @Override public void setMini(boolean value) {}
            @Override public boolean isVert() { return false; }
            @Override public void setVert(boolean value) {}
            @Override public int getBgAlpha() { return 60; }
            @Override public void setBgAlpha(int alpha) {}
        };
        return new ModernModuleSettings.Actions() {
            @Override public void persist(boolean reloadChunks) {}
            @Override public void openInventorySettings() {}
            @Override public void openInventoryMove() {}
            @Override public void openVersionSwitcher() {}
            @Override public ModernModuleSettings.InventoryBridge inventory() { return inv; }
        };
    }

    public static void main(String[] args) {
        assertBottomDrawerSitsBelowFullWidthGrid();
        assertHeaderLayoutAndNonOverlap();
        assertMultiColumnControlsAndAutoCrafterChips();
        System.out.println("ALL ModernInspectorLayoutTest ASSERTIONS PASSED!");
    }

    private static void assertBottomDrawerSitsBelowFullWidthGrid() {
        ModConfig cfg = new ModConfig();
        ModernModuleSettings.Actions actions = stubActions();
        List<ModernModuleSettings.Control> dulkirControls = ModernModuleSettings.getControls("Dulkir", cfg, actions);
        List<ModernModuleSettings.Control> fastBreakControls = ModernModuleSettings.getControls("FastBreak", cfg, actions);

        int contentX = 160;
        int bodyY = 80;
        int contentW = 694;
        int bodyH = 383;

        assert ModernInspectorLayout.computeBottomDrawerBounds(contentX, bodyY, contentW, bodyH, 0) == null;
        assert ModernInspectorLayout.computeBottomDrawerBounds(contentX, bodyY, contentW, bodyH, 16) == null;

        int fastBreakSlot = ModernInspectorLayout.computeTargetDrawerSlotHeight(contentW, bodyH, fastBreakControls);
        int dulkirSlot = ModernInspectorLayout.computeTargetDrawerSlotHeight(contentW, bodyH, dulkirControls);
        assert fastBreakSlot < dulkirSlot : "Expected compact drawer for FastBreak vs Dulkir";
        assert dulkirSlot <= (int) (bodyH * 0.56f) + 2 : "Dulkir bottom drawer must leave top module grid visible";

        int[] bounds = ModernInspectorLayout.computeBottomDrawerBounds(contentX, bodyY, contentW, bodyH, dulkirSlot);
        assert bounds != null;
        int gridH = bodyH - dulkirSlot;
        assert bounds[0] == contentX + ModernInspectorLayout.BOTTOM_MARGIN_X;
        assert bounds[1] >= bodyY + gridH : "Bottom drawer must sit strictly below grid region";
        assert bounds[2] == contentW - ModernInspectorLayout.BOTTOM_MARGIN_X * 2 : "Drawer must span full content width";
        assert bounds[1] + bounds[3] <= bodyY + bodyH : "Bottom drawer must stay inside bodyH";
    }

    private static void assertHeaderLayoutAndNonOverlap() {
        ModernInspectorLayout.HeaderLayout wide = ModernInspectorLayout.computeHeaderLayout(170, 260, 674);
        assert wide.wide() && wide.headerHeight() == ModernInspectorLayout.WIDE_HEADER_HEIGHT;
        assert wide.textMaxW() > 400 : "Wide header should reserve full left space for title + description";
        assert 170 + 12 + wide.textMaxW() < wide.statX() : "Left text must not overlap status pill";
        assert wide.statX() + wide.statW() < wide.bindX() : "Status pill must precede Bind pill";
        assert wide.bindX() + wide.bindW() < wide.closeX() : "Bind pill must precede Close button";
        assert wide.closeX() + wide.closeW() <= 170 + 674 - 8 : "Close button must stay inside right edge";

        ModernInspectorLayout.HeaderLayout compact = ModernInspectorLayout.computeHeaderLayout(20, 200, 320);
        assert !compact.wide() && compact.headerHeight() == ModernInspectorLayout.COMPACT_HEADER_HEIGHT;
        assert compact.statY() == compact.bindY() && compact.statX() + compact.statW() < compact.bindX();
    }

    private static void assertMultiColumnControlsAndAutoCrafterChips() {
        ModConfig cfg = new ModConfig();
        ModernModuleSettings.Actions actions = stubActions();

        List<ModernModuleSettings.Control> dulkir = ModernModuleSettings.getControls("Dulkir", cfg, actions);
        ModernInspectorLayout.ControlsFlow dulkirFlow = ModernInspectorLayout.computeControlsFlow(10, 20, 650, dulkir);
        assert dulkirFlow.items().size() == dulkir.size();
        assert dulkirFlow.items().get(0).y() == dulkirFlow.items().get(1).y() : "First 2 controls must sit side-by-side";
        assert dulkirFlow.items().get(0).x() < dulkirFlow.items().get(1).x();
        assert dulkirFlow.totalHeight() <= 150 : "Dulkir 2-col layout should fit cleanly without excessive height, got " + dulkirFlow.totalHeight();

        List<ModernModuleSettings.Control> crafter = ModernModuleSettings.getControls("Auto Crafter", cfg, actions);
        ModernInspectorLayout.ControlsFlow crafterFlow = ModernInspectorLayout.computeControlsFlow(10, 20, 650, crafter);
        ModernInspectorLayout.PlacedControl last = crafterFlow.items().get(crafterFlow.items().size() - 1);
        assert last.control() instanceof ModernModuleSettings.ChipsControl;
        assert last.x() == 10 && last.w() == 650 : "ChipsControl must span full inner width";
        assert last.chipCols() == 4 : "Wide bottom drawer should render 4 chips per row, got " + last.chipCols();
        assert last.y() > crafterFlow.items().get(crafterFlow.items().size() - 2).y() : "ChipsControl must start on its own row";

        ModernInspectorLayout.ControlsFlow narrowFlow = ModernInspectorLayout.computeControlsFlow(10, 20, 340, crafter);
        ModernInspectorLayout.PlacedControl narrowChips = narrowFlow.items().get(narrowFlow.items().size() - 1);
        assert narrowChips.chipCols() == 2 : "Narrow drawer must fall back to 2 chip columns";
    }
}
