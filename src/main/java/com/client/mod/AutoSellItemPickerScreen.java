package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Full-catalog item picker screen for Auto Sell.
 * Lists all registered Minecraft items with 16x16 icons, live search,
 * All Items / Selected filter, Clear All, and one-click selection.
 */
public class AutoSellItemPickerScreen extends Screen {

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

    private static List<ItemEntry> cachedAllItems = null;

    private final Screen parent;
    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;

    private EditBox searchBox;
    private String searchQuery = "";
    private FilterMode filterMode = FilterMode.ALL;
    private List<ItemEntry> filteredItems = List.of();

    private double scrollAmount = 0.0;
    private int contentTotalHeight = 0;
    private boolean draggingScrollbar = false;
    private int scrollbarDragOffsetY = 0;

    private static final int HEADER_H = 42;
    private static final int TOOLBAR_H = 34;
    private static final int PAD = 14;
    private static final int CELL_H = 26;
    private static final int CELL_GAP = 6;
    private static final int COLS = 3;

    public AutoSellItemPickerScreen(Screen parent) {
        super(Component.literal("Auto Sell Items"));
        this.parent = parent;
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
        this.windowW = Math.min(620, Math.max(440, (int) (this.width * 0.82f)));
        this.windowH = Math.min(420, Math.max(280, (int) (this.height * 0.82f)));
        this.windowX = (this.width - this.windowW) / 2;
        this.windowY = (this.height - this.windowH) / 2;

        int searchX = this.windowX + PAD;
        int searchY = this.windowY + HEADER_H + 6;
        int searchW = 220;

        String prev = this.searchQuery;
        this.searchBox = new EditBox(this.font, searchX + 24, searchY + 6, searchW - 30, 14, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search any item..."));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(40);
        this.searchBox.setValue(prev);
        this.searchBox.setResponder(text -> {
            this.searchQuery = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
            this.scrollAmount = 0.0;
            rebuildFilteredList();
        });
        this.addRenderableWidget(this.searchBox);

        rebuildFilteredList();
    }

    private void rebuildFilteredList() {
        ModConfig config = ModConfig.getInstance();
        List<ItemEntry> all = getOrBuildCatalog();
        List<ItemEntry> out = new ArrayList<>(all.size());
        for (ItemEntry entry : all) {
            if (this.filterMode == FilterMode.SELECTED && !config.isAutoSellTargetEnabled(entry.id())) {
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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, this.width, this.height, 0x8805070B);

        JelloTheme.drawGlowShadow(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, 0x66000000, 4);
        JelloTheme.fillRoundedCard(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, JelloTheme.WINDOW_BG, 0x26FFFFFF);

        ModConfig config = ModConfig.getInstance();
        int selectedCount = config.autoSellTargets != null ? config.autoSellTargets.size() : 0;
        graphics.text(this.font, Component.literal("§lAUTO SELL — ITEM PICKER"), this.windowX + PAD, this.windowY + 11, JelloTheme.TEXT, false);
        graphics.text(this.font, Component.literal(selectedCount + " items selected  |  Triggers /sell all when in inventory"),
                this.windowX + PAD, this.windowY + 24, JelloTheme.TEXT_MUTED, false);

        int doneW = 58;
        int doneH = 22;
        int doneX = this.windowX + this.windowW - PAD - doneW;
        int doneY = this.windowY + (HEADER_H - doneH) / 2;
        boolean hovDone = isInside(mouseX, mouseY, doneX, doneY, doneW, doneH);
        JelloTheme.fillRoundedCard(graphics, doneX, doneY, doneX + doneW, doneY + doneH, 5,
                hovDone ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT, JelloTheme.BORDER_ACTIVE_SOFT);
        graphics.centeredText(this.font, Component.literal("Done"), doneX + doneW / 2, doneY + 7, 0xFFFFFFFF);

        graphics.fill(this.windowX + 1, this.windowY + HEADER_H, this.windowX + this.windowW - 1, this.windowY + HEADER_H + 1, JelloTheme.BORDER_SUBTLE);

        int toolY = this.windowY + HEADER_H + 6;
        int searchX = this.windowX + PAD;
        int searchW = 220;
        int searchH = 22;
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        JelloTheme.fillRoundedCard(graphics, searchX, toolY, searchX + searchW, toolY + searchH, 5,
                JelloTheme.CONTAINER_BG, searchFocused ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        ModernIcons.drawSearchIcon(graphics, searchX + 8, toolY + 7, searchFocused ? JelloTheme.ACCENT_HOVER : JelloTheme.TEXT_MUTED);

        int filterX = searchX + searchW + 8;
        int filterW = 122;
        drawToolbarBtn(graphics, mouseX, mouseY, filterX, toolY, filterW, searchH, this.filterMode.label);

        int clearX = filterX + filterW + 8;
        int clearW = 76;
        drawToolbarBtn(graphics, mouseX, mouseY, clearX, toolY, clearW, searchH, "Clear All");

        int gridTop = this.windowY + HEADER_H + TOOLBAR_H + 4;
        int gridBottom = this.windowY + this.windowH - 10;
        int gridH = gridBottom - gridTop;
        int innerX = this.windowX + PAD;
        int innerW = this.windowW - PAD * 2 - 8;

        int rows = (this.filteredItems.size() + COLS - 1) / COLS;
        this.contentTotalHeight = Math.max(gridH, rows * (CELL_H + CELL_GAP));
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0, Math.max(0, this.contentTotalHeight - gridH));
        int scroll = (int) Math.round(this.scrollAmount);

        int cellW = (innerW - CELL_GAP * (COLS - 1)) / COLS;

        graphics.enableScissor(innerX, gridTop, innerX + innerW, gridBottom);

        if (this.filteredItems.isEmpty()) {
            graphics.centeredText(this.font, Component.literal("No matching items found."),
                    this.windowX + this.windowW / 2, gridTop + gridH / 2 - 6, JelloTheme.TEXT_MUTED);
        } else {
            for (int i = 0; i < this.filteredItems.size(); i++) {
                int row = i / COLS;
                int col = i % COLS;
                int cy = gridTop + row * (CELL_H + CELL_GAP) - scroll;
                if (cy + CELL_H < gridTop || cy > gridBottom) {
                    continue;
                }
                int cx = innerX + col * (cellW + CELL_GAP);
                ItemEntry entry = this.filteredItems.get(i);
                boolean selected = config.isAutoSellTargetEnabled(entry.id());
                boolean hovered = mouseY >= gridTop && mouseY <= gridBottom && isInside(mouseX, mouseY, cx, cy, cellW, CELL_H);

                int bg = selected
                        ? (hovered ? JelloTheme.CARD_ACTIVE_HOVER : JelloTheme.CARD_ACTIVE)
                        : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
                int border = selected
                        ? JelloTheme.ACCENT
                        : (hovered ? JelloTheme.BORDER_SUBTLE : JelloTheme.DIVIDER);

                JelloTheme.fillRoundedCard(graphics, cx, cy, cx + cellW, cy + CELL_H, 5, bg, border);
                graphics.item(entry.stack(), cx + 5, cy + (CELL_H - 16) / 2);

                int maxTextW = cellW - 28 - (selected ? 14 : 4);
                String label = fitText(entry.displayName(), maxTextW);
                graphics.text(this.font, Component.literal(label), cx + 25, cy + (CELL_H - 8) / 2,
                        selected ? JelloTheme.TEXT : JelloTheme.TEXT_MUTED, false);

                if (selected) {
                    ModernIcons.drawCheckIcon(graphics, cx + cellW - 14, cy + (CELL_H - 8) / 2, JelloTheme.ACCENT_HOVER);
                }
            }
        }

        graphics.disableScissor();

        if (this.contentTotalHeight > gridH) {
            int trackX = this.windowX + this.windowW - 8;
            graphics.fill(trackX, gridTop, trackX + 3, gridBottom, JelloTheme.SCROLLBAR_TRACK);
            int thumbH = Math.max(24, (int) ((float) gridH * gridH / this.contentTotalHeight));
            int maxScroll = Math.max(1, this.contentTotalHeight - gridH);
            int thumbY = gridTop + (int) ((gridH - thumbH) * (this.scrollAmount / maxScroll));
            boolean thumbHov = this.draggingScrollbar || (mouseX >= trackX - 4 && mouseX <= trackX + 7 && mouseY >= thumbY && mouseY <= thumbY + thumbH);
            graphics.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, thumbHov ? JelloTheme.SCROLLBAR_THUMB_HOVER : JelloTheme.SCROLLBAR_THUMB);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void drawToolbarBtn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, String label) {
        boolean hov = isInside(mouseX, mouseY, x, y, w, h);
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 5,
                hov ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CONTAINER_BG,
                hov ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        graphics.centeredText(this.font, Component.literal(label), x + w / 2, y + 7, JelloTheme.TEXT);
    }

    private String fitText(String text, int maxW) {
        if (text == null || maxW <= 8 || this.font.width(text) <= maxW) {
            return text != null ? text : "";
        }
        String trimmed = text;
        while (trimmed.length() > 2 && this.font.width(trimmed + "..") > maxW) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();

        if (!MouseButtonMapping.isLeftClick(button)) {
            return super.mouseClicked(event, wasHandled);
        }

        int doneW = 58;
        int doneH = 22;
        int doneX = this.windowX + this.windowW - PAD - doneW;
        int doneY = this.windowY + (HEADER_H - doneH) / 2;
        if (isInside(mouseX, mouseY, doneX, doneY, doneW, doneH)) {
            playClickSound();
            onClose();
            return true;
        }

        int toolY = this.windowY + HEADER_H + 6;
        int searchX = this.windowX + PAD;
        int searchW = 220;
        int searchH = 22;

        if (isInside(mouseX, mouseY, searchX, toolY, searchW, searchH) && this.searchBox != null) {
            this.setFocused(this.searchBox);
            this.searchBox.setFocused(true);
            return super.mouseClicked(event, wasHandled);
        } else if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }

        int filterX = searchX + searchW + 8;
        int filterW = 122;
        if (isInside(mouseX, mouseY, filterX, toolY, filterW, searchH)) {
            this.filterMode = this.filterMode.next();
            this.scrollAmount = 0.0;
            rebuildFilteredList();
            playClickSound();
            return true;
        }

        ModConfig config = ModConfig.getInstance();
        int clearX = filterX + filterW + 8;
        int clearW = 76;
        if (isInside(mouseX, mouseY, clearX, toolY, clearW, searchH)) {
            if (config.autoSellTargets != null) {
                config.autoSellTargets.clear();
            }
            config.save();
            rebuildFilteredList();
            playClickSound();
            return true;
        }

        int gridTop = this.windowY + HEADER_H + TOOLBAR_H + 4;
        int gridBottom = this.windowY + this.windowH - 10;
        int gridH = gridBottom - gridTop;

        if (this.contentTotalHeight > gridH) {
            int trackX = this.windowX + this.windowW - 8;
            if (mouseX >= trackX - 4 && mouseX <= trackX + 7 && mouseY >= gridTop && mouseY <= gridBottom) {
                int thumbH = Math.max(24, (int) ((float) gridH * gridH / this.contentTotalHeight));
                int maxScroll = Math.max(1, this.contentTotalHeight - gridH);
                int thumbY = gridTop + (int) ((gridH - thumbH) * (this.scrollAmount / maxScroll));
                if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                    this.draggingScrollbar = true;
                    this.scrollbarDragOffsetY = mouseY - thumbY;
                } else {
                    int targetThumbY = Mth.clamp(mouseY - thumbH / 2, gridTop, gridBottom - thumbH);
                    double ratio = (double) (targetThumbY - gridTop) / Math.max(1, gridH - thumbH);
                    this.scrollAmount = Mth.clamp(ratio * maxScroll, 0.0, maxScroll);
                    this.draggingScrollbar = true;
                    this.scrollbarDragOffsetY = thumbH / 2;
                }
                return true;
            }
        }

        int innerX = this.windowX + PAD;
        int innerW = this.windowW - PAD * 2 - 8;
        if (isInside(mouseX, mouseY, innerX, gridTop, innerW, gridH)) {
            int scroll = (int) Math.round(this.scrollAmount);
            int cellW = (innerW - CELL_GAP * (COLS - 1)) / COLS;
            for (int i = 0; i < this.filteredItems.size(); i++) {
                int row = i / COLS;
                int col = i % COLS;
                int cy = gridTop + row * (CELL_H + CELL_GAP) - scroll;
                if (cy + CELL_H < gridTop || cy > gridBottom) {
                    continue;
                }
                int cx = innerX + col * (cellW + CELL_GAP);
                if (isInside(mouseX, mouseY, cx, cy, cellW, CELL_H)) {
                    config.toggleAutoSellTarget(this.filteredItems.get(i).id());
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

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            int gridTop = this.windowY + HEADER_H + TOOLBAR_H + 4;
            int gridBottom = this.windowY + this.windowH - 10;
            int gridH = gridBottom - gridTop;
            int thumbH = Math.max(24, (int) ((float) gridH * gridH / this.contentTotalHeight));
            int maxScroll = Math.max(1, this.contentTotalHeight - gridH);
            int targetThumbY = Mth.clamp((int) event.y() - this.scrollbarDragOffsetY, gridTop, gridBottom - thumbH);
            double ratio = (double) (targetThumbY - gridTop) / Math.max(1, gridH - thumbH);
            this.scrollAmount = Mth.clamp(ratio * maxScroll, 0.0, maxScroll);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int gridTop = this.windowY + HEADER_H + TOOLBAR_H + 4;
        int gridBottom = this.windowY + this.windowH - 10;
        int gridH = gridBottom - gridTop;
        int maxScroll = Math.max(0, this.contentTotalHeight - gridH);
        this.scrollAmount = Mth.clamp(this.scrollAmount - verticalAmount * 28.0, 0.0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.4f, 1.0f);
        }
    }
}
