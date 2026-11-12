package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.*;

/**
 * Sestia OLED Obsidian & Crimson Red ClickGUI Dashboard.
 * Unified card-accordion layout: left-click toggles modules, right-click expands
 * the module card downward within its own column to reveal inline settings.
 */
public class ModernClickGuiScreen extends Screen {

    public enum NavTab {
        MODULES("Modules"),
        MACROS("Macros"),
        SETTINGS("Settings");

        public final String label;

        NavTab(String label) {
            this.label = label;
        }
    }

    private static final int SIDEBAR_WIDTH = 148;
    private static final int HEADER_HEIGHT = 44;
    private static final int NAV_ROW_HEIGHT = 34;
    private static final int CHIP_BAR_HEIGHT = 30;

    private final Screen parent;

    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;

    // Navigation & filter state
    private NavTab activeNav = NavTab.MODULES;
    private String selectedCategoryId = "ALL";
    private float indicatorY = -1.0f;
    private final Map<String, Float> switchAnimations = new HashMap<>();

    // Search state
    private EditBox searchBox;
    private String searchQuery = "";

    // Scroll & Inline Accordion state
    private double scrollAmount = 0.0;
    private int contentTotalHeight = 0;
    private String expandedModule = null;
    private String renderedExpandedModule = null;
    private float expandAnim = 0.0f;
    private boolean ensureExpandedVisible = false;

    // Slider drag state
    private String draggingSliderKey = null;
    private ModernModuleSettings.SliderControl activeSlider = null;
    private int draggingSliderStartX = 0;
    private int draggingSliderWidth = 100;

    private long lastFrameNanos = 0L;

    // Module Keybind listening state
    private String listeningKeybindModule = null;
    private final ModernModuleSettings.Actions settingsActions = ModernModuleCardUi.createActions();

    // Macro editor state
    private int editingMacroIndex = -1;
    private boolean listeningMacroKey = false;
    private int tempMacroKey = -1;
    private boolean tempMacroIsMouse = false;
    private EditBox macroNameBox;
    private EditBox macroCmdBox;

    public ModernClickGuiScreen(Screen parent) {
        super(Component.literal("Sestia ClickGUI"));
        this.parent = parent;
        ClickGuiScreen.getAllModules();
    }

    public ModernClickGuiScreen() {
        this(null);
    }

