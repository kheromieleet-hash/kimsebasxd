package com.client.mod;

import java.util.List;
import java.util.Map;

/**
 * Verifies P0 critical fixes: SmoothUiAtlas bounds at scale >= 4, profile password/URL isolation,
 * Item Sucker / Better Place / Wheat Farm slider step scales & inline unit parsing,
 * and SestiaChat DM / mention filtering.
 */
public class CriticalSecurityAndFixesTest {

    public static void main(String[] args) {
        assertSmoothUiAtlasFitsScale4();
        assertProfileExportAndImportStripAndPreserveSecrets();
        assertFixedSlidersAndInlineUnitParsing();
        assertChatMentionAndSestiaDmRules();
        System.out.println("ALL CriticalSecurityAndFixesTest ASSERTIONS PASSED!");
    }

    private static void assertSmoothUiAtlasFitsScale4() {
        final int[] pixelCount = {0};
        SmoothUiAtlas.AtlasLayout layout = SmoothUiAtlas.buildLayout(4, (x, y, argb) -> {
            assert x >= 0 && x < SmoothUiAtlas.ATLAS_W
                    : "Atlas X out of bounds at guiScale=4: x=" + x;
            assert y >= 0 && y < SmoothUiAtlas.ATLAS_H
                    : "Atlas Y out of bounds at guiScale=4: y=" + y + " (ATLAS_H=" + SmoothUiAtlas.ATLAS_H + ")";
            pixelCount[0]++;
        });
        assert pixelCount[0] > 0 : "Expected rasterized pixels in SmoothUiAtlas";
        SmoothUiAtlas.Region glow = layout.glow();
        assert glow != null && glow.v() + glow.h() <= SmoothUiAtlas.ATLAS_H
                : "Glow region overflowed ATLAS_H: bottom=" + (glow != null ? glow.v() + glow.h() : -1);
    }

    private static void assertProfileExportAndImportStripAndPreserveSecrets() {
        ModConfig src = new ModConfig();
        src.accountPasswords = new java.util.HashMap<>(Map.of("Steve", "SuperSecret123!", "Alt", "Pass456"));
        src.clientChatUrl = "wss://private.example.invalid/ws";
        src.xrayOpacity = 80;

        ConfigProfileManager.ProfileEnvelope env = ConfigProfileManager.createEnvelope("test-sec", src, null, 3);
        assert env.config.accountPasswords == null || env.config.accountPasswords.isEmpty()
                : "Exported envelope must never include accountPasswords";
        assert env.config.clientChatUrl == null || env.config.clientChatUrl.isEmpty()
                : "Exported envelope must never include private clientChatUrl";

        String code = ConfigProfileManager.exportShareCode("test-sec", src, null, 3);
        ConfigProfileManager.ProfileEnvelope decoded = ConfigProfileManager.parseShareCode(code);
        assert decoded.config.accountPasswords == null || decoded.config.accountPasswords.isEmpty()
                : "Decoded share code leaked passwords";
        assert decoded.config.clientChatUrl == null || decoded.config.clientChatUrl.isEmpty()
                : "Decoded share code leaked clientChatUrl";

        // Malicious payload attempting to inject WS URL and wipe local passwords
        ConfigProfileManager.ProfileEnvelope hostile = new ConfigProfileManager.ProfileEnvelope();
        hostile.config = new ModConfig();
        hostile.config.clientChatUrl = "wss://attacker.invalid/steal";
        hostile.config.accountPasswords = new java.util.HashMap<>(Map.of("Victim", "Fake"));
        hostile.config.xrayOpacity = 42;

        ModConfig local = new ModConfig();
        local.accountPasswords = new java.util.HashMap<>(Map.of("MyMain", "MyRealPass"));
        local.clientChatUrl = "wss://trusted.sestia.example/ws";

        ConfigProfileManager.applyProfile(hostile, local, null, null);
        assert "wss://trusted.sestia.example/ws".equals(local.clientChatUrl)
                : "applyProfile must preserve existing clientChatUrl, got: " + local.clientChatUrl;
        assert "MyRealPass".equals(local.accountPasswords.get("MyMain"))
                && !local.accountPasswords.containsKey("Victim")
                : "applyProfile must preserve local passwords and ignore imported ones";
        assert local.xrayOpacity == 42 : "applyProfile should still apply safe gameplay settings";
    }

