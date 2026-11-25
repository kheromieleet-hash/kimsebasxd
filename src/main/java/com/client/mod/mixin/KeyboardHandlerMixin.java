package com.client.mod.mixin;

import com.client.mod.ClickGuiScreen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void onKeyPressHead(long window, int action, KeyEvent event, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.getWindow() == null || window != this.minecraft.getWindow().handle()) {
            return;
        }
        if (this.minecraft.gui != null && ClickGuiScreen.isTargetScreen(this.minecraft.gui.screen())) {
            if (ClickGuiScreen.handleKeyPress(action, event)) {
                ci.cancel();
            }
            return;
        }
        if (com.client.mod.CosmeticWheelController.handleKeyPress(this.minecraft, action, event.key())) {
            ci.cancel();
            return;
        }
        if (!com.client.mod.PanicHandler.isPanicActive()
                && action == InputConstants.PRESS
                && this.minecraft.gui != null
                && this.minecraft.gui.screen() == null
                && this.minecraft.player != null
                && event.key() != InputConstants.KEY_F3
                && !InputConstants.isKeyDown(InputConstants.KEY_F3)) {
            ClickGuiScreen.handleInGameMacroKey(event.key());
            ClickGuiScreen.handleInGameModuleKey(event.key());
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void onCharTypedHead(long window, CharacterEvent event, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.getWindow() == null || window != this.minecraft.getWindow().handle()) {
            return;
        }
        if (this.minecraft.gui != null && ClickGuiScreen.isTargetScreen(this.minecraft.gui.screen())) {
            if (ClickGuiScreen.handleCharTyped(event)) {
                ci.cancel();
            }
        }
    }
}
