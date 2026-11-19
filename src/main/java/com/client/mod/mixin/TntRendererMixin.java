package com.client.mod.mixin;

import com.client.mod.ModConfig;
import com.client.mod.WorldRenderToolsHandler;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.world.entity.item.PrimedTnt;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TntRenderer.class)
public class TntRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/PrimedTnt;Lnet/minecraft/client/renderer/entity/state/TntRenderState;F)V", at = @At("TAIL"))
    private void onExtractRenderState(PrimedTnt entity, TntRenderState state, float partialTick, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (!config.tntTimer) {
            return;
        }
        WorldRenderToolsHandler.applyTntTimerTag(state, state.fuseRemainingInTicks, 1.25);
    }
}
