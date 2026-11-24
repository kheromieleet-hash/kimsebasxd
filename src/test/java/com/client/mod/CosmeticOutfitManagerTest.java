package com.client.mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class CosmeticOutfitManagerTest {

    public static void main(String[] args) throws Exception {
        testShareCodeRoundTripAndValidation();
        testSaveListLoadAndDelete();
        System.out.println("ALL CosmeticOutfitManagerTest ASSERTIONS PASSED!");
    }

    private static void testShareCodeRoundTripAndValidation() {
        ModConfig cfg = new ModConfig();
        cfg.wingsEnabled = true;
        cfg.wingsMode = WingsLogic.MODE_SESTIA_LEAF;
        cfg.wingsColorPreset = WingsLogic.COLOR_EMERALD;
        cfg.cosmeticCloakEnabled = true;
        cfg.cosmeticCloakMode = CosmeticsLogic.CLOAK_SESTIA_LEAF;
        cfg.cosmeticHatEnabled = true;
        cfg.cosmeticHatMode = CosmeticsLogic.HAT_ROYAL_CROWN;
        cfg.cosmeticHatScale = 1.25f;
        cfg.cosmeticPetEnabled = true;
        cfg.cosmeticPetMode = CosmeticsLogic.PET_MINI_FOX;
        cfg.cosmeticPetSide = CosmeticsLogic.PET_SIDE_ORBIT;
        cfg.cosmeticAuraEnabled = true;
        cfg.cosmeticAuraMode = CosmeticsLogic.AURA_LEAF_ORBIT;
        cfg.cosmeticGlintEnabled = true;

        CosmeticsLogic.CosmeticLoadout original = CosmeticsLogic.fromConfig(cfg);
        String code = CosmeticOutfitManager.exportShareCode("Emerald Knight", original);
        assert code.startsWith(CosmeticOutfitManager.SHARE_PREFIX);

        CosmeticOutfitManager.OutfitEnvelope decoded = CosmeticOutfitManager.parseShareCode(code);
        assert "Emerald Knight".equals(decoded.name());
        assert decoded.loadout().wings().enabled()
                && decoded.loadout().wings().mode() == WingsLogic.MODE_SESTIA_LEAF;
        assert decoded.loadout().cloakEnabled()
                && decoded.loadout().cloakMode() == CosmeticsLogic.CLOAK_SESTIA_LEAF;
        assert decoded.loadout().hatEnabled()
                && decoded.loadout().hatMode() == CosmeticsLogic.HAT_ROYAL_CROWN
                && Math.abs(decoded.loadout().hatScale() - 1.25f) < 0.01f;
        assert decoded.loadout().petEnabled()
                && decoded.loadout().petSide() == CosmeticsLogic.PET_SIDE_ORBIT;
        assert decoded.loadout().auraEnabled()
                && decoded.loadout().auraMode() == CosmeticsLogic.AURA_LEAF_ORBIT;
        assert decoded.loadout().glintEnabled();

        boolean threwInvalidPrefix = false;
        try {
            CosmeticOutfitManager.parseShareCode("S2:invalid");
        } catch (IllegalArgumentException e) {
            threwInvalidPrefix = true;
        }
        assert threwInvalidPrefix : "SC1 parser must reject non-SC1 codes";

        boolean threwCorrupt = false;
        try {
            CosmeticOutfitManager.parseShareCode("SC1:!!!notbase64!!!");
        } catch (IllegalArgumentException e) {
            threwCorrupt = true;
        }
        assert threwCorrupt : "SC1 parser must reject corrupt payloads";
    }

    private static void testSaveListLoadAndDelete() throws Exception {
        Path tmp = Files.createTempDirectory("sestia_outfit_test_");
        try {
            ModConfig cfg = new ModConfig();
            cfg.cosmeticHatEnabled = true;
            cfg.cosmeticHatMode = CosmeticsLogic.HAT_CYBER_EARS;
            cfg.cosmeticGearEnabled = true;
            cfg.cosmeticGearMode = CosmeticsLogic.GEAR_TECH_PACK;

            Path saved = CosmeticOutfitManager.saveOutfit(tmp, "Cyber Runner", CosmeticsLogic.fromConfig(cfg));
            assert Files.exists(saved);

            List<CosmeticOutfitManager.OutfitSummary> list = CosmeticOutfitManager.listOutfits(tmp);
            assert list.size() == 1;
            CosmeticOutfitManager.OutfitSummary summary = list.get(0);
            assert "Cyber Runner".equals(summary.name());
            assert summary.activeSlots() == 2;
            assert summary.loadout().gearEnabled()
                    && summary.loadout().gearMode() == CosmeticsLogic.GEAR_TECH_PACK;

            assert CosmeticOutfitManager.deleteOutfit(saved);
            assert CosmeticOutfitManager.listOutfits(tmp).isEmpty();
        } finally {
            try (Stream<Path> walk = Files.walk(tmp)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                });
            }
        }
    }
}
