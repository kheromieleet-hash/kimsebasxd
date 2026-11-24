package com.client.mod;

/**
 * Verifies pitch-black initialization, staggered leaf emergence, non-symmetrical distribution,
 * swaying/rotation/flutter dynamics, wrap-around recycling, and lag-spike clamping for
 * {@link SestiaTitleBackgroundRenderer}.
 */
public class SestiaTitleBackgroundRendererTest {

    public static void main(String[] args) {
        testStartsPitchBlackWithSmallLeaves();
        testGradualEmergenceAfterDelay();
        testLongRunDynamicMotionAndRecycling();
        testLagSpikeClampingAndSubScreenDimming();
        testGuiAmbientLeavesPreSeededAndIndependent();
        testPointerWindAndParallaxClamping();
        testStudioDensitySpeedAndModeHelpers();
        System.out.println("ALL SestiaTitleBackgroundRendererTest ASSERTIONS PASSED!");
    }

    private static void testStartsPitchBlackWithSmallLeaves() {
        SestiaTitleBackgroundRenderer.resetToBlackStart(960, 540, 26308L);
        assert SestiaTitleBackgroundRenderer.countVisibleLeaves(540) == 0
                : "Expected 0 visible leaves right at startup so screen opens pitch black";

        for (int i = 0; i < SestiaTitleBackgroundRenderer.LEAF_COUNT; i++) {
            float sz = SestiaTitleBackgroundRenderer.getLeafSize(i);
            assert sz >= 20.0f && sz <= 44.0f : "Leaf size must sit in [20..44], got " + sz;
            assert SestiaTitleBackgroundRenderer.getLeafY(i) < 0.0f : "Leaf must start above screen top";
        }
    }

