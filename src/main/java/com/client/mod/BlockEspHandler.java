package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Meteor-parity Block ESP engine: chunk-cached palette scanning, live invalidation,
 * per-block style resolution, and Gizmo box/tracer rendering.
 */
public final class BlockEspHandler {

    private static final int MAX_CHUNKS_SCANNED_PER_FRAME = 4;
    private static final int MAX_RENDERED_BLOCKS = 3500;
    private static final float DEFAULT_STROKE_WIDTH = 1.8f;
    private static final float TRACER_WIDTH = 1.5f;
    private static final double TRACER_FORWARD_OFFSET = 0.25;

    public enum ShapeMode {
        LINES("Lines"),
        SIDES("Sides"),
        BOTH("Both");

        public final String label;

        ShapeMode(String label) {
            this.label = label;
        }

        private static final ShapeMode[] VALUES = values();

        public ShapeMode next() {
            return VALUES[(ordinal() + 1) % VALUES.length];
        }

        public static ShapeMode fromName(String raw) {
            if (raw != null) {
                for (ShapeMode mode : VALUES) {
                    if (mode.label.equalsIgnoreCase(raw) || mode.name().equalsIgnoreCase(raw)) {
                        return mode;
                    }
                }
            }
            return BOTH;
        }
    }

    public static final class BlockEspStyle {
        public String shapeMode = ShapeMode.BOTH.label;
        public int lineColor = 0xFF00D4FF;
        public int sideColor = 0x3800D4FF;
        public boolean tracer = false;
        public int tracerColor = 0xFF00D4FF;

        public BlockEspStyle() {
        }

        public BlockEspStyle(String shapeMode, int lineColor, int sideColor, boolean tracer, int tracerColor) {
            this.shapeMode = ShapeMode.fromName(shapeMode).label;
            this.lineColor = lineColor;
            this.sideColor = sideColor;
            this.tracer = tracer;
            this.tracerColor = tracerColor;
        }

        public BlockEspStyle copy() {
            return new BlockEspStyle(this.shapeMode, this.lineColor, this.sideColor, this.tracer, this.tracerColor);
        }

        public ShapeMode resolveShapeMode() {
            return ShapeMode.fromName(this.shapeMode);
        }

        public void setColorRGB(int rgb) {
            int clean = rgb & 0x00FFFFFF;
            int lineAlpha = (this.lineColor >>> 24) & 0xFF;
            if (lineAlpha == 0) lineAlpha = 0xFF;
            int sideAlpha = (this.sideColor >>> 24) & 0xFF;
            if (sideAlpha == 0) sideAlpha = 0x38;
            int tracerAlpha = (this.tracerColor >>> 24) & 0xFF;
            if (tracerAlpha == 0) tracerAlpha = 0xFF;
            this.lineColor = (lineAlpha << 24) | clean;
            this.sideColor = (sideAlpha << 24) | clean;
            this.tracerColor = (tracerAlpha << 24) | clean;
        }

        public int rgb() {
            return this.lineColor & 0x00FFFFFF;
        }
    }

    public record BlockTarget(int x, int y, int z, String blockId) {
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final Map<Long, List<BlockTarget>> CHUNK_CACHE = new ConcurrentHashMap<>();
    private static final Set<Long> DIRTY_CHUNKS = ConcurrentHashMap.newKeySet();
    private static String[] cachedTargetArray = EMPTY_STRING_ARRAY;
    private static Map<Block, String> cachedTargetBlockMap = Collections.emptyMap();

    private record ResolvedRenderStyle(GizmoStyle gizmo, boolean tracer, int tracerColor) {
    }

    private BlockEspHandler() {
    }

    public static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    public static void clearCache() {
        CHUNK_CACHE.clear();
        DIRTY_CHUNKS.clear();
        cachedTargetArray = EMPTY_STRING_ARRAY;
        cachedTargetBlockMap = Collections.emptyMap();
    }

    public static void markChunkDirty(int chunkX, int chunkZ) {
        DIRTY_CHUNKS.add(chunkKey(chunkX, chunkZ));
    }

    public static void markBlockPosDirty(int blockX, int blockZ) {
        markChunkDirty(blockX >> 4, blockZ >> 4);
    }

    public static void removeChunkFromCache(int chunkX, int chunkZ) {
        long key = chunkKey(chunkX, chunkZ);
        CHUNK_CACHE.remove(key);
        DIRTY_CHUNKS.remove(key);
    }

    public static boolean isChunkDirty(int chunkX, int chunkZ) {
        return DIRTY_CHUNKS.contains(chunkKey(chunkX, chunkZ));
    }

    public static int getCachedChunkCount() {
        return CHUNK_CACHE.size();
    }

