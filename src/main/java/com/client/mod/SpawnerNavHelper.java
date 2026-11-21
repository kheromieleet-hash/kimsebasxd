package com.client.mod;

import com.client.mod.SpawnerPathfinder.SpotCandidate;
import com.client.mod.SpawnerRoomScanner.SpawnerEnclosure;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Shared path-following, unstuck recovery, reach checks, and elapsed-time formatting
 * for {@link AutoSpawnerModule} and {@link SpawnerAutoSellModule}.
 */
public final class SpawnerNavHelper {

    public enum WalkResult {
        CONTINUE,
        REACHED,
        UNSTUCK_RESTART,
        ABORTED
    }

    private static final double DEFAULT_PENALTY_DECAY = 0.5;
    private static final double MIN_ACTIVE_PENALTY = 0.25;
    private static final double MAX_TILE_PENALTY = 120.0;

    // Local movement combinations: {forward(1/-1/0), left(1/-1/0)}
    private static final int[][] SIDESTEP_LOCAL_DIRS = {
            {0, 1},   // pure left
            {0, -1},  // pure right
            {-1, 1},  // back-left
            {-1, -1}, // back-right
            {-1, 0},  // pure back
            {1, 1},   // forward-left
            {1, -1}   // forward-right
    };

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

    private final Map<BlockPos, Double> tilePenalties = new ConcurrentHashMap<>();
    private final BlockPos.MutableBlockPos scratchPos = new BlockPos.MutableBlockPos();

    private BlockPos trackedTarget = null;
    private int stuckTier = 0;
    private boolean altSpotRequested = false;
    private boolean quarantineRequested = false;
    private boolean replanRequested = false;

    private boolean unstuckForward = false;
    private boolean unstuckBackward = false;
    private boolean unstuckLeft = false;
    private boolean unstuckRight = false;
    private boolean unstuckAllowJump = false;

    private int sprintSuppressTicks = 0;
    private boolean legitMode = false;
    private boolean sprintBurstActive = true;
    private int sprintPhaseTicksRemaining = 0;
    private double tpsDelayMultiplier = 1.0;

    public void clearMovementFlags() {
        this.moveForward = false;
        this.moveBackward = false;
        this.moveLeft = false;
        this.moveRight = false;
        this.wantJump = false;
        this.wantSprint = false;
    }

    private void clearUnstuckDirections() {
        this.unstuckForward = false;
        this.unstuckBackward = false;
        this.unstuckLeft = false;
        this.unstuckRight = false;
        this.unstuckAllowJump = false;
    }

    public void resetAll(double defaultEyeReach) {
        clearMovementFlags();
        clearUnstuckDirections();
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
        this.tilePenalties.clear();
        this.trackedTarget = null;
        this.stuckTier = 0;
        this.altSpotRequested = false;
        this.quarantineRequested = false;
        this.replanRequested = false;
        this.sprintSuppressTicks = 0;
        this.sprintBurstActive = false;
        this.sprintPhaseTicksRemaining = 0;
    }

    public void recordStuckTile(BlockPos pos, double penalty) {
        if (pos == null || !Double.isFinite(penalty) || penalty <= 0.0) {
            return;
        }
        BlockPos key = pos.immutable();
        this.tilePenalties.merge(key, penalty, (oldVal, addVal) -> Math.min(MAX_TILE_PENALTY, oldVal + addVal));
    }

    public Map<BlockPos, Double> getActiveTilePenalties() {
        if (this.tilePenalties.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new HashMap<>(this.tilePenalties));
    }

    public void decayTilePenalties() {
        if (this.tilePenalties.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, Double>> it = this.tilePenalties.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Double> entry = it.next();
            double decayed = entry.getValue() * DEFAULT_PENALTY_DECAY;
            if (!Double.isFinite(decayed) || decayed < MIN_ACTIVE_PENALTY) {
                it.remove();
            } else {
                entry.setValue(decayed);
            }
        }
    }

    public boolean consumeAltSpotRequested() {
        boolean requested = this.altSpotRequested;
        this.altSpotRequested = false;
        return requested;
    }

