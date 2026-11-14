package com.client.mod.inventory;

import com.client.mod.JelloTheme;
import com.client.mod.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InventoryTweaksHandler {
    private static final Comparator<ItemStack> ITEM_COMPARATOR = (a, b) -> {
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) return 1;
        if (b.isEmpty()) return -1;

        // 1. Compare by registry ID
        String idA = BuiltInRegistries.ITEM.getKey(a.getItem()).toString();
        String idB = BuiltInRegistries.ITEM.getKey(b.getItem()).toString();
        int cmp = idA.compareTo(idB);
        if (cmp != 0) return cmp;

        // 2. Compare by count descending
        cmp = Integer.compare(b.getCount(), a.getCount());
        if (cmp != 0) return cmp;

        // 3. Compare by damage / durability
        return Integer.compare(a.getDamageValue(), b.getDamageValue());
    };

    private static class Action {
        final int fromSlot;
        final int toSlot;
        final int returnSlot;

        Action(int fromSlot, int toSlot, int returnSlot) {
            this.fromSlot = fromSlot;
            this.toSlot = toSlot;
            this.returnSlot = returnSlot;
        }

        void execute(int containerId, MultiPlayerGameMode gameMode, Player player) {
            gameMode.handleContainerInput(containerId, fromSlot, 0, ContainerInput.PICKUP, player);
            gameMode.handleContainerInput(containerId, toSlot, 0, ContainerInput.PICKUP, player);
            if (returnSlot != -1) {
                gameMode.handleContainerInput(containerId, returnSlot, 0, ContainerInput.PICKUP, player);
            }
        }
    }

    private static class SimSlot {
        final int menuIndex;
        ItemStack stack;

        SimSlot(int menuIndex, ItemStack stack) {
            this.menuIndex = menuIndex;
            this.stack = stack.copy();
        }
    }

    private static final List<Action> actionQueue = new ArrayList<>();
    private static int activeContainerId = -1;
    private static int tickCounter = 0;

    public static void reset() {
        actionQueue.clear();
        activeContainerId = -1;
        tickCounter = 0;
    }

    public static void tick() {
        if (actionQueue.isEmpty()) {
            return;
        }

        ModConfig config = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameMode == null || mc.player == null) {
            reset();
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null || menu.containerId != activeContainerId) {
            reset();
            return;
        }

        int delay = Math.max(0, config.invSortingDelay);
        if (delay == 0) {
            while (!actionQueue.isEmpty()) {
                Action act = actionQueue.remove(0);
                act.execute(activeContainerId, mc.gameMode, mc.player);
            }
        } else {
            tickCounter++;
            if (tickCounter >= delay) {
                tickCounter = 0;
                Action act = actionQueue.remove(0);
                act.execute(activeContainerId, mc.gameMode, mc.player);
            }
        }
    }

    public static void sort(AbstractContainerScreen<?> screen, Slot clickedSlot) {
        ModConfig config = ModConfig.getInstance();
        if (!config.invTweaksEnabled || !config.invSorting) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }

        if (mc.player.isCreative() && config.invDisableInCreative) {
            return;
        }

        if (screen instanceof CreativeModeInventoryScreen) {
            return;
        }

        AbstractContainerMenu menu = screen.getMenu();
        if (menu == null) {
            return;
        }

        List<Slot> allSlots = menu.slots;
        if (allSlots == null || allSlots.isEmpty()) {
            return;
        }

        // Determine target slots
        List<Slot> targetSlots = new ArrayList<>();
        boolean isExternalContainer = false;

        if (clickedSlot != null) {
            isExternalContainer = !(clickedSlot.container instanceof Inventory);
        } else {
            isExternalContainer = allSlots.stream().anyMatch(s -> !(s.container instanceof Inventory));
        }

        if (isExternalContainer) {
            for (Slot s : allSlots) {
                if (!(s.container instanceof Inventory)) {
                    targetSlots.add(s);
                }
            }
        } else {
            for (Slot s : allSlots) {
                if (s.container instanceof Inventory) {
                    int cs = s.getContainerSlot();
                    if (config.invProtectHotbar) {
                        if (cs >= 9 && cs <= 35) {
                            targetSlots.add(s);
                        }
                    } else {
                        if (cs >= 0 && cs <= 35) {
                            targetSlots.add(s);
                        }
                    }
                }
            }
        }

        if (targetSlots.isEmpty()) {
            return;
        }

        // If cursor has item, place it into an empty slot first
        if (!menu.getCarried().isEmpty()) {
            for (Slot s : targetSlots) {
                if (s.getItem().isEmpty()) {
                    mc.gameMode.handleContainerInput(menu.containerId, s.index, 0, ContainerInput.PICKUP, mc.player);
                    break;
                }
            }
            if (!menu.getCarried().isEmpty()) {
                return;
            }
        }

        // Build simulated slots
        List<SimSlot> simSlots = new ArrayList<>(targetSlots.size());
        for (Slot s : targetSlots) {
            simSlots.add(new SimSlot(s.index, s.getItem()));
        }

        List<Action> generatedActions = new ArrayList<>();

        // Phase 1: Stacking identical items
        for (int i = 0; i < simSlots.size(); i++) {
            SimSlot target = simSlots.get(i);
            if (target.stack.isEmpty() || !target.stack.isStackable() || target.stack.getCount() >= target.stack.getMaxStackSize()) {
                continue;
            }
            for (int j = i + 1; j < simSlots.size(); j++) {
                SimSlot source = simSlots.get(j);
                if (source.stack.isEmpty()) continue;
                if (ItemStack.isSameItemSameComponents(target.stack, source.stack)) {
                    int needed = target.stack.getMaxStackSize() - target.stack.getCount();
                    int transfer = Math.min(needed, source.stack.getCount());
                    target.stack.grow(transfer);
                    source.stack.shrink(transfer);

                    generatedActions.add(new Action(source.menuIndex, target.menuIndex, source.stack.isEmpty() ? -1 : source.menuIndex));

                    if (target.stack.getCount() >= target.stack.getMaxStackSize()) {
                        break;
                    }
                }
            }
        }

        // Phase 2: Selection sorting
        for (int i = 0; i < simSlots.size() - 1; i++) {
            int bestIndex = i;
            for (int j = i + 1; j < simSlots.size(); j++) {
                if (ITEM_COMPARATOR.compare(simSlots.get(j).stack, simSlots.get(bestIndex).stack) < 0) {
                    bestIndex = j;
                }
            }
            if (bestIndex != i) {
                SimSlot s1 = simSlots.get(i);
                SimSlot s2 = simSlots.get(bestIndex);

                ItemStack temp = s1.stack;
                s1.stack = s2.stack;
                s2.stack = temp;

                generatedActions.add(new Action(s2.menuIndex, s1.menuIndex, s2.menuIndex));
            }
        }

        if (generatedActions.isEmpty()) {
            return;
        }

        reset();
        activeContainerId = menu.containerId;
        actionQueue.addAll(generatedActions);

        tick();
    }

    public static boolean renderShulkerTooltip(GuiGraphicsExtractor graphics, Slot hoveredSlot, ItemStack carried, int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();
        if (!config.invShulkerPreview) {
            return false;
        }
        if (hoveredSlot == null || !hoveredSlot.hasItem()) {
            return false;
        }
        if (carried != null && !carried.isEmpty()) {
            return false;
        }

        ItemStack stack = hoveredSlot.getItem();
        boolean isShulker = stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof ShulkerBoxBlock;
        if (!isShulker) {
            return false;
        }

        ItemContainerContents contents = stack.get(DataComponents.CONTAINER);
        if (contents == null || !contents.nonEmptyItems().iterator().hasNext()) {
            return false;
        }

        NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
        contents.copyInto(items);

        int accentColor = 0xFF976797; // Default uncolored shulker box purple
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock shulker) {
            DyeColor dye = shulker.getColor();
            if (dye != null) {
                accentColor = 0xFF000000 | (dye.getTextureDiffuseColor() & 0xFFFFFF);
            }
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        int cols = 9;
        int rows = 3;
        int cellSize = 18;
        int pad = 6;
        int headerH = 18;

        int gridW = cols * cellSize;
        int gridH = rows * cellSize;
        int boxW = Math.max(gridW + pad * 2, font.width(stack.getHoverName()) + pad * 2 + 8);
        int boxH = headerH + gridH + pad + 2;

        int boxX = mouseX + 10;
        int boxY = mouseY - boxH - 6;
        if (boxX + boxW + 4 > screenW) {
            boxX = Math.max(4, mouseX - boxW - 10);
        }
        if (boxY < 4) {
            boxY = Math.min(screenH - boxH - 4, mouseY + 12);
        }
        boxX = Mth.clamp(boxX, 4, Math.max(4, screenW - boxW - 4));
        boxY = Mth.clamp(boxY, 4, Math.max(4, screenH - boxH - 4));

        graphics.nextStratum();

        // Outer border & dark Jello-styled panel
        JelloTheme.fillRounded(graphics, boxX - 1, boxY - 1, boxX + boxW + 1, boxY + boxH + 1, 5, accentColor);
        JelloTheme.fillRounded(graphics, boxX, boxY, boxX + boxW, boxY + boxH, 5, 0xF013151B);
        JelloTheme.fillRounded(graphics, boxX, boxY, boxX + boxW, boxY + headerH, 5, (0xCC << 24) | (accentColor & 0xFFFFFF));

        // Title
        graphics.text(font, stack.getHoverName(), boxX + pad, boxY + 5, 0xFFFFFFFF, true);

        // 9x3 Slot backgrounds
        int gridStartX = boxX + (boxW - gridW) / 2;
        int gridStartY = boxY + headerH + 2;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int sx = gridStartX + col * cellSize;
                int sy = gridStartY + row * cellSize;
                graphics.fill(sx, sy, sx + 17, sy + 17, 0x45FFFFFF);
                graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xCC0D0E12);
            }
        }

        // Render items and stack counts on top stratum
        graphics.nextStratum();
        for (int i = 0; i < 27; i++) {
            ItemStack item = items.get(i);
            if (!item.isEmpty()) {
                int col = i % cols;
                int row = i / cols;
                int ix = gridStartX + col * cellSize + 1;
                int iy = gridStartY + row * cellSize + 1;
                graphics.item(item, ix, iy);
                graphics.itemDecorations(font, item, ix, iy);
            }
        }

        return true;
    }
}
