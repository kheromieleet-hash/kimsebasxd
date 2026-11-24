package com.client.mod;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Compact translucent glass theme switcher capsule shown on the Title Screen.
 * Left leaf icon opens Sestia Leaf Studio (ClickGUI); 6 color dots switch themes live without entering a world.
 */
public final class TitleThemePillWidget extends AbstractWidget {

    public static final int PILL_WIDTH = 152;
    public static final int PILL_HEIGHT = 24;
    private static final int LEAF_HIT_W = 28;
    private static final int DOT_START_X = 34;
    private static final int DOT_STEP = 19;
    private static final int DOT_RADIUS = 5;
    private static final int PALETTE_COUNT = JelloTheme.STRAIN_COUNT + 1;

    private static final String[] PALETTE_LABELS = {
            "Neon Emerald", "Deep Forest", "Frost Mint", "Gold Leaf", "Obsidian Frost", "Crimson"
    };

    private final Screen parentScreen;

    public TitleThemePillWidget(int x, int y, Screen parentScreen) {
        super(x, y, PILL_WIDTH, PILL_HEIGHT, Component.literal("Sestia Theme Switcher"));
        this.parentScreen = parentScreen;
    }

    /** Returns -2 if outside, -1 if on the left leaf Studio button, or 0..5 for the 6 theme dots. */
    static int hitTestSlot(int widgetX, int widgetY, double mouseX, double mouseY) {
        if (mouseX < widgetX || mouseX >= widgetX + PILL_WIDTH || mouseY < widgetY || mouseY >= widgetY + PILL_HEIGHT) {
            return -2;
        }
        double relX = mouseX - widgetX;
        if (relX < LEAF_HIT_W) {
            return -1;
        }
        int bestSlot = -2;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < PALETTE_COUNT; i++) {
            double cx = DOT_START_X + i * DOT_STEP + DOT_RADIUS;
            double dist = Math.abs(relX - cx);
            if (dist <= DOT_STEP * 0.52 && dist < bestDist) {
                bestDist = dist;
                bestSlot = i;
            }
        }
        return bestSlot;
    }

    static int getActiveSlot(ModConfig config) {
        if (config == null) {
            return JelloTheme.STRAIN_NEON_EMERALD;
        }
        if (config.guiTheme == JelloTheme.THEME_SESTIA_CRIMSON) {
            return JelloTheme.STRAIN_COUNT;
        }
        return JelloTheme.normalizeLeafStrain(config.leafStrain);
    }

    static int getDotColorArgb(int slot) {
        if (slot == JelloTheme.STRAIN_COUNT) {
            return 0xFF000000 | JelloTheme.CRIMSON_LEAF_TINT_RGB;
        }
        return 0xFF000000 | JelloTheme.getLeafStrainTintRgb(slot);
    }

    static void applySlot(int slot, ModConfig config) {
        if (config == null || slot < 0 || slot >= PALETTE_COUNT) {
            return;
        }
        if (slot == JelloTheme.STRAIN_COUNT) {
            config.guiTheme = JelloTheme.THEME_SESTIA_CRIMSON;
        } else {
            config.guiTheme = JelloTheme.THEME_SESTIA;
            config.leafStrain = slot;
        }
        JelloTheme.applyFromConfig(config);
        config.save();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (PanicHandler.isPanicActive()) {
            return;
        }

        ModConfig config = ModConfig.getInstance();
        int activeSlot = getActiveSlot(config);
        int hoveredSlot = hitTestSlot(this.getX(), this.getY(), mouseX, mouseY);
        boolean anyHover = hoveredSlot != -2;

        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;
        int activeTint = 0xFF000000 | (JelloTheme.getLeafTintRgb() & 0x00FFFFFF);

        int bg = anyHover ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.WINDOW_BG;
        int border = anyHover ? JelloTheme.BORDER_ACTIVE_SOFT : JelloTheme.WINDOW_BORDER;
        JelloTheme.fillRoundedCard(graphics, x1, y1, x2, y2, 11, bg, border);

        // Left leaf icon (opens Sestia Leaf Studio)
        int leafX = x1 + 7;
        int leafY = y1 + (this.height - 14) / 2;
        if (hoveredSlot == -1) {
            JelloTheme.drawRadialGlow(graphics, leafX + 7, leafY + 7, 11, (0x44 << 24) | (activeTint & 0x00FFFFFF));
        }
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                SestiaTitleBackgroundRenderer.getLeafMaskTexture(),
                leafX, leafY,
                0.0f, 0.0f, 14, 14, 14, 14,
                activeTint
        );

        // Subtle vertical divider
        graphics.fill(x1 + LEAF_HIT_W - 1, y1 + 5, x1 + LEAF_HIT_W, y2 - 5, JelloTheme.DIVIDER);

        // 6 theme swatch dots
        int cy = y1 + this.height / 2;
        for (int i = 0; i < PALETTE_COUNT; i++) {
            int cx = x1 + DOT_START_X + i * DOT_STEP + DOT_RADIUS;
            int dotCol = getDotColorArgb(i);
            boolean selected = (i == activeSlot);
            boolean hov = (i == hoveredSlot);

            if (selected || hov) {
                JelloTheme.drawRadialGlow(graphics, cx, cy, 10, ((selected ? 0x42 : 0x26) << 24) | (dotCol & 0x00FFFFFF));
            }
            if (selected) {
                JelloTheme.fillCircle(graphics, cx, cy, DOT_RADIUS + 2, 0xFFFFFFFF);
                JelloTheme.fillCircle(graphics, cx, cy, DOT_RADIUS + 1, 0xFF0A0D0E);
            } else if (hov) {
                JelloTheme.fillCircle(graphics, cx, cy, DOT_RADIUS + 1, 0xAAFFFFFF);
            }
            JelloTheme.fillCircle(graphics, cx, cy, DOT_RADIUS, dotCol);
        }

        // Hover tooltip badge floating above the capsule
        if (anyHover) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
            Minecraft mc = Minecraft.getInstance();
            Font font = mc != null ? mc.font : null;
            if (font != null) {
                String tip = (hoveredSlot == -1)
                        ? "Leaf Studio (RShift)"
                        : PALETTE_LABELS[hoveredSlot];
                int tipW = font.width(tip) + 12;
                int tipH = 14;
                int tipX = Math.max(4, Math.min(x2 - tipW, (x1 + x2 - tipW) / 2));
                int tipY = y1 - tipH - 5;
                int tipBorder = (hoveredSlot >= 0) ? getDotColorArgb(hoveredSlot) : JelloTheme.BORDER_ACTIVE_SOFT;
                JelloTheme.fillRoundedCard(graphics, tipX, tipY, tipX + tipW, tipY + tipH, 5, JelloTheme.WINDOW_BG, tipBorder);
                JelloTheme.textCentered(graphics, font, tip, tipX + tipW / 2, tipY + 3, JelloTheme.TEXT);
            }
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean isDoubleClick) {
        if (PanicHandler.isPanicActive()) {
            return;
        }
        int slot = hitTestSlot(this.getX(), this.getY(), event.x(), event.y());
        if (slot == -1) {
            playUiClick(1.06f);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.gui.setScreen(new ModernClickGuiScreen(this.parentScreen, ModernClickGuiScreen.NavTab.SETTINGS));
            }
        } else if (slot >= 0 && slot < PALETTE_COUNT) {
            applySlot(slot, ModConfig.getInstance());
            playUiClick(1.12f);
        }
    }

    private static void playUiClick(float pitch) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}
