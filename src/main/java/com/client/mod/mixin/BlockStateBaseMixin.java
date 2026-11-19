package com.client.mod.mixin;

import com.client.mod.ModConfig;
import com.client.mod.XrayHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.BlockStateBase.class)
public class BlockStateBaseMixin {
    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void onGetShadeBrightness(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        if (XrayHandler.isEnabled()) {
            cir.setReturnValue(1.0f);
        }
    }
}