    public boolean consumeQuarantineRequested() {
        boolean requested = this.quarantineRequested;
        this.quarantineRequested = false;
        return requested;
    }

    public int getStuckTier() {
        return this.stuckTier;
    }

    public boolean isSprintSuppressed() {
        return this.sprintSuppressTicks > 0;
    }

    public void onServerSetback(Vec3 before, Vec3 after) {
        BlockPos beforeTile = null;
        if (before != null) {
            beforeTile = BlockPos.containing(before.x, before.y + 0.42, before.z);
            recordStuckTile(beforeTile, 8.0);
        }
        if (after != null) {
            BlockPos afterTile = BlockPos.containing(after.x, after.y + 0.42, after.z);
            if (!afterTile.equals(beforeTile)) {
                recordStuckTile(afterTile, 5.0);
            }
        }
        List<BlockPos> path = this.activePath;
        int idx = this.pathIdx;
        if (path != null && idx >= 0 && idx < path.size()) {
            BlockPos nextNode = path.get(idx);
            if (nextNode != null && !nextNode.equals(beforeTile)) {
                recordStuckTile(nextNode, 8.0);
            }
        }
        this.sprintSuppressTicks = Math.max(this.sprintSuppressTicks, 35);
        this.activePath = null;
        this.pathIdx = 0;
        this.nodeStallTicks = 0;
        this.noProgressTicks = 0;
        this.unstuckTicks = 0;
        this.lastProgressPos = after;
        this.replanRequested = true;
        clearMovementFlags();
    }

    public Vec3 getLookAheadTarget(Vec3 playerPos, Vec3 fallbackTarget) {
        List<BlockPos> path = this.activePath;
        int idx = this.pathIdx;
        if (path == null || path.isEmpty() || idx < 0 || idx >= path.size()) {
            return fallbackTarget;
        }

        int maxIdx = Math.min(path.size() - 1, idx + 3);
        int chosenIdx = idx;

        if (playerPos == null) {
            chosenIdx = Math.min(path.size() - 1, idx + 2);
        } else {
            double prevX = playerPos.x;
            double prevY = playerPos.y;
            double prevZ = playerPos.z;
            double cumDist = 0.0;
            for (int i = idx; i <= maxIdx; i++) {
                BlockPos node = path.get(i);
                if (node == null) break;
                double nx = node.getX() + 0.5;
                double ny = node.getY() + 0.5;
                double nz = node.getZ() + 0.5;
                double seg = Math.sqrt(
                        (nx - prevX) * (nx - prevX)
                                + (ny - prevY) * (ny - prevY)
                                + (nz - prevZ) * (nz - prevZ)
                );
                cumDist += seg;
                if (i == idx || cumDist <= 5.0 || (i <= idx + 2 && cumDist <= 6.5)) {
                    chosenIdx = i;
                } else {
                    break;
                }
                prevX = nx;
                prevY = ny;
                prevZ = nz;
            }
            if (chosenIdx == path.size() - 1 && fallbackTarget != null && cumDist <= 2.4) {
                return fallbackTarget;
            }
        }

        BlockPos chosen = path.get(chosenIdx);
        if (chosen == null) {
            return fallbackTarget;
        }
        return new Vec3(chosen.getX() + 0.5, chosen.getY() + 0.5, chosen.getZ() + 0.5);
    }

    public void setLegitMode(boolean legitMode) {
        if (this.legitMode != legitMode) {
            this.legitMode = legitMode;
            this.sprintBurstActive = false;
            this.sprintPhaseTicksRemaining = 0;
        }
    }

    public boolean isLegitMode() {
        return this.legitMode;
    }

    public void setTpsDelayMultiplier(double mult) {
        if (!Double.isFinite(mult)) {
            this.tpsDelayMultiplier = 1.0;
            return;
        }
        this.tpsDelayMultiplier = Math.max(1.0, Math.min(3.0, mult));
    }

    public double getTpsDelayMultiplier() {
        return this.tpsDelayMultiplier;
    }

    int scaleTpsTicks(int baseTicks) {
        if (baseTicks <= 0) return baseTicks;
        return (int) Math.ceil(baseTicks * this.tpsDelayMultiplier);
    }

