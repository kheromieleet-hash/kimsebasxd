package com.client.mod;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Automatically executes /login <password> or /register <password> <password>
 * when prompted in server chat (or immediately on join if enabled).
 */
public final class AutoLoginHandler {
    private static boolean sessionDone = false;
    private static String pendingCommand = null;
    private static int pendingTicks = -1;

    private AutoLoginHandler() {
    }

    public static void onEnteredWorld() {
        sessionDone = false;
        pendingCommand = null;
        pendingTicks = -1;

        ModConfig config = ModConfig.getInstance();
        if (config.autoLoginEnabled && config.autoLoginOnJoin && hasPassword(config)) {
            pendingCommand = "login " + config.autoLoginPassword.trim();
            pendingTicks = Math.max(0, config.autoLoginDelay);
        }
    }

    public static void onLeftWorld() {
        sessionDone = false;
        pendingCommand = null;
        pendingTicks = -1;
    }

    public static void onChatMessage(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (!config.autoLoginEnabled || sessionDone || pendingCommand != null || !hasPassword(config)) {
            return;
        }

        String clean = rawText.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        String pass = config.autoLoginPassword.trim();

        if (config.autoLoginAutoRegister && (clean.contains("/register") || clean.contains("/reg "))) {
            pendingCommand = "register " + pass + " " + pass;
            pendingTicks = Math.max(0, config.autoLoginDelay);
            return;
        }

        if (clean.contains("/login")
                || clean.contains("/l <")
                || clean.contains("please login")
                || clean.contains("giris yap")
                || clean.contains("giriş yap")) {
            pendingCommand = "login " + pass;
            pendingTicks = Math.max(0, config.autoLoginDelay);
        }
    }

    public static void tick(Minecraft mc) {
        ModConfig config = ModConfig.getInstance();
        if (!config.autoLoginEnabled || mc == null || mc.player == null || mc.player.connection == null) {
            return;
        }
        if (pendingCommand == null) {
            return;
        }
        if (pendingTicks > 0) {
            pendingTicks--;
            return;
        }
        mc.player.connection.sendCommand(pendingCommand);
        pendingCommand = null;
        pendingTicks = -1;
        sessionDone = true;
    }

    private static boolean hasPassword(ModConfig config) {
        return config.autoLoginPassword != null && !config.autoLoginPassword.trim().isEmpty();
    }
}
