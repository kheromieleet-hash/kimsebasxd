package com.client.mod.mixin;

import com.client.mod.CosmeticsLogic;
import com.client.mod.FreecamHandler;
import com.client.mod.ModConfig;
import com.client.mod.PanicHandler;
import com.client.mod.WingsLogic;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
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
    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void onSubmitHandsWithItems(float f, PoseStack poseStack, SubmitNodeCollector collector, PlayerRenderState playerState, FirstPersonHandsAndItemsRenderState handState, CallbackInfo ci) {
        if (FreecamHandler.isActive()) {
            ci.cancel();
        }
    }

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
        if (!PanicHandler.isPanicActive()) {
            if (config.cosmeticHandAuraEnabled && hand == InteractionHand.MAIN_HAND && !itemStack.isEmpty()) {
                sestia$submitHandAura(poseStack, collector, config);
            }
            if (config.cosmeticSlashArcEnabled && hand == InteractionHand.MAIN_HAND && h > 0.04f && h < 0.94f) {
                sestia$submitSlashArc(poseStack, collector, config, h);
            }
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

    @Unique
    private static void sestia$submitHandAura(PoseStack poseStack, SubmitNodeCollector collector, ModConfig cfg) {
        final float time = (System.currentTimeMillis() % 100000L) * 0.005f;
        final int mode = cfg.cosmeticHandAuraMode;
        final int rgb = CosmeticsLogic.resolveColorRgb(cfg.cosmeticAuraColorPreset, cfg.cosmeticAuraCustomRgb, time * 4.0f, 0.0f);
        final int bright = WingsLogic.blendRgb(rgb, 0xFFFFFF, 0.55f);
        final int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        final int br = (bright >> 16) & 0xFF, bg = (bright >> 8) & 0xFF, bb = bright & 0xFF;

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
            if (mode == CosmeticsLogic.HAND_AURA_FOREARM_RINGS) {
                for (int ring = 0; ring < 2; ring++) {
                    float y = -0.10f + ring * 0.14f;
                    float rad = 0.13f + ring * 0.025f;
                    float spin = time * (ring == 0 ? 1.4f : -1.8f);
                    int segs = 14;
                    for (int s = 0; s < segs; s++) {
                        float a0 = spin + (float) (s * Math.PI * 2.0 / segs);
                        float a1 = spin + (float) ((s + 1) * Math.PI * 2.0 / segs);
                        float x0 = (float) Math.cos(a0) * rad, z0 = (float) Math.sin(a0) * rad;
                        float x1 = (float) Math.cos(a1) * rad, z1 = (float) Math.sin(a1) * rad;
                        float hw = 0.012f;
                        sestia$quad(pose, vc, x0, y - hw, z0, r, g, b, 185, x1, y - hw, z1, br, bg, bb, 215,
                                x1, y + hw, z1, br, bg, bb, 215, x0, y + hw, z0, r, g, b, 185);
                    }
                }
            } else {
                // Blade Edge luminous energy filament along weapon silhouette
                float pulse = 0.65f + 0.35f * (float) Math.sin(time * 2.6f);
                int aCore = Math.round(170 * pulse);
                int aEdge = Math.round(80 * pulse);
                sestia$quad(pose, vc, -0.025f, 0.05f, -0.02f, br, bg, bb, aCore,
                        0.025f, 0.05f, -0.02f, r, g, b, aEdge,
                        0.012f, 0.62f, -0.18f, r, g, b, 15,
                        -0.012f, 0.62f, -0.18f, br, bg, bb, aCore);
            }
        });
    }

    @Unique
    private static void sestia$submitSlashArc(PoseStack poseStack, SubmitNodeCollector collector, ModConfig cfg, float swingProgress) {
        final int mode = cfg.cosmeticSlashArcMode;
        final float time = (System.currentTimeMillis() % 100000L) * 0.005f;
        final int baseRgb = (mode == CosmeticsLogic.SLASH_ARC_CRIMSON_WAVE && cfg.cosmeticSlashArcColorPreset == WingsLogic.COLOR_EMERALD)
                ? 0xF43F5E
                : CosmeticsLogic.resolveColorRgb(cfg.cosmeticSlashArcColorPreset, cfg.cosmeticSlashArcCustomRgb, time * 4.0f, 0.0f);
        final int bright = WingsLogic.blendRgb(baseRgb, 0xFFFFFF, 0.60f);
        final int r = (baseRgb >> 16) & 0xFF, g = (baseRgb >> 8) & 0xFF, b = baseRgb & 0xFF;
        final int br = (bright >> 16) & 0xFF, bg = (bright >> 8) & 0xFF, bb = bright & 0xFF;

        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
            int segs = 12;
            float fade = (float) Math.sin(swingProgress * Math.PI);
            float startDeg = -58.0f + swingProgress * 22.0f;
            float sweepDeg = 112.0f;
            float rIn = 0.36f;
            float rOut = 0.68f;
            for (int s = 0; s < segs; s++) {
                float u0 = (float) s / segs;
                float u1 = (float) (s + 1) / segs;
                float a0 = (float) Math.toRadians(startDeg + u0 * sweepDeg);
                float a1 = (float) Math.toRadians(startDeg + u1 * sweepDeg);
                float taper0 = (float) Math.sin(u0 * Math.PI);
                float taper1 = (float) Math.sin(u1 * Math.PI);
                float ro0 = rIn + (rOut - rIn) * taper0;
                float ro1 = rIn + (rOut - rIn) * taper1;
                int al0 = Math.round(fade * taper0 * 215.0f);
                int al1 = Math.round(fade * taper1 * 215.0f);
                if (al0 < 8 && al1 < 8) continue;

                float ix0 = (float) Math.cos(a0) * rIn - 0.18f;
                float iy0 = (float) Math.sin(a0) * rIn * 0.48f + 0.10f;
                float iz0 = -0.32f - (float) Math.sin(a0) * 0.16f;

                float ox0 = (float) Math.cos(a0) * ro0 - 0.18f;
                float oy0 = (float) Math.sin(a0) * ro0 * 0.48f + 0.10f;
                float oz0 = -0.36f - (float) Math.sin(a0) * 0.18f;

                float ix1 = (float) Math.cos(a1) * rIn - 0.18f;
                float iy1 = (float) Math.sin(a1) * rIn * 0.48f + 0.10f;
                float iz1 = -0.32f - (float) Math.sin(a1) * 0.16f;

                float ox1 = (float) Math.cos(a1) * ro1 - 0.18f;
                float oy1 = (float) Math.sin(a1) * ro1 * 0.48f + 0.10f;
                float oz1 = -0.36f - (float) Math.sin(a1) * 0.18f;

                sestia$quad(pose, vc,
                        ix0, iy0, iz0, r, g, b, Math.max(0, al0 / 3),
                        ox0, oy0, oz0, br, bg, bb, al0,
                        ox1, oy1, oz1, br, bg, bb, al1,
                        ix1, iy1, iz1, r, g, b, Math.max(0, al1 / 3));
            }
        });
    }

    @Unique
    private static void sestia$quad(
            PoseStack.Pose pose, VertexConsumer vc,
            float x0, float y0, float z0, int r0, int g0, int b0, int a0,
            float x1, float y1, float z1, int r1, int g1, int b1, int a1,
            float x2, float y2, float z2, int r2, int g2, int b2, int a2,
            float x3, float y3, float z3, int r3, int g3, int b3, int a3
    ) {
        vc.addVertex(pose, x0, y0, z0).setColor(r0, g0, b0, a0);
        vc.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a1);
        vc.addVertex(pose, x2, y2, z2).setColor(r2, g2, b2, a2);
        vc.addVertex(pose, x3, y3, z3).setColor(r3, g3, b3, a3);
        vc.addVertex(pose, x3, y3, z3).setColor(r3, g3, b3, a3);
        vc.addVertex(pose, x2, y2, z2).setColor(r2, g2, b2, a2);
        vc.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a1);
        vc.addVertex(pose, x0, y0, z0).setColor(r0, g0, b0, a0);
    }
}
