package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Automatically executes "/sell all" when any selected item is present in the player's inventory,
 * with an optional Safety Pause that avoids selling during combat/mining/item use and briefly
 * pauses conflicting automation modules.
 */
public final class AutoSellHandler {
    private static int cooldownTicks = 0;
    private static int pauseRestoreTicks = 0;

    private static boolean pausedAny = false;
    private static boolean prevAutoBreak = false;
    private static boolean prevAutoCrafter = false;
    private static boolean prevFastPlace = false;
    private static boolean prevFastUse = false;
    private static boolean prevBacktrack = false;

    private AutoSellHandler() {
    }

    public static void tick(Minecraft mc) {
        ModConfig config = ModConfig.getInstance();

        if (pauseRestoreTicks > 0) {
            pauseRestoreTicks--;
            if (pauseRestoreTicks == 0) {
                restorePausedModules(config);
            }
        }

        if (!config.autoSellEnabled || mc == null || mc.player == null || mc.level == null || mc.player.connection == null) {
            if (pausedAny) {
                restorePausedModules(config);
            }
            if (!config.autoSellEnabled) {
                cooldownTicks = 0;
            }
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        Set<String> targets = config.autoSellTargets;
        if (targets == null || targets.isEmpty()) {
            return;
        }

        if (!hasAnyTargetItem(mc.player.getInventory(), targets)) {
            return;
        }

        if (mc.gui.screen() != null) {
            return;
        }

        if (config.autoSellSafetyPause) {
            if (mc.player.isUsingItem()
                    || mc.player.hurtTime > 0
                    || mc.options.keyAttack.isDown()
                    || (mc.gameMode != null && mc.gameMode.isDestroying())) {
                return;
            }
            pauseConflictingModules(config);
            pauseRestoreTicks = 3;
        }

        mc.player.connection.sendCommand("sell all");
        cooldownTicks = Math.max(1, config.autoSellDelay) * 20;
    }

    private static boolean hasAnyTargetItem(Inventory inv, Set<String> targets) {
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key != null && targets.contains(key.getPath())) {
                return true;
            }
        }
        return false;
    }

    private static void pauseConflictingModules(ModConfig config) {
        if (pausedAny) {
            return;
        }
        prevAutoBreak = config.autoBreakEnabled;
        prevAutoCrafter = config.autoCrafterEnabled;
        prevFastPlace = config.fastPlace;
        prevFastUse = config.fastUseEnabled;
        prevBacktrack = config.backtrack;

        config.autoBreakEnabled = false;
        config.autoCrafterEnabled = false;
        config.fastPlace = false;
        config.fastUseEnabled = false;
        config.backtrack = false;
        pausedAny = true;
    }

    private static void restorePausedModules(ModConfig config) {
        if (!pausedAny) {
            return;
        }
        if (prevAutoBreak) config.autoBreakEnabled = true;
        if (prevAutoCrafter) config.autoCrafterEnabled = true;
        if (prevFastPlace) config.fastPlace = true;
        if (prevFastUse) config.fastUseEnabled = true;
        if (prevBacktrack) config.backtrack = true;
        pausedAny = false;
    }
}
