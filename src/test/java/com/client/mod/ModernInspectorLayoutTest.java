package com.client.mod;

import java.util.List;

/**
 * Verifies {@link ModernInspectorLayout} responsive multi-column control placement,
 * chip column sizing, and inline accordion full-height integration.
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
            @Override public ModernModuleSettings.InventoryBridge inventory() { return inv; }
        };
    }

    public static void main(String[] args) {
        assertMultiColumnControlsAndAutoCrafterChips();
        assertOddAndSingleControlsFitWithoutOverflow();
        assertAccordionIntegrationHeight();
        System.out.println("ALL ModernInspectorLayoutTest ASSERTIONS PASSED!");
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
        assert last.chipCols() == 4 : "Wide accordion should render 4 chips per row, got " + last.chipCols();
        assert last.y() > crafterFlow.items().get(crafterFlow.items().size() - 2).y() : "ChipsControl must start on its own row";

        ModernInspectorLayout.ControlsFlow narrowFlow = ModernInspectorLayout.computeControlsFlow(10, 20, 340, crafter);
        ModernInspectorLayout.PlacedControl narrowChips = narrowFlow.items().get(narrowFlow.items().size() - 1);
        assert narrowChips.chipCols() == 2 : "Narrow accordion must fall back to 2 chip columns";
    }

    private static void assertOddAndSingleControlsFitWithoutOverflow() {
        ModConfig cfg = new ModConfig();
        ModernModuleSettings.Actions actions = stubActions();

        ModernInspectorLayout.ControlsFlow emptyFlow = ModernInspectorLayout.computeControlsFlow(12, 40, 645, List.of());
        assert emptyFlow.items().isEmpty() && emptyFlow.totalHeight() == 0 : "Empty controls must yield zero flow height";
        ModernInspectorLayout.ControlsFlow nullFlow = ModernInspectorLayout.computeControlsFlow(12, 40, 645, null);
        assert nullFlow.items().isEmpty() && nullFlow.totalHeight() == 0 : "Null controls must yield zero flow height";

        for (String singleMod : List.of("Auto Break", "Fast Use")) {
            List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls(singleMod, cfg, actions);
            assert controls.size() == 1 : "Expected 1 control for " + singleMod;
            for (int width : new int[]{645, 420, 419, 340}) {
                ModernInspectorLayout.ControlsFlow flow = ModernInspectorLayout.computeControlsFlow(12, 40, width, controls);
                assert flow.items().size() == 1;
                ModernInspectorLayout.PlacedControl item = flow.items().get(0);
                assert item.x() == 12 : "Single control must start at x=12 for w=" + width;
                assert item.y() == 40 + ModernModuleSettings.SECTION_TOP_PAD;
                assert item.x() + item.w() <= 12 + width : "Single control overflowed width=" + width;
                assert flow.totalHeight() == ModernModuleSettings.SECTION_TOP_PAD + item.h() + ModernModuleSettings.SECTION_BOTTOM_PAD
                        : "Unexpected single-control totalHeight for " + singleMod + " at w=" + width;
            }
        }

        for (String oddMod : List.of("Keybinds HUD", "Better Chat", "Inventory Tweaks", "Inventory HUD+")) {
            List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls(oddMod, cfg, actions);
            assert (controls.size() & 1) == 1 : "Expected odd control count for " + oddMod;

            for (int wideW : new int[]{645, 421, 420}) {
                ModernInspectorLayout.ControlsFlow wide = ModernInspectorLayout.computeControlsFlow(12, 40, wideW, controls);
                assert wide.items().size() == controls.size();
                assertNoOverflowWithinFlow(12, 40, wideW, wide);
                ModernInspectorLayout.PlacedControl first = wide.items().get(0);
                ModernInspectorLayout.PlacedControl second = wide.items().get(1);
                assert first.y() == second.y() : oddMod + " first two controls must share row 0 in 2-col";
                assert second.x() == first.x() + first.w() + ModernInspectorLayout.CONTROLS_COL_GAP;

                ModernInspectorLayout.PlacedControl last = wide.items().get(wide.items().size() - 1);
                ModernInspectorLayout.PlacedControl prev = wide.items().get(wide.items().size() - 2);
                assert last.x() == 12 : oddMod + " trailing odd control must start in left column";
                assert last.y() >= prev.y() + prev.h() + ModernModuleSettings.CONTROL_GAP
                        : oddMod + " trailing odd control must sit on a new row below previous row";
                assert 40 + wide.totalHeight() == last.y() + last.h() + ModernModuleSettings.SECTION_BOTTOM_PAD
                        : oddMod + " 2-col totalHeight mismatch";
            }

            for (int narrowW : new int[]{419, 340}) {
                ModernInspectorLayout.ControlsFlow narrow = ModernInspectorLayout.computeControlsFlow(12, 40, narrowW, controls);
                assert narrow.items().size() == controls.size();
                assertNoOverflowWithinFlow(12, 40, narrowW, narrow);
                for (int i = 0; i < narrow.items().size(); i++) {
                    ModernInspectorLayout.PlacedControl item = narrow.items().get(i);
                    assert item.x() == 12 && item.w() == narrowW : oddMod + " 1-col item must fill exact width";
                    if (i > 0) {
                        ModernInspectorLayout.PlacedControl prev = narrow.items().get(i - 1);
                        assert item.y() == prev.y() + prev.h() + ModernModuleSettings.CONTROL_GAP;
                    }
                }
                ModernInspectorLayout.PlacedControl last = narrow.items().get(narrow.items().size() - 1);
                assert 40 + narrow.totalHeight() == last.y() + last.h() + ModernModuleSettings.SECTION_BOTTOM_PAD;
            }
        }

        for (int width : new int[]{645, 480, 479, 340}) {
            int chipCols = ModernInspectorLayout.computeChipColumns(width);
            assert chipCols == (width >= 480 ? 4 : 2);
            int chipW = ModernInspectorLayout.computeChipWidth(width, chipCols);
            assert chipCols * chipW + (chipCols - 1) * ModernInspectorLayout.CHIP_GAP_X <= width
                    : "Chips overflowed width=" + width;
        }
    }

    private static void assertNoOverflowWithinFlow(int x, int startY, int w, ModernInspectorLayout.ControlsFlow flow) {
        for (ModernInspectorLayout.PlacedControl pc : flow.items()) {
            assert pc.x() >= x : "Control leaked past left edge: " + pc;
            assert pc.x() + pc.w() <= x + w : "Control overflowed right edge (" + (x + w) + "): " + pc;
            assert pc.y() >= startY + ModernModuleSettings.SECTION_TOP_PAD : "Control leaked above top pad: " + pc;
            assert pc.y() + pc.h() <= startY + flow.totalHeight() - ModernModuleSettings.SECTION_BOTTOM_PAD
                    : "Control leaked past bottom pad: " + pc;
        }
    }

    private static void assertAccordionIntegrationHeight() {
        ModConfig cfg = new ModConfig();
        ModernModuleSettings.Actions actions = stubActions();

        assert ModernModuleGridLayout.computeAccordionFullHeight(694, List.of()) == 0
                : "Empty controls must return 0 accordion height";
        assert ModernModuleGridLayout.computeAccordionFullHeight(694, null) == 0
                : "Null controls must return 0 accordion height";

        List<ModernModuleSettings.Control> dulkirControls = ModernModuleSettings.getControls("Dulkir", cfg, actions);
        int panelW = ModernModuleGridLayout.computeAccordionPanelWidth(694);
        int controlsW = Math.max(80, panelW - 24);
        ModernInspectorLayout.ControlsFlow dulkirFlow = ModernInspectorLayout.computeControlsFlow(0, 0, controlsW, dulkirControls);

        int fullH = ModernModuleGridLayout.computeAccordionFullHeight(694, dulkirControls);
        assert fullH == 22 + 1 + dulkirFlow.totalHeight() + 6
                : "Accordion height mismatch: expected " + (22 + 1 + dulkirFlow.totalHeight() + 6) + ", got " + fullH;
        assert fullH == ModernModuleGridLayout.ACCORDION_HEADER_HEIGHT + 1 + dulkirFlow.totalHeight() + ModernModuleGridLayout.ACCORDION_BOTTOM_PAD;

        int narrowPanelW = ModernModuleGridLayout.computeAccordionPanelWidth(340);
        int narrowControlsW = Math.max(80, narrowPanelW - 24);
        assert narrowControlsW < 420 : "Expected 1-col controlsWidth at gridW=340, got " + narrowControlsW;
        ModernInspectorLayout.ControlsFlow narrowFlow = ModernInspectorLayout.computeControlsFlow(0, 0, narrowControlsW, dulkirControls);
        int narrowFullH = ModernModuleGridLayout.computeAccordionFullHeight(340, dulkirControls);
        assert narrowFullH == 22 + 1 + narrowFlow.totalHeight() + 6;
        assert narrowFullH > fullH : "1-col narrow accordion should be taller than 2-col wide accordion";
    }
}
