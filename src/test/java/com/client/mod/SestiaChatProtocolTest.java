package com.client.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

/**
 * Pure-Java verification for SestiaChatProtocol command parsing, URL resolver,
 * sanitization, mute list manipulation, JSON serialization, and reconnect backoff.
 */
public class SestiaChatProtocolTest {

    public static void main(String[] args) {
        testCommandDetectionAndPassThrough();
        testCommandParsingVariants();
        testUrlNormalizationAndConnectUri();
        testNickAndTextSanitization();
        testJsonRoundTrip();
        testDmFormatting();
        testMuteListOperations();
        testBackoffBounds();
        System.out.println("ALL SestiaChatProtocolTest ASSERTIONS PASSED!");
    }

    private static void testCommandDetectionAndPassThrough() {
        assert SestiaChatProtocol.isClientChatCommand(".c");
        assert SestiaChatProtocol.isClientChatCommand("  .C  selam");
        assert !SestiaChatProtocol.isClientChatCommand(".cobble");
        assert !SestiaChatProtocol.isClientChatCommand("/msg Steve hi");
        assert !SestiaChatProtocol.isClientChatCommand("normal sohbet");
        assert !SestiaChatProtocol.isClientChatCommand(null);
    }

    private static void testCommandParsingVariants() {
        assert SestiaChatProtocol.parseOutgoingCommand(".c").kind() == SestiaChatProtocol.CommandKind.HELP;
        assert SestiaChatProtocol.parseOutgoingCommand(".c help").kind() == SestiaChatProtocol.CommandKind.HELP;
        assert SestiaChatProtocol.parseOutgoingCommand(".c ?").kind() == SestiaChatProtocol.CommandKind.HELP;
        assert SestiaChatProtocol.parseOutgoingCommand(".c list").kind() == SestiaChatProtocol.CommandKind.LIST;
        assert SestiaChatProtocol.parseOutgoingCommand(".cobble").kind() == SestiaChatProtocol.CommandKind.NONE;

        // Removed commands (.c status, .c url, .c mute, .c unmute) are no longer subcommands and fall back to global chat
        assert SestiaChatProtocol.parseOutgoingCommand(".c status").kind() == SestiaChatProtocol.CommandKind.CHAT;
        assert SestiaChatProtocol.parseOutgoingCommand(".c url https://chat.example.workers.dev").kind() == SestiaChatProtocol.CommandKind.CHAT;
        assert SestiaChatProtocol.parseOutgoingCommand(".c mute Griefer_01").kind() == SestiaChatProtocol.CommandKind.CHAT;
        assert SestiaChatProtocol.parseOutgoingCommand(".c unmute Griefer_01").kind() == SestiaChatProtocol.CommandKind.CHAT;

        SestiaChatProtocol.ParsedCommand dmCmd = SestiaChatProtocol.parseOutgoingCommand(".c msg Ali selam nasilsin?");
        assert dmCmd.kind() == SestiaChatProtocol.CommandKind.DM;
        assert "Ali".equals(dmCmd.arg1()) && "selam nasilsin?".equals(dmCmd.arg2());

        assert SestiaChatProtocol.parseOutgoingCommand(".c msg Ali").kind() == SestiaChatProtocol.CommandKind.HELP;

        SestiaChatProtocol.ParsedCommand chatCmd = SestiaChatProtocol.parseOutgoingCommand(".c herkese selam!");
        assert chatCmd.kind() == SestiaChatProtocol.CommandKind.CHAT;
        assert "herkese selam!".equals(chatCmd.arg1());
    }

