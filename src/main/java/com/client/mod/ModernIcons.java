package com.client.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Anti-aliased vector UI icons backed by {@link SmoothUiAtlas}, with crisp
 * integer fallbacks if called before GPU textures are ready.
 */
final class ModernIcons {

    private ModernIcons() {
    }

    private static boolean drawAtlasIcon(
            GuiGraphicsExtractor g,
            SmoothUiAtlas.IconKind kind,
            int x,
            int y,
            int w,
            int h,
            int color
    ) {
        SmoothUiAtlas.AtlasLayout layout = SmoothUiAtlas.getOrUpload();
        if (layout == null) {
            return false;
        }
        SmoothUiAtlas.Region region = layout.icon(kind);
        g.blit(
                RenderPipelines.GUI_TEXTURED,
                SmoothUiAtlas.ATLAS_ID,
                x, y,
                region.u(), region.v(),
                w, h,
                region.w(), region.h(),
                SmoothUiAtlas.ATLAS_W, SmoothUiAtlas.ATLAS_H,
                JelloTheme.fade(color)
        );
        return true;
    }

    /** 2x2 rounded-square module grid icon (10x10). */
    static void drawGridIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.GRID, x, y, 10, 10, color)) {
            return;
        }
        g.fill(x, y, x + 4, y + 4, color);
        g.fill(x + 6, y, x + 10, y + 4, color);
        g.fill(x, y + 6, x + 4, y + 10, color);
        g.fill(x + 6, y + 6, x + 10, y + 10, color);
    }

    /** Lightning bolt macro icon (10x10). */
    static void drawBoltIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.BOLT, x, y, 10, 10, color)) {
            return;
        }
        g.fill(x + 5, y, x + 8, y + 2, color);
        g.fill(x + 3, y + 2, x + 7, y + 4, color);
        g.fill(x + 1, y + 4, x + 9, y + 5, color);
        g.fill(x + 4, y + 5, x + 8, y + 7, color);
        g.fill(x + 2, y + 7, x + 6, y + 9, color);
        g.fill(x + 2, y + 9, x + 4, y + 10, color);
    }

    /** Horizontal sliders / client settings icon (10x10). */
    static void drawSlidersIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.SLIDERS, x, y, 10, 10, color)) {
            return;
        }
        g.fill(x, y + 2, x + 10, y + 3, color);
        g.fill(x + 2, y + 1, x + 4, y + 4, color);

        g.fill(x, y + 5, x + 10, y + 6, color);
        g.fill(x + 6, y + 4, x + 8, y + 7, color);

        g.fill(x, y + 8, x + 10, y + 9, color);
        g.fill(x + 3, y + 7, x + 5, y + 10, color);
    }

    /** Magnifying glass search icon (10x10). */
    static void drawSearchIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.SEARCH, x, y, 10, 10, color)) {
            return;
        }
        g.fill(x + 2, y + 1, x + 6, y + 2, color);
        g.fill(x + 2, y + 6, x + 6, y + 7, color);
        g.fill(x + 1, y + 2, x + 2, y + 6, color);
        g.fill(x + 6, y + 2, x + 7, y + 6, color);
        g.fill(x + 6, y + 6, x + 8, y + 8, color);
        g.fill(x + 7, y + 7, x + 9, y + 9, color);
    }

    /** Crisp 9x9 close 'X' icon centered inside box (cx, cy). */
    static void drawCloseIcon(GuiGraphicsExtractor g, int cx, int cy, int color) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.CLOSE, cx - 5, cy - 5, 10, 10, color)) {
            return;
        }
        for (int i = -3; i <= 3; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
            g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
        }
    }

    /** Mini tune icon (9x9) indicating configurable settings on a card. */
    static void drawTuneIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.TUNE, x, y, 9, 9, color)) {
            return;
        }
        g.fill(x, y + 1, x + 8, y + 2, color);
        g.fill(x + 5, y, x + 7, y + 3, color);
        g.fill(x, y + 5, x + 8, y + 6, color);
        g.fill(x + 1, y + 4, x + 3, y + 7, color);
    }

    /** Curved 4-point Sestia brand star emblem centered at (cx, cy). */
    static void drawBrandEmblem(GuiGraphicsExtractor g, int cx, int cy, int outerColor, int coreColor) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.BRAND, cx - 6, cy - 6, 12, 12, outerColor)) {
            JelloTheme.fillCircle(g, cx, cy, 2, coreColor);
            return;
        }
        for (int dy = -5; dy <= 5; dy++) {
            int half = 5 - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, outerColor);
        }
        for (int dy = -2; dy <= 2; dy++) {
            int half = 2 - Math.abs(dy);
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, coreColor);
        }
    }

    /** Crisp checkmark (8x8). */
    static void drawCheckIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.CHECK, x, y, 8, 8, color)) {
            return;
        }
        g.fill(x + 1, y + 4, x + 2, y + 6, color);
        g.fill(x + 2, y + 5, x + 4, y + 7, color);
        g.fill(x + 3, y + 4, x + 5, y + 6, color);
        g.fill(x + 4, y + 3, x + 6, y + 5, color);
        g.fill(x + 5, y + 2, x + 7, y + 4, color);
    }

    /** Right-facing chevron indicator (7x8). */
    static void drawChevronRight(GuiGraphicsExtractor g, int x, int y, int color) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.CHEVRON_RIGHT, x, y, 7, 8, color)) {
            return;
        }
        g.fill(x, y, x + 2, y + 2, color);
        g.fill(x + 1, y + 1, x + 3, y + 3, color);
        g.fill(x + 2, y + 2, x + 4, y + 4, color);
        g.fill(x + 1, y + 3, x + 3, y + 5, color);
        g.fill(x, y + 4, x + 2, y + 6, color);
    }

    /** Compact keyboard/bind emblem (9x9) for Keybinds HUD without tofu glyphs. */
    static void drawKeyboardIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        if (drawAtlasIcon(g, SmoothUiAtlas.IconKind.KEYBOARD, x, y, 9, 9, color)) {
            return;
        }
        g.fill(x, y + 2, x + 9, y + 7, color);
        g.fill(x + 1, y + 3, x + 8, y + 6, 0xFF140B0E);
        g.fill(x + 2, y + 4, x + 7, y + 5, color);
    }

    /** HUD layout frame icon (10x10) for the GUI tab. */
    static void drawLayoutIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 10, y + 1, color);
        g.fill(x, y + 9, x + 10, y + 10, color);
        g.fill(x, y, x + 1, y + 10, color);
        g.fill(x + 9, y, x + 10, y + 10, color);
        g.fill(x + 2, y + 2, x + 5, y + 5, color);
        g.fill(x + 5, y + 6, x + 8, y + 8, color);
    }

    /** Crisp symmetrical 10x10 wing emblem for the Cosmetic sidebar tab and wing cards. */
    static void drawWingIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        int c = JelloTheme.fade(color);
        g.fill(x, y + 1, x + 3, y + 3, c);
        g.fill(x + 7, y + 1, x + 10, y + 3, c);
        g.fill(x + 1, y + 3, x + 4, y + 5, c);
        g.fill(x + 6, y + 3, x + 9, y + 5, c);
        g.fill(x + 2, y + 5, x + 8, y + 6, c);
        g.fill(x + 2, y + 6, x + 4, y + 8, c);
        g.fill(x + 6, y + 6, x + 8, y + 8, c);
        g.fill(x + 3, y + 8, x + 4, y + 9, c);
        g.fill(x + 6, y + 8, x + 7, y + 9, c);
    }

    /** 5-pointed favorite star icon (9x9 or 10x10) in filled or outline style. */
    static void drawStarIcon(GuiGraphicsExtractor g, int x, int y, int size, boolean filled, int color) {
        SmoothUiAtlas.IconKind kind = filled ? SmoothUiAtlas.IconKind.STAR_FILLED : SmoothUiAtlas.IconKind.STAR_OUTLINE;
        if (drawAtlasIcon(g, kind, x, y, size, size, color)) {
            return;
        }
        g.fill(x + 4, y, x + 5, y + 2, color);
        g.fill(x + 1, y + 3, x + 8, y + 4, color);
        if (filled) {
            g.fill(x + 2, y + 2, x + 7, y + 6, color);
        }
        g.fill(x + 2, y + 6, x + 4, y + 8, color);
        g.fill(x + 5, y + 6, x + 7, y + 8, color);
    }
}

