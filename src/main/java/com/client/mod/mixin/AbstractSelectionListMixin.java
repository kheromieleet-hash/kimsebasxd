package com.client.mod.mixin;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractSelectionList.class)
public abstract class AbstractSelectionListMixin {
    @Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
    private void onExtractListBackground(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        AbstractWidget self = (AbstractWidget) (Object) this;
        graphics.fill(self.getX(), self.getY(), self.getX() + self.getWidth(), self.getY() + self.getHeight(), 0x28000000);
        ci.cancel();
    }
}
