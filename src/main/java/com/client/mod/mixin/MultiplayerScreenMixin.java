package com.client.mod.mixin;

import java.util.ArrayList;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = JoinMultiplayerScreen.class, priority = 500)
public abstract class MultiplayerScreenMixin extends Screen {
    @Unique
    private Button customAccountsButton;
    @Unique
    private Button customVersionButton;

    protected MultiplayerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "repositionElements", at = @At("HEAD"))
    private void onRepositionElementsHead(CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        com.client.mod.SestiaAccountScreen.disableIasDefaultWidgets();
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void onRepositionElementsTail(CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        com.client.mod.SestiaAccountScreen.disableIasDefaultWidgets();

        // Remove any default IAS icon buttons (smileys/arrows) without stripping other mods' buttons
        for (GuiEventListener child : new ArrayList<>(this.children())) {
            if (child instanceof ImageButton ib) {
                String cls = ib.getClass().getName();
                String msg = ib.getMessage() != null ? ib.getMessage().getString().toLowerCase(java.util.Locale.ROOT) : "";
                if (cls.startsWith("ru.vidtu.ias.") || msg.contains("account switcher") || msg.contains("ias")) {
                    this.removeWidget(child);
                }
            }
        }

        int btnW = 74;
        int btnH = 20;
        int x = this.width - btnW - 8;
        int y = 6;

        if (this.customAccountsButton != null && this.children().contains(this.customAccountsButton)) {
            this.customAccountsButton.setX(x);
            this.customAccountsButton.setY(y);
        } else {
            this.customAccountsButton = Button.builder(
                    Component.literal("Accounts"),
                    btn -> openAccountScreen()
            ).bounds(x, y, btnW, btnH).build();
            this.addRenderableWidget(this.customAccountsButton);
        }

        // Version Switcher Button
        int verW = 106;
        int verH = 20;
        int verX = x - verW - 6;
        int verY = 6;

        com.client.mod.ModConfig config = com.client.mod.ModConfig.getInstance();
        String verLabel = "Version: " + (config != null ? config.getDisplayVersion() : "Auto");
        Component verComp = Component.literal("§a" + verLabel);

        if (this.customVersionButton != null && this.children().contains(this.customVersionButton)) {
            this.customVersionButton.setX(verX);
            this.customVersionButton.setY(verY);
            this.customVersionButton.setMessage(verComp);
        } else {
            this.customVersionButton = Button.builder(
                    verComp,
                    btn -> openVersionSwitcherScreen()
            ).bounds(verX, verY, verW, verH).build();
            this.addRenderableWidget(this.customVersionButton);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        if (this.customVersionButton != null) {
            com.client.mod.ModConfig config = com.client.mod.ModConfig.getInstance();
            String verLabel = "Version: " + (config != null ? config.getDisplayVersion() : "Auto");
            this.customVersionButton.setMessage(Component.literal("§a" + verLabel));
        }
    }

    @Unique
    private void openVersionSwitcherScreen() {
        if (this.minecraft == null) return;
        this.minecraft.gui.setScreen(new com.client.mod.VersionSwitcherScreen(this));
    }

    @Unique
    private void openAccountScreen() {
        if (this.minecraft == null) return;
        this.minecraft.gui.setScreen(new com.client.mod.SestiaAccountScreen(this));
    }
}
