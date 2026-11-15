package com.client.mod.mixin;

import com.client.mod.AutoToolHandler;
import com.client.mod.ModConfig;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow
    private int rightClickDelay;

    @Shadow
    @Final
    public Options options;

    @Shadow
    @Final
    public Gui gui;

    @Shadow
    public LocalPlayer player;

    @Shadow
    public ClientLevel level;

    @Shadow
    public HitResult hitResult;

    @Shadow
    public MultiPlayerGameMode gameMode;

    @Shadow
    public MouseHandler mouseHandler;

    @Shadow
    protected int missTime;

    @Shadow
    protected abstract boolean startAttack();

    @Unique
    private boolean wasAutoRightClicking = false;

    @Unique
    private boolean wasAutoBreaking = false;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void onStartAttackFreecam(CallbackInfoReturnable<Boolean> cir) {
        if (com.client.mod.FreecamHandler.isActive() && !ModConfig.getInstance().freecamAllowInteract) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void onStartUseItemGhostHand(CallbackInfo ci) {
        if (com.client.mod.FreecamHandler.isActive() && !ModConfig.getInstance().freecamAllowInteract) {
            ci.cancel();
            return;
        }
        if (com.client.mod.ModConfig.getInstance().ghostHand
                && this.gameMode != null && this.player != null && this.level != null
                && !this.gameMode.isDestroying() && !this.player.isHandsBusy()
                && com.client.mod.GhostHandHandler.tryInteractThroughBlocks(this.player, this.level, this.gameMode, this.hitResult)) {
            this.rightClickDelay = 4;
            ci.cancel();
            return;
        }
        tryBetterPlaceAir(ci);
    }

    @Unique
    private void tryBetterPlaceAir(CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.betterPlace || this.gameMode == null || this.player == null || this.level == null) {
            return;
        }
        if (this.gameMode.isDestroying() || this.player.isHandsBusy()) {
            return;
        }
        if (this.hitResult != null && this.hitResult.getType() != HitResult.Type.MISS) {
            return;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = this.player.getItemInHand(hand);
            if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
                continue;
            }
            Vec3 eyePos = this.player.getEyePosition(1.0f);
            Vec3 look = this.player.getViewVector(1.0f);
            double range = Math.max(2.0, Math.min(6.0, cfg.betterPlaceRange));
            BlockPos targetPos = BlockPos.containing(eyePos.add(look.scale(range)));
            if (!this.level.getBlockState(targetPos).canBeReplaced()) {
                continue;
            }
            if (!this.player.isShiftKeyDown() && this.player.getBoundingBox().intersects(new AABB(targetPos))) {
                continue;
            }
            Direction face = Direction.getApproximateNearest(-look.x, -look.y, -look.z);
            BlockHitResult fakeHit = new BlockHitResult(Vec3.atCenterOf(targetPos), face, targetPos, false);
            InteractionResult res = this.gameMode.useItemOn(this.player, hand, fakeHit);
            if (res instanceof InteractionResult.Success success) {
                if (success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
                    this.player.swing(hand, stack.getInteractAnimation(), false);
                }
                this.rightClickDelay = cfg.fastUseEnabled ? Math.max(0, Math.min(4, cfg.fastUseCooldown)) : 4;
                ci.cancel();
                return;
            }
        }
    }

    // Exact Meteor Client injection point inside startUseItem()
    @Inject(
            method = "startUseItem",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;isItemEnabled(Lnet/minecraft/world/flag/FeatureFlagSet;)Z"
            )
    )
    private void onStartUseItemHand(CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.fastUseEnabled) {
            this.rightClickDelay = Math.max(0, Math.min(4, cfg.fastUseCooldown));
        }
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void onStartUseItemReturn(CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.fastUseEnabled) {
            int target = Math.max(0, Math.min(4, cfg.fastUseCooldown));
            if (this.rightClickDelay > target) {
                this.rightClickDelay = target;
            }
        }
        if (cfg.fastPlace && this.player != null && (this.player.getMainHandItem().getItem() instanceof BlockItem || this.player.getOffhandItem().getItem() instanceof BlockItem)) {
            int target = Math.max(0, Math.min(4, cfg.fastPlaceDelay));
            if (this.rightClickDelay > target) {
                this.rightClickDelay = target;
            }
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickHead(CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.fastUseEnabled) {
            int target = Math.max(0, Math.min(4, cfg.fastUseCooldown));
            if (this.rightClickDelay > target) {
                this.rightClickDelay = target;
            }
        }
        if (cfg.fastPlace && this.player != null && (this.player.getMainHandItem().getItem() instanceof BlockItem || this.player.getOffhandItem().getItem() instanceof BlockItem)) {
            int target = Math.max(0, Math.min(4, cfg.fastPlaceDelay));
            if (this.rightClickDelay > target) {
                this.rightClickDelay = target;
            }
        }
        AutoToolHandler.tick();
        if (this.options != null) {
            if (shouldAutoRightClick(cfg)) {
                this.options.keyUse.setDown(true);
                this.wasAutoRightClicking = true;
            } else if (this.wasAutoRightClicking) {
                this.wasAutoRightClicking = false;
                boolean physicalRight = this.mouseHandler != null && this.mouseHandler.isRightPressed();
                if (!physicalRight) {
                    this.options.keyUse.setDown(false);
                }
            }

            // Auto Break logic
            boolean shouldBreak = false;
            if (cfg.autoBreakEnabled && this.player != null && this.level != null
                    && this.gui != null && this.gui.screen() == null
                    && this.hitResult instanceof BlockHitResult blockHit
                    && blockHit.getType() == HitResult.Type.BLOCK) {
                BlockState state = this.level.getBlockState(blockHit.getBlockPos());
                if (!state.isAir()) {
                    boolean toolOk = true;
                    if (cfg.autoBreakOnlyTool) {
                        toolOk = this.player.getMainHandItem().isCorrectToolForDrops(state);
                    }
                    if (toolOk) {
                        shouldBreak = true;
                    }
                }
            }

            if (shouldBreak) {
                if (this.missTime <= 0 && this.gameMode != null && !this.gameMode.isDestroying()) {
                    this.startAttack();
                }
                this.options.keyAttack.setDown(true);
                this.wasAutoBreaking = true;
            } else if (this.wasAutoBreaking) {
                this.wasAutoBreaking = false;
                boolean physicalLeft = this.mouseHandler != null && this.mouseHandler.isLeftPressed();
                if (!physicalLeft) {
                    this.options.keyAttack.setDown(false);
                }
            }
        }
    }

    @Inject(method = "handleKeybinds", at = @At("HEAD"))
    private void onHandleKeybindsHead(CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.fastUseEnabled) {
            int target = Math.max(0, Math.min(4, cfg.fastUseCooldown));
            if (this.rightClickDelay > target) {
                this.rightClickDelay = target;
            }
        }
        if (shouldAutoRightClick(cfg)) {
            this.options.keyUse.setDown(true);
            this.wasAutoRightClicking = true;
        }
        if (cfg.fastPlace && this.player != null && (this.player.getMainHandItem().getItem() instanceof BlockItem || this.player.getOffhandItem().getItem() instanceof BlockItem)) {
            int target = Math.max(0, Math.min(4, cfg.fastPlaceDelay));
            if (this.rightClickDelay > target) {
                this.rightClickDelay = target;
            }
        }
    }

    @Unique
    private boolean shouldAutoRightClick(ModConfig cfg) {
        return cfg.fastPlace
                && cfg.fastPlaceAutoRightClick
                && this.options != null
                && this.player != null
                && this.gui != null
                && this.gui.screen() == null;
    }

    @Inject(method = "shouldEntityAppearGlowing", at = @At("HEAD"), cancellable = true)
    private void onShouldEntityAppearGlowing(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null) return;
        if (cfg.itemEsp && entity instanceof ItemEntity) {
            cir.setReturnValue(true);
        }
    }
}
