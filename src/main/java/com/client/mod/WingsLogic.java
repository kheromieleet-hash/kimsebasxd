package com.client.mod;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure math, clamping, animation, and target-filtering helpers for the LiquidBounce Wings module.
 * Kept free of Minecraft/LWJGL imports so unit tests can exercise all wing math directly.
 */
public final class WingsLogic {

    public static final int MODE_LINES = 0;
    public static final int MODE_DRAGON = 1;
    public static final int MODE_ANGEL = 2;
    public static final int MODE_DEMON = 3;
    public static final int MODE_BUTTERFLY = 4;
    public static final int MODE_CYBER = 5;
    public static final int MODE_PHOENIX = 6;
    public static final int MODE_CRYSTAL = 7;
    public static final int MODE_SERAPH = 8;
    public static final int MODE_VOID = 9;
    public static final int MODE_VALKYRIE = 10;
    public static final int MODE_GALAXY = 11;
    public static final int MODE_STEAMPUNK = 12;
    public static final int MODE_THUNDER = 13;
    public static final int MODE_SAKURA = 14;
    public static final int MODE_BONE = 15;
    public static final int MODE_SESTIA_LEAF = 16;

    private static final String[] MODE_NAMES = {
            "Lines",
            "Dragon",
            "Angel",
            "Demon",
            "Butterfly",
            "Cyber",
            "Phoenix",
            "Crystal",
            "Seraph",
            "Void",
            "Valkyrie",
            "Galaxy",
            "Steampunk",
            "Thunder",
            "Sakura",
            "Bone",
            "Sestia Leaf"
    };

    public static final int COLOR_CHROMA = 0;
    public static final int COLOR_SESTIA_RED = 1;
    public static final int COLOR_CYAN = 2;
    public static final int COLOR_BLUE = 3;
    public static final int COLOR_VIOLET = 4;
    public static final int COLOR_PINK = 5;
    public static final int COLOR_EMERALD = 6;
    public static final int COLOR_GOLD = 7;
    public static final int COLOR_WHITE = 8;
    public static final int COLOR_CUSTOM = 9;

    public static final int DAMAGE_RGB = 0xFF3434;

    public static final int[] PALETTE_SWATCHES = {
            0xFF3355, // Sestia Red
            0x00D4FF, // Cyan
            0x3B82F6, // Blue
            0xA855F7, // Violet
            0xEC4899, // Pink
            0x22C55E, // Emerald
            0xF59E0B, // Gold
            0xFF6B00, // Sunset Orange
            0x14F1D9, // Mint Aqua
            0xDC143C, // Crimson
            0xC4B5FD, // Ice Lavender
            0xFFFFFF  // Pure White
    };

    public static final float FIXED_BACK_OFFSET = 0.00f;
    public static final float FIXED_EQUIPMENT_OFFSET = 0.10f;
    public static final float DRAGON_UPRIGHT_PITCH_DEG = -82.0f;
    public static final int PREVIEW_ENTITY_ID = -26300;

    private static final String[] MODE_STYLE_TAGS = {
            "Ribbon",
            "3D Limb",
            "Feathered",
            "Bat Web",
            "Lepidoptera",
            "Holo Blade",
            "Ember",
            "Gem Shard",
            "6-Wing Halo",
            "Abyss Rift",
            "Spear Gold",
            "Constellation",
            "Brass Gear",
            "Storm Bolt",
            "Blossom",
            "Skeletal",
            "7-Leaf Hemp"
    };

    private static final String[] COLOR_NAMES = {
            "Chroma",
            "Sestia Red",
            "Cyan",
            "Blue",
            "Violet",
            "Pink",
            "Emerald",
            "Gold",
            "White",
            "Custom"
    };

    private static final int[] COLOR_VALUES = {
            0x00D4FF, // Chroma fallback
            0xFF3355, // Sestia Red
            0x00D4FF, // Cyan (LiquidBounce signature)
            0x3B82F6, // Blue
            0xA855F7, // Violet
            0xEC4899, // Pink
            0x22C55E, // Emerald
            0xF59E0B, // Gold
            0xFFFFFF, // White
            0x00D4FF  // Custom fallback
    };

    public record DragonAnimState(float wingXRot, float wingYRot, float wingZRot, float tipYRot, float tipZRot) {
    }

    public record WingProfile(
            boolean enabled,
            int mode,
            int colorPreset,
            int customRgb,
            float length,
            float width,
            int linesCount,
            float fadeStart,
            float height,
            float flapSpeed,
            boolean showDamage
    ) {
        public WingProfile(
                boolean enabled,
                int mode,
                int colorPreset,
                float length,
                float width,
                int linesCount,
                float fadeStart,
                float height,
                float flapSpeed,
                boolean showDamage
        ) {
            this(enabled, mode, colorPreset, 0x00D4FF, length, width, linesCount, fadeStart, height, flapSpeed, showDamage);
        }

        public WingProfile sanitized() {
            return new WingProfile(
                    enabled,
                    clampMode(mode),
                    clampColorPreset(colorPreset),
                    clampRgb(customRgb),
                    clampLength(length),
                    clampWidth(width),
                    clampLinesCount(linesCount),
                    clampFadeStart(fadeStart),
                    clampHeight(height),
                    clampFlapSpeed(flapSpeed),
                    showDamage
            );
        }
    }

