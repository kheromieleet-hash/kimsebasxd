package com.client.mod.mixin;

import net.minecraft.client.gui.screens.LoadingOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.IntSupplier;

/**
 * Forces the Mojang Studios loading/reload overlay background to pure black (0xFF000000)
 * on every startup and resource reload screen.
 */
@Mixin(LoadingOverlay.class)
public class LoadingOverlayMixin {
    @Redirect(
            method = "extractRenderState",
            at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I")
    )
    private int sestia$blackLoadingBackground(IntSupplier supplier) {
        return 0xFF000000;
    }
}
