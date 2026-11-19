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
 * Multi-theme palette manager (Sestia OLED, Transparent, Light, Monokai Pro, Dark) and rounded drawing utilities.
 */
public final class JelloTheme {
    public static final int THEME_SESTIA = 0;
    public static final int THEME_TRANSPARENT = 1;
    public static final int THEME_LIGHT = 2;
    public static final int THEME_MONOKAI_PRO = 3;
    public static final int THEME_DARK = 4;
    public static final int THEME_CYBER_CYAN = 5;
    public static final int THEME_EMERALD_TOXIC = 6;
    public static final int THEME_SOLAR_GOLD = 7;
    public static final int THEME_NORD_FROST = 8;
    public static final int THEME_BLOOD_MOON = 9;
    public static final int THEME_COUNT = 10;

    public static int ACCENT = 0xFFFF1A2B;
    public static int ACCENT_HOVER = 0xFFFF4757;
    public static int PANEL = 0xF508080C;
    public static int HEADER = 0xFC0A0A0E;
    public static int BORDER = 0xE6FF1A2B;
    public static int ROW_HOVER = 0x38FF1A2B;
    public static int TEXT = 0xFFFFFFFF;
    public static int TEXT_MUTED = 0xFFC8D2E0;
    public static int TEXT_DIM = 0xFFFF5A67;
    public static int TRACK = 0x55FF1A2B;
    public static int SWITCH_ON = 0xFFFF1A2B;
    public static int SWITCH_ON_HOVER = 0xFFFF4757;
    public static int SWITCH_OFF = 0xFF1F2029;
    public static int SWITCH_OFF_HOVER = 0xFF2D2E3B;
    public static int SLIDER_BAR = 0xFFFF1A2B;
    public static int SLIDER_BAR_HOVER = 0xFFFF4757;
    public static int SLIDER_TRACK = 0xFF1F2029;
    public static int SCROLLBAR_TRACK = 0x33000000;
    public static int SCROLLBAR_THUMB = 0xFFFF1A2B;
    public static int SCROLLBAR_THUMB_HOVER = 0xFFFF4757;
    public static int BACKDROP_DIM = 0x80000000;
    public static int WINDOW_BORDER = 0x48FFFFFF;

    public static int WINDOW_BG = 0xF6060609;
    public static int HEADER_BG = 0xFC0A0A0E;
    public static int SIDEBAR_BG = 0xF8060608;
    public static int CONTAINER_BG = 0xF20C0C11;
    public static int CARD_INACTIVE = 0xF2111117;
    public static int CARD_INACTIVE_HOVER = 0xF81A1A23;
    public static int CARD_ACTIVE = 0xF6340910;
    public static int CARD_ACTIVE_HOVER = 0xFA450D17;
    public static int BORDER_SUBTLE = 0x44FFFFFF;
    public static int BORDER_ACTIVE_SOFT = 0xFFFF2E3E;
    public static int BORDER_EXPANDED_SOFT = 0xFFFF5260;
    public static int DIVIDER = 0x2EFFFFFF;

    public static String getThemeName(int index) {
        return switch (index) {
            case THEME_TRANSPARENT -> "Transparent";
            case THEME_LIGHT -> "Light";
            case THEME_MONOKAI_PRO -> "Monokai Pro";
            case THEME_DARK -> "Dark";
            case THEME_CYBER_CYAN -> "Cyber Cyan";
            case THEME_EMERALD_TOXIC -> "Emerald Toxic";
            case THEME_SOLAR_GOLD -> "Solar Gold";
            case THEME_NORD_FROST -> "Nord Frost";
            case THEME_BLOOD_MOON -> "Blood Moon";
            default -> "Sestia OLED";
        };
    }

