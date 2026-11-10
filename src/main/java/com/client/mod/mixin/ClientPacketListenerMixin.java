package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacket(Packet<?> packet, CallbackInfo ci) {
        ModConfig config = ModConfig.getInstance();
        if (config == null) return;

        if (packet instanceof ServerboundContainerClosePacket close) {
            if (config.invTweaksEnabled && config.invXcarry && close.getContainerId() == 0) {
                ci.cancel();
                return;
            }
        }

        // AbortBreaking: Cancel sending ABORT_DESTROY_BLOCK to keep/freeze break damage on server
        if (packet instanceof ServerboundPlayerActionPacket actionPacket) {
            if (config.abortBreaking && actionPacket.getAction() == ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK) {
                ci.cancel();
            }
        }
    }
}
