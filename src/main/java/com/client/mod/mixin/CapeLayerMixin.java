package com.client.mod.mixin;

import com.client.mod.CosmeticsLogic;
import com.client.mod.ModConfig;
import com.client.mod.PanicHandler;
import com.client.mod.WingsLogic;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Suppresses vanilla Mojang capes whenever a player has an active Sestia 3D Cloak
 * (and is not wearing Elytra or in {@code .panic} mode).
 */
@Mixin(CapeLayer.class)
public abstract class CapeLayerMixin {

    @Inject(
            method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sestia$onSubmitCape(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            AvatarRenderState state,
            float yRot,
            float xRot,
            CallbackInfo ci
    ) {
        if (PanicHandler.isPanicActive()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null) {
            return;
        }
        boolean hasElytra = state.chestEquipment != null
                && !state.chestEquipment.isEmpty()
                && state.chestEquipment.has(DataComponents.GLIDER);
        boolean isPreview = state.id == WingsLogic.PREVIEW_ENTITY_ID;
        boolean isSelf = false;
        String remoteName = null;
        if (!isPreview) {
            Minecraft mc = Minecraft.getInstance();
            isSelf = mc != null && mc.player != null && state.id == mc.player.getId();
            if (!isSelf && mc != null && mc.level != null && cfg.cosmeticShowFriends && CosmeticsLogic.hasRemoteLoadouts()) {
                Entity e = mc.level.getEntity(state.id);
                if (e instanceof Player p) {
                    remoteName = p.getName().getString();
                }
            }
        }
        if (CosmeticsLogic.shouldSuppressVanillaCape(cfg, false, isPreview, isSelf, hasElytra, remoteName)) {
            ci.cancel();
        }
    }
}
