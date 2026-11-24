package com.client.mod;

import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure-Java math, catalog metadata, clamping, loadout state, remote cache, preview overrides,
 * and emote pose kinematics for Sestia Client's Multi-Slot Cosmetics Studio.
 * Kept free of Minecraft/LWJGL imports so standalone Java 25 unit tests can verify all behavior directly.
 */
public final class CosmeticsLogic {

    // Cloak / Cape modes
    public static final int CLOAK_SESTIA_LEAF = 0;
    public static final int CLOAK_CHROMA_NEBULA = 1;
    public static final int CLOAK_CYBER_GRID = 2;
    public static final int CLOAK_OBSIDIAN_ROYAL = 3;
    private static final String[] CLOAK_NAMES = {
            "Sestia Leaf", "Chroma Nebula", "Cyber Grid", "Obsidian Royal"
    };
    private static final String[] CLOAK_TAGS = {
            "7-Leaf Weave", "Aurora Flow", "Holo Scanline", "Gold Trim"
    };

    // 3D Headwear modes
    public static final int HAT_SERAPH_HALO = 0;
    public static final int HAT_LEAF_WREATH = 1;
    public static final int HAT_DEMON_HORNS = 2;
    public static final int HAT_ROYAL_CROWN = 3;
    public static final int HAT_CYBER_EARS = 4;
    public static final int HAT_KITSUNE_VISOR = 5;
    private static final String[] HAT_NAMES = {
            "Seraph Halo", "Leaf Wreath", "Demon Horns", "Royal Crown", "Cyber Ears", "Kitsune Visor"
    };
    private static final String[] HAT_TAGS = {
            "Floating Ring", "7-Leaf Crown", "Curved Obsidian", "Gem Diadem", "Holo Cat", "Neon Mask"
    };

    // Shoulder Pets & Orbiting Companions
    public static final int PET_EMERALD_WISP = 0;
    public static final int PET_CYBER_DRONE = 1;
    public static final int PET_MINI_FOX = 2;
    public static final int PET_MINI_ALLAY = 3;
    public static final int PET_MINI_BEE = 4;
    private static final String[] PET_NAMES = {
            "Emerald Wisp", "Cyber Drone", "Mini Fox", "Mini Allay", "Mini Bee"
    };
    private static final String[] PET_TAGS = {
            "Orbit Flame", "Twin Rotor", "Shoulder Sit", "Flutter Wing", "Hover Buzz"
    };

    public static final int PET_SIDE_RIGHT = 0;
    public static final int PET_SIDE_LEFT = 1;
    public static final int PET_SIDE_ORBIT = 2;
    private static final String[] PET_SIDE_NAMES = {"Right", "Left", "Orbit"};

    // Back Gear & Articulated Tails
    public static final int GEAR_KATANA_SHEATH = 0;
    public static final int GEAR_TECH_PACK = 1;
    public static final int GEAR_VINE_TAIL = 2;
    public static final int GEAR_DEMON_TAIL = 3;
    public static final int GEAR_FOX_TAIL = 4;
    private static final String[] GEAR_NAMES = {
            "Katana Sheath", "Tech Pack", "Vine Tail", "Demon Tail", "Fox Tail"
    };
    private static final String[] GEAR_TAGS = {
            "Diagonal Blade", "Twin Thruster", "Leaf Sway", "Spade Whip", "Plush Curve"
    };

    // 3D Player Auras
    public static final int AURA_GROUND_RUNE = 0;
    public static final int AURA_DOUBLE_HELIX = 1;
    public static final int AURA_LEAF_ORBIT = 2;
    public static final int AURA_PULSE_HALO = 3;
    private static final String[] AURA_NAMES = {
            "Ground Rune", "Double Helix", "Leaf Orbit", "Pulse Halo"
    };
    private static final String[] AURA_TAGS = {
            "Sigil Circle", "Twin Spiral", "4x Hemp Leaves", "Pulse Rings"
    };

    // Sprint / Jump / Elytra Trails
    public static final int TRAIL_STEP_RIPPLES = 0;
    public static final int TRAIL_LEAF_RIBBON = 1;
    public static final int TRAIL_NEON_SPARKS = 2;
    public static final int TRAIL_ELYTRA_CONTRAIL = 3;
    private static final String[] TRAIL_NAMES = {
            "Step Ripples", "Leaf Ribbon", "Neon Sparks", "Elytra Contrail"
    };
    private static final String[] TRAIL_TAGS = {
            "Ground Rings", "Flowing Wake", "Spark Stream", "Wing Tip Trails"
    };

    // Combat Visual FX (Hit Burst & Kill Finisher)
    public static final int HIT_FX_EMERALD_BURST = 0;
    public static final int HIT_FX_LEAF_PETALS = 1;
    public static final int HIT_FX_SHOCKWAVE_RING = 2;
    private static final String[] HIT_FX_NAMES = {
            "Emerald Burst", "Leaf Petals", "Shockwave Ring"
    };

