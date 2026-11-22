package com.client.mod.mixin;

import com.client.mod.SestiaCursorManager;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hooks MC 26.3 SDL3 cursor selection to display Sestia's custom hemp-leaf hardware pointer.
 */
@Mixin(Window.class)
public abstract class WindowCursorMixin {

    @Shadow
    private boolean allowCursorChanges;

    @Shadow
    private CursorType currentCursor;

    @Inject(method = "selectCursor", at = @At("HEAD"), cancellable = true)
    private void sestia$onSelectCursor(CursorType cursor, CallbackInfo ci) {
        if (SestiaCursorManager.applyCustomCursor(cursor, this.allowCursorChanges)) {
            // Reset cached vanilla cursor so transitioning to IBEAM or Panic Mode always invokes target.select()
            this.currentCursor = null;
            ci.cancel();
        }
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void sestia$onWindowClose(CallbackInfo ci) {
        SestiaCursorManager.destroy();
    }
}
