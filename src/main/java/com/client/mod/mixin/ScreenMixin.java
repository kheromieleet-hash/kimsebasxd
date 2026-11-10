package com.client.mod.mixin;

import com.client.mod.ClickGuiScreen;
import com.client.mod.UpdateNoticeManager;
import com.client.mod.VortexLiveShader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow
    protected Minecraft minecraft;

    @Shadow
    public int width;

    @Shadow
    public int height;

    private void renderVortex(GuiGraphicsExtractor graphics) {
        if (this.width <= 0 || this.height <= 0) return;

        // Render live GLSL raymarching shader on GPU into GpuTexture
        VortexLiveShader.render(this.width, this.height);

        // Draw live GLSL texture natively via Minecraft 26.3 RenderPipeline filling the whole screen
        graphics.blit(RenderPipelines.GUI_TEXTURED, VortexLiveShader.TEXTURE_ID, 0, 0, 0.0f, 0.0f, this.width, this.height, this.width, this.height, 0xFFFFFFFF);
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractBackgroundHead(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        // Strictly only run when out of game (main menu, server list, settings, etc.)
        if (this.minecraft == null || this.minecraft.level == null) {
            if (!(self instanceof TitleScreen)) {
                renderVortex(graphics);
                graphics.fill(0, 0, this.width, this.height, 0x40000000);
            }
            ci.cancel();
        }
    }

    @Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
    private void onExtractPanoramaHead(GuiGraphicsExtractor graphics, float delta, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.level == null) {
            renderVortex(graphics);
            ci.cancel();
        }
    }

    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"), cancellable = true)
    private void onExtractMenuBackgroundHead(GuiGraphicsExtractor graphics, int x, int y, int width, int height, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.level == null) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractBlurredBackgroundHead(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (this.minecraft == null || this.minecraft.level == null) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderStateTail(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Screen self = (Screen) (Object) this;
        if (ClickGuiScreen.isTargetScreen(self)) {
            ClickGuiScreen.renderTabPanel(graphics, mouseX, mouseY);
        }
        UpdateNoticeManager.renderScreenToast(graphics);
    }
}
