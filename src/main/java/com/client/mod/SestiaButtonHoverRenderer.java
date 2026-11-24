package com.client.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Renders sheer transparent glass buttons and sliders so background falling leaves remain visible behind UI,
 * highlighting active hovered/focused controls with Sestia's neon emerald accents.
 */
public final class SestiaButtonHoverRenderer {

    static final int FILL_DISABLED = 0x24000000;
    static final int FILL_NORMAL = 0x34000000;
    static final int FILL_HOVERED = 0x4404140B;

    static final int BORDER_DISABLED = 0x24FFFFFF;
    static final int BORDER_NORMAL = 0x42FFFFFF;
    static final int BORDER_HOVERED = 0xFF5AFCA8;
    static final int HOVER_INNER_GLOW = 0x3A5AFCA8;

    static final int SLIDER_PROGRESS_DISABLED = 0x1AFFFFFF;
    static final int SLIDER_PROGRESS_NORMAL = 0x2C34D399;
    static final int SLIDER_PROGRESS_HOVERED = 0x425AFCA8;

    static final int SLIDER_MARKER_DISABLED = 0x55FFFFFF;
    static final int SLIDER_MARKER_NORMAL = 0xCC5AFCA8;
    static final int SLIDER_MARKER_HOVERED = 0xFFA7F3D0;

    private SestiaButtonHoverRenderer() {
    }

    private static boolean isStrainOverrideActive() {
        return JelloTheme.isLeafTheme();
    }

    private static int resolveFillColor(int normalArgb, int hoveredArgb, int disabledArgb, boolean active, boolean hovered, float widgetAlpha) {
        return resolveStateColor(normalArgb, hoveredArgb, disabledArgb, active, hovered, widgetAlpha);
    }

    private static int resolveStateColor(int normalArgb, int hoveredArgb, int disabledArgb, boolean active, boolean hovered, float widgetAlpha) {
        int base;
        if (!active) {
            base = disabledArgb;
        } else if (hovered) {
            base = hoveredArgb;
        } else {
            base = normalArgb;
        }
        return applyAlpha(base, widgetAlpha);
    }

    static int resolveFillColor(boolean active, boolean hovered, float widgetAlpha) {
        int hoveredFill = isStrainOverrideActive()
                ? (0x44000000 | (JelloTheme.CARD_ACTIVE & 0x00FFFFFF))
                : FILL_HOVERED;
        return resolveFillColor(FILL_NORMAL, hoveredFill, FILL_DISABLED, active, hovered, widgetAlpha);
    }

    static int resolveBorderColor(boolean active, boolean hovered, float widgetAlpha) {
        int hoveredBorder = isStrainOverrideActive()
                ? (0xFF000000 | (JelloTheme.BORDER_ACTIVE_SOFT & 0x00FFFFFF))
                : BORDER_HOVERED;
        return resolveStateColor(BORDER_NORMAL, hoveredBorder, BORDER_DISABLED, active, hovered, widgetAlpha);
    }

    static int resolveSliderProgressColor(boolean active, boolean highlighted, float widgetAlpha) {
        if (isStrainOverrideActive()) {
            int accentRgb = JelloTheme.ACCENT & 0x00FFFFFF;
            int activeRgb = JelloTheme.BORDER_ACTIVE_SOFT & 0x00FFFFFF;
            return resolveStateColor(0x2C000000 | accentRgb, 0x42000000 | activeRgb, SLIDER_PROGRESS_DISABLED, active, highlighted, widgetAlpha);
        }
        return resolveStateColor(
                SLIDER_PROGRESS_NORMAL, SLIDER_PROGRESS_HOVERED, SLIDER_PROGRESS_DISABLED,
                active, highlighted, widgetAlpha
        );
    }

    static int resolveSliderMarkerColor(boolean active, boolean highlighted, float widgetAlpha) {
        if (isStrainOverrideActive()) {
            int normRgb = JelloTheme.BORDER_ACTIVE_SOFT & 0x00FFFFFF;
            int hovRgb = JelloTheme.BORDER_EXPANDED_SOFT & 0x00FFFFFF;
            return resolveStateColor(0xCC000000 | normRgb, 0xFF000000 | hovRgb, SLIDER_MARKER_DISABLED, active, highlighted, widgetAlpha);
        }
        return resolveStateColor(
                SLIDER_MARKER_NORMAL, SLIDER_MARKER_HOVERED, SLIDER_MARKER_DISABLED,
                active, highlighted, widgetAlpha
        );
    }

    static int computeSliderFillWidth(int width, double value) {
        int innerW = Math.max(0, width - 2);
        if (innerW == 0 || !Double.isFinite(value)) {
            return 0;
        }
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return Math.max(0, Math.min(innerW, (int) Math.round(innerW * clamped)));
    }

    static int applyAlpha(int argb, float widgetAlpha) {
        float clamped = Float.isFinite(widgetAlpha) ? Math.max(0.0f, Math.min(1.0f, widgetAlpha)) : 1.0f;
        int baseA = (argb >>> 24) & 0xFF;
        int scaledA = Math.max(0, Math.min(255, Math.round(baseA * clamped)));
        return (scaledA << 24) | (argb & 0x00FFFFFF);
    }

    /**
     * Draws a sheer transparent glass button backing and 1px border (neon emerald on active hover).
     */
    public static void renderTransparentButton(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            boolean active,
            boolean hovered,
            float widgetAlpha
    ) {
        if (graphics == null || width <= 2 || height <= 2 || widgetAlpha <= 0.02f) {
            return;
        }

        int fillColor = resolveFillColor(active, hovered, widgetAlpha);
        int borderColor = resolveBorderColor(active, hovered, widgetAlpha);

        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, fillColor);

        // Non-overlapping 1px border segments so translucent normal borders remain uniform at corners
        graphics.fill(x, y, x + width, y + 1, borderColor);
        graphics.fill(x, y + height - 1, x + width, y + height, borderColor);
        graphics.fill(x, y + 1, x + 1, y + height - 1, borderColor);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, borderColor);

        if (active && hovered && width > 2 && height > 3) {
            int innerGlow = isStrainOverrideActive()
                    ? (0x3A000000 | (JelloTheme.BORDER_ACTIVE_SOFT & 0x00FFFFFF))
                    : HOVER_INNER_GLOW;
            graphics.fill(x + 1, y + 1, x + width - 1, y + 2, applyAlpha(innerGlow, widgetAlpha));
        }
    }

    /**
     * Renders a sheer transparent glass slider with translucent emerald fill and slim 2px emerald marker.
     */
    public static void renderTransparentSlider(
            GuiGraphicsExtractor graphics,
            int x,
            int y,
            int width,
            int height,
            double value,
            boolean active,
            boolean highlighted,
            float widgetAlpha
    ) {
        if (graphics == null || width <= 2 || height <= 2 || widgetAlpha <= 0.02f) {
            return;
        }

        renderTransparentButton(graphics, x, y, width, height, active, highlighted, widgetAlpha);

        int fillW = computeSliderFillWidth(width, value);
        if (fillW > 0) {
            graphics.fill(
                    x + 1, y + 1,
                    x + 1 + fillW, y + height - 1,
                    resolveSliderProgressColor(active, highlighted, widgetAlpha)
            );
        }

        if (height > 4) {
            int markerX = Math.max(x + 1, Math.min(x + width - 3, x + 1 + fillW - 1));
            graphics.fill(
                    markerX, y + 2,
                    markerX + 2, y + height - 2,
                    resolveSliderMarkerColor(active, highlighted, widgetAlpha)
            );
        }
    }
}
