package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Automatically tills eligible dirt/grass blocks into farmland when holding a hoe.
 */
public final class AutoFarmLandHandler {
    private static int tickCooldown = 0;

    private AutoFarmLandHandler() {
    }

    public static void tick(Minecraft mc) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.autoFarmLandEnabled) {
            tickCooldown = 0;
            return;
        }
        if (mc == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            return;
        }
        if (mc.gui != null && mc.gui.screen() != null) {
            return;
        }
        if (tickCooldown > 0) {
            tickCooldown--;
            return;
        }

        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.isEmpty() || !mainHand.is(ItemTags.HOES)) {
            return;
        }

        double range = Math.max(2.0, Math.min(5.0, cfg.autoFarmLandRange));
        double rangeSq = range * range;
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        int radius = (int) Math.ceil(range);
        BlockPos center = mc.player.blockPosition();

        BlockPos bestPos = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    double distSq = Vec3.atCenterOf(pos).distanceToSqr(eyePos);
                    if (distSq > rangeSq || distSq >= bestDistSq) {
                        continue;
                    }
                    if (isEligible(mc.level, pos, cfg.autoFarmLandRequireWater)) {
                        bestDistSq = distSq;
                        bestPos = pos;
                    }
                }
            }
        }

        if (bestPos == null) {
            return;
        }

        Vec3 hitVec = new Vec3(bestPos.getX() + 0.5, bestPos.getY() + 1.0, bestPos.getZ() + 0.5);
        BlockHitResult hitResult = new BlockHitResult(hitVec, Direction.UP, bestPos, false);
        InteractionResult res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        if (res instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            mc.player.swing(InteractionHand.MAIN_HAND, mainHand.getInteractAnimation(), false);
        }
        tickCooldown = Math.max(0, Math.min(10, cfg.autoFarmLandDelay));
    }

    private static boolean isEligible(Level level, BlockPos pos, boolean requireWater) {
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        boolean tillable = block == Blocks.DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT_PATH
                || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT;
        if (!tillable) {
            return false;
        }
        if (!level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        return !requireWater || hasNearbyWater(level, pos);
    }

    private static boolean hasNearbyWater(Level level, BlockPos pos) {
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    if (level.getFluidState(pos.offset(dx, dy, dz)).is(FluidTags.WATER)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
