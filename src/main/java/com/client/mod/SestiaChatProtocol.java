package com.client.mod;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Pure-Java command parser, URL resolver, sanitization, mute-list helpers, and JSON protocol for Sestia Client Chat.
 */
public final class SestiaChatProtocol {

    // Deployed Cloudflare Workers + SQLite Durable Object global chat endpoint.
    public static final String DEFAULT_CHAT_URL = "wss://sestia-client-chat.sestiachat.workers.dev";
    public static final int MAX_MESSAGE_LENGTH = 220;

    private SestiaChatProtocol() {
    }

    public enum CommandKind {
        NONE,
        CHAT,
        DM,
        LIST,
        HELP
    }

    public record ParsedCommand(CommandKind kind, String arg1, String arg2) {
        public static ParsedCommand none() {
            return new ParsedCommand(CommandKind.NONE, "", "");
        }
    }

    public enum EventType {
        WELCOME,
        CHAT,
        DM,
        LIST,
        WINGS,
        SYSTEM,
        ERROR,
        UNKNOWN
    }

    public record ServerEvent(
            EventType type,
            String from,
            String to,
            String text,
            int onlineCount,
            List<String> users) {
    }

    public static boolean isClientChatCommand(String rawInput) {
        if (rawInput == null) {
            return false;
        }
        String trimmed = rawInput.trim();
        if (trimmed.equalsIgnoreCase(".c")) {
            return true;
        }
        return trimmed.length() >= 3
                && trimmed.substring(0, 2).equalsIgnoreCase(".c")
                && Character.isWhitespace(trimmed.charAt(2));
    }

    public static ParsedCommand parseOutgoingCommand(String rawInput) {
        if (!isClientChatCommand(rawInput)) {
            return ParsedCommand.none();
        }
        String trimmed = rawInput.trim();
        if (trimmed.length() <= 2) {
            return new ParsedCommand(CommandKind.HELP, "", "");
        }
        String body = trimmed.substring(2).trim();
        if (body.isEmpty()) {
            return new ParsedCommand(CommandKind.HELP, "", "");
        }

        int firstSpace = findWhitespaceIndex(body);
        String sub = (firstSpace < 0 ? body : body.substring(0, firstSpace)).toLowerCase(Locale.ROOT);
        String rest = firstSpace < 0 ? "" : body.substring(firstSpace + 1).trim();

        switch (sub) {
            case "help", "?" -> {
                return new ParsedCommand(CommandKind.HELP, "", "");
            }
            case "list" -> {
                return new ParsedCommand(CommandKind.LIST, "", "");
            }
            case "msg", "dm", "w" -> {
                if (rest.isEmpty()) {
                    return new ParsedCommand(CommandKind.HELP, "", "");
                }
                int targetSpace = findWhitespaceIndex(rest);
                if (targetSpace < 0) {
                    return new ParsedCommand(CommandKind.HELP, "", "");
                }
                String target = rest.substring(0, targetSpace).trim();
                String msgText = rest.substring(targetSpace + 1).trim();
                if (target.isEmpty() || msgText.isEmpty()) {
                    return new ParsedCommand(CommandKind.HELP, "", "");
                }
                return new ParsedCommand(CommandKind.DM, target, msgText);
            }
            default -> {
                return new ParsedCommand(CommandKind.CHAT, body, "");
            }
        }
    }

    public static String resolveEffectiveUrl(String configuredUrl) {
        return resolveEffectiveUrl(configuredUrl, DEFAULT_CHAT_URL);
    }

    static String resolveEffectiveUrl(String configuredUrl, String fallbackDefaultUrl) {
        String custom = normalizeWebSocketUrl(configuredUrl);
        if (!custom.isEmpty()) {
            return custom;
        }
        return normalizeWebSocketUrl(fallbackDefaultUrl);
    }

    /**
     * Converts https/http URLs to wss/ws and validates host formatting. Returns "" if invalid.
     */
    public static String normalizeWebSocketUrl(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.startsWith("https://")) {
            trimmed = "wss://" + trimmed.substring(8);
        } else if (lower.startsWith("http://")) {
            trimmed = "ws://" + trimmed.substring(7);
        } else if (!lower.startsWith("wss://") && !lower.startsWith("ws://")) {
            if (!trimmed.contains("://")) {
                trimmed = "wss://" + trimmed;
            } else {
                return "";
            }
        }
        while (trimmed.endsWith("/") && trimmed.length() > 6) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if ((!"wss".equals(scheme) && !"ws".equals(scheme)) || uri.getHost() == null || uri.getHost().isBlank()) {
                return "";
            }
            return trimmed;
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    public static String buildConnectUri(String normalizedBaseUrl, String nick) {
        String sep = normalizedBaseUrl.contains("?") ? "&" : "?";
        return normalizedBaseUrl + sep + "nick=" + URLEncoder.encode(sanitizeNick(nick), StandardCharsets.UTF_8);
    }

