package com.client.mod;

import java.util.List;

public class ModernModuleSettingsTest {

    private static final class StubInventory implements ModernModuleSettings.InventoryBridge {
        boolean active = true;
        boolean armor = true;
        boolean potions = true;
        boolean mini = false;
        boolean vert = false;
        int alpha = 60;

        @Override public boolean isActive() { return active; }
        @Override public void setActive(boolean value) { active = value; }
        @Override public boolean isArmor() { return armor; }
        @Override public void setArmor(boolean value) { armor = value; }
        @Override public boolean isPotions() { return potions; }
        @Override public void setPotions(boolean value) { potions = value; }
        @Override public boolean isMini() { return mini; }
        @Override public void setMini(boolean value) { mini = value; }
        @Override public boolean isVert() { return vert; }
        @Override public void setVert(boolean value) { vert = value; }
        @Override public int getBgAlpha() { return alpha; }
        @Override public void setBgAlpha(int value) { alpha = value; }
    }

    private static final class RecordingActions implements ModernModuleSettings.Actions {
        int persistCalls = 0;
        int chunkReloads = 0;
        int invSettingsOpened = 0;
        int invMoveOpened = 0;
        final StubInventory inv = new StubInventory();

        @Override
        public void persist(boolean reloadChunks) {
            persistCalls++;
            if (reloadChunks) {
                chunkReloads++;
            }
        }

        @Override public void openInventorySettings() { invSettingsOpened++; }
        @Override public void openInventoryMove() { invMoveOpened++; }
        @Override public ModernModuleSettings.InventoryBridge inventory() { return inv; }
    }

    public static void main(String[] args) {
        testSettingslessModulesHaveNoControls();
        testConfigurableModulesHaveControlsAndHeight();
        testFullbrightControls();
        testFastBreakModeAndClamp();
        testFastPlaceAndFastUseSettings();
        testDulkirSlidersAndReset();
        testAutoCrafterActionsAndChips();
        testInventoryHudActions();
        System.out.println("ALL ModernModuleSettingsTest ASSERTIONS PASSED!");
    }

