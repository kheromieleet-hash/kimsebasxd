package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Automatically drops selected items from the player's inventory.
 */
public final class AutoDropHandler {
    private static int cooldownTicks = 0;

    private AutoDropHandler() {
    }

    public static void tick(Minecraft mc) {
        ModConfig config = ModConfig.getInstance();
        if (!config.autoDropEnabled || mc == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            cooldownTicks = 0;
            return;
        }

        if (mc.gui.screen() != null) {
            return;
        }

        if (cooldownTicks > 0) {
            cooldownTicks--;
            return;
        }

        Set<String> targets = config.autoDropTargets;
        if (targets == null || targets.isEmpty()) {
            return;
        }

        Inventory inv = mc.player.getInventory();
        int start = config.autoDropExcludeHotbar ? 9 : 0;
        int end = Math.min(inv.getContainerSize(), Inventory.INVENTORY_SIZE);
        int syncId = mc.player.inventoryMenu.containerId;

        for (int i = start; i < end; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            Identifier key = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (key == null || !targets.contains(key.getPath())) {
                continue;
            }
            int containerSlot = i < 9 ? 36 + i : i;
            mc.gameMode.handleContainerInput(syncId, containerSlot, 1, ContainerInput.THROW, mc.player);
            cooldownTicks = Math.max(0, config.autoDropDelay);
            return;
        }
    }
}
