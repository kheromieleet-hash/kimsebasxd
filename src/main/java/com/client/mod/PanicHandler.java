package com.client.mod;

import net.minecraft.client.Minecraft;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

/**
 * Screen-share safe Panic mode (.panic):
 * - Disables all modules, HUDs, overlays, keybinds, macros, ClickGUI, and Sestia chat
 * - Scrubs all [Sestia] messages and dot-commands (.farm, .friend, .panic, etc.) from chat & sent history
 * - Disables dot-command autocomplete so typing "." in chat behaves 100% like vanilla Minecraft
 * - Can be restored with .unpanic
 */
public final class PanicHandler {
    private static volatile boolean panicActive = false;
    private static ModConfig savedSnapshot = null;
    private static boolean savedInvActive = false;
    private static boolean savedInvArmor = false;
    private static boolean savedInvPotions = false;

    private PanicHandler() {
    }

    public static boolean isPanicActive() {
        return panicActive;
    }

    public static void enablePanic(Minecraft mc) {
        ModConfig cfg = ModConfig.getInstance();
        boolean hadCustomAssets = false;
        if (cfg != null && !panicActive) {
            savedSnapshot = cfg.copy();
            hadCustomAssets = cfg.customCrosshair || cfg.customFont;
            try {
                savedInvActive = dlovin.inventoryhud.InventoryHUDCommon.isActive;
                savedInvArmor = dlovin.inventoryhud.InventoryHUDCommon.armorHUD;
                savedInvPotions = dlovin.inventoryhud.InventoryHUDCommon.potionHUD;
            } catch (Throwable ignored) {
            }
        }
        panicActive = true;

        AutoSpawnerModule autoSpawner = AutoSpawnerModule.getInstance();
        if (autoSpawner != null) {
            autoSpawner.setEnabledFromConfig(false);
        }
        SpawnerAutoSellModule spawnerSell = SpawnerAutoSellModule.getInstance();
        if (spawnerSell != null) {
            spawnerSell.setEnabledFromConfig(false);
        }
        PacketMineHandler.clear();
        AutoLoginHandler.onLeftWorld();

        if (cfg != null) {
            if (FreecamHandler.isActive()) {
                cfg.freecamEnabled = false;
                if (mc != null) {
                    FreecamHandler.disable(mc);
                }
            }
            for (Field field : ModConfig.class.getFields()) {
                int mods = field.getModifiers();
                if (Modifier.isPublic(mods) && !Modifier.isStatic(mods) && field.getType() == boolean.class) {
                    String name = field.getName();
                    if (name.endsWith("Migrated") || name.equals("wheatFarmPos1Set") || name.equals("wheatFarmPos2Set")) {
                        continue;
                    }
                    try {
                        field.setBoolean(cfg, false);
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
            try {
                dlovin.inventoryhud.InventoryHUDCommon.isActive = false;
                dlovin.inventoryhud.InventoryHUDCommon.armorHUD = false;
                dlovin.inventoryhud.InventoryHUDCommon.potionHUD = false;
            } catch (Throwable ignored) {
            }
            SestiaChatClient.onConfigChanged();
        }

        WheatFarmBotHandler.onLeftWorld();
        AutoFishHandler.onLeftWorld();
        CosmeticFxHandler.clearAll();
        CosmeticsLogic.clearLocalEmote();
        CosmeticsLogic.clearPreviewOverrides();
        CosmeticsLogic.clearRemoteState();
        CosmeticGlintManager.restoreVanilla(mc);

        if (mc != null) {
            if (mc.options != null) {
                mc.options.keySprint.setDown(false);
                mc.options.keyUp.setDown(false);
                mc.options.keyDown.setDown(false);
                mc.options.keyLeft.setDown(false);
                mc.options.keyRight.setDown(false);
                mc.options.keyAttack.setDown(false);
                mc.options.keyUse.setDown(false);
                mc.options.keyJump.setDown(false);
                mc.options.keyShift.setDown(false);
            }
            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                Object chat = mc.gui.hud.getChat();
                if (chat instanceof IChatComponentHelper helper) {
                    helper.clientMod$scrubSestiaMessages();
                }
                try {
                    List<String> recent = mc.gui.hud.getChat().getRecentChat();
                    if (recent != null) {
                        recent.removeIf(s -> s != null && s.trim().startsWith("."));
                    }
                } catch (Throwable ignored) {
                }
            }
            if (hadCustomAssets) {
                try {
                    mc.reloadResourcePacks();
                } catch (Throwable ignored) {
                }
            }
            ModConfig.reloadChunks();
        }
    }

    public static void disablePanic(Minecraft mc) {
        if (!panicActive) {
            return;
        }
        panicActive = false;
        ModConfig cfg = ModConfig.getInstance();
        boolean restoreCustomAssets = false;
        if (cfg != null && savedSnapshot != null) {
            cfg.copyFrom(savedSnapshot);
            savedSnapshot = null;
            restoreCustomAssets = cfg.customCrosshair || cfg.customFont;
            try {
                dlovin.inventoryhud.InventoryHUDCommon.isActive = savedInvActive;
                dlovin.inventoryhud.InventoryHUDCommon.armorHUD = savedInvArmor;
                dlovin.inventoryhud.InventoryHUDCommon.potionHUD = savedInvPotions;
            } catch (Throwable ignored) {
            }
            SestiaChatClient.onConfigChanged();
        }
        if (restoreCustomAssets && mc != null) {
            try {
                mc.reloadResourcePacks();
            } catch (Throwable ignored) {
            }
        }
        ModConfig.reloadChunks();
    }
}
