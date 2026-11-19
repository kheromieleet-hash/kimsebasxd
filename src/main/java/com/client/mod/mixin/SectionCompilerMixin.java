package com.client.mod.mixin;

import com.client.mod.XrayHandler;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.QuadInstance;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Mixin(SectionCompiler.class)
public abstract class SectionCompilerMixin {

    @ModifyArg(
            method = {"lambda$compile$0", "lambda$compile$1"},
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/chunk/SectionCompiler;getOrBeginLayer(Ljava/util/Map;Lnet/minecraft/client/renderer/SectionBufferBuilderPack;Lnet/minecraft/client/renderer/chunk/ChunkSectionLayer;)Lcom/mojang/blaze3d/vertex/BufferBuilder;"
            ),
            index = 2
    )
    private ChunkSectionLayer redirectXrayChunkLayer(ChunkSectionLayer layer) {
        return XrayHandler.isTranslucentPass() ? ChunkSectionLayer.TRANSLUCENT : layer;
    }

    @Inject(method = {"lambda$compile$0", "lambda$compile$1"}, at = @At("HEAD"))
    private void applyXrayQuadOpacity(
            Map<ChunkSectionLayer, BufferBuilder> map,
            SectionBufferBuilderPack pack,
            float x, float y, float z,
            BakedQuad quad,
            QuadInstance instance,
            CallbackInfo ci
    ) {
        if (XrayHandler.isTranslucentPass()) {
            instance.multiplyColor(XrayHandler.vanillaAlphaMultiplierMask(XrayHandler.effectiveOpacity()));
        }
    }
}
