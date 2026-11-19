package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow private float xRot;
    @Shadow public float xRotO;
    @Shadow public float yRotO;
    @Shadow private Entity vehicle;

    @Shadow public abstract float getXRot();
    @Shadow public abstract float getYRot();
    @Shadow public abstract void setXRot(float xRot);
    @Shadow public abstract void setYRot(float yRot);

    @Inject(method = "setXRot", at = @At("HEAD"), cancellable = true)
    private void onSetXRot(float pitch, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && ModConfig.getInstance().noPitchLimit) {
            if (Float.isFinite(pitch)) {
                this.xRot = pitch;
            }
            ci.cancel();
        }
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void onTurn(double yRot, double xRot, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && ModConfig.getInstance().noPitchLimit) {
            float f = (float) xRot * 0.15F;
            float g = (float) yRot * 0.15F;
            this.setXRot(this.getXRot() + f);
            this.setYRot(this.getYRot() + g);
            this.xRotO += f;
            this.yRotO += g;
            if (this.vehicle != null) {
                this.vehicle.onPassengerTurned((Entity) (Object) this);
            }
            ci.cancel();
        }
    }
}
