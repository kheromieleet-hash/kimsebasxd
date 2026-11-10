package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;

/**
 * Modern Single-Panel ClickGUI Screen with unified sidebar navigation,
 * real-time search filtering, smooth visual switches, accordion module settings,
 * and an interactive real-time ESP mannequin preview panel.
 */
public class ModernClickGuiScreen extends Screen {

    public enum CategoryTab {
        COMBAT("Combat", "⚔"),
        MOVEMENT("Movement", "🏃"),
        RENDER("Render", "👁"),
        ESP("ESP Settings", "⭐"),
        WORLD("World", "🌍"),
        PLAYER("Player", "👤"),
        EXPLOIT("Exploit", "💣"),
        MACROS("Macros", "⌨"),
        SETTINGS("Settings", "⚙");

        public final String label;
        public final String icon;

        CategoryTab(String label, String icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    // Color Palette presets for Player ESP (indices preserved in ModConfig.espColorIndex)
    public static final int[] PALETTE_COLORS = {
            0xFFC084FC, // Pastel Violet
            0xFFF472B6, // Rose Pink
            0xFF00E5FF, // Neon Cyan
            0xFF00E676, // Emerald Green
            0xFFE53935, // Crimson Red
            0xFFFFAB00  // Sunset Gold
    };

    public static final String[] PALETTE_NAMES = {
            "Pastel Violet",
            "Rose Pink",
            "Neon Cyan",
            "Emerald Green",
            "Crimson Red",
            "Sunset Gold"
    };

    private final Screen parent;

    // Window dimensions
    private int windowX;
    private int windowY;
    private int windowW;
    private int windowH;

    // Sidebar & Content dimensions
    private static final int SIDEBAR_WIDTH = 170;
    private static final int HEADER_HEIGHT = 48;
    private static final int TAB_HEIGHT = 38;

    // Navigation & View state
    private CategoryTab activeTab = CategoryTab.ESP;
    private float indicatorY = -1.0f;
    private final Map<String, Float> switchAnimations = new HashMap<>();

    // Search Box & Query
    private EditBox searchBox;
    private String searchQuery = "";

    // Scrolling & Sliders
    private double scrollAmount = 0.0;
    private int contentTotalHeight = 0;
    private String draggingSliderKey = null;
    private ModernModuleSettings.SliderControl activeSlider = null;
    private int draggingSliderStartX = 0;
    private int draggingSliderWidth = 100;

    // Interactive Mannequin Preview state
    private boolean simulatedDamaged = false;
    private long lastFrameNanos = 0L;
    private float mannequinBreathe = 0.0f;

    // Module Keybind & Settings state
    private String listeningKeybindModule = null;
    private String expandedModule = null;
    private final ModernModuleSettings.Actions settingsActions = ModernModuleCardUi.createActions(this);

    // Macro editor state
    private int editingMacroIndex = -1; // -1 = none, -2 = adding new, >= 0 = editing existing
    private boolean listeningMacroKey = false;
    private int tempMacroKey = -1;
    private boolean tempMacroIsMouse = false;
    private EditBox macroNameBox;
    private EditBox macroCmdBox;

    // Preview Armor Items
    private final ItemStack HELMET_STACK = new ItemStack(Items.DIAMOND_HELMET);
    private final ItemStack CHEST_STACK = new ItemStack(Items.DIAMOND_CHESTPLATE);
    private final ItemStack LEGS_STACK = new ItemStack(Items.DIAMOND_LEGGINGS);
    private final ItemStack BOOTS_STACK = new ItemStack(Items.DIAMOND_BOOTS);
    private final ItemStack SWORD_STACK = new ItemStack(Items.DIAMOND_SWORD);

    public ModernClickGuiScreen(Screen parent) {
        super(Component.literal("Modern ClickGUI"));
        this.parent = parent;
        ClickGuiScreen.getAllModules();
    }

    public ModernClickGuiScreen() {
        this(null);
    }

    @Override
    protected void init() {
        // Centered floating dashboard clamped cleanly to screen bounds
        this.windowW = Math.min(760, Math.max(460, (int) (this.width * 0.84f)));
        this.windowH = Math.min(490, Math.max(300, (int) (this.height * 0.82f)));
        this.windowX = (this.width - this.windowW) / 2;
        this.windowY = (this.height - this.windowH) / 2;

        this.scrollAmount = 0.0;
        this.draggingSliderKey = null;
        this.activeSlider = null;

        if (this.indicatorY < 0) {
            this.indicatorY = this.windowY + HEADER_HEIGHT + this.activeTab.ordinal() * TAB_HEIGHT;
        }

        // Search EditBox at top-right of the header
        int searchW = 160;
        int searchH = 18;
        int searchX = this.windowX + this.windowW - searchW - 44;
        int searchY = this.windowY + (HEADER_HEIGHT - searchH) / 2;

        String prevQuery = this.searchQuery;
        this.searchBox = new EditBox(this.font, searchX, searchY, searchW, searchH, Component.literal("Search"));
        this.searchBox.setHint(Component.literal("🔍 Search..."));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(30);
        this.searchBox.setValue(prevQuery);
        this.searchBox.setResponder(text -> {
            this.searchQuery = text.trim();
            this.scrollAmount = 0.0;
            this.draggingSliderKey = null;
            this.activeSlider = null;
        });

        this.addRenderableWidget(this.searchBox);

        // Macro editor text fields
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
        // Delta time computation for smooth UI animations
        long now = System.nanoTime();
        float dt = this.lastFrameNanos == 0L ? 0.016f : Mth.clamp((now - this.lastFrameNanos) / 1.0e9f, 0.001f, 0.05f);
        this.lastFrameNanos = now;
        this.mannequinBreathe += dt * 2.5f;

        // Dark dim background over the world
        graphics.fill(0, 0, this.width, this.height, 0x88000000);

        // Window drop shadow & ambient crimson glow
        JelloTheme.drawGlowShadow(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 12, 0x44E53935, 6);

        // Main glass panel body: Dark Obsidian Slate with rounded crimson borders
        JelloTheme.fillRoundedCard(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 12, JelloTheme.WINDOW_BG, 0x66E53935);

        // Top gradient glow line along the top curve
        JelloTheme.fillHorizontalGradient(graphics, this.windowX + 16, this.windowY + 1, this.windowX + this.windowW - 16, this.windowY + 2, JelloTheme.ACCENT, JelloTheme.ACCENT_HOVER);

        // Top Header
        renderHeader(graphics, mouseX, mouseY);

        // Left Sidebar Navigation
        renderSidebar(graphics, mouseX, mouseY, dt);

        // Main Content Area
        int contentX = this.windowX + SIDEBAR_WIDTH + 1;
        int contentY = this.windowY + HEADER_HEIGHT + 1;
        int contentW = this.windowW - SIDEBAR_WIDTH - 2;
        int contentH = this.windowH - HEADER_HEIGHT - 2;

        graphics.enableScissor(contentX, contentY, contentX + contentW, contentY + contentH);

        if (!this.searchQuery.isEmpty()) {
            renderSearchResults(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
        } else if (this.activeTab == CategoryTab.ESP) {
            renderEspSettingsView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
        } else if (this.activeTab == CategoryTab.MACROS) {
            renderMacrosView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
        } else if (this.activeTab == CategoryTab.SETTINGS) {
            renderClientSettingsView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
        } else {
            renderModuleCardsView(graphics, mouseX, mouseY, contentX, contentY, contentW, contentH, dt);
        }

        graphics.disableScissor();

        // Render Scrollbar if needed
        renderScrollbar(graphics, contentX, contentY, contentW, contentH);

        // Render search widget & components
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    // ==========================================
    // TOP HEADER
    // ==========================================
    private void renderHeader(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int headX = this.windowX;
        int headY = this.windowY;
        int headW = this.windowW;

        // Rounded top header panel
        JelloTheme.fillRoundedTop(graphics, headX + 1, headY + 1, headX + headW - 1, headY + HEADER_HEIGHT, 11, JelloTheme.HEADER_BG);

        // Dual-gradient separator at bottom of header (Crimson -> Scarlet)
        JelloTheme.fillHorizontalGradient(graphics, headX, headY + HEADER_HEIGHT - 1, headX + headW, headY + HEADER_HEIGHT, 0x66E53935, 0x44FF5252);

        // Client Logo & Version
        int logoX = headX + 16;
        int logoY = headY + 14;

        graphics.text(this.font, Component.literal("§c§l✦ SCT CLIENT§r §f§l" + ClientBuildInfo.getDisplayVersion()), logoX, logoY, 0xFFFFFFFF, false);

        // Search box background
        if (this.searchBox != null) {
            int searchX = this.searchBox.getX();
            int searchY = this.searchBox.getY();
            int searchW = this.searchBox.getWidth();
            int searchH = this.searchBox.getHeight();

            int searchBoxBg = this.searchBox.isFocused() ? 0x662B141A : 0x44140A0E;
            int searchBoxBorder = this.searchBox.isFocused() ? 0xAAE53935 : 0x33E53935;
            JelloTheme.fillRoundedCard(graphics, searchX - 6, searchY - 2, searchX + searchW + 6, searchY + searchH + 2, 6, searchBoxBg, searchBoxBorder);
        }

        // Close button '✕' at top right
        int closeX = headX + headW - 30;
        int closeY = headY + 13;
        boolean closeHovered = mouseX >= closeX && mouseX <= closeX + 20 && mouseY >= closeY && mouseY <= closeY + 20;

        JelloTheme.fillRounded(graphics, closeX, closeY, closeX + 20, closeY + 20, 5, closeHovered ? 0x44E53935 : 0x1AFFFFFF);
        graphics.centeredText(this.font, Component.literal("✕"), closeX + 10, closeY + 5, closeHovered ? JelloTheme.ACCENT_HOVER : 0xFFCCCCCC);
    }

    // ==========================================
    // SIDEBAR NAVIGATION
    // ==========================================
    private void renderSidebar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float dt) {
        int sideX = this.windowX;
        int sideY = this.windowY + HEADER_HEIGHT;
        int sideW = SIDEBAR_WIDTH;
        int sideH = this.windowH - HEADER_HEIGHT;

        // Dark pane background & separator
        JelloTheme.fillRoundedBottom(graphics, sideX + 1, sideY, sideX + sideW, sideY + sideH - 1, 11, JelloTheme.SIDEBAR_BG);
        graphics.fill(sideX + sideW, sideY, sideX + sideW + 1, sideY + sideH, 0x22FFFFFF);

        // Smooth vertical sliding indicator
        float targetY = sideY + this.activeTab.ordinal() * TAB_HEIGHT;
        this.indicatorY += (targetY - this.indicatorY) * Mth.clamp(dt * 16.0f, 0.05f, 1.0f);

        int indY = Math.round(this.indicatorY);
        // Pill background behind active tab
        JelloTheme.fillRounded(graphics, sideX + 8, indY + 3, sideX + sideW - 8, indY + TAB_HEIGHT - 3, 6, 0x30E53935);
        // Vertical crimson stripe indicator
        JelloTheme.fillRounded(graphics, sideX + 8, indY + 7, sideX + 11, indY + TAB_HEIGHT - 7, 2, JelloTheme.ACCENT);

        // Tab items
        CategoryTab[] tabs = CategoryTab.values();
        for (int i = 0; i < tabs.length; i++) {
            CategoryTab tab = tabs[i];
            int itemY = sideY + i * TAB_HEIGHT;
            boolean isCurrent = tab == this.activeTab;
            boolean hovered = mouseX >= sideX && mouseX <= sideX + sideW && mouseY >= itemY && mouseY < itemY + TAB_HEIGHT;

            if (hovered && !isCurrent) {
                JelloTheme.fillRounded(graphics, sideX + 8, itemY + 3, sideX + sideW - 8, itemY + TAB_HEIGHT - 3, 6, 0x14FFFFFF);
            }

            // Icon + Label
            int textColor = isCurrent ? 0xFFFFFFFF : (hovered ? 0xFFDDDDDD : 0xFF888E9B);
            int iconColor = isCurrent ? JelloTheme.ACCENT : (hovered ? JelloTheme.ACCENT_HOVER : 0xFFA0A5B2);

            graphics.text(this.font, Component.literal(tab.icon), sideX + 18, itemY + 14, iconColor, false);
            graphics.text(this.font, Component.literal(tab.label), sideX + 38, itemY + 14, textColor, false);

            if (tab == CategoryTab.ESP) {
                // Highlight ESP tab with a tiny 'LIVE' pill
                int tagX = sideX + sideW - 38;
                JelloTheme.fillRounded(graphics, tagX, itemY + 12, tagX + 28, itemY + 22, 4, 0x3300E676);
                graphics.text(this.font, Component.literal("LIVE"), tagX + 4, itemY + 13, 0xFF00E676, false);
            }
        }
    }

    // ==========================================
    // ESP SETTINGS VIEW (DUAL COLUMN + MANNEQUIN)
    // ==========================================
    private void renderEspSettingsView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH, float dt) {
        this.contentTotalHeight = contentH; // Fits without scrolling
        int innerY = contentY + 12;

        ModConfig config = ModConfig.getInstance();
        int leftColW = Math.min(320, contentW / 2 - 10);
        int leftColX = contentX + 14;

        int rightColX = leftColX + leftColW + 14;
        int rightColW = contentW - leftColW - 38;
        int rightColH = contentH - 24;

        // LEFT COLUMN: Toggles & Color Palette
        String espTitle = "⭐ PLAYER ESP CONFIGURATION";
        graphics.text(this.font, Component.literal("§l" + espTitle), leftColX, innerY, 0xFFFFFFFF, false);
        int espTitleW = this.font.width(espTitle);
        JelloTheme.fillHorizontalGradient(graphics, leftColX, innerY + 13, leftColX + espTitleW + 10, innerY + 14, JelloTheme.ACCENT, 0x33FF5252);
        int cardY = innerY + 20;

        cardY = renderEspToggleCard(graphics, mouseX, mouseY, leftColX, cardY, leftColW, "ESP Master Switch", "Toggle all player ESP visuals on/off", config.espEnabled, dt);
        cardY = renderEspToggleCard(graphics, mouseX, mouseY, leftColX, cardY, leftColW, "2D Bounding Box", "Framed player bounds with corner accents", config.espBox, dt);
        cardY = renderEspToggleCard(graphics, mouseX, mouseY, leftColX, cardY, leftColW, "Health Bar", "Dynamic health indicator with numeric HP", config.espHealth, dt);
        cardY = renderEspToggleCard(graphics, mouseX, mouseY, leftColX, cardY, leftColW, "Player Nametags", "Distance badge with heart counter", config.espNametags, dt);
        cardY = renderEspToggleCard(graphics, mouseX, mouseY, leftColX, cardY, leftColW, "Skeleton Wireframe", "Player joint & bone outline connections", config.espSkeleton, dt);
        cardY = renderEspToggleCard(graphics, mouseX, mouseY, leftColX, cardY, leftColW, "Armor & Held Items", "Equipped armor slots & durabilities", config.espArmor, dt);

        // COLOR PALETTE SECTION
        int paletteY = cardY + 6;
        graphics.text(this.font, Component.literal("§lESP ACCENT COLOR"), leftColX, paletteY, 0xFFE0E0E0, false);
        int chipsY = paletteY + 14;

        int chipSize = 22;
        int chipGap = 12;
        for (int i = 0; i < PALETTE_COLORS.length; i++) {
            int cx = leftColX + i * (chipSize + chipGap);
            boolean isSelected = config.espColorIndex == i;
            boolean hovered = mouseX >= cx - 2 && mouseX <= cx + chipSize + 2 && mouseY >= chipsY - 2 && mouseY <= chipsY + chipSize + 2;

            int color = PALETTE_COLORS[i];

            if (isSelected) {
                JelloTheme.fillCircle(graphics, cx + chipSize / 2, chipsY + chipSize / 2, chipSize / 2 + 2, 0xFFFFFFFF);
            } else if (hovered) {
                JelloTheme.fillCircle(graphics, cx + chipSize / 2, chipsY + chipSize / 2, chipSize / 2 + 2, 0x66FFFFFF);
            }

            JelloTheme.fillCircle(graphics, cx + chipSize / 2, chipsY + chipSize / 2, chipSize / 2, color);

            if (isSelected) {
                graphics.centeredText(this.font, Component.literal("✔"), cx + chipSize / 2, chipsY + chipSize / 2 - 4, 0xFFFFFFFF);
            }
        }

        // Selected color name
        String activeColorName = PALETTE_NAMES[Mth.clamp(config.espColorIndex, 0, PALETTE_NAMES.length - 1)];
        graphics.text(this.font, Component.literal("§7Active: §f" + activeColorName), leftColX + 5 * (chipSize + chipGap) + 4, chipsY + 6, 0xFFAAAAAA, false);

        // MAX DISTANCE SLIDER
        int distY = chipsY + chipSize + 14;
        String distLabel = "Render Distance: §c" + config.espMaxDistance + "m";
        graphics.text(this.font, Component.literal(distLabel), leftColX, distY, 0xFFE0E0E0, false);

        int sliderY = distY + 14;
        int sliderW = leftColW - 10;
        int sliderH = 8;
        renderInteractiveSlider(graphics, mouseX, mouseY, leftColX, sliderY, sliderW, sliderH,
                "espDist", (config.espMaxDistance - 16) / 112.0f);

        // RIGHT COLUMN: LIVE MANNEQUIN PREVIEW PANEL
        renderLiveMannequinPreview(graphics, mouseX, mouseY, rightColX, innerY - 2, rightColW, rightColH, config, dt);
    }

    private int renderEspToggleCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, String title, String desc, boolean active, float dt) {
        int cardH = 38;
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + cardH;

        int bg = active ? (hovered ? JelloTheme.CARD_ACTIVE_HOVER : JelloTheme.CARD_ACTIVE)
                : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
        int border = active ? (hovered ? JelloTheme.ACCENT : 0x88E53935)
                : (hovered ? 0x44FFFFFF : JelloTheme.BORDER_SUBTLE);
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + cardH, 8, bg, border);

