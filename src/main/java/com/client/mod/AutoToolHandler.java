package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public class AutoToolHandler {
    private static int previousSlot = -1;
    private static int lastSelectedToolSlot = -1;
    private static int idleTicks = 0;
    private static int switchCooldown = 0;
    private static boolean isMining = false;

    public static boolean isMining() {
        return isMining;
    }

    public static void onStartMining(BlockPos pos) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.autoTool) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) return;

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return;

        isMining = true;
        idleTicks = 0;

        if (switchCooldown > 0) {
            return;
        }

        int currentSlot = player.getInventory().getSelectedSlot();

        // If player manually switched away from our tool, honor their choice
        if (lastSelectedToolSlot != -1 && currentSlot != lastSelectedToolSlot) {
            previousSlot = -1;
            lastSelectedToolSlot = -1;
        }

        float currentSpeed = player.getMainHandItem().getDestroySpeed(state);
        float bestSpeed = currentSpeed;
        int bestSlot = currentSlot;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            float speed = stack.getDestroySpeed(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        // Only switch if the found tool actually speeds up mining and is not already held
        if (bestSlot != currentSlot && bestSpeed > 1.0f) {
            if (previousSlot == -1) {
                previousSlot = currentSlot;
            }
            player.getInventory().setSelectedSlot(bestSlot);
            lastSelectedToolSlot = bestSlot;
            switchCooldown = 5; // 5 ticks (250ms) cooldown to prevent any rapid packet triggers
        }
    }

    public static void tick() {
        if (switchCooldown > 0) {
            switchCooldown--;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            previousSlot = -1;
            lastSelectedToolSlot = -1;
            isMining = false;
            idleTicks = 0;
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.autoTool) {
            previousSlot = -1;
            lastSelectedToolSlot = -1;
            isMining = false;
            idleTicks = 0;
            return;
        }

        // If player is holding attack key (actively mining)
        if (mc.options != null && mc.options.keyAttack.isDown()) {
            idleTicks = 0;
            return;
        }

        // Left click is released - wait for a natural delay before swapping back
        if (isMining || previousSlot != -1) {
            idleTicks++;
            // Natural human delay: 6 ticks (~300ms) after releasing left-click
            if (idleTicks >= 6) {
                if (cfg.autoToolSwapBack && previousSlot != -1 && switchCooldown == 0) {
                    int currentSlot = mc.player.getInventory().getSelectedSlot();
                    // Only swap back if player hasn't manually selected another item
                    if (currentSlot == lastSelectedToolSlot) {
                        mc.player.getInventory().setSelectedSlot(previousSlot);
                        switchCooldown = 5;
                    }
                }
                previousSlot = -1;
                lastSelectedToolSlot = -1;
                isMining = false;
                idleTicks = 0;
            }
        }
    }
}
