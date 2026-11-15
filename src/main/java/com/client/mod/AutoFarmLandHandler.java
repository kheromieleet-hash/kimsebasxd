package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Automatically tills eligible dirt/grass blocks into farmland when holding a hoe,
 * with line-of-sight checks and optional server-side rotation for anti-cheat safety.
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

        BlockHitResult bestHit = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    Vec3 topCenter = new Vec3(pos.getX() + 0.5, pos.getY() + 0.99, pos.getZ() + 0.5);
                    double distSq = topCenter.distanceToSqr(eyePos);
                    if (distSq > rangeSq || distSq >= bestDistSq) {
                        continue;
                    }
                    if (!isEligible(mc.level, pos, cfg.autoFarmLandRequireWater)) {
                        continue;
                    }
                    BlockHitResult ray = mc.level.clip(new ClipContext(
                            eyePos,
                            topCenter,
                            ClipContext.Block.OUTLINE,
                            ClipContext.Fluid.NONE,
                            mc.player
                    ));
                    if (ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(pos)) {
                        bestDistSq = distSq;
                        bestHit = ray;
                    }
                }
            }
        }

        if (bestHit == null) {
            return;
        }

        if (cfg.autoFarmLandRotate && mc.player.connection != null) {
            Vec3 hitLoc = bestHit.getLocation();
            double dx = hitLoc.x - eyePos.x;
            double dy = hitLoc.y - eyePos.y;
            double dz = hitLoc.z - eyePos.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
            float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
            mc.player.connection.send(new ServerboundMovePlayerPacket.Rot(
                    yaw, pitch, mc.player.onGround(), mc.player.horizontalCollision
            ));
        }

        InteractionResult res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, bestHit);
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
