package com.client.mod;

import net.minecraft.core.Direction;

/**
 * Pure-logic unit tests for Meteor-parity Xray & Block ESP helpers, caches, and config resolution.
 */
public final class XrayBlockEspLogicTest {

    public static void main(String[] args) {
        testXrayPresetsAndMatching();
        testXrayOpacityAndAlphaMath();
        testXrayExposedCheck();
        testBlockEspStyleResolution();
        testBlockEspChunkCacheLifecycle();
        testBlockEspRangeAndTracerMath();
        testModConfigDefaultsAndCopy();
        System.out.println("ALL XrayBlockEspLogicTest ASSERTIONS PASSED!");
    }

    private static void testXrayPresetsAndMatching() {
        assert XrayHandler.PRESET_ORES.contains("minecraft:diamond_ore") : "Diamond ore missing";
        assert XrayHandler.PRESET_ORES.contains("minecraft:deepslate_diamond_ore") : "Deepslate diamond missing";
        assert XrayHandler.PRESET_ORES.contains("minecraft:ancient_debris") : "Ancient debris missing";
        assert XrayHandler.PRESET_STORAGE.contains("minecraft:chest") : "Chest missing from storage preset";
        assert XrayHandler.PRESET_STORAGE.contains("minecraft:spawner") : "Spawner missing from storage preset";
        assert XrayHandler.PRESET_NETHER.contains("minecraft:nether_quartz_ore") : "Nether quartz missing";

        ModConfig cfg = new ModConfig();
        assert XrayHandler.isTargetBlockId("minecraft:diamond_ore", cfg) : "Default xray should include diamond ore";
        assert !XrayHandler.isTargetBlockId("minecraft:stone", cfg) : "Default xray should not include stone";
        assert !XrayHandler.isTargetBlockId(null, cfg) : "Null block id should return false";
    }

    private static void testXrayOpacityAndAlphaMath() {
        assert XrayHandler.clampOpacity(-10) == 0 : "Clamp lower bound";
        assert XrayHandler.clampOpacity(300) == 255 : "Clamp upper bound";
        assert XrayHandler.clampOpacity(128) == 128 : "Keep valid opacity";

        assert XrayHandler.shouldHideCompletely(false, 0) : "Non-target at opacity 0 should hide";
        assert !XrayHandler.shouldHideCompletely(true, 0) : "Target at opacity 0 must stay visible";
        assert !XrayHandler.shouldHideCompletely(false, 120) : "Non-target at opacity 120 should render translucent";

        assert XrayHandler.shouldRenderTranslucent(false, 128) : "Non-target opacity 128 should render translucent";
        assert !XrayHandler.shouldRenderTranslucent(false, 0) : "Opacity 0 is hidden, not translucent";
        assert !XrayHandler.shouldRenderTranslucent(false, 255) : "Opacity 255 is opaque, not translucent";
        assert !XrayHandler.shouldRenderTranslucent(true, 128) : "Target block must never be translucent";

        int argb = 0xFF336699;
        int multiplied = XrayHandler.multiplyAlphaARGB(argb, 128);
        assert (multiplied & 0x00FFFFFF) == 0x00336699 : "RGB channels must remain unchanged";
        int alpha = (multiplied >>> 24) & 0xFF;
        assert alpha == 128 : "Expected alpha 128, got " + alpha;

        int halfAlpha = XrayHandler.multiplyAlphaARGB(0x80336699, 128);
        int expectedHalf = (0x80 * 128) / 255;
        assert ((halfAlpha >>> 24) & 0xFF) == expectedHalf : "Should scale existing alpha proportionally";
    }

    private static void testXrayExposedCheck() {
        // Surrounded on all 6 faces by opaque blocks -> not exposed
        boolean exposedAllOpaque = XrayHandler.hasExposedFace(dir -> true);
        assert !exposedAllOpaque : "Block surrounded by 6 opaque faces must not be exposed";

        // Exposed on UP face only -> exposed
        boolean exposedTop = XrayHandler.hasExposedFace(dir -> dir != Direction.UP);
        assert exposedTop : "Block with non-opaque UP face must be exposed";
    }