    public static final int KILL_FX_SOUL_BEACON = 0;
    public static final int KILL_FX_EMERALD_NOVA = 1;
    public static final int KILL_FX_VOXEL_DISSOLVE = 2;
    private static final String[] KILL_FX_NAMES = {
            "Soul Beacon", "Emerald Nova", "Voxel Dissolve"
    };

    // First-Person Hand Aura & Melee Slash Arc
    public static final int HAND_AURA_FOREARM_RINGS = 0;
    public static final int HAND_AURA_BLADE_EDGE = 1;
    private static final String[] HAND_AURA_NAMES = {
            "Forearm Rings", "Blade Edge"
    };

    public static final int SLASH_ARC_EMERALD_CRESCENT = 0;
    public static final int SLASH_ARC_CRIMSON_WAVE = 1;
    private static final String[] SLASH_ARC_NAMES = {
            "Emerald Crescent", "Crimson Wave"
    };

    // 3D Pose Emotes
    public static final int EMOTE_NONE = -1;
    public static final int EMOTE_ARMS_CROSSED = 0;
    public static final int EMOTE_SALUTE = 1;
    public static final int EMOTE_HOVER_MEDITATE = 2;
    public static final int EMOTE_WAVE = 3;
    public static final int EMOTE_POINT = 4;
    private static final String[] EMOTE_NAMES = {
            "Arms Crossed", "Salute", "Hover Meditate", "Wave", "Point"
    };

    // Locker Preview Pose Strip
    public static final int PREVIEW_POSE_IDLE = 0;
    public static final int PREVIEW_POSE_WALK = 1;
    public static final int PREVIEW_POSE_SNEAK = 2;
    public static final int PREVIEW_POSE_GLIDE = 3;
    public static final int PREVIEW_POSE_HIT = 4;
    private static final String[] PREVIEW_POSE_NAMES = {
            "Idle", "Walk", "Sneak", "Glide", "Hit"
    };

    // Emote auto-expiry for remote players (20s safety cap)
    public static final long REMOTE_EMOTE_TTL_MS = 20_000L;

    public record EmotePose(
            float headX, float headY, float headZ,
            float bodyX, float bodyY, float bodyOffsetY,
            float leftArmX, float leftArmY, float leftArmZ,
            float rightArmX, float rightArmY, float rightArmZ,
            float leftLegX, float leftLegZ,
            float rightLegX, float rightLegZ
    ) {
        public static final EmotePose NEUTRAL = new EmotePose(
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f, 0f,
                0f, 0f,
                0f, 0f
        );
    }