    private void syncTargetTracker(BlockPos target) {
        if (target == null) {
            this.trackedTarget = null;
            this.stuckTier = 0;
            this.altSpotRequested = false;
            this.quarantineRequested = false;
            this.replanRequested = false;
            return;
        }
        if (!target.equals(this.trackedTarget)) {
            this.trackedTarget = target.immutable();
            this.stuckTier = 0;
            this.altSpotRequested = false;
            this.quarantineRequested = false;
            this.replanRequested = false;
        }
    }

    private void resetTargetStuckState() {
        this.stuckTier = 0;
        this.altSpotRequested = false;
        this.quarantineRequested = false;
        this.replanRequested = false;
    }

    void startPathWalk(LocalPlayer player, List<BlockPos> path, int maxTicksBudget) {
        syncTargetTracker(this.currentNavTarget);
        this.activePath = path;
        this.pathIdx = 0;
        this.nodeStallTicks = 0;
        this.lastProgressPos = player != null ? player.position() : null;
        this.noProgressTicks = 0;
        this.unstuckTicks = 0;
        this.totalWalkTicks = 0;
        this.maxWalkTicks = maxTicksBudget;
        this.replanRequested = false;
        this.sprintBurstActive = true;
        this.sprintPhaseTicksRemaining = 18 + ThreadLocalRandom.current().nextInt(25);
    }

    WalkResult tickWalk(
            ClientLevel level,
            LocalPlayer player,
            SpawnerEnclosure enclosure,
            boolean isSpawner
    ) {
        if (level == null || player == null || this.currentNavTarget == null) {
            clearMovementFlags();
            return WalkResult.ABORTED;
        }
        syncTargetTracker(this.currentNavTarget);

        if (this.sprintSuppressTicks > 0) {
            this.sprintSuppressTicks--;
        }
        if (this.replanRequested) {
            this.replanRequested = false;
            this.noProgressTicks = 0;
            this.nodeStallTicks = 0;
            clearMovementFlags();
            return WalkResult.UNSTUCK_RESTART;
        }

        if (this.unstuckTicks > 0) {
            this.unstuckTicks--;
            this.moveForward = this.unstuckForward;
            this.moveBackward = this.unstuckBackward;
            this.moveLeft = this.unstuckLeft;
            this.moveRight = this.unstuckRight;
            this.wantJump = this.unstuckAllowJump && (this.unstuckTicks % 4 == 0);
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
        int effectiveMaxWalkTicks = scaleTpsTicks(this.maxWalkTicks);
        if (this.activePath == null || this.pathIdx >= this.activePath.size() || this.totalWalkTicks >= effectiveMaxWalkTicks) {
            clearMovementFlags();
            Vec3 endPos = player.position();
            Vec3 targetCenter = Vec3.atCenterOf(this.currentNavTarget);
            double endHoriz = Math.hypot(endPos.x - targetCenter.x, endPos.z - targetCenter.z);
            double endEye = player.getEyePosition().distanceTo(targetCenter);
            boolean onTop = isSpawner && isStandingOnTopOfTarget(endPos, this.currentNavTarget);
            boolean reached = (onTop || !isSameColumn(endPos, this.currentNavTarget))
                    && endHoriz <= Math.max(this.stopHorizReach + 0.55, 2.45)
                    && endEye <= Math.max(this.stopEyeReach + 0.45, 4.45);
            if (reached) {
                resetTargetStuckState();
                return WalkResult.REACHED;
            }
            if (this.stuckTier >= 2) {
                recordStuckTile(BlockPos.containing(endPos.x, endPos.y + 0.42, endPos.z), 10.0);
                this.quarantineRequested = true;
            }
            return WalkResult.ABORTED;
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
            resetTargetStuckState();
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
        if (this.nodeStallTicks > scaleTpsTicks(52)) { // ~2600ms node timeout (TPS-scaled)
            if (!isLastNode) {
                this.pathIdx++;
                this.nodeStallTicks = 0;
                return WalkResult.CONTINUE;
            }
            if (triggerStuckEscalation(level, player, curPos, targetNode, enclosure)) {
                return WalkResult.ABORTED;
            }
            this.nodeStallTicks = 0;
            return WalkResult.CONTINUE;
        }

        Vec3 lookTarget = getLookAheadTarget(curPos, targetCenter);
        double lookDx = lookTarget.x - curPos.x;
        double lookDz = lookTarget.z - curPos.z;
        double lookHoriz = Math.hypot(lookDx, lookDz);
        double destHoriz = Math.hypot(targetCenter.x - curPos.x, targetCenter.z - curPos.z);
        if (lookHoriz <= 0.25) {
            lookDx = targetCenter.x - curPos.x;
            lookDz = targetCenter.z - curPos.z;
            lookHoriz = destHoriz;
        }
        float sway = (destHoriz > 4.0) ? (float) (Math.sin(this.totalWalkTicks * 0.14) * 0.45) : 0.0f;
        if (!isSpawner) {
            double lookDy = lookTarget.y - curEye.y;
            float destYaw = (float) (Math.toDegrees(Math.atan2(lookDz, lookDx)) - 90.0);
            float destPitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(lookDy, Math.max(0.25, lookHoriz))) + sway, -35.0f, 35.0f);
            AutoSpawnerModule.applyHumanRotation(player, destYaw, destPitch, true);
        } else if (destHoriz > 3.5) {
            float destYaw = (float) (Math.toDegrees(Math.atan2(lookDz, lookDx)) - 90.0);
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
            if (this.noProgressTicks >= scaleTpsTicks(26)) {
                if (triggerStuckEscalation(level, player, curPos, targetNode, enclosure)) {
                    return WalkResult.ABORTED;
                }
                return WalkResult.CONTINUE;
            }
        }
        boolean isStuckBriefly = this.noProgressTicks > scaleTpsTicks(6);

        BlockState targetFloorState = level.getBlockState(targetNode.below());
        boolean isSafeInteriorStepUp = (this.activePathAllowsSpawnerFloor || !SpawnerRoomScanner.isSpawnerBlock(targetFloorState))
                && !SpawnerRoomScanner.isFarmContainerBlock(targetFloorState)
                && (enclosure == null || (targetNode.getX() >= enclosure.minX && targetNode.getX() <= enclosure.maxX
                && targetNode.getZ() >= enclosure.minZ && targetNode.getZ() <= enclosure.maxZ));

        this.wantJump = isSafeInteriorStepUp && ((dy >= 0.15 && dy <= 1.65) || (isStuckBriefly && dy >= -0.20));
        float headingYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float headingErr = Math.abs(Mth.wrapDegrees(headingYaw - player.getYRot()));
        this.wantSprint = computeWantSprint(curPos.x, curPos.z, destHoriz, headingErr, dy, isStuckBriefly);
        return WalkResult.CONTINUE;
    }

