package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;

public class ClientMod implements ClientModInitializer {
    public static final String MOD_ID = "inventoryhud";
    public static KeyMapping zoomKey;

    private static boolean wasRightShiftDown = false;
    private static boolean wasAutoSprinting = false;
    private static boolean wasInWorld = false;

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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ZoomHandler.update();
            FreecamHandler.tick(client);
            AutoReplenishHandler.tick(client);
            AutoSellHandler.tick(client);
            AutoDropHandler.tick(client);
            AutoLoginHandler.tick(client);
            WheatFarmBotHandler.tick(client);
            ItemSuckerHandler.tick(client);
            ItemNotifierHudRenderer.tick(client);
            boolean inWorld = client.player != null;
            if (inWorld && !wasInWorld) {
                wasInWorld = true;
                SestiaChatClient.onEnteredWorld();
                AutoLoginHandler.onEnteredWorld();
            } else if (!inWorld && wasInWorld) {
                wasInWorld = false;
                AutoLoginHandler.onLeftWorld();
                WheatFarmBotHandler.onLeftWorld();
            }

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

            if (client.getWindow() != null) {
                boolean rShiftDown = InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
                if (rShiftDown && !wasRightShiftDown) {
                    if (client.gui.screen() instanceof ModernClickGuiScreen) {
                        client.gui.setScreen(null);
                    } else if (client.gui.screen() == null && client.player != null) {
                        client.gui.setScreen(new ModernClickGuiScreen());
                    }
                }
                wasRightShiftDown = rShiftDown;
            }
        });
    }
}