        if (active) {
            JelloTheme.fillRounded(graphics, x + 2, y + 5, x + 5, y + cardH - 5, 2, JelloTheme.ACCENT);
        }

        int titleColor = active ? 0xFFFFFFFF : 0xFFCCCCCC;
        graphics.text(this.font, Component.literal(title), x + 12, y + 6, titleColor, false);
        graphics.text(this.font, Component.literal("§7" + desc), x + 12, y + 20, 0xFF94A3B8, false);

        int switchW = 32;
        int switchH = 16;
        int switchX = x + w - switchW - 10;
        int switchY = y + (cardH - switchH) / 2;

        renderSmoothSwitch(graphics, mouseX, mouseY, switchX, switchY, switchW, switchH, title, active, dt);

        return y + cardH + 6;
    }

    // ==========================================
    // LIVE PREVIEW PANEL & MINECRAFT MANNEQUIN
    // ==========================================
    private void renderLiveMannequinPreview(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, ModConfig config, float dt) {
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 10, JelloTheme.CONTAINER_BG, JelloTheme.BORDER);

        // Header of Preview
        int pulseAlpha = 0xAA + (int) (Math.sin(System.currentTimeMillis() * 0.005) * 50);
        int liveDotColor = (pulseAlpha << 24) | 0x00E676;
        JelloTheme.fillCircle(graphics, x + 15, y + 15, 3, liveDotColor);
        graphics.text(this.font, Component.literal("§c§lLIVE PREVIEW §7- Steve Mannequin"), x + 24, y + 10, 0xFFFFFFFF, false);

