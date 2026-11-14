package com.client.mod.mixin;

import com.client.mod.FreecamHandler;
import com.client.mod.ZoomHandler;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean detached;

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void onAlignWithEntityFreecam(float partialTicks, CallbackInfo ci) {
        if (FreecamHandler.isActive()) {
            Vec3 pos = FreecamHandler.getInterpolatedPos(partialTicks);
            this.setPosition(pos.x, pos.y, pos.z);
            this.setRotation(FreecamHandler.getYaw(), FreecamHandler.getPitch());
            this.detached = true;
        }
    }

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
