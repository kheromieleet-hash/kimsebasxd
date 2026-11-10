package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.renderer.entity.TntMinecartRenderer;
import net.minecraft.client.renderer.entity.state.MinecartTntRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(TntMinecartRenderer.class)
public class TntMinecartRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/vehicle/minecart/MinecartTNT;Lnet/minecraft/client/renderer/entity/state/MinecartTntRenderState;F)V", at = @At("TAIL"))
    private void onExtractRenderState(MinecartTNT entity, MinecartTntRenderState state, float partialTick, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (!config.tntTimer || state.fuseRemainingInTicks < 0.0f) {
            return;
        }

        float fuseTicks = state.fuseRemainingInTicks;
        float seconds = fuseTicks / 20.0f;

        String color;
        if (seconds > 2.5f) {
            color = "§a";
        } else if (seconds > 1.5f) {
            color = "§e";
        } else if (seconds > 0.75f) {
            color = "§6";
        } else {
            color = "§c§l";
        }

        state.nameTag = Component.literal(color + String.format(Locale.US, "%.2fs", seconds));
        state.nameTagAttachment = new Vec3(0.0, 1.20, 0.0);
    }
}
