package com.client.mod;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Meteor-parity Xray logic: target matching, opacity alpha scaling, and exposed-face detection.
 */
public final class XrayHandler {

    private static final Direction[] CARDINAL_DIRECTIONS = Direction.values();
    private static final ThreadLocal<Boolean> TRANSLUCENT_PASS = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public static void setTranslucentPass(boolean translucent) {
        TRANSLUCENT_PASS.set(translucent);
    }

    public static boolean isTranslucentPass() {
        return Boolean.TRUE.equals(TRANSLUCENT_PASS.get());
    }

    public static final List<String> PRESET_ORES = List.of(
            "minecraft:coal_ore",
            "minecraft:deepslate_coal_ore",
            "minecraft:iron_ore",
            "minecraft:deepslate_iron_ore",
            "minecraft:copper_ore",
            "minecraft:deepslate_copper_ore",
            "minecraft:gold_ore",
            "minecraft:deepslate_gold_ore",
            "minecraft:nether_gold_ore",
            "minecraft:redstone_ore",
            "minecraft:deepslate_redstone_ore",
            "minecraft:lapis_ore",
            "minecraft:deepslate_lapis_ore",
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore",
            "minecraft:deepslate_emerald_ore",
            "minecraft:nether_quartz_ore",
            "minecraft:ancient_debris"
    );

    public static final List<String> PRESET_STORAGE = List.of(
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:ender_chest",
            "minecraft:barrel",
            "minecraft:shulker_box",
            "minecraft:white_shulker_box",
            "minecraft:light_gray_shulker_box",
            "minecraft:gray_shulker_box",
            "minecraft:black_shulker_box",
            "minecraft:brown_shulker_box",
            "minecraft:red_shulker_box",
            "minecraft:orange_shulker_box",
            "minecraft:yellow_shulker_box",
            "minecraft:lime_shulker_box",
            "minecraft:green_shulker_box",
            "minecraft:cyan_shulker_box",
            "minecraft:light_blue_shulker_box",
            "minecraft:blue_shulker_box",
            "minecraft:purple_shulker_box",
            "minecraft:magenta_shulker_box",
            "minecraft:pink_shulker_box",
            "minecraft:spawner",
            "minecraft:trial_spawner",
            "minecraft:vault",
            "minecraft:hopper",
            "minecraft:dispenser",
            "minecraft:dropper",
            "minecraft:furnace",
            "minecraft:blast_furnace",
            "minecraft:smoker"
    );

    public static final List<String> PRESET_NETHER = List.of(
            "minecraft:ancient_debris",
            "minecraft:nether_gold_ore",
            "minecraft:nether_quartz_ore",
            "minecraft:glowstone",
            "minecraft:nether_portal",
            "minecraft:nether_bricks",
            "minecraft:respawn_anchor",
            "minecraft:lodestone",
            "minecraft:gilded_blackstone",
            "minecraft:crying_obsidian"
    );

    private XrayHandler() {
    }

    public static Set<String> createDefaultBlocks() {
        return new LinkedHashSet<>(PRESET_ORES);
    }

    public static Set<String> createDefaultBlockEspBlocks() {
        return new LinkedHashSet<>(List.of(
                "minecraft:chest",
                "minecraft:trapped_chest",
                "minecraft:ender_chest",
                "minecraft:spawner"
        ));
    }

    public static boolean isEnabled() {
        ModConfig cfg = ModConfig.getInstance();
        return cfg != null && cfg.xrayEnabled && !PanicHandler.isPanicActive();
    }

    private static final Object CACHE_LOCK = new Object();
    private static volatile java.util.Set<net.minecraft.world.level.block.Block> cachedTargetBlocks = null;
    private static volatile ModConfig cachedTargetCfg = null;

    public static void invalidateBlockCache() {
        cachedTargetBlocks = null;
        cachedTargetCfg = null;
    }

