package com.client.mod.mixin;

import com.client.mod.SestiaAccountScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.gui.components.SplashRenderer;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
    @Shadow
    private boolean fading;

    @Shadow
    private SplashRenderer splash;

    @Shadow
    private FriendsButton friends;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onInitTail(CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        this.fading = false;
        this.splash = null;
        SestiaAccountScreen.disableIasDefaultWidgets();

        int middleRowY = this.friends != null ? this.friends.getY() : (this.height / 4 + 48 + 72);
        for (GuiEventListener child : new ArrayList<>(this.children())) {
            if (child == this.friends
                    || child instanceof PlainTextButton
                    || (child instanceof SpriteIconButton iconBtn
                    && iconBtn.getY() == middleRowY
                    && iconBtn.getWidth() == 20
                    && iconBtn.getHeight() == 20)) {
                this.removeWidget(child);
            }
        }
        this.friends = null;

        for (GuiEventListener child : this.children()) {
            if (child instanceof Button btn
                    && btn.getY() == middleRowY + 24
                    && btn.getWidth() == 98
                    && btn.getHeight() == 20) {
                btn.setY(middleRowY);
            }
        }
    }

    @Redirect(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"
            )
    )
    private void sestia$hideVersionFooter(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            graphics.text(font, text, x, y, color);
        }
    }
}
