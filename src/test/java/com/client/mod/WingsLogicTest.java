package com.client.mod;

public class WingsLogicTest {

    public static void main(String[] args) {
        testClampingAndNaNGuards();
        testColorPresetsChromaAndDamage();
        testTargetFilteringAndPanic();
        testPreviewProfileAndStyleTags();
        testBackOffsetAndAnchorGeometry();
        testDragonAndFeatherAnimation();
        System.out.println("ALL WingsLogicTest ASSERTIONS PASSED!");
    }

    private static void testClampingAndNaNGuards() {
        assert WingsLogic.modeCount() == 17;
        assert WingsLogic.clampMode(-1) == WingsLogic.MODE_LINES;
        assert WingsLogic.clampMode(99) == WingsLogic.MODE_LINES;
        assert WingsLogic.clampMode(WingsLogic.MODE_DRAGON) == WingsLogic.MODE_DRAGON;
        assert WingsLogic.clampMode(WingsLogic.MODE_ANGEL) == WingsLogic.MODE_ANGEL;
        assert WingsLogic.clampMode(WingsLogic.MODE_DEMON) == WingsLogic.MODE_DEMON;
        assert WingsLogic.clampMode(WingsLogic.MODE_BUTTERFLY) == WingsLogic.MODE_BUTTERFLY;
        assert WingsLogic.clampMode(WingsLogic.MODE_CYBER) == WingsLogic.MODE_CYBER;
        assert WingsLogic.clampMode(WingsLogic.MODE_PHOENIX) == WingsLogic.MODE_PHOENIX;
        assert WingsLogic.clampMode(WingsLogic.MODE_CRYSTAL) == WingsLogic.MODE_CRYSTAL;
        assert WingsLogic.clampMode(WingsLogic.MODE_SERAPH) == WingsLogic.MODE_SERAPH;
        assert WingsLogic.clampMode(WingsLogic.MODE_VOID) == WingsLogic.MODE_VOID;
        assert WingsLogic.clampMode(WingsLogic.MODE_VALKYRIE) == WingsLogic.MODE_VALKYRIE;
        assert WingsLogic.clampMode(WingsLogic.MODE_GALAXY) == WingsLogic.MODE_GALAXY;
        assert WingsLogic.clampMode(WingsLogic.MODE_STEAMPUNK) == WingsLogic.MODE_STEAMPUNK;
        assert WingsLogic.clampMode(WingsLogic.MODE_THUNDER) == WingsLogic.MODE_THUNDER;
        assert WingsLogic.clampMode(WingsLogic.MODE_SAKURA) == WingsLogic.MODE_SAKURA;
        assert WingsLogic.clampMode(WingsLogic.MODE_BONE) == WingsLogic.MODE_BONE;
        assert WingsLogic.clampMode(WingsLogic.MODE_SESTIA_LEAF) == WingsLogic.MODE_SESTIA_LEAF;
        assert "Lines".equals(WingsLogic.modeName(0));
        assert "Dragon".equals(WingsLogic.modeName(1));
        assert "Angel".equals(WingsLogic.modeName(2));
        assert "Demon".equals(WingsLogic.modeName(3));
        assert "Butterfly".equals(WingsLogic.modeName(4));
        assert "Cyber".equals(WingsLogic.modeName(5));
        assert "Phoenix".equals(WingsLogic.modeName(6));
        assert "Crystal".equals(WingsLogic.modeName(7));
        assert "Seraph".equals(WingsLogic.modeName(8));
        assert "Void".equals(WingsLogic.modeName(9));
        assert "Valkyrie".equals(WingsLogic.modeName(10));
        assert "Galaxy".equals(WingsLogic.modeName(11));
        assert "Steampunk".equals(WingsLogic.modeName(12));
        assert "Thunder".equals(WingsLogic.modeName(13));
        assert "Sakura".equals(WingsLogic.modeName(14));
        assert "Bone".equals(WingsLogic.modeName(15));
        assert "Sestia Leaf".equals(WingsLogic.modeName(16));
        assert "7-Leaf Hemp".equals(WingsLogic.modeStyleTag(16));

        assert WingsLogic.clampColorPreset(-1) == WingsLogic.COLOR_CYAN;
        assert WingsLogic.clampColorPreset(99) == WingsLogic.COLOR_CYAN;
        assert WingsLogic.colorCount() == 10;
        assert "Chroma".equals(WingsLogic.colorPresetName(0));
        assert "Sestia Red".equals(WingsLogic.colorPresetName(1));
        assert "Cyan".equals(WingsLogic.colorPresetName(2));
        assert "White".equals(WingsLogic.colorPresetName(8));
        assert "Custom".equals(WingsLogic.colorPresetName(9));
        assert WingsLogic.PALETTE_SWATCHES.length == 12;
        assert "#FF3355".equals(WingsLogic.formatHexRgb(0xFF3355));
        float[] hsv = WingsLogic.rgbToHsv(0xFF0000);
        assert Math.abs(hsv[0]) < 0.01f && Math.abs(hsv[1] - 1.0f) < 0.01f && Math.abs(hsv[2] - 1.0f) < 0.01f;
        assert WingsLogic.resolveRgb(WingsLogic.COLOR_CUSTOM, 0x12AB34, 5.0f, 0.0f, false) == 0x12AB34;
        assert WingsLogic.resolveRgb(WingsLogic.COLOR_CUSTOM, 0x12AB34, 5.0f, 0.0f, true) == WingsLogic.DAMAGE_RGB;

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
        assert !WingsLogic.shouldRender(false, false, false, false, true);
        assert !WingsLogic.shouldRender(true, true, false, false, true);
        assert !WingsLogic.shouldRender(true, false, true, false, true);
        assert !WingsLogic.shouldRender(true, false, false, true, true);
        assert !WingsLogic.shouldRender(true, false, false, false, false);
        assert WingsLogic.shouldRender(true, false, false, false, true);

        WingsLogic.clearRemoteProfiles();
        ModConfig cfg = new ModConfig();
        cfg.wingsEnabled = true;
        cfg.wingsMode = WingsLogic.MODE_ANGEL;
        cfg.wingsColorPreset = WingsLogic.COLOR_WHITE;

        // Self gets own config profile
        WingsLogic.WingProfile selfP = WingsLogic.resolveRenderProfile(cfg, false, false, false, true, "Me");
        assert selfP != null && selfP.mode() == WingsLogic.MODE_ANGEL && selfP.colorPreset() == WingsLogic.COLOR_WHITE;

        // Random non-Sestia player gets null (no wings)
        assert WingsLogic.resolveRenderProfile(cfg, false, false, false, false, "RandomPlayer") == null;

        // Remote Sestia user gets their own chosen different wing model & color!
        WingsLogic.putRemoteProfile("Friend_TR", new WingsLogic.WingProfile(
                true, WingsLogic.MODE_DEMON, WingsLogic.COLOR_SESTIA_RED,
                1.40f, 0.06f, 5, 0.50f, 0.20f, 1.5f, true));
        WingsLogic.WingProfile friendP = WingsLogic.resolveRenderProfile(cfg, false, false, false, false, "friend_tr");
        assert friendP != null
                && friendP.mode() == WingsLogic.MODE_DEMON
                && friendP.colorPreset() == WingsLogic.COLOR_SESTIA_RED
                && Math.abs(friendP.length() - 1.40f) < 0.001f;

        // Even if local player turns their own wings off, remote Sestia friend's wings still render unless Panic is active
        cfg.wingsEnabled = false;
        assert WingsLogic.resolveRenderProfile(cfg, false, false, false, true, "Me") == null;
        assert WingsLogic.resolveRenderProfile(cfg, false, false, false, false, "Friend_TR") != null;
        assert WingsLogic.resolveRenderProfile(cfg, true, false, false, false, "Friend_TR") == null;
        WingsLogic.clearRemoteProfiles();
    }

