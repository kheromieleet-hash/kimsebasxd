package com.client.mod;

import com.client.mod.SpawnerPathfinder.SpotCandidate;
import com.client.mod.SpawnerRoomScanner.SpawnerEnclosure;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared path-following, unstuck recovery, reach checks, and elapsed-time formatting
 * for {@link AutoSpawnerModule} and {@link SpawnerAutoSellModule}.
 */
final class SpawnerNavHelper {

    enum WalkResult {
        CONTINUE,
        REACHED,
        UNSTUCK_RESTART,
        ABORTED
    }

    List<BlockPos> activePath = null;
    int pathIdx = 0;
    BlockPos currentNavTarget = null;
    boolean activePathAllowsSpawnerFloor = false;
    double stopHorizReach = 1.98;
    double stopEyeReach = 4.25;
    int nodeStallTicks = 0;
    Vec3 lastProgressPos = null;
    int noProgressTicks = 0;
    int unstuckTicks = 0;
    int unstuckMode = 0;
    int totalWalkTicks = 0;
    int maxWalkTicks = 280;

    volatile boolean moveForward = false;
    volatile boolean moveBackward = false;
    volatile boolean moveLeft = false;
    volatile boolean moveRight = false;
    volatile boolean wantJump = false;
    volatile boolean wantSprint = false;

    void clearMovementFlags() {
        this.moveForward = false;
        this.moveBackward = false;
        this.moveLeft = false;
        this.moveRight = false;
        this.wantJump = false;
        this.wantSprint = false;
    }

    void resetAll(double defaultEyeReach) {
        clearMovementFlags();
        this.activePath = null;
        this.pathIdx = 0;
        this.currentNavTarget = null;
        this.activePathAllowsSpawnerFloor = false;
        this.stopHorizReach = 1.98;
        this.stopEyeReach = defaultEyeReach;
        this.nodeStallTicks = 0;
        this.lastProgressPos = null;
        this.noProgressTicks = 0;
        this.unstuckTicks = 0;
        this.unstuckMode = 0;
        this.totalWalkTicks = 0;
        this.maxWalkTicks = 280;
    }

    void startPathWalk(LocalPlayer player, List<BlockPos> path, int maxTicksBudget) {
        this.activePath = path;
        this.pathIdx = 0;
        this.nodeStallTicks = 0;
        this.lastProgressPos = player.position();
        this.noProgressTicks = 0;
        this.unstuckTicks = 0;
        this.totalWalkTicks = 0;
        this.maxWalkTicks = maxTicksBudget;
    }

