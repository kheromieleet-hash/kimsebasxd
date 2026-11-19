package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import dlovin.inventoryhud.InventoryHUDCommon;
import dlovin.inventoryhud.gui.InventoryGui;
import dlovin.inventoryhud.platform.ConfigAccess;
import dlovin.inventoryhud.utils.WidgetAligns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Unified full-screen drag-and-drop HUD layout editor for Keybinds HUD,
 * Telemetry HUD (FPS/TPS/Ping/Speed), Position HUD (Direction/Nether/Pos),
 * and Inventory HUD+ overlays (Inventory, Potions, Armor).
 */
public class HudLayoutEditorScreen extends Screen {

    private enum HudTarget {
        NONE,
        KEYBINDS,
        WATERMARK,
        ARRAY_LIST,
        PLAYER_LIST,
        AUTO_SPAWNER,
        SPAWNER_MODE,
        STATS,
        POSITION,
        CLOCK,
        ITEM_NOTIFIER,
        INVENTORY,
        POTIONS,
        ARMOR
    }

    private final Screen parent;
    private HudTarget dragging = HudTarget.NONE;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;
    private Integer activeGuideX = null;
    private Integer activeGuideY = null;

    public HudLayoutEditorScreen(Screen parent) {
        super(Component.literal("HUD Layout Editor"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ModConfig.getInstance().save();
        saveInventoryHudConfig();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.getInstance();
        if (this.dragging != HudTarget.NONE) {
            updateDraggedPosition(config, mouseX, mouseY);
        }

        // Dimmed backdrop
        graphics.fill(0, 0, this.width, this.height, 0x7807090E);

        // 3x3 dashed alignment grid (1/3 & 2/3 guides)
        drawDashedGrid(graphics);

        int[] kbBounds = getKeybindsBounds(config);
        int[] statsBounds = StatsHudRenderer.resolveStatsBounds(this.font, this.width, this.height, config);
        int[] posBounds = StatsHudRenderer.resolvePositionBounds(this.font, this.width, this.height, config);

        drawDraggableBox(
                graphics, mouseX, mouseY,
                kbBounds[0], kbBounds[1], kbBounds[2], kbBounds[3],
                "Keybinds",
                config.keybindsHudEnabled,
                this.dragging == HudTarget.KEYBINDS,
                0x55FF1E2D, 0xFFFF4B5C
        );

        int[] wmBounds = WatermarkArrayListHudRenderer.resolveWatermarkBounds(this.font, this.width, this.height, config);
        drawDraggableBox(
                graphics, mouseX, mouseY,
                wmBounds[0], wmBounds[1], wmBounds[2], wmBounds[3],
                "Watermark",
                config.hudWatermarkEnabled,
                this.dragging == HudTarget.WATERMARK,
                0x55EF4444, 0xFFF87171
        );

        int[] arrBounds = WatermarkArrayListHudRenderer.resolveArrayListBounds(this.font, this.width, this.height, config);
        drawDraggableBox(
                graphics, mouseX, mouseY,
                arrBounds[0], arrBounds[1], arrBounds[2], arrBounds[3],
                "Modules",
                config.hudArrayListEnabled,
                this.dragging == HudTarget.ARRAY_LIST,
                0x488B5CF6, 0xDDA78BFA
        );

        drawDraggableBox(
                graphics, mouseX, mouseY,
                statsBounds[0], statsBounds[1], statsBounds[2], statsBounds[3],
                "Telemetry",
                config.hudInfoEnabled && config.hudTelemetryEnabled,
                this.dragging == HudTarget.STATS,
                0x4436D37C, 0xDD4CF094
        );

        drawDraggableBox(
                graphics, mouseX, mouseY,
                posBounds[0], posBounds[1], posBounds[2], posBounds[3],
                "Position",
                config.hudInfoEnabled && config.hudPositionEnabled,
                this.dragging == HudTarget.POSITION,
                0x484F6BFF, 0xDD7B92FF
        );

        int[] clockBounds = StatsHudRenderer.resolveClockBounds(this.font, this.width, this.height, config);
        drawDraggableBox(
                graphics, mouseX, mouseY,
                clockBounds[0], clockBounds[1], clockBounds[2], clockBounds[3],
                "Clock",
                config.hudInfoEnabled && config.hudShowClock,
                this.dragging == HudTarget.CLOCK,
                0x48F59E0B, 0xDDFBBF24
        );

        int[] itemNotifierBounds = ItemNotifierHudRenderer.resolveBounds(this.font, this.width, this.height, config);
        drawDraggableBox(
                graphics, mouseX, mouseY,
                itemNotifierBounds[0], itemNotifierBounds[1], itemNotifierBounds[2], itemNotifierBounds[3],
                "Item Notifier",
                config.itemNotifierEnabled,
                this.dragging == HudTarget.ITEM_NOTIFIER,
                0x4810B981, 0xDD34D399
        );

        int[] playerListBounds = PlayerListHudRenderer.resolveBounds(this.font, this.width, this.height, config);
        drawDraggableBox(
                graphics, mouseX, mouseY,
                playerListBounds[0], playerListBounds[1], playerListBounds[2], playerListBounds[3],
                "Player List",
                config.playerListEnabled,
                this.dragging == HudTarget.PLAYER_LIST,
                0x4806B6D4, 0xDD22D3EE
        );

        int[] autoSpawnerBounds = AutoSpawnerModule.resolveHudBounds(this.font, this.width, this.height, config);
        drawDraggableBox(
                graphics, mouseX, mouseY,
                autoSpawnerBounds[0], autoSpawnerBounds[1], autoSpawnerBounds[2], autoSpawnerBounds[3],
                "Auto Spawner",
                config.autoSpawnerEnabled,
                this.dragging == HudTarget.AUTO_SPAWNER,
                0x48F43F5E, 0xDDFB7185
        );

        int[] spawnerModeBounds = SpawnerAutoSellModule.resolveHudBounds(this.font, this.width, this.height, config, true);
        drawDraggableBox(
                graphics, mouseX, mouseY,
                spawnerModeBounds[0], spawnerModeBounds[1], spawnerModeBounds[2], spawnerModeBounds[3],
                "Spawner Mode",
                config.spawnerAutoSellEnabled,
                this.dragging == HudTarget.SPAWNER_MODE,
                0x48F59E0B, 0xDDFBBF24
        );

        // Inventory HUD+ overlays (Inventory, Potions, Armor)
        int[] invBounds = getInventoryBounds(config);
        if (invBounds != null) {
            drawDraggableBox(
                    graphics, mouseX, mouseY,
                    invBounds[0], invBounds[1], invBounds[2], invBounds[3],
                    "Inventory",
                    config.inventoryHudEnabled && InventoryHUDCommon.isActive,
                    this.dragging == HudTarget.INVENTORY,
                    0x5032D74B, 0xEE5AF270
            );
        }

        int[] potBounds = getPotionsBounds();
        if (potBounds != null) {
            drawDraggableBox(
                    graphics, mouseX, mouseY,
                    potBounds[0], potBounds[1], potBounds[2], potBounds[3],
                    "Potions",
                    config.inventoryHudEnabled && InventoryHUDCommon.potionHUD,
                    this.dragging == HudTarget.POTIONS,
                    0x50E06C75, 0xEEF08C95
            );
        }

        int[] armBounds = getArmorBounds();
        if (armBounds != null) {
            drawDraggableBox(
                    graphics, mouseX, mouseY,
                    armBounds[0], armBounds[1], armBounds[2], armBounds[3],
                    "Armor",
                    config.inventoryHudEnabled && InventoryHUDCommon.armorHUD,
                    this.dragging == HudTarget.ARMOR,
                    0x506366F1, 0xEE8B8DFF
            );
        }

        // Bottom-center floating toolbar (Reset All + Done)
        int[] resetBtn = getResetButtonBounds();
        int[] doneBtn = getDoneButtonBounds();
        int barX = resetBtn[0] - 8;
        int barY = resetBtn[1] - 6;
        int barW = (doneBtn[0] + doneBtn[2]) - resetBtn[0] + 16;
        int barH = resetBtn[3] + 12;

        JelloTheme.fillRoundedCard(graphics, barX, barY, barX + barW, barY + barH, 8, 0xE610131C, 0x99FF1E2D);

        boolean resetHover = isInside(mouseX, mouseY, resetBtn);
        boolean doneHover = isInside(mouseX, mouseY, doneBtn);
        JelloTheme.fillRoundedCard(
                graphics, resetBtn[0], resetBtn[1], resetBtn[0] + resetBtn[2], resetBtn[1] + resetBtn[3],
                5, resetHover ? 0x55FF3D4E : 0x28FFFFFF, 0x66FFFFFF
        );
        JelloTheme.fillRoundedCard(
                graphics, doneBtn[0], doneBtn[1], doneBtn[0] + doneBtn[2], doneBtn[1] + doneBtn[3],
                5, doneHover ? 0xFFFF3D4E : 0xDDFF1E2D, 0xFFFF6674
        );

        String resetLabel = "Reset";
        String doneLabel = "Done (ESC)";
        graphics.text(this.font, Component.literal(resetLabel),
                resetBtn[0] + (resetBtn[2] - this.font.width(resetLabel)) / 2,
                resetBtn[1] + 6, 0xFFFFFFFF, false);
        graphics.text(this.font, Component.literal(doneLabel),
                doneBtn[0] + (doneBtn[2] - this.font.width(doneLabel)) / 2,
                doneBtn[1] + 6, 0xFFFFFFFF, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        if (MouseButtonMapping.isRightClick(event.button())) {
            if (handleRightClickAt((int) event.x(), (int) event.y())) {
                return true;
            }
        }
        if (MouseButtonMapping.isLeftClick(event.button()) || event.button() == 0) {
            if (handlePressAt((int) event.x(), (int) event.y())) {
                return true;
            }
            if (this.dragging != HudTarget.NONE) {
                return true;
            }
        }
        return super.mouseClicked(event, wasHandled);
    }

    private boolean handleRightClickAt(int mouseX, int mouseY) {
        if (isInside(mouseX, mouseY, getResetButtonBounds()) || isInside(mouseX, mouseY, getDoneButtonBounds())) {
            return false;
        }
        ModConfig config = ModConfig.getInstance();

        // Hit-test in reverse draw order so topmost overlay receives the click.
        int[] arm = getArmorBounds();
        if (arm != null && isInside(mouseX, mouseY, arm)) {
            toggleInventoryComponent(config, HudTarget.ARMOR);
            return true;
        }
        int[] pot = getPotionsBounds();
        if (pot != null && isInside(mouseX, mouseY, pot)) {
            toggleInventoryComponent(config, HudTarget.POTIONS);
            return true;
        }
        int[] inv = getInventoryBounds(config);
        if (inv != null && isInside(mouseX, mouseY, inv)) {
            toggleInventoryComponent(config, HudTarget.INVENTORY);
            return true;
        }
        if (isInside(mouseX, mouseY, SpawnerAutoSellModule.resolveHudBounds(this.font, this.width, this.height, config, true))) {
            boolean next = !config.spawnerAutoSellEnabled;
            config.spawnerAutoSellEnabled = next;
            SpawnerAutoSellModule mod = SpawnerAutoSellModule.getInstance();
            if (mod != null) {
                mod.setEnabledFromConfig(next);
            }
            config.save();
            return true;
        }
        if (isInside(mouseX, mouseY, AutoSpawnerModule.resolveHudBounds(this.font, this.width, this.height, config))) {
            boolean next = !config.autoSpawnerEnabled;
            config.autoSpawnerEnabled = next;
            AutoSpawnerModule mod = AutoSpawnerModule.getInstance();
            if (mod != null) {
                mod.setEnabledFromConfig(next);
            }
            config.save();
            return true;
        }
        if (isInside(mouseX, mouseY, PlayerListHudRenderer.resolveBounds(this.font, this.width, this.height, config))) {
            config.playerListEnabled = !config.playerListEnabled;
            config.save();
            return true;
        }
        if (isInside(mouseX, mouseY, ItemNotifierHudRenderer.resolveBounds(this.font, this.width, this.height, config))) {
            config.itemNotifierEnabled = !config.itemNotifierEnabled;
            if (!config.itemNotifierEnabled) {
                ItemNotifierHudRenderer.reset();
            }
            config.save();
            return true;
        }
        if (isInside(mouseX, mouseY, StatsHudRenderer.resolveClockBounds(this.font, this.width, this.height, config))) {
            if (!config.hudInfoEnabled) {
                config.hudInfoEnabled = true;
                config.hudShowClock = true;
            } else {
                config.hudShowClock = !config.hudShowClock;
            }
            config.save();
            return true;
        }
        if (isInside(mouseX, mouseY, StatsHudRenderer.resolvePositionBounds(this.font, this.width, this.height, config))) {
            if (!config.hudInfoEnabled) {
                config.hudInfoEnabled = true;
                config.hudPositionEnabled = true;
            } else {
                config.hudPositionEnabled = !config.hudPositionEnabled;
            }
            config.save();
            return true;
        }
        if (isInside(mouseX, mouseY, StatsHudRenderer.resolveStatsBounds(this.font, this.width, this.height, config))) {
            if (!config.hudInfoEnabled) {
                config.hudInfoEnabled = true;
                config.hudTelemetryEnabled = true;
            } else {
                config.hudTelemetryEnabled = !config.hudTelemetryEnabled;
            }
            config.save();
            return true;
        }
        if (isInside(mouseX, mouseY, WatermarkArrayListHudRenderer.resolveArrayListBounds(this.font, this.width, this.height, config))) {
            config.hudArrayListEnabled = !config.hudArrayListEnabled;
            config.save();
            return true;
        }
        if (isInside(mouseX, mouseY, WatermarkArrayListHudRenderer.resolveWatermarkBounds(this.font, this.width, this.height, config))) {
            config.hudWatermarkEnabled = !config.hudWatermarkEnabled;
            config.save();
            return true;
        }
        if (isInside(mouseX, mouseY, getKeybindsBounds(config))) {
            config.keybindsHudEnabled = !config.keybindsHudEnabled;
            config.save();
            return true;
        }
        return false;
    }

    private static void toggleInventoryComponent(ModConfig config, HudTarget target) {
        try {
            ConfigAccess invCfg = InventoryHUDCommon.getConfig();
            if (!config.inventoryHudEnabled) {
                config.inventoryHudEnabled = true;
                config.save();
            }
            switch (target) {
                case INVENTORY -> {
                    boolean next = !InventoryHUDCommon.isActive;
                    InventoryHUDCommon.isActive = next;
                    if (invCfg != null) invCfg.setByDefault(next);
                }
                case POTIONS -> {
                    boolean next = !InventoryHUDCommon.potionHUD;
                    InventoryHUDCommon.potionHUD = next;
                    if (invCfg != null) invCfg.setPotions(next);
                }
                case ARMOR -> {
                    boolean next = !InventoryHUDCommon.armorHUD;
                    InventoryHUDCommon.armorHUD = next;
                    if (invCfg != null) invCfg.setArmorDamage(next);
                }
                default -> {
                }
            }
            saveInventoryHudConfig();
        } catch (Throwable ignored) {
        }
    }

    private boolean beginDragIfInside(HudTarget target, int[] bounds, int mouseX, int mouseY) {
        if (bounds != null && isInside(mouseX, mouseY, bounds)) {
            this.dragging = target;
            this.dragOffsetX = mouseX - bounds[0];
            this.dragOffsetY = mouseY - bounds[1];
            return true;
        }
        return false;
    }

    private boolean handlePressAt(int mouseX, int mouseY) {
        ModConfig config = ModConfig.getInstance();

        if (isInside(mouseX, mouseY, getResetButtonBounds())) {
            resetAllHudPositions(config);
            return false;
        }
        if (isInside(mouseX, mouseY, getDoneButtonBounds())) {
            onClose();
            return true;
        }

        // Hit-test in reverse draw order so topmost overlay is grabbed.
        if (beginDragIfInside(HudTarget.ARMOR, getArmorBounds(), mouseX, mouseY)
                || beginDragIfInside(HudTarget.POTIONS, getPotionsBounds(), mouseX, mouseY)
                || beginDragIfInside(HudTarget.INVENTORY, getInventoryBounds(config), mouseX, mouseY)
                || beginDragIfInside(HudTarget.SPAWNER_MODE, SpawnerAutoSellModule.resolveHudBounds(this.font, this.width, this.height, config, true), mouseX, mouseY)
                || beginDragIfInside(HudTarget.AUTO_SPAWNER, AutoSpawnerModule.resolveHudBounds(this.font, this.width, this.height, config), mouseX, mouseY)
                || beginDragIfInside(HudTarget.PLAYER_LIST, PlayerListHudRenderer.resolveBounds(this.font, this.width, this.height, config), mouseX, mouseY)
                || beginDragIfInside(HudTarget.ITEM_NOTIFIER, ItemNotifierHudRenderer.resolveBounds(this.font, this.width, this.height, config), mouseX, mouseY)
                || beginDragIfInside(HudTarget.CLOCK, StatsHudRenderer.resolveClockBounds(this.font, this.width, this.height, config), mouseX, mouseY)
                || beginDragIfInside(HudTarget.POSITION, StatsHudRenderer.resolvePositionBounds(this.font, this.width, this.height, config), mouseX, mouseY)
                || beginDragIfInside(HudTarget.STATS, StatsHudRenderer.resolveStatsBounds(this.font, this.width, this.height, config), mouseX, mouseY)
                || beginDragIfInside(HudTarget.ARRAY_LIST, WatermarkArrayListHudRenderer.resolveArrayListBounds(this.font, this.width, this.height, config), mouseX, mouseY)
                || beginDragIfInside(HudTarget.WATERMARK, WatermarkArrayListHudRenderer.resolveWatermarkBounds(this.font, this.width, this.height, config), mouseX, mouseY)
                || beginDragIfInside(HudTarget.KEYBINDS, getKeybindsBounds(config), mouseX, mouseY)) {
            return false;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if ((MouseButtonMapping.isLeftClick(event.button()) || event.button() == 0) && this.dragging != HudTarget.NONE) {
            updateDraggedPosition(ModConfig.getInstance(), (int) event.x(), (int) event.y());
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    private HudLayoutPresets.SnapResult snapDrag(int rawX, int rawY, int w, int h) {
        boolean snapEnabled = true;
        try {
            snapEnabled = !InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                    && !InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
        } catch (Throwable ignored) {
        }
        HudLayoutPresets.SnapResult snapped = HudLayoutPresets.snapBox(
                rawX, rawY, w, h, this.width, this.height, snapEnabled
        );
        this.activeGuideX = snapped.guideX();
        this.activeGuideY = snapped.guideY();
        return snapped;
    }

    private void updateDraggedPosition(ModConfig config, int mouseX, int mouseY) {
        this.activeGuideX = null;
        this.activeGuideY = null;
        int rawX = mouseX - this.dragOffsetX;
        int rawY = mouseY - this.dragOffsetY;
        switch (this.dragging) {
            case KEYBINDS -> {
                int[] b = getKeybindsBounds(config);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.keybindsHudX = s.x();
                config.keybindsHudY = s.y();
            }
            case WATERMARK -> {
                int[] b = WatermarkArrayListHudRenderer.resolveWatermarkBounds(this.font, this.width, this.height, config);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.hudWatermarkX = s.x();
                config.hudWatermarkY = s.y();
            }
            case ARRAY_LIST -> {
                int[] b = WatermarkArrayListHudRenderer.resolveArrayListBounds(this.font, this.width, this.height, config);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.hudArrayListX = s.x();
                config.hudArrayListY = s.y();
            }
            case PLAYER_LIST -> {
                int[] b = PlayerListHudRenderer.resolveBounds(this.font, this.width, this.height, config);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.playerListX = s.x();
                config.playerListY = s.y();
            }
            case AUTO_SPAWNER -> {
                int[] b = AutoSpawnerModule.resolveHudBounds(this.font, this.width, this.height, config);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.autoSpawnerHudX = s.x();
                config.autoSpawnerHudY = s.y();
            }
            case SPAWNER_MODE -> {
                int[] b = SpawnerAutoSellModule.resolveHudBounds(this.font, this.width, this.height, config, true);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.spawnerAutoSellHudX = s.x();
                config.spawnerAutoSellHudY = s.y();
            }
            case STATS -> {
                int[] b = StatsHudRenderer.resolveStatsBounds(this.font, this.width, this.height, config);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.statsHudX = s.x();
                config.statsHudY = s.y();
            }
            case POSITION -> {
                int[] b = StatsHudRenderer.resolvePositionBounds(this.font, this.width, this.height, config);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.posHudX = s.x();
                config.posHudY = s.y();
            }
            case CLOCK -> {
                int[] b = StatsHudRenderer.resolveClockBounds(this.font, this.width, this.height, config);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.clockHudX = s.x();
                config.clockHudY = s.y();
            }
            case ITEM_NOTIFIER -> {
                int[] b = ItemNotifierHudRenderer.resolveBounds(this.font, this.width, this.height, config);
                HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                config.itemNotifierX = s.x();
                config.itemNotifierY = s.y();
            }
            case INVENTORY -> {
                int[] b = getInventoryBounds(config);
                if (b != null) {
                    HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                    applyInventoryPos(s.x(), s.y());
                }
            }
            case POTIONS -> {
                int[] b = getPotionsBounds();
                if (b != null) {
                    HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                    applyPotionsPos(s.x(), s.y());
                }
            }
            case ARMOR -> {
                int[] b = getArmorBounds();
                if (b != null) {
                    HudLayoutPresets.SnapResult s = snapDrag(rawX, rawY, b[2], b[3]);
                    applyArmorPos(s.x(), s.y());
                }
            }
            case NONE -> {
            }
        }
    }

    public static void applyPresetToEditor(HudLayoutPresets.Preset preset, ModConfig config, int screenW, int screenH) {
        HudLayoutPresets.applyPreset(preset, config, screenW, screenH);
        try {
            ConfigAccess invCfg = InventoryHUDCommon.getConfig();
            if (preset == HudLayoutPresets.Preset.PVP) {
                InventoryHUDCommon.armorHUD = true;
                InventoryHUDCommon.potionHUD = true;
                if (invCfg != null) {
                    invCfg.setArmorDamage(true);
                    invCfg.setPotions(true);
                }
            } else if (preset == HudLayoutPresets.Preset.MINIMAL) {
                InventoryHUDCommon.armorHUD = false;
                InventoryHUDCommon.potionHUD = false;
                if (invCfg != null) {
                    invCfg.setArmorDamage(false);
                    invCfg.setPotions(false);
                }
            }
            saveInventoryHudConfig();
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if ((MouseButtonMapping.isLeftClick(event.button()) || event.button() == 0) && this.dragging != HudTarget.NONE) {
            boolean wasInv = this.dragging == HudTarget.INVENTORY
                    || this.dragging == HudTarget.POTIONS
                    || this.dragging == HudTarget.ARMOR;
            this.dragging = HudTarget.NONE;
            this.activeGuideX = null;
            this.activeGuideY = null;
            ModConfig.getInstance().save();
            if (wasInv) {
                saveInventoryHudConfig();
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    public static void resetAllHudPositions(ModConfig config) {
        config.keybindsHudX = 8;
        config.keybindsHudY = 60;
        config.statsHudX = 4;
        config.statsHudY = 4;
        config.posHudX = -1;
        config.posHudY = -1;
        config.clockHudX = -1;
        config.clockHudY = -1;
        config.itemNotifierX = -1;
        config.itemNotifierY = -1;
        config.playerListX = -1;
        config.playerListY = -1;
        config.autoSpawnerHudX = -1;
        config.autoSpawnerHudY = -1;
        config.spawnerAutoSellHudX = -1;
        config.spawnerAutoSellHudY = -1;
        config.hudWatermarkX = 4;
        config.hudWatermarkY = 4;
        config.hudArrayListX = -1;
        config.hudArrayListY = -1;
        config.save();
    }

    private int[] getInventoryBounds(ModConfig config) {
        try {
            InventoryHUDCommon common = InventoryHUDCommon.getInstance();
            ConfigAccess invCfg = InventoryHUDCommon.getConfig();
            if (common == null || common.getInventoryGui() == null || invCfg == null) {
                return null;
            }
            InventoryGui invGui = common.getInventoryGui();
            float miniScale = invCfg.getInvMini() ? 0.75f : 1.0f;
            float totalScale = miniScale * Mth.clamp(config.invHudScale, 0.5f, 2.0f);
            int w = Math.max(24, Math.round((invCfg.getInvVert() ? 52 : 160) * totalScale));
            int h = Math.max(24, Math.round((invCfg.getInvVert() ? 160 : 52) * totalScale));
            int x = invGui.getX(this.width, w, invCfg.getXPos(), invCfg.getInvHalign());
            int y = invGui.getY(this.height, h, invCfg.getYPos(), invCfg.getInvValign());
            return new int[]{x, y, w, h};
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int[] getPotionsBounds() {
        try {
            InventoryHUDCommon common = InventoryHUDCommon.getInstance();
            ConfigAccess invCfg = InventoryHUDCommon.getConfig();
            if (common == null || common.getInventoryGui() == null || invCfg == null) {
                return null;
            }
            InventoryGui invGui = common.getInventoryGui();
            int w = invCfg.getPotMini() ? 66 : 82;
            int h = invCfg.getPotMini() ? 22 : 28;
            int x = invGui.getX(this.width, w, invCfg.getXPotionPos(), invCfg.getPotHalign());
            int y = invGui.getY(this.height, h, invCfg.getYPotionPos(), invCfg.getPotValign());
            return new int[]{x, y, w, h};
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int[] getArmorBounds() {
        try {
            InventoryHUDCommon common = InventoryHUDCommon.getInstance();
            ConfigAccess invCfg = InventoryHUDCommon.getConfig();
            if (common == null || common.getInventoryGui() == null || invCfg == null) {
                return null;
            }
            InventoryGui invGui = common.getInventoryGui();
            int w = 64;
            int h = 64;
            int x = invGui.getX(this.width, w, invCfg.getXArmPos(), invCfg.getArmHalign());
            int y = invGui.getY(this.height, h, invCfg.getYArmPos(), invCfg.getArmValign());
            return new int[]{x, y, w, h};
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void applyInventoryPos(int x, int y) {
        try {
            ConfigAccess invCfg = InventoryHUDCommon.getConfig();
            if (invCfg != null) {
                invCfg.setInvHalign(WidgetAligns.HAlign.LEFT);
                invCfg.setInvValign(WidgetAligns.VAlign.TOP);
                invCfg.setXPos(x);
                invCfg.setYPos(y);
            }
            InventoryGui.invX = x;
            InventoryGui.invY = y;
            InventoryGui.InvAligns = new WidgetAligns(WidgetAligns.HAlign.LEFT, WidgetAligns.VAlign.TOP);
        } catch (Throwable ignored) {
        }
    }

    private static void applyPotionsPos(int x, int y) {
        try {
            InventoryHUDCommon common = InventoryHUDCommon.getInstance();
            ConfigAccess invCfg = InventoryHUDCommon.getConfig();
            if (invCfg != null) {
                invCfg.setPotHalign(WidgetAligns.HAlign.LEFT);
                invCfg.setPotValign(WidgetAligns.VAlign.TOP);
                invCfg.setXPotionPos(x);
                invCfg.setYPotionPos(y);
            }
            if (common != null && common.getInventoryGui() != null && common.getInventoryGui().potionRenderer != null) {
                common.getInventoryGui().potionRenderer.potX = x;
                common.getInventoryGui().potionRenderer.potY = y;
                common.getInventoryGui().potionRenderer.PotAligns = new WidgetAligns(WidgetAligns.HAlign.LEFT, WidgetAligns.VAlign.TOP);
                common.getInventoryGui().potionRenderer.PotPosChanged();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void applyArmorPos(int x, int y) {
        try {
            InventoryHUDCommon common = InventoryHUDCommon.getInstance();
            ConfigAccess invCfg = InventoryHUDCommon.getConfig();
            WidgetAligns aligns = new WidgetAligns(WidgetAligns.HAlign.LEFT, WidgetAligns.VAlign.TOP);
            if (invCfg != null) {
                invCfg.setArmHalign(WidgetAligns.HAlign.LEFT);
                invCfg.setArmValign(WidgetAligns.VAlign.TOP);
                invCfg.setXArmPos(x);
                invCfg.setYArmPos(y);
            }
            if (common != null && common.getInventoryGui() != null) {
                common.getInventoryGui().changeArmorPos(aligns, x, y);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void saveInventoryHudConfig() {
        try {
            InventoryHUDCommon.saveConfig();
        } catch (Throwable ignored) {
        }
    }

    private int[] getKeybindsBounds(ModConfig config) {
        List<KeybindsHudRenderer.BoundDisplayItem> items = KeybindsHudRenderer.getBoundItems();
        int w = KeybindsHudRenderer.calculateWidth(this.font, items);
        int rows = items.isEmpty() ? 1 : items.size();
        int h = KeybindsHudRenderer.HEADER_HEIGHT + rows * KeybindsHudRenderer.ROW_HEIGHT + 4;
        int x = Mth.clamp(config.keybindsHudX, 2, Math.max(2, this.width - w - 2));
        int y = Mth.clamp(KeybindsHudRenderer.resolveDefaultY(config), 2, Math.max(2, this.height - h - 2));
        return new int[]{x, y, w, h};
    }

    private void drawDraggableBox(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int w,
            int h,
            String label,
            boolean enabled,
            boolean activeDrag,
            int fillColor,
            int borderColor
    ) {
        boolean hovered = isInside(mouseX, mouseY, x, y, w, h);
        int bg = activeDrag ? ((fillColor & 0x00FFFFFF) | 0x77000000)
                : (hovered ? ((fillColor & 0x00FFFFFF) | 0x5A000000) : fillColor);
        if (!enabled) {
            bg = 0x3320242E;
            borderColor = 0x8864748B;
        }

        graphics.fill(x, y, x + w, y + h, bg);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        String title = enabled ? label : (label + " (OFF)");
        int textX = x + Math.max(3, (w - this.font.width(title)) / 2);
        int textY = y + Math.max(2, (h - 8) / 2);
        graphics.text(this.font, Component.literal(title), textX, textY, enabled ? 0xFFFFFFFF : 0xFF94A3B8, true);

        if (hovered || activeDrag) {
            String coord = x + ", " + y;
            int coordY = y > 14 ? y - 11 : y + h + 3;
            graphics.text(this.font, Component.literal(coord), x, coordY, 0xFFE2E8F0, true);
        }
    }

    private void drawDashedGrid(GuiGraphicsExtractor graphics) {
        int lineColor = 0x44CBD5E1;
        int x1 = this.width / 3;
        int x2 = (this.width * 2) / 3;
        int y1 = this.height / 3;
        int y2 = (this.height * 2) / 3;

        for (int y = 0; y < this.height; y += 6) {
            graphics.fill(x1, y, x1 + 1, Math.min(this.height, y + 3), lineColor);
            graphics.fill(x2, y, x2 + 1, Math.min(this.height, y + 3), lineColor);
        }
        for (int x = 0; x < this.width; x += 6) {
            graphics.fill(x, y1, Math.min(this.width, x + 3), y1 + 1, lineColor);
            graphics.fill(x, y2, Math.min(this.width, x + 3), y2 + 1, lineColor);
        }

        if (this.dragging != HudTarget.NONE) {
            int cx = this.width / 2;
            int cy = this.height / 2;
            for (int y = 0; y < this.height; y += 8) {
                graphics.fill(cx, y, cx + 1, Math.min(this.height, y + 4), 0x33FF4B5C);
            }
            for (int x = 0; x < this.width; x += 8) {
                graphics.fill(x, cy, Math.min(this.width, x + 4), cy + 1, 0x33FF4B5C);
            }
            if (this.activeGuideX != null) {
                int gx = this.activeGuideX;
                graphics.fill(gx, 0, gx + 1, this.height, 0xCC38BDF8);
            }
            if (this.activeGuideY != null) {
                int gy = this.activeGuideY;
                graphics.fill(0, gy, this.width, gy + 1, 0xCC38BDF8);
            }
        }
    }

    private int[] getResetButtonBounds() {
        int btnW = 68;
        int btnH = 20;
        int gap = 8;
        int totalW = btnW + gap + 92;
        int startX = (this.width - totalW) / 2;
        int btnY = this.height - 34;
        return new int[]{startX, btnY, btnW, btnH};
    }

    private int[] getDoneButtonBounds() {
        int[] reset = getResetButtonBounds();
        return new int[]{reset[0] + reset[2] + 8, reset[1], 92, reset[3]};
    }

    private static boolean isInside(int mx, int my, int[] box) {
        return isInside(mx, my, box[0], box[1], box[2], box[3]);
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
