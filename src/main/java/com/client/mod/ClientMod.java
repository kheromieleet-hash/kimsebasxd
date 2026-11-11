package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public class ClientMod implements ClientModInitializer {
    public static final String MOD_ID = "inventoryhud";
    public static KeyMapping zoomKey;

    private static boolean openMenuNextTick = false;
    private static boolean wasRightShiftDown = false;
    private static boolean wasAutoSprinting = false;

    @Override
    public void onInitializeClient() {
        ModConfig.getInstance().load();
        SestiaChatClient.onConfigChanged();
        UpdateNoticeManager.loadOnStartup();
        CustomTitleMenu.configureIasPosition();

        zoomKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.customclient.zoom",
                InputConstants.KEY_C,
                KeyMapping.Category.MISC
        ));
        ZoomHandler.setZoomKey(zoomKey);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("dulkir").executes(ctx -> {
                openMenuNextTick = true;
                return 1;
            }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("animations").executes(ctx -> {
                openMenuNextTick = true;
                return 1;
            }));
            dispatcher.register(LiteralArgumentBuilder.<FabricClientCommandSource>literal("client").executes(ctx -> {
                openMenuNextTick = true;
                return 1;
            }));
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ZoomHandler.update();
            AutoReplenishHandler.tick(client);
            UpdateNoticeManager.onClientTick(client);

            ModConfig cfg = ModConfig.getInstance();
            if (cfg.autoSprint) {
                if (client.player != null && client.options != null && client.gui.screen() == null) {
                    client.options.keySprint.setDown(true);
                    wasAutoSprinting = true;
                }
            } else if (wasAutoSprinting) {
                wasAutoSprinting = false;
                if (client.options != null) {
                    client.options.keySprint.setDown(false);
                }
            }

            if (openMenuNextTick) {
                openMenuNextTick = false;
                if (ModConfig.getInstance().clickGuiStyle == 1) {
                    client.gui.setScreen(new ModernClickGuiScreen());
                } else {
                    client.gui.setScreen(new ClickGuiScreen(null));
                }
            }

            if (client.getWindow() != null) {
                boolean rShiftDown = InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
                if (rShiftDown && !wasRightShiftDown) {
                    if (client.gui.screen() instanceof ModernClickGuiScreen || client.gui.screen() instanceof ClickGuiScreen) {
                        client.gui.setScreen(null);
                    } else if (client.gui.screen() == null && client.player != null) {
                        if (ModConfig.getInstance().clickGuiStyle == 1) {
                            client.gui.setScreen(new ModernClickGuiScreen());
                        } else {
                            client.gui.setScreen(new ClickGuiScreen(null));
                        }
                    }
                }
                wasRightShiftDown = rShiftDown;
            }
        });
    }
}
