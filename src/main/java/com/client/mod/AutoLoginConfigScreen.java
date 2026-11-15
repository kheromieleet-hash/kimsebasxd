package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Clean modal screen for setting or clearing the Auto Login password.
 */
public class AutoLoginConfigScreen extends Screen {
    private final Screen parent;
    private int windowX;
    private int windowY;
    private int windowW = 340;
    private int windowH = 148;

    private EditBox passwordBox;

    private static final int PAD = 14;

    public AutoLoginConfigScreen(Screen parent) {
        super(Component.literal("Auto Login Password"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.windowX = (this.width - this.windowW) / 2;
        this.windowY = (this.height - this.windowH) / 2;

        String existing = ModConfig.getInstance().autoLoginPassword;
        if (existing == null) existing = "";

        int boxX = this.windowX + PAD + 8;
        int boxY = this.windowY + 58;
        int boxW = this.windowW - (PAD + 8) * 2;

        this.passwordBox = new EditBox(this.font, boxX, boxY, boxW, 14, Component.literal("Password"));
        this.passwordBox.setHint(Component.literal("Enter /login password..."));
        this.passwordBox.setBordered(false);
        this.passwordBox.setMaxLength(64);
        this.passwordBox.setValue(existing);
        this.addRenderableWidget(this.passwordBox);
        this.setFocused(this.passwordBox);
        this.passwordBox.setFocused(true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x8805070B);

        JelloTheme.drawGlowShadow(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, 0x66000000, 4);
        JelloTheme.fillRoundedCard(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, JelloTheme.WINDOW_BG, JelloTheme.WINDOW_BORDER);

        graphics.text(this.font, Component.literal("§lAUTO LOGIN — PASSWORD"), this.windowX + PAD, this.windowY + 14, JelloTheme.TEXT, false);
        graphics.text(this.font, Component.literal("Used for /login <pass> and /register <pass> <pass>"),
                this.windowX + PAD, this.windowY + 28, JelloTheme.TEXT_MUTED, false);

        int inputX = this.windowX + PAD;
        int inputY = this.windowY + 51;
        int inputW = this.windowW - PAD * 2;
        int inputH = 24;
        boolean focused = this.passwordBox != null && this.passwordBox.isFocused();
        JelloTheme.fillRoundedCard(graphics, inputX, inputY, inputX + inputW, inputY + inputH, 5,
                JelloTheme.CONTAINER_BG, focused ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);

        int btnY = this.windowY + this.windowH - 36;
        int btnH = 22;
        int saveW = 84;
        int clearW = 70;
        int cancelW = 70;

        int saveX = this.windowX + this.windowW - PAD - saveW;
        int cancelX = saveX - 8 - cancelW;
        int clearX = this.windowX + PAD;

        drawBtn(graphics, mouseX, mouseY, clearX, btnY, clearW, btnH, "Clear", false);
        drawBtn(graphics, mouseX, mouseY, cancelX, btnY, cancelW, btnH, "Cancel", false);
        drawBtn(graphics, mouseX, mouseY, saveX, btnY, saveW, btnH, "Save", true);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawBtn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean primary) {
        boolean hov = isInside(mouseX, mouseY, x, y, w, h);
        int bg = primary
                ? (hov ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT)
                : (hov ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CONTAINER_BG);
        int border = primary ? JelloTheme.BORDER_ACTIVE_SOFT : (hov ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 5, bg, border);
        graphics.centeredText(this.font, Component.literal(label), x + w / 2, y + 7, primary ? 0xFFFFFFFF : JelloTheme.TEXT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (!MouseButtonMapping.isLeftClick(event.button())) {
            return super.mouseClicked(event, wasHandled);
        }

        int btnY = this.windowY + this.windowH - 36;
        int btnH = 22;
        int saveW = 84;
        int clearW = 70;
        int cancelW = 70;
        int saveX = this.windowX + this.windowW - PAD - saveW;
        int cancelX = saveX - 8 - cancelW;
        int clearX = this.windowX + PAD;

        if (isInside(mouseX, mouseY, saveX, btnY, saveW, btnH)) {
            saveAndClose();
            return true;
        }
        if (isInside(mouseX, mouseY, cancelX, btnY, cancelW, btnH)) {
            playClickSound();
            onClose();
            return true;
        }
        if (isInside(mouseX, mouseY, clearX, btnY, clearW, btnH)) {
            ModConfig cfg = ModConfig.getInstance();
            cfg.autoLoginPassword = "";
            cfg.save();
            if (this.passwordBox != null) {
                this.passwordBox.setValue("");
            }
            playClickSound();
            return true;
        }

        return super.mouseClicked(event, wasHandled);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_RETURN || event.key() == 335) {
            saveAndClose();
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    private void saveAndClose() {
        ModConfig cfg = ModConfig.getInstance();
        cfg.autoLoginPassword = this.passwordBox != null ? this.passwordBox.getValue().trim() : "";
        cfg.save();
        playClickSound();
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
        }
    }
}
