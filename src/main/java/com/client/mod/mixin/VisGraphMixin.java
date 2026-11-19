package com.client.mod.mixin;

import com.client.mod.XrayHandler;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VisGraph.class)
public abstract class VisGraphMixin {

    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void onSetOpaque(BlockPos pos, CallbackInfo ci) {
        if (XrayHandler.isEnabled()) {
            ci.cancel();
        }
    }
}
