package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Renders a compact scrollable HUD card listing nearby players (sorted closest-first)
 * with optional distance and health readouts, configurable Top Right / Top Left alignment,
 * mouse-wheel scrolling (hover when cursor is open, or Alt + Scroll in-game), and HUD Editor support.
 */
public final class PlayerListHudRenderer {

    public static final int HEADER_HEIGHT = 16;
    public static final int ROW_HEIGHT = 13;
    public static final int MIN_WIDTH = 124;
    private static final int MARGIN = 6;
    private static final int SCROLLBAR_RESERVE = 7;

    public record NearbyPlayerInfo(UUID uuid, String name, int distanceBlocks, int healthHp) {
        public NearbyPlayerInfo(String name, int distanceBlocks, int healthHp) {
            this(null, name, distanceBlocks, healthHp);
        }
    }

    private static int scrollOffsetRows = 0;
    private static int lastBoxX = -1;
    private static int lastBoxY = -1;
    private static int lastBoxW = 0;
    private static int lastBoxH = 0;
    private static int lastTotalPlayers = 0;
    private static int lastVisibleRows = 0;

    private PlayerListHudRenderer() {
    }

    public static List<NearbyPlayerInfo> collectNearbyPlayers(Minecraft mc, ModConfig cfg) {
        if (mc == null || mc.level == null || mc.player == null) {
            return List.of();
        }
        ClientPacketListener conn = mc.getConnection();
        List<NearbyPlayerInfo> raw = new ArrayList<>();
        for (Player other : mc.level.players()) {
            if (other == null || other == mc.player || other.isRemoved()) {
                continue;
            }
            if (cfg != null && cfg.playerListIgnoreNpcs && conn != null) {
                if (conn.getPlayerInfo(other.getUUID()) == null) {
                    continue;
                }
            }
            String name = other.getName().getString();
            if (name == null || name.isBlank()) {
                continue;
            }
            int dist = (int) Math.round(mc.player.distanceTo(other));
            int hp = Math.max(0, Math.round(other.getHealth() + other.getAbsorptionAmount()));
            raw.add(new NearbyPlayerInfo(other.getUUID(), name, dist, hp));
        }
        if (raw.size() > 1) {
            raw.sort(Comparator.comparingInt(NearbyPlayerInfo::distanceBlocks)
                    .thenComparing(NearbyPlayerInfo::name, String.CASE_INSENSITIVE_ORDER));
        }
        return raw;
    }