    private static void testPreviewProfileAndStyleTags() {
        for (int m = 0; m < WingsLogic.modeCount(); m++) {
            String tag = WingsLogic.modeStyleTag(m);
            assert tag != null && !tag.isBlank() : "Expected non-blank style tag for mode " + m;
        }

        ModConfig cfg = new ModConfig();
        cfg.wingsEnabled = false;
        cfg.wingsMode = WingsLogic.MODE_LINES;
        cfg.wingsColorPreset = WingsLogic.COLOR_SESTIA_RED;

        // Disabled & no hover -> null preview
        assert WingsLogic.resolvePreviewProfile(cfg, false, -1) == null;

        // Disabled & hovering Angel -> enabled live Angel preview inheriting color settings
        WingsLogic.WingProfile hoveredOff = WingsLogic.resolvePreviewProfile(cfg, false, WingsLogic.MODE_ANGEL);
        assert hoveredOff != null
                && hoveredOff.enabled()
                && hoveredOff.mode() == WingsLogic.MODE_ANGEL
                && hoveredOff.colorPreset() == WingsLogic.COLOR_SESTIA_RED;

        // Enabled with Lines, hovering Galaxy -> previews Galaxy
        cfg.wingsEnabled = true;
        WingsLogic.WingProfile hoveredOn = WingsLogic.resolvePreviewProfile(cfg, false, WingsLogic.MODE_GALAXY);
        assert hoveredOn != null && hoveredOn.mode() == WingsLogic.MODE_GALAXY;

        // Enabled & no hover -> shows equipped wing
        WingsLogic.WingProfile fallback = WingsLogic.resolvePreviewProfile(cfg, false, -1);
        assert fallback != null && fallback.mode() == WingsLogic.MODE_LINES;

        // Panic disables preview completely
        assert WingsLogic.resolvePreviewProfile(cfg, true, WingsLogic.MODE_GALAXY) == null;
    }

