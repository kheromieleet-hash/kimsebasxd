package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * Detects when the player is mentioned in chat (@nick, nick in message body,
 * server mention alerts, or incoming DMs) and triggers a Windows desktop notification,
 * taskbar attention flash, and in-game chime.
 */
public final class ChatMentionNotifier {
    private static long lastMentionAlertMs = 0L;

    private ChatMentionNotifier() {
    }

    public static void onChatMessage(String rawText) {
        if (rawText == null || rawText.isEmpty() || PanicHandler.isPanicActive()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.chatMentionAlert) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        String myName = resolvePlayerName(mc);
        if (myName == null || myName.length() < 2) {
            return;
        }

        String clean = rawText.replaceAll("§.", "").trim();
        if (clean.isEmpty()) {
            return;
        }

        if (isMentionOrIncomingDm(clean, myName)) {
            triggerWindowsMentionNotification(mc, clean);
        }
    }

    public static void onTitleOrActionBar(net.minecraft.network.chat.Component text) {
        if (text == null || PanicHandler.isPanicActive()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.chatMentionAlert) {
            return;
        }
        String raw = text.getString();
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String clean = raw.replaceAll("§.", "").trim();
        String asciiLower = AutoSpawnerModule.normalizeTurkishText(clean);
        if (asciiLower.contains("senden bahsetti")
                || asciiLower.contains("sizden bahsetti")
                || asciiLower.contains("senden bahsedildi")
                || asciiLower.contains("sizden bahsedildi")
                || asciiLower.contains("mentioned you")) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                triggerWindowsMentionNotification(mc, clean);
            }
        }
    }

    private static String resolvePlayerName(Minecraft mc) {
        if (mc.player != null && mc.player.getName() != null) {
            String n = mc.player.getName().getString();
            if (n != null && !n.isEmpty()) {
                return n;
            }
        }
        if (mc.getUser() != null && mc.getUser().getName() != null) {
            return mc.getUser().getName();
        }
        return null;
    }

    static boolean isMentionOrIncomingDm(String cleanMessage, String myName) {
        if (cleanMessage == null || myName == null || myName.isEmpty()) {
            return false;
        }
        String clean = cleanMessage.replaceAll("§.", "");
        String lower = clean.toLowerCase(Locale.ROOT);
        String myLower = myName.toLowerCase(Locale.ROOT);

        // 1. Sestia Client Chat lines: support both legacy "[Sestia]" and current "[SestiaChat]" / "[sender -> me]"
        if (lower.startsWith("[sestia]")) {
            return lower.contains("[dm]") && lower.contains("-> you:");
        }
        if (lower.startsWith("[me -> ")) {
            return false;
        }
        if (lower.contains(" -> me]")) {
            return !lower.startsWith("[" + myLower + " -> me]");
        }
        if (lower.startsWith("[sestiachat]")) {
            // Skip online list responses (".c list") so listing yourself doesn't trigger a false mention
            if (lower.startsWith("[sestiachat] online (")) {
                return false;
            }
            String afterPrefix = lower.substring("[sestiachat]".length()).trim();
            int colon = afterPrefix.indexOf(':');
            if (colon > 0) {
                String sender = afterPrefix.substring(0, colon).trim();
                String body = afterPrefix.substring(colon + 1);
                if (sender.equalsIgnoreCase(myLower)) {
                    return false;
                }
                return containsWholeWord(body, myLower);
            }
            return false;
        }

        // 2. Explicit server-side mention notifications
        String asciiLower = AutoSpawnerModule.normalizeTurkishText(clean);
        if (asciiLower.contains("senden bahsetti")
                || asciiLower.contains("sizden bahsetti")
                || asciiLower.contains("senden bahsedildi")
                || asciiLower.contains("sizden bahsedildi")
                || asciiLower.contains("mentioned you")) {
            return true;
        }

        // 3. Split sender prefix from message body if a standard chat separator exists
        int sepIdx = -1;
        int sepLen = 0;
        String[] separators = {"»", " -> ", ": ", "> "};
        for (String sep : separators) {
            int idx = lower.indexOf(sep);
            if (idx > 0 && (sepIdx == -1 || idx < sepIdx)) {
                sepIdx = idx;
                sepLen = sep.length();
            }
        }

        if (sepIdx > 0) {
            String senderPart = lower.substring(0, sepIdx);
            String bodyPart = lower.substring(sepIdx + sepLen);

            // Incoming private message (e.g. "[Mesaj] Oyuncu -> Ben: selam" or "Oyuncu -> MyName: selam")
            if (lower.indexOf(" -> ") == sepIdx) {
                int afterArrowEnd = sepIdx + sepLen;
                int colonAfterArrow = lower.indexOf(':', afterArrowEnd);
                if (colonAfterArrow > afterArrowEnd) {
                    String targetPart = lower.substring(afterArrowEnd, colonAfterArrow).trim();
                    String msgBody = lower.substring(colonAfterArrow + 1);
                    if (targetPart.equals("ben") || targetPart.equals("sen") || targetPart.equals("you")
                            || containsWholeWord(targetPart, myLower)) {
                        return true;
                    }
                    if (containsWholeWord(senderPart, myLower)) {
                        return false;
                    }
                    return containsWholeWord(msgBody, myLower);
                }
            }

            // Ignore our own outgoing chat messages
            if (containsWholeWord(senderPart, myLower)) {
                return false;
            }

            // Someone else mentioned our username in the chat message body
            return containsWholeWord(bodyPart, myLower);
        }

        // 4. No standard separator: trigger if @username is explicitly tagged
        return containsWholeWord(lower, "@" + myLower);
    }

    private static boolean containsWholeWord(String haystackLower, String needleLower) {
        if (haystackLower == null || needleLower == null || needleLower.isEmpty()) {
            return false;
        }
        int from = 0;
        int len = needleLower.length();
        while (true) {
            int idx = haystackLower.indexOf(needleLower, from);
            if (idx < 0) {
                return false;
            }
            boolean leftOk = idx == 0 || !isWordChar(haystackLower.charAt(idx - 1));
            int end = idx + len;
            boolean rightOk = end >= haystackLower.length() || !isWordChar(haystackLower.charAt(end));
            if (leftOk && rightOk) {
                return true;
            }
            from = idx + 1;
        }
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static void triggerWindowsMentionNotification(Minecraft mc, String cleanMessage) {
        try {
            if (mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.35f));
            }
        } catch (Throwable ignored) {
        }

        DesktopAlertHelper.requestWindowAttention(mc);

        long now = System.currentTimeMillis();
        if (now - lastMentionAlertMs < 2500L) {
            return;
        }
        lastMentionAlertMs = now;

        DesktopAlertHelper.showDesktopNotification(
                "Sestia - Sohbette Bahsedildin!",
                cleanMessage,
                false,
                "Sestia-MentionAlert"
        );
    }
}
