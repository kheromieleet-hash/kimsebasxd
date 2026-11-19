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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntSupplier;
import java.util.function.Predicate;

/**
 * Block-only searchable picker for Xray and Block ESP with Ores/Storage/Nether presets
 * and optional per-block Color/Shape/Tracer customization in Block ESP mode.
 */
public class CatalogBlockPickerScreen extends AbstractCatalogPickerScreen {

    private enum FilterMode {
        ALL("Filter: All Blocks"),
        SELECTED("Filter: Selected");

        final String label;

        FilterMode(String label) {
            this.label = label;
        }

        FilterMode next() {
            return this == ALL ? SELECTED : ALL;
        }
    }

    private record BlockEntry(String id, String displayName, String searchKey, ItemStack iconStack) {
    }

    private static final int[] SWATCH_COLORS = {
            0x00D4FF, // Cyan
            0x22C55E, // Green
            0xEF4444, // Red
            0xF59E0B, // Amber
            0xA855F7, // Violet
            0xEC4899, // Pink
            0x3B82F6, // Blue
            0xFFFFFF  // White
    };

    private static final int TOOLBAR_H = 30;
    private static final int PRESET_BAR_H = 26;
    private static final int STYLE_BAR_H = 54;

    private static List<BlockEntry> cachedAllBlocks = null;

    private final String headerTitle;
    private final String subtitleHint;
    private final boolean blockEspMode;
    private final Predicate<String> isSelected;
    private final Consumer<String> onToggle;
    private final Consumer<Collection<String>> onApplyPreset;
    private final Runnable onResetDefaults;
    private final Runnable onClearAll;
    private final IntSupplier selectedCountSupplier;

    private FilterMode filterMode = FilterMode.ALL;
    private List<BlockEntry> filteredBlocks = List.of();
    private String inspectedBlockId = null;

    public static CatalogBlockPickerScreen forXray(Screen parent, ModConfig cfg) {
        return new CatalogBlockPickerScreen(
                parent,
                "XRAY — BLOCK PICKER",
                "Click any block to toggle visibility in Xray",
                false,
                cfg::isXrayBlockEnabled,
                id -> {
                    cfg.toggleXrayBlock(id);
                    cfg.save();
                    XrayHandler.refreshChunksIfActive();
                },
                preset -> {
                    if (cfg.xrayBlocks == null) {
                        cfg.xrayBlocks = new LinkedHashSet<>();
                    }
                    cfg.xrayBlocks.addAll(preset);
                    cfg.save();
                    XrayHandler.refreshChunksIfActive();
                },
                () -> {
                    cfg.xrayBlocks = XrayHandler.createDefaultBlocks();
                    cfg.save();
                    XrayHandler.refreshChunksIfActive();
                },
                () -> {
                    if (cfg.xrayBlocks != null) {
                        cfg.xrayBlocks.clear();
                    }
                    cfg.save();
                    XrayHandler.refreshChunksIfActive();
                },
                () -> cfg.xrayBlocks != null ? cfg.xrayBlocks.size() : 0
        );
    }

    public static CatalogBlockPickerScreen forBlockEsp(Screen parent, ModConfig cfg) {
        return new CatalogBlockPickerScreen(
                parent,
                "BLOCK ESP — BLOCK PICKER",
                "Left click: toggle | Right click: edit color, shape & tracer",
                true,
                cfg::isBlockEspTargetEnabled,
                id -> {
                    cfg.toggleBlockEspTarget(id);
                    cfg.save();
                    BlockEspHandler.clearCache();
                },
                preset -> {
                    if (cfg.blockEspBlocks == null) {
                        cfg.blockEspBlocks = new LinkedHashSet<>();
                    }
                    cfg.blockEspBlocks.addAll(preset);
                    cfg.save();
                    BlockEspHandler.clearCache();
                },
                () -> {
                    cfg.blockEspBlocks = XrayHandler.createDefaultBlockEspBlocks();
                    cfg.blockEspPerBlockStyles.clear();
                    cfg.save();
                    BlockEspHandler.clearCache();
                },
                () -> {
                    if (cfg.blockEspBlocks != null) {
                        cfg.blockEspBlocks.clear();
                    }
                    cfg.save();
                    BlockEspHandler.clearCache();
                },
                () -> cfg.blockEspBlocks != null ? cfg.blockEspBlocks.size() : 0
        );
    }

