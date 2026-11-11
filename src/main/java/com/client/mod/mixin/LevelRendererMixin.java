package com.client.mod.mixin;

import com.client.mod.PacketMineHandler;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void onFinalizeGizmoCollection(CallbackInfo ci) {
        PacketMineHandler.renderGizmo();
    }
}
