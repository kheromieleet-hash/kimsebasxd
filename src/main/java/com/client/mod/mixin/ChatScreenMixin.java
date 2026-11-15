package com.client.mod.mixin;

import com.client.mod.IChatComponentHelper;
import com.client.mod.ModConfig;
import com.client.mod.MouseButtonMapping;
import com.client.mod.SestiaChatClient;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "handleChatInput(Ljava/lang/String;Z)V", at = @At("HEAD"), cancellable = true)
    private void onHandleChatInput(String message, boolean addToRecent, CallbackInfo ci) {
        ChatScreen self = (ChatScreen) (Object) this;
        String normalized = self.normalizeChatMessage(message);
        if (normalized.isEmpty()) {
            return;
        }
        String trimmed = normalized.trim();
        if (trimmed.equalsIgnoreCase(".coordinates") || trimmed.equalsIgnoreCase(".coords")) {
            if (this.minecraft != null && this.minecraft.player != null) {
                int x = (int) Math.floor(this.minecraft.player.getX());
                int y = (int) Math.floor(this.minecraft.player.getY());
                int z = (int) Math.floor(this.minecraft.player.getZ());
                String coords = x + ", " + y + ", " + z;
                this.minecraft.keyboardHandler.setClipboard(coords);
                if (this.minecraft.gui != null && this.minecraft.gui.hud != null && this.minecraft.gui.hud.getChat() != null) {
                    this.minecraft.gui.hud.getChat().addClientSystemMessage(
                            Component.literal("§a[Sestia] §fCoordinates copied to clipboard: §e" + coords)
                    );
                }
            }
            if (addToRecent && this.minecraft != null && this.minecraft.gui != null && this.minecraft.gui.hud != null) {
                this.minecraft.gui.hud.getChat().addRecentChat(normalized);
            }
            ci.cancel();
            return;
        }
        if (SestiaChatClient.handleOutgoingChat(normalized)) {
            if (addToRecent && this.minecraft != null && this.minecraft.gui != null && this.minecraft.gui.hud != null) {
                this.minecraft.gui.hud.getChat().addRecentChat(normalized);
            }
            ci.cancel();
        }
    }

    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        ModConfig config = ModConfig.getInstance();
        if (config == null || !config.chatClickCopy) {
            return;
        }

        boolean isRightClick = MouseButtonMapping.isRightClick(event.button());
        boolean isShiftLeftClick = MouseButtonMapping.isLeftClick(event.button()) && event.hasShiftDown();

        if (isRightClick || isShiftLeftClick) {
            if (this.minecraft != null && this.minecraft.gui != null && this.minecraft.gui.hud != null) {
                Object chat = this.minecraft.gui.hud.getChat();
                if (chat instanceof IChatComponentHelper helper) {
                    String message = helper.clientMod$getMessageAt(event.x(), event.y());
                    if (message != null && !message.trim().isEmpty()) {
                        this.minecraft.keyboardHandler.setClipboard(message);
                        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.4F));
                        this.minecraft.gui.hud.setOverlayMessage(Component.literal("§a[Sestia] Chat message copied to clipboard!"), false);
                        cir.setReturnValue(true);
                    }
                }
            }
        }
    }
}
