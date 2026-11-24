package com.client.mod.mixin;

import com.client.mod.CosmeticsLogic;
import com.client.mod.ModConfig;
import com.client.mod.PanicHandler;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Scales the HUD Totem of Undying activation animation when custom Totem Pop FX is enabled.
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererMixin {

    @Redirect(
            method = "renderItemActivationAnimation(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FLnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V",
                    ordinal = 0
            )
    )
    private void sestia$scaleTotemActivation(PoseStack poseStack, float sx, float sy, float sz) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg != null && cfg.cosmeticTotemFxEnabled && !PanicHandler.isPanicActive()) {
            float mult = CosmeticsLogic.clampTotemScale(cfg.cosmeticTotemFxScale);
            poseStack.scale(sx * mult, sy * mult, sz * mult);
        } else {
            poseStack.scale(sx, sy, sz);
        }
    }
}