    private static void testBlockEspStyleResolution() {
        ModConfig cfg = new ModConfig();
        cfg.blockEspDefaultStyle = new BlockEspHandler.BlockEspStyle("Both", 0xFF00D4FF, 0x3800D4FF, true, 0xFF00D4FF);

        BlockEspHandler.BlockEspStyle fallback = BlockEspHandler.resolveEffectiveStyle("minecraft:chest", cfg);
        assert "Both".equals(fallback.shapeMode) : "Should use default shape mode";
        assert fallback.tracer : "Should inherit tracer flag from default style";

        BlockEspHandler.BlockEspStyle custom = new BlockEspHandler.BlockEspStyle("Lines", 0xFFFF0000, 0x20FF0000, false, 0xFFFF0000);
        cfg.blockEspPerBlockStyles.put("minecraft:diamond_ore", custom);

        BlockEspHandler.BlockEspStyle resolved = BlockEspHandler.resolveEffectiveStyle("minecraft:diamond_ore", cfg);
        assert resolved.resolveShapeMode() == BlockEspHandler.ShapeMode.LINES : "Per-block override should win";
        assert resolved.lineColor == 0xFFFF0000 : "Per-block line color should win";
        assert !resolved.tracer : "Per-block tracer false should override default";
    }

    private static void testBlockEspChunkCacheLifecycle() {
        BlockEspHandler.clearCache();
        assert BlockEspHandler.getCachedChunkCount() == 0 : "Cache should start empty";

        BlockEspHandler.setCachedChunkForTest(1, 2, java.util.List.of(
                new BlockEspHandler.BlockTarget(20, 64, 35, "minecraft:chest")
        ));
        assert BlockEspHandler.getCachedChunkCount() == 1 : "Expected 1 cached chunk";

        BlockEspHandler.markChunkDirty(1, 2);
        assert BlockEspHandler.isChunkDirty(1, 2) : "Chunk (1,2) should be marked dirty";

        BlockEspHandler.removeChunkFromCache(1, 2);
        assert BlockEspHandler.getCachedChunkCount() == 0 : "Chunk (1,2) should be removed on unload";
        BlockEspHandler.clearCache();
    }

    private static void testBlockEspRangeAndTracerMath() {
        assert BlockEspHandler.isWithinRange(0.0, 64.0, 0.0, 63, 64, 0, 64) : "63 blocks away within 64 range";
        assert !BlockEspHandler.isWithinRange(0.0, 64.0, 0.0, 65, 64, 0, 64) : "65 blocks away exceeds 64 range";

        double[] start = BlockEspHandler.computeTracerStart(10.0, 65.0, -5.0, 0.0, 0.0, 1.0);
        assert Math.abs(start[0] - 10.0) < 1e-6 : "Tracer X origin mismatch";
        assert Math.abs(start[1] - 65.0) < 1e-6 : "Tracer Y origin mismatch";
        assert Math.abs(start[2] - (-4.75)) < 1e-6 : "Tracer Z should offset +0.25 along look direction";
    }

    private static void testModConfigDefaultsAndCopy() {
        ModConfig a = new ModConfig();
        a.xrayEnabled = true;
        a.xrayOpacity = 96;
        a.xrayExposedOnly = true;
        a.toggleXrayBlock("minecraft:ancient_debris"); // remove or toggle
        a.blockEspEnabled = true;
        a.blockEspRange = 80;
        a.blockEspPerBlockStyles.put("minecraft:spawner",
                new BlockEspHandler.BlockEspStyle("Sides", 0xFF22C55E, 0x4022C55E, true, 0xFF22C55E));

        ModConfig b = a.copy();
        assert b.xrayEnabled : "copyFrom failed for xrayEnabled";
        assert b.xrayOpacity == 96 : "copyFrom failed for xrayOpacity";
        assert b.xrayExposedOnly : "copyFrom failed for xrayExposedOnly";
        assert b.blockEspEnabled : "copyFrom failed for blockEspEnabled";
        assert b.blockEspRange == 80 : "copyFrom failed for blockEspRange";
        assert b.blockEspPerBlockStyles.containsKey("minecraft:spawner") : "copyFrom failed for per-block styles";
        assert b.blockEspPerBlockStyles.get("minecraft:spawner").tracer : "copyFrom failed to deep-copy style";
    }
}
