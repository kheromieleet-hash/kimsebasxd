package com.client.mod;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ray-steps along the player's look vector to interact with containers behind blocking blocks.
 */
public final class GhostHandHandler {

    private static final double STEP_SIZE = 0.1;

    private GhostHandHandler() {
    }

    public static boolean tryInteractThroughBlocks(
            LocalPlayer player,
            ClientLevel level,
            MultiPlayerGameMode gameMode,
            HitResult currentHit) {
        if (player == null || level == null || gameMode == null) {
            return false;
        }
        if (currentHit instanceof BlockHitResult directHit && directHit.getType() == HitResult.Type.BLOCK) {
            BlockPos directPos = directHit.getBlockPos();
            if (isInteractableContainer(level, directPos, level.getBlockState(directPos))) {
                // Direct hit is already a container; let vanilla handle it normally.
                return false;
            }
        }

        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        double maxRange = Math.max(0.5, player.blockInteractionRange());
        final BlockPos[] matchedPos = new BlockPos[1];
        final Vec3[] matchedHitVec = new Vec3[1];

        ExploitPacketRules.walkRayBlocks(
                eyePos.x, eyePos.y, eyePos.z,
                look.x, look.y, look.z,
                maxRange, STEP_SIZE,
                (bx, by, bz, dist) -> {
                    BlockPos pos = new BlockPos(bx, by, bz);
                    BlockState state = level.getBlockState(pos);
                    if (isInteractableContainer(level, pos, state)) {
                        matchedPos[0] = pos;
                        matchedHitVec[0] = eyePos.add(look.scale(dist));
                        return true;
                    }
                    return false;
                });

        if (matchedPos[0] == null) {
            return false;
        }

        Direction face = Direction.getApproximateNearest(-look.x, -look.y, -look.z);
        BlockHitResult ghostHit = new BlockHitResult(matchedHitVec[0], face, matchedPos[0], false);
        InteractionResult result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, ghostHit);
        if (result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);
            return true;
        }
        return false;
    }

    private static boolean isInteractableContainer(ClientLevel level, BlockPos pos, BlockState state) {
        if (state == null || state.isAir()) {
            return false;
        }
        if (state.getBlock() instanceof EnderChestBlock) {
            return true;
        }
        return state.getMenuProvider(level, pos) != null;
    }
}
