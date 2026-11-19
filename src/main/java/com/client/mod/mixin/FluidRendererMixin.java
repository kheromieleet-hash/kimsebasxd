package com.client.mod.mixin;

import com.client.mod.XrayHandler;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FluidRenderer.class)
public abstract class FluidRendererMixin {

    @Unique
    private static final ThreadLocal<Boolean> CUSTOMCLIENT_SAVED_TRANSLUCENT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void onTesselateFluidHead(
            BlockAndTintGetter level,
            BlockPos pos,
            FluidRenderer.Output output,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        CUSTOMCLIENT_SAVED_TRANSLUCENT.set(XrayHandler.isTranslucentPass());
        if (!XrayHandler.isEnabled()) {
            return;
        }
        if (XrayHandler.shouldHideBlock(level, pos, blockState)) {
            ci.cancel();
            return;
        }
        boolean isTarget = XrayHandler.isTargetBlock(blockState);
        if (XrayHandler.shouldRenderTranslucent(isTarget, XrayHandler.effectiveOpacity())) {
            XrayHandler.setTranslucentPass(true);
        }
    }

    @Inject(method = "tesselate", at = @At("RETURN"))
    private void onTesselateFluidReturn(
            BlockAndTintGetter level,
            BlockPos pos,
            FluidRenderer.Output output,
            BlockState blockState,
            FluidState fluidState,
            CallbackInfo ci
    ) {
        XrayHandler.setTranslucentPass(Boolean.TRUE.equals(CUSTOMCLIENT_SAVED_TRANSLUCENT.get()));
    }
}
