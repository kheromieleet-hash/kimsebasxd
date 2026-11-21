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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        List<BlockPos> found = new ArrayList<>();
        Map<BlockPos, BlockPos> partners = new HashMap<>();

        for (int dx = -maxDist; dx <= maxDist; dx++) {
            for (int dy = -5; dy <= 5; dy++) {
                for (int dz = -maxDist; dz <= maxDist; dz++) {
                    BlockPos checkPos = base.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(checkPos);
                    if (isFarmContainerBlock(st)) {
                        BlockPos imm = checkPos.immutable();
                        found.add(imm);
                        BlockPos partner = getConnectedChestPos(level, imm);
                        if (partner != null) {
                            partners.put(imm, partner);
                        }
                    }
                }
            }
        }
        Vec3 stableRef = originPos != null
                ? new Vec3(originPos.getX() + 0.5, playerPos.y, originPos.getZ() + 0.5)
                : playerPos;
        return deduplicateChestColumns(found, partners, stableRef);
    }

    /**
     * Collapses vertically stacked chests in the same (x, z) column (including stacked double-chest
     * towers) into a single representative chest per column, selecting the topmost reachable chest.
     */
    public static List<BlockPos> deduplicateChestColumns(
            List<BlockPos> rawChests,
            Map<BlockPos, BlockPos> doubleChestPartners,
            Vec3 playerPos
    ) {
        if (rawChests == null || rawChests.isEmpty()) return List.of();

        Map<Long, Long> parent = new HashMap<>();
        for (BlockPos p : rawChests) {
            if (p == null) continue;
            long col = packColumnKey(p.getX(), p.getZ());
            parent.putIfAbsent(col, col);
        }
        if (parent.isEmpty()) return List.of();

        if (doubleChestPartners != null && !doubleChestPartners.isEmpty()) {
            for (BlockPos p : rawChests) {
                if (p == null) continue;
                BlockPos partner = doubleChestPartners.get(p);
                if (partner == null) continue;
                long c1 = packColumnKey(p.getX(), p.getZ());
                long c2 = packColumnKey(partner.getX(), partner.getZ());
                parent.putIfAbsent(c2, c2);
                long r1 = findColumnRoot(parent, c1);
                long r2 = findColumnRoot(parent, c2);
                if (r1 != r2) {
                    parent.put(r2, r1);
                }
            }
        }

        Map<Long, List<BlockPos>> groups = new HashMap<>();
        for (BlockPos p : rawChests) {
            if (p == null) continue;
            long root = findColumnRoot(parent, packColumnKey(p.getX(), p.getZ()));
            groups.computeIfAbsent(root, ignored -> new ArrayList<>()).add(p.immutable());
        }

        int maxReachY = playerPos != null ? (int) Math.floor(playerPos.y) + 4 : Integer.MAX_VALUE;
        List<BlockPos> representatives = new ArrayList<>(groups.size());
        for (List<BlockPos> group : groups.values()) {
            boolean hasReachable = false;
            for (BlockPos p : group) {
                if (p.getY() <= maxReachY) {
                    hasReachable = true;
                    break;
                }
            }

            BlockPos best = null;
            for (BlockPos p : group) {
                if (hasReachable && p.getY() > maxReachY) continue;
                if (best == null || isBetterColumnChest(p, best, playerPos)) {
                    best = p;
                }
            }
            if (best != null) {
                representatives.add(best);
            }
        }

        // Filter out lower staircase/hopper storage tiers (e.g. floor chests offset 1-2 blocks
        // horizontally beneath top hopper-fed chests) so only the top input tier is targeted.
        List<BlockPos> topTier = new ArrayList<>(representatives.size());
        for (BlockPos cand : representatives) {
            boolean hasHigherNearby = false;
            for (BlockPos other : representatives) {
                if (other == cand) continue;
                if (other.getY() > cand.getY()
                        && Math.abs(other.getX() - cand.getX()) <= 2
                        && Math.abs(other.getZ() - cand.getZ()) <= 2) {
                    hasHigherNearby = true;
                    break;
                }
            }
            if (!hasHigherNearby) {
                topTier.add(cand);
            }
        }
        List<BlockPos> finalReps = topTier.isEmpty() ? representatives : topTier;

        if (playerPos != null) {
            finalReps.sort(Comparator.comparingDouble(
                    p -> playerPos.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5)
            ));
        } else {
            finalReps.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                    .thenComparingInt(BlockPos::getX)
                    .thenComparingInt(BlockPos::getZ));
        }
        return finalReps;
    }

    private static long packColumnKey(int x, int z) {
        return (((long) x) << 32) | (z & 0xFFFFFFFFL);
    }

    private static long findColumnRoot(Map<Long, Long> parent, long key) {
        long cur = key;
        while (true) {
            long p = parent.getOrDefault(cur, cur);
            if (p == cur) break;
            long gp = parent.getOrDefault(p, p);
            parent.put(cur, gp);
            cur = p;
        }
        return cur;
    }

    private static boolean isBetterColumnChest(BlockPos candidate, BlockPos currentBest, Vec3 playerPos) {
        if (candidate.getY() != currentBest.getY()) {
            return candidate.getY() > currentBest.getY();
        }
        if (playerPos != null) {
            double candDistSq = playerPos.distanceToSqr(
                    candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            double bestDistSq = playerPos.distanceToSqr(
                    currentBest.getX() + 0.5, currentBest.getY() + 0.5, currentBest.getZ() + 0.5);
            if (Math.abs(candDistSq - bestDistSq) > 1e-6) {
                return candDistSq < bestDistSq;
            }
        }
        if (candidate.getX() != currentBest.getX()) {
            return candidate.getX() < currentBest.getX();
        }
        return candidate.getZ() < currentBest.getZ();
    }

    /**
     * Orders horizontal chest columns wall-by-wall in parallel direction starting from {@code anchorPos}:
     * Wall 1 (closest to {@code anchorPos}) is ordered from the {@code anchorPos} end to its far end
     * (1 -> 2 -> 3 -> 4 -> 5), then Wall 2 (opposite wall) is ordered in the exact same direction
     * from the {@code anchorPos} end to its far end (6 -> 7 -> 8 -> 9 -> 10), wrapping 10 -> 1.
     */
    public static List<BlockPos> orderChestColumnsSequentially(List<BlockPos> columns, BlockPos anchorPos) {
        if (columns == null || columns.isEmpty()) return List.of();
        List<BlockPos> clean = new ArrayList<>(columns.size());
        for (BlockPos c : columns) {
            if (c != null) clean.add(c.immutable());
        }
        int n = clean.size();
        if (n <= 1) return clean;

        int anchorX = anchorPos != null ? anchorPos.getX() : 0;
        int anchorZ = anchorPos != null ? anchorPos.getZ() : 0;

        List<List<BlockPos>> clustersByX = clusterWallsByAxis(clean, true);
        List<List<BlockPos>> clustersByZ = clusterWallsByAxis(clean, false);

        boolean groupByX;
        if (clustersByX.size() != clustersByZ.size()) {
            groupByX = clustersByX.size() < clustersByZ.size();
        } else {
            int maxClusterX = maxClusterSize(clustersByX);
            int maxClusterZ = maxClusterSize(clustersByZ);
            groupByX = maxClusterX >= maxClusterZ;
        }

        List<List<BlockPos>> walls = groupByX ? clustersByX : clustersByZ;
        boolean alongZ = groupByX; // Wall has near-constant X -> chests run along Z (and vice versa)

        // Sort walls so Wall 1 is the wall closest to anchorPos (where the player started)
        walls.sort((w1, w2) -> {
            double d1 = minWallHorizDistSq(w1, anchorX, anchorZ);
            double d2 = minWallHorizDistSq(w2, anchorX, anchorZ);
            if (Math.abs(d1 - d2) > 1e-6) return Double.compare(d1, d2);
            return compareXZ(w1.get(0), w2.get(0));
        });

        // Determine scan direction along the wall from Wall 1's end closest to anchorPos
        List<BlockPos> firstWall = walls.get(0);
        int minAlong = Integer.MAX_VALUE;
        int maxAlong = Integer.MIN_VALUE;
        for (BlockPos p : firstWall) {
            int c = alongZ ? p.getZ() : p.getX();
            if (c < minAlong) minAlong = c;
            if (c > maxAlong) maxAlong = c;
        }
        int anchorAlong = alongZ ? anchorZ : anchorX;
        boolean ascending = Math.abs(anchorAlong - minAlong) <= Math.abs(anchorAlong - maxAlong);

        List<BlockPos> ordered = new ArrayList<>(n);
        for (List<BlockPos> wall : walls) {
            wall.sort((a, b) -> {
                int ca = alongZ ? a.getZ() : a.getX();
                int cb = alongZ ? b.getZ() : b.getX();
                if (ca != cb) {
                    return ascending ? Integer.compare(ca, cb) : Integer.compare(cb, ca);
                }
                return compareXZ(a, b);
            });
            ordered.addAll(wall);
        }
        return ordered;
    }

    private static List<List<BlockPos>> clusterWallsByAxis(List<BlockPos> points, boolean byX) {
        List<BlockPos> sorted = new ArrayList<>(points);
        sorted.sort((a, b) -> {
            int ca = byX ? a.getX() : a.getZ();
            int cb = byX ? b.getX() : b.getZ();
            if (ca != cb) return Integer.compare(ca, cb);
            return compareXZ(a, b);
        });
        List<List<BlockPos>> clusters = new ArrayList<>();
        List<BlockPos> current = new ArrayList<>();
        int prevCoord = 0;
        for (BlockPos p : sorted) {
            int coord = byX ? p.getX() : p.getZ();
            if (current.isEmpty() || Math.abs(coord - prevCoord) <= 1) {
                current.add(p);
            } else {
                clusters.add(current);
                current = new ArrayList<>();
                current.add(p);
            }
            prevCoord = coord;
        }
        if (!current.isEmpty()) {
            clusters.add(current);
        }
        return clusters;
    }

    private static int maxClusterSize(List<List<BlockPos>> clusters) {
        int max = 0;
        for (List<BlockPos> c : clusters) {
            if (c.size() > max) max = c.size();
        }
        return max;
    }

    private static double minWallHorizDistSq(List<BlockPos> wall, int anchorX, int anchorZ) {
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos p : wall) {
            double dx = p.getX() - anchorX;
            double dz = p.getZ() - anchorZ;
            double dSq = dx * dx + dz * dz;
            if (dSq < best) best = dSq;
        }
        return best;
    }

    private static int compareXZ(BlockPos a, BlockPos b) {
        if (a.getX() != b.getX()) return Integer.compare(a.getX(), b.getX());
        return Integer.compare(a.getZ(), b.getZ());
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
