package com.client.mod;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi-theme palette manager (Sestia Leaf with 5 strains + translucent Crimson Leaf) and rounded drawing utilities.
 */
public final class JelloTheme {
    public static final int THEME_SESTIA = 0;
    public static final int THEME_SESTIA_CRIMSON = 1;
    public static final int THEME_COUNT = 2;

    public static final int STRAIN_NEON_EMERALD = 0;
    public static final int STRAIN_DEEP_FOREST = 1;
    public static final int STRAIN_FROST_MINT = 2;
    public static final int STRAIN_GOLD_LEAF = 3;
    public static final int STRAIN_OBSIDIAN_FROST = 4;
    public static final int STRAIN_COUNT = 5;
    public static final int CRIMSON_LEAF_TINT_RGB = 0xFF4757;

    public static final float MIN_GLASS_OPACITY = 0.55f;
    public static final float MAX_GLASS_OPACITY = 1.00f;
    public static final float DEFAULT_GLASS_OPACITY = 0.78f;

    private static int currentTheme = THEME_SESTIA;
    private static int currentLeafStrain = STRAIN_NEON_EMERALD;
    private static float currentGlassOpacity = DEFAULT_GLASS_OPACITY;
    private static int currentLeafTintRgb = 0x5AFCA8;

    public static int ACCENT = 0xFF34D399;
    public static int ACCENT_HOVER = 0xFF6EE7B7;
    public static int PANEL = 0xC8050708;
    public static int HEADER = 0xDC090D0E;
    public static int BORDER = 0xE65AFCA8;
    public static int ROW_HOVER = 0x2E5AFCA8;
    public static int TEXT = 0xFFF6FFFA;
    public static int TEXT_MUTED = 0xFFB0CFC2;
    public static int TEXT_DIM = 0xFF86EFAC;
    public static int TRACK = 0x485AFCA8;
    public static int SWITCH_ON = 0xFF4ADE9B;
    public static int SWITCH_ON_HOVER = 0xFF86FFD0;
    public static int SWITCH_OFF = 0xCC161D20;
    public static int SWITCH_OFF_HOVER = 0xE0202A2E;
    public static int SLIDER_BAR = 0xFF5AFCA8;
    public static int SLIDER_BAR_HOVER = 0xFFA7F3D0;
    public static int SLIDER_TRACK = 0xCC111719;
    public static int SCROLLBAR_TRACK = 0x33000000;
    public static int SCROLLBAR_THUMB = 0xCC5AFCA8;
    public static int SCROLLBAR_THUMB_HOVER = 0xFFA7F3D0;
    public static int BACKDROP_DIM = 0x96000000;
    public static int WINDOW_BORDER = 0x665AFCA8;

    public static int WINDOW_BG = 0xE2050708;
    public static int HEADER_BG = 0xB8090D0E;
    public static int SIDEBAR_BG = 0xA6080B0C;
    public static int CONTAINER_BG = 0x9C0D1214;
    public static int CARD_INACTIVE = 0x7A0D1214;
    public static int CARD_INACTIVE_HOVER = 0xA6172225;
    public static int CARD_ACTIVE = 0xBA143025;
    public static int CARD_ACTIVE_HOVER = 0xCE1C4233;
    public static int BORDER_SUBTLE = 0x38A7F3D0;
    public static int BORDER_ACTIVE_SOFT = 0xFF5AFCA8;
    public static int BORDER_EXPANDED_SOFT = 0xFFA7F3D0;
    public static int DIVIDER = 0x2E5AFCA8;

    public static int getCurrentTheme() {
        return currentTheme;
    }

    public static boolean isLeafTheme() {
        return currentTheme == THEME_SESTIA || currentTheme == THEME_SESTIA_CRIMSON;
    }

    public static int getCurrentLeafStrain() {
        return currentLeafStrain;
    }

    public static float getCurrentGlassOpacity() {
        return currentGlassOpacity;
    }