    private static final Map<String, WingProfile> REMOTE_PROFILES = new ConcurrentHashMap<>();
    private static volatile WingProfile previewProfile = null;

    private WingsLogic() {
    }

    public static WingProfile fromConfig(ModConfig cfg) {
        if (cfg == null) {
            return new WingProfile(false, MODE_LINES, COLOR_CYAN, 0x00D4FF, 1.15f, 0.06f, 5, 0.50f, 0.15f, 1.0f, true);
        }
        return new WingProfile(
                cfg.wingsEnabled,
                cfg.wingsMode,
                cfg.wingsColorPreset,
                cfg.wingsCustomRgb,
                cfg.wingsLength,
                cfg.wingsWidth,
                cfg.wingsLinesCount,
                cfg.wingsFadeStart,
                cfg.wingsHeight,
                cfg.wingsFlapSpeed,
                cfg.wingsShowDamage
        ).sanitized();
    }

    public static void putRemoteProfile(String playerName, WingProfile profile) {
        if (playerName == null || playerName.isBlank() || profile == null) {
            return;
        }
        REMOTE_PROFILES.put(playerName.trim().toLowerCase(Locale.ROOT), profile.sanitized());
    }

    public static WingProfile getRemoteProfile(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        return REMOTE_PROFILES.get(playerName.trim().toLowerCase(Locale.ROOT));
    }

    public static boolean hasRemoteProfiles() {
        return !REMOTE_PROFILES.isEmpty();
    }