    private static void testUrlNormalizationAndConnectUri() {
        assert "wss://sestia.example.workers.dev".equals(
                SestiaChatProtocol.normalizeWebSocketUrl("https://sestia.example.workers.dev/"));
        assert "ws://localhost:8787".equals(
                SestiaChatProtocol.normalizeWebSocketUrl("http://localhost:8787/"));
        assert "wss://chat.sestia.net".equals(
                SestiaChatProtocol.normalizeWebSocketUrl("chat.sestia.net"));
        assert "".equals(SestiaChatProtocol.normalizeWebSocketUrl("ftp://invalid.host"));
        assert "".equals(SestiaChatProtocol.normalizeWebSocketUrl(""));
        assert "".equals(SestiaChatProtocol.normalizeWebSocketUrl(null));

        assert "wss://sestia-client-chat.sestiachat.workers.dev".equals(SestiaChatProtocol.DEFAULT_CHAT_URL);
        assert "wss://sestia-client-chat.sestiachat.workers.dev".equals(SestiaChatProtocol.resolveEffectiveUrl(""));
        assert "wss://default.workers.dev".equals(
                SestiaChatProtocol.resolveEffectiveUrl("", "https://default.workers.dev/"));
        assert "wss://custom.workers.dev".equals(
                SestiaChatProtocol.resolveEffectiveUrl("wss://custom.workers.dev", "https://default.workers.dev/"));

        String uri = SestiaChatProtocol.buildConnectUri("wss://sestia.example.workers.dev", "§cSteve_1");
        assert uri.equals("wss://sestia.example.workers.dev?nick=Steve_1")
                : "Unexpected connect URI: " + uri;
    }

    private static void testNickAndTextSanitization() {
        assert "Steve_123".equals(SestiaChatProtocol.sanitizeNick("§a§lSteve_123!"));
        assert "Player".equals(SestiaChatProtocol.sanitizeNick("§c!"));

        String dirty = "§aMerhaba\nDunya\tTest";
        assert "Merhaba Dunya Test".equals(SestiaChatProtocol.sanitizeText(dirty));

        String longText = "x".repeat(300);
        assert SestiaChatProtocol.sanitizeText(longText).length() == SestiaChatProtocol.MAX_MESSAGE_LENGTH;
    }

