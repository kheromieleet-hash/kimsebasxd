package com.client.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Vanilla Confirm-style Delete Account screen replacing IAS's DeletePopupScreen.
 */
public final class SestiaDeleteAccountScreen extends Screen {
    private final Screen parent;
    private final Component prompt;
    private final Runnable confirmAction;
    private MultiLineLabel promptLabel;

    public SestiaDeleteAccountScreen(Screen parent, Component prompt, Runnable confirmAction) {
        super(Component.translatable("ias.delete"));
        this.parent = parent;
        this.prompt = prompt != null ? prompt : Component.translatable("ias.delete");
        this.confirmAction = confirmAction;
    }

    @Override
    protected void init() {
        this.promptLabel = MultiLineLabel.create(this.font, this.prompt, Math.max(220, Math.min(340, this.width - 40)));
        int y = this.height / 2 + 12;
        int centerX = this.width / 2;

        this.addRenderableWidget(Button.builder(Component.translatable("ias.delete"), btn -> {
            if (this.confirmAction != null) {
                this.confirmAction.run();
            }
            onClose();
        }).bounds(centerX - 102, y, 98, 20).build());

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> onClose())
                .bounds(centerX + 4, y, 98, 20)
                .build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.confirmAction != null) {
                this.confirmAction.run();
            }
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        int centerY = this.height / 2;

        graphics.centeredText(this.font, this.title, centerX, centerY - 52, 0xFFFFFFFF);
        if (this.promptLabel != null) {
            this.promptLabel.visitLines(net.minecraft.client.gui.TextAlignment.CENTER, centerX, centerY - 24, 11, graphics.textRenderer());
        }
        Component hint = Component.translatable("ias.delete.hint", Component.literal("Shift")).withStyle(ChatFormatting.GRAY);
        graphics.centeredText(this.font, hint, centerX, centerY + 44, 0xFF808080);
    }
}
