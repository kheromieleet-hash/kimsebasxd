package com.client.mod.mixin;

import com.client.mod.ClickGuiScreen;
import com.client.mod.SestiaTitleBackgroundRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
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

    @Unique
    private boolean shouldUseSestiaBackground(Screen self) {
        return this.minecraft == null
                || this.minecraft.level == null
                || self instanceof LevelLoadingScreen
                || self instanceof GenericMessageScreen
                || self instanceof ProgressScreen
                || self instanceof ConnectScreen;
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractBackgroundHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        Screen self = (Screen) (Object) this;
        if (shouldUseSestiaBackground(self)) {
            if (!(self instanceof TitleScreen)) {
                SestiaTitleBackgroundRenderer.render(graphics, this.width, this.height, false);
            }
            ci.cancel();
        }
    }

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void onExtractPanoramaHead(GuiGraphicsExtractor graphics, float delta, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        Screen self = (Screen) (Object) this;
        if (shouldUseSestiaBackground(self)) {
            SestiaTitleBackgroundRenderer.render(graphics, this.width, this.height, self instanceof TitleScreen);
            ci.cancel();
        }
    }

    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"), cancellable = true)
    private void onExtractMenuBackgroundHead(GuiGraphicsExtractor graphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        if (shouldUseSestiaBackground((Screen) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractBlurredBackgroundHead(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        if (shouldUseSestiaBackground((Screen) (Object) this)) {
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
