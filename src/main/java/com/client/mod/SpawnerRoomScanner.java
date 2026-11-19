package com.client.mod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SpawnerRoomScanner {

    private static final Pattern COORD_NUM_PATTERN = Pattern.compile("-?\\d+(?:\\.\\d+)?");

    private SpawnerRoomScanner() {}

    public static final class SpawnerEnclosure {
        public final BlockPos anchorPos;
        public final int floorY;
        public final int minX;
        public final int maxX;
        public final int minZ;
        public final int maxZ;
        public final int minY;
        public final int maxY;
        public final Set<BlockPos> interiorSet;

        public SpawnerEnclosure(
                BlockPos anchorPos,
                int floorY,
                int minX,
                int maxX,
                int minZ,
                int maxZ,
                int minY,
                int maxY,
                Set<BlockPos> interiorSet
        ) {
            this.anchorPos = anchorPos;
            this.floorY = floorY;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
            this.minY = minY;
            this.maxY = maxY;
            this.interiorSet = interiorSet;
        }
    }

    public static List<BlockPos> parseCustomSpawnerCoords(String customCoordsText) {
        List<BlockPos> coords = new ArrayList<>();
        if (customCoordsText == null || customCoordsText.isBlank()) {
            return coords;
        }
        String[] lines = customCoordsText.split("[\\r\\n;,]+");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            Matcher m = COORD_NUM_PATTERN.matcher(trimmed);
            List<Double> nums = new ArrayList<>(3);
            while (m.find() && nums.size() < 3) {
                nums.add(Double.parseDouble(m.group()));
            }
            if (nums.size() >= 3) {
                coords.add(new BlockPos(
                        (int) Math.floor(nums.get(0)),
                        (int) Math.floor(nums.get(1)),
                        (int) Math.floor(nums.get(2))
                ));
            }
        }
        return coords;
    }

    private static final class SnakeRow {
        double avgCoord;
        final List<BlockPos> points = new ArrayList<>();

        SnakeRow(double avgCoord, BlockPos first) {
            this.avgCoord = avgCoord;
            this.points.add(first);
        }
    }

    public static List<BlockPos> sortSpawnersInSnakePattern(List<BlockPos> spawners, Vec3 referencePos) {
        if (spawners == null || spawners.size() <= 1) {
            return spawners == null ? new ArrayList<>() : new ArrayList<>(spawners);
        }

        List<BlockPos> sortedByZ = new ArrayList<>(spawners);
        sortedByZ.sort(Comparator.comparingInt(BlockPos::getZ));
        List<SnakeRow> zRows = new ArrayList<>();
        for (BlockPos p : sortedByZ) {
            SnakeRow matched = null;
            for (SnakeRow r : zRows) {
                if (Math.abs(r.avgCoord - p.getZ()) <= 1.35) {
                    matched = r;
                    break;
                }
            }
            if (matched != null) {
                matched.points.add(p);
                double sum = 0;
                for (BlockPos pt : matched.points) sum += pt.getZ();
                matched.avgCoord = sum / matched.points.size();
            } else {
                zRows.add(new SnakeRow(p.getZ(), p));
            }
        }

        List<BlockPos> sortedByX = new ArrayList<>(spawners);
        sortedByX.sort(Comparator.comparingInt(BlockPos::getX));
        List<SnakeRow> xRows = new ArrayList<>();
        for (BlockPos p : sortedByX) {
            SnakeRow matched = null;
            for (SnakeRow r : xRows) {
                if (Math.abs(r.avgCoord - p.getX()) <= 1.35) {
                    matched = r;
                    break;
                }
            }
            if (matched != null) {
                matched.points.add(p);
                double sum = 0;
                for (BlockPos pt : matched.points) sum += pt.getX();
                matched.avgCoord = sum / matched.points.size();
            } else {
                xRows.add(new SnakeRow(p.getX(), p));
            }
        }

        List<SnakeRow> primaryRows = zRows;
        boolean useZAsRows = true;
        if (zRows.size() == 1 && xRows.size() > 1) {
            primaryRows = xRows;
            useZAsRows = false;
        }

        Vec3 pBot = referencePos != null
                ? referencePos
                : Vec3.atLowerCornerOf(primaryRows.get(0).points.get(0));

        SnakeRow firstRow = primaryRows.get(0);
        SnakeRow lastRow = primaryRows.get(primaryRows.size() - 1);

        double rowStartDist = Math.hypot(
                (useZAsRows ? firstRow.points.get(0).getX() : firstRow.avgCoord) - pBot.x,
                (useZAsRows ? firstRow.avgCoord : firstRow.points.get(0).getZ()) - pBot.z
        );
        double rowEndDist = Math.hypot(
                (useZAsRows ? lastRow.points.get(0).getX() : lastRow.avgCoord) - pBot.x,
                (useZAsRows ? lastRow.avgCoord : lastRow.points.get(0).getZ()) - pBot.z
        );
        if (rowEndDist < rowStartDist) {
            java.util.Collections.reverse(primaryRows);
        }

        final boolean sortXSecondary = useZAsRows;
        List<BlockPos> firstRowPoints = new ArrayList<>(primaryRows.get(0).points);
        firstRowPoints.sort(Comparator.comparingInt(p -> sortXSecondary ? p.getX() : p.getZ()));
        double minCoord = sortXSecondary ? firstRowPoints.get(0).getX() : firstRowPoints.get(0).getZ();
        double maxCoord = sortXSecondary
                ? firstRowPoints.get(firstRowPoints.size() - 1).getX()
                : firstRowPoints.get(firstRowPoints.size() - 1).getZ();
        double botSecondary = sortXSecondary ? pBot.x : pBot.z;
        boolean currentAscending = botSecondary <= (minCoord + maxCoord) / 2.0;

        List<BlockPos> result = new ArrayList<>(spawners.size());
        for (SnakeRow row : primaryRows) {
            List<BlockPos> rowPoints = new ArrayList<>(row.points);
            final boolean asc = currentAscending;
            rowPoints.sort((a, b) -> {
                int aSec = sortXSecondary ? a.getX() : a.getZ();
                int bSec = sortXSecondary ? b.getX() : b.getZ();
                double diff = aSec - bSec;
                if (Math.abs(diff) > 0.45) {
                    return asc ? Double.compare(diff, 0.0) : Double.compare(0.0, diff);
                }
                return Integer.compare(a.getY(), b.getY());
            });
            result.addAll(rowPoints);
            currentAscending = !currentAscending;
        }
        return result;
    }

    public static List<BlockPos> findNearbySpawnerBlocks(
            ClientLevel level,
            BlockPos anchorPos,
            int maxRadius,
            int maxCount,
            String customCoordsText
    ) {
        if (level == null || anchorPos == null) return List.of();

        if (customCoordsText != null && !customCoordsText.isBlank()) {
            List<BlockPos> customList = parseCustomSpawnerCoords(customCoordsText);
            if (!customList.isEmpty()) {
                return maxCount > 0 && maxCount < customList.size()
                        ? customList.subList(0, maxCount)
                        : customList;
            }
        }

        List<BlockPos> found = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();

        scanSpawnerBox(level, anchorPos, maxRadius, 8, seen, found);

        if (found.isEmpty()) return List.of();

        List<List<BlockPos>> clusters = clusterSpawners(found);

        // Choose closest cluster to anchorPos
        clusters.sort(Comparator.comparingDouble(cluster -> minHorizDistToPoint(cluster, anchorPos)));

        // Expand scan from the closest cluster's centroid so large grids are fully captured
        // even when the player starts on one edge of the room.
        BlockPos centroid = computeCentroid(clusters.get(0));
        int prevSize = found.size();
        scanSpawnerBox(level, centroid, maxRadius, 8, seen, found);
        if (found.size() > prevSize) {
            clusters = clusterSpawners(found);
            clusters.sort(Comparator.comparingDouble(cluster -> minHorizDistToPoint(cluster, anchorPos)));
        }

        List<BlockPos> activeCluster = new ArrayList<>(clusters.get(0));
        if (maxCount == 0 && clusters.size() > 1) {
            for (int c = 1; c < clusters.size(); c++) {
                List<BlockPos> other = clusters.get(c);
                if (minClusterHorizDist(activeCluster, other) <= 10.0) {
                    activeCluster.addAll(other);
                }
            }
        }

        // Fixed corner (minX, minY, minZ) so S-Snake order is 100% deterministic every pass
        int minCornerX = Integer.MAX_VALUE;
        int minCornerY = Integer.MAX_VALUE;
        int minCornerZ = Integer.MAX_VALUE;
        for (BlockPos sp : activeCluster) {
            if (sp.getX() < minCornerX) minCornerX = sp.getX();
            if (sp.getY() < minCornerY) minCornerY = sp.getY();
            if (sp.getZ() < minCornerZ) minCornerZ = sp.getZ();
        }

        List<BlockPos> ordered = sortSpawnersInSnakePattern(
                activeCluster,
                new Vec3(minCornerX, minCornerY, minCornerZ)
        );
        if (maxCount > 0 && maxCount < ordered.size()) {
            return new ArrayList<>(ordered.subList(0, maxCount));
        }
        return ordered;
    }

    private static void scanSpawnerBox(
            ClientLevel level,
            BlockPos center,
            int maxRadius,
            int maxDy,
            Set<BlockPos> seen,
            List<BlockPos> found
    ) {
        for (int dx = -maxRadius; dx <= maxRadius; dx++) {
            for (int dy = -maxDy; dy <= maxDy; dy++) {
                for (int dz = -maxRadius; dz <= maxRadius; dz++) {
                    BlockPos checkPos = center.offset(dx, dy, dz);
                    if (seen.contains(checkPos)) continue;
                    BlockState state = level.getBlockState(checkPos);
                    if (isSpawnerBlock(state)) {
                        BlockPos imm = checkPos.immutable();
                        seen.add(imm);
                        found.add(imm);
                    }
                }
            }
        }
    }

    private static List<List<BlockPos>> clusterSpawners(List<BlockPos> found) {
        boolean[] visited = new boolean[found.size()];
        List<List<BlockPos>> clusters = new ArrayList<>();
        for (int i = 0; i < found.size(); i++) {
            if (visited[i]) continue;
            List<BlockPos> cluster = new ArrayList<>();
            Queue<Integer> q = new ArrayDeque<>();
            visited[i] = true;
            q.add(i);
            while (!q.isEmpty()) {
                int idx = q.poll();
                BlockPos cur = found.get(idx);
                cluster.add(cur);
                for (int j = 0; j < found.size(); j++) {
                    if (visited[j]) continue;
                    BlockPos other = found.get(j);
                    if (Math.abs(cur.getX() - other.getX()) <= 4
                            && Math.abs(cur.getZ() - other.getZ()) <= 4
                            && Math.abs(cur.getY() - other.getY()) <= 5) {
                        visited[j] = true;
                        q.add(j);
                    }
                }
            }
            clusters.add(cluster);
        }
        return clusters;
    }

    private static double minHorizDistToPoint(List<BlockPos> cluster, BlockPos point) {
        double minD = Double.POSITIVE_INFINITY;
        for (BlockPos p : cluster) {
            double d = Math.hypot(p.getX() - point.getX(), p.getZ() - point.getZ());
            if (d < minD) minD = d;
        }
        return minD;
    }

    private static double minClusterHorizDist(List<BlockPos> a, List<BlockPos> b) {
        double minD = Double.POSITIVE_INFINITY;
        for (BlockPos pa : a) {
            for (BlockPos pb : b) {
                double d = Math.hypot(pa.getX() - pb.getX(), pa.getZ() - pb.getZ());
                if (d < minD) minD = d;
            }
        }
        return minD;
    }

    private static BlockPos computeCentroid(List<BlockPos> points) {
        long sx = 0, sy = 0, sz = 0;
        for (BlockPos p : points) {
            sx += p.getX();
            sy += p.getY();
            sz += p.getZ();
        }
        int n = Math.max(1, points.size());
        return new BlockPos((int) Math.round((double) sx / n), (int) Math.round((double) sy / n), (int) Math.round((double) sz / n));
    }

    public static boolean isSpawnerBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        Block b = state.getBlock();
        if (b == Blocks.SPAWNER) return true;
        String key = BuiltInRegistries.BLOCK.getKey(b).getPath().toLowerCase(Locale.ROOT);
        return key.contains("spawner");
    }

    public static boolean isFarmContainerBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        String bName = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        return bName.equals("chest")
                || bName.equals("trapped_chest")
                || bName.equals("barrel")
                || bName.equals("shulker_box")
                || bName.endsWith("_shulker_box")
                || bName.contains("chest")
                || bName.contains("barrel");
    }

    private static boolean isPassableBlockPath(String name) {
        return name.contains("rail") || name.contains("carpet") || name.equals("ladder") || name.equals("vine");
    }

    public static boolean isBlockPassable(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null) return true;
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || state.getCollisionShape(level, pos).isEmpty()) {
            return true;
        }
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        return isPassableBlockPath(name);
    }

    public static boolean isSpawnerFloorValid(ClientLevel level, BlockPos floorPos) {
        return isSpawnerFloorValid(level, floorPos, true);
    }

    public static boolean isSpawnerFloorValid(ClientLevel level, BlockPos floorPos, boolean allowSpawnerFloor) {
        if (level == null || floorPos == null) return false;
        BlockState state = level.getBlockState(floorPos);
        if (state.isAir()) return false;
        if (isSpawnerBlock(state)) {
            return allowSpawnerFloor;
        }
        // Chests, barrels, shulkers, and non-solid blocks must NEVER be walkable floor
        if (isFarmContainerBlock(state) || state.getCollisionShape(level, floorPos).isEmpty()) {
            return false;
        }
        String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().toLowerCase(Locale.ROOT);
        if (name.equals("air") || name.equals("cave_air") || name.equals("void_air")
                || name.equals("lava") || name.equals("flowing_lava")
                || name.equals("water") || name.equals("flowing_water")
                || name.equals("fire") || name.equals("soul_fire")
                || name.equals("cactus") || name.equals("sweet_berry_bush")
                || name.equals("cobweb") || name.equals("powder_snow")
                || name.equals("magma_block") || name.equals("campfire") || name.equals("soul_campfire")) {
            return false;
        }
        if (name.contains("fence") || name.contains("wall") || name.contains("gate") || name.contains("iron_bars")) {
            return false;
        }
        return !isPassableBlockPath(name);
    }

    public static boolean isSpawnerTileWalkableRaw(ClientLevel level, BlockPos pos) {
        return isSpawnerTileWalkableRaw(level, pos, true);
    }

    public static boolean isSpawnerTileWalkableRaw(ClientLevel level, BlockPos pos, boolean allowSpawnerFloor) {
        if (level == null || pos == null) return false;
        BlockPos headPos = pos.above();
        BlockPos floorPos = pos.below();

        BlockState foot = level.getBlockState(pos);
        BlockState head = level.getBlockState(headPos);
        BlockState floor = level.getBlockState(floorPos);

        if (isSpawnerBlock(foot) || isSpawnerBlock(head)) return false;
        if (!allowSpawnerFloor && isSpawnerBlock(floor)) return false;
        if (isFarmContainerBlock(foot) || isFarmContainerBlock(head) || isFarmContainerBlock(floor)) return false;

        if (!isBlockPassable(level, pos) || !isBlockPassable(level, headPos)) return false;
        return isSpawnerFloorValid(level, floorPos, allowSpawnerFloor);
    }

    public static boolean isSpawnerCorridorWalkable(ClientLevel level, BlockPos pos, SpawnerEnclosure enc) {
        return isSpawnerCorridorWalkable(level, pos, enc, true);
    }

    public static boolean isSpawnerCorridorWalkable(
            ClientLevel level,
            BlockPos pos,
            SpawnerEnclosure enc,
            boolean allowSpawnerFloor
    ) {
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
        return isSpawnerTileWalkableRaw(level, pos, allowSpawnerFloor);
    }

    public static BlockPos getSpawnerBotStandPos(ClientLevel level, LocalPlayer player, SpawnerEnclosure enc) {
        if (level == null || player == null) return null;
        Vec3 p = player.position();
        // Inside hopper pit y = X.625, +0.42 resolves cleanly to floor integer Y
        int baseY = (int) Math.floor(p.y + 0.42);
        int bx = (int) Math.floor(p.x);
        int bz = (int) Math.floor(p.z);

        int[] yCandidates = {baseY, baseY + 1, baseY - 1};
        double[][] xzOffsets = {
                {0.0, 0.0},
                {0.34, 0.0}, {-0.34, 0.0},
                {0.0, 0.34}, {0.0, -0.34},
                {0.34, 0.34}, {-0.34, 0.34},
                {0.34, -0.34}, {-0.34, -0.34}
        };

        for (int ty : yCandidates) {
            for (double[] off : xzOffsets) {
                BlockPos cand = new BlockPos((int) Math.floor(p.x + off[0]), ty, (int) Math.floor(p.z + off[1]));
                if (isSpawnerCorridorWalkable(level, cand, enc)) {
                    return cand;
                }
            }
        }
        for (int ty : yCandidates) {
            for (double[] off : xzOffsets) {
                BlockPos cand = new BlockPos((int) Math.floor(p.x + off[0]), ty, (int) Math.floor(p.z + off[1]));
                if (isSpawnerTileWalkableRaw(level, cand)) {
                    return cand;
                }
            }
        }
        return new BlockPos(bx, baseY, bz);
    }

    public static SpawnerEnclosure computeSpawnerEnclosure(
            ClientLevel level,
            LocalPlayer player,
            List<BlockPos> spawners,
            BlockPos homePos,
            SpawnerEnclosure existingEnc
    ) {
        if (level == null || homePos == null) return null;
        BlockPos standPos = (existingEnc != null && existingEnc.anchorPos != null)
                ? existingEnc.anchorPos
                : homePos;

        List<BlockPos> activeSpawners = (spawners != null && !spawners.isEmpty())
                ? spawners
                : List.of(standPos);

        int minSpX = Integer.MAX_VALUE, maxSpX = Integer.MIN_VALUE;
        int minSpZ = Integer.MAX_VALUE, maxSpZ = Integer.MIN_VALUE;
        int minSpY = Integer.MAX_VALUE, maxSpY = Integer.MIN_VALUE;

        for (BlockPos sp : activeSpawners) {
            if (sp.getX() < minSpX) minSpX = sp.getX();
            if (sp.getX() > maxSpX) maxSpX = sp.getX();
            if (sp.getZ() < minSpZ) minSpZ = sp.getZ();
            if (sp.getZ() > maxSpZ) maxSpZ = sp.getZ();
            if (sp.getY() < minSpY) minSpY = sp.getY();
            if (sp.getY() > maxSpY) maxSpY = sp.getY();
        }

        int minX = Math.min(minSpX - 2, standPos.getX() - 1);
        int maxX = Math.max(maxSpX + 2, standPos.getX() + 1);
        int minZ = Math.min(minSpZ - 2, standPos.getZ() - 1);
        int maxZ = Math.max(maxSpZ + 2, standPos.getZ() + 1);
        int minY = Math.min(minSpY - 2, standPos.getY() - 2);
        int maxY = Math.max(maxSpY + 2, standPos.getY() + 2);
        int floorY = standPos.getY();

        Set<BlockPos> interiorSet = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        java.util.function.Consumer<BlockPos> addSeedIfWalkable = pt -> {
            if (pt == null) return;
            if (pt.getX() < minX || pt.getX() > maxX
                    || pt.getZ() < minZ || pt.getZ() > maxZ
                    || pt.getY() < minY || pt.getY() > maxY) {
                return;
            }
            if (interiorSet.contains(pt)) return;
            if (isSpawnerTileWalkableRaw(level, pt)) {
                BlockPos imm = pt.immutable();
                interiorSet.add(imm);
                queue.add(imm);
            }
        };

        addSeedIfWalkable.accept(standPos);
        addSeedIfWalkable.accept(standPos.above());
        addSeedIfWalkable.accept(standPos.below());

        BlockPos curBotStand = getSpawnerBotStandPos(level, player, null);
        if (curBotStand != null) {
            addSeedIfWalkable.accept(curBotStand);
        }

        Set<Long> seenCols = new HashSet<>();
        for (BlockPos sp : activeSpawners) {
            addSeedIfWalkable.accept(sp.above());
            long colKey = (((long) sp.getX()) << 32) ^ (sp.getZ() & 0xffffffffL);
            if (!seenCols.add(colKey)) continue;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -2; dy <= 2; dy++) {
                        addSeedIfWalkable.accept(new BlockPos(sp.getX() + dx, sp.getY() + dy, sp.getZ() + dz));
                    }
                }
            }
        }

        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && interiorSet.size() < 2000) {
            BlockPos cur = queue.poll();
            for (int[] d : dirs) {
                int nx = cur.getX() + d[0];
                int nz = cur.getZ() + d[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) continue;
                for (int dy = -1; dy <= 1; dy++) {
                    int ny = cur.getY() + dy;
                    if (ny < minY || ny > maxY) continue;
                    BlockPos cand = new BlockPos(nx, ny, nz);
                    if (interiorSet.contains(cand)) continue;
                    if (isSpawnerTileWalkableRaw(level, cand)) {
                        interiorSet.add(cand);
                        queue.add(cand);
                    }
                }
            }
        }

        return new SpawnerEnclosure(standPos, floorY, minX, maxX, minZ, maxZ, minY, maxY, interiorSet);
    }

    public static boolean isTargetReachableFromSpawnerEnclosure(
            BlockPos targetPos,
            SpawnerEnclosure enclosure,
            double maxReach
    ) {
        if (targetPos == null || enclosure == null || enclosure.interiorSet == null || enclosure.interiorSet.isEmpty()) {
            return true;
        }
        Vec3 targetCenter = Vec3.atCenterOf(targetPos);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (dx == 0 && dz == 0) continue;
                int sx = targetPos.getX() + dx;
                int sz = targetPos.getZ() + dz;
                for (int sy = enclosure.minY; sy <= enclosure.maxY; sy++) {
                    if (!enclosure.interiorSet.contains(new BlockPos(sx, sy, sz))) continue;
                    Vec3 eyePos = new Vec3(sx + 0.5, sy + 1.62, sz + 0.5);
                    double dist = eyePos.distanceTo(targetCenter);
                    if (dist >= 0.85 && dist <= maxReach) return true;
                }
            }
        }
        return false;
    }

    public static List<BlockPos> findNearbyContainerPositions(
            ClientLevel level,
            Vec3 playerPos,
            BlockPos originPos,
            int maxDist
    ) {
        if (level == null || playerPos == null) return List.of();
        BlockPos base = originPos != null ? originPos : BlockPos.containing(playerPos);
        record ChestCandidate(BlockPos pos, double dist) {}
        List<ChestCandidate> chests = new ArrayList<>();

        for (int dx = -maxDist; dx <= maxDist; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -maxDist; dz <= maxDist; dz++) {
                    BlockPos checkPos = base.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(checkPos);
                    if (isFarmContainerBlock(st)) {
                        double dist = playerPos.distanceTo(Vec3.atCenterOf(checkPos));
                        chests.add(new ChestCandidate(checkPos.immutable(), dist));
                    }
                }
            }
        }
        chests.sort(Comparator.comparingDouble(ChestCandidate::dist));
        List<BlockPos> result = new ArrayList<>(chests.size());
        Set<BlockPos> coveredDoubleChestHalves = new HashSet<>();
        for (ChestCandidate c : chests) {
            if (coveredDoubleChestHalves.contains(c.pos)) continue;
            result.add(c.pos);
            BlockPos partner = getConnectedChestPos(level, c.pos);
            if (partner != null) {
                coveredDoubleChestHalves.add(partner);
            }
        }
        return result;
    }

    public static BlockPos getConnectedChestPos(ClientLevel level, BlockPos pos) {
        if (level == null || pos == null) return null;
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ChestBlock)) return null;
        if (!state.hasProperty(ChestBlock.TYPE)) return null;
        ChestType type = state.getValue(ChestBlock.TYPE);
        if (type == ChestType.SINGLE) return null;
        Direction connectedDir = ChestBlock.getConnectedDirection(state);
        return pos.relative(connectedDir).immutable();
    }
}