    /**
     * Advances the 3-tier anti-stuck state machine for the active target.
     *
     * @return {@code true} if Tier 2 quarantine abort triggered immediately;
     *         {@code false} if a Tier 0 or Tier 1 sidestep recovery started.
     */
    boolean triggerStuckEscalation(
            ClientLevel level,
            LocalPlayer player,
            Vec3 curPos,
            BlockPos blockedNode,
            SpawnerEnclosure enclosure
    ) {
        syncTargetTracker(this.currentNavTarget);
        float yaw = player != null ? player.getYRot() : 0.0f;
        if (this.stuckTier == 0) {
            // Tier 0: directional wall-away sidestep without penalizing yet
            this.stuckTier = 1;
            chooseWallAwaySidestep(level, curPos, yaw, blockedNode, enclosure);
            this.unstuckTicks = 10;
            this.noProgressTicks = 0;
            return false;
        }
        if (this.stuckTier == 1) {
            // Tier 1: record tile penalty + request alternate stand spot + wall-away sidestep
            this.stuckTier = 2;
            recordStuckTilesForEvent(curPos, blockedNode, 8.0);
            this.altSpotRequested = true;
            chooseWallAwaySidestep(level, curPos, yaw, blockedNode, enclosure);
            this.unstuckTicks = 10;
            this.noProgressTicks = 0;
            return false;
        }
        // Tier 2: repeated stall after Tier 1 -> escalate penalty + request quarantine
        this.stuckTier = 3;
        recordStuckTilesForEvent(curPos, blockedNode, 12.0);
        this.quarantineRequested = true;
        this.unstuckTicks = 0;
        this.noProgressTicks = 0;
        this.nodeStallTicks = 0;
        clearMovementFlags();
        return true;
    }