    private static void testGradualEmergenceAfterDelay() {
        SestiaTitleBackgroundRenderer.resetToBlackStart(960, 540, 26308L);
        for (int f = 0; f < 33; f++) {
            SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540);
        }
        int visible = SestiaTitleBackgroundRenderer.countVisibleLeaves(540);
        assert visible > 0 && visible < 30
                : "Expected a few leaves to begin falling gradually at ~0.55s, got " + visible;
    }

    private static void testLongRunDynamicMotionAndRecycling() {
        SestiaTitleBackgroundRenderer.resetToBlackStart(960, 540, 26308L);
        for (int f = 0; f < 900; f++) {
            SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540);
        }

        int visible = SestiaTitleBackgroundRenderer.countVisibleLeaves(540);
        assert visible >= 95 : "Expected lush randomized falling leaf population at 15s, got " + visible;

        boolean[] buckets = new boolean[8];
        int nonZeroAngles = 0;
        int varyingDrawX = 0;
        for (int i = 0; i < SestiaTitleBackgroundRenderer.LEAF_COUNT; i++) {
            float drawX = SestiaTitleBackgroundRenderer.computeDrawX(i);
            float baseX = SestiaTitleBackgroundRenderer.getLeafX(i);
            float angle = SestiaTitleBackgroundRenderer.computeAngle(i);
            float scaleX = SestiaTitleBackgroundRenderer.computeFlutterScaleX(i);
            int titleAlpha = SestiaTitleBackgroundRenderer.computeLeafAlpha(i, 540, true);
            int subAlpha = SestiaTitleBackgroundRenderer.computeLeafAlpha(i, 540, false);

            assert Float.isFinite(drawX) && Float.isFinite(angle);
            assert scaleX >= 0.40f && scaleX <= 1.0001f : "Flutter scaleX out of bounds: " + scaleX;
            assert titleAlpha >= 0 && titleAlpha <= 255;
            assert subAlpha <= titleAlpha : "Sub-screen leaves must not exceed title-screen opacity";

            if (Math.abs(angle) > 0.05f) {
                nonZeroAngles++;
            }
            if (Math.abs(drawX - baseX) > 0.5f) {
                varyingDrawX++;
            }
            int b = Math.max(0, Math.min(7, (int) (Math.max(0.0f, Math.min(959.0f, baseX)) / 120.0f)));
            buckets[b] = true;
        }

        int occupiedBuckets = 0;
        for (boolean b : buckets) {
            if (b) occupiedBuckets++;
        }
        assert occupiedBuckets == 8 : "Expected full edge-to-edge spread across screen width, buckets=" + occupiedBuckets;
        assert nonZeroAngles >= 90 : "Expected leaves to rotate/rock while falling, got " + nonZeroAngles;
        assert varyingDrawX >= 80 : "Expected lateral swaying motion on falling leaves, got " + varyingDrawX;

        // Run an additional 30 seconds to verify continuous bottom->top recycling never exhausts leaves
        for (int f = 0; f < 1800; f++) {
            SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540);
        }
        assert SestiaTitleBackgroundRenderer.countVisibleLeaves(540) >= 95
                : "Expected recycled leaves to remain continuously populated at 45s";
    }

    private static void testLagSpikeClampingAndSubScreenDimming() {
        SestiaTitleBackgroundRenderer.resetToBlackStart(960, 540, 999L);
        // Huge dt spike (5.0s) must clamp to <= 0.1f so all leaves stay at <= 0.1s rather than jumping mid-screen
        SestiaTitleBackgroundRenderer.stepSimulation(5.0f, 960, 540);
        assert SestiaTitleBackgroundRenderer.countVisibleLeaves(540) == 0
                : "dt clamp failed: leaves skipped initial black fade on 5s spike";
    }

    private static void testGuiAmbientLeavesPreSeededAndIndependent() {
        SestiaTitleBackgroundRenderer.resetToBlackStart(960, 540, 26308L);
        SestiaTitleBackgroundRenderer.resetGuiAmbientLeaves(860, 520, 26329L);

        int initialVisible = SestiaTitleBackgroundRenderer.countVisibleGuiLeaves(860, 520);
        assert initialVisible >= 20
                : "Expected pre-seeded GUI ambient leaves immediately visible, got " + initialVisible;

        int maxInteriorAlpha = 0;
        for (int i = 0; i < SestiaTitleBackgroundRenderer.GUI_LEAF_COUNT; i++) {
            float sz = SestiaTitleBackgroundRenderer.getGuiLeafSize(i);
            assert sz >= 14.0f && sz <= 26.0f : "GUI ambient leaf size must be in [14..26], got " + sz;
            assert Float.isFinite(SestiaTitleBackgroundRenderer.computeGuiDrawX(i));
            int a = SestiaTitleBackgroundRenderer.computeGuiLeafAlpha(i, 860, 520);
            assert a >= 0 && a <= 255 : "GUI leaf alpha out of range: " + a;
            if (a > maxInteriorAlpha) {
                maxInteriorAlpha = a;
            }
        }
        assert maxInteriorAlpha > 0 : "Expected non-zero interior GUI ambient leaf alpha";

        for (int f = 0; f < 120; f++) {
            SestiaTitleBackgroundRenderer.stepGuiAmbientLeaves(1.0f / 60.0f, 860, 520);
        }
        int visibleAfter2s = SestiaTitleBackgroundRenderer.countVisibleGuiLeaves(860, 520);
        assert visibleAfter2s >= 20
                : "Expected GUI ambient leaves to stay populated across frames, got " + visibleAfter2s;
        assert SestiaTitleBackgroundRenderer.countVisibleLeaves(540) == 0
                : "Stepping GUI ambient leaves must not mutate fullscreen title-screen leaves";

        SestiaTitleBackgroundRenderer.stepGuiAmbientLeaves(5.0f, 860, 520);
        SestiaTitleBackgroundRenderer.stepGuiAmbientLeaves(Float.NaN, 860, 520);
        for (int i = 0; i < SestiaTitleBackgroundRenderer.GUI_LEAF_COUNT; i++) {
            assert Float.isFinite(SestiaTitleBackgroundRenderer.computeGuiDrawX(i));
        }

        SestiaTitleBackgroundRenderer.resetGuiAmbientLeaves(20, 20, 26329L);
        int maxEdgeAlpha = 0;
        for (int i = 0; i < SestiaTitleBackgroundRenderer.GUI_LEAF_COUNT; i++) {
            int edgeAlpha = SestiaTitleBackgroundRenderer.computeGuiLeafAlpha(i, 20, 20);
            assert edgeAlpha >= 0 && edgeAlpha <= 255;
            if (edgeAlpha > maxEdgeAlpha) {
                maxEdgeAlpha = edgeAlpha;
            }
        }
        assert maxEdgeAlpha < maxInteriorAlpha
                : "Edge-constrained GUI leaves should fade relative to interior leaves: edge="
                + maxEdgeAlpha + " interior=" + maxInteriorAlpha;
    }

    private static void testPointerWindAndParallaxClamping() {
        SestiaTitleBackgroundRenderer.clearPointer();
        SestiaTitleBackgroundRenderer.resetToBlackStart(960, 540, 26308L);
        for (int f = 0; f < 180; f++) {
            SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540);
        }

        int activeIdx = -1;
        for (int i = 0; i < SestiaTitleBackgroundRenderer.LEAF_COUNT; i++) {
            float ly = SestiaTitleBackgroundRenderer.getLeafY(i);
            if (ly > 20.0f && ly < 480.0f) {
                activeIdx = i;
                break;
            }
        }
        assert activeIdx >= 0 : "Expected at least one active falling leaf after 3s";

        float lx = SestiaTitleBackgroundRenderer.getLeafX(activeIdx);
        float ly = SestiaTitleBackgroundRenderer.getLeafY(activeIdx);
        SestiaTitleBackgroundRenderer.updatePointer(lx, ly, 960, 540);
        SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540);
        SestiaTitleBackgroundRenderer.updatePointer(lx + 15.0f, ly + 4.0f, 960, 540);
        for (int f = 0; f < 3; f++) {
            SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540);
        }

        float pushX = SestiaTitleBackgroundRenderer.getLeafPushX(activeIdx);
        float pushY = SestiaTitleBackgroundRenderer.getLeafPushY(activeIdx);
        assert Math.abs(pushX) > 0.01f || Math.abs(pushY) > 0.01f
                : "Pointer sweep near falling leaf must impart non-zero breeze impulse";
        assert Math.abs(pushX) <= 120.0f && Math.abs(pushY) <= 120.0f
                : "Breeze push must stay clamped within [-120..120]: " + pushX + ", " + pushY;
        assert Float.isFinite(SestiaTitleBackgroundRenderer.computeDrawX(activeIdx))
                && Float.isFinite(SestiaTitleBackgroundRenderer.computeDrawY(activeIdx));

        SestiaTitleBackgroundRenderer.clearPointer();
        for (int f = 0; f < 180; f++) {
            SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540);
        }
        assert Math.abs(SestiaTitleBackgroundRenderer.getLeafPushX(activeIdx)) < 0.5f
                && Math.abs(SestiaTitleBackgroundRenderer.getLeafPushY(activeIdx)) < 0.5f
                : "Leaf push impulse must damp smoothly toward zero after pointer clears";

        SestiaTitleBackgroundRenderer.updatePointer(Float.NaN, Float.POSITIVE_INFINITY, 960, 540);
        SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540);
        for (int i = 0; i < SestiaTitleBackgroundRenderer.LEAF_COUNT; i++) {
            assert Float.isFinite(SestiaTitleBackgroundRenderer.computeDrawX(i))
                    && Float.isFinite(SestiaTitleBackgroundRenderer.computeDrawY(i));
        }

        SestiaTitleBackgroundRenderer.clearPointer();
        SestiaTitleBackgroundRenderer.resetGuiAmbientLeaves(860, 520, 26329L);
        float gx = SestiaTitleBackgroundRenderer.getGuiLeafX(0);
        float gy = SestiaTitleBackgroundRenderer.getGuiLeafY(0);
        SestiaTitleBackgroundRenderer.setGuiPointer(gx, gy, 860, 520);
        for (int f = 0; f < 3; f++) {
            SestiaTitleBackgroundRenderer.stepGuiAmbientLeaves(1.0f / 60.0f, 860, 520);
        }
        float guiPushX = SestiaTitleBackgroundRenderer.getGuiLeafPushX(0);
        float guiPushY = SestiaTitleBackgroundRenderer.getGuiLeafPushY(0);
        assert Math.abs(guiPushX) > 0.01f || Math.abs(guiPushY) > 0.01f
                : "Pointer near GUI ambient leaf must impart non-zero breeze impulse";
        assert Math.abs(guiPushX) <= 120.0f && Math.abs(guiPushY) <= 120.0f
                : "GUI leaf push must stay clamped within [-120..120]";
        assert Float.isFinite(SestiaTitleBackgroundRenderer.computeGuiDrawX(0))
                && Float.isFinite(SestiaTitleBackgroundRenderer.computeGuiDrawY(0));
        SestiaTitleBackgroundRenderer.clearPointer();
    }

    private static void testStudioDensitySpeedAndModeHelpers() {
        int lowCount = SestiaTitleBackgroundRenderer.resolveActiveLeafCount(0.25f);
        int defCount = SestiaTitleBackgroundRenderer.resolveActiveLeafCount(1.00f);
        int highCount = SestiaTitleBackgroundRenderer.resolveActiveLeafCount(1.50f);
        assert lowCount >= 1 && lowCount < defCount : "Expected 0.25x count < 1.0x count";
        assert defCount == SestiaTitleBackgroundRenderer.LEAF_COUNT : "Expected 1.0x density == LEAF_COUNT";
        assert highCount == SestiaTitleBackgroundRenderer.MAX_LEAF_COUNT && highCount > defCount
                : "Expected 1.50x density == MAX_LEAF_COUNT";
        assert SestiaTitleBackgroundRenderer.resolveActiveLeafCount(Float.NaN) == SestiaTitleBackgroundRenderer.LEAF_COUNT;

        int lowGui = SestiaTitleBackgroundRenderer.resolveActiveGuiLeafCount(0.25f);
        int defGui = SestiaTitleBackgroundRenderer.resolveActiveGuiLeafCount(1.00f);
        int highGui = SestiaTitleBackgroundRenderer.resolveActiveGuiLeafCount(1.50f);
        assert lowGui >= 1 && lowGui < defGui;
        assert defGui == SestiaTitleBackgroundRenderer.GUI_LEAF_COUNT;
        assert highGui == SestiaTitleBackgroundRenderer.MAX_GUI_LEAF_COUNT && highGui > defGui;

        assert SestiaTitleBackgroundRenderer.resolveSpeedMultiplier(0.10f) == 0.40f;
        assert SestiaTitleBackgroundRenderer.resolveSpeedMultiplier(5.00f) == 2.00f;
        assert SestiaTitleBackgroundRenderer.resolveSpeedMultiplier(Float.NaN) == 1.00f;

        assert !SestiaTitleBackgroundRenderer.isMenuLeavesEnabled(0) && !SestiaTitleBackgroundRenderer.isGuiLeavesEnabled(0);
        assert SestiaTitleBackgroundRenderer.isMenuLeavesEnabled(1) && !SestiaTitleBackgroundRenderer.isGuiLeavesEnabled(1);
        assert SestiaTitleBackgroundRenderer.isMenuLeavesEnabled(2) && SestiaTitleBackgroundRenderer.isGuiLeavesEnabled(2);

        SestiaTitleBackgroundRenderer.clearPointer();
        SestiaTitleBackgroundRenderer.resetToBlackStart(960, 540, 26308L);
        for (int f = 0; f < 240; f++) {
            SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540, 1.0f);
        }
        float yNormal = SestiaTitleBackgroundRenderer.getLeafY(SestiaTitleBackgroundRenderer.MAX_LEAF_COUNT - 1);

        SestiaTitleBackgroundRenderer.resetToBlackStart(960, 540, 26308L);
        for (int f = 0; f < 240; f++) {
            SestiaTitleBackgroundRenderer.stepSimulation(1.0f / 60.0f, 960, 540, 2.0f);
        }
        float yFast = SestiaTitleBackgroundRenderer.getLeafY(SestiaTitleBackgroundRenderer.MAX_LEAF_COUNT - 1);
        assert Float.isFinite(yNormal) && Float.isFinite(yFast) && yFast != yNormal
                : "Expected 2.0x speed multiplier to advance extended-pool leaves cleanly";
    }
}
