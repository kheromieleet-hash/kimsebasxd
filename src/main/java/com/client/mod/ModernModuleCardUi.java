package com.client.mod;

import dlovin.inventoryhud.InventoryHUDCommon;
import dlovin.inventoryhud.gui.InGameConfigScreen;
import dlovin.inventoryhud.gui.InventoryGui;
import dlovin.inventoryhud.platform.ConfigAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    @FunctionalInterface
    interface SliderEditHandler {
        void onEditStart(ModernModuleSettings.SliderControl slider);
    }

    @FunctionalInterface
    interface EditBoxBoundsConsumer {
        void accept(int x, int y, int w, int h);
    }

    record SliderEditState(String key, String text, boolean selectAll) {}

    private ModernModuleCardUi() {
    }

    static ModernModuleSettings.Actions createActions() {
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
            public void openInventoryMove() {
                try {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc != null && mc.gui != null) {
                        mc.gui.setScreen(new InGameConfigScreen(-1, true));
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
            SwitchDrawer switchDrawer,
            SliderEditState editState,
            EditBoxBoundsConsumer editBoundsConsumer
    ) {
        graphics.fill(x, startY, x + w, startY + 1, JelloTheme.DIVIDER);
        ModernInspectorLayout.ControlsFlow flow = ModernInspectorLayout.computeControlsFlow(x, startY, w, controls);
        boolean compact = w < 200;
        int padX = compact ? 6 : 8;

        for (ModernInspectorLayout.PlacedControl placed : flow.items()) {
            ModernModuleSettings.Control control = placed.control();
            int cx = placed.x();
            int cy = placed.y();
            int cw = placed.w();
            if (control instanceof ModernModuleSettings.SliderControl s) {
                boolean isEditingThis = editState != null && s.key().equals(editState.key());
                if (isEditingThis) {
                    String buf = editState.text() != null ? editState.text() : "";
                    int rawTextW = font.width(buf);
                    int boxW = Math.max(42, Math.min(cw - 24, rawTextW + 14));
                    int boxX = cx + cw - boxW;
                    int boxY = cy - 1;
                    int boxH = 11;
                    if (editBoundsConsumer != null) {
                        editBoundsConsumer.accept(boxX, boxY, boxW, boxH);
                    }
                    String label = fitText(font, s.label(), Math.max(20, cw - boxW - 6));
                    graphics.text(font, Component.literal(label), cx, cy, JelloTheme.TEXT, false);
                    JelloTheme.fillRoundedCard(graphics, boxX, boxY, boxX + boxW, boxY + boxH, 3, 0xFF0A0A0F, JelloTheme.ACCENT);
                } else {
                    String valText = s.valueLabel().get();
                    int valW = font.width(valText);
                    boolean valHovered = ModernModuleSettings.isInsideSliderValueBox(mouseX, mouseY, cx, cy, cw, valW);
                    String label = fitText(font, s.label(), Math.max(24, cw - valW - 10));
                    graphics.text(font, Component.literal(label), cx, cy, JelloTheme.TEXT, false);
                    if (valHovered) {
                        int pillX = Math.max(cx + 22, cx + cw - valW - 3);
                        JelloTheme.fillRoundedCard(graphics, pillX, cy - 1, cx + cw + 2, cy + 10, 3,
                                withAlpha(JelloTheme.ACCENT, 0x2A), withAlpha(JelloTheme.ACCENT, 0x88));
                    }
                    graphics.text(font, Component.literal(valText), cx + cw - valW, cy,
                            valHovered ? JelloTheme.TEXT : JelloTheme.TEXT_DIM, false);
                }
                sliderDrawer.draw(cx, cy + 12, cw, 6, s.key(), s.pctGetter().get());
            } else if (control instanceof ModernModuleSettings.ToggleControl t) {
                boolean active = t.getter().get();
                boolean hovered = isInside(mouseX, mouseY, cx, cy, cw, t.height());
                int bg = active
                        ? (hovered ? JelloTheme.CARD_ACTIVE_HOVER : JelloTheme.CARD_ACTIVE)
                        : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
                int border = active ? withAlpha(JelloTheme.ACCENT, 0xCC) : JelloTheme.BORDER_SUBTLE;
                JelloTheme.fillRoundedCard(graphics, cx, cy, cx + cw, cy + t.height(), 5, bg, border);
                int swW = compact ? 24 : 28;
                int swH = compact ? 12 : 14;
                int swX = compact ? (cx + cw - padX - swW) : (cx + cw - 34);
                int swY = cy + (t.height() - swH) / 2;
                int labelMaxW = compact ? Math.max(24, cw - 36) : Math.max(24, cw - 46);
                String text = fitText(font, t.label(), labelMaxW);
                graphics.text(font, Component.literal(text), cx + padX, cy + 6,
                        active ? JelloTheme.TEXT : JelloTheme.TEXT_MUTED, false);
                switchDrawer.draw(swX, swY, swW, swH, "sub:" + modName + ":" + t.label(), active);
            } else if (control instanceof ModernModuleSettings.CycleControl c) {
                boolean hovered = isInside(mouseX, mouseY, cx, cy, cw, c.height());
                int bg = hovered ? withAlpha(JelloTheme.ACCENT, 0x44) : JelloTheme.CARD_INACTIVE;
                int border = hovered ? JelloTheme.ACCENT : withAlpha(JelloTheme.ACCENT, 0x99);
                JelloTheme.fillRoundedCard(graphics, cx, cy, cx + cw, cy + c.height(), 5, bg, border);
                String valText = c.valueLabel().get();
                int valW = font.width(valText);
                String label = fitText(font, c.label(), Math.max(18, cw - valW - padX * 2 - 6));
                graphics.text(font, Component.literal(label), cx + padX, cy + 6, JelloTheme.TEXT, false);
                graphics.text(font, Component.literal(valText), cx + cw - padX - valW, cy + 6, JelloTheme.ACCENT_HOVER, false);
            } else if (control instanceof ModernModuleSettings.ActionControl a) {
                boolean hovered = isInside(mouseX, mouseY, cx, cy, cw, a.height());
                int bg = hovered ? withAlpha(JelloTheme.ACCENT, 0x66) : withAlpha(JelloTheme.ACCENT, 0x33);
                int border = hovered ? JelloTheme.ACCENT : withAlpha(JelloTheme.ACCENT, 0xAA);
                JelloTheme.fillRoundedCard(graphics, cx, cy, cx + cw, cy + a.height(), 5, bg, border);
                String label = fitText(font, a.labelSupplier().get(), Math.max(20, cw - padX * 2));
                graphics.centeredText(font, Component.literal(label), cx + cw / 2, cy + 6, JelloTheme.TEXT);
            } else if (control instanceof ModernModuleSettings.ChipsControl cc) {
                renderChips(graphics, font, mouseX, mouseY, cx, cy, cw, placed.chipCols(), cc.options());
            }
        }
    }

    private static int withAlpha(int argb, int alphaByte) {
        return ((alphaByte & 0xFF) << 24) | (argb & 0x00FFFFFF);
    }

    private static void renderChips(
            GuiGraphicsExtractor graphics,
            Font font,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int w,
            int chipCols,
            List<ModernModuleSettings.ChipOption> options
    ) {
        int chipW = ModernInspectorLayout.computeChipWidth(w, chipCols);
        for (int i = 0; i < options.size(); i++) {
            ModernModuleSettings.ChipOption opt = options.get(i);
            int col = i % chipCols;
            int row = i / chipCols;
            int cx = x + col * (chipW + ModernInspectorLayout.CHIP_GAP_X);
            int cy = y + row * (ModernModuleSettings.CHIP_HEIGHT + ModernModuleSettings.CHIP_GAP_Y);
            boolean active = opt.activeGetter().get();
            boolean hovered = isInside(mouseX, mouseY, cx, cy, chipW, ModernModuleSettings.CHIP_HEIGHT);
            int bg = active
                    ? (hovered ? withAlpha(JelloTheme.ACCENT, 0x77) : withAlpha(JelloTheme.ACCENT, 0x55))
                    : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
            int border = active ? JelloTheme.ACCENT : (hovered ? JelloTheme.BORDER_ACTIVE_SOFT : JelloTheme.BORDER_SUBTLE);
            JelloTheme.fillRoundedCard(graphics, cx, cy, cx + chipW, cy + ModernModuleSettings.CHIP_HEIGHT, 4, bg, border);
            if (opt.icon() != null && !opt.icon().isEmpty()) {
                int iconX = cx + (chipW - 16) / 2;
                int iconY = cy + (ModernModuleSettings.CHIP_HEIGHT - 16) / 2;
                graphics.item(opt.icon(), iconX, iconY);
                if (hovered) {
                    graphics.setTooltipForNextFrame(font, opt.icon(), mouseX, mouseY);
                }
            } else {
                String label = fitText(font, opt.label(), chipW - 8);
                graphics.centeredText(font, Component.literal(label), cx + chipW / 2, cy + 5,
                        active ? JelloTheme.TEXT : JelloTheme.TEXT_MUTED);
            }
        }
    }

    static boolean handleControlsClick(
            Font font,
            int mouseX,
            int mouseY,
            int x,
            int startY,
            int w,
            List<ModernModuleSettings.Control> controls,
            SliderStartHandler sliderStartHandler,
            SliderEditHandler sliderEditHandler,
            Runnable soundPlayer
    ) {
        ModernInspectorLayout.ControlsFlow flow = ModernInspectorLayout.computeControlsFlow(x, startY, w, controls);
        for (ModernInspectorLayout.PlacedControl placed : flow.items()) {
            ModernModuleSettings.Control control = placed.control();
            int cx = placed.x();
            int cy = placed.y();
            int cw = placed.w();
            int ch = placed.h();
            if (mouseY >= cy - 2 && mouseY < cy + ch && mouseX >= cx - 2 && mouseX <= cx + cw + 2) {
                if (control instanceof ModernModuleSettings.SliderControl s) {
                    int valW = font != null ? font.width(s.valueLabel().get()) : 24;
                    if (sliderEditHandler != null
                            && ModernModuleSettings.isInsideSliderValueBox(mouseX, mouseY, cx, cy, cw, valW)) {
                        sliderEditHandler.onEditStart(s);
                        soundPlayer.run();
                        return true;
                    }
                    if (mouseY >= cy) {
                        float pct = Mth.clamp((float) (mouseX - cx) / (float) Math.max(1, cw), 0.0f, 1.0f);
                        sliderStartHandler.onStart(s, cx, cw, pct);
                        soundPlayer.run();
                        return true;
                    }
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
                    if (handleChipsClick(mouseX, mouseY, cx, cy, cw, placed.chipCols(), cc.options())) {
                        soundPlayer.run();
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean handleChipsClick(
            int mouseX,
            int mouseY,
            int x,
            int y,
            int w,
            int chipCols,
            List<ModernModuleSettings.ChipOption> options
    ) {
        int chipW = ModernInspectorLayout.computeChipWidth(w, chipCols);
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
        for (int c = 0; c < chipCols; c++) {
            int colStart = c * (chipW + ModernInspectorLayout.CHIP_GAP_X);
            if (relX >= colStart && relX <= colStart + chipW) {
                col = c;
                break;
            }
        }
        if (col < 0) {
            return false;
        }
        int idx = row * chipCols + col;
        if (idx >= 0 && idx < options.size()) {
            options.get(idx).onToggle().run();
            return true;
        }
        return false;
    }

    static String getModuleDescription(String name, String fallbackCategory) {
        return switch (name) {
            case "Viewmodel" -> "Held item/hand viewmodel, Item & Player Scale, AirWalker";
            case "Inventory HUD+" -> "On-screen inventory, armor & potion status";
            case "Fullbright" -> "Full night vision & clear sight in caves";
            case "Xray" -> "See selected ores/blocks with opacity & exposed filter";
            case "Block ESP" -> "Highlight selected blocks & tracers per block color";
            case "Freecam" -> "Detach camera to scout freely through blocks";
            case "Wings" -> "LiquidBounce cosmetic back wings (Lines & Dragon modes)";
            case "HUD Info" -> "FPS, Ping, TPS & coordinates on screen";
            case "TNT Timer" -> "Accurate fuse ticks & detonation timer";
            case "Keybinds HUD" -> "Active toggles & bound keys overlay";
            case "Player List" -> "Nearby players overlay with distance & health";
            case "TrueSight" -> "See invisible entities & players";
            case "Item ESP" -> "Glowing box around dropped items";
            case "Chunk Borders" -> "Show 16x16 chunk boundaries without F3";
            case "Light Overlay" -> "Highlight dark spawnable blocks (Red=Always, Yellow=Night)";
            case "Trajectories" -> "Predict arrow, pearl, potion & trident arc";
            case "FastBreak" -> "Break blocks faster with Vulcan or Vanilla mode";
            case "Fast Place" -> "Remove placing delay for rapid building";
            case "Fast Use" -> "Rapid consumption of food & potions";
            case "Auto Break" -> "Automatically mine selected target blocks";
            case "Auto Tool" -> "Automatically switch to best mining tool";
            case "Auto Crafter" -> "Automatically compact ores & items into blocks";
            case "Auto Sell" -> "Run /sell all when selected items enter inventory";
            case "Auto Drop" -> "Automatically throw selected junk items from inventory";
            case "No Strip" -> "Prevent accidentally stripping logs with an axe";
            case "MultiTask" -> "Use both hands simultaneously (e.g. mine while eating)";
            case "No Pause" -> "Prevent pausing the game when Alt+Tabbing (losing focus)";
            case "Better Place" -> "Place blocks in mid-air and from awkward angles";
            case "Auto Wheat Farm" -> "Human-like hoe & wheat seed planter inside .farm coordinates";
            case "Auto Fish" -> "Automatically right-click green bar minigame & recast rod";
            case "Auto Spawner" -> "S-Snake spawner looter, /craft iron blocks & chest depositor";
            case "Spawner Mode" -> "Toggle all spawners in room to Gold Block or Gold Nugget";
            case "Auto Sprint" -> "Keep sprinting automatically when moving";
            case "Auto Walk" -> "Hold chosen movement direction automatically";
            case "Safe Walk" -> "Prevent falling off block edges like sneaking";
            case "Item Sucker" -> "Magnetically move toward & collect nearby dropped items";
            case "Better Chat" -> "Infinite chat history, anti-clear & copy";
            case "No Pitch Limit" -> "Unlock vertical camera pitch limits";
            case "Abort Breaking" -> "Prevent block-break reset glitches";
            case "Anti Hunger" -> "Filter sprint packets to reduce hunger drain";
            case "Anti Levitation" -> "Ignore shulker levitation float effect";
            case "Backtrack" -> "Briefly hold opponent move packets for hits";
            case "New Velocity" -> "Reduce or jump-reset incoming knockback";
            case "Disabler" -> "Vulcan & Grim packet alignment rules";
            case "Exploit Guard" -> "Drop abusive particle, knockback & URL packets";
            case "Ghost Hand" -> "Open chests & containers through solid walls";
            case "No Mining Trace" -> "Mine blocks directly through entities & mobs";
            case "No Slowdown" -> "Keep full speed while eating, bowing & blocking";
            case "Ping Spoof" -> "Delay keep-alive & pong packets cleanly";
            case "Packet Mine" -> "Mine blocks with packets & instant rebreak";
            case "Portal GUI" -> "Keep chat & screens open inside Nether portals";
            case "Resource Pack Spoof" -> "Accept server packs without downloading them";
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
