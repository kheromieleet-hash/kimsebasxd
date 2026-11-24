package com.client.mod.mixin;

import com.client.mod.CosmeticsLogic;
import com.client.mod.JelloTheme;
import com.client.mod.ModConfig;
import com.client.mod.PanicHandler;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prepends an emerald/theme-accented Sestia star/leaf emblem in the vanilla Tablist
 * for active Sestia Client users when badges are enabled and {@code .panic} is off.
 */
@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabOverlayMixin {

    @Inject(
            method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void sestia$onGetNameForDisplay(PlayerInfo info, CallbackInfoReturnable<Component> cir) {
        if (PanicHandler.isPanicActive() || info == null) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.cosmeticShowBadges) {
            return;
        }
        GameProfile profile = info.getProfile();
        if (profile == null || profile.name() == null || profile.name().isBlank()) {
            return;
        }
        String name = profile.name();
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc != null && mc.getUser() != null
                && mc.getUser().getName() != null
                && mc.getUser().getName().equalsIgnoreCase(name);
        if (!isSelf && !CosmeticsLogic.isOnlineSestiaUser(name)) {
            return;
        }
        Component base = cir.getReturnValue();
        if (base != null) {
            int badgeRgb = (JelloTheme.ACCENT != 0 ? (JelloTheme.ACCENT & 0xFFFFFF) : 0x34D399);
            cir.setReturnValue(Component.literal("✦ ").withColor(badgeRgb).append(base));
        }
    }
}
