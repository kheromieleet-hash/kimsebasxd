package com.client.mod;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Vanilla screen replacing IAS's MicrosoftCryptPopupScreen.
 */
public final class SestiaMicrosoftCryptScreen extends Screen {
    private final Screen parent;
    private final Consumer<Object> handler;
    private Button plainButton;

    public SestiaMicrosoftCryptScreen(Screen parent, Consumer<Object> handler) {
        super(Component.translatable("ias.microsoft"));
        this.parent = parent;
        this.handler = handler;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2 - 100;
        int startY = this.height / 2 - 34;

        Button passBtn = Button.builder(Component.translatable("ias.microsoft.password"),
                        btn -> openAuthScreen(true, null))
                .bounds(centerX, startY, 200, 20)
                .tooltip(Tooltip.create(Component.translatable("ias.microsoft.password.tip")))
                .build();
        passBtn.setTooltipDelay(Duration.ofMillis(250));
        this.addRenderableWidget(passBtn);

        Button hwBtn = Button.builder(Component.translatable("ias.microsoft.hardware"),
                        btn -> openAuthScreen(false, IasBridge.hardwareCrypt()))
                .bounds(centerX, startY + 24, 200, 20)
                .tooltip(Tooltip.create(Component.translatable("ias.microsoft.hardware.tip")))
                .build();
        hwBtn.setTooltipDelay(Duration.ofMillis(250));
        this.addRenderableWidget(hwBtn);

        boolean allowNoCrypt = IasBridge.allowNoCrypt();
        Component plainTip = allowNoCrypt
                ? Component.translatable("ias.microsoft.plain.tip.off", Component.literal("Alt"), Component.literal("N"))
                : Component.translatable("ias.microsoft.plain.tip.no");
        this.plainButton = Button.builder(Component.translatable("ias.microsoft.plain"),
                        btn -> openAuthScreen(false, IasBridge.dummyCrypt()))
                .bounds(centerX, startY + 48, 200, 20)
                .tooltip(Tooltip.create(plainTip))
                .build();
        this.plainButton.setTooltipDelay(Duration.ofMillis(250));
        this.plainButton.active = false;
        this.addRenderableWidget(this.plainButton);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, btn -> onClose())
                .bounds(centerX, startY + 76, 200, 20)
                .build());
    }

    private void openAuthScreen(boolean askPassword, Object fixedCrypt) {
        if (this.minecraft != null && this.minecraft.gui != null) {
            this.minecraft.gui.setScreen(new SestiaMicrosoftAuthScreen(this.parent, this.handler, askPassword, fixedCrypt));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_N && event.hasAltDown()
                && IasBridge.allowNoCrypt() && this.plainButton != null && !this.plainButton.isActive()) {
            this.plainButton.active = true;
            this.plainButton.setTooltip(Tooltip.create(Component.translatable("ias.microsoft.plain.tip.on")));
            this.plainButton.setMessage(Component.translatable("ias.microsoft.plain").withStyle(ChatFormatting.RED));
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
        int startY = this.height / 2 - 34;
        graphics.centeredText(this.font, this.title, centerX, startY - 26, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("Choose token storage encryption"), centerX, startY - 13, 0xFFA0A0A0);
    }
}