    public static void setCachedChunkForTest(int chunkX, int chunkZ, List<BlockTarget> targets) {
        CHUNK_CACHE.put(chunkKey(chunkX, chunkZ), targets);
    }

    public static BlockEspStyle resolveEffectiveStyle(String blockId, ModConfig cfg) {
        if (cfg != null && blockId != null && cfg.blockEspPerBlockStyles != null) {
            BlockEspStyle override = cfg.blockEspPerBlockStyles.get(blockId.toLowerCase(Locale.ROOT));
            if (override != null) {
                return override;
            }
        }
        if (cfg != null && cfg.blockEspDefaultStyle != null) {
            return cfg.blockEspDefaultStyle;
        }
        return new BlockEspStyle();
    }

    public static boolean isWithinRange(
            double px, double py, double pz,
            int bx, int by, int bz,
            int maxRange
    ) {
        double dx = (bx + 0.5) - px;
        double dy = (by + 0.5) - py;
        double dz = (bz + 0.5) - pz;
        double maxSq = (double) maxRange * (double) maxRange;
        return dx * dx + dy * dy + dz * dz <= maxSq;
    }

    public static double[] computeTracerStart(
            double camX, double camY, double camZ,
            double lookX, double lookY, double lookZ
    ) {
        return new double[] {
                camX + lookX * TRACER_FORWARD_OFFSET,
                camY + lookY * TRACER_FORWARD_OFFSET,
                camZ + lookZ * TRACER_FORWARD_OFFSET
        };
    }

    public static void render(Minecraft mc, ModConfig cfg) {
        if (mc == null || mc.level == null || mc.player == null || cfg == null) {
            return;
        }
        if (!cfg.blockEspEnabled || PanicHandler.isPanicActive()) {
            if (!CHUNK_CACHE.isEmpty()) {
                clearCache();
            }
            return;
        }
        Set<String> targets = cfg.blockEspBlocks;
        if (targets == null || targets.isEmpty()) {
            clearCache();
            return;
        }

        if (!matchesTargetCache(targets)) {
            clearCache();
            rebuildTargetBlockMap(targets);
        }
        Map<Block, String> targetBlockMap = cachedTargetBlockMap;
        if (targetBlockMap.isEmpty()) {
            return;
        }

        int range = Math.max(16, Math.min(160, cfg.blockEspRange));
        int chunkRadius = Math.max(1, (range + 15) >> 4);
        int playerChunkX = mc.player.getBlockX() >> 4;
        int playerChunkZ = mc.player.getBlockZ() >> 4;

        updateVisibleChunks(mc.level, targetBlockMap, playerChunkX, playerChunkZ, chunkRadius);
        renderCachedBlocks(mc, cfg, range);
    }

    private static boolean matchesTargetCache(Set<String> targets) {
        String[] arr = cachedTargetArray;
        if (targets.size() != arr.length) {
            return false;
        }
        for (String s : arr) {
            if (!targets.contains(s)) {
                return false;
            }
        }
        return true;
    }

    private static void rebuildTargetBlockMap(Set<String> targets) {
        String[] snapshot = targets.toArray(new String[0]);
        Map<Block, String> map = new IdentityHashMap<>(Math.max(4, snapshot.length * 2));
        for (String rawId : snapshot) {
            if (rawId == null || rawId.isEmpty()) {
                continue;
            }
            String clean = rawId.trim().toLowerCase(Locale.ROOT);
            Identifier id = Identifier.tryParse(clean);
            if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                Block block = BuiltInRegistries.BLOCK.getValue(id);
                if (block != null) {
                    map.put(block, clean);
                }
            }
        }
        cachedTargetArray = snapshot;
        cachedTargetBlockMap = map;
    }

    private static void updateVisibleChunks(
            ClientLevel level,
            Map<Block, String> targetBlockMap,
            int centerCx,
            int centerCz,
            int chunkRadius
    ) {
        int maxKeepRadiusSq = (chunkRadius + 2) * (chunkRadius + 2);
        Iterator<Map.Entry<Long, List<BlockTarget>>> it = CHUNK_CACHE.entrySet().iterator();
        while (it.hasNext()) {
            long key = it.next().getKey();
            int cx = (int) (key >> 32);
            int cz = (int) key;
            int dx = cx - centerCx;
            int dz = cz - centerCz;
            if (dx * dx + dz * dz > maxKeepRadiusSq) {
                it.remove();
                DIRTY_CHUNKS.remove(key);
            }
        }

        int scanned = 0;
        for (int r = 0; r <= chunkRadius && scanned < MAX_CHUNKS_SCANNED_PER_FRAME; r++) {
            for (int dx = -r; dx <= r && scanned < MAX_CHUNKS_SCANNED_PER_FRAME; dx++) {
                for (int dz = -r; dz <= r && scanned < MAX_CHUNKS_SCANNED_PER_FRAME; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int cx = centerCx + dx;
                    int cz = centerCz + dz;
                    long key = chunkKey(cx, cz);
                    if (CHUNK_CACHE.containsKey(key) && !DIRTY_CHUNKS.contains(key)) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
                    if (chunk == null) {
                        continue;
                    }
                    DIRTY_CHUNKS.remove(key);
                    CHUNK_CACHE.put(key, scanChunk(level, chunk, cx, cz, targetBlockMap));
                    scanned++;
                }
            }
        }
    }

