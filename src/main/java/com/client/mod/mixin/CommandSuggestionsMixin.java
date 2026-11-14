package com.client.mod.mixin;

import com.client.mod.DotCommandSuggester;
import com.client.mod.ModConfig;
import com.client.mod.SestiaChatClient;
import com.client.mod.SestiaChatProtocol;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {

    @Shadow
    @Final
    private EditBox input;

    @Shadow
    @Final
    private Font font;

    @Shadow
    @Final
    private List<FormattedCharSequence> commandUsage;

    @Shadow
    private int commandUsagePosition;

    @Shadow
    private int commandUsageWidth;

    @Shadow
    private ParseResults<ClientSuggestionProvider> currentParse;

    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private CommandSuggestions.SuggestionsList suggestions;

    @Shadow
    private boolean allowSuggestions;

    @Shadow
    private boolean keepSuggestions;

    @Shadow
    private boolean currentParseIsCommand;

    @Shadow
    private boolean currentParseIsMessage;

    @Shadow
    public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void onUpdateCommandInfo(CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.clientChatEnabled || this.input == null) {
            return;
        }
        String value = this.input.getValue();
        if (value == null || value.isEmpty()) {
            return;
        }
        int cursor = Math.max(0, Math.min(this.input.getCursorPosition(), value.length()));
        String upToCursor = value.substring(0, cursor);
        if (!DotCommandSuggester.isDotCommandInput(upToCursor)) {
            return;
        }

        this.currentParse = null;
        this.currentParseIsCommand = false;
        this.currentParseIsMessage = false;
        if (!this.keepSuggestions) {
            this.input.setSuggestion(null);
            this.suggestions = null;
        }
        this.commandUsage.clear();

        if (this.suggestions == null || !this.keepSuggestions) {
            Suggestions built = DotCommandSuggester.buildSuggestions(
                    value,
                    cursor,
                    SestiaChatClient.getSuggestedPlayers(),
                    SestiaChatProtocol.parseMuteSet(cfg.clientChatMuted)
            );
            this.pendingSuggestions = CompletableFuture.completedFuture(built);
            if (!built.isEmpty()) {
                if (this.allowSuggestions) {
                    this.showSuggestions(false);
                }
            } else {
                String usage = DotCommandSuggester.resolveUsageHint(upToCursor);
                if (!usage.isEmpty()) {
                    this.commandUsage.add(Component.literal(usage).withStyle(ChatFormatting.GRAY).getVisualOrderText());
                    this.commandUsageWidth = this.font.width(usage);
                    this.commandUsagePosition = 0;
                }
            }
        }
        ci.cancel();
    }
}
