package com.client.mod.mixin;

import com.client.mod.ExploitPacketRules;
import com.client.mod.FreecamHandler;
import com.client.mod.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Redirect(
            method = "aiStep",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/player/ClientInput;tick()V"
            )
    )
    private void onClientInputTick(ClientInput input) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this == mc.player && FreecamHandler.isActive()) {
            input.keyPresses = Input.EMPTY;
            ((ClientInputAccessor) input).sestia$setMoveVector(Vec2.ZERO);
            return;
        }
        input.tick();
    }

    @Inject(method = "itemUseSpeedMultiplier", at = @At("HEAD"), cancellable = true)
    private void onItemUseSpeedMultiplier(CallbackInfoReturnable<Float> cir) {
        Minecraft mc = Minecraft.getInstance();
        if ((Object) this == mc.player && ModConfig.getInstance().noSlowdown) {
            cir.setReturnValue(1.0f);
        }
    }

    @Inject(method = "isSlowDueToUsingItem", at = @At("HEAD"), cancellable = true)
    private void onIsSlowDueToUsingItem(CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        if ((Object) this == mc.player && ExploitPacketRules.shouldAllowNoSlowSprint(cfg.noSlowdown, cfg.noSlowdownMode)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "raycastHitResult", at = @At("HEAD"), cancellable = true)
    private void onRaycastHitResultHead(float partialTick, Entity cameraEntity, CallbackInfoReturnable<HitResult> cir) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self != mc.player || !FreecamHandler.isActive()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (!FreecamHandler.shouldAllowWorldInteraction(true, cfg.freecamAllowInteract)) {
            cir.setReturnValue(BlockHitResult.miss(self.getEyePosition(partialTick), self.getDirection(), self.blockPosition()));
            return;
        }
        cir.setReturnValue(FreecamHandler.raycastFromCamera(self, self.level(), partialTick));
    }

    @Inject(method = "raycastHitResult", at = @At("RETURN"), cancellable = true)
    private void onRaycastHitResult(float partialTick, Entity cameraEntity, CallbackInfoReturnable<HitResult> cir) {
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self != mc.player || FreecamHandler.isActive() || !cfg.noMiningTrace || cameraEntity == null) {
            return;
        }
        if (!(cir.getReturnValue() instanceof EntityHitResult)) {
            return;
        }
        if (cfg.noMiningTraceToolsOnly && !self.getMainHandItem().has(DataComponents.TOOL)) {
            return;
        }
        cir.setReturnValue(cameraEntity.pick(self.blockInteractionRange(), partialTick, false));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (self != mc.player || !cfg.noSlowdown || cfg.noSlowdownMode != ExploitPacketRules.MODE_GRIM) {
            return;
        }
        if (mc.isLocalServer() || mc.gameMode == null || !self.isUsingItem() || (self.tickCount & 1) != 0) {
            return;
        }
        InteractionHand otherHand = self.getUsedItemHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND
                : InteractionHand.MAIN_HAND;
        if (self.getItemInHand(otherHand).isEmpty()) {
            mc.gameMode.useItem(self, otherHand);
        }
    }
}