    public static int getLeafTintRgb() {
        return currentLeafTintRgb;
    }

    public static int normalizeLeafStrain(int strain) {
        return (strain >= 0 && strain < STRAIN_COUNT) ? strain : STRAIN_NEON_EMERALD;
    }

    public static float normalizeGlassOpacity(float glassOpacity) {
        if (!Float.isFinite(glassOpacity)) {
            return DEFAULT_GLASS_OPACITY;
        }
        return Mth.clamp(glassOpacity, MIN_GLASS_OPACITY, MAX_GLASS_OPACITY);
    }

    public static int getLeafStrainTintRgb(int strain) {
        return switch (normalizeLeafStrain(strain)) {
            case STRAIN_DEEP_FOREST -> 0x86EFAC;
            case STRAIN_FROST_MINT -> 0x7EF9F0;
            case STRAIN_GOLD_LEAF -> 0xFDE68A;
            case STRAIN_OBSIDIAN_FROST -> 0xE2E8F0;
            default -> 0x5AFCA8;
        };
    }

    public static String getLeafStrainName(int strain) {
        return switch (normalizeLeafStrain(strain)) {
            case STRAIN_DEEP_FOREST -> "Deep Forest";
            case STRAIN_FROST_MINT -> "Frost Mint";
            case STRAIN_GOLD_LEAF -> "Gold Leaf";
            case STRAIN_OBSIDIAN_FROST -> "Obsidian Frost";
            default -> "Neon Emerald";
        };
    }

    public static String getLeafStrainShortName(int strain) {
        return switch (normalizeLeafStrain(strain)) {
            case STRAIN_DEEP_FOREST -> "Forest";
            case STRAIN_FROST_MINT -> "Frost";
            case STRAIN_GOLD_LEAF -> "Gold";
            case STRAIN_OBSIDIAN_FROST -> "Obsidian";
            default -> "Emerald";
        };
    }

    public static int[] getLeafStrainPalette(int strain) {
        return switch (normalizeLeafStrain(strain)) {
            case STRAIN_DEEP_FOREST -> new int[]{0xFF86EFAC, 0xFFDCFCE7, 0xFF0D1210};
            case STRAIN_FROST_MINT -> new int[]{0xFF7EF9F0, 0xFFCCFBF1, 0xFF0C1216};
            case STRAIN_GOLD_LEAF -> new int[]{0xFFFDE68A, 0xFFFEF9C3, 0xFF101114};
            case STRAIN_OBSIDIAN_FROST -> new int[]{0xFFE2E8F0, 0xFF64748B, 0xFF050608};
            default -> new int[]{0xFF5AFCA8, 0xFFA7F3D0, 0xFF0D1214};
        };
    }

    public static String getThemeName(int index) {
        return index == THEME_SESTIA_CRIMSON ? "Crimson" : "Sestia Leaf";
    }

    public static void applyTheme(int index) {
        applyTheme(index, currentLeafStrain, currentGlassOpacity);
    }

    public static void applyFromConfig(ModConfig config) {
        if (config == null) {
            applyTheme(THEME_SESTIA, STRAIN_NEON_EMERALD, DEFAULT_GLASS_OPACITY);
            return;
        }
        applyTheme(config.guiTheme, config.leafStrain, config.guiGlassOpacity);
    }

    public static void applyTheme(int index, int leafStrain, float glassOpacity) {
        int normalized = (index >= 0 && index < THEME_COUNT) ? index : THEME_SESTIA;
        currentTheme = normalized;
        currentLeafStrain = normalizeLeafStrain(leafStrain);
        currentGlassOpacity = normalizeGlassOpacity(glassOpacity);
        if (normalized == THEME_SESTIA_CRIMSON) {
            currentLeafTintRgb = CRIMSON_LEAF_TINT_RGB;
            applyCrimsonLeafTokens(currentGlassOpacity);
        } else {
            currentLeafTintRgb = getLeafStrainTintRgb(currentLeafStrain);
            applySestiaLeafStrain(currentLeafStrain, currentGlassOpacity);
        }
    }

