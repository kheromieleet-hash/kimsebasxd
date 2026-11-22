package com.client.mod.mixin;

import com.client.mod.PanicHandler;
import com.client.mod.SestiaButtonHoverRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the opaque vanilla button sprite with Sestia's sheer transparent glass button
 * and emerald hover frame while preserving label rendering and Panic Mode fallback.
 */
@Mixin(AbstractButton.class)
public abstract class AbstractButtonMixin {

    @Inject(method = "extractDefaultSprite", at = @At("HEAD"), cancellable = true)
    private void sestia$renderTransparentButton(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (PanicHandler.isPanicActive()) {
            return;
        }
        AbstractButton self = (AbstractButton) (Object) this;
        SestiaButtonHoverRenderer.renderTransparentButton(
                graphics,
                self.getX(),
                self.getY(),
                self.getWidth(),
                self.getHeight(),
                self.isActive(),
                self.isHovered(),
                self.getAlpha()
        );
        ci.cancel();
    }
}