    public record CosmeticLoadout(
            WingsLogic.WingProfile wings,
            boolean cloakEnabled, int cloakMode, int cloakColorPreset, int cloakCustomRgb,
            boolean hatEnabled, int hatMode, int hatColorPreset, int hatCustomRgb, float hatScale,
            boolean petEnabled, int petMode, int petSide, int petColorPreset, int petCustomRgb,
            boolean gearEnabled, int gearMode, int gearColorPreset, int gearCustomRgb,
            boolean auraEnabled, int auraMode, int auraColorPreset, int auraCustomRgb, float auraRadius, float auraSpeed,
            boolean trailEnabled, int trailMode, int trailColorPreset, int trailCustomRgb,
            boolean hitFxEnabled, int hitFxMode, int hitFxColorPreset, int hitFxCustomRgb,
            boolean killFxEnabled, int killFxMode, int killFxColorPreset, int killFxCustomRgb,
            boolean totemFxEnabled, float totemFxScale, boolean totemFxRing,
            boolean glintEnabled, int glintColorPreset, int glintCustomRgb, float glintIntensity,
            boolean handAuraEnabled, int handAuraMode,
            boolean slashArcEnabled, int slashArcMode, int slashArcColorPreset, int slashArcCustomRgb
    ) {
        public CosmeticLoadout sanitized() {
            WingsLogic.WingProfile safeWings = wings != null
                    ? wings.sanitized()
                    : new WingsLogic.WingProfile(false, WingsLogic.MODE_LINES, WingsLogic.COLOR_CYAN, 0x00D4FF, 1.15f, 0.06f, 5, 0.50f, 0.15f, 1.0f, true);
            return new CosmeticLoadout(
                    safeWings,
                    cloakEnabled, clampCloakMode(cloakMode), WingsLogic.clampColorPreset(cloakColorPreset), WingsLogic.clampRgb(cloakCustomRgb),
                    hatEnabled, clampHatMode(hatMode), WingsLogic.clampColorPreset(hatColorPreset), WingsLogic.clampRgb(hatCustomRgb), clampHatScale(hatScale),
                    petEnabled, clampPetMode(petMode), clampPetSide(petSide), WingsLogic.clampColorPreset(petColorPreset), WingsLogic.clampRgb(petCustomRgb),
                    gearEnabled, clampGearMode(gearMode), WingsLogic.clampColorPreset(gearColorPreset), WingsLogic.clampRgb(gearCustomRgb),
                    auraEnabled, clampAuraMode(auraMode), WingsLogic.clampColorPreset(auraColorPreset), WingsLogic.clampRgb(auraCustomRgb), clampAuraRadius(auraRadius), clampAuraSpeed(auraSpeed),
                    trailEnabled, clampTrailMode(trailMode), WingsLogic.clampColorPreset(trailColorPreset), WingsLogic.clampRgb(trailCustomRgb),
                    hitFxEnabled, clampHitFxMode(hitFxMode), WingsLogic.clampColorPreset(hitFxColorPreset), WingsLogic.clampRgb(hitFxCustomRgb),
                    killFxEnabled, clampKillFxMode(killFxMode), WingsLogic.clampColorPreset(killFxColorPreset), WingsLogic.clampRgb(killFxCustomRgb),
                    totemFxEnabled, clampTotemScale(totemFxScale), totemFxRing,
                    glintEnabled, WingsLogic.clampColorPreset(glintColorPreset), WingsLogic.clampRgb(glintCustomRgb), clampGlintIntensity(glintIntensity),
                    handAuraEnabled, clampHandAuraMode(handAuraMode),
                    slashArcEnabled, clampSlashArcMode(slashArcMode), WingsLogic.clampColorPreset(slashArcColorPreset), WingsLogic.clampRgb(slashArcCustomRgb)
            );
        }

        public int activeSlotCount() {
            int count = 0;
            if (wings != null && wings.enabled()) count++;
            if (cloakEnabled) count++;
            if (hatEnabled) count++;
            if (petEnabled) count++;
            if (gearEnabled) count++;
            if (auraEnabled) count++;
            if (trailEnabled) count++;
            if (hitFxEnabled || killFxEnabled || totemFxEnabled) count++;
            if (glintEnabled) count++;
            if (handAuraEnabled || slashArcEnabled) count++;
            return count;
        }
    }

    public record ActiveEmote(int emoteId, long startedAtMs) {
    }

    private static final Map<String, CosmeticLoadout> REMOTE_LOADOUTS = new ConcurrentHashMap<>();
    private static final Map<String, ActiveEmote> REMOTE_EMOTES = new ConcurrentHashMap<>();
    private static final Set<String> ONLINE_SESTIA_USERS = ConcurrentHashMap.newKeySet();

    private static volatile CosmeticLoadout previewLoadout = null;
    private static volatile int previewPose = PREVIEW_POSE_IDLE;
    private static volatile int previewEmote = EMOTE_NONE;
    private static volatile boolean previewShowArmor = false;
    private static volatile int localActiveEmote = EMOTE_NONE;
    private static volatile long localEmoteStartedAtMs = 0L;

    private CosmeticsLogic() {
    }

    public static CosmeticLoadout fromConfig(ModConfig cfg) {
        if (cfg == null) {
            return defaultLoadout();
        }
        return new CosmeticLoadout(
                WingsLogic.fromConfig(cfg),
                cfg.cosmeticCloakEnabled, cfg.cosmeticCloakMode, cfg.cosmeticCloakColorPreset, cfg.cosmeticCloakCustomRgb,
                cfg.cosmeticHatEnabled, cfg.cosmeticHatMode, cfg.cosmeticHatColorPreset, cfg.cosmeticHatCustomRgb, cfg.cosmeticHatScale,
                cfg.cosmeticPetEnabled, cfg.cosmeticPetMode, cfg.cosmeticPetSide, cfg.cosmeticPetColorPreset, cfg.cosmeticPetCustomRgb,
                cfg.cosmeticGearEnabled, cfg.cosmeticGearMode, cfg.cosmeticGearColorPreset, cfg.cosmeticGearCustomRgb,
                cfg.cosmeticAuraEnabled, cfg.cosmeticAuraMode, cfg.cosmeticAuraColorPreset, cfg.cosmeticAuraCustomRgb, cfg.cosmeticAuraRadius, cfg.cosmeticAuraSpeed,
                cfg.cosmeticTrailEnabled, cfg.cosmeticTrailMode, cfg.cosmeticTrailColorPreset, cfg.cosmeticTrailCustomRgb,
                cfg.cosmeticHitFxEnabled, cfg.cosmeticHitFxMode, cfg.cosmeticHitFxColorPreset, cfg.cosmeticHitFxCustomRgb,
                cfg.cosmeticKillFxEnabled, cfg.cosmeticKillFxMode, cfg.cosmeticKillFxColorPreset, cfg.cosmeticKillFxCustomRgb,
                cfg.cosmeticTotemFxEnabled, cfg.cosmeticTotemFxScale, cfg.cosmeticTotemFxRing,
                cfg.cosmeticGlintEnabled, cfg.cosmeticGlintColorPreset, cfg.cosmeticGlintCustomRgb, cfg.cosmeticGlintIntensity,
                cfg.cosmeticHandAuraEnabled, cfg.cosmeticHandAuraMode,
                cfg.cosmeticSlashArcEnabled, cfg.cosmeticSlashArcMode, cfg.cosmeticSlashArcColorPreset, cfg.cosmeticSlashArcCustomRgb
        ).sanitized();
    }

