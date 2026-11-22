package com.client.mod.mixin;

import com.client.mod.PanicHandler;
import com.client.mod.SestiaButtonHoverRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces vanilla stone slider track & chunky gray handle box with Sestia's sheer transparent
 * glass + emerald progress slider while preserving label rendering and Panic Mode fallback.
 */
@Mixin(AbstractSliderButton.class)
public abstract class AbstractSliderButtonMixin extends AbstractWidget {

    @Shadow
    protected double value;

    @Shadow
    protected boolean canChangeValue;

    @Shadow
    private boolean dragging;

    protected AbstractSliderButtonMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void sestia$renderTransparentSlider(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (PanicHandler.isPanicActive()) {
            return;
        }
        boolean highlighted = this.isActive() && (this.isHovered() || this.dragging || this.canChangeValue);
        SestiaButtonHoverRenderer.renderTransparentSlider(
                graphics,
                this.getX(),
                this.getY(),
                this.getWidth(),
                this.getHeight(),
                this.value,
                this.isActive(),
                highlighted,
                this.getAlpha()
        );
        this.extractScrollingStringOverContents(
                graphics.textRendererForWidget(this, GuiGraphicsExtractor.HoveredTextEffects.NONE),
                this.getMessage(),
                2
        );
        this.handleCursor(graphics);
        ci.cancel();
    }
}
