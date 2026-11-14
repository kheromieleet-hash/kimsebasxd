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
                : Path.of("sestia", "pending-notice.json");

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
        if (mc == null || mc.font == null || mc.gui == null || mc.gui.overlay() != null) {
            return; // Wait until splash LoadingOverlay finishes before starting/showing toast
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
                ? "Loader Update Available"
                : "Sestia Updated (v" + verText + ")";
        String subtitle = isOutdated
                ? ("Download latest sestia.jar for v" + verText)
                : ((notice.changelog != null && !notice.changelog.isBlank()) ? notice.changelog : "Ready to play");

        int maxTextW = 190;
        if (font.width(subtitle) > maxTextW) {
            while (subtitle.length() > 3 && font.width(subtitle + "...") > maxTextW) {
                subtitle = subtitle.substring(0, subtitle.length() - 1);
            }
            subtitle = subtitle + "...";
        }

        int contentW = Math.max(font.width(title), font.width(subtitle));
        int boxW = Math.max(168, Math.min(248, contentW + 38));
        int boxH = 32;
        int x = Math.round(graphics.guiWidth() - boxW - 6 + slideOffset);
        int y = 6;

        int titleColor = isOutdated ? 0xFFFFFF55 : 0xFF55FF55;
        int bgColor = 0xEE212121;
        int outerBorder = 0xFF555555;
        int innerBorder = 0xFF3A3A3A;

        JelloTheme.setAlpha(alpha);
        try {
            // Classic Minecraft advancement / system toast style: crisp dark panel + double 1px border
            JelloTheme.fill(graphics, x, y, x + boxW, y + boxH, outerBorder);
            JelloTheme.fill(graphics, x + 1, y + 1, x + boxW - 1, y + boxH - 1, innerBorder);
            JelloTheme.fill(graphics, x + 2, y + 2, x + boxW - 2, y + boxH - 2, bgColor);

            // Minimal 16x16 Minecraft block icon (dirt/stone base + grass/amber top)
            int ix = x + 8;
            int iy = y + 8;
            JelloTheme.fill(graphics, ix, iy, ix + 16, iy + 16, 0xFF5E3D28);
            JelloTheme.fill(graphics, ix, iy, ix + 16, iy + 5, isOutdated ? 0xFFE0A82E : 0xFF52A535);
            JelloTheme.fill(graphics, ix + 1, iy + 1, ix + 15, iy + 2, isOutdated ? 0xFFFBBF24 : 0xFF6BCB46);

            JelloTheme.text(graphics, font, title, x + 30, y + 7, titleColor);
            JelloTheme.text(graphics, font, subtitle, x + 30, y + 18, 0xFFFFFFFF);
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
            msg = "§7[§eSestia§7] §fNew §bsestia.jar §floader required for §ev" + ver + "§f: §n" + url;
        } else {
            String prev = (notice.previousVersion != null && !"initial".equals(notice.previousVersion))
                    ? (" §7(from v" + notice.previousVersion + ")") : "";
            String log = (notice.changelog != null && !notice.changelog.isBlank()) ? (" §7- " + notice.changelog) : "";
            msg = "§7[§aSestia§7] §fUpdated to §av" + ver + prev + log;
        }
        client.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
    }
}