    private static void testJsonRoundTrip() {
        JsonObject hello = JsonParser.parseString(
                SestiaChatProtocol.buildHelloJson("§bMehmet_TR")).getAsJsonObject();
        assert "hello".equals(hello.get("type").getAsString());
        assert "Mehmet_TR".equals(hello.get("nick").getAsString());

        JsonObject chat = JsonParser.parseString(SestiaChatProtocol.buildChatJson("§cSa §rBeyler")).getAsJsonObject();
        assert "chat".equals(chat.get("type").getAsString());
        assert "Sa Beyler".equals(chat.get("text").getAsString());

        JsonObject dm = JsonParser.parseString(SestiaChatProtocol.buildDmJson("§eAli", "Geliyor musun?")).getAsJsonObject();
        assert "dm".equals(dm.get("type").getAsString());
        assert "Ali".equals(dm.get("to").getAsString());
        assert "Geliyor musun?".equals(dm.get("text").getAsString());

        SestiaChatProtocol.ServerEvent welcome = SestiaChatProtocol.parseServerEvent(
                "{\"type\":\"welcome\",\"nick\":\"Mehmet\",\"online\":4}");
        assert welcome.type() == SestiaChatProtocol.EventType.WELCOME;
        assert "Mehmet".equals(welcome.from()) && welcome.onlineCount() == 4;

        SestiaChatProtocol.ServerEvent list = SestiaChatProtocol.parseServerEvent(
                "{\"type\":\"list\",\"users\":[\"Mehmet\",\"§cAli\"]}");
        assert list.type() == SestiaChatProtocol.EventType.LIST;
        assert List.of("Mehmet", "Ali").equals(list.users());

        assert SestiaChatProtocol.parseServerEvent("not json").type() == SestiaChatProtocol.EventType.UNKNOWN;

        // Live Wing Sync JSON round-trip (hello, wings event, welcome wingsMap)
        WingsLogic.clearRemoteProfiles();
        WingsLogic.WingProfile wp = new WingsLogic.WingProfile(
                true, WingsLogic.MODE_CYBER, WingsLogic.COLOR_CHROMA,
                1.35f, 0.08f, 6, 0.55f, 0.18f, 1.4f, true);
        JsonObject helloWings = JsonParser.parseString(
                SestiaChatProtocol.buildHelloJson("SestiaKing", wp)).getAsJsonObject();
        assert helloWings.has("wings") && helloWings.getAsJsonObject("wings").get("mode").getAsInt() == WingsLogic.MODE_CYBER;

        String wingsBroadcast = "{\"type\":\"wings\",\"from\":\"Kanka_01\",\"wings\":"
                + SestiaChatProtocol.serializeWingProfile(wp) + "}";
        SestiaChatProtocol.ServerEvent wingsEv = SestiaChatProtocol.parseServerEvent(wingsBroadcast);
        assert wingsEv.type() == SestiaChatProtocol.EventType.WINGS && "Kanka_01".equals(wingsEv.from());
        WingsLogic.WingProfile synced = WingsLogic.getRemoteProfile("kanka_01");
        assert synced != null && synced.enabled()
                && synced.mode() == WingsLogic.MODE_CYBER
                && synced.colorPreset() == WingsLogic.COLOR_CHROMA;

        // Multi-Slot Cosmetics v2 Sync + FX Emote + Leave cleanup
        CosmeticsLogic.clearRemoteState();
        ModConfig cfg = new ModConfig();
        cfg.wingsEnabled = true;
        cfg.wingsMode = WingsLogic.MODE_SESTIA_LEAF;
        cfg.cosmeticCloakEnabled = true;
        cfg.cosmeticCloakMode = CosmeticsLogic.CLOAK_OBSIDIAN_ROYAL;
        cfg.cosmeticHatEnabled = true;
        cfg.cosmeticHatMode = CosmeticsLogic.HAT_LEAF_WREATH;
        cfg.cosmeticPetEnabled = true;
        cfg.cosmeticPetMode = CosmeticsLogic.PET_MINI_ALLAY;
        cfg.cosmeticGearEnabled = true;
        cfg.cosmeticGearMode = CosmeticsLogic.GEAR_KATANA_SHEATH;
        cfg.cosmeticAuraEnabled = true;
        cfg.cosmeticAuraMode = CosmeticsLogic.AURA_DOUBLE_HELIX;
        cfg.cosmeticGlintEnabled = true;
        cfg.cosmeticSlashArcEnabled = true;

        CosmeticsLogic.CosmeticLoadout srcLoadout = CosmeticsLogic.fromConfig(cfg);
        String helloV2 = SestiaChatProtocol.buildHelloJson("SestiaKing", wp, srcLoadout);
        assert helloV2.length() < 1500 : "v2 hello payload must remain compact: " + helloV2.length();
        JsonObject helloV2Obj = JsonParser.parseString(helloV2).getAsJsonObject();
        assert helloV2Obj.has("c") && helloV2Obj.getAsJsonObject("c").get("v").getAsInt() == 2;

        String cosBroadcast = "{\"type\":\"cosmetics\",\"from\":\"Dost_TR\",\"c\":"
                + SestiaChatProtocol.serializeCosmeticsV2(srcLoadout) + "}";
        SestiaChatProtocol.ServerEvent cosEv = SestiaChatProtocol.parseServerEvent(cosBroadcast);
        assert cosEv.type() == SestiaChatProtocol.EventType.COSMETICS && "Dost_TR".equals(cosEv.from());
        CosmeticsLogic.CosmeticLoadout remoteLoadout = CosmeticsLogic.getRemoteLoadout("dost_tr");
        assert remoteLoadout != null
                && remoteLoadout.wings().mode() == WingsLogic.MODE_SESTIA_LEAF
                && remoteLoadout.cloakEnabled() && remoteLoadout.cloakMode() == CosmeticsLogic.CLOAK_OBSIDIAN_ROYAL
                && remoteLoadout.hatEnabled() && remoteLoadout.hatMode() == CosmeticsLogic.HAT_LEAF_WREATH
                && remoteLoadout.petEnabled() && remoteLoadout.petMode() == CosmeticsLogic.PET_MINI_ALLAY
                && remoteLoadout.gearEnabled() && remoteLoadout.gearMode() == CosmeticsLogic.GEAR_KATANA_SHEATH
                && remoteLoadout.auraEnabled() && remoteLoadout.auraMode() == CosmeticsLogic.AURA_DOUBLE_HELIX
                && remoteLoadout.glintEnabled() && remoteLoadout.slashArcEnabled();

        // FX Emote packet
        String emoteJson = SestiaChatProtocol.buildEmoteFxJson(CosmeticsLogic.EMOTE_HOVER_MEDITATE);
        String remoteEmotePacket = "{\"type\":\"fx\",\"from\":\"Dost_TR\",\"k\":\"emote\",\"id\":"
                + CosmeticsLogic.EMOTE_HOVER_MEDITATE + "}";
        assert emoteJson.contains("\"k\":\"emote\"");
        SestiaChatProtocol.ServerEvent fxEv = SestiaChatProtocol.parseServerEvent(remoteEmotePacket);
        assert fxEv.type() == SestiaChatProtocol.EventType.FX;
        assert CosmeticsLogic.resolveActiveEmote(false, false, "Dost_TR", System.currentTimeMillis())
                == CosmeticsLogic.EMOTE_HOVER_MEDITATE;

        // Leave cleans up remote loadout & emote cache
        SestiaChatProtocol.ServerEvent leaveEv = SestiaChatProtocol.parseServerEvent("{\"type\":\"leave\",\"from\":\"Dost_TR\"}");
        assert leaveEv.type() == SestiaChatProtocol.EventType.LEAVE;
        assert CosmeticsLogic.getRemoteLoadout("Dost_TR") == null;
        assert CosmeticsLogic.resolveActiveEmote(false, false, "Dost_TR", System.currentTimeMillis())
                == CosmeticsLogic.EMOTE_NONE;
        CosmeticsLogic.clearRemoteState();
    }

