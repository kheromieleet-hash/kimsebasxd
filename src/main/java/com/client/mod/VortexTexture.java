package com.client.mod;

import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.AbstractTexture;

public class VortexTexture extends AbstractTexture {
    public VortexTexture(GpuTexture texture, GpuTextureView view, GpuSampler sampler) {
        this.texture = texture;
        this.textureView = view;
        this.sampler = sampler;
    }
}
