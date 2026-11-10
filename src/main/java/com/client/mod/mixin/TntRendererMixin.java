package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.renderer.entity.TntRenderer;
import net.minecraft.client.renderer.entity.state.TntRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(TntRenderer.class)
public class TntRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/PrimedTnt;Lnet/minecraft/client/renderer/entity/state/TntRenderState;F)V", at = @At("TAIL"))
    private void onExtractRenderState(PrimedTnt entity, TntRenderState state, float partialTick, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (!config.tntTimer) {
            return;
        }

        float fuseTicks = state.fuseRemainingInTicks;
        if (fuseTicks < 0.0f) {
            fuseTicks = 0.0f;
        }
        float seconds = fuseTicks / 20.0f;

        String color;
        if (seconds > 2.5f) {
            color = "§a"; // Green
        } else if (seconds > 1.5f) {
            color = "§e"; // Yellow
        } else if (seconds > 0.75f) {
            color = "§6"; // Orange / Gold
        } else {
            color = "§c§l"; // Bold Red (< 0.75s)
        }

        state.nameTag = Component.literal(color + String.format(Locale.US, "%.2fs", seconds));
        state.nameTagAttachment = new Vec3(0.0, 1.25, 0.0);
    }
}
