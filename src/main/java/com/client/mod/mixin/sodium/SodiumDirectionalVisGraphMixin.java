package com.client.mod.mixin.sodium;

import com.client.mod.XrayHandler;
import net.caffeinemc.mods.sodium.client.render.chunk.occlusion.DirectionalVisGraph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DirectionalVisGraph.class, remap = false)
public abstract class SodiumDirectionalVisGraphMixin {

    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void onSetOpaque(int x, int y, int z, CallbackInfo ci) {
        if (XrayHandler.isEnabled()) {
            ci.cancel();
        }
    }
}
