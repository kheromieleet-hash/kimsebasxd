package com.client.mod;

import com.google.gson.Gson;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Displays a single-shot Rise 6 Luxury in-game toast and chat confirmation
 * after an automatic bootloader update (or loader version mismatch warning).
 */
public final class UpdateNoticeManager {
    public static final class Notice {
        public String type; // "UPDATED" or "LOADER_OUTDATED"
        public String version;
        public String previousVersion;
        public String changelog;
        public String releaseUrl;
    }

    private static final Gson GSON = new Gson();
    private static final long TOAST_DURATION_MS = 7000L;
    private static final long OUTDATED_DURATION_MS = 9500L;

    private static volatile Notice activeNotice;
    private static long firstRenderMs = -1L;
    private static boolean chatMessageSent = false;

    private UpdateNoticeManager() {
    }

    public static void loadOnStartup() {
        String workDirProp = System.getProperty("sct.work.dir");
        Path noticeFile = (workDirProp != null && !workDirProp.isBlank())
                ? Path.of(workDirProp).resolve("pending-notice.json")
                : Path.of("sctclient", "pending-notice.json");

        Notice loaded = consumePendingNotice(noticeFile);
        if (loaded != null) {
            activeNotice = loaded;
            firstRenderMs = -1L;
            chatMessageSent = false;
        }
    }

    public static Notice consumePendingNotice(Path noticeFile) {
        if (noticeFile == null || !Files.isRegularFile(noticeFile)) {
            return null;
        }
        try {
            String raw = Files.readString(noticeFile, StandardCharsets.UTF_8);
            Files.deleteIfExists(noticeFile);
            Notice parsed = GSON.fromJson(raw, Notice.class);
            if (parsed == null || parsed.type == null || parsed.type.isBlank()) {
                return null;
            }
            return parsed;
        } catch (Exception ignored) {
            try {
                Files.deleteIfExists(noticeFile);
            } catch (IOException ignoredDelete) {
            }
            return null;
        }
    }

    public static void renderScreenToast(GuiGraphicsExtractor graphics) {
        renderToastInternal(graphics);
    }

    public static void renderHudToast(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.screen() != null) {
            return; // ScreenMixin draws on top while a screen is active
        }
        renderToastInternal(graphics);
    }

    private static void renderToastInternal(GuiGraphicsExtractor graphics) {
        Notice notice = activeNotice;
        if (notice == null || graphics == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) {
            return;
        }

        long now = System.currentTimeMillis();
        if (firstRenderMs < 0L) {
            firstRenderMs = now;
        }

        boolean isOutdated = "LOADER_OUTDATED".equalsIgnoreCase(notice.type);
        long totalDuration = isOutdated ? OUTDATED_DURATION_MS : TOAST_DURATION_MS;
        long elapsed = now - firstRenderMs;
        if (elapsed >= totalDuration) {
            return;
        }

        float fadeIn = Mth.clamp(elapsed / 320.0f, 0.0f, 1.0f);
        float fadeOut = elapsed > (totalDuration - 450L)
                ? Mth.clamp((totalDuration - elapsed) / 450.0f, 0.0f, 1.0f)
                : 1.0f;
        float alpha = Math.min(fadeIn, fadeOut);
        float slideOffset = (1.0f - (float) Math.pow(fadeIn, 0.45)) * 40.0f;

        Font font = mc.font;
        String verText = notice.version != null ? notice.version : ClientBuildInfo.getVersion();
        String title = isOutdated
                ? "⚠ New SCTClient.jar Loader Required"
                : "✦ SCT Client Updated to v" + verText;
        String subtitle = isOutdated
                ? ("Needed for v" + verText + " • Running cached build")
                : (((notice.changelog != null && !notice.changelog.isBlank()) ? notice.changelog : "Latest build active") + " • Ready");
        String pillLabel = isOutdated ? "ACTION" : "READY";

        int maxSubtitleW = 205;
        if (font.width(subtitle) > maxSubtitleW) {
            while (subtitle.length() > 3 && font.width(subtitle + "...") > maxSubtitleW) {
                subtitle = subtitle.substring(0, subtitle.length() - 1);
            }
            subtitle = subtitle + "...";
        }

        int pillW = font.width(pillLabel) + 12;
        int contentW = Math.max(font.width(title), font.width(subtitle));
        int boxW = Math.max(210, Math.min(310, contentW + pillW + 34));
        int boxH = 38;
        int x = Math.round(graphics.guiWidth() - boxW - 10 + slideOffset);
        int y = 10;

        int accentColor = isOutdated ? 0xFFFBBF24 : 0xFFC084FC;
        int borderColor = isOutdated ? 0x99FBBF24 : 0x99C084FC;
        int pillBg = isOutdated ? 0x30FBBF24 : 0x3034D399;
        int pillText = isOutdated ? 0xFFFBBF24 : 0xFF34D399;

        JelloTheme.setAlpha(alpha);
        try {
            JelloTheme.fillRoundedCard(graphics, x, y, x + boxW, y + boxH, 8, 0xEC0F1222, borderColor);
            JelloTheme.fillRounded(graphics, x + 3, y + 6, x + 6, y + boxH - 6, 2, accentColor);

            JelloTheme.text(graphics, font, title, x + 12, y + 7, 0xFFFFFFFF);
            JelloTheme.text(graphics, font, subtitle, x + 12, y + 21, 0xFFC4B5FD);

            int px1 = x + boxW - pillW - 8;
            int py1 = y + 11;
            JelloTheme.fillRounded(graphics, px1, py1, px1 + pillW, py1 + 15, 4, pillBg);
            JelloTheme.textCentered(graphics, font, pillLabel, px1 + pillW / 2, py1 + 4, pillText);
        } finally {
            JelloTheme.setAlpha(1.0f);
        }
    }

    public static void onClientTick(Minecraft client) {
        Notice notice = activeNotice;
        if (notice == null || chatMessageSent || client == null || client.player == null || client.level == null) {
            return;
        }
        if (client.gui == null || client.gui.hud == null) {
            return;
        }
        chatMessageSent = true;
        String ver = notice.version != null ? notice.version : ClientBuildInfo.getVersion();
        String msg;
        if ("LOADER_OUTDATED".equalsIgnoreCase(notice.type)) {
            String url = notice.releaseUrl != null ? notice.releaseUrl : "https://github.com/kheromieallah/sestia/releases/latest";
            msg = "§e§l⚠ [SCT Client] §fA newer §bSCTClient.jar §floader is required for §dv" + ver + "§f. Download latest from: §n" + url;
        } else {
            String prev = (notice.previousVersion != null && !"initial".equals(notice.previousVersion))
                    ? (" §7(from v" + notice.previousVersion + ")") : "";
            String log = (notice.changelog != null && !notice.changelog.isBlank()) ? (" §7— " + notice.changelog) : "";
            msg = "§d§l✦ [SCT Client] §aUpdated to v" + ver + prev + log;
        }
        client.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
    }
}
