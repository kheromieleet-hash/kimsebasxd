package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FirstPersonHandsAndItems.class)
public class FirstPersonHandsMixin {
    @Shadow
    private float mainHandHeight;

    @Shadow
    private float oMainHandHeight;

    @Shadow
    private float offHandHeight;

    @Shadow
    private float oOffHandHeight;

    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("HEAD"), cancellable = true)
    private void onShouldInstantlyReplaceVisibleItem(ItemStack current, ItemStack expected, LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
        ModConfig config = ModConfig.getInstance();
        if (config.vmEnabled && config.vmNoEquip) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(LocalPlayer player, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config.vmEnabled && config.vmNoEquip) {
            this.mainHandHeight = 1.0f;
            this.oMainHandHeight = 1.0f;
            this.offHandHeight = 1.0f;
            this.oOffHandHeight = 1.0f;
        }
    }
}
