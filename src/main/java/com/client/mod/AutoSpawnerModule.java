package com.client.mod;

import com.client.mod.SpawnerPathfinder.SpotCandidate;
import com.client.mod.SpawnerRoomScanner.SpawnerEnclosure;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        COOLDOWN_NEXT_PASS
    }

    private static final AutoSpawnerModule INSTANCE = new AutoSpawnerModule();
    private static final Pattern DEPO_PAGE_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)");

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
    private int spawnerIndex = 0;
    private int spawnerAttempt = 1;
    private int passNumber = 0;
    private int lastScanCount = -1;
    private boolean waitingToFinishCurrentSpawner = false;
    private BlockPos pendingGreenSpawner = null;

    // Movement & Pathing Helper
    private final SpawnerNavHelper nav = new SpawnerNavHelper();

    // Camera Restore & GUI Tracking
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

    // Chest Deposit Tracking
    private final Set<BlockPos> fullSpawnerChests = new HashSet<>();
    private final Map<BlockPos, Integer> chestDepositCounts = new HashMap<>();
    private final Set<BlockPos> openedSpawners = new HashSet<>();
    private List<BlockPos> scannedChests = new ArrayList<>();
    private List<BlockPos> candidateChests = new ArrayList<>();
    private int chestIndex = 0;
    private int chestDepositSlotIdx = -1;
    private int chestPrevSlotCount = -1;
    private int totalDepositedBlocks = 0;
    private long enabledStartMs = 0L;

    private AutoSpawnerModule() {
    }

    public boolean isEnabled() {
        return enabled && !PanicHandler.isPanicActive();
    }

    public boolean isActiveLockingMouse() {
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        boolean activeLock = cfg != null ? (cfg.autoSpawnerEnabled && cfg.autoSpawnerLockMouse) : (enabled && lockMouse);
        return enabled && activeLock && !PanicHandler.isPanicActive()
                && mc != null && mc.player != null && getCurrentScreen(mc) == null;
    }

    public boolean isControllingMovement() {
        Minecraft mc = Minecraft.getInstance();
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

    public Set<BlockPos> getFullSpawnerChests() {
        return fullSpawnerChests;
    }

    public List<BlockPos> getScannedSpawners() {
        return new ArrayList<>(spawners);
    }

    public Set<BlockPos> getOpenedSpawners() {
        return new HashSet<>(openedSpawners);
    }

    public void setEnabledFromConfig(boolean val) {
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
    }

    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        enabled = true;
        clearMovementFlags();
        resetAllRuntimeState();
        enabledStartMs = System.currentTimeMillis();
        if (mc != null && mc.player != null) {
            savedYaw = mc.player.getYRot();
            savedPitch = mc.player.getXRot();
        }
        state = State.SCANNING_ROOM;
    }

    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        enabled = false;
        clearMovementFlags();
        if (mc != null && mc.player != null) {
            if (mc.player.containerMenu != null && getCurrentScreen(mc) != null) {
                mc.player.closeContainer();
            }
            if (savedYaw != null && savedPitch != null) {
                mc.player.setYRot(savedYaw);
                mc.player.setXRot(savedPitch);
            }
        }
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
    }

    private void resetAllRuntimeState() {
        state = State.SCANNING_ROOM;
        waitTicks = 0;
        stateTimeoutTicks = 0;
        enclosure = null;
        homePos = null;
        spawners = new ArrayList<>();
        spawnerIndex = 0;
        spawnerAttempt = 1;
        passNumber = 0;
        lastScanCount = -1;
        waitingToFinishCurrentSpawner = false;
        pendingGreenSpawner = null;
        nav.resetAll(4.25);
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
        candidateChests = new ArrayList<>();
        chestIndex = 0;
        totalDepositedBlocks = 0;
        enabledStartMs = 0L;
        fullSpawnerChests.clear();
        chestDepositCounts.clear();
        openedSpawners.clear();
    }

    private void clearMovementFlags() {
        nav.clearMovementFlags();
    }

    private static Screen getCurrentScreen(Minecraft mc) {
        return mc != null && mc.gui != null ? mc.gui.screen() : null;
    }

    public void onClientTick() {
        syncSettingsFromConfig();
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc != null ? mc.player : null;
        ClientLevel level = mc != null ? mc.level : null;
        MultiPlayerGameMode gameMode = mc != null ? mc.gameMode : null;
        if (player == null || level == null || gameMode == null) {
            clearMovementFlags();
            return;
        }

        // Strict InventoryMove prevention: never move while ANY GUI is open
        if (getCurrentScreen(mc) != null) {
            clearMovementFlags();
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
            waitTicks = 40; // Retry in 2s if no spawner is in loaded chunks yet
            return;
        }

        // First scan all spawners and chests in the room before starting navigation
        this.spawners = found;
        this.enclosure = SpawnerRoomScanner.computeSpawnerEnclosure(level, player, found, anchor, enclosure);
        this.homePos = anchor;
        this.scannedChests = scanRoomChests(level, player, anchor, this.enclosure);

        if (lastScanCount != found.size()) {
            lastScanCount = found.size();
            waitTicks = 4;
            return;
        }

        lastScanCount = -1;
        passNumber++;
        this.openedSpawners.clear();
        this.pendingGreenSpawner = null;
        this.spawnerIndex = 0;
        this.spawnerAttempt = 1;
        nav.activePath = null;
        nav.activePathAllowsSpawnerFloor = false;

        prepareSpawnerNavigation(level, player);
    }

    private void prepareSpawnerNavigation(ClientLevel level, LocalPlayer player) {
        clearMovementFlags();
        nav.activePathAllowsSpawnerFloor = true;
        if (spawnerIndex >= spawners.size()) {
            finishAllSpawnersCycle(player);
            return;
        }

        BlockPos targetSpawner = spawners.get(spawnerIndex);
        nav.currentNavTarget = targetSpawner;
        nav.stopHorizReach = 1.98;
        nav.stopEyeReach = 4.35;

        if (SpawnerNavHelper.isAlreadyInOptimalReach(level, player, targetSpawner, true, spawnerAttempt >= 2, enclosure)) {
            state = State.INTERACT_SPAWNER;
            return;
        }

        List<BlockPos> selectedPath = nav.selectSpawnerPath(level, player, targetSpawner, enclosure, spawnerAttempt >= 2);
        if (selectedPath == null || selectedPath.isEmpty()) {
            if (SpawnerNavHelper.canFallbackInteractDirectly(player, targetSpawner)) {
                state = State.INTERACT_SPAWNER;
            } else {
                advanceSpawnerAttemptOrSkip(level, player);
            }
            return;
        }

        nav.startPathWalk(player, selectedPath, Math.max(100, Math.min(360, (selectedPath.size() + 3) * 11)));
        state = State.MOVE_TO_SPAWNER;
    }

    private void tickMoveToTarget(Minecraft mc, ClientLevel level, LocalPlayer player, boolean isSpawner) {
        if (getCurrentScreen(mc) != null) {
            closeOpenScreenIfAny(mc, player);
            clearMovementFlags();
            waitTicks = 2;
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
                    state = State.FIND_NEXT_CHEST;
                }
            }
            case REACHED -> state = isSpawner ? State.INTERACT_SPAWNER : State.INTERACT_CHEST;
            case ABORTED -> {
                if (isSpawner) {
                    advanceSpawnerAttemptOrSkip(level, player);
                } else {
                    chestIndex++;
                    state = State.FIND_NEXT_CHEST;
                }
            }
            case CONTINUE -> {
            }
        }
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
            waitTicks = 4;
            return;
        }
        if (nav.currentNavTarget == null) {
            state = State.SCANNING_ROOM;
            return;
        }

        Direction clickFace = SpawnerPathfinder.getExposedSpawnerClickFace(level, player, nav.currentNavTarget);
        Vec3 hitVec = Vec3.atCenterOf(nav.currentNavTarget).add(
                clickFace.getStepX() * 0.5,
                clickFace.getStepY() * 0.5,
                clickFace.getStepZ() * 0.5
        );

        boolean aimed = lookAtPointGcd(player, hitVec);

        // Wait until smooth human aim converges + 2 settle ticks before interact packet
        stateTimeoutTicks++;
        if ((!aimed && stateTimeoutTicks < 28) || stateTimeoutTicks < 3) {
            return;
        }
        stateTimeoutTicks = 0;

        BlockHitResult hitResult = new BlockHitResult(hitVec, clickFace, nav.currentNavTarget, false);
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hitResult);
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);

        waitTicks = 2;
        stateTimeoutTicks = 0;
        state = State.WAIT_MANAGEMENT_GUI;
    }

    private void tickWaitManagementGui(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        if (getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen) {
            String titleNorm = normalizeTurkishText(containerScreen.getTitle().getString());
            if (titleNorm.contains("depo")) {
                resetLootTrackingState();
                waitTicks = 4;
                state = State.LOOT_IRON_INGOTS;
                return;
            }
            waitTicks = 4;
            state = State.CLICK_OPEN_DEPO;
            return;
        }

        stateTimeoutTicks++;
        if (stateTimeoutTicks > 56) { // ~2.8s timeout
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
        waitTicks = 3;
        state = State.WAIT_DEPO_GUI;
    }

    private void tickWaitDepoGui(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        if (getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen) {
            AbstractContainerMenu menu = containerScreen.getMenu();
            int invStart = Math.max(0, menu.slots.size() - 36);
            if (menu.containerId != lastManagementContainerId
                    && (hasAnyContainerItem(menu, invStart) || stateTimeoutTicks >= 12)) {
                resetLootTrackingState();
                waitTicks = 3;
                state = State.LOOT_IRON_INGOTS;
                return;
            }
        }
        stateTimeoutTicks++;
        if (stateTimeoutTicks > 70) { // ~3.5s timeout
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

    private void tickLootIronIngots(Minecraft mc, LocalPlayer player, MultiPlayerGameMode gameMode) {
        clearMovementFlags();
        if (!(getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen)) {
            afterSpawnerLooted(mc, player, false);
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        int storageEnd = getStorageEndSlot(menu);
        int invStart = Math.max(0, menu.slots.size() - 36);
        int invEnd = menu.slots.size();
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
                waitTicks = 3;
                return;
            } else {
                lootNextPageTimeout++;
                if (lootNextPageTimeout <= 60) {
                    return;
                }
                lootWaitingForPageFrom = -1;
                lootPageClickContainerId = -1;
                lootNextPageTimeout = 0;
                closeOpenScreenIfAny(mc, player);
                waitTicks = 4;
                afterSpawnerLooted(mc, player, false);
                return;
            }
        }

        // Wait until the new page's container slots have arrived from the server
        if (lootWaitingForPageContent) {
            lootContentWaitTicks++;
            if (!hasAnyContainerItem(menu, invStart) && lootContentWaitTicks <= 24) {
                return;
            }
            lootWaitingForPageContent = false;
            lootContentWaitTicks = 0;
            waitTicks = 1;
            return;
        }

        boolean hasExplicitNextButton = findNextDepoPageSlot(menu, storageEnd, invStart, false) != -1;
        boolean hasMoreDepoPages = depoPage != null ? depoPage[0] < depoPage[1] : hasExplicitNextButton;

        // If cursor is holding an item from fallback PICKUP click, place it into first empty player slot
        if (!menu.getCarried().isEmpty()) {
            int emptyInv = findFirstEmptyPlayerSlotInMenu(menu, invStart, invEnd);
            if (emptyInv != -1) {
                gameMode.handleContainerInput(menu.containerId, emptyInv, 0, ContainerInput.PICKUP, player);
                lootLastClickedSlot = -1;
                lootLastSlotCount = -1;
                lootTriedNormalClick = false;
                waitTicks = 2;
                return;
            }
            closeOpenScreenIfAny(mc, player);
            waitTicks = 4;
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
                    waitTicks = Math.max(2, clickDelayTicks);
                    return;
                } else {
                    boolean shouldStay = hasMoreDepoPages || countEmptyPlayerSlots(menu) <= 2;
                    closeOpenScreenIfAny(mc, player);
                    waitTicks = 4;
                    afterSpawnerLooted(mc, player, shouldStay);
                    return;
                }
            }
            lootLastClickedSlot = -1;
            lootLastSlotCount = -1;
            lootTriedNormalClick = false;
        }

        int targetSlot = -1;
        for (int s = 0; s < storageEnd; s++) {
            ItemStack stack = menu.getSlot(s).getItem();
            if (!stack.isEmpty() && stack.is(Items.IRON_INGOT)) {
                targetSlot = s;
                break;
            }
        }

        // Only finish the spawner when there are NO iron ingots left AND NO remaining depo pages
        if (targetSlot == -1 && !hasMoreDepoPages) {
            closeOpenScreenIfAny(mc, player);
            waitTicks = 4;
            afterSpawnerLooted(mc, player, false);
            return;
        }

        if (lootSafetyClicks >= 55 || countEmptyPlayerSlots(menu) <= 1) {
            closeOpenScreenIfAny(mc, player);
            waitTicks = 4;
            afterSpawnerLooted(mc, player, true);
            return;
        }

        if (targetSlot == -1) {
            int nextPageSlot = findNextDepoPageSlot(menu, storageEnd, invStart, depoPage != null && depoPage[0] < depoPage[1]);
            if (nextPageSlot != -1) {
                lootWaitingForPageFrom = depoPage != null ? depoPage[0] : 0;
                lootPageClickContainerId = menu.containerId;
                lootNextPageTimeout = 0;
                lootLastClickedSlot = -1;
                lootLastSlotCount = -1;
                lootTriedNormalClick = false;
                gameMode.handleContainerInput(menu.containerId, nextPageSlot, 0, ContainerInput.PICKUP, player);
                waitTicks = 2;
                return;
            }
            closeOpenScreenIfAny(mc, player);
            waitTicks = 4;
            afterSpawnerLooted(mc, player, false);
            return;
        }

        lootSafetyClicks++;
        lootLastClickedSlot = targetSlot;
        lootLastSlotCount = menu.getSlot(targetSlot).getItem().getCount();
        lootTriedNormalClick = false;
        gameMode.handleContainerInput(menu.containerId, targetSlot, 0, ContainerInput.QUICK_MOVE, player);
        waitTicks = humanDelay(Math.max(2, clickDelayTicks), 2);
    }

    private void afterSpawnerLooted(Minecraft mc, LocalPlayer player, boolean stayOnCurrentSpawner) {
        BlockPos curSp = (spawnerIndex >= 0 && spawnerIndex < spawners.size()) ? spawners.get(spawnerIndex) : null;
        this.waitingToFinishCurrentSpawner = stayOnCurrentSpawner;
        if (stayOnCurrentSpawner) {
            // Stay on the same spawner until all pages & iron are looted
            spawnerAttempt = 1;
        } else {
            // Do NOT turn green yet: turn green only after crafting & depositing into chest
            if (curSp != null) {
                pendingGreenSpawner = curSp.immutable();
            }
            spawnerIndex++;
            spawnerAttempt = 1;
        }

        int currentIngots = countPlayerInventoryItem(player, Items.IRON_INGOT);
        if (currentIngots >= 9) {
            state = State.SEND_CRAFT_CMD;
            return;
        }

        int blocksInInv = countPlayerInventoryItem(player, Items.IRON_BLOCK);
        if (blocksInInv > 0 && shouldDepositBlocksNow(player)) {
            candidateChests = List.of();
            chestIndex = 0;
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
            state = State.SEND_CRAFT_CMD;
            return;
        }
        int remBlocks = countPlayerInventoryItem(player, Items.IRON_BLOCK);
        if (remBlocks > 0) {
            candidateChests = List.of();
            chestIndex = 0;
            state = State.FIND_NEXT_CHEST;
            return;
        }
        waitTicks = 7; // 350ms before next full pass
        state = State.COOLDOWN_NEXT_PASS;
    }

    private void tickSendCraftCmd(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        closeOpenScreenIfAny(mc, player);
        ModConfig cfg = ModConfig.getInstance();
        boolean autoCrafterOn = cfg != null && cfg.autoCrafterEnabled;
        if (!autoCrafterOn || player.connection == null || countPlayerInventoryItem(player, Items.IRON_INGOT) < 9) {
            afterCraftingCompleted(mc, player);
            return;
        }
        player.connection.sendCommand("craft");
        stateTimeoutTicks = 0;
        waitTicks = 3;
        state = State.WAIT_CRAFT_GUI;
    }

    private void tickWaitCraftGui(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.autoCrafterEnabled) {
            closeOpenScreenIfAny(mc, player);
            afterCraftingCompleted(mc, player);
            return;
        }
        if (getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen
                && containerScreen.getMenu().slots.size() >= 46) {
            stateTimeoutTicks = 0;
            waitTicks = 2;
            state = State.CRAFT_SPLIT_AND_FILL;
            return;
        }
        stateTimeoutTicks++;
        if (stateTimeoutTicks > 110) { // 5.5s timeout
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

        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.autoCrafterEnabled) {
            closeOpenScreenIfAny(mc, player);
            stateTimeoutTicks = 0;
            waitTicks = 4;
            afterCraftingCompleted(mc, player);
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        int invStart = Math.max(0, menu.slots.size() - 36);
        int invEnd = menu.slots.size();
        int remainingIngots = countIngotsInCraftingMenu(menu, invStart, invEnd);

        if (remainingIngots < 9 && !menu.slots.isEmpty() && menu.getSlot(0).getItem().isEmpty()) {
            closeOpenScreenIfAny(mc, player);
            stateTimeoutTicks = 0;
            waitTicks = 4;
            afterCraftingCompleted(mc, player);
            return;
        }

        stateTimeoutTicks++;
        if (stateTimeoutTicks > 120) { // 6s safety timeout
            closeOpenScreenIfAny(mc, player);
            stateTimeoutTicks = 0;
            waitTicks = 4;
            afterCraftingCompleted(mc, player);
        }
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
            state = State.FIND_NEXT_CHEST;
        } else {
            flushPendingGreenSpawnerIfFinished();
            if (spawnerIndex < spawners.size()) {
                prepareSpawnerNavigation(mc.level, player);
            } else {
                waitTicks = 7;
                state = State.COOLDOWN_NEXT_PASS;
            }
        }
    }

    private void tickFindNextChest(Minecraft mc, ClientLevel level, LocalPlayer player) {
        clearMovementFlags();
        closeOpenScreenIfAny(mc, player);

        if (countPlayerInventoryItem(player, Items.IRON_BLOCK) <= 0) {
            if (spawnerIndex < spawners.size()) {
                prepareSpawnerNavigation(level, player);
            } else {
                waitTicks = 7;
                state = State.COOLDOWN_NEXT_PASS;
            }
            return;
        }

        if (candidateChests == null || candidateChests.isEmpty() || chestIndex == 0) {
            if (scannedChests == null || scannedChests.isEmpty()) {
                BlockPos origin = homePos != null ? homePos : player.blockPosition();
                scannedChests = scanRoomChests(level, player, origin, enclosure);
            }
            List<BlockPos> rawChests = new ArrayList<>(scannedChests);
            if (rawChests.isEmpty()) {
                rawChests = SpawnerRoomScanner.findNearbyContainerPositions(
                        level, player.position(), homePos != null ? homePos : player.blockPosition(), 26
                );
            }

            List<BlockPos> nonFull = new ArrayList<>();
            List<BlockPos> fullOnes = new ArrayList<>();
            for (BlockPos c : rawChests) {
                if (fullSpawnerChests.contains(c)) {
                    fullOnes.add(c);
                } else {
                    nonFull.add(c);
                }
            }
            if (nonFull.isEmpty()) {
                fullSpawnerChests.clear();
                nonFull.addAll(rawChests);
                fullOnes.clear();
            }

            // Equal distribution: always prioritize chests with the fewest deposits so far (shuffled among ties)
            int minDeposits = Integer.MAX_VALUE;
            for (BlockPos c : nonFull) {
                int cnt = chestDepositCounts.getOrDefault(c, 0);
                if (cnt < minDeposits) minDeposits = cnt;
            }
            List<BlockPos> leastUsed = new ArrayList<>();
            List<BlockPos> moreUsed = new ArrayList<>();
            for (BlockPos c : nonFull) {
                if (chestDepositCounts.getOrDefault(c, 0) <= minDeposits) {
                    leastUsed.add(c);
                } else {
                    moreUsed.add(c);
                }
            }
            Collections.shuffle(leastUsed, ThreadLocalRandom.current());
            Collections.shuffle(moreUsed, ThreadLocalRandom.current());

            candidateChests = new ArrayList<>(nonFull.size() + fullOnes.size());
            candidateChests.addAll(leastUsed);
            candidateChests.addAll(moreUsed);
            candidateChests.addAll(fullOnes);
        }

        if (candidateChests.isEmpty() || chestIndex >= candidateChests.size()) {
            // No accessible chests or all scanned; move on so loop never deadlocks
            flushPendingGreenSpawnerIfFinished();
            if (spawnerIndex < spawners.size()) {
                prepareSpawnerNavigation(level, player);
            } else {
                waitTicks = 20;
                state = State.COOLDOWN_NEXT_PASS;
            }
            return;
        }

        BlockPos chestPos = candidateChests.get(chestIndex);
        nav.currentNavTarget = chestPos;
        nav.stopHorizReach = 3.65;
        nav.stopEyeReach = 3.95;

        if (SpawnerNavHelper.isAlreadyInOptimalReach(level, player, chestPos, false, false, enclosure)) {
            state = State.INTERACT_CHEST;
            return;
        }

        BlockPos startStand = SpawnerRoomScanner.getSpawnerBotStandPos(level, player, enclosure);
        List<SpotCandidate> spots = SpawnerPathfinder.findBestStandSpots(
                level, player, chestPos, enclosure, false, false, startStand
        );

        List<BlockPos> chosenPath = null;
        for (int i = 0; i < Math.min(spots.size(), 10); i++) {
            SpotCandidate cand = spots.get(i);
            List<BlockPos> p = SpawnerPathfinder.findSpawnerRoomPath(level, startStand, cand.spot(), enclosure, 2000);
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
            waitTicks = 4;
            return;
        }
        if (nav.currentNavTarget == null) {
            chestIndex++;
            state = State.FIND_NEXT_CHEST;
            return;
        }

        Direction face = SpawnerPathfinder.getExposedSpawnerClickFace(level, player, nav.currentNavTarget);
        Vec3 hitVec = Vec3.atCenterOf(nav.currentNavTarget);
        boolean aimed = lookAtPointGcd(player, hitVec);

        stateTimeoutTicks++;
        if ((!aimed && stateTimeoutTicks < 28) || stateTimeoutTicks < 3) {
            return;
        }
        stateTimeoutTicks = 0;

        BlockHitResult hit = new BlockHitResult(hitVec, face, nav.currentNavTarget, false);
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getInteractAnimation(), false);

        waitTicks = 2;
        state = State.WAIT_CHEST_GUI;
    }

    private void tickWaitChestGui(Minecraft mc, LocalPlayer player) {
        clearMovementFlags();
        if (getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen) {
            if (isMinerChestWindow(containerScreen)) {
                closeOpenScreenIfAny(mc, player);
                chestIndex++;
                waitTicks = 4;
                state = State.FIND_NEXT_CHEST;
                return;
            }
            AbstractContainerMenu menu = containerScreen.getMenu();
            chestDepositSlotIdx = Math.max(0, menu.slots.size() - 36);
            chestPrevSlotCount = -1;
            waitTicks = 5;
            state = State.DEPOSIT_IRON_BLOCKS;
            return;
        }

        stateTimeoutTicks++;
        if (stateTimeoutTicks > 100) { // ~5s timeout
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
                waitTicks = 4;
                state = State.FIND_NEXT_CHEST;
                return;
            }
            int remaining = prevAfter.isEmpty() ? 0 : prevAfter.getCount();
            if (chestPrevSlotCount > remaining) {
                totalDepositedBlocks += (chestPrevSlotCount - remaining);
            }
            chestDepositSlotIdx++;
            chestPrevSlotCount = -1;
        }

        while (chestDepositSlotIdx < invEnd) {
            ItemStack st = menu.getSlot(chestDepositSlotIdx).getItem();
            if (!st.isEmpty() && st.is(Items.IRON_BLOCK)) {
                chestPrevSlotCount = st.getCount();
                gameMode.handleContainerInput(menu.containerId, chestDepositSlotIdx, 0, ContainerInput.QUICK_MOVE, player);
                waitTicks = humanDelay(Math.max(2, clickDelayTicks), 2);
                return;
            }
            chestDepositSlotIdx++;
        }

        // All iron_block stacks deposited from inventory
        if (nav.currentNavTarget != null) {
            BlockPos chestKey = nav.currentNavTarget.immutable();
            chestDepositCounts.merge(chestKey, 1, Integer::sum);
            BlockPos partner = SpawnerRoomScanner.getConnectedChestPos(mc.level, nav.currentNavTarget);
            if (partner != null) {
                chestDepositCounts.merge(partner, 1, Integer::sum);
            }
        }
        closeOpenScreenIfAny(mc, player);
        waitTicks = 4;
        if (countPlayerInventoryItem(player, Items.IRON_BLOCK) <= 0) {
            if (!waitingToFinishCurrentSpawner && pendingGreenSpawner != null) {
                openedSpawners.add(pendingGreenSpawner);
                pendingGreenSpawner = null;
            }
            if (spawnerIndex < spawners.size()) {
                prepareSpawnerNavigation(mc.level, player);
            } else {
                state = State.COOLDOWN_NEXT_PASS;
            }
        } else {
            chestIndex++;
            state = State.FIND_NEXT_CHEST;
        }
    }

    // --- Helpers ---

    private List<BlockPos> scanRoomChests(
            ClientLevel level,
            LocalPlayer player,
            BlockPos origin,
            SpawnerEnclosure enc
    ) {
        int radius = Math.max(searchRadius, 26);
        List<BlockPos> raw = SpawnerRoomScanner.findNearbyContainerPositions(
                level, player.position(), origin, radius
        );
        if (enc == null || raw.isEmpty()) return raw;
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
        return bounded.isEmpty() ? raw : bounded;
    }

    private void advanceSpawnerAttemptOrSkip(ClientLevel level, LocalPlayer player) {
        if (spawnerAttempt < 3) {
            spawnerAttempt++;
            waitTicks = 5;
            prepareSpawnerNavigation(level, player);
        } else {
            spawnerIndex++;
            spawnerAttempt = 1;
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

        float smoothFactor = isWalking ? 0.16f : 0.22f;
        float maxYawStep = isWalking ? 11.0f : 14.0f;
        float maxPitchStep = isWalking ? 7.0f : 10.0f;

        float stepYaw = Mth.clamp(dYaw * smoothFactor, -maxYawStep, maxYawStep);
        float stepPitch = Mth.clamp(dPitch * smoothFactor, -maxPitchStep, maxPitchStep);

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

    public String getStatusLabelEnglish() {
        return switch (state) {
            case SCANNING_ROOM -> "Scanning room";
            case MOVE_TO_SPAWNER -> "Moving to spawner";
            case INTERACT_SPAWNER, WAIT_MANAGEMENT_GUI, CLICK_OPEN_DEPO, WAIT_DEPO_GUI -> "Opening spawner";
            case LOOT_IRON_INGOTS -> "Looting iron";
            case SEND_CRAFT_CMD, WAIT_CRAFT_GUI, CRAFT_SPLIT_AND_FILL -> "Crafting blocks";
            case FIND_NEXT_CHEST, MOVE_TO_CHEST -> "Moving to chest";
            case INTERACT_CHEST, WAIT_CHEST_GUI, DEPOSIT_IRON_BLOCKS -> "Depositing";
            case COOLDOWN_NEXT_PASS -> "Next pass";
        };
    }

    public String getFormattedElapsedTime() {
        return SpawnerNavHelper.formatElapsedTime(enabledStartMs);
    }

    private static final int HUD_MIN_WIDTH = 138;
    private static final int HUD_DEFAULT_X = 8;
    private static final int HUD_DEFAULT_Y = 128;

    private static List<JelloTheme.HudStatRow> buildHudRows(AutoSpawnerModule mod) {
        boolean active = mod != null && mod.isEnabled();
        String spVal = active ? (mod.openedSpawners.size() + "/" + mod.spawners.size()) : "0/64";
        float spProgress = active
                ? (mod.spawners.isEmpty() ? 0.0f : (float) mod.openedSpawners.size() / mod.spawners.size())
                : 0.61f;
        String statusVal = active ? mod.getStatusLabelEnglish() : "Looting iron";
        String depVal = active ? Integer.toString(mod.totalDepositedBlocks) : "0";
        String timeVal = active ? mod.getFormattedElapsedTime() : "00:00";

        int statusColor = active ? switch (mod.state) {
            case SCANNING_ROOM, MOVE_TO_SPAWNER, INTERACT_SPAWNER, WAIT_MANAGEMENT_GUI,
                    CLICK_OPEN_DEPO, WAIT_DEPO_GUI, LOOT_IRON_INGOTS -> 0xFF22C55E;
            case SEND_CRAFT_CMD, WAIT_CRAFT_GUI, CRAFT_SPLIT_AND_FILL,
                    FIND_NEXT_CHEST, MOVE_TO_CHEST, INTERACT_CHEST, WAIT_CHEST_GUI,
                    DEPOSIT_IRON_BLOCKS -> 0xFFF59E0B;
            case COOLDOWN_NEXT_PASS -> 0xFF38BDF8;
        } : 0xFF22C55E;

        boolean showBar = ModConfig.getInstance().hudUnifiedGlass;
        return List.of(
                new JelloTheme.HudStatRow("Spawner", spVal, JelloTheme.ACCENT_HOVER, 0xFFFFFFFF, showBar ? spProgress : -1.0f),
                new JelloTheme.HudStatRow("Status", statusVal, statusColor, statusColor),
                new JelloTheme.HudStatRow("Deposited", depVal, 0xFFF59E0B, 0xFFF59E0B),
                new JelloTheme.HudStatRow("Time", timeVal, 0xFFD8D8D8, 0xFFFFFFFF)
        );
    }

    public static int[] resolveHudBounds(Font font, int screenW, int screenH, ModConfig cfg) {
        AutoSpawnerModule mod = getInstance();
        int pass = (mod != null && mod.isEnabled()) ? Math.max(1, mod.passNumber) : 1;
        String title = "Auto Spawner #" + pass;
        return resolveHudBoundsForRows(font, screenW, screenH, cfg, title, buildHudRows(mod));
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
        String title = "Auto Spawner #" + Math.max(1, mod.passNumber);
        List<JelloTheme.HudStatRow> rows = buildHudRows(mod);
        int[] bounds = resolveHudBoundsForRows(mc.font, graphics.guiWidth(), graphics.guiHeight(), cfg, title, rows);
        JelloTheme.renderHudCard(graphics, mc.font, bounds[0], bounds[1], bounds[2], bounds[3], title, rows);
    }
}
