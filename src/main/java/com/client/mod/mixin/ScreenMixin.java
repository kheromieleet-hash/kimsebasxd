package com.client.mod.mixin;

import com.client.mod.ClickGuiScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    @org.spongepowered.asm.mixin.injection.Inject(method = "isAllowedInPortal", at = @org.spongepowered.asm.mixin.injection.At("HEAD"), cancellable = true)
    private void onIsAllowedInPortal(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if (com.client.mod.ModConfig.getInstance().portalGui
                && net.minecraft.client.Minecraft.getInstance().player != null) {
            cir.setReturnValue(true);
        }
    }
    @Shadow
    protected Minecraft minecraft;

    @Shadow
    public int width;

    @Shadow
    public int height;

    private void fillBlackBackground(GuiGraphicsExtractor graphics) {
        if (this.width > 0 && this.height > 0) {
            graphics.fill(0, 0, this.width, this.height, 0xFF000000);
        }
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractBackgroundHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        Screen self = (Screen) (Object) this;
        // Strictly only run when out of game (main menu, server list, settings, etc.)
        if (this.minecraft == null || this.minecraft.level == null) {
            if (!(self instanceof TitleScreen)) {
                fillBlackBackground(graphics);
            }
            ci.cancel();
        }
    }

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void onExtractPanoramaHead(GuiGraphicsExtractor graphics, float delta, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        if (this.minecraft == null || this.minecraft.level == null) {
            fillBlackBackground(graphics);
            ci.cancel();
        }
    }

    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"), cancellable = true)
    private void onExtractMenuBackgroundHead(GuiGraphicsExtractor graphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        if (this.minecraft == null || this.minecraft.level == null) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractBlurredBackgroundHead(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        if (this.minecraft == null || this.minecraft.level == null) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderStateTail(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        Screen self = (Screen) (Object) this;
        if (ClickGuiScreen.isTargetScreen(self)) {
            ClickGuiScreen.renderTabPanel(graphics, mouseX, mouseY);
        }
    }
}
