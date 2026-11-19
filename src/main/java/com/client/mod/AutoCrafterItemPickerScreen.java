package com.client.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Meteor-style item picker screen for Auto Crafter.
 * Lists all craftable Minecraft items with 16x16 item icons, live search,
 * filter modes (Craftable / Selected / All Items), and one-click selection.
 */
public class AutoCrafterItemPickerScreen extends AbstractCatalogPickerScreen {

    private enum FilterMode {
        CRAFTABLE("Filter: Craftable"),
        SELECTED("Filter: Selected"),
        ALL("Filter: All Items");

        final String label;

        FilterMode(String label) {
            this.label = label;
        }

        FilterMode next() {
            return switch (this) {
                case CRAFTABLE -> SELECTED;
                case SELECTED -> ALL;
                case ALL -> CRAFTABLE;
            };
        }
    }

    private record ItemEntry(String id, String displayName, String searchKey, ItemStack stack, boolean craftable) {
    }

    private static final int TOOLBAR_H = 34;
    private static List<ItemEntry> cachedAllItems = null;

    private FilterMode filterMode = FilterMode.CRAFTABLE;
    private List<ItemEntry> filteredItems = List.of();

    public AutoCrafterItemPickerScreen(Screen parent) {
        super(parent, Component.literal("Auto Crafter Items"));
    }

