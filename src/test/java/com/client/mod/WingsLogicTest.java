package com.client.mod;

public class WingsLogicTest {

    public static void main(String[] args) {
        testClampingAndNaNGuards();
        testColorPresetsChromaAndDamage();
        testTargetFilteringAndPanic();
        testBackOffsetAndAnchorGeometry();
        testDragonAndFeatherAnimation();
        System.out.println("ALL WingsLogicTest ASSERTIONS PASSED!");
    }

    private static void testClampingAndNaNGuards() {
        assert WingsLogic.clampMode(-1) == WingsLogic.MODE_LINES;
        assert WingsLogic.clampMode(99) == WingsLogic.MODE_LINES;
        assert WingsLogic.clampMode(WingsLogic.MODE_DRAGON) == WingsLogic.MODE_DRAGON;
        assert "Lines".equals(WingsLogic.modeName(0));
        assert "Dragon".equals(WingsLogic.modeName(1));

        assert WingsLogic.clampTarget(-5) == WingsLogic.TARGET_ONLY_ME;
        assert WingsLogic.clampTarget(10) == WingsLogic.TARGET_ONLY_ME;
        assert "Only Me".equals(WingsLogic.targetName(0));
        assert "Friends".equals(WingsLogic.targetName(1));
        assert "All Players".equals(WingsLogic.targetName(2));

        assert WingsLogic.clampColorPreset(-1) == WingsLogic.COLOR_CYAN;
        assert WingsLogic.clampColorPreset(99) == WingsLogic.COLOR_CYAN;
        assert WingsLogic.colorCount() == 9;
        assert "Chroma".equals(WingsLogic.colorPresetName(0));
        assert "Sestia Red".equals(WingsLogic.colorPresetName(1));
        assert "Cyan".equals(WingsLogic.colorPresetName(2));
        assert "White".equals(WingsLogic.colorPresetName(8));

        assert Math.abs(WingsLogic.clampLength(Float.NaN) - 1.15f) < 0.0001f;
        assert Math.abs(WingsLogic.clampLength(0.01f) - 0.40f) < 0.0001f;
        assert Math.abs(WingsLogic.clampLength(9.0f) - 2.50f) < 0.0001f;

        assert Math.abs(WingsLogic.clampWidth(Float.POSITIVE_INFINITY) - 0.06f) < 0.0001f;
        assert Math.abs(WingsLogic.clampWidth(0.001f) - 0.02f) < 0.0001f;
        assert Math.abs(WingsLogic.clampWidth(1.0f) - 0.20f) < 0.0001f;

        assert WingsLogic.clampLinesCount(0) == 2;
        assert WingsLogic.clampLinesCount(50) == 12;
        assert WingsLogic.clampLinesCount(6) == 6;

        assert Math.abs(WingsLogic.clampFadeStart(0.0f) - 0.10f) < 0.0001f;
        assert Math.abs(WingsLogic.clampFadeStart(2.0f) - 1.00f) < 0.0001f;
        assert Math.abs(WingsLogic.clampHeight(-5.0f) + 0.50f) < 0.0001f;
        assert Math.abs(WingsLogic.clampHeight(5.0f) - 0.60f) < 0.0001f;
        assert Math.abs(WingsLogic.clampFlapSpeed(-1.0f)) < 0.0001f;
        assert Math.abs(WingsLogic.clampFlapSpeed(9.0f) - 3.0f) < 0.0001f;
    }

    private static void testColorPresetsChromaAndDamage() {
        for (int p = 0; p < WingsLogic.colorCount(); p++) {
            int rgb = WingsLogic.resolveRgb(p, 12.5f, 0.1f, false);
            assert (rgb & 0xFF000000) == 0 : "RGB should remain 24-bit without stray alpha";
            if (p != WingsLogic.COLOR_CHROMA) {
                assert rgb != 0 : "Preset color " + p + " must be non-black";
            }
        }
        assert WingsLogic.resolveRgb(WingsLogic.COLOR_CYAN, 0.0f, 0.0f, false) == 0x00D4FF;
        assert WingsLogic.resolveRgb(WingsLogic.COLOR_WHITE, 0.0f, 0.0f, false) == 0xFFFFFF;
        assert WingsLogic.resolveRgb(WingsLogic.COLOR_CYAN, 10.0f, 0.0f, true) == WingsLogic.DAMAGE_RGB;
        assert WingsLogic.resolveRgb(WingsLogic.COLOR_CHROMA, 10.0f, 0.0f, true) == WingsLogic.DAMAGE_RGB;

        int c0 = WingsLogic.resolveRgb(WingsLogic.COLOR_CHROMA, 0.0f, 0.0f, false);
        int c1 = WingsLogic.resolveRgb(WingsLogic.COLOR_CHROMA, 28.0f, 0.0f, false);
        assert c0 != c1 : "Chroma should cycle hue across ticks";
    }

