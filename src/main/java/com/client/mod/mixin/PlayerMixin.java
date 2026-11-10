package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Inject(method = "isStayingOnGroundSurface", at = @At("HEAD"), cancellable = true)
    private void onIsStayingOnGroundSurface(CallbackInfoReturnable<Boolean> cir) {
        Player self = (Player) (Object) this;
        if (ModConfig.getInstance().safeWalk && self.onGround()) {
            cir.setReturnValue(true);
        }
    }
}
