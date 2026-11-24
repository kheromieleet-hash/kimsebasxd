package com.client.mod;

public class CosmeticsLogicTest {

    public static void main(String[] args) {
        testCatalogCountsAndClamping();
        testLoadoutRoundTripAndMatchStrain();
        testCapeSuppressionAndArmorClearance();
        testRemoteCacheAndPanicGating();
        testEmotePoseAndAutoCancel();
        System.out.println("ALL CosmeticsLogicTest ASSERTIONS PASSED!");
    }

    private static void testCatalogCountsAndClamping() {
        assert CosmeticsLogic.cloakCount() == 4;
        assert CosmeticsLogic.hatCount() == 6;
        assert CosmeticsLogic.petCount() == 5;
        assert CosmeticsLogic.petSideCount() == 3;
        assert CosmeticsLogic.gearCount() == 5;
        assert CosmeticsLogic.auraCount() == 4;
        assert CosmeticsLogic.trailCount() == 4;
        assert CosmeticsLogic.hitFxCount() == 3;
        assert CosmeticsLogic.killFxCount() == 3;
        assert CosmeticsLogic.handAuraCount() == 2;
        assert CosmeticsLogic.slashArcCount() == 2;
        assert CosmeticsLogic.emoteCount() == 5;
        assert CosmeticsLogic.previewPoseCount() == 5;

        assert CosmeticsLogic.clampCloakMode(-1) == 0;
        assert CosmeticsLogic.clampHatMode(99) == 0;
        assert CosmeticsLogic.clampHatScale(Float.NaN) == 1.0f;
        assert Math.abs(CosmeticsLogic.clampHatScale(0.1f) - 0.70f) < 0.001f;
        assert Math.abs(CosmeticsLogic.clampHatScale(5.0f) - 1.45f) < 0.001f;
        assert Math.abs(CosmeticsLogic.clampAuraRadius(0.1f) - 0.55f) < 0.001f;
        assert Math.abs(CosmeticsLogic.clampAuraRadius(9.0f) - 1.65f) < 0.001f;
        assert Math.abs(CosmeticsLogic.clampAuraSpeed(0.01f) - 0.20f) < 0.001f;
        assert Math.abs(CosmeticsLogic.clampTotemScale(0.1f) - 0.40f) < 0.001f;
        assert Math.abs(CosmeticsLogic.clampGlintIntensity(9.0f) - 1.35f) < 0.001f;

        for (int i = 0; i < CosmeticsLogic.hatCount(); i++) {
            assert !CosmeticsLogic.hatName(i).isBlank() && !CosmeticsLogic.hatTag(i).isBlank();
        }
        for (int i = 0; i < CosmeticsLogic.petCount(); i++) {
            assert !CosmeticsLogic.petName(i).isBlank() && !CosmeticsLogic.petTag(i).isBlank();
        }
        for (int i = 0; i < CosmeticsLogic.gearCount(); i++) {
            assert !CosmeticsLogic.gearName(i).isBlank() && !CosmeticsLogic.gearTag(i).isBlank();
        }
    }

    private static void testLoadoutRoundTripAndMatchStrain() {
        ModConfig cfg = new ModConfig();
        CosmeticsLogic.matchStrainTheme(cfg, 0xF43F5E);
        assert cfg.wingsColorPreset == WingsLogic.COLOR_CUSTOM && cfg.wingsCustomRgb == 0xF43F5E;
        assert cfg.cosmeticCloakColorPreset == WingsLogic.COLOR_CUSTOM && cfg.cosmeticCloakCustomRgb == 0xF43F5E;
        assert cfg.cosmeticHatCustomRgb == 0xF43F5E;
        assert cfg.cosmeticGlintCustomRgb == 0xF43F5E;
        assert cfg.cosmeticAuraCustomRgb == 0xF43F5E;

        cfg.wingsEnabled = true;
        cfg.cosmeticCloakEnabled = true;
        cfg.cosmeticHatEnabled = true;
        cfg.cosmeticPetEnabled = true;
        cfg.cosmeticGearEnabled = true;
        cfg.cosmeticAuraEnabled = true;
        cfg.cosmeticTrailEnabled = true;
        cfg.cosmeticGlintEnabled = true;
        cfg.cosmeticSlashArcEnabled = true;

        CosmeticsLogic.CosmeticLoadout loadout = CosmeticsLogic.fromConfig(cfg);
        assert loadout.activeSlotCount() == 9;

        ModConfig target = new ModConfig();
        CosmeticsLogic.applyLoadoutToConfig(loadout, target);
        assert target.wingsEnabled && target.cosmeticCloakEnabled && target.cosmeticHatEnabled;
        assert target.cosmeticPetEnabled && target.cosmeticGearEnabled && target.cosmeticAuraEnabled;
        assert target.cosmeticTrailEnabled && target.cosmeticGlintEnabled && target.cosmeticSlashArcEnabled;
        assert target.cosmeticHatCustomRgb == 0xF43F5E;
    }