    private static void applyCrimsonLeafTokens(float glassOpacity) {
        setLeafStrainTokens(
                0xFFFF2E43, 0xFFFF6B7A,
                0xC8090506, 0xDC0E080A,
                0xE6FF4757, 0x2EFF4757,
                0xFFFFF7F8, 0xFFD4B8BE, 0xFFFF8A96,
                0x48FF4757, 0xFFFF3B4F, 0xFFFF7A88,
                0xCC211619, 0xE0301F24,
                0xFFFF4757, 0xFFFF9AA4, 0xCC191113,
                0x33000000, 0xCCFF4757, 0xFFFF9AA4,
                0x96000000, 0x66FF4757,
                0xE2090506, 0xB80E080A, 0xA60B0708, 0x9C140C0E,
                0x7A140C0E, 0xA6241519, 0xBA381219, 0xCE4D1822,
                0x38FF9AA4, 0xFFFF4757, 0xFFFF9AA4, 0x2EFF4757,
                glassOpacity
        );
    }

    private static void applySestiaLeafStrain(int strain, float glassOpacity) {
        switch (normalizeLeafStrain(strain)) {
            case STRAIN_DEEP_FOREST -> setLeafStrainTokens(
                    0xFF4ADE80, 0xFF86EFAC,
                    0xC8050706, 0xDC090D0A,
                    0xE686EFAC, 0x2E86EFAC,
                    0xFFF7FFF9, 0xFFB5CEBC, 0xFFBBF7D0,
                    0x4886EFAC, 0xFF6EE787, 0xFFBBF7D0,
                    0xCC161E18, 0xE0202C23,
                    0xFF86EFAC, 0xFFDCFCE7, 0xCC111713,
                    0x33000000, 0xCC86EFAC, 0xFFDCFCE7,
                    0x96000000, 0x6686EFAC,
                    0xE2050706, 0xB8090D0A, 0xA6080B09, 0x9C0D1210,
                    0x7A0D1210, 0xA617221B, 0xBA16301E, 0xCE1F422A,
                    0x38BBF7D0, 0xFF86EFAC, 0xFFDCFCE7, 0x2E86EFAC,
                    glassOpacity
            );
            case STRAIN_FROST_MINT -> setLeafStrainTokens(
                    0xFF45E3D3, 0xFF7EF9F0,
                    0xC8050709, 0xDC090C10,
                    0xE67EF9F0, 0x2E7EF9F0,
                    0xFFF5FFFE, 0xFFAFCACE, 0xFF99F6E4,
                    0x487EF9F0, 0xFF5EEAD4, 0xFF99F6E4,
                    0xCC151D22, 0xE01F2B32,
                    0xFF7EF9F0, 0xFFCCFBF1, 0xCC10161B,
                    0x33000000, 0xCC7EF9F0, 0xFFCCFBF1,
                    0x96000000, 0x667EF9F0,
                    0xE2050709, 0xB8090C10, 0xA6080A0E, 0x9C0C1216,
                    0x7A0C1216, 0xA6162129, 0xBA132D33, 0xCE1B3E46,
                    0x38CCFBF1, 0xFF7EF9F0, 0xFFCCFBF1, 0x2E7EF9F0,
                    glassOpacity
            );
            case STRAIN_GOLD_LEAF -> setLeafStrainTokens(
                    0xFFFACC15, 0xFFFDE047,
                    0xC8070709, 0xDC0B0B0F,
                    0xE6FDE68A, 0x2EFDE68A,
                    0xFFFFFEF7, 0xFFCFC8B4, 0xFFFEF08A,
                    0x48FDE68A, 0xFFFDE047, 0xFFFEF08A,
                    0xCC1C1B18, 0xE0282721,
                    0xFFFDE68A, 0xFFFEF9C3, 0xCC151512,
                    0x33000000, 0xCCFDE68A, 0xFFFEF9C3,
                    0x96000000, 0x66FDE68A,
                    0xE2070709, 0xB80B0B0F, 0xA609090D, 0x9C101114,
                    0x7A101114, 0xA61D1E23, 0xBA2C2818, 0xCE3B3520,
                    0x38FEF08A, 0xFFFDE68A, 0xFFFEF9C3, 0x2EFDE68A,
                    glassOpacity
            );
            case STRAIN_OBSIDIAN_FROST -> setLeafStrainTokens(
                    0xFF64748B, 0xFF94A3B8,
                    0xC8050608, 0xDC090B0F,
                    0xE6E2E8F0, 0x2EE2E8F0,
                    0xFFF8FAFC, 0xFF94A3B8, 0xFFCBD5E1,
                    0x48E2E8F0, 0xFF94A3B8, 0xFFE2E8F0,
                    0xCC161B22, 0xE0212833,
                    0xFFCBD5E1, 0xFFF8FAFC, 0xCC11151B,
                    0x33000000, 0xCCE2E8F0, 0xFFF8FAFC,
                    0x96000000, 0x66E2E8F0,
                    0xE2050608, 0xB8090B0F, 0xA6080A0D, 0x9C0D1016,
                    0x7A0D1016, 0xA6191F2A, 0xBA1E2633, 0xCE293446,
                    0x38CBD5E1, 0xFFE2E8F0, 0xFFF8FAFC, 0x2EE2E8F0,
                    glassOpacity
            );
            default -> setLeafStrainTokens(
                    0xFF34D399, 0xFF6EE7B7,
                    0xC8050708, 0xDC090D0E,
                    0xE65AFCA8, 0x2E5AFCA8,
                    0xFFF6FFFA, 0xFFB0CFC2, 0xFF86EFAC,
                    0x485AFCA8, 0xFF4ADE9B, 0xFF86FFD0,
                    0xCC161D20, 0xE0202A2E,
                    0xFF5AFCA8, 0xFFA7F3D0, 0xCC111719,
                    0x33000000, 0xCC5AFCA8, 0xFFA7F3D0,
                    0x96000000, 0x665AFCA8,
                    0xE2050708, 0xB8090D0E, 0xA6080B0C, 0x9C0D1214,
                    0x7A0D1214, 0xA6172225, 0xBA143025, 0xCE1C4233,
                    0x38A7F3D0, 0xFF5AFCA8, 0xFFA7F3D0, 0x2E5AFCA8,
                    glassOpacity
            );
        }
    }