    private void recordStuckTilesForEvent(Vec3 curPos, BlockPos blockedNode, double penalty) {
        if (blockedNode != null) {
            recordStuckTile(blockedNode, penalty);
        }
        if (curPos != null) {
            BlockPos curTile = BlockPos.containing(curPos.x, curPos.y + 0.42, curPos.z);
            if (!curTile.equals(blockedNode)) {
                recordStuckTile(curTile, penalty * 0.5);
            }
        }
    }

    private void chooseWallAwaySidestep(
            ClientLevel level,
            Vec3 curPos,
            float playerYaw,
            BlockPos blockedNode,
            SpawnerEnclosure enclosure
    ) {
        double yawRad = Math.toRadians(playerYaw);
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double lx = Math.cos(yawRad);
        double lz = Math.sin(yawRad);

        int bestIdx = 2; // default back-left
        double bestScore = Double.NEGATIVE_INFINITY;
        int baseY = curPos != null ? Mth.floor(curPos.y + 0.42) : 0;

        for (int i = 0; i < SIDESTEP_LOCAL_DIRS.length; i++) {
            int fwd = SIDESTEP_LOCAL_DIRS[i][0];
            int left = SIDESTEP_LOCAL_DIRS[i][1];
            double wx = fwd * fx + left * lx;
            double wz = fwd * fz + left * lz;
            double wLen = Math.hypot(wx, wz);
            if (wLen < 1e-4) continue;
            wx /= wLen;
            wz /= wLen;

            double score = (fwd <= 0 ? 0.5 : -1.5) + ThreadLocalRandom.current().nextDouble(0.35);

            if (curPos != null) {
                if (level != null) {
                    this.scratchPos.set(Mth.floor(curPos.x + wx * 0.75), baseY, Mth.floor(curPos.z + wz * 0.75));
                    if (SpawnerRoomScanner.isSpawnerCorridorWalkable(level, this.scratchPos, enclosure, this.activePathAllowsSpawnerFloor)) {
                        score += 12.0;
                    } else if (SpawnerRoomScanner.isBlockPassable(level, this.scratchPos)
                            && SpawnerRoomScanner.isBlockPassable(level, this.scratchPos.above())) {
                        score += 4.0;
                    } else {
                        score -= 14.0;
                    }
                    Double pen = this.tilePenalties.get(this.scratchPos);
                    if (pen != null && pen > 0.0) {
                        score -= Math.min(10.0, pen);
                    }

                    this.scratchPos.set(Mth.floor(curPos.x + wx * 1.25), baseY, Mth.floor(curPos.z + wz * 1.25));
                    if (SpawnerRoomScanner.isSpawnerCorridorWalkable(level, this.scratchPos, enclosure, this.activePathAllowsSpawnerFloor)) {
                        score += 4.0;
                    } else if (!SpawnerRoomScanner.isBlockPassable(level, this.scratchPos)) {
                        score -= 4.0;
                    }

                    // Reward moving directly away from a wall blocking the opposite side
                    this.scratchPos.set(Mth.floor(curPos.x - wx * 0.65), baseY, Mth.floor(curPos.z - wz * 0.65));
                    if (!SpawnerRoomScanner.isBlockPassable(level, this.scratchPos)) {
                        score += 5.0;
                    }
                }

                if (blockedNode != null) {
                    double bx = (blockedNode.getX() + 0.5) - curPos.x;
                    double bz = (blockedNode.getZ() + 0.5) - curPos.z;
                    double bLen = Math.hypot(bx, bz);
                    if (bLen > 1e-4) {
                        double towardDot = (wx * bx + wz * bz) / bLen;
                        score -= towardDot * 3.5;
                    }
                }
            }

            if (score > bestScore) {
                bestScore = score;
                bestIdx = i;
            }
        }

        this.unstuckMode = bestIdx;
        int chosenFwd = SIDESTEP_LOCAL_DIRS[bestIdx][0];
        int chosenLeft = SIDESTEP_LOCAL_DIRS[bestIdx][1];
        this.unstuckForward = chosenFwd > 0;
        this.unstuckBackward = chosenFwd < 0;
        this.unstuckLeft = chosenLeft > 0;
        this.unstuckRight = chosenLeft < 0;
        if (level != null && curPos != null) {
            this.scratchPos.set(Mth.floor(curPos.x), baseY + 2, Mth.floor(curPos.z));
            this.unstuckAllowJump = SpawnerRoomScanner.isBlockPassable(level, this.scratchPos);
        } else {
            this.unstuckAllowJump = true;
        }
    }

