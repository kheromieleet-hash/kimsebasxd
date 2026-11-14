package com.client.mod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Glassmorphic Obsidian & Crimson Red Theme constants and rounded drawing utilities.
 */
public final class JelloTheme {
    public static final FontDescription SANS_FONT =
            new FontDescription.Resource(Identifier.fromNamespaceAndPath("inventoryhud", "sans_ui"));

    public static final int ACCENT = 0xFFE53935;        // Crimson Red
    public static final int ACCENT_HOVER = 0xFFFF5252;  // Vibrant Scarlet Red
    public static final int PANEL = 0xD80B0F17;             // Translucent obsidian-navy panel body
    public static final int HEADER = 0x700E131D;            // Layered translucent header
    public static final int BORDER = 0x48E53935;            // Soft crimson hairline border
    public static final int ROW_HOVER = 0x22E53935;
    public static final int TEXT = 0xFFF8FAFC;              // Primary high-contrast white
    public static final int TEXT_MUTED = 0xFF94A3B8;        // Crisp slate secondary text
    public static final int TEXT_DIM = 0xFFFCA5A5;          // Warm Soft Red accent text
    public static final int TRACK = 0x33E53935;
    public static final int SWITCH_OFF = 0xFF161A25;        // Dark obsidian-navy pill off-track
    public static final int SWITCH_OFF_HOVER = 0xFF1F2534;

    public static final int WINDOW_BG = 0xD80B0F17;         // Layered translucent obsidian-navy glass shell
    public static final int HEADER_BG = 0x700E131D;         // Elevated translucent top bar
    public static final int SIDEBAR_BG = 0x68080B12;        // Recessed translucent nav rail
    public static final int CONTAINER_BG = 0xC010141F;      // Translucent container surface
    public static final int CARD_INACTIVE = 0xAC131722;     // Translucent elevated dark navy card
    public static final int CARD_INACTIVE_HOVER = 0xCC1B2130;
    public static final int CARD_ACTIVE = 0xBF281218;       // Warm crimson-tinted active glass card
    public static final int CARD_ACTIVE_HOVER = 0xD834161D;
    public static final int BORDER_SUBTLE = 0x1EFFFFFF;     // 1px low-opacity glass hairline
    public static final int BORDER_ACTIVE_SOFT = 0x5CE53935;// Soft low-opacity active crimson hairline
    public static final int BORDER_EXPANDED_SOFT = 0x80FF5252;
    public static final int DIVIDER = 0x18FFFFFF;           // Subtle translucent structural divider

    public static Component sans(String text) {
        return Component.literal(text == null ? "" : text).withStyle(style -> style.withFont(SANS_FONT));
    }