    private static void testCapeSuppressionAndArmorClearance() {
        ModConfig cfg = new ModConfig();
        cfg.cosmeticCloakEnabled = true;
        assert CosmeticsLogic.shouldSuppressVanillaCape(cfg, false, false, true, false, null);
        // Wearing Elytra or Panic active must NOT suppress vanilla render handling
        assert !CosmeticsLogic.shouldSuppressVanillaCape(cfg, false, false, true, true, null);
        assert !CosmeticsLogic.shouldSuppressVanillaCape(cfg, true, false, true, false, null);

        assert CosmeticsLogic.computeHelmetClearanceY(true) < CosmeticsLogic.computeHelmetClearanceY(false);
        assert CosmeticsLogic.computeCloakBackZ(true) > CosmeticsLogic.computeCloakBackZ(false);
        assert CosmeticsLogic.computeGearBackZ(true, true) > CosmeticsLogic.computeGearBackZ(true, false);
    }

    private static void testRemoteCacheAndPanicGating() {
        CosmeticsLogic.clearRemoteState();
        ModConfig cfg = new ModConfig();
        cfg.cosmeticShowFriends = true;
        cfg.cosmeticHatEnabled = true;

        assert CosmeticsLogic.resolveRenderLoadout(cfg, true, false, false, true, "Me") == null;
        assert CosmeticsLogic.resolveRenderLoadout(cfg, false, true, false, true, "Me") == null;
        assert CosmeticsLogic.resolveRenderLoadout(cfg, false, false, true, true, "Me") == null;
        assert CosmeticsLogic.resolveRenderLoadout(cfg, false, false, false, true, "Me").hatEnabled();

        assert CosmeticsLogic.resolveRenderLoadout(cfg, false, false, false, false, "Stranger") == null;

        // Build explicit friend loadout
        ModConfig fCfg = new ModConfig();
        fCfg.cosmeticAuraEnabled = true;
        fCfg.cosmeticAuraMode = CosmeticsLogic.AURA_LEAF_ORBIT;
        CosmeticsLogic.putRemoteLoadout("Friend_1", CosmeticsLogic.fromConfig(fCfg));

        assert CosmeticsLogic.isOnlineSestiaUser("friend_1");
        assert CosmeticsLogic.resolveRenderLoadout(cfg, false, false, false, false, "Friend_1") != null;

        cfg.cosmeticShowFriends = false;
        assert CosmeticsLogic.resolveRenderLoadout(cfg, false, false, false, false, "Friend_1") == null;

        CosmeticsLogic.removeRemotePlayer("Friend_1");
        assert !CosmeticsLogic.isOnlineSestiaUser("Friend_1");
        CosmeticsLogic.clearRemoteState();
    }

    private static void testEmotePoseAndAutoCancel() {
        for (int e = 0; e < CosmeticsLogic.emoteCount(); e++) {
            CosmeticsLogic.EmotePose p = CosmeticsLogic.computeEmotePose(e, 15.0f);
            assert Float.isFinite(p.leftArmX()) && Float.isFinite(p.rightArmX()) && Float.isFinite(p.bodyOffsetY());
            assert p != CosmeticsLogic.EmotePose.NEUTRAL;
        }
        assert CosmeticsLogic.computeEmotePose(CosmeticsLogic.EMOTE_NONE, 10.0f) == CosmeticsLogic.EmotePose.NEUTRAL;

        assert !CosmeticsLogic.shouldCancelLocalEmote(false, false, false, false, false, false, false);
        assert CosmeticsLogic.shouldCancelLocalEmote(true, false, false, false, false, false, false);
        assert CosmeticsLogic.shouldCancelLocalEmote(false, false, true, false, false, false, false);
        assert CosmeticsLogic.shouldCancelLocalEmote(false, false, false, false, false, true, false);
        assert CosmeticsLogic.shouldCancelLocalEmote(false, false, false, false, false, false, true);

        long now = 100_000L;
        CosmeticsLogic.putRemoteEmote("Buddy", CosmeticsLogic.EMOTE_SALUTE, now);
        assert CosmeticsLogic.resolveActiveEmote(false, false, "buddy", now + 2_000L) == CosmeticsLogic.EMOTE_SALUTE;
        assert CosmeticsLogic.resolveActiveEmote(false, false, "buddy", now + CosmeticsLogic.REMOTE_EMOTE_TTL_MS + 50L)
                == CosmeticsLogic.EMOTE_NONE;
        CosmeticsLogic.clearRemoteState();
    }
}
