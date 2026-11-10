package com.client.mod.mixin;

import com.client.mod.EspRenderHandler;
import com.client.mod.KeybindsHudRenderer;
import com.client.mod.StatsHudRenderer;
import com.client.mod.UpdateNoticeManager;
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
        if (!this.isHidden) {
            StatsHudRenderer.render(graphics);
            KeybindsHudRenderer.render(graphics);
            EspRenderHandler.render(graphics, deltaTracker);
            UpdateNoticeManager.renderHudToast(graphics);
        }
    }
}