    private static void testMuteListOperations() {
        String muted = "";
        assert !SestiaChatProtocol.isMuted("Troll_1", muted);
        muted = SestiaChatProtocol.addMute("§cTroll_1", muted);
        assert SestiaChatProtocol.isMuted("troll_1", muted);
        muted = SestiaChatProtocol.addMute("Spammer", muted);
        assert SestiaChatProtocol.isMuted("Spammer", muted);
        muted = SestiaChatProtocol.removeMute("TROLL_1", muted);
        assert !SestiaChatProtocol.isMuted("Troll_1", muted);
        assert SestiaChatProtocol.isMuted("Spammer", muted);
    }

    private static void testBackoffBounds() {
        assert SestiaChatProtocol.computeBackoffMs(0) == 1000L;
        assert SestiaChatProtocol.computeBackoffMs(1) == 2000L;
        assert SestiaChatProtocol.computeBackoffMs(4) == 16000L;
        assert SestiaChatProtocol.computeBackoffMs(10) == 30000L;
        assert SestiaChatProtocol.computeBackoffMs(-3) == 1000L;
    }

    private static void testDmFormatting() {
        String incoming = SestiaChatProtocol.formatDmMessage("Pannoid", "SelfUser", false, "selam");
        assert "§8[§aPannoid §7-> §ame§8] §rselam".equals(incoming) : "Unexpected incoming DM: " + incoming;

        String outgoing = SestiaChatProtocol.formatDmMessage("SelfUser", "Pannoid", true, "yanlis oldu");
        assert "§8[§ame §7-> §aPannoid§8] §ryanlis oldu".equals(outgoing) : "Unexpected outgoing DM: " + outgoing;

        assert SestiaChatProtocol.isHeartbeatFrame("ping") && SestiaChatProtocol.isHeartbeatFrame("pong");
        assert !SestiaChatProtocol.isHeartbeatFrame("{\"type\":\"chat\"}");
    }
}
