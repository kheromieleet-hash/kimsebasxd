package com.client.mod;

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
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scans all spawners in the room and toggles each spawner's Auto-Sell setting
 * to either Gold Block (Acik) or Gold Nugget (Kapali), then stops automatically.
 */
public class SpawnerAutoSellModule {

    public enum State {
        SCANNING_ROOM,
        MOVE_TO_SPAWNER,
        INTERACT_SPAWNER,
        WAIT_MANAGEMENT_GUI,
        TOGGLE_AUTO_SELL
    }

    private static final SpawnerAutoSellModule INSTANCE = new SpawnerAutoSellModule();

    public static SpawnerAutoSellModule getInstance() {
        return INSTANCE;
    }

    public boolean enabled = false;
    public boolean targetGoldBlock = true; // true = Gold Block (Acik), false = Gold Nugget (Kapali)
    public int searchRadius = 26;
    public boolean lockMouse = true;
    public boolean renderEsp = true;

    private State state = State.SCANNING_ROOM;
    private int waitTicks = 0;
    private int stateTimeoutTicks = 0;

    private SpawnerEnclosure enclosure = null;
    private List<BlockPos> spawners = new ArrayList<>();
    private final Set<BlockPos> completedSpawners = new HashSet<>();
    private int spawnerIndex = 0;
    private int spawnerAttempt = 1;
    private int lastScanCount = -1;
    private long enabledStartMs = 0L;

    // Navigation & Movement Helper
    private final SpawnerNavHelper nav = new SpawnerNavHelper();

    // Camera Restore & GUI Toggle Tracking
    private Float savedYaw = null;
    private Float savedPitch = null;
    private boolean clickedToggleThisOpen = false;
    private int toggleConfirmTicks = 0;

    private SpawnerAutoSellModule() {
    }

    public boolean isEnabled() {
        return enabled && !PanicHandler.isPanicActive();
    }

    public boolean isActiveLockingMouse() {
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        boolean activeLock = cfg != null ? (cfg.spawnerAutoSellEnabled && cfg.spawnerAutoSellLockMouse) : (enabled && lockMouse);
        return enabled && activeLock && !PanicHandler.isPanicActive()
                && mc != null && mc.player != null && getCurrentScreen(mc) == null;
    }

    public boolean isControllingMovement() {
        Minecraft mc = Minecraft.getInstance();
        ModConfig cfg = ModConfig.getInstance();
        boolean cfgEnabled = cfg == null || cfg.spawnerAutoSellEnabled;
        return enabled && cfgEnabled && !PanicHandler.isPanicActive()
                && mc != null && mc.player != null && mc.level != null;
    }

    public boolean wantsMoveForward() { return nav.moveForward; }
    public boolean wantsMoveBackward() { return nav.moveBackward; }
    public boolean wantsMoveLeft() { return nav.moveLeft; }
    public boolean wantsMoveRight() { return nav.moveRight; }
    public boolean wantsJump() { return nav.wantJump; }
    public boolean wantsSprint() { return nav.wantSprint; }

    public List<BlockPos> getScannedSpawners() {
        return new ArrayList<>(spawners);
    }

    public Set<BlockPos> getCompletedSpawners() {
        return new HashSet<>(completedSpawners);
    }

    public List<BlockPos> getActivePath() {
        return nav.activePath;
    }

    public void setEnabledFromConfig(boolean val) {
        if (val && !PanicHandler.isPanicActive()) {
            ModConfig cfg = ModConfig.getInstance();
            if (cfg != null && cfg.autoSpawnerEnabled) {
                cfg.autoSpawnerEnabled = false;
                AutoSpawnerModule.getInstance().setEnabledFromConfig(false);
            }
            if (!this.enabled) {
                onEnable();
            }
        } else if (this.enabled) {
            onDisable();
        } else {
            clearMovementFlags();
        }
    }

    public void onLeftWorld() {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg != null && cfg.spawnerAutoSellEnabled) {
            cfg.spawnerAutoSellEnabled = false;
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
        boolean targetEnabled = cfg.spawnerAutoSellEnabled && !PanicHandler.isPanicActive();
        if (targetEnabled != this.enabled) {
            setEnabledFromConfig(targetEnabled);
        }
        this.targetGoldBlock = cfg.spawnerAutoSellTargetGoldBlock;
        this.searchRadius = Mth.clamp(cfg.spawnerAutoSellSearchRadius, 12, 40);
        this.lockMouse = cfg.spawnerAutoSellLockMouse;
        this.renderEsp = cfg.spawnerAutoSellRenderEsp;
    }

