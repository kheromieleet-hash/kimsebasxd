package com.client.mod;

import com.client.mod.SpawnerRoomScanner.SpawnerEnclosure;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class SpawnerPathfinder {

    private SpawnerPathfinder() {}

    public record SpotCandidate(
            BlockPos spot,
            int isDirectlyAdjacent,
            int isOrthogonalAdjacent,
            double horizDist,
            double distToPlayer,
            int yDiff
    ) {}

    public static boolean hasClearLineOfSightToSpawnerOrChest(
            ClientLevel level,
            BlockPos standSpot,
            BlockPos targetBlockPos
    ) {
        if (level == null || standSpot == null || targetBlockPos == null) return false;
        if (standSpot.getX() == targetBlockPos.getX()
                && standSpot.getZ() == targetBlockPos.getZ()
                && standSpot.getY() >= targetBlockPos.getY() + 1) {
            return true;
        }
        double horizDist = Math.hypot(
                (standSpot.getX() + 0.5) - (targetBlockPos.getX() + 0.5),
                (standSpot.getZ() + 0.5) - (targetBlockPos.getZ() + 0.5)
        );
        if (horizDist <= 1.55) return true;

        Vec3 eyePos = new Vec3(standSpot.getX() + 0.5, standSpot.getY() + 1.62, standSpot.getZ() + 0.5);
        Vec3 targetCenter = Vec3.atCenterOf(targetBlockPos);
        if (hasClearRayToPoint(level, standSpot, targetBlockPos, eyePos, targetCenter)) {
            return true;
        }

        // For inner/middle spawners surrounded by other spawners, the top face is clickable from above
        if (SpawnerRoomScanner.isBlockPassable(level, targetBlockPos.above())
                && eyePos.y >= targetBlockPos.getY() + 0.98) {
            Vec3 topFace = new Vec3(targetBlockPos.getX() + 0.5, targetBlockPos.getY() + 0.99, targetBlockPos.getZ() + 0.5);
            if (hasClearRayToPoint(level, standSpot, targetBlockPos, eyePos, topFace)) {
                return true;
            }
        }

        for (Direction dir : Direction.Plane.HORIZONTAL) {
            if (!SpawnerRoomScanner.isBlockPassable(level, targetBlockPos.relative(dir))) continue;
            Vec3 sideFace = targetCenter.add(dir.getStepX() * 0.49, 0.0, dir.getStepZ() * 0.49);
            if (hasClearRayToPoint(level, standSpot, targetBlockPos, eyePos, sideFace)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasClearRayToPoint(
            ClientLevel level,
            BlockPos standSpot,
            BlockPos targetBlockPos,
            Vec3 eyePos,
            Vec3 aimPoint
    ) {
        int steps = 14;
        for (int s = 1; s < steps; s++) {
            double t = (double) s / steps;
            int rx = (int) Math.floor(eyePos.x + (aimPoint.x - eyePos.x) * t);
            int ry = (int) Math.floor(eyePos.y + (aimPoint.y - eyePos.y) * t);
            int rz = (int) Math.floor(eyePos.z + (aimPoint.z - eyePos.z) * t);

            // Do not count stacked spawners in the same (x, z) column as obstructions
            if (rx == targetBlockPos.getX() && rz == targetBlockPos.getZ()) continue;
            if (rx == standSpot.getX() && rz == standSpot.getZ()
                    && (ry == standSpot.getY() || ry == standSpot.getY() + 1)) {
                continue;
            }
            BlockState midState = level.getBlockState(new BlockPos(rx, ry, rz));
            if (SpawnerRoomScanner.isSpawnerBlock(midState) || SpawnerRoomScanner.isFarmContainerBlock(midState)) {
                return false;
            }
        }
        return true;
    }

    public static Direction getExposedSpawnerClickFace(
            ClientLevel level,
            LocalPlayer player,
            BlockPos spawnerPos
    ) {
        if (level == null || player == null || spawnerPos == null) return Direction.UP;

        if ((int) Math.floor(player.getX()) == spawnerPos.getX()
                && (int) Math.floor(player.getZ()) == spawnerPos.getZ()
                && player.getY() >= spawnerPos.getY() + 0.85) {
            return Direction.UP;
        }

        Vec3 eyePos = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(spawnerPos);
        double dx = eyePos.x - center.x;
        double dz = eyePos.z - center.z;

        record FaceWeight(Direction dir, double weight) {}
        List<FaceWeight> horizFaces = new ArrayList<>(4);
        horizFaces.add(new FaceWeight(Direction.EAST, dx));
        horizFaces.add(new FaceWeight(Direction.WEST, -dx));
        horizFaces.add(new FaceWeight(Direction.SOUTH, dz));
        horizFaces.add(new FaceWeight(Direction.NORTH, -dz));
        horizFaces.sort((a, b) -> Double.compare(b.weight, a.weight));

        // 1) Prefer side face pointing toward the player that has passable air/non-solid block and clear clip raycast
        for (FaceWeight f : horizFaces) {
            if (f.weight <= -0.15) continue;
            BlockPos adj = spawnerPos.relative(f.dir);
            if (SpawnerRoomScanner.isBlockPassable(level, adj)
                    && hasClearClipRaycast(level, eyePos, faceCenter(spawnerPos, f.dir), spawnerPos)) {
                return f.dir;
            }
        }

        // 2) Any exposed side face with clear clip raycast
        for (FaceWeight f : horizFaces) {
            BlockPos adj = spawnerPos.relative(f.dir);
            if (SpawnerRoomScanner.isBlockPassable(level, adj)
                    && hasClearClipRaycast(level, eyePos, faceCenter(spawnerPos, f.dir), spawnerPos)) {
                return f.dir;
            }
        }

        // 3) Top face if player eyes are above spawner top and top is open with clear clip raycast
        if (SpawnerRoomScanner.isBlockPassable(level, spawnerPos.above())
                && eyePos.y >= spawnerPos.getY() + 0.95
                && hasClearClipRaycast(level, eyePos, faceCenter(spawnerPos, Direction.UP), spawnerPos)) {
            return Direction.UP;
        }

        // 4) Fallback to passable-only check if OUTLINE clip is obstructed by non-solid decor
        for (FaceWeight f : horizFaces) {
            if (f.weight <= -0.15) continue;
            BlockPos adj = spawnerPos.relative(f.dir);
            if (SpawnerRoomScanner.isBlockPassable(level, adj)) {
                return f.dir;
            }
        }
        for (FaceWeight f : horizFaces) {
            BlockPos adj = spawnerPos.relative(f.dir);
            if (SpawnerRoomScanner.isBlockPassable(level, adj)) {
                return f.dir;
            }
        }
        if (SpawnerRoomScanner.isBlockPassable(level, spawnerPos.above()) && eyePos.y >= spawnerPos.getY() + 0.95) {
            return Direction.UP;
        }

        return horizFaces.get(0).dir;
    }

    private static Vec3 faceCenter(BlockPos blockPos, Direction face) {
        return new Vec3(
                blockPos.getX() + 0.5 + face.getStepX() * 0.5,
                blockPos.getY() + 0.5 + face.getStepY() * 0.5,
                blockPos.getZ() + 0.5 + face.getStepZ() * 0.5
        );
    }

    /**
     * Distance-weighted chest priority score (lower is preferred).
     */
    public static double scoreChestCandidate(int depositCount, double horizDist) {
        return (depositCount * 6.0) + horizDist;
    }

    /**
     * Collects up to {@code maxBatch} pending spawners reachable from {@code standPos}'s eye position,
     * scanning forward from {@code startIdx} along {@code orderedSpawners}.
     */
    public static List<BlockPos> collectReachableBatch(
            BlockPos standPos,
            List<BlockPos> orderedSpawners,
            int startIdx,
            Set<BlockPos> opened,
            double maxReach,
            int maxBatch
    ) {
        if (standPos == null
                || orderedSpawners == null
                || orderedSpawners.isEmpty()
                || startIdx < 0
                || startIdx >= orderedSpawners.size()
                || maxBatch <= 0
                || !Double.isFinite(maxReach)
                || maxReach <= 0.0) {
            return List.of();
        }
        double eyeX = standPos.getX() + 0.5;
        double eyeY = standPos.getY() + 1.62;
        double eyeZ = standPos.getZ() + 0.5;
        int cap = Math.min(maxBatch, orderedSpawners.size() - startIdx);
        List<BlockPos> batch = new ArrayList<>(Math.min(cap, 8));
        for (int i = startIdx; i < orderedSpawners.size() && batch.size() < maxBatch; i++) {
            BlockPos sp = orderedSpawners.get(i);
            if (sp == null) continue;
            if (opened != null && opened.contains(sp)) continue;
            double dx = (sp.getX() + 0.5) - eyeX;
            double dy = (sp.getY() + 0.5) - eyeY;
            double dz = (sp.getZ() + 0.5) - eyeZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist <= maxReach) {
                batch.add(sp);
            }
        }
        return batch;
    }

    /**
     * Picks a Gaussian-sampled hit point on {@code face} of {@code blockPos},
     * with both tangent coordinates clamped to the inner 60% {@code [0.2, 0.8]}.
     */
    public static Vec3 pickGaussianSubFacePoint(BlockPos blockPos, Direction face, Random rng) {
        if (blockPos == null) return Vec3.ZERO;
        Direction dir = face != null ? face : Direction.UP;
        Random rand = rng != null ? rng : ThreadLocalRandom.current();
        double u = Mth.clamp(0.5 + rand.nextGaussian() * 0.12, 0.2, 0.8);
        double v = Mth.clamp(0.5 + rand.nextGaussian() * 0.12, 0.2, 0.8);
        double bx = blockPos.getX();
        double by = blockPos.getY();
        double bz = blockPos.getZ();
        return switch (dir.getAxis()) {
            case X -> new Vec3(bx + (dir == Direction.EAST ? 1.0 : 0.0), by + u, bz + v);
            case Y -> new Vec3(bx + u, by + (dir == Direction.UP ? 1.0 : 0.0), bz + v);
            case Z -> new Vec3(bx + u, by + v, bz + (dir == Direction.SOUTH ? 1.0 : 0.0));
        };
    }

    /**
     * Performs a real MC 26.3 {@code level.clip} raycast against block outlines to confirm
     * {@code targetPoint} is either unobstructed or hits {@code targetBlock} itself.
     */
    public static boolean hasClearClipRaycast(
            Level level,
            Vec3 eyePos,
            Vec3 targetPoint,
            BlockPos targetBlock
    ) {
        if (level == null || eyePos == null || targetPoint == null || targetBlock == null) {
            return false;
        }
        BlockHitResult hit = level.clip(new ClipContext(
                eyePos,
                targetPoint,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                CollisionContext.empty()
        ));
        if (hit == null) return false;
        HitResult.Type type = hit.getType();
        return type == HitResult.Type.MISS
                || (type == HitResult.Type.BLOCK && targetBlock.equals(hit.getBlockPos()));
    }

    public static List<SpotCandidate> findBestStandSpots(
            ClientLevel level,
            LocalPlayer player,
            BlockPos targetBlockPos,
            SpawnerEnclosure enc,
            boolean isTargetSpawner,
            boolean preferAlternativeSpot,
            BlockPos startStand
    ) {
        return findBestStandSpots(
                level, player, targetBlockPos, enc, isTargetSpawner, preferAlternativeSpot, startStand, isTargetSpawner
        );
    }

    public static List<SpotCandidate> findBestStandSpots(
            ClientLevel level,
            LocalPlayer player,
            BlockPos targetBlockPos,
            SpawnerEnclosure enc,
            boolean isTargetSpawner,
            boolean preferAlternativeSpot,
            BlockPos startStand,
            boolean allowSpawnerFloor
    ) {
        if (level == null || player == null || targetBlockPos == null || startStand == null) {
            return List.of();
        }
        int minY = enc != null ? enc.minY : targetBlockPos.getY() - 3;
        int maxY = (enc != null ? enc.maxY : targetBlockPos.getY() + 1) + (allowSpawnerFloor ? 1 : 0);
        int searchRadius = isTargetSpawner ? 4 : 3;

        Vec3 targetCenter = Vec3.atCenterOf(targetBlockPos);
        Vec3 botPos = player.position();
        List<SpotCandidate> candidates = new ArrayList<>();

        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                boolean isSameCol = (dx == 0 && dz == 0);
                if (isSameCol && (!isTargetSpawner || !allowSpawnerFloor)) continue;
                int isDirectlyAdjacent = (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) ? 0 : 1;
                int isOrthogonalAdjacent = (isSameCol || (Math.abs(dx) == 1 && dz == 0) || (dx == 0 && Math.abs(dz) == 1)) ? 0 : 1;

                for (int candY = minY; candY <= maxY; candY++) {
                    if (isSameCol && candY != targetBlockPos.getY() + 1) continue;
                    BlockPos spot = new BlockPos(targetBlockPos.getX() + dx, candY, targetBlockPos.getZ() + dz);
                    if (preferAlternativeSpot && spot.getX() == startStand.getX() && spot.getZ() == startStand.getZ()) {
                        continue;
                    }
                    Vec3 eyeFromSpot = new Vec3(spot.getX() + 0.5, spot.getY() + 1.62, spot.getZ() + 0.5);
                    double reachDist = eyeFromSpot.distanceTo(targetCenter);
                    double horizDist = Math.hypot((spot.getX() + 0.5) - targetCenter.x, (spot.getZ() + 0.5) - targetCenter.z);

                    if (reachDist < 0.80 || reachDist > (isTargetSpawner ? 4.35 : 3.85)) continue;
                    if (!SpawnerRoomScanner.isSpawnerCorridorWalkable(level, spot, enc, allowSpawnerFloor)) continue;
                    if (!hasClearLineOfSightToSpawnerOrChest(level, spot, targetBlockPos)) continue;

                    candidates.add(new SpotCandidate(
                            spot,
                            isDirectlyAdjacent,
                            isOrthogonalAdjacent,
                            horizDist,
                            botPos.distanceTo(new Vec3(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5)),
                            Math.abs(spot.getY() - startStand.getY())
                    ));
                }
            }
        }

        candidates.sort((a, b) -> {
            if (isTargetSpawner) {
                if (a.isDirectlyAdjacent != b.isDirectlyAdjacent) {
                    return Integer.compare(a.isDirectlyAdjacent, b.isDirectlyAdjacent);
                }
                if (a.isOrthogonalAdjacent != b.isOrthogonalAdjacent) {
                    return Integer.compare(a.isOrthogonalAdjacent, b.isOrthogonalAdjacent);
                }
            }
            if (a.yDiff != b.yDiff) return Integer.compare(a.yDiff, b.yDiff);
            return Double.compare(a.distToPlayer, b.distToPlayer);
        });

        return candidates;
    }

    private static final int[][] CARDINAL_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final double[][] HITBOX_OFFSETS = {{0.26, 0.26}, {-0.26, 0.26}, {0.26, -0.26}, {-0.26, -0.26}};

    private record AStarNode(BlockPos pos, double f) {}

    public static List<BlockPos> findSpawnerRoomPath(
            Level level,
            BlockPos start,
            BlockPos goal,
            SpawnerRoomScanner.SpawnerEnclosure enclosure
    ) {
        return findSpawnerRoomPath(level, start, goal, enclosure, null);
    }

    public static List<BlockPos> findSpawnerRoomPath(
            Level level,
            BlockPos start,
            BlockPos goal,
            SpawnerRoomScanner.SpawnerEnclosure enclosure,
            Map<BlockPos, Double> tilePenalties
    ) {
        return findSpawnerRoomPath(level, start, goal, enclosure, 3000, true, tilePenalties);
    }

    public static List<BlockPos> findSpawnerRoomPath(
            ClientLevel level,
            BlockPos startPos,
            BlockPos goalPos,
            SpawnerEnclosure enc,
            int maxNodes
    ) {
        return findSpawnerRoomPath(level, startPos, goalPos, enc, maxNodes, true, null);
    }

    public static List<BlockPos> findSpawnerRoomPath(
            ClientLevel level,
            BlockPos startPos,
            BlockPos goalPos,
            SpawnerEnclosure enc,
            int maxNodes,
            boolean allowSpawnerFloor
    ) {
        return findSpawnerRoomPath(level, startPos, goalPos, enc, maxNodes, allowSpawnerFloor, null);
    }

    public static List<BlockPos> findSpawnerRoomPath(
            Level level,
            BlockPos startPos,
            BlockPos goalPos,
            SpawnerEnclosure enc,
            int maxNodes,
            boolean allowSpawnerFloor,
            Map<BlockPos, Double> tilePenalties
    ) {
        if (level == null || startPos == null || goalPos == null || maxNodes <= 0) return null;
        if (startPos.getX() == goalPos.getX()
                && startPos.getZ() == goalPos.getZ()
                && Math.abs(startPos.getY() - goalPos.getY()) <= 1) {
            return List.of();
        }

        Map<BlockPos, Double> gScore = new HashMap<>();
        Map<BlockPos, BlockPos> cameFrom = new HashMap<>();
        Set<BlockPos> closedSet = new HashSet<>();
        PriorityQueue<AStarNode> openSet = new PriorityQueue<>(Comparator.comparingDouble(AStarNode::f));

        BlockPos startImm = startPos.immutable();
        gScore.put(startImm, 0.0);
        openSet.add(new AStarNode(startImm, Math.sqrt(startImm.distSqr(goalPos))));

        int nodesCount = 0;

        while (!openSet.isEmpty() && nodesCount < maxNodes) {
            nodesCount++;
            AStarNode cur = openSet.poll();
            if (closedSet.contains(cur.pos)) continue;

            if (cur.pos.getX() == goalPos.getX()
                    && cur.pos.getZ() == goalPos.getZ()
                    && Math.abs(cur.pos.getY() - goalPos.getY()) <= 1) {
                List<BlockPos> path = new ArrayList<>();
                BlockPos step = cur.pos;
                while (step != null) {
                    path.add(step);
                    step = cameFrom.get(step);
                }
                Collections.reverse(path);
                if (!path.isEmpty()) path.remove(0); // exclude start node
                return smoothPath(level, enc, allowSpawnerFloor, tilePenalties, startImm, path);
            }

            closedSet.add(cur.pos);
            double curG = gScore.getOrDefault(cur.pos, Double.POSITIVE_INFINITY);

            for (int[] d : CARDINAL_DIRS) {
                int nx = cur.pos.getX() + d[0];
                int nz = cur.pos.getZ() + d[1];
                int y = cur.pos.getY();

                BlockPos flat = new BlockPos(nx, y, nz);
                BlockPos stepUp = new BlockPos(nx, y + 1, nz);
                BlockPos stepDown = new BlockPos(nx, y - 1, nz);

                // 1) Flat move
                tryRelaxNeighbor(level, enc, goalPos, cur.pos, curG, flat, 1.0, null, allowSpawnerFloor, tilePenalties, closedSet, gScore, cameFrom, openSet);
                // 2) Step up (requires ceiling clearance above current tile)
                tryRelaxNeighbor(level, enc, goalPos, cur.pos, curG, stepUp, 1.3, cur.pos.above(2), allowSpawnerFloor, tilePenalties, closedSet, gScore, cameFrom, openSet);
                // 3) Step down (requires ceiling clearance above destination tile)
                tryRelaxNeighbor(level, enc, goalPos, cur.pos, curG, stepDown, 1.2, new BlockPos(nx, y + 1, nz), allowSpawnerFloor, tilePenalties, closedSet, gScore, cameFrom, openSet);
            }
        }
        return null;
    }

    /**
     * String-pulling pass: collapses 4-directional staircase zig-zags into straight lines
     * whenever the full player hitbox corridor between two nodes is walkable on the same Y
     * and does not cut across penalized tiles.
     */
    private static List<BlockPos> smoothPath(
            Level level,
            SpawnerEnclosure enc,
            boolean allowSpawnerFloor,
            Map<BlockPos, Double> tilePenalties,
            BlockPos startPos,
            List<BlockPos> rawPath
    ) {
        if (rawPath == null || rawPath.size() <= 1) return rawPath;
        List<BlockPos> smoothed = new ArrayList<>();
        BlockPos anchor = startPos;
        int i = 0;
        while (i < rawPath.size()) {
            int farthest = i;
            for (int j = rawPath.size() - 1; j > i; j--) {
                BlockPos cand = rawPath.get(j);
                if (cand.getY() == anchor.getY()
                        && hasWalkableStraightCorridor(level, enc, allowSpawnerFloor, tilePenalties, anchor, cand)) {
                    farthest = j;
                    break;
                }
            }
            BlockPos chosen = rawPath.get(farthest);
            smoothed.add(chosen);
            anchor = chosen;
            i = farthest + 1;
        }
        return smoothed;
    }

    private static boolean hasWalkableStraightCorridor(
            Level level,
            SpawnerEnclosure enc,
            boolean allowSpawnerFloor,
            Map<BlockPos, Double> tilePenalties,
            BlockPos from,
            BlockPos to
    ) {
        double x0 = from.getX() + 0.5;
        double z0 = from.getZ() + 0.5;
        double x1 = to.getX() + 0.5;
        double z1 = to.getZ() + 0.5;
        double dist = Math.hypot(x1 - x0, z1 - z0);
        int steps = Math.max(2, (int) Math.ceil(dist * 4.0));
        int y = from.getY();
        BlockPos.MutableBlockPos check = new BlockPos.MutableBlockPos();
        for (int s = 1; s <= steps; s++) {
            double t = (double) s / steps;
            double sx = x0 + (x1 - x0) * t;
            double sz = z0 + (z1 - z0) * t;
            for (double[] off : HITBOX_OFFSETS) {
                check.set((int) Math.floor(sx + off[0]), y, (int) Math.floor(sz + off[1]));
                if (!isCorridorWalkableInLevel(level, check, enc, allowSpawnerFloor)) {
                    return false;
                }
                if (!check.equals(from) && !check.equals(to) && getTilePenalty(tilePenalties, check) > 0.0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static void tryRelaxNeighbor(
            Level level,
            SpawnerEnclosure enc,
            BlockPos goalPos,
            BlockPos curPos,
            double curG,
            BlockPos nextPos,
            double stepCost,
            BlockPos checkCeil,
            boolean allowSpawnerFloor,
            Map<BlockPos, Double> tilePenalties,
            Set<BlockPos> closedSet,
            Map<BlockPos, Double> gScore,
            Map<BlockPos, BlockPos> cameFrom,
            PriorityQueue<AStarNode> openSet
    ) {
        if (closedSet.contains(nextPos)) return;
        if (checkCeil != null && !isPassableInLevel(level, checkCeil)) return;
        if (!isCorridorWalkableInLevel(level, nextPos, enc, allowSpawnerFloor)) return;

        double penalty = getTilePenalty(tilePenalties, nextPos);
        double tentativeG = curG + stepCost + penalty;
        if (tentativeG < gScore.getOrDefault(nextPos, Double.POSITIVE_INFINITY)) {
            cameFrom.put(nextPos, curPos);
            gScore.put(nextPos, tentativeG);
            double f = tentativeG + Math.sqrt(nextPos.distSqr(goalPos));
            openSet.add(new AStarNode(nextPos, f));
        }
    }

    private static double getTilePenalty(Map<BlockPos, Double> tilePenalties, BlockPos pos) {
        if (tilePenalties == null || tilePenalties.isEmpty() || pos == null) {
            return 0.0;
        }
        Double penalty = tilePenalties.get(pos);
        if (penalty == null || !Double.isFinite(penalty) || penalty <= 0.0) {
            return 0.0;
        }
        return penalty;
    }

    private static boolean isPassableInLevel(Level level, BlockPos pos) {
        if (level instanceof ClientLevel clientLevel) {
            return SpawnerRoomScanner.isBlockPassable(clientLevel, pos);
        }
        if (level == null || pos == null) return true;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
            return true;
        }
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        return name.contains("rail") || name.contains("carpet") || name.equals("ladder") || name.equals("vine");
    }

    private static boolean isCorridorWalkableInLevel(
            Level level,
            BlockPos pos,
            SpawnerEnclosure enc,
            boolean allowSpawnerFloor
    ) {
        if (level instanceof ClientLevel clientLevel) {
            return SpawnerRoomScanner.isSpawnerCorridorWalkable(clientLevel, pos, enc, allowSpawnerFloor);
        }
        if (level == null || pos == null) return false;
        if (enc != null) {
            int maxAllowedY = allowSpawnerFloor ? enc.maxY + 1 : enc.maxY;
            if (pos.getX() < enc.minX || pos.getX() > enc.maxX
                    || pos.getZ() < enc.minZ || pos.getZ() > enc.maxZ
                    || pos.getY() < enc.minY || pos.getY() > maxAllowedY) {
                return false;
            }
            if (!allowSpawnerFloor && enc.interiorSet != null && !enc.interiorSet.isEmpty() && !enc.interiorSet.contains(pos)) {
                return false;
            }
        }
        BlockPos headPos = pos.above();
        BlockPos floorPos = pos.below();
        BlockState foot = level.getBlockState(pos);
        BlockState head = level.getBlockState(headPos);
        BlockState floor = level.getBlockState(floorPos);
        if (SpawnerRoomScanner.isSpawnerBlock(foot) || SpawnerRoomScanner.isSpawnerBlock(head)) return false;
        if (!allowSpawnerFloor && SpawnerRoomScanner.isSpawnerBlock(floor)) return false;
        if (SpawnerRoomScanner.isFarmContainerBlock(foot)
                || SpawnerRoomScanner.isFarmContainerBlock(head)
                || SpawnerRoomScanner.isFarmContainerBlock(floor)) {
            return false;
        }
        if (!isPassableInLevel(level, pos) || !isPassableInLevel(level, headPos)) return false;
        if (floor.isAir()) return false;
        if (SpawnerRoomScanner.isSpawnerBlock(floor)) return allowSpawnerFloor;
        return !floor.getCollisionShape(level, floorPos).isEmpty();
    }
}