    private static java.util.Set<net.minecraft.world.level.block.Block> getOrBuildTargetBlocks(ModConfig cfg) {
        if (cfg == null) {
            return java.util.Set.of();
        }
        java.util.Set<net.minecraft.world.level.block.Block> blocks = cachedTargetBlocks;
        if (blocks != null && cachedTargetCfg == cfg) {
            return blocks;
        }
        synchronized (CACHE_LOCK) {
            if (cachedTargetBlocks != null && cachedTargetCfg == cfg) {
                return cachedTargetBlocks;
            }
            java.util.Set<net.minecraft.world.level.block.Block> built =
                    java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            java.util.Set<String> ids = cfg.xrayBlocks;
            if (ids != null && !ids.isEmpty()) {
                for (String rawId : new java.util.ArrayList<>(ids)) {
                    if (rawId == null || rawId.isEmpty()) continue;
                    net.minecraft.resources.Identifier id =
                            net.minecraft.resources.Identifier.tryParse(rawId.toLowerCase(Locale.ROOT));
                    if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                        net.minecraft.world.level.block.Block b = BuiltInRegistries.BLOCK.getValue(id);
                        if (b != null) {
                            built.add(b);
                        }
                    }
                }
            }
            java.util.Set<net.minecraft.world.level.block.Block> immutable = java.util.Collections.unmodifiableSet(built);
            cachedTargetBlocks = immutable;
            cachedTargetCfg = cfg;
            return immutable;
        }
    }

    public static String blockId(BlockState state) {
        if (state == null) {
            return "";
        }
        return blockId(state.getBlock());
    }

    public static String blockId(Block block) {
        if (block == null) {
            return "";
        }
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    public static boolean isTargetBlock(BlockState state) {
        if (state == null) {
            return false;
        }
        return getOrBuildTargetBlocks(ModConfig.getInstance()).contains(state.getBlock());
    }

    public static boolean isTargetBlockId(String id, ModConfig cfg) {
        if (id == null || id.isEmpty() || cfg == null || cfg.xrayBlocks == null) {
            return false;
        }
        return cfg.xrayBlocks.contains(id.toLowerCase(Locale.ROOT));
    }

    public static int clampOpacity(int opacity) {
        return Math.max(0, Math.min(255, opacity));
    }

    public static int effectiveOpacity() {
        ModConfig cfg = ModConfig.getInstance();
        return cfg != null ? clampOpacity(cfg.xrayOpacity) : 0;
    }

    public static boolean shouldHideCompletely(boolean isTarget, int opacity) {
        return !isTarget && clampOpacity(opacity) <= 0;
    }

    public static boolean shouldRenderTranslucent(boolean isTarget, int opacity) {
        int clamped = clampOpacity(opacity);
        return !isTarget && clamped > 0 && clamped < 255;
    }

    public static int vanillaAlphaMultiplierMask(int opacity) {
        return (clampOpacity(opacity) << 24) | 0x00FFFFFF;
    }

    public static int multiplyAlphaARGB(int argb, int opacity) {
        int alpha = ((argb >>> 24) & 0xFF) * clampOpacity(opacity) / 255;
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static boolean shouldHideBlock(BlockGetter level, BlockPos pos, BlockState state) {
        if (!isEnabled() || state == null || state.isAir()) {
            return false;
        }
        ModConfig cfg = ModConfig.getInstance();
        boolean isTarget = getOrBuildTargetBlocks(cfg).contains(state.getBlock());
        if (!isTarget) {
            return clampOpacity(cfg.xrayOpacity) <= 0;
        }
        return cfg.xrayExposedOnly && level != null && pos != null && !isExposed(level, pos);
    }

    public static boolean isExposed(BlockGetter level, BlockPos pos) {
        BlockPos.MutableBlockPos neighborPos = new BlockPos.MutableBlockPos();
        return hasExposedFace(dir -> {
            neighborPos.setWithOffset(pos, dir);
            BlockState neighbor = level.getBlockState(neighborPos);
            return neighbor.isSolidRender() && neighbor.canOcclude();
        });
    }

    /**
     * Pure testable check: returns true if at least one of the 6 faces borders a non-opaque block.
     */
    public static boolean hasExposedFace(Predicate<Direction> isOpaqueNeighbor) {
        for (Direction dir : CARDINAL_DIRECTIONS) {
            if (!isOpaqueNeighbor.test(dir)) {
                return true;
            }
        }
        return false;
    }

    public static void refreshChunksIfActive() {
        if (ModConfig.getInstance().xrayEnabled) {
            ModConfig.reloadChunks();
        }
    }
}