    public static CosmeticLoadout defaultLoadout() {
        return new CosmeticLoadout(
                new WingsLogic.WingProfile(false, WingsLogic.MODE_SESTIA_LEAF, WingsLogic.COLOR_EMERALD, 0x34D399, 1.15f, 0.06f, 5, 0.50f, 0.15f, 1.0f, true),
                false, CLOAK_SESTIA_LEAF, WingsLogic.COLOR_EMERALD, 0x34D399,
                false, HAT_SERAPH_HALO, WingsLogic.COLOR_EMERALD, 0x34D399, 1.0f,
                false, PET_EMERALD_WISP, PET_SIDE_RIGHT, WingsLogic.COLOR_EMERALD, 0x34D399,
                false, GEAR_KATANA_SHEATH, WingsLogic.COLOR_EMERALD, 0x34D399,
                false, AURA_GROUND_RUNE, WingsLogic.COLOR_EMERALD, 0x34D399, 0.95f, 1.0f,
                false, TRAIL_LEAF_RIBBON, WingsLogic.COLOR_EMERALD, 0x34D399,
                false, HIT_FX_EMERALD_BURST, WingsLogic.COLOR_EMERALD, 0x34D399,
                false, KILL_FX_SOUL_BEACON, WingsLogic.COLOR_EMERALD, 0x34D399,
                false, 0.78f, true,
                false, WingsLogic.COLOR_EMERALD, 0x34D399, 0.85f,
                false, HAND_AURA_FOREARM_RINGS,
                false, SLASH_ARC_EMERALD_CRESCENT, WingsLogic.COLOR_EMERALD, 0x34D399
        );
    }

    public static void applyLoadoutToConfig(CosmeticLoadout raw, ModConfig cfg) {
        if (raw == null || cfg == null) {
            return;
        }
        CosmeticLoadout l = raw.sanitized();
        WingsLogic.WingProfile w = l.wings();
        cfg.wingsEnabled = w.enabled();
        cfg.wingsMode = w.mode();
        cfg.wingsColorPreset = w.colorPreset();
        cfg.wingsCustomRgb = w.customRgb();
        float[] hsv = WingsLogic.rgbToHsv(w.customRgb());
        cfg.wingsCustomHue = hsv[0];
        cfg.wingsCustomSat = hsv[1];
        cfg.wingsCustomVal = Math.max(0.10f, hsv[2]);
        cfg.wingsLength = w.length();
        cfg.wingsWidth = w.width();
        cfg.wingsLinesCount = w.linesCount();
        cfg.wingsFadeStart = w.fadeStart();
        cfg.wingsHeight = w.height();
        cfg.wingsFlapSpeed = w.flapSpeed();
        cfg.wingsShowDamage = w.showDamage();

        cfg.cosmeticCloakEnabled = l.cloakEnabled();
        cfg.cosmeticCloakMode = l.cloakMode();
        cfg.cosmeticCloakColorPreset = l.cloakColorPreset();
        cfg.cosmeticCloakCustomRgb = l.cloakCustomRgb();

        cfg.cosmeticHatEnabled = l.hatEnabled();
        cfg.cosmeticHatMode = l.hatMode();
        cfg.cosmeticHatColorPreset = l.hatColorPreset();
        cfg.cosmeticHatCustomRgb = l.hatCustomRgb();
        cfg.cosmeticHatScale = l.hatScale();

        cfg.cosmeticPetEnabled = l.petEnabled();
        cfg.cosmeticPetMode = l.petMode();
        cfg.cosmeticPetSide = l.petSide();
        cfg.cosmeticPetColorPreset = l.petColorPreset();
        cfg.cosmeticPetCustomRgb = l.petCustomRgb();

        cfg.cosmeticGearEnabled = l.gearEnabled();
        cfg.cosmeticGearMode = l.gearMode();
        cfg.cosmeticGearColorPreset = l.gearColorPreset();
        cfg.cosmeticGearCustomRgb = l.gearCustomRgb();

        cfg.cosmeticAuraEnabled = l.auraEnabled();
        cfg.cosmeticAuraMode = l.auraMode();
        cfg.cosmeticAuraColorPreset = l.auraColorPreset();
        cfg.cosmeticAuraCustomRgb = l.auraCustomRgb();
        cfg.cosmeticAuraRadius = l.auraRadius();
        cfg.cosmeticAuraSpeed = l.auraSpeed();

        cfg.cosmeticTrailEnabled = l.trailEnabled();
        cfg.cosmeticTrailMode = l.trailMode();
        cfg.cosmeticTrailColorPreset = l.trailColorPreset();
        cfg.cosmeticTrailCustomRgb = l.trailCustomRgb();

        cfg.cosmeticHitFxEnabled = l.hitFxEnabled();
        cfg.cosmeticHitFxMode = l.hitFxMode();
        cfg.cosmeticHitFxColorPreset = l.hitFxColorPreset();
        cfg.cosmeticHitFxCustomRgb = l.hitFxCustomRgb();

        cfg.cosmeticKillFxEnabled = l.killFxEnabled();
        cfg.cosmeticKillFxMode = l.killFxMode();
        cfg.cosmeticKillFxColorPreset = l.killFxColorPreset();
        cfg.cosmeticKillFxCustomRgb = l.killFxCustomRgb();

        cfg.cosmeticTotemFxEnabled = l.totemFxEnabled();
        cfg.cosmeticTotemFxScale = l.totemFxScale();
        cfg.cosmeticTotemFxRing = l.totemFxRing();

        cfg.cosmeticGlintEnabled = l.glintEnabled();
        cfg.cosmeticGlintColorPreset = l.glintColorPreset();
        cfg.cosmeticGlintCustomRgb = l.glintCustomRgb();
        cfg.cosmeticGlintIntensity = l.glintIntensity();

        cfg.cosmeticHandAuraEnabled = l.handAuraEnabled();
        cfg.cosmeticHandAuraMode = l.handAuraMode();

        cfg.cosmeticSlashArcEnabled = l.slashArcEnabled();
        cfg.cosmeticSlashArcMode = l.slashArcMode();
        cfg.cosmeticSlashArcColorPreset = l.slashArcColorPreset();
        cfg.cosmeticSlashArcCustomRgb = l.slashArcCustomRgb();
    }

