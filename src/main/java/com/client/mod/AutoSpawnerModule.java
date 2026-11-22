package com.client.mod;

import com.client.mod.SpawnerPathfinder.SpotCandidate;
import com.client.mod.SpawnerRoomScanner.SpawnerEnclosure;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.RecipeDisplayId;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.text.Normalizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tick-based Auto-Spawner routine:
 * Scan room -> S-Snake spawners -> Pull iron_ingot -> /craft iron_block -> Deposit chests -> Repeat.
 */
public class AutoSpawnerModule {

    public enum State {
        SCANNING_ROOM,
        MOVE_TO_SPAWNER,
        INTERACT_SPAWNER,
        WAIT_MANAGEMENT_GUI,
        CLICK_OPEN_DEPO,
        WAIT_DEPO_GUI,
        LOOT_IRON_INGOTS,
        SEND_CRAFT_CMD,
        WAIT_CRAFT_GUI,
        CRAFT_SPLIT_AND_FILL,
        FIND_NEXT_CHEST,
        MOVE_TO_CHEST,
        INTERACT_CHEST,
        WAIT_CHEST_GUI,
        DEPOSIT_IRON_BLOCKS,
        COOLDOWN_NEXT_PASS,
        PAUSED_SAFETY
    }

    private record DepositSample(long timestampMs, int blocks) {
    }

    private static final class SessionCheckpoint {
        final BlockPos homePos;
        final List<BlockPos> orderedSpawners;
        final Set<BlockPos> openedSpawners;
        final List<BlockPos> scannedChests;
        final Set<BlockPos> fullSpawnerChests;
        final Map<BlockPos, Integer> chestDepositCounts;
        final int chestCycleCursor;
        final int totalDepositedBlocks;
        final int passCount;
        final long elapsedBeforeDisconnectMs;
        final long savedAtMs;

        SessionCheckpoint(
                BlockPos homePos,
                List<BlockPos> orderedSpawners,
                Set<BlockPos> openedSpawners,
                List<BlockPos> scannedChests,
                Set<BlockPos> fullSpawnerChests,
                Map<BlockPos, Integer> chestDepositCounts,
                int chestCycleCursor,
                int totalDepositedBlocks,
                int passCount,
                long elapsedBeforeDisconnectMs,
                long savedAtMs
        ) {
            this.homePos = homePos.immutable();
            this.orderedSpawners = new ArrayList<>(orderedSpawners);
            this.openedSpawners = new HashSet<>(openedSpawners);
            this.scannedChests = new ArrayList<>(scannedChests);
            this.fullSpawnerChests = new HashSet<>(fullSpawnerChests);
            this.chestDepositCounts = new HashMap<>(chestDepositCounts);
            this.chestCycleCursor = Math.max(0, chestCycleCursor);
            this.totalDepositedBlocks = totalDepositedBlocks;
            this.passCount = passCount;
            this.elapsedBeforeDisconnectMs = elapsedBeforeDisconnectMs;
            this.savedAtMs = savedAtMs;
        }
    }

    private static final AutoSpawnerModule INSTANCE = new AutoSpawnerModule();
    private static final Pattern DEPO_PAGE_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");
    private static final long RATE_WINDOW_MS = 15L * 60_000L;
    private static final int MAX_DEPOSIT_SAMPLES = 2048;
    private static final long CHECKPOINT_TTL_MS = 5L * 60_000L;
    private static final double CHECKPOINT_MAX_DIST_SQ = 48.0 * 48.0;
    private static final int CHECKPOINT_WAIT_TICKS = 240;
    private static final double DEFAULT_AVG_SEC_PER_SPAWNER = 2.5;
    private static final double MAX_SPAWNER_BATCH_REACH = 4.25;
    private static final int MAX_REACH_BATCH_SIZE = 4;

    public static AutoSpawnerModule getInstance() {
        return INSTANCE;
    }

    // --- Settings synced from ModConfig / ModernClickGuiScreen ---
    public boolean enabled = false;
    public int spawnerCount = 0;        // 0 = Auto (all spawners in room cluster), max 256
    public int searchRadius = 26;       // fallback 36 if 0 found
    public int clickDelayTicks = 3;     // ~150ms between GUI clicks
    public boolean lockMouse = true;    // Lock physical mouse while enabled & no GUI open
    public boolean renderEsp = true;    // Render active spawner, path, and full chests

    // --- Runtime State Machine Fields ---
    private State state = State.SCANNING_ROOM;
    private int waitTicks = 0;
    private int stateTimeoutTicks = 0;

    private SpawnerEnclosure enclosure = null;
    private BlockPos homePos = null;
    private List<BlockPos> spawners = new ArrayList<>();
    private List<BlockPos> spawnersView = Collections.unmodifiableList(spawners);
    private int spawnerIndex = 0;
    private int spawnerAttempt = 1;
    private int passNumber = 0;
    private int lastScanCount = -1;
    private boolean waitingToFinishCurrentSpawner = false;
    private BlockPos pendingGreenSpawner = null;

    // Movement & Pathing Helper
    private final SpawnerNavHelper nav = new SpawnerNavHelper();
    private final ArrayDeque<BlockPos> reachBatchQueue = new ArrayDeque<>();
    private final Set<BlockPos> batchProcessedSpawners = new HashSet<>();
    private final Set<BlockPos> quarantinedSpawners = new HashSet<>();

    // Safety Guard State
    private String safetyPauseReason = "";
    private boolean safetyCritical = false;
    private float lastSafetyHealth = -1.0f;
    private State preSafetyState = null;
    private final SpawnerSafetyGuard safetyGuard = new SpawnerSafetyGuard();

    // Camera Restore, Sub-Face Aim & GUI Tracking
    private final Random interactRandom = new Random();
    private BlockPos interactAimTarget = null;
    private Direction interactAimFace = null;
    private Vec3 interactAimPoint = null;
    private int interactRaycastMissTicks = 0;
    private Float savedYaw = null;
    private Float savedPitch = null;
    private int lastManagementContainerId = -1;
    private int lootSafetyClicks = 0;
    private int lootLastClickedSlot = -1;
    private int lootLastSlotCount = -1;
    private boolean lootTriedNormalClick = false;
    private int lootWaitingForPageFrom = -1;
    private int lootPageClickContainerId = -1;
    private int lootNextPageTimeout = 0;
    private boolean lootWaitingForPageContent = false;
    private int lootContentWaitTicks = 0;

    // Chest Deposit & Economy Tracking
    private final Set<BlockPos> fullSpawnerChests = new HashSet<>();
    private final Set<BlockPos> fullSpawnerChestsView = Collections.unmodifiableSet(fullSpawnerChests);
    private final Map<BlockPos, Integer> chestDepositCounts = new HashMap<>();
    private final Map<BlockPos, Integer> chestDepositCountsView = Collections.unmodifiableMap(chestDepositCounts);
    private final Set<BlockPos> openedSpawners = new HashSet<>();
    private final Set<BlockPos> openedSpawnersView = Collections.unmodifiableSet(openedSpawners);
    private List<BlockPos> scannedChests = new ArrayList<>();
    private List<BlockPos> scannedChestsView = Collections.unmodifiableList(scannedChests);
    private List<BlockPos> candidateChests = new ArrayList<>();
    private int chestIndex = 0;
    private int chestCycleCursor = 0;
    private int chestDepositSlotIdx = -1;
    private int chestPrevSlotCount = -1;
    private boolean chestPreferAltSpot = false;
    private int totalDepositedBlocks = 0;
    private long enabledStartMs = 0L;
    private long passStartMs = 0L;
    private long lastPassDurationMs = 0L;
    private final Deque<DepositSample> depositSamples = new ArrayDeque<>();

    // Adaptive TPS/Ping per-tick snapshot
    private float currentTps = 20.0f;
    private int currentPingMs = 0;
    private boolean tpsAdaptiveEnabled = true;

    // Reconnect Resume Checkpoint
    private SessionCheckpoint savedCheckpoint = null;
    private boolean pendingCheckpointResume = false;
    private int checkpointResumeWaitTicks = 0;
    private boolean suppressNextLeftWorldCheckpoint = false;
    private boolean chatPaused = false;

    private static final class HudRowCache {
        boolean valid = false;
        boolean active = false;
        boolean detailed = true;
        boolean showBar = true;
        int accentHover = 0;
        State state = State.SCANNING_ROOM;
        boolean chatPaused = false;
        String safetyReason = "";
        boolean safetyCritical = false;
        int passNumber = 0;
        int openedCount = 0;
        int spawnerIndex = 0;
        int spawnerTotal = 0;
        int depositedBlocks = 0;
        int rawFullChestsSize = 0;
        int totalChests = 0;
        long wallSecond = Long.MIN_VALUE;
        long elapsedSec = -1L;
        List<JelloTheme.HudStatRow> rows = List.of();

        void invalidate() {
            this.valid = false;
            this.rows = List.of();
        }
    }

    private static final HudRowCache PREVIEW_HUD_CACHE = new HudRowCache();
    private final HudRowCache activeHudCache = new HudRowCache();

    private AutoSpawnerModule() {
    }

    public boolean isEnabled() {
        return enabled && !PanicHandler.isPanicActive();
    }

    public boolean isSafetyPaused() {
        return state == State.PAUSED_SAFETY;
    }

    public boolean isChatPaused() {
        return chatPaused;
    }

    boolean isActivelyCrafting() {
        return enabled && !PanicHandler.isPanicActive()
                && (state == State.WAIT_CRAFT_GUI || state == State.CRAFT_SPLIT_AND_FILL);
    }

    public static boolean isChatScreen(Screen screen) {
        return screen instanceof ChatScreen;
    }

    private static boolean isChatScreenOpen(Minecraft mc) {
        return isChatScreen(getCurrentScreen(mc));
    }

    boolean updateChatPauseState(boolean chatOpen) {
        if (chatOpen) {
            this.chatPaused = true;
            clearMovementFlags();
            return true;
        }
        if (this.chatPaused) {
            this.chatPaused = false;
            clearInteractAimState();
            this.waitTicks = Math.max(this.waitTicks, 2);
        }
        return false;
    }

    public boolean isActiveLockingMouse() {
        Minecraft mc = Minecraft.getInstance();
        if (state == State.PAUSED_SAFETY || chatPaused || isChatScreenOpen(mc)) {
            return false;
        }
        ModConfig cfg = ModConfig.getInstance();
        boolean activeLock = cfg != null ? (cfg.autoSpawnerEnabled && cfg.autoSpawnerLockMouse) : (enabled && lockMouse);
        return enabled && activeLock && !PanicHandler.isPanicActive()
                && mc != null && mc.player != null && getCurrentScreen(mc) == null;
    }

    public boolean isControllingMovement() {
        Minecraft mc = Minecraft.getInstance();
        if (state == State.PAUSED_SAFETY || chatPaused || isChatScreenOpen(mc)) {
            return false;
        }
        ModConfig cfg = ModConfig.getInstance();
        boolean cfgEnabled = cfg == null || cfg.autoSpawnerEnabled;
        return enabled && cfgEnabled && !PanicHandler.isPanicActive()
                && mc != null && mc.player != null && mc.level != null;
    }

    public boolean wantsMoveForward() {
        return nav.moveForward;
    }

    public boolean wantsMoveBackward() {
        return nav.moveBackward;
    }

    public boolean wantsMoveLeft() {
        return nav.moveLeft;
    }

    public boolean wantsMoveRight() {
        return nav.moveRight;
    }

    public boolean wantsJump() {
        return nav.wantJump;
    }

    public boolean wantsSprint() {
        return nav.wantSprint;
    }

    public List<BlockPos> getActivePath() {
        return nav.activePath;
    }

    public BlockPos getCurrentNavTarget() {
        return nav.currentNavTarget;
    }

    public int getSpawnerIndex() {
        return spawnerIndex;
    }

    public List<BlockPos> getOrderedSpawners() {
        return spawnersView;
    }

    public SpawnerEnclosure getEnclosure() {
        return enclosure;
    }

    public List<BlockPos> getScannedChests() {
        return scannedChestsView;
    }

    public Map<BlockPos, Integer> getChestDepositCounts() {
        return chestDepositCountsView;
    }

    public Set<BlockPos> getFullSpawnerChests() {
        return fullSpawnerChestsView;
    }

    public List<BlockPos> getScannedSpawners() {
        return spawnersView;
    }

    public Set<BlockPos> getOpenedSpawners() {
        return openedSpawnersView;
    }

    public void setEnabledFromConfig(boolean val) {
        clearCheckpoint();
        if (val && !PanicHandler.isPanicActive()) {
            ModConfig cfg = ModConfig.getInstance();
            if (cfg != null && cfg.spawnerAutoSellEnabled) {
                cfg.spawnerAutoSellEnabled = false;
                SpawnerAutoSellModule.getInstance().setEnabledFromConfig(false);
            }
            if (!this.enabled) {
                onEnable();
            }
        } else {
            if (this.enabled) {
                onDisable();
            } else {
                clearMovementFlags();
            }
        }
    }

    public void onLeftWorld() {
        ModConfig cfg = ModConfig.getInstance();
        boolean shouldSaveCheckpoint = this.enabled
                && !suppressNextLeftWorldCheckpoint
                && !PanicHandler.isPanicActive()
                && cfg != null
                && cfg.autoSpawnerAutoResume
                && !cfg.spawnerAutoSellEnabled
                && this.homePos != null
                && !this.spawners.isEmpty();
        suppressNextLeftWorldCheckpoint = false;
        SessionCheckpoint captured = shouldSaveCheckpoint ? captureCheckpoint() : null;

        if (cfg != null && cfg.autoSpawnerEnabled) {
            cfg.autoSpawnerEnabled = false;
            cfg.save();
        }
        if (this.enabled) {
            onDisable();
        } else {
            clearMovementFlags();
            resetAllRuntimeState();
        }
        this.savedCheckpoint = captured;
        this.pendingCheckpointResume = false;
        this.checkpointResumeWaitTicks = 0;
    }

