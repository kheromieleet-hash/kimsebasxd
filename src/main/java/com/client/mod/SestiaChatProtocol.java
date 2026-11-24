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
        COSMETICS,
        FX,
        LEAVE,
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
        return buildHelloJson(nick, null, null);
    }

    public static String buildHelloJson(String nick, WingsLogic.WingProfile wings) {
        return buildHelloJson(nick, wings, null);
    }

    public static String buildHelloJson(
            String nick,
            WingsLogic.WingProfile wings,
            CosmeticsLogic.CosmeticLoadout cosmetics
    ) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "hello");
        obj.addProperty("nick", sanitizeNick(nick));
        if (wings != null) {
            obj.add("wings", serializeWingProfile(wings));
        }
        if (cosmetics != null) {
            obj.add("c", serializeCosmeticsV2(cosmetics));
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

    public static String buildCosmeticsJson(CosmeticsLogic.CosmeticLoadout cosmetics) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "cosmetics");
        if (cosmetics != null) {
            obj.add("c", serializeCosmeticsV2(cosmetics));
        }
        return obj.toString();
    }

    public static String buildEmoteFxJson(int emoteId) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "fx");
        obj.addProperty("k", "emote");
        obj.addProperty("id", emoteId < 0 ? CosmeticsLogic.EMOTE_NONE : CosmeticsLogic.clampEmoteMode(emoteId));
        return obj.toString();
    }

    public static JsonObject serializeCosmeticsV2(CosmeticsLogic.CosmeticLoadout raw) {
        CosmeticsLogic.CosmeticLoadout l = (raw != null ? raw : CosmeticsLogic.defaultLoadout()).sanitized();
        WingsLogic.WingProfile w = l.wings();

        JsonObject c = new JsonObject();
        c.addProperty("v", 2);

        JsonArray wArr = new JsonArray();
        wArr.add(w.enabled());
        wArr.add(w.mode());
        wArr.add(w.colorPreset());
        wArr.add(w.customRgb());
        wArr.add(round3(w.length()));
        wArr.add(round3(w.width()));
        wArr.add(w.linesCount());
        wArr.add(round3(w.fadeStart()));
        wArr.add(round3(w.height()));
        wArr.add(round3(w.flapSpeed()));
        wArr.add(w.showDamage());
        c.add("w", wArr);

        c.add("cl", arr(l.cloakEnabled(), l.cloakMode(), l.cloakColorPreset(), l.cloakCustomRgb()));
        c.add("h", arr(l.hatEnabled(), l.hatMode(), l.hatColorPreset(), l.hatCustomRgb(), round3(l.hatScale())));
        c.add("p", arr(l.petEnabled(), l.petMode(), l.petSide(), l.petColorPreset(), l.petCustomRgb()));
        c.add("g", arr(l.gearEnabled(), l.gearMode(), l.gearColorPreset(), l.gearCustomRgb()));
        c.add("a", arr(l.auraEnabled(), l.auraMode(), l.auraColorPreset(), l.auraCustomRgb(), round3(l.auraRadius()), round3(l.auraSpeed())));
        c.add("t", arr(l.trailEnabled(), l.trailMode(), l.trailColorPreset(), l.trailCustomRgb()));
        c.add("hf", arr(l.hitFxEnabled(), l.hitFxMode(), l.hitFxColorPreset(), l.hitFxCustomRgb()));
        c.add("kf", arr(l.killFxEnabled(), l.killFxMode(), l.killFxColorPreset(), l.killFxCustomRgb()));

        JsonArray tfArr = new JsonArray();
        tfArr.add(l.totemFxEnabled());
        tfArr.add(round3(l.totemFxScale()));
        tfArr.add(l.totemFxRing());
        c.add("tf", tfArr);

        c.add("gl", arr(l.glintEnabled(), l.glintColorPreset(), l.glintCustomRgb(), round3(l.glintIntensity())));

        JsonArray haArr = new JsonArray();
        haArr.add(l.handAuraEnabled());
        haArr.add(l.handAuraMode());
        c.add("ha", haArr);

        c.add("sa", arr(l.slashArcEnabled(), l.slashArcMode(), l.slashArcColorPreset(), l.slashArcCustomRgb()));
        return c;
    }

    public static CosmeticsLogic.CosmeticLoadout parseCosmeticsV2(JsonObject c) {
        if (c == null) {
            return null;
        }
        CosmeticsLogic.CosmeticLoadout def = CosmeticsLogic.defaultLoadout();

        JsonArray wArr = getArray(c, "w");
        WingsLogic.WingProfile wings = wArr != null ? new WingsLogic.WingProfile(
                arrBool(wArr, 0, false),
                arrInt(wArr, 1, WingsLogic.MODE_SESTIA_LEAF),
                arrInt(wArr, 2, WingsLogic.COLOR_EMERALD),
                arrInt(wArr, 3, 0x34D399),
                arrFloat(wArr, 4, 1.15f),
                arrFloat(wArr, 5, 0.06f),
                arrInt(wArr, 6, 5),
                arrFloat(wArr, 7, 0.50f),
                arrFloat(wArr, 8, 0.15f),
                arrFloat(wArr, 9, 1.0f),
                arrBool(wArr, 10, true)
        ) : def.wings();

        JsonArray cl = getArray(c, "cl");
        JsonArray h = getArray(c, "h");
        JsonArray p = getArray(c, "p");
        JsonArray g = getArray(c, "g");
        JsonArray a = getArray(c, "a");
        JsonArray t = getArray(c, "t");
        JsonArray hf = getArray(c, "hf");
        JsonArray kf = getArray(c, "kf");
        JsonArray tf = getArray(c, "tf");
        JsonArray gl = getArray(c, "gl");
        JsonArray ha = getArray(c, "ha");
        JsonArray sa = getArray(c, "sa");

        return new CosmeticsLogic.CosmeticLoadout(
                wings,
                arrBool(cl, 0, def.cloakEnabled()), arrInt(cl, 1, def.cloakMode()), arrInt(cl, 2, def.cloakColorPreset()), arrInt(cl, 3, def.cloakCustomRgb()),
                arrBool(h, 0, def.hatEnabled()), arrInt(h, 1, def.hatMode()), arrInt(h, 2, def.hatColorPreset()), arrInt(h, 3, def.hatCustomRgb()), arrFloat(h, 4, def.hatScale()),
                arrBool(p, 0, def.petEnabled()), arrInt(p, 1, def.petMode()), arrInt(p, 2, def.petSide()), arrInt(p, 3, def.petColorPreset()), arrInt(p, 4, def.petCustomRgb()),
                arrBool(g, 0, def.gearEnabled()), arrInt(g, 1, def.gearMode()), arrInt(g, 2, def.gearColorPreset()), arrInt(g, 3, def.gearCustomRgb()),
                arrBool(a, 0, def.auraEnabled()), arrInt(a, 1, def.auraMode()), arrInt(a, 2, def.auraColorPreset()), arrInt(a, 3, def.auraCustomRgb()), arrFloat(a, 4, def.auraRadius()), arrFloat(a, 5, def.auraSpeed()),
                arrBool(t, 0, def.trailEnabled()), arrInt(t, 1, def.trailMode()), arrInt(t, 2, def.trailColorPreset()), arrInt(t, 3, def.trailCustomRgb()),
                arrBool(hf, 0, def.hitFxEnabled()), arrInt(hf, 1, def.hitFxMode()), arrInt(hf, 2, def.hitFxColorPreset()), arrInt(hf, 3, def.hitFxCustomRgb()),
                arrBool(kf, 0, def.killFxEnabled()), arrInt(kf, 1, def.killFxMode()), arrInt(kf, 2, def.killFxColorPreset()), arrInt(kf, 3, def.killFxCustomRgb()),
                arrBool(tf, 0, def.totemFxEnabled()), arrFloat(tf, 1, def.totemFxScale()), arrBool(tf, 2, def.totemFxRing()),
                arrBool(gl, 0, def.glintEnabled()), arrInt(gl, 1, def.glintColorPreset()), arrInt(gl, 2, def.glintCustomRgb()), arrFloat(gl, 3, def.glintIntensity()),
                arrBool(ha, 0, def.handAuraEnabled()), arrInt(ha, 1, def.handAuraMode()),
                arrBool(sa, 0, def.slashArcEnabled()), arrInt(sa, 1, def.slashArcMode()), arrInt(sa, 2, def.slashArcColorPreset()), arrInt(sa, 3, def.slashArcCustomRgb())
        ).sanitized();
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
                case "cosmetics" -> EventType.COSMETICS;
                case "fx" -> EventType.FX;
                case "leave" -> EventType.LEAVE;
                case "system" -> EventType.SYSTEM;
                case "error" -> EventType.ERROR;
                default -> EventType.UNKNOWN;
            };
            String from = sanitizeNick(getString(obj, "from"));
            if (type == EventType.WELCOME) {
                from = sanitizeNick(getString(obj, "nick"));
                if (!from.isEmpty()) {
                    CosmeticsLogic.markSestiaUserOnline(from);
                }
                if (obj.has("cosmeticsMap") && obj.get("cosmeticsMap").isJsonArray()) {
                    for (JsonElement el : obj.getAsJsonArray("cosmeticsMap")) {
                        if (el.isJsonObject()) {
                            JsonObject entry = el.getAsJsonObject();
                            String entryNick = sanitizeNick(getString(entry, "nick"));
                            if (!entryNick.isEmpty() && entry.has("c") && entry.get("c").isJsonObject()) {
                                CosmeticsLogic.CosmeticLoadout cl = parseCosmeticsV2(entry.getAsJsonObject("c"));
                                if (cl != null) {
                                    CosmeticsLogic.putRemoteLoadout(entryNick, cl);
                                }
                            }
                        }
                    }
                }
                if (obj.has("wingsMap") && obj.get("wingsMap").isJsonArray()) {
                    for (JsonElement el : obj.getAsJsonArray("wingsMap")) {
                        if (el.isJsonObject()) {
                            JsonObject entry = el.getAsJsonObject();
                            String entryNick = sanitizeNick(getString(entry, "nick"));
                            if (!entryNick.isEmpty() && entry.has("wings") && entry.get("wings").isJsonObject()) {
                                WingsLogic.WingProfile wp = parseWingProfile(entry.getAsJsonObject("wings"));
                                if (wp != null && WingsLogic.getRemoteProfile(entryNick) == null) {
                                    WingsLogic.putRemoteProfile(entryNick, wp);
                                }
                            }
                        }
                    }
                }
            } else if (type == EventType.WINGS) {
                if (!from.isEmpty()) {
                    CosmeticsLogic.markSestiaUserOnline(from);
                    if (obj.has("wings") && obj.get("wings").isJsonObject()) {
                        WingsLogic.WingProfile wp = parseWingProfile(obj.getAsJsonObject("wings"));
                        if (wp != null) {
                            WingsLogic.putRemoteProfile(from, wp);
                        }
                    }
                }
            } else if (type == EventType.COSMETICS) {
                if (!from.isEmpty()) {
                    CosmeticsLogic.markSestiaUserOnline(from);
                    if (obj.has("c") && obj.get("c").isJsonObject()) {
                        CosmeticsLogic.CosmeticLoadout cl = parseCosmeticsV2(obj.getAsJsonObject("c"));
                        if (cl != null) {
                            CosmeticsLogic.putRemoteLoadout(from, cl);
                        }
                    }
                }
            } else if (type == EventType.FX) {
                if (!from.isEmpty()) {
                    CosmeticsLogic.markSestiaUserOnline(from);
                    if ("emote".equalsIgnoreCase(getString(obj, "k"))
                            && obj.has("id") && obj.get("id").isJsonPrimitive()) {
                        int emoteId = obj.get("id").getAsInt();
                        CosmeticsLogic.putRemoteEmote(from, emoteId, System.currentTimeMillis());
                    }
                }
            } else if (type == EventType.LEAVE) {
                if (!from.isEmpty()) {
                    CosmeticsLogic.removeRemotePlayer(from);
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
                        String u = sanitizeNick(el.getAsString());
                        users.add(u);
                        if (type == EventType.WELCOME || type == EventType.LIST) {
                            CosmeticsLogic.markSestiaUserOnline(u);
                        }
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
            return "§8[§ame §7-> §a" + sanitizeNick(to) + "§8] §r" + safeText;
        }
        return "§8[§a" + sanitizeNick(from) + " §7-> §ame§8] §r" + safeText;
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

    private static float round3(float value) {
        return Float.isFinite(value) ? (Math.round(value * 1000.0f) / 1000.0f) : 0.0f;
    }

    private static JsonArray arr(boolean en, int m, int c, int rgb) {
        JsonArray a = new JsonArray();
        a.add(en); a.add(m); a.add(c); a.add(rgb);
        return a;
    }

    private static JsonArray arr(boolean en, int m, int c, int rgb, float extra) {
        JsonArray a = arr(en, m, c, rgb);
        a.add(extra);
        return a;
    }

    private static JsonArray arr(boolean en, int m, int side, int c, int rgb) {
        JsonArray a = new JsonArray();
        a.add(en); a.add(m); a.add(side); a.add(c); a.add(rgb);
        return a;
    }

    private static JsonArray arr(boolean en, int m, int c, int rgb, float r, float spd) {
        JsonArray a = arr(en, m, c, rgb);
        a.add(r); a.add(spd);
        return a;
    }

    private static JsonArray arr(boolean en, int c, int rgb, float intensity) {
        JsonArray a = new JsonArray();
        a.add(en); a.add(c); a.add(rgb); a.add(intensity);
        return a;
    }

    private static JsonArray getArray(JsonObject obj, String key) {
        return (obj != null && obj.has(key) && obj.get(key).isJsonArray()) ? obj.getAsJsonArray(key) : null;
    }

    private static boolean arrBool(JsonArray arr, int idx, boolean fallback) {
        if (arr == null || idx < 0 || idx >= arr.size() || !arr.get(idx).isJsonPrimitive()) return fallback;
        try {
            return arr.get(idx).getAsBoolean();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int arrInt(JsonArray arr, int idx, int fallback) {
        if (arr == null || idx < 0 || idx >= arr.size() || !arr.get(idx).isJsonPrimitive()) return fallback;
        try {
            return arr.get(idx).getAsInt();
        } catch (Exception e) {
            return fallback;
        }
    }

    private static float arrFloat(JsonArray arr, int idx, float fallback) {
        if (arr == null || idx < 0 || idx >= arr.size() || !arr.get(idx).isJsonPrimitive()) return fallback;
        try {
            return arr.get(idx).getAsFloat();
        } catch (Exception e) {
            return fallback;
        }
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
