package com.client.mod.mixin;

import com.client.mod.ItemNotifierHudRenderer;
import com.client.mod.KeybindsHudRenderer;
import com.client.mod.StatsHudRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudOverlayMixin {
    @Shadow
    private boolean isHidden;

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onExtractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!this.isHidden && !com.client.mod.PanicHandler.isPanicActive()) {
            StatsHudRenderer.render(graphics);
            KeybindsHudRenderer.render(graphics);
            ItemNotifierHudRenderer.render(graphics);
        }
    }
}
