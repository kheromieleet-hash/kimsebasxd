package com.client.mod;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Evaluates player proximity, damage spikes, staff/macro verification text, and server position anomalies
 * for AutoSpawnerModule safety pausing and cooldown recovery.
 */
public final class SpawnerSafetyGuard {

    public enum MoveAnomaly {
        NONE,
        RUBBERBAND,
        TELEPORT
    }

    public enum TriggerKind {
        NONE,
        PLAYER_NEARBY,
        DAMAGE,
        CHAT_OR_TITLE,
        TELEPORT
    }

    public record SafetyAlert(TriggerKind kind, String reason, boolean critical) {
        public static final SafetyAlert NONE = new SafetyAlert(TriggerKind.NONE, "", false);

        public boolean isTriggered() {
            return kind != null && kind != TriggerKind.NONE;
        }
    }

    private record PendingTextAlert(String message, long timestampMs) {
    }

    private static final long TEXT_ALERT_TTL_MS = 8_000L;
    private static final long DESKTOP_NOTIFY_COOLDOWN_MS = 10_000L;
    private static final double RUBBERBAND_MIN_DIST = 0.45;
    private static final double TELEPORT_MIN_DIST = 12.0;

    private static final String[] SUSPICIOUS_KEYWORDS = {
            "macro",
            "makro",
            "kontrol",
            "admin",
            "yetkili",
            "afk check",
            "dogrulama",
            "captcha",
            "burada misin",
            "cevap ver"
    };

    private static volatile PendingTextAlert pendingTextAlert = null;
    private static final AtomicLong LAST_DESKTOP_NOTIFY_MS = new AtomicLong(0L);

    private boolean safeTimerActive = false;
    private long safeSinceMs = 0L;

