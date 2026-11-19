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
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Reusable full-catalog item picker screen for Auto Sell, Auto Drop, and similar item-list modules.
 */
public class CatalogItemPickerScreen extends AbstractCatalogPickerScreen {

    private enum FilterMode {
        ALL("Filter: All Items"),
        SELECTED("Filter: Selected");

        final String label;

        FilterMode(String label) {
            this.label = label;
        }

        FilterMode next() {
            return this == ALL ? SELECTED : ALL;
        }
    }

    private record ItemEntry(String id, String displayName, String searchKey, ItemStack stack) {
    }

    private static final int TOOLBAR_H = 34;
    private static List<ItemEntry> cachedAllItems = null;

    private final String headerTitle;
    private final String subtitleHint;
    private final Predicate<String> isSelected;
    private final Consumer<String> onToggle;
    private final Runnable onClearAll;
    private final IntSupplier selectedCountSupplier;

    private FilterMode filterMode = FilterMode.ALL;
    private List<ItemEntry> filteredItems = List.of();

    public CatalogItemPickerScreen(
            Screen parent,
            String headerTitle,
            String subtitleHint,
            Predicate<String> isSelected,
            Consumer<String> onToggle,
            Runnable onClearAll,
            IntSupplier selectedCountSupplier
    ) {
        super(parent, Component.literal(headerTitle));
        this.headerTitle = headerTitle;
        this.subtitleHint = subtitleHint;
        this.isSelected = isSelected;
        this.onToggle = onToggle;
        this.onClearAll = onClearAll;
        this.selectedCountSupplier = selectedCountSupplier;
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
            list.add(new ItemEntry(id, displayName, searchKey, stack));
        }
        list.sort(Comparator.comparing(ItemEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        cachedAllItems = list;
        return cachedAllItems;
    }

    @Override
    protected void init() {
        initWindowBounds(620, 440, 420, 280, 0.82f);
        int[] tb = computeToolbarLayout();
        initSearchWidget(tb[0], this.windowY + HEADER_H + 6, tb[1], "Search any item...");
        rebuildFilteredList();
    }

    @Override
    protected void rebuildFilteredList() {
        List<ItemEntry> all = getOrBuildCatalog();
        List<ItemEntry> out = new ArrayList<>(all.size());
        for (ItemEntry entry : all) {
            if (this.filterMode == FilterMode.SELECTED && !this.isSelected.test(entry.id())) {
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
        int selectedCount = this.selectedCountSupplier.getAsInt();
        renderWindowFrame(graphics, mouseX, mouseY,
                this.headerTitle,
                selectedCount + " items selected  |  " + this.subtitleHint);

        int[] tb = computeToolbarLayout();
        int toolY = this.windowY + HEADER_H + 6;
        renderSearchFieldBox(graphics, tb[0], toolY, tb[1]);
        drawToolbarBtn(graphics, mouseX, mouseY, tb[2], toolY, tb[3], TOOLBAR_BTN_H, this.filterMode.label);
        drawToolbarBtn(graphics, mouseX, mouseY, tb[4], toolY, tb[5], TOOLBAR_BTN_H, "Clear All");

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
                boolean selected = this.isSelected.test(entry.id());
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
        int gap = 8;
        int clearW = Math.max(62, Math.min(78, Math.round(innerW * 0.16f)));
        int filterW = Math.max(98, Math.min(126, Math.round(innerW * 0.26f)));
        int searchW = Math.max(140, innerW - gap * 2 - filterW - clearW);
        int searchX = this.windowX + PAD;
        int filterX = searchX + searchW + gap;
        int clearX = filterX + filterW + gap;
        return new int[]{searchX, searchW, filterX, filterW, clearX, clearW};
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

        if (isInside(mouseX, mouseY, tb[4], toolY, tb[5], TOOLBAR_BTN_H)) {
            this.onClearAll.run();
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
        if (isInside(mouseX, mouseY, innerX, top, innerW, bottom - top)) {
            int scroll = (int) Math.round(this.scrollAmount);
            int cellW = gridCellW(innerW);
            for (int i = 0; i < this.filteredItems.size(); i++) {
                int row = i / COLS;
                int col = i % COLS;
                int cy = top + row * (CELL_H + CELL_GAP) - scroll;
                if (cy + CELL_H < top || cy > bottom) {
                    continue;
                }
                int cx = innerX + col * (cellW + CELL_GAP);
                if (isInside(mouseX, mouseY, cx, cy, cellW, CELL_H)) {
                    this.onToggle.accept(this.filteredItems.get(i).id());
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
