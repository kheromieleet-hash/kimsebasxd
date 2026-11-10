package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public class AutoReplenishHandler {
    private static final ItemStack[] ITEMS = new ItemStack[10];
    private static boolean initialized = false;
    private static boolean wasEnabled = false;
    private static boolean prevHadOpenScreen = false;
    private static int tickDelayLeft = 0;

    static {
        for (int i = 0; i < ITEMS.length; i++) {
            ITEMS[i] = ItemStack.EMPTY;
        }
    }

    public static void tick(Minecraft mc) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.autoReplenishEnabled || mc.player == null || mc.gameMode == null) {
            wasEnabled = false;
            return;
        }

        boolean hasScreen = mc.gui != null && mc.gui.screen() != null;
        if (!wasEnabled || !initialized) {
            fillItems(mc);
            tickDelayLeft = cfg.autoReplenishTickDelay;
            prevHadOpenScreen = hasScreen;
            wasEnabled = true;
            initialized = true;
            return;
        }

        if (!hasScreen && prevHadOpenScreen) {
            fillItems(mc);
        }
        prevHadOpenScreen = hasScreen;

        if (hasScreen) {
            return;
        }
        if (mc.player.containerMenu != mc.player.inventoryMenu) {
            return;
        }

        if (tickDelayLeft > 0) {
            tickDelayLeft--;
            return;
        }

        Inventory inv = mc.player.getInventory();
        boolean moved = false;
        for (int i = 0; i < 9; i++) {
            if (checkSlot(mc, cfg, i, inv.getItem(i))) {
                moved = true;
                break;
            }
        }

        if (!moved && cfg.autoReplenishOffhand) {
            if (checkSlot(mc, cfg, 9, mc.player.getOffhandItem())) {
                moved = true;
            }
        }

        if (moved) {
            tickDelayLeft = Math.max(1, cfg.autoReplenishTickDelay);
        }
    }

    private static boolean checkSlot(Minecraft mc, ModConfig cfg, int slot, ItemStack currentStack) {
        ItemStack prevStack = ITEMS[slot] != null ? ITEMS[slot] : ItemStack.EMPTY;
        ITEMS[slot] = currentStack.copy();

        int invIndex = (slot == 9) ? 40 : slot;
        int fromSlot = -1;

        if (currentStack.isStackable() && !currentStack.isEmpty() && currentStack.getCount() <= cfg.autoReplenishMinCount) {
            fromSlot = findItem(mc, cfg, currentStack, invIndex, cfg.autoReplenishMinCount - currentStack.getCount() + 1, true);
        } else if (prevStack.isStackable() && currentStack.isEmpty() && !prevStack.isEmpty()) {
            fromSlot = findItem(mc, cfg, prevStack, invIndex, cfg.autoReplenishMinCount - currentStack.getCount() + 1, false);
        } else if (cfg.autoReplenishUnstackable && !prevStack.isStackable() && currentStack.isEmpty() && !prevStack.isEmpty()) {
            fromSlot = findItem(mc, cfg, prevStack, invIndex, 1, false);
        }

        if (fromSlot < 0) {
            return false;
        }

        int selectedSlot = mc.player.getInventory().getSelectedSlot();
        if (fromSlot == selectedSlot || fromSlot == 40) {
            return false;
        }
        if (fromSlot < 9 && fromSlot < invIndex && invIndex != selectedSlot && invIndex != 40) {
            return false;
        }

        moveItem(mc, fromSlot, invIndex);
        ITEMS[slot] = (slot == 9 ? mc.player.getOffhandItem() : mc.player.getInventory().getItem(slot)).copy();
        return true;
    }

    private static int findItem(Minecraft mc, ModConfig cfg, ItemStack targetStack, int excludedSlot, int goodEnoughCount, boolean checkComponents) {
        int bestSlot = -1;
        int bestCount = 0;
        Inventory inv = mc.player.getInventory();
        int minSlot = cfg.autoReplenishSearchHotbar ? 0 : 9;

        for (int i = 35; i >= minSlot; i--) {
            if (i == excludedSlot) {
                continue;
            }
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || stack.getItem() != targetStack.getItem()) {
                continue;
            }
            if (checkComponents && !ItemStack.isSameItemSameComponents(targetStack, stack)) {
                continue;
            }
            if (cfg.autoReplenishSameEnchants && !stack.getEnchantments().equals(targetStack.getEnchantments())) {
                continue;
            }
            if (stack.getCount() > bestCount) {
                bestSlot = i;
                bestCount = stack.getCount();
                if (bestCount >= goodEnoughCount) {
                    break;
                }
            }
        }
        return bestSlot;
    }

    private static void moveItem(Minecraft mc, int fromInvIndex, int toInvIndex) {
        int fromId = indexToContainerId(fromInvIndex);
        int toId = indexToContainerId(toInvIndex);
        if (fromId < 0 || toId < 0) {
            return;
        }
        int syncId = mc.player.inventoryMenu.containerId;
        mc.gameMode.handleContainerInput(syncId, fromId, 0, ContainerInput.PICKUP, mc.player);
        mc.gameMode.handleContainerInput(syncId, toId, 0, ContainerInput.PICKUP, mc.player);
        if (!mc.player.inventoryMenu.getCarried().isEmpty()) {
            mc.gameMode.handleContainerInput(syncId, fromId, 0, ContainerInput.PICKUP, mc.player);
        }
    }

    private static int indexToContainerId(int invIndex) {
        if (invIndex >= 0 && invIndex < 9) {
            return 36 + invIndex;
        }
        if (invIndex >= 9 && invIndex <= 35) {
            return invIndex;
        }
        if (invIndex == 40) {
            return 45;
        }
        return -1;
    }

    private static void fillItems(Minecraft mc) {
        Inventory inv = mc.player.getInventory();
        for (int i = 0; i < 9; i++) {
            ITEMS[i] = inv.getItem(i).copy();
        }
        ITEMS[9] = mc.player.getOffhandItem().copy();
    }
}
