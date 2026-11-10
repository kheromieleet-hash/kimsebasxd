package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.state.CreeperRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(CreeperRenderer.class)
public class CreeperRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/monster/Creeper;Lnet/minecraft/client/renderer/entity/state/CreeperRenderState;F)V", at = @At("TAIL"))
    private void onExtractRenderState(Creeper entity, CreeperRenderState state, float partialTick, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (!config.tntTimer) {
            return;
        }

        if (state.swelling > 0.0f) {
            float remainingSeconds = Math.max(0.0f, (1.0f - state.swelling) * 1.5f);
            String color;
            if (remainingSeconds > 0.9f) {
                color = "§e";
            } else if (remainingSeconds > 0.4f) {
                color = "§6";
            } else {
                color = "§c§l";
            }

            state.nameTag = Component.literal(color + String.format(Locale.US, "%.2fs", remainingSeconds));
            state.nameTagAttachment = new Vec3(0.0, state.boundingBoxHeight + 0.35, 0.0);
        }
    }
}
