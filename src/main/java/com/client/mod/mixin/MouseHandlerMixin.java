package com.client.mod.mixin;

import com.client.mod.ClickGuiScreen;
import com.client.mod.CustomTitleMenu;
import com.client.mod.ZoomHandler;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MouseHandler.class, priority = 500)
public abstract class MouseHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    private double accumulatedDX;

    @Shadow
    private double accumulatedDY;

    @Shadow
    public abstract double getScaledXPos(Window window);

    @Shadow
    public abstract double getScaledYPos(Window window);

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void onMouseButtonHead(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if (this.minecraft != null && this.minecraft.gui != null) {
            if (this.minecraft.gui.screen() instanceof TitleScreen titleScreen && action == 1) {
                Window win = this.minecraft.getWindow();
                double mouseX = this.getScaledXPos(win);
                double mouseY = this.getScaledYPos(win);
                if (CustomTitleMenu.handleClick(this.minecraft, titleScreen, mouseX, mouseY, buttonInfo.button(), action)) {
                    ci.cancel();
                    return;
                }
            }
            if (ClickGuiScreen.isTargetScreen(this.minecraft.gui.screen())) {
                Window win = this.minecraft.getWindow();
                double mouseX = this.getScaledXPos(win);
                double mouseY = this.getScaledYPos(win);
                if (ClickGuiScreen.handleMouseButton(mouseX, mouseY, buttonInfo.button(), action)) {
                    ci.cancel();
                }
                return;
            }
            if (action == 1 && this.minecraft.gui.screen() == null && this.minecraft.player != null) {
                ClickGuiScreen.handleInGameMacroMouse(buttonInfo.button());
                ClickGuiScreen.handleInGameModuleMouse(buttonInfo.button());
            }
        }
    }

    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void onHandleAccumulatedMovement(CallbackInfo ci) {
        if (ZoomHandler.isZooming()) {
            float factor = ZoomHandler.getCurrentZoomFactor();
            if (factor > 1.0f) {
                this.accumulatedDX /= (double) factor;
                this.accumulatedDY /= (double) factor;
            }
        }
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (ZoomHandler.onMouseScrolled(vertical)) {
            ci.cancel();
        }
    }
}
