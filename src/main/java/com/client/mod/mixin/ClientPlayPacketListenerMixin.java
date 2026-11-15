package com.client.mod.mixin;

import com.client.mod.AutoFishHandler;
import com.client.mod.ExploitPacketRules;
import com.client.mod.ModConfig;
import com.client.mod.PacketMineHandler;
import com.client.mod.WorldRenderToolsHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayPacketListenerMixin {

    @Shadow
    private ClientLevel level;

    @Unique
    private final ExploitPacketRules.BacktrackBuffer<Packet<ClientGamePacketListener>> customclient$backtrackQueue =
            new ExploitPacketRules.BacktrackBuffer<>();

    @Unique
    private boolean customclient$replayingBacktrack = false;

    @Unique
    private Vec3 customclient$preVelocity = null;

    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true)
    private void onHandleParticleEvent(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.exploitGuard) {
            return;
        }
        if (ExploitPacketRules.isDangerousParticlePacket(
                packet.count(),
                cfg.exploitGuardMaxParticles,
                packet.x(),
                packet.y(),
                packet.z(),
                packet.xDist(),
                packet.yDist(),
                packet.zDist(),
                packet.xMaxSpeed(),
                packet.yMaxSpeed(),
                packet.zMaxSpeed())) {
            ci.cancel();
        }
    }

    @Inject(method = "handleExplosion", at = @At("HEAD"), cancellable = true)
    private void onHandleExplosion(ClientboundExplodePacket packet, CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            customclient$preVelocity = mc.player.getDeltaMovement();
        }
        if (!cfg.exploitGuard) {
            return;
        }
        Vec3 center = packet.center();
        Vec3 knockback = packet.playerKnockback().orElse(null);
        boolean hasKnockback = knockback != null;
        if (ExploitPacketRules.isDangerousExplosionPacket(
                center.x,
                center.y,
                center.z,
                packet.radius(),
                packet.blockCount(),
                hasKnockback,
                hasKnockback ? knockback.x : 0.0,
                hasKnockback ? knockback.y : 0.0,
                hasKnockback ? knockback.z : 0.0)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void onHandleExplosionTail(ClientboundExplodePacket packet, CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.newVelocityEnabled || !cfg.newVelocityExplosions || mc.player == null || packet.playerKnockback().isEmpty()) {
            return;
        }
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= cfg.newVelocityChance) {
            return;
        }
        Vec3 pre = customclient$preVelocity != null ? customclient$preVelocity : Vec3.ZERO;
        Vec3 kb = packet.playerKnockback().get();
        double h = cfg.newVelocityHorizontal / 100.0;
        double v = cfg.newVelocityVertical / 100.0;
        mc.player.setDeltaMovement(pre.x + kb.x * h, pre.y + kb.y * v, pre.z + kb.z * h);
    }

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void onHandleSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && packet.id() == mc.player.getId()) {
            customclient$preVelocity = mc.player.getDeltaMovement();
        }
        if (!cfg.exploitGuard) {
            return;
        }
        Vec3 motion = packet.movement();
        if (motion != null && ExploitPacketRules.isDangerousVelocityPacket(motion.x, motion.y, motion.z)) {
            ci.cancel();
        }
    }

    @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
    private void onHandleSetEntityMotionTail(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.newVelocityEnabled || mc.player == null || packet.id() != mc.player.getId()) {
            return;
        }
        if (java.util.concurrent.ThreadLocalRandom.current().nextInt(100) >= cfg.newVelocityChance) {
            return;
        }
        Vec3 pre = customclient$preVelocity != null ? customclient$preVelocity : Vec3.ZERO;
        Vec3 kb = packet.movement();
        if (cfg.newVelocityMode == 1) {
            if (mc.player.onGround() && kb.horizontalDistanceSqr() > 0.0004) {
                mc.player.jumpFromGround();
            }
            return;
        }
        double h = cfg.newVelocityMode == 2 ? (cfg.newVelocityHorizontal * 0.6 / 100.0) : (cfg.newVelocityHorizontal / 100.0);
        double v = cfg.newVelocityVertical / 100.0;
        mc.player.setDeltaMovement(
                pre.x + (kb.x - pre.x) * h,
                pre.y + (kb.y - pre.y) * v,
                pre.z + (kb.z - pre.z) * h
        );
    }

    @Inject(
            method = "handleMoveEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onHandleMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        if (this.level == null) {
            return;
        }
        Entity entity = packet.getEntity(this.level);
        if (shouldDelayBacktrack(entity)) {
            enqueueOrFlushOverflow(packet);
            ci.cancel();
        }
    }

    @Inject(
            method = "handleEntityPositionSync",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onHandleEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        if (this.level == null) {
            return;
        }
        Entity entity = this.level.getEntity(packet.id());
        if (shouldDelayBacktrack(entity)) {
            enqueueOrFlushOverflow(packet);
            ci.cancel();
        }
    }

    @Inject(
            method = "handleTeleportEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void onHandleTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        if (this.level == null) {
            return;
        }
        Entity entity = this.level.getEntity(packet.id());
        if (shouldDelayBacktrack(entity)) {
            enqueueOrFlushOverflow(packet);
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void onTick(CallbackInfo ci) {
        if (this.customclient$backtrackQueue.size() == 0) {
            return;
        }
        if (this.level == null) {
            this.customclient$backtrackQueue.clear();
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        long maxDelayMs = cfg.backtrack ? cfg.backtrackDelay : 0L;
        List<Packet<ClientGamePacketListener>> ready = this.customclient$backtrackQueue.pollReady(
                System.currentTimeMillis(),
                maxDelayMs
        );
        if (!ready.isEmpty()) {
            replayPackets(ready);
        }
    }

    @Inject(method = "clearLevel", at = @At("HEAD"))
    private void onClearLevel(CallbackInfo ci) {
        this.customclient$backtrackQueue.clear();
        PacketMineHandler.clear();
        WorldRenderToolsHandler.clearLightCache();
    }

    @Inject(method = "setTitleText", at = @At("TAIL"))
    private void onSetTitleText(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            AutoFishHandler.onTitlePacket(packet.text());
        }
    }

    @Inject(method = "setSubtitleText", at = @At("TAIL"))
    private void onSetSubtitleText(ClientboundSetSubtitleTextPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            AutoFishHandler.onSubtitlePacket(packet.text());
        }
    }

    @Inject(method = "setActionBarText", at = @At("TAIL"))
    private void onSetActionBarText(ClientboundSetActionBarTextPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            AutoFishHandler.onActionBarPacket(packet.text());
        }
    }

    @Inject(method = "handleTitlesClear", at = @At("TAIL"))
    private void onHandleTitlesClear(ClientboundClearTitlesPacket packet, CallbackInfo ci) {
        if (Minecraft.getInstance().isSameThread()) {
            AutoFishHandler.onTitlesCleared();
        }
    }

    @Unique
    private boolean shouldDelayBacktrack(Entity entity) {
        if (this.customclient$replayingBacktrack || !ModConfig.getInstance().backtrack || this.level == null) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(entity instanceof Player) || entity == mc.player) {
            return false;
        }
        return entity.distanceToSqr(mc.player) <= 256.0;
    }

    @Unique
    private void enqueueOrFlushOverflow(Packet<ClientGamePacketListener> packet) {
        Packet<ClientGamePacketListener> evicted = this.customclient$backtrackQueue.enqueue(
                packet,
                System.currentTimeMillis()
        );
        if (evicted != null) {
            replayPackets(List.of(evicted));
        }
    }

    @Unique
    private void replayPackets(List<Packet<ClientGamePacketListener>> packets) {
        ClientGamePacketListener listener = (ClientGamePacketListener) (Object) this;
        this.customclient$replayingBacktrack = true;
        try {
            for (Packet<ClientGamePacketListener> pkt : packets) {
                if (this.level == null) {
                    break;
                }
                pkt.handle(listener);
            }
        } finally {
            this.customclient$replayingBacktrack = false;
        }
    }
}
