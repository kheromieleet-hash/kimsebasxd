package com.client.mod;

/**
 * Verifies magnetic HUD snap guide math, Shift snap bypass,
 * one-click Farm/Minimal/PvP layout presets, and custom folder renaming.
 */
public class HudLayoutPresetsTest {

    public static void main(String[] args) {
        assertCenterAndEdgeSnapping();
        assertSnapBypassAndClamping();
        assertHudPresetsApplyCleanly();
        assertFolderRenameAndClamp();
        System.out.println("ALL HudLayoutPresetsTest ASSERTIONS PASSED!");
    }

    private static void assertCenterAndEdgeSnapping() {
        int screenW = 960;
        int screenH = 540;
        int w = 100;
        int h = 40;

        // Dragging box center near 480x270 should lock onto center guides
        HudLayoutPresets.SnapResult centerSnap = HudLayoutPresets.snapBox(432, 248, w, h, screenW, screenH, true);
        assert centerSnap.x() == 430 && centerSnap.guideX() != null && centerSnap.guideX() == 480
                : "Expected center X snap to 480, got x=" + centerSnap.x() + " guideX=" + centerSnap.guideX();
        assert centerSnap.y() == 250 && centerSnap.guideY() != null && centerSnap.guideY() == 270
                : "Expected center Y snap to 270, got y=" + centerSnap.y() + " guideY=" + centerSnap.guideY();

        // Dragging near top-left 4px safe margin
        HudLayoutPresets.SnapResult leftTopSnap = HudLayoutPresets.snapBox(6, 7, w, h, screenW, screenH, true);
        assert leftTopSnap.x() == 4 && leftTopSnap.guideX() != null && leftTopSnap.guideX() == 4;
        assert leftTopSnap.y() == 4 && leftTopSnap.guideY() != null && leftTopSnap.guideY() == 4;

        // Dragging right edge near screenW - 4 (956 -> x = 856)
        HudLayoutPresets.SnapResult rightEdgeSnap = HudLayoutPresets.snapBox(853, 100, w, h, screenW, screenH, true);
        assert rightEdgeSnap.x() == 856 && rightEdgeSnap.guideX() != null && rightEdgeSnap.guideX() == 956;
        assert rightEdgeSnap.guideY() == null : "Y=100 should stay free of guides";
    }

    private static void assertSnapBypassAndClamping() {
        HudLayoutPresets.SnapResult bypass = HudLayoutPresets.snapBox(432, 248, 100, 40, 960, 540, false);
        assert bypass.x() == 432 && bypass.y() == 248;
        assert bypass.guideX() == null && bypass.guideY() == null;

        HudLayoutPresets.SnapResult clamped = HudLayoutPresets.snapBox(-20, 2000, 100, 40, 960, 540, true);
        assert clamped.x() >= 2 && clamped.y() <= 540 - 40 - 2;
    }

    private static void assertHudPresetsApplyCleanly() {
        ModConfig cfg = new ModConfig();

        HudLayoutPresets.applyPresetState(HudLayoutPresets.Preset.FARM, cfg, 960, 540);
        assert cfg.hudWatermarkEnabled && cfg.hudTelemetryEnabled && cfg.hudPositionEnabled && cfg.itemNotifierEnabled;
        assert !cfg.hudArrayListEnabled && !cfg.keybindsHudEnabled && !cfg.playerListEnabled;
        assert cfg.autoSpawnerHudX == 960 - 156 && cfg.spawnerAutoSellHudX == 960 - 156;

        HudLayoutPresets.applyPresetState(HudLayoutPresets.Preset.MINIMAL, cfg, 960, 540);
        assert cfg.hudWatermarkEnabled && cfg.hudTelemetryEnabled && cfg.hudShowClock;
        assert !cfg.hudPositionEnabled && !cfg.keybindsHudEnabled && !cfg.itemNotifierEnabled;

        HudLayoutPresets.applyPresetState(HudLayoutPresets.Preset.PVP, cfg, 960, 540);
        assert cfg.hudWatermarkEnabled && cfg.hudArrayListEnabled && cfg.keybindsHudEnabled && cfg.playerListEnabled && cfg.inventoryHudEnabled;
    }

    private static void assertFolderRenameAndClamp() {
        ModConfig cfg = new ModConfig();
        ModConfig.CustomModuleFolder folder = new ModConfig.CustomModuleFolder();
        folder.id = "FOLDER_1";
        folder.name = "temp";
        cfg.customFolders.add(folder);
        assert !cfg.renameCustomFolderInMemory(folder.id, "   ") : "Blank rename must fail";
        assert cfg.renameCustomFolderInMemory(folder.id, "  MinerTown Pro Farm Extras  ");
        assert folder.name.length() == 20 && folder.name.startsWith("MinerTown Pro");
    }
}
