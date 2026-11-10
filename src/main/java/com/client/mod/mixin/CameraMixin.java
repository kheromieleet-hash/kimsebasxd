package com.client.mod.mixin;

import com.client.mod.ZoomHandler;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void onCalculateFov(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (ZoomHandler.isZooming()) {
            float factor = ZoomHandler.getCurrentZoomFactor();
            if (factor > 1.0f) {
                cir.setReturnValue(cir.getReturnValue() / factor);
            }
        }
    }
}
