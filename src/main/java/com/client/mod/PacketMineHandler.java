package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Coordinates Packet Mine background breaking, Instant Rebreak packets, and MC 26.3 Gizmo outline rendering.
 */
public final class PacketMineHandler {

    /** Bridge implemented by MultiPlayerGameModeMixin to dispatch predicted STOP_DESTROY_BLOCK packets. */
    public interface StopPacketSender {
        void customclient$sendStopAndBreak(ClientLevel level, BlockPos pos, Direction dir);
    }

    private static final double SAFE_BLOCK_REACH_BUFFER = 0.95;
    private static final PacketMineState STATE = new PacketMineState();
    private static BlockPos crackPos = null;

    private PacketMineHandler() {
    }

    /**
     * Intercepts startDestroyBlock when clicking an already locked Instant Rebreak position
     * so START_DESTROY_BLOCK is skipped and only STOP_DESTROY_BLOCK is sent.
     */
    public static boolean onStartDestroyAttempt(MultiPlayerGameMode gameMode, BlockPos pos, Direction dir) {
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.packetMine || !cfg.packetMineRebreak || mc.player == null || mc.level == null || gameMode == null) {
            return false;
        }
        if (mc.player.getAbilities().instabuild) {
            clear();
            return false;
        }
        if (!STATE.isRebreakAt(pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        BlockState blockState = mc.level.getBlockState(pos);
        if (blockState.isAir() || blockState.getDestroySpeed(mc.level, pos) < 0.0f) {
            return false;
        }
        STATE.triggerManualRebreak(dir.ordinal(), cfg.packetMineDelay);
        sendStopAndBreak(mc, gameMode, pos, dir);
        return true;
    }

    public static void onAfterStartDestroy(BlockPos pos, Direction dir, float initialProgress) {
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (!cfg.packetMine || mc.player == null || mc.level == null) {
            return;
        }
        if (mc.player.getAbilities().instabuild) {
            clear();
            return;
        }
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || state.getDestroySpeed(mc.level, pos) < 0.0f) {
            return;
        }
        clearCrackOverlayIfNeeded(pos);
        STATE.startTarget(pos.getX(), pos.getY(), pos.getZ(), dir.ordinal(), initialProgress);
    }

    public static boolean shouldSuppressAbort(BlockPos pos) {
        ModConfig cfg = ModConfig.getInstance();
        return cfg.packetMine && pos != null && STATE.isActiveAt(pos.getX(), pos.getY(), pos.getZ());
    }

    public static void syncContinueProgress(BlockPos pos, float externalProgress) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.packetMine && pos != null && STATE.isActiveAt(pos.getX(), pos.getY(), pos.getZ())) {
            STATE.syncExternalProgress(externalProgress);
        }
    }

    public static void tick(MultiPlayerGameMode gameMode, boolean vanillaDestroying, float vanillaProgress) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.packetMine) {
            if (STATE.hasTarget() || crackPos != null) {
                clear();
            }
            return;
        }
        if (!STATE.hasTarget()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || gameMode == null || mc.player.getAbilities().instabuild) {
            clear();
            return;
        }

        BlockPos pos = new BlockPos(STATE.getTargetX(), STATE.getTargetY(), STATE.getTargetZ());
        boolean withinReach = mc.player.isWithinBlockInteractionRange(pos, SAFE_BLOCK_REACH_BUFFER);
        BlockState blockState = mc.level.getBlockState(pos);
        boolean isAir = blockState.isAir();
        boolean unbreakable = !isAir && blockState.getDestroySpeed(mc.level, pos) < 0.0f;

        if (vanillaDestroying) {
            STATE.syncExternalProgress(vanillaProgress);
        }

        // Only advance background progress when vanilla continueDestroyBlock is not driving it
        float perTick = (!isAir && !unbreakable && !vanillaDestroying)
                ? blockState.getDestroyProgress(mc.player, mc.level, pos)
                : 0.0f;

        PacketMineState.TickAction action = STATE.stepTick(
                true,
                isAir,
                unbreakable,
                withinReach,
                perTick,
                cfg.packetMineRebreak,
                cfg.packetMineDelay
        );

        Direction dir = Direction.values()[Math.max(0, Math.min(Direction.values().length - 1, STATE.getDirOrdinal()))];
        if (action == PacketMineState.TickAction.SEND_STOP_AND_BREAK) {
            clearCrackOverlay(mc.level, mc.player.getId());
            sendStopAndBreak(mc, gameMode, pos, dir);
        } else if (action == PacketMineState.TickAction.CLEAR) {
            clearCrackOverlay(mc.level, mc.player.getId());
        } else if (!vanillaDestroying && STATE.getPhase() == PacketMineState.Phase.MINING) {
            int stage = STATE.getDestroyStage();
            if (stage >= 0) {
                mc.level.destroyBlockProgress(mc.player.getId(), pos, stage);
                crackPos = pos.immutable();
            }
        } else if (vanillaDestroying || STATE.getPhase() == PacketMineState.Phase.READY_REBREAK) {
            clearCrackOverlay(mc.level, mc.player.getId());
        }
    }

    public static void renderGizmo() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.packetMine || !cfg.packetMineRender || !STATE.hasTarget()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        BlockPos pos = new BlockPos(STATE.getTargetX(), STATE.getTargetY(), STATE.getTargetZ());
        boolean ready = STATE.getPhase() == PacketMineState.Phase.READY_REBREAK;
        float progress = ready ? 1.0f : Math.max(0.05f, Math.min(1.0f, STATE.getProgress()));

        int stroke = ready ? 0xFF34D399 : 0xFFFF4D4D;
        int fill = ready ? 0x3034D399 : 0x38FF3333;

        // Outer target frame
        Gizmos.cuboid(pos, 0.002f, GizmoStyle.strokeAndFill(stroke, 2.0f, fill));

        // Inner progress pulse box while mining
        if (!ready && progress < 0.999f) {
            double half = 0.5 * progress;
            double cx = pos.getX() + 0.5;
            double cy = pos.getY() + 0.5;
            double cz = pos.getZ() + 0.5;
            AABB inner = new AABB(cx - half, cy - half, cz - half, cx + half, cy + half, cz + half);
            Gizmos.cuboid(inner, GizmoStyle.strokeAndFill(0xCCFF8844, 1.5f, 0x24FF6622));
        }
    }

    public static void clear() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            clearCrackOverlay(mc.level, mc.player.getId());
        } else {
            crackPos = null;
        }
        STATE.clear();
    }

    private static void sendStopAndBreak(Minecraft mc, MultiPlayerGameMode gameMode, BlockPos pos, Direction dir) {
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (gameMode instanceof StopPacketSender sender) {
            sender.customclient$sendStopAndBreak(mc.level, pos, dir);
        }
    }

    private static void clearCrackOverlayIfNeeded(BlockPos nextPos) {
        if (crackPos != null && !crackPos.equals(nextPos)) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                mc.level.destroyBlockProgress(mc.player.getId(), crackPos, -1);
            }
            crackPos = null;
        }
    }

    private static void clearCrackOverlay(ClientLevel level, int breakerId) {
        if (crackPos != null) {
            level.destroyBlockProgress(breakerId, crackPos, -1);
            crackPos = null;
        }
    }
}