    public void onEnteredWorld() {
        if (savedCheckpoint == null) {
            pendingCheckpointResume = false;
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (PanicHandler.isPanicActive()
                || cfg == null
                || !cfg.autoSpawnerAutoResume
                || cfg.spawnerAutoSellEnabled
                || isCheckpointExpired(savedCheckpoint)) {
            clearCheckpoint();
            return;
        }
        pendingCheckpointResume = true;
        checkpointResumeWaitTicks = 0;
        tryRestorePendingCheckpoint(Minecraft.getInstance(), cfg);
    }

    public void onServerPositionSync(Vec3 before, Vec3 after) {
        if (!enabled || before == null || after == null) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        SpawnerSafetyGuard.MoveAnomaly anomaly = SpawnerSafetyGuard.classifyServerMove(
                after.x - before.x,
                after.y - before.y,
                after.z - before.z
        );
        if (anomaly == SpawnerSafetyGuard.MoveAnomaly.TELEPORT) {
            if (cfg != null && cfg.autoSpawnerSafetyEnabled) {
                Minecraft mc = Minecraft.getInstance();
                closeCurrentContainerIfOpen(mc);
                clearMovementFlags();
                nav.activePath = null;
                clearCheckpoint();
                SpawnerSafetyGuard.notifyDesktopSafety(
                        "Sestia - Auto Spawner Safety",
                        "Unexpected server teleport detected"
                );
                if (cfg.autoSpawnerSafetyAction == 2) {
                    disconnectForSafety(mc, cfg, "Auto Spawner Safety: Unexpected teleport detected");
                } else {
                    enterSafetyPause(mc, "Teleported", true);
                }
            }
        } else if (anomaly == SpawnerSafetyGuard.MoveAnomaly.RUBBERBAND) {
            if (state != State.PAUSED_SAFETY) {
                nav.onServerSetback(before, after);
            }
        }
    }

    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        enabled = true;
        clearMovementFlags();
        resetAllRuntimeState();
        SpawnerSafetyGuard.clearPendingTextAlert();
        enabledStartMs = System.currentTimeMillis();
        nav.setLegitMode(cfg == null || cfg.autoSpawnerLegitMode);
        if (mc != null && mc.player != null) {
            savedYaw = mc.player.getYRot();
            savedPitch = mc.player.getXRot();
            lastSafetyHealth = mc.player.getHealth();
        }
        state = State.SCANNING_ROOM;
    }

    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        boolean legit = cfg == null || cfg.autoSpawnerLegitMode;
        enabled = false;
        clearMovementFlags();
        if (mc != null && mc.player != null) {
            closeCurrentContainerIfOpen(mc);
            if (!legit && savedYaw != null && savedPitch != null) {
                mc.player.setYRot(savedYaw);
                mc.player.setXRot(savedPitch);
            }
        }
        SpawnerSafetyGuard.clearPendingTextAlert();
        resetAllRuntimeState();
    }

    private void syncSettingsFromConfig() {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null) return;
        boolean targetEnabled = cfg.autoSpawnerEnabled && !PanicHandler.isPanicActive();
        if (targetEnabled != this.enabled) {
            setEnabledFromConfig(targetEnabled);
        }
        this.spawnerCount = Mth.clamp(cfg.autoSpawnerCount, 0, 256);
        this.searchRadius = Mth.clamp(cfg.autoSpawnerSearchRadius, 12, 40);
        this.clickDelayTicks = Mth.clamp(cfg.autoSpawnerClickDelay, 2, 8);
        this.lockMouse = cfg.autoSpawnerLockMouse;
        this.renderEsp = cfg.autoSpawnerRenderEsp;
        this.tpsAdaptiveEnabled = cfg.autoSpawnerTpsAdaptive;
        nav.setLegitMode(cfg.autoSpawnerLegitMode);
    }

    private void resetAllRuntimeState() {
        state = State.SCANNING_ROOM;
        waitTicks = 0;
        stateTimeoutTicks = 0;
        enclosure = null;
        homePos = null;
        spawners = new ArrayList<>();
        spawnersView = Collections.unmodifiableList(spawners);
        spawnerIndex = 0;
        spawnerAttempt = 1;
        passNumber = 0;
        lastScanCount = -1;
        waitingToFinishCurrentSpawner = false;
        pendingGreenSpawner = null;
        nav.resetAll(4.25);
        reachBatchQueue.clear();
        batchProcessedSpawners.clear();
        quarantinedSpawners.clear();
        safetyPauseReason = "";
        safetyCritical = false;
        lastSafetyHealth = -1.0f;
        preSafetyState = null;
        safetyGuard.reset();
        clearInteractAimState();
        savedYaw = null;
        savedPitch = null;
        lootSafetyClicks = 0;
        lootLastClickedSlot = -1;
        lootLastSlotCount = -1;
        lootTriedNormalClick = false;
        lootWaitingForPageFrom = -1;
        lootPageClickContainerId = -1;
        lootNextPageTimeout = 0;
        lootWaitingForPageContent = false;
        lootContentWaitTicks = 0;
        scannedChests = new ArrayList<>();
        scannedChestsView = Collections.unmodifiableList(scannedChests);
        candidateChests = new ArrayList<>();
        chestIndex = 0;
        chestCycleCursor = 0;
        chestDepositSlotIdx = -1;
        chestPrevSlotCount = -1;
        chestPreferAltSpot = false;
        totalDepositedBlocks = 0;
        enabledStartMs = 0L;
        passStartMs = 0L;
        lastPassDurationMs = 0L;
        depositSamples.clear();
        fullSpawnerChests.clear();
        chestDepositCounts.clear();
        openedSpawners.clear();
        chatPaused = false;
        activeHudCache.invalidate();
    }

    private void clearMovementFlags() {
        nav.clearMovementFlags();
    }

    private void clearInteractAimState() {
        interactAimTarget = null;
        interactAimFace = null;
        interactAimPoint = null;
        interactRaycastMissTicks = 0;
    }

    private void clearCheckpoint() {
        savedCheckpoint = null;
        pendingCheckpointResume = false;
        checkpointResumeWaitTicks = 0;
    }

    private static boolean isCheckpointExpired(SessionCheckpoint cp) {
        return cp == null || (System.currentTimeMillis() - cp.savedAtMs) > CHECKPOINT_TTL_MS;
    }

    private SessionCheckpoint captureCheckpoint() {
        long elapsed = enabledStartMs > 0L ? Math.max(0L, System.currentTimeMillis() - enabledStartMs) : 0L;
        return new SessionCheckpoint(
                homePos,
                spawners,
                openedSpawners,
                scannedChests,
                fullSpawnerChests,
                chestDepositCounts,
                chestCycleCursor,
                totalDepositedBlocks,
                passNumber,
                elapsed,
                System.currentTimeMillis()
        );
    }

    private void tryRestorePendingCheckpoint(Minecraft mc, ModConfig cfg) {
        if (!pendingCheckpointResume || savedCheckpoint == null) {
            return;
        }
        if (PanicHandler.isPanicActive()
                || cfg == null
                || !cfg.autoSpawnerAutoResume
                || cfg.spawnerAutoSellEnabled
                || isCheckpointExpired(savedCheckpoint)) {
            clearCheckpoint();
            return;
        }
        if (mc == null || mc.player == null || mc.level == null) {
            checkpointResumeWaitTicks++;
            if (checkpointResumeWaitTicks > CHECKPOINT_WAIT_TICKS) {
                clearCheckpoint();
            }
            return;
        }
        double distSq = mc.player.blockPosition().distSqr(savedCheckpoint.homePos);
        if (distSq > CHECKPOINT_MAX_DIST_SQ) {
            checkpointResumeWaitTicks++;
            if (checkpointResumeWaitTicks > CHECKPOINT_WAIT_TICKS) {
                clearCheckpoint();
            }
            return;
        }
        // Give newly joined chunks a brief settle window so blocks are populated before scanning/pathing
        if (checkpointResumeWaitTicks < 10) {
            checkpointResumeWaitTicks++;
            return;
        }
        restoreFromCheckpoint(mc, cfg, savedCheckpoint);
    }

    private void restoreFromCheckpoint(Minecraft mc, ModConfig cfg, SessionCheckpoint cp) {
        clearCheckpoint();
        resetAllRuntimeState();
        SpawnerSafetyGuard.clearPendingTextAlert();

        this.enabled = true;
        cfg.autoSpawnerEnabled = true;
        cfg.save();

        this.homePos = cp.homePos;
        this.spawners = new ArrayList<>(cp.orderedSpawners);
        this.spawnersView = Collections.unmodifiableList(this.spawners);
        this.openedSpawners.addAll(cp.openedSpawners);
        this.scannedChests = new ArrayList<>(cp.scannedChests);
        this.scannedChestsView = Collections.unmodifiableList(this.scannedChests);
        this.fullSpawnerChests.addAll(cp.fullSpawnerChests);
        this.chestDepositCounts.putAll(cp.chestDepositCounts);
        this.chestCycleCursor = this.scannedChests.isEmpty() ? 0 : Math.floorMod(cp.chestCycleCursor, this.scannedChests.size());
        this.totalDepositedBlocks = cp.totalDepositedBlocks;
        this.passNumber = Math.max(1, cp.passCount);

        long now = System.currentTimeMillis();
        this.enabledStartMs = now - Math.max(0L, cp.elapsedBeforeDisconnectMs);
        this.passStartMs = now;
        this.lastSafetyHealth = mc.player.getHealth();
        this.savedYaw = mc.player.getYRot();
        this.savedPitch = mc.player.getXRot();

        this.tpsAdaptiveEnabled = cfg.autoSpawnerTpsAdaptive;
        nav.setLegitMode(cfg.autoSpawnerLegitMode);
        this.enclosure = SpawnerRoomScanner.computeSpawnerEnclosure(
                mc.level, mc.player, this.spawners, this.homePos, null
        );

        int idx = 0;
        while (idx < this.spawners.size() && this.openedSpawners.contains(this.spawners.get(idx))) {
            idx++;
        }
        this.spawnerIndex = idx;
        this.spawnerAttempt = 1;

        if (countPlayerInventoryItem(mc.player, Items.IRON_INGOT) >= 9) {
            this.state = State.SEND_CRAFT_CMD;
        } else if (countPlayerInventoryItem(mc.player, Items.IRON_BLOCK) > 0) {
            this.candidateChests = List.of();
            this.chestIndex = 0;
            this.state = State.FIND_NEXT_CHEST;
        } else if (this.spawnerIndex < this.spawners.size()) {
            prepareSpawnerNavigation(mc.level, mc.player);
        } else {
            enterNextPassCooldown(scaleTicks(7));
        }
    }

    private static Screen getCurrentScreen(Minecraft mc) {
        return mc != null && mc.gui != null ? mc.gui.screen() : null;
    }

    private static void closeCurrentContainerIfOpen(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return;
        }
        if (getCurrentScreen(mc) != null
                || (mc.player.containerMenu != null && mc.player.containerMenu != mc.player.inventoryMenu)) {
            mc.player.closeContainer();
        }
    }

    private static int getPlayerPingMs(Minecraft mc) {
        if (mc == null || mc.player == null) {
            return 0;
        }
        ClientPacketListener conn = mc.getConnection();
        if (conn == null) {
            return 0;
        }
        PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
        return info != null ? Math.max(0, info.getLatency()) : 0;
    }

    private int scaleTicks(int baseTicks) {
        return StatsHudRenderer.scaleAdaptiveTicks(baseTicks, currentTps, currentPingMs, tpsAdaptiveEnabled);
    }

    private void enterSafetyPause(Minecraft mc, String reason, boolean critical) {
        if (this.state != State.PAUSED_SAFETY) {
            this.preSafetyState = this.state;
        }
        this.safetyPauseReason = reason != null ? reason : "Safety alert";
        this.safetyCritical = critical;
        this.chatPaused = false;
        this.state = State.PAUSED_SAFETY;
        this.waitTicks = 0;
        this.stateTimeoutTicks = 0;
        this.safetyGuard.reset();
        clearMovementFlags();
        clearInteractAimState();
        nav.activePath = null;
        reachBatchQueue.clear();
        closeCurrentContainerIfOpen(mc);
    }

    private void disconnectForSafety(Minecraft mc, ModConfig cfg, String reason) {
        suppressNextLeftWorldCheckpoint = true;
        clearCheckpoint();
        if (cfg != null && cfg.autoSpawnerEnabled) {
            cfg.autoSpawnerEnabled = false;
            cfg.save();
        }
        if (this.enabled) {
            onDisable();
        } else {
            clearMovementFlags();
            resetAllRuntimeState();
        }
        if (mc != null && mc.getConnection() != null && mc.getConnection().getConnection() != null) {
            mc.getConnection().getConnection().disconnect(Component.literal(reason));
        }
    }

    private void resumeFromSafetyPause(Minecraft mc, ClientLevel level, LocalPlayer player) {
        State resumeFrom = this.preSafetyState;
        this.preSafetyState = null;
        this.safetyPauseReason = "";
        this.safetyCritical = false;
        this.safetyGuard.reset();
        this.waitTicks = 0;
        this.stateTimeoutTicks = 0;
        this.chestPrevSlotCount = -1;
        this.lastSafetyHealth = player.getHealth();
        clearMovementFlags();
        clearInteractAimState();
        nav.activePath = null;
        reachBatchQueue.clear();

        boolean farFromHome = this.homePos != null && player.blockPosition().distSqr(this.homePos) > CHECKPOINT_MAX_DIST_SQ;
        if (farFromHome || this.spawners.isEmpty() || resumeFrom == null
                || resumeFrom == State.SCANNING_ROOM || resumeFrom == State.COOLDOWN_NEXT_PASS) {
            this.state = State.SCANNING_ROOM;
            return;
        }

        switch (resumeFrom) {
            case SEND_CRAFT_CMD, WAIT_CRAFT_GUI, CRAFT_SPLIT_AND_FILL -> {
                if (countPlayerInventoryItem(player, Items.IRON_INGOT) >= 9) {
                    this.state = State.SEND_CRAFT_CMD;
                } else {
                    afterCraftingCompleted(mc, player);
                }
            }
            case FIND_NEXT_CHEST, MOVE_TO_CHEST, INTERACT_CHEST, WAIT_CHEST_GUI, DEPOSIT_IRON_BLOCKS -> {
                if (countPlayerInventoryItem(player, Items.IRON_BLOCK) > 0) {
                    this.candidateChests = List.of();
                    this.chestIndex = 0;
                    this.state = State.FIND_NEXT_CHEST;
                } else {
                    prepareSpawnerNavigation(level, player);
                }
            }
            default -> {
                this.spawnerAttempt = 1;
                prepareSpawnerNavigation(level, player);
            }
        }
    }

    public void onClientTick() {
        syncSettingsFromConfig();
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        if (!enabled) {
            if (pendingCheckpointResume) {
                tryRestorePendingCheckpoint(mc, cfg);
            }
            return;
        }

        LocalPlayer player = mc != null ? mc.player : null;
        ClientLevel level = mc != null ? mc.level : null;
        MultiPlayerGameMode gameMode = mc != null ? mc.gameMode : null;
        if (player == null || level == null || gameMode == null) {
            clearMovementFlags();
            return;
        }

        this.currentTps = StatsHudRenderer.getTickRate();
        this.currentPingMs = getPlayerPingMs(mc);
        this.tpsAdaptiveEnabled = cfg == null || cfg.autoSpawnerTpsAdaptive;
        nav.setTpsDelayMultiplier(
                this.tpsAdaptiveEnabled
                        ? StatsHudRenderer.computeTpsDelayMultiplier(this.currentTps, this.currentPingMs)
                        : 1.0
        );

        if (cfg != null && cfg.autoSpawnerSafetyEnabled) {
            SpawnerSafetyGuard.SafetyAlert alert = safetyGuard.evaluate(mc, cfg, lastSafetyHealth);
            lastSafetyHealth = player.getHealth();
            if (state == State.PAUSED_SAFETY) {
                clearMovementFlags();
                if (alert.isTriggered()) {
                    safetyPauseReason = alert.reason();
                    safetyCritical = safetyCritical || alert.critical();
                    if (cfg.autoSpawnerSafetyAction == 2) {
                        SpawnerSafetyGuard.notifyDesktopSafety("Sestia - Auto Spawner Safety", alert.reason());
                        disconnectForSafety(mc, cfg, "Auto Spawner Safety: " + alert.reason());
                        return;
                    }
                }
                if (cfg.autoSpawnerSafetyAction == 1) {
                    if (safetyGuard.updateClearCooldown(!alert.isTriggered(), cfg.autoSpawnerSafetyResumeSec, System.currentTimeMillis())) {
                        resumeFromSafetyPause(mc, level, player);
                    }
                } else {
                    safetyGuard.updateClearCooldown(false, cfg.autoSpawnerSafetyResumeSec, System.currentTimeMillis());
                }
                return;
            } else if (alert.isTriggered()) {
                closeCurrentContainerIfOpen(mc);
                clearMovementFlags();
                nav.activePath = null;
                SpawnerSafetyGuard.notifyDesktopSafety("Sestia - Auto Spawner Safety", alert.reason());
                if (cfg.autoSpawnerSafetyAction == 2) {
                    disconnectForSafety(mc, cfg, "Auto Spawner Safety: " + alert.reason());
                    return;
                }
                enterSafetyPause(mc, alert.reason(), alert.critical());
                return;
            }
        } else {
            lastSafetyHealth = player.getHealth();
            if (state == State.PAUSED_SAFETY) {
                resumeFromSafetyPause(mc, level, player);
            }
        }

        // Pause cleanly when the chat screen is open, then resume from exact state when closed
        if (updateChatPauseState(isChatScreenOpen(mc))) {
            return;
        }

        // Strict InventoryMove prevention: never move while ANY GUI is open
        if (getCurrentScreen(mc) != null) {
            clearMovementFlags();
        }

        // Pause container clicks, timeouts, and movement cleanly while server packets are stalled
        if (StatsHudRenderer.isServerFrozen(3000L)) {
            clearMovementFlags();
            return;
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (state) {
            case SCANNING_ROOM -> tickScanningRoom(mc, level, player);
            case MOVE_TO_SPAWNER -> tickMoveToTarget(mc, level, player, true);
            case INTERACT_SPAWNER -> tickInteractSpawner(mc, level, player, gameMode);
            case WAIT_MANAGEMENT_GUI -> tickWaitManagementGui(mc, player);
            case CLICK_OPEN_DEPO -> tickClickOpenDepo(mc, player, gameMode);
            case WAIT_DEPO_GUI -> tickWaitDepoGui(mc, player);
            case LOOT_IRON_INGOTS -> tickLootIronIngots(mc, player, gameMode);
            case SEND_CRAFT_CMD -> tickSendCraftCmd(mc, player);
            case WAIT_CRAFT_GUI -> tickWaitCraftGui(mc, player);
            case CRAFT_SPLIT_AND_FILL -> tickWaitAutoCrafter(mc, player);
            case FIND_NEXT_CHEST -> tickFindNextChest(mc, level, player);
            case MOVE_TO_CHEST -> tickMoveToTarget(mc, level, player, false);
            case INTERACT_CHEST -> tickInteractChest(mc, level, player, gameMode);
            case WAIT_CHEST_GUI -> tickWaitChestGui(mc, player);
            case DEPOSIT_IRON_BLOCKS -> tickDepositIronBlocks(mc, player, gameMode);
            case COOLDOWN_NEXT_PASS -> {
                clearMovementFlags();
                state = State.SCANNING_ROOM;
            }
            case PAUSED_SAFETY -> clearMovementFlags();
        }
    }

    private void tickScanningRoom(Minecraft mc, ClientLevel level, LocalPlayer player) {
        clearMovementFlags();
        closeOpenScreenIfAny(mc, player);

        BlockPos rawHome = SpawnerRoomScanner.getSpawnerBotStandPos(level, player, enclosure);
        if (rawHome == null) rawHome = player.blockPosition();
        BlockPos anchor = (enclosure != null && enclosure.anchorPos != null) ? enclosure.anchorPos : rawHome;

        int maxCount = Math.max(0, Math.min(256, spawnerCount));
        List<BlockPos> found = SpawnerRoomScanner.findNearbySpawnerBlocks(level, anchor, searchRadius, maxCount, "");
        if (found.isEmpty()) {
            found = SpawnerRoomScanner.findNearbySpawnerBlocks(level, anchor, Math.max(searchRadius, 36), maxCount, "");
        }

        if (found.isEmpty()) {
            lastScanCount = -1;
            waitTicks = scaleTicks(40); // Retry in 2s if no spawner is in loaded chunks yet
            return;
        }

        // First scan all spawners and chests in the room before starting navigation
        this.spawners = found;
        this.spawnersView = Collections.unmodifiableList(this.spawners);
        this.enclosure = SpawnerRoomScanner.computeSpawnerEnclosure(level, player, found, anchor, enclosure);
        this.homePos = anchor;
        this.scannedChests = scanRoomChests(level, player, anchor, this.enclosure, anchor);
        this.scannedChestsView = Collections.unmodifiableList(this.scannedChests);

        if (lastScanCount != found.size()) {
            lastScanCount = found.size();
            waitTicks = scaleTicks(1);
            return;
        }

        lastScanCount = -1;
        passNumber++;
        if (passStartMs > 0L) {
            lastPassDurationMs = Math.max(1000L, System.currentTimeMillis() - passStartMs);
        }
        passStartMs = System.currentTimeMillis();
        this.openedSpawners.clear();
        this.batchProcessedSpawners.clear();
        this.quarantinedSpawners.clear();
        this.reachBatchQueue.clear();
        this.pendingGreenSpawner = null;
        this.spawnerIndex = 0;
        this.spawnerAttempt = 1;
        this.chestPreferAltSpot = false;
        nav.decayTilePenalties();
        nav.activePath = null;
        nav.activePathAllowsSpawnerFloor = false;

        prepareSpawnerNavigation(level, player);
    }

    private void prepareSpawnerNavigation(ClientLevel level, LocalPlayer player) {
        clearMovementFlags();
        clearInteractAimState();
        nav.activePathAllowsSpawnerFloor = true;

        while (spawnerIndex < spawners.size()) {
            BlockPos cand = spawners.get(spawnerIndex);
            if (quarantinedSpawners.contains(cand)
                    || batchProcessedSpawners.contains(cand)
                    || openedSpawners.contains(cand)) {
                spawnerIndex++;
            } else {
                break;
            }
        }

        if (spawnerIndex >= spawners.size() && reachBatchQueue.isEmpty()) {
            finishAllSpawnersCycle(player);
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        boolean useReachBatch = cfg == null || cfg.autoSpawnerReachBatch;
        boolean useAltSpot = spawnerAttempt >= 2 || nav.consumeAltSpotRequested();

        if (useReachBatch && spawnerAttempt == 1 && !useAltSpot) {
            while (!reachBatchQueue.isEmpty()) {
                BlockPos queued = reachBatchQueue.poll();
                if (queued == null
                        || openedSpawners.contains(queued)
                        || batchProcessedSpawners.contains(queued)
                        || quarantinedSpawners.contains(queued)) {
                    continue;
                }
                if (canReachAndRaySpawnerHere(level, player, queued)) {
                    nav.currentNavTarget = queued;
                    nav.stopHorizReach = 2.65;
                    nav.stopEyeReach = 4.35;
                    state = State.INTERACT_SPAWNER;
                    return;
                }
            }

            if (spawnerIndex < spawners.size()) {
                BlockPos primary = spawners.get(spawnerIndex);
                List<BlockPos> rawBatch = SpawnerPathfinder.collectReachableBatch(
                        player.blockPosition(), spawners, spawnerIndex, openedSpawners, MAX_SPAWNER_BATCH_REACH, 8
                );
                List<BlockPos> validBatch = new ArrayList<>(MAX_REACH_BATCH_SIZE);
                boolean includesPrimary = false;
                for (BlockPos cand : rawBatch) {
                    if (cand == null
                            || batchProcessedSpawners.contains(cand)
                            || quarantinedSpawners.contains(cand)) {
                        continue;
                    }
                    if (canReachAndRaySpawnerHere(level, player, cand)) {
                        if (cand.equals(primary)) {
                            includesPrimary = true;
                        }
                        validBatch.add(cand);
                        if (validBatch.size() >= MAX_REACH_BATCH_SIZE) {
                            break;
                        }
                    }
                }
                if (includesPrimary && !validBatch.isEmpty()) {
                    reachBatchQueue.clear();
                    for (BlockPos sp : validBatch) {
                        if (!sp.equals(primary)) {
                            reachBatchQueue.add(sp);
                        }
                    }
                    nav.currentNavTarget = primary;
                    nav.stopHorizReach = 2.65;
                    nav.stopEyeReach = 4.35;
                    state = State.INTERACT_SPAWNER;
                    return;
                }
            }
        } else {
            reachBatchQueue.clear();
        }

        if (spawnerIndex >= spawners.size()) {
            finishAllSpawnersCycle(player);
            return;
        }

        BlockPos targetSpawner = spawners.get(spawnerIndex);
        nav.currentNavTarget = targetSpawner;
        nav.stopHorizReach = 2.65;
        nav.stopEyeReach = 4.35;

        if (SpawnerNavHelper.isAlreadyInOptimalReach(level, player, targetSpawner, true, useAltSpot, enclosure)) {
            state = State.INTERACT_SPAWNER;
            return;
        }

        List<BlockPos> selectedPath = nav.selectSpawnerPath(level, player, targetSpawner, enclosure, useAltSpot);
        if (selectedPath == null || selectedPath.isEmpty()) {
            if (!useAltSpot && SpawnerNavHelper.canFallbackInteractDirectly(player, targetSpawner)) {
                state = State.INTERACT_SPAWNER;
            } else {
                advanceSpawnerAttemptOrSkip(level, player);
            }
            return;
        }

        nav.startPathWalk(player, selectedPath, Math.max(100, Math.min(360, (selectedPath.size() + 3) * 11)));
        state = State.MOVE_TO_SPAWNER;
    }

    private static boolean canReachAndRaySpawnerHere(ClientLevel level, LocalPlayer player, BlockPos sp) {
        if (level == null || player == null || sp == null) {
            return false;
        }
        Vec3 pos = player.position();
        boolean onTop = SpawnerNavHelper.isStandingOnTopOfTarget(pos, sp);
        if (!onTop && SpawnerNavHelper.isSameColumn(pos, sp)) {
            return false;
        }
        Vec3 eye = player.getEyePosition();
        if (eye.distanceTo(Vec3.atCenterOf(sp)) > MAX_SPAWNER_BATCH_REACH) {
            return false;
        }
        Direction face = SpawnerPathfinder.getExposedSpawnerClickFace(level, player, sp);
        Vec3 faceCenter = Vec3.atCenterOf(sp).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        if (SpawnerPathfinder.hasClearClipRaycast(level, eye, faceCenter, sp)) {
            return true;
        }
        BlockPos stand = SpawnerRoomScanner.getSpawnerBotStandPos(level, player, null);
        return stand != null && SpawnerPathfinder.hasClearLineOfSightToSpawnerOrChest(level, stand, sp);
    }

    private void tickMoveToTarget(Minecraft mc, ClientLevel level, LocalPlayer player, boolean isSpawner) {
        if (getCurrentScreen(mc) != null) {
            closeOpenScreenIfAny(mc, player);
            clearMovementFlags();
            waitTicks = scaleTicks(2);
            return;
        }
        if (nav.currentNavTarget == null) {
            clearMovementFlags();
            state = isSpawner ? State.SCANNING_ROOM : State.FIND_NEXT_CHEST;
            return;
        }

        switch (nav.tickWalk(level, player, enclosure, isSpawner)) {
            case UNSTUCK_RESTART -> {
                if (isSpawner) {
                    prepareSpawnerNavigation(level, player);
                } else {
                    if (nav.consumeAltSpotRequested()) {
                        chestPreferAltSpot = true;
                    }
                    state = State.FIND_NEXT_CHEST;
                }
            }
            case REACHED -> {
                clearInteractAimState();
                state = isSpawner ? State.INTERACT_SPAWNER : State.INTERACT_CHEST;
            }
            case ABORTED -> {
                boolean quarantine = nav.consumeQuarantineRequested();
                if (isSpawner) {
                    if (quarantine && nav.currentNavTarget != null) {
                        quarantineCurrentSpawner(level, player);
                    } else {
                        advanceSpawnerAttemptOrSkip(level, player);
                    }
                } else {
                    chestPreferAltSpot = false;
                    chestIndex++;
                    state = State.FIND_NEXT_CHEST;
                }
            }
            case CONTINUE -> {
                if (isSpawner) {
                    ModConfig cfg = ModConfig.getInstance();
                    if ((cfg == null || cfg.autoSpawnerLegitMode)
                            && nav.activePath != null
                            && nav.activePath.size() > 1
                            && player.getXRot() <= 48.0f) {
                        Vec3 targetCenter = Vec3.atCenterOf(nav.currentNavTarget);
                        double destHoriz = Math.hypot(targetCenter.x - player.getX(), targetCenter.z - player.getZ());
                        if (destHoriz <= 3.5) {
                            Vec3 eye = player.getEyePosition();
                            Vec3 lookAhead = nav.getLookAheadTarget(eye, targetCenter);
                            double ldx = lookAhead.x - eye.x;
                            double ldz = lookAhead.z - eye.z;
                            if (Math.hypot(ldx, ldz) > 0.35) {
                                float destYaw = (float) (Math.toDegrees(Math.atan2(ldz, ldx)) - 90.0);
                                applyHumanRotation(player, destYaw, 18.0f, true);
                            }
                        }
                    }
                }
            }
        }
    }

    private Vec3 computeBlockInteractPoint(BlockPos target, Direction face, boolean legitMode) {
        if (legitMode) {
            return SpawnerPathfinder.pickGaussianSubFacePoint(target, face, interactRandom);
        }
        return Vec3.atCenterOf(target).add(
                face.getStepX() * 0.5,
                face.getStepY() * 0.5,
                face.getStepZ() * 0.5
        );
    }

    private void tickInteractSpawner(
            Minecraft mc,
            ClientLevel level,
            LocalPlayer player,
            MultiPlayerGameMode gameMode
    ) {
        clearMovementFlags();
        if (getCurrentScreen(mc) != null) {
            closeOpenScreenIfAny(mc, player);
            waitTicks = scaleTicks(4);
            return;
        }
        if (nav.currentNavTarget == null) {
            state = State.SCANNING_ROOM;
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        boolean legitMode = cfg == null || cfg.autoSpawnerLegitMode;
        Direction clickFace = SpawnerPathfinder.getExposedSpawnerClickFace(level, player, nav.currentNavTarget);
        if (interactAimPoint == null
                || !nav.currentNavTarget.equals(interactAimTarget)
                || clickFace != interactAimFace) {
            interactAimTarget = nav.currentNavTarget.immutable();
            interactAimFace = clickFace;
            interactAimPoint = computeBlockInteractPoint(nav.currentNavTarget, clickFace, legitMode);
            interactRaycastMissTicks = 0;
        }

        if (!SpawnerPathfinder.hasClearClipRaycast(level, player.getEyePosition(), interactAimPoint, nav.currentNavTarget)) {
            interactRaycastMissTicks++;
            if (interactRaycastMissTicks < 4) {
                interactAimPoint = interactRaycastMissTicks == 3
                        ? computeBlockInteractPoint(nav.currentNavTarget, clickFace, false)
                        : computeBlockInteractPoint(nav.currentNavTarget, clickFace, legitMode);
                return;
            }
            clearInteractAimState();
            reachBatchQueue.clear();
            stateTimeoutTicks = 0;
            advanceSpawnerAttemptOrSkip(level, player);
            return;
        }
        interactRaycastMissTicks = 0;

        boolean aimed = lookAtPointGcd(player, interactAimPoint);

        // Wait until smooth human aim converges + settle ticks before interact packet
        stateTimeoutTicks++;
        if ((!aimed && stateTimeoutTicks < scaleTicks(28)) || stateTimeoutTicks < scaleTicks(2)) {
            return;
        }
        Vec3 clickPoint = interactAimPoint;
        clearInteractAimState();
        stateTimeoutTicks = 0;

        BlockHitResult hitResult = new BlockHitResult(clickPoint, clickFace, nav.currentNavTarget, false);
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);

        waitTicks = scaleTicks(1);
        state = State.WAIT_MANAGEMENT_GUI;
    }

    private void tickWaitManagementGui(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        if (getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen) {
            String titleNorm = normalizeTurkishText(containerScreen.getTitle().getString());
            if (titleNorm.contains("depo")) {
                resetLootTrackingState();
                waitTicks = scaleTicks(1);
                state = State.LOOT_IRON_INGOTS;
                return;
            }
            waitTicks = scaleTicks(1);
            state = State.CLICK_OPEN_DEPO;
            return;
        }

        stateTimeoutTicks++;
        if (stateTimeoutTicks > scaleTicks(44)) { // ~2.2s timeout (TPS-scaled)
            stateTimeoutTicks = 0;
            advanceSpawnerAttemptOrSkip(mc.level, player);
        }
    }

    private void tickClickOpenDepo(Minecraft mc, LocalPlayer player, MultiPlayerGameMode gameMode) {
        clearMovementFlags();
        if (!(getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen)) {
            advanceSpawnerAttemptOrSkip(mc.level, player);
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        if (!menu.getCarried().isEmpty()) {
            if (tryReturnCarriedToPlayerInventory(menu, player, gameMode)) {
                waitTicks = scaleTicks(2);
                return;
            }
            closeOpenScreenIfAny(mc, player);
            advanceSpawnerAttemptOrSkip(mc.level, player);
            return;
        }

        int containerEnd = Math.max(0, menu.slots.size() - 36);
        int depoSlot = -1;

        for (int i = 0; i < containerEnd; i++) {
            ItemStack st = menu.getSlot(i).getItem();
            if (st.isEmpty()) continue;
            String itemPath = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().toLowerCase(Locale.ROOT);
            StringBuilder text = new StringBuilder();
            text.append(st.getHoverName().getString()).append(' ');
            Component customName = st.get(DataComponents.CUSTOM_NAME);
            if (customName != null) {
                text.append(customName.getString()).append(' ');
            }
            Component itemName = st.get(DataComponents.ITEM_NAME);
            if (itemName != null) {
                text.append(itemName.getString()).append(' ');
            }
            ItemLore lore = st.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    text.append(line.getString()).append(' ');
                }
            }
            String norm = normalizeTurkishText(text.toString());
            if (itemPath.contains("chest")
                    || norm.contains("depoyu ac")
                    || norm.contains("birikmis esyalari")
                    || norm.contains("depo")) {
                depoSlot = i;
                break;
            }
        }
        if (depoSlot == -1 && containerEnd > 10 && !menu.getSlot(10).getItem().isEmpty()) {
            depoSlot = 10;
        }

        if (depoSlot == -1) {
            closeOpenScreenIfAny(mc, player);
            advanceSpawnerAttemptOrSkip(mc.level, player);
            return;
        }

        lastManagementContainerId = menu.containerId;
        gameMode.handleContainerInput(menu.containerId, depoSlot, 0, ContainerInput.PICKUP, player);
        stateTimeoutTicks = 0;
        waitTicks = scaleTicks(1);
        state = State.WAIT_DEPO_GUI;
    }

    private void tickWaitDepoGui(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        if (getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen) {
            AbstractContainerMenu menu = containerScreen.getMenu();
            int invStart = Math.max(0, menu.slots.size() - 36);
            if (menu.containerId != lastManagementContainerId
                    && (hasAnyContainerItem(menu, invStart) || stateTimeoutTicks >= scaleTicks(8))) {
                resetLootTrackingState();
                waitTicks = scaleTicks(1);
                state = State.LOOT_IRON_INGOTS;
                return;
            }
        }
        stateTimeoutTicks++;
        if (stateTimeoutTicks > scaleTicks(60)) { // ~3.0s timeout (TPS-scaled)
            closeOpenScreenIfAny(mc, player);
            stateTimeoutTicks = 0;
            advanceSpawnerAttemptOrSkip(mc.level, player);
        }
    }

    private void resetLootTrackingState() {
        lootSafetyClicks = 0;
        lootLastClickedSlot = -1;
        lootLastSlotCount = -1;
        lootTriedNormalClick = false;
        lootWaitingForPageFrom = -1;
        lootPageClickContainerId = -1;
        lootNextPageTimeout = 0;
        lootWaitingForPageContent = false;
        lootContentWaitTicks = 0;
    }

    int resolveBurstClicksPerStep() {
        return clickDelayTicks <= 3 ? 4 : (clickDelayTicks <= 5 ? 2 : 1);
    }

    int resolveBurstStepWaitTicks() {
        return scaleTicks(clickDelayTicks <= 3 ? 1 : clickDelayTicks - 1);
    }

    private void tickLootIronIngots(Minecraft mc, LocalPlayer player, MultiPlayerGameMode gameMode) {
        clearMovementFlags();
        if (!(getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen)) {
            afterSpawnerLooted(mc, player, false);
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        int storageEnd = getStorageEndSlot(menu);
        int invStart = Math.max(0, menu.slots.size() - 36);
        int[] depoPage = parseDepoPageInfo(containerScreen.getTitle().getString());

        // Wait for server to send the next DEPO page after clicking SONRAKI SAYFA
        if (lootWaitingForPageFrom != -1) {
            boolean pageChanged = (depoPage != null && lootWaitingForPageFrom > 0 && depoPage[0] != lootWaitingForPageFrom)
                    || (lootPageClickContainerId != -1 && menu.containerId != lootPageClickContainerId);
            if (pageChanged) {
                lootWaitingForPageFrom = -1;
                lootPageClickContainerId = -1;
                lootNextPageTimeout = 0;
                lootSafetyClicks = 0;
                lootLastClickedSlot = -1;
                lootLastSlotCount = -1;
                lootTriedNormalClick = false;
                // Wait for ContainerSetContentPacket on the newly opened page before inspecting slots
                lootWaitingForPageContent = true;
                lootContentWaitTicks = 0;
                waitTicks = scaleTicks(1);
                return;
            } else {
                lootNextPageTimeout++;
                if (lootNextPageTimeout <= scaleTicks(50)) {
                    return;
                }
                lootWaitingForPageFrom = -1;
                lootPageClickContainerId = -1;
                lootNextPageTimeout = 0;
                closeOpenScreenIfAny(mc, player);
                waitTicks = scaleTicks(1);
                afterSpawnerLooted(mc, player, false);
                return;
            }
        }

        // Wait until the new page's container slots have arrived from the server
        if (lootWaitingForPageContent) {
            lootContentWaitTicks++;
            if (!hasAnyContainerItem(menu, invStart) && lootContentWaitTicks <= scaleTicks(18)) {
                return;
            }
            lootWaitingForPageContent = false;
            lootContentWaitTicks = 0;
            waitTicks = 0;
            return;
        }

        boolean hasExplicitNextButton = findNextDepoPageSlot(menu, storageEnd, invStart, false) != -1;
        boolean hasMoreDepoPages = depoPage != null ? depoPage[0] < depoPage[1] : hasExplicitNextButton;

        // Self-heal carried item desync: drop cursor stack back into a matching or empty player slot first
        if (!menu.getCarried().isEmpty()) {
            if (tryReturnCarriedToPlayerInventory(menu, player, gameMode)) {
                lootLastClickedSlot = -1;
                lootLastSlotCount = -1;
                lootTriedNormalClick = false;
                waitTicks = scaleTicks(1);
                return;
            }
            closeOpenScreenIfAny(mc, player);
            waitTicks = scaleTicks(1);
            afterSpawnerLooted(mc, player, hasMoreDepoPages || hasAnyIronIngotInStorage(menu, storageEnd));
            return;
        }

        // Verify whether the previous QUICK_MOVE actually moved the stack; fallback to normal PICKUP if blocked
        if (lootLastClickedSlot != -1 && lootLastClickedSlot < storageEnd) {
            ItemStack prevAfter = menu.getSlot(lootLastClickedSlot).getItem();
            boolean unchanged = !prevAfter.isEmpty()
                    && prevAfter.is(Items.IRON_INGOT)
                    && prevAfter.getCount() == lootLastSlotCount;
            if (unchanged) {
                if (!lootTriedNormalClick) {
                    lootTriedNormalClick = true;
                    gameMode.handleContainerInput(menu.containerId, lootLastClickedSlot, 0, ContainerInput.PICKUP, player);
                    waitTicks = scaleTicks(1);
                    return;
                } else {
                    boolean shouldStay = hasMoreDepoPages || countEmptyPlayerSlots(menu) <= 2;
                    closeOpenScreenIfAny(mc, player);
                    waitTicks = scaleTicks(1);
                    afterSpawnerLooted(mc, player, shouldStay);
                    return;
                }
            }
            lootLastClickedSlot = -1;
            lootLastSlotCount = -1;
            lootTriedNormalClick = false;
        }

        int maxBurst = resolveBurstClicksPerStep();
        int burstCount = 0;
        for (int s = 0; s < storageEnd && burstCount < maxBurst; s++) {
            ItemStack stack = menu.getSlot(s).getItem();
            if (stack.isEmpty() || !stack.is(Items.IRON_INGOT)) {
                continue;
            }
            if (lootSafetyClicks >= 55 || countEmptyPlayerSlots(menu) <= 1) {
                break;
            }
            int beforeCount = stack.getCount();
            lootSafetyClicks++;
            lootLastClickedSlot = s;
            lootLastSlotCount = beforeCount;
            lootTriedNormalClick = false;
            gameMode.handleContainerInput(menu.containerId, s, 0, ContainerInput.QUICK_MOVE, player);
            burstCount++;

            ItemStack afterNow = menu.getSlot(s).getItem();
            if (!afterNow.isEmpty() && afterNow.is(Items.IRON_INGOT) && afterNow.getCount() == beforeCount) {
                // Slot did not empty synchronously on client -> break so fallback or full-inv check runs next tick
                break;
            }
        }

        if (burstCount > 0) {
            waitTicks = resolveBurstStepWaitTicks();
            return;
        }

        if (lootSafetyClicks >= 55 || countEmptyPlayerSlots(menu) <= 1) {
            closeOpenScreenIfAny(mc, player);
            waitTicks = scaleTicks(1);
            afterSpawnerLooted(mc, player, true);
            return;
        }

        // Only finish the spawner when there are NO iron ingots left AND NO remaining depo pages
        if (!hasMoreDepoPages) {
            closeOpenScreenIfAny(mc, player);
            waitTicks = scaleTicks(1);
            afterSpawnerLooted(mc, player, false);
            return;
        }

        int nextPageSlot = findNextDepoPageSlot(menu, storageEnd, invStart, depoPage != null && depoPage[0] < depoPage[1]);
        if (nextPageSlot != -1) {
            lootWaitingForPageFrom = depoPage != null ? depoPage[0] : 0;
            lootPageClickContainerId = menu.containerId;
            lootNextPageTimeout = 0;
            lootLastClickedSlot = -1;
            lootLastSlotCount = -1;
            lootTriedNormalClick = false;
            gameMode.handleContainerInput(menu.containerId, nextPageSlot, 0, ContainerInput.PICKUP, player);
            waitTicks = scaleTicks(1);
            return;
        }
        closeOpenScreenIfAny(mc, player);
        waitTicks = scaleTicks(1);
        afterSpawnerLooted(mc, player, false);
    }

    private void afterSpawnerLooted(Minecraft mc, LocalPlayer player, boolean stayOnCurrentSpawner) {
        BlockPos curSp = nav.currentNavTarget != null
                ? nav.currentNavTarget.immutable()
                : ((spawnerIndex >= 0 && spawnerIndex < spawners.size()) ? spawners.get(spawnerIndex) : null);
        this.waitingToFinishCurrentSpawner = stayOnCurrentSpawner;
        if (stayOnCurrentSpawner) {
            // Stay on the same spawner until all pages & iron are looted
            spawnerAttempt = 1;
        } else {
            // Do NOT turn green yet: turn green only after crafting & depositing into chest
            if (curSp != null) {
                pendingGreenSpawner = curSp;
                batchProcessedSpawners.add(curSp);
            }
            if (curSp == null || (spawnerIndex < spawners.size() && curSp.equals(spawners.get(spawnerIndex)))) {
                spawnerIndex++;
            }
            while (spawnerIndex < spawners.size() && batchProcessedSpawners.contains(spawners.get(spawnerIndex))) {
                spawnerIndex++;
            }
            spawnerAttempt = 1;
        }

        int currentIngots = countPlayerInventoryItem(player, Items.IRON_INGOT);
        if (currentIngots >= 9) {
            reachBatchQueue.clear();
            state = State.SEND_CRAFT_CMD;
            return;
        }

        int blocksInInv = countPlayerInventoryItem(player, Items.IRON_BLOCK);
        if (blocksInInv > 0 && shouldDepositBlocksNow(player)) {
            reachBatchQueue.clear();
            candidateChests = List.of();
            chestIndex = 0;
            chestPreferAltSpot = false;
            state = State.FIND_NEXT_CHEST;
            return;
        }

        if (!waitingToFinishCurrentSpawner && pendingGreenSpawner != null) {
            openedSpawners.add(pendingGreenSpawner);
            pendingGreenSpawner = null;
        }
        prepareSpawnerNavigation(mc.level, player);
    }

    private void finishAllSpawnersCycle(LocalPlayer player) {
        int remIngots = countPlayerInventoryItem(player, Items.IRON_INGOT);
        if (remIngots >= 9) {
            reachBatchQueue.clear();
            state = State.SEND_CRAFT_CMD;
            return;
        }
        int remBlocks = countPlayerInventoryItem(player, Items.IRON_BLOCK);
        if (remBlocks > 0) {
            reachBatchQueue.clear();
            candidateChests = List.of();
            chestIndex = 0;
            chestPreferAltSpot = false;
            state = State.FIND_NEXT_CHEST;
            return;
        }
        enterNextPassCooldown(2); // 100ms before next full pass
    }

    private void tickSendCraftCmd(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        reachBatchQueue.clear();
        closeOpenScreenIfAny(mc, player);
        if (player.connection == null || countPlayerInventoryItem(player, Items.IRON_INGOT) < 9) {
            afterCraftingCompleted(mc, player);
            return;
        }
        player.connection.sendCommand("craft");
        stateTimeoutTicks = 0;
        waitTicks = 0;
        state = State.WAIT_CRAFT_GUI;
    }

    private void tickWaitCraftGui(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        if (getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen
                && containerScreen.getMenu() instanceof AbstractCraftingMenu craftingMenu
                && craftingMenu.slots.size() >= 46) {
            stateTimeoutTicks = 0;
            waitTicks = 0;
            state = State.CRAFT_SPLIT_AND_FILL;
            craftIronBlocksTurbo(mc, player, craftingMenu);
            return;
        }
        stateTimeoutTicks++;
        if (stateTimeoutTicks > scaleTicks(90)) { // 4.5s timeout (TPS-scaled)
            stateTimeoutTicks = 0;
            afterCraftingCompleted(mc, player);
        }
    }

    private void tickWaitAutoCrafter(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        if (!(getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen)) {
            stateTimeoutTicks = 0;
            afterCraftingCompleted(mc, player);
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        boolean didCraft = false;
        if (menu instanceof AbstractCraftingMenu craftingMenu) {
            didCraft = craftIronBlocksTurbo(mc, player, craftingMenu);
        }

        int invStart = Math.max(0, menu.slots.size() - 36);
        int invEnd = menu.slots.size();
        int remainingIngots = countIngotsInCraftingMenu(menu, invStart, invEnd);

        if (!didCraft && remainingIngots < 9 && !menu.slots.isEmpty() && menu.getSlot(0).getItem().isEmpty()) {
            closeOpenScreenIfAny(mc, player);
            stateTimeoutTicks = 0;
            waitTicks = scaleTicks(1);
            afterCraftingCompleted(mc, player);
            return;
        }

        stateTimeoutTicks++;
        if (stateTimeoutTicks > scaleTicks(100)) { // 5s safety timeout (TPS-scaled)
            closeOpenScreenIfAny(mc, player);
            stateTimeoutTicks = 0;
            waitTicks = scaleTicks(1);
            afterCraftingCompleted(mc, player);
        }
    }

    private static RecipeDisplayId findIronBlockRecipeId(
            Minecraft mc,
            LocalPlayer player,
            AbstractCraftingMenu craftingMenu
    ) {
        ClientRecipeBook recipeBook = player.getRecipeBook();
        if (recipeBook == null || mc.level == null) {
            return null;
        }
        StackedItemContents stackedContents = new StackedItemContents();
        player.getInventory().fillStackedContents(stackedContents);
        int gridSlots = craftingMenu.getGridWidth() * craftingMenu.getGridHeight();
        for (int i = 1; i <= gridSlots && i < craftingMenu.slots.size(); i++) {
            Slot slot = craftingMenu.getSlot(i);
            if (slot.hasItem()) {
                stackedContents.accountStack(slot.getItem());
            }
        }

        ContextMap context = SlotDisplayContext.fromLevel(mc.level);
        for (RecipeCollection collection : recipeBook.getCollections()) {
            collection.selectRecipes(stackedContents, d -> true);
            if (!collection.hasCraftable()) {
                continue;
            }
            for (RecipeDisplayEntry entry : collection.getRecipes()) {
                if (!collection.isCraftable(entry.id()) && !entry.canCraft(stackedContents)) {
                    continue;
                }
                if (entry.craftingRequirements().isPresent()) {
                    int reqCount = entry.craftingRequirements().get().size();
                    if (reqCount <= 1 || gridSlots < reqCount) {
                        continue;
                    }
                }
                ItemStack resultStack = entry.display().result().resolveForFirstStack(context);
                if (!resultStack.isEmpty() && resultStack.is(Items.IRON_BLOCK)) {
                    return entry.id();
                }
            }
        }
        return null;
    }

    private static boolean craftIronBlocksTurbo(
            Minecraft mc,
            LocalPlayer player,
            AbstractCraftingMenu craftingMenu
    ) {
        if (mc.gameMode == null || mc.level == null || craftingMenu.slots.isEmpty()) {
            return false;
        }
        boolean didAct = false;

        if (craftingMenu.getSlot(0).hasItem() && craftingMenu.getSlot(0).getItem().is(Items.IRON_BLOCK)) {
            mc.gameMode.handleContainerInput(craftingMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, player);
            didAct = true;
        }

        int invStart = Math.max(0, craftingMenu.slots.size() - 36);
        int availIngots = countIngotsInCraftingMenu(craftingMenu, invStart, craftingMenu.slots.size());
        if (availIngots >= 9) {
            RecipeDisplayId recipeId = findIronBlockRecipeId(mc, player, craftingMenu);
            if (recipeId != null) {
                int batches = Math.min(4, Math.max(1, (availIngots / 9 + 63) / 64));
                for (int b = 0; b < batches; b++) {
                    mc.gameMode.handlePlaceRecipe(craftingMenu.containerId, recipeId, true);
                    mc.gameMode.handleContainerInput(craftingMenu.containerId, 0, 0, ContainerInput.QUICK_MOVE, player);
                    didAct = true;
                }
            }
        }
        return didAct;
    }

    private boolean shouldDepositBlocksNow(LocalPlayer player) {
        if (waitingToFinishCurrentSpawner) {
            return countEmptyPlayerInventorySlots(player) <= 4;
        }
        return true;
    }

    private void flushPendingGreenSpawnerIfFinished() {
        if (!waitingToFinishCurrentSpawner && pendingGreenSpawner != null) {
            openedSpawners.add(pendingGreenSpawner);
            pendingGreenSpawner = null;
        }
    }

    private void afterCraftingCompleted(Minecraft mc, LocalPlayer player) {
        if (countPlayerInventoryItem(player, Items.IRON_BLOCK) > 0 && shouldDepositBlocksNow(player)) {
            candidateChests = List.of();
            chestIndex = 0;
            chestPreferAltSpot = false;
            state = State.FIND_NEXT_CHEST;
        } else {
            flushPendingGreenSpawnerIfFinished();
            if (spawnerIndex < spawners.size() || !reachBatchQueue.isEmpty()) {
                prepareSpawnerNavigation(mc.level, player);
            } else {
                enterNextPassCooldown(7);
            }
        }
    }

    private void tickFindNextChest(Minecraft mc, ClientLevel level, LocalPlayer player) {
        clearMovementFlags();
        clearInteractAimState();
        reachBatchQueue.clear();
        closeOpenScreenIfAny(mc, player);

        if (countPlayerInventoryItem(player, Items.IRON_BLOCK) <= 0) {
            if (spawnerIndex < spawners.size()) {
                prepareSpawnerNavigation(level, player);
            } else {
                enterNextPassCooldown(7);
            }
            return;
        }

        if (candidateChests == null || candidateChests.isEmpty() || chestIndex == 0) {
            BlockPos origin = homePos != null ? homePos : player.blockPosition();
            if (scannedChests == null || scannedChests.isEmpty()) {
                scannedChests = scanRoomChests(level, player, origin, enclosure, origin);
                scannedChestsView = Collections.unmodifiableList(scannedChests);
            }
            List<BlockPos> rawChests = new ArrayList<>(scannedChests);
            if (rawChests.isEmpty()) {
                List<BlockPos> fallback = SpawnerRoomScanner.findNearbyContainerPositions(
                        level, player.position(), origin, 26
                );
                rawChests = SpawnerRoomScanner.orderChestColumnsSequentially(fallback, origin);
                if (!rawChests.isEmpty()) {
                    scannedChests = new ArrayList<>(rawChests);
                    scannedChestsView = Collections.unmodifiableList(scannedChests);
                }
            }

            if (rawChests.stream().allMatch(fullSpawnerChests::contains)) {
                fullSpawnerChests.clear();
            }
            candidateChests = buildSequentialChestCandidates(rawChests, chestCycleCursor, fullSpawnerChests);
        }

        if (candidateChests.isEmpty() || chestIndex >= candidateChests.size()) {
            // No accessible chests or all scanned; move on so loop never deadlocks
            chestPreferAltSpot = false;
            flushPendingGreenSpawnerIfFinished();
            if (spawnerIndex < spawners.size()) {
                prepareSpawnerNavigation(level, player);
            } else {
                enterNextPassCooldown(20);
            }
            return;
        }

        BlockPos chestPos = candidateChests.get(chestIndex);
        boolean preferAlt = chestPreferAltSpot;
        chestPreferAltSpot = false;
        nav.currentNavTarget = chestPos;
        nav.activePathAllowsSpawnerFloor = true;
        nav.stopHorizReach = 3.85;
        nav.stopEyeReach = 4.35;

        if (SpawnerNavHelper.isAlreadyInOptimalReach(level, player, chestPos, false, preferAlt, enclosure)) {
            state = State.INTERACT_CHEST;
            return;
        }

        BlockPos startStand = SpawnerRoomScanner.getSpawnerBotStandPos(level, player, enclosure);
        List<SpotCandidate> spots = SpawnerPathfinder.findBestStandSpots(
                level, player, chestPos, enclosure, false, preferAlt, startStand
        );

        Map<BlockPos, Double> penalties = nav.getActiveTilePenalties();
        List<BlockPos> chosenPath = null;
        for (int i = 0; i < Math.min(spots.size(), 10); i++) {
            SpotCandidate cand = spots.get(i);
            List<BlockPos> p = SpawnerPathfinder.findSpawnerRoomPath(
                    level, startStand, cand.spot(), enclosure, 2000, true, penalties
            );
            if (p != null) {
                chosenPath = p.isEmpty() ? List.of(cand.spot()) : p;
                break;
            }
        }
        if (chosenPath == null || chosenPath.isEmpty()) {
            chestIndex++;
            return;
        }

        nav.startPathWalk(player, chosenPath, Math.max(100, Math.min(400, (chosenPath.size() + 2) * 9)));
        state = State.MOVE_TO_CHEST;
    }

    private void tickInteractChest(
            Minecraft mc,
            ClientLevel level,
            LocalPlayer player,
            MultiPlayerGameMode gameMode
    ) {
        clearMovementFlags();
        if (getCurrentScreen(mc) != null) {
            closeOpenScreenIfAny(mc, player);
            waitTicks = scaleTicks(1);
            return;
        }
        if (nav.currentNavTarget == null) {
            chestIndex++;
            state = State.FIND_NEXT_CHEST;
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        boolean legitMode = cfg == null || cfg.autoSpawnerLegitMode;
        Direction face = SpawnerPathfinder.getExposedSpawnerClickFace(level, player, nav.currentNavTarget);
        if (interactAimPoint == null
                || !nav.currentNavTarget.equals(interactAimTarget)
                || face != interactAimFace) {
            interactAimTarget = nav.currentNavTarget.immutable();
            interactAimFace = face;
            interactAimPoint = computeBlockInteractPoint(nav.currentNavTarget, face, legitMode);
            interactRaycastMissTicks = 0;
        }

        if (!SpawnerPathfinder.hasClearClipRaycast(level, player.getEyePosition(), interactAimPoint, nav.currentNavTarget)) {
            // Chest VoxelShape is inset by 1/16 and may sit directly above a hopper rim;
            // fall back to the upper-center of the chest rather than skipping the chest.
            interactAimPoint = new Vec3(
                    nav.currentNavTarget.getX() + 0.5,
                    nav.currentNavTarget.getY() + 0.58,
                    nav.currentNavTarget.getZ() + 0.5
            );
        }
        interactRaycastMissTicks = 0;

        boolean aimed = lookAtPointGcd(player, interactAimPoint);

        stateTimeoutTicks++;
        if ((!aimed && stateTimeoutTicks < scaleTicks(28)) || stateTimeoutTicks < scaleTicks(2)) {
            return;
        }
        Vec3 clickPoint = interactAimPoint;
        clearInteractAimState();
        stateTimeoutTicks = 0;

        BlockHitResult hit = new BlockHitResult(clickPoint, face, nav.currentNavTarget, false);
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);

        waitTicks = scaleTicks(1);
        state = State.WAIT_CHEST_GUI;
    }

    private void tickWaitChestGui(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        if (getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen) {
            if (isMinerChestWindow(containerScreen)) {
                closeOpenScreenIfAny(mc, player);
                chestIndex++;
                waitTicks = scaleTicks(1);
                state = State.FIND_NEXT_CHEST;
                return;
            }
            AbstractContainerMenu menu = containerScreen.getMenu();
            chestDepositSlotIdx = Math.max(0, menu.slots.size() - 36);
            chestPrevSlotCount = -1;
            waitTicks = scaleTicks(1);
            state = State.DEPOSIT_IRON_BLOCKS;
            return;
        }

        stateTimeoutTicks++;
        if (stateTimeoutTicks > scaleTicks(80)) { // ~4s timeout (TPS-scaled)
            stateTimeoutTicks = 0;
            chestIndex++;
            state = State.FIND_NEXT_CHEST;
        }
    }

    private void tickDepositIronBlocks(Minecraft mc, LocalPlayer player, MultiPlayerGameMode gameMode) {
        clearMovementFlags();
        if (!(getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen)) {
            chestIndex++;
            state = State.FIND_NEXT_CHEST;
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        int invEnd = menu.slots.size();

        // Self-heal carried item desync BEFORE inspecting previous click or flagging chest full
        if (!menu.getCarried().isEmpty()) {
            chestPrevSlotCount = -1;
            if (tryReturnCarriedToPlayerInventory(menu, player, gameMode)) {
                waitTicks = scaleTicks(1);
                return;
            }
            closeOpenScreenIfAny(mc, player);
            chestIndex++;
            waitTicks = scaleTicks(1);
            state = State.FIND_NEXT_CHEST;
            return;
        }

        // Check if the previous click failed to transfer (chest is full)
        if (chestPrevSlotCount != -1 && chestDepositSlotIdx < invEnd) {
            ItemStack prevAfter = menu.getSlot(chestDepositSlotIdx).getItem();
            if (!prevAfter.isEmpty() && prevAfter.getCount() == chestPrevSlotCount) {
                if (nav.currentNavTarget != null) {
                    fullSpawnerChests.add(nav.currentNavTarget.immutable());
                    BlockPos partner = SpawnerRoomScanner.getConnectedChestPos(mc.level, nav.currentNavTarget);
                    if (partner != null) {
                        fullSpawnerChests.add(partner);
                    }
                }
                closeOpenScreenIfAny(mc, player);
                chestIndex++;
                waitTicks = scaleTicks(1);
                state = State.FIND_NEXT_CHEST;
                return;
            }
            int remaining = prevAfter.isEmpty() ? 0 : prevAfter.getCount();
            if (chestPrevSlotCount > remaining) {
                int depositedNow = chestPrevSlotCount - remaining;
                totalDepositedBlocks += depositedNow;
                recordDepositedBlocks(depositedNow);
            }
            chestDepositSlotIdx++;
            chestPrevSlotCount = -1;
        }

        int maxBurst = resolveBurstClicksPerStep();
        int burstCount = 0;
        while (chestDepositSlotIdx < invEnd && burstCount < maxBurst) {
            ItemStack st = menu.getSlot(chestDepositSlotIdx).getItem();
            if (!st.isEmpty() && st.is(Items.IRON_BLOCK)) {
                int beforeCount = st.getCount();
                gameMode.handleContainerInput(menu.containerId, chestDepositSlotIdx, 0, ContainerInput.QUICK_MOVE, player);
                burstCount++;

                ItemStack afterNow = menu.getSlot(chestDepositSlotIdx).getItem();
                int remNow = afterNow.isEmpty() ? 0 : afterNow.getCount();
                if (remNow == beforeCount) {
                    // Upper chest slots are full -> leave chestPrevSlotCount set so next tick flags chest full
                    chestPrevSlotCount = beforeCount;
                    waitTicks = scaleTicks(1);
                    return;
                }
                int depositedNow = beforeCount - remNow;
                totalDepositedBlocks += depositedNow;
                recordDepositedBlocks(depositedNow);
                if (remNow > 0) {
                    // Partial transfer filled the last slot of the chest
                    chestPrevSlotCount = remNow;
                    waitTicks = scaleTicks(1);
                    return;
                }
                chestPrevSlotCount = -1;
                chestDepositSlotIdx++;
            } else {
                chestDepositSlotIdx++;
            }
        }

        if (chestDepositSlotIdx < invEnd) {
            waitTicks = resolveBurstStepWaitTicks();
            return;
        }

        // All iron_block stacks deposited from inventory -> advance sequential cursor for next spawner
        if (nav.currentNavTarget != null) {
            BlockPos chestKey = nav.currentNavTarget.immutable();
            chestDepositCounts.merge(chestKey, 1, Integer::sum);
            BlockPos partner = SpawnerRoomScanner.getConnectedChestPos(mc.level, nav.currentNavTarget);
            if (partner != null) {
                chestDepositCounts.merge(partner, 1, Integer::sum);
            }
        }
        if (scannedChests != null && !scannedChests.isEmpty()) {
            int depositedIdx = nav.currentNavTarget != null ? scannedChests.indexOf(nav.currentNavTarget) : -1;
            int baseIdx = depositedIdx >= 0 ? depositedIdx : chestCycleCursor;
            chestCycleCursor = Math.floorMod(baseIdx + 1, scannedChests.size());
        }
        closeOpenScreenIfAny(mc, player);
        waitTicks = scaleTicks(1);
        if (countPlayerInventoryItem(player, Items.IRON_BLOCK) <= 0) {
            if (!waitingToFinishCurrentSpawner && pendingGreenSpawner != null) {
                openedSpawners.add(pendingGreenSpawner);
                pendingGreenSpawner = null;
            }
            if (spawnerIndex < spawners.size() || !reachBatchQueue.isEmpty()) {
                prepareSpawnerNavigation(mc.level, player);
            } else {
                enterNextPassCooldown(2);
            }
        } else {
            chestIndex++;
            state = State.FIND_NEXT_CHEST;
        }
    }

    // --- Helpers ---

    private void enterNextPassCooldown(int baseTicks) {
        if (passStartMs > 0L) {
            lastPassDurationMs = Math.max(1000L, System.currentTimeMillis() - passStartMs);
            passStartMs = 0L;
        }
        waitTicks = scaleTicks(baseTicks);
        state = State.COOLDOWN_NEXT_PASS;
    }

    private void quarantineCurrentSpawner(ClientLevel level, LocalPlayer player) {
        BlockPos failed = nav.currentNavTarget != null ? nav.currentNavTarget.immutable() : null;
        if (failed != null) {
            quarantinedSpawners.add(failed);
        }
        reachBatchQueue.clear();
        nav.decayTilePenalties();
        if (failed != null && spawnerIndex < spawners.size() && failed.equals(spawners.get(spawnerIndex))) {
            spawnerIndex++;
        }
        spawnerAttempt = 1;
        prepareSpawnerNavigation(level, player);
    }

    private static boolean tryReturnCarriedToPlayerInventory(
            AbstractContainerMenu menu,
            LocalPlayer player,
            MultiPlayerGameMode gameMode
    ) {
        ItemStack carried = menu.getCarried();
        if (carried.isEmpty()) {
            return false;
        }
        int invStart = Math.max(0, menu.slots.size() - 36);
        int invEnd = menu.slots.size();

        if (carried.isStackable()) {
            for (int s = invStart; s < invEnd; s++) {
                ItemStack st = menu.getSlot(s).getItem();
                if (!st.isEmpty()
                        && st.getCount() < st.getMaxStackSize()
                        && ItemStack.isSameItemSameComponents(st, carried)) {
                    gameMode.handleContainerInput(menu.containerId, s, 0, ContainerInput.PICKUP, player);
                    return true;
                }
            }
        }

        int emptyInv = findFirstEmptyPlayerSlotInMenu(menu, invStart, invEnd);
        if (emptyInv != -1) {
            gameMode.handleContainerInput(menu.containerId, emptyInv, 0, ContainerInput.PICKUP, player);
            return true;
        }
        return false;
    }

    private List<BlockPos> scanRoomChests(
            ClientLevel level,
            LocalPlayer player,
            BlockPos origin,
            SpawnerEnclosure enc,
            BlockPos chestOrderAnchor
    ) {
        BlockPos orderAnchor = chestOrderAnchor != null ? chestOrderAnchor : origin;
        int radius = Math.max(searchRadius, 26);
        List<BlockPos> raw = SpawnerRoomScanner.findNearbyContainerPositions(
                level, player.position(), origin, radius
        );
        if (enc == null || raw.isEmpty()) {
            return SpawnerRoomScanner.orderChestColumnsSequentially(raw, orderAnchor);
        }
        List<BlockPos> bounded = new ArrayList<>();
        for (BlockPos c : raw) {
            if (c.getY() < enc.minY - 1 || c.getY() > enc.maxY + 3) continue;
            if (c.getX() < enc.minX - 3 || c.getX() > enc.maxX + 3
                    || c.getZ() < enc.minZ - 3 || c.getZ() > enc.maxZ + 3) {
                continue;
            }
            if (SpawnerRoomScanner.isTargetReachableFromSpawnerEnclosure(c, enc, 4.25)) {
                bounded.add(c);
            }
        }
        return SpawnerRoomScanner.orderChestColumnsSequentially(bounded.isEmpty() ? raw : bounded, orderAnchor);
    }

    public static List<BlockPos> buildSequentialChestCandidates(
            List<BlockPos> orderedChests,
            int startCursor,
            Set<BlockPos> fullChests
    ) {
        if (orderedChests == null || orderedChests.isEmpty()) {
            return List.of();
        }
        int n = orderedChests.size();
        int start = Math.floorMod(startCursor, n);
        List<BlockPos> nonFull = new ArrayList<>(n);
        List<BlockPos> fullOnes = new ArrayList<>();
        for (int offset = 0; offset < n; offset++) {
            BlockPos c = orderedChests.get((start + offset) % n);
            if (c == null) continue;
            if (fullChests != null && fullChests.contains(c)) {
                fullOnes.add(c);
            } else {
                nonFull.add(c);
            }
        }
        List<BlockPos> result = new ArrayList<>(nonFull.size() + fullOnes.size());
        result.addAll(nonFull);
        result.addAll(fullOnes);
        return result;
    }

    private void advanceSpawnerAttemptOrSkip(ClientLevel level, LocalPlayer player) {
        BlockPos failed = nav.currentNavTarget != null ? nav.currentNavTarget.immutable() : null;
        boolean matchesPrimary = failed == null
                || (spawnerIndex < spawners.size() && failed.equals(spawners.get(spawnerIndex)));
        if (!matchesPrimary) {
            reachBatchQueue.clear();
            spawnerAttempt = 1;
            waitTicks = scaleTicks(2);
            prepareSpawnerNavigation(level, player);
            return;
        }
        if (spawnerAttempt < 3) {
            spawnerAttempt++;
            waitTicks = scaleTicks(5);
            prepareSpawnerNavigation(level, player);
        } else {
            spawnerIndex++;
            spawnerAttempt = 1;
            nav.decayTilePenalties();
            prepareSpawnerNavigation(level, player);
        }
    }

    public static int humanDelay(int baseTicks, int maxExtraTicks) {
        int extra = maxExtraTicks > 0 ? ThreadLocalRandom.current().nextInt(maxExtraTicks + 1) : 0;
        // Occasional micro-hesitation (~4%) for human-like reaction timing
        int microPause = ThreadLocalRandom.current().nextInt(100) < 4 ? ThreadLocalRandom.current().nextInt(2, 5) : 0;
        return Math.max(2, baseTicks + extra + microPause);
    }

    public static boolean lookAtPointGcd(LocalPlayer player, Vec3 target) {
        Vec3 eye = player.getEyePosition();
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        double horiz = Math.hypot(dx, dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
        return applyHumanRotation(player, yaw, Mth.clamp(pitch, -90.0f, 90.0f), false);
    }

    private static float computeEaseInOutStep(float delta, float baseSmooth, float maxStep, float gcdStep) {
        float abs = Math.abs(delta);
        if (abs <= 1e-4f) {
            return 0.0f;
        }
        float t = Mth.clamp(abs / 45.0f, 0.0f, 1.0f);
        float ease = t * t * (3.0f - 2.0f * t);
        float effSmooth = baseSmooth * (0.65f + 0.55f * ease);
        float cap = abs > 110.0f ? maxStep * 0.85f : maxStep;
        float step = Math.min(abs * effSmooth, cap);
        if (abs > gcdStep && step < gcdStep) {
            step = gcdStep;
        }
        return Math.copySign(step, delta);
    }

    public static boolean applyHumanRotation(LocalPlayer player, float targetYaw, float targetPitch, boolean isWalking) {
        Minecraft mc = Minecraft.getInstance();
        float gcdStep = 0.15f;
        if (mc != null && mc.options != null) {
            double sens = mc.options.sensitivity().get() * 0.6 + 0.2;
            gcdStep = Math.max(0.05f, (float) (sens * sens * sens * 1.2));
        }

        float curYaw = player.getYRot();
        float curPitch = player.getXRot();

        float dYaw = Mth.wrapDegrees(targetYaw - curYaw);
        float dPitch = targetPitch - curPitch;

        float smoothFactor = isWalking ? 0.14f : 0.19f;
        float maxYawStep = isWalking ? 9.5f : 12.5f;
        float maxPitchStep = isWalking ? 6.0f : 8.5f;

        float stepYaw = computeEaseInOutStep(dYaw, smoothFactor, maxYawStep, gcdStep);
        float stepPitch = computeEaseInOutStep(dPitch, smoothFactor, maxPitchStep, gcdStep);

        // Subtle organic curve while turning larger angles, always quantized to mouse GCD
        if (Math.abs(dYaw) > 8.0f) {
            stepYaw += (float) ((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.45);
        }
        if (Math.abs(dPitch) > 8.0f) {
            stepPitch += (float) ((ThreadLocalRandom.current().nextDouble() - 0.5) * 0.35);
        }

        if (Math.abs(dYaw) > gcdStep && Math.abs(stepYaw) < gcdStep) {
            stepYaw = Math.copySign(gcdStep, dYaw);
        }
        if (Math.abs(dPitch) > gcdStep && Math.abs(stepPitch) < gcdStep) {
            stepPitch = Math.copySign(gcdStep, dPitch);
        }

        float safeYaw = curYaw + Math.round(stepYaw / gcdStep) * gcdStep;
        float safePitch = Mth.clamp(curPitch + Math.round(stepPitch / gcdStep) * gcdStep, -90.0f, 90.0f);

        player.setYRot(safeYaw);
        player.setXRot(safePitch);
        player.setYHeadRot(safeYaw);

        float remYaw = Math.abs(Mth.wrapDegrees(targetYaw - safeYaw));
        float remPitch = Math.abs(targetPitch - safePitch);
        return remYaw <= 3.5f && remPitch <= 3.5f;
    }

    private static void closeOpenScreenIfAny(Minecraft mc, LocalPlayer player) {
        if (getCurrentScreen(mc) != null && player != null) {
            player.closeContainer();
        }
    }

    private static int getStorageEndSlot(AbstractContainerMenu menu) {
        int containerEnd = Math.max(0, menu.slots.size() - 36);
        // 54-slot plugin menus often reserve bottom row (45..53) for control buttons
        return containerEnd >= 54 ? 45 : containerEnd;
    }

    private static boolean isMinerChestWindow(AbstractContainerScreen<?> screen) {
        String titleNorm = normalizeTurkishText(screen.getTitle().getString());
        if (titleNorm.contains("miner sandik")
                || titleNorm.contains("miner sandigi")
                || titleNorm.contains("madenci sandik")
                || titleNorm.contains("madenci sandigi")
                || (titleNorm.contains("miner") && titleNorm.contains("sayfa"))) {
            return true;
        }
        AbstractContainerMenu menu = screen.getMenu();
        int containerEnd = Math.max(0, menu.slots.size() - 36);
        if (containerEnd == 54) {
            ItemStack s45 = menu.getSlot(45).getItem();
            ItemStack s46 = menu.getSlot(46).getItem();
            ItemStack s49 = menu.getSlot(49).getItem();
            boolean isHopper = !s45.isEmpty() && s45.is(Items.HOPPER);
            boolean isFurnace = !s46.isEmpty() && (s46.is(Items.FURNACE) || s46.is(Items.BLAST_FURNACE));
            boolean isExp = !s49.isEmpty() && s49.is(Items.EXPERIENCE_BOTTLE);
            return isHopper && isFurnace && isExp;
        }
        return false;
    }

    private static int countEmptyPlayerSlots(AbstractContainerMenu menu) {
        int invStart = Math.max(0, menu.slots.size() - 36);
        int empty = 0;
        for (int i = invStart; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().isEmpty()) empty++;
        }
        return empty;
    }

    private static int countEmptyPlayerInventorySlots(LocalPlayer player) {
        int empty = 0;
        for (int i = 0; i < 36; i++) {
            if (player.getInventory().getItem(i).isEmpty()) empty++;
        }
        return empty;
    }

    private static int countPlayerInventoryItem(LocalPlayer player, Item item) {
        int total = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack st = player.getInventory().getItem(i);
            if (!st.isEmpty() && st.is(item)) {
                total += st.getCount();
            }
        }
        return total;
    }

    private static int countIngotsInCraftingMenu(AbstractContainerMenu menu, int invStart, int invEnd) {
        int total = 0;
        int slotCount = menu.slots.size();
        for (int g = 1; g <= 9 && g < slotCount; g++) {
            ItemStack st = menu.getSlot(g).getItem();
            if (!st.isEmpty() && st.is(Items.IRON_INGOT)) total += st.getCount();
        }
        for (int s = invStart; s < invEnd && s < slotCount; s++) {
            ItemStack st = menu.getSlot(s).getItem();
            if (!st.isEmpty() && st.is(Items.IRON_INGOT)) total += st.getCount();
        }
        return total;
    }

    private static int findFirstEmptyPlayerSlotInMenu(AbstractContainerMenu menu, int invStart, int invEnd) {
        for (int s = invStart; s < invEnd; s++) {
            if (menu.getSlot(s).getItem().isEmpty()) return s;
        }
        return -1;
    }

    public static int[] parseDepoPageInfo(String title) {
        if (title == null || title.isEmpty()) return null;
        String clean = stripFormattingCodes(title);
        Matcher m = DEPO_PAGE_PATTERN.matcher(clean);
        if (!m.find()) return null;
        try {
            int cur = Integer.parseInt(m.group(1));
            int total = Integer.parseInt(m.group(2));
            return new int[]{cur, total};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean hasAnyContainerItem(AbstractContainerMenu menu, int invStart) {
        for (int s = 0; s < invStart; s++) {
            if (!menu.getSlot(s).getItem().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int findNextDepoPageSlot(
            AbstractContainerMenu menu,
            int storageEnd,
            int invStart,
            boolean allowSlot50PaperFallback
    ) {
        for (int s = storageEnd; s < invStart; s++) {
            ItemStack st = menu.getSlot(s).getItem();
            if (st.isEmpty()) continue;
            StringBuilder text = new StringBuilder();
            text.append(st.getHoverName().getString()).append(' ');
            Component customName = st.get(DataComponents.CUSTOM_NAME);
            if (customName != null) text.append(customName.getString()).append(' ');
            Component itemName = st.get(DataComponents.ITEM_NAME);
            if (itemName != null) text.append(itemName.getString()).append(' ');
            ItemLore lore = st.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) {
                    text.append(line.getString()).append(' ');
                }
            }
            String norm = normalizeTurkishText(text.toString());
            if (norm.contains("onceki") || norm.contains("previous") || norm.contains("geri")) {
                continue;
            }
            if (norm.contains("sonraki sayfa")
                    || norm.contains("sonraki")
                    || norm.contains("next page")
                    || norm.contains("ileri")) {
                return s;
            }
        }
        if (allowSlot50PaperFallback
                && invStart > 50
                && !menu.getSlot(50).getItem().isEmpty()
                && menu.getSlot(50).getItem().is(Items.PAPER)) {
            return 50;
        }
        return -1;
    }

    private static boolean hasAnyIronIngotInStorage(AbstractContainerMenu menu, int storageEnd) {
        for (int s = 0; s < storageEnd; s++) {
            ItemStack stack = menu.getSlot(s).getItem();
            if (!stack.isEmpty() && stack.is(Items.IRON_INGOT)) {
                return true;
            }
        }
        return false;
    }

    public static String stripFormattingCodes(String text) {
        if (text == null || text.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    public static String normalizeTurkishText(String text) {
        if (text == null || text.isEmpty()) return "";
        String clean = stripFormattingCodes(text);
        StringBuilder mapped = new StringBuilder(clean.length());
        for (int i = 0; i < clean.length(); i++) {
            char c = switch (clean.charAt(i)) {
                case 'ᴀ' -> 'a';
                case 'ʙ' -> 'b';
                case 'ᴄ' -> 'c';
                case 'ᴅ' -> 'd';
                case 'ᴇ' -> 'e';
                case 'ғ', 'ꜰ' -> 'f';
                case 'ɢ' -> 'g';
                case 'ʜ' -> 'h';
                case 'і', 'ɪ' -> 'i';
                case 'ᴊ' -> 'j';
                case 'ᴋ' -> 'k';
                case 'ʟ' -> 'l';
                case 'ᴍ' -> 'm';
                case 'ɴ' -> 'n';
                case 'ᴏ' -> 'o';
                case 'ᴘ' -> 'p';
                case 'ǫ' -> 'q';
                case 'ʀ' -> 'r';
                case 'ꜱ' -> 's';
                case 'ᴛ' -> 't';
                case 'ᴜ' -> 'u';
                case 'ᴠ' -> 'v';
                case 'ᴡ' -> 'w';
                case 'ʏ' -> 'y';
                case 'ᴢ' -> 'z';
                default -> clean.charAt(i);
            };
            mapped.append(c);
        }
        String lower = mapped.toString()
                .replace('İ', 'i')
                .replace('I', 'i')
                .replace('ı', 'i')
                .replace('Ş', 's')
                .replace('ş', 's')
                .replace('Ğ', 'g')
                .replace('ğ', 'g')
                .replace('Ü', 'u')
                .replace('ü', 'u')
                .replace('Ö', 'o')
                .replace('ö', 'o')
                .replace('Ç', 'c')
                .replace('ç', 'c')
                .toLowerCase(Locale.ROOT);
        return Normalizer.normalize(lower, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    public static double computeBlocksPerHour(int blocks, long elapsedMs) {
        if (blocks <= 0 || elapsedMs <= 0L) {
            return 0.0;
        }
        return (blocks * 3_600_000.0) / (double) elapsedMs;
    }

    public static int computePassEtaSeconds(int remainingSpawners, double avgSecPerSpawner) {
        if (remainingSpawners <= 0 || !Double.isFinite(avgSecPerSpawner) || avgSecPerSpawner <= 0.0) {
            return 0;
        }
        double sec = remainingSpawners * avgSecPerSpawner;
        if (!Double.isFinite(sec) || sec <= 0.0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.round(sec));
    }

    public static int computeChestFillColor(int fullChests, int totalChests) {
        if (totalChests <= 0 || fullChests <= 0) {
            return 0xFF4ADE80;
        }
        if (fullChests >= totalChests) {
            return 0xFFEF4444;
        }
        return 0xFFFBBF24;
    }

    private void recordDepositedBlocks(int blocks) {
        if (blocks <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        pruneDepositSamples(now);
        while (depositSamples.size() >= MAX_DEPOSIT_SAMPLES) {
            depositSamples.pollFirst();
        }
        depositSamples.addLast(new DepositSample(now, blocks));
    }

    private void pruneDepositSamples(long nowMs) {
        long cutoff = nowMs - RATE_WINDOW_MS;
        while (!depositSamples.isEmpty() && depositSamples.peekFirst().timestampMs() < cutoff) {
            depositSamples.pollFirst();
        }
    }

    public double getBlocksPerHour() {
        long now = System.currentTimeMillis();
        pruneDepositSamples(now);
        if (depositSamples.isEmpty()) {
            return 0.0;
        }
        int windowBlocks = 0;
        for (DepositSample sample : depositSamples) {
            windowBlocks += sample.blocks();
        }
        if (windowBlocks <= 0) {
            return 0.0;
        }
        long sessionElapsed = enabledStartMs > 0L
                ? (now - enabledStartMs)
                : (now - depositSamples.peekFirst().timestampMs());
        long windowMs = Math.min(RATE_WINDOW_MS, Math.max(10_000L, sessionElapsed));
        return computeBlocksPerHour(windowBlocks, windowMs);
    }

    public double getIngotsPerHour() {
        return getBlocksPerHour() * 9.0;
    }

    public int getPassEtaSeconds() {
        int total = spawners.size();
        if (total <= 0) {
            return 0;
        }
        int doneOrCurrent = Math.max(openedSpawners.size(), Math.min(total, spawnerIndex));
        int remaining = Math.max(0, total - doneOrCurrent);
        if (remaining <= 0) {
            return 0;
        }
        double avgSec;
        if (spawnerIndex >= 1 && passStartMs > 0L) {
            double curSec = Math.max(1.0, (System.currentTimeMillis() - passStartMs) / 1000.0);
            double liveAvg = curSec / Math.max(1, spawnerIndex);
            if (spawnerIndex < 3 && lastPassDurationMs > 0L) {
                double prevAvg = (lastPassDurationMs / 1000.0) / total;
                avgSec = (liveAvg + prevAvg) * 0.5;
            } else {
                avgSec = liveAvg;
            }
        } else if (lastPassDurationMs > 0L) {
            avgSec = (lastPassDurationMs / 1000.0) / total;
        } else {
            avgSec = DEFAULT_AVG_SEC_PER_SPAWNER;
        }
        return computePassEtaSeconds(remaining, avgSec);
    }

    public int getFullChestCount() {
        if (scannedChests == null || scannedChests.isEmpty()) {
            return fullSpawnerChests.size();
        }
        int count = 0;
        for (BlockPos chest : scannedChests) {
            if (fullSpawnerChests.contains(chest)) {
                count++;
            }
        }
        return count;
    }

    public int getScannedChestCount() {
        return scannedChests != null ? scannedChests.size() : 0;
    }

    private static String truncateText(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen || maxLen <= 3) return text;
        return text.substring(0, maxLen - 3) + "...";
    }

    private static String formatMmSs(int totalSeconds) {
        int sec = Math.max(0, totalSeconds);
        int mins = sec / 60;
        int rem = sec % 60;
        return String.format(Locale.ROOT, "%02d:%02d", mins, rem);
    }

    private static String formatRateText(double blkPerHr, double ingotPerHr) {
        long blk = Math.round(Math.max(0.0, blkPerHr));
        long ing = Math.round(Math.max(0.0, ingotPerHr));
        String ingStr = ing >= 1000L
                ? String.format(Locale.ROOT, "%.1fk", ing / 1000.0)
                : Long.toString(ing);
        return blk + " blk/h (" + ingStr + ")";
    }

    public String getStatusLabelEnglish() {
        if (state != State.PAUSED_SAFETY && chatPaused) {
            return "Paused (Chat)";
        }
        return switch (state) {
            case SCANNING_ROOM -> "Scanning room";
            case MOVE_TO_SPAWNER -> "Moving to spawner";
            case INTERACT_SPAWNER, WAIT_MANAGEMENT_GUI, CLICK_OPEN_DEPO, WAIT_DEPO_GUI -> "Opening spawner";
            case LOOT_IRON_INGOTS -> "Looting iron";
            case SEND_CRAFT_CMD, WAIT_CRAFT_GUI, CRAFT_SPLIT_AND_FILL -> "Crafting blocks";
            case FIND_NEXT_CHEST, MOVE_TO_CHEST -> "Moving to chest";
            case INTERACT_CHEST, WAIT_CHEST_GUI, DEPOSIT_IRON_BLOCKS -> "Depositing";
            case COOLDOWN_NEXT_PASS -> "Next pass";
            case PAUSED_SAFETY -> (safetyPauseReason == null || safetyPauseReason.isBlank())
                    ? "Safety Pause"
                    : "Safety: " + truncateText(safetyPauseReason, 24);
        };
    }

    public String getFormattedElapsedTime() {
        return SpawnerNavHelper.formatElapsedTime(enabledStartMs);
    }

    private static final int HUD_MIN_WIDTH = 138;
    private static final int HUD_DEFAULT_X = 8;
    private static final int HUD_DEFAULT_Y = 128;

    private int cachedTitlePass = -1;
    private String cachedTitle = "Auto Spawner #1";

    private static String resolveHudTitle(AutoSpawnerModule mod) {
        if (mod == null || !mod.isEnabled()) {
            return "Auto Spawner #1";
        }
        int pass = Math.max(1, mod.passNumber);
        if (mod.cachedTitlePass != pass) {
            mod.cachedTitlePass = pass;
            mod.cachedTitle = "Auto Spawner #" + pass;
        }
        return mod.cachedTitle;
    }

    private static List<JelloTheme.HudStatRow> buildHudRows(AutoSpawnerModule mod, ModConfig cfg) {
        ModConfig effectiveCfg = cfg != null ? cfg : ModConfig.getInstance();
        boolean active = mod != null && mod.isEnabled();
        boolean detailed = effectiveCfg == null || effectiveCfg.autoSpawnerHudDetailed;
        boolean showBar = effectiveCfg == null || effectiveCfg.hudUnifiedGlass;
        int accentHover = JelloTheme.ACCENT_HOVER;

        if (!active) {
            if (PREVIEW_HUD_CACHE.valid
                    && !PREVIEW_HUD_CACHE.active
                    && PREVIEW_HUD_CACHE.detailed == detailed
                    && PREVIEW_HUD_CACHE.showBar == showBar
                    && PREVIEW_HUD_CACHE.accentHover == accentHover) {
                return PREVIEW_HUD_CACHE.rows;
            }
            List<JelloTheme.HudStatRow> previewRows = detailed
                    ? List.of(
                    new JelloTheme.HudStatRow("Spawner", "0/64", accentHover, 0xFFFFFFFF, showBar ? 0.61f : -1.0f),
                    new JelloTheme.HudStatRow("Status", "Looting iron", 0xFF22C55E, 0xFF22C55E),
                    new JelloTheme.HudStatRow("Deposited", "0", 0xFFF59E0B, 0xFFF59E0B),
                    new JelloTheme.HudStatRow("Rate", "180 blk/h (1.6k)", 0xFF38BDF8, 0xFF38BDF8),
                    new JelloTheme.HudStatRow("ETA", "~01:15", 0xFF93C5FD, 0xFFFFFFFF),
                    new JelloTheme.HudStatRow("Chests", "1/6", computeChestFillColor(1, 6), computeChestFillColor(1, 6), showBar ? (1.0f / 6.0f) : -1.0f),
                    new JelloTheme.HudStatRow("Time", "00:00", 0xFFD8D8D8, 0xFFFFFFFF)
            )
                    : List.of(
                    new JelloTheme.HudStatRow("Spawner", "0/64", accentHover, 0xFFFFFFFF, showBar ? 0.61f : -1.0f),
                    new JelloTheme.HudStatRow("Status", "Looting iron", 0xFF22C55E, 0xFF22C55E),
                    new JelloTheme.HudStatRow("Deposited", "0", 0xFFF59E0B, 0xFFF59E0B),
                    new JelloTheme.HudStatRow("Time", "00:00", 0xFFD8D8D8, 0xFFFFFFFF)
            );
            PREVIEW_HUD_CACHE.valid = true;
            PREVIEW_HUD_CACHE.active = false;
            PREVIEW_HUD_CACHE.detailed = detailed;
            PREVIEW_HUD_CACHE.showBar = showBar;
            PREVIEW_HUD_CACHE.accentHover = accentHover;
            PREVIEW_HUD_CACHE.rows = previewRows;
            return previewRows;
        }

        long nowMs = System.currentTimeMillis();
        long nowWallSec = nowMs / 1000L;
        long elapsedSec = mod.enabledStartMs > 0L ? Math.max(0L, (nowMs - mod.enabledStartMs) / 1000L) : 0L;
        int openedCount = mod.openedSpawners.size();
        int spawnerTotal = mod.spawners.size();
        int rawFullSize = mod.fullSpawnerChests.size();
        int totalChests = mod.getScannedChestCount();
        String safetyReason = mod.state == State.PAUSED_SAFETY ? mod.safetyPauseReason : "";

        HudRowCache cache = mod.activeHudCache;
        if (cache.valid
                && cache.active
                && cache.detailed == detailed
                && cache.showBar == showBar
                && cache.accentHover == accentHover
                && cache.state == mod.state
                && cache.chatPaused == mod.chatPaused
                && cache.safetyCritical == mod.safetyCritical
                && Objects.equals(cache.safetyReason, safetyReason)
                && cache.passNumber == mod.passNumber
                && cache.openedCount == openedCount
                && cache.spawnerIndex == mod.spawnerIndex
                && cache.spawnerTotal == spawnerTotal
                && cache.depositedBlocks == mod.totalDepositedBlocks
                && cache.rawFullChestsSize == rawFullSize
                && cache.totalChests == totalChests
                && cache.wallSecond == nowWallSec
                && cache.elapsedSec == elapsedSec) {
            return cache.rows;
        }

        String spVal = openedCount + "/" + spawnerTotal;
        float spProgress = spawnerTotal == 0 ? 0.0f : (float) openedCount / spawnerTotal;
        String statusVal = mod.getStatusLabelEnglish();
        double blkPerHr = mod.getBlocksPerHour();
        double ingPerHr = mod.getIngotsPerHour();
        String depVal = (!detailed && blkPerHr >= 1.0)
                ? mod.totalDepositedBlocks + " (" + Math.round(blkPerHr) + "/h)"
                : Integer.toString(mod.totalDepositedBlocks);
        String timeVal = mod.getFormattedElapsedTime();

        int statusColor = (mod.chatPaused && mod.state != State.PAUSED_SAFETY)
                ? 0xFF38BDF8
                : switch (mod.state) {
            case SCANNING_ROOM, MOVE_TO_SPAWNER, INTERACT_SPAWNER, WAIT_MANAGEMENT_GUI,
                    CLICK_OPEN_DEPO, WAIT_DEPO_GUI, LOOT_IRON_INGOTS -> 0xFF22C55E;
            case SEND_CRAFT_CMD, WAIT_CRAFT_GUI, CRAFT_SPLIT_AND_FILL,
                    FIND_NEXT_CHEST, MOVE_TO_CHEST, INTERACT_CHEST, WAIT_CHEST_GUI,
                    DEPOSIT_IRON_BLOCKS -> 0xFFF59E0B;
            case COOLDOWN_NEXT_PASS -> 0xFF38BDF8;
            case PAUSED_SAFETY -> mod.safetyCritical ? 0xFFEF4444 : 0xFFF59E0B;
        };

        List<JelloTheme.HudStatRow> built;
        if (!detailed) {
            built = List.of(
                    new JelloTheme.HudStatRow("Spawner", spVal, accentHover, 0xFFFFFFFF, showBar ? spProgress : -1.0f),
                    new JelloTheme.HudStatRow("Status", statusVal, statusColor, statusColor),
                    new JelloTheme.HudStatRow("Deposited", depVal, 0xFFF59E0B, 0xFFF59E0B),
                    new JelloTheme.HudStatRow("Time", timeVal, 0xFFD8D8D8, 0xFFFFFFFF)
            );
        } else {
            String rateVal = formatRateText(blkPerHr, ingPerHr);
            int etaSec = mod.getPassEtaSeconds();
            String etaVal = "~" + formatMmSs(etaSec);
            int fullChests = mod.getFullChestCount();
            int chestCol = computeChestFillColor(fullChests, totalChests);
            float chestProgress = totalChests > 0 ? Math.min(1.0f, (float) fullChests / (float) totalChests) : 0.0f;

            built = List.of(
                    new JelloTheme.HudStatRow("Spawner", spVal, accentHover, 0xFFFFFFFF, showBar ? spProgress : -1.0f),
                    new JelloTheme.HudStatRow("Status", statusVal, statusColor, statusColor),
                    new JelloTheme.HudStatRow("Deposited", depVal, 0xFFF59E0B, 0xFFF59E0B),
                    new JelloTheme.HudStatRow("Rate", rateVal, 0xFF38BDF8, 0xFF38BDF8),
                    new JelloTheme.HudStatRow("ETA", etaVal, 0xFF93C5FD, 0xFFFFFFFF),
                    new JelloTheme.HudStatRow("Chests", fullChests + "/" + totalChests, chestCol, chestCol, showBar ? chestProgress : -1.0f),
                    new JelloTheme.HudStatRow("Time", timeVal, 0xFFD8D8D8, 0xFFFFFFFF)
            );
        }

        cache.valid = true;
        cache.active = true;
        cache.detailed = detailed;
        cache.showBar = showBar;
        cache.accentHover = accentHover;
        cache.state = mod.state;
        cache.chatPaused = mod.chatPaused;
        cache.safetyCritical = mod.safetyCritical;
        cache.safetyReason = safetyReason;
        cache.passNumber = mod.passNumber;
        cache.openedCount = openedCount;
        cache.spawnerIndex = mod.spawnerIndex;
        cache.spawnerTotal = spawnerTotal;
        cache.depositedBlocks = mod.totalDepositedBlocks;
        cache.rawFullChestsSize = rawFullSize;
        cache.totalChests = totalChests;
        cache.wallSecond = nowWallSec;
        cache.elapsedSec = elapsedSec;
        cache.rows = built;
        return built;
    }

    public static int[] resolveHudBounds(Font font, int screenW, int screenH, ModConfig cfg) {
        AutoSpawnerModule mod = getInstance();
        String title = resolveHudTitle(mod);
        return resolveHudBoundsForRows(font, screenW, screenH, cfg, title, buildHudRows(mod, cfg));
    }

    private static int[] resolveHudBoundsForRows(
            Font font,
            int screenW,
            int screenH,
            ModConfig cfg,
            String title,
            List<JelloTheme.HudStatRow> rows
    ) {
        int cfgX = cfg != null ? cfg.autoSpawnerHudX : -1;
        int cfgY = cfg != null ? cfg.autoSpawnerHudY : -1;
        return JelloTheme.resolveHudCardBounds(
                font, screenW, screenH, title, rows,
                HUD_MIN_WIDTH, cfgX, cfgY, HUD_DEFAULT_X, HUD_DEFAULT_Y
        );
    }

    public static void renderHud(GuiGraphicsExtractor graphics) {
        if (PanicHandler.isPanicActive()) return;
        AutoSpawnerModule mod = getInstance();
        if (mod == null || !mod.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.font == null) return;
        if (mc.gui != null && (ClickGuiScreen.isTargetScreen(mc.gui.screen()) || mc.gui.screen() instanceof HudLayoutEditorScreen)) {
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        boolean safetyPaused = mod.isSafetyPaused();
        String title = resolveHudTitle(mod);
        List<JelloTheme.HudStatRow> rows = buildHudRows(mod, cfg);
        int[] bounds = resolveHudBoundsForRows(mc.font, graphics.guiWidth(), graphics.guiHeight(), cfg, title, rows);
        int headerBg = JelloTheme.hudHeaderBackground(safetyPaused);
        JelloTheme.renderHudCard(graphics, mc.font, bounds[0], bounds[1], bounds[2], bounds[3], title, rows);
        if (safetyPaused) {
            int alertBg = JelloTheme.lerpColor(headerBg, 0xF2991B1B, 0.72f);
            int x = bounds[0];
            int y = bounds[1];
            int w = bounds[2];
            JelloTheme.fillRoundedTop(graphics, x + 1, y + 1, x + w - 1, y + JelloTheme.HUD_CARD_HEADER_HEIGHT, 4, alertBg);
            JelloTheme.fill(graphics, x + 1, y + JelloTheme.HUD_CARD_HEADER_HEIGHT - 1, x + w - 1, y + JelloTheme.HUD_CARD_HEADER_HEIGHT, 0xFFEF4444);
            JelloTheme.textCentered(graphics, mc.font, title, x + w / 2, y + 4, 0xFFFFFFFF);
        }
    }
}
