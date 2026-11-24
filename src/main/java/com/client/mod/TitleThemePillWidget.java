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
 * Displays 6 colored hemp leaf icons that switch themes live without entering a world.
 */
public final class TitleThemePillWidget extends AbstractWidget {

    public static final int PILL_WIDTH = 132;
    public static final int PILL_HEIGHT = 24;
    private static final int PADDING_X = 9;
    private static final int SLOT_STEP = 20;
    private static final int LEAF_SIZE = 14;
    private static final int PALETTE_COUNT = JelloTheme.STRAIN_COUNT + 1;

    private static final String[] PALETTE_LABELS = {
            "Neon Emerald", "Deep Forest", "Frost Mint", "Gold Leaf", "Obsidian Frost", "Crimson"
    };

    public TitleThemePillWidget(int x, int y, Screen parentScreen) {
        super(x, y, PILL_WIDTH, PILL_HEIGHT, Component.literal("Sestia Theme Switcher"));
    }

    /** Returns -1 if outside, or 0..5 for the 6 colored leaf theme slots. */
    static int hitTestSlot(int widgetX, int widgetY, double mouseX, double mouseY) {
        if (mouseX < widgetX || mouseX >= widgetX + PILL_WIDTH || mouseY < widgetY || mouseY >= widgetY + PILL_HEIGHT) {
            return -1;
        }
        double relX = mouseX - widgetX;
        int bestSlot = -1;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < PALETTE_COUNT; i++) {
            double cx = PADDING_X + i * SLOT_STEP + LEAF_SIZE * 0.5;
            double dist = Math.abs(relX - cx);
            if (dist <= SLOT_STEP * 0.55 && dist < bestDist) {
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
        boolean anyHover = hoveredSlot >= 0;

        int x1 = this.getX();
        int y1 = this.getY();
        int x2 = x1 + this.width;
        int y2 = y1 + this.height;

        int bg = anyHover ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.WINDOW_BG;
        int border = anyHover ? JelloTheme.BORDER_ACTIVE_SOFT : JelloTheme.WINDOW_BORDER;
        JelloTheme.fillRoundedCard(graphics, x1, y1, x2, y2, 11, bg, border);

        int leafY = y1 + (this.height - LEAF_SIZE) / 2;
        int cy = y1 + this.height / 2;
        for (int i = 0; i < PALETTE_COUNT; i++) {
            int lx = x1 + PADDING_X + i * SLOT_STEP;
            int cx = lx + LEAF_SIZE / 2;
            int leafCol = getDotColorArgb(i);
            boolean selected = (i == activeSlot);
            boolean hov = (i == hoveredSlot);

            if (selected) {
                JelloTheme.drawRadialGlow(graphics, cx, cy, 11, (0x44 << 24) | (leafCol & 0x00FFFFFF));
                JelloTheme.fillRoundedCard(graphics, lx - 2, leafY - 2, lx + LEAF_SIZE + 2, leafY + LEAF_SIZE + 2, 5,
                        (0x36 << 24) | (leafCol & 0x00FFFFFF), leafCol);
            } else if (hov) {
                JelloTheme.drawRadialGlow(graphics, cx, cy, 10, (0x2A << 24) | (leafCol & 0x00FFFFFF));
            }

            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    SestiaTitleBackgroundRenderer.getLeafMaskTexture(),
                    lx, leafY,
                    0.0f, 0.0f, LEAF_SIZE, LEAF_SIZE, LEAF_SIZE, LEAF_SIZE,
                    leafCol
            );
        }

        if (anyHover) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
            Minecraft mc = Minecraft.getInstance();
            Font font = mc != null ? mc.font : null;
            if (font != null) {
                String tip = PALETTE_LABELS[hoveredSlot];
                int tipW = font.width(tip) + 12;
                int tipH = 14;
                int tipX = Math.max(4, Math.min(x2 - tipW, (x1 + x2 - tipW) / 2));
                int tipY = y1 - tipH - 5;
                JelloTheme.fillRoundedCard(graphics, tipX, tipY, tipX + tipW, tipY + tipH, 5,
                        JelloTheme.WINDOW_BG, getDotColorArgb(hoveredSlot));
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
        if (slot >= 0 && slot < PALETTE_COUNT) {
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