        // Interactive Simulation Button on top right
        int simBtnW = 104;
        int simBtnH = 18;
        int simBtnX = x + w - simBtnW - 12;
        int simBtnY = y + 8;
        boolean simHovered = mouseX >= simBtnX && mouseX <= simBtnX + simBtnW && mouseY >= simBtnY && mouseY <= simBtnY + simBtnH;

        JelloTheme.fillRoundedCard(graphics, simBtnX, simBtnY, simBtnX + simBtnW, simBtnY + simBtnH, 5,
                simHovered ? 0x44E53935 : 0x22261217, simHovered ? JelloTheme.ACCENT : JelloTheme.BORDER);

        String simText = this.simulatedDamaged ? "⚡ Reset Health" : "⚔ Simulate Hit";
        graphics.centeredText(this.font, Component.literal(simText), simBtnX + simBtnW / 2, simBtnY + 4,
                simHovered ? JelloTheme.ACCENT_HOVER : 0xFFE0E0E0);

        // Subtle viewport grid
        int gridSpacing = 28;
        for (int gx = x + gridSpacing; gx < x + w; gx += gridSpacing) {
            graphics.fill(gx, y + 30, gx + 1, y + h - 10, 0x08FFFFFF);
        }
        for (int gy = y + 30 + gridSpacing; gy < y + h - 10; gy += gridSpacing) {
            graphics.fill(x + 10, gy, x + w - 10, gy + 1, 0x08FFFFFF);
        }

        int centerX = x + w / 2 - 20;
        int centerY = y + h / 2 + 14;

        int bobY = Math.round((float) Math.sin(this.mannequinBreathe) * 1.5f);
        int mannequinTop = centerY - 80 + bobY;
        int mannequinBottom = centerY + 80 + bobY;
        int mannequinLeft = centerX - 30;
        int mannequinRight = centerX + 30;

        JelloTheme.fillRounded(graphics, centerX - 35, mannequinBottom + 2, centerX + 35, mannequinBottom + 7, 3, 0x55000000);
        drawStylizedMannequin(graphics, centerX, mannequinTop, mannequinBottom);