    private static void testBackOffsetAndAnchorGeometry() {
        assert Math.abs(WingsLogic.FIXED_BACK_OFFSET - 0.00f) < 0.0001f;
        assert Math.abs(WingsLogic.FIXED_EQUIPMENT_OFFSET - 0.10f) < 0.0001f;

        float bareZ = WingsLogic.computeBackZ(false);
        float armoredZ = WingsLogic.computeBackZ(true);
        float cloakZ = WingsLogic.computeBackZ(false, false, true);
        float elytraZ = WingsLogic.computeBackZ(true, true, true);
        assert Math.abs(bareZ - 0.125f) < 0.0001f;
        assert Math.abs(armoredZ - 0.225f) < 0.0001f;
        assert Math.abs((armoredZ - bareZ) - 0.10f) < 0.0001f;
        assert cloakZ > bareZ;
        assert elytraZ > armoredZ;

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

        assert WingsLogic.blendRgb(0x000000, 0xFFFFFF, 0.0f) == 0x000000;
        assert WingsLogic.blendRgb(0x000000, 0xFFFFFF, 1.0f) == 0xFFFFFF;
        assert WingsLogic.blendRgb(0xFF0000, 0x0000FF, 0.5f) == 0x800080;

        assert WingsLogic.computeFlapPhase(50.0f, 0.0f, 0.12f, false, 1.8f) == 0.0f;
        assert WingsLogic.computeFlapPhase(10.0f, 1.0f, 0.12f, true, 2.0f)
                > WingsLogic.computeFlapPhase(10.0f, 1.0f, 0.12f, false, 2.0f);

        float staticSweep = WingsLogic.computeWingSweepRad(28.0f, 18.0f, 0.0f, 0.0f, false, false);
        float glideSweep = WingsLogic.computeWingSweepRad(28.0f, 18.0f, 0.0f, 0.0f, true, false);
        float crouchSweep = WingsLogic.computeWingSweepRad(28.0f, 18.0f, 0.0f, 0.0f, false, true);
        assert staticSweep >= 0.14f && glideSweep > staticSweep && crouchSweep > staticSweep;
        for (int t = 0; t <= 100; t += 2) {
            float p = WingsLogic.computeFlapPhase((float) t, 1.0f, 0.12f, false, 1.8f);
            float sw = WingsLogic.computeWingSweepRad(28.0f, 18.0f, p, 0.2f, false, false);
            assert sw >= 0.14f : "Custom 3D wing sweep must stay behind back: " + sw;
        }

        float p0 = WingsLogic.computeCyberPulse(0.0f, 1.0f, 0.0f);
        float p1 = WingsLogic.computeCyberPulse(8.0f, 1.0f, 0.0f);
        assert p0 >= 0.0f && p0 <= 1.0f && p1 >= 0.0f && p1 <= 1.0f && p0 != p1;
    }
}
