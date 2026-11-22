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
}
