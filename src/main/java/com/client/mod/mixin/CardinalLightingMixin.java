package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CardinalLighting.class)
public class CardinalLightingMixin {
    @Inject(method = "byFace", at = @At("HEAD"), cancellable = true)
    private void onByFace(Direction direction, CallbackInfoReturnable<Float> cir) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.fullbright && !cfg.fullbright3DShading) {
            cir.setReturnValue(1.0f);
        }
    }
}
