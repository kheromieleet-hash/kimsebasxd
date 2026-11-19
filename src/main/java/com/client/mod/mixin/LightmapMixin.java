package com.client.mod.mixin;

import com.client.mod.ModConfig;
import com.client.mod.XrayHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.renderer.Lightmap;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Lightmap.class)
public class LightmapMixin {
    @Unique
    private static final Vector4f FULLBRIGHT_WHITE = new Vector4f(1.0f, 1.0f, 1.0f, 1.0f);

    @Shadow
    @Final
    private GpuTexture texture;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRenderLightmap(LightmapRenderState state, CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.fullbright || XrayHandler.isEnabled()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(this.texture, FULLBRIGHT_WHITE);
            ci.cancel();
        }
    }
}