    @Override
    protected void init() {
        this.windowW = Math.min(880, Math.max(520, (int) (this.width * 0.88f)));
        this.windowH = Math.min(540, Math.max(320, (int) (this.height * 0.85f)));
        this.windowX = (this.width - this.windowW) / 2;
        this.windowY = (this.height - this.windowH) / 2;

        this.scrollAmount = 0.0;
        this.draggingSliderKey = null;
        this.activeSlider = null;
        if (this.expandedModule != null) {
            this.ensureExpandedVisible = true;
        }

        if (this.indicatorY < 0) {
            this.indicatorY = this.windowY + HEADER_HEIGHT + 10 + this.activeNav.ordinal() * NAV_ROW_HEIGHT;
        }

        int searchW = 166;
        int searchH = 18;
        int searchX = this.windowX + this.windowW - searchW - 44;
        int searchY = this.windowY + (HEADER_HEIGHT - searchH) / 2 + 2;

        String prevQuery = this.searchQuery;
        this.searchBox = new EditBox(this.font, searchX + 16, searchY, searchW - 30, searchH, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search modules..."));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(30);
        this.searchBox.setValue(prevQuery);
        this.searchBox.setResponder(text -> {
            this.searchQuery = text.trim();
            this.scrollAmount = 0.0;
            this.draggingSliderKey = null;
            this.activeSlider = null;
            if (this.expandedModule != null) {
                if (findModuleIndex(getVisibleModules(), this.expandedModule) < 0) {
                    clearExpandedImmediate();
                } else {
                    this.ensureExpandedVisible = true;
                }
            }
        });
        this.addRenderableWidget(this.searchBox);

        this.macroNameBox = new EditBox(this.font, 0, 0, 180, 20, Component.literal("Macro Name"));
        this.macroNameBox.setHint(Component.literal("Name (e.g. Lobby)"));
        this.macroNameBox.setBordered(false);
        this.macroNameBox.setMaxLength(32);
        this.macroNameBox.visible = false;
        this.addRenderableWidget(this.macroNameBox);

        this.macroCmdBox = new EditBox(this.font, 0, 0, 240, 20, Component.literal("Macro Command"));
        this.macroCmdBox.setHint(Component.literal("Command / Message (e.g. /hub)"));
        this.macroCmdBox.setBordered(false);
        this.macroCmdBox.setMaxLength(128);
        this.macroCmdBox.visible = false;
        this.addRenderableWidget(this.macroCmdBox);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        if (ModConfig.getInstance().jelloBlur) {
            this.extractBlurredBackground(graphics);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? 0.016f : Mth.clamp((now - this.lastFrameNanos) / 1.0e9f, 0.001f, 0.05f);
        this.lastFrameNanos = now;

        if (this.expandedModule != null) {
            this.renderedExpandedModule = this.expandedModule;
        }
        float targetAnim = this.expandedModule != null ? 1.0f : 0.0f;
        this.expandAnim += (targetAnim - this.expandAnim) * Mth.clamp(dt * 18.0f, 0.08f, 1.0f);
        if (Math.abs(targetAnim - this.expandAnim) < 0.005f) {
            this.expandAnim = targetAnim;
            if (targetAnim == 0.0f) {
                this.renderedExpandedModule = null;
            }
        }

        // Dim world backdrop
        graphics.fill(0, 0, this.width, this.height, 0x99000000);

        // Outer window glow + OLED Obsidian glass shell (Pencere 10px)
        JelloTheme.drawGlowShadow(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, 0x38E53935, 5);
        JelloTheme.fillRoundedCard(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, JelloTheme.WINDOW_BG, 0x55E53935);

        renderHeader(graphics, mouseX, mouseY);
        renderSidebar(graphics, mouseX, mouseY, dt);

        int contentX = this.windowX + SIDEBAR_WIDTH + 1;
        int contentY = this.windowY + HEADER_HEIGHT + 1;
        int contentW = this.windowW - SIDEBAR_WIDTH - 2;
        int contentH = this.windowH - HEADER_HEIGHT - 2;

        boolean showingModules = !this.searchQuery.isEmpty() || this.activeNav == NavTab.MODULES;
        if (showingModules) {
            renderModulesWorkspace(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
        } else {
            graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
            if (this.activeNav == NavTab.MACROS) {
                renderMacrosView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
            } else if (this.activeNav == NavTab.SETTINGS) {
                renderClientSettingsView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
            }
            graphics.disableScissor();
            renderScrollbarAt(graphics, contentX + contentW - 6, contentY, contentH, this.contentTotalHeight, this.scrollAmount);
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    // ==========================================
    // TOP HEADER
    // ==========================================
    private void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int headX = this.windowX;
        int headY = this.windowY;
        int headW = this.windowW;

        JelloTheme.fillRoundedTop(graphics, headX + 1, headY + 1, headX + headW - 1, headY + HEADER_HEIGHT, 9, JelloTheme.HEADER_BG);
        graphics.fill(headX + 1, headY + HEADER_HEIGHT, headX + headW - 1, headY + HEADER_HEIGHT + 1, JelloTheme.DIVIDER);

        // Brand emblem + Name + Version badge
        ModernIcons.drawBrandEmblem(graphics, headX + 20, headY + HEADER_HEIGHT / 2, JelloTheme.ACCENT, 0xFFFFCDD2);
        graphics.text(this.font, Component.literal("§lSESTIA"), headX + 31, headY + 18, JelloTheme.TEXT, false);
        int titleW = this.font.width("SESTIA");

        String verLabel = "v" + ClientBuildInfo.getVersion();
        int verX = headX + 38 + titleW;
        int verY = headY + 15;
        int verW = this.font.width(verLabel) + 10;
        JelloTheme.fillRoundedCard(graphics, verX, verY, verX + verW, verY + 14, 4, 0x26E53935, 0x44E53935);
        graphics.centeredText(this.font, Component.literal(verLabel), verX + verW / 2, verY + 3, JelloTheme.TEXT_DIM);

        // Active module count pill
        int activeCount = countActiveModules();
        String activeText = activeCount + " Active";
        int activeX = verX + verW + 8;
        int activeW = this.font.width(activeText) + 16;
        if (activeX + activeW < headX + headW - 230) {
            JelloTheme.fillRoundedCard(graphics, activeX, verY, activeX + activeW, verY + 14, 4, 0x1810B981, 0x3310B981);
            JelloTheme.fillCircle(graphics, activeX + 6, verY + 7, 2, 0xFF34D399);
            graphics.text(this.font, Component.literal(activeText), activeX + 11, verY + 3, 0xFF6EE7B7, false);
        }

        // Search input container + vector icon
        int searchW = 166;
        int searchH = 22;
        int searchBoxX = headX + headW - searchW - 44;
        int searchBoxY = headY + (HEADER_HEIGHT - searchH) / 2;
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        int sBg = searchFocused ? 0xFF161621 : 0xFF111118;
        int sBorder = searchFocused ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE;
        JelloTheme.fillRoundedCard(graphics, searchBoxX, searchBoxY, searchBoxX + searchW, searchBoxY + searchH, 6, sBg, sBorder);
        ModernIcons.drawSearchIcon(graphics, searchBoxX + 6, searchBoxY + 6, searchFocused ? JelloTheme.ACCENT_HOVER : JelloTheme.TEXT_MUTED);

        if (!this.searchQuery.isEmpty()) {
            int clearCx = searchBoxX + searchW - 10;
            int clearCy = searchBoxY + searchH / 2;
            boolean clearHov = isInside(mouseX, mouseY, searchBoxX + searchW - 18, searchBoxY, 18, searchH);
            ModernIcons.drawCloseIcon(graphics, clearCx, clearCy, clearHov ? JelloTheme.ACCENT_HOVER : JelloTheme.TEXT_MUTED);
        }

        // Close button
        int closeX = headX + headW - 30;
        int closeY = headY + 12;
        boolean closeHovered = isInside(mouseX, mouseY, closeX, closeY, 20, 20);
        JelloTheme.fillRoundedCard(graphics, closeX, closeY, closeX + 20, closeY + 20, 5,
                closeHovered ? 0x44E53935 : JelloTheme.CARD_INACTIVE,
                closeHovered ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        ModernIcons.drawCloseIcon(graphics, closeX + 10, closeY + 10, closeHovered ? JelloTheme.ACCENT_HOVER : 0xFFCBD5E1);
    }

    // ==========================================
    // LEFT SIDEBAR NAVIGATION (ZERO DEAD TABS)
    // ==========================================
    private void renderSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float dt) {
        int sideX = this.windowX;
        int sideY = this.windowY + HEADER_HEIGHT + 1;
        int sideW = SIDEBAR_WIDTH;
        int sideH = this.windowH - HEADER_HEIGHT - 2;

        JelloTheme.fillRoundedBottomLeft(graphics, sideX + 1, sideY, sideX + sideW, sideY + sideH, 9, JelloTheme.SIDEBAR_BG);
        graphics.fill(sideX + sideW, sideY, sideX + sideW + 1, sideY + sideH, JelloTheme.DIVIDER);

        int listTop = sideY + 10;
        float targetY = listTop + this.activeNav.ordinal() * NAV_ROW_HEIGHT;
        this.indicatorY += (targetY - this.indicatorY) * Mth.clamp(dt * 16.0f, 0.08f, 1.0f);

        int indY = Math.round(this.indicatorY);
        JelloTheme.fillRounded(graphics, sideX + 8, indY + 2, sideX + sideW - 8, indY + NAV_ROW_HEIGHT - 2, 6, 0x2AE53935);

        NavTab[] tabs = NavTab.values();
        for (int i = 0; i < tabs.length; i++) {
            NavTab tab = tabs[i];
            int itemY = listTop + i * NAV_ROW_HEIGHT;
            boolean isCurrent = tab == this.activeNav && this.searchQuery.isEmpty();
            boolean hovered = isInside(mouseX, mouseY, sideX + 6, itemY, sideW - 12, NAV_ROW_HEIGHT);

            if (hovered && !isCurrent) {
                JelloTheme.fillRounded(graphics, sideX + 8, itemY + 2, sideX + sideW - 8, itemY + NAV_ROW_HEIGHT - 2, 6, 0x12FFFFFF);
            }

            int iconColor = isCurrent ? JelloTheme.ACCENT : (hovered ? JelloTheme.ACCENT_HOVER : JelloTheme.TEXT_MUTED);
            int textColor = isCurrent ? JelloTheme.TEXT : (hovered ? 0xFFE2E8F0 : JelloTheme.TEXT_MUTED);

            int iconX = sideX + 18;
            int iconY = itemY + 12;
            switch (tab) {
                case MODULES -> ModernIcons.drawGridIcon(graphics, iconX, iconY, iconColor);
                case MACROS -> ModernIcons.drawBoltIcon(graphics, iconX, iconY, iconColor);
                case SETTINGS -> ModernIcons.drawSlidersIcon(graphics, iconX, iconY, iconColor);
            }

            graphics.text(this.font, Component.literal(tab.label), sideX + 35, itemY + 13, textColor, false);
        }

        // Interaction legend card at bottom of sidebar
        if (sideH > 235) {
            int legX = sideX + 10;
            int legW = sideW - 20;
            int legH = 56;
            int legY = sideY + sideH - legH - 10;
            JelloTheme.fillRoundedCard(graphics, legX, legY, legX + legW, legY + legH, 6, JelloTheme.CARD_INACTIVE, JelloTheme.BORDER_SUBTLE);
            graphics.text(this.font, Component.literal("Controls"), legX + 8, legY + 7, JelloTheme.TEXT_DIM, false);
            graphics.text(this.font, Component.literal("LMB  Toggle"), legX + 8, legY + 21, JelloTheme.TEXT_MUTED, false);
            graphics.text(this.font, Component.literal("RMB  Settings"), legX + 8, legY + 33, JelloTheme.TEXT_MUTED, false);
            graphics.text(this.font, Component.literal("ESC  Collapse"), legX + 8, legY + 45, 0xFF64748B, false);
        }
    }

    // ==========================================
    // MODULES WORKSPACE (CHIP FILTER + GRID + INLINE ACCORDION)
    // ==========================================
    private record ResolvedAccordionPlan(
            List<ClickGuiScreen.ModuleEntry> modules,
            int startY,
            ModernModuleGridLayout.GridPlan plan,
            ClickGuiScreen.ModuleEntry expandedEntry,
            List<ModernModuleSettings.Control> expandedControls,
            List<FormattedCharSequence> descLines,
            int descHeaderHeight
    ) {
    }

    private ResolvedAccordionPlan resolveAccordionPlan(int contentX, int bodyY, int contentW) {
        List<ClickGuiScreen.ModuleEntry> modules = getVisibleModules();
        int expIdx = findModuleIndex(modules, this.renderedExpandedModule);
        ClickGuiScreen.ModuleEntry expandedEntry = expIdx >= 0 ? modules.get(expIdx) : null;
        List<ModernModuleSettings.Control> expandedControls = expandedEntry != null
                ? ModernModuleSettings.getControls(expandedEntry.name, ModConfig.getInstance(), this.settingsActions)
                : List.of();
        ModernModuleGridLayout.GridMetrics baseMetrics = ModernModuleGridLayout.computeGridMetrics(contentW, modules.size());
        List<FormattedCharSequence> descLines = List.of();
        int descHeaderH = 0;
        int fullH = 0;
        if (expandedEntry != null) {
            String desc = ModernModuleCardUi.getModuleDescription(expandedEntry.name, expandedEntry.category.name);
            descLines = this.font.split(
                    Component.literal(desc),
                    ModernModuleGridLayout.computeCardControlsWidth(baseMetrics.colWidth())
            );
            descHeaderH = ModernModuleGridLayout.computeExpandedDescHeaderHeight(descLines.size());
            fullH = ModernModuleGridLayout.computeCardExtraHeight(baseMetrics.colWidth(), descHeaderH, expandedControls);
        }
        int startY = (int) (bodyY + ModernModuleGridLayout.GRID_PAD_Y - this.scrollAmount);
        ModernModuleGridLayout.GridPlan plan = ModernModuleGridLayout.computeGridPlan(
                contentX, startY, contentW, modules.size(), expIdx, fullH, this.expandAnim
        );
        return new ResolvedAccordionPlan(modules, startY, plan, expandedEntry, expandedControls, descLines, descHeaderH);
    }

    private void renderModulesWorkspace(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH, float dt) {
        renderCategoryChipBar(graphics, mouseX, mouseY, contentX, contentY, contentW);

        int bodyY = contentY + CHIP_BAR_HEIGHT;
        int bodyH = contentH - CHIP_BAR_HEIGHT;
        if (bodyH <= 16) {
            return;
        }

        ResolvedAccordionPlan resolved = resolveAccordionPlan(contentX, bodyY, contentW);
        if (this.ensureExpandedVisible && resolved.expandedEntry() != null && this.expandedModule != null) {
            if (resolved.expandedControls().isEmpty()) {
                this.ensureExpandedVisible = false;
            } else {
                int expIdx = resolved.plan().expandedIndex();
                if (expIdx >= 0) {
                    int row = expIdx / resolved.plan().metrics().columns();
                    int fullH = ModernModuleGridLayout.computeCardExtraHeight(
                            resolved.plan().metrics().colWidth(),
                            resolved.descHeaderHeight(),
                            resolved.expandedControls()
                    );
                    int desiredBottom = bodyY + ModernModuleGridLayout.GRID_PAD_Y
                            + row * (ModernModuleGridLayout.CARD_HEIGHT + ModernModuleGridLayout.GRID_GAP_Y)
                            + ModernModuleGridLayout.CARD_HEIGHT
                            + fullH + 8;
                    int viewBottom = bodyY + bodyH;
                    double screenBottom = desiredBottom - this.scrollAmount;
                    if (screenBottom > viewBottom) {
                        double targetScroll = desiredBottom - viewBottom;
                        double maxCurrentScroll = Math.max(0, resolved.plan().totalHeight() - bodyH);
                        double maxCardTopScroll = row * (ModernModuleGridLayout.CARD_HEIGHT + ModernModuleGridLayout.GRID_GAP_Y)
                                + ModernModuleGridLayout.GRID_PAD_Y;
                        double nextScroll = Math.min(Math.min(targetScroll, maxCurrentScroll), maxCardTopScroll);
                        if (nextScroll > this.scrollAmount) {
                            this.scrollAmount = nextScroll;
                            resolved = resolveAccordionPlan(contentX, bodyY, contentW);
                            screenBottom = desiredBottom - this.scrollAmount;
                        }
                    }
                    if (this.expandAnim >= 0.995f || (this.expandAnim >= 0.85f && screenBottom <= viewBottom)) {
                        this.ensureExpandedVisible = false;
                    }
                }
            }
        }

        graphics.enableScissor(contentX, bodyY, contentX + contentW, bodyY + bodyH);
        renderModuleCardsGrid(graphics, mouseX, mouseY, contentX, bodyY, contentW, bodyH, resolved, dt);
        graphics.disableScissor();

        this.contentTotalHeight = resolved.plan().totalHeight();
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0, Math.max(0, this.contentTotalHeight - bodyH));
        renderScrollbarAt(graphics, contentX + contentW - 6, bodyY + 4, bodyH - 8, this.contentTotalHeight, this.scrollAmount);
    }

    private void renderCategoryChipBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW) {
        graphics.fill(contentX, contentY + CHIP_BAR_HEIGHT - 1, contentX + contentW, contentY + CHIP_BAR_HEIGHT, JelloTheme.DIVIDER);

        List<FilterChip> chips = getFilterChips();
        int curX = contentX + 12;
        int chipY = contentY + 6;
        int chipH = 18;

        for (FilterChip chip : chips) {
            String text = chip.label + " " + chip.count;
            int chipW = Math.max(28, this.font.width(text) + 18);
            if (curX + chipW > contentX + contentW - 8) {
                break;
            }
            boolean active = this.searchQuery.isEmpty() && chip.id.equals(this.selectedCategoryId);
            boolean hovered = isInside(mouseX, mouseY, curX, chipY, chipW, chipH);
            int bg = active ? JelloTheme.ACCENT : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
            int border = active ? JelloTheme.ACCENT_HOVER : (hovered ? 0x5594A3B8 : JelloTheme.BORDER_SUBTLE);
            JelloTheme.fillRoundedCard(graphics, curX, chipY, curX + chipW, chipY + chipH, 4, bg, border);
            // Keep the label inside the pill even with wider glyphs/fonts.
            String chipText = fitText(text, chipW - 12);
            graphics.centeredText(this.font, Component.literal(chipText), curX + chipW / 2, chipY + 5,
                    active ? JelloTheme.TEXT : JelloTheme.TEXT_MUTED);
            curX += chipW + 5;
        }
    }

    private void renderModuleCardsGrid(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            int gridX,
            int gridY,
            int gridW,
            int gridH,
            ResolvedAccordionPlan resolved,
            float dt
    ) {
        List<ClickGuiScreen.ModuleEntry> modules = resolved.modules();
        if (modules.isEmpty()) {
            int emptyY = gridY + 24;
            JelloTheme.fillRoundedCard(graphics, gridX + 12, emptyY, gridX + gridW - 12, emptyY + 54, 7, JelloTheme.CARD_INACTIVE, JelloTheme.BORDER_SUBTLE);
            String msg = this.searchQuery.isEmpty() ? "No modules in this category" : ("No modules matched '" + this.searchQuery + "'");
            graphics.centeredText(this.font, Component.literal(msg), gridX + gridW / 2, emptyY + 22, JelloTheme.TEXT_MUTED);
            return;
        }

        ModernModuleGridLayout.GridPlan plan = resolved.plan();
        ModernModuleGridLayout.AccordionBounds acc = plan.accordion();
        int colW = plan.metrics().colWidth();
        int cardH = plan.metrics().cardHeight();

        for (int i = 0; i < modules.size(); i++) {
            int cx = plan.cardX(gridX, i);
            int cy = plan.cardY(resolved.startY(), i);
            boolean isAccordionCard = acc != null && i == plan.expandedIndex();
            int cardTotalH = cardH + (isAccordionCard ? acc.visibleHeight() : 0);

            if (cy + cardTotalH >= gridY - 10 && cy <= gridY + gridH + 10) {
                renderUniformModuleCard(graphics, mouseX, mouseY, cx, cy, colW, cardTotalH, modules.get(i), isAccordionCard, resolved, dt);
            }
        }
    }

    private record CardControlsBounds(
            ModernModuleGridLayout.CardControlLayout layout,
            String bindTag,
            boolean isListening,
            boolean hasSettings
    ) {
    }

    private CardControlsBounds computeCardControls(int x, int y, int w, ClickGuiScreen.ModuleEntry mod, ModConfig cfg) {
        boolean hasSettings = ModernModuleSettings.hasSettings(mod.name, cfg, this.settingsActions);
        boolean isListening = mod.name.equals(this.listeningKeybindModule);
        String bindTag = getBindPillText(cfg, mod.name, isListening);
        ModernModuleGridLayout.CardControlLayout layout =
                ModernModuleGridLayout.computeCardControlLayout(x, y, w, this.font.width(bindTag), hasSettings);
        return new CardControlsBounds(layout, bindTag, isListening, hasSettings);
    }

    private void renderUniformModuleCard(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int w,
            int cardTotalH,
            ClickGuiScreen.ModuleEntry mod,
            boolean isAccordionCard,
            ResolvedAccordionPlan resolved,
            float dt
    ) {
        ModConfig cfg = ModConfig.getInstance();
        boolean active = mod.getter.get();
        CardControlsBounds controls = computeCardControls(x, y, w, mod, cfg);
        ModernModuleGridLayout.CardControlLayout layout = controls.layout();
        boolean isExpanded = controls.hasSettings() && mod.name.equals(this.expandedModule);
        int cardH = ModernModuleGridLayout.CARD_HEIGHT;
        boolean hovered = isInside(mouseX, mouseY, x, y, w, cardH);

        int bg = active
                ? (hovered ? JelloTheme.CARD_ACTIVE_HOVER : JelloTheme.CARD_ACTIVE)
                : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
        int border = isExpanded
                ? JelloTheme.ACCENT
                : (active ? (hovered ? JelloTheme.ACCENT_HOVER : 0x66E53935) : (hovered ? 0x5594A3B8 : JelloTheme.BORDER_SUBTLE));
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + cardTotalH, ModernModuleGridLayout.CARD_CORNER_RADIUS, bg, border);

        // Compact 2-Row Bento Tile: Full Title (top) + [Tune] [Bind] [Toggle] (bottom-right)
        String title = fitText(mod.name, layout.titleMaxW());
        graphics.text(this.font, Component.literal(title),
                x + ModernModuleGridLayout.CARD_PAD_X,
                y + ModernModuleGridLayout.TITLE_Y_OFFSET,
                active ? JelloTheme.TEXT : 0xFFE2E8F0,
                false);

        if (controls.hasSettings()) {
            boolean tuneHov = isInside(mouseX, mouseY, layout.tuneX(), layout.tuneY(), layout.tuneW(), layout.tuneH());
            int tuneBg = isExpanded ? 0x44E53935 : (tuneHov ? 0x26FFFFFF : 0xFF1B1B25);
            int tuneBorder = isExpanded ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE;
            JelloTheme.fillRoundedCard(graphics, layout.tuneX(), layout.tuneY(), layout.tuneX() + layout.tuneW(), layout.tuneY() + layout.tuneH(), 4, tuneBg, tuneBorder);
            ModernIcons.drawTuneIcon(graphics, layout.tuneX() + 4, layout.tuneY() + 3,
                    isExpanded ? JelloTheme.ACCENT_HOVER : (tuneHov ? JelloTheme.TEXT : JelloTheme.TEXT_MUTED));
        }

        boolean bindHovered = isInside(mouseX, mouseY, layout.bindX(), layout.bindY(), layout.bindW(), layout.bindH());
        int bindBg = controls.isListening() ? 0x55E53935 : (bindHovered ? 0x33E53935 : 0xFF1A1A24);
        int bindBorder = controls.isListening() ? JelloTheme.ACCENT_HOVER : (bindHovered ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        JelloTheme.fillRoundedCard(graphics, layout.bindX(), layout.bindY(), layout.bindX() + layout.bindW(), layout.bindY() + layout.bindH(), 4, bindBg, bindBorder);
        graphics.centeredText(this.font, Component.literal(controls.bindTag()), layout.bindX() + layout.bindW() / 2, layout.bindY() + 4,
                controls.isListening() ? 0xFFFFDD55 : (cfg.getModuleKey(mod.name) >= 0 ? JelloTheme.TEXT_DIM : JelloTheme.TEXT_MUTED));

        renderSmoothSwitch(graphics, mouseX, mouseY, layout.switchX(), layout.switchY(), layout.switchW(), layout.switchH(), "mod:" + mod.name, active, dt);

        ModernModuleGridLayout.AccordionBounds acc = resolved.plan().accordion();
        if (isAccordionCard && acc != null && acc.visibleHeight() > 6) {
            graphics.enableScissor(x + 1, y + cardH, x + w - 1, y + cardTotalH - 1);
            graphics.fill(x + 1, y + cardH, x + w - 1, y + cardH + 1, JelloTheme.DIVIDER);

            List<FormattedCharSequence> descLines = resolved.descLines();
            int maxDescLines = Math.min(2, descLines.size());
            for (int lineIdx = 0; lineIdx < maxDescLines; lineIdx++) {
                int lineY = y + cardH + 1 + ModernModuleGridLayout.ACCORDION_TOP_DIVIDER_PAD
                        + lineIdx * ModernModuleGridLayout.ACCORDION_DESC_LINE_HEIGHT;
                graphics.text(this.font, descLines.get(lineIdx),
                        x + ModernModuleGridLayout.CARD_PADDING_X, lineY, JelloTheme.TEXT_MUTED, false);
            }

            int controlsY = y + cardH + 1 + resolved.descHeaderHeight();
            ModernModuleCardUi.renderControls(
                    graphics,
                    this.font,
                    mouseX,
                    mouseY,
                    x + ModernModuleGridLayout.CARD_PADDING_X,
                    controlsY,
                    acc.controlsWidth(),
                    mod.name,
                    resolved.expandedControls(),
                    (sx, sy, sw, sh, skey, pct) -> renderInteractiveSlider(graphics, mouseX, mouseY, sx, sy, sw, sh, skey, pct),
                    (tx, ty, tw, th, tkey, tactive) -> renderSmoothSwitch(graphics, mouseX, mouseY, tx, ty, tw, th, tkey, tactive, dt)
            );
            graphics.disableScissor();
        }
    }

    private int renderSettingsToggleCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, String title, String desc, boolean active, float dt) {
        int cardH = 38;
        boolean hovered = isInside(mouseX, mouseY, x, y, w, cardH);

        int bg = active ? (hovered ? JelloTheme.CARD_ACTIVE_HOVER : JelloTheme.CARD_ACTIVE)
                : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
        int border = active ? (hovered ? JelloTheme.ACCENT : 0x66E53935)
                : (hovered ? 0x5594A3B8 : JelloTheme.BORDER_SUBTLE);
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + cardH, 7, bg, border);

        graphics.text(this.font, Component.literal(title), x + 12, y + 6, active ? JelloTheme.TEXT : 0xFFE2E8F0, false);
        graphics.text(this.font, Component.literal(fitText(desc, w - 58)), x + 12, y + 20, JelloTheme.TEXT_MUTED, false);

        renderSmoothSwitch(graphics, mouseX, mouseY, x + w - 42, y + (cardH - 16) / 2, 32, 16, "cfg:" + title, active, dt);
        return y + cardH + 6;
    }

