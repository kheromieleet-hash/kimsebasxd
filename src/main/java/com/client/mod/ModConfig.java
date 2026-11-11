package com.client.mod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ModConfig {
    public static class MacroEntry {
        public String name = "craft";
        public String message = "/craft";
        public int keyCode = -1;
        public boolean isMouse = false;

        public MacroEntry() {
        }

        public MacroEntry(String name, String message, int keyCode, boolean isMouse) {
            this.name = name;
            this.message = message;
            this.keyCode = keyCode;
            this.isMouse = isMouse;
        }

        public MacroEntry copy() {
            return new MacroEntry(this.name, this.message, this.keyCode, this.isMouse);
        }
    }
    private static ModConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Module master toggles
    public boolean dulkirEnabled = true;
    public boolean inventoryHudEnabled = true;

    // DulkirMod AnimationPreset fields
    public int posX = 0;                     // -150 to 150 (divided by 100f)
    public int posY = 0;                     // -150 to 150 (divided by 100f)
    public int posZ = 0;                     // -150 to 50  (divided by 100f)
    public int rotationX = 0;                // -180 to 180 degrees
    public int rotationY = 0;                // -180 to 180 degrees
    public int rotationZ = 0;                // -180 to 180 degrees
    public float itemScale = 1.0f;           // 0.1f to 2.0f
    public int swingDuration = 6;            // 2 to 20 ticks (default 6)
    public boolean cancelReEquip = false;    // default false
    public boolean rotationlessDrink = true; // default true in DulkirMod

    // Client-side Player Scale
    public float playerScale = 1.0f;         // 0.1f to 2.0f

    // Inventory HUD+ extra scale multiplier
    public float invHudScale = 1.0f;         // 0.50f to 2.00f

    // Top-Left (FPS, TPS, Ping, Speed) & Bottom-Right (Direction, Nether/Overworld, Pos) HUD
    public boolean hudInfoEnabled = true;

    // Keybinds HUD Table
    public boolean keybindsHudEnabled = true;
    public boolean keybindsHudOnlyActive = false;
    public boolean keybindsHudAutoHide = false;
    public boolean keybindsHudCollapsed = false;
    public int keybindsHudX = 8;
    public int keybindsHudY = 60;

    // Fullbright settings
    public boolean fullbright = true;
    public int fullbrightMode = 0;            // 0 = Natural / Night Vision (göz yormayan), 1 = Gamma (slider), 2 = Ultra
    public float fullbrightBrightness = 0.70f;// 0.20f to 1.00f
    public boolean fullbright3DShading = true;// 3D blok derinliğini korur, parlamayı önler
    public boolean noBlockShadows = false;    // AO köşe karartması
    public boolean fullbrightForceMaxLight = false;

    // Visual / QoL Features
    public boolean zoomEnabled = true;
    public float zoomFactor = 4.0f;
    public boolean lowFire = true;
    public float lowFireOffset = 0.35f;
    public boolean chatTimestamps = true;    // Istanbul time <HH:mm:ss>
    public boolean compactChat = true;       // Stacks duplicate consecutive messages with (2), (3)
    public boolean chatInfinite = true;      // Expands chat history from 100 to 16,384 lines
    public boolean chatAntiClear = true;     // Prevents server-side clear chat wipes & blank lines
    public boolean chatClickCopy = true;     // Right-click or Shift+Left click to copy message to clipboard

    // Native Inventory Tweaks
    public boolean invTweaksEnabled = true;
    public boolean invMouseDrag = true;
    public boolean invSorting = true;
    public int invSortingDelay = 1;
    public boolean invProtectHotbar = true;
    public boolean invXcarry = false;
    public boolean invDisableInCreative = true;
    public boolean invShulkerPreview = true;

    // Jello theme
    public boolean jelloBlur = true;
    public boolean autoSprint = true;

    // Fast Use
    public boolean fastUseEnabled = true;
    public int fastUseMode = 0;          // 0 = All, 1 = Some
    public boolean fastUseItems = true;
    public boolean fastUseBlocks = true;
    public int fastUseCooldown = 0;      // 0 to 4 ticks

    // Auto Replenish
    public boolean autoReplenishEnabled = false;
    public int autoReplenishMinCount = 8;        // 1 to 63
    public int autoReplenishTickDelay = 1;       // 0 to 10 ticks
    public boolean autoReplenishOffhand = true;
    public boolean autoReplenishUnstackable = true;
    public boolean autoReplenishSameEnchants = true;
    public boolean autoReplenishSearchHotbar = false;

    // Fast Place
    public boolean fastPlace = true;
    public int fastPlaceDelay = 0; // 0 to 4 ticks
    public boolean fastPlaceAutoRightClick = false;

    // Safe Walk
    public boolean safeWalk = false;

    // Auto Tool
    public boolean autoTool = true;
    public boolean autoToolSwapBack = true;

    // Extra Dulkir Animations
    public boolean dulkirAirWalker = false;

    // TNT & Creeper Timer
    public boolean tntTimer = true;

    // Advanced PvP & Render Modules
    public boolean trueSight = true;
    public boolean itemEsp = true;

    // ClickGUI Style: 0 = Classic Multi-Window, 1 = Modern Single-Panel
    public int clickGuiStyle = 1;
    public boolean toggleNotifications = true;

    public boolean fastBreak = false;
    public int fastBreakMode = 1; // 0 = Vanilla (0 Delay), 1 = Vulcan (Anti-Cheat Bypass)
    public float fastBreakSpeed = 1.15f;
    public boolean noPitchLimit = false;
    public boolean abortBreaking = false;
    public boolean portalGui = false;
    public boolean resourcePackSpoof = false;
    public boolean noMiningTrace = false;
    public boolean noMiningTraceToolsOnly = true;
    public boolean antiHunger = false;
    public boolean pingSpoof = false;
    public int pingSpoofDelay = 150;
    public boolean noSlowdown = false;
    public int noSlowdownMode = 0; // 0 = Vanilla, 1 = Vulcan, 2 = Grim
    public boolean ghostHand = false;
    public boolean exploitGuard = false;
    public int exploitGuardMaxParticles = 500;
    public boolean backtrack = false;
    public int backtrackDelay = 120;
    public boolean antiLevitation = false;
    public boolean disabler = false;
    public int disablerMode = 0; // 0 = Vulcan, 1 = Grim

    // Protocol & Version Switcher (LiquidBounce style)
    public int targetProtocol = -1; // -1 = Auto / Default (26.3 Native, Protocol 777)
    public String targetVersionName = "Auto";

    public boolean isCustomProtocol() {
        return targetProtocol > 0;
    }

    public String getDisplayVersion() {
        if (targetProtocol <= 0 || "Auto".equalsIgnoreCase(targetVersionName)) {
            return "Auto";
        }
        return targetVersionName;
    }

    // Auto Break
    public boolean autoBreakEnabled = false;
    public boolean autoBreakOnlyTool = false;

    // Auto Crafter
    public boolean autoCrafterEnabled = false;
    public int autoCrafterDelay = 0; // 0 to 5 ticks (0 = instant)
    public boolean autoCrafterCloseWhenDone = false;
    public Set<String> autoCrafterTargets = new HashSet<>(Arrays.asList(
            "iron_block", "gold_block", "diamond_block", "emerald_block",
            "lapis_block", "redstone_block", "coal_block", "copper_block"
    ));

    public boolean isAutoCraftTargetEnabled(String targetId) {
        if (autoCrafterTargets == null) {
            autoCrafterTargets = new HashSet<>();
        }
        return autoCrafterTargets.contains(targetId);
    }

    public void toggleAutoCraftTarget(String targetId) {
        if (autoCrafterTargets == null) {
            autoCrafterTargets = new HashSet<>();
        }
        if (autoCrafterTargets.contains(targetId)) {
            autoCrafterTargets.remove(targetId);
        } else {
            autoCrafterTargets.add(targetId);
        }
    }

    public void setAllAutoCraftTargets(boolean enabled) {
        if (autoCrafterTargets == null) {
            autoCrafterTargets = new HashSet<>();
        }
        if (enabled) {
            for (AutoCrafterHandler.CraftTarget target : AutoCrafterHandler.TARGETS) {
                autoCrafterTargets.add(target.id());
            }
        } else {
            autoCrafterTargets.clear();
        }
    }

    // Right-Shift ClickGUI Tab position & state
    public int guiWindowX = 480;
    public int guiWindowY = 34;
    public boolean guiExpanded = true;
    public int activeTopTab = 0; // 0 = Modules, 1 = Macros
    public List<MacroEntry> macros = new ArrayList<>();

    // LiquidBounce multi-category GUI positions & expanded state
    public Map<String, Integer> categoryX = new HashMap<>();
    public Map<String, Integer> categoryY = new HashMap<>();
    public Map<String, Boolean> categoryExpanded = new HashMap<>();

    public int getCategoryX(String id, int defaultX) {
        if (categoryX == null) categoryX = new HashMap<>();
        return categoryX.getOrDefault(id, defaultX);
    }

    public int getCategoryY(String id, int defaultY) {
        if (categoryY == null) categoryY = new HashMap<>();
        return categoryY.getOrDefault(id, defaultY);
    }

    public void setCategoryPos(String id, int x, int y) {
        if (categoryX == null) categoryX = new HashMap<>();
        if (categoryY == null) categoryY = new HashMap<>();
        categoryX.put(id, x);
        categoryY.put(id, y);
    }

    public boolean isCategoryExpanded(String id, boolean defaultExpanded) {
        if (categoryExpanded == null) categoryExpanded = new HashMap<>();
        return categoryExpanded.getOrDefault(id, defaultExpanded);
    }

    public void setCategoryExpanded(String id, boolean expanded) {
        if (categoryExpanded == null) categoryExpanded = new HashMap<>();
        categoryExpanded.put(id, expanded);
    }

    public void toggleCategoryExpanded(String id, boolean defaultExpanded) {
        boolean current = isCategoryExpanded(id, defaultExpanded);
        setCategoryExpanded(id, !current);
    }

    // Module Keybinds
    public Map<String, Integer> moduleKeybinds = new HashMap<>();
    public Map<String, Boolean> moduleKeybindIsMouse = new HashMap<>();

    public int getModuleKey(String id) {
        if (moduleKeybinds == null) moduleKeybinds = new HashMap<>();
        return moduleKeybinds.getOrDefault(id, -1);
    }

    public boolean isModuleKeyMouse(String id) {
        if (moduleKeybindIsMouse == null) moduleKeybindIsMouse = new HashMap<>();
        return moduleKeybindIsMouse.getOrDefault(id, false);
    }

    public void setModuleKey(String id, int keyCode, boolean isMouse) {
        if (moduleKeybinds == null) moduleKeybinds = new HashMap<>();
        if (moduleKeybindIsMouse == null) moduleKeybindIsMouse = new HashMap<>();
        if (keyCode < 0) {
            moduleKeybinds.remove(id);
            moduleKeybindIsMouse.remove(id);
        } else {
            moduleKeybinds.put(id, keyCode);
            moduleKeybindIsMouse.put(id, isMouse);
        }
        save();
    }

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        return INSTANCE;
    }

    public static void reloadChunks() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.levelExtractor != null) {
                mc.levelExtractor.allChanged();
            }
        } catch (Throwable ignored) {
        }
    }

    /** Formats the in-game hotbar overlay notification shown when toggling a module via keybind. */
    public static String formatToggleNotification(String modName, boolean enabled) {
        String status = "GUI Style".equals(modName)
                ? (enabled ? "§eModern Dashboard" : "§bClassic Multi-Window")
                : (enabled ? "§aEnabled" : "§cDisabled");
        return "§c[Sestia] §f" + modName + " §7» " + status;
    }

    public ModConfig copy() {
        ModConfig c = new ModConfig();
        c.copyFrom(this);
        return c;
    }

    public void copyFrom(ModConfig other) {
        this.dulkirEnabled = other.dulkirEnabled;
        this.inventoryHudEnabled = other.inventoryHudEnabled;
        this.posX = other.posX;
        this.posY = other.posY;
        this.posZ = other.posZ;
        this.rotationX = other.rotationX;
        this.rotationY = other.rotationY;
        this.rotationZ = other.rotationZ;
        this.itemScale = other.itemScale;
        this.swingDuration = other.swingDuration;
        this.cancelReEquip = other.cancelReEquip;
        this.rotationlessDrink = other.rotationlessDrink;
        this.playerScale = other.playerScale;
        this.invHudScale = other.invHudScale;
        this.hudInfoEnabled = other.hudInfoEnabled;
        this.keybindsHudEnabled = other.keybindsHudEnabled;
        this.keybindsHudOnlyActive = other.keybindsHudOnlyActive;
        this.keybindsHudAutoHide = other.keybindsHudAutoHide;
        this.keybindsHudCollapsed = other.keybindsHudCollapsed;
        this.keybindsHudX = other.keybindsHudX;
        this.keybindsHudY = other.keybindsHudY;
        this.fullbright = other.fullbright;
        this.fullbrightMode = other.fullbrightMode;
        this.fullbrightBrightness = other.fullbrightBrightness;
        this.fullbright3DShading = other.fullbright3DShading;
        this.noBlockShadows = other.noBlockShadows;
        this.fullbrightForceMaxLight = other.fullbrightForceMaxLight;
        this.zoomEnabled = other.zoomEnabled;
        this.zoomFactor = other.zoomFactor;
        this.lowFire = other.lowFire;
        this.lowFireOffset = other.lowFireOffset;
        this.chatTimestamps = other.chatTimestamps;
        this.compactChat = other.compactChat;
        this.chatInfinite = other.chatInfinite;
        this.chatAntiClear = other.chatAntiClear;
        this.chatClickCopy = other.chatClickCopy;
        this.invTweaksEnabled = other.invTweaksEnabled;
        this.invMouseDrag = other.invMouseDrag;
        this.invSorting = other.invSorting;
        this.invSortingDelay = other.invSortingDelay;
        this.invProtectHotbar = other.invProtectHotbar;
        this.invXcarry = other.invXcarry;
        this.invDisableInCreative = other.invDisableInCreative;
        this.invShulkerPreview = other.invShulkerPreview;
        this.jelloBlur = other.jelloBlur;
        this.autoSprint = other.autoSprint;
        this.fastUseEnabled = other.fastUseEnabled;
        this.fastUseMode = other.fastUseMode;
        this.fastUseItems = other.fastUseItems;
        this.fastUseBlocks = other.fastUseBlocks;
        this.fastUseCooldown = other.fastUseCooldown;
        this.autoReplenishEnabled = other.autoReplenishEnabled;
        this.autoReplenishMinCount = other.autoReplenishMinCount;
        this.autoReplenishTickDelay = other.autoReplenishTickDelay;
        this.autoReplenishOffhand = other.autoReplenishOffhand;
        this.autoReplenishUnstackable = other.autoReplenishUnstackable;
        this.autoReplenishSameEnchants = other.autoReplenishSameEnchants;
        this.autoReplenishSearchHotbar = other.autoReplenishSearchHotbar;
        this.fastPlace = other.fastPlace;
        this.fastPlaceDelay = other.fastPlaceDelay;
        this.fastPlaceAutoRightClick = other.fastPlaceAutoRightClick;
        this.safeWalk = other.safeWalk;
        this.autoTool = other.autoTool;
        this.autoToolSwapBack = other.autoToolSwapBack;
        this.dulkirAirWalker = other.dulkirAirWalker;
        this.tntTimer = other.tntTimer;
        this.trueSight = other.trueSight;
        this.itemEsp = other.itemEsp;
        this.clickGuiStyle = other.clickGuiStyle;
        this.toggleNotifications = other.toggleNotifications;
        this.fastBreak = other.fastBreak;
        this.fastBreakMode = other.fastBreakMode;
        this.fastBreakSpeed = other.fastBreakSpeed;
        this.noPitchLimit = other.noPitchLimit;
        this.abortBreaking = other.abortBreaking;
        this.portalGui = other.portalGui;
        this.resourcePackSpoof = other.resourcePackSpoof;
        this.noMiningTrace = other.noMiningTrace;
        this.noMiningTraceToolsOnly = other.noMiningTraceToolsOnly;
        this.antiHunger = other.antiHunger;
        this.pingSpoof = other.pingSpoof;
        this.pingSpoofDelay = Math.max(50, Math.min(1000, other.pingSpoofDelay));
        this.noSlowdown = other.noSlowdown;
        this.noSlowdownMode = Math.max(0, Math.min(2, other.noSlowdownMode));
        this.ghostHand = other.ghostHand;
        this.exploitGuard = other.exploitGuard;
        this.exploitGuardMaxParticles = Math.max(100, Math.min(2000, other.exploitGuardMaxParticles));
        this.backtrack = other.backtrack;
        this.backtrackDelay = Math.max(40, Math.min(300, other.backtrackDelay));
        this.antiLevitation = other.antiLevitation;
        this.disabler = other.disabler;
        this.disablerMode = Math.max(0, Math.min(1, other.disablerMode));
        this.targetProtocol = other.targetProtocol;
        this.targetVersionName = other.targetVersionName != null ? other.targetVersionName : "Auto";
        this.autoBreakEnabled = other.autoBreakEnabled;
        this.autoBreakOnlyTool = other.autoBreakOnlyTool;
        this.autoCrafterEnabled = other.autoCrafterEnabled;
        this.autoCrafterDelay = other.autoCrafterDelay;
        this.autoCrafterCloseWhenDone = other.autoCrafterCloseWhenDone;
        this.autoCrafterTargets = new HashSet<>();
        if (other.autoCrafterTargets != null) {
            this.autoCrafterTargets.addAll(other.autoCrafterTargets);
        }
        this.guiWindowX = other.guiWindowX;
        this.guiWindowY = other.guiWindowY;
        this.guiExpanded = other.guiExpanded;
        this.activeTopTab = other.activeTopTab;
        this.macros = new ArrayList<>();
        if (other.macros != null) {
            for (MacroEntry m : other.macros) {
                if (m != null) {
                    this.macros.add(m.copy());
                }
            }
        }
        this.categoryX = new HashMap<>();
        if (other.categoryX != null) {
            this.categoryX.putAll(other.categoryX);
        }
        this.categoryY = new HashMap<>();
        if (other.categoryY != null) {
            this.categoryY.putAll(other.categoryY);
        }
        this.categoryExpanded = new HashMap<>();
        if (other.categoryExpanded != null) {
            this.categoryExpanded.putAll(other.categoryExpanded);
        }
        this.moduleKeybinds = new HashMap<>();
        if (other.moduleKeybinds != null) {
            this.moduleKeybinds.putAll(other.moduleKeybinds);
        }
        this.moduleKeybindIsMouse = new HashMap<>();
        if (other.moduleKeybindIsMouse != null) {
            this.moduleKeybindIsMouse.putAll(other.moduleKeybindIsMouse);
        }
    }

    private static File getConfigFile() {
        return new File(FabricLoader.getInstance().getConfigDir().toFile(), "customclient.json");
    }

    public void load() {
        File file = getConfigFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    this.copyFrom(loaded);
                    if (this.itemScale < 0.05f || this.itemScale > 5.0f) this.itemScale = 1.0f;
                    if (this.playerScale < 0.05f || this.playerScale > 5.0f) this.playerScale = 1.0f;
                    if (this.invHudScale < 0.50f || this.invHudScale > 2.50f) this.invHudScale = 1.0f;
                    if (this.fullbrightBrightness < 0.20f || this.fullbrightBrightness > 1.0f) this.fullbrightBrightness = 0.70f;
                    if (this.fullbrightMode < 0 || this.fullbrightMode > 2) this.fullbrightMode = 0;
                    if (this.swingDuration < 1 || this.swingDuration > 40) this.swingDuration = 6;
                    if (this.zoomFactor < 1.5f || this.zoomFactor > 20.0f) this.zoomFactor = 4.0f;
                    if (this.lowFireOffset < 0.05f || this.lowFireOffset > 0.8f) this.lowFireOffset = 0.35f;
                    if (this.autoCrafterDelay < 0 || this.autoCrafterDelay > 10) this.autoCrafterDelay = 0;
                    if (this.autoCrafterTargets == null || this.autoCrafterTargets.isEmpty()) {
                        this.autoCrafterTargets = new HashSet<>(Arrays.asList(
                                "iron_block", "gold_block", "diamond_block", "emerald_block",
                                "lapis_block", "redstone_block", "coal_block", "copper_block"
                        ));
                    }
                    if (this.fastBreakSpeed < 0.5f || this.fastBreakSpeed > 5.0f) this.fastBreakSpeed = 1.15f;
                    if (this.fastBreakMode < 0 || this.fastBreakMode > 1) this.fastBreakMode = 1;
                    if (this.pingSpoofDelay < 50 || this.pingSpoofDelay > 1000) this.pingSpoofDelay = 150;
                    if (this.noSlowdownMode < 0 || this.noSlowdownMode > 2) this.noSlowdownMode = 0;
                    if (this.exploitGuardMaxParticles < 100 || this.exploitGuardMaxParticles > 2000) this.exploitGuardMaxParticles = 500;
                    if (this.backtrackDelay < 40 || this.backtrackDelay > 300) this.backtrackDelay = 120;
                    if (this.disablerMode < 0 || this.disablerMode > 1) this.disablerMode = 0;
                    if (this.clickGuiStyle < 0 || this.clickGuiStyle > 1) this.clickGuiStyle = 1;
                    if (this.targetVersionName == null) this.targetVersionName = "Auto";
                    if (this.categoryX == null) this.categoryX = new HashMap<>();
                    if (this.categoryY == null) this.categoryY = new HashMap<>();
                    if (this.categoryExpanded == null) this.categoryExpanded = new HashMap<>();
                    if (this.moduleKeybinds == null) this.moduleKeybinds = new HashMap<>();
                    if (this.moduleKeybindIsMouse == null) this.moduleKeybindIsMouse = new HashMap<>();
                    if (this.moduleKeybinds.isEmpty()) {
                        this.moduleKeybinds.put("Zoom", 67); // C
                        this.moduleKeybindIsMouse.put("Zoom", false);
                        this.moduleKeybinds.put("Fullbright", 66); // B
                        this.moduleKeybindIsMouse.put("Fullbright", false);
                        this.keybindsHudEnabled = true;
                    }
                    if (this.keybindsHudX < 0 || this.keybindsHudX > 3000) this.keybindsHudX = 8;
                    if (this.keybindsHudY < 0 || this.keybindsHudY > 3000) this.keybindsHudY = 60;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            save();
        }
    }

    public void save() {
        File file = getConfigFile();
        try {
            if (!file.getParentFile().exists()) {
                file.getParentFile().mkdirs();
            }
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