        if (config.espEnabled) {
            int espColor = config.getEspColorHex();
            float simulatedHealth = this.simulatedDamaged ? 7.5f : 20.0f;
            float maxHealth = 20.0f;
            float hpPercent = Mth.clamp(simulatedHealth / maxHealth, 0.0f, 1.0f);

            if (config.espBox) {
                renderPreviewBox(graphics, mannequinLeft, mannequinTop, mannequinRight, mannequinBottom, espColor);
            }
            if (config.espHealth) {
                renderPreviewHealthBar(graphics, mannequinLeft, mannequinTop, mannequinBottom, simulatedHealth, hpPercent);
            }
            if (config.espSkeleton) {
                renderPreviewSkeleton(graphics, mannequinLeft, mannequinTop, mannequinRight, mannequinBottom, espColor);
            }
            if (config.espNametags) {
                renderPreviewNametag(graphics, mannequinLeft, mannequinTop, mannequinRight, simulatedHealth, hpPercent, espColor);
            }
            if (config.espArmor) {
                renderPreviewArmor(graphics, mannequinRight, mannequinTop);
            }
        }
    }

    private void drawStylizedMannequin(GuiGraphicsExtractor graphics, int midX, int top, int bottom) {
        int headTop = top + 8;
        int headBottom = headTop + 28;
        int torsoTop = headBottom;
        int torsoBottom = torsoTop + 44;
        int legBottom = bottom;

        graphics.fill(midX - 14, headTop, midX + 14, headBottom, 0xFFD29D74);
        graphics.fill(midX - 14, headTop, midX + 14, headTop + 9, 0xFF4B3322);
        graphics.fill(midX - 14, headTop, midX - 10, headTop + 16, 0xFF4B3322);
        graphics.fill(midX + 10, headTop, midX + 14, headTop + 16, 0xFF4B3322);
        graphics.fill(midX - 10, headTop + 14, midX - 5, headTop + 18, 0xFFFFFFFF);
        graphics.fill(midX - 8, headTop + 14, midX - 5, headTop + 18, 0xFF2E5CB8);
        graphics.fill(midX + 5, headTop + 14, midX + 10, headTop + 18, 0xFFFFFFFF);
        graphics.fill(midX + 5, headTop + 14, midX + 8, headTop + 18, 0xFF2E5CB8);
        graphics.fill(midX - 4, headTop + 18, midX + 4, headTop + 21, 0xFFBD845C);
        graphics.fill(midX - 7, headTop + 22, midX + 7, headTop + 26, 0xFF3C2618);

        graphics.fill(midX - 14, torsoTop, midX + 14, torsoBottom, 0xFF00A2B0);
        graphics.fill(midX - 6, torsoTop, midX + 6, torsoTop + 6, 0xFFD29D74);

        graphics.fill(midX - 26, torsoTop, midX - 14, torsoTop + 16, 0xFF00A2B0);
        graphics.fill(midX - 26, torsoTop + 16, midX - 14, torsoBottom - 2, 0xFFD29D74);
        graphics.fill(midX + 14, torsoTop, midX + 26, torsoTop + 16, 0xFF00A2B0);
        graphics.fill(midX + 14, torsoTop + 16, midX + 26, torsoBottom - 2, 0xFFD29D74);

        graphics.fill(midX - 14, torsoBottom, midX - 1, legBottom - 10, 0xFF29326D);
        graphics.fill(midX - 14, legBottom - 10, midX - 1, legBottom, 0xFF444444);
        graphics.fill(midX + 1, torsoBottom, midX + 14, legBottom - 10, 0xFF29326D);
        graphics.fill(midX + 1, legBottom - 10, midX + 14, legBottom, 0xFF444444);
    }

    private void renderPreviewBox(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        int boxBg = (0x22 << 24) | (color & 0x00FFFFFF);
        graphics.fill(left, top, right, bottom, boxBg);

        graphics.fill(left - 1, top - 1, right + 1, top, 0xFF000000);
        graphics.fill(left - 1, bottom, right + 1, bottom + 1, 0xFF000000);
        graphics.fill(left - 1, top, left, bottom, 0xFF000000);
        graphics.fill(right, top, right + 1, bottom, 0xFF000000);

        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        graphics.fill(right - 1, top + 1, right, bottom - 1, color);

        int cLen = 8;
        graphics.fill(left - 1, top - 1, left + cLen, top + 1, color);
        graphics.fill(left - 1, top - 1, left + 1, top + cLen, color);

        graphics.fill(right - cLen, top - 1, right + 1, top + 1, color);
        graphics.fill(right - 1, top - 1, right + 1, top + cLen, color);

        graphics.fill(left - 1, bottom - 1, left + cLen, bottom + 1, color);
        graphics.fill(left - 1, bottom - cLen, left + 1, bottom + 1, color);

        graphics.fill(right - cLen, bottom - 1, right + 1, bottom + 1, color);
        graphics.fill(right - 1, bottom - cLen, right + 1, bottom + 1, color);
    }

    private void renderPreviewHealthBar(GuiGraphicsExtractor graphics, int left, int top, int bottom, float health, float hpPercent) {
        int barW = 3;
        int barRight = left - 3;
        int barLeft = barRight - barW;

        graphics.fill(barLeft - 1, top - 1, barRight + 1, bottom + 1, 0xFF000000);

        int totalH = bottom - top;
        int filledH = Math.max(1, (int) (totalH * hpPercent));
        int hpTop = bottom - filledH;
        int hpColor = EspRenderHandler.getHealthColor(hpPercent);

        graphics.fill(barLeft, hpTop, barRight, bottom, hpColor);

        String hpStr = String.valueOf((int) Math.ceil(health));
        int hpW = this.font.width(hpStr);
        graphics.text(this.font, Component.literal(hpStr), barLeft - hpW - 2, hpTop - 2, 0xFFFFFFFF, true);
    }

    private void renderPreviewSkeleton(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        int midX = (left + right) / 2;
        int headBottom = top + 36;
        int spineBottom = top + 98;

        EspRenderHandler.drawLine(graphics, midX, headBottom, midX, spineBottom, color);

        int shoulderY = top + 44;
        int handY = top + 90;
        EspRenderHandler.drawLine(graphics, midX, shoulderY, left + 6, handY, color);
        EspRenderHandler.drawLine(graphics, midX, shoulderY, right - 6, handY, color);

        int kneeY = top + 130;
        int legSpan = 9;
        EspRenderHandler.drawLine(graphics, midX, spineBottom, midX - legSpan, kneeY, color);
        EspRenderHandler.drawLine(graphics, midX - legSpan, kneeY, midX - legSpan, bottom, color);
        EspRenderHandler.drawLine(graphics, midX, spineBottom, midX + legSpan, kneeY, color);
        EspRenderHandler.drawLine(graphics, midX + legSpan, kneeY, midX + legSpan, bottom, color);

        int headRad = 11;
        graphics.fill(midX - headRad, top + 8, midX + headRad, headBottom, (0x33 << 24) | (color & 0x00FFFFFF));
        graphics.fill(midX - headRad, top + 8, midX + headRad, top + 9, color);
        graphics.fill(midX - headRad, headBottom - 1, midX + headRad, headBottom, color);
        graphics.fill(midX - headRad, top + 8, midX - headRad + 1, headBottom, color);
        graphics.fill(midX + headRad - 1, top + 8, midX + headRad, headBottom, color);
    }

    private void renderPreviewNametag(GuiGraphicsExtractor graphics, int left, int top, int right, float health, float hpPercent, int espColor) {
        String hpColorCode = hpPercent > 0.6f ? "§a" : (hpPercent > 0.25f ? "§e" : "§c");
        String badge = "Steve §7[12m] " + hpColorCode + (int) Math.ceil(health) + "❤";

        int badgeW = this.font.width(badge);
        int midX = (left + right) / 2;
        int badgeX = midX - badgeW / 2;
        int badgeY = top - 15;

        JelloTheme.fillRounded(graphics, badgeX - 6, badgeY - 2, badgeX + badgeW + 6, badgeY + 11, 4, 0xDD0D0D11);
        JelloTheme.fillRounded(graphics, badgeX - 6, badgeY - 2, badgeX - 4, badgeY + 11, 1, espColor);

        graphics.text(this.font, Component.literal(badge), badgeX, badgeY, 0xFFFFFFFF, true);
    }

    private void renderPreviewArmor(GuiGraphicsExtractor graphics, int right, int top) {
        int itemX = right + 6;
        int itemY = top + 4;

        ItemStack[] previewItems = {
                HELMET_STACK,
                CHEST_STACK,
                LEGS_STACK,
                BOOTS_STACK,
                SWORD_STACK
        };

        String[] durStrings = {
                "100%",
                "88%",
                "94%",
                this.simulatedDamaged ? "24%" : "72%",
                "100%"
        };

        int[] durColors = {
                0xFF00FF66,
                0xFF00FF66,
                0xFF00FF66,
                this.simulatedDamaged ? 0xFFFF3333 : 0xFFFFAA00,
                0xFF00FF66
        };

        for (int i = 0; i < previewItems.length; i++) {
            ItemStack stack = previewItems[i];
            graphics.item(stack, itemX, itemY);
            graphics.itemDecorations(this.font, stack, itemX, itemY);
            graphics.text(this.font, Component.literal(durStrings[i]), itemX + 18, itemY + 4, durColors[i], true);
            itemY += 17;
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
        int leftColW = Math.max(280, contentW - 250);
        int leftColX = contentX + 14;
        int rightColX = leftColX + leftColW + 12;
        int rightColW = contentW - leftColW - 38;
        int rightColH = contentH - 24;

        graphics.text(this.font, Component.literal("§l⌨ CONFIGURED MACROS"), leftColX, innerY, 0xFFFFFFFF, false);
        graphics.text(this.font, Component.literal("§7" + config.macros.size() + " active macros"), leftColX + leftColW - 130, innerY, JelloTheme.TEXT_DIM, false);
        JelloTheme.fillHorizontalGradient(graphics, leftColX, innerY + 13, leftColX + 140, innerY + 14, JelloTheme.ACCENT, 0x33FF5252);

        int listStartY = innerY + 24;
        int cardH = 46;
        int cardGap = 8;
        int totalListH = 30 + config.macros.size() * (cardH + cardGap);
        this.contentTotalHeight = Math.max(contentH, totalListH);

        if (config.macros.isEmpty()) {
            int emptyH = 80;
            JelloTheme.fillRoundedCard(graphics, leftColX, listStartY, leftColX + leftColW, listStartY + emptyH, 8, JelloTheme.CONTAINER_BG, 0x22FFFFFF);
            graphics.centeredText(this.font, Component.literal("§7No macros configured yet."), leftColX + leftColW / 2, listStartY + 26, 0xFFAAAAAA);
            graphics.centeredText(this.font, Component.literal("§8Click '+ Add New Macro' on the right to create your first macro!"), leftColX + leftColW / 2, listStartY + 44, 0xFF777777);
        } else {
            for (int i = 0; i < config.macros.size(); i++) {
                ModConfig.MacroEntry m = config.macros.get(i);
                int cardY = listStartY + i * (cardH + cardGap) - (int) this.scrollAmount;

                if (cardY + cardH < contentY || cardY > contentY + contentH) {
                    continue;
                }

                boolean isEditingThis = (this.editingMacroIndex == i);
                boolean hovered = mouseX >= leftColX && mouseX <= leftColX + leftColW && mouseY >= cardY && mouseY <= cardY + cardH;

                int cardBg = isEditingThis ? 0xEE381820 : (hovered ? 0xDD2A1319 : 0xCC1A0E12);
                int borderColor = isEditingThis ? JelloTheme.ACCENT : (hovered ? 0x88E53935 : JelloTheme.BORDER_SUBTLE);
                JelloTheme.fillRoundedCard(graphics, leftColX, cardY, leftColX + leftColW, cardY + cardH, 8, cardBg, borderColor);

                if (isEditingThis) {
                    JelloTheme.fillRounded(graphics, leftColX + 2, cardY + 5, leftColX + 5, cardY + cardH - 5, 2, JelloTheme.ACCENT_HOVER);
                }

                String nameTitle = (m.name != null && !m.name.isEmpty()) ? m.name : "Unnamed Macro";
                graphics.text(this.font, Component.literal("§f§l" + nameTitle), leftColX + 12, cardY + 9, 0xFFFFFFFF, false);

                String cmdPreview = (m.message != null && !m.message.isEmpty()) ? m.message : "/help";
                if (cmdPreview.length() > 30) {
                    cmdPreview = cmdPreview.substring(0, 27) + "...";
                }
                int cmdColor = cmdPreview.startsWith("/") ? JelloTheme.TEXT_DIM : 0xFF81C784;
                graphics.text(this.font, Component.literal("§7Cmd: " + cmdPreview), leftColX + 12, cardY + 25, cmdColor, false);

                String bindText = ClickGuiScreen.getBindDisplayName(m.keyCode, m.isMouse);
                int bindBadgeW = Math.max(48, this.font.width(bindText) + 16);
                int bindBadgeH = 20;
                int bindBadgeX = leftColX + leftColW - 68 - bindBadgeW;
                int bindBadgeY = cardY + 13;

                boolean bindHovered = mouseX >= bindBadgeX && mouseX <= bindBadgeX + bindBadgeW && mouseY >= bindBadgeY && mouseY <= bindBadgeY + bindBadgeH;
                int bindBg = (isEditingThis && this.listeningMacroKey) ? 0x66E53935 : (bindHovered ? 0x35E53935 : 0x1AE53935);
                int bindBorder = (isEditingThis && this.listeningMacroKey) ? JelloTheme.ACCENT_HOVER : (bindHovered ? 0x88E53935 : 0x33E53935);
                JelloTheme.fillRoundedCard(graphics, bindBadgeX, bindBadgeY, bindBadgeX + bindBadgeW, bindBadgeY + bindBadgeH, 4, bindBg, bindBorder);

                int bindTextColor = (isEditingThis && this.listeningMacroKey) ? JelloTheme.ACCENT_HOVER : (m.keyCode >= 0 ? 0xFFFFD54F : 0xFF888888);
                graphics.centeredText(this.font, Component.literal(bindText), bindBadgeX + bindBadgeW / 2, bindBadgeY + 6, bindTextColor);

                int editBtnX = leftColX + leftColW - 60;
                int editBtnY = cardY + 13;
                int editBtnW = 24;
                int editBtnH = 20;
                boolean editHovered = mouseX >= editBtnX && mouseX <= editBtnX + editBtnW && mouseY >= editBtnY && mouseY <= editBtnY + editBtnH;

                JelloTheme.fillRounded(graphics, editBtnX, editBtnY, editBtnX + editBtnW, editBtnY + editBtnH, 4, editHovered ? 0x44E53935 : 0x1AFFFFFF);
                graphics.centeredText(this.font, Component.literal("✏"), editBtnX + editBtnW / 2, editBtnY + 5, editHovered ? 0xFFFFFFFF : 0xFFCCCCCC);

                int delBtnX = leftColX + leftColW - 30;
                int delBtnY = cardY + 13;
                int delBtnW = 24;
                int delBtnH = 20;
                boolean delHovered = mouseX >= delBtnX && mouseX <= delBtnX + delBtnW && mouseY >= delBtnY && mouseY <= delBtnY + delBtnH;

                JelloTheme.fillRounded(graphics, delBtnX, delBtnY, delBtnX + delBtnW, delBtnY + delBtnH, 4, delHovered ? 0x66FF4444 : 0x1AFFFFFF);
                graphics.centeredText(this.font, Component.literal("✕"), delBtnX + delBtnW / 2, delBtnY + 5, delHovered ? 0xFFFFFFFF : 0xFFFF6666);
            }
        }

        renderMacroEditorOrHelp(graphics, mouseX, mouseY, rightColX, innerY - 2, rightColW, rightColH, config);
    }

    private void renderMacroEditorOrHelp(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, ModConfig config) {
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 10, JelloTheme.CONTAINER_BG, 0x44E53935);

        if (this.editingMacroIndex != -1) {
            String title = (this.editingMacroIndex == -2) ? "§l➕ CREATE NEW MACRO" : "§l✏ EDIT MACRO";
            graphics.text(this.font, Component.literal(title), x + 12, y + 12, 0xFFFFFFFF, false);
            int mTitleW = this.font.width((this.editingMacroIndex == -2) ? "CREATE NEW MACRO" : "EDIT MACRO");
            JelloTheme.fillHorizontalGradient(graphics, x + 12, y + 25, x + 12 + mTitleW + 20, y + 26, JelloTheme.ACCENT, 0x33FF5252);

            int formY = y + 36;
            int boxW = Math.max(120, w - 24);

            graphics.text(this.font, Component.literal("§7Macro Name:"), x + 12, formY, 0xFFAAAAAA, false);
            JelloTheme.fillRoundedCard(graphics, x + 12, formY + 10, x + 12 + boxW, formY + 30, 5, 0x55000000, 0x33E53935);
            if (this.macroNameBox != null) {
                this.macroNameBox.setX(x + 16);
                this.macroNameBox.setY(formY + 14);
                this.macroNameBox.setWidth(boxW - 8);
                this.macroNameBox.visible = true;
            }
            formY += 40;

            graphics.text(this.font, Component.literal("§7Command or Chat Message:"), x + 12, formY, 0xFFAAAAAA, false);
            JelloTheme.fillRoundedCard(graphics, x + 12, formY + 10, x + 12 + boxW, formY + 30, 5, 0x55000000, 0x33E53935);
            if (this.macroCmdBox != null) {
                this.macroCmdBox.setX(x + 16);
                this.macroCmdBox.setY(formY + 14);
                this.macroCmdBox.setWidth(boxW - 8);
                this.macroCmdBox.visible = true;
            }
            formY += 40;

            graphics.text(this.font, Component.literal("§7Trigger Keybind:"), x + 12, formY, 0xFFAAAAAA, false);
            int bindBtnX = x + 12;
            int bindBtnY = formY + 12;
            int bindBtnW = boxW;
            int bindBtnH = 22;

            boolean bindHovered = mouseX >= bindBtnX && mouseX <= bindBtnX + bindBtnW && mouseY >= bindBtnY && mouseY <= bindBtnY + bindBtnH;
            int bindBg = this.listeningMacroKey ? 0x55E53935 : (bindHovered ? 0x33FFFFFF : 0x1AFFFFFF);
            int bindBorder = this.listeningMacroKey ? JelloTheme.ACCENT_HOVER : (bindHovered ? 0x66FFFFFF : 0x22FFFFFF);

            JelloTheme.fillRoundedCard(graphics, bindBtnX, bindBtnY, bindBtnX + bindBtnW, bindBtnY + bindBtnH, 5, bindBg, bindBorder);

            String keyDisplay = this.listeningMacroKey ? "⌨ Press any key... (Esc: cancel)" : ClickGuiScreen.getBindDisplayName(this.tempMacroKey, this.tempMacroIsMouse);
            int keyColor = this.listeningMacroKey ? JelloTheme.ACCENT_HOVER : (this.tempMacroKey >= 0 ? 0xFFFFD54F : 0xFFFFFFFF);
            graphics.centeredText(this.font, Component.literal(keyDisplay), bindBtnX + bindBtnW / 2, bindBtnY + 6, keyColor);

            graphics.text(this.font, Component.literal("§8Left-click to bind, Right-click to clear"), x + 12, formY + 38, 0xFF666666, false);
            formY += 56;

            int btnH = 24;
            int btnW = (boxW - 8) / 2;
            int saveBtnX = x + 12;
            int cancelBtnX = saveBtnX + btnW + 8;
            int actionBtnY = formY;

            boolean saveHov = mouseX >= saveBtnX && mouseX <= saveBtnX + btnW && mouseY >= actionBtnY && mouseY <= actionBtnY + btnH;
            boolean cancelHov = mouseX >= cancelBtnX && mouseX <= cancelBtnX + btnW && mouseY >= actionBtnY && mouseY <= actionBtnY + btnH;

            JelloTheme.fillRounded(graphics, saveBtnX, actionBtnY, saveBtnX + btnW, actionBtnY + btnH, 5, saveHov ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT);
            graphics.centeredText(this.font, Component.literal("💾 Save Macro"), saveBtnX + btnW / 2, actionBtnY + 7, 0xFFFFFFFF);

            JelloTheme.fillRounded(graphics, cancelBtnX, actionBtnY, cancelBtnX + btnW, actionBtnY + btnH, 5, cancelHov ? 0x44FFFFFF : 0x22FFFFFF);
            graphics.centeredText(this.font, Component.literal("Cancel"), cancelBtnX + btnW / 2, actionBtnY + 7, cancelHov ? 0xFFFFFFFF : 0xFFCCCCCC);

        } else {
            if (this.macroNameBox != null) this.macroNameBox.visible = false;
            if (this.macroCmdBox != null) this.macroCmdBox.visible = false;

            graphics.text(this.font, Component.literal("§lℹ MACRO GUIDE"), x + 12, y + 12, 0xFFFFFFFF, false);
            JelloTheme.fillHorizontalGradient(graphics, x + 12, y + 25, x + 120, y + 26, JelloTheme.ACCENT, 0x33FF5252);

            int addBtnX = x + 12;
            int addBtnY = y + 36;
            int addBtnW = w - 24;
            int addBtnH = 26;
            boolean addHovered = mouseX >= addBtnX && mouseX <= addBtnX + addBtnW && mouseY >= addBtnY && mouseY <= addBtnY + addBtnH;

            JelloTheme.fillRounded(graphics, addBtnX, addBtnY, addBtnX + addBtnW, addBtnY + addBtnH, 6, addHovered ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT);
            graphics.centeredText(this.font, Component.literal("§l+ Add New Macro"), addBtnX + addBtnW / 2, addBtnY + 8, 0xFFFFFFFF);

            int infoY = addBtnY + addBtnH + 18;
            graphics.text(this.font, Component.literal("§f§lHow Macros Work:"), x + 12, infoY, 0xFFFFFFFF, false);
            infoY += 16;
            graphics.text(this.font, Component.literal("§7• Pressing your bound key automatically"), x + 12, infoY, 0xFFAAAAAA, false);
            infoY += 12;
            graphics.text(this.font, Component.literal("§7  sends the command or message in-game."), x + 12, infoY, 0xFFAAAAAA, false);
            infoY += 18;
            graphics.text(this.font, Component.literal("§f§lCommands & Chat:"), x + 12, infoY, 0xFFFFFFFF, false);
            infoY += 16;
            graphics.text(this.font, Component.literal("§7• Start with §b'/' §7for commands:"), x + 12, infoY, 0xFFAAAAAA, false);
            infoY += 12;
            graphics.text(this.font, Component.literal("§e  /hub, /craft, /spawn, /gfs"), x + 12, infoY, 0xFFE0E0E0, false);
            infoY += 16;
            graphics.text(this.font, Component.literal("§7• Plain text sends to public chat:"), x + 12, infoY, 0xFFAAAAAA, false);
            infoY += 12;
            graphics.text(this.font, Component.literal("§a  gg, glhf, Thanks for game!"), x + 12, infoY, 0xFFE0E0E0, false);
            infoY += 18;
            graphics.text(this.font, Component.literal("§f§lKey & Mouse Bindings:"), x + 12, infoY, 0xFFFFFFFF, false);
            infoY += 16;
            graphics.text(this.font, Component.literal("§7• Supports any keyboard key or mouse"), x + 12, infoY, 0xFFAAAAAA, false);
            infoY += 12;
            graphics.text(this.font, Component.literal("§7  button (M3, M4, M5)."), x + 12, infoY, 0xFFAAAAAA, false);
            infoY += 12;
            graphics.text(this.font, Component.literal("§7• Right-click bind badge to unbind."), x + 12, infoY, 0xFFAAAAAA, false);
        }
    }

    // ==========================================
    // MODULE CARDS VIEW FOR ALL CATEGORIES & SEARCH
    // ==========================================
    private void renderModuleCardsView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH, float dt) {
        List<ClickGuiScreen.ModuleEntry> modules = getModulesForCurrentTab();
        String headerTitle = this.activeTab.icon + " " + this.activeTab.label.toUpperCase(Locale.ROOT) + " MODULES";
        String emptyMsg = "§7No active modules loaded under §f" + this.activeTab.label;
        renderModuleGrid(graphics, mouseX, mouseY, contentX, contentY, contentW, headerTitle, modules, emptyMsg, dt);
    }

    private void renderSearchResults(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH, float dt) {
        List<ClickGuiScreen.ModuleEntry> matches = getSearchMatches();
        String headerTitle = "🔍 SEARCH RESULTS (" + matches.size() + " matches for '" + this.searchQuery + "')";
        String emptyMsg = "§7No modules matched your search term '§f" + this.searchQuery + "§7'";
        renderModuleGrid(graphics, mouseX, mouseY, contentX, contentY, contentW, headerTitle, matches, emptyMsg, dt);
    }

    private void renderModuleGrid(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            int contentX,
            int contentY,
            int contentW,
            String headerTitle,
            List<ClickGuiScreen.ModuleEntry> modules,
            String emptyMessage,
            float dt
    ) {
        int topY = (int) (contentY + 12 - this.scrollAmount);
        graphics.text(this.font, Component.literal("§l" + headerTitle), contentX + 14, topY, 0xFFFFFFFF, false);
        int headTitleW = this.font.width(headerTitle);
        JelloTheme.fillHorizontalGradient(graphics, contentX + 14, topY + 13, contentX + 14 + headTitleW, topY + 14, JelloTheme.ACCENT, 0x33FF5252);

        if (modules.isEmpty()) {
            int emptyY = topY + 36;
            JelloTheme.fillRoundedCard(graphics, contentX + 14, emptyY, contentX + contentW - 14, emptyY + 50, 8, 0x660E0F14, 0x22FFFFFF);
            graphics.centeredText(this.font, Component.literal(emptyMessage), contentX + contentW / 2, emptyY + 18, 0xFFAAAAAA);
            this.contentTotalHeight = 120;
            return;
        }

        int startY = topY + 22;
        int colW = (contentW - 38) / 2;
        int col1X = contentX + 14;
        int col2X = col1X + colW + 10;

        int rowY1 = startY;
        int rowY2 = startY;

        for (int i = 0; i < modules.size(); i++) {
            ClickGuiScreen.ModuleEntry mod = modules.get(i);
            boolean inCol1 = (i % 2 == 0);
            int cx = inCol1 ? col1X : col2X;
            int cy = inCol1 ? rowY1 : rowY2;

            int cardH = renderModuleCard(graphics, mouseX, mouseY, cx, cy, colW, mod, dt);
            if (inCol1) {
                rowY1 += cardH + 8;
            } else {
                rowY2 += cardH + 8;
            }
        }

        int maxRowY = Math.max(rowY1, rowY2);
        this.contentTotalHeight = (int) (maxRowY - contentY + this.scrollAmount + 20);
    }

    private int renderModuleCard(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, ClickGuiScreen.ModuleEntry mod, float dt) {
        boolean active = mod.getter.get();
        ModConfig cfg = ModConfig.getInstance();
        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls(mod.name, cfg, this.settingsActions);
        boolean hasSettings = !controls.isEmpty();
        boolean isExpanded = hasSettings && mod.name.equals(this.expandedModule);
        int baseH = 46;
        int extraH = isExpanded ? ModernModuleSettings.computeExpandedHeight(controls) : 0;
        int totalH = baseH + extraH;

        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + totalH;

        int bg = active ? (hovered ? JelloTheme.CARD_ACTIVE_HOVER : JelloTheme.CARD_ACTIVE)
                : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
        int border = active ? (hovered ? JelloTheme.ACCENT : 0x88E53935)
                : (hovered ? 0x44FFFFFF : JelloTheme.BORDER_SUBTLE);
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + totalH, 8, bg, border);

        if (active) {
            JelloTheme.fillRounded(graphics, x + 2, y + 5, x + 5, y + totalH - 5, 2, JelloTheme.ACCENT);
        }

        // Module Title & Description
        graphics.text(this.font, Component.literal(mod.name), x + 14, y + 9, active ? 0xFFFFFFFF : 0xFFCCCCCC, false);

        String desc = ModernModuleCardUi.getModuleDescription(mod.name, mod.category.name);
        int maxDescW = w - (hasSettings ? 122 : 98);
        if (this.font.width(desc) > maxDescW) {
            while (desc.length() > 3 && this.font.width(desc + "...") > maxDescW) {
                desc = desc.substring(0, desc.length() - 1);
            }
            desc = desc + "...";
        }
        graphics.text(this.font, Component.literal("§7" + desc), x + 14, y + 25, 0xFF94A3B8, false);

        // Keybind Pill
        boolean isListening = mod.name.equals(this.listeningKeybindModule);
        String bindTag = getBindPillText(cfg, mod.name, isListening);
        int bindW = this.font.width(bindTag) + 6;
        int bindX = computeBindX(x, w, bindW);
        int bindY = y + 15;

        boolean bindHovered = mouseX >= bindX && mouseX <= bindX + bindW && mouseY >= bindY && mouseY <= bindY + 16;
        int bindBg = isListening ? 0x66E53935 : (bindHovered ? 0x33E53935 : 0x1AE53935);
        int bindBorder = isListening ? JelloTheme.ACCENT_HOVER : (bindHovered ? 0x66E53935 : 0x22E53935);
        JelloTheme.fillRoundedCard(graphics, bindX, bindY, bindX + bindW, bindY + 16, 4, bindBg, bindBorder);
        graphics.centeredText(this.font, Component.literal(bindTag), bindX + bindW / 2, bindY + 4, isListening ? 0xFFFFDD55 : JelloTheme.TEXT_DIM);

        // Animated Toggle Switch
        int switchW = 32;
        int switchH = 16;
        int switchX = x + w - switchW - 12;
        int switchY = y + 15;
        renderSmoothSwitch(graphics, mouseX, mouseY, switchX, switchY, switchW, switchH, mod.name, active, dt);

        // Settings gear button only if module actually has configurable controls
        if (hasSettings) {
            int gearX = switchX - 22;
            int gearY = y + 15;
            boolean gearHov = mouseX >= gearX && mouseX <= gearX + 16 && mouseY >= gearY && mouseY <= gearY + 16;
            JelloTheme.fillRounded(graphics, gearX, gearY, gearX + 16, gearY + 16, 4,
                    isExpanded ? 0x44E53935 : (gearHov ? 0x22FFFFFF : 0x11FFFFFF));
            graphics.centeredText(this.font, Component.literal(isExpanded ? "▲" : "⚙"), gearX + 8, gearY + 4,
                    isExpanded ? JelloTheme.ACCENT_HOVER : 0xFF94A3B8);

            if (isExpanded) {
                ModernModuleCardUi.renderControls(
                        graphics,
                        this.font,
                        mouseX,
                        mouseY,
                        x + 12,
                        y + baseH,
                        w - 24,
                        mod.name,
                        controls,
                        (sx, sy, sw, sh, skey, pct) -> renderInteractiveSlider(graphics, mouseX, mouseY, sx, sy, sw, sh, skey, pct),
                        (tx, ty, tw, th, tkey, tactive) -> renderSmoothSwitch(graphics, mouseX, mouseY, tx, ty, tw, th, tkey, tactive, dt)
                );
            }
        }

        return totalH;
    }

    private static String getBindPillText(ModConfig cfg, String modName, boolean isListening) {
        if (isListening) {
            return "§e[...]";
        }
        int key = cfg.getModuleKey(modName);
        boolean isMouse = cfg.isModuleKeyMouse(modName);
        return "[" + ClickGuiScreen.getShortBindName(key, isMouse) + "]";
    }

    private static int computeBindX(int cardX, int cardW, int bindW) {
        return cardX + cardW - 74 - bindW;
    }

    // ==========================================
    // CLIENT SETTINGS VIEW
    // ==========================================
    private void renderClientSettingsView(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int contentX, int contentY, int contentW, int contentH, float dt) {
        int topY = (int) (contentY + 12 - this.scrollAmount);
        graphics.text(this.font, Component.literal("§l⚙ CLIENT CONFIGURATION"), contentX + 14, topY, 0xFFFFFFFF, false);
        int cTitleW = this.font.width("⚙ CLIENT CONFIGURATION");
        JelloTheme.fillHorizontalGradient(graphics, contentX + 14, topY + 13, contentX + 14 + cTitleW, topY + 14, JelloTheme.ACCENT, 0x33FF5252);

        ModConfig config = ModConfig.getInstance();
        int cardY = topY + 22;
        int colW = contentW - 28;

        cardY = renderEspToggleCard(graphics, mouseX, mouseY, contentX + 14, cardY, colW, "Modern Dashboard GUI", "Switch between Modern ClickGUI and Classic Floating Windows", config.clickGuiStyle == 1, dt);
        cardY = renderEspToggleCard(graphics, mouseX, mouseY, contentX + 14, cardY, colW, "Glass Background Blur", "Enable blurred backdrop behind ClickGUI screens", config.jelloBlur, dt);
        cardY = renderEspToggleCard(graphics, mouseX, mouseY, contentX + 14, cardY, colW, "Keybinds HUD Table", "Render active draggable keybinds overlay on screen", config.keybindsHudEnabled, dt);
        cardY = renderEspToggleCard(graphics, mouseX, mouseY, contentX + 14, cardY, colW, "Infinite Chat History", "Remove chat line limits and preserve all messages", config.chatInfinite, dt);
        cardY = renderEspToggleCard(graphics, mouseX, mouseY, contentX + 14, cardY, colW, "Anti Chat Clear", "Prevent servers from clearing your chat buffer", config.chatAntiClear, dt);

        this.contentTotalHeight = cardY - contentY + (int) this.scrollAmount + 20;
    }

    // ==========================================
    // REUSABLE UI COMPONENTS (SWITCHES, SLIDERS, SCROLLBAR)
    // ==========================================
    private void renderSmoothSwitch(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, String key, boolean active, float dt) {
        float currentAnim = this.switchAnimations.getOrDefault(key, active ? 1.0f : 0.0f);
        float target = active ? 1.0f : 0.0f;
        float nextAnim = currentAnim + (target - currentAnim) * Mth.clamp(dt * 18.0f, 0.05f, 1.0f);
        this.switchAnimations.put(key, nextAnim);

        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;

        int trackRadius = h / 2;
        int offColor = hovered ? JelloTheme.SWITCH_OFF_HOVER : JelloTheme.SWITCH_OFF;
        int onColor = hovered ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT;
        int trackColor = JelloTheme.lerpColor(offColor, onColor, nextAnim);
        int trackBorder = JelloTheme.lerpColor(0x33FFFFFF, 0x99FF5252, nextAnim);

        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, trackRadius, trackColor, trackBorder);

        int knobSize = h - 4;
        int knobMinX = x + 2;
        int knobMaxX = x + w - knobSize - 2;
        int knobX = Math.round(knobMinX + nextAnim * (knobMaxX - knobMinX));
        int knobY = y + 2;
        int knobRadius = knobSize / 2;

        int knobColor = hovered ? 0xFFFFFFFF : 0xFFF1F5F9;
        JelloTheme.fillCircle(graphics, knobX + knobRadius, knobY + knobRadius, knobRadius, knobColor);
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
        int knobColor = (isDraggingThis || hovered) ? 0xFFFFFFFF : 0xFFE2E8F0;
        JelloTheme.fillCircle(graphics, knobX, knobY, knobRadius, knobColor);
        JelloTheme.fillCircle(graphics, knobX, knobY, knobRadius - 2, isDraggingThis ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT);
    }

    private void renderScrollbar(GuiGraphicsExtractor graphics, int contentX, int contentY, int contentW, int contentH) {
        int maxScroll = Math.max(0, this.contentTotalHeight - contentH);
        if (maxScroll <= 0) return;

        int barX = contentX + contentW - 6;
        int viewH = contentH;
        int thumbH = Math.max(24, (int) ((float) (viewH * viewH) / this.contentTotalHeight));
        int thumbY = contentY + (int) ((this.scrollAmount / maxScroll) * (viewH - thumbH));

        JelloTheme.fillRounded(graphics, barX, contentY, barX + 4, contentY + contentH, 2, 0x33000000);
        JelloTheme.fillRounded(graphics, barX, thumbY, barX + 4, thumbY + thumbH, 2, 0xAAE53935);
    }

    // ==========================================
    // INPUT HANDLING: MOUSE & KEYBOARD
    // ==========================================
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int button = event.button();

        if (this.listeningKeybindModule != null && button >= 2) {
            ModConfig cfg = ModConfig.getInstance();
            cfg.setModuleKey(this.listeningKeybindModule, button, true);
            this.listeningKeybindModule = null;
            cfg.save();
            playClickSound();
            return true;
        }

        // 1. Close button
        int closeX = this.windowX + this.windowW - 30;
        int closeY = this.windowY + 13;
        if (mouseX >= closeX && mouseX <= closeX + 20 && mouseY >= closeY && mouseY <= closeY + 20) {
            playClickSound();
            onClose();
            return true;
        }

        // 2. Sidebar Tabs
        int sideX = this.windowX;
        int sideY = this.windowY + HEADER_HEIGHT;
        if (mouseX >= sideX && mouseX <= sideX + SIDEBAR_WIDTH && mouseY >= sideY && mouseY <= this.windowY + this.windowH) {
            int tabIndex = (mouseY - sideY) / TAB_HEIGHT;
            CategoryTab[] tabs = CategoryTab.values();
            if (tabIndex >= 0 && tabIndex < tabs.length) {
                this.activeTab = tabs[tabIndex];
                this.scrollAmount = 0.0;
                this.listeningKeybindModule = null;
                this.expandedModule = null;
                this.draggingSliderKey = null;
                this.activeSlider = null;
                if (this.activeTab != CategoryTab.MACROS) {
                    this.editingMacroIndex = -1;
                    this.listeningMacroKey = false;
                    if (this.macroNameBox != null) this.macroNameBox.visible = false;
                    if (this.macroCmdBox != null) this.macroCmdBox.visible = false;
                }
                playClickSound();
                return true;
            }
        }

        // 3. Content Area Clicks
        int contentX = this.windowX + SIDEBAR_WIDTH + 1;
        int contentY = this.windowY + HEADER_HEIGHT + 1;
        int contentW = this.windowW - SIDEBAR_WIDTH - 2;
        int contentH = this.windowH - HEADER_HEIGHT - 2;

        if (mouseX >= contentX && mouseX <= contentX + contentW && mouseY >= contentY && mouseY <= contentY + contentH) {
            if (!this.searchQuery.isEmpty()) {
                if (handleSearchResultsClick(mouseX, mouseY, button, contentX, contentY, contentW)) {
                    return true;
                }
            } else if (this.activeTab == CategoryTab.ESP) {
                if (handleEspSettingsClick(mouseX, mouseY, button, contentX, contentY, contentW, contentH)) {
                    return true;
                }
            } else if (this.activeTab == CategoryTab.MACROS) {
                if (handleMacrosClick(mouseX, mouseY, button, contentX, contentY, contentW, contentH)) {
                    return true;
                }
            } else if (this.activeTab == CategoryTab.SETTINGS) {
                if (handleClientSettingsClick(mouseX, mouseY, button, contentX, contentY, contentW)) {
                    return true;
                }
            } else {
                if (handleModuleCardsClick(mouseX, mouseY, button, contentX, contentY, contentW)) {
                    return true;
                }
            }
        }

        return super.mouseClicked(event, wasHandled);
    }

    private boolean handleEspSettingsClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW, int contentH) {
        if (button != 0) {
            return false;
        }
        ModConfig config = ModConfig.getInstance();
        int leftColW = Math.min(320, contentW / 2 - 10);
        int leftColX = contentX + 14;
        int innerY = contentY + 12;

        int rightColX = leftColX + leftColW + 14;
        int rightColW = contentW - leftColW - 38;

        int simBtnW = 104;
        int simBtnH = 18;
        int simBtnX = rightColX + rightColW - simBtnW - 12;
        int simBtnY = innerY + 6;
        if (mouseX >= simBtnX && mouseX <= simBtnX + simBtnW && mouseY >= simBtnY && mouseY <= simBtnY + simBtnH) {
            this.simulatedDamaged = !this.simulatedDamaged;
            playClickSound();
            return true;
        }

        int cardY = innerY + 20;
        int cardH = 38;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= leftColX && mouseX <= leftColX + leftColW) {
            config.espEnabled = !config.espEnabled;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= leftColX && mouseX <= leftColX + leftColW) {
            config.espBox = !config.espBox;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= leftColX && mouseX <= leftColX + leftColW) {
            config.espHealth = !config.espHealth;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= leftColX && mouseX <= leftColX + leftColW) {
            config.espNametags = !config.espNametags;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= leftColX && mouseX <= leftColX + leftColW) {
            config.espSkeleton = !config.espSkeleton;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= leftColX && mouseX <= leftColX + leftColW) {
            config.espArmor = !config.espArmor;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        int chipsY = cardY + 20;
        int chipSize = 22;
        int chipGap = 12;
        for (int i = 0; i < PALETTE_COLORS.length; i++) {
            int cx = leftColX + i * (chipSize + chipGap);
            if (mouseX >= cx - 2 && mouseX <= cx + chipSize + 2 && mouseY >= chipsY - 2 && mouseY <= chipsY + chipSize + 2) {
                config.espColorIndex = i;
                config.save();
                playClickSound();
                return true;
            }
        }

        int distSliderY = chipsY + chipSize + 28;
        int distSliderW = leftColW - 10;
        if (mouseX >= leftColX && mouseX <= leftColX + distSliderW && mouseY >= distSliderY - 4 && mouseY <= distSliderY + 12) {
            this.draggingSliderKey = "espDist";
            this.activeSlider = null;
            this.draggingSliderStartX = leftColX;
            this.draggingSliderWidth = distSliderW;
            updateSliderValue(mouseX);
            playClickSound();
            return true;
        }

        return false;
    }

    private boolean handleMacrosClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW, int contentH) {
        ModConfig config = ModConfig.getInstance();
        if (config.macros == null) {
            config.macros = new ArrayList<>();
        }

        int innerY = contentY + 12;
        int leftColW = Math.max(280, contentW - 250);
        int leftColX = contentX + 14;
        int rightColX = leftColX + leftColW + 12;
        int rightColW = contentW - leftColW - 38;
        int rightColH = contentH - 24;

        if (mouseX >= rightColX && mouseX <= rightColX + rightColW && mouseY >= innerY - 2 && mouseY <= innerY + rightColH) {
            if (this.editingMacroIndex != -1) {
                int formY = innerY + 36;
                int boxW = Math.max(120, rightColW - 24);
                formY += 40;
                formY += 40;

                int bindBtnX = rightColX + 12;
                int bindBtnY = formY + 12;
                int bindBtnW = boxW;
                int bindBtnH = 22;

                if (mouseX >= bindBtnX && mouseX <= bindBtnX + bindBtnW && mouseY >= bindBtnY && mouseY <= bindBtnY + bindBtnH) {
                    if (button == 1) {
                        this.tempMacroKey = -1;
                        this.tempMacroIsMouse = false;
                        this.listeningMacroKey = false;
                    } else {
                        this.listeningMacroKey = true;
                    }
                    playClickSound();
                    return true;
                }

                if (this.listeningMacroKey && button >= 2) {
                    this.tempMacroKey = button;
                    this.tempMacroIsMouse = true;
                    this.listeningMacroKey = false;
                    playClickSound();
                    return true;
                }

                formY += 56;

                int btnH = 24;
                int btnW = (boxW - 8) / 2;
                int saveBtnX = rightColX + 12;
                int cancelBtnX = saveBtnX + btnW + 8;
                int actionBtnY = formY;

                if (mouseX >= saveBtnX && mouseX <= saveBtnX + btnW && mouseY >= actionBtnY && mouseY <= actionBtnY + btnH) {
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

                    this.editingMacroIndex = -1;
                    this.listeningMacroKey = false;
                    if (this.macroNameBox != null) this.macroNameBox.visible = false;
                    if (this.macroCmdBox != null) this.macroCmdBox.visible = false;

                    playClickSound();
                    return true;
                }

                if (mouseX >= cancelBtnX && mouseX <= cancelBtnX + btnW && mouseY >= actionBtnY && mouseY <= actionBtnY + btnH) {
                    this.editingMacroIndex = -1;
                    this.listeningMacroKey = false;
                    if (this.macroNameBox != null) this.macroNameBox.visible = false;
                    if (this.macroCmdBox != null) this.macroCmdBox.visible = false;
                    playClickSound();
                    return true;
                }
            } else {
                int addBtnX = rightColX + 12;
                int addBtnY = innerY + 36;
                int addBtnW = rightColW - 24;
                int addBtnH = 26;

                if (mouseX >= addBtnX && mouseX <= addBtnX + addBtnW && mouseY >= addBtnY && mouseY <= addBtnY + addBtnH) {
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
            if (cardY + cardH < contentY || cardY > contentY + contentH) {
                continue;
            }

            if (mouseX >= leftColX && mouseX <= leftColX + leftColW && mouseY >= cardY && mouseY <= cardY + cardH) {
                ModConfig.MacroEntry m = config.macros.get(i);

                int delBtnX = leftColX + leftColW - 30;
                int delBtnY = cardY + 13;
                int delBtnW = 24;
                int delBtnH = 20;
                if (mouseX >= delBtnX && mouseX <= delBtnX + delBtnW && mouseY >= delBtnY && mouseY <= delBtnY + delBtnH) {
                    config.macros.remove(i);
                    if (this.editingMacroIndex == i) {
                        this.editingMacroIndex = -1;
                        if (this.macroNameBox != null) this.macroNameBox.visible = false;
                        if (this.macroCmdBox != null) this.macroCmdBox.visible = false;
                    } else if (this.editingMacroIndex > i) {
                        this.editingMacroIndex--;
                    }
                    config.save();
                    playClickSound();
                    return true;
                }

                int editBtnX = leftColX + leftColW - 60;
                int editBtnY = cardY + 13;
                int editBtnW = 24;
                int editBtnH = 20;
                if (mouseX >= editBtnX && mouseX <= editBtnX + editBtnW && mouseY >= editBtnY && mouseY <= editBtnY + editBtnH) {
                    loadMacroIntoEditor(i, m);
                    playClickSound();
                    return true;
                }

                String bindText = ClickGuiScreen.getBindDisplayName(m.keyCode, m.isMouse);
                int bindBadgeW = Math.max(48, this.font.width(bindText) + 16);
                int bindBadgeH = 20;
                int bindBadgeX = leftColX + leftColW - 68 - bindBadgeW;
                int bindBadgeY = cardY + 13;

                if (mouseX >= bindBadgeX && mouseX <= bindBadgeX + bindBadgeW && mouseY >= bindBadgeY && mouseY <= bindBadgeY + bindBadgeH) {
                    if (button == 1) {
                        m.keyCode = -1;
                        m.isMouse = false;
                        if (this.editingMacroIndex == i) {
                            this.tempMacroKey = -1;
                            this.tempMacroIsMouse = false;
                            this.listeningMacroKey = false;
                        }
                        config.save();
                    } else {
                        loadMacroIntoEditor(i, m);
                        this.listeningMacroKey = true;
                    }
                    playClickSound();
                    return true;
                }

                loadMacroIntoEditor(i, m);
                playClickSound();
                return true;
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

    private boolean handleModuleCardsClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW) {
        return handleModuleGridClick(mouseX, mouseY, button, contentX, contentY, contentW, getModulesForCurrentTab());
    }

    private boolean handleSearchResultsClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW) {
        return handleModuleGridClick(mouseX, mouseY, button, contentX, contentY, contentW, getSearchMatches());
    }

    private boolean handleModuleGridClick(
            int mouseX,
            int mouseY,
            int button,
            int contentX,
            int contentY,
            int contentW,
            List<ClickGuiScreen.ModuleEntry> modules
    ) {
        ModConfig cfg = ModConfig.getInstance();
        int topY = (int) (contentY + 12 - this.scrollAmount);
        int startY = topY + 22;
        int colW = (contentW - 38) / 2;
        int col1X = contentX + 14;
        int col2X = col1X + colW + 10;

        int rowY1 = startY;
        int rowY2 = startY;

        for (int i = 0; i < modules.size(); i++) {
            ClickGuiScreen.ModuleEntry mod = modules.get(i);
            boolean inCol1 = (i % 2 == 0);
            int cx = inCol1 ? col1X : col2X;
            int cy = inCol1 ? rowY1 : rowY2;
            List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls(mod.name, cfg, this.settingsActions);
            boolean hasSettings = !controls.isEmpty();
            boolean isExpanded = hasSettings && mod.name.equals(this.expandedModule);
            int baseH = 46;
            int extraH = isExpanded ? ModernModuleSettings.computeExpandedHeight(controls) : 0;
            int totalH = baseH + extraH;

            if (mouseX >= cx && mouseX <= cx + colW && mouseY >= cy && mouseY < cy + totalH) {
                if (handleCardKeybindClick(mouseX, mouseY, button, cx, cy, colW, cfg, mod.name)) {
                    return true;
                }

                if (hasSettings) {
                    int gearX = cx + colW - 32 - 12 - 22;
                    int gearY = cy + 15;
                    if (mouseX >= gearX && mouseX <= gearX + 16 && mouseY >= gearY && mouseY <= gearY + 16
                            && (button == 0 || button == 1)) {
                        toggleExpandedModule(mod.name, isExpanded);
                        return true;
                    }
                }

                if (isExpanded && mouseY >= cy + baseH) {
                    if (button == 0) {
                        ModernModuleCardUi.handleControlsClick(
                                mouseX,
                                mouseY,
                                cx + 12,
                                cy + baseH,
                                colW - 24,
                                controls,
                                (slider, startX, width, initialPct) -> {
                                    this.draggingSliderKey = slider.key();
                                    this.activeSlider = slider;
                                    this.draggingSliderStartX = startX;
                                    this.draggingSliderWidth = width;
                                    slider.pctSetter().accept(initialPct);
                                },
                                this::playClickSound
                        );
                        return true;
                    }
                    if (button == 1) {
                        toggleExpandedModule(mod.name, true);
                        return true;
                    }
                    return true;
                }

                // Card header/body: Left-click toggles module; Right-click opens settings ONLY when hasSettings
                if (button == 0) {
                    mod.setter.accept(!mod.getter.get());
                    playClickSound();
                    return true;
                }
                if (button == 1) {
                    if (hasSettings) {
                        toggleExpandedModule(mod.name, isExpanded);
                    }
                    return true;
                }
                return true;
            }

            if (inCol1) {
                rowY1 += totalH + 8;
            } else {
                rowY2 += totalH + 8;
            }
        }

        return false;
    }

    private boolean handleCardKeybindClick(int mouseX, int mouseY, int button, int cx, int cy, int colW, ModConfig cfg, String modName) {
        boolean isListening = modName.equals(this.listeningKeybindModule);
        String bindTag = getBindPillText(cfg, modName, isListening);
        int bindW = this.font.width(bindTag) + 6;
        int bindX = computeBindX(cx, colW, bindW);
        int bindY = cy + 15;

        if (mouseX < bindX || mouseX > bindX + bindW || mouseY < bindY || mouseY > bindY + 16) {
            return false;
        }
        if (button == 1) {
            cfg.setModuleKey(modName, -1, false);
            this.listeningKeybindModule = null;
            cfg.save();
            playClickSound();
        } else if (button == 0) {
            this.listeningKeybindModule = isListening ? null : modName;
            cfg.save();
            playClickSound();
        }
        return true;
    }

    private void toggleExpandedModule(String modName, boolean currentlyExpanded) {
        this.expandedModule = currentlyExpanded ? null : modName;
        this.draggingSliderKey = null;
        this.activeSlider = null;
        playClickSound();
    }

    private boolean handleClientSettingsClick(int mouseX, int mouseY, int button, int contentX, int contentY, int contentW) {
        if (button != 0) {
            return false;
        }
        ModConfig config = ModConfig.getInstance();
        int topY = (int) (contentY + 12 - this.scrollAmount);
        int cardY = topY + 22;
        int cardH = 38;
        int colW = contentW - 28;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= contentX + 14 && mouseX <= contentX + 14 + colW) {
            config.clickGuiStyle = (config.clickGuiStyle == 1) ? 0 : 1;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= contentX + 14 && mouseX <= contentX + 14 + colW) {
            config.jelloBlur = !config.jelloBlur;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= contentX + 14 && mouseX <= contentX + 14 + colW) {
            config.keybindsHudEnabled = !config.keybindsHudEnabled;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= contentX + 14 && mouseX <= contentX + 14 + colW) {
            config.chatInfinite = !config.chatInfinite;
            config.save();
            playClickSound();
            return true;
        }
        cardY += cardH + 6;

        if (mouseY >= cardY && mouseY <= cardY + cardH && mouseX >= contentX + 14 && mouseX <= contentX + 14 + colW) {
            config.chatAntiClear = !config.chatAntiClear;
            config.save();
            playClickSound();
            return true;
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
            if (needReload) {
                ModConfig.reloadChunks();
            }
            if (wasInvAlpha) {
                ModernModuleCardUi.flushInventoryConfigSave();
            }
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
        int maxScroll = Math.max(0, this.contentTotalHeight - (this.windowH - HEADER_HEIGHT - 2));
        if (maxScroll > 0) {
            this.scrollAmount = Mth.clamp(this.scrollAmount - scrollY * 26.0, 0.0, maxScroll);
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
                this.editingMacroIndex = -1;
                this.listeningMacroKey = false;
                if (this.macroNameBox != null) this.macroNameBox.visible = false;
                if (this.macroCmdBox != null) this.macroCmdBox.visible = false;
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
            return;
        }
        if ("espDist".equals(this.draggingSliderKey)) {
            ModConfig.getInstance().espMaxDistance = Math.round(16 + pct * 112.0f);
        }
    }

    private List<ClickGuiScreen.ModuleEntry> getModulesForCurrentTab() {
        ClickGuiScreen.Category targetCat = switch (this.activeTab) {
            case COMBAT -> ClickGuiScreen.Category.COMBAT;
            case MOVEMENT -> ClickGuiScreen.Category.MOVEMENT;
            case RENDER -> ClickGuiScreen.Category.RENDER;
            case WORLD -> ClickGuiScreen.Category.WORLD;
            case PLAYER -> ClickGuiScreen.Category.PLAYER;
            case EXPLOIT -> ClickGuiScreen.Category.EXPLOIT;
            default -> ClickGuiScreen.Category.MISC;
        };
        List<ClickGuiScreen.ModuleEntry> all = ClickGuiScreen.getAllModules();
        List<ClickGuiScreen.ModuleEntry> list = new ArrayList<>();
        for (ClickGuiScreen.ModuleEntry m : all) {
            if (m.category == targetCat) {
                list.add(m);
            }
        }
        return list;
    }

    private List<ClickGuiScreen.ModuleEntry> getSearchMatches() {
        String query = this.searchQuery.toLowerCase(Locale.ROOT);
        List<ClickGuiScreen.ModuleEntry> all = ClickGuiScreen.getAllModules();
        List<ClickGuiScreen.ModuleEntry> matches = new ArrayList<>();
        for (ClickGuiScreen.ModuleEntry m : all) {
            if (m.name.toLowerCase(Locale.ROOT).contains(query) || m.category.name.toLowerCase(Locale.ROOT).contains(query)) {
                matches.add(m);
            }
        }
        return matches;
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
