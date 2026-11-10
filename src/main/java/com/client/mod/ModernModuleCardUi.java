package com.client.mod;

import dlovin.inventoryhud.InventoryHUDCommon;
import dlovin.inventoryhud.gui.InGameConfigScreen;
import dlovin.inventoryhud.gui.InventoryGui;
import dlovin.inventoryhud.gui.config.InventoryConfigScreen;
import dlovin.inventoryhud.platform.ConfigAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * UI bridge and renderer/hit-test coordinator for ModernClickGuiScreen accordion
 * module settings cards, keeping geometry shared between render and click paths.
 */
final class ModernModuleCardUi {

    @FunctionalInterface
    interface SliderDrawer {
        void draw(int x, int y, int w, int h, String key, float pct);
    }

    @FunctionalInterface
    interface SwitchDrawer {
        void draw(int x, int y, int w, int h, String key, boolean active);
    }

    @FunctionalInterface
    interface SliderStartHandler {
        void onStart(ModernModuleSettings.SliderControl slider, int x, int w, float initialPct);
    }

    private ModernModuleCardUi() {
    }

    static ModernModuleSettings.Actions createActions(Screen parentScreen) {
        ModernModuleSettings.InventoryBridge invBridge = new ModernModuleSettings.InventoryBridge() {
            @Override
            public boolean isActive() {
                return InventoryHUDCommon.isActive;
            }

            @Override
            public void setActive(boolean value) {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        invCfg.setByDefault(value);
                        invCfg.save();
                    }
                    InventoryHUDCommon.isActive = value;
                } catch (Throwable ignored) {
                }
            }

            @Override
            public boolean isArmor() {
                return InventoryHUDCommon.armorHUD;
            }

