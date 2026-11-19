package com.client.mod;

/**
 * Pure-Java HUD snap guide calculator and one-click layout preset applier.
 * Keeps magnetic drag alignment math and preset coordinates testable without Minecraft UI state.
 */
public final class HudLayoutPresets {

    public enum Preset {
        FARM("Farm"),
        MINIMAL("Minimal"),
        PVP("PvP");

        public final String label;

        Preset(String label) {
            this.label = label;
        }
    }

    public record SnapResult(int x, int y, Integer guideX, Integer guideY) {
    }

    public static final int SNAP_THRESHOLD = 5;

    private HudLayoutPresets() {
    }

    public static SnapResult snapBox(
            int rawX,
            int rawY,
            int w,
            int h,
            int screenW,
            int screenH,
            boolean snapEnabled
    ) {
        int clampedX = clamp(rawX, 2, Math.max(2, screenW - w - 2));
        int clampedY = clamp(rawY, 2, Math.max(2, screenH - h - 2));
        if (!snapEnabled) {
            return new SnapResult(clampedX, clampedY, null, null);
        }

        int[] xGuides = {
                4,
                screenW / 3,
                screenW / 2,
                (screenW * 2) / 3,
                screenW - 4
        };
        int[] yGuides = {
                4,
                screenH / 3,
                screenH / 2,
                (screenH * 2) / 3,
                screenH - 4
        };

        SnapAxis bestX = findBestAxisSnap(clampedX, w, xGuides, 2, Math.max(2, screenW - w - 2));
        SnapAxis bestY = findBestAxisSnap(clampedY, h, yGuides, 2, Math.max(2, screenH - h - 2));
        return new SnapResult(bestX.pos, bestY.pos, bestX.guide, bestY.guide);
    }

    private record SnapAxis(int pos, Integer guide) {
    }

    private static SnapAxis findBestAxisSnap(int pos, int size, int[] guides, int minPos, int maxPos) {
        int bestPos = pos;
        Integer bestGuide = null;
        int bestDist = SNAP_THRESHOLD + 1;

        for (int g : guides) {
            int[] anchors = {
                    g,                  // start edge aligns with guide
                    g - size / 2,       // center aligns with guide
                    g - size            // end edge aligns with guide
            };
            int[] anchorEdges = {pos, pos + size / 2, pos + size};
            for (int i = 0; i < 3; i++) {
                int dist = Math.abs(anchorEdges[i] - g);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestPos = clamp(anchors[i], minPos, maxPos);
                    bestGuide = g;
                }
            }
        }
        return new SnapAxis(bestPos, bestGuide);
    }

    public static void applyPreset(Preset preset, ModConfig config, int screenW, int screenH) {
        if (preset == null || config == null) {
            return;
        }
        applyPresetState(preset, config, screenW, screenH);
        config.save();
    }

    static void applyPresetState(Preset preset, ModConfig config, int screenW, int screenH) {
        if (preset == null || config == null) {
            return;
        }
        int safeW = Math.max(320, screenW);
        switch (preset) {
            case FARM -> {
                config.hudWatermarkEnabled = true;
                config.hudWatermarkX = 4;
                config.hudWatermarkY = 4;
                config.hudArrayListEnabled = false;
                config.hudArrayListX = -1;
                config.hudArrayListY = -1;

                config.hudInfoEnabled = true;
                config.hudTelemetryEnabled = true;
                config.statsHudX = 4;
                config.statsHudY = 26;
                config.hudPositionEnabled = true;
                config.posHudX = 4;
                config.posHudY = 76;
                config.hudShowClock = false;
                config.clockHudX = -1;
                config.clockHudY = -1;

                config.keybindsHudEnabled = false;
                config.playerListEnabled = false;
                config.itemNotifierEnabled = true;
                config.itemNotifierX = -1;
                config.itemNotifierY = -1;

                config.autoSpawnerHudX = Math.max(4, safeW - 156);
                config.autoSpawnerHudY = 4;
                config.spawnerAutoSellHudX = Math.max(4, safeW - 156);
                config.spawnerAutoSellHudY = 82;
            }
            case MINIMAL -> {
                config.hudWatermarkEnabled = true;
                config.hudWatermarkX = 4;
                config.hudWatermarkY = 4;
                config.hudArrayListEnabled = false;
                config.hudArrayListX = -1;
                config.hudArrayListY = -1;

                config.hudInfoEnabled = true;
                config.hudTelemetryEnabled = true;
                config.statsHudX = 4;
                config.statsHudY = 26;
                config.hudPositionEnabled = false;
                config.hudShowClock = true;
                config.clockHudX = -1;
                config.clockHudY = -1;

                config.keybindsHudEnabled = false;
                config.playerListEnabled = false;
                config.itemNotifierEnabled = false;
                config.autoSpawnerHudX = -1;
                config.autoSpawnerHudY = -1;
                config.spawnerAutoSellHudX = -1;
                config.spawnerAutoSellHudY = -1;
            }
            case PVP -> {
                config.hudWatermarkEnabled = true;
                config.hudWatermarkX = 4;
                config.hudWatermarkY = 4;
                config.hudArrayListEnabled = true;
                config.hudArrayListX = -1;
                config.hudArrayListY = -1;

                config.hudInfoEnabled = true;
                config.hudTelemetryEnabled = true;
                config.statsHudX = 4;
                config.statsHudY = 26;
                config.hudPositionEnabled = true;
                config.posHudX = -1;
                config.posHudY = -1;
                config.hudShowClock = false;

                config.keybindsHudEnabled = true;
                config.keybindsHudX = 4;
                config.keybindsHudY = 76;
                config.playerListEnabled = true;
                config.playerListX = 4;
                config.playerListY = 158;
                config.inventoryHudEnabled = true;
                config.itemNotifierEnabled = true;
                config.itemNotifierX = -1;
                config.itemNotifierY = -1;
            }
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
