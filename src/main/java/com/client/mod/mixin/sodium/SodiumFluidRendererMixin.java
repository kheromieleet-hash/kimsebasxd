package com.client.mod.mixin.sodium;

import com.client.mod.XrayHandler;
import net.caffeinemc.mods.sodium.client.model.color.ColorProvider;
import net.caffeinemc.mods.sodium.client.model.light.data.QuadLightData;
import net.caffeinemc.mods.sodium.client.model.quad.ModelQuadView;
import net.caffeinemc.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.DefaultFluidRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.material.Material;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.TranslucentGeometryCollector;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DefaultFluidRenderer.class, remap = false)
public abstract class SodiumFluidRendererMixin {

    @Shadow private QuadLightData quadLightData;
    @Shadow private int[] quadColors;

    @Unique
    private boolean customclient$xrayActive = false;

    @Unique
    private boolean customclient$xrayTarget = false;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderFluid(
            LevelSlice level,
            BlockState blockState,
            FluidState fluidState,
            BlockPos blockPos,
            BlockPos offset,
            TranslucentGeometryCollector collector,
            ChunkModelBuilder meshBuilder,
            Material material,
            ColorProvider<FluidState> colorProvider,
            FluidModel fluidModel,
            CallbackInfo ci
    ) {
        this.customclient$xrayActive = XrayHandler.isEnabled();
        this.customclient$xrayTarget = false;
        if (!this.customclient$xrayActive) {
            return;
        }
        if (XrayHandler.shouldHideBlock((net.minecraft.world.level.BlockGetter) (Object) level, blockPos, blockState)) {
            ci.cancel();
            return;
        }
        this.customclient$xrayTarget = XrayHandler.isTargetBlock(blockState);
    }

    @Inject(method = "writeQuad", at = @At("HEAD"))
    private void onWriteFluidQuadHead(
            ChunkModelBuilder builder,
            TranslucentGeometryCollector collector,
            Material material,
            BlockPos offset,
            ModelQuadView quad,
            ModelQuadFacing facing,
            boolean flip,
            CallbackInfo ci
    ) {
        if (!this.customclient$xrayActive) {
            return;
        }
        if (this.quadLightData != null && this.quadLightData.lm != null) {
            for (int i = 0; i < 4 && i < this.quadLightData.lm.length; i++) {
                this.quadLightData.lm[i] = 0x00F000F0;
            }
        }
        int opacity = XrayHandler.effectiveOpacity();
        if (this.quadColors != null && XrayHandler.shouldRenderTranslucent(this.customclient$xrayTarget, opacity)) {
            for (int i = 0; i < 4 && i < this.quadColors.length; i++) {
                this.quadColors[i] = XrayHandler.multiplyAlphaARGB(this.quadColors[i], opacity);
            }
        }
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true)
    private Material modifyFluidMaterial(
            Material material,
            LevelSlice level,
            BlockState blockState
    ) {
        if (XrayHandler.isEnabled()
                && XrayHandler.shouldRenderTranslucent(XrayHandler.isTargetBlock(blockState), XrayHandler.effectiveOpacity())) {
            return DefaultMaterials.TRANSLUCENT;
        }
        return material;
    }
}