    private CatalogBlockPickerScreen(
            Screen parent,
            String headerTitle,
            String subtitleHint,
            boolean blockEspMode,
            Predicate<String> isSelected,
            Consumer<String> onToggle,
            Consumer<Collection<String>> onApplyPreset,
            Runnable onResetDefaults,
            Runnable onClearAll,
            IntSupplier selectedCountSupplier
    ) {
        super(parent, Component.literal(headerTitle));
        this.headerTitle = headerTitle;
        this.subtitleHint = subtitleHint;
        this.blockEspMode = blockEspMode;
        this.isSelected = isSelected;
        this.onToggle = onToggle;
        this.onApplyPreset = onApplyPreset;
        this.onResetDefaults = onResetDefaults;
        this.onClearAll = onClearAll;
        this.selectedCountSupplier = selectedCountSupplier;
    }

    private static List<BlockEntry> getOrBuildCatalog() {
        if (cachedAllBlocks != null && !cachedAllBlocks.isEmpty()) {
            return cachedAllBlocks;
        }
        List<BlockEntry> list = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (block == null || block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR) {
                continue;
            }
            Identifier key = BuiltInRegistries.BLOCK.getKey(block);
            if (key == null) {
                continue;
            }
            String fullId = key.toString().toLowerCase(Locale.ROOT);
            String path = key.getPath();
            ItemStack stack = resolveIconStack(block);
            String displayName = block.getName().getString();
            if (displayName == null || displayName.isBlank()) {
                displayName = path;
            }
            String searchKey = (displayName + " " + path.replace('_', ' ') + " " + fullId).toLowerCase(Locale.ROOT);
            list.add(new BlockEntry(fullId, displayName, searchKey, stack));
        }
        list.sort(Comparator.comparing(BlockEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        cachedAllBlocks = list;
        return cachedAllBlocks;
    }

    private static ItemStack resolveIconStack(Block block) {
        Item item = block.asItem();
        if (item != Items.AIR) {
            return new ItemStack(item);
        }
        if (block == Blocks.WATER) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        if (block == Blocks.LAVA) {
            return new ItemStack(Items.LAVA_BUCKET);
        }
        if (block == Blocks.NETHER_PORTAL) {
            return new ItemStack(Items.OBSIDIAN);
        }
        if (block == Blocks.END_PORTAL || block == Blocks.END_GATEWAY) {
            return new ItemStack(Items.ENDER_EYE);
        }
        return ItemStack.EMPTY;
    }

    @Override
    protected void init() {
        initWindowBounds(640, 460, 440, 300, 0.84f);
        int[] tb = computeToolbarLayout();
        initSearchWidget(tb[0], this.windowY + HEADER_H + 6, tb[1], "Search blocks...");
        rebuildFilteredList();
    }

    @Override
    protected void rebuildFilteredList() {
        List<BlockEntry> all = getOrBuildCatalog();
        List<BlockEntry> out = new ArrayList<>(all.size());
        for (BlockEntry entry : all) {
            if (this.filterMode == FilterMode.SELECTED && !this.isSelected.test(entry.id())) {
                continue;
            }
            if (!this.searchQuery.isEmpty() && !entry.searchKey().contains(this.searchQuery)) {
                continue;
            }
            out.add(entry);
        }
        this.filteredBlocks = out;
    }

    @Override
    protected int gridTop() {
        return this.windowY + HEADER_H + TOOLBAR_H + PRESET_BAR_H + 6;
    }

    @Override
    protected int gridBottom() {
        boolean showStyleBar = this.blockEspMode && this.inspectedBlockId != null;
        return this.windowY + this.windowH - 10 - (showStyleBar ? STYLE_BAR_H + 6 : 0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int selectedCount = this.selectedCountSupplier.getAsInt();
        renderWindowFrame(graphics, mouseX, mouseY,
                this.headerTitle,
                selectedCount + " blocks selected  |  " + this.subtitleHint);

        renderToolbar(graphics, mouseX, mouseY);
        renderPresetBar(graphics, mouseX, mouseY);
        renderGrid(graphics, mouseX, mouseY);

        if (this.blockEspMode && this.inspectedBlockId != null) {
            renderStyleBar(graphics, mouseX, mouseY);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    private void renderToolbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int[] tb = computeToolbarLayout();
        int toolY = this.windowY + HEADER_H + 6;
        renderSearchFieldBox(graphics, tb[0], toolY, tb[1]);
        drawToolbarBtn(graphics, mouseX, mouseY, tb[2], toolY, tb[3], TOOLBAR_BTN_H, this.filterMode.label);
        drawToolbarBtn(graphics, mouseX, mouseY, tb[4], toolY, tb[5], TOOLBAR_BTN_H, "Reset Defaults");
        drawToolbarBtn(graphics, mouseX, mouseY, tb[6], toolY, tb[7], TOOLBAR_BTN_H, "Clear All");
    }

    private void renderPresetBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int y = this.windowY + HEADER_H + TOOLBAR_H + 4;
        int x = this.windowX + PAD;
        graphics.text(this.font, Component.literal("Presets:"), x, y + 6, JelloTheme.TEXT_MUTED, false);
        int bx = x + 52;
        drawToolbarBtn(graphics, mouseX, mouseY, bx, y, 76, 20, "+ Ores");
        bx += 82;
        drawToolbarBtn(graphics, mouseX, mouseY, bx, y, 86, 20, "+ Storage");
        bx += 92;
        drawToolbarBtn(graphics, mouseX, mouseY, bx, y, 84, 20, "+ Nether");
    }

    private void renderGrid(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int top = gridTop();
        int bottom = gridBottom();
        int gridH = Math.max(40, bottom - top);
        int innerX = gridInnerX();
        int innerW = gridInnerW();
        updateContentScrollBounds(this.filteredBlocks.size(), gridH);
        int scroll = (int) Math.round(this.scrollAmount);
        int cellW = gridCellW(innerW);

        graphics.enableScissor(innerX, top, innerX + innerW, bottom);
        ModConfig cfg = ModConfig.getInstance();

        if (this.filteredBlocks.isEmpty()) {
            graphics.centeredText(this.font, Component.literal("No matching blocks found."),
                    this.windowX + this.windowW / 2, top + gridH / 2 - 6, JelloTheme.TEXT_MUTED);
        } else {
            for (int i = 0; i < this.filteredBlocks.size(); i++) {
                int row = i / COLS;
                int col = i % COLS;
                int cy = top + row * (CELL_H + CELL_GAP) - scroll;
                if (cy + CELL_H < top || cy > bottom) {
                    continue;
                }
                int cx = innerX + col * (cellW + CELL_GAP);
                BlockEntry entry = this.filteredBlocks.get(i);
                boolean selected = this.isSelected.test(entry.id());
                boolean inspecting = this.blockEspMode && entry.id().equals(this.inspectedBlockId);
                boolean hovered = mouseY >= top && mouseY <= bottom && isInside(mouseX, mouseY, cx, cy, cellW, CELL_H);
                int trailingReserve = this.blockEspMode && selected ? 26 : (selected ? 14 : 4);
                renderPickerCell(graphics, cx, cy, cellW, entry.iconStack(), entry.displayName(),
                        selected, inspecting, hovered, trailingReserve, 12);

                if (selected && this.blockEspMode) {
                    BlockEspHandler.BlockEspStyle style = BlockEspHandler.resolveEffectiveStyle(entry.id(), cfg);
                    int swatch = style.lineColor | 0xFF000000;
                    graphics.fill(cx + cellW - 24, cy + 8, cx + cellW - 14, cy + 18, swatch);
                }
            }
        }

        graphics.disableScissor();
        renderScrollbar(graphics, mouseX, mouseY, top, bottom, gridH);
    }

    private void renderStyleBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ModConfig cfg = ModConfig.getInstance();
        int barX = this.windowX + PAD;
        int barY = gridBottom() + 6;
        int barW = this.windowW - PAD * 2;
        JelloTheme.fillRoundedCard(graphics, barX, barY, barX + barW, barY + STYLE_BAR_H, 6,
                JelloTheme.CONTAINER_BG, JelloTheme.ACCENT);

        boolean hasCustom = cfg.blockEspPerBlockStyles != null && cfg.blockEspPerBlockStyles.containsKey(this.inspectedBlockId);
        BlockEspHandler.BlockEspStyle style = BlockEspHandler.resolveEffectiveStyle(this.inspectedBlockId, cfg);
        String shortName = this.inspectedBlockId.replace("minecraft:", "");
        String title = fitText("Style: " + shortName + (hasCustom ? " (Custom)" : " (Default)"), barW - 20);
        graphics.text(this.font, Component.literal(title), barX + 10, barY + 7, JelloTheme.TEXT, false);

        int[] sl = computeStyleBarLayout(barX, barY, barW);
        int swX = sl[0];
        int swY = sl[1];
        int swStep = sl[2];
        int swSize = sl[3];
        for (int i = 0; i < SWATCH_COLORS.length; i++) {
            int sx = swX + i * swStep;
            int col = 0xFF000000 | SWATCH_COLORS[i];
            boolean active = (style.rgb() == SWATCH_COLORS[i]) && hasCustom;
            graphics.fill(sx - 1, swY - 1, sx + swSize + 1, swY + swSize + 1, active ? 0xFFFFFFFF : JelloTheme.BORDER_SUBTLE);
            graphics.fill(sx, swY, sx + swSize, swY + swSize, col);
        }

        int btnY = sl[10];
        drawToolbarBtn(graphics, mouseX, mouseY, sl[4], btnY, sl[5], 20, "Shape: " + style.resolveShapeMode().label);
        drawToolbarBtn(graphics, mouseX, mouseY, sl[6], btnY, sl[7], 20, "Tracer: " + (style.tracer ? "ON" : "OFF"));
        drawToolbarBtn(graphics, mouseX, mouseY, sl[8], btnY, sl[9], 20, "Use Default");
    }

    private int[] computeToolbarLayout() {
        int innerW = Math.max(220, this.windowW - PAD * 2);
        int gap = 6;
        int clearW = Math.max(58, Math.min(76, Math.round(innerW * 0.14f)));
        int resetW = Math.max(84, Math.min(110, Math.round(innerW * 0.21f)));
        int filterW = Math.max(96, Math.min(132, Math.round(innerW * 0.24f)));
        int searchW = Math.max(120, innerW - gap * 3 - filterW - resetW - clearW);
        int searchX = this.windowX + PAD;
        int filterX = searchX + searchW + gap;
        int resetX = filterX + filterW + gap;
        int clearX = resetX + resetW + gap;
        return new int[]{searchX, searchW, filterX, filterW, resetX, resetW, clearX, clearW};
    }

    private int[] computeStyleBarLayout(int barX, int barY, int barW) {
        int swX = barX + 10;
        int swY = barY + 24;
        boolean compact = barW < 510;
        int swStep = compact ? 18 : 22;
        int swSize = compact ? 14 : 16;
        int swatchesEnd = swX + SWATCH_COLORS.length * swStep;
        int availBtnW = Math.max(150, (barX + barW - 8) - (swatchesEnd + 6));
        int gap = 4;
        int usableBtnW = availBtnW - gap * 2;
        int shapeW = Math.min(96, Math.round(usableBtnW * 0.35f));
        int tracerW = Math.min(88, Math.round(usableBtnW * 0.31f));
        int defaultW = Math.min(96, Math.max(48, usableBtnW - shapeW - tracerW));
        int totalBtnW = shapeW + gap + tracerW + gap + defaultW;
        int shapeX = barX + barW - 8 - totalBtnW;
        int tracerX = shapeX + shapeW + gap;
        int defaultX = tracerX + tracerW + gap;
        int btnY = barY + 22;
        return new int[]{swX, swY, swStep, swSize, shapeX, shapeW, tracerX, tracerW, defaultX, defaultW, btnY};
    }

    private BlockEspHandler.BlockEspStyle ensureCustomStyle(ModConfig cfg, String blockId) {
        if (cfg.blockEspPerBlockStyles == null) {
            cfg.blockEspPerBlockStyles = new java.util.HashMap<>();
        }
        return cfg.blockEspPerBlockStyles.computeIfAbsent(blockId, k -> BlockEspHandler.resolveEffectiveStyle(k, cfg).copy());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();
        boolean left = MouseButtonMapping.isLeftClick(button);
        boolean right = MouseButtonMapping.isRightClick(button);

        if (!left && !right) {
            return super.mouseClicked(event, wasHandled);
        }

        if (left) {
            if (handleDoneButtonClick(mouseX, mouseY)) {
                return true;
            }
            if (handleToolbarClick(mouseX, mouseY, event, wasHandled)) {
                return true;
            }
            if (handlePresetBarClick(mouseX, mouseY)) {
                return true;
            }
            if (this.blockEspMode && this.inspectedBlockId != null && handleStyleBarClick(mouseX, mouseY)) {
                return true;
            }
            if (handleScrollbarClick(mouseX, mouseY)) {
                return true;
            }
        }

        int top = gridTop();
        int bottom = gridBottom();
        int innerX = gridInnerX();
        int innerW = gridInnerW();
        if (isInside(mouseX, mouseY, innerX, top, innerW, bottom - top)) {
            int scroll = (int) Math.round(this.scrollAmount);
            int cellW = gridCellW(innerW);
            for (int i = 0; i < this.filteredBlocks.size(); i++) {
                int row = i / COLS;
                int col = i % COLS;
                int cy = top + row * (CELL_H + CELL_GAP) - scroll;
                if (cy + CELL_H < top || cy > bottom) {
                    continue;
                }
                int cx = innerX + col * (cellW + CELL_GAP);
                if (isInside(mouseX, mouseY, cx, cy, cellW, CELL_H)) {
                    String blockId = this.filteredBlocks.get(i).id();
                    if (right && this.blockEspMode) {
                        if (!this.isSelected.test(blockId)) {
                            this.onToggle.accept(blockId);
                        }
                        this.inspectedBlockId = blockId;
                    } else if (left) {
                        this.onToggle.accept(blockId);
                        if (this.blockEspMode && this.isSelected.test(blockId)) {
                            this.inspectedBlockId = blockId;
                        } else if (blockId.equals(this.inspectedBlockId)) {
                            this.inspectedBlockId = null;
                        }
                        if (this.filterMode == FilterMode.SELECTED) {
                            rebuildFilteredList();
                        }
                    }
                    playClickSound();
                    return true;
                }
            }
        }

        return super.mouseClicked(event, wasHandled);
    }

    private boolean handleToolbarClick(int mouseX, int mouseY, MouseButtonEvent event, boolean wasHandled) {
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
            this.onResetDefaults.run();
            this.inspectedBlockId = null;
            rebuildFilteredList();
            playClickSound();
            return true;
        }
        if (isInside(mouseX, mouseY, tb[6], toolY, tb[7], TOOLBAR_BTN_H)) {
            this.onClearAll.run();
            this.inspectedBlockId = null;
            rebuildFilteredList();
            playClickSound();
            return true;
        }
        return false;
    }

