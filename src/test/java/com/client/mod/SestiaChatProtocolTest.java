package com.client.mod;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;

/**
 * Pure-Java verification for SestiaChatProtocol command parsing, URL/nick/room
 * sanitization, mute list manipulation, JSON serialization, and reconnect backoff.
 */
public class SestiaChatProtocolTest {

    public static void main(String[] args) {
        testCommandDetectionAndPassThrough();
        testCommandParsingVariants();
        testUrlNormalizationAndConnectUri();
        testRoomNickAndTextSanitization();
        testJsonRoundTrip();
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
        assert SestiaChatProtocol.parseOutgoingCommand(".c status").kind() == SestiaChatProtocol.CommandKind.STATUS;
        assert SestiaChatProtocol.parseOutgoingCommand(".c list").kind() == SestiaChatProtocol.CommandKind.LIST;
        assert SestiaChatProtocol.parseOutgoingCommand(".cobble").kind() == SestiaChatProtocol.CommandKind.NONE;

        SestiaChatProtocol.ParsedCommand urlCmd = SestiaChatProtocol.parseOutgoingCommand(".c url https://chat.example.workers.dev");
        assert urlCmd.kind() == SestiaChatProtocol.CommandKind.URL;
        assert "https://chat.example.workers.dev".equals(urlCmd.arg1());

        SestiaChatProtocol.ParsedCommand roomCmd = SestiaChatProtocol.parseOutgoingCommand(".c room Turkiye-1");
        assert roomCmd.kind() == SestiaChatProtocol.CommandKind.ROOM;
        assert "Turkiye-1".equals(roomCmd.arg1());

        SestiaChatProtocol.ParsedCommand keyCmd = SestiaChatProtocol.parseOutgoingCommand(".c key gizli123");
        assert keyCmd.kind() == SestiaChatProtocol.CommandKind.KEY;
        assert "gizli123".equals(keyCmd.arg1());

        SestiaChatProtocol.ParsedCommand muteCmd = SestiaChatProtocol.parseOutgoingCommand(".c mute Griefer_01");
        assert muteCmd.kind() == SestiaChatProtocol.CommandKind.MUTE;
        assert "Griefer_01".equals(muteCmd.arg1());

        SestiaChatProtocol.ParsedCommand unmuteCmd = SestiaChatProtocol.parseOutgoingCommand(".c unmute Griefer_01");
        assert unmuteCmd.kind() == SestiaChatProtocol.CommandKind.UNMUTE;
        assert "Griefer_01".equals(unmuteCmd.arg1());

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

        String uri = SestiaChatProtocol.buildConnectUri("wss://sestia.example.workers.dev", "PvP Room", "§cSteve_1");
        assert uri.equals("wss://sestia.example.workers.dev?room=pvproom&nick=Steve_1")
                : "Unexpected connect URI: " + uri;
    }

    private static void testRoomNickAndTextSanitization() {
        assert "global".equals(SestiaChatProtocol.normalizeRoom(null));
        assert "global".equals(SestiaChatProtocol.normalizeRoom("!!!"));
        assert "tr_lobby-1".equals(SestiaChatProtocol.normalizeRoom("  TR_Lobby-1!  "));

        assert "Steve_123".equals(SestiaChatProtocol.sanitizeNick("§a§lSteve_123!"));
        assert "Player".equals(SestiaChatProtocol.sanitizeNick("§c!"));

        String dirty = "§aMerhaba\nDunya\tTest";
        assert "Merhaba Dunya Test".equals(SestiaChatProtocol.sanitizeText(dirty));

        String longText = "x".repeat(300);
        assert SestiaChatProtocol.sanitizeText(longText).length() == SestiaChatProtocol.MAX_MESSAGE_LENGTH;
    }

    private static void testJsonRoundTrip() {
        JsonObject hello = JsonParser.parseString(
                SestiaChatProtocol.buildHelloJson("§bMehmet_TR", "Arena#1")).getAsJsonObject();
        assert "hello".equals(hello.get("type").getAsString());
        assert "Mehmet_TR".equals(hello.get("nick").getAsString());
        assert "arena1".equals(hello.get("room").getAsString());

        JsonObject chat = JsonParser.parseString(SestiaChatProtocol.buildChatJson("§cSa §rBeyler")).getAsJsonObject();
        assert "chat".equals(chat.get("type").getAsString());
        assert "Sa Beyler".equals(chat.get("text").getAsString());

        JsonObject dm = JsonParser.parseString(SestiaChatProtocol.buildDmJson("§eAli", "Geliyor musun?")).getAsJsonObject();
        assert "dm".equals(dm.get("type").getAsString());
        assert "Ali".equals(dm.get("to").getAsString());
        assert "Geliyor musun?".equals(dm.get("text").getAsString());

        SestiaChatProtocol.ServerEvent welcome = SestiaChatProtocol.parseServerEvent(
                "{\"type\":\"welcome\",\"nick\":\"Mehmet\",\"room\":\"global\",\"online\":4}");
        assert welcome.type() == SestiaChatProtocol.EventType.WELCOME;
        assert "Mehmet".equals(welcome.from()) && "global".equals(welcome.room()) && welcome.onlineCount() == 4;

        SestiaChatProtocol.ServerEvent list = SestiaChatProtocol.parseServerEvent(
                "{\"type\":\"list\",\"room\":\"global\",\"users\":[\"Mehmet\",\"§cAli\"]}");
        assert list.type() == SestiaChatProtocol.EventType.LIST;
        assert List.of("Mehmet", "Ali").equals(list.users());

        assert SestiaChatProtocol.parseServerEvent("not json").type() == SestiaChatProtocol.EventType.UNKNOWN;
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
}