    private void resetAllRuntimeState() {
        state = State.SCANNING_ROOM;
        waitTicks = 0;
        stateTimeoutTicks = 0;
        enclosure = null;
        spawners = new ArrayList<>();
        completedSpawners.clear();
        spawnerIndex = 0;
        spawnerAttempt = 1;
        lastScanCount = -1;
        enabledStartMs = 0L;
        savedYaw = null;
        savedPitch = null;
        clickedToggleThisOpen = false;
        toggleConfirmTicks = 0;
        nav.resetAll(4.35);
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

        if (getCurrentScreen(mc) != null) {
            clearMovementFlags();
        }

        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        switch (state) {
            case SCANNING_ROOM -> tickScanningRoom(mc, level, player);
            case MOVE_TO_SPAWNER -> tickMoveToSpawner(mc, level, player);
            case INTERACT_SPAWNER -> tickInteractSpawner(mc, level, player, gameMode);
            case WAIT_MANAGEMENT_GUI -> tickWaitManagementGui(mc, player);
            case TOGGLE_AUTO_SELL -> tickToggleAutoSell(mc, player, gameMode);
        }
    }

    private void tickScanningRoom(Minecraft mc, ClientLevel level, LocalPlayer player) {
        clearMovementFlags();
        if (getCurrentScreen(mc) != null) player.closeContainer();

        BlockPos rawHome = SpawnerRoomScanner.getSpawnerBotStandPos(level, player, enclosure);
        if (rawHome == null) rawHome = player.blockPosition();
        BlockPos anchor = (enclosure != null && enclosure.anchorPos != null) ? enclosure.anchorPos : rawHome;

        List<BlockPos> found = SpawnerRoomScanner.findNearbySpawnerBlocks(level, anchor, searchRadius, 0, "");
        if (found.isEmpty()) {
            found = SpawnerRoomScanner.findNearbySpawnerBlocks(level, anchor, Math.max(searchRadius, 36), 0, "");
        }
        if (found.isEmpty()) {
            lastScanCount = -1;
            waitTicks = 40;
            return;
        }

        this.spawners = found;
        this.enclosure = SpawnerRoomScanner.computeSpawnerEnclosure(level, player, found, anchor, enclosure);

        if (lastScanCount != found.size()) {
            lastScanCount = found.size();
            waitTicks = 4;
            return;
        }

        lastScanCount = -1;
        this.completedSpawners.clear();
        this.spawnerIndex = 0;
        this.spawnerAttempt = 1;
        prepareNextSpawner(level, player);
    }

    private void prepareNextSpawner(ClientLevel level, LocalPlayer player) {
        clearMovementFlags();
        if (spawnerIndex >= spawners.size()) {
            finishAllAndStop();
            return;
        }

        BlockPos targetSpawner = spawners.get(spawnerIndex);
        nav.currentNavTarget = targetSpawner;
        nav.activePathAllowsSpawnerFloor = true;
        nav.stopHorizReach = 1.98;
        nav.stopEyeReach = 4.35;

        if (SpawnerNavHelper.isAlreadyInOptimalReach(level, player, targetSpawner, true, spawnerAttempt >= 2, enclosure)) {
            stateTimeoutTicks = 0;
            state = State.INTERACT_SPAWNER;
            return;
        }

        List<BlockPos> selectedPath = nav.selectSpawnerPath(level, player, targetSpawner, enclosure, spawnerAttempt >= 2);
        if (selectedPath == null || selectedPath.isEmpty()) {
            if (SpawnerNavHelper.canFallbackInteractDirectly(player, targetSpawner)) {
                stateTimeoutTicks = 0;
                state = State.INTERACT_SPAWNER;
            } else {
                advanceAttemptOrSkip(level, player);
            }
            return;
        }

        nav.startPathWalk(player, selectedPath, Math.max(100, Math.min(360, (selectedPath.size() + 3) * 11)));
        state = State.MOVE_TO_SPAWNER;
    }

