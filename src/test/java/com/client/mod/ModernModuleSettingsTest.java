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
        testAutoCrafterActionsAndChips();
        testInventoryHudActions();
        testNewExploitModulesControls();
        testViewmodelControlsAndReset();
        System.out.println("ALL ModernModuleSettingsTest ASSERTIONS PASSED!");
    }

    private static void testSettingslessModulesHaveNoControls() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        List<String> simpleModules = List.of(
                "HUD Info", "TNT Timer", "Auto Sprint", "Safe Walk",
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
                "Viewmodel", "Inventory HUD+", "Fullbright", "Keybinds HUD",
                "Fast Place", "Auto Break", "FastBreak", "Inventory Tweaks",
                "Auto Replenish", "Fast Use", "Auto Tool", "Auto Crafter",
                "Better Chat", "Backtrack", "Disabler", "Exploit Guard",
                "No Mining Trace", "No Slowdown", "Packet Mine", "Ping Spoof",
                "Chunk Borders", "Light Overlay", "Trajectories"
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

        List<ModernModuleSettings.Control> chatControls = ModernModuleSettings.getControls("Client Chat", cfg, actions);
        assert chatControls.size() == 3;
        ModernModuleSettings.ToggleControl allowDms = (ModernModuleSettings.ToggleControl) chatControls.get(0);
        assert cfg.clientChatAllowDms && allowDms.getter().get();
        allowDms.setter().accept(false);
        assert !cfg.clientChatAllowDms;
        assert chatControls.get(1) instanceof ModernModuleSettings.ActionControl;
        assert chatControls.get(2) instanceof ModernModuleSettings.ActionControl;
    }

    private static void testViewmodelControlsAndReset() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        List<ModernModuleSettings.Control> vm = ModernModuleSettings.getControls("Viewmodel", cfg, actions);
        assert vm.size() == 22 : "Expected 22 Viewmodel controls (NoFrills + AirWalker + Item/Player Scale + Reset), got " + vm.size();

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
        ((ModernModuleSettings.ActionControl) vm.get(21)).onClick().run();
        assert actions.persistCalls == beforePersist + 1;
        assert !cfg.vmNoHaste && !cfg.vmNoEquip && !cfg.vmAirWalker && cfg.vmSwingSpeed == 0;
        assert Math.abs(cfg.itemScale - 1.0f) < 0.001f && Math.abs(cfg.playerScale - 1.0f) < 0.001f;
        assert Math.abs(cfg.vmOffsetX) < 0.001f && Math.abs(cfg.vmScaleX - 1.0f) < 0.001f;
        assert Math.abs(cfg.vmRotZ) < 0.001f && Math.abs(cfg.vmSwingY - 1.0f) < 0.001f;
    }
}