    /**
     * Syncs all cosmetic slot colors to the active Sestia Leaf theme strain accent RGB.
     */
    public static void matchStrainTheme(ModConfig cfg, int accentRgb) {
        if (cfg == null) {
            return;
        }
        int rgb = WingsLogic.clampRgb(accentRgb);
        float[] hsv = WingsLogic.rgbToHsv(rgb);
        cfg.wingsColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.wingsCustomRgb = rgb;
        cfg.wingsCustomHue = hsv[0];
        cfg.wingsCustomSat = hsv[1];
        cfg.wingsCustomVal = Math.max(0.10f, hsv[2]);

        cfg.cosmeticCloakColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticCloakCustomRgb = rgb;
        cfg.cosmeticHatColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticHatCustomRgb = rgb;
        cfg.cosmeticPetColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticPetCustomRgb = rgb;
        cfg.cosmeticGearColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticGearCustomRgb = rgb;
        cfg.cosmeticAuraColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticAuraCustomRgb = rgb;
        cfg.cosmeticTrailColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticTrailCustomRgb = rgb;
        cfg.cosmeticHitFxColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticHitFxCustomRgb = rgb;
        cfg.cosmeticKillFxColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticKillFxCustomRgb = rgb;
        cfg.cosmeticGlintColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticGlintCustomRgb = rgb;
        cfg.cosmeticSlashArcColorPreset = WingsLogic.COLOR_CUSTOM;
        cfg.cosmeticSlashArcCustomRgb = rgb;
    }

    public static int resolveStrainAccentRgb(int themePreset) {
        return switch (themePreset) {
            case 1 -> 0x22C55E; // Forest
            case 2 -> 0x2DD4BF; // Mint
            case 3 -> 0xFACC15; // Amber Gold
            case 4 -> 0xE2E8F0; // Frost
            case 5 -> 0xF43F5E; // Crimson
            default -> 0x34D399; // Emerald
        };
    }

    // Remote cache & Sestia user registry
    public static void markSestiaUserOnline(String playerName) {
        String key = normalizeKey(playerName);
        if (!key.isEmpty()) {
            ONLINE_SESTIA_USERS.add(key);
        }
    }

    public static void setOnlineSestiaUsers(Collection<String> names) {
        ONLINE_SESTIA_USERS.clear();
        if (names != null) {
            for (String n : names) {
                markSestiaUserOnline(n);
            }
        }
    }