    private static void testSettingslessModulesHaveNoControls() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        List<String> simpleModules = List.of(
                "HUD Info", "TNT Timer", "Auto Sprint", "Safe Walk",
                "TrueSight", "Item ESP", "No Pitch Limit", "Abort Breaking",
                "GUI Style", "Version Switcher", "Unknown Module"
        );
        for (String name : simpleModules) {
            assert !ModernModuleSettings.hasSettings(name, cfg, actions)
                    : "Expected no extra settings for " + name;
            assert ModernModuleSettings.computeExpandedHeight(
                    ModernModuleSettings.getControls(name, cfg, actions)) == 0
                    : "Expected zero expanded height for " + name;
        }
    }

    private static void testConfigurableModulesHaveControlsAndHeight() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        List<String> configurable = List.of(
                "Dulkir", "Inventory HUD+", "Fullbright", "Keybinds HUD",
                "Fast Place", "Auto Break", "FastBreak", "Inventory Tweaks",
                "Auto Replenish", "Fast Use", "Auto Tool", "Auto Crafter",
                "Better Chat"
        );
        for (String name : configurable) {
            List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls(name, cfg, actions);
            assert !controls.isEmpty() : "Expected controls for " + name;
            assert ModernModuleSettings.computeExpandedHeight(controls) > ModernModuleSettings.SECTION_TOP_PAD
                    : "Expected positive expanded height for " + name;
        }
    }

    private static void testFullbrightControls() {
        ModConfig cfg = new ModConfig();
        cfg.fullbrightMode = 0;
        RecordingActions actions = new RecordingActions();
        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("Fullbright", cfg, actions);

        ModernModuleSettings.CycleControl mode = (ModernModuleSettings.CycleControl) controls.get(0);
        assert "Natural".equals(mode.valueLabel().get());
        mode.onCycle().run();
        assert cfg.fullbrightMode == 1 : "Expected mode 1 after cycle";
        assert actions.chunkReloads == 1 : "Expected chunk reload on mode cycle";

        cfg.fullbrightMode = 0;
        ModernModuleSettings.SliderControl brightness = (ModernModuleSettings.SliderControl) controls.get(1);
        brightness.pctSetter().accept(1.0f);
        assert Math.abs(cfg.fullbrightBrightness - 1.0f) < 0.001f;
        assert cfg.fullbrightMode == 1 : "Expected gamma mode enabled when dragging brightness from Natural";
        assert brightness.needsChunkReload();
    }

    private static void testFastBreakModeAndClamp() {
        ModConfig cfg = new ModConfig();
        cfg.fastBreakMode = 0;
        cfg.fastBreakSpeed = 3.5f;
        RecordingActions actions = new RecordingActions();
        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("FastBreak", cfg, actions);

        ModernModuleSettings.CycleControl mode = (ModernModuleSettings.CycleControl) controls.get(0);
        mode.onCycle().run();
        assert cfg.fastBreakMode == 1;
        assert Math.abs(cfg.fastBreakSpeed - 1.15f) < 0.001f : "Expected Vulcan speed clamp <= 1.15f";

        ModernModuleSettings.SliderControl speed = (ModernModuleSettings.SliderControl) controls.get(1);
        speed.pctSetter().accept(0.0f);
        assert Math.abs(cfg.fastBreakSpeed - 1.0f) < 0.001f;
    }

    private static void testFastPlaceAndFastUseSettings() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();

        List<ModernModuleSettings.Control> fastPlaceControls = ModernModuleSettings.getControls("Fast Place", cfg, actions);
        assert fastPlaceControls.size() == 2 : "Expected Auto Right Click + Fast Place Delay under Fast Place";
        ModernModuleSettings.ToggleControl autoRightClick = (ModernModuleSettings.ToggleControl) fastPlaceControls.get(0);
        assert "Auto Right Click".equals(autoRightClick.label());
        assert !cfg.fastPlaceAutoRightClick;
        autoRightClick.setter().accept(true);
        assert cfg.fastPlaceAutoRightClick : "Expected Fast Place Auto Right Click toggle to update config";
        ModernModuleSettings.SliderControl fpDelay = (ModernModuleSettings.SliderControl) fastPlaceControls.get(1);
        assert "fastPlaceDelay".equals(fpDelay.key());

        List<ModernModuleSettings.Control> fastUseControls = ModernModuleSettings.getControls("Fast Use", cfg, actions);
        assert fastUseControls.size() == 1 : "Expected only Cooldown under Fast Use, got " + fastUseControls.size();
        ModernModuleSettings.SliderControl cooldown = (ModernModuleSettings.SliderControl) fastUseControls.get(0);
        assert "fastUseCooldown".equals(cooldown.key());
    }

    private static void testDulkirSlidersAndReset() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("Dulkir", cfg, actions);

        ModernModuleSettings.SliderControl itemScale = (ModernModuleSettings.SliderControl) controls.get(0);
        itemScale.pctSetter().accept(1.0f);
        assert Math.abs(cfg.itemScale - 2.0f) < 0.001f;

        ModernModuleSettings.SliderControl posZ = (ModernModuleSettings.SliderControl) controls.get(4);
        posZ.pctSetter().accept(1.0f);
        assert cfg.posZ == 50 : "Expected posZ upper bound 50, got " + cfg.posZ;

        ModernModuleSettings.ToggleControl airWalker = (ModernModuleSettings.ToggleControl) controls.get(8);
        airWalker.setter().accept(true);
        assert cfg.dulkirAirWalker;

        ModernModuleSettings.ActionControl reset = (ModernModuleSettings.ActionControl) controls.get(9);
        reset.onClick().run();
        assert Math.abs(cfg.itemScale - 1.0f) < 0.001f;
        assert cfg.posZ == 0 && !cfg.dulkirAirWalker && cfg.swingDuration == 6;
    }

    private static void testAutoCrafterActionsAndChips() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("Auto Crafter", cfg, actions);

        ModernModuleSettings.ActionControl selectAll = (ModernModuleSettings.ActionControl) controls.get(2);
        selectAll.onClick().run();
        assert cfg.autoCrafterTargets.size() == AutoCrafterHandler.TARGETS.size();

        ModernModuleSettings.ChipsControl chips = (ModernModuleSettings.ChipsControl) controls.get(3);
        ModernModuleSettings.ChipOption firstChip = chips.options().get(0);
        boolean before = firstChip.activeGetter().get();
        firstChip.onToggle().run();
        assert firstChip.activeGetter().get() != before : "Expected chip toggle to flip state";
    }

    private static void testInventoryHudActions() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();

        List<ModernModuleSettings.Control> invControls = ModernModuleSettings.getControls("Inventory HUD+", cfg, actions);
        ModernModuleSettings.SliderControl alphaSlider = (ModernModuleSettings.SliderControl) invControls.get(6);
        alphaSlider.pctSetter().accept(0.8f);
        assert actions.inv.getBgAlpha() == 80;
        ((ModernModuleSettings.ActionControl) invControls.get(7)).onClick().run();
        ((ModernModuleSettings.ActionControl) invControls.get(8)).onClick().run();
        assert actions.invSettingsOpened == 1 && actions.invMoveOpened == 1;
    }
}
