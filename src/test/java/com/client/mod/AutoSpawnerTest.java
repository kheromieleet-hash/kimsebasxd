package com.client.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class AutoSpawnerTest {

    private static final class StubInventory implements ModernModuleSettings.InventoryBridge {
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
        @Override public int getBgAlpha() { return 0; }
        @Override public void setBgAlpha(int value) {}
    }

    private static final class RecordingActions implements ModernModuleSettings.Actions {
        int persistCalls = 0;
        final StubInventory inv = new StubInventory();

        @Override
        public void persist(boolean reloadChunks) {
            persistCalls++;
        }

        @Override public void openInventoryMove() {}
        @Override public ModernModuleSettings.InventoryBridge inventory() { return inv; }
    }

    public static void main(String[] args) {
        testConfigDefaultsCopyAndSanitize();
        testModuleSettingsControls();
        testModuleCardDescription();
        testSnakePatternPreservesMiddleSpawners();
        testParseDepoPageInfo();
        testSpawnerAutoSellSettingsAndCard();
        testCustomFoldersInConfig();
        testEconomyHudMathAndCompactVsDetailed();
        testTacticalEspAndGizmoRendering();
        testPenaltyMapAndAntiStuckEscalation();
        testSpawnerSafetyGuard();
        testTpsPingAdaptiveAndFreezeDetection();
        testLegitKinematicsAndNonSnapDisable();
        testReachBatchingAndWeightedChestRouting();
        testChatPauseAndResume();
        testStackedChestColumnDeduplication();
        testSequentialPerimeterChestCycling();
        System.out.println("ALL AutoSpawnerTest ASSERTIONS PASSED!");
    }

    private static void testConfigDefaultsCopyAndSanitize() {
        ModConfig cfg = new ModConfig();
        assert !cfg.autoSpawnerEnabled : "autoSpawnerEnabled should default to false";
        assert cfg.autoSpawnerCount == 0 : "autoSpawnerCount should default to 0 (Auto)";
        assert cfg.autoSpawnerSearchRadius == 26 : "autoSpawnerSearchRadius should default to 26";
        assert cfg.autoSpawnerClickDelay == 3 : "autoSpawnerClickDelay should default to 3";
        assert cfg.autoSpawnerLockMouse : "autoSpawnerLockMouse should default to true";
        assert cfg.autoSpawnerRenderEsp : "autoSpawnerRenderEsp should default to true";
        assert cfg.autoSpawnerHudX == -1 : "autoSpawnerHudX should default to -1";
        assert cfg.autoSpawnerHudY == -1 : "autoSpawnerHudY should default to -1";

        // Wave 1 v2 defaults
        assert cfg.autoSpawnerHudDetailed : "autoSpawnerHudDetailed should default to true";
        assert cfg.autoSpawnerEspDetail == 2 : "autoSpawnerEspDetail should default to 2 (Tactical)";
        assert cfg.autoSpawnerReachBatch : "autoSpawnerReachBatch should default to true";
        assert cfg.autoSpawnerLegitMode : "autoSpawnerLegitMode should default to true";
        assert cfg.autoSpawnerSafetyEnabled : "autoSpawnerSafetyEnabled should default to true";
        assert cfg.autoSpawnerSafetyRadius == 16 : "autoSpawnerSafetyRadius should default to 16";
        assert cfg.autoSpawnerSafetyAction == 1 : "autoSpawnerSafetyAction should default to 1 (Auto-Resume)";
        assert cfg.autoSpawnerSafetyResumeSec == 15 : "autoSpawnerSafetyResumeSec should default to 15";
        assert cfg.autoSpawnerTpsAdaptive : "autoSpawnerTpsAdaptive should default to true";
        assert cfg.autoSpawnerAutoResume : "autoSpawnerAutoResume should default to true";

        cfg.autoSpawnerEnabled = true;
        cfg.autoSpawnerCount = 500;
        cfg.autoSpawnerSearchRadius = 99;
        cfg.autoSpawnerClickDelay = 1;
        cfg.autoSpawnerLockMouse = false;
        cfg.autoSpawnerRenderEsp = false;
        cfg.autoSpawnerHudX = 9999;
        cfg.autoSpawnerHudY = -42;
        cfg.autoSpawnerEspDetail = 7;
        cfg.autoSpawnerSafetyRadius = 2;
        cfg.autoSpawnerSafetyAction = -1;
        cfg.autoSpawnerSafetyResumeSec = 999;
        cfg.sanitize();

        // ModConfig.clampInt falls back to default when out of range
        assert cfg.autoSpawnerCount == 0 : "Expected autoSpawnerCount fallback 0, got " + cfg.autoSpawnerCount;
        assert cfg.autoSpawnerSearchRadius == 26 : "Expected autoSpawnerSearchRadius fallback 26, got " + cfg.autoSpawnerSearchRadius;
        assert cfg.autoSpawnerClickDelay == 3 : "Expected autoSpawnerClickDelay fallback 3, got " + cfg.autoSpawnerClickDelay;
        assert cfg.autoSpawnerHudX == -1 : "Expected autoSpawnerHudX fallback -1, got " + cfg.autoSpawnerHudX;
        assert cfg.autoSpawnerHudY == -1 : "Expected autoSpawnerHudY fallback -1, got " + cfg.autoSpawnerHudY;
        assert cfg.autoSpawnerEspDetail == 2 : "Expected autoSpawnerEspDetail fallback 2, got " + cfg.autoSpawnerEspDetail;
        assert cfg.autoSpawnerSafetyRadius == 16 : "Expected autoSpawnerSafetyRadius fallback 16, got " + cfg.autoSpawnerSafetyRadius;
        assert cfg.autoSpawnerSafetyAction == 1 : "Expected autoSpawnerSafetyAction fallback 1, got " + cfg.autoSpawnerSafetyAction;
        assert cfg.autoSpawnerSafetyResumeSec == 15 : "Expected autoSpawnerSafetyResumeSec fallback 15, got " + cfg.autoSpawnerSafetyResumeSec;

        int[] defaultBounds = AutoSpawnerModule.resolveHudBounds(null, 854, 480, cfg);
        assert defaultBounds[0] == 8 && defaultBounds[1] == 128 : "Expected default HUD bounds at (8, 128)";
        assert defaultBounds[2] >= 138 && defaultBounds[3] > 60 : "Expected valid card dimensions";

        cfg.autoSpawnerCount = 64;
        cfg.autoSpawnerSearchRadius = 32;
        cfg.autoSpawnerClickDelay = 6;
        cfg.autoSpawnerHudX = 320;
        cfg.autoSpawnerHudY = 180;
        cfg.autoSpawnerHudDetailed = false;
        cfg.autoSpawnerEspDetail = 0;
        cfg.autoSpawnerReachBatch = false;
        cfg.autoSpawnerLegitMode = false;
        cfg.autoSpawnerSafetyEnabled = false;
        cfg.autoSpawnerSafetyRadius = 48;
        cfg.autoSpawnerSafetyAction = 2;
        cfg.autoSpawnerSafetyResumeSec = 120;
        cfg.autoSpawnerTpsAdaptive = false;
        cfg.autoSpawnerAutoResume = false;
        cfg.sanitize();

        int[] customBounds = AutoSpawnerModule.resolveHudBounds(null, 854, 480, cfg);
        assert customBounds[0] == 320 && customBounds[1] == 180 : "Expected custom HUD bounds at (320, 180)";

        ModConfig copy = new ModConfig();
        copy.copyFrom(cfg);
        assert copy.autoSpawnerEnabled == cfg.autoSpawnerEnabled;
        assert copy.autoSpawnerCount == 64;
        assert copy.autoSpawnerSearchRadius == 32;
        assert copy.autoSpawnerClickDelay == 6;
        assert !copy.autoSpawnerLockMouse;
        assert !copy.autoSpawnerRenderEsp;
        assert copy.autoSpawnerHudX == 320 && copy.autoSpawnerHudY == 180;
        assert !copy.autoSpawnerHudDetailed;
        assert copy.autoSpawnerEspDetail == 0;
        assert !copy.autoSpawnerReachBatch;
        assert !copy.autoSpawnerLegitMode;
        assert !copy.autoSpawnerSafetyEnabled;
        assert copy.autoSpawnerSafetyRadius == 48;
        assert copy.autoSpawnerSafetyAction == 2;
        assert copy.autoSpawnerSafetyResumeSec == 120;
        assert !copy.autoSpawnerTpsAdaptive;
        assert !copy.autoSpawnerAutoResume;

        // copyFrom also sanitizes out-of-range ints from untrusted imported configs
        ModConfig bad = new ModConfig();
        bad.autoSpawnerEspDetail = -3;
        bad.autoSpawnerSafetyRadius = 500;
        bad.autoSpawnerSafetyAction = 9;
        bad.autoSpawnerSafetyResumeSec = 2;
        ModConfig sanitizedCopy = new ModConfig();
        sanitizedCopy.copyFrom(bad);
        assert sanitizedCopy.autoSpawnerEspDetail == 2;
        assert sanitizedCopy.autoSpawnerSafetyRadius == 16;
        assert sanitizedCopy.autoSpawnerSafetyAction == 1;
        assert sanitizedCopy.autoSpawnerSafetyResumeSec == 15;
    }

    private static void testModuleSettingsControls() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        assert ModernModuleSettings.hasSettings("Auto Spawner", cfg, actions);

        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("Auto Spawner", cfg, actions);
        assert controls.size() == 15 : "Expected 15 controls for Auto Spawner, got " + controls.size();
        assert ModernModuleSettings.computeExpandedHeight(controls) > ModernModuleSettings.SECTION_TOP_PAD;

        ModernModuleSettings.SliderControl countSlider = (ModernModuleSettings.SliderControl) controls.get(0);
        assert "Auto".equals(countSlider.valueLabel().get()) : "Expected 'Auto' when count=0";
        assert countSlider.textCommitter().test("32") && cfg.autoSpawnerCount == 32;
        assert "32".equals(countSlider.valueLabel().get());

        ModernModuleSettings.SliderControl radiusSlider = (ModernModuleSettings.SliderControl) controls.get(1);
        assert radiusSlider.textCommitter().test("30") && cfg.autoSpawnerSearchRadius == 30;

        ModernModuleSettings.SliderControl delaySlider = (ModernModuleSettings.SliderControl) controls.get(2);
        assert delaySlider.textCommitter().test("5") && cfg.autoSpawnerClickDelay == 5;

        ModernModuleSettings.ToggleControl lockToggle = (ModernModuleSettings.ToggleControl) controls.get(3);
        assert lockToggle.getter().get();
        lockToggle.setter().accept(false);
        assert !cfg.autoSpawnerLockMouse;

        ModernModuleSettings.ToggleControl espToggle = (ModernModuleSettings.ToggleControl) controls.get(4);
        assert espToggle.getter().get();
        espToggle.setter().accept(false);
        assert !cfg.autoSpawnerRenderEsp;

        // 10 new Auto Spawner controls (indices 5..14)
        ModernModuleSettings.ToggleControl detailedHudToggle = (ModernModuleSettings.ToggleControl) controls.get(5);
        assert "Detailed HUD".equals(detailedHudToggle.label()) && detailedHudToggle.getter().get();
        detailedHudToggle.setter().accept(false);
        assert !cfg.autoSpawnerHudDetailed;

        ModernModuleSettings.CycleControl espDetailCycle = (ModernModuleSettings.CycleControl) controls.get(6);
        assert "ESP Detail".equals(espDetailCycle.label()) && "Tactical".equals(espDetailCycle.valueLabel().get());
        espDetailCycle.onCycle().run();
        assert cfg.autoSpawnerEspDetail == 0 && "Minimal".equals(espDetailCycle.valueLabel().get());
        espDetailCycle.onCycle().run();
        assert cfg.autoSpawnerEspDetail == 1 && "Standard".equals(espDetailCycle.valueLabel().get());
        espDetailCycle.onCycle().run();
        assert cfg.autoSpawnerEspDetail == 2 && "Tactical".equals(espDetailCycle.valueLabel().get());

        ModernModuleSettings.ToggleControl reachBatchToggle = (ModernModuleSettings.ToggleControl) controls.get(7);
        assert "Reach Batching".equals(reachBatchToggle.label()) && reachBatchToggle.getter().get();
        reachBatchToggle.setter().accept(false);
        assert !cfg.autoSpawnerReachBatch;

        ModernModuleSettings.ToggleControl legitToggle = (ModernModuleSettings.ToggleControl) controls.get(8);
        assert "Legit Kinematics".equals(legitToggle.label()) && legitToggle.getter().get();
        legitToggle.setter().accept(false);
        assert !cfg.autoSpawnerLegitMode;

        ModernModuleSettings.ToggleControl safetyToggle = (ModernModuleSettings.ToggleControl) controls.get(9);
        assert "Safety Guard".equals(safetyToggle.label()) && safetyToggle.getter().get();
        safetyToggle.setter().accept(false);
        assert !cfg.autoSpawnerSafetyEnabled;

        ModernModuleSettings.SliderControl safetyRadiusSlider = (ModernModuleSettings.SliderControl) controls.get(10);
        assert "autoSpawnerSafetyRadius".equals(safetyRadiusSlider.key());
        assert "16b".equals(safetyRadiusSlider.valueLabel().get());
        assert safetyRadiusSlider.textCommitter().test("24b") && cfg.autoSpawnerSafetyRadius == 24;
        assert safetyRadiusSlider.textCommitter().test("2") && cfg.autoSpawnerSafetyRadius == 6;
        assert safetyRadiusSlider.textCommitter().test("99") && cfg.autoSpawnerSafetyRadius == 48;

        ModernModuleSettings.CycleControl safetyActionCycle = (ModernModuleSettings.CycleControl) controls.get(11);
        assert "Safety Action".equals(safetyActionCycle.label()) && "Auto-Resume".equals(safetyActionCycle.valueLabel().get());
        safetyActionCycle.onCycle().run();
        assert cfg.autoSpawnerSafetyAction == 2 && "Disconnect".equals(safetyActionCycle.valueLabel().get());
        safetyActionCycle.onCycle().run();
        assert cfg.autoSpawnerSafetyAction == 0 && "Pause".equals(safetyActionCycle.valueLabel().get());
        safetyActionCycle.onCycle().run();
        assert cfg.autoSpawnerSafetyAction == 1 && "Auto-Resume".equals(safetyActionCycle.valueLabel().get());

        ModernModuleSettings.SliderControl safetyResumeSlider = (ModernModuleSettings.SliderControl) controls.get(12);
        assert "autoSpawnerSafetyResumeSec".equals(safetyResumeSlider.key());
        assert "15s".equals(safetyResumeSlider.valueLabel().get());
        assert safetyResumeSlider.textCommitter().test("30s") && cfg.autoSpawnerSafetyResumeSec == 30;
        assert safetyResumeSlider.textCommitter().test("60 s") && cfg.autoSpawnerSafetyResumeSec == 60;
        assert safetyResumeSlider.textCommitter().test("45") && cfg.autoSpawnerSafetyResumeSec == 45;
        assert safetyResumeSlider.textCommitter().test("1") && cfg.autoSpawnerSafetyResumeSec == 5;
        assert safetyResumeSlider.textCommitter().test("999") && cfg.autoSpawnerSafetyResumeSec == 120;

        ModernModuleSettings.ToggleControl tpsToggle = (ModernModuleSettings.ToggleControl) controls.get(13);
        assert "TPS Adaptive".equals(tpsToggle.label()) && tpsToggle.getter().get();
        tpsToggle.setter().accept(false);
        assert !cfg.autoSpawnerTpsAdaptive;

        ModernModuleSettings.ToggleControl resumeToggle = (ModernModuleSettings.ToggleControl) controls.get(14);
        assert "Reconnect Resume".equals(resumeToggle.label()) && resumeToggle.getter().get();
        resumeToggle.setter().accept(false);
        assert !cfg.autoSpawnerAutoResume;

        assert actions.persistCalls == 14 : "Expected 14 persist calls (8 toggles + 6 cycle steps), got " + actions.persistCalls;
    }

    private static void testModuleCardDescription() {
        String desc = ModernModuleCardUi.getModuleDescription("Auto Spawner", "World");
        assert desc != null && !desc.isBlank() && !"World module".equals(desc)
                : "Expected dedicated description for Auto Spawner, got: " + desc;
    }

    private static void testSnakePatternPreservesMiddleSpawners() {
        List<BlockPos> grid = new ArrayList<>();
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                grid.add(new BlockPos(x, 64, z));
            }
        }
        List<BlockPos> ordered = SpawnerRoomScanner.sortSpawnersInSnakePattern(grid, new Vec3(0, 64, 0));
        assert ordered.size() == 49 : "Expected all 49 spawners in 7x7 grid, got " + ordered.size();
        Set<BlockPos> unique = new HashSet<>(ordered);
        assert unique.size() == 49 : "Expected 49 unique spawners, got " + unique.size();
        assert unique.contains(new BlockPos(3, 64, 3)) : "Center spawner (3, 64, 3) must be included";
    }

    private static void testParseDepoPageInfo() {
        int[] p1 = AutoSpawnerModule.parseDepoPageInfo("DEPO 1/3");
        assert p1 != null && p1[0] == 1 && p1[1] == 3 : "Expected 1/3";
        int[] p2 = AutoSpawnerModule.parseDepoPageInfo("DEPO 1/4");
        assert p2 != null && p2[0] == 1 && p2[1] == 4 : "Expected 1/4";
        int[] p3 = AutoSpawnerModule.parseDepoPageInfo("DEPO 1/1");
        assert p3 != null && p3[0] == 1 && p3[1] == 1 : "Expected 1/1";
        int[] p4 = AutoSpawnerModule.parseDepoPageInfo("§8ᴅᴇᴘᴏ §71§8/§72");
        assert p4 != null && p4[0] == 1 && p4[1] == 2 : "Expected 1/2 when color codes & small-caps present";
        assert AutoSpawnerModule.normalizeTurkishText("§8ᴅᴇᴘᴏ 1/2").contains("depo")
                : "Small-caps depo must normalize to depo";
        assert AutoSpawnerModule.normalizeTurkishText("§6§lꜱᴏɴʀᴀᴋɪ ꜱᴀʏꜰᴀ »").contains("sonraki sayfa")
                : "Small-caps sonraki sayfa must normalize";
        assert AutoSpawnerModule.parseDepoPageInfo("Sandık") == null : "Expected null for non-paged title";
    }

    private static void testSpawnerAutoSellSettingsAndCard() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        assert cfg.spawnerAutoSellTargetGoldBlock : "spawnerAutoSellTargetGoldBlock should default to true";
        assert ModernModuleSettings.hasSettings("Spawner Mode", cfg, actions);
        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("Spawner Mode", cfg, actions);
        assert controls.size() == 4 : "Expected 4 controls for Spawner Mode, got " + controls.size();
        ModernModuleSettings.CycleControl targetCycle = (ModernModuleSettings.CycleControl) controls.get(0);
        assert "Gold Block (ON)".equals(targetCycle.valueLabel().get());
        targetCycle.onCycle().run();
        assert !cfg.spawnerAutoSellTargetGoldBlock;
        assert "Gold Nugget (OFF)".equals(targetCycle.valueLabel().get());
        String desc = ModernModuleCardUi.getModuleDescription("Spawner Mode", "World");
        assert desc != null && !desc.isBlank() && !"World module".equals(desc);

        assert cfg.spawnerAutoSellHudX == -1 && cfg.spawnerAutoSellHudY == -1;
        cfg.spawnerAutoSellHudX = 5000;
        cfg.spawnerAutoSellHudY = -20;
        cfg.sanitize();
        assert cfg.spawnerAutoSellHudX == -1 && cfg.spawnerAutoSellHudY == -1;
        int[] smDefault = SpawnerAutoSellModule.resolveHudBounds(null, 854, 480, cfg, false);
        assert smDefault[0] == 8 && smDefault[1] == 128 : "Expected Spawner Mode default (8, 128)";
        cfg.autoSpawnerHudDetailed = true;
        int[] smEditorDetailed = SpawnerAutoSellModule.resolveHudBounds(null, 854, 480, cfg, true);
        assert smEditorDetailed[0] == 8 && smEditorDetailed[1] == 256
                : "Expected Spawner Mode editor default (8, 256) when Detailed HUD is active, got " + smEditorDetailed[1];
        cfg.autoSpawnerHudDetailed = false;
        int[] smEditorCompact = SpawnerAutoSellModule.resolveHudBounds(null, 854, 480, cfg, true);
        assert smEditorCompact[0] == 8 && smEditorCompact[1] == 206
                : "Expected Spawner Mode editor default (8, 206) when Compact HUD is active, got " + smEditorCompact[1];

        cfg.autoSpawnerHudDetailed = true;
        HudLayoutPresets.applyPresetState(HudLayoutPresets.Preset.FARM, cfg, 960, 540);
        int[] farmAsDetailed = AutoSpawnerModule.resolveHudBounds(null, 960, 540, cfg);
        assert cfg.spawnerAutoSellHudY >= cfg.autoSpawnerHudY + farmAsDetailed[3]
                && cfg.spawnerAutoSellHudY >= cfg.autoSpawnerHudY + 122
                : "FARM preset Spawner Mode Y (" + cfg.spawnerAutoSellHudY + ") must clear Detailed Auto Spawner HUD";

        cfg.autoSpawnerHudDetailed = false;
        HudLayoutPresets.applyPresetState(HudLayoutPresets.Preset.FARM, cfg, 960, 540);
        int[] farmAsCompact = AutoSpawnerModule.resolveHudBounds(null, 960, 540, cfg);
        assert cfg.spawnerAutoSellHudY >= cfg.autoSpawnerHudY + farmAsCompact[3]
                : "FARM preset Spawner Mode Y (" + cfg.spawnerAutoSellHudY + ") must clear Compact Auto Spawner HUD";

        cfg.spawnerAutoSellHudX = 240;
        cfg.spawnerAutoSellHudY = 160;
        cfg.sanitize();
        ModConfig copy = new ModConfig();
        copy.copyFrom(cfg);
        assert copy.spawnerAutoSellHudX == 240 && copy.spawnerAutoSellHudY == 160;
    }

    private static void testCustomFoldersInConfig() {
        ModConfig cfg = new ModConfig();
        ModConfig.CustomModuleFolder f = new ModConfig.CustomModuleFolder();
        f.id = "FOLDER_1";
        f.name = "Farm";
        f.modules.add("Auto Spawner");
        f.modules.add("Spawner Auto Sell");
        cfg.customFolders.add(f);
        cfg.favoriteModules.add("Spawner Auto Sell");
        cfg.sanitize();

        assert cfg.isModuleInCustomFolder("FOLDER_1", "Auto Spawner");
        assert !cfg.isModuleInCustomFolder("FOLDER_1", "Auto Fish");
        assert cfg.isModuleInCustomFolder("FOLDER_1", "Spawner Mode") : "Legacy folder entry should migrate";
        assert cfg.favoriteModules.contains("Spawner Mode") : "Legacy favorite should migrate";

        ModConfig copy = new ModConfig();
        copy.copyFrom(cfg);
        assert copy.customFolders.size() == 1;
        assert copy.isModuleInCustomFolder("FOLDER_1", "Spawner Mode");
    }

    private static void testEconomyHudMathAndCompactVsDetailed() {
        // Blocks/hr math & edge guards
        assert AutoSpawnerModule.computeBlocksPerHour(0, 60_000L) == 0.0;
        assert AutoSpawnerModule.computeBlocksPerHour(-5, 60_000L) == 0.0;
        assert AutoSpawnerModule.computeBlocksPerHour(10, 0L) == 0.0;
        assert AutoSpawnerModule.computeBlocksPerHour(10, -100L) == 0.0;
        assert Math.abs(AutoSpawnerModule.computeBlocksPerHour(60, 1_800_000L) - 120.0) < 1e-6;
        assert Math.abs(AutoSpawnerModule.computeBlocksPerHour(180, 3_600_000L) - 180.0) < 1e-6;

        // Pass ETA math & edge guards
        assert AutoSpawnerModule.computePassEtaSeconds(0, 2.5) == 0;
        assert AutoSpawnerModule.computePassEtaSeconds(-3, 2.5) == 0;
        assert AutoSpawnerModule.computePassEtaSeconds(10, 0.0) == 0;
        assert AutoSpawnerModule.computePassEtaSeconds(10, Double.NaN) == 0;
        assert AutoSpawnerModule.computePassEtaSeconds(10, Double.POSITIVE_INFINITY) == 0;
        assert AutoSpawnerModule.computePassEtaSeconds(10, 2.5) == 25;
        assert AutoSpawnerModule.computePassEtaSeconds(3, 1.4) == 4;

        // Chest fill color thresholds
        assert AutoSpawnerModule.computeChestFillColor(0, 0) == 0xFF4ADE80;
        assert AutoSpawnerModule.computeChestFillColor(0, 6) == 0xFF4ADE80;
        assert AutoSpawnerModule.computeChestFillColor(2, 6) == 0xFFFBBF24;
        assert AutoSpawnerModule.computeChestFillColor(6, 6) == 0xFFEF4444;
        assert AutoSpawnerModule.computeChestFillColor(8, 6) == 0xFFEF4444;

        // Detailed (7 rows + 2 progress bars) vs Compact (4 rows + 1 progress bar)
        ModConfig cfg = new ModConfig();
        cfg.hudUnifiedGlass = true;
        cfg.autoSpawnerHudDetailed = true;
        int[] detailedBounds = AutoSpawnerModule.resolveHudBounds(null, 854, 480, cfg);
        cfg.autoSpawnerHudDetailed = false;
        int[] compactBounds = AutoSpawnerModule.resolveHudBounds(null, 854, 480, cfg);

        // Detailed adds 3 rows (Rate, ETA, Chests) + 1 Chests progress bar -> 3*13 + 5 = 44px taller
        assert compactBounds[3] == 16 + 4 * 13 + 5 + 5 : "Expected compact height 78, got " + compactBounds[3];
        assert detailedBounds[3] == 16 + 7 * 13 + 2 * 5 + 5 : "Expected detailed height 122, got " + detailedBounds[3];
        assert detailedBounds[3] - compactBounds[3] == 44;

        // Instance zero-alloc getters & idle safety checks
        AutoSpawnerModule mod = AutoSpawnerModule.getInstance();
        assert mod.getOrderedSpawners() == mod.getOrderedSpawners();
        assert mod.getOpenedSpawners() == mod.getOpenedSpawners();
        assert mod.getScannedChests() == mod.getScannedChests();
        assert mod.getFullSpawnerChests() == mod.getFullSpawnerChests();
        assert mod.getChestDepositCounts() == mod.getChestDepositCounts();
        assert mod.getBlocksPerHour() == 0.0 && mod.getIngotsPerHour() == 0.0;
        assert mod.getPassEtaSeconds() == 0;
        assert mod.getFullChestCount() == 0 && mod.getScannedChestCount() == 0;
        assert !mod.isSafetyPaused();
        assert !mod.isActiveLockingMouse();
        assert !mod.isControllingMovement();
        assert "Scanning room".equals(mod.getStatusLabelEnglish());

        // Zero-alloc HUD row caching (preview & active)
        try {
            Method buildHudRows = AutoSpawnerModule.class.getDeclaredMethod(
                    "buildHudRows", AutoSpawnerModule.class, ModConfig.class);
            buildHudRows.setAccessible(true);

            ModConfig cacheCfg = new ModConfig();
            cacheCfg.autoSpawnerHudDetailed = true;
            Object p1 = buildHudRows.invoke(null, null, cacheCfg);
            Object p2 = buildHudRows.invoke(null, null, cacheCfg);
            assert p1 == p2 : "Preview HUD cache should reuse exact List instance across frames";
            assert ((List<?>) p1).size() == 7 : "Expected 7 detailed preview rows";

            cacheCfg.autoSpawnerHudDetailed = false;
            Object p3 = buildHudRows.invoke(null, null, cacheCfg);
            Object p4 = buildHudRows.invoke(null, null, cacheCfg);
            assert p3 != p1 && p3 == p4 : "Compact preview HUD should invalidate once and then reuse cached List";
            assert ((List<?>) p3).size() == 4 : "Expected 4 compact preview rows";

            // Align away from 1-second boundary so wallSecond/elapsedSec match on consecutive calls
            while (System.currentTimeMillis() % 1000L > 750L) {
                Thread.yield();
            }
            cacheCfg.autoSpawnerHudDetailed = true;
            mod.onEnable();
            try {
                Object a1 = buildHudRows.invoke(null, mod, cacheCfg);
                Object a2 = buildHudRows.invoke(null, mod, cacheCfg);
                assert a1 == a2 : "Active HUD cache should reuse exact List instance within same second";
                assert ((List<?>) a1).size() == 7 : "Expected 7 detailed active rows";

                cacheCfg.autoSpawnerHudDetailed = false;
                Object a3 = buildHudRows.invoke(null, mod, cacheCfg);
                Object a4 = buildHudRows.invoke(null, mod, cacheCfg);
                assert a3 != a1 && a3 == a4 : "Active HUD cache should rebuild on config change then reuse";
                assert ((List<?>) a3).size() == 4 : "Expected 4 compact active rows";
            } finally {
                mod.onDisable();
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to inspect AutoSpawnerModule.buildHudRows cache", e);
        }
    }

    private static void testTacticalEspAndGizmoRendering() {
        // Detail level clamping [0..2]
        ModConfig cfg = new ModConfig();
        cfg.autoSpawnerEspDetail = -4;
        cfg.sanitize();
        assert cfg.autoSpawnerEspDetail == 2;
        cfg.autoSpawnerEspDetail = 1;
        cfg.sanitize();
        assert cfg.autoSpawnerEspDetail == 1;
        cfg.autoSpawnerEspDetail = 99;
        cfg.sanitize();
        assert cfg.autoSpawnerEspDetail == 2;

        // Pre-allocated safety pulse styles (non-null across steps)
        GizmoStyle pulseStyle = WorldRenderToolsHandler.getSafetyPulseStyle();
        assert pulseStyle != null : "Safety pulse style must not be null";

        // Enclosure validation guards
        assert !WorldRenderToolsHandler.isValidEnclosure(null);
        assert !WorldRenderToolsHandler.isValidEnclosure(
                new SpawnerRoomScanner.SpawnerEnclosure(BlockPos.ZERO, 64, 10, 5, 0, 5, 64, 66, Set.of()));
        assert !WorldRenderToolsHandler.isValidEnclosure(
                new SpawnerRoomScanner.SpawnerEnclosure(BlockPos.ZERO, 64, 0, 300, 0, 10, 64, 66, Set.of()));
        SpawnerRoomScanner.SpawnerEnclosure validEnc = new SpawnerRoomScanner.SpawnerEnclosure(
                BlockPos.ZERO, 64, 0, 8, 0, 8, 64, 66, Set.of()
        );
        assert WorldRenderToolsHandler.isValidEnclosure(validEnc);

        // Chest heatmap style differentiation
        BlockPos c0 = new BlockPos(1, 64, 1);
        BlockPos c1 = new BlockPos(2, 64, 1);
        BlockPos c3 = new BlockPos(3, 64, 1);
        BlockPos cFull = new BlockPos(4, 64, 1);
        Map<BlockPos, Integer> counts = Map.of(c1, 2, c3, 5, cFull, 1);
        Set<BlockPos> fullSet = Set.of(cFull);
        GizmoStyle styleEmpty = WorldRenderToolsHandler.resolveChestHeatStyle(c0, fullSet, counts);
        GizmoStyle styleLight = WorldRenderToolsHandler.resolveChestHeatStyle(c1, fullSet, counts);
        GizmoStyle styleHeavy = WorldRenderToolsHandler.resolveChestHeatStyle(c3, fullSet, counts);
        GizmoStyle styleFull = WorldRenderToolsHandler.resolveChestHeatStyle(cFull, fullSet, counts);
        assert styleEmpty != styleLight && styleLight != styleHeavy && styleHeavy != styleFull
                : "Expected distinct heatmap styles for empty/light/heavy/full chests";

        // Gizmos.withCollector live verification
        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        try (var ignored = Gizmos.withCollector(collector)) {
            List<BlockPos> spawners = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                spawners.add(new BlockPos(i, 64, 0));
            }
            Set<BlockPos> opened = Set.of(spawners.get(0), spawners.get(1));
            BlockPos activeSpawner = spawners.get(2);

            boolean handled = WorldRenderToolsHandler.renderAutoSpawnerTargets(spawners, opened, activeSpawner, pulseStyle);
            assert handled : "Active target inside orderedSpawners must be reported as handled";
            assert collector.getGizmos().size() == 15 : "Expected 15 spawner cuboids, got " + collector.getGizmos().size();

            boolean missingTargetHandled = WorldRenderToolsHandler.renderAutoSpawnerTargets(
                    spawners, opened, new BlockPos(99, 64, 99), pulseStyle);
            assert !missingTargetHandled : "Target outside orderedSpawners must return false";

            // Snake preview caps at MAX_SNAKE_PREVIEW_NODES = 10 pending spawners -> max 9 lines
            int beforeSnake = collector.getGizmos().size();
            WorldRenderToolsHandler.renderAutoSpawnerSnakePreview(spawners, opened, 0);
            int snakeLines = collector.getGizmos().size() - beforeSnake;
            assert snakeLines == 9 : "Expected 9 preview segments for 10 pending nodes, got " + snakeLines;

            // Chest heatmap deduplicates fullChests already in scannedChests & skips fallback iterator when all matched
            int beforeChests = collector.getGizmos().size();
            boolean chestHandled = WorldRenderToolsHandler.renderAutoSpawnerChestHeatmap(
                    List.of(c0, c1, cFull), Set.of(cFull, c3), counts, c1, pulseStyle
            );
            assert chestHandled : "Active chest target must be reported as handled";
            int chestCuboids = collector.getGizmos().size() - beforeChests;
            assert chestCuboids == 4 : "Expected 4 unique chest cuboids (c0, c1, cFull, c3), got " + chestCuboids;

            final int[] iteratorCalls = {0};
            Set<BlockPos> trackedFullSet = new AbstractSet<>() {
                private final Set<BlockPos> delegate = Set.of(cFull);
                @Override public boolean contains(Object o) { return delegate.contains(o); }
                @Override public int size() { return delegate.size(); }
                @Override public Iterator<BlockPos> iterator() {
                    iteratorCalls[0]++;
                    return delegate.iterator();
                }
            };
            WorldRenderToolsHandler.renderAutoSpawnerChestHeatmap(
                    List.of(c0, c1, cFull), trackedFullSet, counts, null, pulseStyle
            );
            assert iteratorCalls[0] == 0 : "Fallback fullChests iterator must be skipped when all full chests are in scannedChests";

            // Enclosure perimeter emits 4 floor lines
            int beforePerim = collector.getGizmos().size();
            WorldRenderToolsHandler.renderEnclosurePerimeter(validEnc);
            int perimLines = collector.getGizmos().size() - beforePerim;
            assert perimLines == 4 : "Expected 4 enclosure perimeter lines, got " + perimLines;
        }
    }

    private static void testPenaltyMapAndAntiStuckEscalation() {
        SpawnerNavHelper nav = new SpawnerNavHelper();
        nav.resetAll(4.25);

        // MutableBlockPos safety & clamping at 120.0
        BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos(10, 64, 10);
        nav.recordStuckTile(mut, 4.0);
        mut.set(99, 64, 99);
        assert nav.getActiveTilePenalties().containsKey(new BlockPos(10, 64, 10));
        assert !nav.getActiveTilePenalties().containsKey(new BlockPos(99, 64, 99));

        nav.recordStuckTile(null, 10.0);
        nav.recordStuckTile(new BlockPos(10, 64, 10), -5.0);
        nav.recordStuckTile(new BlockPos(10, 64, 10), Double.NaN);
        nav.recordStuckTile(new BlockPos(10, 64, 10), 200.0);
        assert Math.abs(nav.getActiveTilePenalties().get(new BlockPos(10, 64, 10)) - 120.0) < 1e-6;

        // Decay halves penalties & prunes < 0.25
        BlockPos decayPos = new BlockPos(5, 64, 5);
        nav.recordStuckTile(decayPos, 0.4);
        nav.decayTilePenalties(); // 0.4 * 0.5 = 0.2 < 0.25 -> removed; 120 * 0.5 = 60
        assert !nav.getActiveTilePenalties().containsKey(decayPos);
        assert Math.abs(nav.getActiveTilePenalties().get(new BlockPos(10, 64, 10)) - 60.0) < 1e-6;

        // 3-tier anti-stuck escalation
        nav.resetAll(4.25);
        BlockPos spawnerTarget = new BlockPos(20, 64, 20);
        BlockPos blockedNode = new BlockPos(19, 64, 20);
        nav.currentNavTarget = spawnerTarget;
        Vec3 curPos = new Vec3(18.5, 64.0, 20.5);

        // Tier 0 -> sidestep only, no penalty or altSpot/quarantine
        boolean abort0 = nav.triggerStuckEscalation(null, null, curPos, blockedNode, null);
        assert !abort0 && nav.getStuckTier() == 1 && nav.unstuckTicks == 10;
        assert !nav.consumeAltSpotRequested() && !nav.consumeQuarantineRequested();
        assert nav.getActiveTilePenalties().isEmpty();

        // Tier 1 -> penalty + altSpotRequested + sidestep
        boolean abort1 = nav.triggerStuckEscalation(null, null, curPos, blockedNode, null);
        assert !abort1 && nav.getStuckTier() == 2 && nav.unstuckTicks == 10;
        assert nav.consumeAltSpotRequested() && !nav.consumeAltSpotRequested();
        assert !nav.consumeQuarantineRequested();
        assert nav.getActiveTilePenalties().getOrDefault(blockedNode, 0.0) >= 8.0;

        // Tier 2 -> quarantine abort
        boolean abort2 = nav.triggerStuckEscalation(null, null, curPos, blockedNode, null);
        assert abort2 && nav.getStuckTier() == 3 && nav.unstuckTicks == 0;
        assert nav.consumeQuarantineRequested() && !nav.consumeQuarantineRequested();

        // Target switch resets stuck tier
        nav.currentNavTarget = new BlockPos(22, 64, 20);
        boolean abortNewTarget = nav.triggerStuckEscalation(null, null, curPos, blockedNode, null);
        assert !abortNewTarget && nav.getStuckTier() == 1;

        // Server setback handling
        nav.resetAll(4.25);
        nav.currentNavTarget = spawnerTarget;
        nav.activePath = List.of(new BlockPos(15, 64, 15), new BlockPos(16, 64, 15));
        nav.pathIdx = 0;
        nav.onServerSetback(new Vec3(14.5, 64.0, 15.5), new Vec3(12.5, 64.0, 15.5));
        assert nav.isSprintSuppressed();
        assert nav.activePath == null;
        assert nav.getActiveTilePenalties().containsKey(new BlockPos(14, 64, 15));
        assert nav.getActiveTilePenalties().containsKey(new BlockPos(12, 64, 15));
        assert nav.getActiveTilePenalties().containsKey(new BlockPos(15, 64, 15));

        // Reset clears penalties, tier, and sprint suppression
        nav.resetAll(4.25);
        assert nav.getActiveTilePenalties().isEmpty();
        assert nav.getStuckTier() == 0;
        assert !nav.isSprintSuppressed();

        // Null-safe pathfinder overload checks
        assert SpawnerPathfinder.findSpawnerRoomPath(null, BlockPos.ZERO, new BlockPos(2, 0, 0), null, Map.of()) == null;
    }

    private static void testSpawnerSafetyGuard() {
        // Move classification
        assert SpawnerSafetyGuard.classifyServerMove(0.0, 0.0, 0.0) == SpawnerSafetyGuard.MoveAnomaly.NONE;
        assert SpawnerSafetyGuard.classifyServerMove(0.3, 0.1, 0.2) == SpawnerSafetyGuard.MoveAnomaly.NONE;
        assert SpawnerSafetyGuard.classifyServerMove(0.5, 0.0, 0.0) == SpawnerSafetyGuard.MoveAnomaly.RUBBERBAND;
        assert SpawnerSafetyGuard.classifyServerMove(8.0, 2.0, 3.0) == SpawnerSafetyGuard.MoveAnomaly.RUBBERBAND;
        assert SpawnerSafetyGuard.classifyServerMove(12.1, 0.0, 0.0) == SpawnerSafetyGuard.MoveAnomaly.TELEPORT;
        assert SpawnerSafetyGuard.classifyServerMove(Double.NaN, 0.0, 0.0) == SpawnerSafetyGuard.MoveAnomaly.TELEPORT;
        assert SpawnerSafetyGuard.classifyServerMove(0.0, Double.POSITIVE_INFINITY, 0.0) == SpawnerSafetyGuard.MoveAnomaly.TELEPORT;

        // CSV whitelist matching
        assert SpawnerSafetyGuard.isWhitelistedPlayer("Steve", "Alex, Steve; Notch");
        assert SpawnerSafetyGuard.isWhitelistedPlayer("§aStEvE", "Alex, steve Notch");
        assert !SpawnerSafetyGuard.isWhitelistedPlayer("Herobrine", "Alex, Steve; Notch");
        assert !SpawnerSafetyGuard.isWhitelistedPlayer(null, "Steve");
        assert !SpawnerSafetyGuard.isWhitelistedPlayer("Steve", "   ");

        // Turkish & English staff / macro / captcha keywords
        assert SpawnerSafetyGuard.isSuspiciousStaffOrCheckText("§c[Yetkili] Kontrol!");
        assert SpawnerSafetyGuard.isSuspiciousStaffOrCheckText("Lütfen DOĞRULAMA yapınız");
        assert SpawnerSafetyGuard.isSuspiciousStaffOrCheckText("CAPTCHA kodunu yazın");
        assert SpawnerSafetyGuard.isSuspiciousStaffOrCheckText("Makro kontrolü aktif");
        assert SpawnerSafetyGuard.isSuspiciousStaffOrCheckText("Burada mısın? Cevap ver!");
        assert SpawnerSafetyGuard.isSuspiciousStaffOrCheckText("AFK Check triggered by Admin");
        assert !SpawnerSafetyGuard.isSuspiciousStaffOrCheckText("Collected 64 iron ingots");
        assert !SpawnerSafetyGuard.isSuspiciousStaffOrCheckText("");

        // Evaluate consumes pending text alert & respects safety toggle
        SpawnerSafetyGuard guard = new SpawnerSafetyGuard();
        ModConfig cfg = new ModConfig();
        cfg.autoSpawnerSafetyEnabled = true;

        SpawnerSafetyGuard.clearPendingTextAlert();
        SpawnerSafetyGuard.onServerTextMessage("§e[Admin] Makro kontrol: cevap ver");
        SpawnerSafetyGuard.SafetyAlert alert = guard.evaluate(null, cfg, -1.0f);
        assert alert.isTriggered() && alert.kind() == SpawnerSafetyGuard.TriggerKind.CHAT_OR_TITLE && alert.critical();
        assert !guard.evaluate(null, cfg, -1.0f).isTriggered() : "Pending text alert must be consumed after evaluate";

        SpawnerSafetyGuard.onServerTextMessage("Captcha doğrulama");
        SpawnerSafetyGuard.clearPendingTextAlert();
        assert !guard.evaluate(null, cfg, -1.0f).isTriggered() : "clearPendingTextAlert must clear pending alert";

        cfg.autoSpawnerSafetyEnabled = false;
        SpawnerSafetyGuard.onServerTextMessage("AFK check");
        assert !guard.evaluate(null, cfg, -1.0f).isTriggered() : "Disabled safety must return NONE";

        // Clear cooldown timer progression & resets
        guard.reset();
        assert !guard.updateClearCooldown(true, 15, 1_000L);
        assert !guard.updateClearCooldown(true, 15, 15_999L);
        assert guard.updateClearCooldown(true, 15, 16_000L);
        assert !guard.updateClearCooldown(false, 15, 17_000L) : "Unsafe tick must reset cooldown";
        assert !guard.updateClearCooldown(true, 15, 20_000L);
        assert !guard.updateClearCooldown(true, 15, 34_999L);
        assert guard.updateClearCooldown(true, 15, 35_000L);
        assert !guard.updateClearCooldown(true, 15, 10_000L) : "Backwards clock must restart cooldown safely";
    }

    private static void testTpsPingAdaptiveAndFreezeDetection() {
        // TPS/Ping delay multiplier
        assert Math.abs(StatsHudRenderer.computeTpsDelayMultiplier(20.0f, 50) - 1.0) < 1e-6;
        assert Math.abs(StatsHudRenderer.computeTpsDelayMultiplier(19.0f, 120) - 1.0) < 1e-6;
        assert Math.abs(StatsHudRenderer.computeTpsDelayMultiplier(Float.NaN, 500) - 1.0) < 1e-6;
        assert Math.abs(StatsHudRenderer.computeTpsDelayMultiplier(10.0f, 50) - 2.0) < 1e-6;
        assert Math.abs(StatsHudRenderer.computeTpsDelayMultiplier(20.0f, 500) - 2.0) < 1e-6;
        assert Math.abs(StatsHudRenderer.computeTpsDelayMultiplier(5.0f, 1200) - 3.0) < 1e-6 : "Multiplier must cap at 3.0";

        // Adaptive tick scaling
        assert StatsHudRenderer.scaleAdaptiveTicks(4, 10.0f, 0, false) == 4;
        assert StatsHudRenderer.scaleAdaptiveTicks(0, 10.0f, 0, true) == 0;
        assert StatsHudRenderer.scaleAdaptiveTicks(-1, 10.0f, 0, true) == -1;
        assert StatsHudRenderer.scaleAdaptiveTicks(4, 10.0f, 0, true) == 8;
        assert StatsHudRenderer.scaleAdaptiveTicks(3, 15.0f, 200, true) == 5;

        // Nav helper multiplier clamping
        SpawnerNavHelper nav = new SpawnerNavHelper();
        nav.setTpsDelayMultiplier(Double.NaN);
        assert Math.abs(nav.getTpsDelayMultiplier() - 1.0) < 1e-6;
        nav.setTpsDelayMultiplier(0.25);
        assert Math.abs(nav.getTpsDelayMultiplier() - 1.0) < 1e-6;
        nav.setTpsDelayMultiplier(7.5);
        assert Math.abs(nav.getTpsDelayMultiplier() - 3.0) < 1e-6;
        assert nav.scaleTpsTicks(10) == 30;

        // Freeze detection during initial join grace period
        StatsHudRenderer.onGameJoin();
        StatsHudRenderer.onTimePacket();
        assert StatsHudRenderer.getMsSinceLastTimePacket() == 0L : "Expected 0ms during 4s join grace period";
        assert !StatsHudRenderer.isServerFrozen(3000L);
    }

    private static void testLegitKinematicsAndNonSnapDisable() {
        // Gaussian sub-face inner 60% [0.2..0.8] bounds across all 6 faces
        BlockPos block = new BlockPos(10, 64, 20);
        Random rng = new Random(42L);
        assert SpawnerPathfinder.pickGaussianSubFacePoint(null, Direction.UP, rng).equals(Vec3.ZERO);
        for (Direction face : Direction.values()) {
            for (int i = 0; i < 200; i++) {
                Vec3 pt = SpawnerPathfinder.pickGaussianSubFacePoint(block, face, rng);
                double lx = pt.x - block.getX();
                double ly = pt.y - block.getY();
                double lz = pt.z - block.getZ();
                switch (face) {
                    case UP -> {
                        assert Math.abs(ly - 1.0) < 1e-9;
                        assert lx >= 0.2 - 1e-9 && lx <= 0.8 + 1e-9 && lz >= 0.2 - 1e-9 && lz <= 0.8 + 1e-9;
                    }
                    case DOWN -> {
                        assert Math.abs(ly - 0.0) < 1e-9;
                        assert lx >= 0.2 - 1e-9 && lx <= 0.8 + 1e-9 && lz >= 0.2 - 1e-9 && lz <= 0.8 + 1e-9;
                    }
                    case EAST -> {
                        assert Math.abs(lx - 1.0) < 1e-9;
                        assert ly >= 0.2 - 1e-9 && ly <= 0.8 + 1e-9 && lz >= 0.2 - 1e-9 && lz <= 0.8 + 1e-9;
                    }
                    case WEST -> {
                        assert Math.abs(lx - 0.0) < 1e-9;
                        assert ly >= 0.2 - 1e-9 && ly <= 0.8 + 1e-9 && lz >= 0.2 - 1e-9 && lz <= 0.8 + 1e-9;
                    }
                    case SOUTH -> {
                        assert Math.abs(lz - 1.0) < 1e-9;
                        assert lx >= 0.2 - 1e-9 && lx <= 0.8 + 1e-9 && ly >= 0.2 - 1e-9 && ly <= 0.8 + 1e-9;
                    }
                    case NORTH -> {
                        assert Math.abs(lz - 0.0) < 1e-9;
                        assert lx >= 0.2 - 1e-9 && lx <= 0.8 + 1e-9 && ly >= 0.2 - 1e-9 && ly <= 0.8 + 1e-9;
                    }
                }
            }
        }

        // Look-ahead target steering
        SpawnerNavHelper nav = new SpawnerNavHelper();
        Vec3 fallback = new Vec3(99.0, 64.0, 99.0);
        assert nav.getLookAheadTarget(Vec3.ZERO, fallback).equals(fallback);

        nav.activePath = List.of(
                new BlockPos(1, 64, 0),
                new BlockPos(2, 64, 0),
                new BlockPos(3, 64, 0),
                new BlockPos(4, 64, 0),
                new BlockPos(10, 64, 0)
        );
        nav.pathIdx = 0;
        Vec3 look = nav.getLookAheadTarget(new Vec3(0.5, 64.5, 0.5), fallback);
        assert look.x >= 2.5 && look.x <= 4.5 : "Expected look-ahead 2-3 nodes forward, got x=" + look.x;

        // Sharp corner detection & legit sprint burst
        nav.activePath = List.of(
                new BlockPos(1, 64, 0),
                new BlockPos(1, 64, 2) // 90-degree turn after (1, 64, 0)
        );
        nav.pathIdx = 0;
        assert nav.isApproachingSharpCorner(0.0, 0.5) : "Expected sharp 90-degree corner detection";
        assert !nav.isApproachingSharpCorner(-5.0, 0.5) : "Corner > 2.25m away should not trigger decel yet";

        nav.activePath = List.of(
                new BlockPos(1, 64, 0),
                new BlockPos(3, 64, 0) // straight path
        );
        nav.pathIdx = 0;
        assert !nav.isApproachingSharpCorner(0.0, 0.5) : "Straight path must not be flagged as sharp corner";

        nav.setLegitMode(true);
        nav.moveForward = true;
        nav.wantJump = false;
        assert !nav.computeWantSprint(0.0, 0.5, 10.0, 42.0f, 0.0, false) : "Heading err > 40 deg must suppress sprint";
        assert !nav.computeWantSprint(0.0, 0.5, 0.5, 0.0f, 0.0, false) : "Distance <= 0.6 must suppress sprint";

        for (int t = 0; t < 40; t++) {
            assert nav.computeWantSprint(0.0, 0.5, 2.0 + (t % 8), (t % 35), 0.0, false)
                    : "Continuous full sprint must remain active while moving forward within heading tolerance";
        }

        // Non-snap onDisable safety & rubberband setback path
        AutoSpawnerModule mod = AutoSpawnerModule.getInstance();
        ModConfig.getInstance().autoSpawnerLegitMode = true;
        mod.onEnable();
        assert mod.isEnabled();
        mod.onServerPositionSync(new Vec3(10.0, 64.0, 10.0), new Vec3(11.5, 64.0, 10.0));
        mod.onDisable();
        assert !mod.isEnabled() && !mod.isSafetyPaused();
        assert !mod.wantsMoveForward() && !mod.wantsSprint();

        // Check real MC ClipContext raycast helper rejects null safely
        assert !SpawnerPathfinder.hasClearClipRaycast(null, Vec3.ZERO, new Vec3(1, 1, 1), BlockPos.ZERO);
    }

    private static void testReachBatchingAndWeightedChestRouting() {
        BlockPos standPos = new BlockPos(0, 64, 0); // eye = (0.5, 65.62, 0.5)
        List<BlockPos> ordered = List.of(
                new BlockPos(1, 64, 0),   // already opened -> skipped
                new BlockPos(2, 65, 0),   // dist ~ 1.62 -> included
                new BlockPos(2, 65, 1),   // dist ~ 1.90 -> included
                new BlockPos(3, 65, 0),   // dist ~ 2.58 -> included
                new BlockPos(10, 65, 0),  // dist > 4.25 -> excluded
                new BlockPos(1, 65, -2)   // dist ~ 2.32 -> included
        );
        Set<BlockPos> opened = Set.of(ordered.get(0));

        List<BlockPos> batch = SpawnerPathfinder.collectReachableBatch(standPos, ordered, 0, opened, 4.25, 4);
        assert batch.size() == 4 : "Expected 4 reachable unopened spawners in batch, got " + batch.size();
        assert !batch.contains(ordered.get(0)) : "Opened spawner must be excluded";
        assert !batch.contains(ordered.get(4)) : "Out-of-reach spawner must be excluded";
        assert batch.get(0).equals(ordered.get(1));
        assert batch.get(3).equals(ordered.get(5));

        assert SpawnerPathfinder.collectReachableBatch(null, ordered, 0, opened, 4.25, 4).isEmpty();
        assert SpawnerPathfinder.collectReachableBatch(standPos, ordered, 99, opened, 4.25, 4).isEmpty();
        assert SpawnerPathfinder.collectReachableBatch(standPos, ordered, 0, opened, 0.0, 4).isEmpty();

        // Weighted chest scoring: (depositCount * 6.0) + horizDist
        double emptyNear = SpawnerPathfinder.scoreChestCandidate(0, 3.0); // 3.0
        double emptyFar = SpawnerPathfinder.scoreChestCandidate(0, 7.5);  // 7.5
        double usedNear = SpawnerPathfinder.scoreChestCandidate(1, 3.0);  // 9.0
        assert Math.abs(emptyNear - 3.0) < 1e-6;
        assert Math.abs(usedNear - 9.0) < 1e-6;
        assert emptyNear < emptyFar && emptyFar < usedNear
                : "Empty far chest (7.5) should beat already-used near chest (9.0)";
    }

    private static void testChatPauseAndResume() {
        assert !AutoSpawnerModule.isChatScreen(null) : "null screen must not be treated as ChatScreen";

        AutoSpawnerModule mod = AutoSpawnerModule.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        cfg.autoSpawnerHudDetailed = true;
        mod.onEnable();
        try {
            Field stateField = AutoSpawnerModule.class.getDeclaredField("state");
            Field spawnerIdxField = AutoSpawnerModule.class.getDeclaredField("spawnerIndex");
            Field navField = AutoSpawnerModule.class.getDeclaredField("nav");
            Field waitTicksField = AutoSpawnerModule.class.getDeclaredField("waitTicks");
            stateField.setAccessible(true);
            spawnerIdxField.setAccessible(true);
            navField.setAccessible(true);
            waitTicksField.setAccessible(true);

            stateField.set(mod, AutoSpawnerModule.State.MOVE_TO_SPAWNER);
            spawnerIdxField.setInt(mod, 4);
            SpawnerNavHelper nav = (SpawnerNavHelper) navField.get(mod);
            nav.moveForward = true;
            nav.wantSprint = true;

            Method buildHudRows = AutoSpawnerModule.class.getDeclaredMethod(
                    "buildHudRows", AutoSpawnerModule.class, ModConfig.class);
            buildHudRows.setAccessible(true);
            Object beforeRows = buildHudRows.invoke(null, mod, cfg);

            boolean paused = mod.updateChatPauseState(true);
            assert paused : "updateChatPauseState(true) must return true and hold tick progression";
            assert mod.isChatPaused() : "chatPaused must be true while chat is open";
            assert !mod.isActiveLockingMouse() : "Mouse lock must release while chat is open";
            assert !mod.isControllingMovement() : "Movement override must release while chat is open";
            assert !mod.wantsMoveForward() && !mod.wantsSprint() : "Movement flags must clear on chat pause";
            assert "Paused (Chat)".equals(mod.getStatusLabelEnglish())
                    : "Expected 'Paused (Chat)' status, got " + mod.getStatusLabelEnglish();
            assert stateField.get(mod) == AutoSpawnerModule.State.MOVE_TO_SPAWNER
                    && mod.getSpawnerIndex() == 4
                    : "Chat pause must preserve exact mid-run state and spawner index";

            Object pausedRows = buildHudRows.invoke(null, mod, cfg);
            assert pausedRows != beforeRows : "HUD cache must invalidate when entering chat pause";

            boolean stillPaused = mod.updateChatPauseState(false);
            assert !stillPaused : "updateChatPauseState(false) must resume";
            assert !mod.isChatPaused() : "chatPaused must clear when chat closes";
            assert stateField.get(mod) == AutoSpawnerModule.State.MOVE_TO_SPAWNER
                    && mod.getSpawnerIndex() == 4
                    : "Closing chat must resume from exact saved state and spawner index";
            assert waitTicksField.getInt(mod) >= 2 : "Closing chat should apply a brief settle delay";
            assert "Moving to spawner".equals(mod.getStatusLabelEnglish())
                    : "Expected 'Moving to spawner' after resume, got " + mod.getStatusLabelEnglish();

            Object resumedRows = buildHudRows.invoke(null, mod, cfg);
            assert resumedRows != pausedRows : "HUD cache must invalidate when resuming from chat pause";

            assert mod.updateChatPauseState(true);
            assert mod.isChatPaused();
            mod.onDisable();
            assert !mod.isChatPaused() : "onDisable must clear chatPaused";
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to reflect AutoSpawnerModule chat pause state", e);
        } finally {
            mod.onDisable();
        }
    }

    private static void testStackedChestColumnDeduplication() {
        assert SpawnerRoomScanner.deduplicateChestColumns(null, null, null).isEmpty();
        assert SpawnerRoomScanner.deduplicateChestColumns(List.of(), Map.of(), Vec3.ZERO).isEmpty();

        Vec3 playerAt64 = new Vec3(0.5, 64.0, 5.5);

        // 1. Single-chest vertical stack -> collapses to 1 representative (topmost Y=67)
        List<BlockPos> singleStack = List.of(
                new BlockPos(10, 64, 5),
                new BlockPos(10, 65, 5),
                new BlockPos(10, 66, 5),
                new BlockPos(10, 67, 5)
        );
        List<BlockPos> singleOut = SpawnerRoomScanner.deduplicateChestColumns(singleStack, Map.of(), playerAt64);
        assert singleOut.size() == 1 : "Vertical chest stack should collapse to 1, got " + singleOut.size();
        assert singleOut.get(0).equals(new BlockPos(10, 67, 5))
                : "Expected topmost chest Y=67, got " + singleOut.get(0);

        // 2. Hopper-gapped stack (Y=64 + Y=66) -> collapses to Y=66
        List<BlockPos> gappedStack = List.of(
                new BlockPos(10, 64, 5),
                new BlockPos(10, 66, 5)
        );
        List<BlockPos> gappedOut = SpawnerRoomScanner.deduplicateChestColumns(gappedStack, Map.of(), playerAt64);
        assert gappedOut.size() == 1 && gappedOut.get(0).equals(new BlockPos(10, 66, 5))
                : "Hopper-gapped stack should collapse to top Y=66, got " + gappedOut;

        // 3. Double-chest tower {(10,5),(11,5)} at Y=64..66 -> collapses to 1 chest at Y=66 closer half (x=10)
        List<BlockPos> doubleTower = List.of(
                new BlockPos(10, 64, 5), new BlockPos(11, 64, 5),
                new BlockPos(10, 65, 5), new BlockPos(11, 65, 5),
                new BlockPos(10, 66, 5), new BlockPos(11, 66, 5)
        );
        Map<BlockPos, BlockPos> doublePartners = new HashMap<>();
        for (int y = 64; y <= 66; y++) {
            BlockPos left = new BlockPos(10, y, 5);
            BlockPos right = new BlockPos(11, y, 5);
            doublePartners.put(left, right);
            doublePartners.put(right, left);
        }
        List<BlockPos> doubleOut = SpawnerRoomScanner.deduplicateChestColumns(doubleTower, doublePartners, playerAt64);
        assert doubleOut.size() == 1 : "Double-chest tower should collapse to 1, got " + doubleOut.size();
        assert doubleOut.get(0).equals(new BlockPos(10, 66, 5))
                : "Expected closer top half (10,66,5), got " + doubleOut.get(0);

        // 4. Adjacent separate single chests (no partner) stay 2 separate columns
        List<BlockPos> twoSingles = List.of(
                new BlockPos(10, 64, 5),
                new BlockPos(11, 64, 5)
        );
        List<BlockPos> twoSinglesOut = SpawnerRoomScanner.deduplicateChestColumns(twoSingles, Map.of(), playerAt64);
        assert twoSinglesOut.size() == 2 : "Adjacent separate single chests must remain 2";

        // 5. Two side-by-side double-chest towers stay 2 separate towers
        List<BlockPos> twoDoubleTowers = new ArrayList<>(doubleTower);
        Map<BlockPos, BlockPos> twoTowerPartners = new HashMap<>(doublePartners);
        for (int y = 64; y <= 66; y++) {
            BlockPos l2 = new BlockPos(12, y, 5);
            BlockPos r2 = new BlockPos(13, y, 5);
            twoDoubleTowers.add(l2);
            twoDoubleTowers.add(r2);
            twoTowerPartners.put(l2, r2);
            twoTowerPartners.put(r2, l2);
        }
        List<BlockPos> twoTowersOut = SpawnerRoomScanner.deduplicateChestColumns(
                twoDoubleTowers, twoTowerPartners, playerAt64);
        assert twoTowersOut.size() == 2 : "Two double-chest towers must remain 2 columns, got " + twoTowersOut.size();
        assert twoTowersOut.get(0).equals(new BlockPos(10, 66, 5));
        assert twoTowersOut.get(1).equals(new BlockPos(12, 66, 5));

        // 6. Tall stack Y=64..69 with player at Y=64 -> prefers Y=68 (<= 64 + 4) over Y=69
        List<BlockPos> tallStack = List.of(
                new BlockPos(10, 64, 5),
                new BlockPos(10, 68, 5),
                new BlockPos(10, 69, 5)
        );
        List<BlockPos> tallOut = SpawnerRoomScanner.deduplicateChestColumns(tallStack, Map.of(), playerAt64);
        assert tallOut.size() == 1 && tallOut.get(0).equals(new BlockPos(10, 68, 5))
                : "Expected reachable cap Y=68 over Y=69, got " + tallOut;

        // 7. Staircase 3-tier hopper system (floor chest at Y=294 offset 1 block horizontally at x=1,
        // middle chest at Y=296 x=0, top chest at Y=298 x=0) -> keeps ONLY top tier Y=298
        Vec3 playerAt295 = new Vec3(2.5, 295.0, 5.5);
        List<BlockPos> staircase = List.of(
                new BlockPos(1, 294, 5), // floor storage chest sticking out 1 block
                new BlockPos(0, 296, 5), // middle hopper chest
                new BlockPos(0, 298, 5)  // top input chest
        );
        List<BlockPos> stairOut = SpawnerRoomScanner.deduplicateChestColumns(staircase, Map.of(), playerAt295);
        assert stairOut.size() == 1 && stairOut.get(0).equals(new BlockPos(0, 298, 5))
                : "Staircase hopper system must keep ONLY top tier Y=298, got " + stairOut;
    }

    private static void testSequentialPerimeterChestCycling() {
        assert SpawnerRoomScanner.orderChestColumnsSequentially(null, null).isEmpty();
        assert AutoSpawnerModule.buildSequentialChestCandidates(List.of(), 0, Set.of()).isEmpty();

        // 1. Two opposite walls of 5 chests each (N = 10), starting near (0, 66, 0):
        // Wall 1 (x=0): 1..5 in direction z=0->8; Wall 2 (x=6): 6..10 in the SAME parallel direction z=0->8
        List<BlockPos> tenChestsShuffled = List.of(
                new BlockPos(6, 66, 4), new BlockPos(0, 66, 8), new BlockPos(0, 66, 2),
                new BlockPos(6, 66, 0), new BlockPos(0, 66, 6), new BlockPos(6, 66, 8),
                new BlockPos(0, 66, 0), new BlockPos(6, 66, 2), new BlockPos(0, 66, 4),
                new BlockPos(6, 66, 6)
        );
        List<BlockPos> ordered10 = SpawnerRoomScanner.orderChestColumnsSequentially(
                tenChestsShuffled, new BlockPos(1, 64, 0));
        List<BlockPos> expected10 = List.of(
                new BlockPos(0, 66, 0), new BlockPos(0, 66, 2), new BlockPos(0, 66, 4),
                new BlockPos(0, 66, 6), new BlockPos(0, 66, 8),
                new BlockPos(6, 66, 0), new BlockPos(6, 66, 2), new BlockPos(6, 66, 4),
                new BlockPos(6, 66, 6), new BlockPos(6, 66, 8)
        );
        assert ordered10.equals(expected10) : "Expected parallel wall order " + expected10 + ", got " + ordered10;

        // Spawners 1..10 hit Chests #1..#10, and spawner 11 wraps back to Chest #1
        for (int spawnerTurn = 0; spawnerTurn < 12; spawnerTurn++) {
            List<BlockPos> cands = AutoSpawnerModule.buildSequentialChestCandidates(ordered10, spawnerTurn, Set.of());
            assert cands.get(0).equals(expected10.get(spawnerTurn % 10))
                    : "Turn " + spawnerTurn + " expected chest " + expected10.get(spawnerTurn % 10)
                    + ", got " + cands.get(0);
        }

        // Skipping a full chest picks the very next non-full chest in sequence
        Set<BlockPos> full = Set.of(expected10.get(2)); // #3 full
        List<BlockPos> skipFull = AutoSpawnerModule.buildSequentialChestCandidates(ordered10, 2, full);
        assert skipFull.get(0).equals(expected10.get(3)) : "Should skip full #3 and pick #4";
        assert skipFull.get(skipFull.size() - 1).equals(expected10.get(2)) : "Full chest must move to end";

        // 2. Starting at the z=8 end orders both walls descending (z=8->0 on Wall 1, then z=8->0 on Wall 2)
        List<BlockPos> revOrdered = SpawnerRoomScanner.orderChestColumnsSequentially(
                tenChestsShuffled, new BlockPos(1, 64, 8));
        for (int i = 0; i < 5; i++) {
            assert revOrdered.get(i).equals(new BlockPos(0, 66, (4 - i) * 2))
                    : "Wall 1 from z=8 should descend at i=" + i + ", got " + revOrdered;
            assert revOrdered.get(5 + i).equals(new BlockPos(6, 66, (4 - i) * 2))
                    : "Wall 2 from z=8 should descend in parallel at i=" + i + ", got " + revOrdered;
        }

        // 3. Doorway gap on Wall A/B (z=0,2 and z=6,8 separated by 4 blocks on x=0 vs x=2) stays on Wall 1 first
        List<BlockPos> doorwayWalls = List.of(
                new BlockPos(2, 66, 6), new BlockPos(0, 66, 2), new BlockPos(2, 66, 0), new BlockPos(0, 66, 8),
                new BlockPos(0, 66, 0), new BlockPos(2, 66, 8), new BlockPos(0, 66, 6), new BlockPos(2, 66, 2)
        );
        List<BlockPos> doorwayOrdered = SpawnerRoomScanner.orderChestColumnsSequentially(
                doorwayWalls, new BlockPos(0, 64, 0));
        List<BlockPos> expectedDoorway = List.of(
                new BlockPos(0, 66, 0), new BlockPos(0, 66, 2), new BlockPos(0, 66, 6), new BlockPos(0, 66, 8),
                new BlockPos(2, 66, 0), new BlockPos(2, 66, 2), new BlockPos(2, 66, 6), new BlockPos(2, 66, 8)
        );
        assert doorwayOrdered.equals(expectedDoorway)
                : "Doorway gap must stay on x=0 wall first, expected " + expectedDoorway + ", got " + doorwayOrdered;

        // 4. Dynamic count adaptation: N=4 chests wraps on turn 4 (5th spawner -> 1st chest)
        List<BlockPos> fourChests = SpawnerRoomScanner.orderChestColumnsSequentially(List.of(
                new BlockPos(4, 65, 2), new BlockPos(0, 65, 0),
                new BlockPos(4, 65, 0), new BlockPos(0, 65, 2)
        ), new BlockPos(0, 64, 0));
        assert fourChests.size() == 4;
        assert AutoSpawnerModule.buildSequentialChestCandidates(fourChests, 4, Set.of()).get(0)
                .equals(fourChests.get(0)) : "With 4 horizontal chests, 5th deposit must wrap to 1st chest";

        // 5. Single-wall linear room orders strictly from anchor end to far end
        List<BlockPos> singleWall = SpawnerRoomScanner.orderChestColumnsSequentially(List.of(
                new BlockPos(0, 65, 6), new BlockPos(0, 65, 2), new BlockPos(0, 65, 8),
                new BlockPos(0, 65, 0), new BlockPos(0, 65, 4)
        ), new BlockPos(0, 64, 8));
        assert singleWall.equals(List.of(
                new BlockPos(0, 65, 8), new BlockPos(0, 65, 6), new BlockPos(0, 65, 4),
                new BlockPos(0, 65, 2), new BlockPos(0, 65, 0)
        )) : "Single wall from z=8 should descend linearly: " + singleWall;

        // 6. Burst click scaling by clickDelayTicks (54 = full inventory in 1 tick at default delay)
        AutoSpawnerModule mod = AutoSpawnerModule.getInstance();
        mod.clickDelayTicks = 2;
        assert mod.resolveBurstClicksPerStep() == 54 : "Delay 2 should sweep all 54 slots in 1 tick";
        assert mod.resolveBurstStepWaitTicks() == 0 : "Delay 2 should have 0 step wait";
        mod.clickDelayTicks = 3;
        assert mod.resolveBurstClicksPerStep() == 54 : "Delay 3 should sweep all 54 slots in 1 tick";
        assert mod.resolveBurstStepWaitTicks() == 0 : "Delay 3 should have 0 step wait";
        mod.clickDelayTicks = 5;
        assert mod.resolveBurstClicksPerStep() == 12 : "Delay 5 should burst 12 clicks/step";
        mod.clickDelayTicks = 7;
        assert mod.resolveBurstClicksPerStep() == 4 : "Delay 7 should burst 4 clicks/step";
        mod.clickDelayTicks = 3;
    }
}