    public static boolean isOnlineSestiaUser(String playerName) {
        String key = normalizeKey(playerName);
        if (key.isEmpty()) {
            return false;
        }
        return ONLINE_SESTIA_USERS.contains(key)
                || REMOTE_LOADOUTS.containsKey(key)
                || WingsLogic.getRemoteProfile(key) != null;
    }

    public static void putRemoteLoadout(String playerName, CosmeticLoadout loadout) {
        String key = normalizeKey(playerName);
        if (key.isEmpty() || loadout == null) {
            return;
        }
        CosmeticLoadout clean = loadout.sanitized();
        REMOTE_LOADOUTS.put(key, clean);
        ONLINE_SESTIA_USERS.add(key);
        if (clean.wings() != null) {
            WingsLogic.putRemoteProfile(key, clean.wings());
        }
    }

    public static CosmeticLoadout getRemoteLoadout(String playerName) {
        String key = normalizeKey(playerName);
        if (key.isEmpty()) {
            return null;
        }
        return REMOTE_LOADOUTS.get(key);
    }

    public static Map<String, CosmeticLoadout> getRemoteLoadoutsView() {
        return Collections.unmodifiableMap(REMOTE_LOADOUTS);
    }

    public static boolean hasRemoteLoadouts() {
        return !REMOTE_LOADOUTS.isEmpty();
    }

    public static void removeRemotePlayer(String playerName) {
        String key = normalizeKey(playerName);
        if (key.isEmpty()) {
            return;
        }
        REMOTE_LOADOUTS.remove(key);
        REMOTE_EMOTES.remove(key);
        ONLINE_SESTIA_USERS.remove(key);
        WingsLogic.removeRemoteProfile(key);
    }

    public static void clearRemoteState() {
        REMOTE_LOADOUTS.clear();
        REMOTE_EMOTES.clear();
        ONLINE_SESTIA_USERS.clear();
        WingsLogic.clearRemoteProfiles();
    }

    // Emote runtime state
    public static void setLocalEmote(int emoteId, long nowMs) {
        if (emoteId < 0 || emoteId >= EMOTE_NAMES.length) {
            localActiveEmote = EMOTE_NONE;
            localEmoteStartedAtMs = 0L;
        } else {
            localActiveEmote = emoteId;
            localEmoteStartedAtMs = nowMs;
        }
    }

    public static void clearLocalEmote() {
        localActiveEmote = EMOTE_NONE;
        localEmoteStartedAtMs = 0L;
    }

    public static int getLocalActiveEmote() {
        return localActiveEmote;
    }

    public static long getLocalEmoteStartedAtMs() {
        return localEmoteStartedAtMs;
    }

    public static void putRemoteEmote(String playerName, int emoteId, long nowMs) {
        String key = normalizeKey(playerName);
        if (key.isEmpty()) {
            return;
        }
        if (emoteId < 0 || emoteId >= EMOTE_NAMES.length) {
            REMOTE_EMOTES.remove(key);
        } else {
            REMOTE_EMOTES.put(key, new ActiveEmote(emoteId, nowMs));
        }
    }

    public static int resolveActiveEmote(boolean isPreview, boolean isSelf, String remotePlayerName, long nowMs) {
        if (isPreview) {
            return previewEmote;
        }
        if (isSelf) {
            return localActiveEmote;
        }
        String key = normalizeKey(remotePlayerName);
        if (key.isEmpty()) {
            return EMOTE_NONE;
        }
        ActiveEmote active = REMOTE_EMOTES.get(key);
        if (active == null) {
            return EMOTE_NONE;
        }
        if (nowMs - active.startedAtMs() > REMOTE_EMOTE_TTL_MS) {
            REMOTE_EMOTES.remove(key);
            return EMOTE_NONE;
        }
        return active.emoteId();
    }

    // Preview overrides (Locker Studio)
    public static void setPreviewLoadout(CosmeticLoadout loadout) {
        previewLoadout = loadout != null ? loadout.sanitized() : null;
        if (previewLoadout != null) {
            WingsLogic.setPreviewProfile(previewLoadout.wings());
        } else {
            WingsLogic.setPreviewProfile(null);
        }
    }

    public static CosmeticLoadout getPreviewLoadout() {
        return previewLoadout;
    }

    public static void setPreviewPose(int pose) {
        previewPose = clampPreviewPose(pose);
    }

    public static int getPreviewPose() {
        return previewPose;
    }

    public static void setPreviewEmote(int emoteId) {
        previewEmote = (emoteId >= 0 && emoteId < EMOTE_NAMES.length) ? emoteId : EMOTE_NONE;
    }

    public static int getPreviewEmote() {
        return previewEmote;
    }

    public static void setPreviewShowArmor(boolean showArmor) {
        previewShowArmor = showArmor;
    }

    public static boolean isPreviewShowArmor() {
        return previewShowArmor;
    }