    private static List<BlockTarget> scanChunk(
            ClientLevel level,
            LevelChunk chunk,
            int chunkX,
            int chunkZ,
            Map<Block, String> targetBlockMap
    ) {
        List<BlockTarget> found = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int s = 0; s < sections.length; s++) {
            LevelChunkSection section = sections[s];
            if (section == null || section.hasOnlyAir()) {
                continue;
            }
            if (!section.maybeHas(state -> targetBlockMap.containsKey(state.getBlock()))) {
                continue;
            }
            int baseY = level.getSectionYFromSectionIndex(s) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.isAir()) {
                            continue;
                        }
                        String id = targetBlockMap.get(state.getBlock());
                        if (id != null) {
                            found.add(new BlockTarget(baseX + x, baseY + y, baseZ + z, id));
                        }
                    }
                }
            }
        }
        return found;
    }

    private static ResolvedRenderStyle resolveRenderStyle(BlockEspStyle style) {
        ShapeMode shape = style.resolveShapeMode();
        GizmoStyle gizmo = switch (shape) {
            case LINES -> GizmoStyle.stroke(style.lineColor, DEFAULT_STROKE_WIDTH);
            case SIDES -> GizmoStyle.fill(style.sideColor);
            case BOTH -> GizmoStyle.strokeAndFill(style.lineColor, DEFAULT_STROKE_WIDTH, style.sideColor);
        };
        return new ResolvedRenderStyle(gizmo, style.tracer, style.tracerColor);
    }

    private static void renderCachedBlocks(Minecraft mc, ModConfig cfg, int range) {
        net.minecraft.client.Camera camera = mc.gameRenderer.mainCamera();
        Vec3 camPos = camera.position();
        Vec3 lookVec = Vec3.directionFromRotation(camera.xRot(), camera.yRot());
        Vec3 tracerOrigin = new Vec3(
                camPos.x + lookVec.x * TRACER_FORWARD_OFFSET,
                camPos.y + lookVec.y * TRACER_FORWARD_OFFSET,
                camPos.z + lookVec.z * TRACER_FORWARD_OFFSET
        );

        BlockEspStyle fallbackStyle = cfg.blockEspDefaultStyle != null ? cfg.blockEspDefaultStyle : new BlockEspStyle();
        ResolvedRenderStyle defaultRender = resolveRenderStyle(fallbackStyle);
        Map<String, ResolvedRenderStyle> overrideStyles = null;
        if (cfg.blockEspPerBlockStyles != null && !cfg.blockEspPerBlockStyles.isEmpty()) {
            overrideStyles = new HashMap<>(cfg.blockEspPerBlockStyles.size() * 2);
            for (Map.Entry<String, BlockEspStyle> entry : cfg.blockEspPerBlockStyles.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    overrideStyles.put(entry.getKey().toLowerCase(Locale.ROOT), resolveRenderStyle(entry.getValue()));
                }
            }
        }

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        int drawn = 0;
        for (List<BlockTarget> chunkTargets : CHUNK_CACHE.values()) {
            if (chunkTargets == null || chunkTargets.isEmpty()) {
                continue;
            }
            for (BlockTarget target : chunkTargets) {
                if (drawn >= MAX_RENDERED_BLOCKS) {
                    return;
                }
                if (!isWithinRange(px, py, pz, target.x(), target.y(), target.z(), range)) {
                    continue;
                }
                ResolvedRenderStyle renderStyle = defaultRender;
                if (overrideStyles != null) {
                    ResolvedRenderStyle override = overrideStyles.get(target.blockId());
                    if (override != null) {
                        renderStyle = override;
                    }
                }
                mutablePos.set(target.x(), target.y(), target.z());
                Gizmos.cuboid(mutablePos, 0.004f, renderStyle.gizmo()).setAlwaysOnTop();

                if (renderStyle.tracer()) {
                    Vec3 center = new Vec3(target.x() + 0.5, target.y() + 0.5, target.z() + 0.5);
                    Gizmos.line(tracerOrigin, center, renderStyle.tracerColor(), TRACER_WIDTH).setAlwaysOnTop();
                }
                drawn++;
            }
        }
    }
}
