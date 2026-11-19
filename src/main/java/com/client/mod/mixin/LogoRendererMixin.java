package com.client.mod.mixin;

import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LogoRenderer.class)
public class LogoRendererMixin {
    private static final Identifier SCT_EDITION = Identifier.fromNamespaceAndPath("inventoryhud", "textures/gui/title/edition.png");

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