    private static void setLeafStrainTokens(
            int accent, int accentHover,
            int panel, int header,
            int border, int rowHover,
            int text, int textMuted, int textDim,
            int track, int switchOn, int switchOnHover,
            int switchOff, int switchOffHover,
            int sliderBar, int sliderBarHover, int sliderTrack,
            int scrollbarTrack, int scrollbarThumb, int scrollbarThumbHover,
            int backdropDim, int windowBorder,
            int windowBg, int headerBg, int sidebarBg, int containerBg,
            int cardInactive, int cardInactiveHover, int cardActive, int cardActiveHover,
            int borderSubtle, int borderActiveSoft, int borderExpandedSoft, int divider,
            float glassOpacity
    ) {
        ACCENT = accent;
        ACCENT_HOVER = accentHover;
        PANEL = scaleGlassAlpha(panel, glassOpacity);
        HEADER = scaleGlassAlpha(header, glassOpacity);
        BORDER = border;
        ROW_HOVER = rowHover;
        TEXT = text;
        TEXT_MUTED = textMuted;
        TEXT_DIM = textDim;
        TRACK = track;
        SWITCH_ON = switchOn;
        SWITCH_ON_HOVER = switchOnHover;
        SWITCH_OFF = switchOff;
        SWITCH_OFF_HOVER = switchOffHover;
        SLIDER_BAR = sliderBar;
        SLIDER_BAR_HOVER = sliderBarHover;
        SLIDER_TRACK = sliderTrack;
        SCROLLBAR_TRACK = scrollbarTrack;
        SCROLLBAR_THUMB = scrollbarThumb;
        SCROLLBAR_THUMB_HOVER = scrollbarThumbHover;
        BACKDROP_DIM = backdropDim;
        WINDOW_BORDER = windowBorder;
        WINDOW_BG = scaleGlassAlpha(windowBg, glassOpacity);
        HEADER_BG = scaleGlassAlpha(headerBg, glassOpacity);
        SIDEBAR_BG = scaleGlassAlpha(sidebarBg, glassOpacity);
        CONTAINER_BG = scaleGlassAlpha(containerBg, glassOpacity);
        CARD_INACTIVE = scaleGlassAlpha(cardInactive, glassOpacity);
        CARD_INACTIVE_HOVER = scaleGlassAlpha(cardInactiveHover, glassOpacity);
        CARD_ACTIVE = scaleGlassAlpha(cardActive, glassOpacity);
        CARD_ACTIVE_HOVER = scaleGlassAlpha(cardActiveHover, glassOpacity);
        BORDER_SUBTLE = borderSubtle;
        BORDER_ACTIVE_SOFT = borderActiveSoft;
        BORDER_EXPANDED_SOFT = borderExpandedSoft;
        DIVIDER = divider;
    }

