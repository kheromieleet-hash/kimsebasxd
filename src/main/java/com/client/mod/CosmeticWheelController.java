package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Non-pausing Sestia Leaf OLED Glass Quick Emote Radial Menu (default Hold {@code B}).
 * Captures mouse delta cleanly without camera spin or cursor flicker for instant 1-flick Emote selection.
 */
public final class CosmeticWheelController {

    public static final int SLICE_COUNT = 8;
    public static final int HUB_W = 76;
    public static final int HUB_H = 34;
    public static final int CARD_W = 68;
    public static final int CARD_H = 22;
    public static final double RING_RADIUS_X = 112.0;
    public static final double RING_RADIUS_Y = 82.0;

    private static final double CURSOR_RADIUS_MAX = 96.0;
    private static final double DEADZONE_RADIUS = 18.0;

    private static final int[] EMOTE_SLICE_IDS = {
            CosmeticsLogic.EMOTE_SIT,
            CosmeticsLogic.EMOTE_BACKFLIP,
            CosmeticsLogic.EMOTE_HYPE_DANCE,
            CosmeticsLogic.EMOTE_RUNE_MEDITATE,
            CosmeticsLogic.EMOTE_HIGH_FIVE,
            CosmeticsLogic.EMOTE_FIST_BUMP,
            CosmeticsLogic.EMOTE_WAVE,
            CosmeticsLogic.EMOTE_NONE
    };
    private static final String[] EMOTE_SLICE_LABELS = {
            "Sit", "Backflip", "Dance", "Rune Book",
            "High-Five", "Fist Bump", "Wave", "Stop"
    };

    private static boolean open = false;
    private static int activeKey = InputConstants.KEY_B;
    private static double cursorX = 0.0;
    private static double cursorY = 0.0;
    private static int hoveredSlice = -1;
    private static long openedAtMs = 0L;

    private CosmeticWheelController() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static int getHoveredSlice() {
        return hoveredSlice;
    }

    public static void forceClose() {
        open = false;
        cursorX = 0.0;
        cursorY = 0.0;
        hoveredSlice = -1;
    }

    /**
     * Pure geometric wedge resolver: Slice 0 is at 12 o'clock (-Y), proceeding clockwise.
     * Returns -1 when pointer is inside the center deadzone.
     */
    public static int resolveWedgeIndex(double dx, double dy, double deadzone, int sliceCount) {
        if (sliceCount <= 0 || !Double.isFinite(dx) || !Double.isFinite(dy)) {
            return -1;
        }
        double dist = Math.hypot(dx, dy);
        if (dist < deadzone) {
            return -1;
        }
        double ang = Math.atan2(dx, -dy);
        if (ang < 0.0) {
            ang += Math.PI * 2.0;
        }
        double step = (Math.PI * 2.0) / sliceCount;
        int idx = (int) Math.floor(((ang + step * 0.5) % (Math.PI * 2.0)) / step);
        return Math.max(0, Math.min(sliceCount - 1, idx));
    }

    public static int computeSliceOffsetX(int slice, float ease) {
        double ang = slice * (Math.PI * 2.0 / SLICE_COUNT);
        return (int) Math.round(Math.sin(ang) * RING_RADIUS_X * ease);
    }

    public static int computeSliceOffsetY(int slice, float ease) {
        double ang = slice * (Math.PI * 2.0 / SLICE_COUNT);
        return (int) Math.round(-Math.cos(ang) * RING_RADIUS_Y * ease);
    }

    public static void onMouseDelta(double dx, double dy) {
        if (!open) {
            return;
        }
        cursorX += dx * 0.38;
        cursorY += dy * 0.38;
        double dist = Math.hypot(cursorX, cursorY);
        if (dist > CURSOR_RADIUS_MAX) {
            double scale = CURSOR_RADIUS_MAX / dist;
            cursorX *= scale;
            cursorY *= scale;
        }
        hoveredSlice = resolveWedgeIndex(cursorX, cursorY, DEADZONE_RADIUS, SLICE_COUNT);
    }

    public static boolean handleKeyPress(Minecraft mc, int action, int key) {
        if (mc == null || PanicHandler.isPanicActive()) {
            forceClose();
            return false;
        }
        ModConfig cfg = ModConfig.getInstance();
        int wheelKey = (cfg != null && cfg.cosmeticWheelKey > 0) ? cfg.cosmeticWheelKey : InputConstants.KEY_B;

        if (!open) {
            if (action == InputConstants.PRESS && key == wheelKey && mc.gui != null && mc.gui.screen() == null && mc.player != null) {
                openWheel(wheelKey);
                return true;
            }
            return false;
        }

        if (key == InputConstants.KEY_ESCAPE && action == InputConstants.PRESS) {
            forceClose();
            return true;
        }
        if (key == activeKey && action == InputConstants.RELEASE) {
            confirmAndClose(mc);
            return true;
        }
        return key == activeKey;
    }

