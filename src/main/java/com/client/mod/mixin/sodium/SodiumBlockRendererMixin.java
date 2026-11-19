package com.client.mod.mixin.sodium;

import com.client.mod.SodiumContextAccess;
import com.client.mod.XrayHandler;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.model.MutableQuadViewImpl;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BlockRenderer.class, remap = false)
public abstract class SodiumBlockRendererMixin {

    @Unique
    private boolean customclient$xrayActive = false;

    @Unique
    private boolean customclient$xrayTarget = false;

    @Inject(method = "renderModel", at = @At("HEAD"), cancellable = true)
    private void onRenderModelHead(
            BlockStateModel model,
            BlockState state,
            BlockPos pos,
            BlockPos origin,
            CallbackInfo ci
    ) {
        this.customclient$xrayActive = XrayHandler.isEnabled();
        this.customclient$xrayTarget = false;
        if (!this.customclient$xrayActive) {
            return;
        }
        BlockAndTintGetter lvl = ((SodiumContextAccess) this).sestia$getLevel();
        if (XrayHandler.shouldHideBlock(lvl, pos, state)) {
            ci.cancel();
            return;
        }
        this.customclient$xrayTarget = XrayHandler.isTargetBlock(state);
    }

    @Inject(method = "bufferQuad", at = @At("HEAD"))
    private void onBufferQuadHead(MutableQuadViewImpl quad, float[] brightnesses, Material material, CallbackInfo ci) {
        if (!this.customclient$xrayActive) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            quad.setLight(i, 0x00F000F0);
        }
        int opacity = XrayHandler.effectiveOpacity();
        if (XrayHandler.shouldRenderTranslucent(this.customclient$xrayTarget, opacity)) {
            for (int i = 0; i < 4; i++) {
                quad.setColor(i, XrayHandler.multiplyAlphaARGB(quad.baseColor(i), opacity));
            }
        }
    }

    @ModifyVariable(method = "bufferQuad", at = @At("HEAD"), argsOnly = true)
    private Material modifyXrayMaterial(Material material) {
        if (this.customclient$xrayActive
                && XrayHandler.shouldRenderTranslucent(this.customclient$xrayTarget, XrayHandler.effectiveOpacity())) {
            return DefaultMaterials.TRANSLUCENT;
        }
        return material;
    }
}
