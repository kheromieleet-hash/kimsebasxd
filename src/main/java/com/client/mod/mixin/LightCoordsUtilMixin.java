package com.client.mod.mixin;

import com.client.mod.ModConfig;
import com.client.mod.XrayHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.BlockAndLightGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LightCoordsUtil.class)
public class LightCoordsUtilMixin {
    @Inject(
            method = "getLightCoords(Lnet/minecraft/util/LightCoordsUtil$BrightnessGetter;Lnet/minecraft/world/level/BlockAndLightGetter;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void onGetLightCoords(
            LightCoordsUtil.BrightnessGetter brightnessGetter,
            BlockAndLightGetter level,
            BlockState state,
            BlockPos pos,
            CallbackInfoReturnable<Integer> cir
    ) {
        ModConfig cfg = ModConfig.getInstance();
        if ((cfg.fullbright && cfg.fullbrightForceMaxLight) || XrayHandler.isEnabled()) {
            cir.setReturnValue(15728880);
        }
    }
}