    private static void assertFixedSlidersAndInlineUnitParsing() {
        ModConfig cfg = new ModConfig();
        ModernModuleSettings.Actions noOp = new ModernModuleSettings.Actions() {
            @Override public void persist(boolean reloadChunks) {}
            @Override public void openInventoryMove() {}
            @Override public ModernModuleSettings.InventoryBridge inventory() { return null; }
        };

        List<ModernModuleSettings.Control> sucker = ModernModuleSettings.getControls("Item Sucker", cfg, noOp);
        ModernModuleSettings.SliderControl rangeSlider = (ModernModuleSettings.SliderControl) sucker.get(1);
        ModernModuleSettings.SliderControl speedSlider = (ModernModuleSettings.SliderControl) sucker.get(2);

        // Item Sucker Move Speed must sweep full [0.15, 1.00] instead of locking at 0.15
        speedSlider.pctSetter().accept(0.5f);
        assert cfg.itemSuckerSpeed > 0.50f && cfg.itemSuckerSpeed < 0.65f
                : "Expected Item Sucker Move Speed near 0.58 at 50%, got " + cfg.itemSuckerSpeed;
        speedSlider.pctSetter().accept(1.0f);
        assert Math.abs(cfg.itemSuckerSpeed - 1.00f) < 0.01f
                : "Expected Item Sucker Move Speed 1.00 at 100%, got " + cfg.itemSuckerSpeed;

        // Item Sucker Range inline edit with 'm' suffix must parse and clamp
        assert rangeSlider.textCommitter().test("7.5m") : "Range inline committer rejected '7.5m'";
        assert Math.abs(cfg.itemSuckerRange - 7.5f) < 0.01f : "Expected range 7.5, got " + cfg.itemSuckerRange;

        // Better Place formatting and inline 'b' unit parsing
        List<ModernModuleSettings.Control> place = ModernModuleSettings.getControls("Better Place", cfg, noOp);
        ModernModuleSettings.SliderControl placeRange = (ModernModuleSettings.SliderControl) place.get(0);
        placeRange.pctSetter().accept(0.5f);
        assert Math.abs(cfg.betterPlaceRange - 4.0f) < 0.01f : "Expected Better Place range 4.0, got " + cfg.betterPlaceRange;
        assert "4.0b".equals(placeRange.valueLabel().get()) : "Expected '4.0b', got " + placeRange.valueLabel().get();
        assert placeRange.textCommitter().test("5.25b") : "Better Place inline committer rejected '5.25b'";
        assert Math.abs(cfg.betterPlaceRange - 5.25f) < 0.01f;
    }

    private static void assertChatMentionAndSestiaDmRules() {
        assert ChatMentionNotifier.isMentionOrIncomingDm("§8[§cAlex §7-> §cme§8] selam", "Steve")
                : "Incoming SestiaChat DM must trigger alert";
        assert !ChatMentionNotifier.isMentionOrIncomingDm("§8[§cme §7-> §cAlex§8] selam", "Steve")
                : "Outgoing SestiaChat DM must not alert self";
        assert !ChatMentionNotifier.isMentionOrIncomingDm("§8[§cSestiaChat§8] §7Online (§f3§7): §fSteve, Alex, Bob", "Steve")
                : ".c list output must not false-trigger mention alert";
        assert ChatMentionNotifier.isMentionOrIncomingDm("§8[§cSestiaChat§8] §fAlex§7: §rhey Steve!", "Steve")
                : "Mention inside public SestiaChat message must alert";
        assert !ChatMentionNotifier.isMentionOrIncomingDm("§8[§cSestiaChat§8] §fSteve§7: §rhello world", "Steve")
                : "Own SestiaChat message must not alert";
    }
}