    public static void applyTheme(int index) {
        switch (index) {
            case THEME_TRANSPARENT -> {
                ACCENT = 0xFFB84242;
                ACCENT_HOVER = 0xFFBD5C5C;
                PANEL = 0x24A7A7A7;
                HEADER = 0x28A7A7A7;
                BORDER = 0x88B84242;
                ROW_HOVER = 0x28B86060;
                TEXT = 0xFFDCDCDC;
                TEXT_MUTED = 0xFFBEBEBE;
                TEXT_DIM = 0xFFEA6060;
                TRACK = 0x38A74545;
                SWITCH_ON = 0xFF5AAF5A;
                SWITCH_ON_HOVER = 0xFF69BD69;
                SWITCH_OFF = 0xFFA74545;
                SWITCH_OFF_HOVER = 0xFFBD5C5C;
                SLIDER_BAR = 0xFFA74545;
                SLIDER_BAR_HOVER = 0xFFBD5C5C;
                SLIDER_TRACK = 0x44A0A0A0;
                SCROLLBAR_TRACK = 0x32A0A0A0;
                SCROLLBAR_THUMB = 0x64A0A0A0;
                SCROLLBAR_THUMB_HOVER = 0x99C8C8C8;
                BACKDROP_DIM = 0x38000000;
                WINDOW_BORDER = 0x3CA0A0A0;
                WINDOW_BG = 0x22B6B6B6;
                HEADER_BG = 0x26A7A7A7;
                SIDEBAR_BG = 0x20A7A7A7;
                CONTAINER_BG = 0x20B1B1B1;
                CARD_INACTIVE = 0x1EB1B1B1;
                CARD_INACTIVE_HOVER = 0x2E909090;
                CARD_ACTIVE = 0x36B84242;
                CARD_ACTIVE_HOVER = 0x48B86060;
                BORDER_SUBTLE = 0x3CA0A0A0;
                BORDER_ACTIVE_SOFT = 0xCCEA6060;
                BORDER_EXPANDED_SOFT = 0xFFD19898;
                DIVIDER = 0x2EA0A0A0;
            }
            case THEME_LIGHT -> {
                ACCENT = 0xFFB84242;
                ACCENT_HOVER = 0xFF9E3434;
                PANEL = 0xF5A7A7A7;
                HEADER = 0xF8A7A7A7;
                BORDER = 0xCCB84242;
                ROW_HOVER = 0x28612C2C;
                TEXT = 0xFF232323;
                TEXT_MUTED = 0xFF343434;
                TEXT_DIM = 0xFFB84242;
                TRACK = 0x44A74545;
                SWITCH_ON = 0xFF5AAF5A;
                SWITCH_ON_HOVER = 0xFF69BD69;
                SWITCH_OFF = 0xFFA74545;
                SWITCH_OFF_HOVER = 0xFFBD5C5C;
                SLIDER_BAR = 0xFFA74545;
                SLIDER_BAR_HOVER = 0xFFBD5C5C;
                SLIDER_TRACK = 0xFF8C8C8C;
                SCROLLBAR_TRACK = 0x32000000;
                SCROLLBAR_THUMB = 0x64000000;
                SCROLLBAR_THUMB_HOVER = 0x99000000;
                BACKDROP_DIM = 0x55000000;
                WINDOW_BORDER = 0x55000000;
                WINDOW_BG = 0xF6B6B6B6;
                HEADER_BG = 0xF8A7A7A7;
                SIDEBAR_BG = 0xF6A7A7A7;
                CONTAINER_BG = 0xF2B1B1B1;
                CARD_INACTIVE = 0xF4B1B1B1;
                CARD_INACTIVE_HOVER = 0xF8909090;
                CARD_ACTIVE = 0xF4C4A4A4;
                CARD_ACTIVE_HOVER = 0xF8B88C8C;
                BORDER_SUBTLE = 0x44000000;
                BORDER_ACTIVE_SOFT = 0xFFB84242;
                BORDER_EXPANDED_SOFT = 0xFF6D3434;
                DIVIDER = 0x28000000;
            }
            case THEME_MONOKAI_PRO -> {
                ACCENT = 0xFFA7ED76;
                ACCENT_HOVER = 0xFF66DEFF;
                PANEL = 0xF5211F22;
                HEADER = 0xFC1A191B;
                BORDER = 0xCCA7ED76;
                ROW_HOVER = 0x3293D962;
                TEXT = 0xFFE48561;
                TEXT_MUTED = 0xFFB4B4B4;
                TEXT_DIM = 0xFFFF5B7B;
                TRACK = 0x44A5A1FF;
                SWITCH_ON = 0xFFDCD350;
                SWITCH_ON_HOVER = 0xFFEBE155;
                SWITCH_OFF = 0xFFA5A1FF;
                SWITCH_OFF_HOVER = 0xFFB4B1FF;
                SLIDER_BAR = 0xFFA5A1FF;
                SLIDER_BAR_HOVER = 0xFFB4B1FF;
                SLIDER_TRACK = 0xFF222222;
                SCROLLBAR_TRACK = 0x32D1CE5E;
                SCROLLBAR_THUMB = 0x99D1CE5E;
                SCROLLBAR_THUMB_HOVER = 0xFFD1CE5E;
                BACKDROP_DIM = 0x70000000;
                WINDOW_BORDER = 0x44A7ED76;
                WINDOW_BG = 0xF61A191B;
                HEADER_BG = 0xFC1A191B;
                SIDEBAR_BG = 0xF8211F22;
                CONTAINER_BG = 0xF2211F22;
                CARD_INACTIVE = 0xF22C292D;
                CARD_INACTIVE_HOVER = 0xF83E3B3F;
                CARD_ACTIVE = 0xF5293624;
                CARD_ACTIVE_HOVER = 0xFA34472D;
                BORDER_SUBTLE = 0x38FFFFFF;
                BORDER_ACTIVE_SOFT = 0xFFA7ED76;
                BORDER_EXPANDED_SOFT = 0xFF66DEFF;
                DIVIDER = 0x28FFFFFF;
            }
            case THEME_DARK -> {
                ACCENT = 0xFFFA6464;
                ACCENT_HOVER = 0xFFBF8D8D;
                PANEL = 0xF5282828;
                HEADER = 0xFC242424;
                BORDER = 0xCCFA6464;
                ROW_HOVER = 0x30FA6464;
                TEXT = 0xFFBEBEBE;
                TEXT_MUTED = 0xFF969696;
                TEXT_DIM = 0xFFFA6464;
                TRACK = 0x44963232;
                SWITCH_ON = 0xFF328232;
                SWITCH_ON_HOVER = 0xFF55A355;
                SWITCH_OFF = 0xFF963232;
                SWITCH_OFF_HOVER = 0xFFB14A4A;
                SLIDER_BAR = 0xFF963232;
                SLIDER_BAR_HOVER = 0xFFB14A4A;
                SLIDER_TRACK = 0xFF222222;
                SCROLLBAR_TRACK = 0x32000000;
                SCROLLBAR_THUMB = 0x64000000;
                SCROLLBAR_THUMB_HOVER = 0x99000000;
                BACKDROP_DIM = 0x70000000;
                WINDOW_BORDER = 0x38FFFFFF;
                WINDOW_BG = 0xF6242424;
                HEADER_BG = 0xFC242424;
                SIDEBAR_BG = 0xF8282828;
                CONTAINER_BG = 0xF2282828;
                CARD_INACTIVE = 0xF22D2D2D;
                CARD_INACTIVE_HOVER = 0xF83E3E3E;
                CARD_ACTIVE = 0xF43C2626;
                CARD_ACTIVE_HOVER = 0xF84D3030;
                BORDER_SUBTLE = 0x34FFFFFF;
                BORDER_ACTIVE_SOFT = 0xFFFA6464;
                BORDER_EXPANDED_SOFT = 0xFFB47878;
                DIVIDER = 0x24FFFFFF;
            }
            case THEME_CYBER_CYAN -> {
                ACCENT = 0xFF00E5FF;
                ACCENT_HOVER = 0xFF4DF0FF;
                PANEL = 0xF5070B10;
                HEADER = 0xFC05080C;
                BORDER = 0xE600E5FF;
                ROW_HOVER = 0x3600E5FF;
                TEXT = 0xFFF2FBFF;
                TEXT_MUTED = 0xFFAEC8D8;
                TEXT_DIM = 0xFF38ECFF;
                TRACK = 0x5000E5FF;
                SWITCH_ON = 0xFF00E5FF;
                SWITCH_ON_HOVER = 0xFF4DF0FF;
                SWITCH_OFF = 0xFF16222E;
                SWITCH_OFF_HOVER = 0xFF223344;
                SLIDER_BAR = 0xFF00E5FF;
                SLIDER_BAR_HOVER = 0xFF4DF0FF;
                SLIDER_TRACK = 0xFF141E28;
                SCROLLBAR_TRACK = 0x33000000;
                SCROLLBAR_THUMB = 0xFF00E5FF;
                SCROLLBAR_THUMB_HOVER = 0xFF4DF0FF;
                BACKDROP_DIM = 0x78000408;
                WINDOW_BORDER = 0x4400E5FF;
                WINDOW_BG = 0xF605080C;
                HEADER_BG = 0xFC05080C;
                SIDEBAR_BG = 0xF8060A0F;
                CONTAINER_BG = 0xF20A1018;
                CARD_INACTIVE = 0xF20E1620;
                CARD_INACTIVE_HOVER = 0xF8152231;
                CARD_ACTIVE = 0xF6062833;
                CARD_ACTIVE_HOVER = 0xFA093847;
                BORDER_SUBTLE = 0x3E7CC8E0;
                BORDER_ACTIVE_SOFT = 0xFF00E5FF;
                BORDER_EXPANDED_SOFT = 0xFF5EF3FF;
                DIVIDER = 0x2A7CC8E0;
            }
            case THEME_EMERALD_TOXIC -> {
                ACCENT = 0xFF10F576;
                ACCENT_HOVER = 0xFF4DFF9B;
                PANEL = 0xF5070D09;
                HEADER = 0xFC050907;
                BORDER = 0xE610F576;
                ROW_HOVER = 0x3410F576;
                TEXT = 0xFFF2FFF7;
                TEXT_MUTED = 0xFFB0D1BE;
                TEXT_DIM = 0xFF3EF88E;
                TRACK = 0x5010F576;
                SWITCH_ON = 0xFF10F576;
                SWITCH_ON_HOVER = 0xFF4DFF9B;
                SWITCH_OFF = 0xFF17261D;
                SWITCH_OFF_HOVER = 0xFF22382B;
                SLIDER_BAR = 0xFF10F576;
                SLIDER_BAR_HOVER = 0xFF4DFF9B;
                SLIDER_TRACK = 0xFF142119;
                SCROLLBAR_TRACK = 0x33000000;
                SCROLLBAR_THUMB = 0xFF10F576;
                SCROLLBAR_THUMB_HOVER = 0xFF4DFF9B;
                BACKDROP_DIM = 0x78000502;
                WINDOW_BORDER = 0x4410F576;
                WINDOW_BG = 0xF6050907;
                HEADER_BG = 0xFC050907;
                SIDEBAR_BG = 0xF8060B08;
                CONTAINER_BG = 0xF20A130E;
                CARD_INACTIVE = 0xF20E1A13;
                CARD_INACTIVE_HOVER = 0xF816281E;
                CARD_ACTIVE = 0xF6092B18;
                CARD_ACTIVE_HOVER = 0xFA0D3B21;
                BORDER_SUBTLE = 0x3E7AD9A3;
                BORDER_ACTIVE_SOFT = 0xFF10F576;
                BORDER_EXPANDED_SOFT = 0xFF5EFFA3;
                DIVIDER = 0x2A7AD9A3;
            }
            case THEME_SOLAR_GOLD -> {
                ACCENT = 0xFFFFB800;
                ACCENT_HOVER = 0xFFFFCF4D;
                PANEL = 0xF50E0B07;
                HEADER = 0xFC0A0805;
                BORDER = 0xE6FFB800;
                ROW_HOVER = 0x36FFB800;
                TEXT = 0xFFFFFDF7;
                TEXT_MUTED = 0xFFD9CC9B;
                TEXT_DIM = 0xFFFFC938;
                TRACK = 0x50FFB800;
                SWITCH_ON = 0xFFFFB800;
                SWITCH_ON_HOVER = 0xFFFFCF4D;
                SWITCH_OFF = 0xFF292116;
                SWITCH_OFF_HOVER = 0xFF3B2F1F;
                SLIDER_BAR = 0xFFFFB800;
                SLIDER_BAR_HOVER = 0xFFFFCF4D;
                SLIDER_TRACK = 0xFF241D13;
                SCROLLBAR_TRACK = 0x33000000;
                SCROLLBAR_THUMB = 0xFFFFB800;
                SCROLLBAR_THUMB_HOVER = 0xFFFFCF4D;
                BACKDROP_DIM = 0x78060400;
                WINDOW_BORDER = 0x44FFB800;
                WINDOW_BG = 0xF60A0805;
                HEADER_BG = 0xFC0A0805;
                SIDEBAR_BG = 0xF80C0906;
                CONTAINER_BG = 0xF214100A;
                CARD_INACTIVE = 0xF21B150E;
                CARD_INACTIVE_HOVER = 0xF8282015;
                CARD_ACTIVE = 0xF6332407;
                CARD_ACTIVE_HOVER = 0xFA45310A;
                BORDER_SUBTLE = 0x3EE5C278;
                BORDER_ACTIVE_SOFT = 0xFFFFB800;
                BORDER_EXPANDED_SOFT = 0xFFFFD86B;
                DIVIDER = 0x2AE5C278;
            }
            case THEME_NORD_FROST -> {
                ACCENT = 0xFF88C0D0;
                ACCENT_HOVER = 0xFF8FBCBB;
                PANEL = 0xF52E3440;
                HEADER = 0xFC242933;
                BORDER = 0xCC88C0D0;
                ROW_HOVER = 0x3288C0D0;
                TEXT = 0xFFECEFF4;
                TEXT_MUTED = 0xFFD8DEE9;
                TEXT_DIM = 0xFF81A1C1;
                TRACK = 0x485E81AC;
                SWITCH_ON = 0xFFA3BE8C;
                SWITCH_ON_HOVER = 0xFFB5CFA0;
                SWITCH_OFF = 0xFF4C566A;
                SWITCH_OFF_HOVER = 0xFF5E6A82;
                SLIDER_BAR = 0xFF88C0D0;
                SLIDER_BAR_HOVER = 0xFF8FBCBB;
                SLIDER_TRACK = 0xFF3B4252;
                SCROLLBAR_TRACK = 0x33000000;
                SCROLLBAR_THUMB = 0xAA81A1C1;
                SCROLLBAR_THUMB_HOVER = 0xFF88C0D0;
                BACKDROP_DIM = 0x6814181F;
                WINDOW_BORDER = 0x4488C0D0;
                WINDOW_BG = 0xF6242933;
                HEADER_BG = 0xFC242933;
                SIDEBAR_BG = 0xF8292E39;
                CONTAINER_BG = 0xF22E3440;
                CARD_INACTIVE = 0xF23B4252;
                CARD_INACTIVE_HOVER = 0xF8434C5E;
                CARD_ACTIVE = 0xF62E4552;
                CARD_ACTIVE_HOVER = 0xFA375363;
                BORDER_SUBTLE = 0x3CD8DEE9;
                BORDER_ACTIVE_SOFT = 0xFF88C0D0;
                BORDER_EXPANDED_SOFT = 0xFF8FBCBB;
                DIVIDER = 0x28D8DEE9;
            }
            case THEME_BLOOD_MOON -> {
                ACCENT = 0xFFE6192E;
                ACCENT_HOVER = 0xFFFF3B4E;
                PANEL = 0xB80E0507;
                HEADER = 0xCC0A0305;
                BORDER = 0xD8E6192E;
                ROW_HOVER = 0x3AE6192E;
                TEXT = 0xFFFFF2F4;
                TEXT_MUTED = 0xFFD9B4BA;
                TEXT_DIM = 0xFFFF4D5E;
                TRACK = 0x55E6192E;
                SWITCH_ON = 0xFFE6192E;
                SWITCH_ON_HOVER = 0xFFFF3B4E;
                SWITCH_OFF = 0xAA291216;
                SWITCH_OFF_HOVER = 0xCC3B1A20;
                SLIDER_BAR = 0xFFE6192E;
                SLIDER_BAR_HOVER = 0xFFFF3B4E;
                SLIDER_TRACK = 0xAA1F0D10;
                SCROLLBAR_TRACK = 0x33000000;
                SCROLLBAR_THUMB = 0xCCE6192E;
                SCROLLBAR_THUMB_HOVER = 0xFFFF3B4E;
                BACKDROP_DIM = 0x50140003;
                WINDOW_BORDER = 0x66E6192E;
                WINDOW_BG = 0xB0090305;
                HEADER_BG = 0xCC0A0305;
                SIDEBAR_BG = 0xB80B0406;
                CONTAINER_BG = 0xA8120609;
                CARD_INACTIVE = 0xAC1A090D;
                CARD_INACTIVE_HOVER = 0xC4280E14;
                CARD_ACTIVE = 0xCC3D0B13;
                CARD_ACTIVE_HOVER = 0xDC520F1A;
                BORDER_SUBTLE = 0x44FF808D;
                BORDER_ACTIVE_SOFT = 0xFFFF243A;
                BORDER_EXPANDED_SOFT = 0xFFFF5C6C;
                DIVIDER = 0x30FF808D;
            }
            default -> {
                ACCENT = 0xFFFF1A2B;
                ACCENT_HOVER = 0xFFFF4757;
                PANEL = 0xF508080C;
                HEADER = 0xFC0A0A0E;
                BORDER = 0xE6FF1A2B;
                ROW_HOVER = 0x38FF1A2B;
                TEXT = 0xFFFFFFFF;
                TEXT_MUTED = 0xFFC8D2E0;
                TEXT_DIM = 0xFFFF5A67;
                TRACK = 0x55FF1A2B;
                SWITCH_ON = 0xFFFF1A2B;
                SWITCH_ON_HOVER = 0xFFFF4757;
                SWITCH_OFF = 0xFF1F2029;
                SWITCH_OFF_HOVER = 0xFF2D2E3B;
                SLIDER_BAR = 0xFFFF1A2B;
                SLIDER_BAR_HOVER = 0xFFFF4757;
                SLIDER_TRACK = 0xFF1F2029;
                SCROLLBAR_TRACK = 0x33000000;
                SCROLLBAR_THUMB = 0xFFFF1A2B;
                SCROLLBAR_THUMB_HOVER = 0xFFFF4757;
                BACKDROP_DIM = 0x80000000;
                WINDOW_BORDER = 0x48FFFFFF;
                WINDOW_BG = 0xF6060609;
                HEADER_BG = 0xFC0A0A0E;
                SIDEBAR_BG = 0xF8060608;
                CONTAINER_BG = 0xF20C0C11;
                CARD_INACTIVE = 0xF2111117;
                CARD_INACTIVE_HOVER = 0xF81A1A23;
                CARD_ACTIVE = 0xF6340910;
                CARD_ACTIVE_HOVER = 0xFA450D17;
                BORDER_SUBTLE = 0x44FFFFFF;
                BORDER_ACTIVE_SOFT = 0xFFFF2E3E;
                BORDER_EXPANDED_SOFT = 0xFFFF5260;
                DIVIDER = 0x2EFFFFFF;
            }
        }
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