    public static List<NearbyPlayerInfo> sortPlayers(List<NearbyPlayerInfo> input) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }
        List<NearbyPlayerInfo> sorted = new ArrayList<>(input);
        sorted.sort(Comparator.comparingInt(NearbyPlayerInfo::distanceBlocks)
                .thenComparing(NearbyPlayerInfo::name, String.CASE_INSENSITIVE_ORDER));
        return sorted;
    }

    public static int computeVisibleRowCount(int totalPlayers, int maxVisibleRows, int screenH, int boxY) {
        if (totalPlayers <= 0) {
            return 1;
        }
        int maxByScreen = Math.max(4, (screenH - Math.max(0, boxY) - HEADER_HEIGHT - 12) / ROW_HEIGHT);
        int configured = Math.max(4, Math.min(40, maxVisibleRows));
        return Math.max(1, Math.min(totalPlayers, Math.min(configured, maxByScreen)));
    }

    public static int clampScrollOffset(int offset, int totalPlayers, int visibleRows) {
        int maxOffset = Math.max(0, totalPlayers - Math.max(1, visibleRows));
        return Math.max(0, Math.min(maxOffset, offset));
    }

    public static int stepScrollOffset(int currentOffset, double verticalDelta, int totalPlayers, int visibleRows) {
        int delta = verticalDelta > 0 ? -1 : (verticalDelta < 0 ? 1 : 0);
        return clampScrollOffset(currentOffset + delta, totalPlayers, visibleRows);
    }

    public static int[] resolveDefaultCoords(int align, int screenW, int boxW) {
        int x = align == 1 ? MARGIN : Math.max(2, screenW - boxW - MARGIN);
        return new int[]{x, MARGIN};
    }

    public static int calculateWidth(Font font, List<NearbyPlayerInfo> players, ModConfig cfg, boolean hasScrollbar) {
        int maxW = MIN_WIDTH;
        if (font == null || players == null || players.isEmpty()) {
            return maxW;
        }
        boolean showDist = cfg == null || cfg.playerListShowDistance;
        boolean showHp = cfg == null || cfg.playerListShowHealth;
        boolean showBadges = cfg != null && cfg.cosmeticShowBadges && !PanicHandler.isPanicActive();
        boolean unified = cfg == null || cfg.hudUnifiedGlass;
        int extra = (hasScrollbar ? SCROLLBAR_RESERVE : 0) + (unified ? 4 : 0);
        for (NearbyPlayerInfo p : players) {
            String right = buildRightTag(p, showDist, showHp);
            int badgePad = (showBadges && CosmeticsLogic.isOnlineSestiaUser(p.name())) ? 10 : 0;
            int w = 16 + badgePad + font.width(p.name()) + (right.isEmpty() ? 0 : font.width(right) + 8) + 6 + extra;
            if (w > maxW) {
                maxW = w;
            }
        }
        return Math.min(260, maxW);
    }

    public static int[] resolveBounds(Font font, int screenW, int screenH, ModConfig cfg) {
        Minecraft mc = Minecraft.getInstance();
        List<NearbyPlayerInfo> players = collectNearbyPlayers(mc, cfg);
        return resolveBoundsForList(font, screenW, screenH, players, cfg);
    }

    public static int[] resolveBoundsForList(Font font, int screenW, int screenH, List<NearbyPlayerInfo> players, ModConfig cfg) {
        int configuredMax = cfg != null ? cfg.playerListMaxRows : 20;
        int initialY = (cfg != null && cfg.playerListY >= 0) ? cfg.playerListY : MARGIN;
        int visibleRows = computeVisibleRowCount(players.size(), configuredMax, screenH, initialY);
        boolean hasScrollbar = players.size() > visibleRows;
        int w = calculateWidth(font, players, cfg, hasScrollbar);
        int h = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + 4;

        int defX = resolveDefaultCoords(cfg != null ? cfg.playerListAlign : 0, screenW, w)[0];
        int x = (cfg != null && cfg.playerListX >= 0) ? cfg.playerListX : defX;
        int y = initialY;
        x = Mth.clamp(x, 2, Math.max(2, screenW - w - 2));
        y = Mth.clamp(y, 2, Math.max(2, screenH - h - 2));
        return new int[]{x, y, w, h};
    }

    public static boolean onMouseScroll(Minecraft mc, double mouseX, double mouseY, double verticalDelta) {
        if (verticalDelta == 0.0 || mc == null || mc.player == null || mc.level == null) {
            return false;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.playerListEnabled || PanicHandler.isPanicActive()) {
            return false;
        }
        if (mc.gui != null && (ClickGuiScreen.isTargetScreen(mc.gui.screen()) || mc.gui.screen() instanceof HudLayoutEditorScreen)) {
            return false;
        }
        if (lastTotalPlayers <= lastVisibleRows || lastBoxW <= 0) {
            return false;
        }

        boolean cursorOpen = mc.gui != null && mc.gui.screen() != null;
        if (cursorOpen) {
            boolean inside = mouseX >= lastBoxX && mouseX < lastBoxX + lastBoxW
                    && mouseY >= lastBoxY && mouseY < lastBoxY + lastBoxH;
            if (!inside) {
                return false;
            }
            scrollOffsetRows = stepScrollOffset(scrollOffsetRows, verticalDelta, lastTotalPlayers, lastVisibleRows);
            return true;
        }

        boolean altHeld = InputConstants.isKeyDown(InputConstants.KEY_LALT)
                || InputConstants.isKeyDown(InputConstants.KEY_RALT);
        if (altHeld) {
            scrollOffsetRows = stepScrollOffset(scrollOffsetRows, verticalDelta, lastTotalPlayers, lastVisibleRows);
            return true;
        }
        return false;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.playerListEnabled || PanicHandler.isPanicActive()) {
            resetBoundsState();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            resetBoundsState();
            return;
        }
        if (mc.gui != null && (ClickGuiScreen.isTargetScreen(mc.gui.screen()) || mc.gui.screen() instanceof HudLayoutEditorScreen)) {
            return;
        }

        List<NearbyPlayerInfo> players = collectNearbyPlayers(mc, cfg);
        if (cfg.playerListAutoHide && players.isEmpty()) {
            resetBoundsState();
            return;
        }

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        boolean showDist = cfg.playerListShowDistance;
        boolean showHp = cfg.playerListShowHealth;
        boolean showBadges = cfg.cosmeticShowBadges;
        boolean unified = cfg.hudUnifiedGlass;

        String[] rightTags = new String[players.size()];
        int[] rightWidths = new int[players.size()];
        int initialY = cfg.playerListY >= 0 ? cfg.playerListY : MARGIN;
        int visibleRows = computeVisibleRowCount(players.size(), cfg.playerListMaxRows, screenH, initialY);
        boolean hasScrollbar = players.size() > visibleRows;
        int maxW = MIN_WIDTH;
        int extra = (hasScrollbar ? SCROLLBAR_RESERVE : 0) + (unified ? 4 : 0);
        for (int i = 0; i < players.size(); i++) {
            NearbyPlayerInfo p = players.get(i);
            String right = buildRightTag(p, showDist, showHp);
            int rightW = right.isEmpty() ? 0 : mc.font.width(right);
            rightTags[i] = right;
            rightWidths[i] = rightW;
            int badgePad = (showBadges && CosmeticsLogic.isOnlineSestiaUser(p.name())) ? 10 : 0;
            int w = 16 + badgePad + mc.font.width(p.name()) + (rightW > 0 ? rightW + 8 : 0) + 6 + extra;
            if (w > maxW) {
                maxW = w;
            }
        }
        int width = Math.min(260, maxW);
        int bodyH = HEADER_HEIGHT + visibleRows * ROW_HEIGHT + 4;
        int defX = resolveDefaultCoords(cfg.playerListAlign, screenW, width)[0];
        int x = Mth.clamp(cfg.playerListX >= 0 ? cfg.playerListX : defX, 2, Math.max(2, screenW - width - 2));
        int y = Mth.clamp(initialY, 2, Math.max(2, screenH - bodyH - 2));
        scrollOffsetRows = clampScrollOffset(scrollOffsetRows, players.size(), visibleRows);

        lastBoxX = x;
        lastBoxY = y;
        lastBoxW = width;
        lastBoxH = bodyH;
        lastTotalPlayers = players.size();
        lastVisibleRows = visibleRows;

        int cardBg = unified ? JelloTheme.hudCardBackground() : 0xD8120B0E;
        int cardBorder = unified ? JelloTheme.hudCardBorder(false) : 0x55242431;
        int headerBg = unified ? JelloTheme.hudHeaderBackground(false) : 0xF0140B0E;

        JelloTheme.fillRoundedCard(graphics, x, y, x + width, y + bodyH, 5, cardBg, cardBorder);
        JelloTheme.fillRoundedTop(graphics, x + 1, y + 1, x + width - 1, y + HEADER_HEIGHT, 4, headerBg);
        int halfW = width / 2;
        JelloTheme.fill(graphics, x + 1, y + HEADER_HEIGHT - 1, x + halfW, y + HEADER_HEIGHT, JelloTheme.ACCENT);
        JelloTheme.fill(graphics, x + halfW, y + HEADER_HEIGHT - 1, x + width - 1, y + HEADER_HEIGHT, JelloTheme.ACCENT_HOVER);

        int textCol = unified ? JelloTheme.TEXT : 0xFFFFFFFF;
        String title = "Players (" + players.size() + ")";
        JelloTheme.textCentered(graphics, mc.font, title, x + width / 2, y + 4, textCol);

        int curY = y + HEADER_HEIGHT + 3;
        if (players.isEmpty()) {
            JelloTheme.textCentered(graphics, mc.font, "No players nearby", x + width / 2, curY + 2, unified ? JelloTheme.TEXT_MUTED : JelloTheme.TEXT_DIM);
            return;
        }

        ClientPacketListener conn = mc.getConnection();
        int rightPad = hasScrollbar ? 5 + SCROLLBAR_RESERVE : 5;
        int end = Math.min(players.size(), scrollOffsetRows + visibleRows);
        for (int i = scrollOffsetRows; i < end; i++) {
            NearbyPlayerInfo p = players.get(i);
            int dotColor = distanceColor(p.distanceBlocks());
            PlayerInfo info = (unified && conn != null && p.uuid() != null) ? conn.getPlayerInfo(p.uuid()) : null;
            int nameX = x + 11;
            if (info != null) {
                PlayerFaceExtractor.extractRenderState(graphics, info.getSkin(), x + 5, curY + 2, 8);
                nameX = x + 16;
            } else {
                JelloTheme.fill(graphics, x + 5, curY + 4, x + 8, curY + 7, dotColor);
            }

            if (showBadges && CosmeticsLogic.isOnlineSestiaUser(p.name())) {
                int leafCol = 0xFF000000 | (JelloTheme.ACCENT != 0 ? (JelloTheme.ACCENT & 0xFFFFFF) : 0x34D399);
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        SestiaTitleBackgroundRenderer.getLeafMaskTexture(),
                        nameX, curY + 2,
                        0.0f, 0.0f, 8, 8, 8, 8,
                        leafCol
                );
                nameX += 10;
            }

            String right = rightTags[i];
            int rightW = rightWidths[i];
            int maxNameW = Math.max(24, width - (nameX - x) - rightPad - (rightW > 0 ? rightW + 6 : 2));
            String displayName = fitText(mc.font, p.name(), maxNameW);
            JelloTheme.text(graphics, mc.font, displayName, nameX, curY + 2, textCol);

            if (!right.isEmpty()) {
                JelloTheme.text(graphics, mc.font, right, x + width - rightPad - rightW, curY + 2, dotColor);
            }
            curY += ROW_HEIGHT;
        }

        if (hasScrollbar) {
            int trackTop = y + HEADER_HEIGHT + 3;
            int trackH = visibleRows * ROW_HEIGHT;
            int trackX = x + width - 5;
            JelloTheme.fill(graphics, trackX, trackTop, trackX + 2, trackTop + trackH, JelloTheme.SCROLLBAR_TRACK);
            int thumbH = Math.max(12, Math.round((float) visibleRows / players.size() * trackH));
            int maxScroll = Math.max(1, players.size() - visibleRows);
            int thumbY = trackTop + Math.round((float) scrollOffsetRows / maxScroll * Math.max(0, trackH - thumbH));
            JelloTheme.fill(graphics, trackX, thumbY, trackX + 2, thumbY + thumbH, JelloTheme.ACCENT_HOVER);
        }
    }

    private static void resetBoundsState() {
        lastBoxW = 0;
        lastTotalPlayers = 0;
        lastVisibleRows = 0;
        scrollOffsetRows = 0;
    }

    private static String buildRightTag(NearbyPlayerInfo p, boolean showDist, boolean showHp) {
        if (showHp && showDist) {
            return p.healthHp() + "hp " + p.distanceBlocks() + "m";
        }
        if (showDist) {
            return p.distanceBlocks() + "m";
        }
        if (showHp) {
            return p.healthHp() + "hp";
        }
        return "";
    }

    private static int distanceColor(int dist) {
        if (dist <= 16) return 0xFFEF4444;
        if (dist <= 40) return 0xFFF59E0B;
        return 0xFF22C55E;
    }

    private static String fitText(Font font, String text, int maxW) {
        if (text == null || font.width(text) <= maxW) {
            return text == null ? "" : text;
        }
        String t = text;
        while (t.length() > 2 && font.width(t + "..") > maxW) {
            t = t.substring(0, t.length() - 1);
        }
        return t + "..";
    }
}
