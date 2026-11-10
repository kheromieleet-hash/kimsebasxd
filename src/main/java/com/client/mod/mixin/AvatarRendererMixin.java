package com.client.mod.mixin;

import com.client.mod.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {
    @Inject(method = "scale(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;)V", at = @At("TAIL"))
    protected void onScale(AvatarRenderState state, PoseStack poseStack, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (!config.dulkirEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && state.id == mc.player.getId()) {
            float s = config.playerScale;
            if (s != 1.0f) {
                poseStack.scale(s, s, s);
            }
        }
    }
}
