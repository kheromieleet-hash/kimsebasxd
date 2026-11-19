package com.client.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

        cfg.autoSpawnerEnabled = true;
        cfg.autoSpawnerCount = 500;
        cfg.autoSpawnerSearchRadius = 99;
        cfg.autoSpawnerClickDelay = 1;
        cfg.autoSpawnerLockMouse = false;
        cfg.autoSpawnerRenderEsp = false;
        cfg.autoSpawnerHudX = 9999;
        cfg.autoSpawnerHudY = -42;
        cfg.sanitize();

        // ModConfig.clampInt falls back to default when out of range
        assert cfg.autoSpawnerCount == 0 : "Expected autoSpawnerCount fallback 0, got " + cfg.autoSpawnerCount;
        assert cfg.autoSpawnerSearchRadius == 26 : "Expected autoSpawnerSearchRadius fallback 26, got " + cfg.autoSpawnerSearchRadius;
        assert cfg.autoSpawnerClickDelay == 3 : "Expected autoSpawnerClickDelay fallback 3, got " + cfg.autoSpawnerClickDelay;
        assert cfg.autoSpawnerHudX == -1 : "Expected autoSpawnerHudX fallback -1, got " + cfg.autoSpawnerHudX;
        assert cfg.autoSpawnerHudY == -1 : "Expected autoSpawnerHudY fallback -1, got " + cfg.autoSpawnerHudY;

        int[] defaultBounds = AutoSpawnerModule.resolveHudBounds(null, 854, 480, cfg);
        assert defaultBounds[0] == 8 && defaultBounds[1] == 128 : "Expected default HUD bounds at (8, 128)";
        assert defaultBounds[2] >= 138 && defaultBounds[3] > 60 : "Expected valid card dimensions";

        cfg.autoSpawnerCount = 64;
        cfg.autoSpawnerSearchRadius = 32;
        cfg.autoSpawnerClickDelay = 6;
        cfg.autoSpawnerHudX = 320;
        cfg.autoSpawnerHudY = 180;
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
    }

    private static void testModuleSettingsControls() {
        ModConfig cfg = new ModConfig();
        RecordingActions actions = new RecordingActions();
        assert ModernModuleSettings.hasSettings("Auto Spawner", cfg, actions);

        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("Auto Spawner", cfg, actions);
        assert controls.size() == 5 : "Expected 5 controls for Auto Spawner, got " + controls.size();
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

        assert actions.persistCalls == 2 : "Expected 2 persist calls from toggles, got " + actions.persistCalls;
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
        int[] smEditor = SpawnerAutoSellModule.resolveHudBounds(null, 854, 480, cfg, true);
        assert smEditor[0] == 8 && smEditor[1] == 206 : "Expected Spawner Mode editor default (8, 206)";
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
}
