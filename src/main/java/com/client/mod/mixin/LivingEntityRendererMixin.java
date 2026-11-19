package com.client.mod.mixin;

import com.client.mod.FreecamHandler;
import com.client.mod.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void onShouldShowName(LivingEntity entity, double distanceToCameraSq, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && entity == mc.player && FreecamHandler.isActive()) {
            cir.setReturnValue(distanceToCameraSq < 4096.0);
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V", at = @At("TAIL"))
    public void onExtractRenderState(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config == null) return;

        // TrueSight: Force invisibility off so player & mobs render visibly
        if (config.trueSight) {
            state.isInvisible = false;
            state.isInvisibleToPlayer = false;
        }
    }
}