    public static boolean handleMouseButton(Minecraft mc, int button, int action) {
        if (!open) {
            return false;
        }
        if (action == 1) {
            if (button == 1) {
                forceClose();
                return true;
            }
            if (button == 0) {
                confirmAndClose(mc);
                return true;
            }
        }
        return true;
    }

    public static boolean handleMouseScroll(double vertical) {
        return open && Math.abs(vertical) >= 0.05;
    }

    private static void openWheel(int wheelKey) {
        open = true;
        activeKey = wheelKey;
        cursorX = 0.0;
        cursorY = 0.0;
        hoveredSlice = -1;
        openedAtMs = System.currentTimeMillis();
    }

    private static void confirmAndClose(Minecraft mc) {
        int slice = hoveredSlice;
        forceClose();
        if (slice < 0 || slice >= SLICE_COUNT || mc == null || mc.player == null) {
            return;
        }
        int emoteId = EMOTE_SLICE_IDS[slice];
        CosmeticsLogic.setLocalEmote(emoteId, System.currentTimeMillis());
        SestiaChatClient.broadcastEmote(emoteId);
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (!open || graphics == null || PanicHandler.isPanicActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || (mc.gui != null && mc.gui.screen() != null)) {
            forceClose();
            return;
        }
        if (!InputConstants.isKeyDown(activeKey)) {
            confirmAndClose(mc);
            return;
        }

        Font font = mc.font;
        if (font == null) {
            return;
        }
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int cx = sw / 2;
        int cy = Math.min(sh / 2, Math.max(104, sh - 122));

        float anim = Math.min(1.0f, (System.currentTimeMillis() - openedAtMs) / 110.0f);
        float ease = 1.0f - (1.0f - anim) * (1.0f - anim);

        int accent = JelloTheme.ACCENT != 0 ? (JelloTheme.ACCENT & 0xFFFFFF) : 0x34D399;
        int accentFill = (0x5A << 24) | accent;
        int accentBorder = (0xEE << 24) | accent;
        int glassBg = (0xD2 << 24) | (JelloTheme.WINDOW_BG & 0xFFFFFF);
        int subtleBorder = JelloTheme.BORDER_SUBTLE != 0 ? JelloTheme.BORDER_SUBTLE : 0x55FFFFFF;

        // Clean center hub card (guaranteed clear of all 8 elliptical cards)
        JelloTheme.fillRoundedCard(graphics, cx - HUB_W / 2, cy - HUB_H / 2, cx + HUB_W / 2, cy + HUB_H / 2, 8,
                glassBg, hoveredSlice >= 0 ? accentBorder : subtleBorder);
        graphics.centeredText(font, Component.literal("§lEMOTES"), cx, cy - 11, (0xFF << 24) | accent);
        String subHint = (hoveredSlice >= 0) ? EMOTE_SLICE_LABELS[hoveredSlice] : "Select";
        graphics.centeredText(font, Component.literal(subHint), cx, cy + 2, 0xFFE6EFF6);

        // 8 cleanly spaced radial emote cards arranged clockwise from 12 o'clock
        int activeEmote = CosmeticsLogic.getLocalActiveEmote();
        for (int i = 0; i < SLICE_COUNT; i++) {
            int wx = cx + computeSliceOffsetX(i, ease);
            int wy = cy + computeSliceOffsetY(i, ease);
            boolean hov = (i == hoveredSlice);
            boolean isActiveItem = (EMOTE_SLICE_IDS[i] >= 0 && EMOTE_SLICE_IDS[i] == activeEmote);
            int bg = hov ? accentFill : (isActiveItem ? ((0x33 << 24) | accent) : glassBg);
            int border = hov ? accentBorder : (isActiveItem ? ((0xAA << 24) | accent) : subtleBorder);
            JelloTheme.fillRoundedCard(graphics, wx - CARD_W / 2, wy - CARD_H / 2, wx + CARD_W / 2, wy + CARD_H / 2, 6, bg, border);

            String label = EMOTE_SLICE_LABELS[i];
            int textColor = hov ? 0xFFFFFFFF : (isActiveItem ? ((0xFF << 24) | accent) : 0xFFD8E4EE);
            graphics.centeredText(font, Component.literal(label), wx, wy - 4, textColor);
        }

        // Subtle pointer indicator outside the hub border pointing toward the hovered card (never covers hub text)
        double dist = Math.hypot(cursorX, cursorY);
        if (dist >= DEADZONE_RADIUS) {
            double ux = cursorX / dist;
            double uy = cursorY / dist;
            int px = cx + (int) Math.round(ux * (HUB_W * 0.5 + 11.0) * ease);
            int py = cy + (int) Math.round(uy * (HUB_H * 0.5 + 11.0) * ease);
            graphics.fill(px - 3, py - 3, px + 3, py + 3, (0xFF << 24) | accent);
            graphics.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFFFFFF);
        }
    }
}
