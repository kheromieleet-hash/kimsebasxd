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
        source.fullbrightMode = 1;
        source.fullbrightBrightness = 0.85f;
        source.fastBreak = true;
        source.fastBreakSpeed = 1.45f;
        source.backtrack = true;
        source.backtrackDelay = 180;
        source.chatInfinite = false;
        source.moduleKeybinds.put("Fullbright", 70);
        source.moduleKeybindIsMouse.put("Fullbright", false);
        source.macros.add(new ModConfig.MacroEntry("Hub", "/hub", 72, false));

        StubInventory srcInv = new StubInventory();
        srcInv.active = false;
        srcInv.mini = true;
        srcInv.alpha = 25;

        String json = ConfigProfileManager.exportJson("PvP Preset", source, srcInv, 14);
        String shareCode = ConfigProfileManager.exportShareCode("PvP Preset", source, srcInv, 14);
        assert shareCode.startsWith(ConfigProfileManager.SHARE_PREFIX);

        for (String payload : List.of(json, shareCode)) {
            ConfigProfileManager.ProfileEnvelope parsed = ConfigProfileManager.parseImportPayload(payload);
            assert "PvP Preset".equals(parsed.name);

            ModConfig target = new ModConfig();
            target.clientChatUrl = "wss://custom.example/ws";
            StubInventory dstInv = new StubInventory();
            final boolean[] saved = {false};

            ConfigProfileManager.applyProfile(parsed, target, dstInv, () -> saved[0] = true);
            assert saved[0];
            assert !target.fullbright;
            assert target.fullbrightMode == 1;
            assert Math.abs(target.fullbrightBrightness - 0.85f) < 0.001f;
            assert target.fastBreak;
            assert target.backtrack && target.backtrackDelay == 180;
            assert !target.chatInfinite;
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
        for (String bad : List.of("", "   ", "not json", "[]", "SESTIA1:", "SESTIA1:!!!corrupt!!!")) {
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