    public static void clearPreviewOverrides() {
        previewLoadout = null;
        previewEmote = EMOTE_NONE;
        WingsLogic.setPreviewProfile(null);
    }

    public static CosmeticLoadout resolveRenderLoadout(
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
            return fromConfig(cfg);
        }
        if (!cfg.cosmeticShowFriends) {
            return null;
        }
        return getRemoteLoadout(remotePlayerName);
    }

    public static boolean shouldSuppressVanillaCape(
            ModConfig cfg,
            boolean panicActive,
            boolean isPreview,
            boolean isSelf,
            boolean wearingElytra,
            String remotePlayerName
    ) {
        if (cfg == null || panicActive || wearingElytra) {
            return false;
        }
        if (isPreview) {
            CosmeticLoadout p = previewLoadout;
            return p != null && p.cloakEnabled();
        }
        if (isSelf) {
            return cfg.cosmeticCloakEnabled;
        }
        if (!cfg.cosmeticShowFriends) {
            return false;
        }
        CosmeticLoadout remote = getRemoteLoadout(remotePlayerName);
        return remote != null && remote.cloakEnabled();
    }

    public static boolean shouldCancelLocalEmote(
            boolean movedHorizontally,
            boolean jumped,
            boolean crouching,
            boolean fallFlying,
            boolean swimming,
            boolean swinging,
            boolean panicActive
    ) {
        return movedHorizontally || jumped || crouching || fallFlying || swimming || swinging || panicActive;
    }

    /**
     * Pure procedural skeletal pose solver for MC 26.3 {@code PlayerModel} emotes.
     * Angles are in radians matching Mojang {@code ModelPart} conventions.
     */
    public static EmotePose computeEmotePose(int emoteId, float ageInTicks) {
        float t = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
        return switch (emoteId) {
            case EMOTE_ARMS_CROSSED -> new EmotePose(
                    0.06f + (float) Math.sin(t * 0.08f) * 0.03f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f,
                    -0.92f, 0.52f, -0.35f,
                    -0.92f, -0.52f, 0.35f,
                    0.04f, -0.04f,
                    -0.04f, 0.04f
            );
            case EMOTE_SALUTE -> new EmotePose(
                    -0.06f, 0.0f, 0.0f,
                    0.0f, 0.0f, 0.0f,
                    0.08f, 0.0f, -0.06f,
                    -2.18f, -0.42f, 0.32f,
                    0.0f, -0.02f,
                    0.0f, 0.02f
            );
            case EMOTE_HOVER_MEDITATE -> {
                float bob = (float) Math.sin(t * 0.14f) * 0.18f;
                yield new EmotePose(
                        0.12f, 0.0f, 0.0f,
                        0.0f, 0.0f, -2.4f + bob * 4.5f,
                        -0.68f, 0.38f, -0.28f + bob * 0.15f,
                        -0.68f, -0.38f, 0.28f - bob * 0.15f,
                        -0.88f, -0.36f,
                        -0.88f, 0.36f
                );
            }
            case EMOTE_WAVE -> {
                float waveZ = 0.42f + (float) Math.sin(t * 0.45f) * 0.34f;
                yield new EmotePose(
                        -0.04f, -0.10f, 0.06f,
                        0.0f, 0.0f, 0.0f,
                        0.10f, 0.0f, -0.08f,
                        -2.62f, 0.0f, waveZ,
                        0.0f, -0.03f,
                        0.0f, 0.03f
                );
            }
            case EMOTE_POINT -> new EmotePose(
                    -0.04f, 0.0f, 0.0f,
                    0.0f, 0.12f, 0.0f,
                    0.18f, 0.0f, -0.10f,
                    -1.58f, -0.08f, 0.04f,
                    -0.08f, -0.04f,
                    0.10f, 0.04f
            );
            default -> EmotePose.NEUTRAL;
        };
    }

    public static float computeHelmetClearanceY(boolean hasHelmet) {
        return hasHelmet ? -0.08f : 0.0f;
    }

    public static float computeCloakBackZ(boolean hasChestplate) {
        return 0.135f + (hasChestplate ? 0.055f : 0.0f);
    }

    public static float computeGearBackZ(boolean hasChestplate, boolean hasCloak) {
        return 0.135f + (hasChestplate ? 0.065f : 0.0f) + (hasCloak ? 0.045f : 0.0f);
    }

    public static int resolveColorRgb(int colorPreset, int customRgb, float ageInTicks, float phaseOffset) {
        return WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, phaseOffset, false);
    }

    // Metadata & Clamping helpers
    public static int cloakCount() { return CLOAK_NAMES.length; }
    public static String cloakName(int mode) { return CLOAK_NAMES[clampCloakMode(mode)]; }
    public static String cloakTag(int mode) { return CLOAK_TAGS[clampCloakMode(mode)]; }
    public static int clampCloakMode(int mode) { return clampIndex(mode, CLOAK_NAMES.length); }

    public static int hatCount() { return HAT_NAMES.length; }
    public static String hatName(int mode) { return HAT_NAMES[clampHatMode(mode)]; }
    public static String hatTag(int mode) { return HAT_TAGS[clampHatMode(mode)]; }
    public static int clampHatMode(int mode) { return clampIndex(mode, HAT_NAMES.length); }
    public static float clampHatScale(float v) { return clampFloat(v, 0.70f, 1.45f, 1.0f); }

    public static int petCount() { return PET_NAMES.length; }
    public static String petName(int mode) { return PET_NAMES[clampPetMode(mode)]; }
    public static String petTag(int mode) { return PET_TAGS[clampPetMode(mode)]; }
    public static int clampPetMode(int mode) { return clampIndex(mode, PET_NAMES.length); }
    public static int petSideCount() { return PET_SIDE_NAMES.length; }
    public static String petSideName(int side) { return PET_SIDE_NAMES[clampPetSide(side)]; }
    public static int clampPetSide(int side) { return clampIndex(side, PET_SIDE_NAMES.length); }

    public static int gearCount() { return GEAR_NAMES.length; }
    public static String gearName(int mode) { return GEAR_NAMES[clampGearMode(mode)]; }
    public static String gearTag(int mode) { return GEAR_TAGS[clampGearMode(mode)]; }
    public static int clampGearMode(int mode) { return clampIndex(mode, GEAR_NAMES.length); }

    public static int auraCount() { return AURA_NAMES.length; }
    public static String auraName(int mode) { return AURA_NAMES[clampAuraMode(mode)]; }
    public static String auraTag(int mode) { return AURA_TAGS[clampAuraMode(mode)]; }
    public static int clampAuraMode(int mode) { return clampIndex(mode, AURA_NAMES.length); }
    public static float clampAuraRadius(float v) { return clampFloat(v, 0.55f, 1.65f, 0.95f); }
    public static float clampAuraSpeed(float v) { return clampFloat(v, 0.20f, 2.50f, 1.0f); }

    public static int trailCount() { return TRAIL_NAMES.length; }
    public static String trailName(int mode) { return TRAIL_NAMES[clampTrailMode(mode)]; }
    public static String trailTag(int mode) { return TRAIL_TAGS[clampTrailMode(mode)]; }
    public static int clampTrailMode(int mode) { return clampIndex(mode, TRAIL_NAMES.length); }

    public static int hitFxCount() { return HIT_FX_NAMES.length; }
    public static String hitFxName(int mode) { return HIT_FX_NAMES[clampHitFxMode(mode)]; }
    public static int clampHitFxMode(int mode) { return clampIndex(mode, HIT_FX_NAMES.length); }

    public static int killFxCount() { return KILL_FX_NAMES.length; }
    public static String killFxName(int mode) { return KILL_FX_NAMES[clampKillFxMode(mode)]; }
    public static int clampKillFxMode(int mode) { return clampIndex(mode, KILL_FX_NAMES.length); }

    public static float clampTotemScale(float v) { return clampFloat(v, 0.40f, 1.35f, 0.78f); }
    public static float clampGlintIntensity(float v) { return clampFloat(v, 0.25f, 1.35f, 0.85f); }

    public static int handAuraCount() { return HAND_AURA_NAMES.length; }
    public static String handAuraName(int mode) { return HAND_AURA_NAMES[clampHandAuraMode(mode)]; }
    public static int clampHandAuraMode(int mode) { return clampIndex(mode, HAND_AURA_NAMES.length); }

    public static int slashArcCount() { return SLASH_ARC_NAMES.length; }
    public static String slashArcName(int mode) { return SLASH_ARC_NAMES[clampSlashArcMode(mode)]; }
    public static int clampSlashArcMode(int mode) { return clampIndex(mode, SLASH_ARC_NAMES.length); }

    public static int emoteCount() { return EMOTE_NAMES.length; }
    public static String emoteName(int mode) { return EMOTE_NAMES[clampEmoteMode(mode)]; }
    public static int clampEmoteMode(int mode) { return clampIndex(mode, EMOTE_NAMES.length); }

    public static int previewPoseCount() { return PREVIEW_POSE_NAMES.length; }
    public static String previewPoseName(int pose) { return PREVIEW_POSE_NAMES[clampPreviewPose(pose)]; }
    public static int clampPreviewPose(int pose) { return clampIndex(pose, PREVIEW_POSE_NAMES.length); }

    private static int clampIndex(int idx, int length) {
        return (idx < 0 || idx >= length) ? 0 : idx;
    }

    private static float clampFloat(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static String normalizeKey(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