    boolean isApproachingSharpCorner(double curX, double curZ) {
        List<BlockPos> path = this.activePath;
        int idx = this.pathIdx;
        if (path == null || idx < 0 || idx + 1 >= path.size()) {
            return false;
        }
        BlockPos curNode = path.get(idx);
        BlockPos nextNode = path.get(idx + 1);
        if (curNode == null || nextNode == null) {
            return false;
        }
        double cx = curNode.getX() + 0.5;
        double cz = curNode.getZ() + 0.5;
        double dx1 = cx - curX;
        double dz1 = cz - curZ;
        double distToCorner = Math.hypot(dx1, dz1);
        if (distToCorner > 2.25) {
            return false;
        }
        if (nextNode.getY() != curNode.getY()) {
            return true;
        }
        double dx2 = (nextNode.getX() + 0.5) - cx;
        double dz2 = (nextNode.getZ() + 0.5) - cz;
        double len2 = Math.hypot(dx2, dz2);
        if (distToCorner < 1e-4 || len2 < 1e-4) {
            return false;
        }
        double cosAngle = (dx1 * dx2 + dz1 * dz2) / (distToCorner * len2);
        return cosAngle < 0.70;
    }

    boolean computeWantSprint(
            double curX,
            double curZ,
            double destHoriz,
            float headingErr,
            double dy,
            boolean isStuckBriefly
    ) {
        if (this.sprintSuppressTicks > 0
                || !this.moveForward
                || this.wantJump
                || isStuckBriefly
                || destHoriz <= 4.2
                || headingErr > 25.0f
                || Math.abs(dy) >= 0.15) {
            return false;
        }
        if (!this.legitMode) {
            return (this.totalWalkTicks % 35) < 28;
        }
        if (headingErr > 18.0f || isApproachingSharpCorner(curX, curZ)) {
            return false;
        }
        if (this.sprintPhaseTicksRemaining <= 0) {
            this.sprintBurstActive = !this.sprintBurstActive;
            this.sprintPhaseTicksRemaining = this.sprintBurstActive
                    ? 18 + ThreadLocalRandom.current().nextInt(25)
                    : 5 + ThreadLocalRandom.current().nextInt(10);
        }
        this.sprintPhaseTicksRemaining--;
        return this.sprintBurstActive;
    }

    List<BlockPos> selectSpawnerPath(
            ClientLevel level,
            LocalPlayer player,
            BlockPos targetSpawner,
            SpawnerEnclosure enclosure,
            boolean preferAlternativeSpot
    ) {
        if (level == null || player == null || targetSpawner == null) {
            return null;
        }
        syncTargetTracker(targetSpawner);
        boolean useAlt = preferAlternativeSpot || this.altSpotRequested;
        BlockPos startStand = SpawnerRoomScanner.getSpawnerBotStandPos(level, player, enclosure);
        List<SpotCandidate> spots = SpawnerPathfinder.findBestStandSpots(
                level, player, targetSpawner, enclosure, true, useAlt, startStand, true
        );
        Map<BlockPos, Double> penalties = getActiveTilePenalties();
        List<BlockPos> selectedPath = null;
        for (int i = 0; i < Math.min(spots.size(), 16); i++) {
            SpotCandidate cand = spots.get(i);
            List<BlockPos> p = SpawnerPathfinder.findSpawnerRoomPath(
                    level, startStand, cand.spot(), enclosure, 3000, true, penalties
            );
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
