package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
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
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (config.vmEnabled) {
            LivingEntity self = (LivingEntity) (Object) this;
            if (config.vmNoBowSwing && self.isHolding(Items.BOW)) {
                cir.setReturnValue(0);
                return;
            }
            if (config.vmSwingSpeed > 0) {
                cir.setReturnValue(config.vmSwingSpeed);
                return;
            }
            if (config.vmNoHaste) {
                cir.setReturnValue(swingAnimation.duration());
            }
            return;
        }
        if (config.dulkirEnabled && config.swingDuration > 0) {
            cir.setReturnValue(config.swingDuration);
        }
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"), cancellable = true)
    private void onSwing(InteractionHand hand, SwingAnimation swingAnimation, boolean sendToSelf, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (!config.vmEnabled) {
            return;
        }
        LivingEntity self = (LivingEntity) (Object) this;
        if (config.vmNoBowSwing && self.isHolding(Items.BOW)) {
            cir.setReturnValue(false);
            return;
        }
        if (config.vmNoSwingReset && self.isSwinging()) {
            cir.setReturnValue(false);
        }
    }
}
