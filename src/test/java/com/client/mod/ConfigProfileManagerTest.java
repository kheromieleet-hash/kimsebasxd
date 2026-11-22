package com.client.mod;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public class ConfigProfileManagerTest {

    private static final class StubInventory implements ModernModuleSettings.InventoryBridge {
        boolean active = true;
        boolean armor = true;
        boolean potions = true;
        boolean mini = false;
        boolean vert = false;
        int alpha = 60;

        @Override public boolean isActive() { return active; }
        @Override public void setActive(boolean value) { active = value; }
        @Override public boolean isArmor() { return armor; }
        @Override public void setArmor(boolean value) { armor = value; }
        @Override public boolean isPotions() { return potions; }
        @Override public void setPotions(boolean value) { potions = value; }
        @Override public boolean isMini() { return mini; }
        @Override public void setMini(boolean value) { mini = value; }
        @Override public boolean isVert() { return vert; }
        @Override public void setVert(boolean value) { vert = value; }
        @Override public int getBgAlpha() { return alpha; }
        @Override public void setBgAlpha(int value) { alpha = value; }
    }

    public static void main(String[] args) throws Exception {
        testSlugSanitization();
        testJsonAndShareCodeRoundTrip();
        testBareConfigJsonImportAndUrlPreservation();
        testShareCodeOnlyImport();
        testShortDeltaCompressionAndClearedCollections();
        testLocalSaveListLoadAndDelete();
        testRejectsCorruptPayloads();
        System.out.println("ALL ConfigProfileManagerTest ASSERTIONS PASSED!");
    }

    private static void testSlugSanitization() {
        assert "my-pvp".equals(ConfigProfileManager.sanitizeFileSlug("../../My PvP!"));
        assert "profile".equals(ConfigProfileManager.sanitizeFileSlug("   ///   "));
        assert "crystal_26".equals(ConfigProfileManager.sanitizeFileSlug("Crystal_26"));
    }

    private static void testJsonAndShareCodeRoundTrip() {
        ModConfig source = new ModConfig();
        source.fullbright = false;
        source.fullbrightForceMaxLight = true;
        source.zoomFactor = 6.5f;
        source.fastBreak = true;
        source.fastBreakSpeed = 1.45f;
        source.backtrack = true;
        source.backtrackDelay = 180;
        source.chatInfinite = false;
        source.vmEnabled = true;
        source.vmAirWalker = true;
        source.itemScale = 0.65f;
        source.playerScale = 1.40f;
        source.vmOffsetX = 0.45f;
        source.vmScaleY = 1.75f;
        source.vmRotZ = -35.0f;
        source.vmSwingSpeed = 12;
        source.moduleKeybinds.put("Fullbright", 70);
        source.moduleKeybindIsMouse.put("Fullbright", false);
        source.macros.add(new ModConfig.MacroEntry("Hub", "/hub", 72, false));

        StubInventory srcInv = new StubInventory();
        srcInv.active = false;
        srcInv.mini = true;
        srcInv.alpha = 25;

        String json = ConfigProfileManager.exportJson("PvP Preset", source, srcInv, 14);
        String shareCode = ConfigProfileManager.exportShareCode("PvP Preset", source, srcInv, 14);
        String legacyCode = ConfigProfileManager.exportLegacyShareCode(ConfigProfileManager.createEnvelope("PvP Preset", source, srcInv, 14));
        System.out.println("PvP ShareCode lengths -> legacy SESTIA1=" + legacyCode.length() + ", short S2=" + shareCode.length());
        assert shareCode.startsWith(ConfigProfileManager.SHARE_PREFIX);
        assert legacyCode.startsWith(ConfigProfileManager.LEGACY_SHARE_PREFIX);
        assert shareCode.length() * 100 < legacyCode.length() * 45
                : "Expected S2 code to be <45% of legacy SESTIA1 length (" + shareCode.length() + " vs " + legacyCode.length() + ")";

        for (String payload : List.of(json, shareCode, legacyCode)) {
            ConfigProfileManager.ProfileEnvelope parsed = ConfigProfileManager.parseImportPayload(payload);
            assert "PvP Preset".equals(parsed.name);

            ModConfig target = new ModConfig();
            target.clientChatUrl = "wss://custom.example/ws";
            StubInventory dstInv = new StubInventory();
            final boolean[] saved = {false};

            ConfigProfileManager.applyProfile(parsed, target, dstInv, () -> saved[0] = true);
            assert saved[0];
            assert !target.fullbright;
            assert target.fullbrightForceMaxLight;
            assert Math.abs(target.zoomFactor - 6.5f) < 0.001f;
            assert target.fastBreak;
            assert target.backtrack && target.backtrackDelay == 180;
            assert !target.chatInfinite;
            assert target.vmEnabled && target.vmAirWalker && Math.abs(target.vmOffsetX - 0.45f) < 0.001f;
            assert Math.abs(target.itemScale - 0.65f) < 0.001f && Math.abs(target.playerScale - 1.40f) < 0.001f;
            assert Math.abs(target.vmScaleY - 1.75f) < 0.001f && Math.abs(target.vmRotZ + 35.0f) < 0.001f;
            assert target.vmSwingSpeed == 12;
            assert target.moduleKeybinds.getOrDefault("Fullbright", -1) == 70;
            assert target.macros.size() == 1 && "/hub".equals(target.macros.get(0).message);
            assert !dstInv.active && dstInv.mini && dstInv.alpha == 25;
            assert "wss://custom.example/ws".equals(target.clientChatUrl)
                    : "Existing custom chat URL must survive importing profile without URL";
        }
    }

    private static void testBareConfigJsonImportAndUrlPreservation() {
        String bareJson = "{\"fullbright\":false,\"pingSpoof\":true,\"pingSpoofDelay\":99999}";
        ConfigProfileManager.ProfileEnvelope parsed = ConfigProfileManager.parseImportPayload(bareJson);
        assert "Imported Config".equals(parsed.name);
        ModConfig target = new ModConfig();
        ConfigProfileManager.applyProfile(parsed, target, null, null);
        assert !target.fullbright;
        assert target.pingSpoof;
        assert target.pingSpoofDelay >= 50 && target.pingSpoofDelay <= 1000
                : "Sanitizer must normalize out-of-range pingSpoofDelay, got " + target.pingSpoofDelay;
    }

    private static void testShareCodeOnlyImport() {
        ModConfig source = new ModConfig();
        source.fullbright = true;
        source.fastBreak = true;
        source.fastBreakSpeed = 1.12f;

        String json = ConfigProfileManager.exportJson("CodeOnly", source, null, 2);
        String shareCode = ConfigProfileManager.exportShareCode("CodeOnly", source, null, 2);

        ConfigProfileManager.ProfileEnvelope parsed = ConfigProfileManager.parseShareCode("  " + shareCode + "  ");
        assert "CodeOnly".equals(parsed.name);
        assert parsed.config.fastBreak;
        assert Math.abs(parsed.config.fastBreakSpeed - 1.12f) < 0.001f;

        for (String nonShareCode : List.of(json, "{}", "", "   ", "S2:", "S2:bad", "SESTIA1:", "SESTIA1:bad")) {
            boolean threw = false;
            try {
                ConfigProfileManager.parseShareCode(nonShareCode);
            } catch (IllegalArgumentException expected) {
                threw = true;
            }
            assert threw : "parseShareCode must reject non-share-code input: " + nonShareCode;
        }
    }

    private static void testShortDeltaCompressionAndClearedCollections() {
        ModConfig defaultCfg = new ModConfig();
        String defaultCode = ConfigProfileManager.exportShareCode("Profile", defaultCfg, null, 0);
        String defaultLegacy = ConfigProfileManager.exportLegacyShareCode(ConfigProfileManager.createEnvelope("Profile", defaultCfg, null, 0));
        System.out.println("Default profile length -> legacy SESTIA1=" + defaultLegacy.length() + ", short S2=" + defaultCode.length());
        assert defaultCode.length() < 45 : "Default profile S2 code should be compact (<45 chars), got " + defaultCode.length();

        ModConfig cleared = new ModConfig();
        cleared.xrayBlocks.clear();
        String clearedCode = ConfigProfileManager.exportShareCode("ClearedXray", cleared, null, 1);
        ConfigProfileManager.ProfileEnvelope parsed = ConfigProfileManager.parseShareCode(clearedCode);
        assert parsed.config.xrayBlocks != null && parsed.config.xrayBlocks.isEmpty()
                : "Cleared default collection must round-trip as empty through delta encoding";
    }

    private static void testLocalSaveListLoadAndDelete() throws Exception {
        Path tempDir = Files.createTempDirectory("sestia_profiles_test_");
        try {
            ModConfig pvp = new ModConfig();
            pvp.antiHunger = true;
            pvp.moduleKeybinds.put("Zoom", 90);

            StubInventory inv = new StubInventory();
            inv.alpha = 40;

            Path savedFile = ConfigProfileManager.saveProfile(tempDir, "Crystal PvP", pvp, inv, 9);
            assert Files.exists(savedFile);
            assert savedFile.getFileName().toString().equals("crystal-pvp.json");

            List<ConfigProfileManager.ProfileSummary> list = ConfigProfileManager.listProfiles(tempDir);
            assert list.size() == 1;
            assert "Crystal PvP".equals(list.get(0).name());
            assert list.get(0).activeCount() == 9;
            assert list.get(0).bindCount() == 1;

            ConfigProfileManager.ProfileEnvelope loaded = ConfigProfileManager.loadProfileFile(savedFile);
            assert loaded.config.antiHunger;
            assert loaded.inventoryHud != null && loaded.inventoryHud.bgAlpha == 40;

            assert ConfigProfileManager.deleteProfile(savedFile);
            assert ConfigProfileManager.listProfiles(tempDir).isEmpty();
        } finally {
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (Exception ignored) {
                    }
                });
            }
        }
    }

    private static void testRejectsCorruptPayloads() {
        for (String bad : List.of("", "   ", "not json", "[]", "S2:", "S2:!!!corrupt!!!", "SESTIA1:", "SESTIA1:!!!corrupt!!!")) {
            boolean threw = false;
            try {
                ConfigProfileManager.parseImportPayload(bad);
            } catch (IllegalArgumentException expected) {
                threw = true;
            }
            assert threw : "Expected IllegalArgumentException for payload: " + bad;
        }
    }
}