    private static void testTargetFilteringAndPanic() {
        assert !WingsLogic.shouldRenderTarget(false, false, false, false, WingsLogic.TARGET_ALL, true, true);
        assert !WingsLogic.shouldRenderTarget(true, true, false, false, WingsLogic.TARGET_ALL, true, true);
        assert !WingsLogic.shouldRenderTarget(true, false, true, false, WingsLogic.TARGET_ALL, true, true);
        assert !WingsLogic.shouldRenderTarget(true, false, false, true, WingsLogic.TARGET_ALL, true, true);

        assert WingsLogic.shouldRenderTarget(true, false, false, false, WingsLogic.TARGET_ONLY_ME, true, false);
        assert !WingsLogic.shouldRenderTarget(true, false, false, false, WingsLogic.TARGET_ONLY_ME, false, true);

        assert WingsLogic.shouldRenderTarget(true, false, false, false, WingsLogic.TARGET_FRIENDS, true, false);
        assert WingsLogic.shouldRenderTarget(true, false, false, false, WingsLogic.TARGET_FRIENDS, false, true);
        assert !WingsLogic.shouldRenderTarget(true, false, false, false, WingsLogic.TARGET_FRIENDS, false, false);

        assert WingsLogic.shouldRenderTarget(true, false, false, false, WingsLogic.TARGET_ALL, false, false);
    }

    private static void testBackOffsetAndAnchorGeometry() {
        assert Math.abs(WingsLogic.FIXED_BACK_OFFSET - 0.00f) < 0.0001f;
        assert Math.abs(WingsLogic.FIXED_EQUIPMENT_OFFSET - 0.10f) < 0.0001f;

        float bareZ = WingsLogic.computeBackZ(false);
        float armoredZ = WingsLogic.computeBackZ(true);
        assert Math.abs(bareZ - 0.125f) < 0.0001f;
        assert Math.abs(armoredZ - 0.225f) < 0.0001f;
        assert Math.abs((armoredZ - bareZ) - 0.10f) < 0.0001f;

        float higherY = WingsLogic.computeAnchorY(0.40f);
        float lowerY = WingsLogic.computeAnchorY(-0.20f);
        assert higherY < lowerY : "Increasing height should move anchor up (-Y in MC entity coords)";
    }

    private static void testDragonAndFeatherAnimation() {
        assert WingsLogic.DRAGON_UPRIGHT_PITCH_DEG >= -90.0f && WingsLogic.DRAGON_UPRIGHT_PITCH_DEG <= -75.0f;

        WingsLogic.DragonAnimState staticPose = WingsLogic.computeDragonAnim(50.0f, 0.0f, false, false);
        WingsLogic.DragonAnimState staticLater = WingsLogic.computeDragonAnim(999.0f, 0.0f, false, false);
        assert Math.abs(staticPose.wingZRot() - staticLater.wingZRot()) < 0.0001f;
        assert staticPose.wingZRot() < -0.25f : "Upright angel wings must sweep behind the back";
        assert staticPose.wingYRot() > 0.15f : "Upright angel wings must lift upward above shoulders";
        assert staticPose.tipYRot() < 0.0f : "Outer wing tip must arch naturally outward";

        float minZ = Float.POSITIVE_INFINITY;
        float maxZ = Float.NEGATIVE_INFINITY;
        for (int t = 0; t <= 100; t += 2) {
            WingsLogic.DragonAnimState step = WingsLogic.computeDragonAnim((float) t, 1.0f, false, false);
            assert step.wingZRot() < -0.15f : "Wings must never clip forward at tick " + t + ": " + step.wingZRot();
            assert step.wingYRot() > 0.10f : "Wings must stay arched upward at tick " + t + ": " + step.wingYRot();
            if (step.wingZRot() < minZ) minZ = step.wingZRot();
            if (step.wingZRot() > maxZ) maxZ = step.wingZRot();
        }
        assert (maxZ - minZ) > 0.30f : "Wings must oscillate visibly during flap cycle";

        WingsLogic.DragonAnimState crouch = WingsLogic.computeDragonAnim(10.0f, 1.0f, false, true);
        WingsLogic.DragonAnimState stand = WingsLogic.computeDragonAnim(10.0f, 1.0f, false, false);
        assert crouch.wingXRot() > stand.wingXRot();
        assert crouch.wingZRot() < stand.wingZRot();

        WingsLogic.DragonAnimState gliding = WingsLogic.computeDragonAnim(25.0f, 1.0f, true, false);
        assert Float.isFinite(gliding.wingXRot())
                && Float.isFinite(gliding.wingYRot())
                && Float.isFinite(gliding.wingZRot())
                && Float.isFinite(gliding.tipYRot())
                && Float.isFinite(gliding.tipZRot());
        assert WingsLogic.computeDragonAnim(0.0f, 0.0f, true, false).wingZRot()
                < WingsLogic.computeDragonAnim(0.0f, 0.0f, false, false).wingZRot();

        float topScale = WingsLogic.computeFeatherLengthScale(0.0f);
        float bottomScale = WingsLogic.computeFeatherLengthScale(1.0f);
        assert topScale > bottomScale && bottomScale >= 0.50f && topScale <= 1.05f;
        assert Math.abs(WingsLogic.computeFeatherPitchDeg(0.0f) - 46.0f) < 0.001f;
        assert Math.abs(WingsLogic.computeFeatherPitchDeg(1.0f) + 22.0f) < 0.001f;

        float sweep0 = WingsLogic.computeFeatherSweepDeg(0.5f, 10.0f, 0.0f, false);
        assert Math.abs(sweep0 - 29.0f) < 0.001f;
    }
}