            @Override
            public void setArmor(boolean value) {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        invCfg.setArmorDamage(value);
                        invCfg.save();
                    }
                    InventoryHUDCommon.armorHUD = value;
                } catch (Throwable ignored) {
                }
            }

            @Override
            public boolean isPotions() {
                return InventoryHUDCommon.potionHUD;
            }

            @Override
            public void setPotions(boolean value) {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        invCfg.setPotions(value);
                        invCfg.save();
                    }
                    InventoryHUDCommon.potionHUD = value;
                } catch (Throwable ignored) {
                }
            }

            @Override
            public boolean isMini() {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    return invCfg != null && invCfg.getInvMini();
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            public void setMini(boolean value) {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        invCfg.setInvMini(value);
                        invCfg.save();
                    }
                    InventoryGui.invMini = value;
                } catch (Throwable ignored) {
                }
            }

            @Override
            public boolean isVert() {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    return invCfg != null && invCfg.getInvVert();
                } catch (Throwable ignored) {
                    return false;
                }
            }

            @Override
            public void setVert(boolean value) {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        invCfg.setInvVert(value);
                        invCfg.save();
                    }
                    InventoryGui.invVert = value;
                } catch (Throwable ignored) {
                }
            }

            @Override
            public int getBgAlpha() {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    return invCfg != null ? invCfg.getInvAlpha() : 60;
                } catch (Throwable ignored) {
                    return 60;
                }
            }

            @Override
            public void setBgAlpha(int alpha) {
                try {
                    ConfigAccess invCfg = InventoryHUDCommon.getConfig();
                    if (invCfg != null) {
                        invCfg.setInvAlpha(alpha);
                    }
                    InventoryGui.invAlpha = alpha / 100.0f;
                } catch (Throwable ignored) {
                }
            }
        };

        return new ModernModuleSettings.Actions() {
            @Override
            public void persist(boolean reloadChunks) {
                ModConfig.getInstance().save();
                if (reloadChunks) {
                    ModConfig.reloadChunks();
                }
            }

            @Override
            public void openInventorySettings() {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.gui != null) {
                        mc.gui.setScreen(new InventoryConfigScreen(true));
                    }
                } catch (Throwable ignored) {
                }
            }

            @Override
            public void openInventoryMove() {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.gui != null) {
                        mc.gui.setScreen(new InGameConfigScreen(0, true));
                    }
                } catch (Throwable ignored) {
                }
            }

            @Override
            public void openVersionSwitcher() {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.gui != null) {
                        mc.gui.setScreen(new VersionSwitcherScreen(parentScreen));
                    }
                } catch (Throwable ignored) {
                }
            }

            @Override
            public ModernModuleSettings.InventoryBridge inventory() {
                return invBridge;
            }
        };
    }

    static void flushInventoryConfigSave() {
        try {
            ConfigAccess invCfg = InventoryHUDCommon.getConfig();
            if (invCfg != null) {
                invCfg.save();
            }
        } catch (Throwable ignored) {
        }
    }

    static void renderControls(
            GuiGraphicsExtractor graphics,
            Font font,
            int mouseX,
            int mouseY,
            int x,
            int startY,
            int w,
            String modName,
            List<ModernModuleSettings.Control> controls,
            SliderDrawer sliderDrawer,
            SwitchDrawer switchDrawer
    ) {
        graphics.fill(x, startY, x + w, startY + 1, JelloTheme.DIVIDER);
        int curY = startY + ModernModuleSettings.SECTION_TOP_PAD;

        for (ModernModuleSettings.Control control : controls) {
            if (control instanceof ModernModuleSettings.SliderControl s) {
                String valText = s.valueLabel().get();
                int valW = font.width(valText);
                String label = fitText(font, s.label(), Math.max(24, w - valW - 10));
                graphics.text(font, Component.literal(label), x, curY, JelloTheme.TEXT, false);
                graphics.text(font, Component.literal(valText), x + w - valW, curY, JelloTheme.TEXT_DIM, false);
                sliderDrawer.draw(x, curY + 12, w, 6, s.key(), s.pctGetter().get());
            } else if (control instanceof ModernModuleSettings.ToggleControl t) {
                boolean active = t.getter().get();
                boolean hovered = isInside(mouseX, mouseY, x, curY, w, t.height());
                int bg = active
                        ? (hovered ? JelloTheme.CARD_ACTIVE_HOVER : JelloTheme.CARD_ACTIVE)
                        : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
                int border = active ? 0x66E53935 : JelloTheme.BORDER_SUBTLE;
                JelloTheme.fillRoundedCard(graphics, x, curY, x + w, curY + t.height(), 5, bg, border);
                String text = fitText(font, t.label(), w - 46);
                graphics.text(font, Component.literal(text), x + 8, curY + 6, active ? JelloTheme.TEXT : 0xFFCBD5E1, false);
                switchDrawer.draw(x + w - 34, curY + 3, 28, 14, "sub:" + modName + ":" + t.label(), active);
            } else if (control instanceof ModernModuleSettings.CycleControl c) {
                boolean hovered = isInside(mouseX, mouseY, x, curY, w, c.height());
                int bg = hovered ? 0x33E53935 : JelloTheme.CARD_INACTIVE;
                int border = hovered ? JelloTheme.ACCENT : 0x55E53935;
                JelloTheme.fillRoundedCard(graphics, x, curY, x + w, curY + c.height(), 5, bg, border);
                String valText = c.valueLabel().get();
                int valW = font.width(valText);
                String label = fitText(font, c.label(), Math.max(24, w - valW - 22));
                graphics.text(font, Component.literal(label), x + 8, curY + 6, JelloTheme.TEXT, false);
                graphics.text(font, Component.literal(valText), x + w - 8 - valW, curY + 6, JelloTheme.ACCENT_HOVER, false);
            } else if (control instanceof ModernModuleSettings.ActionControl a) {
                boolean hovered = isInside(mouseX, mouseY, x, curY, w, a.height());
                int bg = hovered ? 0x55E53935 : 0x28E53935;
                int border = hovered ? JelloTheme.ACCENT : 0x66E53935;
                JelloTheme.fillRoundedCard(graphics, x, curY, x + w, curY + a.height(), 5, bg, border);
                String label = fitText(font, a.labelSupplier().get(), w - 12);
                graphics.centeredText(font, Component.literal(label), x + w / 2, curY + 6, JelloTheme.TEXT);
            } else if (control instanceof ModernModuleSettings.ChipsControl cc) {
                renderChips(graphics, font, mouseX, mouseY, x, curY, w, cc.options());
            }
            curY += control.height() + ModernModuleSettings.CONTROL_GAP;
        }
    }

    private static void renderChips(
            GuiGraphicsExtractor graphics,
            Font font,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int w,
            List<ModernModuleSettings.ChipOption> options
    ) {
        int gapX = 6;
        int chipW = Math.max(20, (w - gapX) / 2);
        for (int i = 0; i < options.size(); i++) {
            ModernModuleSettings.ChipOption opt = options.get(i);
            int col = i % 2;
            int row = i / 2;
            int cx = x + col * (chipW + gapX);
            int cy = y + row * (ModernModuleSettings.CHIP_HEIGHT + ModernModuleSettings.CHIP_GAP_Y);
            boolean active = opt.activeGetter().get();
            boolean hovered = isInside(mouseX, mouseY, cx, cy, chipW, ModernModuleSettings.CHIP_HEIGHT);
            int bg = active
                    ? (hovered ? 0x66E53935 : 0x44E53935)
                    : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
            int border = active ? JelloTheme.ACCENT : (hovered ? 0x5594A3B8 : JelloTheme.BORDER_SUBTLE);
            JelloTheme.fillRoundedCard(graphics, cx, cy, cx + chipW, cy + ModernModuleSettings.CHIP_HEIGHT, 4, bg, border);
            String label = fitText(font, opt.label(), chipW - 8);
            graphics.centeredText(font, Component.literal(label), cx + chipW / 2, cy + 5,
                    active ? JelloTheme.TEXT : JelloTheme.TEXT_MUTED);
        }
    }

    static boolean handleControlsClick(
            int mouseX,
            int mouseY,
            int x,
            int startY,
            int w,
            List<ModernModuleSettings.Control> controls,
            SliderStartHandler sliderStartHandler,
            Runnable soundPlayer
    ) {
        int curY = startY + ModernModuleSettings.SECTION_TOP_PAD;
        for (ModernModuleSettings.Control control : controls) {
            int h = control.height();
            if (mouseY >= curY && mouseY < curY + h && mouseX >= x - 2 && mouseX <= x + w + 2) {
                if (control instanceof ModernModuleSettings.SliderControl s) {
                    float pct = Mth.clamp((float) (mouseX - x) / (float) Math.max(1, w), 0.0f, 1.0f);
                    sliderStartHandler.onStart(s, x, w, pct);
                    soundPlayer.run();
                    return true;
                }
                if (control instanceof ModernModuleSettings.ToggleControl t) {
                    t.setter().accept(!t.getter().get());
                    soundPlayer.run();
                    return true;
                }
                if (control instanceof ModernModuleSettings.CycleControl c) {
                    c.onCycle().run();
                    soundPlayer.run();
                    return true;
                }
                if (control instanceof ModernModuleSettings.ActionControl a) {
                    a.onClick().run();
                    soundPlayer.run();
                    return true;
                }
                if (control instanceof ModernModuleSettings.ChipsControl cc) {
                    if (handleChipsClick(mouseX, mouseY, x, curY, w, cc.options())) {
                        soundPlayer.run();
                        return true;
                    }
                }
            }
            curY += h + ModernModuleSettings.CONTROL_GAP;
        }
        return false;
    }

    private static boolean handleChipsClick(
            int mouseX,
            int mouseY,
            int x,
            int y,
            int w,
            List<ModernModuleSettings.ChipOption> options
    ) {
        int gapX = 6;
        int chipW = Math.max(20, (w - gapX) / 2);
        int rowStep = ModernModuleSettings.CHIP_HEIGHT + ModernModuleSettings.CHIP_GAP_Y;
        int relY = mouseY - y;
        if (relY < 0) {
            return false;
        }
        int row = relY / rowStep;
        if ((relY % rowStep) >= ModernModuleSettings.CHIP_HEIGHT) {
            return false;
        }
        int relX = mouseX - x;
        int col = -1;
        if (relX >= 0 && relX <= chipW) {
            col = 0;
        } else if (relX >= chipW + gapX && relX <= 2 * chipW + gapX) {
            col = 1;
        }
        if (col < 0) {
            return false;
        }
        int idx = row * 2 + col;
        if (idx >= 0 && idx < options.size()) {
            options.get(idx).onToggle().run();
            return true;
        }
        return false;
    }

    static String getModuleDescription(String name, String fallbackCategory) {
        return switch (name) {
            case "Dulkir" -> "Custom hand model scale, positions & swing speed";
            case "Inventory HUD+" -> "On-screen inventory, armor & potion status";
            case "Fullbright" -> "Full night vision & clear sight in caves";
            case "HUD Info" -> "FPS, Ping, TPS & coordinates on screen";
            case "TNT Timer" -> "Accurate fuse ticks & detonation timer";
            case "Keybinds HUD" -> "Active toggles & bound keys overlay";
            case "TrueSight" -> "See invisible entities & players";
            case "Item ESP" -> "Glowing box around dropped items";
            case "FastBreak" -> "Break blocks faster with Vulcan or Vanilla mode";
            case "Fast Place" -> "Remove placing delay for rapid building";
            case "Fast Use" -> "Rapid consumption of food & potions";
            case "Auto Break" -> "Automatically mine selected target blocks";
            case "Auto Tool" -> "Automatically switch to best mining tool";
            case "Auto Crafter" -> "Automatically compact ores & items into blocks";
            case "Auto Sprint" -> "Keep sprinting automatically when moving";
            case "Safe Walk" -> "Prevent falling off block edges like sneaking";
            case "Better Chat" -> "Infinite chat history, anti-clear & copy";
            case "GUI Style" -> "Switch between Modern Dashboard and Classic Windows";
            case "No Pitch Limit" -> "Unlock vertical camera pitch limits";
            case "Abort Breaking" -> "Prevent block-break reset glitches";
            case "Version Switcher" -> "Spoof target Minecraft protocol version";
            case "Inventory Tweaks" -> "One-click sort & smart inventory handling";
            case "Auto Replenish" -> "Refill hotbar slots from inventory";
            default -> fallbackCategory.toUpperCase(java.util.Locale.ROOT) + " MODULE";
        };
    }

    private static String fitText(Font font, String text, int maxWidth) {
        if (text == null || maxWidth <= 8 || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String trimmed = text;
        while (trimmed.length() > 2 && font.width(trimmed + "...") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "...";
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY < y + h;
    }
}