    public static String sanitizeNick(String rawNick) {
        if (rawNick == null) {
            return "Player";
        }
        String stripped = stripSectionCodes(rawNick);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stripped.length() && sb.length() < 20; i++) {
            char c = stripped.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_') {
                sb.append(c);
            }
        }
        return sb.length() >= 2 ? sb.toString() : "Player";
    }

    public static String sanitizeText(String rawText) {
        if (rawText == null) {
            return "";
        }
        String stripped = stripSectionCodes(rawText);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stripped.length(); i++) {
            char c = stripped.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t' || c < 0x20 || c == 0x7F) {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        String cleaned = sb.toString().trim();
        return cleaned.length() > MAX_MESSAGE_LENGTH ? cleaned.substring(0, MAX_MESSAGE_LENGTH) : cleaned;
    }

    public static String buildHelloJson(String nick) {
        return buildHelloJson(nick, null);
    }

    public static String buildHelloJson(String nick, WingsLogic.WingProfile wings) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "hello");
        obj.addProperty("nick", sanitizeNick(nick));
        if (wings != null) {
            obj.add("wings", serializeWingProfile(wings));
        }
        return obj.toString();
    }

    public static String buildWingsJson(WingsLogic.WingProfile wings) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "wings");
        if (wings != null) {
            obj.add("wings", serializeWingProfile(wings));
        }
        return obj.toString();
    }

    public static JsonObject serializeWingProfile(WingsLogic.WingProfile raw) {
        WingsLogic.WingProfile p = raw.sanitized();
        JsonObject w = new JsonObject();
        w.addProperty("enabled", p.enabled());
        w.addProperty("mode", p.mode());
        w.addProperty("color", p.colorPreset());
        w.addProperty("rgb", p.customRgb());
        w.addProperty("length", p.length());
        w.addProperty("width", p.width());
        w.addProperty("lines", p.linesCount());
        w.addProperty("fade", p.fadeStart());
        w.addProperty("height", p.height());
        w.addProperty("speed", p.flapSpeed());
        w.addProperty("damage", p.showDamage());
        return w;
    }

    public static WingsLogic.WingProfile parseWingProfile(JsonObject w) {
        if (w == null) {
            return null;
        }
        boolean enabled = w.has("enabled") && w.get("enabled").isJsonPrimitive() && w.get("enabled").getAsBoolean();
        int mode = w.has("mode") && w.get("mode").isJsonPrimitive() ? w.get("mode").getAsInt() : WingsLogic.MODE_LINES;
        int color = w.has("color") && w.get("color").isJsonPrimitive() ? w.get("color").getAsInt() : WingsLogic.COLOR_CYAN;
        int rgb = w.has("rgb") && w.get("rgb").isJsonPrimitive() ? w.get("rgb").getAsInt() : 0x00D4FF;
        float length = w.has("length") && w.get("length").isJsonPrimitive() ? w.get("length").getAsFloat() : 1.15f;
        float width = w.has("width") && w.get("width").isJsonPrimitive() ? w.get("width").getAsFloat() : 0.06f;
        int lines = w.has("lines") && w.get("lines").isJsonPrimitive() ? w.get("lines").getAsInt() : 5;
        float fade = w.has("fade") && w.get("fade").isJsonPrimitive() ? w.get("fade").getAsFloat() : 0.50f;
        float height = w.has("height") && w.get("height").isJsonPrimitive() ? w.get("height").getAsFloat() : 0.15f;
        float speed = w.has("speed") && w.get("speed").isJsonPrimitive() ? w.get("speed").getAsFloat() : 1.0f;
        boolean damage = !w.has("damage") || !w.get("damage").isJsonPrimitive() || w.get("damage").getAsBoolean();
        return new WingsLogic.WingProfile(enabled, mode, color, rgb, length, width, lines, fade, height, speed, damage).sanitized();
    }

    public static String buildChatJson(String text) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "chat");
        obj.addProperty("text", sanitizeText(text));
        return obj.toString();
    }

    public static String buildDmJson(String target, String text) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "dm");
        obj.addProperty("to", sanitizeNick(target));
        obj.addProperty("text", sanitizeText(text));
        return obj.toString();
    }

    public static String buildListJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "list");
        return obj.toString();
    }

    public static ServerEvent parseServerEvent(String json) {
        if (json == null || json.isBlank()) {
            return new ServerEvent(EventType.UNKNOWN, "", "", "", 0, List.of());
        }
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            String typeStr = getString(obj, "type").toLowerCase(Locale.ROOT);
            EventType type = switch (typeStr) {
                case "welcome" -> EventType.WELCOME;
                case "chat" -> EventType.CHAT;
                case "dm" -> EventType.DM;
                case "list" -> EventType.LIST;
                case "wings" -> EventType.WINGS;
                case "system" -> EventType.SYSTEM;
                case "error" -> EventType.ERROR;
                default -> EventType.UNKNOWN;
            };
            String from = sanitizeNick(getString(obj, "from"));
            if (type == EventType.WELCOME) {
                from = sanitizeNick(getString(obj, "nick"));
                if (obj.has("wingsMap") && obj.get("wingsMap").isJsonArray()) {
                    for (JsonElement el : obj.getAsJsonArray("wingsMap")) {
                        if (el.isJsonObject()) {
                            JsonObject entry = el.getAsJsonObject();
                            String entryNick = sanitizeNick(getString(entry, "nick"));
                            if (!entryNick.isEmpty() && entry.has("wings") && entry.get("wings").isJsonObject()) {
                                WingsLogic.WingProfile wp = parseWingProfile(entry.getAsJsonObject("wings"));
                                if (wp != null) {
                                    WingsLogic.putRemoteProfile(entryNick, wp);
                                }
                            }
                        }
                    }
                }
            } else if (type == EventType.WINGS) {
                if (!from.isEmpty() && obj.has("wings") && obj.get("wings").isJsonObject()) {
                    WingsLogic.WingProfile wp = parseWingProfile(obj.getAsJsonObject("wings"));
                    if (wp != null) {
                        WingsLogic.putRemoteProfile(from, wp);
                    }
                }
            }
            String to = sanitizeNick(getString(obj, "to"));
            String text = sanitizeText(getString(obj, "text"));
            int online = obj.has("online") && obj.get("online").isJsonPrimitive()
                    ? obj.get("online").getAsInt()
                    : 0;
            List<String> users = new ArrayList<>();
            if (obj.has("users") && obj.get("users").isJsonArray()) {
                JsonArray arr = obj.getAsJsonArray("users");
                for (JsonElement el : arr) {
                    if (el.isJsonPrimitive()) {
                        users.add(sanitizeNick(el.getAsString()));
                    }
                }
            }
            return new ServerEvent(type, from, to, text, online, users);
        } catch (Exception e) {
            return new ServerEvent(EventType.UNKNOWN, "", "", "", 0, List.of());
        }
    }

    public static boolean isMuted(String sender, String mutedCsv) {
        if (sender == null || sender.isBlank() || mutedCsv == null || mutedCsv.isBlank()) {
            return false;
        }
        String cleanSender = sanitizeNick(sender).toLowerCase(Locale.ROOT);
        for (String part : mutedCsv.split(",")) {
            if (part.trim().equalsIgnoreCase(cleanSender)) {
                return true;
            }
        }
        return false;
    }

    public static String addMute(String user, String mutedCsv) {
        Set<String> set = parseMuteSet(mutedCsv);
        String clean = sanitizeNick(user);
        if (!clean.isEmpty()) {
            set.add(clean.toLowerCase(Locale.ROOT));
        }
        return String.join(",", set);
    }

    public static String removeMute(String user, String mutedCsv) {
        Set<String> set = parseMuteSet(mutedCsv);
        set.remove(sanitizeNick(user).toLowerCase(Locale.ROOT));
        return String.join(",", set);
    }

    public static long computeBackoffMs(int attempt) {
        int clamped = Math.max(0, Math.min(5, attempt));
        return Math.min(30_000L, 1_000L << clamped);
    }

    public static boolean isHeartbeatFrame(String payload) {
        return "ping".equals(payload) || "pong".equals(payload);
    }

    public static String formatDmMessage(String from, String to, boolean outgoing, String text) {
        String safeText = sanitizeText(text);
        if (outgoing) {
            return "§8[§cme §7-> §c" + sanitizeNick(to) + "§8] §r" + safeText;
        }
        return "§8[§c" + sanitizeNick(from) + " §7-> §cme§8] §r" + safeText;
    }

    public static Set<String> parseMuteSet(String mutedCsv) {
        Set<String> set = new LinkedHashSet<>();
        if (mutedCsv != null && !mutedCsv.isBlank()) {
            for (String part : mutedCsv.split(",")) {
                String clean = part.trim().toLowerCase(Locale.ROOT);
                if (!clean.isEmpty()) {
                    set.add(clean);
                }
            }
        }
        return set;
    }

    private static String stripSectionCodes(String s) {
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                if (i + 1 < s.length()) {
                    i++;
                }
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static String getString(JsonObject obj, String key) {
        return obj.has(key) && obj.get(key).isJsonPrimitive() ? obj.get(key).getAsString() : "";
    }

    private static int findWhitespaceIndex(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return i;
            }
        }
        return -1;
    }
}
