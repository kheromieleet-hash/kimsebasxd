package com.client.mod.mixin;

import com.client.mod.ModConfig;
import com.client.mod.WorldRenderToolsHandler;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartTntRenderState;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntMinecartRenderer.class)
public class TntMinecartRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/MinecartTNT;Lnet/minecraft/client/renderer/entity/state/MinecartTntRenderState;F)V", at = @At("TAIL"))
    private void onExtractRenderState(MinecartTNT entity, MinecartTntRenderState state, float partialTick, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (!config.tntTimer || state.fuseRemainingInTicks < 0.0f) {
            return;
        }
        WorldRenderToolsHandler.applyTntTimerTag(state, state.fuseRemainingInTicks, 1.20);
    }
}
