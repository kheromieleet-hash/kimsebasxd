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
        int invMoveOpened = 0;
        final StubInventory inv = new StubInventory();

        @Override
        public void persist(boolean reloadChunks) {
            persistCalls++;
            if (reloadChunks) {
                chunkReloads++;
            }
        }

        @Override public void openInventoryMove() { invMoveOpened++; }
        @Override public ModernModuleSettings.InventoryBridge inventory() { return inv; }
    }

    public static void main(String[] args) {
        testSettingslessModulesHaveNoControls();
        testConfigurableModulesHaveControlsAndHeight();
        testFullbrightControls();
        testFastBreakModeAndClamp();
        testFastPlaceAndFastUseSettings();
        testAutoCrafterActionsAndChips();
        testInventoryHudActions();
        testNewExploitModulesControls();
        testViewmodelControlsAndReset();
        testAllSlidersSupportDirectNumericInput();
        System.out.println("ALL ModernModuleSettingsTest ASSERTIONS PASSED!");
    }

    private static void testSettingslessModulesHaveNoControls() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        List<String> simpleModules = List.of(
                "Fullbright", "TNT Timer", "Auto Sprint", "Safe Walk",
                "TrueSight", "Item ESP", "No Pitch Limit", "Abort Breaking",
                "Anti Hunger", "Anti Levitation", "Ghost Hand", "Portal GUI", "Resource Pack Spoof",
                "Version Switcher", "Unknown Module"
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
                "Viewmodel", "Inventory HUD+", "HUD Info", "Keybinds HUD", "Player List",
                "Fast Place", "Auto Break", "FastBreak", "Inventory Tweaks",
                "Auto Replenish", "Fast Use", "Auto Tool", "Auto Crafter",
                "Auto Fish", "Freecam", "Backtrack", "Disabler", "Exploit Guard",
                "No Mining Trace", "No Slowdown", "Packet Mine", "Ping Spoof",
                "Chunk Borders", "Light Overlay", "Trajectories", "Xray", "Block ESP"
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
        RecordingActions actions = new RecordingActions();
        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("Fullbright", cfg, actions);
        assert controls.isEmpty() : "Fullbright should remain a direct vanilla max-gamma toggle without inline controls";
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

    private static void testAutoCrafterActionsAndChips() {
        ModConfig cfg = new ModConfig();
        cfg.setAllAutoCraftTargets(true);
        RecordingActions actions = new RecordingActions();
        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("Auto Crafter", cfg, actions);

        ModernModuleSettings.ActionControl pickerAction = (ModernModuleSettings.ActionControl) controls.get(2);
        assert pickerAction.labelSupplier().get().contains(Integer.toString(AutoCrafterHandler.TARGETS.size()));

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
        assert invControls.size() == 7 : "Inventory HUD+ should have 5 toggles and 2 sliders";
        for (ModernModuleSettings.Control c : invControls) {
            if (c instanceof ModernModuleSettings.ActionControl ac) {
                assert !"Open Settings...".equals(ac.labelSupplier().get()) : "Open Settings... must be removed";
            }
        }
        ModernModuleSettings.SliderControl alphaSlider = (ModernModuleSettings.SliderControl) invControls.get(6);
        alphaSlider.pctSetter().accept(0.8f);
        assert actions.inv.getBgAlpha() == 80;
    }

    private static void testNewExploitModulesControls() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();

        ModernModuleSettings.SliderControl pingDelay =
                (ModernModuleSettings.SliderControl) ModernModuleSettings.getControls("Ping Spoof", cfg, actions).get(0);
        pingDelay.pctSetter().accept(1.0f);
        assert cfg.pingSpoofDelay == 1000;
        pingDelay.pctSetter().accept(0.0f);
        assert cfg.pingSpoofDelay == 50;

        ModernModuleSettings.CycleControl noSlowMode =
                (ModernModuleSettings.CycleControl) ModernModuleSettings.getControls("No Slowdown", cfg, actions).get(0);
        assert "Vanilla".equals(noSlowMode.valueLabel().get());
        noSlowMode.onCycle().run();
        assert "Vulcan".equals(noSlowMode.valueLabel().get()) && cfg.noSlowdownMode == 1;
        noSlowMode.onCycle().run();
        assert "Grim".equals(noSlowMode.valueLabel().get()) && cfg.noSlowdownMode == 2;
        noSlowMode.onCycle().run();
        assert "Vanilla".equals(noSlowMode.valueLabel().get()) && cfg.noSlowdownMode == 0;

        ModernModuleSettings.ToggleControl toolsOnly =
                (ModernModuleSettings.ToggleControl) ModernModuleSettings.getControls("No Mining Trace", cfg, actions).get(0);
        assert cfg.noMiningTraceToolsOnly;
        toolsOnly.setter().accept(false);
        assert !cfg.noMiningTraceToolsOnly;

        ModernModuleSettings.SliderControl backtrackDelay =
                (ModernModuleSettings.SliderControl) ModernModuleSettings.getControls("Backtrack", cfg, actions).get(0);
        backtrackDelay.pctSetter().accept(1.0f);
        assert cfg.backtrackDelay == 300;
        backtrackDelay.pctSetter().accept(0.0f);
        assert cfg.backtrackDelay == 40;

        ModernModuleSettings.SliderControl maxParticles =
                (ModernModuleSettings.SliderControl) ModernModuleSettings.getControls("Exploit Guard", cfg, actions).get(0);
        maxParticles.pctSetter().accept(1.0f);
        assert cfg.exploitGuardMaxParticles == 2000;
        maxParticles.pctSetter().accept(0.0f);
        assert cfg.exploitGuardMaxParticles == 100;

        ModernModuleSettings.CycleControl disablerMode =
                (ModernModuleSettings.CycleControl) ModernModuleSettings.getControls("Disabler", cfg, actions).get(0);
        assert "Vulcan".equals(disablerMode.valueLabel().get());
        disablerMode.onCycle().run();
        assert "Grim".equals(disablerMode.valueLabel().get()) && cfg.disablerMode == 1;
        disablerMode.onCycle().run();
        assert "Vulcan".equals(disablerMode.valueLabel().get()) && cfg.disablerMode == 0;

        List<ModernModuleSettings.Control> pmControls = ModernModuleSettings.getControls("Packet Mine", cfg, actions);
        assert pmControls.size() == 3;
        ModernModuleSettings.ToggleControl rebreak = (ModernModuleSettings.ToggleControl) pmControls.get(0);
        assert cfg.packetMineRebreak;
        rebreak.setter().accept(false);
        assert !cfg.packetMineRebreak;
        ModernModuleSettings.SliderControl pmDelay = (ModernModuleSettings.SliderControl) pmControls.get(1);
        pmDelay.pctSetter().accept(1.0f);
        assert cfg.packetMineDelay == 5;
        pmDelay.pctSetter().accept(0.0f);
        assert cfg.packetMineDelay == 0;
        ModernModuleSettings.ToggleControl renderBox = (ModernModuleSettings.ToggleControl) pmControls.get(2);
        assert cfg.packetMineRender;
        renderBox.setter().accept(false);
        assert !cfg.packetMineRender;

        // Chunk Borders, Light Overlay, Trajectories
        ModernModuleSettings.ToggleControl subGrid =
                (ModernModuleSettings.ToggleControl) ModernModuleSettings.getControls("Chunk Borders", cfg, actions).get(0);
        assert !cfg.chunkBordersSubGrid;
        subGrid.setter().accept(true);
        assert cfg.chunkBordersSubGrid;

        List<ModernModuleSettings.Control> lightControls = ModernModuleSettings.getControls("Light Overlay", cfg, actions);
        assert lightControls.size() == 2;
        ModernModuleSettings.SliderControl lightRange = (ModernModuleSettings.SliderControl) lightControls.get(0);
        lightRange.pctSetter().accept(1.0f);
        assert cfg.lightOverlayRange == 24;
        lightRange.pctSetter().accept(0.0f);
        assert cfg.lightOverlayRange == 8;
        ModernModuleSettings.ToggleControl onlyAlways = (ModernModuleSettings.ToggleControl) lightControls.get(1);
        onlyAlways.setter().accept(true);
        assert cfg.lightOverlayOnlyAlways;

        List<ModernModuleSettings.Control> trajControls = ModernModuleSettings.getControls("Trajectories", cfg, actions);
        assert trajControls.size() == 2;
        ((ModernModuleSettings.ToggleControl) trajControls.get(0)).setter().accept(false);
        ((ModernModuleSettings.ToggleControl) trajControls.get(1)).setter().accept(false);
        assert !cfg.trajectoriesShowPassive && !cfg.trajectoriesBoxOutline;

        assert ModernModuleSettings.getControls("Client Chat", cfg, actions).isEmpty()
                : "Client Chat must not be exposed as a configurable/toggleable module";
    }

    private static void testViewmodelControlsAndReset() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        List<ModernModuleSettings.Control> vm = ModernModuleSettings.getControls("Viewmodel", cfg, actions);
        assert vm.size() == 21 : "Expected 21 Viewmodel controls (NoFrills + AirWalker + Item/Player Scale), got " + vm.size();

        ((ModernModuleSettings.ToggleControl) vm.get(0)).setter().accept(true);
        ((ModernModuleSettings.ToggleControl) vm.get(1)).setter().accept(true);
        ((ModernModuleSettings.ToggleControl) vm.get(2)).setter().accept(true);
        ((ModernModuleSettings.ToggleControl) vm.get(3)).setter().accept(true);
        ((ModernModuleSettings.ToggleControl) vm.get(4)).setter().accept(true);
        ((ModernModuleSettings.ToggleControl) vm.get(5)).setter().accept(true);
        assert cfg.vmNoHaste && cfg.vmNoEquip && cfg.vmNoBowSwing && cfg.vmNoSwingReset && cfg.vmApplyToHand && cfg.vmAirWalker;

        ModernModuleSettings.SliderControl speed = (ModernModuleSettings.SliderControl) vm.get(6);
        assert "Off".equals(speed.valueLabel().get());
        speed.pctSetter().accept(0.25f);
        assert cfg.vmSwingSpeed == 25 && "25t".equals(speed.valueLabel().get());

        ModernModuleSettings.SliderControl itemScale = (ModernModuleSettings.SliderControl) vm.get(7);
        itemScale.pctSetter().accept(1.0f);
        assert Math.abs(cfg.itemScale - 2.0f) < 0.001f;

        ModernModuleSettings.SliderControl playerScale = (ModernModuleSettings.SliderControl) vm.get(8);
        playerScale.pctSetter().accept(0.0f);
        assert Math.abs(cfg.playerScale - 0.10f) < 0.001f;

        ModernModuleSettings.SliderControl offsetX = (ModernModuleSettings.SliderControl) vm.get(9);
        offsetX.pctSetter().accept(1.0f);
        assert Math.abs(cfg.vmOffsetX - 2.0f) < 0.001f;

        ModernModuleSettings.SliderControl scaleX = (ModernModuleSettings.SliderControl) vm.get(12);
        scaleX.pctSetter().accept(0.5f);
        assert Math.abs(cfg.vmScaleX - 2.5f) < 0.001f;

        ModernModuleSettings.SliderControl rotZ = (ModernModuleSettings.SliderControl) vm.get(17);
        rotZ.pctSetter().accept(0.0f);
        assert Math.abs(cfg.vmRotZ + 180.0f) < 0.001f;

        ModernModuleSettings.SliderControl swingY = (ModernModuleSettings.SliderControl) vm.get(19);
        swingY.pctSetter().accept(0.0f);
        assert Math.abs(cfg.vmSwingY) < 0.001f;

        int beforePersist = actions.persistCalls;
        ModernModuleSettings.resetModuleToDefaults("Viewmodel", cfg, actions);
        assert actions.persistCalls == beforePersist + 1;
        assert !cfg.vmNoHaste && !cfg.vmNoEquip && !cfg.vmAirWalker && cfg.vmSwingSpeed == 0;
        assert Math.abs(cfg.itemScale - 1.0f) < 0.001f && Math.abs(cfg.playerScale - 1.0f) < 0.001f;
        assert Math.abs(cfg.vmOffsetX) < 0.001f && Math.abs(cfg.vmScaleX - 1.0f) < 0.001f;
        assert Math.abs(cfg.vmRotZ) < 0.001f && Math.abs(cfg.vmSwingY - 1.0f) < 0.001f;
    }

    private static void testAllSlidersSupportDirectNumericInput() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        List<String> sliderModules = List.of(
                "Viewmodel", "Inventory HUD+", "Fast Place", "FastBreak",
                "Inventory Tweaks", "Auto Replenish", "Fast Use", "Auto Crafter",
                "Freecam", "Backtrack", "Exploit Guard", "Packet Mine", "Ping Spoof", "Light Overlay"
        );
        int sliderCount = 0;
        for (String mod : sliderModules) {
            for (ModernModuleSettings.Control c : ModernModuleSettings.getControls(mod, cfg, actions)) {
                if (c instanceof ModernModuleSettings.SliderControl s) {
                    sliderCount++;
                    assert s.editText() != null && s.editText().get() != null
                            : "Missing editText on slider " + mod + "/" + s.key();
                    assert s.textCommitter() != null
                            : "Missing textCommitter on slider " + mod + "/" + s.key();
                    assert !s.textCommitter().test("not-a-number")
                            : "Invalid text must be rejected for " + mod + "/" + s.key();
                }
            }
        }
        assert sliderCount >= 23 : "Expected all module sliders tested, got " + sliderCount;

        // Viewmodel floats & int sliders (including commas, exact values, negatives, clamping)
        List<ModernModuleSettings.Control> vm = ModernModuleSettings.getControls("Viewmodel", cfg, actions);
        ModernModuleSettings.SliderControl swingSpeed = (ModernModuleSettings.SliderControl) vm.get(6);
        assert swingSpeed.textCommitter().test("25") && cfg.vmSwingSpeed == 25;
        assert swingSpeed.textCommitter().test("999") && cfg.vmSwingSpeed == 100;
        assert swingSpeed.textCommitter().test("off") && cfg.vmSwingSpeed == 0;

        ModernModuleSettings.SliderControl itemScale = (ModernModuleSettings.SliderControl) vm.get(7);
        assert "1.00".equals(itemScale.editText().get());
        assert itemScale.textCommitter().test("1,23") && Math.abs(cfg.itemScale - 1.23f) < 0.001f;
        assert itemScale.textCommitter().test("-5") && Math.abs(cfg.itemScale - 0.10f) < 0.001f;

        ModernModuleSettings.SliderControl offsetX = (ModernModuleSettings.SliderControl) vm.get(9);
        assert offsetX.textCommitter().test("-0.75") && Math.abs(cfg.vmOffsetX + 0.75f) < 0.001f;

        ModernModuleSettings.SliderControl rotX = (ModernModuleSettings.SliderControl) vm.get(15);
        assert rotX.textCommitter().test("-45.5") && Math.abs(cfg.vmRotX + 45.5f) < 0.001f;

        // FastBreak dynamic mode range
        cfg.fastBreakMode = 1; // Vulcan max 1.15x
        ModernModuleSettings.SliderControl breakSpeed =
                (ModernModuleSettings.SliderControl) ModernModuleSettings.getControls("FastBreak", cfg, actions).get(1);
        assert breakSpeed.textCommitter().test("3.5x") && Math.abs(cfg.fastBreakSpeed - 1.15f) < 0.001f;
        cfg.fastBreakMode = 0; // Vanilla up to 5.0x
        assert breakSpeed.textCommitter().test("3.2x") && Math.abs(cfg.fastBreakSpeed - 3.2f) < 0.001f;

        // Input buffer & hitbox helpers
        assert "-0.59".equals(ModernModuleSettings.sanitizeSliderInputString("abc-0,5.9x", 16))
                : "Expected sanitized '-0.59', got " + ModernModuleSettings.sanitizeSliderInputString("abc-0,5.9x", 16);
        assert "5".equals(ModernModuleSettings.appendSliderInputChar("1.00", true, '5', 16));
        assert ModernModuleSettings.mapKeyCodeToSliderChar(48, false, false) == '0';
        assert ModernModuleSettings.mapKeyCodeToSliderChar(57, false, false) == '9';
        assert ModernModuleSettings.mapKeyCodeToSliderChar(325, false, false) == '5';
        assert ModernModuleSettings.mapKeyCodeToSliderChar(330, false, false) == '.';
        assert ModernModuleSettings.mapKeyCodeToSliderChar(333, false, false) == '-';
        assert ModernModuleSettings.mapKeyCodeToSliderChar(49, true, false) == 0;
        assert ModernModuleSettings.mapKeyCodeToSliderChar(49, false, true) == 0;
        assert ModernModuleSettings.isInsideSliderValueBox(145, 22, 10, 20, 150, 24);
        assert !ModernModuleSettings.isInsideSliderValueBox(50, 35, 10, 20, 150, 24);
        assert ModernModuleSettings.matchesSubSetting("Inventory Tweaks", "auto sorting", cfg, actions);
        assert ModernModuleSettings.matchesSubSetting("Viewmodel", "airwalker", cfg, actions);
        assert !ModernModuleSettings.matchesSubSetting("Viewmodel", "auto sorting", cfg, actions);
    }
}
