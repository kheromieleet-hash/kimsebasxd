package com.client.mod.mixin;

import com.client.mod.IChatComponentHelper;
import com.client.mod.ModConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Predicate;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin implements IChatComponentHelper {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;
    @Shadow private Predicate<GuiMessage> visibleMessageFilter;

    @Shadow protected abstract void logChatMessage(GuiMessage message);
    @Shadow protected abstract void addMessageToDisplayQueue(GuiMessage message);
    @Shadow protected abstract void addMessageToQueue(GuiMessage message);
    @Shadow protected abstract void refreshTrimmedMessages();
    @Shadow protected abstract double getScale();
    @Shadow protected abstract int getWidth();
    @Shadow protected abstract int getLineHeight();
    @Shadow public abstract int getLinesPerPage();

    @Unique private static final ZoneId ISTANBUL_ZONE = ZoneId.of("Europe/Istanbul");
    @Unique private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    @Unique private static String lastRawText = null;
    @Unique private static Component lastBaseComponent = null;
    @Unique private static int duplicateCount = 1;
    @Unique private static String lastTimestamp = null;

    @ModifyConstant(
            method = {"addMessageToQueue", "addMessageToDisplayQueue"},
            constant = @Constant(intValue = 100)
    )
    private int modifyMaxChatHistory(int original) {
        ModConfig config = ModConfig.getInstance();
        return (config != null && config.chatInfinite) ? 16384 : original;
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (message == null) {
            return;
        }

        ModConfig config = ModConfig.getInstance();
        String rawText = message.getString();
        com.client.mod.AutoLoginHandler.onChatMessage(rawText);
        com.client.mod.AntiVanishHandler.onChatMessage(rawText);

        // Anti-Clear: Drop empty/whitespace messages sent by server /clearchat routines
        if (config != null && config.chatAntiClear && rawText.trim().isEmpty()) {
            ci.cancel();
            return;
        }

        boolean canCompact = config != null && config.compactChat && !rawText.trim().isEmpty();

        // Check for duplicate consecutive message
        if (canCompact && !this.allMessages.isEmpty() && rawText.equals(lastRawText) && lastBaseComponent != null) {
            duplicateCount++;

            MutableComponent updated = Component.empty();
            if (config.chatTimestamps && lastTimestamp != null) {
                updated.append(Component.literal("<" + lastTimestamp + "> ").withStyle(ChatFormatting.GRAY));
            }
            updated.append(lastBaseComponent);
            updated.append(Component.literal(" (" + duplicateCount + ")").withStyle(ChatFormatting.GRAY));

            int guiTicks = this.minecraft.gui.hud.getGuiTicks();
            GuiMessage updatedMsg = new GuiMessage(guiTicks, updated, signature, source, tag);
            this.allMessages.set(0, updatedMsg);
            this.refreshTrimmedMessages();
            ci.cancel();
            return;
        }

        // Fresh new message
        duplicateCount = 1;
        lastRawText = rawText;
        lastBaseComponent = message;
        lastTimestamp = LocalTime.now(ISTANBUL_ZONE).format(TIME_FORMAT);

        if (config != null && config.chatTimestamps) {
            MutableComponent timestamped = Component.empty();
            timestamped.append(Component.literal("<" + lastTimestamp + "> ").withStyle(ChatFormatting.GRAY));
            timestamped.append(message);

            int guiTicks = this.minecraft.gui.hud.getGuiTicks();
            GuiMessage guiMessage = new GuiMessage(guiTicks, timestamped, signature, source, tag);
            if (this.visibleMessageFilter.test(guiMessage)) {
                this.logChatMessage(guiMessage);
                this.addMessageToDisplayQueue(guiMessage);
                this.addMessageToQueue(guiMessage);
            }
            ci.cancel();
        }
    }

    @Inject(method = "clearMessages", at = @At("HEAD"), cancellable = true)
    private void onClearMessages(boolean clearHistory, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        // Prevent automatic server wipes (clearHistory == false). Allow manual F3+D (clearHistory == true).
        if (config != null && config.chatAntiClear && !clearHistory) {
            ci.cancel();
            return;
        }

        lastRawText = null;
        lastBaseComponent = null;
        duplicateCount = 1;
        lastTimestamp = null;
    }

    @Override
    public String clientMod$getMessageAt(double mouseX, double mouseY) {
        if (this.trimmedMessages == null || this.trimmedMessages.isEmpty()) {
            return null;
        }
        double scale = this.getScale();
        if (scale <= 0.0) {
            scale = 1.0;
        }
        float fScale = (float) scale;
        int chatWidth = Mth.ceil((float) this.getWidth() / fScale);
        double scaledMouseX = mouseX / scale;
        if (scaledMouseX < -4.0 || scaledMouseX > chatWidth + 8.0) {
            return null;
        }
        int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
        int chatBottomY = Mth.floor((float)(screenHeight - 40) / fScale);
        double scaledMouseY = mouseY / scale;
        double diffFromBottom = chatBottomY - scaledMouseY;
        if (diffFromBottom < 0.0) {
            return null;
        }
        int lineHeight = this.getLineHeight();
        if (lineHeight <= 0) {
            lineHeight = 9;
        }
        int i = (int)(diffFromBottom / lineHeight);
        int linesPerPage = this.getLinesPerPage();
        int maxLines = Math.min(linesPerPage, this.trimmedMessages.size() - this.chatScrollbarPos);
        if (i < 0 || i >= maxLines) {
            return null;
        }
        int index = i + this.chatScrollbarPos;
        if (index >= 0 && index < this.trimmedMessages.size()) {
            GuiMessage.Line line = this.trimmedMessages.get(index);
            if (line != null && line.parent() != null && line.parent().content() != null) {
                return line.parent().content().getString();
            }
        }
        return null;
    }
}
