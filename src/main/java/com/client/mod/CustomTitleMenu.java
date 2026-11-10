package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.realmsclient.RealmsMainScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;

public class CustomTitleMenu {
    private static final Identifier BG_TEX = Identifier.fromNamespaceAndPath("inventoryhud", "textures/gui/custom_title_bg.png");
    private static final Identifier HOVER_TEX = Identifier.fromNamespaceAndPath("inventoryhud", "textures/gui/custom_title_hover.png");

    // Normalized button regions on the 1920x1080 artwork: {x1, y1, x2, y2}
    private static final int[][] BUTTON_BOXES = {
            {836, 569, 1084, 616}, // 0: singleplayer
            {836, 617, 1084, 666}, // 1: multiplayer
            {836, 667, 1084, 718}, // 2: relams
            {836, 719, 1084, 770}, // 3: acc (IAS Account Switcher)
            {836, 771, 1084, 822}, // 4: options
            {836, 823, 1084, 873}  // 5: quit
    };

    public static void render(TitleScreen screen, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int w = screen.width;
        int h = screen.height;

        graphics.blit(BG_TEX, 0, 0, w, h, 0.0f, 1.0f, 0.0f, 1.0f);

        for (int i = 0; i < BUTTON_BOXES.length; i++) {
            int[] box = getScaledBox(i, w, h);
            if (mouseX >= box[0] && mouseX <= box[2] && mouseY >= box[1] && mouseY <= box[3]) {
                int[] raw = BUTTON_BOXES[i];
                graphics.blit(
                        HOVER_TEX,
                        box[0], box[1], box[2], box[3],
                        raw[0] / 1920.0f, raw[2] / 1920.0f,
                        raw[1] / 1080.0f, raw[3] / 1080.0f
                );
                break;
            }
        }
    }

    public static boolean handleClick(Minecraft mc, TitleScreen screen, double rawX, double rawY, int button, int action) {
        return false;
    }

    public static void configureIasPosition() {
        try {
            Class<?> cfgCls = Class.forName("ru.vidtu.ias.config.IASConfig");
            cfgCls.getField("serversButton").setBoolean(null, false);
            cfgCls.getField("titleButton").setBoolean(null, false);
            cfgCls.getField("serversText").setBoolean(null, false);
            cfgCls.getField("titleText").setBoolean(null, false);
        } catch (Throwable ignored) {
        }
    }

    private static void openAccountSwitcher(Minecraft mc, TitleScreen parent) {
        try {
            Class<?> cls = Class.forName("ru.vidtu.ias.screen.AccountScreen");
            Screen s = (Screen) cls.getConstructor(Screen.class).newInstance(parent);
            mc.gui.setScreen(s);
        } catch (Throwable t) {
            mc.gui.setScreen(new OptionsScreen(parent, mc.options));
        }
    }

    private static int[] getScaledBox(int idx, int width, int height) {
        int[] raw = BUTTON_BOXES[idx];
        int x1 = Math.round(raw[0] * width / 1920.0f);
        int y1 = Math.round(raw[1] * height / 1080.0f);
        int x2 = Math.round(raw[2] * width / 1920.0f);
        int y2 = Math.round(raw[3] * height / 1080.0f);
        return new int[]{x1, y1, x2, y2};
    }

    private static void playClick(Minecraft mc) {
        try {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
        } catch (Throwable ignored) {
        }
    }
}