    private void tickMoveToSpawner(Minecraft mc, ClientLevel level, LocalPlayer player) {
        if (getCurrentScreen(mc) != null) {
            player.closeContainer();
            clearMovementFlags();
            waitTicks = 2;
            return;
        }
        if (nav.currentNavTarget == null) {
            clearMovementFlags();
            state = State.SCANNING_ROOM;
            return;
        }

        switch (nav.tickWalk(level, player, enclosure, true)) {
            case UNSTUCK_RESTART -> prepareNextSpawner(level, player);
            case REACHED -> {
                stateTimeoutTicks = 0;
                state = State.INTERACT_SPAWNER;
            }
            case ABORTED -> advanceAttemptOrSkip(level, player);
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
            player.closeContainer();
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
        boolean aimed = AutoSpawnerModule.lookAtPointGcd(player, hitVec);

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
        if (getCurrentScreen(mc) instanceof AbstractContainerScreen<?>) {
            clickedToggleThisOpen = false;
            toggleConfirmTicks = 0;
            waitTicks = AutoSpawnerModule.humanDelay(3, 1);
            state = State.TOGGLE_AUTO_SELL;
            return;
        }

        stateTimeoutTicks++;
        if (stateTimeoutTicks > 56) {
            stateTimeoutTicks = 0;
            advanceAttemptOrSkip(mc.level, player);
        }
    }

    private void tickToggleAutoSell(Minecraft mc, LocalPlayer player, MultiPlayerGameMode gameMode) {
        clearMovementFlags();
        if (!(getCurrentScreen(mc) instanceof AbstractContainerScreen<?> containerScreen)) {
            if (clickedToggleThisOpen && nav.currentNavTarget != null) {
                completedSpawners.add(nav.currentNavTarget.immutable());
                spawnerIndex++;
                spawnerAttempt = 1;
            }
            prepareNextSpawner(mc.level, player);
            return;
        }

        AbstractContainerMenu menu = containerScreen.getMenu();
        int containerEnd = Math.max(0, menu.slots.size() - 36);
        int sellSlot = findAutoSellSlot(menu, containerEnd);
        if (sellSlot == -1) {
            player.closeContainer();
            waitTicks = 3;
            advanceAttemptOrSkip(mc.level, player);
            return;
        }

        ItemStack stack = menu.getSlot(sellSlot).getItem();
        boolean isGoldBlock = stack.is(Items.GOLD_BLOCK);
        boolean isGoldNugget = stack.is(Items.GOLD_NUGGET);
        boolean alreadyTarget = targetGoldBlock ? isGoldBlock : isGoldNugget;

        if (alreadyTarget) {
            player.closeContainer();
            if (nav.currentNavTarget != null) {
                completedSpawners.add(nav.currentNavTarget.immutable());
            }
            spawnerIndex++;
            spawnerAttempt = 1;
            waitTicks = AutoSpawnerModule.humanDelay(3, 2);
            prepareNextSpawner(mc.level, player);
            return;
        }

        if (!clickedToggleThisOpen) {
            clickedToggleThisOpen = true;
            toggleConfirmTicks = 0;
            gameMode.handleContainerInput(menu.containerId, sellSlot, 0, ContainerInput.PICKUP, player);
            waitTicks = AutoSpawnerModule.humanDelay(3, 2);
            return;
        }

        toggleConfirmTicks++;
        if (toggleConfirmTicks >= 16) {
            player.closeContainer();
            if (nav.currentNavTarget != null) {
                completedSpawners.add(nav.currentNavTarget.immutable());
            }
            spawnerIndex++;
            spawnerAttempt = 1;
            waitTicks = 3;
            prepareNextSpawner(mc.level, player);
        }
    }

    private static int findAutoSellSlot(AbstractContainerMenu menu, int containerEnd) {
        if (containerEnd > 14 && !menu.getSlot(14).getItem().isEmpty()) {
            ItemStack s14 = menu.getSlot(14).getItem();
            if (s14.is(Items.GOLD_BLOCK) || s14.is(Items.GOLD_NUGGET) || isAutoSellItemText(s14)) {
                return 14;
            }
        }
        for (int i = 0; i < containerEnd; i++) {
            ItemStack st = menu.getSlot(i).getItem();
            if (st.isEmpty()) continue;
            if (st.is(Items.GOLD_BLOCK) || st.is(Items.GOLD_NUGGET) || isAutoSellItemText(st)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isAutoSellItemText(ItemStack st) {
        StringBuilder sb = new StringBuilder();
        sb.append(st.getHoverName().getString()).append(' ');
        Component customName = st.get(DataComponents.CUSTOM_NAME);
        if (customName != null) sb.append(customName.getString()).append(' ');
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore != null) {
            for (Component line : lore.lines()) {
                sb.append(line.getString()).append(' ');
            }
        }
        return AutoSpawnerModule.normalizeTurkishText(sb.toString()).contains("otomatik satis");
    }

    private void finishAllAndStop() {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg != null && cfg.spawnerAutoSellEnabled) {
            cfg.spawnerAutoSellEnabled = false;
            cfg.save();
        }
        onDisable();
    }

    private void advanceAttemptOrSkip(ClientLevel level, LocalPlayer player) {
        if (spawnerAttempt < 3) {
            spawnerAttempt++;
            waitTicks = 5;
            prepareNextSpawner(level, player);
        } else {
            spawnerIndex++;
            spawnerAttempt = 1;
            prepareNextSpawner(level, player);
        }
    }

    public String getStatusLabelEnglish() {
        return switch (state) {
            case SCANNING_ROOM -> "Scanning room";
            case MOVE_TO_SPAWNER -> "Moving to spawner";
            case INTERACT_SPAWNER, WAIT_MANAGEMENT_GUI -> "Opening spawner";
            case TOGGLE_AUTO_SELL -> "Toggling sell";
        };
    }

    public String getFormattedElapsedTime() {
        return SpawnerNavHelper.formatElapsedTime(enabledStartMs);
    }

    private static final String HUD_TITLE = "Spawner Mode";
    private static final int HUD_MIN_WIDTH = 156;
    private static final int HUD_DEFAULT_X = 8;

    private static List<JelloTheme.HudStatRow> buildHudRows(SpawnerAutoSellModule mod, ModConfig cfg) {
        boolean active = mod != null && mod.isEnabled();
        boolean goldBlock = active ? mod.targetGoldBlock : (cfg == null || cfg.spawnerAutoSellTargetGoldBlock);
        String targetVal = goldBlock ? "Gold Block (ON)" : "Gold Nugget (OFF)";
        int targetColor = goldBlock ? 0xFF22C55E : 0xFFF59E0B;
        String spVal = active ? (mod.completedSpawners.size() + "/" + mod.spawners.size()) : "0/62";
        float spProgress = active
                ? (mod.spawners.isEmpty() ? 0.0f : (float) mod.completedSpawners.size() / mod.spawners.size())
                : 0.31f;
        String statusVal = active ? mod.getStatusLabelEnglish() : "Moving to spawner";
        String timeVal = active ? mod.getFormattedElapsedTime() : "00:00";

        int statusColor = active ? switch (mod.state) {
            case SCANNING_ROOM, MOVE_TO_SPAWNER, INTERACT_SPAWNER, WAIT_MANAGEMENT_GUI -> 0xFF22C55E;
            case TOGGLE_AUTO_SELL -> 0xFFF59E0B;
        } : 0xFF22C55E;

        boolean showBar = cfg == null || cfg.hudUnifiedGlass;
        return List.of(
                new JelloTheme.HudStatRow("Target", targetVal, targetColor, targetColor),
                new JelloTheme.HudStatRow("Spawner", spVal, JelloTheme.ACCENT_HOVER, 0xFFFFFFFF, showBar ? spProgress : -1.0f),
                new JelloTheme.HudStatRow("Status", statusVal, statusColor, statusColor),
                new JelloTheme.HudStatRow("Time", timeVal, 0xFFD8D8D8, 0xFFFFFFFF)
        );
    }

    public static int[] resolveHudBounds(Font font, int screenW, int screenH, ModConfig cfg, boolean editorMode) {
        SpawnerAutoSellModule mod = getInstance();
        return resolveHudBoundsForRows(font, screenW, screenH, cfg, editorMode, buildHudRows(mod, cfg));
    }

    private static int[] resolveHudBoundsForRows(
            Font font,
            int screenW,
            int screenH,
            ModConfig cfg,
            boolean editorMode,
            List<JelloTheme.HudStatRow> rows
    ) {
        boolean autoSpawnerActive = AutoSpawnerModule.getInstance() != null && AutoSpawnerModule.getInstance().isEnabled();
        int defaultY = (editorMode || autoSpawnerActive) ? ((cfg == null || cfg.autoSpawnerHudDetailed) ? 256 : 206) : 128;
        int cfgX = cfg != null ? cfg.spawnerAutoSellHudX : -1;
        int cfgY = cfg != null ? cfg.spawnerAutoSellHudY : -1;
        return JelloTheme.resolveHudCardBounds(
                font, screenW, screenH, HUD_TITLE, rows,
                HUD_MIN_WIDTH, cfgX, cfgY, HUD_DEFAULT_X, defaultY
        );
    }

    public static void renderHud(GuiGraphicsExtractor graphics) {
        if (PanicHandler.isPanicActive()) return;
        SpawnerAutoSellModule mod = getInstance();
        if (mod == null || !mod.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.font == null) return;
        if (mc.gui != null && (ClickGuiScreen.isTargetScreen(mc.gui.screen()) || mc.gui.screen() instanceof HudLayoutEditorScreen)) {
            return;
        }

        ModConfig cfg = ModConfig.getInstance();
        List<JelloTheme.HudStatRow> rows = buildHudRows(mod, cfg);
        int[] bounds = resolveHudBoundsForRows(mc.font, graphics.guiWidth(), graphics.guiHeight(), cfg, false, rows);
        JelloTheme.renderHudCard(graphics, mc.font, bounds[0], bounds[1], bounds[2], bounds[3], HUD_TITLE, rows);
    }
}