    static int scaleGlassAlpha(int argb, float glassOpacity) {
        float normalized = normalizeGlassOpacity(glassOpacity);
        if (Math.abs(normalized - DEFAULT_GLASS_OPACITY) < 1.0e-4f) {
            return argb;
        }
        int baseA = (argb >>> 24) & 0xFF;
        float ratio = normalized / DEFAULT_GLASS_OPACITY;
        int scaledA = Math.max(0x38, Math.min(0xFA, Math.round(baseA * ratio)));
        return (scaledA << 24) | (argb & 0x00FFFFFF);
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
        final int step = 4;
        for (int x = 0; x < w; x += step) {
            int segEnd = Math.min(w, x + step);
            float t = (x + (segEnd - x) * 0.5f) / (float) w;
            int col = lerpColor(colorFrom, colorTo, t);
            graphics.fill(x1 + x, y1, x1 + segEnd, y2, fade(col));
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
            float clampedDt = Math.max(0.0f, Math.min(0.05f, dt));
            int steps = Math.max(1, (int) Math.ceil(clampedDt / 0.004f));
            float h = clampedDt / steps;
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

    public record HudStatRow(String label, String value, int accentColor, int valueColor, float progress) {
        public HudStatRow(String label, String value, int accentColor, int valueColor) {
            this(label, value, accentColor, valueColor, -1.0f);
        }
    }

    public static final int HUD_CARD_HEADER_HEIGHT = 16;
    public static final int HUD_CARD_ROW_HEIGHT = 13;

    public static int hudCardBackground() {
        return (0xD4 << 24) | (CONTAINER_BG & 0x00FFFFFF);
    }

    public static int hudHeaderBackground(boolean highlighted) {
        if (highlighted) {
            int mixed = lerpColor(HEADER_BG, ACCENT, 0.18f);
            return (0xF2 << 24) | (mixed & 0x00FFFFFF);
        }
        return (0xEE << 24) | (HEADER_BG & 0x00FFFFFF);
    }

    public static int hudCardBorder(boolean highlighted) {
        int mixed = highlighted ? ACCENT_HOVER : lerpColor(DIVIDER, ACCENT, 0.30f);
        int alpha = highlighted ? 0x99 : 0x5A;
        return (alpha << 24) | (mixed & 0x00FFFFFF);
    }

    public static int[] resolveHudCardBounds(
            Font font, int screenW, int screenH,
            String title, List<HudStatRow> rows,
            int minWidth, int cfgX, int cfgY, int defaultX, int defaultY
    ) {
        int maxW = font != null ? font.width(title) + 18 : minWidth;
        int extraProgressH = 0;
        if (rows != null) {
            for (HudStatRow row : rows) {
                if (font != null) {
                    int rowW = 16 + font.width(row.label()) + 10 + font.width(row.value()) + 8;
                    if (rowW > maxW) {
                        maxW = rowW;
                    }
                }
                if (row.progress() >= 0.0f) {
                    extraProgressH += 5;
                }
            }
        }
        int rowCount = rows != null ? rows.size() : 0;
        int w = Math.max(minWidth, maxW);
        int h = HUD_CARD_HEADER_HEIGHT + rowCount * HUD_CARD_ROW_HEIGHT + extraProgressH + 5;
        int rawX = cfgX >= 0 ? cfgX : defaultX;
        int rawY = cfgY >= 0 ? cfgY : defaultY;
        int x = Mth.clamp(rawX, 2, Math.max(2, screenW - w - 2));
        int y = Mth.clamp(rawY, 2, Math.max(2, screenH - h - 2));
        return new int[]{x, y, w, h};
    }

    public static void renderHudCard(
            GuiGraphicsExtractor graphics, Font font,
            int x, int y, int width, int height,
            String title, List<HudStatRow> rows
    ) {
        boolean unified = ModConfig.getInstance().hudUnifiedGlass;
        int bg = unified ? hudCardBackground() : 0xD8120B0E;
        int border = unified ? hudCardBorder(false) : 0x55242431;
        int hdrBg = unified ? hudHeaderBackground(false) : 0xF0140B0E;

        fillRoundedCard(graphics, x, y, x + width, y + height, 5, bg, border);
        fillRoundedTop(graphics, x + 1, y + 1, x + width - 1, y + HUD_CARD_HEADER_HEIGHT, 4, hdrBg);
        int halfW = width / 2;
        fill(graphics, x + 1, y + HUD_CARD_HEADER_HEIGHT - 1, x + halfW, y + HUD_CARD_HEADER_HEIGHT, ACCENT);
        fill(graphics, x + halfW, y + HUD_CARD_HEADER_HEIGHT - 1, x + width - 1, y + HUD_CARD_HEADER_HEIGHT, ACCENT_HOVER);

        textCentered(graphics, font, title, x + width / 2, y + 4, unified ? TEXT : 0xFFFFFFFF);

        int curY = y + HUD_CARD_HEADER_HEIGHT + 3;
        int labelCol = unified ? TEXT_MUTED : 0xFFD8D8D8;
        for (HudStatRow row : rows) {
            fill(graphics, x + 5, curY + 4, x + 8, curY + 7, row.accentColor());
            text(graphics, font, row.label(), x + 11, curY + 2, labelCol);
            int valW = font.width(row.value());
            text(graphics, font, row.value(), x + width - 6 - valW, curY + 2, row.valueColor());
            curY += HUD_CARD_ROW_HEIGHT;

            if (row.progress() >= 0.0f) {
                int barX = x + 6;
                int barW = Math.max(16, width - 12);
                int barY = curY;
                fillRounded(graphics, barX, barY, barX + barW, barY + 2, 1, 0x26FFFFFF);
                float clamped = Mth.clamp(row.progress(), 0.0f, 1.0f);
                int fillW = Math.round(barW * clamped);
                if (fillW > 0) {
                    int fillColor = lerpColor(ACCENT, ACCENT_HOVER, clamped);
                    fillRounded(graphics, barX, barY, barX + Math.max(2, fillW), barY + 2, 1, fillColor);
                }
                curY += 5;
            }
        }
    }
}
