package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
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
        GUI("GUI"),
        CONFIG("Config"),
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
    private static final int SEARCH_BOX_WIDTH = 196;
    private static final int SEARCH_BOX_HEIGHT = 22;

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
    private final Map<String, Float> hoverAnimations = new HashMap<>();

    // Search state
    private EditBox searchBox;
    private String searchQuery = "";

    // Scroll & Inline Accordion state
    private double scrollAmount = 0.0;
    private int contentTotalHeight = 0;
    private boolean draggingScrollbar = false;
    private double scrollbarDragOffset = 0.0;
    private String expandedModule = null;
    private String renderedExpandedModule = null;
    private float expandAnim = 0.0f;
    private boolean ensureExpandedVisible = false;
    private boolean expandedBySearch = false;

    // Slider drag state
    private String draggingSliderKey = null;
    private ModernModuleSettings.SliderControl activeSlider = null;
    private int draggingSliderStartX = 0;
    private int draggingSliderWidth = 100;

    // Inline slider numeric editing state
    private EditBox sliderInputBox;
    private ModernModuleSettings.SliderControl editingSlider = null;
    private String editingSliderText = "";
    private int editingBoxX = 0;
    private int editingBoxY = 0;
    private int editingBoxW = 0;
    private int editingBoxH = 0;
    private char lastSliderKeyChar = 0;
    private long lastSliderKeyTimeMs = 0L;

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

    // Config profiles state
    private EditBox profileNameBox;
    private EditBox profileCodeBox;
    private List<ConfigProfileManager.ProfileSummary> cachedProfiles = List.of();
    private String profileStatusText = null;
    private boolean profileStatusError = false;
    private long profileStatusUntilMs = 0L;

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
        cancelEditingSlider();
        if (this.expandedModule != null) {
            this.ensureExpandedVisible = true;
        }

        if (this.indicatorY < 0) {
            this.indicatorY = this.windowY + HEADER_HEIGHT + 10 + this.activeNav.ordinal() * NAV_ROW_HEIGHT;
        }

        int searchX = this.windowX + this.windowW - SEARCH_BOX_WIDTH - 44;
        int searchBoxY = this.windowY + (HEADER_HEIGHT - SEARCH_BOX_HEIGHT) / 2;

        String prevQuery = this.searchQuery;
        this.searchBox = new EditBox(this.font, searchX + 26, searchBoxY + 7, SEARCH_BOX_WIDTH - 44, 14, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("Search modules..."));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(30);
        this.searchBox.setValue(prevQuery);
        this.searchBox.setResponder(text -> {
            this.searchQuery = text.trim();
            this.scrollAmount = 0.0;
            this.draggingSliderKey = null;
            this.activeSlider = null;
            cancelEditingSlider();
            List<ClickGuiScreen.ModuleEntry> visible = getVisibleModules();
            String subMatch = findSubSettingAutoExpandTarget(visible, this.searchQuery);
            if (subMatch != null) {
                this.expandedModule = subMatch;
                this.renderedExpandedModule = subMatch;
                this.expandAnim = 1.0f;
                this.ensureExpandedVisible = true;
                this.expandedBySearch = true;
            } else if (this.searchQuery.isEmpty() && this.expandedBySearch) {
                closeExpandedModule();
            } else if (this.expandedModule != null) {
                if (findModuleIndex(visible, this.expandedModule) < 0) {
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

        this.profileNameBox = new EditBox(this.font, 0, 0, 180, 20, Component.literal("Profile Name"));
        this.profileNameBox.setHint(Component.literal("e.g. PvP, Survival, Mine"));
        this.profileNameBox.setBordered(false);
        this.profileNameBox.setMaxLength(32);
        this.profileNameBox.visible = false;
        this.addRenderableWidget(this.profileNameBox);

        this.profileCodeBox = new EditBox(this.font, 0, 0, 180, 20, Component.literal("Share Code"));
        this.profileCodeBox.setHint(Component.literal("Paste SESTIA1:... code"));
        this.profileCodeBox.setBordered(false);
        this.profileCodeBox.setMaxLength(32768);
        this.profileCodeBox.visible = false;
        this.addRenderableWidget(this.profileCodeBox);

        this.sliderInputBox = new EditBox(this.font, 0, 0, 42, 9, Component.literal("Value"));
        this.sliderInputBox.setBordered(false);
        this.sliderInputBox.setMaxLength(16);
        this.sliderInputBox.setTextColor(0xFFFFFFFF);
        this.sliderInputBox.setResponder(text -> {
            if (this.editingSlider != null) {
                this.editingSliderText = text != null ? text : "";
            }
        });
        this.sliderInputBox.visible = false;
        this.addRenderableWidget(this.sliderInputBox);

        refreshProfilesList();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
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
        graphics.fill(0, 0, this.width, this.height, JelloTheme.BACKDROP_DIM);

        // Soft dark elevation shadow + themed shell
        JelloTheme.drawGlowShadow(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, 0x66000000, 4);
        JelloTheme.fillRoundedCard(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, JelloTheme.WINDOW_BG, JelloTheme.WINDOW_BORDER);

        renderHeader(graphics, mouseX, mouseY);
        renderSidebar(graphics, mouseX, mouseY, dt);

        int contentX = this.windowX + SIDEBAR_WIDTH + 1;
        int contentY = this.windowY + HEADER_HEIGHT + 1;
        int contentW = this.windowW - SIDEBAR_WIDTH - 2;
        int contentH = this.windowH - HEADER_HEIGHT - 2;

        boolean showingModules = !this.searchQuery.isEmpty() || this.activeNav == NavTab.MODULES;
        if (showingModules) {
            setSecondaryInputsVisible(false, false);
            renderModulesWorkspace(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
        } else {
            if (this.sliderInputBox != null) {
                this.sliderInputBox.visible = false;
            }
            if (this.activeNav == NavTab.MACROS) {
                setSecondaryInputsVisible(this.editingMacroIndex != -1, false);
                graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
                renderMacrosView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
                graphics.disableScissor();
                renderScrollbarAt(graphics, contentX + contentW - 6, contentY, contentH, this.contentTotalHeight, this.scrollAmount);
            } else if (this.activeNav == NavTab.GUI) {
                setSecondaryInputsVisible(false, false);
                renderGuiView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH);
            } else if (this.activeNav == NavTab.CONFIG) {
                setSecondaryInputsVisible(false, true);
                renderProfilesView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH);
                renderScrollbarAt(graphics, contentX + contentW - 6, contentY, contentH, this.contentTotalHeight, this.scrollAmount);
            } else if (this.activeNav == NavTab.SETTINGS) {
                setSecondaryInputsVisible(false, false);
                graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);
                renderSettingsView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH);
                graphics.disableScissor();
                renderScrollbarAt(graphics, contentX + contentW - 6, contentY, contentH, this.contentTotalHeight, this.scrollAmount);
            }
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

        // Brand icon + Name + Version badge (classic crisp default font)
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                Identifier.fromNamespaceAndPath("sestia", "icons/icon_32x32.png"),
                headX + 12, headY + (HEADER_HEIGHT - 16) / 2,
                0.0f, 0.0f, 16, 16, 16, 16
        );
        Component titleComp = Component.literal("§lSESTIA");
        graphics.text(this.font, titleComp, headX + 34, headY + 18, 0xFFFFFFFF, true);
        int titleW = this.font.width(titleComp);

        String verLabel = "v" + ClientBuildInfo.getVersion();
        Component verComp = Component.literal(verLabel);
        int verX = headX + 34 + titleW + 8;
        int verY = headY + 15;
        int verW = this.font.width(verComp) + 12;
        JelloTheme.fillRoundedCard(graphics, verX, verY, verX + verW, verY + 14, 4, 0x55FF1A2B, 0xFFFF2E3E);
        graphics.text(this.font, verComp, verX + 6, verY + 3, 0xFFFFFFFF, true);

        // Active module count pill
        int activeCount = countActiveModules();
        String activeText = activeCount + " Active";
        Component activeComp = Component.literal(activeText);
        int activeX = verX + verW + 8;
        int activeW = this.font.width(activeComp) + 18;
        if (activeX + activeW < headX + headW - SEARCH_BOX_WIDTH - 56) {
            JelloTheme.fillRoundedCard(graphics, activeX, verY, activeX + activeW, verY + 14, 4, 0x3A10B981, 0xCC10B981);
            JelloTheme.fillCircle(graphics, activeX + 6, verY + 7, 2, 0xFF4ADE80);
            graphics.text(this.font, activeComp, activeX + 12, verY + 3, 0xFF86EFAC, false);
        }

        // Clean OLED black Search container
        int searchBoxX = headX + headW - SEARCH_BOX_WIDTH - 44;
        int searchBoxY = headY + (HEADER_HEIGHT - SEARCH_BOX_HEIGHT) / 2;
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        int sBg = searchFocused ? 0xF5141419 : 0xEE0D0D11;
        int sBorder = searchFocused ? JelloTheme.ACCENT : 0x34FFFFFF;
        JelloTheme.fillRoundedCard(graphics, searchBoxX, searchBoxY, searchBoxX + SEARCH_BOX_WIDTH, searchBoxY + SEARCH_BOX_HEIGHT, 6, sBg, sBorder);
        ModernIcons.drawSearchIcon(graphics, searchBoxX + 9, searchBoxY + 6, searchFocused ? JelloTheme.ACCENT_HOVER : 0xFFCBD5E1);

        if (!this.searchQuery.isEmpty()) {
            int clearCx = searchBoxX + SEARCH_BOX_WIDTH - 10;
            int clearCy = searchBoxY + SEARCH_BOX_HEIGHT / 2;
            boolean clearHov = isInside(mouseX, mouseY, searchBoxX + SEARCH_BOX_WIDTH - 18, searchBoxY, 18, SEARCH_BOX_HEIGHT);
            ModernIcons.drawCloseIcon(graphics, clearCx, clearCy, clearHov ? JelloTheme.ACCENT_HOVER : JelloTheme.TEXT_MUTED);
        }

        // Close button
        int closeX = headX + headW - 30;
        int closeY = headY + 12;
        boolean closeHovered = isInside(mouseX, mouseY, closeX, closeY, 20, 20);
        JelloTheme.fillRoundedCard(graphics, closeX, closeY, closeX + 20, closeY + 20, 5,
                closeHovered ? 0x66FF1E2D : JelloTheme.CARD_INACTIVE,
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
        JelloTheme.fillRoundedCard(graphics, sideX + 8, indY + 2, sideX + sideW - 8, indY + NAV_ROW_HEIGHT - 2, 6, 0x38FF1E2D, 0xBBFF1E2D);

        NavTab[] tabs = NavTab.values();
        for (int i = 0; i < tabs.length; i++) {
            NavTab tab = tabs[i];
            int itemY = listTop + i * NAV_ROW_HEIGHT;
            boolean isCurrent = tab == this.activeNav && this.searchQuery.isEmpty();
            boolean hovered = isInside(mouseX, mouseY, sideX + 6, itemY, sideW - 12, NAV_ROW_HEIGHT);

            if (hovered && !isCurrent) {
                JelloTheme.fillRounded(graphics, sideX + 8, itemY + 2, sideX + sideW - 8, itemY + NAV_ROW_HEIGHT - 2, 6, 0x16FFFFFF);
            }

            int iconColor = isCurrent ? JelloTheme.ACCENT_HOVER : (hovered ? JelloTheme.ACCENT_HOVER : JelloTheme.TEXT_MUTED);
            int textColor = isCurrent ? JelloTheme.TEXT : (hovered ? 0xFFE2E8F0 : JelloTheme.TEXT_MUTED);

            int iconX = sideX + 18;
            int iconY = itemY + 12;
            switch (tab) {
                case MODULES -> ModernIcons.drawGridIcon(graphics, iconX, iconY, iconColor);
                case MACROS -> ModernIcons.drawBoltIcon(graphics, iconX, iconY, iconColor);
                case GUI -> ModernIcons.drawLayoutIcon(graphics, iconX, iconY, iconColor);
                case CONFIG -> ModernIcons.drawSlidersIcon(graphics, iconX, iconY, iconColor);
                case SETTINGS -> ModernIcons.drawTuneIcon(graphics, iconX, iconY, iconColor);
            }

            graphics.text(this.font, Component.literal(tab.label), sideX + 35, itemY + 13, textColor, isCurrent);
        }

        // Interaction legend card at bottom of sidebar (minimal Controls only)
        if (sideH > 235) {
            int legX = sideX + 10;
            int legW = sideW - 20;
            int legH = 56;
            int legY = sideY + sideH - legH - 10;
            JelloTheme.fillRoundedCard(graphics, legX, legY, legX + legW, legY + legH, 6, JelloTheme.CONTAINER_BG, JelloTheme.BORDER_SUBTLE);
            graphics.text(this.font, Component.literal("Controls"), legX + 8, legY + 7, JelloTheme.ACCENT_HOVER, false);
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
        renderCategoryChipBar(graphics, mouseX, mouseY, contentX, contentY, contentW, dt);

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

    private void renderCategoryChipBar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, float dt) {
        graphics.fill(contentX, contentY + CHIP_BAR_HEIGHT - 1, contentX + contentW, contentY + CHIP_BAR_HEIGHT, JelloTheme.DIVIDER);

        List<FilterChip> chips = getFilterChips();
        int curX = contentX + 12;
        int chipY = contentY + 6;
        int chipH = 18;

        for (FilterChip chip : chips) {
            String text = chip.label + " " + chip.count;
            int chipW = Math.max(30, this.font.width(text) + 18);
            if (curX + chipW > contentX + contentW - 8) {
                break;
            }
            boolean active = this.searchQuery.isEmpty() && chip.id.equals(this.selectedCategoryId);
            boolean hovered = isInside(mouseX, mouseY, curX, chipY, chipW, chipH);
            float hoverT = animateHover("chip:" + chip.id, hovered, dt);
            int bg = active
                    ? JelloTheme.lerpColor(JelloTheme.ACCENT, JelloTheme.ACCENT_HOVER, hoverT)
                    : JelloTheme.lerpColor(JelloTheme.CARD_INACTIVE, JelloTheme.CARD_INACTIVE_HOVER, hoverT);
            int border = active
                    ? JelloTheme.ACCENT_HOVER
                    : JelloTheme.lerpColor(JelloTheme.BORDER_SUBTLE, 0x5594A3B8, hoverT);
            JelloTheme.fillRoundedCard(graphics, curX, chipY, curX + chipW, chipY + chipH, 4, bg, border);
            String chipText = fitText(text, chipW - 12);
            graphics.centeredText(this.font, Component.literal(chipText), curX + chipW / 2, chipY + 5,
                    active ? JelloTheme.TEXT : JelloTheme.lerpColor(JelloTheme.TEXT_MUTED, JelloTheme.TEXT, hoverT));
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
            JelloTheme.fillRoundedCard(graphics, gridX + 12, emptyY, gridX + gridW - 12, emptyY + 54, 10, JelloTheme.CARD_INACTIVE, JelloTheme.BORDER_SUBTLE);
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
        float hoverT = animateHover("card:" + mod.name, hovered, dt);

        if (active) {
            JelloTheme.drawGlowShadow(graphics, x, y, x + w, y + cardTotalH, ModernModuleGridLayout.CARD_CORNER_RADIUS,
                    isExpanded ? 0x55FF1E2D : 0x3FFF1E2D, 3);
        }

        int bg = active
                ? JelloTheme.lerpColor(JelloTheme.CARD_ACTIVE, JelloTheme.CARD_ACTIVE_HOVER, hoverT)
                : JelloTheme.lerpColor(JelloTheme.CARD_INACTIVE, JelloTheme.CARD_INACTIVE_HOVER, hoverT);
        int border = isExpanded
                ? JelloTheme.BORDER_EXPANDED_SOFT
                : (active
                    ? JelloTheme.lerpColor(JelloTheme.BORDER_ACTIVE_SOFT, JelloTheme.ACCENT_HOVER, hoverT)
                    : JelloTheme.lerpColor(JelloTheme.BORDER_SUBTLE, 0x5094A3B8, hoverT));
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + cardTotalH, ModernModuleGridLayout.CARD_CORNER_RADIUS, bg, border);

        // Compact 2-Row Bento Tile: Classic crisp Title (top) + borderless [Tune] [Bind] [Pill Toggle] (bottom-right)
        String title = fitText(mod.name, layout.titleMaxW());
        int titleColor = active ? 0xFFFFFFFF : JelloTheme.lerpColor(0xFFE2E8F0, 0xFFFFFFFF, hoverT);
        graphics.text(this.font, Component.literal(title),
                x + ModernModuleGridLayout.CARD_PAD_X,
                y + ModernModuleGridLayout.TITLE_Y_OFFSET,
                titleColor,
                active);

        if (controls.hasSettings()) {
            boolean tuneHov = isInside(mouseX, mouseY, layout.tuneX(), layout.tuneY(), layout.tuneW(), layout.tuneH());
            float tuneT = animateHover("tune:" + mod.name, tuneHov, dt);
            if (isExpanded || tuneT > 0.05f) {
                int tuneBg = isExpanded ? 0x55FF1E2D : JelloTheme.lerpColor(0x0016161D, 0x7724242F, tuneT);
                int tuneBorder = isExpanded ? JelloTheme.BORDER_EXPANDED_SOFT : JelloTheme.lerpColor(0x0094A3B8, 0x5594A3B8, tuneT);
                JelloTheme.fillRoundedCard(graphics, layout.tuneX(), layout.tuneY(), layout.tuneX() + layout.tuneW(), layout.tuneY() + layout.tuneH(), 4, tuneBg, tuneBorder);
            }
            ModernIcons.drawTuneIcon(graphics, layout.tuneX() + 4, layout.tuneY() + 3,
                    isExpanded ? JelloTheme.ACCENT_HOVER : JelloTheme.lerpColor(0xFF94A3B8, JelloTheme.TEXT, tuneT));
        }

        boolean bindHovered = isInside(mouseX, mouseY, layout.bindX(), layout.bindY(), layout.bindW(), layout.bindH());
        float bindT = animateHover("bind:" + mod.name, bindHovered, dt);
        boolean hasBoundKey = cfg.getModuleKey(mod.name) >= 0;
        if (controls.isListening() || hasBoundKey || bindT > 0.05f) {
            int bindBg = controls.isListening()
                    ? 0x66FF1E2D
                    : (hasBoundKey ? 0x33FF1E2D : JelloTheme.lerpColor(0x0016161D, 0x44FF1E2D, bindT));
            int bindBorder = controls.isListening()
                    ? JelloTheme.ACCENT_HOVER
                    : (hasBoundKey ? 0x99FF2838 : JelloTheme.lerpColor(0x00FF1E2D, 0x88FF1E2D, bindT));
            JelloTheme.fillRoundedCard(graphics, layout.bindX(), layout.bindY(), layout.bindX() + layout.bindW(), layout.bindY() + layout.bindH(), 3, bindBg, bindBorder);
        }
        // Keybind badges deliberately retain monospace font for crisp alignment; [None] renders borderless
        graphics.centeredText(this.font, Component.literal(controls.bindTag()), layout.bindX() + layout.bindW() / 2, layout.bindY() + 4,
                controls.isListening() ? 0xFFFFDD55 : (hasBoundKey ? (active ? JelloTheme.TEXT_DIM : 0xFFE2E8F0) : JelloTheme.TEXT_MUTED));

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
            ModernModuleCardUi.SliderEditState editState = this.editingSlider != null
                    ? new ModernModuleCardUi.SliderEditState(this.editingSlider.key(), this.editingSliderText, false)
                    : null;
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
                    (tx, ty, tw, th, tkey, tactive) -> renderSmoothSwitch(graphics, mouseX, mouseY, tx, ty, tw, th, tkey, tactive, dt),
                    editState,
                    (bx, by, bw, bh) -> {
                        this.editingBoxX = bx;
                        this.editingBoxY = by;
                        this.editingBoxW = bw;
                        this.editingBoxH = bh;
                        if (this.sliderInputBox != null && this.editingSlider != null) {
                            this.sliderInputBox.setX(bx + 4);
                            this.sliderInputBox.setY(by + 2);
                            this.sliderInputBox.setWidth(Math.max(16, bw - 8));
                            this.sliderInputBox.setHeight(9);
                            this.sliderInputBox.visible = true;
                        }
                    }
            );
            graphics.disableScissor();
        }
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
        JelloTheme.fillHorizontalGradient(graphics, leftColX, innerY + 13, leftColX + 155, innerY + 14, JelloTheme.ACCENT, 0x55FF424E);

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
                int borderColor = isEditingThis ? JelloTheme.ACCENT : (hovered ? 0xAAFF1E2D : JelloTheme.BORDER_SUBTLE);
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
                int bindBg = (isEditingThis && this.listeningMacroKey) ? 0x66FF1E2D : (bindHovered ? 0x44FF1E2D : 0xFF141419);
                int bindBorder = (isEditingThis && this.listeningMacroKey) ? JelloTheme.ACCENT_HOVER : (bindHovered ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
                JelloTheme.fillRoundedCard(graphics, bindBadgeX, bindBadgeY, bindBadgeX + bindBadgeW, bindBadgeY + bindBadgeH, 4, bindBg, bindBorder);
                graphics.centeredText(this.font, Component.literal(bindText), bindBadgeX + bindBadgeW / 2, bindBadgeY + 5,
                        (isEditingThis && this.listeningMacroKey) ? JelloTheme.ACCENT_HOVER : (m.keyCode >= 0 ? 0xFFFFD54F : JelloTheme.TEXT_MUTED));

                int editBtnX = leftColX + leftColW - 56;
                int editBtnY = cardY + 14;
                boolean editHovered = isInside(mouseX, mouseY, editBtnX, editBtnY, 22, 18);
                JelloTheme.fillRoundedCard(graphics, editBtnX, editBtnY, editBtnX + 22, editBtnY + 18, 4,
                        editHovered ? 0x55FF1E2D : 0xFF141419, editHovered ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
                ModernIcons.drawTuneIcon(graphics, editBtnX + 6, editBtnY + 4, editHovered ? JelloTheme.TEXT : JelloTheme.TEXT_MUTED);

                int delBtnX = leftColX + leftColW - 28;
                boolean delHovered = isInside(mouseX, mouseY, delBtnX, editBtnY, 22, 18);
                JelloTheme.fillRoundedCard(graphics, delBtnX, editBtnY, delBtnX + 22, editBtnY + 18, 4,
                        delHovered ? 0x77FF1E2D : 0xFF141419, delHovered ? JelloTheme.ACCENT_HOVER : JelloTheme.BORDER_SUBTLE);
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
            int bindBg = this.listeningMacroKey ? 0x55FF1E2D : (bindHovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
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
    // CONFIG PROFILES VIEW (SAVE / LOAD / EXPORT / IMPORT)
    // ==========================================
    private record ProfileLayout(
            int leftColX, int leftColW,
            int rightColX, int rightColY, int rightColW, int rightColH,
            int innerY, int listStartY,
            int folderBtnX, int folderBtnY, int folderBtnW, int folderBtnH,
            int boxX, int boxW, int inputBoxY,
            int saveBtnY, int importCodeBoxY, int importBtnY, int btnH
    ) {}

    private record ProfileCardButtons(
            int btnY,
            int loadBtnX, int loadBtnW,
            int exportBtnX, int exportBtnW,
            int delBtnX, int delBtnW
    ) {}

    private ProfileLayout computeProfileLayout(int contentX, int contentY, int contentW, int contentH) {
        int innerY = contentY + 12;
        int leftColW = Math.max(255, contentW - 255);
        int leftColX = contentX + 14;
        int rightColX = leftColX + leftColW + 12;
        int rightColY = innerY - 2;
        int rightColW = contentW - leftColW - 38;
        int rightColH = contentH - 24;
        int listStartY = innerY + 24;

        int folderBtnW = 74;
        int folderBtnH = 16;
        int folderBtnX = leftColX + leftColW - folderBtnW;
        int folderBtnY = innerY - 3;

        int boxX = rightColX + 12;
        int boxW = Math.max(120, rightColW - 24);
        int inputBoxY = rightColY + 41;
        int saveBtnY = rightColY + 67;
        int importCodeBoxY = rightColY + 113;
        int importBtnY = rightColY + 139;
        int btnH = 22;

        return new ProfileLayout(
                leftColX, leftColW,
                rightColX, rightColY, rightColW, rightColH,
                innerY, listStartY,
                folderBtnX, folderBtnY, folderBtnW, folderBtnH,
                boxX, boxW, inputBoxY,
                saveBtnY, importCodeBoxY, importBtnY, btnH
        );
    }

    private static ProfileCardButtons computeProfileCardButtons(ProfileLayout layout, int cardY) {
        int btnY = cardY + 14;
        int delBtnW = 22;
        int exportBtnW = 48;
        int loadBtnW = 40;
        int delBtnX = layout.leftColX() + layout.leftColW() - 28;
        int exportBtnX = delBtnX - exportBtnW - 6;
        int loadBtnX = exportBtnX - loadBtnW - 6;
        return new ProfileCardButtons(btnY, loadBtnX, loadBtnW, exportBtnX, exportBtnW, delBtnX, delBtnW);
    }

    private void renderGuiView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH) {
        ModConfig config = ModConfig.getInstance();
        int pad = 16;
        int innerX = contentX + pad;
        int innerW = contentW - pad * 2;
        int innerY = contentY + 14;

        ModernIcons.drawLayoutIcon(graphics, innerX, innerY - 1, JelloTheme.ACCENT);
        graphics.text(this.font, Component.literal("§lHUD LAYOUT & GUI"), innerX + 15, innerY, JelloTheme.TEXT, false);
        JelloTheme.fillHorizontalGradient(graphics, innerX, innerY + 13, innerX + 165, innerY + 14, JelloTheme.ACCENT, 0x55FF424E);

        int btnY = innerY + 24;
        int openBtnW = 148;
        int resetAllBtnW = 132;
        int btnH = 24;
        boolean openHov = isInside(mouseX, mouseY, innerX, btnY, openBtnW, btnH);
        JelloTheme.fillRoundedCard(graphics, innerX, btnY, innerX + openBtnW, btnY + btnH, 6,
                openHov ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT, 0xFFFF6674);
        graphics.centeredText(this.font, Component.literal("Open HUD Editor"), innerX + openBtnW / 2, btnY + 8, 0xFFFFFFFF);

        int resetAllX = innerX + openBtnW + 10;
        boolean resetAllHov = isInside(mouseX, mouseY, resetAllX, btnY, resetAllBtnW, btnH);
        JelloTheme.fillRoundedCard(graphics, resetAllX, btnY, resetAllX + resetAllBtnW, btnY + btnH, 6,
                resetAllHov ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE,
                resetAllHov ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        graphics.centeredText(this.font, Component.literal("Reset All Positions"), resetAllX + resetAllBtnW / 2, btnY + 8, JelloTheme.TEXT);

        int listY = btnY + btnH + 12;
        int cardH = 40;
        int cardGap = 6;

        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY, innerW, cardH,
                "Keybinds HUD",
                "Pos: " + config.keybindsHudX + ", " + config.keybindsHudY,
                config.keybindsHudEnabled, true, true, "Reset");

        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY + (cardH + cardGap), innerW, cardH,
                "Telemetry HUD (FPS / TPS / Ping / Speed)",
                "Pos: " + config.statsHudX + ", " + config.statsHudY,
                config.hudInfoEnabled && config.hudTelemetryEnabled, true, true, "Reset");

        String posDesc = (config.posHudX < 0 || config.posHudY < 0)
                ? "Pos: Default (Bottom-Right)"
                : ("Pos: " + config.posHudX + ", " + config.posHudY);
        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY + (cardH + cardGap) * 2, innerW, cardH,
                "Position HUD (Pos / Nether / Dir)",
                posDesc,
                config.hudInfoEnabled && config.hudPositionEnabled, true, true, "Reset");

        String clockDesc = (config.clockHudX < 0 || config.clockHudY < 0)
                ? "Pos: Default (Top-Right)  |  Europe/Istanbul"
                : ("Pos: " + config.clockHudX + ", " + config.clockHudY + "  |  Europe/Istanbul");
        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY + (cardH + cardGap) * 3, innerW, cardH,
                "Clock HUD (Istanbul)",
                clockDesc,
                config.hudInfoEnabled && config.hudShowClock, true, true, "Reset");

        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY + (cardH + cardGap) * 4, innerW, cardH,
                "Inventory HUD+",
                "Inventory, Potions & Armor overlays (edit in HUD Editor)",
                config.inventoryHudEnabled, true, false, "");

        String itemNotifierDesc = (config.itemNotifierX < 0 || config.itemNotifierY < 0)
                ? "Auto-placed near hotbar (drag in HUD Editor)"
                : ("Pos: " + config.itemNotifierX + ", " + config.itemNotifierY + "  |  Item Pickup/Drop");
        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY + (cardH + cardGap) * 5, innerW, cardH,
                "Item Notifier",
                itemNotifierDesc,
                config.itemNotifierEnabled, true, true, "Reset");
    }

    private void drawHudResetCard(
            GuiGraphicsExtractor graphics,
            int mouseX, int mouseY,
            int x, int y, int w, int h,
            String title, String subtitle,
            boolean enabled, boolean showToggle, boolean showActionBtn, String actionLabel
    ) {
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 6, JelloTheme.CARD_INACTIVE, JelloTheme.BORDER_SUBTLE);
        graphics.text(this.font, Component.literal(title), x + 12, y + 9, JelloTheme.TEXT, false);
        graphics.text(this.font, Component.literal(subtitle), x + 12, y + 24, JelloTheme.TEXT_MUTED, false);

        int rightEdge = x + w - 10;
        int btnH = 20;
        int btnY = y + (h - btnH) / 2;

        if (showActionBtn) {
            int actionW = Math.max(54, this.font.width(actionLabel) + 16);
            int actionX = rightEdge - actionW;
            boolean hov = isInside(mouseX, mouseY, actionX, btnY, actionW, btnH);
            JelloTheme.fillRoundedCard(graphics, actionX, btnY, actionX + actionW, btnY + btnH, 4,
                    hov ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CONTAINER_BG,
                    hov ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
            graphics.centeredText(this.font, Component.literal(actionLabel), actionX + actionW / 2, btnY + 6, JelloTheme.TEXT);
            rightEdge = actionX - 8;
        }

        if (showToggle) {
            int toggleW = 38;
            int toggleX = rightEdge - toggleW;
            boolean hov = isInside(mouseX, mouseY, toggleX, btnY, toggleW, btnH);
            int bg = enabled ? (hov ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT) : (hov ? 0x33FFFFFF : 0x22FFFFFF);
            int border = enabled ? 0xFFFF6674 : JelloTheme.BORDER_SUBTLE;
            JelloTheme.fillRoundedCard(graphics, toggleX, btnY, toggleX + toggleW, btnY + btnH, 4, bg, border);
            graphics.centeredText(this.font, Component.literal(enabled ? "ON" : "OFF"), toggleX + toggleW / 2, btnY + 6, 0xFFFFFFFF);
        }
    }

    private boolean handleGuiClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW, int contentH) {
        if (!MouseButtonMapping.isLeftClick(button)) {
            return false;
        }
        ModConfig config = ModConfig.getInstance();
        int pad = 16;
        int innerX = contentX + pad;
        int innerW = contentW - pad * 2;
        int innerY = contentY + 14;
        int btnY = innerY + 24;
        int openBtnW = 148;
        int resetAllBtnW = 132;
        int btnH = 24;

        if (isInside(mouseX, mouseY, innerX, btnY, openBtnW, btnH)) {
            playClickSound();
            Minecraft.getInstance().gui.setScreen(new HudLayoutEditorScreen(this));
            return true;
        }
        int resetAllX = innerX + openBtnW + 10;
        if (isInside(mouseX, mouseY, resetAllX, btnY, resetAllBtnW, btnH)) {
            HudLayoutEditorScreen.resetAllHudPositions(config);
            playClickSound();
            return true;
        }

        int listY = btnY + btnH + 12;
        int cardH = 40;
        int cardGap = 6;

        // Card 0: Keybinds HUD (Toggle + Reset)
        if (handleHudCardClick(mouseX, mouseY, innerX, listY, innerW, cardH, true, true, "Reset",
                () -> { config.keybindsHudEnabled = !config.keybindsHudEnabled; config.save(); },
                () -> { config.keybindsHudX = 8; config.keybindsHudY = 60; config.save(); })) {
            return true;
        }
        // Card 1: Telemetry HUD (Toggle + Reset)
        if (handleHudCardClick(mouseX, mouseY, innerX, listY + (cardH + cardGap), innerW, cardH, true, true, "Reset",
                () -> {
                    if (!config.hudInfoEnabled) { config.hudInfoEnabled = true; config.hudTelemetryEnabled = true; }
                    else { config.hudTelemetryEnabled = !config.hudTelemetryEnabled; }
                    config.save();
                },
                () -> { config.statsHudX = 4; config.statsHudY = 4; config.save(); })) {
            return true;
        }
        // Card 2: Position HUD (Toggle + Reset)
        if (handleHudCardClick(mouseX, mouseY, innerX, listY + (cardH + cardGap) * 2, innerW, cardH, true, true, "Reset",
                () -> {
                    if (!config.hudInfoEnabled) { config.hudInfoEnabled = true; config.hudPositionEnabled = true; }
                    else { config.hudPositionEnabled = !config.hudPositionEnabled; }
                    config.save();
                },
                () -> { config.posHudX = -1; config.posHudY = -1; config.save(); })) {
            return true;
        }
        // Card 3: Clock HUD (Istanbul) (Toggle + Reset)
        if (handleHudCardClick(mouseX, mouseY, innerX, listY + (cardH + cardGap) * 3, innerW, cardH, true, true, "Reset",
                () -> {
                    if (!config.hudInfoEnabled) { config.hudInfoEnabled = true; config.hudShowClock = true; }
                    else { config.hudShowClock = !config.hudShowClock; }
                    config.save();
                },
                () -> { config.clockHudX = -1; config.clockHudY = -1; config.save(); })) {
            return true;
        }
        // Card 4: Inventory HUD+ (Toggle only; positions edited in Open HUD Editor)
        if (handleHudCardClick(mouseX, mouseY, innerX, listY + (cardH + cardGap) * 4, innerW, cardH, true, false, "",
                () -> { config.inventoryHudEnabled = !config.inventoryHudEnabled; config.save(); },
                null)) {
            return true;
        }
        // Card 5: Item Notifier (Toggle + Reset)
        return handleHudCardClick(mouseX, mouseY, innerX, listY + (cardH + cardGap) * 5, innerW, cardH, true, true, "Reset",
                () -> {
                    config.itemNotifierEnabled = !config.itemNotifierEnabled;
                    if (!config.itemNotifierEnabled) {
                        ItemNotifierHudRenderer.reset();
                    }
                    config.save();
                },
                () -> { config.itemNotifierX = -1; config.itemNotifierY = -1; config.save(); });
    }

    // ==========================================
    // SETTINGS VIEW (BETTER CHAT + ITEM NOTIFIER)
    // ==========================================
    private void renderSettingsView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH) {
        ModConfig config = ModConfig.getInstance();
        int pad = 16;
        int innerX = contentX + pad;
        int innerW = contentW - pad * 2;
        int cardH = 40;
        int cardGap = 6;

        int totalContentH = 14 + 34 + (cardH + cardGap) * 6 + 10 + 30 + (cardH + cardGap) * 5 + 10 + 30 + (cardH + cardGap) * 3 + 12;
        this.contentTotalHeight = Math.max(contentH, totalContentH);
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0, Math.max(0, this.contentTotalHeight - contentH));
        int scroll = (int) Math.round(this.scrollAmount);

        int innerY = contentY + 14 - scroll;

        graphics.text(this.font, Component.literal("§lSETTINGS — GENERAL"), innerX, innerY, JelloTheme.TEXT, false);
        graphics.text(this.font, Component.literal("Theme, inventory & quality-of-life settings"), innerX, innerY + 13, JelloTheme.TEXT_MUTED, false);

        int generalY = innerY + 34;
        drawHudResetCard(graphics, mouseX, mouseY, innerX, generalY, innerW, cardH,
                "GUI Theme",
                "Active: " + JelloTheme.getThemeName(config.guiTheme) + " (Sestia OLED / Transparent / Light / Monokai Pro / Dark)",
                false, false, true, "Switch");

        drawHudResetCard(graphics, mouseX, mouseY, innerX, generalY + (cardH + cardGap), innerW, cardH,
                "Shulker Preview",
                "Show shulker box contents tooltip when hovering in inventory",
                config.invShulkerPreview, true, false, "");

        drawHudResetCard(graphics, mouseX, mouseY, innerX, generalY + (cardH + cardGap) * 2, innerW, cardH,
                "Zoom",
                "Hold Zoom key (default C) + scroll wheel to adjust magnification",
                config.zoomEnabled, true, false, "");

        drawStepperCard(graphics, mouseX, mouseY, innerX, generalY + (cardH + cardGap) * 3, innerW, cardH,
                "Zoom Factor",
                "Camera zoom magnification level (1.5x - 20.0x)",
                String.format(java.util.Locale.ROOT, "%.1fx", config.zoomFactor));

        drawHudResetCard(graphics, mouseX, mouseY, innerX, generalY + (cardH + cardGap) * 4, innerW, cardH,
                "Custom Crosshair",
                "Use Sestia custom crosshair texture (reloads resource packs)",
                config.customCrosshair, true, false, "");

        drawHudResetCard(graphics, mouseX, mouseY, innerX, generalY + (cardH + cardGap) * 5, innerW, cardH,
                "Custom Font",
                "Use Sestia custom Minecraft UI font (reloads resource packs)",
                config.customFont, true, false, "");

        int chatHeaderY = generalY + (cardH + cardGap) * 6 + 10;
        graphics.text(this.font, Component.literal("§lBETTER CHAT"), innerX, chatHeaderY, JelloTheme.TEXT, false);
        graphics.text(this.font, Component.literal("Toggle individual Better Chat features on or off"), innerX, chatHeaderY + 13, JelloTheme.TEXT_MUTED, false);

        int listY = chatHeaderY + 30;

        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY, innerW, cardH,
                "Infinite History",
                "Expands chat history from 100 to 16,384 lines",
                config.chatInfinite, true, false, "");

        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY + (cardH + cardGap), innerW, cardH,
                "Anti Clear",
                "Prevents server-side clear chat wipes & blank lines",
                config.chatAntiClear, true, false, "");

        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY + (cardH + cardGap) * 2, innerW, cardH,
                "Click to Copy",
                "Right-click or Shift+Left click a chat message to copy",
                config.chatClickCopy, true, false, "");

        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY + (cardH + cardGap) * 3, innerW, cardH,
                "Timestamps",
                "Adds Istanbul time <HH:mm:ss> prefix to chat messages",
                config.chatTimestamps, true, false, "");

        drawHudResetCard(graphics, mouseX, mouseY, innerX, listY + (cardH + cardGap) * 4, innerW, cardH,
                "Compact Chat",
                "Stacks duplicate consecutive messages with (2), (3)...",
                config.compactChat, true, false, "");

        int notifierHeaderY = listY + (cardH + cardGap) * 5 + 10;
        graphics.text(this.font, Component.literal("§lITEM NOTIFIER"), innerX, notifierHeaderY, JelloTheme.TEXT, false);
        graphics.text(this.font, Component.literal("Pickup/drop HUD alerts (drag position in Open HUD Editor)"), innerX, notifierHeaderY + 13, JelloTheme.TEXT_MUTED, false);

        int notifierY = notifierHeaderY + 30;
        String notifierPosDesc = (config.itemNotifierX < 0 || config.itemNotifierY < 0)
                ? "Auto-placed near hotbar (drag in HUD Editor)"
                : ("Pos: " + config.itemNotifierX + ", " + config.itemNotifierY);
        drawHudResetCard(graphics, mouseX, mouseY, innerX, notifierY, innerW, cardH,
                "Item Notifier",
                notifierPosDesc,
                config.itemNotifierEnabled, true, true, "Reset");

        drawStepperCard(graphics, mouseX, mouseY, innerX, notifierY + (cardH + cardGap), innerW, cardH,
                "Display Duration",
                "How long each pickup/drop alert stays on screen",
                String.format(java.util.Locale.ROOT, "%.1fs", config.itemNotifierDuration));

        drawStepperCard(graphics, mouseX, mouseY, innerX, notifierY + (cardH + cardGap) * 2, innerW, cardH,
                "Max Items",
                "Maximum number of simultaneous item alerts shown",
                Integer.toString(config.itemNotifierMaxItems));
    }

    private void drawStepperCard(
            GuiGraphicsExtractor graphics,
            int mouseX, int mouseY,
            int x, int y, int w, int h,
            String title, String subtitle, String valueText
    ) {
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 6, JelloTheme.CARD_INACTIVE, JelloTheme.BORDER_SUBTLE);
        graphics.text(this.font, Component.literal(title), x + 12, y + 9, JelloTheme.TEXT, false);
        graphics.text(this.font, Component.literal(subtitle), x + 12, y + 24, JelloTheme.TEXT_MUTED, false);

        int btnH = 20;
        int btnY = y + (h - btnH) / 2;
        int stepW = 22;
        int valW = 46;
        int rightEdge = x + w - 10;

        int plusX = rightEdge - stepW;
        int valX = plusX - 4 - valW;
        int minusX = valX - 4 - stepW;

        boolean hovMinus = isInside(mouseX, mouseY, minusX, btnY, stepW, btnH);
        JelloTheme.fillRoundedCard(graphics, minusX, btnY, minusX + stepW, btnY + btnH, 4,
                hovMinus ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CONTAINER_BG,
                hovMinus ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        graphics.centeredText(this.font, Component.literal("-"), minusX + stepW / 2, btnY + 6, JelloTheme.TEXT);

        JelloTheme.fillRoundedCard(graphics, valX, btnY, valX + valW, btnY + btnH, 4,
                JelloTheme.CONTAINER_BG, JelloTheme.BORDER_SUBTLE);
        graphics.centeredText(this.font, Component.literal(valueText), valX + valW / 2, btnY + 6, JelloTheme.TEXT);

        boolean hovPlus = isInside(mouseX, mouseY, plusX, btnY, stepW, btnH);
        JelloTheme.fillRoundedCard(graphics, plusX, btnY, plusX + stepW, btnY + btnH, 4,
                hovPlus ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CONTAINER_BG,
                hovPlus ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        graphics.centeredText(this.font, Component.literal("+"), plusX + stepW / 2, btnY + 6, JelloTheme.TEXT);
    }

    private boolean handleStepperCardClick(
            int mouseX, int mouseY,
            int x, int y, int w, int h,
            Runnable onMinus, Runnable onPlus
    ) {
        int btnH = 20;
        int btnY = y + (h - btnH) / 2;
        int stepW = 22;
        int valW = 46;
        int rightEdge = x + w - 10;

        int plusX = rightEdge - stepW;
        int valX = plusX - 4 - valW;
        int minusX = valX - 4 - stepW;

        if (isInside(mouseX, mouseY, minusX, btnY, stepW, btnH)) {
            onMinus.run();
            playClickSound();
            return true;
        }
        if (isInside(mouseX, mouseY, plusX, btnY, stepW, btnH)) {
            onPlus.run();
            playClickSound();
            return true;
        }
        return false;
    }

    private boolean handleSettingsClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW, int contentH) {
        if (!MouseButtonMapping.isLeftClick(button)) {
            return false;
        }
        ModConfig config = ModConfig.getInstance();
        int pad = 16;
        int innerX = contentX + pad;
        int innerW = contentW - pad * 2;
        int scroll = (int) Math.round(this.scrollAmount);
        int innerY = contentY + 14 - scroll;
        int generalY = innerY + 34;
        int cardH = 40;
        int cardGap = 6;

        if (handleHudCardClick(mouseX, mouseY, innerX, generalY, innerW, cardH, false, true, "Switch",
                null,
                () -> {
                    config.guiTheme = (config.guiTheme + 1) % JelloTheme.THEME_COUNT;
                    JelloTheme.applyTheme(config.guiTheme);
                    config.save();
                })) {
            return true;
        }
        if (handleHudCardClick(mouseX, mouseY, innerX, generalY + (cardH + cardGap), innerW, cardH, true, false, "",
                () -> { config.invShulkerPreview = !config.invShulkerPreview; config.save(); }, null)) {
            return true;
        }
        if (handleHudCardClick(mouseX, mouseY, innerX, generalY + (cardH + cardGap) * 2, innerW, cardH, true, false, "",
                () -> { config.zoomEnabled = !config.zoomEnabled; config.save(); }, null)) {
            return true;
        }
        if (handleStepperCardClick(mouseX, mouseY, innerX, generalY + (cardH + cardGap) * 3, innerW, cardH,
                () -> {
                    config.zoomFactor = Math.max(1.5f, Math.round((config.zoomFactor - 0.5f) * 2.0f) / 2.0f);
                    config.save();
                },
                () -> {
                    config.zoomFactor = Math.min(20.0f, Math.round((config.zoomFactor + 0.5f) * 2.0f) / 2.0f);
                    config.save();
                })) {
            return true;
        }
        if (handleHudCardClick(mouseX, mouseY, innerX, generalY + (cardH + cardGap) * 4, innerW, cardH, true, false, "",
                () -> {
                    config.customCrosshair = !config.customCrosshair;
                    config.save();
                    Minecraft.getInstance().reloadResourcePacks();
                }, null)) {
            return true;
        }
        if (handleHudCardClick(mouseX, mouseY, innerX, generalY + (cardH + cardGap) * 5, innerW, cardH, true, false, "",
                () -> {
                    config.customFont = !config.customFont;
                    config.save();
                    Minecraft.getInstance().reloadResourcePacks();
                }, null)) {
            return true;
        }

        int listY = generalY + (cardH + cardGap) * 6 + 10 + 30;
        if (handleHudCardClick(mouseX, mouseY, innerX, listY, innerW, cardH, true, false, "",
                () -> { config.chatInfinite = !config.chatInfinite; config.save(); }, null)) {
            return true;
        }
        if (handleHudCardClick(mouseX, mouseY, innerX, listY + (cardH + cardGap), innerW, cardH, true, false, "",
                () -> { config.chatAntiClear = !config.chatAntiClear; config.save(); }, null)) {
            return true;
        }
        if (handleHudCardClick(mouseX, mouseY, innerX, listY + (cardH + cardGap) * 2, innerW, cardH, true, false, "",
                () -> { config.chatClickCopy = !config.chatClickCopy; config.save(); }, null)) {
            return true;
        }
        if (handleHudCardClick(mouseX, mouseY, innerX, listY + (cardH + cardGap) * 3, innerW, cardH, true, false, "",
                () -> { config.chatTimestamps = !config.chatTimestamps; config.save(); }, null)) {
            return true;
        }
        if (handleHudCardClick(mouseX, mouseY, innerX, listY + (cardH + cardGap) * 4, innerW, cardH, true, false, "",
                () -> { config.compactChat = !config.compactChat; config.save(); }, null)) {
            return true;
        }

        int notifierY = listY + (cardH + cardGap) * 5 + 10 + 30;
        if (handleHudCardClick(mouseX, mouseY, innerX, notifierY, innerW, cardH, true, true, "Reset",
                () -> {
                    config.itemNotifierEnabled = !config.itemNotifierEnabled;
                    if (!config.itemNotifierEnabled) {
                        ItemNotifierHudRenderer.reset();
                    }
                    config.save();
                },
                () -> {
                    config.itemNotifierX = -1;
                    config.itemNotifierY = -1;
                    config.save();
                })) {
            return true;
        }

        if (handleStepperCardClick(mouseX, mouseY, innerX, notifierY + (cardH + cardGap), innerW, cardH,
                () -> {
                    config.itemNotifierDuration = Math.max(1.0f, Math.round((config.itemNotifierDuration - 0.5f) * 2.0f) / 2.0f);
                    config.save();
                },
                () -> {
                    config.itemNotifierDuration = Math.min(10.0f, Math.round((config.itemNotifierDuration + 0.5f) * 2.0f) / 2.0f);
                    config.save();
                })) {
            return true;
        }

        return handleStepperCardClick(mouseX, mouseY, innerX, notifierY + (cardH + cardGap) * 2, innerW, cardH,
                () -> {
                    config.itemNotifierMaxItems = Math.max(1, config.itemNotifierMaxItems - 1);
                    config.save();
                },
                () -> {
                    config.itemNotifierMaxItems = Math.min(10, config.itemNotifierMaxItems + 1);
                    config.save();
                });
    }

    private boolean handleHudCardClick(
            int mouseX, int mouseY,
            int x, int y, int w, int h,
            boolean showToggle, boolean showActionBtn, String actionLabel,
            Runnable onToggle, Runnable onAction
    ) {
        int rightEdge = x + w - 10;
        int btnH = 20;
        int btnY = y + (h - btnH) / 2;
        if (showActionBtn) {
            int actionW = Math.max(54, this.font.width(actionLabel) + 16);
            int actionX = rightEdge - actionW;
            if (isInside(mouseX, mouseY, actionX, btnY, actionW, btnH)) {
                if (onAction != null) onAction.run();
                playClickSound();
                return true;
            }
            rightEdge = actionX - 8;
        }
        if (showToggle) {
            int toggleW = 38;
            int toggleX = rightEdge - toggleW;
            if (isInside(mouseX, mouseY, toggleX, btnY, toggleW, btnH)) {
                if (onToggle != null) onToggle.run();
                playClickSound();
                return true;
            }
        }
        return false;
    }

    private void renderProfilesView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH) {
        ProfileLayout layout = computeProfileLayout(contentX, contentY, contentW, contentH);

        ModernIcons.drawSlidersIcon(graphics, layout.leftColX(), layout.innerY() - 1, JelloTheme.ACCENT);
        graphics.text(this.font, Component.literal("§lCONFIG PROFILES"), layout.leftColX() + 15, layout.innerY(), JelloTheme.TEXT, false);
        JelloTheme.fillHorizontalGradient(graphics, layout.leftColX(), layout.innerY() + 13, layout.leftColX() + 155, layout.innerY() + 14, JelloTheme.ACCENT, 0x55FF424E);

        boolean folderHov = isInside(mouseX, mouseY, layout.folderBtnX(), layout.folderBtnY(), layout.folderBtnW(), layout.folderBtnH());
        JelloTheme.fillRoundedCard(graphics, layout.folderBtnX(), layout.folderBtnY(), layout.folderBtnX() + layout.folderBtnW(), layout.folderBtnY() + layout.folderBtnH(),
                4, folderHov ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE, folderHov ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        graphics.centeredText(this.font, Component.literal("Open Folder"), layout.folderBtnX() + layout.folderBtnW() / 2, layout.folderBtnY() + 4,
                folderHov ? JelloTheme.TEXT : JelloTheme.TEXT_DIM);

        int cardH = 46;
        int cardGap = 8;
        this.contentTotalHeight = Math.max(contentH, 34 + this.cachedProfiles.size() * (cardH + cardGap));

        graphics.enableScissor(layout.leftColX() - 2, layout.listStartY() - 2, layout.leftColX() + layout.leftColW() + 2, contentY + contentH);
        if (this.cachedProfiles.isEmpty()) {
            int emptyH = 82;
            JelloTheme.fillRoundedCard(graphics, layout.leftColX(), layout.listStartY(), layout.leftColX() + layout.leftColW(), layout.listStartY() + emptyH,
                    7, JelloTheme.CARD_INACTIVE, JelloTheme.BORDER_SUBTLE);
            graphics.centeredText(this.font, Component.literal("No saved profiles yet."), layout.leftColX() + layout.leftColW() / 2, layout.listStartY() + 24, JelloTheme.TEXT);
            graphics.centeredText(this.font, Component.literal("Save your active modules, binds & macros on the right."), layout.leftColX() + layout.leftColW() / 2, layout.listStartY() + 42, JelloTheme.TEXT_MUTED);
        } else {
            for (int i = 0; i < this.cachedProfiles.size(); i++) {
                ConfigProfileManager.ProfileSummary p = this.cachedProfiles.get(i);
                int cardY = layout.listStartY() + i * (cardH + cardGap) - (int) this.scrollAmount;
                if (cardY + cardH < layout.listStartY() || cardY > contentY + contentH) {
                    continue;
                }

                boolean cardHov = isInside(mouseX, mouseY, layout.leftColX(), cardY, layout.leftColW(), cardH);
                int cardBg = cardHov ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE;
                int borderColor = cardHov ? 0xAAFF1E2D : JelloTheme.BORDER_SUBTLE;
                JelloTheme.fillRoundedCard(graphics, layout.leftColX(), cardY, layout.leftColX() + layout.leftColW(), cardY + cardH, 7, cardBg, borderColor);

                ProfileCardButtons btns = computeProfileCardButtons(layout, cardY);
                int textMaxW = Math.max(60, btns.loadBtnX() - layout.leftColX() - 18);

                graphics.text(this.font, Component.literal(fitText(p.name(), textMaxW)), layout.leftColX() + 12, cardY + 9, JelloTheme.TEXT, false);
                String meta = p.activeCount() + " Active  •  " + p.bindCount() + " Binds  •  " + p.macroCount() + " Macros";
                graphics.text(this.font, Component.literal(fitText(meta, textMaxW)), layout.leftColX() + 12, cardY + 25, JelloTheme.TEXT_MUTED, false);

                boolean loadHov = isInside(mouseX, mouseY, btns.loadBtnX(), btns.btnY(), btns.loadBtnW(), 18);
                JelloTheme.fillRoundedCard(graphics, btns.loadBtnX(), btns.btnY(), btns.loadBtnX() + btns.loadBtnW(), btns.btnY() + 18, 4,
                        loadHov ? JelloTheme.ACCENT : 0x33FF1E2D, loadHov ? JelloTheme.ACCENT_HOVER : 0x88FF1E2D);
                graphics.centeredText(this.font, Component.literal("Load"), btns.loadBtnX() + btns.loadBtnW() / 2, btns.btnY() + 5, 0xFFFFFFFF);

                boolean exportHov = isInside(mouseX, mouseY, btns.exportBtnX(), btns.btnY(), btns.exportBtnW(), 18);
                JelloTheme.fillRoundedCard(graphics, btns.exportBtnX(), btns.btnY(), btns.exportBtnX() + btns.exportBtnW(), btns.btnY() + 18, 4,
                        exportHov ? 0x44FF1E2D : 0xFF141419, exportHov ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
                graphics.centeredText(this.font, Component.literal("Export"), btns.exportBtnX() + btns.exportBtnW() / 2, btns.btnY() + 5, exportHov ? 0xFFFFFFFF : JelloTheme.TEXT_DIM);

                boolean delHov = isInside(mouseX, mouseY, btns.delBtnX(), btns.btnY(), btns.delBtnW(), 18);
                JelloTheme.fillRoundedCard(graphics, btns.delBtnX(), btns.btnY(), btns.delBtnX() + btns.delBtnW(), btns.btnY() + 18, 4,
                        delHov ? 0x77FF1E2D : 0xFF141419, delHov ? JelloTheme.ACCENT_HOVER : JelloTheme.BORDER_SUBTLE);
                ModernIcons.drawCloseIcon(graphics, btns.delBtnX() + btns.delBtnW() / 2, btns.btnY() + 9, delHov ? 0xFFFFFFFF : 0xFFFF6666);
            }
        }
        graphics.disableScissor();

        renderProfileActionsPanel(graphics, mouseX, mouseY, layout);
    }

    private void renderProfileActionsPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, ProfileLayout layout) {
        int x = layout.rightColX();
        int y = layout.rightColY();
        int w = layout.rightColW();
        int h = layout.rightColH();

        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 8, JelloTheme.CONTAINER_BG, JelloTheme.BORDER_SUBTLE);

        graphics.text(this.font, Component.literal("§lPROFILE MANAGER"), x + 12, y + 10, JelloTheme.TEXT, false);
        JelloTheme.fillHorizontalGradient(graphics, x + 12, y + 23, x + 145, y + 24, JelloTheme.ACCENT, 0x33FF5252);

        graphics.text(this.font, Component.literal("Profile Name:"), layout.boxX(), y + 30, JelloTheme.TEXT_MUTED, false);
        renderProfileInputFrame(graphics, this.profileNameBox, layout.boxX(), layout.inputBoxY(), layout.boxW());
        renderProfileButton(graphics, mouseX, mouseY, layout.boxX(), layout.saveBtnY(), layout.boxW(), layout.btnH(), "+ Save Current Config", true);

        graphics.text(this.font, Component.literal("IMPORT CODE"), layout.boxX(), y + 101, JelloTheme.TEXT_MUTED, false);
        renderProfileInputFrame(graphics, this.profileCodeBox, layout.boxX(), layout.importCodeBoxY(), layout.boxW());
        renderProfileButton(graphics, mouseX, mouseY, layout.boxX(), layout.importBtnY(), layout.boxW(), layout.btnH(), "Import", false);

        int footerY = layout.importBtnY() + layout.btnH() + 8;
        if (this.profileStatusText != null && System.currentTimeMillis() < this.profileStatusUntilMs) {
            int stBg = this.profileStatusError ? 0x33FF1E2D : 0x2410B981;
            int stBorder = this.profileStatusError ? 0xAAFF1E2D : 0x5510B981;
            int stColor = this.profileStatusError ? 0xFFFF8089 : 0xFF6EE7B7;
            JelloTheme.fillRoundedCard(graphics, layout.boxX(), footerY, layout.boxX() + layout.boxW(), footerY + 20, 5, stBg, stBorder);
            graphics.centeredText(this.font, Component.literal(fitText(this.profileStatusText, layout.boxW() - 10)),
                    layout.boxX() + layout.boxW() / 2, footerY + 6, stColor);
            footerY += 26;
        }

        if (footerY + 22 <= y + h) {
            graphics.text(this.font, Component.literal(fitText("Saves all active modules, sliders,", layout.boxW())), layout.boxX(), footerY + 2, 0xFF64748B, false);
            graphics.text(this.font, Component.literal(fitText("keybinds, macros & HUD settings.", layout.boxW())), layout.boxX(), footerY + 13, 0xFF64748B, false);
        }
    }

    private void renderProfileButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean primary) {
        boolean hov = isInside(mouseX, mouseY, x, y, w, h);
        if (primary) {
            JelloTheme.fillRounded(graphics, x, y, x + w, y + h, 5, hov ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT);
        } else {
            JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 5,
                    hov ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE,
                    hov ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        }
        graphics.centeredText(this.font, Component.literal(fitText(label, w - 10)), x + w / 2, y + 7,
                (primary || hov) ? 0xFFFFFFFF : JelloTheme.TEXT_DIM);
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
        int onColor = hovered ? JelloTheme.SWITCH_ON_HOVER : JelloTheme.SWITCH_ON;
        int trackColor = JelloTheme.lerpColor(offColor, onColor, nextAnim);
        int trackBorder = JelloTheme.lerpColor(0x22FFFFFF, JelloTheme.SWITCH_ON_HOVER, nextAnim);

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
        JelloTheme.fillRounded(graphics, x, y, x + w, y + h, trackRadius, JelloTheme.SLIDER_TRACK);

        int fillW = Math.max(trackRadius * 2, (int) (w * value));
        int fillColor = (isDraggingThis || hovered) ? JelloTheme.SLIDER_BAR_HOVER : JelloTheme.SLIDER_BAR;
        JelloTheme.fillRounded(graphics, x, y, x + fillW, y + h, trackRadius, fillColor);

        int knobX = x + (int) (w * value);
        int knobRadius = h / 2 + 3;
        int knobY = y + h / 2;
        JelloTheme.fillCircle(graphics, knobX, knobY, knobRadius, (isDraggingThis || hovered) ? 0xFFFFFFFF : 0xFFE2E8F0);
        JelloTheme.fillCircle(graphics, knobX, knobY, knobRadius - 2, (isDraggingThis || hovered) ? JelloTheme.SLIDER_BAR_HOVER : JelloTheme.SLIDER_BAR);
    }

    private void renderScrollbarAt(GuiGraphicsExtractor graphics, int barX, int viewY, int viewH, int totalH, double scroll) {
        int maxScroll = Math.max(0, totalH - viewH);
        if (maxScroll <= 0 || viewH <= 20) return;

        int thumbH = Math.max(22, (int) ((float) (viewH * viewH) / totalH));
        int thumbY = viewY + (int) ((scroll / maxScroll) * (viewH - thumbH));

        JelloTheme.fillRounded(graphics, barX, viewY, barX + 3, viewY + viewH, 1, JelloTheme.SCROLLBAR_TRACK);
        JelloTheme.fillRounded(graphics, barX, thumbY, barX + 3, thumbY + thumbH, 1,
                this.draggingScrollbar ? JelloTheme.SCROLLBAR_THUMB_HOVER : JelloTheme.SCROLLBAR_THUMB);
    }

    private int[] computeActiveScrollbarMetrics() {
        int contentX = this.windowX + SIDEBAR_WIDTH + 1;
        int contentY = this.windowY + HEADER_HEIGHT + 1;
        int contentW = this.windowW - SIDEBAR_WIDTH - 2;
        int contentH = this.windowH - HEADER_HEIGHT - 2;

        boolean showingModules = !this.searchQuery.isEmpty() || this.activeNav == NavTab.MODULES;
        int viewY = showingModules ? (contentY + CHIP_BAR_HEIGHT + 4) : contentY;
        int viewH = showingModules ? Math.max(0, contentH - CHIP_BAR_HEIGHT - 8) : contentH;
        int totalH = showingModules
                ? resolveAccordionPlan(contentX, contentY + CHIP_BAR_HEIGHT, contentW).plan().totalHeight()
                : this.contentTotalHeight;
        int maxScroll = Math.max(0, totalH - viewH);
        int thumbH = maxScroll > 0 ? Math.max(22, (int) ((float) (viewH * viewH) / totalH)) : viewH;
        int thumbY = maxScroll > 0 ? viewY + (int) ((this.scrollAmount / maxScroll) * (viewH - thumbH)) : viewY;
        int barX = contentX + contentW - 6;
        return new int[]{barX, viewY, viewH, maxScroll, thumbH, thumbY, contentX + contentW};
    }

    private void updateScrollFromDrag(double mouseY, int[] metrics) {
        int viewY = metrics[1];
        int viewH = metrics[2];
        int maxScroll = metrics[3];
        int thumbH = metrics[4];
        int trackSpan = Math.max(1, viewH - thumbH);
        double thumbTop = mouseY - this.scrollbarDragOffset - viewY;
        double ratio = Mth.clamp(thumbTop / (double) trackSpan, 0.0, 1.0);
        this.scrollAmount = ratio * maxScroll;
        this.ensureExpandedVisible = false;
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
        if (this.editingSlider != null) {
            if (MouseButtonMapping.isLeftClick(button) && this.editingBoxW > 0
                    && isInside(mouseX, mouseY, this.editingBoxX, this.editingBoxY, this.editingBoxW, this.editingBoxH)) {
                super.mouseClicked(event, wasHandled);
                if (this.sliderInputBox != null) {
                    this.sliderInputBox.setFocused(true);
                    this.setFocused(this.sliderInputBox);
                }
                playClickSound();
                return true;
            }
            commitEditingSlider();
        }

        int searchBoxX = this.windowX + this.windowW - SEARCH_BOX_WIDTH - 44;
        int searchBoxY = this.windowY + (HEADER_HEIGHT - SEARCH_BOX_HEIGHT) / 2;
        if (MouseButtonMapping.isLeftClick(button) && !this.searchQuery.isEmpty() && isInside(mouseX, mouseY, searchBoxX + SEARCH_BOX_WIDTH - 18, searchBoxY, 18, SEARCH_BOX_HEIGHT)) {
            if (this.searchBox != null) {
                this.searchBox.setValue("");
                this.searchBox.setFocused(true);
                this.setFocused(this.searchBox);
            }
            this.searchQuery = "";
            playClickSound();
            return true;
        }

        // Clicking anywhere inside the visual search container (including icon/padding) focuses searchBox
        if (MouseButtonMapping.isLeftClick(button)
                && this.searchBox != null
                && isInside(mouseX, mouseY, searchBoxX, searchBoxY, SEARCH_BOX_WIDTH, SEARCH_BOX_HEIGHT)) {
            super.mouseClicked(event, wasHandled);
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
            return true;
        }

        // Unfocus secondary boxes when clicking outside them
        if (this.searchBox != null && !isInside(mouseX, mouseY, searchBoxX, searchBoxY, SEARCH_BOX_WIDTH, SEARCH_BOX_HEIGHT)) {
            this.searchBox.setFocused(false);
            if (this.getFocused() == this.searchBox) {
                this.setFocused(null);
            }
        }
        unfocusProfileInputIfOutside(this.profileNameBox, mouseX, mouseY);
        unfocusProfileInputIfOutside(this.profileCodeBox, mouseX, mouseY);

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
                }
                if (this.activeNav == NavTab.CONFIG) {
                    refreshProfilesList();
                }
                setSecondaryInputsVisible(this.activeNav == NavTab.MACROS && this.editingMacroIndex != -1,
                        this.activeNav == NavTab.CONFIG);
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
            if (MouseButtonMapping.isLeftClick(button)) {
                int[] sb = computeActiveScrollbarMetrics();
                int barX = sb[0];
                int viewY = sb[1];
                int viewH = sb[2];
                int maxScroll = sb[3];
                int thumbH = sb[4];
                int thumbY = sb[5];
                int contentRight = sb[6];
                if (maxScroll > 0 && viewH > 20 && mouseX >= barX - 4 && mouseX <= contentRight
                        && mouseY >= viewY && mouseY <= viewY + viewH) {
                    if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                        this.scrollbarDragOffset = mouseY - thumbY;
                    } else {
                        this.scrollbarDragOffset = thumbH / 2.0;
                        updateScrollFromDrag(mouseY, sb);
                    }
                    this.draggingScrollbar = true;
                    this.ensureExpandedVisible = false;
                    return true;
                }
            }

            boolean showingModules = !this.searchQuery.isEmpty() || this.activeNav == NavTab.MODULES;
            if (showingModules) {
                if (handleModulesWorkspaceClick(mouseX, mouseY, button, contentX, contentY, contentW, contentH)) {
                    return true;
                }
            } else if (this.activeNav == NavTab.MACROS) {
                if (handleMacrosClick(mouseX, mouseY, button, contentX, contentY, contentW, contentH)) {
                    return true;
                }
            } else if (this.activeNav == NavTab.GUI) {
                if (handleGuiClick(mouseX, mouseY, button, contentX, contentY, contentW, contentH)) {
                    return true;
                }
            } else if (this.activeNav == NavTab.CONFIG) {
                if (handleProfilesClick(mouseX, mouseY, button, contentX, contentY, contentW, contentH)) {
                    return true;
                }
            } else if (this.activeNav == NavTab.SETTINGS) {
                if (handleSettingsClick(mouseX, mouseY, button, contentX, contentY, contentW, contentH)) {
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
                    int chipW = Math.max(30, this.font.width(chip.label + " " + chip.count) + 18);
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
                                this.font,
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
                                this::startEditingSlider,
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

    private boolean handleProfilesClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW, int contentH) {
        if (!MouseButtonMapping.isLeftClick(button)) return false;
        ProfileLayout layout = computeProfileLayout(contentX, contentY, contentW, contentH);
        ModConfig config = ModConfig.getInstance();
        ModernModuleSettings.InventoryBridge inv = this.settingsActions.inventory();
        Path profilesDir = ConfigProfileManager.getDefaultProfilesDir();

        if (isInside(mouseX, mouseY, layout.folderBtnX(), layout.folderBtnY(), layout.folderBtnW(), layout.folderBtnH())) {
            boolean opened = ConfigProfileManager.openProfilesFolder(profilesDir);
            setProfileStatus(opened ? "Opened profiles folder!" : "Could not open folder", !opened);
            playClickSound();
            return true;
        }

        if (isInside(mouseX, mouseY, layout.boxX(), layout.saveBtnY(), layout.boxW(), layout.btnH())) {
            String rawName = this.profileNameBox != null ? this.profileNameBox.getValue() : "";
            String finalName = ConfigProfileManager.normalizeProfileName(rawName);
            try {
                ConfigProfileManager.saveProfile(profilesDir, finalName, config, inv, countActiveModules());
                refreshProfilesList();
                setProfileStatus("Saved '" + finalName + "'!", false);
            } catch (Exception e) {
                setProfileStatus("Failed to save profile", true);
            }
            playClickSound();
            return true;
        }

        if (isInside(mouseX, mouseY, layout.boxX(), layout.importBtnY(), layout.boxW(), layout.btnH())) {
            String rawCode = this.profileCodeBox != null ? this.profileCodeBox.getValue().trim() : "";
            if (rawCode.isEmpty()) {
                setProfileStatus("Paste a share code first", true);
                playClickSound();
                return true;
            }
            try {
                ConfigProfileManager.ProfileEnvelope env = ConfigProfileManager.parseShareCode(rawCode);
                String customName = this.profileNameBox != null ? this.profileNameBox.getValue().trim() : "";
                if (!customName.isEmpty()) {
                    env.name = customName;
                }
                ConfigProfileManager.saveEnvelope(profilesDir, env);
                refreshProfilesList();
                if (this.profileCodeBox != null) {
                    this.profileCodeBox.setValue("");
                }
                setProfileStatus("Imported '" + env.name + "' (click Load)", false);
            } catch (Exception e) {
                setProfileStatus("Invalid share code", true);
            }
            playClickSound();
            return true;
        }

        int cardH = 46;
        int cardGap = 8;
        for (int i = 0; i < this.cachedProfiles.size(); i++) {
            ConfigProfileManager.ProfileSummary p = this.cachedProfiles.get(i);
            int cardY = layout.listStartY() + i * (cardH + cardGap) - (int) this.scrollAmount;
            if (cardY + cardH < layout.listStartY() || cardY > contentY + contentH) {
                continue;
            }
            if (!isInside(mouseX, mouseY, layout.leftColX(), cardY, layout.leftColW(), cardH)) {
                continue;
            }

            ProfileCardButtons btns = computeProfileCardButtons(layout, cardY);

            if (isInside(mouseX, mouseY, btns.delBtnX(), btns.btnY(), btns.delBtnW(), 18)) {
                try {
                    ConfigProfileManager.deleteProfile(p.file());
                    refreshProfilesList();
                    setProfileStatus("Deleted '" + p.name() + "'", false);
                } catch (Exception e) {
                    setProfileStatus("Could not delete profile", true);
                }
                playClickSound();
                return true;
            }

            if (isInside(mouseX, mouseY, btns.exportBtnX(), btns.btnY(), btns.exportBtnW(), 18)) {
                try {
                    ConfigProfileManager.ProfileEnvelope env = ConfigProfileManager.loadProfileFile(p.file());
                    setClipboardSafe(ConfigProfileManager.exportShareCode(env));
                    setProfileStatus("Exported '" + p.name() + "' code!", false);
                } catch (Exception e) {
                    setProfileStatus("Failed to export profile", true);
                }
                playClickSound();
                return true;
            }

            if (isInside(mouseX, mouseY, btns.loadBtnX(), btns.btnY(), btns.loadBtnW(), 18)) {
                try {
                    ConfigProfileManager.ProfileEnvelope env = ConfigProfileManager.loadProfileFile(p.file());
                    applyProfileEnvelope(env, config, inv);
                    setProfileStatus("Loaded '" + p.name() + "'!", false);
                } catch (Exception e) {
                    setProfileStatus("Failed to load profile", true);
                }
                playClickSound();
                return true;
            }
            return false;
        }
        return false;
    }

    private void renderProfileInputFrame(GuiGraphicsExtractor graphics, EditBox box, int boxX, int boxY, int boxW) {
        boolean focused = box != null && box.isFocused();
        JelloTheme.fillRoundedCard(graphics, boxX, boxY, boxX + boxW, boxY + 20,
                5, 0xFF0A0A0F, focused ? JelloTheme.ACCENT : JelloTheme.BORDER_SUBTLE);
        if (box != null) {
            box.setX(boxX + 5);
            box.setY(boxY + 6);
            box.setWidth(boxW - 10);
        }
    }

    private static void unfocusProfileInputIfOutside(EditBox box, int mouseX, int mouseY) {
        if (box != null && (!box.visible || !isInside(mouseX, mouseY,
                box.getX() - 5, box.getY() - 6, box.getWidth() + 10, 20))) {
            box.setFocused(false);
        }
    }

    private void applyProfileEnvelope(
            ConfigProfileManager.ProfileEnvelope env,
            ModConfig config,
            ModernModuleSettings.InventoryBridge inv
    ) {
        ConfigProfileManager.applyProfile(env, config, inv, () -> {
            config.save();
            ModernModuleCardUi.flushInventoryConfigSave();
            ModConfig.reloadChunks();
        });
    }

    private void refreshProfilesList() {
        this.cachedProfiles = ConfigProfileManager.listProfiles(ConfigProfileManager.getDefaultProfilesDir());
    }

    private void setSecondaryInputsVisible(boolean showMacroInputs, boolean showProfileInput) {
        if (this.macroNameBox != null) this.macroNameBox.visible = showMacroInputs;
        if (this.macroCmdBox != null) this.macroCmdBox.visible = showMacroInputs;
        if (this.profileNameBox != null) this.profileNameBox.visible = showProfileInput;
        if (this.profileCodeBox != null) this.profileCodeBox.visible = showProfileInput;
    }

    private void setProfileStatus(String text, boolean isError) {
        this.profileStatusText = text;
        this.profileStatusError = isError;
        this.profileStatusUntilMs = System.currentTimeMillis() + 3500L;
    }

    private static void setClipboardSafe(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.keyboardHandler != null) {
            mc.keyboardHandler.setClipboard(text != null ? text : "");
        }
    }

    private static String getClipboardSafe() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.keyboardHandler != null) {
            return mc.keyboardHandler.getClipboard();
        }
        return "";
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        if (this.draggingSliderKey != null) {
            boolean needReload = this.activeSlider != null && this.activeSlider.needsChunkReload();
            boolean wasInvAlpha = "invBgAlpha".equals(this.draggingSliderKey);
            this.draggingSliderKey = null;
            this.activeSlider = null;
            cancelEditingSlider();
            ModConfig.getInstance().save();
            if (needReload) ModConfig.reloadChunks();
            if (wasInvAlpha) ModernModuleCardUi.flushInventoryConfigSave();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingScrollbar) {
            int[] sb = computeActiveScrollbarMetrics();
            if (sb[3] > 0) {
                updateScrollFromDrag(event.y(), sb);
            }
            return true;
        }
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

        if (this.editingSlider != null) {
            int key = event.key();
            if (key == InputConstants.KEY_RETURN || key == GLFW.GLFW_KEY_KP_ENTER || key == InputConstants.KEY_TAB) {
                commitEditingSlider();
                playClickSound();
                return true;
            }
            if (key == InputConstants.KEY_ESCAPE) {
                cancelEditingSlider();
                playClickSound();
                return true;
            }
            if (this.sliderInputBox != null && this.sliderInputBox.keyPressed(event)) {
                return true;
            }
            boolean ctrlDown = (event.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            boolean shiftDown = (event.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
            boolean altDown = (event.modifiers() & GLFW.GLFW_MOD_ALT) != 0;
            char mappedChar = ModernModuleSettings.mapKeyCodeToSliderChar(key, shiftDown, ctrlDown || altDown);
            if (mappedChar != 0 && this.sliderInputBox != null) {
                this.sliderInputBox.insertText(String.valueOf(mappedChar));
                this.lastSliderKeyChar = mappedChar;
                this.lastSliderKeyTimeMs = System.currentTimeMillis();
                return true;
            }
            return true;
        }

        if (event.key() == InputConstants.KEY_ESCAPE) {
            if (this.searchBox != null && this.searchBox.isFocused() && !this.searchQuery.isEmpty()) {
                this.searchBox.setValue("");
                return true;
            }
            if (this.profileNameBox != null && this.profileNameBox.isFocused()) {
                this.profileNameBox.setFocused(false);
                return true;
            }
            if (this.profileCodeBox != null && this.profileCodeBox.isFocused()) {
                this.profileCodeBox.setFocused(false);
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
        if (this.editingSlider != null) {
            char ch = (char) event.codepoint();
            if (ch != 0 && ch == this.lastSliderKeyChar
                    && (System.currentTimeMillis() - this.lastSliderKeyTimeMs) < 80L) {
                this.lastSliderKeyChar = 0;
                return true;
            }
            if (this.sliderInputBox != null) {
                this.sliderInputBox.charTyped(event);
            }
            return true;
        }
        return super.charTyped(event);
    }

    private void startEditingSlider(ModernModuleSettings.SliderControl slider) {
        if (this.editingSlider != null) {
            commitEditingSlider();
        }
        this.draggingSliderKey = null;
        this.activeSlider = null;
        if (this.searchBox != null) this.searchBox.setFocused(false);
        if (this.profileNameBox != null) this.profileNameBox.setFocused(false);
        if (this.profileCodeBox != null) this.profileCodeBox.setFocused(false);

        this.editingSlider = slider;
        String initial = slider.editText() != null ? slider.editText().get() : "";
        this.editingSliderText = initial != null ? initial : "";
        this.editingBoxX = 0;
        this.editingBoxY = 0;
        this.editingBoxW = 0;
        this.editingBoxH = 0;
        if (this.sliderInputBox != null) {
            this.sliderInputBox.setValue(this.editingSliderText);
            this.sliderInputBox.moveCursorToEnd(false);
            this.sliderInputBox.setHighlightPos(0);
            this.sliderInputBox.visible = true;
            this.sliderInputBox.setFocused(true);
            this.setFocused(this.sliderInputBox);
        }
    }

    private void commitEditingSlider() {
        ModernModuleSettings.SliderControl slider = this.editingSlider;
        String text = this.sliderInputBox != null ? this.sliderInputBox.getValue() : this.editingSliderText;
        cancelEditingSlider();
        if (slider == null || slider.textCommitter() == null) {
            return;
        }
        if (slider.textCommitter().test(text)) {
            ModConfig.getInstance().save();
            if (slider.needsChunkReload()) {
                ModConfig.reloadChunks();
            }
            if ("invBgAlpha".equals(slider.key())) {
                ModernModuleCardUi.flushInventoryConfigSave();
            }
        }
    }

    private void cancelEditingSlider() {
        this.editingSlider = null;
        this.editingSliderText = "";
        this.editingBoxX = 0;
        this.editingBoxY = 0;
        this.editingBoxW = 0;
        this.editingBoxH = 0;
        if (this.sliderInputBox != null) {
            this.sliderInputBox.visible = false;
            this.sliderInputBox.setFocused(false);
            if (this.getFocused() == this.sliderInputBox) {
                this.setFocused(null);
            }
        }
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
            this.expandedBySearch = false;
            this.draggingSliderKey = null;
            this.activeSlider = null;
            cancelEditingSlider();
            this.ensureExpandedVisible = true;
        }
        playClickSound();
    }

    private void closeExpandedModule() {
        this.expandedModule = null;
        this.expandedBySearch = false;
        this.ensureExpandedVisible = false;
        this.draggingSliderKey = null;
        this.activeSlider = null;
        cancelEditingSlider();
    }

    private void clearExpandedImmediate() {
        this.expandedModule = null;
        this.renderedExpandedModule = null;
        this.expandedBySearch = false;
        this.expandAnim = 0.0f;
        this.ensureExpandedVisible = false;
        this.draggingSliderKey = null;
        this.activeSlider = null;
        cancelEditingSlider();
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
        ModConfig cfg = ModConfig.getInstance();
        return ModernModuleGridLayout.filterAndSortModules(
                ClickGuiScreen.getAllModules(),
                m -> m.name,
                m -> m.category.id,
                m -> m.category.name,
                this.selectedCategoryId,
                this.searchQuery,
                (m, q) -> ModernModuleSettings.matchesSubSetting(m.name, q, cfg, this.settingsActions)
        );
    }

    private String findSubSettingAutoExpandTarget(List<ClickGuiScreen.ModuleEntry> visible, String query) {
        if (query == null || query.isEmpty() || visible == null || visible.isEmpty()) {
            return null;
        }
        String q = query.toLowerCase(java.util.Locale.ROOT);
        ModConfig cfg = ModConfig.getInstance();
        for (ClickGuiScreen.ModuleEntry m : visible) {
            if (m != null && ModernModuleSettings.matchesSubSetting(m.name, q, cfg, this.settingsActions)) {
                return m.name;
            }
        }
        return null;
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

    private float animateHover(String key, boolean hovered, float dt) {
        float current = this.hoverAnimations.getOrDefault(key, hovered ? 1.0f : 0.0f);
        float target = hovered ? 1.0f : 0.0f;
        float next = current + (target - current) * Mth.clamp(dt * 14.0f, 0.08f, 1.0f);
        if (Math.abs(target - next) < 0.01f) {
            next = target;
        }
        this.hoverAnimations.put(key, next);
        return next;
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
