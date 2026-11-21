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
    public boolean inventoryHudEnabled = true;

    // Inventory HUD+ extra scale multiplier
    public float invHudScale = 1.0f;         // 0.50f to 2.00f

    // Top-Left (FPS, TPS, Ping, Speed) & Bottom-Right (Direction, Nether/Overworld, Pos) HUD
    public boolean hudInfoEnabled = true;
    public boolean hudTelemetryEnabled = true;
    public boolean hudPositionEnabled = true;
    public boolean hudShowFps = true;
    public boolean hudShowTps = true;
    public boolean hudShowPing = true;
    public boolean hudShowSpeed = true;
    public boolean hudShowDir = true;
    public boolean hudShowNether = true;
    public boolean hudShowPos = true;
    public boolean hudShowClock = true;
    public boolean hudUnifiedGlass = true;
    public boolean hudWatermarkEnabled = true;
    public boolean hudArrayListEnabled = true;
    public int hudWatermarkX = 4;
    public int hudWatermarkY = 4;
    public int hudArrayListX = -1;
    public int hudArrayListY = -1;
    public int statsHudX = 4;
    public int statsHudY = 4;
    public int posHudX = -1;
    public int posHudY = -1;
    public int clockHudX = -1;
    public int clockHudY = -1;

    // Keybinds HUD Table
    public boolean keybindsHudEnabled = true;
    public boolean keybindsHudOnlyActive = false;
    public boolean keybindsHudAutoHide = false;
    public int keybindsHudX = 8;
    public int keybindsHudY = 60;

    // Player List HUD (nearby players)
    public boolean playerListEnabled = false;
    public int playerListAlign = 0; // 0 = Top Right, 1 = Top Left
    public boolean playerListShowDistance = true;
    public boolean playerListShowHealth = true;
    public boolean playerListIgnoreNpcs = false;
    public boolean playerListAutoHide = false;
    public int playerListMaxRows = 20;
    public int playerListX = -1;
    public int playerListY = -1;

    // Item Pickup / Drop Notifier HUD
    public boolean itemNotifierEnabled = false;
    public float itemNotifierDuration = 3.0f;
    public int itemNotifierMaxItems = 5;
    public int itemNotifierX = -1;
    public int itemNotifierY = -1;

    // Fullbright settings
    public boolean fullbright = false;
    public boolean fullbrightForceMaxLight = false;

    // Visual / QoL Features
    public boolean zoomEnabled = true;
    public float zoomFactor = 4.0f;
    public boolean customCrosshair = true;
    public boolean customFont = true;
    public boolean chatTimestamps = true;    // Istanbul time <HH:mm:ss>
    public boolean compactChat = true;       // Stacks duplicate consecutive messages with (2), (3)
    public boolean chatInfinite = true;      // Expands chat history from 100 to 16,384 lines
    public boolean chatAntiClear = true;     // Prevents server-side clear chat wipes & blank lines
    public boolean chatClickCopy = true;     // Right-click or Shift+Left click to copy message to clipboard
    public boolean chatMentionAlert = false; // Windows notification & sound when mentioned in chat (default OFF)
    public boolean chatMentionAlertDefaultOffMigrated = false;

    // Cloudflare Client Chat
    public int configVersion = 0;
    public boolean clientChatEnabled = true;
    public String clientChatUrl = "";
    public boolean clientChatAllowDms = true;
    public String clientChatMuted = "";

    // Native Inventory Tweaks
    public boolean invTweaksEnabled = false;
    public boolean invMouseDrag = true;
    public boolean invSorting = true;
    public int invSortingDelay = 1;
    public boolean invProtectHotbar = true;
    public boolean invXcarry = false;
    public boolean invDisableInCreative = true;
    public boolean invShulkerPreview = true;

    public boolean autoSprint = false;
    public boolean autoWalkEnabled = false;
    public int autoWalkDirection = 0;    // 0 = Forwards, 1 = Backwards, 2 = Left, 3 = Right

    // Fast Use
    public boolean fastUseEnabled = false;
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
    public boolean fastPlace = false;
    public int fastPlaceDelay = 0; // 0 to 4 ticks
    public boolean fastPlaceAutoRightClick = false;
    public boolean fastPlaceAutoRightClickResume = false;

    // Safe Walk
    public boolean safeWalk = false;

    // Auto Tool
    public boolean autoTool = false;
    public boolean autoToolSwapBack = true;

    // Viewmodel (NoFrills + Item/Player Scale + AirWalker)
    public boolean vmEnabled = false;
    public boolean vmNoHaste = false;
    public boolean vmNoEquip = false;
    public boolean vmApplyToHand = false;
    public boolean vmNoBowSwing = false;
    public boolean vmNoSwingReset = false;
    public boolean vmAirWalker = false;
    public float itemScale = 1.0f;      // 0.10 to 2.00
    public float playerScale = 1.0f;    // 0.10 to 2.00
    public int vmSwingSpeed = 0;        // 0 to 100 (0 = disabled)
    public float vmOffsetX = 0.0f;      // -2.0 to 2.0
    public float vmOffsetY = 0.0f;      // -2.0 to 2.0
    public float vmOffsetZ = 0.0f;      // -2.0 to 2.0
    public float vmScaleX = 1.0f;       // 0.0 to 5.0
    public float vmScaleY = 1.0f;       // 0.0 to 5.0
    public float vmScaleZ = 1.0f;       // 0.0 to 5.0
    public float vmRotX = 0.0f;         // -180.0 to 180.0
    public float vmRotY = 0.0f;         // -180.0 to 180.0
    public float vmRotZ = 0.0f;         // -180.0 to 180.0
    public float vmSwingX = 1.0f;       // 0.0 to 2.0
    public float vmSwingY = 1.0f;       // 0.0 to 2.0
    public float vmSwingZ = 1.0f;       // 0.0 to 2.0

    // TNT & Creeper Timer
    public boolean tntTimer = false;

    // Advanced PvP & Render Modules
    public boolean trueSight = false;
    public boolean itemEsp = false;

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
    public boolean packetMine = false;
    public boolean packetMineRebreak = true;
    public int packetMineDelay = 1;
    public boolean packetMineRender = true;
    public boolean chunkBorders = false;
    public boolean chunkBordersSubGrid = false;
    public boolean lightOverlay = false;
    public int lightOverlayRange = 16;
    public boolean lightOverlayOnlyAlways = false;
    public boolean trajectories = false;
    public boolean trajectoriesShowPassive = true;
    public boolean trajectoriesBoxOutline = true;

    // Freecam (Meteor + LiquidBounce hybrid)
    public boolean freecamEnabled = false;
    public float freecamSpeed = 1.0f;
    public boolean freecamScrollSpeed = true;
    public boolean freecamToggleOnDamage = true;
    public boolean freecamAllowInteract = false;
    public boolean freecamRotate = true;

    // LiquidBounce Wings (Render: Lines & Dragon modes)
    public boolean wingsEnabled = false;
    public int wingsMode = WingsLogic.MODE_LINES;
    public int wingsColorPreset = WingsLogic.COLOR_CYAN;
    public int wingsCustomRgb = 0x00D4FF;
    public float wingsCustomHue = 0.528f;
    public float wingsCustomSat = 1.00f;
    public float wingsCustomVal = 1.00f;
    public float wingsLength = 1.15f;
    public float wingsWidth = 0.06f;
    public int wingsLinesCount = 5;
    public float wingsFadeStart = 0.50f;
    public float wingsHeight = 0.15f;
    public float wingsFlapSpeed = 1.0f;
    public boolean wingsShowDamage = true;

    // Xray (Meteor parity)
    public boolean xrayEnabled = false;
    public int xrayOpacity = 0; // 0 = hide non-targets, 1..254 = translucent, 255 = solid
    public boolean xrayOpacityDefaultMigrated = true;
    public boolean xrayExposedOnly = false;
    public Set<String> xrayBlocks = XrayHandler.createDefaultBlocks();

    // Block ESP (Meteor parity)
    public boolean blockEspEnabled = false;
    public int blockEspRange = 64; // 16..160 blocks
    public BlockEspHandler.BlockEspStyle blockEspDefaultStyle = new BlockEspHandler.BlockEspStyle();
    public Set<String> blockEspBlocks = XrayHandler.createDefaultBlockEspBlocks();
    public Map<String, BlockEspHandler.BlockEspStyle> blockEspPerBlockStyles = new HashMap<>();

    public boolean isXrayBlockEnabled(String blockId) {
        if (xrayBlocks == null) {
            xrayBlocks = XrayHandler.createDefaultBlocks();
        }
        return blockId != null && xrayBlocks.contains(blockId);
    }

    public void toggleXrayBlock(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }
        if (xrayBlocks == null) {
            xrayBlocks = XrayHandler.createDefaultBlocks();
        }
        if (!xrayBlocks.remove(blockId)) {
            xrayBlocks.add(blockId);
        }
        XrayHandler.invalidateBlockCache();
    }

    public boolean isBlockEspTargetEnabled(String blockId) {
        if (blockEspBlocks == null) {
            blockEspBlocks = XrayHandler.createDefaultBlockEspBlocks();
        }
        return blockId != null && blockEspBlocks.contains(blockId);
    }

    public void toggleBlockEspTarget(String blockId) {
        if (blockId == null || blockId.isEmpty()) {
            return;
        }
        if (blockEspBlocks == null) {
            blockEspBlocks = XrayHandler.createDefaultBlockEspBlocks();
        }
        if (!blockEspBlocks.remove(blockId)) {
            blockEspBlocks.add(blockId);
        }
    }

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

    public void resetDefaultAutoCraftTargets() {
        this.autoCrafterTargets = new HashSet<>(Arrays.asList(
                "iron_block", "gold_block", "diamond_block", "emerald_block",
                "lapis_block", "redstone_block", "coal_block", "copper_block"
        ));
    }

    // Auto Sell
    public boolean autoSellEnabled = false;
    public int autoSellDelay = 5; // 1 to 60 seconds
    public boolean autoSellSafetyPause = true;
    public boolean autoSellOnlySelected = false;
    public Set<String> autoSellTargets = new HashSet<>();

    public boolean isAutoSellTargetEnabled(String targetId) {
        if (autoSellTargets == null) {
            autoSellTargets = new HashSet<>();
        }
        return autoSellTargets.contains(targetId);
    }

    public void toggleAutoSellTarget(String targetId) {
        if (autoSellTargets == null) {
            autoSellTargets = new HashSet<>();
        }
        if (autoSellTargets.contains(targetId)) {
            autoSellTargets.remove(targetId);
        } else {
            autoSellTargets.add(targetId);
        }
    }

    // Auto Drop
    public boolean autoDropEnabled = false;
    public int autoDropDelay = 2; // 0 to 20 ticks
    public boolean autoDropExcludeHotbar = true;
    public Set<String> autoDropTargets = new HashSet<>();

    public boolean isAutoDropTargetEnabled(String targetId) {
        if (autoDropTargets == null) {
            autoDropTargets = new HashSet<>();
        }
        return autoDropTargets.contains(targetId);
    }

    public void toggleAutoDropTarget(String targetId) {
        if (autoDropTargets == null) {
            autoDropTargets = new HashSet<>();
        }
        if (autoDropTargets.contains(targetId)) {
            autoDropTargets.remove(targetId);
        } else {
            autoDropTargets.add(targetId);
        }
    }

    // Account Switcher per-account auto-login passwords (lowercase username -> password)
    public Map<String, String> accountPasswords = new HashMap<>();

    // New Velocity (Combat)
    public boolean newVelocityEnabled = false;
    public int newVelocityMode = 0; // 0=Normal, 1=Jump Reset, 2=Grim
    public int newVelocityHorizontal = 0; // 0 to 100%
    public int newVelocityVertical = 0; // 0 to 100%
    public int newVelocityChance = 100; // 0 to 100%
    public boolean newVelocityExplosions = true;

    // No Strip & Better Place (Numby Hack)
    public boolean noStrip = false;
    public boolean noStripAllowSneak = true;
    public boolean betterPlace = false;
    public float betterPlaceRange = 4.5f;

    // MultiTask (Meteor Tweaks)
    public boolean multiTask = false;

    // Auto Wheat Farm (Area Bot)
    public boolean wheatFarmEnabled = false;
    public boolean wheatFarmPos1Set = false;
    public int wheatFarmX1 = 0;
    public int wheatFarmY1 = 0;
    public int wheatFarmZ1 = 0;
    public boolean wheatFarmPos2Set = false;
    public int wheatFarmX2 = 0;
    public int wheatFarmY2 = 0;
    public int wheatFarmZ2 = 0;
    public boolean wheatFarmWalk = true;
    public boolean wheatFarmFly = true;
    public boolean wheatFarmStickWand = true;
    public boolean wheatFarmFailsafe = true;
    public boolean wheatFarmFlyMigrated = false;
    public boolean wheatFarmSafeMigrated = false;
    public Set<String> wheatFarmWhitelist = new HashSet<>();
    public int wheatFarmDelay = 4; // 2 to 20 ticks
    public float wheatFarmTurnSpeed = 16.0f; // 5 to 30 deg/tick

    public boolean isWheatFarmWhitelisted(String name) {
        if (name == null || name.isBlank() || wheatFarmWhitelist == null) {
            return false;
        }
        return wheatFarmWhitelist.contains(name.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public boolean addWheatFarmWhitelist(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (wheatFarmWhitelist == null) {
            wheatFarmWhitelist = new HashSet<>();
        }
        return wheatFarmWhitelist.add(name.trim().toLowerCase(java.util.Locale.ROOT));
    }

    public boolean removeWheatFarmWhitelist(String name) {
        if (name == null || name.isBlank() || wheatFarmWhitelist == null) {
            return false;
        }
        return wheatFarmWhitelist.remove(name.trim().toLowerCase(java.util.Locale.ROOT));
    }

    // Auto Fish (Minigame Bar & Auto Recast)
    public boolean autoFishEnabled = true;
    public boolean autoFishMigrated = false;
    public boolean autoFishRecast = true;
    public int autoFishClickDelay = 5; // 1 to 15 ticks
    public int autoFishRecastDelay = 15; // 8 to 40 ticks

    // Auto Spawner (S-Snake Room Looter, /craft & Chest Depositor)
    public boolean autoSpawnerEnabled = false;
    public int autoSpawnerCount = 0;        // 0 = Auto (all spawners in cluster), max 256
    public int autoSpawnerSearchRadius = 26; // 12 to 40
    public int autoSpawnerClickDelay = 3;    // 2 to 8 ticks
    public boolean autoSpawnerLockMouse = true;
    public boolean autoSpawnerRenderEsp = true;
    public int autoSpawnerHudX = -1;
    public int autoSpawnerHudY = -1;
    public boolean autoSpawnerHudDetailed = true;
    public int autoSpawnerEspDetail = 2;     // 0=Minimal, 1=Standard, 2=Tactical
    public boolean autoSpawnerReachBatch = true;
    public boolean autoSpawnerLegitMode = true;
    public boolean autoSpawnerSafetyEnabled = true;
    public int autoSpawnerSafetyRadius = 16; // 6 to 48 blocks
    public int autoSpawnerSafetyAction = 1;  // 0=Pause & Alert, 1=Pause & Auto-Resume, 2=Disconnect
    public int autoSpawnerSafetyResumeSec = 15; // 5 to 120 seconds
    public boolean autoSpawnerTpsAdaptive = true;
    public boolean autoSpawnerAutoResume = true;

    // Spawner Auto Sell (Toggle all spawners to Gold Block / Gold Nugget)
    public boolean spawnerAutoSellEnabled = false;
    public boolean spawnerAutoSellTargetGoldBlock = true;
    public int spawnerAutoSellSearchRadius = 26;
    public boolean spawnerAutoSellLockMouse = true;
    public boolean spawnerAutoSellRenderEsp = true;
    public int spawnerAutoSellHudX = -1;
    public int spawnerAutoSellHudY = -1;

    // No Pause On Lost Focus (Meteor Utils)
    public boolean noPauseOnLostFocus = false;

    // Item Sucker (Movement)
    public boolean itemSuckerEnabled = false;
    public int itemSuckerMode = 0; // 0=Move, 1=TP
    public float itemSuckerRange = 6.0f; // 2.0f to 16.0f
    public float itemSuckerSpeed = 0.35f; // 0.15f to 1.00f
    public boolean itemSuckerOnlyPickable = true;
    public boolean itemSuckerOnlyOnGround = false;

    // Right-Shift ClickGUI Tab position & state
    public int guiTheme = 0; // 0=Sestia OLED, 1=Transparent, 2=Light, 3=Monokai Pro, 4=Dark
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

    // Module Keybinds & Favorites
    public Map<String, Integer> moduleKeybinds = new HashMap<>();
    public Map<String, Boolean> moduleKeybindIsMouse = new HashMap<>();
    public Set<String> favoriteModules = new HashSet<>();

    public static final class CustomModuleFolder {
        public String id;
        public String name;
        public Set<String> modules = new HashSet<>();
    }

    public List<CustomModuleFolder> customFolders = new ArrayList<>();

    public CustomModuleFolder getCustomFolder(String folderId) {
        if (folderId == null || customFolders == null) return null;
        for (CustomModuleFolder f : customFolders) {
            if (f != null && folderId.equals(f.id)) return f;
        }
        return null;
    }

    public CustomModuleFolder createCustomFolder(String name) {
        if (customFolders == null) customFolders = new ArrayList<>();
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) clean = "Folder " + (customFolders.size() + 1);
        if (clean.length() > 20) clean = clean.substring(0, 20);
        int seq = 1;
        String id;
        do {
            id = "FOLDER_" + seq++;
        } while (getCustomFolder(id) != null);
        CustomModuleFolder f = new CustomModuleFolder();
        f.id = id;
        f.name = clean;
        f.modules = new HashSet<>();
        customFolders.add(f);
        save();
        return f;
    }

    public void deleteCustomFolder(String folderId) {
        if (customFolders == null || folderId == null) return;
        customFolders.removeIf(f -> f != null && folderId.equals(f.id));
        save();
    }

    public boolean renameCustomFolder(String folderId, String newName) {
        if (renameCustomFolderInMemory(folderId, newName)) {
            save();
            return true;
        }
        return false;
    }

    boolean renameCustomFolderInMemory(String folderId, String newName) {
        CustomModuleFolder f = getCustomFolder(folderId);
        if (f == null || newName == null) return false;
        String clean = newName.trim();
        if (clean.isEmpty()) return false;
        if (clean.length() > 20) clean = clean.substring(0, 20);
        f.name = clean;
        return true;
    }

    public boolean isModuleInCustomFolder(String folderId, String moduleName) {
        CustomModuleFolder f = getCustomFolder(folderId);
        return f != null && f.modules != null && moduleName != null && f.modules.contains(moduleName);
    }

    public void toggleModuleInCustomFolder(String folderId, String moduleName) {
        CustomModuleFolder f = getCustomFolder(folderId);
        if (f == null || moduleName == null) return;
        if (f.modules == null) f.modules = new HashSet<>();
        if (!f.modules.remove(moduleName)) {
            f.modules.add(moduleName);
        }
        save();
    }

    public boolean isFavoriteModule(String id) {
        if (id == null) return false;
        if (favoriteModules == null) favoriteModules = new HashSet<>();
        return favoriteModules.contains(id);
    }

    public void toggleFavoriteModule(String id) {
        if (id == null) return;
        if (favoriteModules == null) favoriteModules = new HashSet<>();
        if (!favoriteModules.remove(id)) {
            favoriteModules.add(id);
        }
        save();
    }

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

    public ModConfig copy() {
        ModConfig c = new ModConfig();
        c.copyFrom(this);
        return c;
    }

    public void copyFrom(ModConfig other) {
        this.inventoryHudEnabled = other.inventoryHudEnabled;
        this.invHudScale = other.invHudScale;
        this.hudInfoEnabled = other.hudInfoEnabled;
        this.hudTelemetryEnabled = other.hudTelemetryEnabled;
        this.hudPositionEnabled = other.hudPositionEnabled;
        this.hudShowFps = other.hudShowFps;
        this.hudShowTps = other.hudShowTps;
        this.hudShowPing = other.hudShowPing;
        this.hudShowSpeed = other.hudShowSpeed;
        this.hudShowDir = other.hudShowDir;
        this.hudShowNether = other.hudShowNether;
        this.hudShowPos = other.hudShowPos;
        this.hudShowClock = other.hudShowClock;
        this.hudUnifiedGlass = other.hudUnifiedGlass;
        this.hudWatermarkEnabled = other.hudWatermarkEnabled;
        this.hudArrayListEnabled = other.hudArrayListEnabled;
        this.hudWatermarkX = other.hudWatermarkX;
        this.hudWatermarkY = other.hudWatermarkY;
        this.hudArrayListX = other.hudArrayListX;
        this.hudArrayListY = other.hudArrayListY;
        this.statsHudX = other.statsHudX;
        this.statsHudY = other.statsHudY;
        this.posHudX = other.posHudX;
        this.posHudY = other.posHudY;
        this.clockHudX = other.clockHudX;
        this.clockHudY = other.clockHudY;
        this.itemNotifierEnabled = other.itemNotifierEnabled;
        this.itemNotifierDuration = other.itemNotifierDuration;
        this.itemNotifierMaxItems = other.itemNotifierMaxItems;
        this.itemNotifierX = other.itemNotifierX;
        this.itemNotifierY = other.itemNotifierY;
        this.keybindsHudEnabled = other.keybindsHudEnabled;
        this.keybindsHudOnlyActive = other.keybindsHudOnlyActive;
        this.keybindsHudAutoHide = other.keybindsHudAutoHide;
        this.keybindsHudX = other.keybindsHudX;
        this.keybindsHudY = other.keybindsHudY;
        this.playerListEnabled = other.playerListEnabled;
        this.playerListAlign = (other.playerListAlign == 1) ? 1 : 0;
        this.playerListShowDistance = other.playerListShowDistance;
        this.playerListShowHealth = other.playerListShowHealth;
        this.playerListIgnoreNpcs = other.playerListIgnoreNpcs;
        this.playerListAutoHide = other.playerListAutoHide;
        this.playerListMaxRows = clampInt(other.playerListMaxRows, 4, 40, 20);
        this.playerListX = other.playerListX;
        this.playerListY = other.playerListY;
        this.fullbright = other.fullbright;
        this.fullbrightForceMaxLight = other.fullbrightForceMaxLight;
        this.zoomEnabled = other.zoomEnabled;
        this.zoomFactor = other.zoomFactor;
        this.customCrosshair = other.customCrosshair;
        this.customFont = other.customFont;
        this.chatTimestamps = other.chatTimestamps;
        this.compactChat = other.compactChat;
        this.chatInfinite = other.chatInfinite;
        this.chatAntiClear = other.chatAntiClear;
        this.chatClickCopy = other.chatClickCopy;
        this.chatMentionAlert = other.chatMentionAlert;
        this.chatMentionAlertDefaultOffMigrated = other.chatMentionAlertDefaultOffMigrated;
        this.invTweaksEnabled = other.invTweaksEnabled;
        this.invMouseDrag = other.invMouseDrag;
        this.invSorting = other.invSorting;
        this.invSortingDelay = other.invSortingDelay;
        this.invProtectHotbar = other.invProtectHotbar;
        this.invXcarry = other.invXcarry;
        this.invDisableInCreative = other.invDisableInCreative;
        this.invShulkerPreview = other.invShulkerPreview;
        this.autoSprint = other.autoSprint;
        this.autoWalkEnabled = other.autoWalkEnabled;
        this.autoWalkDirection = other.autoWalkDirection;
        this.fastUseEnabled = other.fastUseEnabled;
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
        this.fastPlaceAutoRightClickResume = other.fastPlaceAutoRightClickResume;
        this.safeWalk = other.safeWalk;
        this.autoTool = other.autoTool;
        this.autoToolSwapBack = other.autoToolSwapBack;
        this.vmEnabled = other.vmEnabled;
        this.vmNoHaste = other.vmNoHaste;
        this.vmNoEquip = other.vmNoEquip;
        this.vmApplyToHand = other.vmApplyToHand;
        this.vmNoBowSwing = other.vmNoBowSwing;
        this.vmNoSwingReset = other.vmNoSwingReset;
        this.vmAirWalker = other.vmAirWalker;
        this.itemScale = clampFloat(other.itemScale, 0.10f, 2.00f, 1.0f);
        this.playerScale = clampFloat(other.playerScale, 0.10f, 2.00f, 1.0f);
        this.vmSwingSpeed = clampInt(other.vmSwingSpeed, 0, 100, 0);
        this.vmOffsetX = clampFloat(other.vmOffsetX, -2.0f, 2.0f, 0.0f);
        this.vmOffsetY = clampFloat(other.vmOffsetY, -2.0f, 2.0f, 0.0f);
        this.vmOffsetZ = clampFloat(other.vmOffsetZ, -2.0f, 2.0f, 0.0f);
        this.vmScaleX = clampFloat(other.vmScaleX, 0.0f, 5.0f, 1.0f);
        this.vmScaleY = clampFloat(other.vmScaleY, 0.0f, 5.0f, 1.0f);
        this.vmScaleZ = clampFloat(other.vmScaleZ, 0.0f, 5.0f, 1.0f);
        this.vmRotX = clampFloat(other.vmRotX, -180.0f, 180.0f, 0.0f);
        this.vmRotY = clampFloat(other.vmRotY, -180.0f, 180.0f, 0.0f);
        this.vmRotZ = clampFloat(other.vmRotZ, -180.0f, 180.0f, 0.0f);
        this.vmSwingX = clampFloat(other.vmSwingX, 0.0f, 2.0f, 1.0f);
        this.vmSwingY = clampFloat(other.vmSwingY, 0.0f, 2.0f, 1.0f);
        this.vmSwingZ = clampFloat(other.vmSwingZ, 0.0f, 2.0f, 1.0f);
        this.tntTimer = other.tntTimer;
        this.trueSight = other.trueSight;
        this.itemEsp = other.itemEsp;
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
        this.packetMine = other.packetMine;
        this.packetMineRebreak = other.packetMineRebreak;
        this.packetMineDelay = Math.max(0, Math.min(5, other.packetMineDelay));
        this.packetMineRender = other.packetMineRender;
        this.chunkBorders = other.chunkBorders;
        this.chunkBordersSubGrid = other.chunkBordersSubGrid;
        this.lightOverlay = other.lightOverlay;
        this.lightOverlayRange = Math.max(8, Math.min(24, other.lightOverlayRange));
        this.lightOverlayOnlyAlways = other.lightOverlayOnlyAlways;
        this.trajectories = other.trajectories;
        this.trajectoriesShowPassive = other.trajectoriesShowPassive;
        this.trajectoriesBoxOutline = other.trajectoriesBoxOutline;
        this.configVersion = other.configVersion;
        this.clientChatEnabled = other.clientChatEnabled;
        this.clientChatUrl = SestiaChatProtocol.normalizeWebSocketUrl(other.clientChatUrl);
        this.clientChatAllowDms = other.clientChatAllowDms;
        this.clientChatMuted = other.clientChatMuted != null ? other.clientChatMuted : "";
        this.targetProtocol = other.targetProtocol;
        this.targetVersionName = other.targetVersionName != null ? other.targetVersionName : "Auto";
        this.autoBreakEnabled = other.autoBreakEnabled;
        this.autoBreakOnlyTool = other.autoBreakOnlyTool;
        this.autoCrafterEnabled = other.autoCrafterEnabled;
        this.autoCrafterDelay = other.autoCrafterDelay;
        this.autoCrafterCloseWhenDone = other.autoCrafterCloseWhenDone;
        this.freecamEnabled = other.freecamEnabled;
        this.freecamSpeed = other.freecamSpeed;
        this.freecamScrollSpeed = other.freecamScrollSpeed;
        this.freecamToggleOnDamage = other.freecamToggleOnDamage;
        this.freecamAllowInteract = other.freecamAllowInteract;
        this.freecamRotate = other.freecamRotate;
        this.wingsEnabled = other.wingsEnabled;
        this.wingsMode = WingsLogic.clampMode(other.wingsMode);
        this.wingsColorPreset = WingsLogic.clampColorPreset(other.wingsColorPreset);
        this.wingsCustomRgb = WingsLogic.clampRgb(other.wingsCustomRgb);
        this.wingsCustomHue = clampFloat(other.wingsCustomHue, 0.0f, 1.0f, 0.528f);
        this.wingsCustomSat = clampFloat(other.wingsCustomSat, 0.0f, 1.0f, 1.0f);
        this.wingsCustomVal = clampFloat(other.wingsCustomVal, 0.10f, 1.0f, 1.0f);
        this.wingsLength = WingsLogic.clampLength(other.wingsLength);
        this.wingsWidth = WingsLogic.clampWidth(other.wingsWidth);
        this.wingsLinesCount = WingsLogic.clampLinesCount(other.wingsLinesCount);
        this.wingsFadeStart = WingsLogic.clampFadeStart(other.wingsFadeStart);
        this.wingsHeight = WingsLogic.clampHeight(other.wingsHeight);
        this.wingsFlapSpeed = WingsLogic.clampFlapSpeed(other.wingsFlapSpeed);
        this.wingsShowDamage = other.wingsShowDamage;
        this.xrayEnabled = other.xrayEnabled;
        this.xrayOpacity = XrayHandler.clampOpacity(other.xrayOpacity);
        this.xrayOpacityDefaultMigrated = other.xrayOpacityDefaultMigrated;
        this.xrayExposedOnly = other.xrayExposedOnly;
        this.xrayBlocks = other.xrayBlocks != null ? new java.util.LinkedHashSet<>(other.xrayBlocks) : XrayHandler.createDefaultBlocks();
        XrayHandler.invalidateBlockCache();
        this.blockEspEnabled = other.blockEspEnabled;
        this.blockEspRange = clampInt(other.blockEspRange, 16, 160, 64);
        this.blockEspDefaultStyle = other.blockEspDefaultStyle != null
                ? other.blockEspDefaultStyle.copy()
                : new BlockEspHandler.BlockEspStyle();
        this.blockEspBlocks = other.blockEspBlocks != null
                ? new java.util.LinkedHashSet<>(other.blockEspBlocks)
                : XrayHandler.createDefaultBlockEspBlocks();
        this.blockEspPerBlockStyles = new HashMap<>();
        if (other.blockEspPerBlockStyles != null) {
            for (Map.Entry<String, BlockEspHandler.BlockEspStyle> entry : other.blockEspPerBlockStyles.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    this.blockEspPerBlockStyles.put(entry.getKey(), entry.getValue().copy());
                }
            }
        }
        this.autoCrafterTargets = new HashSet<>();
        if (other.autoCrafterTargets != null) {
            this.autoCrafterTargets.addAll(other.autoCrafterTargets);
        }
        this.autoSellEnabled = other.autoSellEnabled;
        this.autoSellDelay = other.autoSellDelay;
        this.autoSellSafetyPause = other.autoSellSafetyPause;
        this.autoSellOnlySelected = other.autoSellOnlySelected;
        this.autoSellTargets = new HashSet<>();
        if (other.autoSellTargets != null) {
            this.autoSellTargets.addAll(other.autoSellTargets);
        }
        this.autoDropEnabled = other.autoDropEnabled;
        this.autoDropDelay = other.autoDropDelay;
        this.autoDropExcludeHotbar = other.autoDropExcludeHotbar;
        this.autoDropTargets = new HashSet<>();
        if (other.autoDropTargets != null) {
            this.autoDropTargets.addAll(other.autoDropTargets);
        }
        this.accountPasswords = other.accountPasswords != null ? new HashMap<>(other.accountPasswords) : new HashMap<>();
        this.newVelocityEnabled = other.newVelocityEnabled;
        this.newVelocityMode = other.newVelocityMode;
        this.newVelocityHorizontal = other.newVelocityHorizontal;
        this.newVelocityVertical = other.newVelocityVertical;
        this.newVelocityChance = other.newVelocityChance;
        this.newVelocityExplosions = other.newVelocityExplosions;
        this.noStrip = other.noStrip;
        this.noStripAllowSneak = other.noStripAllowSneak;
        this.betterPlace = other.betterPlace;
        this.betterPlaceRange = other.betterPlaceRange;
        this.multiTask = other.multiTask;
        this.wheatFarmEnabled = other.wheatFarmEnabled;
        this.wheatFarmPos1Set = other.wheatFarmPos1Set;
        this.wheatFarmX1 = other.wheatFarmX1;
        this.wheatFarmY1 = other.wheatFarmY1;
        this.wheatFarmZ1 = other.wheatFarmZ1;
        this.wheatFarmPos2Set = other.wheatFarmPos2Set;
        this.wheatFarmX2 = other.wheatFarmX2;
        this.wheatFarmY2 = other.wheatFarmY2;
        this.wheatFarmZ2 = other.wheatFarmZ2;
        this.wheatFarmWalk = other.wheatFarmWalk;
        this.wheatFarmFly = other.wheatFarmFly;
        this.wheatFarmStickWand = other.wheatFarmStickWand;
        this.wheatFarmFailsafe = other.wheatFarmFailsafe;
        this.wheatFarmFlyMigrated = other.wheatFarmFlyMigrated;
        this.wheatFarmSafeMigrated = other.wheatFarmSafeMigrated;
        this.wheatFarmWhitelist = new HashSet<>();
        if (other.wheatFarmWhitelist != null) {
            this.wheatFarmWhitelist.addAll(other.wheatFarmWhitelist);
        }
        this.wheatFarmDelay = other.wheatFarmDelay;
        this.wheatFarmTurnSpeed = other.wheatFarmTurnSpeed;
        this.autoFishEnabled = other.autoFishEnabled;
        this.autoFishMigrated = other.autoFishMigrated;
        this.autoFishRecast = other.autoFishRecast;
        this.autoFishClickDelay = other.autoFishClickDelay;
        this.autoFishRecastDelay = other.autoFishRecastDelay;
        this.autoSpawnerEnabled = other.autoSpawnerEnabled;
        this.autoSpawnerCount = clampInt(other.autoSpawnerCount, 0, 256, 0);
        this.autoSpawnerSearchRadius = clampInt(other.autoSpawnerSearchRadius, 12, 40, 26);
        this.autoSpawnerClickDelay = clampInt(other.autoSpawnerClickDelay, 2, 8, 3);
        this.autoSpawnerLockMouse = other.autoSpawnerLockMouse;
        this.autoSpawnerRenderEsp = other.autoSpawnerRenderEsp;
        this.autoSpawnerHudX = other.autoSpawnerHudX;
        this.autoSpawnerHudY = other.autoSpawnerHudY;
        this.autoSpawnerHudDetailed = other.autoSpawnerHudDetailed;
        this.autoSpawnerEspDetail = clampInt(other.autoSpawnerEspDetail, 0, 2, 2);
        this.autoSpawnerReachBatch = other.autoSpawnerReachBatch;
        this.autoSpawnerLegitMode = other.autoSpawnerLegitMode;
        this.autoSpawnerSafetyEnabled = other.autoSpawnerSafetyEnabled;
        this.autoSpawnerSafetyRadius = clampInt(other.autoSpawnerSafetyRadius, 6, 48, 16);
        this.autoSpawnerSafetyAction = clampInt(other.autoSpawnerSafetyAction, 0, 2, 1);
        this.autoSpawnerSafetyResumeSec = clampInt(other.autoSpawnerSafetyResumeSec, 5, 120, 15);
        this.autoSpawnerTpsAdaptive = other.autoSpawnerTpsAdaptive;
        this.autoSpawnerAutoResume = other.autoSpawnerAutoResume;
        this.spawnerAutoSellEnabled = other.spawnerAutoSellEnabled;
        this.spawnerAutoSellTargetGoldBlock = other.spawnerAutoSellTargetGoldBlock;
        this.spawnerAutoSellSearchRadius = clampInt(other.spawnerAutoSellSearchRadius, 12, 40, 26);
        this.spawnerAutoSellLockMouse = other.spawnerAutoSellLockMouse;
        this.spawnerAutoSellRenderEsp = other.spawnerAutoSellRenderEsp;
        this.spawnerAutoSellHudX = other.spawnerAutoSellHudX;
        this.spawnerAutoSellHudY = other.spawnerAutoSellHudY;
        this.noPauseOnLostFocus = other.noPauseOnLostFocus;
        this.itemSuckerEnabled = other.itemSuckerEnabled;
        this.itemSuckerMode = other.itemSuckerMode;
        this.itemSuckerRange = other.itemSuckerRange;
        this.itemSuckerSpeed = other.itemSuckerSpeed;
        this.itemSuckerOnlyPickable = other.itemSuckerOnlyPickable;
        this.itemSuckerOnlyOnGround = other.itemSuckerOnlyOnGround;
        this.guiTheme = other.guiTheme;
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
        this.favoriteModules = new HashSet<>();
        if (other.favoriteModules != null) {
            this.favoriteModules.addAll(other.favoriteModules);
        }
        this.customFolders = new ArrayList<>();
        if (other.customFolders != null) {
            for (CustomModuleFolder f : other.customFolders) {
                if (f == null || f.id == null || f.name == null) continue;
                CustomModuleFolder copy = new CustomModuleFolder();
                copy.id = f.id;
                copy.name = f.name;
                copy.modules = f.modules != null ? new HashSet<>(f.modules) : new HashSet<>();
                this.customFolders.add(copy);
            }
        }
    }

    public void sanitize() {
        this.itemScale = clampFloat(this.itemScale, 0.10f, 2.00f, 1.0f);
        this.playerScale = clampFloat(this.playerScale, 0.10f, 2.00f, 1.0f);
        if (this.invHudScale < 0.50f || this.invHudScale > 2.50f) this.invHudScale = 1.0f;
        if (this.zoomFactor < 1.5f || this.zoomFactor > 20.0f) this.zoomFactor = 4.0f;
        if (this.guiTheme < 0 || this.guiTheme >= JelloTheme.THEME_COUNT) this.guiTheme = 0;
        if (this == INSTANCE) {
            JelloTheme.applyTheme(this.guiTheme);
        }
        this.freecamSpeed = clampFloat(this.freecamSpeed, 0.2f, 5.0f, 1.0f);
        this.wingsMode = WingsLogic.clampMode(this.wingsMode);
        this.wingsColorPreset = WingsLogic.clampColorPreset(this.wingsColorPreset);
        this.wingsCustomRgb = WingsLogic.clampRgb(this.wingsCustomRgb);
        this.wingsCustomHue = clampFloat(this.wingsCustomHue, 0.0f, 1.0f, 0.528f);
        this.wingsCustomSat = clampFloat(this.wingsCustomSat, 0.0f, 1.0f, 1.0f);
        this.wingsCustomVal = clampFloat(this.wingsCustomVal, 0.10f, 1.0f, 1.0f);
        this.wingsLength = WingsLogic.clampLength(this.wingsLength);
        this.wingsWidth = WingsLogic.clampWidth(this.wingsWidth);
        this.wingsLinesCount = WingsLogic.clampLinesCount(this.wingsLinesCount);
        this.wingsFadeStart = WingsLogic.clampFadeStart(this.wingsFadeStart);
        this.wingsHeight = WingsLogic.clampHeight(this.wingsHeight);
        this.wingsFlapSpeed = WingsLogic.clampFlapSpeed(this.wingsFlapSpeed);
        if (this.autoCrafterDelay < 0 || this.autoCrafterDelay > 10) this.autoCrafterDelay = 0;
        if (this.autoCrafterTargets == null) {
            resetDefaultAutoCraftTargets();
        } else {
            // Strip legacy non-item group IDs so invisible groups cannot trigger unwanted crafts
            this.autoCrafterTargets.remove("raw_blocks");
            this.autoCrafterTargets.remove("nuggets");
            this.autoCrafterTargets.remove("decraft");
            this.autoCrafterTargets.remove("food_paper");
        }
        if (this.autoSellDelay < 1 || this.autoSellDelay > 60) this.autoSellDelay = 5;
        if (this.autoSellTargets == null) {
            this.autoSellTargets = new HashSet<>();
        }
        if (this.autoDropDelay < 0 || this.autoDropDelay > 20) this.autoDropDelay = 2;
        if (this.autoDropTargets == null) {
            this.autoDropTargets = new HashSet<>();
        }
        if (this.accountPasswords == null) this.accountPasswords = new HashMap<>();
        if (this.newVelocityMode < 0 || this.newVelocityMode > 2) this.newVelocityMode = 0;
        if (this.newVelocityHorizontal < 0 || this.newVelocityHorizontal > 100) this.newVelocityHorizontal = 0;
        if (this.newVelocityVertical < 0 || this.newVelocityVertical > 100) this.newVelocityVertical = 0;
        if (this.newVelocityChance < 0 || this.newVelocityChance > 100) this.newVelocityChance = 100;
        this.betterPlaceRange = clampFloat(this.betterPlaceRange, 2.0f, 6.0f, 4.5f);
        if (this.wheatFarmWhitelist == null) {
            this.wheatFarmWhitelist = new HashSet<>();
        }
        if (!this.wheatFarmFlyMigrated) {
            this.wheatFarmFly = true;
            this.wheatFarmWalk = true;
            this.wheatFarmFlyMigrated = true;
        }
        if (!this.wheatFarmSafeMigrated) {
            this.wheatFarmFly = true;
            this.wheatFarmWalk = true;
            this.wheatFarmDelay = 4;
            this.wheatFarmTurnSpeed = 16.0f;
            this.wheatFarmSafeMigrated = true;
        }
        if (this.wheatFarmDelay < 2 || this.wheatFarmDelay > 20) this.wheatFarmDelay = 4;
        this.wheatFarmTurnSpeed = clampFloat(this.wheatFarmTurnSpeed, 5.0f, 30.0f, 16.0f);
        if (!this.autoFishMigrated) {
            this.autoFishEnabled = true;
            this.autoFishRecast = true;
            this.autoFishClickDelay = 5;
            this.autoFishRecastDelay = 15;
            this.autoFishMigrated = true;
        }
        if (!this.chatMentionAlertDefaultOffMigrated) {
            this.chatMentionAlert = false;
            this.chatMentionAlertDefaultOffMigrated = true;
        }
        if (this.autoFishClickDelay < 1 || this.autoFishClickDelay > 15) this.autoFishClickDelay = 5;
        if (this.autoFishRecastDelay < 8 || this.autoFishRecastDelay > 40) this.autoFishRecastDelay = 15;
        this.autoSpawnerCount = clampInt(this.autoSpawnerCount, 0, 256, 0);
        this.autoSpawnerSearchRadius = clampInt(this.autoSpawnerSearchRadius, 12, 40, 26);
        this.autoSpawnerClickDelay = clampInt(this.autoSpawnerClickDelay, 2, 8, 3);
        if (this.autoSpawnerHudX < -1 || this.autoSpawnerHudX > 4000) this.autoSpawnerHudX = -1;
        if (this.autoSpawnerHudY < -1 || this.autoSpawnerHudY > 4000) this.autoSpawnerHudY = -1;
        this.autoSpawnerEspDetail = clampInt(this.autoSpawnerEspDetail, 0, 2, 2);
        this.autoSpawnerSafetyRadius = clampInt(this.autoSpawnerSafetyRadius, 6, 48, 16);
        this.autoSpawnerSafetyAction = clampInt(this.autoSpawnerSafetyAction, 0, 2, 1);
        this.autoSpawnerSafetyResumeSec = clampInt(this.autoSpawnerSafetyResumeSec, 5, 120, 15);
        this.spawnerAutoSellSearchRadius = clampInt(this.spawnerAutoSellSearchRadius, 12, 40, 26);
        if (this.spawnerAutoSellHudX < -1 || this.spawnerAutoSellHudX > 4000) this.spawnerAutoSellHudX = -1;
        if (this.spawnerAutoSellHudY < -1 || this.spawnerAutoSellHudY > 4000) this.spawnerAutoSellHudY = -1;
        if (this.itemSuckerMode < 0 || this.itemSuckerMode > 1) this.itemSuckerMode = 0;
        this.itemSuckerRange = clampFloat(this.itemSuckerRange, 2.0f, 16.0f, 6.0f);
        this.itemSuckerSpeed = clampFloat(this.itemSuckerSpeed, 0.15f, 1.00f, 0.35f);
        if (this.fastBreakSpeed < 0.5f || this.fastBreakSpeed > 5.0f) this.fastBreakSpeed = 1.15f;
        if (this.fastBreakMode < 0 || this.fastBreakMode > 1) this.fastBreakMode = 1;
        if (this.pingSpoofDelay < 50 || this.pingSpoofDelay > 1000) this.pingSpoofDelay = 150;
        if (this.noSlowdownMode < 0 || this.noSlowdownMode > 2) this.noSlowdownMode = 0;
        if (this.exploitGuardMaxParticles < 100 || this.exploitGuardMaxParticles > 2000) this.exploitGuardMaxParticles = 500;
        if (this.backtrackDelay < 40 || this.backtrackDelay > 300) this.backtrackDelay = 120;
        if (this.disablerMode < 0 || this.disablerMode > 1) this.disablerMode = 0;
        if (this.packetMineDelay < 0 || this.packetMineDelay > 5) this.packetMineDelay = 1;
        if (this.lightOverlayRange < 8 || this.lightOverlayRange > 24) this.lightOverlayRange = 16;
        this.xrayOpacity = XrayHandler.clampOpacity(this.xrayOpacity);
        if (!this.xrayOpacityDefaultMigrated) {
            if (this.xrayOpacity == 128 || this.xrayOpacity == 255) {
                this.xrayOpacity = 0;
            }
            this.xrayOpacityDefaultMigrated = true;
        }
        if (this.xrayBlocks == null) this.xrayBlocks = XrayHandler.createDefaultBlocks();
        this.blockEspRange = clampInt(this.blockEspRange, 16, 160, 64);
        if (this.blockEspDefaultStyle == null) this.blockEspDefaultStyle = new BlockEspHandler.BlockEspStyle();
        if (this.blockEspBlocks == null) this.blockEspBlocks = XrayHandler.createDefaultBlockEspBlocks();
        if (this.blockEspPerBlockStyles == null) this.blockEspPerBlockStyles = new HashMap<>();
        if (this.targetVersionName == null) this.targetVersionName = "Auto";
        this.clientChatUrl = SestiaChatProtocol.normalizeWebSocketUrl(this.clientChatUrl);
        if (this.clientChatMuted == null) this.clientChatMuted = "";
        if (this.macros == null) this.macros = new ArrayList<>();
        if (this.categoryX == null) this.categoryX = new HashMap<>();
        if (this.categoryY == null) this.categoryY = new HashMap<>();
        if (this.categoryExpanded == null) this.categoryExpanded = new HashMap<>();
        if (this.moduleKeybinds == null) this.moduleKeybinds = new HashMap<>();
        if (this.moduleKeybindIsMouse == null) this.moduleKeybindIsMouse = new HashMap<>();
        if (this.favoriteModules == null) this.favoriteModules = new HashSet<>();
        if (this.customFolders == null) this.customFolders = new ArrayList<>();
        migrateModuleName("Spawner Auto Sell", "Spawner Mode");
        if (this.keybindsHudX < 0 || this.keybindsHudX > 3000) this.keybindsHudX = 8;
        if (this.keybindsHudY < 0 || this.keybindsHudY > 3000) this.keybindsHudY = 60;
        if (this.playerListAlign != 1) this.playerListAlign = 0;
        this.playerListMaxRows = clampInt(this.playerListMaxRows, 4, 40, 20);
        if (this.playerListX < -1 || this.playerListX > 4000) this.playerListX = -1;
        if (this.playerListY < -1 || this.playerListY > 4000) this.playerListY = -1;
        if (this.statsHudX < 0 || this.statsHudX > 4000) this.statsHudX = 4;
        if (this.statsHudY < 0 || this.statsHudY > 4000) this.statsHudY = 4;
        if (this.posHudX < -1 || this.posHudX > 4000) this.posHudX = -1;
        if (this.posHudY < -1 || this.posHudY > 4000) this.posHudY = -1;
        if (this.clockHudX < -1 || this.clockHudX > 4000) this.clockHudX = -1;
        this.itemNotifierDuration = clampFloat(this.itemNotifierDuration, 1.0f, 10.0f, 3.0f);
        if (this.itemNotifierMaxItems < 1 || this.itemNotifierMaxItems > 10) this.itemNotifierMaxItems = 5;
        if (this.itemNotifierX < -1 || this.itemNotifierX > 4000) this.itemNotifierX = -1;
        if (this.itemNotifierY < -1 || this.itemNotifierY > 4000) this.itemNotifierY = -1;
        if (this.clockHudY < -1 || this.clockHudY > 4000) this.clockHudY = -1;
        if (this.hudWatermarkX < 0 || this.hudWatermarkX > 4000) this.hudWatermarkX = 4;
        if (this.hudWatermarkY < 0 || this.hudWatermarkY > 4000) this.hudWatermarkY = 4;
        if (this.hudArrayListX < -1 || this.hudArrayListX > 4000) this.hudArrayListX = -1;
        if (this.hudArrayListY < -1 || this.hudArrayListY > 4000) this.hudArrayListY = -1;
        this.vmSwingSpeed = clampInt(this.vmSwingSpeed, 0, 100, 0);
        this.vmOffsetX = clampFloat(this.vmOffsetX, -2.0f, 2.0f, 0.0f);
        this.vmOffsetY = clampFloat(this.vmOffsetY, -2.0f, 2.0f, 0.0f);
        this.vmOffsetZ = clampFloat(this.vmOffsetZ, -2.0f, 2.0f, 0.0f);
        this.vmScaleX = clampFloat(this.vmScaleX, 0.0f, 5.0f, 1.0f);
        this.vmScaleY = clampFloat(this.vmScaleY, 0.0f, 5.0f, 1.0f);
        this.vmScaleZ = clampFloat(this.vmScaleZ, 0.0f, 5.0f, 1.0f);
        this.vmRotX = clampFloat(this.vmRotX, -180.0f, 180.0f, 0.0f);
        this.vmRotY = clampFloat(this.vmRotY, -180.0f, 180.0f, 0.0f);
        this.vmRotZ = clampFloat(this.vmRotZ, -180.0f, 180.0f, 0.0f);
        this.vmSwingX = clampFloat(this.vmSwingX, 0.0f, 2.0f, 1.0f);
        this.vmSwingY = clampFloat(this.vmSwingY, 0.0f, 2.0f, 1.0f);
        this.vmSwingZ = clampFloat(this.vmSwingZ, 0.0f, 2.0f, 1.0f);
    }

    private void migrateModuleName(String oldName, String newName) {
        if (this.moduleKeybinds.containsKey(oldName)) {
            Integer oldKey = this.moduleKeybinds.remove(oldName);
            if (oldKey != null) {
                this.moduleKeybinds.putIfAbsent(newName, oldKey);
            }
        }
        if (this.moduleKeybindIsMouse.containsKey(oldName)) {
            Boolean oldMouse = this.moduleKeybindIsMouse.remove(oldName);
            if (oldMouse != null) {
                this.moduleKeybindIsMouse.putIfAbsent(newName, oldMouse);
            }
        }
        if (this.favoriteModules.remove(oldName)) {
            this.favoriteModules.add(newName);
        }
        for (CustomModuleFolder folder : this.customFolders) {
            if (folder != null && folder.modules != null && folder.modules.remove(oldName)) {
                if (!folder.modules.contains(newName)) {
                    folder.modules.add(newName);
                }
            }
        }
    }

    private static float clampFloat(float value, float min, float max, float fallback) {
        if (!Float.isFinite(value)) return fallback;
        return Math.max(min, Math.min(max, value));
    }

    private static int clampInt(int value, int min, int max, int fallback) {
        return (value < min || value > max) ? fallback : value;
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
                    this.sanitize();
                    this.autoSpawnerEnabled = false;
                    this.spawnerAutoSellEnabled = false;
                    if (this.configVersion < 2 && this.moduleKeybinds.isEmpty()) {
                        this.moduleKeybinds.put("Fullbright", 66); // B
                        this.moduleKeybindIsMouse.put("Fullbright", false);
                        this.keybindsHudEnabled = true;
                    }
                    if (this.configVersion < 3) {
                        // Zoom uses vanilla KeyMapping ("key.customclient.zoom"), remove legacy orphan bind
                        this.moduleKeybinds.remove("Zoom");
                        this.moduleKeybindIsMouse.remove("Zoom");
                    }
                    this.clientChatEnabled = true;
                    if (this.configVersion < 3) {
                        this.configVersion = 3;
                        save();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            this.configVersion = 3;
            save();
        }
    }

    public void save() {
        if (PanicHandler.isPanicActive()) {
            return;
        }
        XrayHandler.invalidateBlockCache();
        SestiaChatClient.broadcastWingsUpdate();
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
