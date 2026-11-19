package com.client.mod.mixin;

import com.client.mod.XrayHandler;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(method = "shouldRenderFace", at = @At("HEAD"), cancellable = true)
    private static void onShouldRenderFace(
            BlockState state,
            BlockState neighborState,
            Direction direction,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!XrayHandler.isEnabled()) {
            return;
        }
        boolean isTarget = XrayHandler.isTargetBlock(state);
        if (isTarget) {
            // Render all faces of target blocks except shared opaque faces between two target blocks
            cir.setReturnValue(!XrayHandler.isTargetBlock(neighborState) || !neighborState.canOcclude());
            return;
        }
        if (XrayHandler.effectiveOpacity() <= 0) {
            cir.setReturnValue(false);
        }
    }
}