    public static Component sansBold(String text) {
        return Component.literal(text == null ? "" : text).withStyle(style -> style.withFont(SANS_FONT).withBold(true));
    }

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
        fillRoundedSides(graphics, x1, y1, x2, y2, radius, color, true, true);
    }

    public static void fillRoundedTop(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        fillRoundedSides(graphics, x1, y1, x2, y2, radius, color, true, false);
    }

    public static void fillRoundedBottom(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        fillRoundedSides(graphics, x1, y1, x2, y2, radius, color, false, true);
    }

    /** Rounds only the bottom-left corner (useful for a docked sidebar flush against a vertical divider). */
    public static void fillRoundedBottomLeft(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int color) {
        int c = fade(color);
        if ((c >>> 24) == 0 || x2 <= x1 || y2 <= y1) {
            return;
        }
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0) {
            graphics.fill(x1, y1, x2, y2, c);
            return;
        }
        SmoothUiAtlas.AtlasLayout layout = SmoothUiAtlas.getOrUpload();
        if (layout == null) {
            fillRoundedFallback(graphics, x1, y1, x2, y2, r, c, false, true);
            return;
        }
        SmoothUiAtlas.Region region = layout.solid(r);
        int halfTex = region.w() / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SmoothUiAtlas.ATLAS_ID,
                x1, y2 - r, region.u(), region.v() + halfTex, r, r, halfTex, halfTex, SmoothUiAtlas.ATLAS_W, SmoothUiAtlas.ATLAS_H, c);
        if (y2 - r > y1) {
            graphics.fill(x1, y1, x2, y2 - r, c);
        }
        if (x2 > x1 + r) {
            graphics.fill(x1 + r, y2 - r, x2, y2, c);
        }
    }

    private static void fillRoundedSides(
            GuiGraphicsExtractor graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int radius,
            int color,
            boolean roundTop,
            boolean roundBottom
    ) {
        int c = fade(color);
        if ((c >>> 24) == 0 || x2 <= x1 || y2 <= y1) {
            return;
        }
        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        if (r <= 0 || (!roundTop && !roundBottom)) {
            graphics.fill(x1, y1, x2, y2, c);
            return;
        }

        SmoothUiAtlas.AtlasLayout layout = SmoothUiAtlas.getOrUpload();
        if (layout == null) {
            fillRoundedFallback(graphics, x1, y1, x2, y2, r, c, roundTop, roundBottom);
            return;
        }

        drawQuadCorners(graphics, layout.solid(r), layout.atlasScale, x1, y1, x2, y2, r, roundTop, roundBottom, c);

        int topEnd = roundTop ? y1 + r : y1;
        int botStart = roundBottom ? y2 - r : y2;
        if (roundTop && x2 - r > x1 + r) {
            graphics.fill(x1 + r, y1, x2 - r, y1 + r, c);
        }
        if (roundBottom && x2 - r > x1 + r) {
            graphics.fill(x1 + r, y2 - r, x2 - r, y2, c);
        }
        if (botStart > topEnd) {
            graphics.fill(x1, topEnd, x2, botStart, c);
        }
    }

    private static void fillRoundedFallback(
            GuiGraphicsExtractor graphics,
            int x1,
            int y1,
            int x2,
            int y2,
            int r,
            int c,
            boolean roundTop,
            boolean roundBottom
    ) {
        for (int dy = 0; dy < r; dy++) {
            double d = r - dy - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - d * d));
            if (roundTop) {
                graphics.fill(x1 + inset, y1 + dy, x2 - inset, y1 + dy + 1, c);
            }
            if (roundBottom) {
                graphics.fill(x1 + inset, y2 - dy - 1, x2 - inset, y2 - dy, c);
            }
        }
        int midTop = roundTop ? y1 + r : y1;
        int midBot = roundBottom ? y2 - r : y2;
        if (midBot > midTop) {
            graphics.fill(x1, midTop, x2, midBot, c);
        }
    }

    public static void fillRoundedCard(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int radius, int bgColor, int borderColor) {
        if ((borderColor >>> 24) == 0 || x2 - x1 < 3 || y2 - y1 < 3) {
            fillRounded(graphics, x1, y1, x2, y2, radius, bgColor);
            return;
        }

        int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
        SmoothUiAtlas.AtlasLayout layout = SmoothUiAtlas.getOrUpload();
        if (layout == null) {
            fillRounded(graphics, x1, y1, x2, y2, r, borderColor);
            fillRounded(graphics, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), bgColor);
            return;
        }

        // Opaque cards fill full outer curve so border ring blends seamlessly; translucent pills inset fill to keep border pure
        if (((bgColor >>> 24) & 0xFF) >= 0xF0) {
            fillRounded(graphics, x1, y1, x2, y2, r, bgColor);
        } else {
            fillRounded(graphics, x1 + 1, y1 + 1, x2 - 1, y2 - 1, Math.max(0, r - 1), bgColor);
        }

        drawRoundedBorder(graphics, layout, x1, y1, x2, y2, r, fade(borderColor));
    }

    private static void drawRoundedBorder(
            GuiGraphicsExtractor graphics,
            SmoothUiAtlas.AtlasLayout layout,
            int x1,
            int y1,
            int x2,
            int y2,
            int r,
            int c
    ) {
        if (r <= 0) {
            graphics.fill(x1, y1, x2, y1 + 1, c);
            graphics.fill(x1, y2 - 1, x2, y2, c);
            graphics.fill(x1, y1 + 1, x1 + 1, y2 - 1, c);
            graphics.fill(x2 - 1, y1 + 1, x2, y2 - 1, c);
            return;
        }

        drawQuadCorners(graphics, layout.ring(r), layout.atlasScale, x1, y1, x2, y2, r, true, true, c);

        if (x2 - r > x1 + r) {
            graphics.fill(x1 + r, y1, x2 - r, y1 + 1, c);
            graphics.fill(x1 + r, y2 - 1, x2 - r, y2, c);
        }
        if (y2 - r > y1 + r) {
            graphics.fill(x1, y1 + r, x1 + 1, y2 - r, c);
            graphics.fill(x2 - 1, y1 + r, x2, y2 - r, c);
        }
    }

    private static void drawQuadCorners(
            GuiGraphicsExtractor graphics,
            SmoothUiAtlas.Region region,
            int atlasScale,
            int x1,
            int y1,
            int x2,
            int y2,
            int r,
            boolean drawTop,
            boolean drawBottom,
            int c
    ) {
        int halfTex = region.w() / 2;
        if (drawTop) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SmoothUiAtlas.ATLAS_ID,
                    x1, y1, region.u(), region.v(), r, r, halfTex, halfTex, SmoothUiAtlas.ATLAS_W, SmoothUiAtlas.ATLAS_H, c);
            graphics.blit(RenderPipelines.GUI_TEXTURED, SmoothUiAtlas.ATLAS_ID,
                    x2 - r, y1, region.u() + halfTex, region.v(), r, r, halfTex, halfTex, SmoothUiAtlas.ATLAS_W, SmoothUiAtlas.ATLAS_H, c);
        }
        if (drawBottom) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, SmoothUiAtlas.ATLAS_ID,
                    x1, y2 - r, region.u(), region.v() + halfTex, r, r, halfTex, halfTex, SmoothUiAtlas.ATLAS_W, SmoothUiAtlas.ATLAS_H, c);
            graphics.blit(RenderPipelines.GUI_TEXTURED, SmoothUiAtlas.ATLAS_ID,
                    x2 - r, y2 - r, region.u() + halfTex, region.v() + halfTex, r, r, halfTex, halfTex, SmoothUiAtlas.ATLAS_W, SmoothUiAtlas.ATLAS_H, c);
        }
    }

    public static void fillCircle(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        int c = fade(color);
        if ((c >>> 24) == 0) {
            return;
        }
        SmoothUiAtlas.AtlasLayout layout = SmoothUiAtlas.getOrUpload();
        if (layout == null) {
            fillRoundedFallback(graphics, centerX - radius, centerY - radius, centerX + radius, centerY + radius, radius, c, true, true);
            return;
        }
        SmoothUiAtlas.Region region = layout.solid(radius);
        int diam = radius * 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SmoothUiAtlas.ATLAS_ID,
                centerX - radius, centerY - radius,
                region.u(), region.v(),
                diam, diam,
                region.w(), region.h(),
                SmoothUiAtlas.ATLAS_W, SmoothUiAtlas.ATLAS_H,
                c);
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

    /** Renders a smooth hardware-filtered ambient radial gradient glow from the SDF atlas. */
    public static void drawRadialGlow(GuiGraphicsExtractor graphics, int centerX, int centerY, int radius, int color) {
        if (radius <= 0) {
            return;
        }
        int c = fade(color);
        if ((c >>> 24) == 0) {
            return;
        }
        SmoothUiAtlas.AtlasLayout layout = SmoothUiAtlas.getOrUpload();
        if (layout == null || layout.glow() == null) {
            return;
        }
        SmoothUiAtlas.Region glow = layout.glow();
        int diam = radius * 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, SmoothUiAtlas.ATLAS_ID,
                centerX - radius, centerY - radius,
                glow.u(), glow.v(),
                diam, diam,
                glow.w(), glow.h(),
                SmoothUiAtlas.ATLAS_W, SmoothUiAtlas.ATLAS_H,
                c);
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
