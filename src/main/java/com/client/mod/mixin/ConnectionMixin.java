package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @ModifyVariable(method = "doSendPacket", at = @At("HEAD"), argsOnly = true)
    private Packet<?> onDoSendPacket(Packet<?> packet) {
        if (packet instanceof ClientIntentionPacket orig) {
            ModConfig config = ModConfig.getInstance();
            if (config != null && config.targetProtocol > 0 && orig.protocolVersion() != config.targetProtocol) {
                return new ClientIntentionPacket(
                    config.targetProtocol,
                    orig.hostName(),
                    orig.port(),
                    orig.intention()
                );
            }
        }
        return packet;
    }
}