    /**
     * Classifies a server-forced position delta as normal sync, rubberband, or teleport.
     */
    public static MoveAnomaly classifyServerMove(double dx, double dy, double dz) {
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) {
            return MoveAnomaly.TELEPORT;
        }
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist > TELEPORT_MIN_DIST) {
            return MoveAnomaly.TELEPORT;
        }
        if (dist >= RUBBERBAND_MIN_DIST) {
            return MoveAnomaly.RUBBERBAND;
        }
        return MoveAnomaly.NONE;
    }

    /**
     * Checks whether playerName matches any entry in a comma/semicolon/whitespace-separated whitelist.
     */
    public static boolean isWhitelistedPlayer(String playerName, String whitelistCsv) {
        if (playerName == null || playerName.isBlank() || whitelistCsv == null || whitelistCsv.isBlank()) {
            return false;
        }
        String target = AutoSpawnerModule.stripFormattingCodes(playerName).trim();
        if (target.isEmpty()) {
            return false;
        }
        int len = whitelistCsv.length();
        int start = -1;
        for (int i = 0; i <= len; i++) {
            char c = i < len ? whitelistCsv.charAt(i) : ',';
            boolean sep = c == ',' || c == ';' || Character.isWhitespace(c);
            if (sep) {
                if (start >= 0) {
                    String token = whitelistCsv.substring(start, i);
                    if (token.equalsIgnoreCase(target)) {
                        return true;
                    }
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        return false;
    }

    /**
     * Performs case- and Turkish-normalized matching for staff, AFK, captcha, or macro-check prompts.
     */
    public static boolean isSuspiciousStaffOrCheckText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return false;
        }
        String norm = normalizeWhitespace(AutoSpawnerModule.normalizeTurkishText(rawText));
        if (norm.isEmpty()) {
            return false;
        }
        for (String keyword : SUSPICIOUS_KEYWORDS) {
            if (norm.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Records a suspicious server chat, title, subtitle, or actionbar text packet for tick evaluation.
     */
    public static void onServerTextMessage(String text) {
        if (text == null || text.isBlank() || !isSuspiciousStaffOrCheckText(text)) {
            return;
        }
        String clean = normalizeWhitespace(AutoSpawnerModule.stripFormattingCodes(text));
        if (clean.length() > 96) {
            clean = clean.substring(0, 93) + "...";
        }
        pendingTextAlert = new PendingTextAlert(clean, System.currentTimeMillis());
    }

    /**
     * Clears any pending suspicious text alert.
     */
    public static void clearPendingTextAlert() {
        pendingTextAlert = null;
    }

    /**
     * Sends a cooldown-gated (10s) desktop notification and window attention flash.
     */
    public static void notifyDesktopSafety(String title, String message) {
        long now = System.currentTimeMillis();
        long prev = LAST_DESKTOP_NOTIFY_MS.get();
        if (now - prev < DESKTOP_NOTIFY_COOLDOWN_MS || !LAST_DESKTOP_NOTIFY_MS.compareAndSet(prev, now)) {
            return;
        }
        Minecraft mc = null;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
        }
        DesktopAlertHelper.requestWindowAttention(mc);
        String safeTitle = (title == null || title.isBlank()) ? "Sestia - Auto Spawner Safety" : title;
        String safeBody = message == null ? "" : message;
        DesktopAlertHelper.showDesktopNotification(safeTitle, safeBody, true, "Sestia-SpawnerSafety");
    }

    /**
     * Resets per-session safety clear-cooldown state.
     */
    public void reset() {
        this.safeTimerActive = false;
        this.safeSinceMs = 0L;
    }

    /**
     * Evaluates nearby non-whitelisted players, sudden/significant health drops, and recent staff/check texts.
     * Always returns a non-null SafetyAlert; returns SafetyAlert.NONE when safe.
     */
    public SafetyAlert evaluate(Minecraft mc, ModConfig cfg, float lastKnownHealth) {
        if (cfg != null && !cfg.autoSpawnerSafetyEnabled) {
            pendingTextAlert = null;
            return SafetyAlert.NONE;
        }

        long now = System.currentTimeMillis();
        boolean hasPlayer = mc != null && mc.player != null;
        float hpDrop = 0.0f;
        float currentHp = 0.0f;
        if (hasPlayer && lastKnownHealth > 0.0f) {
            currentHp = mc.player.getHealth();
            hpDrop = lastKnownHealth - currentHp;
            if (hpDrop > 0.5f && (hpDrop >= 4.0f || currentHp <= 8.0f)) {
                pendingTextAlert = null;
                return new SafetyAlert(
                        TriggerKind.DAMAGE,
                        "Critical damage (-" + format1(hpDrop) + " HP, " + format1(currentHp) + " HP left)",
                        true
                );
            }
        }

        PendingTextAlert textAlert = pendingTextAlert;
        if (textAlert != null) {
            pendingTextAlert = null;
            if ((now - textAlert.timestampMs()) <= TEXT_ALERT_TTL_MS) {
                return new SafetyAlert(
                        TriggerKind.CHAT_OR_TITLE,
                        "Check message: " + textAlert.message(),
                        true
                );
            }
        }

        if (hpDrop > 0.5f) {
            return new SafetyAlert(
                    TriggerKind.DAMAGE,
                    "Took damage (-" + format1(hpDrop) + " HP)",
                    false
            );
        }

        if (hasPlayer && mc.level != null) {
            int radius = cfg != null ? Math.max(1, cfg.autoSpawnerSafetyRadius) : 16;
            List<PlayerListHudRenderer.NearbyPlayerInfo> nearby = PlayerListHudRenderer.collectNearbyPlayers(mc, cfg);
            String whitelistCsv = null;
            for (PlayerListHudRenderer.NearbyPlayerInfo info : nearby) {
                if (info.distanceBlocks() > radius) {
                    break;
                }
                if (whitelistCsv == null) {
                    whitelistCsv = (cfg != null && cfg.wheatFarmWhitelist != null && !cfg.wheatFarmWhitelist.isEmpty())
                            ? String.join(",", cfg.wheatFarmWhitelist)
                            : "";
                }
                if (isWhitelistedPlayer(info.name(), whitelistCsv)) {
                    continue;
                }
                return new SafetyAlert(
                        TriggerKind.PLAYER_NEARBY,
                        "Player nearby: " + info.name() + " (" + info.distanceBlocks() + "m)",
                        false
                );
            }
        }

        return SafetyAlert.NONE;
    }

    /**
     * Tracks continuous safe duration and returns true once it reaches requiredClearSec seconds.
     */
    public boolean updateClearCooldown(boolean currentlySafe, int requiredClearSec, long nowMs) {
        if (!currentlySafe) {
            this.safeTimerActive = false;
            this.safeSinceMs = 0L;
            return false;
        }
        if (!this.safeTimerActive || nowMs < this.safeSinceMs) {
            this.safeTimerActive = true;
            this.safeSinceMs = nowMs;
        }
        long requiredMs = Math.max(0L, (long) requiredClearSec) * 1000L;
        return (nowMs - this.safeSinceMs) >= requiredMs;
    }

    private static String normalizeWhitespace(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        boolean lastSpace = true;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isWhitespace(c)) {
                if (!lastSpace) {
                    sb.append(' ');
                    lastSpace = true;
                }
            } else {
                sb.append(c);
                lastSpace = false;
            }
        }
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == ' ') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }

    private static String format1(float value) {
        return Float.toString(Math.round(value * 10.0f) / 10.0f);
    }
}