    public static void removeRemoteProfile(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return;
        }
        REMOTE_PROFILES.remove(playerName.trim().toLowerCase(Locale.ROOT));
    }

    public static void clearRemoteProfiles() {
        REMOTE_PROFILES.clear();
    }

    public static void setPreviewProfile(WingProfile profile) {
        previewProfile = profile != null ? profile.sanitized() : null;
    }

    public static WingProfile getPreviewProfile() {
        return previewProfile;
    }

    public static WingProfile resolvePreviewProfile(ModConfig cfg, boolean panicActive, int hoveredWingMode) {
        if (cfg == null || panicActive) {
            return null;
        }
        if (hoveredWingMode >= 0 && hoveredWingMode < modeCount()) {
            return new WingProfile(
                    true,
                    hoveredWingMode,
                    cfg.wingsColorPreset,
                    cfg.wingsCustomRgb,
                    cfg.wingsLength,
                    cfg.wingsWidth,
                    cfg.wingsLinesCount,
                    cfg.wingsFadeStart,
                    cfg.wingsHeight,
                    cfg.wingsFlapSpeed,
                    cfg.wingsShowDamage
            ).sanitized();
        }
        return cfg.wingsEnabled ? fromConfig(cfg) : null;
    }

    public static WingProfile resolveRenderProfile(
            ModConfig cfg,
            boolean panicActive,
            boolean invisible,
            boolean spectator,
            boolean isSelf,
            String remotePlayerName
    ) {
        if (cfg == null || panicActive || invisible || spectator) {
            return null;
        }
        if (isSelf) {
            return cfg.wingsEnabled ? fromConfig(cfg) : null;
        }
        WingProfile remote = getRemoteProfile(remotePlayerName);
        return (remote != null && remote.enabled()) ? remote : null;
    }

    public static int modeCount() {
        return MODE_NAMES.length;
    }

    public static int colorCount() {
        return COLOR_NAMES.length;
    }

    public static String modeName(int mode) {
        return MODE_NAMES[clampMode(mode)];
    }

    public static String modeStyleTag(int mode) {
        return MODE_STYLE_TAGS[clampMode(mode)];
    }

    public static String colorPresetName(int colorPreset) {
        return COLOR_NAMES[clampColorPreset(colorPreset)];
    }

    public static int clampMode(int mode) {
        if (mode < 0 || mode >= MODE_NAMES.length) {
            return MODE_LINES;
        }
        return mode;
    }

    public static int clampColorPreset(int preset) {
        if (preset < 0 || preset >= COLOR_NAMES.length) {
            return COLOR_CYAN;
        }
        return preset;
    }

    public static float clampLength(float length) {
        return clampFloat(length, 0.40f, 2.50f, 1.15f);
    }

    public static float clampWidth(float width) {
        return clampFloat(width, 0.02f, 0.20f, 0.06f);
    }

    public static int clampLinesCount(int count) {
        if (count < 2 || count > 12) {
            return Math.max(2, Math.min(12, count));
        }
        return count;
    }

    public static float clampFadeStart(float fadeStart) {
        return clampFloat(fadeStart, 0.10f, 1.00f, 0.50f);
    }

    public static float clampHeight(float height) {
        return clampFloat(height, -0.50f, 0.60f, 0.15f);
    }

    public static float clampFlapSpeed(float flapSpeed) {
        return clampFloat(flapSpeed, 0.0f, 3.0f, 1.0f);
    }

    public static boolean shouldRender(
            boolean enabled,
            boolean panicActive,
            boolean invisible,
            boolean spectator,
            boolean isSelf
    ) {
        return enabled && !panicActive && !invisible && !spectator && isSelf;
    }

    public static float computeBackZ(boolean hasChestEquipment) {
        return computeBackZ(hasChestEquipment, false, false);
    }

    public static float computeBackZ(boolean hasChestEquipment, boolean hasElytra, boolean hasCustomCloak) {
        float base = 0.125f + FIXED_BACK_OFFSET + (hasChestEquipment ? FIXED_EQUIPMENT_OFFSET : 0.0f);
        if (hasElytra) {
            base += 0.095f;
        } else if (hasCustomCloak) {
            base += 0.050f;
        }
        return base;
    }

    public static float computeAnchorY(float height) {
        return 0.25f - clampHeight(height);
    }

    public static int clampRgb(int rgb) {
        return rgb & 0xFFFFFF;
    }

    public static String formatHexRgb(int rgb) {
        return String.format(Locale.ROOT, "#%06X", clampRgb(rgb));
    }

    public static int resolveRgb(int colorPreset, float ageInTicks, float phaseOffset, boolean showDamageActive) {
        return resolveRgb(colorPreset, 0x00D4FF, ageInTicks, phaseOffset, showDamageActive);
    }

    public static int resolveRgb(int colorPreset, int customRgb, float ageInTicks, float phaseOffset, boolean showDamageActive) {
        if (showDamageActive) {
            return DAMAGE_RGB;
        }
        int clamped = clampColorPreset(colorPreset);
        if (clamped == COLOR_CHROMA) {
            float safeTicks = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
            float safePhase = Float.isFinite(phaseOffset) ? phaseOffset : 0.0f;
            float rawHue = safeTicks * 0.012f + safePhase;
            float hue = rawHue - (float) Math.floor(rawHue);
            return hsvToRgb(hue, 0.85f, 1.0f);
        }
        if (clamped == COLOR_CUSTOM) {
            return clampRgb(customRgb);
        }
        return COLOR_VALUES[clamped];
    }

    public static DragonAnimState computeDragonAnim(
            float ageInTicks,
            float flapSpeed,
            boolean isFallFlying,
            boolean isCrouching
    ) {
        float safeTicks = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
        float speed = clampFlapSpeed(flapSpeed);
        float crouchSweep = isCrouching ? -0.12f : 0.0f;
        float glideSweep = isFallFlying ? -0.15f : 0.0f;
        float crouchPitch = isCrouching ? 0.10f : 0.0f;
        float tipYRot = -0.18f;

        if (speed <= 0.001f) {
            return new DragonAnimState(
                    crouchPitch,
                    0.30f,
                    -0.48f + crouchSweep + glideSweep,
                    tipYRot,
                    -0.22f
            );
        }

        float mult = isFallFlying ? 1.9f : 1.0f;
        float phase = safeTicks * 0.12f * speed * mult;

        float xRot = (float) Math.cos(phase) * 0.07f + crouchPitch;
        float yRot = 0.30f + (float) Math.cos(phase) * 0.05f;
        float zRot = -0.58f - (float) Math.sin(phase) * 0.28f + crouchSweep + glideSweep;
        float tipZRot = -0.20f + (float) Math.sin(phase + 1.4f) * 0.32f;
        return new DragonAnimState(xRot, yRot, zRot, tipYRot, tipZRot);
    }

    public static float computeFeatherLengthScale(float featherNorm) {
        float t = clampUnit(featherNorm);
        float raw = 0.92f + 0.12f * (float) Math.sin(t * Math.PI * 0.7f) - 0.38f * t;
        return Math.max(0.50f, Math.min(1.05f, raw));
    }

    public static float computeFeatherPitchDeg(float featherNorm) {
        float t = clampUnit(featherNorm);
        return 46.0f - 68.0f * t;
    }

    public static float computeFeatherSweepDeg(
            float featherNorm,
            float ageInTicks,
            float flapSpeed,
            boolean isFallFlying
    ) {
        float t = clampUnit(featherNorm);
        float safeTicks = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
        float speed = clampFlapSpeed(flapSpeed);
        float mult = isFallFlying ? 2.0f : 1.0f;
        float wave = (speed <= 0.001f)
                ? 0.0f
                : (float) Math.sin(safeTicks * 0.14f * speed * mult - t * 0.65f) * (isFallFlying ? 15.0f : 11.0f);
        return 24.0f + 10.0f * t + wave;
    }

    public static int blendRgb(int rgbA, int rgbB, float t) {
        float u = clampUnit(t);
        int ar = (rgbA >> 16) & 0xFF;
        int ag = (rgbA >> 8) & 0xFF;
        int ab = rgbA & 0xFF;
        int br = (rgbB >> 16) & 0xFF;
        int bg = (rgbB >> 8) & 0xFF;
        int bb = rgbB & 0xFF;
        int rr = Math.round(ar + (br - ar) * u);
        int rg = Math.round(ag + (bg - ag) * u);
        int rb = Math.round(ab + (bb - ab) * u);
        return (rr << 16) | (rg << 8) | rb;
    }

    public static float computeFlapPhase(
            float ageInTicks,
            float flapSpeed,
            float baseRate,
            boolean isFallFlying,
            float glideMult
    ) {
        float speed = clampFlapSpeed(flapSpeed);
        if (speed <= 0.001f) {
            return 0.0f;
        }
        float safeTicks = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
        float mult = isFallFlying ? glideMult : 1.0f;
        return safeTicks * baseRate * speed * mult;
    }

    public static float computeWingSweepRad(
            float baseDeg,
            float ampDeg,
            float phase,
            float lagOffset,
            boolean isFallFlying,
            boolean isCrouching
    ) {
        float deg = baseDeg + (isFallFlying ? 10.0f : 0.0f) + (isCrouching ? 6.0f : 0.0f);
        if (phase != 0.0f) {
            deg += (float) Math.sin(phase - lagOffset) * ampDeg;
        }
        float clampedDeg = clampFloat(deg, 8.5f, 82.0f, 24.0f);
        return clampedDeg * (float) (Math.PI / 180.0);
    }

    public static float computeCyberPulse(float ageInTicks, float flapSpeed, float bladeNorm) {
        float safeTicks = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
        float speed = Math.max(0.35f, clampFlapSpeed(flapSpeed));
        float wave = 0.5f + 0.5f * (float) Math.sin(safeTicks * 0.22f * speed - clampUnit(bladeNorm) * 2.4f);
        return clampUnit(wave);
    }

    /**
     * Pure float HSV -> packed 24-bit RGB without {@code java.awt.Color} so headless/macOS environments work safely.
     */
    public static int hsvToRgb(float h, float s, float v) {
        float hue = h - (float) Math.floor(h);
        float sat = Math.max(0.0f, Math.min(1.0f, s));
        float val = Math.max(0.0f, Math.min(1.0f, v));

        float sector = hue * 6.0f;
        int i = ((int) Math.floor(sector)) % 6;
        float f = sector - (float) Math.floor(sector);
        float p = val * (1.0f - sat);
        float q = val * (1.0f - sat * f);
        float t = val * (1.0f - sat * (1.0f - f));

        float r;
        float g;
        float b;
        switch (i) {
            case 0 -> { r = val; g = t;   b = p; }
            case 1 -> { r = q;   g = val; b = p; }
            case 2 -> { r = p;   g = val; b = t; }
            case 3 -> { r = p;   g = q;   b = val; }
            case 4 -> { r = t;   g = p;   b = val; }
            default -> { r = val; g = p;  b = q; }
        }
        int ri = Math.max(0, Math.min(255, Math.round(r * 255.0f)));
        int gi = Math.max(0, Math.min(255, Math.round(g * 255.0f)));
        int bi = Math.max(0, Math.min(255, Math.round(b * 255.0f)));
        return (ri << 16) | (gi << 8) | bi;
    }

    public static float[] rgbToHsv(int rgb) {
        int clamped = clampRgb(rgb);
        float r = ((clamped >> 16) & 0xFF) / 255.0f;
        float g = ((clamped >> 8) & 0xFF) / 255.0f;
        float b = (clamped & 0xFF) / 255.0f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float d = max - min;
        float h;
        if (d < 1.0e-5f) {
            h = 0.0f;
        } else if (max == r) {
            h = ((g - b) / d) / 6.0f;
            if (h < 0.0f) h += 1.0f;
        } else if (max == g) {
            h = (((b - r) / d) + 2.0f) / 6.0f;
        } else {
            h = (((r - g) / d) + 4.0f) / 6.0f;
        }
        float s = (max < 1.0e-5f) ? 0.0f : (d / max);
        return new float[]{clampUnit(h), clampUnit(s), clampUnit(max)};
    }

    private static float clampUnit(float value) {
        if (!Float.isFinite(value)) {
            return 0.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float clampFloat(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }
}
