package com.client.mod.mixin;

import com.client.mod.ExploitPacketRules;
import com.client.mod.ModConfig;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.handshake.ClientIntentionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(Connection.class)
public abstract class ConnectionMixin {

    @Shadow
    public abstract PacketFlow getSending();

    @Shadow
    public abstract void send(Packet<?> packet, ChannelFutureListener listener, boolean flush);

    @Unique
    private record PendingPingPacket(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
    }

    @Unique
    private static final ThreadLocal<Boolean> CUSTOMCLIENT_BYPASS_PING_SPOOF =
            ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Unique
    private final ExploitPacketRules.PingSpoofBuffer<PendingPingPacket> customclient$pingSpoofBuffer =
            new ExploitPacketRules.PingSpoofBuffer<>();

    @ModifyVariable(method = "doSendPacket", at = @At("HEAD"), argsOnly = true)
    private Packet<?> onDoSendPacket(Packet<?> packet) {
        if (packet instanceof ClientIntentionPacket orig && !com.client.mod.PanicHandler.isPanicActive()) {
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

    @Inject(
            method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onSendPacketDelay(Packet<?> packet, ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
        if (this.getSending() != PacketFlow.SERVERBOUND || Boolean.TRUE.equals(CUSTOMCLIENT_BYPASS_PING_SPOOF.get())) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (config == null || !config.pingSpoof) {
            return;
        }
        if (!(packet instanceof ServerboundKeepAlivePacket || packet instanceof ServerboundPongPacket)) {
            return;
        }
        PendingPingPacket overflow = this.customclient$pingSpoofBuffer.enqueueAndPollOverflow(
                new PendingPingPacket(packet, listener, flush),
                System.currentTimeMillis()
        );
        if (overflow != null) {
            flushPingPackets(List.of(overflow));
        }
        ci.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTickPingSpoof(CallbackInfo ci) {
        if (this.getSending() != PacketFlow.SERVERBOUND || this.customclient$pingSpoofBuffer.size() == 0) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        long delayMs = (config != null && config.pingSpoof) ? config.pingSpoofDelay : 0L;
        List<PendingPingPacket> ready = this.customclient$pingSpoofBuffer.pollReady(System.currentTimeMillis(), delayMs);
        if (!ready.isEmpty()) {
            flushPingPackets(ready);
        }
    }

    @Inject(method = "handleDisconnection", at = @At("HEAD"))
    private void onHandleDisconnection(CallbackInfo ci) {
        this.customclient$pingSpoofBuffer.clear();
    }

    @Unique
    private void flushPingPackets(List<PendingPingPacket> ready) {
        CUSTOMCLIENT_BYPASS_PING_SPOOF.set(Boolean.TRUE);
        try {
            for (PendingPingPacket entry : ready) {
                this.send(entry.packet(), entry.listener(), entry.flush());
            }
        } finally {
            CUSTOMCLIENT_BYPASS_PING_SPOOF.remove();
        }
    }
}
