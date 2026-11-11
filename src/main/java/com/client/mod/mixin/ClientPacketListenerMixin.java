package com.client.mod.mixin;

import com.client.mod.ExploitPacketRules;
import com.client.mod.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientPacketListenerMixin {

    @Shadow
    @Final
    protected Connection connection;

    @Unique
    private final ExploitPacketRules.PlayerCommandTracker customclient$commandTracker =
            new ExploitPacketRules.PlayerCommandTracker();

    @Inject(
            method = "handleResourcePackPush",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onHandleResourcePackPush(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.exploitGuard && ExploitPacketRules.isSuspiciousResourcePackUrl(packet.url())) {
            this.connection.send(new ServerboundResourcePackPacket(
                    packet.id(), ServerboundResourcePackPacket.Action.INVALID_URL));
            ci.cancel();
            return;
        }
        if (cfg.resourcePackSpoof) {
            this.connection.send(new ServerboundResourcePackPacket(
                    packet.id(), ServerboundResourcePackPacket.Action.ACCEPTED));
            this.connection.send(new ServerboundResourcePackPacket(
                    packet.id(), ServerboundResourcePackPacket.Action.DOWNLOADED));
            this.connection.send(new ServerboundResourcePackPacket(
                    packet.id(), ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            ci.cancel();
        }
    }

    @ModifyVariable(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), argsOnly = true)
    private Packet<?> onModifySendPacket(Packet<?> packet) {
        if (packet instanceof ServerboundPlayerInputPacket inputPacket) {
            ModConfig cfg = ModConfig.getInstance();
            Minecraft mc = Minecraft.getInstance();
            boolean inWater = mc.player != null && mc.player.isInWater();
            boolean isUsingItem = mc.player != null && mc.player.isUsingItem();
            Input in = inputPacket.input();
            if (ExploitPacketRules.shouldStripInputSprint(
                    cfg.antiHunger, cfg.disabler, cfg.disablerMode, inWater, isUsingItem, in.sprint())) {
                return new ServerboundPlayerInputPacket(new Input(
                        in.forward(), in.backward(), in.left(), in.right(), in.jump(), in.shift(), false));
            }
        }
        return packet;
    }

    @Inject(method = "onDisconnect", at = @At("HEAD"))
    private void onDisconnectReset(DisconnectionDetails details, CallbackInfo ci) {
        this.customclient$commandTracker.reset();
    }
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
                return;
            }
        }

        if (packet instanceof ServerboundPlayerCommandPacket commandPacket) {
            ExploitPacketRules.PlayerCommandKind kind = switch (commandPacket.getAction()) {
                case START_SPRINTING -> ExploitPacketRules.PlayerCommandKind.START_SPRINTING;
                case STOP_SPRINTING -> ExploitPacketRules.PlayerCommandKind.STOP_SPRINTING;
                default -> ExploitPacketRules.PlayerCommandKind.OTHER;
            };
            Minecraft mc = Minecraft.getInstance();
            boolean inWater = mc.player != null && mc.player.isInWater();
            if (ExploitPacketRules.shouldCancelPlayerCommand(
                    config.antiHunger,
                    config.disabler,
                    config.disablerMode,
                    kind,
                    inWater,
                    this.customclient$commandTracker)) {
                ci.cancel();
            }
        }
    }
}
