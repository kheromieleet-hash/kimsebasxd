package com.client.mod.mixin;

import com.client.mod.AutoToolHandler;
import com.client.mod.ModConfig;
import com.client.mod.PacketMineHandler;
import com.client.mod.WheatFarmBotHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.prediction.PredictiveAction;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin implements PacketMineHandler.StopPacketSender {
    @Shadow private int destroyDelay;
    @Shadow private float destroyProgress;
    @Shadow private boolean isDestroying;

    @Shadow
    private void startPrediction(ClientLevel level, PredictiveAction action) {
    }

    @Override
    public void customclient$sendStopAndBreak(ClientLevel level, BlockPos pos, Direction dir) {
        this.startPrediction(level, sequence -> {
            ((MultiPlayerGameMode) (Object) this).destroyBlock(pos);
            return new ServerboundPlayerActionPacket(
                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                    pos,
                    dir,
                    sequence
            );
        });
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onStartDestroyBlock(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (WheatFarmBotHandler.onStickLeftClickBlock(pos)) {
            cir.setReturnValue(false);
            return;
        }
        AutoToolHandler.onStartMining(pos);
        if (PacketMineHandler.onStartDestroyAttempt((MultiPlayerGameMode) (Object) this, pos, dir)) {
            cir.setReturnValue(true);
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (config != null && config.fastBreak) {
            // Vulcan Anti-Cheat bypass: 1 tick delay (50ms) to bypass Vulcan Delay check
            // Vanilla mode: 0 tick delay
            this.destroyDelay = (config.fastBreakMode == 1) ? 1 : 0;
        }
    }

    @Inject(method = "startDestroyBlock", at = @At("RETURN"))
    private void onStartDestroyBlockReturn(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            PacketMineHandler.onAfterStartDestroy(pos, dir, this.destroyProgress);
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
    private void onContinueDestroyBlock(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        AutoToolHandler.onStartMining(pos);
        ModConfig config = ModConfig.getInstance();
        if (config != null && config.fastBreak) {
            if (config.fastBreakMode == 1) {
                // Vulcan Mode:
                // 1) Enforce safe delay (1 tick)
                if (this.destroyDelay > 1) {
                    this.destroyDelay = 1;
                }
                // 2) Vulcan Ping/Tolerance Buffer: Vulcan accepts block break at 0.78f progress
                if (this.destroyProgress >= 0.78f) {
                    this.destroyProgress = 1.0f;
                } else {
                    // Safe smooth boost within Vulcan's 15% tolerance window
                    float safeSpeed = Math.min(config.fastBreakSpeed, 1.15f);
                    if (safeSpeed > 1.0f) {
                        this.destroyProgress += (safeSpeed - 1.0f) * 0.05f;
                    } else {
                        this.destroyProgress += 0.012f;
                    }
                }
            } else {
                // Vanilla mode: instant 0 delay and direct speed boost
                this.destroyDelay = 0;
                if (config.fastBreakSpeed > 1.0f) {
                    this.destroyProgress += (config.fastBreakSpeed - 1.0f) * 0.05f;
                }
            }
        }
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"))
    private void onStopDestroyBlock(CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config != null && config.fastBreak) {
            this.destroyDelay = (config.fastBreakMode == 1) ? 1 : 0;
        }
    }

    @Inject(method = "continueDestroyBlock", at = @At("RETURN"))
    private void onContinueDestroyBlockReturn(BlockPos pos, Direction dir, CallbackInfoReturnable<Boolean> cir) {
        PacketMineHandler.syncContinueProgress(pos, this.destroyProgress);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickPacketMine(CallbackInfo ci) {
        PacketMineHandler.tick((MultiPlayerGameMode) (Object) this, this.isDestroying, this.destroyProgress);
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void onUseItemOnNoStrip(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        if (hand == InteractionHand.MAIN_HAND && hitResult != null
                && WheatFarmBotHandler.onStickRightClickBlock(player, hitResult.getBlockPos())) {
            cir.setReturnValue(InteractionResult.SUCCESS);
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (config == null || !config.noStrip || player == null || hitResult == null) {
            return;
        }
        if (config.noStripAllowSneak && player.isShiftKeyDown()) {
            return;
        }
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEmpty() || !stack.is(ItemTags.AXES)) {
            return;
        }
        BlockState state = player.level().getBlockState(hitResult.getBlockPos());
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.BAMBOO_BLOCKS)) {
            String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if (!path.startsWith("stripped_")) {
                cir.setReturnValue(InteractionResult.PASS);
            }
        }
    }
}
