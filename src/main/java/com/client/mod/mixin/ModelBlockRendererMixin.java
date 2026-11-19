package com.client.mod.mixin;

import com.client.mod.XrayHandler;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererMixin {

    @Unique
    private static final ThreadLocal<Boolean> CUSTOMCLIENT_SAVED_TRANSLUCENT =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void onTesselateBlockHead(
            BlockQuadOutput output,
            float x, float y, float z,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            CallbackInfo ci
    ) {
        CUSTOMCLIENT_SAVED_TRANSLUCENT.set(XrayHandler.isTranslucentPass());
        if (!XrayHandler.isEnabled()) {
            return;
        }
        if (XrayHandler.shouldHideBlock(level, pos, state)) {
            ci.cancel();
            return;
        }
        boolean isTarget = XrayHandler.isTargetBlock(state);
        if (XrayHandler.shouldRenderTranslucent(isTarget, XrayHandler.effectiveOpacity())) {
            XrayHandler.setTranslucentPass(true);
        }
    }

    @Inject(method = "tesselateBlock", at = @At("RETURN"))
    private void onTesselateBlockReturn(
            BlockQuadOutput output,
            float x, float y, float z,
            BlockAndTintGetter level,
            BlockPos pos,
            BlockState state,
            BlockStateModel model,
            long seed,
            CallbackInfo ci
    ) {
        XrayHandler.setTranslucentPass(Boolean.TRUE.equals(CUSTOMCLIENT_SAVED_TRANSLUCENT.get()));
    }
}
