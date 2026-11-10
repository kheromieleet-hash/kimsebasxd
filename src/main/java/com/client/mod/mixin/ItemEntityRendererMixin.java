package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public class ItemEntityRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/item/ItemEntity;Lnet/minecraft/client/renderer/entity/state/ItemEntityRenderState;F)V", at = @At("TAIL"))
    private void onExtractRenderState(ItemEntity entity, ItemEntityRenderState state, float partialTick, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config == null || !config.itemEsp) {
            return;
        }
        ItemStack item = entity.getItem();
        if (!item.isEmpty()) {
            int count = item.getCount();
            String name = item.getHoverName().getString();
            state.nameTag = Component.literal("§e" + name + (count > 1 ? " §6x" + count : ""));
            state.nameTagAttachment = new Vec3(0.0, 0.5, 0.0);
        }
    }
}
