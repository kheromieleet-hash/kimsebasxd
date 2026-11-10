package com.client.mod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Rise 6 / Tenacity Luxury Theme: Dark Indigo Glass with Pastel Violet & Rose accents.
 */
public final class JelloTheme {
    public static final int ACCENT = 0xFFE53935;        // Crimson Red
    public static final int ACCENT_HOVER = 0xFFFF5252;  // Vibrant Scarlet Red
    public static final int PANEL = 0xF2140B0E;         // Dark Obsidian Crimson panel body
    public static final int HEADER = 0xFA0E080B;        // Deep Obsidian header
    public static final int BORDER = 0x55E53935;        // Glowing Crimson border outline
    public static final int ROW_HOVER = 0x22E53935;
    public static final int TEXT = 0xFFF8FAFC;
    public static final int TEXT_DIM = 0xFFFCA5A5;      // Soft Warm Red text
    public static final int TRACK = 0x33E53935;
    public static final int SWITCH_OFF = 0x5529161B;
    public static final int SWITCH_OFF_HOVER = 0x663A1E25;

    public static final int WINDOW_BG = 0xF2120B0E;
    public static final int HEADER_BG = 0xDD11090C;
    public static final int SIDEBAR_BG = 0xD00D080A;
    public static final int CONTAINER_BG = 0xE00D080B;
    public static final int CARD_INACTIVE = 0x99160E12;
    public static final int CARD_INACTIVE_HOVER = 0xBB201419;
    public static final int CARD_ACTIVE = 0xDD261217;
    public static final int CARD_ACTIVE_HOVER = 0xEE33171E;
    public static final int BORDER_SUBTLE = 0x2252333B;

    private static float alpha = 1.0f;

    private JelloTheme() {
    }

    public static void setAlpha(float value) {
        alpha = Mth.clamp(value, 0.0f, 1.0f);
    }

    public static int fade(int color) {
        int a = (int) (((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0xFFFFFF);
    }

    private static int fadeText(int color) {
        int a = Math.max(5, (int) (((color >>> 24) & 0xFF) * alpha));
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int lerpColor(int from, int to, float t) {
        t = Mth.clamp(t, 0.0f, 1.0f);
        int a = (int) Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static void fill(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, fade(color));
    }

    public static void fillRounded(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        int c = fade(color);
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            graphics.fill(x1, y1, x2, y2, c);
            return;
        }
        for (int dy = 0; dy < r; dy++) {
            double d = r - dy - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            graphics.fill(x1 + inset, y1 + dy, x2 - inset, y1 + dy + 1, c);
            graphics.fill(x1 + inset, y2 - dy - 1, x2 - inset, y2 - dy, c);
        }
        graphics.fill(x1, y1 + r, x2, y2 - r, c);
    }

    public static void fillRoundedTop(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        int c = fade(color);
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            graphics.fill(x1, y1, x2, y2, c);
            return;
        }
        for (int dy = 0; dy < r; dy++) {
            double d = r - dy - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            graphics.fill(x1 + inset, y1 + dy, x2 - inset, y1 + dy + 1, c);
        }
        graphics.fill(x1, y1 + r, x2, y2, c);
    }

    public static void fillRoundedBottom(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        int c = fade(color);
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            graphics.fill(x1, y1, x2, y2, c);
            return;
        }
        graphics.fill(x1, y1, x2, y2 - r, c);
        for (int dy = 0; dy < r; dy++) {
            double d = r - dy - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            graphics.fill(x1 + inset, y2 - dy - 1, x2 - inset, y2 - dy, c);
        }
    }

    public static void fillRoundedCard(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int bgColor, int borderColor) {
        if ((borderColor >>> 24) != 0) {
            fillRounded(graphics, x1, y1, x2, y2, radius, borderColor);
            fillRounded(graphics, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, radius - 1), bgColor);
        } else {
            fillRounded(graphics, x1, y1, x2, y2, radius, bgColor);
        }
    }

    public static void fillCircle(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        fillRounded(graphics, centerX - radius, centerY - radius, centerX + radius, centerY + radius, radius, color);
    }

    public static void fillHorizontalGradient(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int colorFrom, int colorTo) {
        int w = x2 - x1;
        if (w <= 0) return;
        for (int x = 0; x < w; x++) {
            float t = (float) x / (float) w;
            int col = lerpColor(colorFrom, colorTo, t);
            graphics.fill(x1 + x, y1, x1 + x + 1, y2, fade(col));
        }
    }

    public static void drawGlowShadow(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color, int spread) {
        int baseA = (color >>> 24) & 0xFF;
        int rgb = color & 0xFFFFFF;
        for (int s = spread; s >= 1; s--) {
            float ratio = (float) (spread - s + 1) / (spread + 1);
            int a = (int) (baseA * ratio * 0.40f);
            if (a <= 0) continue;
            fillRounded(graphics, x1 - s, y1 - s, x2 + s, y2 + s, radius + s, (a << 24) | rgb);
        }
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, Component.literal(text), x, y, fadeText(color), false);
    }

    public static void textCentered(GuiGraphicsExtractor graphics, Font font, String text, int centerX, int y, int color) {
        text(graphics, font, text, centerX - font.width(text) / 2, y, color);
    }

    /** Underdamped spring: overshoots its target, which produces the bounce. */
    private static final class Spring {
        float pos;
        float vel;

        Spring(float pos) {
            this.pos = pos;
        }

        void update(float target, float stiffness, float damping, float dt) {
            int steps = Math.max(1, (int) Math.ceil(dt / 0.004f));
            float h = dt / steps;
            for (int i = 0; i < steps; i++) {
                vel += ((target - pos) * stiffness - vel * damping) * h;
                pos += vel * h;
            }
        }
    }

    private static final Map<String, Spring> SPRINGS = new HashMap<>();

    public static float spring(String key, float target, float initial, float stiffness, float damping, float dt) {
        Spring s = SPRINGS.computeIfAbsent(key, k -> new Spring(initial));
        s.update(target, stiffness, damping, dt);
        return s.pos;
    }

    public static void clearSprings(String prefix) {
        SPRINGS.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
