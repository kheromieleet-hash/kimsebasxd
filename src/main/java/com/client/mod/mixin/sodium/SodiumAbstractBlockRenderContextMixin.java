package com.client.mod.mixin.sodium;

import com.client.mod.SodiumContextAccess;
import com.client.mod.XrayHandler;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AbstractBlockRenderContext.class, remap = false)
public abstract class SodiumAbstractBlockRenderContextMixin implements SodiumContextAccess {

    @Shadow
    protected BlockAndTintGetter level;

    @Shadow
    protected BlockState state;

    @Shadow
    protected BlockPos pos;

    @Shadow
    protected boolean useAmbientOcclusion;

    @Override
    public BlockAndTintGetter sestia$getLevel() {
        return this.level;
    }

    private static final ThreadLocal<BlockPos.MutableBlockPos> TL_NEIGHBOR_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    @Inject(method = "isFaceCulled", at = @At("HEAD"), cancellable = true)
    private void onIsFaceCulled(Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (!XrayHandler.isEnabled() || this.state == null || face == null) {
            return;
        }
        boolean isTarget = XrayHandler.isTargetBlock(this.state);
        if (isTarget) {
            if (this.level != null && this.pos != null) {
                BlockPos.MutableBlockPos neighborPos = TL_NEIGHBOR_POS.get().setWithOffset(this.pos, face);
                BlockState neighbor = this.level.getBlockState(neighborPos);
                boolean cull = XrayHandler.isTargetBlock(neighbor) && neighbor.canOcclude();
                cir.setReturnValue(cull);
            } else {
                cir.setReturnValue(false);
            }
            return;
        }
        if (XrayHandler.effectiveOpacity() <= 0) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "prepareAoInfo", at = @At("RETURN"))
    private void onPrepareAoInfo(boolean modelAo, CallbackInfo ci) {
        if (XrayHandler.isEnabled()) {
            this.useAmbientOcclusion = false;
        }
    }
}
