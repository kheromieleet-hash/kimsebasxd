package com.client.mod;

import net.minecraft.client.Minecraft;

import java.util.Locale;

/**
 * Automatically executes /login <password> or /register <password> <password>
 * when prompted in server chat, using the password saved for the active Account Switcher username.
 */
public final class AutoLoginHandler {
    private static final int DEFAULT_DELAY_TICKS = 15;

    private static boolean sessionDone = false;
    private static String pendingCommand = null;
    private static int pendingTicks = -1;

    private AutoLoginHandler() {
    }

    public static void onEnteredWorld() {
        sessionDone = false;
        pendingCommand = null;
        pendingTicks = -1;
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
        Minecraft mc = Minecraft.getInstance();
        String pass = resolveCurrentPassword(mc, ModConfig.getInstance());
        if (sessionDone || pendingCommand != null || pass == null) {
            return;
        }

        String clean = rawText.replaceAll("§.", "").toLowerCase(Locale.ROOT);

        if (clean.contains("/register") || clean.contains("/reg ")) {
            pendingCommand = "register " + pass + " " + pass;
            pendingTicks = DEFAULT_DELAY_TICKS;
            return;
        }

        if (clean.contains("/login")
                || clean.contains("/l <")
                || clean.contains("please login")
                || clean.contains("giris yap")
                || clean.contains("giriş yap")) {
            pendingCommand = "login " + pass;
            pendingTicks = DEFAULT_DELAY_TICKS;
        }
    }

    public static void tick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.player.connection == null) {
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

    private static String resolveCurrentPassword(Minecraft mc, ModConfig config) {
        if (mc == null || mc.getUser() == null || config == null || config.accountPasswords == null) {
            return null;
        }
        String name = mc.getUser().getName();
        if (name == null || name.isBlank()) {
            return null;
        }
        String pass = config.accountPasswords.get(name.toLowerCase(Locale.ROOT));
        if (pass == null || pass.trim().isEmpty()) {
            return null;
        }
        return pass.trim();
    }
}