    private boolean handlePresetBarClick(int mouseX, int mouseY) {
        int y = this.windowY + HEADER_H + TOOLBAR_H + 4;
        int bx = this.windowX + PAD + 52;
        if (isInside(mouseX, mouseY, bx, y, 76, 20)) {
            this.onApplyPreset.accept(XrayHandler.PRESET_ORES);
            rebuildFilteredList();
            playClickSound();
            return true;
        }
        bx += 82;
        if (isInside(mouseX, mouseY, bx, y, 86, 20)) {
            this.onApplyPreset.accept(XrayHandler.PRESET_STORAGE);
            rebuildFilteredList();
            playClickSound();
            return true;
        }
        bx += 92;
        if (isInside(mouseX, mouseY, bx, y, 84, 20)) {
            this.onApplyPreset.accept(XrayHandler.PRESET_NETHER);
            rebuildFilteredList();
            playClickSound();
            return true;
        }
        return false;
    }

    private boolean handleStyleBarClick(int mouseX, int mouseY) {
        ModConfig cfg = ModConfig.getInstance();
        int barX = this.windowX + PAD;
        int barY = gridBottom() + 6;
        int barW = this.windowW - PAD * 2;
        int[] sl = computeStyleBarLayout(barX, barY, barW);
        int swX = sl[0];
        int swY = sl[1];
        int swStep = sl[2];
        int swSize = sl[3];
        for (int i = 0; i < SWATCH_COLORS.length; i++) {
            int sx = swX + i * swStep;
            if (isInside(mouseX, mouseY, sx - 1, swY - 1, swSize + 2, swSize + 2)) {
                BlockEspHandler.BlockEspStyle custom = ensureCustomStyle(cfg, this.inspectedBlockId);
                custom.setColorRGB(SWATCH_COLORS[i]);
                cfg.save();
                playClickSound();
                return true;
            }
        }
        int btnY = sl[10];
        if (isInside(mouseX, mouseY, sl[4], btnY, sl[5], 20)) {
            BlockEspHandler.BlockEspStyle custom = ensureCustomStyle(cfg, this.inspectedBlockId);
            custom.shapeMode = custom.resolveShapeMode().next().label;
            cfg.save();
            playClickSound();
            return true;
        }
        if (isInside(mouseX, mouseY, sl[6], btnY, sl[7], 20)) {
            BlockEspHandler.BlockEspStyle custom = ensureCustomStyle(cfg, this.inspectedBlockId);
            custom.tracer = !custom.tracer;
            cfg.save();
            playClickSound();
            return true;
        }
        if (isInside(mouseX, mouseY, sl[8], btnY, sl[9], 20)) {
            if (cfg.blockEspPerBlockStyles != null) {
                cfg.blockEspPerBlockStyles.remove(this.inspectedBlockId);
                cfg.save();
            }
            playClickSound();
            return true;
        }
        return false;
    }
}
