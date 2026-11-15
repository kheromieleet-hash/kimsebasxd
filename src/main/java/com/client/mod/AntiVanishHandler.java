package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Detects vanished staff/players using Tab-list leave-without-quit monitoring
 * and periodic command-suggestion (/w ) leak probes.
 */
public final class AntiVanishHandler {
    public static final int PROBE_ID = 0x534553;
    private static final Pattern VALID_USERNAME = Pattern.compile("^[a-zA-Z0-9_]{3,16}$");

    private static final Map<UUID, String> trackedPlayers = new HashMap<>();
    private static final Map<String, Integer> pendingLeaves = new HashMap<>();
    private static final Set<String> alertedVanished = new HashSet<>();

    private static int probeCooldownTicks = 60;
    private static boolean initializedTab = false;

    private AntiVanishHandler() {
    }

    public static void onEnteredWorld() {
        trackedPlayers.clear();
        pendingLeaves.clear();
        alertedVanished.clear();
        probeCooldownTicks = 60;
        initializedTab = false;
    }

    public static void onLeftWorld() {
        trackedPlayers.clear();
        pendingLeaves.clear();
        alertedVanished.clear();
        initializedTab = false;
    }

    public static void onChatMessage(String rawText) {
        if (rawText == null || rawText.isEmpty() || pendingLeaves.isEmpty()) {
            return;
        }
        String clean = rawText.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        boolean looksLikeLeave = clean.contains("left the game")
                || clean.contains("disconnected")
                || clean.contains("ayrıldı")
                || clean.contains("ayrildi")
                || clean.contains("çıktı")
                || clean.contains("cikti")
                || clean.contains("[-]");
        if (!looksLikeLeave) {
            return;
        }
        Iterator<String> it = pendingLeaves.keySet().iterator();
        while (it.hasNext()) {
            String name = it.next();
            if (clean.contains(name.toLowerCase(Locale.ROOT))) {
                it.remove();
            }
        }
    }

    public static void tick(Minecraft mc) {
        ModConfig config = ModConfig.getInstance();
        if (!config.antiVanishEnabled || mc == null || mc.player == null || mc.level == null) {
            return;
        }
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) {
            return;
        }

        if (config.antiVanishTabCheck) {
            updateTabTracking(mc, conn);
        }

        if (config.antiVanishCommandCheck) {
            if (probeCooldownTicks > 0) {
                probeCooldownTicks--;
            } else {
                conn.send(new ServerboundCommandSuggestionPacket(PROBE_ID, "/w "));
                probeCooldownTicks = Math.max(5, config.antiVanishInterval) * 20;
            }
        }
    }

    private static void updateTabTracking(Minecraft mc, ClientPacketListener conn) {
        Map<UUID, String> current = new HashMap<>();
        for (PlayerInfo info : conn.getOnlinePlayers()) {
            if (info == null || info.getProfile() == null) {
                continue;
            }
            UUID id = info.getProfile().id();
            String name = info.getProfile().name();
            if (id != null && name != null && !name.isEmpty()) {
                current.put(id, name);
                alertedVanished.remove(name.toLowerCase(Locale.ROOT));
            }
        }

        if (!initializedTab) {
            trackedPlayers.putAll(current);
            initializedTab = true;
            return;
        }

        for (Map.Entry<UUID, String> entry : trackedPlayers.entrySet()) {
            if (!current.containsKey(entry.getKey())) {
                pendingLeaves.putIfAbsent(entry.getValue(), 12);
            }
        }

        trackedPlayers.clear();
        trackedPlayers.putAll(current);

        Iterator<Map.Entry<String, Integer>> it = pendingLeaves.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            int left = entry.getValue() - 1;
            if (left <= 0) {
                notifyClient(mc, "§e" + entry.getKey() + " §7left Tab without quit message (possible vanish)");
                it.remove();
            } else {
                entry.setValue(left);
            }
        }
    }

    public static boolean handleCommandSuggestions(ClientboundCommandSuggestionsPacket packet) {
        if (packet == null || packet.id() != PROBE_ID) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        ModConfig config = ModConfig.getInstance();
        if (!config.antiVanishEnabled || !config.antiVanishCommandCheck || mc.getConnection() == null) {
            return true;
        }

        Set<String> tabNames = new HashSet<>();
        for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
            if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                tabNames.add(info.getProfile().name().toLowerCase(Locale.ROOT));
            }
        }

        for (ClientboundCommandSuggestionsPacket.Entry entry : packet.suggestions()) {
            String text = entry.text();
            if (text == null || !VALID_USERNAME.matcher(text).matches()) {
                continue;
            }
            String lower = text.toLowerCase(Locale.ROOT);
            if (!tabNames.contains(lower) && alertedVanished.add(lower)) {
                notifyClient(mc, "§e" + text + " §7detected in command suggestions (vanished)");
            }
        }
        return true;
    }

    private static void notifyClient(Minecraft mc, String message) {
        if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§c[Sestia] §f[AntiVanish] " + message));
        }
    }
}
