package com.client.mod.mixin;

import com.client.mod.SestiaAddAccountScreen;
import dlovin.inventoryhud.gui.InGameConfigScreen;
import dlovin.inventoryhud.gui.config.ConfigScreen;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public class GuiMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (SestiaAddAccountScreen.tryReplaceAddPopup(screen)) {
            ci.cancel();
            return;
        }
        if (screen instanceof ConfigScreen) {
            Gui self = (Gui) (Object) this;
            if (self.screen() instanceof InGameConfigScreen) {
                self.setScreen(null);
            }
            ci.cancel();
        }
    }
}
