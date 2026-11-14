package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.ClientAvatarState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientAvatarState.class)
public abstract class ClientAvatarStateMixin {
    @Shadow
    private float bob;

    @Inject(method = "updateBob(F)V", at = @At("RETURN"))
    private void onUpdateBob(float movement, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config != null && config.vmEnabled && config.vmAirWalker) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null && mc.player.avatarState() == (Object) this) {
                this.bob = (float) Math.min(0.1D, mc.player.getDeltaMovement().horizontalDistance());
            }
        }
    }
}