    private static List<ItemEntry> getOrBuildCatalog() {
        if (cachedAllItems != null && !cachedAllItems.isEmpty()) {
            return cachedAllItems;
        }
        List<ItemEntry> list = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null || item == Items.AIR) {
                continue;
            }
            Identifier key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null) {
                continue;
            }
            String id = key.getPath();
            ItemStack stack = new ItemStack(item);
            String displayName = stack.getHoverName().getString();
            String searchKey = (displayName + " " + id.replace('_', ' ') + " " + id).toLowerCase(Locale.ROOT);
            boolean craftable = isLikelyCraftableItem(id);
            list.add(new ItemEntry(id, displayName, searchKey, stack, craftable));
        }
        list.sort(Comparator.comparing(ItemEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        cachedAllItems = list;
        return cachedAllItems;
    }

    private static boolean isLikelyCraftableItem(String id) {
        if (id.endsWith("_spawn_egg") || id.endsWith("_ore") || id.startsWith("infested_")
                || id.startsWith("command_block") || id.startsWith("structure_")
                || (id.startsWith("music_disc_") && !id.equals("music_disc_5"))) {
            return false;
        }
        return switch (id) {
            case "air", "barrier", "jigsaw", "light", "debug_stick", "knowledge_book",
                 "bedrock", "end_portal_frame", "spawner", "trial_spawner", "vault",
                 "budding_amethyst", "reinforced_deepslate", "chorus_plant", "farmland",
                 "dirt_path", "raw_iron", "raw_gold", "raw_copper", "potion",
                 "splash_potion", "lingering_potion", "enchanted_book", "filled_map",
                 "totem_of_undying", "elytra", "trident", "nether_star", "dragon_egg",
                 "dragon_breath", "heart_of_the_sea", "nautilus_shell", "echo_shard",
                 "disc_fragment_5", "bell", "saddle", "name_tag", "goat_horn",
                 "ominous_bottle", "trial_key", "ominous_trial_key", "heavy_core",
                 "breeze_rod", "blaze_rod", "ghast_tear", "ender_pearl", "shulker_shell",
                 "cobweb", "sponge", "wet_sponge", "tall_grass", "large_fern", "fern",
                 "short_grass", "dead_bush", "seagrass", "kelp", "lily_pad", "vine",
                 "glow_lichen", "hanging_roots", "spore_blossom", "big_dripleaf",
                 "small_dripleaf", "cactus", "sugar_cane", "bamboo", "cocoa_beans",
                 "sweet_berries", "glow_berries", "chorus_fruit", "popped_chorus_fruit",
                 "apple", "potato", "baked_potato", "poisonous_potato", "carrot",
                 "beetroot", "melon_slice", "rotten_flesh", "spider_eye", "string",
                 "feather", "gunpowder", "flint", "leather", "rabbit_hide", "rabbit_foot",
                 "ink_sac", "glow_ink_sac", "turtle_scute", "armadillo_scute",
                 "phantom_membrane", "prismarine_shard", "prismarine_crystals",
                 "clay_ball", "snowball", "egg", "water_bucket", "lava_bucket",
                 "milk_bucket", "powder_snow_bucket", "porkchop", "cooked_porkchop",
                 "beef", "cooked_beef", "chicken", "cooked_chicken", "mutton",
                 "cooked_mutton", "rabbit", "cooked_rabbit", "cod", "cooked_cod",
                 "salmon", "cooked_salmon", "tropical_fish", "pufferfish",
                 "experience_bottle", "dragon_head", "player_head", "zombie_head",
                 "skeleton_skull", "wither_skeleton_skull", "creeper_head", "piglin_head" -> false;
            default -> true;
        };
    }

    @Override
    protected void init() {
        initWindowBounds(620, 440, 420, 280, 0.82f);
        int[] tb = computeToolbarLayout();
        initSearchWidget(tb[0], this.windowY + HEADER_H + 6, tb[1], "Search items...");
        rebuildFilteredList();
    }

    @Override
    protected void rebuildFilteredList() {
        ModConfig config = ModConfig.getInstance();
        List<ItemEntry> all = getOrBuildCatalog();
        List<ItemEntry> out = new ArrayList<>(all.size());
        for (ItemEntry entry : all) {
            if (this.filterMode == FilterMode.CRAFTABLE && !entry.craftable() && !config.isAutoCraftTargetEnabled(entry.id())) {
                continue;
            }
            if (this.filterMode == FilterMode.SELECTED && !config.isAutoCraftTargetEnabled(entry.id())) {
                continue;
            }
            if (!this.searchQuery.isEmpty() && !entry.searchKey().contains(this.searchQuery)) {
                continue;
            }
            out.add(entry);
        }
        this.filteredItems = out;
    }

    @Override
    protected int gridTop() {
        return this.windowY + HEADER_H + TOOLBAR_H + 4;
    }

    @Override
    protected int gridBottom() {
        return this.windowY + this.windowH - 10;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();
        int selectedCount = config.autoCrafterTargets != null ? config.autoCrafterTargets.size() : 0;
        renderWindowFrame(graphics, mouseX, mouseY,
                "AUTO CRAFTER — ITEM PICKER",
                selectedCount + " items selected  |  Click any item to toggle auto-crafting");

        int[] tb = computeToolbarLayout();
        int toolY = this.windowY + HEADER_H + 6;
        renderSearchFieldBox(graphics, tb[0], toolY, tb[1]);
        drawToolbarBtn(graphics, mouseX, mouseY, tb[2], toolY, tb[3], TOOLBAR_BTN_H, this.filterMode.label);
        drawToolbarBtn(graphics, mouseX, mouseY, tb[4], toolY, tb[5], TOOLBAR_BTN_H, "Default Ores");
        drawToolbarBtn(graphics, mouseX, mouseY, tb[6], toolY, tb[7], TOOLBAR_BTN_H, "Clear All");

        int top = gridTop();
        int bottom = gridBottom();
        int gridH = bottom - top;
        int innerX = gridInnerX();
        int innerW = gridInnerW();
        updateContentScrollBounds(this.filteredItems.size(), gridH);
        int scroll = (int) Math.round(this.scrollAmount);
        int cellW = gridCellW(innerW);

        graphics.enableScissor(innerX, top, innerX + innerW, bottom);
        if (this.filteredItems.isEmpty()) {
            graphics.centeredText(this.font, Component.literal("No matching items found."),
                    this.windowX + this.windowW / 2, top + gridH / 2 - 6, JelloTheme.TEXT_MUTED);
        } else {
            for (int i = 0; i < this.filteredItems.size(); i++) {
                int row = i / COLS;
                int col = i % COLS;
                int cy = top + row * (CELL_H + CELL_GAP) - scroll;
                if (cy + CELL_H < top || cy > bottom) {
                    continue;
                }
                int cx = innerX + col * (cellW + CELL_GAP);
                ItemEntry entry = this.filteredItems.get(i);
                boolean selected = config.isAutoCraftTargetEnabled(entry.id());
                boolean hovered = mouseY >= top && mouseY <= bottom && isInside(mouseX, mouseY, cx, cy, cellW, CELL_H);
                renderPickerCell(graphics, cx, cy, cellW, entry.stack(), entry.displayName(),
                        selected, false, hovered, selected ? 14 : 4, 14);
            }
        }
        graphics.disableScissor();

        renderScrollbar(graphics, mouseX, mouseY, top, bottom, gridH);
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private int[] computeToolbarLayout() {
        int innerW = Math.max(200, this.windowW - PAD * 2);
        int gap = 6;
        int clearW = Math.max(58, Math.min(72, Math.round(innerW * 0.14f)));
        int oresW = Math.max(78, Math.min(92, Math.round(innerW * 0.19f)));
        int filterW = Math.max(96, Math.min(122, Math.round(innerW * 0.25f)));
        int searchW = Math.max(120, innerW - gap * 3 - filterW - oresW - clearW);
        int searchX = this.windowX + PAD;
        int filterX = searchX + searchW + gap;
        int oresX = filterX + filterW + gap;
        int clearX = oresX + oresW + gap;
        return new int[]{searchX, searchW, filterX, filterW, oresX, oresW, clearX, clearW};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        if (!MouseButtonMapping.isLeftClick(event.button())) {
            return super.mouseClicked(event, wasHandled);
        }

        if (handleDoneButtonClick(mouseX, mouseY)) {
            return true;
        }

        int[] tb = computeToolbarLayout();
        int toolY = this.windowY + HEADER_H + 6;
        if (handleSearchFocusClick(mouseX, mouseY, tb[0], toolY, tb[1], event, wasHandled)) {
            return true;
        }

        if (isInside(mouseX, mouseY, tb[2], toolY, tb[3], TOOLBAR_BTN_H)) {
            this.filterMode = this.filterMode.next();
            this.scrollAmount = 0.0;
            rebuildFilteredList();
            playClickSound();
            return true;
        }

        ModConfig config = ModConfig.getInstance();
        if (isInside(mouseX, mouseY, tb[4], toolY, tb[5], TOOLBAR_BTN_H)) {
            config.resetDefaultAutoCraftTargets();
            config.save();
            rebuildFilteredList();
            playClickSound();
            return true;
        }

        if (isInside(mouseX, mouseY, tb[6], toolY, tb[7], TOOLBAR_BTN_H)) {
            if (config.autoCrafterTargets != null) {
                config.autoCrafterTargets.clear();
            }
            config.save();
            rebuildFilteredList();
            playClickSound();
            return true;
        }

        if (handleScrollbarClick(mouseX, mouseY)) {
            return true;
        }

        int top = gridTop();
        int bottom = gridBottom();
        int innerX = gridInnerX();
        int innerW = gridInnerW();
        int cellW = gridCellW(innerW);
        int scroll = (int) Math.round(this.scrollAmount);

        if (isInside(mouseX, mouseY, innerX, top, innerW, bottom - top)) {
            for (int i = 0; i < this.filteredItems.size(); i++) {
                int row = i / COLS;
                int col = i % COLS;
                int cy = top + row * (CELL_H + CELL_GAP) - scroll;
                if (cy + CELL_H < top || cy > bottom) {
                    continue;
                }
                int cx = innerX + col * (cellW + CELL_GAP);
                if (isInside(mouseX, mouseY, cx, cy, cellW, CELL_H)) {
                    ItemEntry entry = this.filteredItems.get(i);
                    config.toggleAutoCraftTarget(entry.id());
                    config.save();
                    if (this.filterMode == FilterMode.SELECTED) {
                        rebuildFilteredList();
                    }
                    playClickSound();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, wasHandled);
    }
}
