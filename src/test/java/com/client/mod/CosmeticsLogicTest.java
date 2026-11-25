package com.client.mod;

public class CosmeticsLogicTest {

    public static void main(String[] args) {
        testCatalogCountsAndClamping();
        testLoadoutRoundTripAndMatchStrain();
        testCapeSuppressionAndArmorClearance();
        testRemoteCacheAndPanicGating();
        testEmotePoseAndAutoCancel();
        testGradientLocksAutoOutfitWheelAndCustomCape();
        System.out.println("ALL CosmeticsLogicTest ASSERTIONS PASSED!");
    }

    private static void testCatalogCountsAndClamping() {
        assert CosmeticsLogic.cloakCount() == 5;
        assert CosmeticsLogic.hatCount() == 7;
        assert CosmeticsLogic.petCount() == 8;
        assert CosmeticsLogic.petSideCount() == 3;
        assert CosmeticsLogic.gearCount() == 5;
        assert CosmeticsLogic.auraCount() == 4;
        assert CosmeticsLogic.trailCount() == 4;
        assert CosmeticsLogic.hitFxCount() == 3;
        assert CosmeticsLogic.killFxCount() == 3;
        assert CosmeticsLogic.handAuraCount() == 2;
        assert CosmeticsLogic.slashArcCount() == 2;
        assert CosmeticsLogic.emoteCount() == 11;
        assert CosmeticsLogic.gradientCount() == 4;
        assert CosmeticsLogic.previewPoseCount() == 5;

        assert CosmeticsLogic.clampCloakMode(-1) == 0;
        assert CosmeticsLogic.clampCloakMode(4) == 4;
        assert CosmeticsLogic.clampCloakMode(5) == 0;
        assert "Custom PNG".equals(CosmeticsLogic.cloakName(CosmeticsLogic.CLOAK_CUSTOM_PNG));
        assert CosmeticsLogic.clampHatMode(-1) == 0;
        assert CosmeticsLogic.clampHatMode(6) == 6;
        assert CosmeticsLogic.clampHatMode(7) == 0;
        assert CosmeticsLogic.clampHatMode(99) == 0;
        assert "Cat Ears".equals(CosmeticsLogic.hatName(CosmeticsLogic.HAT_CAT_EARS));
        assert "Plush Twitch".equals(CosmeticsLogic.hatTag(CosmeticsLogic.HAT_CAT_EARS));
        assert CosmeticsLogic.clampPetMode(-1) == 0;
        assert CosmeticsLogic.clampPetMode(7) == 7;
        assert CosmeticsLogic.clampPetMode(8) == 0;
        assert "Baby Dragon".equals(CosmeticsLogic.petName(CosmeticsLogic.PET_BABY_DRAGON));
        assert "Ember Wing".equals(CosmeticsLogic.petTag(CosmeticsLogic.PET_BABY_DRAGON));
        assert "Mini Cat".equals(CosmeticsLogic.petName(CosmeticsLogic.PET_MINI_CAT));
        assert "Purr Buddy".equals(CosmeticsLogic.petTag(CosmeticsLogic.PET_MINI_CAT));
        assert "Arcane Owl".equals(CosmeticsLogic.petName(CosmeticsLogic.PET_PHANTOM_OWL));
        assert "Mystic Glide".equals(CosmeticsLogic.petTag(CosmeticsLogic.PET_PHANTOM_OWL));
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
        assert CosmeticsLogic.computeCloakBackZ(false) >= 0.17f;
        assert CosmeticsLogic.computeCloakBackZ(true) >= 0.25f;
        assert CosmeticsLogic.computeCloakBackZ(true) > CosmeticsLogic.computeCloakBackZ(false);
        assert CosmeticsLogic.computeCloakTopY(true) < CosmeticsLogic.computeCloakTopY(false);
        assert CosmeticsLogic.computeCloakLeanRad(0f, -15f, 0f, false) >= 0.20f;
        assert CosmeticsLogic.computeCloakLeanRad(0f, 0f, 0.85f, false)
                > CosmeticsLogic.computeCloakLeanRad(0f, 0f, 0f, false) + 0.25f;
        float crouchLean = CosmeticsLogic.computeCloakLeanRad(Float.NaN, Float.NEGATIVE_INFINITY, Float.NaN, true);
        assert Float.isFinite(crouchLean) && crouchLean >= 0.35f;
        assert CosmeticsLogic.computeGearBackZ(true, true) > CosmeticsLogic.computeCloakBackZ(true);
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

    private static void testGradientLocksAutoOutfitWheelAndCustomCape() {
        // 2-Color Gradient & Harmony
        assert CosmeticsLogic.resolveGradientRgb(0x34D399, 0x00D4FF, CosmeticsLogic.GRADIENT_SOLID, 10.0f, 1.0f) == 0x34D399;
        assert CosmeticsLogic.resolveGradientRgb(0x34D399, 0x00D4FF, CosmeticsLogic.GRADIENT_TIP_TO_BASE, 10.0f, 0.0f) == 0x34D399;
        assert CosmeticsLogic.resolveGradientRgb(0x34D399, 0x00D4FF, CosmeticsLogic.GRADIENT_TIP_TO_BASE, 10.0f, 1.0f) == 0x00D4FF;
        int waveA = CosmeticsLogic.resolveGradientRgb(0x34D399, 0xF43F5E, CosmeticsLogic.GRADIENT_WAVE, 0.0f, 0.25f);
        int waveB = CosmeticsLogic.resolveGradientRgb(0x34D399, 0xF43F5E, CosmeticsLogic.GRADIENT_WAVE, 12.0f, 0.25f);
        assert waveA != waveB;

        ModConfig cfg = new ModConfig();
        CosmeticsLogic.applyHarmonyTheme(cfg, 0x34D399);
        assert cfg.cosmeticGradientMode == CosmeticsLogic.GRADIENT_TIP_TO_BASE;
        assert cfg.cosmeticGradientSecondaryRgb == 0x00D4FF;

        // Slot Lock preserves locked slots during outfit equip
        cfg.cosmeticPetEnabled = true;
        cfg.cosmeticPetMode = CosmeticsLogic.PET_BABY_DRAGON;
        cfg.cosmeticHatEnabled = false;
        int lockMask = CosmeticsLogic.toggleSlotLock(0, 3); // Lock Pet
        assert CosmeticsLogic.isSlotLocked(lockMask, 3);
        ModConfig incomingCfg = new ModConfig();
        incomingCfg.cosmeticPetEnabled = false;
        incomingCfg.cosmeticPetMode = CosmeticsLogic.PET_MINI_BEE;
        incomingCfg.cosmeticHatEnabled = true;
        incomingCfg.cosmeticHatMode = CosmeticsLogic.HAT_CAT_EARS;
        CosmeticsLogic.applyLoadoutToConfig(CosmeticsLogic.fromConfig(incomingCfg), cfg, lockMask);
        assert cfg.cosmeticPetEnabled && cfg.cosmeticPetMode == CosmeticsLogic.PET_BABY_DRAGON : "Locked pet must not be overwritten";
        assert cfg.cosmeticHatEnabled && cfg.cosmeticHatMode == CosmeticsLogic.HAT_CAT_EARS : "Unlocked hat must update";

        // Smart Auto-Outfit Priority: PvP > Elytra > Mining > Night > null
        assert "PvPSet".equals(CosmeticsLogic.resolveAutoOutfitTarget(true, true, true, true, true, "PvPSet", "ElytraSet", "MineSet", "NightSet"));
        assert "ElytraSet".equals(CosmeticsLogic.resolveAutoOutfitTarget(true, false, true, true, true, "PvPSet", "ElytraSet", "MineSet", "NightSet"));
        assert "MineSet".equals(CosmeticsLogic.resolveAutoOutfitTarget(true, false, false, true, true, "PvPSet", "ElytraSet", "MineSet", "NightSet"));
        assert "NightSet".equals(CosmeticsLogic.resolveAutoOutfitTarget(true, false, false, false, true, "PvPSet", "ElytraSet", "MineSet", "NightSet"));
        assert CosmeticsLogic.resolveAutoOutfitTarget(false, true, true, true, true, "PvPSet", "ElytraSet", "MineSet", "NightSet") == null;

        // Kinetic Backflip & Partner/Prop Emotes
        assert CosmeticsLogic.isPartnerEmote(CosmeticsLogic.EMOTE_HIGH_FIVE);
        assert CosmeticsLogic.isPartnerEmote(CosmeticsLogic.EMOTE_FIST_BUMP);
        assert !CosmeticsLogic.isPartnerEmote(CosmeticsLogic.EMOTE_SIT);
        assert CosmeticsLogic.hasEmoteProp(CosmeticsLogic.EMOTE_RUNE_MEDITATE);
        assert CosmeticsLogic.computeEmoteBodyPitchDeg(CosmeticsLogic.EMOTE_BACKFLIP, 11.0f) < -100.0f;
        assert CosmeticsLogic.computeEmoteBodyLiftY(CosmeticsLogic.EMOTE_BACKFLIP, 11.0f) > 0.5f;
        assert CosmeticsLogic.computeEmoteBodyPitchDeg(CosmeticsLogic.EMOTE_SIT, 11.0f) == 0.0f;

        // Quick Emote Radial Wheel wedge math + zero-overlap card/hub geometry proof
        assert CosmeticWheelController.resolveWedgeIndex(0.0, 0.0, 18.0, 8) == -1;
        assert CosmeticWheelController.resolveWedgeIndex(0.0, -50.0, 18.0, 8) == 0; // 12 o'clock
        assert CosmeticWheelController.resolveWedgeIndex(50.0, 0.0, 18.0, 8) == 2;  // 3 o'clock
        assert CosmeticWheelController.resolveWedgeIndex(0.0, 50.0, 18.0, 8) == 4;  // 6 o'clock
        assert CosmeticWheelController.resolveWedgeIndex(-50.0, 0.0, 18.0, 8) == 6; // 9 o'clock
        int hw = CosmeticWheelController.CARD_W / 2;
        int hh = CosmeticWheelController.CARD_H / 2;
        int hubHw = CosmeticWheelController.HUB_W / 2;
        int hubHh = CosmeticWheelController.HUB_H / 2;
        for (int i = 0; i < CosmeticWheelController.SLICE_COUNT; i++) {
            int ix = CosmeticWheelController.computeSliceOffsetX(i, 1.0f);
            int iy = CosmeticWheelController.computeSliceOffsetY(i, 1.0f);
            boolean overlapsHub = (Math.abs(ix) < hw + hubHw) && (Math.abs(iy) < hh + hubHh);
            assert !overlapsHub : "Wheel slice " + i + " overlaps center hub";
            for (int j = i + 1; j < CosmeticWheelController.SLICE_COUNT; j++) {
                int jx = CosmeticWheelController.computeSliceOffsetX(j, 1.0f);
                int jy = CosmeticWheelController.computeSliceOffsetY(j, 1.0f);
                boolean overlapsPeer = (Math.abs(ix - jx) < CosmeticWheelController.CARD_W)
                        && (Math.abs(iy - jy) < CosmeticWheelController.CARD_H);
                assert !overlapsPeer : "Wheel slices " + i + " and " + j + " overlap";
            }
        }

        // Custom PNG cape filename validation (blocks path traversal)
        assert CustomCapeManager.isValidCapeFileName("sestia_cape.png");
        assert !CustomCapeManager.isValidCapeFileName("../secret.png");
        assert !CustomCapeManager.isValidCapeFileName("sub/cape.png");
        assert !CustomCapeManager.isValidCapeFileName("cape.jpg");
        assert !CustomCapeManager.isValidCapeFileName(".png");

        // Protocol round-trip of 2-color gradient + new hat/pet/cloak indices
        CosmeticsLogic.applyHarmonyTheme(cfg, 0x34D399);
        CosmeticsLogic.CosmeticLoadout rtIn = CosmeticsLogic.fromConfig(cfg);
        CosmeticsLogic.CosmeticLoadout rtOut = SestiaChatProtocol.parseCosmeticsV2(SestiaChatProtocol.serializeCosmeticsV2(rtIn));
        assert rtOut != null && rtOut.gradientMode() == CosmeticsLogic.GRADIENT_TIP_TO_BASE
                && rtOut.gradientSecondaryRgb() == 0x00D4FF;
    }
}
