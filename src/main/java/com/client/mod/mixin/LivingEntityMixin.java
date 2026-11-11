package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public class LivingEntityMixin {
    @Inject(method = "hasEffect", at = @At("HEAD"), cancellable = true)
    private void onHasEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                             org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == net.minecraft.client.Minecraft.getInstance().player
                && com.client.mod.ModConfig.getInstance().antiLevitation
                && net.minecraft.world.effect.MobEffects.LEVITATION.equals(effect)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getEffect", at = @At("HEAD"), cancellable = true)
    private void onGetEffect(net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect,
                             org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.world.effect.MobEffectInstance> cir) {
        if ((Object) this == net.minecraft.client.Minecraft.getInstance().player
                && com.client.mod.ModConfig.getInstance().antiLevitation
                && net.minecraft.world.effect.MobEffects.LEVITATION.equals(effect)) {
            cir.setReturnValue(null);
        }
    }

    @Inject(method = "getModifiedSwingDuration", at = @At("HEAD"), cancellable = true)
    private void onGetModifiedSwingDuration(SwingAnimation swingAnimation, CallbackInfoReturnable<Integer> cir) {
        ModConfig config = ModConfig.getInstance();
        if (!config.dulkirEnabled) {
            return;
        }
        if ((Object) this == Minecraft.getInstance().player) {
            int duration = config.swingDuration;
            if (duration > 0) {
                cir.setReturnValue(duration);
            }
        }
    }
}
