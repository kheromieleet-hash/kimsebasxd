package com.client.mod.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LogoRenderer.class)
public class LogoRendererMixin {
    private static final Identifier SCT_EDITION = Identifier.fromNamespaceAndPath("inventoryhud", "textures/gui/title/edition.png");

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V", at = @At("HEAD"), cancellable = true)
    private void sestia$hideTitleLogo(GuiGraphicsExtractor graphics, int width, float transparency, int height, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!com.client.mod.PanicHandler.isPanicActive()
                && mc != null
                && mc.gui != null
                && mc.gui.screen() instanceof TitleScreen) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IFI)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIII)V"),
            index = 1
    )
    private Identifier redirectEditionTexture(Identifier original) {
        if (!com.client.mod.PanicHandler.isPanicActive() && original != null && original.getPath().endsWith("edition.png")) {
            return SCT_EDITION;
        }
        return original;
    }
}