    WalkResult tickWalk(
            ClientLevel level,
            LocalPlayer player,
            SpawnerEnclosure enclosure,
            boolean isSpawner
    ) {
        if (this.unstuckTicks > 0) {
            this.unstuckTicks--;
            this.moveForward = false;
            this.moveBackward = (this.unstuckMode == 0 || this.unstuckMode == 1);
            this.moveLeft = (this.unstuckMode == 0 || this.unstuckMode == 2);
            this.moveRight = (this.unstuckMode == 1 || this.unstuckMode == 3);
            this.wantJump = (this.unstuckTicks % 4 == 0);
            this.wantSprint = false;
            if (this.unstuckTicks == 0) {
                this.noProgressTicks = 0;
                this.nodeStallTicks = 0;
                clearMovementFlags();
                return WalkResult.UNSTUCK_RESTART;
            }
            return WalkResult.CONTINUE;
        }

        this.totalWalkTicks++;
        if (this.activePath == null || this.pathIdx >= this.activePath.size() || this.totalWalkTicks >= this.maxWalkTicks) {
            clearMovementFlags();
            Vec3 endPos = player.position();
            Vec3 targetCenter = Vec3.atCenterOf(this.currentNavTarget);
            double endHoriz = Math.hypot(endPos.x - targetCenter.x, endPos.z - targetCenter.z);
            double endEye = player.getEyePosition().distanceTo(targetCenter);
            boolean onTop = isSpawner && isStandingOnTopOfTarget(endPos, this.currentNavTarget);
            boolean reached = (onTop || !isSameColumn(endPos, this.currentNavTarget))
                    && endHoriz <= Math.max(this.stopHorizReach + 0.55, 2.45)
                    && endEye <= Math.max(this.stopEyeReach + 0.45, 4.45);
            return reached ? WalkResult.REACHED : WalkResult.ABORTED;
        }

        Vec3 curPos = player.position();
        Vec3 curEye = player.getEyePosition();
        Vec3 targetCenter = Vec3.atCenterOf(this.currentNavTarget);
        double curHorizDist = Math.hypot(curPos.x - targetCenter.x, curPos.z - targetCenter.z);
        double curEyeDist = curEye.distanceTo(targetCenter);
        double curAxisDist = Math.max(Math.abs(curPos.x - targetCenter.x), Math.abs(curPos.z - targetCenter.z));
        BlockPos flooredStand = new BlockPos(
                (int) Math.floor(curPos.x),
                (int) Math.floor(curPos.y + 0.42),
                (int) Math.floor(curPos.z)
        );

        boolean curOnTop = isSpawner && isStandingOnTopOfTarget(curPos, this.currentNavTarget);
        if ((curOnTop || (!isSameColumn(curPos, this.currentNavTarget) && curAxisDist >= 0.80))
                && curHorizDist <= this.stopHorizReach
                && curEyeDist <= this.stopEyeReach
                && SpawnerPathfinder.hasClearLineOfSightToSpawnerOrChest(level, flooredStand, this.currentNavTarget)) {
            clearMovementFlags();
            return WalkResult.REACHED;
        }

        BlockPos targetNode = this.activePath.get(this.pathIdx);
        if (enclosure != null
                && (targetNode.getX() < enclosure.minX || targetNode.getX() > enclosure.maxX
                || targetNode.getZ() < enclosure.minZ || targetNode.getZ() > enclosure.maxZ)) {
            clearMovementFlags();
            this.pathIdx = this.activePath.size();
            return WalkResult.CONTINUE;
        }

        double nodeCenterX = targetNode.getX() + 0.5;
        double nodeCenterZ = targetNode.getZ() + 0.5;
        double hDist = Math.hypot(nodeCenterX - curPos.x, nodeCenterZ - curPos.z);
        boolean isLastNode = this.pathIdx == this.activePath.size() - 1;
        double reachThreshold = isLastNode ? 0.28 : 0.42;

        if (hDist <= reachThreshold && Math.abs(targetNode.getY() - (curPos.y + 0.2)) <= 1.25) {
            this.pathIdx++;
            this.nodeStallTicks = 0;
            this.lastProgressPos = curPos;
            this.noProgressTicks = 0;
            return WalkResult.CONTINUE;
        }

        this.nodeStallTicks++;
        if (this.nodeStallTicks > 52) { // ~2600ms node timeout
            if (!isLastNode) {
                this.pathIdx++;
                this.nodeStallTicks = 0;
            } else {
                this.pathIdx = this.activePath.size();
            }
            return WalkResult.CONTINUE;
        }

        double tdx = (this.currentNavTarget.getX() + 0.5) - curPos.x;
        double tdz = (this.currentNavTarget.getZ() + 0.5) - curPos.z;
        double destHoriz = Math.hypot(tdx, tdz);
        float sway = (destHoriz > 4.0) ? (float) (Math.sin(this.totalWalkTicks * 0.14) * 0.45) : 0.0f;
        if (!isSpawner) {
            double tdy = (this.currentNavTarget.getY() + 0.5) - curEye.y;
            float destYaw = (float) (Math.toDegrees(Math.atan2(tdz, tdx)) - 90.0);
            float destPitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(tdy, Math.max(0.25, destHoriz))) + sway, -35.0f, 35.0f);
            AutoSpawnerModule.applyHumanRotation(player, destYaw, destPitch, true);
        } else if (destHoriz > 3.5) {
            float destYaw = (float) (Math.toDegrees(Math.atan2(tdz, tdx)) - 90.0);
            AutoSpawnerModule.applyHumanRotation(player, destYaw, 18.0f + sway, true);
        } else if (player.getXRot() > 48.0f) {
            AutoSpawnerModule.applyHumanRotation(player, player.getYRot(), 32.0f, true);
        }

        double dx = nodeCenterX - curPos.x;
        double dy = targetNode.getY() - curPos.y;
        double dz = nodeCenterZ - curPos.z;
        double distXZ = Math.hypot(dx, dz);
        if (distXZ > 1e-4) {
            double wx = dx / distXZ;
            double wz = dz / distXZ;
            double yawRad = Math.toRadians(player.getYRot());
            double fx = -Math.sin(yawRad);
            double fz = Math.cos(yawRad);
            double lx = Math.cos(yawRad);
            double lz = Math.sin(yawRad);
            double fwdDot = wx * fx + wz * fz;
            double leftDot = wx * lx + wz * lz;
            this.moveForward = fwdDot > 0.35;
            this.moveBackward = fwdDot < -0.35;
            this.moveLeft = leftDot > 0.35;
            this.moveRight = leftDot < -0.35;
            if (!this.moveForward && !this.moveBackward && !this.moveLeft && !this.moveRight) {
                if (Math.abs(fwdDot) >= Math.abs(leftDot)) {
                    this.moveForward = fwdDot >= 0;
                    this.moveBackward = fwdDot < 0;
                } else {
                    this.moveLeft = leftDot >= 0;
                    this.moveRight = leftDot < 0;
                }
            }
        } else {
            clearMovementFlags();
        }

        if (this.lastProgressPos == null || curPos.distanceTo(this.lastProgressPos) >= 0.08) {
            this.lastProgressPos = curPos;
            this.noProgressTicks = 0;
        } else {
            this.noProgressTicks++;
            if (this.noProgressTicks >= 26) {
                this.unstuckTicks = 10;
                this.unstuckMode = ThreadLocalRandom.current().nextInt(4);
                this.noProgressTicks = 0;
                return WalkResult.CONTINUE;
            }
        }
        boolean isStuckBriefly = this.noProgressTicks > 6;

        BlockState targetFloorState = level.getBlockState(targetNode.below());
        boolean isSafeInteriorStepUp = (this.activePathAllowsSpawnerFloor || !SpawnerRoomScanner.isSpawnerBlock(targetFloorState))
                && !SpawnerRoomScanner.isFarmContainerBlock(targetFloorState)
                && (enclosure == null || (targetNode.getX() >= enclosure.minX && targetNode.getX() <= enclosure.maxX
                && targetNode.getZ() >= enclosure.minZ && targetNode.getZ() <= enclosure.maxZ));

        this.wantJump = isSafeInteriorStepUp && ((dy >= 0.15 && dy <= 1.65) || (isStuckBriefly && dy >= -0.20));
        float headingYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float headingErr = Math.abs(Mth.wrapDegrees(headingYaw - player.getYRot()));
        this.wantSprint = this.moveForward && !this.wantJump && !isStuckBriefly
                && destHoriz > 4.2 && headingErr <= 25.0f && Math.abs(dy) < 0.15
                && (this.totalWalkTicks % 35) < 28;
        return WalkResult.CONTINUE;
    }

    List<BlockPos> selectSpawnerPath(
            ClientLevel level,
            LocalPlayer player,
            BlockPos targetSpawner,
            SpawnerEnclosure enclosure,
            boolean preferAlternativeSpot
    ) {
        BlockPos startStand = SpawnerRoomScanner.getSpawnerBotStandPos(level, player, enclosure);
        List<SpotCandidate> spots = SpawnerPathfinder.findBestStandSpots(
                level, player, targetSpawner, enclosure, true, preferAlternativeSpot, startStand, true
        );
        List<BlockPos> selectedPath = null;
        for (int i = 0; i < Math.min(spots.size(), 16); i++) {
            SpotCandidate cand = spots.get(i);
            List<BlockPos> p = SpawnerPathfinder.findSpawnerRoomPath(level, startStand, cand.spot(), enclosure, 3000, true);
            if (p != null) {
                selectedPath = p.isEmpty() ? List.of(cand.spot()) : p;
                this.stopHorizReach = Math.max(cand.isDirectlyAdjacent() == 0 ? 1.98 : 2.65, cand.horizDist() + 0.35);
                break;
            }
        }
        if (selectedPath == null && !spots.isEmpty()) {
            SpotCandidate fallback = spots.get(0);
            selectedPath = List.of(fallback.spot());
            this.stopHorizReach = Math.max(2.35, fallback.horizDist() + 0.35);
        }
        return selectedPath;
    }

    static boolean isAlreadyInOptimalReach(
            ClientLevel level,
            LocalPlayer player,
            BlockPos targetPos,
            boolean isSpawner,
            boolean preferAlternativeSpot,
            SpawnerEnclosure enclosure
    ) {
        if (preferAlternativeSpot) return false;
        Vec3 pos = player.position();
        boolean onTop = isSpawner && isStandingOnTopOfTarget(pos, targetPos);
        if (!onTop && isSameColumn(pos, targetPos)) return false;

        Vec3 targetCenter = Vec3.atCenterOf(targetPos);
        double curHorizDist = Math.hypot(pos.x - targetCenter.x, pos.z - targetCenter.z);
        double curEyeDist = player.getEyePosition().distanceTo(targetCenter);
        double maxAxisDist = Math.max(Math.abs(pos.x - targetCenter.x), Math.abs(pos.z - targetCenter.z));

        double desiredHoriz = isSpawner ? 1.98 : 3.65;
        double desiredEye = isSpawner ? 4.25 : 3.85;
        BlockPos stand = SpawnerRoomScanner.getSpawnerBotStandPos(level, player, enclosure);

        return (onTop || (maxAxisDist >= 0.80 && curHorizDist >= 0.85))
                && curHorizDist <= desiredHoriz
                && curEyeDist <= desiredEye
                && SpawnerPathfinder.hasClearLineOfSightToSpawnerOrChest(level, stand, targetPos);
    }

    static boolean canFallbackInteractDirectly(LocalPlayer player, BlockPos targetSpawner) {
        return player.getEyePosition().distanceTo(Vec3.atCenterOf(targetSpawner)) <= 4.45
                && (!isSameColumn(player.position(), targetSpawner)
                || isStandingOnTopOfTarget(player.position(), targetSpawner));
    }

    static boolean isSameColumn(Vec3 pos, BlockPos blockPos) {
        return Mth.floor(pos.x) == blockPos.getX() && Mth.floor(pos.z) == blockPos.getZ();
    }

    static boolean isStandingOnTopOfTarget(Vec3 pos, BlockPos targetPos) {
        return isSameColumn(pos, targetPos)
                && pos.y >= targetPos.getY() + 0.85
                && pos.y <= targetPos.getY() + 1.5;
    }

    static String formatElapsedTime(long enabledStartMs) {
        if (enabledStartMs <= 0L) return "00:00";
        long totalSec = Math.max(0L, (System.currentTimeMillis() - enabledStartMs) / 1000L);
        long hrs = totalSec / 3600L;
        long mins = (totalSec % 3600L) / 60L;
        long secs = totalSec % 60L;
        return hrs > 0
                ? String.format(Locale.ROOT, "%02d:%02d:%02d", hrs, mins, secs)
                : String.format(Locale.ROOT, "%02d:%02d", mins, secs);
    }
}
