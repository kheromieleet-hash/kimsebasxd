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
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FirstPersonHandsAndItemsRenderer.class)
public class FirstPersonRendererMixin {
    @Inject(
            method = "submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
                    shift = At.Shift.AFTER
            )
    )
    private void onBeforeRenderHeldItem(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handState, float f, float g, InteractionHand hand, float h, ItemStack itemStack, float i, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (!config.vmEnabled || itemStack.isEmpty()) {
            return;
        }
        float x = (hand == InteractionHand.MAIN_HAND) ? config.vmOffsetX : -config.vmOffsetX;
        poseStack.translate(x, config.vmOffsetY, config.vmOffsetZ);
    }

    @Inject(
            method = "submitArmWithItem(Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/level/FirstPersonHandsAndItemsRenderState;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"
            )
    )
    private void onRenderHeldItem(PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handState, float f, float g, InteractionHand hand, float h, ItemStack itemStack, float i, PoseStack poseStack, SubmitNodeCollector collector, int light, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config.vmEnabled) {
            applyViewmodelRotAndScale(poseStack, config, config.itemScale);
        }
    }

    @Inject(method = "renderPlayerArm", at = @At("HEAD"))
    private void onBeforeRenderPlayerArm(PoseStack poseStack, SubmitNodeCollector collector, int light, float inverseArmHeight, float attackValue, HumanoidArm arm, PlayerRenderState playerState, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config.vmEnabled && config.vmApplyToHand) {
            float x = (arm == HumanoidArm.RIGHT) ? config.vmOffsetX : -config.vmOffsetX;
            poseStack.translate(x, config.vmOffsetY, config.vmOffsetZ);
        }
    }

    @Inject(
            method = "renderPlayerArm",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;renderPlayerHand(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;)V"
            )
    )
    private void onRenderPlayerArm(PoseStack poseStack, SubmitNodeCollector collector, int light, float inverseArmHeight, float attackValue, HumanoidArm arm, PlayerRenderState playerState, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config.vmEnabled && config.vmApplyToHand) {
            applyViewmodelRotAndScale(poseStack, config, 1.0f);
        }
    }

    @Redirect(
            method = "swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V",
                    ordinal = 0
            )
    )
    private void onSwingArmTranslate(PoseStack poseStack, float x, float y, float z) {
        ModConfig config = ModConfig.getInstance();
        if (config.vmEnabled) {
            poseStack.translate(x * config.vmSwingX, y * config.vmSwingY, z * config.vmSwingZ);
        } else {
            poseStack.translate(x, y, z);
        }
    }

    @Inject(method = "applyItemArmTransform", at = @At("HEAD"), cancellable = true)
    private void onApplyItemArmTransform(PoseStack poseStack, HumanoidArm humanoidArm, float f, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config.vmEnabled && config.vmNoEquip) {
            int dir = (humanoidArm == HumanoidArm.RIGHT) ? 1 : -1;
            poseStack.translate((float) dir * 0.56f, -0.52f, -0.72f);
            ci.cancel();
        }
    }

    @Unique
    private static void applyViewmodelRotAndScale(PoseStack poseStack, ModConfig config, float uniformScale) {
        poseStack.rotateDegrees(Axis.XP, config.vmRotX);
        poseStack.rotateDegrees(Axis.YP, config.vmRotY);
        poseStack.rotateDegrees(Axis.ZP, config.vmRotZ);
        poseStack.scale(config.vmScaleX * uniformScale, config.vmScaleY * uniformScale, config.vmScaleZ * uniformScale);
    }
}
