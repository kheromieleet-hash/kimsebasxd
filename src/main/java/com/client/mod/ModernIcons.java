package com.client.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Crisp pixel-aligned vector icons rendered via GuiGraphicsExtractor primitives,
 * avoiding inconsistent or missing Unicode emoji glyphs in Minecraft bitmap fonts.
 */
final class ModernIcons {

    private ModernIcons() {
    }

    /** 2x2 rounded-square module grid icon (10x10). */
    static void drawGridIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 4, y + 4, color);
        g.fill(x + 6, y, x + 10, y + 4, color);
        g.fill(x, y + 6, x + 4, y + 10, color);
        g.fill(x + 6, y + 6, x + 10, y + 10, color);
    }

    /** Eye / ESP studio radar icon (10x10). */
    static void drawEyeIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 2, y + 2, x + 8, y + 3, color);
        g.fill(x + 1, y + 3, x + 3, y + 4, color);
        g.fill(x + 7, y + 3, x + 9, y + 4, color);
        g.fill(x, y + 4, x + 2, y + 6, color);
        g.fill(x + 8, y + 4, x + 10, y + 6, color);
        g.fill(x + 1, y + 6, x + 3, y + 7, color);
        g.fill(x + 7, y + 6, x + 9, y + 7, color);
        g.fill(x + 2, y + 7, x + 8, y + 8, color);
        g.fill(x + 4, y + 4, x + 6, y + 6, color);
    }

    /** Lightning bolt macro icon (10x10). */
    static void drawBoltIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 5, y, x + 8, y + 2, color);
        g.fill(x + 3, y + 2, x + 7, y + 4, color);
        g.fill(x + 1, y + 4, x + 9, y + 5, color);
        g.fill(x + 4, y + 5, x + 8, y + 7, color);
        g.fill(x + 2, y + 7, x + 6, y + 9, color);
        g.fill(x + 2, y + 9, x + 4, y + 10, color);
    }

    /** Horizontal sliders / client settings icon (10x10). */
    static void drawSlidersIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y + 2, x + 10, y + 3, color);
        g.fill(x + 2, y + 1, x + 4, y + 4, color);

        g.fill(x, y + 5, x + 10, y + 6, color);
        g.fill(x + 6, y + 4, x + 8, y + 7, color);

        g.fill(x, y + 8, x + 10, y + 9, color);
        g.fill(x + 3, y + 7, x + 5, y + 10, color);
    }

    /** Magnifying glass search icon (10x10). */
    static void drawSearchIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x + 2, y + 1, x + 6, y + 2, color);
        g.fill(x + 2, y + 6, x + 6, y + 7, color);
        g.fill(x + 1, y + 2, x + 2, y + 6, color);
        g.fill(x + 6, y + 2, x + 7, y + 6, color);
        g.fill(x + 6, y + 6, x + 8, y + 8, color);
        g.fill(x + 7, y + 7, x + 9, y + 9, color);
    }

    /** Crisp 7x7 close 'X' icon centered inside box (cx, cy). */
    static void drawCloseIcon(GuiGraphicsExtractor g, int cx, int cy, int color) {
        for (int i = -3; i <= 3; i++) {
            g.fill(cx + i, cy + i, cx + i + 1, cy + i + 1, color);
            g.fill(cx + i, cy - i, cx + i + 1, cy - i + 1, color);
        }
    }

    /** Three-bar mini tune icon (8x8) indicating configurable settings on a card. */
    static void drawTuneIcon(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y + 1, x + 8, y + 2, color);
        g.fill(x + 5, y, x + 7, y + 3, color);
        g.fill(x, y + 5, x + 8, y + 6, color);
        g.fill(x + 1, y + 4, x + 3, y + 7, color);
    }

    /** Diamond brand emblem (10x10). */
    static void drawBrandEmblem(GuiGraphicsExtractor g, int cx, int cy, int outerColor, int coreColor) {
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
        g.fill(x + 1, y + 4, x + 2, y + 6, color);
        g.fill(x + 2, y + 5, x + 4, y + 7, color);
        g.fill(x + 3, y + 4, x + 5, y + 6, color);
        g.fill(x + 4, y + 3, x + 6, y + 5, color);
        g.fill(x + 5, y + 2, x + 7, y + 4, color);
    }

    /** Right-facing chevron indicator (5x7). */
    static void drawChevronRight(GuiGraphicsExtractor g, int x, int y, int color) {
        g.fill(x, y, x + 2, y + 2, color);
        g.fill(x + 1, y + 1, x + 3, y + 3, color);
        g.fill(x + 2, y + 2, x + 4, y + 4, color);
        g.fill(x + 1, y + 3, x + 3, y + 5, color);
        g.fill(x, y + 4, x + 2, y + 6, color);
    }
}
