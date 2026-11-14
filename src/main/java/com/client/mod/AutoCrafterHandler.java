package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.util.context.ContextMap;

import java.util.List;
import java.util.Set;

public class AutoCrafterHandler {
    private static int cooldown = 0;
    private static boolean craftedSomethingThisOpen = false;

    public record CraftTarget(String id, String label, Set<String> itemIds) {}

    public static final List<CraftTarget> TARGETS = List.of(
            new CraftTarget("iron_block", "Iron", Set.of("iron_block")),
            new CraftTarget("gold_block", "Gold", Set.of("gold_block")),
            new CraftTarget("diamond_block", "Diamond", Set.of("diamond_block")),
            new CraftTarget("emerald_block", "Emerald", Set.of("emerald_block")),
            new CraftTarget("redstone_block", "Redstone", Set.of("redstone_block")),
            new CraftTarget("lapis_block", "Lapis", Set.of("lapis_block")),
            new CraftTarget("coal_block", "Coal", Set.of("coal_block")),
            new CraftTarget("copper_block", "Copper", Set.of("copper_block")),
            new CraftTarget("netherite_block", "Netherite", Set.of("netherite_block")),
            new CraftTarget("raw_blocks", "Raw Ores", Set.of("raw_iron_block", "raw_gold_block", "raw_copper_block")),
            new CraftTarget("slime_block", "Slime", Set.of("slime_block")),
            new CraftTarget("honey_block", "Honey", Set.of("honey_block")),
            new CraftTarget("hay_block", "Hay", Set.of("hay_block")),
            new CraftTarget("bone_block", "Bone", Set.of("bone_block")),
            new CraftTarget("glowstone", "Glowstone", Set.of("glowstone", "quartz_block", "amethyst_block")),
            new CraftTarget("nuggets", "Nuggets", Set.of("iron_ingot", "gold_ingot")),
            new CraftTarget("decraft", "Uncraft", Set.of("iron_ingot", "gold_ingot", "diamond", "emerald", "lapis_lazuli", "redstone", "coal", "copper_ingot", "netherite_ingot", "slime_ball", "bone_meal", "wheat")),
            new CraftTarget("food_paper", "Food/Paper", Set.of("bread", "sugar", "paper"))
    );

    public static void reset() {
        cooldown = 1;
        craftedSomethingThisOpen = false;
    }

    public static void tick(AbstractContainerMenu menu) {
        ModConfig config = ModConfig.getInstance();
        if (!config.autoCrafterEnabled) return;
        if (!(menu instanceof AbstractCraftingMenu craftingMenu)) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.level == null) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        // 1. If result slot has a targeted item, shift-click it to inventory
        if (craftingMenu.getSlot(0).hasItem()) {
            ItemStack before = craftingMenu.getSlot(0).getItem().copy();
            String slot0Id = BuiltInRegistries.ITEM.getKey(before.getItem()).getPath();
            if (isExplicitTargetEnabled(slot0Id, config)) {
                mc.gameMode.handleContainerInput(craftingMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, player);

                // Check if slot 0 item was not taken (inventory is full)
                if (craftingMenu.getSlot(0).hasItem() && ItemStack.isSameItemSameComponents(before, craftingMenu.getSlot(0).getItem())) {
                    // 10 ticklik bekleme kaldirildi, envanter doluysa 1 tick sonra tekrar kontrol edilir
                    cooldown = 1;
                    return;
                }

                craftedSomethingThisOpen = true;

                if (config.autoCrafterDelay > 0) {
                    cooldown = config.autoCrafterDelay;
                    return;
                }
            }
        }

        // 2. Prepare stacked contents so recipe craftability is 100% up-to-date synchronously
        ClientRecipeBook recipeBook = player.getRecipeBook();
        if (recipeBook == null) return;

        StackedItemContents stackedContents = new StackedItemContents();
        player.getInventory().fillStackedContents(stackedContents);
        int gridSlots = craftingMenu.getGridWidth() * craftingMenu.getGridHeight();
        for (int i = 1; i <= gridSlots && i < craftingMenu.slots.size(); i++) {
            Slot slot = craftingMenu.getSlot(i);
            if (slot.hasItem()) {
                stackedContents.accountStack(slot.getItem());
            }
        }

        ContextMap context = SlotDisplayContext.fromLevel(mc.level);

        boolean foundAndPlaced = false;
        for (RecipeCollection collection : recipeBook.getCollections()) {
            collection.selectRecipes(stackedContents, d -> true);
            if (!collection.hasCraftable()) continue;

            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!collection.isCraftable(entry.id()) && !entry.canCraft(stackedContents)) continue;

                // Validate grid size requirement
                if (entry.craftingRequirements().isPresent()) {
                    int reqCount = entry.craftingRequirements().get().size();
                    if (gridSlots < reqCount) {
                        continue;
                    }
                }

                ItemStack resultStack = entry.display().result().resolveForFirstStack(context);
                if (resultStack.isEmpty()) continue;

                String itemId = BuiltInRegistries.ITEM.getKey(resultStack.getItem()).getPath();
                if (!matchesTarget(entry, itemId, config)) continue;

                // Place recipe with shift-click (craft full stack)
                mc.gameMode.handlePlaceRecipe(craftingMenu.containerId, entry.id(), true);

                // If result slot is populated immediately on client, take it
                if (craftingMenu.getSlot(0).hasItem()) {
                    mc.gameMode.handleContainerInput(craftingMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, player);
                }

                craftedSomethingThisOpen = true;
                foundAndPlaced = true;
                cooldown = config.autoCrafterDelay;
                break;
            }

            if (foundAndPlaced) break;
        }

        // If no more craftable items were found and closeWhenDone is true
        if (!foundAndPlaced && craftedSomethingThisOpen && config.autoCrafterCloseWhenDone) {
            // Sonuc slotunda esya kalmissa veya envanter doluysa asla kapatma
            if (craftingMenu.getSlot(0).hasItem() || player.getInventory().getFreeSlot() == -1) {
                return;
            }
            player.closeContainer();
            reset();
        }
    }

    private static boolean matchesTarget(RecipeDisplayEntry entry, String resultId, ModConfig config) {
        if (!isExplicitTargetEnabled(resultId, config)) {
            return false;
        }
        return !wouldLoopSingleIngredient(entry, config);
    }

    static boolean isExplicitTargetEnabled(String resultId, ModConfig config) {
        if (resultId == null || resultId.isEmpty() || config == null) {
            return false;
        }
        return config.isAutoCraftTargetEnabled(resultId);
    }

    private static boolean wouldLoopSingleIngredient(RecipeDisplayEntry entry, ModConfig config) {
        return entry.craftingRequirements().map(reqs -> {
            if (reqs.size() != 1) {
                return false;
            }
            return reqs.get(0).items().anyMatch(h -> {
                String regName = h.getRegisteredName();
                int colon = regName.indexOf(':');
                String path = colon >= 0 ? regName.substring(colon + 1) : regName;
                return config.isAutoCraftTargetEnabled(path);
            });
        }).orElse(false);
    }
}
