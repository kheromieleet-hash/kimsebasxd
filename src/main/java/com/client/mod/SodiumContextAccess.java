package com.client.mod;

import net.minecraft.client.renderer.block.BlockAndTintGetter;

/**
 * Exposes Sodium AbstractBlockRenderContext's protected LevelSlice/BlockAndTintGetter cleanly
 * from outside the mixin package (required by Sponge Mixin's package isolation rule).
 */
public interface SodiumContextAccess {
    BlockAndTintGetter sestia$getLevel();
}