    // ==========================================
    // MACROS & KEYBINDS VIEW
    // ==========================================
    private void renderMacrosView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH, float dt) {
        ModConfig config = ModConfig.getInstance();
        if (config.macros == null) {
            config.macros = new ArrayList<>();
        }

        int innerY = contentY + 12;
        int leftColW = Math.max(260, contentW - 255);
        int leftColX = contentX + 14;
        int rightColX = leftColX + leftColW + 12;
        int rightColW = contentW - leftColW - 38;
        int rightColH = contentH - 24;

        ModernIcons.drawBoltIcon(graphics, leftColX, innerY - 1, JelloTheme.ACCENT);
        graphics.text(this.font, Component.literal("§lCONFIGURED MACROS"), leftColX + 15, innerY, JelloTheme.TEXT, false);
        String countText = config.macros.size() + " active";
        graphics.text(this.font, Component.literal(countText), leftColX + leftColW - this.font.width(countText), innerY, JelloTheme.TEXT_DIM, false);
        JelloTheme.fillHorizontalGradient(graphics, leftColX, innerY + 13, leftColX + 155, innerY + 14, JelloTheme.ACCENT, 0x33FF5252);

        int listStartY = innerY + 24;
        int cardH = 46;
        int cardGap = 8;
        this.contentTotalHeight = Math.max(contentH, 30 + config.macros.size() * (cardH + cardGap));

        if (config.macros.isEmpty()) {
            int emptyH = 80;
            JelloTheme.fillRoundedCard(graphics, leftColX, listStartY, leftColX + leftColW, listStartY + emptyH, 7, JelloTheme.CARD_INACTIVE, JelloTheme.BORDER_SUBTLE);
            graphics.centeredText(this.font, Component.literal("No macros configured yet."), leftColX + leftColW / 2, listStartY + 26, JelloTheme.TEXT);
            graphics.centeredText(this.font, Component.literal("Click '+ Add New Macro' on the right to create one."), leftColX + leftColW / 2, listStartY + 44, JelloTheme.TEXT_MUTED);
        } else {
            for (int i = 0; i < config.macros.size(); i++) {
                ModConfig.MacroEntry m = config.macros.get(i);
                int cardY = listStartY + i * (cardH + cardGap) - (int) this.scrollAmount;
                if (cardY + cardH < contentY || cardY > contentY + contentH) {
                    continue;
                }

                boolean isEditingThis = (this.editingMacroIndex == i);
                boolean hovered = isInside(mouseX, mouseY, leftColX, cardY, leftColW, cardH);

                int cardBg = isEditingThis ? JelloTheme.CARD_ACTIVE : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
                int borderColor = isEditingThis ? JelloTheme.ACCENT : (hovered ? 0x66E53935 : JelloTheme.BORDER_SUBTLE);
                JelloTheme.fillRoundedCard(graphics, leftColX, cardY, leftColX + leftColW, cardY + cardH, 7, cardBg, borderColor);

                String nameTitle = (m.name != null && !m.name.isEmpty()) ? m.name : "Unnamed Macro";
                graphics.text(this.font, Component.literal(nameTitle), leftColX + 12, cardY + 9, JelloTheme.TEXT, false);

                String cmdPreview = (m.message != null && !m.message.isEmpty()) ? m.message : "/help";
                if (cmdPreview.length() > 30) {
                    cmdPreview = cmdPreview.substring(0, 27) + "...";
                }
                int cmdColor = cmdPreview.startsWith("/") ? JelloTheme.TEXT_DIM : 0xFF81C784;
                graphics.text(this.font, Component.literal(cmdPreview), leftColX + 12, cardY + 25, cmdColor, false);

                String bindText = ClickGuiScreen.getBindDisplayName(m.keyCode, m.isMouse);
                int bindBadgeW = Math.max(48, this.font.width(bindText) + 14);
                int bindBadgeH = 18;
                int bindBadgeX = leftColX + leftColW - 64 - bindBadgeW;
                int bindBadgeY = cardY + 14;

                boolean bindHovered = isInside(mouseX, mouseY, bindBadgeX, bindBadgeY, bindBadgeW, bindBadgeH);
                int bindBg = (isEditingThis && this.listeningMacroKey) ? 0x55E53935 : (bindHovered ? 0x33E53935 : 0xFF1A1A24);
                int bindBorder = (isEditingThis && this.listeningMacroKey) ? JelloTheme.ACCENT_HOVER : (bindHovered ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
                JelloTheme.fillRoundedCard(graphics, bindBadgeX, bindBadgeY, bindBadgeX + bindBadgeW, bindBadgeY + bindBadgeH, 4, bindBg, bindBorder);
                graphics.centeredText(this.font, Component.literal(bindText), bindBadgeX + bindBadgeW / 2, bindBadgeY + 5,
                        (isEditingThis && this.listeningMacroKey) ? JelloTheme.ACCENT_HOVER : (m.keyCode >= 0 ? 0xFFFFD54F : JelloTheme.TEXT_MUTED));

                int editBtnX = leftColX + leftColW - 56;
                int editBtnY = cardY + 14;
                boolean editHovered = isInside(mouseX, mouseY, editBtnX, editBtnY, 22, 18);
                JelloTheme.fillRoundedCard(graphics, editBtnX, editBtnY, editBtnX + 22, editBtnY + 18, 4,
                        editHovered ? 0x44E53935 : 0xFF1A1A24, editHovered ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
                ModernIcons.drawTuneIcon(graphics, editBtnX + 6, editBtnY + 4, editHovered ? JelloTheme.TEXT : JelloTheme.TEXT_MUTED);

                int delBtnX = leftColX + leftColW - 28;
                boolean delHovered = isInside(mouseX, mouseY, delBtnX, editBtnY, 22, 18);
                JelloTheme.fillRoundedCard(graphics, delBtnX, editBtnY, delBtnX + 22, editBtnY + 18, 4,
                        delHovered ? 0x66E53935 : 0xFF1A1A24, delHovered ? JelloTheme.ACCENT_HOVER : JelloTheme.BORDER_SUBTLE);
                ModernIcons.drawCloseIcon(graphics, delBtnX + 11, editBtnY + 9, delHovered ? 0xFFFFFFFF : 0xFFFF6666);
            }
        }

        renderMacroEditorOrHelp(graphics, mouseX, mouseY, rightColX, innerY - 2, rightColW, rightColH);
    }

    private void renderMacroEditorOrHelp(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h) {
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 8, JelloTheme.CONTAINER_BG, JelloTheme.BORDER_SUBTLE);

        if (this.editingMacroIndex != -1) {
            String title = (this.editingMacroIndex == -2) ? "CREATE NEW MACRO" : "EDIT MACRO";
            graphics.text(this.font, Component.literal("§l" + title), x + 12, y + 12, JelloTheme.TEXT, false);
            JelloTheme.fillHorizontalGradient(graphics, x + 12, y + 25, x + 12 + this.font.width(title) + 18, y + 26, JelloTheme.ACCENT, 0x33FF5252);

            int formY = y + 36;
            int boxW = Math.max(120, w - 24);

            graphics.text(this.font, Component.literal("Macro Name:"), x + 12, formY, JelloTheme.TEXT_MUTED, false);
            boolean nameFocus = this.macroNameBox != null && this.macroNameBox.isFocused();
            JelloTheme.fillRoundedCard(graphics, x + 12, formY + 10, x + 12 + boxW, formY + 30, 5, 0xFF0A0A0F, nameFocus ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
            if (this.macroNameBox != null) {
                this.macroNameBox.setX(x + 16);
                this.macroNameBox.setY(formY + 15);
                this.macroNameBox.setWidth(boxW - 8);
                this.macroNameBox.visible = true;
            }
            formY += 40;

            graphics.text(this.font, Component.literal("Command or Chat Message:"), x + 12, formY, JelloTheme.TEXT_MUTED, false);
            boolean cmdFocus = this.macroCmdBox != null && this.macroCmdBox.isFocused();
            JelloTheme.fillRoundedCard(graphics, x + 12, formY + 10, x + 12 + boxW, formY + 30, 5, 0xFF0A0A0F, cmdFocus ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
            if (this.macroCmdBox != null) {
                this.macroCmdBox.setX(x + 16);
                this.macroCmdBox.setY(formY + 15);
                this.macroCmdBox.setWidth(boxW - 8);
                this.macroCmdBox.visible = true;
            }
            formY += 40;

            graphics.text(this.font, Component.literal("Trigger Keybind:"), x + 12, formY, JelloTheme.TEXT_MUTED, false);
            int bindBtnX = x + 12;
            int bindBtnY = formY + 12;
            int bindBtnW = boxW;
            int bindBtnH = 22;

            boolean bindHovered = isInside(mouseX, mouseY, bindBtnX, bindBtnY, bindBtnW, bindBtnH);
            int bindBg = this.listeningMacroKey ? 0x44E53935 : (bindHovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
            int bindBorder = this.listeningMacroKey ? JelloTheme.ACCENT_HOVER : (bindHovered ? 0x6694A3B8 : JelloTheme.BORDER_SUBTLE);
            JelloTheme.fillRoundedCard(graphics, bindBtnX, bindBtnY, bindBtnX + bindBtnW, bindBtnY + bindBtnH, 5, bindBg, bindBorder);

            String keyDisplay = this.listeningMacroKey ? "Press any key... (Esc: cancel)" : ClickGuiScreen.getBindDisplayName(this.tempMacroKey, this.tempMacroIsMouse);
            graphics.centeredText(this.font, Component.literal(keyDisplay), bindBtnX + bindBtnW / 2, bindBtnY + 6,
                    this.listeningMacroKey ? JelloTheme.ACCENT_HOVER : (this.tempMacroKey >= 0 ? 0xFFFFD54F : JelloTheme.TEXT));
            graphics.text(this.font, Component.literal("LMB to bind, RMB to clear"), x + 12, formY + 38, 0xFF64748B, false);
            formY += 56;

            int btnH = 24;
            int btnW = (boxW - 8) / 2;
            int saveBtnX = x + 12;
            int cancelBtnX = saveBtnX + btnW + 8;

            boolean saveHov = isInside(mouseX, mouseY, saveBtnX, formY, btnW, btnH);
            boolean cancelHov = isInside(mouseX, mouseY, cancelBtnX, formY, btnW, btnH);

            JelloTheme.fillRounded(graphics, saveBtnX, formY, saveBtnX + btnW, formY + btnH, 5, saveHov ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT);
            graphics.centeredText(this.font, Component.literal("Save Macro"), saveBtnX + btnW / 2, formY + 8, 0xFFFFFFFF);

            JelloTheme.fillRoundedCard(graphics, cancelBtnX, formY, cancelBtnX + btnW, formY + btnH, 5,
                    cancelHov ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE, JelloTheme.BORDER_SUBTLE);
            graphics.centeredText(this.font, Component.literal("Cancel"), cancelBtnX + btnW / 2, formY + 8, cancelHov ? 0xFFFFFFFF : JelloTheme.TEXT_MUTED);
        } else {
            if (this.macroNameBox != null) this.macroNameBox.visible = false;
            if (this.macroCmdBox != null) this.macroCmdBox.visible = false;

            graphics.text(this.font, Component.literal("§lMACRO GUIDE"), x + 12, y + 12, JelloTheme.TEXT, false);
            JelloTheme.fillHorizontalGradient(graphics, x + 12, y + 25, x + 120, y + 26, JelloTheme.ACCENT, 0x33FF5252);

            int addBtnX = x + 12;
            int addBtnY = y + 36;
            int addBtnW = w - 24;
            int addBtnH = 26;
            boolean addHovered = isInside(mouseX, mouseY, addBtnX, addBtnY, addBtnW, addBtnH);
            JelloTheme.fillRounded(graphics, addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + addBtnH, 6, addHovered ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT);
            graphics.centeredText(this.font, Component.literal("§l+ Add New Macro"), addBtnX + addBtnW / 2, addBtnY + 9, 0xFFFFFFFF);

            int infoY = addBtnY + addBtnH + 18;
            graphics.text(this.font, Component.literal("§lHow Macros Work:"), x + 12, infoY, JelloTheme.TEXT, false);
            infoY += 14;
            graphics.text(this.font, Component.literal("Pressing your bound key sends"), x + 12, infoY, JelloTheme.TEXT_MUTED, false);
            infoY += 11;
            graphics.text(this.font, Component.literal("the command or message in-game."), x + 12, infoY, JelloTheme.TEXT_MUTED, false);
            infoY += 18;
            graphics.text(this.font, Component.literal("§lCommands & Chat:"), x + 12, infoY, JelloTheme.TEXT, false);
            infoY += 14;
            graphics.text(this.font, Component.literal("Start with '/' for commands:"), x + 12, infoY, JelloTheme.TEXT_MUTED, false);
            infoY += 11;
            graphics.text(this.font, Component.literal("/hub, /craft, /spawn"), x + 12, infoY, JelloTheme.TEXT_DIM, false);
            infoY += 18;
            graphics.text(this.font, Component.literal("§lKey & Mouse Binds:"), x + 12, infoY, JelloTheme.TEXT, false);
            infoY += 14;
            graphics.text(this.font, Component.literal("Supports keyboard & mouse (M3-M5)."), x + 12, infoY, JelloTheme.TEXT_MUTED, false);
            infoY += 11;
            graphics.text(this.font, Component.literal("Right-click bind badge to clear."), x + 12, infoY, JelloTheme.TEXT_MUTED, false);
        }
    }

    // ==========================================
    // CLIENT SETTINGS VIEW
    // ==========================================
    private record ClientSettingItem(
            String title,
            String desc,
            java.util.function.BooleanSupplier getter,
            Runnable onToggle
    ) {}

    private List<ClientSettingItem> getClientSettingItems(ModConfig config) {
        return List.of(
                new ClientSettingItem("Modern Dashboard GUI", "Switch between Modern ClickGUI and Classic Floating Windows",
                        () -> config.clickGuiStyle == 1,
                        () -> config.clickGuiStyle = (config.clickGuiStyle == 1) ? 0 : 1),
                new ClientSettingItem("Glass Background Blur", "Enable blurred backdrop behind ClickGUI screens",
                        () -> config.jelloBlur,
                        () -> config.jelloBlur = !config.jelloBlur),
                new ClientSettingItem("Keybinds HUD Table", "Render active draggable keybinds overlay on screen",
                        () -> config.keybindsHudEnabled,
                        () -> config.keybindsHudEnabled = !config.keybindsHudEnabled),
                new ClientSettingItem("Toggle Notifications", "Show [Sestia] overlay alert when toggling modules via keybind",
                        () -> config.toggleNotifications,
                        () -> config.toggleNotifications = !config.toggleNotifications),
                new ClientSettingItem("Infinite Chat History", "Remove chat line limits and preserve all messages",
                        () -> config.chatInfinite,
                        () -> config.chatInfinite = !config.chatInfinite),
                new ClientSettingItem("Anti Chat Clear", "Prevent servers from clearing your chat buffer",
                        () -> config.chatAntiClear,
                        () -> config.chatAntiClear = !config.chatAntiClear)
        );
    }

    private void renderClientSettingsView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH, float dt) {
        int topY = (int) (contentY + 12 - this.scrollAmount);
        ModernIcons.drawSlidersIcon(graphics, contentX + 14, topY - 1, JelloTheme.ACCENT);
        graphics.text(this.font, Component.literal("§lCLIENT CONFIGURATION"), contentX + 29, topY, JelloTheme.TEXT, false);
        JelloTheme.fillHorizontalGradient(graphics, contentX + 14, topY + 13, contentX + 180, topY + 14, JelloTheme.ACCENT, 0x33FF5252);

        ModConfig config = ModConfig.getInstance();
        int cardY = topY + 22;
        int colW = contentW - 28;

        for (ClientSettingItem item : getClientSettingItems(config)) {
            cardY = renderSettingsToggleCard(graphics, mouseX, mouseY, contentX + 14, cardY, colW,
                    item.title(), item.desc(), item.getter().getAsBoolean(), dt);
        }

        this.contentTotalHeight = cardY - contentY + (int) this.scrollAmount + 20;
    }

    // ==========================================
    // REUSABLE UI COMPONENTS
    // ==========================================
    private void renderSmoothSwitch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, String key, boolean active, float dt) {
        float currentAnim = this.switchAnimations.getOrDefault(key, active ? 1.0f : 0.0f);
        float target = active ? 1.0f : 0.0f;
        float nextAnim = currentAnim + (target - currentAnim) * Mth.clamp(dt * 18.0f, 0.08f, 1.0f);
        this.switchAnimations.put(key, nextAnim);

        boolean hovered = isInside(mouseX, mouseY, x, y, w, h);
        int trackRadius = h / 2;
        int offColor = hovered ? JelloTheme.SWITCH_OFF_HOVER : JelloTheme.SWITCH_OFF;
        int onColor = hovered ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT;
        int trackColor = JelloTheme.lerpColor(offColor, onColor, nextAnim);
        int trackBorder = JelloTheme.lerpColor(JelloTheme.BORDER_SUBTLE, 0xAAFF5252, nextAnim);

        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, trackRadius, trackColor, trackBorder);

        int knobSize = h - 4;
        int knobMinX = x + 2;
        int knobMaxX = x + w - knobSize - 2;
        int knobX = Math.round(knobMinX + nextAnim * (knobMaxX - knobMinX));
        int knobY = y + 2;
        int knobRadius = knobSize / 2;

        JelloTheme.fillCircle(graphics, knobX + knobRadius, knobY + knobRadius, knobRadius, hovered ? 0xFFFFFFFF : 0xFFF1F5F9);
    }

    private void renderInteractiveSlider(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, String key, float value) {
        value = Mth.clamp(value, 0.0f, 1.0f);
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y - 4 && mouseY <= y + h + 4;
        boolean isDraggingThis = key.equals(this.draggingSliderKey);

        int trackRadius = h / 2;
        JelloTheme.fillRounded(graphics, x, y, x + w, y + h, trackRadius, JelloTheme.SWITCH_OFF);

        int fillW = Math.max(trackRadius * 2, (int) (w * value));
        int fillColor = isDraggingThis ? JelloTheme.ACCENT_HOVER : (hovered ? JelloTheme.ACCENT : 0xDDE53935);
        JelloTheme.fillRounded(graphics, x, y, x + fillW, y + h, trackRadius, fillColor);

        int knobX = x + (int) (w * value);
        int knobRadius = h / 2 + 3;
        int knobY = y + h / 2;
        JelloTheme.fillCircle(graphics, knobX, knobY, knobRadius, (isDraggingThis || hovered) ? 0xFFFFFFFF : 0xFFE2E8F0);
        JelloTheme.fillCircle(graphics, knobX, knobY, knobRadius - 2, isDraggingThis ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT);
    }

    private void renderScrollbarAt(GuiGraphicsExtractor graphics, int barX, int viewY, int viewH, int totalH, double scroll) {
        int maxScroll = Math.max(0, totalH - viewH);
        if (maxScroll <= 0 || viewH <= 20) return;

        int thumbH = Math.max(22, (int) ((float) (viewH * viewH) / totalH));
        int thumbY = viewY + (int) ((scroll / maxScroll) * (viewH - thumbH));

        JelloTheme.fillRounded(graphics, barX, viewY, barX + 3, viewY + viewH, 1, 0x33000000);
        JelloTheme.fillRounded(graphics, barX, thumbY, barX + 3, thumbY + thumbH, 1, 0xAAE53935);
    }

    // ==========================================
    // INPUT HANDLING: MOUSE & KEYBOARD
    // ==========================================
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();

        if (this.listeningKeybindModule != null && MouseButtonMapping.isExtraMouseButton(button)) {
            ModConfig cfg = ModConfig.getInstance();
            cfg.setModuleKey(this.listeningKeybindModule, button, true);
            this.listeningKeybindModule = null;
            cfg.save();
            playClickSound();
            return true;
        }

        if (this.listeningMacroKey && MouseButtonMapping.isExtraMouseButton(button)) {
            this.tempMacroKey = button;
            this.tempMacroIsMouse = true;
            this.listeningMacroKey = false;
            if (this.editingMacroIndex >= 0 && this.editingMacroIndex < ModConfig.getInstance().macros.size()) {
                ModConfig.MacroEntry m = ModConfig.getInstance().macros.get(this.editingMacroIndex);
                m.keyCode = this.tempMacroKey;
                m.isMouse = true;
                ModConfig.getInstance().save();
            }
            playClickSound();
            return true;
        }

        // Clear search [X] button in header
        int searchW = 166;
        int searchH = 22;
        int searchBoxX = this.windowX + this.windowW - searchW - 44;
        int searchBoxY = this.windowY + (HEADER_HEIGHT - searchH) / 2;
        if (MouseButtonMapping.isLeftClick(button) && !this.searchQuery.isEmpty() && isInside(mouseX, mouseY, searchBoxX + searchW - 18, searchBoxY, 18, searchH)) {
            if (this.searchBox != null) {
                this.searchBox.setValue("");
            }
            this.searchQuery = "";
            playClickSound();
            return true;
        }

        // Unfocus header search box when clicking anywhere outside it
        if (this.searchBox != null && !isInside(mouseX, mouseY, searchBoxX, searchBoxY, searchW, searchH)) {
            this.searchBox.setFocused(false);
        }

        // Close [X] button in header
        int closeX = this.windowX + this.windowW - 30;
        int closeY = this.windowY + 12;
        if (MouseButtonMapping.isLeftClick(button) && isInside(mouseX, mouseY, closeX, closeY, 20, 20)) {
            playClickSound();
            onClose();
            return true;
        }

        // Sidebar Navigation
        int sideX = this.windowX;
        int sideY = this.windowY + HEADER_HEIGHT + 1;
        int listTop = sideY + 10;
        if (MouseButtonMapping.isLeftClick(button) && isInside(mouseX, mouseY, sideX, sideY, SIDEBAR_WIDTH, this.windowH - HEADER_HEIGHT - 2)) {
            int tabIndex = (mouseY - listTop) / NAV_ROW_HEIGHT;
            NavTab[] tabs = NavTab.values();
            if (mouseY >= listTop && tabIndex >= 0 && tabIndex < tabs.length) {
                this.activeNav = tabs[tabIndex];
                this.scrollAmount = 0.0;
                this.listeningKeybindModule = null;
                clearExpandedImmediate();
                if (!this.searchQuery.isEmpty() && this.searchBox != null) {
                    this.searchBox.setValue("");
                    this.searchQuery = "";
                }
                if (this.activeNav != NavTab.MACROS) {
                    this.editingMacroIndex = -1;
                    this.listeningMacroKey = false;
                    if (this.macroNameBox != null) this.macroNameBox.visible = false;
                    if (this.macroCmdBox != null) this.macroCmdBox.visible = false;
                }
                playClickSound();
                return true;
            }
        }

        // Content Area
        int contentX = this.windowX + SIDEBAR_WIDTH + 1;
        int contentY = this.windowY + HEADER_HEIGHT + 1;
        int contentW = this.windowW - SIDEBAR_WIDTH - 2;
        int contentH = this.windowH - HEADER_HEIGHT - 2;

        if (isInside(mouseX, mouseY, contentX, contentY, contentW, contentH)) {
            boolean showingModules = !this.searchQuery.isEmpty() || this.activeNav == NavTab.MODULES;
            if (showingModules) {
                if (handleModulesWorkspaceClick(mouseX, mouseY, button, contentX, contentY, contentW, contentH)) {
                    return true;
                }
            } else if (this.activeNav == NavTab.MACROS) {
                if (handleMacrosClick(mouseX, mouseY, button, contentX, contentY, contentW, contentH)) {
                    return true;
                }
            } else if (this.activeNav == NavTab.SETTINGS) {
                if (handleClientSettingsClick(mouseX, mouseY, button, contentX, contentY, contentW)) {
                    return true;
                }
            }
        }

        return super.mouseClicked(event, wasHandled);
    }

    private boolean handleModulesWorkspaceClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW, int contentH) {
        // 1. Category chip filter bar
        if (mouseY < contentY + CHIP_BAR_HEIGHT) {
            if (MouseButtonMapping.isLeftClick(button)) {
                List<FilterChip> chips = getFilterChips();
                int curX = contentX + 12;
                int chipY = contentY + 6;
                int chipH = 18;
                for (FilterChip chip : chips) {
                    int chipW = Math.max(28, this.font.width(chip.label + " " + chip.count) + 18);
                    if (curX + chipW > contentX + contentW - 8) break;
                    if (isInside(mouseX, mouseY, curX, chipY, chipW, chipH)) {
                        this.selectedCategoryId = chip.id;
                        this.scrollAmount = 0.0;
                        if (!this.searchQuery.isEmpty() && this.searchBox != null) {
                            this.searchBox.setValue("");
                            this.searchQuery = "";
                        }
                        if (this.expandedModule != null) {
                            if (findModuleIndex(getVisibleModules(), this.expandedModule) < 0) {
                                clearExpandedImmediate();
                            } else {
                                this.ensureExpandedVisible = true;
                            }
                        }
                        playClickSound();
                        return true;
                    }
                    curX += chipW + 5;
                }
            }
            return true;
        }

        int bodyY = contentY + CHIP_BAR_HEIGHT;
        int bodyH = contentH - CHIP_BAR_HEIGHT;
        if (!isInside(mouseX, mouseY, contentX, bodyY, contentW, bodyH)) {
            return false;
        }

        ResolvedAccordionPlan resolved = resolveAccordionPlan(contentX, bodyY, contentW);
        ModernModuleGridLayout.GridPlan plan = resolved.plan();
        ModernModuleGridLayout.AccordionBounds acc = plan.accordion();

        // 2. Module Grid & Unified Card Accordion interaction
        List<ClickGuiScreen.ModuleEntry> modules = resolved.modules();
        int colW = plan.metrics().colWidth();
        int cardH = plan.metrics().cardHeight();
        ModConfig cfg = ModConfig.getInstance();

        for (int i = 0; i < modules.size(); i++) {
            int cx = plan.cardX(contentX, i);
            int cy = plan.cardY(resolved.startY(), i);
            boolean isAccordionCard = acc != null && i == plan.expandedIndex();
            int cardTotalH = cardH + (isAccordionCard ? acc.visibleHeight() : 0);

            if (isInside(mouseX, mouseY, cx, cy, colW, cardTotalH)) {
                ClickGuiScreen.ModuleEntry mod = modules.get(i);
                if (isAccordionCard && mouseY >= cy + cardH) {
                    if (MouseButtonMapping.isRightClick(button)) {
                        closeExpandedModule();
                        playClickSound();
                        return true;
                    }
                    if (this.expandedModule != null && MouseButtonMapping.isLeftClick(button)) {
                        int controlsY = cy + cardH + 1 + resolved.descHeaderHeight();
                        ModernModuleCardUi.handleControlsClick(
                                mouseX,
                                mouseY,
                                cx + ModernModuleGridLayout.CARD_PADDING_X,
                                controlsY,
                                acc.controlsWidth(),
                                resolved.expandedControls(),
                                (slider, startX, width, initialPct) -> {
                                    this.draggingSliderKey = slider.key();
                                    this.activeSlider = slider;
                                    this.draggingSliderStartX = startX;
                                    this.draggingSliderWidth = width;
                                    slider.pctSetter().accept(initialPct);
                                },
                                this::playClickSound
                        );
                    }
                    return true;
                }

                CardControlsBounds controls = computeCardControls(cx, cy, colW, mod, cfg);
                ModernModuleGridLayout.CardControlLayout layout = controls.layout();

                // Keybind Pill (immediately left of toggle switch)
                if (isInside(mouseX, mouseY, layout.bindX(), layout.bindY(), layout.bindW(), layout.bindH())) {
                    if (MouseButtonMapping.isRightClick(button)) {
                        cfg.setModuleKey(mod.name, -1, false);
                        this.listeningKeybindModule = null;
                        cfg.save();
                        playClickSound();
                    } else if (MouseButtonMapping.isLeftClick(button)) {
                        this.listeningKeybindModule = controls.isListening() ? null : mod.name;
                        cfg.save();
                        playClickSound();
                    }
                    return true;
                }

                // Settings Tune Icon (immediately left of keybind pill, only on configurable modules)
                if (controls.hasSettings()
                        && isInside(mouseX, mouseY, layout.tuneX(), layout.tuneY(), layout.tuneW(), layout.tuneH())
                        && (MouseButtonMapping.isLeftClick(button) || MouseButtonMapping.isRightClick(button))) {
                    toggleExpandedModule(mod.name);
                    return true;
                }

                // Main Card Body / Toggle Switch: Left-click toggles ON/OFF, Right-click expands Settings (if configurable)
                if (MouseButtonMapping.isLeftClick(button)) {
                    mod.setter.accept(!mod.getter.get());
                    playClickSound();
                    return true;
                }
                if (MouseButtonMapping.isRightClick(button)) {
                    if (controls.hasSettings()) {
                        toggleExpandedModule(mod.name);
                    }
                    return true;
                }
                return true;
            }
        }

        return false;
    }

    private boolean handleMacrosClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW, int contentH) {
        ModConfig config = ModConfig.getInstance();
        if (config.macros == null) {
            config.macros = new ArrayList<>();
        }

        int innerY = contentY + 12;
        int leftColW = Math.max(260, contentW - 255);
        int leftColX = contentX + 14;
        int rightColX = leftColX + leftColW + 12;
        int rightColW = contentW - leftColW - 38;
        int rightColH = contentH - 24;

        if (isInside(mouseX, mouseY, rightColX, innerY - 2, rightColW, rightColH)) {
            if (this.editingMacroIndex != -1) {
                int formY = innerY + 36 + 80;
                int boxW = Math.max(120, rightColW - 24);
                int bindBtnX = rightColX + 12;
                int bindBtnY = formY + 12;

                if (isInside(mouseX, mouseY, bindBtnX, bindBtnY, boxW, 22)) {
                    if (MouseButtonMapping.isRightClick(button)) {
                        this.tempMacroKey = -1;
                        this.tempMacroIsMouse = false;
                        this.listeningMacroKey = false;
                        playClickSound();
                    } else if (MouseButtonMapping.isLeftClick(button)) {
                        this.listeningMacroKey = !this.listeningMacroKey;
                        playClickSound();
                    }
                    return true;
                }

                if (!MouseButtonMapping.isLeftClick(button)) {
                    return false;
                }

                formY += 56;
                int btnH = 24;
                int btnW = (boxW - 8) / 2;
                int saveBtnX = rightColX + 12;
                int cancelBtnX = saveBtnX + btnW + 8;

                if (isInside(mouseX, mouseY, saveBtnX, formY, btnW, btnH)) {
                    String name = (this.macroNameBox != null) ? this.macroNameBox.getValue().trim() : "";
                    if (name.isEmpty()) name = "Macro " + (config.macros.size() + 1);
                    String cmd = (this.macroCmdBox != null) ? this.macroCmdBox.getValue().trim() : "";
                    if (cmd.isEmpty()) cmd = "/help";

                    if (this.editingMacroIndex == -2) {
                        ModConfig.MacroEntry m = new ModConfig.MacroEntry();
                        m.name = name;
                        m.message = cmd;
                        m.keyCode = this.tempMacroKey;
                        m.isMouse = this.tempMacroIsMouse;
                        config.macros.add(m);
                    } else if (this.editingMacroIndex >= 0 && this.editingMacroIndex < config.macros.size()) {
                        ModConfig.MacroEntry m = config.macros.get(this.editingMacroIndex);
                        m.name = name;
                        m.message = cmd;
                        m.keyCode = this.tempMacroKey;
                        m.isMouse = this.tempMacroIsMouse;
                    }
                    config.save();
                    closeMacroEditor();
                    playClickSound();
                    return true;
                }

                if (isInside(mouseX, mouseY, cancelBtnX, formY, btnW, btnH)) {
                    closeMacroEditor();
                    playClickSound();
                    return true;
                }
            } else {
                int addBtnX = rightColX + 12;
                int addBtnY = innerY + 36;
                if (MouseButtonMapping.isLeftClick(button) && isInside(mouseX, mouseY, addBtnX, addBtnY, rightColW - 24, 26)) {
                    this.editingMacroIndex = -2;
                    this.listeningMacroKey = false;
                    this.tempMacroKey = -1;
                    this.tempMacroIsMouse = false;
                    if (this.macroNameBox != null) {
                        this.macroNameBox.setValue("");
                        this.macroNameBox.visible = true;
                        this.macroNameBox.setFocused(true);
                    }
                    if (this.macroCmdBox != null) {
                        this.macroCmdBox.setValue("");
                        this.macroCmdBox.visible = true;
                    }
                    playClickSound();
                    return true;
                }
            }
        }

        int listStartY = innerY + 24;
        int cardH = 46;
        int cardGap = 8;

        for (int i = 0; i < config.macros.size(); i++) {
            int cardY = listStartY + i * (cardH + cardGap) - (int) this.scrollAmount;
            if (cardY + cardH < contentY || cardY > contentY + contentH) continue;

            if (isInside(mouseX, mouseY, leftColX, cardY, leftColW, cardH)) {
                ModConfig.MacroEntry m = config.macros.get(i);
                int delBtnX = leftColX + leftColW - 28;
                int btnY = cardY + 14;
                if (MouseButtonMapping.isLeftClick(button) && isInside(mouseX, mouseY, delBtnX, btnY, 22, 18)) {
                    config.macros.remove(i);
                    if (this.editingMacroIndex == i) {
                        closeMacroEditor();
                    } else if (this.editingMacroIndex > i) {
                        this.editingMacroIndex--;
                    }
                    config.save();
                    playClickSound();
                    return true;
                }

                int editBtnX = leftColX + leftColW - 56;
                if (MouseButtonMapping.isLeftClick(button) && isInside(mouseX, mouseY, editBtnX, btnY, 22, 18)) {
                    loadMacroIntoEditor(i, m);
                    playClickSound();
                    return true;
                }

                String bindText = ClickGuiScreen.getBindDisplayName(m.keyCode, m.isMouse);
                int bindBadgeW = Math.max(48, this.font.width(bindText) + 14);
                int bindBadgeX = leftColX + leftColW - 64 - bindBadgeW;
                if (isInside(mouseX, mouseY, bindBadgeX, btnY, bindBadgeW, 18)) {
                    if (MouseButtonMapping.isRightClick(button)) {
                        m.keyCode = -1;
                        m.isMouse = false;
                        if (this.editingMacroIndex == i) {
                            this.tempMacroKey = -1;
                            this.tempMacroIsMouse = false;
                            this.listeningMacroKey = false;
                        }
                        config.save();
                        playClickSound();
                    } else if (MouseButtonMapping.isLeftClick(button)) {
                        loadMacroIntoEditor(i, m);
                        this.listeningMacroKey = true;
                        playClickSound();
                    }
                    return true;
                }

                if (MouseButtonMapping.isLeftClick(button)) {
                    loadMacroIntoEditor(i, m);
                    playClickSound();
                    return true;
                }
                return false;
            }
        }

        return false;
    }

    private void loadMacroIntoEditor(int index, ModConfig.MacroEntry m) {
        this.editingMacroIndex = index;
        this.listeningMacroKey = false;
        this.tempMacroKey = m.keyCode;
        this.tempMacroIsMouse = m.isMouse;
        if (this.macroNameBox != null) {
            this.macroNameBox.setValue(m.name != null ? m.name : "");
            this.macroNameBox.visible = true;
        }
        if (this.macroCmdBox != null) {
            this.macroCmdBox.setValue(m.message != null ? m.message : "");
            this.macroCmdBox.visible = true;
        }
    }

    private void closeMacroEditor() {
        this.editingMacroIndex = -1;
        this.listeningMacroKey = false;
        if (this.macroNameBox != null) this.macroNameBox.visible = false;
        if (this.macroCmdBox != null) this.macroCmdBox.visible = false;
    }

    private boolean handleClientSettingsClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW) {
        if (!MouseButtonMapping.isLeftClick(button)) return false;
        ModConfig config = ModConfig.getInstance();
        int topY = (int) (contentY + 12 - this.scrollAmount);
        int cardY = topY + 22;
        int cardH = 38;
        int colW = contentW - 28;

        for (ClientSettingItem item : getClientSettingItems(config)) {
            if (isInside(mouseX, mouseY, contentX + 14, cardY, colW, cardH)) {
                item.onToggle().run();
                config.save();
                playClickSound();
                return true;
            }
            cardY += cardH + 6;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingSliderKey != null) {
            boolean needReload = this.activeSlider != null && this.activeSlider.needsChunkReload();
            boolean wasInvAlpha = "invBgAlpha".equals(this.draggingSliderKey);
            this.draggingSliderKey = null;
            this.activeSlider = null;
            ModConfig.getInstance().save();
            if (needReload) ModConfig.reloadChunks();
            if (wasInvAlpha) ModernModuleCardUi.flushInventoryConfigSave();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingSliderKey != null) {
            updateSliderValue((int) event.x());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int contentX = this.windowX + SIDEBAR_WIDTH + 1;
        int contentY = this.windowY + HEADER_HEIGHT + 1;
        int contentW = this.windowW - SIDEBAR_WIDTH - 2;
        int contentH = this.windowH - HEADER_HEIGHT - 2;

        boolean showingModules = !this.searchQuery.isEmpty() || this.activeNav == NavTab.MODULES;
        int viewH = showingModules ? Math.max(0, contentH - CHIP_BAR_HEIGHT) : contentH;
        int totalH = showingModules
                ? resolveAccordionPlan(contentX, contentY + CHIP_BAR_HEIGHT, contentW).plan().totalHeight()
                : this.contentTotalHeight;
        int maxScroll = Math.max(0, totalH - viewH);
        if (maxScroll > 0) {
            this.scrollAmount = Mth.clamp(this.scrollAmount - scrollY * 26.0, 0.0, maxScroll);
            this.ensureExpandedVisible = false;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.listeningMacroKey) {
            int key = event.key();
            if (key == InputConstants.KEY_ESCAPE) {
                this.listeningMacroKey = false;
            } else {
                this.tempMacroKey = key;
                this.tempMacroIsMouse = false;
                this.listeningMacroKey = false;
                if (this.editingMacroIndex >= 0 && this.editingMacroIndex < ModConfig.getInstance().macros.size()) {
                    ModConfig.MacroEntry m = ModConfig.getInstance().macros.get(this.editingMacroIndex);
                    m.keyCode = this.tempMacroKey;
                    m.isMouse = false;
                    ModConfig.getInstance().save();
                }
            }
            playClickSound();
            return true;
        }

        if (this.listeningKeybindModule != null) {
            int key = event.key();
            ModConfig config = ModConfig.getInstance();
            if (key == InputConstants.KEY_ESCAPE) {
                this.listeningKeybindModule = null;
            } else {
                config.setModuleKey(this.listeningKeybindModule, key, false);
                this.listeningKeybindModule = null;
            }
            config.save();
            playClickSound();
            return true;
        }

        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (this.searchBox != null && this.searchBox.isFocused() && !this.searchQuery.isEmpty()) {
                this.searchBox.setValue("");
                return true;
            }
            if (this.editingMacroIndex != -1) {
                closeMacroEditor();
                return true;
            }
            if (isExpandedOpen() && (!this.searchQuery.isEmpty() || this.activeNav == NavTab.MODULES)) {
                closeExpandedModule();
                return true;
            }
            onClose();
            return true;
        }

        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return super.charTyped(event);
    }

    private void updateSliderValue(int mouseX) {
        if (this.draggingSliderKey == null || this.draggingSliderWidth <= 0) return;
        float pct = Mth.clamp((float) (mouseX - this.draggingSliderStartX) / (float) this.draggingSliderWidth, 0.0f, 1.0f);
        if (this.activeSlider != null) {
            this.activeSlider.pctSetter().accept(pct);
        }
    }

    private boolean isExpandedOpen() {
        return this.expandedModule != null && findModuleIndex(getVisibleModules(), this.expandedModule) >= 0;
    }

    private static int findModuleIndex(List<ClickGuiScreen.ModuleEntry> modules, String modName) {
        if (modName == null || modules == null) {
            return -1;
        }
        for (int i = 0; i < modules.size(); i++) {
            if (modName.equals(modules.get(i).name)) {
                return i;
            }
        }
        return -1;
    }

    private void toggleExpandedModule(String modName) {
        if (modName != null && modName.equals(this.expandedModule)) {
            closeExpandedModule();
        } else {
            if (modName != null && !modName.equals(this.renderedExpandedModule)) {
                this.expandAnim = 0.0f;
            }
            this.expandedModule = modName;
            this.renderedExpandedModule = modName;
            this.draggingSliderKey = null;
            this.activeSlider = null;
            this.ensureExpandedVisible = true;
        }
        playClickSound();
    }

    private void closeExpandedModule() {
        this.expandedModule = null;
        this.ensureExpandedVisible = false;
        this.draggingSliderKey = null;
        this.activeSlider = null;
    }

    private void clearExpandedImmediate() {
        this.expandedModule = null;
        this.renderedExpandedModule = null;
        this.expandAnim = 0.0f;
        this.ensureExpandedVisible = false;
        this.draggingSliderKey = null;
        this.activeSlider = null;
    }

    private record FilterChip(String id, String label, int count) {}

    private List<FilterChip> getFilterChips() {
        List<ClickGuiScreen.ModuleEntry> all = ClickGuiScreen.getAllModules();
        List<FilterChip> chips = new ArrayList<>();
        chips.add(new FilterChip("ALL", "All", all.size()));

        ClickGuiScreen.Category[] cats = {
                ClickGuiScreen.Category.RENDER,
                ClickGuiScreen.Category.PLAYER,
                ClickGuiScreen.Category.WORLD,
                ClickGuiScreen.Category.MOVEMENT,
                ClickGuiScreen.Category.EXPLOIT,
                ClickGuiScreen.Category.MISC
        };
        for (ClickGuiScreen.Category cat : cats) {
            int count = 0;
            for (ClickGuiScreen.ModuleEntry m : all) {
                if (m.category == cat) count++;
            }
            if (count > 0) {
                chips.add(new FilterChip(cat.id, cat.name, count));
            }
        }
        return chips;
    }

    private List<ClickGuiScreen.ModuleEntry> getVisibleModules() {
        return ModernModuleGridLayout.filterAndSortModules(
                ClickGuiScreen.getAllModules(),
                m -> m.name,
                m -> m.category.id,
                m -> m.category.name,
                this.selectedCategoryId,
                this.searchQuery
        );
    }

    private static int countActiveModules() {
        int active = 0;
        for (ClickGuiScreen.ModuleEntry m : ClickGuiScreen.getAllModules()) {
            if (m.getter.get()) active++;
        }
        return active;
    }

    private static String getBindPillText(ModConfig cfg, String modName, boolean isListening) {
        if (isListening) return "§e[...]";
        int key = cfg.getModuleKey(modName);
        boolean isMouse = cfg.isModuleKeyMouse(modName);
        return "[" + ClickGuiScreen.getShortBindName(key, isMouse) + "]";
    }

    private String fitText(String text, int maxWidth) {
        if (text == null || maxWidth <= 8 || this.font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String trimmed = text;
        while (trimmed.length() > 2 && this.font.width(trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void playClickSound() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void onClose() {
        ModConfig.getInstance().save();
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
