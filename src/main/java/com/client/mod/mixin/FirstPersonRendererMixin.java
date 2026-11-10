package com.client.mod.mixin;

import com.client.mod.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonRendererMixin {
    @Inject(
            method = "submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
            )
    )
    private void onRenderHeldItem(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handState, float f, float g, InteractionHand hand, float h, ItemStack itemStack, float i, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (!config.dulkirEnabled) {
            return;
        }
        float scale = config.itemScale;
        if (hand == InteractionHand.MAIN_HAND) {
            float rotX = (float) config.rotationX;
            float rotY = (float) config.rotationY;
            float rotZ = (float) config.rotationZ;
            float posX = (float) config.posX / 100.0f;
            float posY = (float) config.posY / 100.0f;
            float posZ = (float) config.posZ / 100.0f;

            poseStack.translate(posX, posY, posZ);
            poseStack.rotateDegrees(Axis.XP, rotX);
            poseStack.rotateDegrees(Axis.YP, rotY);
            poseStack.rotateDegrees(Axis.ZP, rotZ);
            poseStack.scale(scale, scale, scale);
        } else if (hand == InteractionHand.OFF_HAND) {
            float posX = -(float) config.posX / 100.0f;
            float posY = (float) config.posY / 100.0f;
            float posZ = (float) config.posZ / 100.0f;

            poseStack.translate(posX, posY, posZ);
            poseStack.scale(scale, scale, scale);
        }
    }

    @Inject(method = "applyEatTransform", at = @At("HEAD"), cancellable = true)
    private void onApplyEatTransform(PoseStack poseStack, float f, HumanoidArm humanoidArm, float g, int i, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config.dulkirEnabled && config.rotationlessDrink) {
            ci.cancel();
        }
    }

    @Inject(method = "applyItemArmTransform", at = @At("HEAD"), cancellable = true)
    private void onApplyItemArmTransform(PoseStack poseStack, HumanoidArm humanoidArm, float f, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config.dulkirEnabled && config.cancelReEquip) {
            int dir = (humanoidArm == HumanoidArm.RIGHT) ? 1 : -1;
            poseStack.translate((float) dir * 0.56f, -0.52f, -0.72f);
            ci.cancel();
        }
    }
}
