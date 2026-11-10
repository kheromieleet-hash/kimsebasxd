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
        disableIasDefaultWidgets();
    }

    @Inject(method = "repositionElements", at = @At("TAIL"))
    private void onRepositionElementsTail(CallbackInfo ci) {
        disableIasDefaultWidgets();

        // Remove any default IAS icon buttons (smileys/arrows)
        for (GuiEventListener child : new ArrayList<>(this.children())) {
            if (child instanceof ImageButton) {
                this.removeWidget(child);
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
    private void disableIasDefaultWidgets() {
        try {
            Class<?> cfgCls = Class.forName("ru.vidtu.ias.config.IASConfig");
            cfgCls.getField("serversButton").setBoolean(null, false);
            cfgCls.getField("titleButton").setBoolean(null, false);
            cfgCls.getField("serversText").setBoolean(null, false);
            cfgCls.getField("titleText").setBoolean(null, false);
        } catch (Throwable ignored) {
        }
    }

    @Unique
    private void openAccountScreen() {
        if (this.minecraft == null) return;
        try {
            Class<?> cls = Class.forName("ru.vidtu.ias.screen.AccountScreen");
            Screen s = (Screen) cls.getConstructor(Screen.class).newInstance(this);
            this.minecraft.gui.setScreen(s);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
