package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import dlovin.inventoryhud.InventoryHUDCommon;
import dlovin.inventoryhud.gui.InGameConfigScreen;
import dlovin.inventoryhud.gui.InventoryGui;
import dlovin.inventoryhud.platform.ConfigAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ClickGuiScreen {
    private static final Style COMFORTAA_STYLE = Style.EMPTY;

    private static final int PANEL_WIDTH = 124;
    private static final int MACRO_WIDTH = 178;
    private static final int HEADER_HEIGHT = 20;
    private static final int ROW_HEIGHT = 20;
    private static final int SUB_WIDTH = 200;

    private static final int TOP_BAR_W = 136;
    private static final int TOP_BAR_H = 20;
    private static final int TOP_BAR_Y = 4;

    private static final int SWITCH_W = 22;
    private static final int SWITCH_H = 10;

    public enum Category {
        COMBAT("combat", "Combat", "[C]", 220, 32, false),
        FUN("fun", "Fun", "[F]", 354, 32, false),
        EXPLOIT("exploit", "Exploit", "[E]", 20, 60, false),
        WORLD("world", "World", "[W]", 154, 60, false),
        RENDER("render", "Render", "[R]", 288, 60, false),
        PLAYER("player", "Player", "[P]", 422, 60, false),
        MOVEMENT("movement", "Movement", "[M]", 556, 60, false),
        MISC("misc", "Misc", "[*]", 690, 60, false);

        public final String id;
        public final String name;
        public final String icon;
        public final int defaultX;
        public final int defaultY;
        public final boolean defaultExpanded;

        Category(String id, String name, String icon, int defaultX, int defaultY, boolean defaultExpanded) {
            this.id = id;
            this.name = name;
            this.icon = icon;
            this.defaultX = defaultX;
            this.defaultY = defaultY;
            this.defaultExpanded = defaultExpanded;
        }
    }

    private static long lastRenderNanos = 0L;
    private static float frameDt = 0.016f;
    private static String lastSubModule = null;

    private static final List<ModuleEntry> MODULES = new ArrayList<>();
    private static boolean initialized = false;

    public static List<ModuleEntry> getAllModules() {
        ensureModulesInitialized();
        return MODULES;
    }

    // Shared interactive state (works both in ClickGuiScreen and on Meteor's Right-Shift screen)
    private static Category draggingCategory = null;
    private static boolean isDraggingMacroWindow = false;
    private static boolean isDraggingKeybindsHud = false;
    private static int dragKeybindsOffsetX = 0;
    private static int dragKeybindsOffsetY = 0;
    private static int dragOffsetX = 0;
    private static int dragOffsetY = 0;
    private static int dragStartX = 0;
    private static int dragStartY = 0;
    private static boolean hasDragged = false;

    private static String activeSettingsModule = null;
    private static boolean isDraggingSlider = false;
    private static String draggingSliderKey = null;

    // Macro editor state
    private static int editingMacroIndex = -1;
    private static int editingField = 0; // 0 = none, 1 = name, 2 = message
    private static boolean listeningMacroBind = false;
    public static String listeningModuleBind = null;

    public static boolean isTargetScreen(Screen screen) {
        if (screen == null || PanicHandler.isPanicActive()) {
            return false;
        }
        String className = screen.getClass().getName();
        return className.equals("meteordevelopment.meteorclient.gui.screens.ModulesScreen");
    }

    private static void ensureModulesInitialized() {
        if (initialized) {
            return;
        }
        initialized = true;
        MODULES.clear();
        ModConfig config = ModConfig.getInstance();

        // 1. Viewmodel (Render)
        MODULES.add(new ModuleEntry(
                "Viewmodel", Category.RENDER,
                () -> config.vmEnabled,
                val -> {
                    config.vmEnabled = val;
                    config.save();
                }
        ));

        // 2. Inventory HUD+ (Render)
        MODULES.add(new ModuleEntry(
                "Inventory HUD+", Category.RENDER,
                () -> InventoryHUDCommon.isActive || InventoryHUDCommon.armorHUD || InventoryHUDCommon.potionHUD,
                val -> {
                    config.inventoryHudEnabled = val;
                    config.save();
                    try {
                        ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                        if (invCfg != null) {
                            invCfg.setByDefault(val);
                            invCfg.setArmorDamage(val);
                            invCfg.setPotions(val);
                            invCfg.save();
                        }
                        InventoryHUDCommon.isActive = val;
                        InventoryHUDCommon.armorHUD = val;
                        InventoryHUDCommon.potionHUD = val;
                    } catch (Throwable ignored) {
                    }
                }
        ));

        // 3. Fullbright (No Block Shadows) (Render)
        MODULES.add(new ModuleEntry(
                "Fullbright", Category.RENDER,
                () -> config.fullbright,
                val -> {
                    config.fullbright = val;
                    config.save();
                    ModConfig.reloadChunks();
                }
        ));

        // 3b. Freecam (Render)
        MODULES.add(new ModuleEntry(
                "Freecam", Category.RENDER,
                () -> config.freecamEnabled,
                val -> {
                    config.freecamEnabled = val;
                    if (!val) {
                        FreecamHandler.disable(Minecraft.getInstance());
                    } else {
                        FreecamHandler.enable(Minecraft.getInstance());
                    }
                    config.save();
                }
        ));

        // 4. HUD Info (FPS/Pos) (Render)
        MODULES.add(new ModuleEntry(
                "HUD Info", Category.RENDER,
                () -> config.hudInfoEnabled,
                val -> {
                    config.hudInfoEnabled = val;
                    config.save();
                }
        ));

        // 5. TNT Timer (Render)
        MODULES.add(new ModuleEntry(
                "TNT Timer", Category.RENDER,
                () -> config.tntTimer,
                val -> {
                    config.tntTimer = val;
                    config.save();
                }
        ));

        // 6. Keybinds HUD (Render)
        MODULES.add(new ModuleEntry(
                "Keybinds HUD", Category.RENDER,
                () -> config.keybindsHudEnabled,
                val -> {
                    config.keybindsHudEnabled = val;
                    config.save();
                }
        ));

        // 6. Fast Place (World)
        MODULES.add(new ModuleEntry(
                "Fast Place", Category.WORLD,
                () -> config.fastPlace,
                val -> {
                    config.fastPlace = val;
                    if (!val) {
                        config.fastPlaceAutoRightClickResume = config.fastPlaceAutoRightClick;
                        config.fastPlaceAutoRightClick = false;
                    } else if (config.fastPlaceAutoRightClickResume) {
                        config.fastPlaceAutoRightClick = true;
                        config.fastPlaceAutoRightClickResume = false;
                    }
                    config.save();
                }
        ));

        // Better Place (World)
        MODULES.add(new ModuleEntry(
                "Better Place", Category.WORLD,
                () -> config.betterPlace,
                val -> {
                    config.betterPlace = val;
                    config.save();
                }
        ));

        // No Strip (Player)
        MODULES.add(new ModuleEntry(
                "No Strip", Category.PLAYER,
                () -> config.noStrip,
                val -> {
                    config.noStrip = val;
                    config.save();
                }
        ));

        // MultiTask (Player)
        MODULES.add(new ModuleEntry(
                "MultiTask", Category.PLAYER,
                () -> config.multiTask,
                val -> {
                    config.multiTask = val;
                    config.save();
                }
        ));

        // 7. Auto Break (World)
        MODULES.add(new ModuleEntry(
                "Auto Break", Category.WORLD,
                () -> config.autoBreakEnabled,
                val -> {
                    config.autoBreakEnabled = val;
                    config.save();
                }
        ));

        // Auto Wheat Farm (World)
        MODULES.add(new ModuleEntry(
                "Auto Wheat Farm", Category.WORLD,
                () -> config.wheatFarmEnabled,
                val -> {
                    config.wheatFarmEnabled = val;
                    config.save();
                }
        ));

        // Auto Fish (World)
        MODULES.add(new ModuleEntry(
                "Auto Fish", Category.WORLD,
                () -> config.autoFishEnabled,
                val -> {
                    config.autoFishEnabled = val;
                    config.save();
                }
        ));

        // 8. Inventory Tweaks (Player)
        MODULES.add(new ModuleEntry(
                "Inventory Tweaks", Category.PLAYER,
                () -> config.invTweaksEnabled,
                val -> {
                    config.invTweaksEnabled = val;
                    config.save();
                }
        ));

        // 9. Auto Replenish (Player)
        MODULES.add(new ModuleEntry(
                "Auto Replenish", Category.PLAYER,
                () -> config.autoReplenishEnabled,
                val -> {
                    config.autoReplenishEnabled = val;
                    config.save();
                }
        ));

        // 10. Fast Use (Player)
        MODULES.add(new ModuleEntry(
                "Fast Use", Category.PLAYER,
                () -> config.fastUseEnabled,
                val -> {
                    config.fastUseEnabled = val;
                    config.save();
                }
        ));

        // 11. Auto Tool (Player)
        MODULES.add(new ModuleEntry(
                "Auto Tool", Category.PLAYER,
                () -> config.autoTool,
                val -> {
                    config.autoTool = val;
                    config.save();
                }
        ));

        // 12. Auto Crafter (Player)
        MODULES.add(new ModuleEntry(
                "Auto Crafter", Category.PLAYER,
                () -> config.autoCrafterEnabled,
                val -> {
                    config.autoCrafterEnabled = val;
                    config.save();
                }
        ));

        // Auto Sell (Player)
        MODULES.add(new ModuleEntry(
                "Auto Sell", Category.PLAYER,
                () -> config.autoSellEnabled,
                val -> {
                    config.autoSellEnabled = val;
                    config.save();
                }
        ));

        // Auto Drop (Player)
        MODULES.add(new ModuleEntry(
                "Auto Drop", Category.PLAYER,
                () -> config.autoDropEnabled,
                val -> {
                    config.autoDropEnabled = val;
                    config.save();
                }
        ));

        // 13. Auto Sprint (Movement)
        MODULES.add(new ModuleEntry(
                "Auto Sprint", Category.MOVEMENT,
                () -> config.autoSprint,
                val -> {
                    config.autoSprint = val;
                    config.save();
                }
        ));

        // 14. Safe Walk (Movement)
        MODULES.add(new ModuleEntry(
                "Safe Walk", Category.MOVEMENT,
                () -> config.safeWalk,
                val -> {
                    config.safeWalk = val;
                    config.save();
                }
        ));

        // Item Sucker (Movement)
        MODULES.add(new ModuleEntry(
                "Item Sucker", Category.MOVEMENT,
                () -> config.itemSuckerEnabled,
                val -> {
                    config.itemSuckerEnabled = val;
                    config.save();
                }
        ));

        // TrueSight (Render)
        MODULES.add(new ModuleEntry(
                "TrueSight", Category.RENDER,
                () -> config.trueSight,
                val -> {
                    config.trueSight = val;
                    config.save();
                }
        ));

        // 17. Item ESP (Render)
        MODULES.add(new ModuleEntry(
                "Item ESP", Category.RENDER,
                () -> config.itemEsp,
                val -> {
                    config.itemEsp = val;
                    config.save();
                }
        ));

        // 18. FastBreak (World)
        MODULES.add(new ModuleEntry(
                "FastBreak", Category.WORLD,
                () -> config.fastBreak,
                val -> {
                    config.fastBreak = val;
                    config.save();
                }
        ));

        // 19. No Pitch Limit (Exploit)
        MODULES.add(new ModuleEntry(
                "No Pitch Limit", Category.EXPLOIT,
                () -> config.noPitchLimit,
                val -> {
                    config.noPitchLimit = val;
                    config.save();
                }
        ));

        // 20. Abort Breaking (Exploit)
        MODULES.add(new ModuleEntry(
                "Abort Breaking", Category.EXPLOIT,
                () -> config.abortBreaking,
                val -> {
                    config.abortBreaking = val;
                    config.save();
                }
        ));

        // 21. Anti Hunger (Exploit)
        MODULES.add(new ModuleEntry(
                "Anti Hunger", Category.EXPLOIT,
                () -> config.antiHunger,
                val -> {
                    config.antiHunger = val;
                    config.save();
                }
        ));

        // 22. Anti Levitation (Exploit)
        MODULES.add(new ModuleEntry(
                "Anti Levitation", Category.EXPLOIT,
                () -> config.antiLevitation,
                val -> {
                    config.antiLevitation = val;
                    config.save();
                }
        ));

        // 23. Backtrack (Combat)
        MODULES.add(new ModuleEntry(
                "Backtrack", Category.COMBAT,
                () -> config.backtrack,
                val -> {
                    config.backtrack = val;
                    config.save();
                }
        ));

        // New Velocity (Combat)
        MODULES.add(new ModuleEntry(
                "New Velocity", Category.COMBAT,
                () -> config.newVelocityEnabled,
                val -> {
                    config.newVelocityEnabled = val;
                    config.save();
                }
        ));

        // 24. Disabler (Exploit)
        MODULES.add(new ModuleEntry(
                "Disabler", Category.EXPLOIT,
                () -> config.disabler,
                val -> {
                    config.disabler = val;
                    config.save();
                }
        ));

        // 25. Exploit Guard (Exploit)
        MODULES.add(new ModuleEntry(
                "Exploit Guard", Category.EXPLOIT,
                () -> config.exploitGuard,
                val -> {
                    config.exploitGuard = val;
                    config.save();
                }
        ));

        // 26. Ghost Hand (Exploit)
        MODULES.add(new ModuleEntry(
                "Ghost Hand", Category.EXPLOIT,
                () -> config.ghostHand,
                val -> {
                    config.ghostHand = val;
                    config.save();
                }
        ));

        // 27. No Mining Trace (Exploit)
        MODULES.add(new ModuleEntry(
                "No Mining Trace", Category.EXPLOIT,
                () -> config.noMiningTrace,
                val -> {
                    config.noMiningTrace = val;
                    config.save();
                }
        ));

        // 28. No Slowdown (Exploit)
        MODULES.add(new ModuleEntry(
                "No Slowdown", Category.EXPLOIT,
                () -> config.noSlowdown,
                val -> {
                    config.noSlowdown = val;
                    config.save();
                }
        ));

        // 29. Ping Spoof (Exploit)
        MODULES.add(new ModuleEntry(
                "Ping Spoof", Category.EXPLOIT,
                () -> config.pingSpoof,
                val -> {
                    config.pingSpoof = val;
                    config.save();
                }
        ));

        // 30. Portal GUI (Exploit)
        MODULES.add(new ModuleEntry(
                "Portal GUI", Category.EXPLOIT,
                () -> config.portalGui,
                val -> {
                    config.portalGui = val;
                    config.save();
                }
        ));

        // 31. Resource Pack Spoof (Exploit)
        MODULES.add(new ModuleEntry(
                "Resource Pack Spoof", Category.EXPLOIT,
                () -> config.resourcePackSpoof,
                val -> {
                    config.resourcePackSpoof = val;
                    config.save();
                }
        ));

        // 32. Packet Mine (Exploit)
        MODULES.add(new ModuleEntry(
                "Packet Mine", Category.EXPLOIT,
                () -> config.packetMine,
                val -> {
                    config.packetMine = val;
                    if (!val) {
                        PacketMineHandler.clear();
                    }
                    config.save();
                }
        ));

        // 33. Chunk Borders (Render)
        MODULES.add(new ModuleEntry(
                "Chunk Borders", Category.RENDER,
                () -> config.chunkBorders,
                val -> {
                    config.chunkBorders = val;
                    config.save();
                }
        ));

        // 34. Light Overlay (Render)
        MODULES.add(new ModuleEntry(
                "Light Overlay", Category.RENDER,
                () -> config.lightOverlay,
                val -> {
                    config.lightOverlay = val;
                    if (!val) {
                        WorldRenderToolsHandler.clearLightCache();
                    }
                    config.save();
                }
        ));

        // 35. Trajectories (Render)
        MODULES.add(new ModuleEntry(
                "Trajectories", Category.RENDER,
                () -> config.trajectories,
                val -> {
                    config.trajectories = val;
                    config.save();
                }
        ));

        // 36. No Pause (Misc)
        MODULES.add(new ModuleEntry(
                "No Pause", Category.MISC,
                () -> config.noPauseOnLostFocus,
                val -> {
                    config.noPauseOnLostFocus = val;
                    config.save();
                }
        ));

        ModernModuleGridLayout.sortByNameInPlace(MODULES, m -> m.name);
    }

    public static List<ModuleEntry> getModulesForCategory(Category cat) {
        List<ModuleEntry> list = new ArrayList<>();
        for (ModuleEntry m : MODULES) {
            if (m.category == cat) {
                list.add(m);
            }
        }
        return list;
    }

    public static ModuleEntry getModuleByName(String name) {
        if (name == null) return null;
        for (ModuleEntry m : MODULES) {
            if (name.equals(m.name)) {
                return m;
            }
        }
        return null;
    }

    public static boolean hasSubSettings(String modName) {
        return modName != null && getModuleByName(modName) != null;
    }

    private static boolean isLeftClick(int button) {
        return MouseButtonMapping.isLeftClick(button);
    }

    private static boolean isRightClick(int button) {
        return MouseButtonMapping.isRightClick(button);
    }

    private static boolean isShiftDown() {
        try {
            Minecraft mc = Minecraft.getInstance();
            return mc != null && mc.hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void playClickSound() {
        try {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        } catch (Throwable ignored) {
        }
    }

    public static void renderTabPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ensureModulesInitialized();
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        ModConfig config = ModConfig.getInstance();

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();

        // 1. Handle dragging of category panels
        if (config.activeTopTab == 0 && draggingCategory != null) {
            if (Math.abs(mouseX - dragStartX) > 2 || Math.abs(mouseY - dragStartY) > 2) {
                hasDragged = true;
            }
            if (hasDragged) {
                int newX = mouseX - dragOffsetX;
                int newY = mouseY - dragOffsetY;
                newX = Mth.clamp(newX, 2, Math.max(2, screenW - PANEL_WIDTH - 2));
                newY = Mth.clamp(newY, 24, Math.max(24, screenH - HEADER_HEIGHT - 2));
                config.setCategoryPos(draggingCategory.id, newX, newY);
            }
        }

        // 2. Handle dragging of macro window
        if (config.activeTopTab == 1 && isDraggingMacroWindow) {
            if (Math.abs(mouseX - dragStartX) > 2 || Math.abs(mouseY - dragStartY) > 2) {
                hasDragged = true;
            }
            if (hasDragged) {
                config.guiWindowX = mouseX - dragOffsetX;
                config.guiWindowY = mouseY - dragOffsetY;
            }
        }

        // 3. Handle slider dragging
        if (config.activeTopTab == 0 && isDraggingSlider && activeSettingsModule != null && draggingSliderKey != null) {
            ModuleEntry mod = getModuleByName(activeSettingsModule);
            Category cat = mod != null ? mod.category : Category.RENDER;
            int catX = config.getCategoryX(cat.id, cat.defaultX);
            catX = Mth.clamp(catX, 2, Math.max(2, screenW - PANEL_WIDTH - 2));
            int subX = getSubPanelXForWidth(catX, PANEL_WIDTH, screenW);
            updateSliderValue(mouseX, subX + 8, SUB_WIDTH - 16, draggingSliderKey);
        }

        // Frame timing + spring state (bounce on every (re)open)
        long now = System.nanoTime();
        if (now - lastRenderNanos > 150_000_000L) {
            JelloTheme.clearSprings("");
            lastSubModule = null;
        }
        frameDt = Mth.clamp((now - lastRenderNanos) / 1.0e9f, 0.001f, 0.05f);
        lastRenderNanos = now;

        float openScale = JelloTheme.spring("open", 1.0f, 0.88f, 220f, 16f, frameDt);
        JelloTheme.setAlpha((openScale - 0.88f) / 0.12f);

        // 0. Top Tab Bar (Modules | Macros)
        renderTopBar(graphics, font, mouseX, mouseY, screenW, config);

        if (config.activeTopTab == 1) {
            int winX = Mth.clamp(config.guiWindowX, 4, Math.max(4, screenW - MACRO_WIDTH - 4));
            int winY = Mth.clamp(config.guiWindowY, 28, Math.max(28, screenH - HEADER_HEIGHT - 4));
            config.guiWindowX = winX;
            config.guiWindowY = winY;
            renderMacrosTab(graphics, font, mouseX, mouseY, winX, winY, screenW, config.guiExpanded, openScale, config);
            JelloTheme.setAlpha(1.0f);
            return;
        }

        // 1. Render all 8 LiquidBounce category panels
        for (Category cat : Category.values()) {
            renderCategoryPanel(graphics, font, mouseX, mouseY, screenW, screenH, cat, openScale, config);
        }

        // 2. Side settings panel (opens adjacent to active module's category panel)
        if (activeSettingsModule != null && hasSubSettings(activeSettingsModule)) {
            ModuleEntry mod = getModuleByName(activeSettingsModule);
            Category cat = mod != null ? mod.category : Category.RENDER;
            int catX = config.getCategoryX(cat.id, cat.defaultX);
            int catY = config.getCategoryY(cat.id, cat.defaultY);
            catX = Mth.clamp(catX, 2, Math.max(2, screenW - PANEL_WIDTH - 2));
            catY = Mth.clamp(catY, 26, Math.max(26, screenH - HEADER_HEIGHT - 2));

            if (!activeSettingsModule.equals(lastSubModule)) {
                JelloTheme.clearSprings("sub:");
                lastSubModule = activeSettingsModule;
            }
            float subScale = JelloTheme.spring("sub:scale", 1.0f, 0.86f, 240f, 16f, frameDt);
            JelloTheme.setAlpha((subScale - 0.86f) / 0.12f);

            int subX = getSubPanelXForWidth(catX, PANEL_WIDTH, screenW);
            float subCenterX = subX + SUB_WIDTH / 2.0f;
            graphics.pose().pushMatrix();
            graphics.pose().translate(subCenterX, (float) catY);
            graphics.pose().scale(subScale, subScale);
            graphics.pose().translate(-subCenterX, (float) -catY);
            renderSubSettings(graphics, font, mouseX, mouseY, catX, catY, screenW);
            graphics.pose().popMatrix();
        } else {
            lastSubModule = null;
        }

        // 3. Render Keybinds HUD table in ClickGUI with dragging support
        if (config.keybindsHudEnabled) {
            int khX = config.keybindsHudX;
            int khY = config.keybindsHudY;
            int khW = KeybindsHudRenderer.calculateWidth(font, KeybindsHudRenderer.getBoundItems());
            boolean khHovered = mouseX >= khX && mouseX <= khX + khW && mouseY >= khY && mouseY <= khY + KeybindsHudRenderer.HEADER_HEIGHT;

            if (isDraggingKeybindsHud) {
                config.keybindsHudX = mouseX - dragKeybindsOffsetX;
                config.keybindsHudY = mouseY - dragKeybindsOffsetY;
                config.keybindsHudX = Mth.clamp(config.keybindsHudX, 2, Math.max(2, screenW - khW - 2));
                config.keybindsHudY = Mth.clamp(config.keybindsHudY, 2, Math.max(2, screenH - KeybindsHudRenderer.HEADER_HEIGHT - 2));
            }

            KeybindsHudRenderer.renderBox(graphics, font, config.keybindsHudX, config.keybindsHudY, mouseX, mouseY, khHovered || isDraggingKeybindsHud, true);
        }

        JelloTheme.setAlpha(1.0f);
    }

    private static void renderCategoryPanel(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int screenW, int screenH, Category cat, float openScale, ModConfig config) {
        int catX = config.getCategoryX(cat.id, cat.defaultX);
        int catY = config.getCategoryY(cat.id, cat.defaultY);
        catX = Mth.clamp(catX, 2, Math.max(2, screenW - PANEL_WIDTH - 2));
        catY = Mth.clamp(catY, 26, Math.max(26, screenH - HEADER_HEIGHT - 2));

        boolean expanded = config.isCategoryExpanded(cat.id, cat.defaultExpanded);
        List<ModuleEntry> mods = getModulesForCategory(cat);
        int rows = mods.isEmpty() ? 1 : mods.size();
        int bodyH = HEADER_HEIGHT + (expanded ? rows * ROW_HEIGHT + 3 : 0);

        float centerX = catX + PANEL_WIDTH / 2.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, (float) catY);
        graphics.pose().scale(openScale, openScale);
        graphics.pose().translate(-centerX, (float) -catY);

        // 1. Subtle drop border / shadow
        JelloTheme.fill(graphics, catX - 1, catY - 1, catX + PANEL_WIDTH + 1, catY + bodyH + 1, 0x44000000);

        // 2. Expanded body background
        if (expanded) {
            JelloTheme.fill(graphics, catX, catY + HEADER_HEIGHT, catX + PANEL_WIDTH, catY + bodyH, JelloTheme.PANEL);
        }

        // 3. Header background
        boolean headerHovered = mouseX >= catX && mouseX <= catX + PANEL_WIDTH && mouseY >= catY && mouseY <= catY + HEADER_HEIGHT;
        int headerBg = headerHovered ? 0xFA140E10 : JelloTheme.HEADER;
        JelloTheme.fill(graphics, catX, catY, catX + PANEL_WIDTH, catY + HEADER_HEIGHT, headerBg);

        // 4. LiquidBounce 2px solid Royal Blue line at bottom of header
        JelloTheme.fill(graphics, catX, catY + HEADER_HEIGHT - 2, catX + PANEL_WIDTH, catY + HEADER_HEIGHT, JelloTheme.ACCENT);

        // 5. Left icon and Category title
        String headerTitle = cat.icon + " " + cat.name;
        JelloTheme.text(graphics, font, headerTitle, catX + 6, catY + 5, 0xFFFFFFFF);

        // 6. Right expand / collapse indicator (+ or -)
        boolean plusHovered = headerHovered && mouseX >= catX + PANEL_WIDTH - 20;
        String ind = expanded ? "-" : "+";
        int indColor = plusHovered ? JelloTheme.ACCENT : 0xFFFFFFFF;
        JelloTheme.textCentered(graphics, font, ind, catX + PANEL_WIDTH - 10, catY + 5, indColor);

        // 7. Render modules if expanded
        if (expanded) {
            if (mods.isEmpty()) {
                JelloTheme.textCentered(graphics, font, "No modules", catX + PANEL_WIDTH / 2, catY + HEADER_HEIGHT + 6, JelloTheme.TEXT_DIM);
            } else {
                int curY = catY + HEADER_HEIGHT + 2;
                for (ModuleEntry mod : mods) {
                    int rowX1 = catX + 2;
                    int rowX2 = catX + PANEL_WIDTH - 2;
                    int rowY1 = curY;
                    int rowY2 = curY + ROW_HEIGHT - 2;

                    boolean hovered = mouseX >= rowX1 && mouseX <= rowX2 && mouseY >= rowY1 && mouseY <= rowY2;
                    boolean active = mod.getter.get();
                    boolean settingsOpen = mod.name.equals(activeSettingsModule);

                    int rowBg;
                    if (active) {
                        rowBg = (hovered || settingsOpen) ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT;
                    } else if (hovered || settingsOpen) {
                        rowBg = 0x2AFFFFFF;
                    } else {
                        rowBg = 0x08FFFFFF;
                    }

                    // LiquidBounce styled rounded button
                    JelloTheme.fillRounded(graphics, rowX1, rowY1, rowX2, rowY2, 3, rowBg);

                    // Module text
                    int textColor = active ? 0xFFFFFFFF : (hovered ? 0xFFFFFFFF : JelloTheme.TEXT_DIM);
                    JelloTheme.text(graphics, font, mod.name, rowX1 + 5, rowY1 + 5, textColor);

                    // Right arrow / chevron indicator '>'
                    int arrowColor = active ? 0xEEFFFFFF : (hovered ? 0xCCFFFFFF : 0x66FFFFFF);
                    JelloTheme.text(graphics, font, ">", rowX2 - 9, rowY1 + 5, arrowColor);

                    // Keybind indicator tag: e.g. [V] or [M3] or red [...] when waiting for press
                    boolean isListening = mod.name.equals(listeningModuleBind);
                    int bindCode = config.getModuleKey(mod.name);
                    boolean isMouse = config.isModuleKeyMouse(mod.name);
                    if (isListening) {
                        String tag = "[...]";
                        int tagW = font.width(tag);
                        long time = System.currentTimeMillis();
                        int pulseColor = (time / 250 % 2 == 0) ? JelloTheme.ACCENT : 0xFFFFFFFF;
                        JelloTheme.text(graphics, font, tag, rowX2 - 13 - tagW, rowY1 + 5, pulseColor);
                    } else if (bindCode >= 0) {
                        String shortName = getShortBindName(bindCode, isMouse);
                        String tag = "[" + shortName + "]";
                        int tagW = font.width(tag);
                        int tagColor = active ? 0xEEFFFFFF : (hovered ? 0xDDFFFFFF : 0x88FFFFFF);
                        JelloTheme.text(graphics, font, tag, rowX2 - 13 - tagW, rowY1 + 5, tagColor);
                    } else if (hovered) {
                        String tag = "[+]";
                        int tagW = font.width(tag);
                        JelloTheme.text(graphics, font, tag, rowX2 - 13 - tagW, rowY1 + 5, 0x66FFFFFF);
                    }

                    curY += ROW_HEIGHT;
                }
            }
        }

        graphics.pose().popMatrix();
    }

    private static void drawSwitch(GuiGraphicsExtractor graphics, int x, int y, float on) {
        int track = JelloTheme.lerpColor(JelloTheme.SWITCH_OFF, JelloTheme.ACCENT, on);
        JelloTheme.fillRounded(graphics, x, y, x + SWITCH_W, y + SWITCH_H, SWITCH_H / 2, track);
        int knobX = x + 1 + Math.round(on * (SWITCH_W - SWITCH_H));
        JelloTheme.fillRounded(graphics, knobX, y + 1, knobX + SWITCH_H - 2, y + SWITCH_H - 1, (SWITCH_H - 2) / 2, 0xFFFFFFFF);
    }

    private static int getSubPanelXForWidth(int winX, int baseW, int screenW) {
        int rightSide = winX + baseW + 4;
        if (rightSide + SUB_WIDTH + 4 <= screenW) {
            return rightSide;
        }
        return Math.max(4, winX - SUB_WIDTH - 4);
    }

    private static void renderTopBar(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int screenW, ModConfig config) {
        int barX = (screenW - TOP_BAR_W) / 2;
        int barY = TOP_BAR_Y;
        JelloTheme.fillRounded(graphics, barX - 1, barY - 1, barX + TOP_BAR_W + 1, barY + TOP_BAR_H + 1, 5, JelloTheme.BORDER);
        JelloTheme.fillRounded(graphics, barX, barY, barX + TOP_BAR_W, barY + TOP_BAR_H, 5, JelloTheme.HEADER);

        int halfW = (TOP_BAR_W - 6) / 2;
        int tab0X = barX + 2;
        int tab1X = barX + 4 + halfW;
        int tabY = barY + 2;
        int tabH = TOP_BAR_H - 4;

        boolean hov0 = mouseX >= tab0X && mouseX <= tab0X + halfW && mouseY >= tabY && mouseY <= tabY + tabH;
        boolean hov1 = mouseX >= tab1X && mouseX <= tab1X + halfW && mouseY >= tabY && mouseY <= tabY + tabH;

        if (config.activeTopTab == 0) {
            JelloTheme.fillRounded(graphics, tab0X, tabY, tab0X + halfW, tabY + tabH, 4, JelloTheme.ACCENT);
        } else if (hov0) {
            JelloTheme.fillRounded(graphics, tab0X, tabY, tab0X + halfW, tabY + tabH, 4, 0x28FFFFFF);
        }

        if (config.activeTopTab == 1) {
            JelloTheme.fillRounded(graphics, tab1X, tabY, tab1X + halfW, tabY + tabH, 4, JelloTheme.ACCENT);
        } else if (hov1) {
            JelloTheme.fillRounded(graphics, tab1X, tabY, tab1X + halfW, tabY + tabH, 4, 0x28FFFFFF);
        }

        JelloTheme.textCentered(graphics, font, "Modules", tab0X + halfW / 2, tabY + 4, config.activeTopTab == 0 ? 0xFFFFFFFF : JelloTheme.TEXT_DIM);
        JelloTheme.textCentered(graphics, font, "Macros", tab1X + halfW / 2, tabY + 4, config.activeTopTab == 1 ? 0xFFFFFFFF : JelloTheme.TEXT_DIM);
    }

    private static void renderMacrosTab(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int winX, int winY, int screenW, boolean expanded, float openScale, ModConfig config) {
        if (config.macros == null) {
            config.macros = new ArrayList<>();
        }
        int macroCount = config.macros.size();
        int bodyH = HEADER_HEIGHT + (expanded ? (macroCount * 22 + 30) : 0);

        float centerX = winX + MACRO_WIDTH / 2.0f;
        graphics.pose().pushMatrix();
        graphics.pose().translate(centerX, (float) winY);
        graphics.pose().scale(openScale, openScale);
        graphics.pose().translate(-centerX, (float) -winY);

        JelloTheme.fillRounded(graphics, winX - 1, winY - 1, winX + MACRO_WIDTH + 1, winY + bodyH + 1, 5, JelloTheme.BORDER);
        JelloTheme.fillRounded(graphics, winX, winY, winX + MACRO_WIDTH, winY + bodyH, 5, JelloTheme.PANEL);
        JelloTheme.fillRounded(graphics, winX, winY, winX + MACRO_WIDTH, winY + HEADER_HEIGHT, 5, JelloTheme.HEADER);
        JelloTheme.textCentered(graphics, font, "Macros", winX + MACRO_WIDTH / 2, winY + 7, 0xFFFFFFFF);
        JelloTheme.text(graphics, font, expanded ? "v" : ">", winX + MACRO_WIDTH - 16, winY + 7, JelloTheme.TEXT_DIM);

        if (expanded) {
            int curY = winY + HEADER_HEIGHT + 2;
            for (int i = 0; i < macroCount; i++) {
                ModConfig.MacroEntry m = config.macros.get(i);
                boolean rowHov = mouseX >= winX + 4 && mouseX <= winX + MACRO_WIDTH - 4 && mouseY >= curY && mouseY < curY + 20;
                if (rowHov || editingMacroIndex == i) {
                    JelloTheme.fillRounded(graphics, winX + 4, curY, winX + MACRO_WIDTH - 4, curY + 20, 4, editingMacroIndex == i ? 0x35E53935 : 0x20FFFFFF);
                }

                String label = m.name + " (" + getBindDisplayName(m.keyCode, m.isMouse) + ")";
                JelloTheme.text(graphics, font, label, winX + 8, curY + 6, 0xFFFFFFFF);

                int editX = winX + MACRO_WIDTH - 38;
                int delX = winX + MACRO_WIDTH - 19;
                int btnY = curY + 2;

                boolean hovEdit = mouseX >= editX && mouseX <= editX + 16 && mouseY >= btnY && mouseY <= btnY + 16;
                boolean hovDel = mouseX >= delX && mouseX <= delX + 16 && mouseY >= btnY && mouseY <= btnY + 16;

                JelloTheme.fillRounded(graphics, editX, btnY, editX + 16, btnY + 16, 3, hovEdit ? 0x66E53935 : 0x30FFFFFF);
                JelloTheme.textCentered(graphics, font, "E", editX + 8, btnY + 4, 0xFFFFFFFF);

                JelloTheme.fillRounded(graphics, delX, btnY, delX + 16, btnY + 16, 3, hovDel ? 0x66FF4444 : 0x30FFFFFF);
                JelloTheme.textCentered(graphics, font, "-", delX + 8, btnY + 4, 0xFFFF5555);

                curY += 22;
            }

            // Separator line
            JelloTheme.fill(graphics, winX + 6, curY + 1, winX + MACRO_WIDTH - 6, curY + 2, 0x45FFFFFF);

            // Create button
            int createY = curY + 5;
            boolean hovCreate = mouseX >= winX + 6 && mouseX <= winX + MACRO_WIDTH - 6 && mouseY >= createY && mouseY <= createY + 19;
            JelloTheme.fillRounded(graphics, winX + 6, createY, winX + MACRO_WIDTH - 6, createY + 19, 4, hovCreate ? 0xCCE53935 : 0x88E53935);
            JelloTheme.textCentered(graphics, font, "Create", winX + MACRO_WIDTH / 2, createY + 5, 0xFFFFFFFF);
        }

        graphics.pose().popMatrix();

        // Side Macro Editor Panel
        if (expanded && editingMacroIndex >= 0 && editingMacroIndex < config.macros.size()) {
            int subX = getSubPanelXForWidth(winX, MACRO_WIDTH, screenW);
            int subY = winY;
            int subH = HEADER_HEIGHT + 4 * 22 + 8;

            JelloTheme.fillRounded(graphics, subX - 1, subY - 1, subX + SUB_WIDTH + 1, subY + subH + 1, 5, JelloTheme.BORDER);
            JelloTheme.fillRounded(graphics, subX, subY, subX + SUB_WIDTH, subY + subH, 5, JelloTheme.PANEL);
            JelloTheme.fillRounded(graphics, subX, subY, subX + SUB_WIDTH, subY + HEADER_HEIGHT, 5, JelloTheme.HEADER);
            JelloTheme.textCentered(graphics, font, "Edit Macro", subX + SUB_WIDTH / 2, subY + 7, 0xFFFFFFFF);

            ModConfig.MacroEntry m = config.macros.get(editingMacroIndex);
            int innerX = subX + 8;
            int innerW = SUB_WIDTH - 16;
            int rowY = subY + HEADER_HEIGHT + 4;
            boolean cursorBlink = (System.currentTimeMillis() / 450L) % 2L == 0L;

            // Row 0: Name Input
            int bgName = editingField == 1 ? 0x55E53935 : 0x28FFFFFF;
            JelloTheme.fillRounded(graphics, innerX, rowY, innerX + innerW, rowY + 19, 4, bgName);
            String nameStr = "Name: " + m.name + (editingField == 1 && cursorBlink ? "|" : "");
            JelloTheme.text(graphics, font, nameStr, innerX + 6, rowY + 5, 0xFFFFFFFF);
            rowY += 22;

            // Row 1: Command / Text Input
            int bgMsg = editingField == 2 ? 0x55E53935 : 0x28FFFFFF;
            JelloTheme.fillRounded(graphics, innerX, rowY, innerX + innerW, rowY + 19, 4, bgMsg);
            String msgStr = "Text: " + m.message + (editingField == 2 && cursorBlink ? "|" : "");
            JelloTheme.text(graphics, font, msgStr, innerX + 6, rowY + 5, 0xFFFFFFFF);
            rowY += 22;

            // Row 2: Key / Mouse Bind
            int bgBind = listeningMacroBind ? 0x77E53935 : 0x28FFFFFF;
            JelloTheme.fillRounded(graphics, innerX, rowY, innerX + innerW, rowY + 19, 4, bgBind);
            String bindVal = listeningMacroBind ? "Press Key/Mouse..." : getBindDisplayName(m.keyCode, m.isMouse);
            JelloTheme.text(graphics, font, "Bind: " + bindVal, innerX + 6, rowY + 5, listeningMacroBind ? JelloTheme.ACCENT : 0xFFFFFFFF);
            rowY += 22;

            // Row 3: Done Button
            drawActionButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW, "Done");
        }
    }

    public static String getBindDisplayName(int keyCode, boolean isMouse) {
        if (keyCode < 0) {
            return "None";
        }
        if (isMouse) {
            return MouseButtonMapping.getDisplayName(keyCode);
        }
        try {
            String s = InputConstants.getKey(new KeyEvent(keyCode, 0, 0)).getDisplayName().getString();
            if (s != null && !s.isEmpty()) {
                return s.toUpperCase(Locale.ROOT);
            }
        } catch (Throwable ignored) {
        }
        return "Key " + keyCode;
    }

    public static String getShortBindName(int keyCode, boolean isMouse) {
        if (keyCode < 0) {
            return "None";
        }
        if (isMouse) {
            return MouseButtonMapping.getShortName(keyCode);
        }
        try {
            String s = InputConstants.getKey(new KeyEvent(keyCode, 0, 0)).getDisplayName().getString();
            if (s != null && !s.isEmpty()) {
                s = s.toUpperCase(Locale.ROOT);
                if (s.length() > 5) {
                    s = s.substring(0, 4) + ".";
                }
                return s;
            }
        } catch (Throwable ignored) {
        }
        return "K" + keyCode;
    }

    private static void renderSubSettings(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int catX, int catY, int screenW) {
        ModConfig config = ModConfig.getInstance();
        int subX = getSubPanelXForWidth(catX, PANEL_WIDTH, screenW);
        int subY = catY;

        int contentRows = getSubSettingsRowCount(activeSettingsModule);
        int subH = HEADER_HEIGHT + contentRows * 22 + 8;
        if ("Auto Crafter".equals(activeSettingsModule)) {
            subH = HEADER_HEIGHT + 4 * 22 + ((AutoCrafterHandler.TARGETS.size() + 1) / 2) * 20 + 8;
        }
        int screenH = graphics.guiHeight();
        if (subY + subH > screenH - 6) {
            subY = Math.max(6, screenH - subH - 6);
        }

        // LiquidBounce styled sub-settings panel
        JelloTheme.fill(graphics, subX - 1, subY - 1, subX + SUB_WIDTH + 1, subY + subH + 1, 0x44000000);
        JelloTheme.fill(graphics, subX, subY + HEADER_HEIGHT, subX + SUB_WIDTH, subY + subH, JelloTheme.PANEL);
        JelloTheme.fill(graphics, subX, subY, subX + SUB_WIDTH, subY + HEADER_HEIGHT, JelloTheme.HEADER);
        JelloTheme.fill(graphics, subX, subY + HEADER_HEIGHT - 2, subX + SUB_WIDTH, subY + HEADER_HEIGHT, JelloTheme.ACCENT);
        JelloTheme.textCentered(graphics, font, activeSettingsModule, subX + SUB_WIDTH / 2, subY + 5, 0xFFFFFFFF);

        int rowY = subY + HEADER_HEIGHT + 4;
        int innerX = subX + 8;
        int innerW = SUB_WIDTH - 16;

        // Row 0: Universal Keybind Row
        int curBind = config.getModuleKey(activeSettingsModule);
        boolean curIsMouse = config.isModuleKeyMouse(activeSettingsModule);
        boolean isListeningThis = activeSettingsModule.equals(listeningModuleBind);
        String bindDisplay = isListeningThis ? "Press Key/Mouse..." : getBindDisplayName(curBind, curIsMouse);
        drawActionButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW, "Bind: " + bindDisplay);
        rowY += 22;

        switch (activeSettingsModule) {
            case "Inventory HUD+" -> {
                boolean inv = InventoryHUDCommon.isActive;
                boolean arm = InventoryHUDCommon.armorHUD;
                boolean pot = InventoryHUDCommon.potionHUD;
                boolean mini = false;
                boolean vert = false;
                int bgAlpha = 60;
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        mini = invCfg.getInvMini();
                        vert = invCfg.getInvVert();
                        bgAlpha = invCfg.getInvAlpha();
                    }
                } catch (Throwable ignored) {
                }
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Inventory HUD: " + (inv ? "ON" : "OFF"), inv);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Armor HUD: " + (arm ? "ON" : "OFF"), arm);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Potions HUD: " + (pot ? "ON" : "OFF"), pot);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Mini Mode: " + (mini ? "ON" : "OFF"), mini);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Vertical Mode: " + (vert ? "ON" : "OFF"), vert);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        String.format(Locale.US, "Inv Scale: %.2fx", config.invHudScale),
                        (config.invHudScale - 0.50f) / 1.50f);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Bg Opacity: " + bgAlpha + "%",
                        bgAlpha / 100.0f);
                rowY += 22;
                drawActionButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW, "Move HUD Positions...");
            }
            case "Fullbright" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Fullbright: " + (config.fullbright ? "ON" : "OFF"), config.fullbright);
            }
            case "Fast Use" -> {
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Cooldown: " + config.fastUseCooldown,
                        config.fastUseCooldown / 4.0f);
            }
            case "Auto Replenish" -> {
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Min Count: " + config.autoReplenishMinCount,
                        (config.autoReplenishMinCount - 1) / 62.0f);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Tick Delay: " + config.autoReplenishTickDelay,
                        config.autoReplenishTickDelay / 10.0f);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Offhand: " + (config.autoReplenishOffhand ? "ON" : "OFF"), config.autoReplenishOffhand);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Unstackable: " + (config.autoReplenishUnstackable ? "ON" : "OFF"), config.autoReplenishUnstackable);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Same Enchants: " + (config.autoReplenishSameEnchants ? "ON" : "OFF"), config.autoReplenishSameEnchants);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Search Hotbar: " + (config.autoReplenishSearchHotbar ? "ON" : "OFF"), config.autoReplenishSearchHotbar);
            }
            case "Inventory Tweaks" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Mouse Drag Move: " + (config.invMouseDrag ? "ON" : "OFF"), config.invMouseDrag);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Auto Sorting: " + (config.invSorting ? "ON" : "OFF"), config.invSorting);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Sorting Delay: " + config.invSortingDelay + " tick" + (config.invSortingDelay > 1 ? "s" : ""),
                        config.invSortingDelay / 10.0f);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Protect Hotbar: " + (config.invProtectHotbar ? "ON" : "OFF"), config.invProtectHotbar);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Xcarry (2x2 Crafting): " + (config.invXcarry ? "ON" : "OFF"), config.invXcarry);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Disable In Creative: " + (config.invDisableInCreative ? "ON" : "OFF"), config.invDisableInCreative);
            }
            case "Fast Place" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Auto Right Click: " + (config.fastPlaceAutoRightClick ? "ON" : "OFF"), config.fastPlaceAutoRightClick);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Delay: " + config.fastPlaceDelay + "t",
                        config.fastPlaceDelay / 4.0f);
            }
            case "Auto Tool" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Swap Back: " + (config.autoToolSwapBack ? "ON" : "OFF"), config.autoToolSwapBack);
            }
            case "Auto Crafter" -> {
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Delay: " + config.autoCrafterDelay + "t" + (config.autoCrafterDelay == 0 ? " (Instant)" : ""),
                        config.autoCrafterDelay / 5.0f);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Close When Done: " + (config.autoCrafterCloseWhenDone ? "ON" : "OFF"), config.autoCrafterCloseWhenDone);
                rowY += 22;
                boolean allSelected = config.autoCrafterTargets != null && config.autoCrafterTargets.size() >= AutoCrafterHandler.TARGETS.size();
                drawActionButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW, allSelected ? "Deselect All" : "Select All");
                rowY += 22;

                int chipGap = 4;
                int chipH = 18;
                int chipW = (innerW - chipGap) / 2;
                List<AutoCrafterHandler.CraftTarget> targets = AutoCrafterHandler.TARGETS;
                for (int i = 0; i < targets.size(); i++) {
                    AutoCrafterHandler.CraftTarget target = targets.get(i);
                    int col = i % 2;
                    int row = i / 2;
                    int curX = innerX + col * (chipW + chipGap);
                    int curY = rowY + row * (chipH + 2);
                    boolean active = config.isAutoCraftTargetEnabled(target.id());
                    drawChipButton(graphics, font, mouseX, mouseY, curX, curY, chipW, chipH, target.label(), active);
                }
            }
            case "Auto Break" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Only With Tool: " + (config.autoBreakOnlyTool ? "ON" : "OFF"), config.autoBreakOnlyTool);
            }
            case "HUD Info" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "HUD Info: " + (config.hudInfoEnabled ? "ON" : "OFF"), config.hudInfoEnabled);
            }
            case "TNT Timer" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "TNT Timer: " + (config.tntTimer ? "ON" : "OFF"), config.tntTimer);
            }
            case "Auto Sprint" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Auto Sprint: " + (config.autoSprint ? "ON" : "OFF"), config.autoSprint);
            }
            case "Safe Walk" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Safe Walk: " + (config.safeWalk ? "ON" : "OFF"), config.safeWalk);
            }
            case "Keybinds HUD" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Keybinds HUD: " + (config.keybindsHudEnabled ? "ON" : "OFF"), config.keybindsHudEnabled);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Only Active: " + (config.keybindsHudOnlyActive ? "ON" : "OFF"), config.keybindsHudOnlyActive);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Auto-Hide: " + (config.keybindsHudAutoHide ? "ON" : "OFF"), config.keybindsHudAutoHide);
                rowY += 22;
                drawActionButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW, "Reset Position");
            }
            case "TrueSight" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "TrueSight: " + (config.trueSight ? "ON" : "OFF"), config.trueSight);
            }
            case "Item ESP" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Item ESP: " + (config.itemEsp ? "ON" : "OFF"), config.itemEsp);
            }
            case "FastBreak" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "FastBreak: " + (config.fastBreak ? "ON" : "OFF"), config.fastBreak);
                rowY += 22;
                String modeName = (config.fastBreakMode == 1) ? "§aVulcan (Bypass)" : "§eVanilla (0 Delay)";
                drawActionButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW, "Mode: " + modeName);
                rowY += 22;
                if (config.fastBreakMode == 1) {
                    float vulcanSpeed = Math.min(config.fastBreakSpeed, 1.15f);
                    drawSlider(graphics, font, innerX, rowY, innerW,
                            String.format(Locale.US, "Vulcan Speed: %.2fx (Safe)", vulcanSpeed),
                            (vulcanSpeed - 1.0f) / 0.15f);
                } else {
                    drawSlider(graphics, font, innerX, rowY, innerW,
                            String.format(Locale.US, "Speed: %.1fx", config.fastBreakSpeed),
                            (config.fastBreakSpeed - 0.5f) / 4.5f);
                }
            }
            case "No Pitch Limit" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "No Pitch Limit: " + (config.noPitchLimit ? "ON" : "OFF"), config.noPitchLimit);
            }
            case "Abort Breaking" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Abort Breaking: " + (config.abortBreaking ? "ON" : "OFF"), config.abortBreaking);
            }
            case "Anti Hunger" -> drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                    "Anti Hunger: " + (config.antiHunger ? "ON" : "OFF"), config.antiHunger);
            case "Anti Levitation" -> drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                    "Anti Levitation: " + (config.antiLevitation ? "ON" : "OFF"), config.antiLevitation);
            case "Backtrack" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Backtrack: " + (config.backtrack ? "ON" : "OFF"), config.backtrack);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Max Delay: " + config.backtrackDelay + "ms",
                        (config.backtrackDelay - 40) / 260.0f);
            }
            case "Disabler" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Disabler: " + (config.disabler ? "ON" : "OFF"), config.disabler);
                rowY += 22;
                String disablerName = config.disablerMode == 1 ? "§bGrim" : "§aVulcan";
                drawActionButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW, "Mode: " + disablerName);
            }
            case "Exploit Guard" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Exploit Guard: " + (config.exploitGuard ? "ON" : "OFF"), config.exploitGuard);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Max Particles: " + config.exploitGuardMaxParticles,
                        (config.exploitGuardMaxParticles - 100) / 1900.0f);
            }
            case "Ghost Hand" -> drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                    "Ghost Hand: " + (config.ghostHand ? "ON" : "OFF"), config.ghostHand);
            case "No Mining Trace" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "No Mining Trace: " + (config.noMiningTrace ? "ON" : "OFF"), config.noMiningTrace);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Tools Only: " + (config.noMiningTraceToolsOnly ? "ON" : "OFF"), config.noMiningTraceToolsOnly);
            }
            case "No Slowdown" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "No Slowdown: " + (config.noSlowdown ? "ON" : "OFF"), config.noSlowdown);
                rowY += 22;
                String slowMode = switch (config.noSlowdownMode) {
                    case 1 -> "§aVulcan";
                    case 2 -> "§bGrim";
                    default -> "§eVanilla";
                };
                drawActionButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW, "Mode: " + slowMode);
            }
            case "Ping Spoof" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Ping Spoof: " + (config.pingSpoof ? "ON" : "OFF"), config.pingSpoof);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Delay: " + config.pingSpoofDelay + "ms",
                        (config.pingSpoofDelay - 50) / 950.0f);
            }
            case "Packet Mine" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Packet Mine: " + (config.packetMine ? "ON" : "OFF"), config.packetMine);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Instant Rebreak: " + (config.packetMineRebreak ? "ON" : "OFF"), config.packetMineRebreak);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Delay: " + config.packetMineDelay + "t",
                        config.packetMineDelay / 5.0f);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Render Box: " + (config.packetMineRender ? "ON" : "OFF"), config.packetMineRender);
            }
            case "Portal GUI" -> drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                    "Portal GUI: " + (config.portalGui ? "ON" : "OFF"), config.portalGui);
            case "Resource Pack Spoof" -> drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                    "Pack Spoof: " + (config.resourcePackSpoof ? "ON" : "OFF"), config.resourcePackSpoof);
            case "Chunk Borders" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Chunk Borders: " + (config.chunkBorders ? "ON" : "OFF"), config.chunkBorders);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Sub-Grid: " + (config.chunkBordersSubGrid ? "ON" : "OFF"), config.chunkBordersSubGrid);
            }
            case "Light Overlay" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Light Overlay: " + (config.lightOverlay ? "ON" : "OFF"), config.lightOverlay);
                rowY += 22;
                drawSlider(graphics, font, innerX, rowY, innerW,
                        "Range: " + config.lightOverlayRange,
                        (config.lightOverlayRange - 8) / 16.0f);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Only Always: " + (config.lightOverlayOnlyAlways ? "ON" : "OFF"), config.lightOverlayOnlyAlways);
            }
            case "Trajectories" -> {
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Trajectories: " + (config.trajectories ? "ON" : "OFF"), config.trajectories);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Show Passive: " + (config.trajectoriesShowPassive ? "ON" : "OFF"), config.trajectoriesShowPassive);
                rowY += 22;
                drawToggleButton(graphics, font, mouseX, mouseY, innerX, rowY, innerW,
                        "Box Outline: " + (config.trajectoriesBoxOutline ? "ON" : "OFF"), config.trajectoriesBoxOutline);
            }
        }
    }

    private static int getSubSettingsRowCount(String modName) {
        int specific = switch (modName) {
            case "Inventory HUD+" -> 8;
            case "Inventory Tweaks" -> 6;
            case "Auto Replenish" -> 6;
            case "Packet Mine" -> 4;
            case "Fullbright", "Fast Use" -> 1;
            case "Fast Place", "Chunk Borders" -> 2;
            case "Auto Tool" -> 1;
            case "Auto Crafter" -> 3 + (AutoCrafterHandler.TARGETS.size() + 1) / 2;
            case "Auto Break" -> 1;
            case "Keybinds HUD" -> 4;
            case "FastBreak", "Light Overlay", "Trajectories" -> 3;
            case "Backtrack", "Disabler", "Exploit Guard", "No Mining Trace", "No Slowdown", "Ping Spoof" -> 2;
            case "HUD Info", "TNT Timer", "Auto Sprint", "Safe Walk", "TrueSight", "Item ESP",
                 "No Pitch Limit", "Abort Breaking", "Anti Hunger", "Anti Levitation",
                 "Ghost Hand", "Portal GUI", "Resource Pack Spoof" -> 1;
            default -> 0;
        };
        return specific + 1;
    }

    private static void drawSlider(GuiGraphicsExtractor graphics, Font font, int x, int y, int w, String label, float pct) {
        pct = Mth.clamp(pct, 0.0f, 1.0f);
        JelloTheme.fillRounded(graphics, x, y + 13, x + w, y + 16, 1, JelloTheme.TRACK);
        int fillW = (int) (w * pct);
        if (fillW > 2) {
            JelloTheme.fillRounded(graphics, x, y + 13, x + fillW, y + 16, 1, JelloTheme.ACCENT);
        }
        JelloTheme.fillRounded(graphics, x + fillW - 3, y + 10, x + fillW + 3, y + 19, 3, 0xFFFFFFFF);
        JelloTheme.text(graphics, font, label, x, y + 1, JelloTheme.TEXT);
    }

    private static void drawToggleButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int x, int y, int w, String label, boolean state) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + 19;
        int sep = label.indexOf(": ");
        String name = sep >= 0 ? label.substring(0, sep) : label;
        String value = sep >= 0 ? label.substring(sep + 2) : "";
        boolean isSwitch = value.equals("ON") || value.equals("OFF");

        float hover = JelloTheme.spring("sub:hov:" + name, hov ? 1.0f : 0.0f, 0.0f, 200f, 28f, frameDt);
        int bg = ((int) (0x20 + 0x20 * Mth.clamp(hover, 0.0f, 1.0f)) << 24) | 0xFFFFFF;
        JelloTheme.fillRounded(graphics, x, y, x + w, y + 19, 4, bg);

        if (isSwitch) {
            float on = JelloTheme.spring("sub:tog:" + name, state ? 1.0f : 0.0f, state ? 1.0f : 0.0f, 300f, 16f, frameDt);
            JelloTheme.text(graphics, font, name, x + 7, y + 5, JelloTheme.lerpColor(JelloTheme.TEXT_DIM, JelloTheme.TEXT, on));
            drawSwitch(graphics, x + w - 7 - SWITCH_W, y + 4, on);
        } else {
            JelloTheme.text(graphics, font, name, x + 7, y + 5, JelloTheme.TEXT);
            if (!value.isEmpty()) {
                JelloTheme.text(graphics, font, value, x + w - 7 - font.width(value), y + 5, JelloTheme.ACCENT);
            }
        }
    }

    private static void drawActionButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int x, int y, int w, String label) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + 19;
        int bg = hov ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT;
        JelloTheme.fillRounded(graphics, x, y, x + w, y + 19, 4, bg);
        JelloTheme.textCentered(graphics, font, label, x + w / 2, y + 5, 0xFFFFFFFF);
    }

    private static void drawChipButton(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int x, int y, int w, int h, String label, boolean active) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + h;
        int bg = active ? (hov ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT) : (hov ? 0x40FFFFFF : 0x22FFFFFF);
        int textColor = active ? 0xFFFFFFFF : (hov ? 0xFFFFFFFF : 0xAAFFFFFF);
        JelloTheme.fillRounded(graphics, x, y, x + w, y + h, 3, bg);
        JelloTheme.textCentered(graphics, font, label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    public static boolean handleMouseButton(double rawMouseX, double rawMouseY, int button, int action) {
        ensureModulesInitialized();
        int mouseX = (int) rawMouseX;
        int mouseY = (int) rawMouseY;
        ModConfig config = ModConfig.getInstance();

        if (action == 0) { // Mouse Release
            boolean wasActive = draggingCategory != null || isDraggingMacroWindow || isDraggingSlider || isDraggingKeybindsHud;
            if (isDraggingKeybindsHud) {
                isDraggingKeybindsHud = false;
            }
            if (draggingCategory != null) {
                if (!hasDragged) {
                    config.toggleCategoryExpanded(draggingCategory.id, draggingCategory.defaultExpanded);
                    playClickSound();
                }
                draggingCategory = null;
            }
            if (isDraggingMacroWindow) {
                if (!hasDragged) {
                    config.guiExpanded = !config.guiExpanded;
                    playClickSound();
                }
                isDraggingMacroWindow = false;
            }
            if (isDraggingSlider) {
                isDraggingSlider = false;
                draggingSliderKey = null;
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        invCfg.save();
                    }
                } catch (Throwable ignored) {
                }
            }
            hasDragged = false;
            if (wasActive) {
                ModConfig.getInstance().save();
            }
            return false;
        }

        if (action != 1) { // Only handle initial press
            return false;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        // If waiting for a module keybind, bind any extra mouse button pressed (or cancel on left/right click)
        if (listeningModuleBind != null) {
            if (MouseButtonMapping.isExtraMouseButton(button)) {
                config.setModuleKey(listeningModuleBind, button, true);
                listeningModuleBind = null;
                playClickSound();
                return true;
            } else {
                listeningModuleBind = null;
                playClickSound();
                return true;
            }
        }

        // If waiting for a macro bind, bind any mouse button pressed!
        if (config.activeTopTab == 1 && listeningMacroBind && editingMacroIndex >= 0 && editingMacroIndex < config.macros.size()) {
            ModConfig.MacroEntry m = config.macros.get(editingMacroIndex);
            m.keyCode = button;
            m.isMouse = true;
            listeningMacroBind = false;
            config.save();
            playClickSound();
            return true;
        }

        // 0. Top Tab Bar Click (Modules | Macros)
        int barX = (screenW - TOP_BAR_W) / 2;
        int barY = TOP_BAR_Y;
        if (mouseX >= barX && mouseX <= barX + TOP_BAR_W && mouseY >= barY && mouseY <= barY + TOP_BAR_H) {
            int halfW = (TOP_BAR_W - 6) / 2;
            if (mouseX <= barX + 2 + halfW) {
                config.activeTopTab = 0;
            } else {
                config.activeTopTab = 1;
            }
            editingField = 0;
            listeningMacroBind = false;
            listeningModuleBind = null;
            config.save();
            playClickSound();
            return true;
        }

        // 1. Macros Tab Handling
        if (config.activeTopTab == 1) {
            int winX = config.guiWindowX;
            int winY = config.guiWindowY;
            if (mouseX >= winX && mouseX <= winX + MACRO_WIDTH && mouseY >= winY && mouseY <= winY + HEADER_HEIGHT) {
                if (isRightClick(button)) {
                    config.guiExpanded = !config.guiExpanded;
                    config.save();
                    playClickSound();
                    return true;
                }
                if (isLeftClick(button)) {
                    isDraggingMacroWindow = true;
                    dragOffsetX = mouseX - winX;
                    dragOffsetY = mouseY - winY;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    hasDragged = false;
                    return true;
                }
            }
            if (config.guiExpanded) {
                return handleMacrosMouseClick(mouseX, mouseY, winX, winY, screenW, button, config);
            }
            return false;
        }

        // 2. Modules Tab Handling (config.activeTopTab == 0)

        // Sub-settings Panel Click (Check first so sub-panel clicks take priority over background category panels)
        if (activeSettingsModule != null && hasSubSettings(activeSettingsModule)) {
            ModuleEntry activeMod = getModuleByName(activeSettingsModule);
            Category parentCat = activeMod != null ? activeMod.category : Category.RENDER;
            int catX = config.getCategoryX(parentCat.id, parentCat.defaultX);
            int catY = config.getCategoryY(parentCat.id, parentCat.defaultY);
            catX = Mth.clamp(catX, 2, Math.max(2, screenW - PANEL_WIDTH - 2));
            catY = Mth.clamp(catY, 26, Math.max(26, screenH - HEADER_HEIGHT - 2));

            int subX = getSubPanelXForWidth(catX, PANEL_WIDTH, screenW);
            int subY = catY;
            int subH = HEADER_HEIGHT + getSubSettingsRowCount(activeSettingsModule) * 22 + 8;
            if ("Auto Crafter".equals(activeSettingsModule)) {
                subH = HEADER_HEIGHT + 3 * 22 + ((AutoCrafterHandler.TARGETS.size() + 1) / 2) * 20 + 8;
            }
            if (subY + subH > screenH - 6) {
                subY = Math.max(6, screenH - subH - 6);
            }
            if (mouseX >= subX && mouseX <= subX + SUB_WIDTH && mouseY >= subY && mouseY <= subY + subH) {
                handleSubSettingsClick(mouseX, mouseY, subX, subY, button);
                return true;
            }
        }

        // Keybinds HUD Header Interaction (Close, Collapse, Drag)
        if (config.keybindsHudEnabled && isLeftClick(button)) {
            int khX = config.keybindsHudX;
            int khY = config.keybindsHudY;
            int khW = KeybindsHudRenderer.calculateWidth(mc.font, KeybindsHudRenderer.getBoundItems());
            if (mouseX >= khX && mouseX <= khX + khW && mouseY >= khY && mouseY <= khY + KeybindsHudRenderer.HEADER_HEIGHT) {
                isDraggingKeybindsHud = true;
                dragKeybindsOffsetX = mouseX - khX;
                dragKeybindsOffsetY = mouseY - khY;
                playClickSound();
                return true;
            }
        }

        // Category Headers and Module Rows Click (Iterate backwards so top-most visually rendered gets click first)
        Category[] allCats = Category.values();
        for (int i = allCats.length - 1; i >= 0; i--) {
            Category cat = allCats[i];
            int catX = config.getCategoryX(cat.id, cat.defaultX);
            int catY = config.getCategoryY(cat.id, cat.defaultY);
            catX = Mth.clamp(catX, 2, Math.max(2, screenW - PANEL_WIDTH - 2));
            catY = Mth.clamp(catY, 26, Math.max(26, screenH - HEADER_HEIGHT - 2));
            boolean expanded = config.isCategoryExpanded(cat.id, cat.defaultExpanded);

            // Check Header
            if (mouseX >= catX && mouseX <= catX + PANEL_WIDTH && mouseY >= catY && mouseY <= catY + HEADER_HEIGHT) {
                if (isRightClick(button)) {
                    config.toggleCategoryExpanded(cat.id, cat.defaultExpanded);
                    config.save();
                    playClickSound();
                    return true;
                }
                if (isLeftClick(button)) {
                    draggingCategory = cat;
                    dragOffsetX = mouseX - catX;
                    dragOffsetY = mouseY - catY;
                    dragStartX = mouseX;
                    dragStartY = mouseY;
                    hasDragged = false;
                    return true;
                }
            }

            // Check Module Rows if expanded
            if (expanded) {
                List<ModuleEntry> mods = getModulesForCategory(cat);
                int curY = catY + HEADER_HEIGHT + 2;
                for (ModuleEntry mod : mods) {
                    int rowX1 = catX + 2;
                    int rowX2 = catX + PANEL_WIDTH - 2;
                    int rowY1 = curY;
                    int rowY2 = curY + ROW_HEIGHT - 2;

                    if (mouseX >= rowX1 && mouseX <= rowX2 && mouseY >= rowY1 && mouseY <= rowY2) {
                        boolean onRightTag = mouseX >= rowX2 - 30;
                        if (onRightTag) {
                            if (isRightClick(button)) {
                                // Right clicking the tag clears the bind
                                config.setModuleKey(mod.name, -1, false);
                                listeningModuleBind = null;
                                playClickSound();
                                return true;
                            } else {
                                // Left clicking the tag enters bind listening mode
                                if (mod.name.equals(listeningModuleBind)) {
                                    listeningModuleBind = null;
                                } else {
                                    listeningModuleBind = mod.name;
                                }
                                playClickSound();
                                return true;
                            }
                        }
                        if (button == InputConstants.MOUSE_BUTTON_MIDDLE || (isLeftClick(button) && isShiftDown())) {
                            if (mod.name.equals(listeningModuleBind)) {
                                listeningModuleBind = null;
                            } else {
                                listeningModuleBind = mod.name;
                            }
                            playClickSound();
                            return true;
                        }
                        if (isLeftClick(button)) {
                            boolean next = !mod.getter.get();
                            mod.setter.accept(next);
                            playClickSound();
                            return true;
                        } else if (isRightClick(button)) {
                            if (mod.name.equals(activeSettingsModule)) {
                                activeSettingsModule = null;
                            } else {
                                activeSettingsModule = mod.name;
                            }
                            playClickSound();
                            return true;
                        }
                    }
                    curY += ROW_HEIGHT;
                }
            }
        }

        return false;
    }

    private static boolean handleMacrosMouseClick(int mouseX, int mouseY, int winX, int winY, int screenW, int button, ModConfig config) {
        if (config.macros == null) {
            config.macros = new ArrayList<>();
        }

        // 1. Check Side Macro Editor Panel first
        if (editingMacroIndex >= 0 && editingMacroIndex < config.macros.size()) {
            int subX = getSubPanelXForWidth(winX, MACRO_WIDTH, screenW);
            int subY = winY;
            int subH = HEADER_HEIGHT + 4 * 22 + 8;
            if (mouseX >= subX && mouseX <= subX + SUB_WIDTH && mouseY >= subY && mouseY <= subY + subH) {
                int rowStartY = subY + HEADER_HEIGHT + 4;
                int rowIndex = (mouseY - rowStartY) / 22;
                ModConfig.MacroEntry m = config.macros.get(editingMacroIndex);
                switch (rowIndex) {
                    case 0 -> {
                        if (isRightClick(button)) {
                            m.name = "";
                        }
                        editingField = 1;
                        listeningMacroBind = false;
                        playClickSound();
                    }
                    case 1 -> {
                        if (isRightClick(button)) {
                            m.message = "";
                        }
                        editingField = 2;
                        listeningMacroBind = false;
                        playClickSound();
                    }
                    case 2 -> {
                        editingField = 0;
                        if (isRightClick(button)) {
                            m.keyCode = -1;
                            m.isMouse = false;
                            listeningMacroBind = false;
                            config.save();
                        } else {
                            listeningMacroBind = !listeningMacroBind;
                        }
                        playClickSound();
                    }
                    case 3 -> {
                        editingField = 0;
                        listeningMacroBind = false;
                        editingMacroIndex = -1;
                        config.save();
                        playClickSound();
                    }
                }
                return true;
            }
        }

        // 2. Check Macros Main Panel
        int macroCount = config.macros.size();
        int bodyH = HEADER_HEIGHT + macroCount * 22 + 30;
        if (mouseX >= winX && mouseX <= winX + MACRO_WIDTH && mouseY >= winY + HEADER_HEIGHT && mouseY <= winY + bodyH) {
            int curY = winY + HEADER_HEIGHT + 2;
            for (int i = 0; i < macroCount; i++) {
                if (mouseY >= curY && mouseY < curY + 20) {
                    int editX = winX + MACRO_WIDTH - 38;
                    int delX = winX + MACRO_WIDTH - 19;
                    int btnY = curY + 2;

                    if (mouseX >= delX && mouseX <= delX + 16 && mouseY >= btnY && mouseY <= btnY + 16) {
                        config.macros.remove(i);
                        if (editingMacroIndex == i) {
                            editingMacroIndex = -1;
                            editingField = 0;
                            listeningMacroBind = false;
                        } else if (editingMacroIndex > i) {
                            editingMacroIndex--;
                        }
                        config.save();
                        playClickSound();
                        return true;
                    }
                    // Clicking Edit button or anywhere on the row opens editor for this macro
                    editingMacroIndex = i;
                    editingField = 2; // Focus Command/Text field immediately for convenience
                    listeningMacroBind = false;
                    playClickSound();
                    return true;
                }
                curY += 22;
            }

            // Check Create button
            int createY = curY + 5;
            if (mouseX >= winX + 6 && mouseX <= winX + MACRO_WIDTH - 6 && mouseY >= createY && mouseY <= createY + 19) {
                ModConfig.MacroEntry created = new ModConfig.MacroEntry("craft", "/craft", -1, false);
                config.macros.add(created);
                editingMacroIndex = config.macros.size() - 1;
                editingField = 1;
                listeningMacroBind = false;
                config.save();
                playClickSound();
                return true;
            }
            return true;
        }

        editingField = 0;
        listeningMacroBind = false;
        return false;
    }

    public static boolean handleKeyPress(int action, KeyEvent event) {
        ModConfig config = ModConfig.getInstance();
        int key = event.key();

        if (listeningModuleBind != null) {
            if (action == InputConstants.PRESS) {
                if (key == InputConstants.KEY_ESCAPE) {
                    listeningModuleBind = null;
                } else if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                    config.setModuleKey(listeningModuleBind, -1, false);
                    listeningModuleBind = null;
                } else if (key != InputConstants.KEY_RSHIFT) {
                    config.setModuleKey(listeningModuleBind, key, false);
                    listeningModuleBind = null;
                }
                playClickSound();
            }
            return true;
        }

        if (config.activeTopTab == 1 && listeningMacroBind && editingMacroIndex >= 0 && editingMacroIndex < config.macros.size()) {
            if (action == InputConstants.PRESS) {
                ModConfig.MacroEntry m = config.macros.get(editingMacroIndex);
                if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
                    m.keyCode = -1;
                    m.isMouse = false;
                } else if (key != InputConstants.KEY_RSHIFT) {
                    m.keyCode = key;
                    m.isMouse = false;
                }
                listeningMacroBind = false;
                config.save();
                playClickSound();
            }
            return true;
        }

        if (config.activeTopTab == 1 && editingField != 0 && editingMacroIndex >= 0 && editingMacroIndex < config.macros.size()) {
            if (action == InputConstants.PRESS || action == InputConstants.REPEAT) {
                ModConfig.MacroEntry m = config.macros.get(editingMacroIndex);
                if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
                    editingField = 0;
                    config.save();
                    return true;
                }
                if (key == InputConstants.KEY_TAB) {
                    editingField = (editingField == 1) ? 2 : 1;
                    config.save();
                    return true;
                }
                if (key == InputConstants.KEY_BACKSPACE) {
                    if (editingField == 1 && !m.name.isEmpty()) {
                        m.name = m.name.substring(0, m.name.length() - 1);
                        config.save();
                    } else if (editingField == 2 && !m.message.isEmpty()) {
                        m.message = m.message.substring(0, m.message.length() - 1);
                        config.save();
                    }
                    return true;
                }
                if (key == InputConstants.KEY_V && (event.modifiers() & InputConstants.MOD_CONTROL) != 0) {
                    try {
                        String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                        if (clip != null && !clip.isEmpty()) {
                            clip = clip.replace("\r", "").replace("\n", "");
                            if (editingField == 1) {
                                m.name += clip;
                            } else if (editingField == 2) {
                                m.message += clip;
                            }
                            config.save();
                        }
                    } catch (Throwable ignored) {
                    }
                    return true;
                }
            }
            return true;
        }

        return false;
    }

    public static boolean handleCharTyped(CharacterEvent event) {
        ModConfig config = ModConfig.getInstance();
        if (config.activeTopTab == 1 && editingField != 0 && editingMacroIndex >= 0 && editingMacroIndex < config.macros.size()) {
            String ch = event.codepointAsString();
            if (ch != null && !ch.isEmpty() && event.codepoint() >= 32 && event.codepoint() != 127) {
                ModConfig.MacroEntry m = config.macros.get(editingMacroIndex);
                if (editingField == 1 && m.name.length() < 24) {
                    m.name += ch;
                    config.save();
                } else if (editingField == 2 && m.message.length() < 200) {
                    m.message += ch;
                    config.save();
                }
            }
            return true;
        }
        return false;
    }

    public static void handleInGameMacroKey(int keyCode) {
        if (keyCode < 0) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (config.macros == null) {
            return;
        }
        for (ModConfig.MacroEntry m : config.macros) {
            if (m != null && !m.isMouse && m.keyCode == keyCode) {
                executeMacro(m);
            }
        }
    }

    public static void handleInGameMacroMouse(int button) {
        if (button < 0) {
            return;
        }
        ModConfig config = ModConfig.getInstance();
        if (config.macros == null) {
            return;
        }
        for (ModConfig.MacroEntry m : config.macros) {
            if (m != null && m.isMouse && m.keyCode == button) {
                executeMacro(m);
            }
        }
    }

    public static void handleInGameModuleKey(int keyCode) {
        if (keyCode < 0) {
            return;
        }
        ensureModulesInitialized();
        ModConfig config = ModConfig.getInstance();
        if (config.moduleKeybinds == null || config.moduleKeybinds.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<String, Integer> entry : config.moduleKeybinds.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == keyCode && !config.isModuleKeyMouse(entry.getKey())) {
                toggleModuleInGame(entry.getKey());
            }
        }
    }

    public static void handleInGameModuleMouse(int button) {
        if (!MouseButtonMapping.isExtraMouseButton(button)) {
            return; // Ignore LMB (1) and RMB (3) in-game to avoid breaking attack / block interaction
        }
        ensureModulesInitialized();
        ModConfig config = ModConfig.getInstance();
        if (config.moduleKeybinds == null || config.moduleKeybinds.isEmpty()) {
            return;
        }
        for (java.util.Map.Entry<String, Integer> entry : config.moduleKeybinds.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == button && config.isModuleKeyMouse(entry.getKey())) {
                toggleModuleInGame(entry.getKey());
            }
        }
    }

    private static void toggleModuleInGame(String moduleName) {
        ModuleEntry mod = getModuleByName(moduleName);
        if (mod == null) {
            return;
        }
        boolean current = mod.getter.get();
        boolean next = !current;
        mod.setter.accept(next);

        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, next ? 1.0f : 0.8f));
            }
        } catch (Throwable ignored) {
        }
    }

    private static void executeMacro(ModConfig.MacroEntry macro) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null || mc.player == null || mc.player.connection == null || macro.message == null) {
                return;
            }
            String raw = macro.message.trim();
            if (raw.isEmpty()) {
                return;
            }
            if (raw.startsWith("/")) {
                String cmd = raw.substring(1).trim();
                if (!cmd.isEmpty()) {
                    mc.player.connection.sendCommand(cmd);
                }
            } else {
                mc.player.connection.sendChat(raw);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void handleSubSettingsClick(int mouseX, int mouseY, int subX, int subY, int button) {
        ModConfig config = ModConfig.getInstance();
        int rowStartY = subY + HEADER_HEIGHT + 4;
        int rowIndex = (mouseY - rowStartY) / 22;
        if (rowIndex < 0) {
            return;
        }
        int innerX = subX + 8;
        int innerW = SUB_WIDTH - 16;

        // Row 0 is the universal Keybind button
        if (rowIndex == 0) {
            if (isRightClick(button)) {
                config.setModuleKey(activeSettingsModule, -1, false);
                listeningModuleBind = null;
                playClickSound();
            } else if (isLeftClick(button)) {
                if (activeSettingsModule.equals(listeningModuleBind)) {
                    listeningModuleBind = null;
                } else {
                    listeningModuleBind = activeSettingsModule;
                }
                playClickSound();
            }
            return;
        }

        if (!isLeftClick(button)) {
            return;
        }
        int specificRow = rowIndex - 1;

        switch (activeSettingsModule) {
            case "Inventory HUD+" -> {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        switch (specificRow) {
                            case 0 -> {
                                boolean next = !InventoryHUDCommon.isActive;
                                invCfg.setByDefault(next);
                                invCfg.save();
                                InventoryHUDCommon.isActive = next;
                                playClickSound();
                            }
                            case 1 -> {
                                boolean next = !InventoryHUDCommon.armorHUD;
                                invCfg.setArmorDamage(next);
                                invCfg.save();
                                InventoryHUDCommon.armorHUD = next;
                                playClickSound();
                            }
                            case 2 -> {
                                boolean next = !InventoryHUDCommon.potionHUD;
                                invCfg.setPotions(next);
                                invCfg.save();
                                InventoryHUDCommon.potionHUD = next;
                                playClickSound();
                            }
                            case 3 -> {
                                boolean next = !invCfg.getInvMini();
                                invCfg.setInvMini(next);
                                invCfg.save();
                                InventoryGui.invMini = next;
                                playClickSound();
                            }
                            case 4 -> {
                                boolean next = !invCfg.getInvVert();
                                invCfg.setInvVert(next);
                                invCfg.save();
                                InventoryGui.invVert = next;
                                playClickSound();
                            }
                            case 5 -> startSliderDrag(mouseX, innerX, innerW, "invHudScale");
                            case 6 -> startSliderDrag(mouseX, innerX, innerW, "invBgAlpha");
                            case 7 -> {
                                playClickSound();
                                Minecraft.getInstance().gui.setScreen(new InGameConfigScreen(-1, true));
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            case "Fullbright" -> {
                if (specificRow == 0) {
                    config.fullbright = !config.fullbright;
                    config.save();
                    playClickSound();
                }
            }
            case "Fast Use" -> {
                if (specificRow == 0) startSliderDrag(mouseX, innerX, innerW, "fastUseCooldown");
            }
            case "Auto Replenish" -> {
                switch (specificRow) {
                    case 0 -> startSliderDrag(mouseX, innerX, innerW, "autoReplenishMinCount");
                    case 1 -> startSliderDrag(mouseX, innerX, innerW, "autoReplenishTickDelay");
                    case 2 -> {
                        config.autoReplenishOffhand = !config.autoReplenishOffhand;
                        config.save();
                        playClickSound();
                    }
                    case 3 -> {
                        config.autoReplenishUnstackable = !config.autoReplenishUnstackable;
                        config.save();
                        playClickSound();
                    }
                    case 4 -> {
                        config.autoReplenishSameEnchants = !config.autoReplenishSameEnchants;
                        config.save();
                        playClickSound();
                    }
                    case 5 -> {
                        config.autoReplenishSearchHotbar = !config.autoReplenishSearchHotbar;
                        config.save();
                        playClickSound();
                    }
                }
            }
            case "Inventory Tweaks" -> {
                switch (specificRow) {
                    case 0 -> {
                        config.invMouseDrag = !config.invMouseDrag;
                        config.save();
                        playClickSound();
                    }
                    case 1 -> {
                        config.invSorting = !config.invSorting;
                        config.save();
                        playClickSound();
                    }
                    case 2 -> startSliderDrag(mouseX, innerX, innerW, "invSortingDelay");
                    case 3 -> {
                        config.invProtectHotbar = !config.invProtectHotbar;
                        config.save();
                        playClickSound();
                    }
                    case 4 -> {
                        config.invXcarry = !config.invXcarry;
                        config.save();
                        playClickSound();
                    }
                    case 5 -> {
                        config.invDisableInCreative = !config.invDisableInCreative;
                        config.save();
                        playClickSound();
                    }
                }
            }
            case "Fast Place" -> {
                switch (specificRow) {
                    case 0 -> {
                        config.fastPlaceAutoRightClick = !config.fastPlaceAutoRightClick;
                        config.save();
                        playClickSound();
                    }
                    case 1 -> startSliderDrag(mouseX, innerX, innerW, "fastPlaceDelay");
                }
            }
            case "Auto Tool" -> {
                if (specificRow == 0) {
                    config.autoToolSwapBack = !config.autoToolSwapBack;
                    config.save();
                    playClickSound();
                }
            }
            case "Auto Crafter" -> {
                int contentY = rowStartY + 22;
                if (mouseY < contentY + 22) {
                    startSliderDrag(mouseX, innerX, innerW, "autoCrafterDelay");
                } else if (mouseY < contentY + 44) {
                    config.autoCrafterCloseWhenDone = !config.autoCrafterCloseWhenDone;
                    config.save();
                    playClickSound();
                } else if (mouseY < contentY + 66) {
                    boolean allSelected = config.autoCrafterTargets != null && config.autoCrafterTargets.size() >= AutoCrafterHandler.TARGETS.size();
                    config.setAllAutoCraftTargets(!allSelected);
                    config.save();
                    playClickSound();
                } else {
                    int gridY = mouseY - (contentY + 66);
                    int chipH = 18;
                    int chipRow = gridY / (chipH + 2);
                    int chipGap = 4;
                    int chipW = (innerW - chipGap) / 2;
                    int relX = mouseX - innerX;
                    int col = -1;
                    if (relX >= 0 && relX <= chipW) {
                        col = 0;
                    } else if (relX >= chipW + chipGap && relX <= 2 * chipW + chipGap) {
                        col = 1;
                    }
                    if (col != -1 && chipRow >= 0) {
                        int index = chipRow * 2 + col;
                        if (index < AutoCrafterHandler.TARGETS.size()) {
                            AutoCrafterHandler.CraftTarget target = AutoCrafterHandler.TARGETS.get(index);
                            config.toggleAutoCraftTarget(target.id());
                            config.save();
                            playClickSound();
                        }
                    }
                }
            }
            case "Auto Break" -> {
                if (specificRow == 0) {
                    config.autoBreakOnlyTool = !config.autoBreakOnlyTool;
                    config.save();
                    playClickSound();
                }
            }
            case "HUD Info" -> {
                if (specificRow == 0) {
                    config.hudInfoEnabled = !config.hudInfoEnabled;
                    config.save();
                    playClickSound();
                }
            }
            case "TNT Timer" -> {
                if (specificRow == 0) {
                    config.tntTimer = !config.tntTimer;
                    config.save();
                    playClickSound();
                }
            }
            case "Auto Sprint" -> {
                if (specificRow == 0) {
                    config.autoSprint = !config.autoSprint;
                    config.save();
                    playClickSound();
                }
            }
            case "Safe Walk" -> {
                if (specificRow == 0) {
                    config.safeWalk = !config.safeWalk;
                    config.save();
                    playClickSound();
                }
            }
            case "Keybinds HUD" -> {
                if (specificRow == 0) {
                    config.keybindsHudEnabled = !config.keybindsHudEnabled;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    config.keybindsHudOnlyActive = !config.keybindsHudOnlyActive;
                    config.save();
                    playClickSound();
                } else if (specificRow == 2) {
                    config.keybindsHudAutoHide = !config.keybindsHudAutoHide;
                    config.save();
                    playClickSound();
                } else if (specificRow == 3) {
                    config.keybindsHudX = 8;
                    config.keybindsHudY = 60;
                    config.save();
                    playClickSound();
                }
            }
            case "TrueSight" -> {
                if (specificRow == 0) {
                    config.trueSight = !config.trueSight;
                    config.save();
                    playClickSound();
                }
            }
            case "Item ESP" -> {
                if (specificRow == 0) {
                    config.itemEsp = !config.itemEsp;
                    config.save();
                    playClickSound();
                }
            }
            case "FastBreak" -> {
                if (specificRow == 0) {
                    config.fastBreak = !config.fastBreak;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    config.fastBreakMode = (config.fastBreakMode == 1) ? 0 : 1;
                    if (config.fastBreakMode == 1 && config.fastBreakSpeed > 1.15f) {
                        config.fastBreakSpeed = 1.15f;
                    }
                    config.save();
                    playClickSound();
                } else if (specificRow == 2) {
                    startSliderDrag(mouseX, innerX, innerW, "fastBreakSpeed");
                }
            }
            case "No Pitch Limit" -> {
                if (specificRow == 0) {
                    config.noPitchLimit = !config.noPitchLimit;
                    config.save();
                    playClickSound();
                }
            }
            case "Abort Breaking" -> {
                if (specificRow == 0) {
                    config.abortBreaking = !config.abortBreaking;
                    config.save();
                    playClickSound();
                }
            }
            case "Anti Hunger" -> {
                if (specificRow == 0) {
                    config.antiHunger = !config.antiHunger;
                    config.save();
                    playClickSound();
                }
            }
            case "Anti Levitation" -> {
                if (specificRow == 0) {
                    config.antiLevitation = !config.antiLevitation;
                    config.save();
                    playClickSound();
                }
            }
            case "Backtrack" -> {
                if (specificRow == 0) {
                    config.backtrack = !config.backtrack;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    startSliderDrag(mouseX, innerX, innerW, "backtrackDelay");
                }
            }
            case "Disabler" -> {
                if (specificRow == 0) {
                    config.disabler = !config.disabler;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    config.disablerMode = (config.disablerMode + 1) % 2;
                    config.save();
                    playClickSound();
                }
            }
            case "Exploit Guard" -> {
                if (specificRow == 0) {
                    config.exploitGuard = !config.exploitGuard;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    startSliderDrag(mouseX, innerX, innerW, "exploitGuardMaxParticles");
                }
            }
            case "Ghost Hand" -> {
                if (specificRow == 0) {
                    config.ghostHand = !config.ghostHand;
                    config.save();
                    playClickSound();
                }
            }
            case "No Mining Trace" -> {
                if (specificRow == 0) {
                    config.noMiningTrace = !config.noMiningTrace;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    config.noMiningTraceToolsOnly = !config.noMiningTraceToolsOnly;
                    config.save();
                    playClickSound();
                }
            }
            case "No Slowdown" -> {
                if (specificRow == 0) {
                    config.noSlowdown = !config.noSlowdown;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    config.noSlowdownMode = (config.noSlowdownMode + 1) % 3;
                    config.save();
                    playClickSound();
                }
            }
            case "Ping Spoof" -> {
                if (specificRow == 0) {
                    config.pingSpoof = !config.pingSpoof;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    startSliderDrag(mouseX, innerX, innerW, "pingSpoofDelay");
                }
            }
            case "Packet Mine" -> {
                if (specificRow == 0) {
                    config.packetMine = !config.packetMine;
                    if (!config.packetMine) {
                        PacketMineHandler.clear();
                    }
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    config.packetMineRebreak = !config.packetMineRebreak;
                    config.save();
                    playClickSound();
                } else if (specificRow == 2) {
                    startSliderDrag(mouseX, innerX, innerW, "packetMineDelay");
                } else if (specificRow == 3) {
                    config.packetMineRender = !config.packetMineRender;
                    config.save();
                    playClickSound();
                }
            }
            case "Portal GUI" -> {
                if (specificRow == 0) {
                    config.portalGui = !config.portalGui;
                    config.save();
                    playClickSound();
                }
            }
            case "Resource Pack Spoof" -> {
                if (specificRow == 0) {
                    config.resourcePackSpoof = !config.resourcePackSpoof;
                    config.save();
                    playClickSound();
                }
            }
            case "Chunk Borders" -> {
                if (specificRow == 0) {
                    config.chunkBorders = !config.chunkBorders;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    config.chunkBordersSubGrid = !config.chunkBordersSubGrid;
                    config.save();
                    playClickSound();
                }
            }
            case "Light Overlay" -> {
                if (specificRow == 0) {
                    config.lightOverlay = !config.lightOverlay;
                    if (!config.lightOverlay) {
                        WorldRenderToolsHandler.clearLightCache();
                    }
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    startSliderDrag(mouseX, innerX, innerW, "lightOverlayRange");
                } else if (specificRow == 2) {
                    config.lightOverlayOnlyAlways = !config.lightOverlayOnlyAlways;
                    config.save();
                    playClickSound();
                }
            }
            case "Trajectories" -> {
                if (specificRow == 0) {
                    config.trajectories = !config.trajectories;
                    config.save();
                    playClickSound();
                } else if (specificRow == 1) {
                    config.trajectoriesShowPassive = !config.trajectoriesShowPassive;
                    config.save();
                    playClickSound();
                } else if (specificRow == 2) {
                    config.trajectoriesBoxOutline = !config.trajectoriesBoxOutline;
                    config.save();
                    playClickSound();
                }
            }
        }
    }

    private static void startSliderDrag(int mouseX, int x, int w, String key) {
        isDraggingSlider = true;
        draggingSliderKey = key;
        updateSliderValue(mouseX, x, w, key);
        playClickSound();
    }

    private static void updateSliderValue(int mouseX, int x, int w, String key) {
        float pct = Mth.clamp((float) (mouseX - x) / (float) w, 0.0f, 1.0f);
        ModConfig config = ModConfig.getInstance();
        switch (key) {
            case "invHudScale" -> config.invHudScale = Math.round((0.50f + pct * 1.50f) * 20.0f) / 20.0f;
            case "invBgAlpha" -> {
                int alpha = Math.round(pct * 100.0f);
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        invCfg.setInvAlpha(alpha);
                        InventoryGui.invAlpha = alpha / 100.0f;
                    }
                } catch (Throwable ignored) {
                }
            }
            case "zoomFactor" -> config.zoomFactor = Math.round((1.5f + pct * 13.5f) * 10.0f) / 10.0f;
            case "invSortingDelay" -> config.invSortingDelay = Math.round(pct * 10.0f);
            case "fastUseCooldown" -> config.fastUseCooldown = Math.round(pct * 4.0f);
            case "autoReplenishMinCount" -> config.autoReplenishMinCount = Math.max(1, Math.min(63, Math.round(1 + pct * 62.0f)));
            case "autoReplenishTickDelay" -> config.autoReplenishTickDelay = Math.round(pct * 10.0f);
            case "fastPlaceDelay" -> config.fastPlaceDelay = Math.round(pct * 4.0f);
            case "autoCrafterDelay" -> config.autoCrafterDelay = Math.max(0, Math.min(5, Math.round(pct * 5.0f)));
            case "fastBreakSpeed" -> {
                if (config.fastBreakMode == 1) {
                    config.fastBreakSpeed = Math.round((1.0f + pct * 0.15f) * 100.0f) / 100.0f;
                } else {
                    config.fastBreakSpeed = Math.round((0.5f + pct * 4.5f) * 10.0f) / 10.0f;
                }
            }
            case "pingSpoofDelay" -> config.pingSpoofDelay = Math.max(50, Math.min(1000, Math.round(50 + pct * 950.0f)));
            case "exploitGuardMaxParticles" -> config.exploitGuardMaxParticles = Math.max(100, Math.min(2000, Math.round(100 + pct * 1900.0f)));
            case "backtrackDelay" -> config.backtrackDelay = Math.max(40, Math.min(300, Math.round(40 + pct * 260.0f)));
            case "packetMineDelay" -> config.packetMineDelay = Math.max(0, Math.min(5, Math.round(pct * 5.0f)));
            case "lightOverlayRange" -> config.lightOverlayRange = Math.max(8, Math.min(24, Math.round(8 + pct * 16.0f)));
        }
    }

    public static class ModuleEntry {
        public final String name;
        public final Category category;
        public final Supplier<Boolean> getter;
        public final Consumer<Boolean> setter;

        public ModuleEntry(String name, Category category, Supplier<Boolean> getter, Consumer<Boolean> setter) {
            this.name = name;
            this.category = category;
            this.getter = getter;
            this.setter = setter;
        }
    }
}
