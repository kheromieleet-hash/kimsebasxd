package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class KeybindsHudRenderer {
    public static final int HEADER_HEIGHT = 16;
    public static final int ROW_HEIGHT = 13;
    public static final int MIN_WIDTH = 112;

    public static class BoundDisplayItem {
        public final String name;
        public final String keyTag;
        public final boolean active;

        public BoundDisplayItem(String name, String keyTag, boolean active) {
            this.name = name;
            this.keyTag = keyTag;
            this.active = active;
        }
    }

    public static List<BoundDisplayItem> getBoundItems() {
        ModConfig config = ModConfig.getInstance();
        List<BoundDisplayItem> items = new ArrayList<>();
        List<ClickGuiScreen.ModuleEntry> allMods = ClickGuiScreen.getAllModules();
        if (allMods == null || config.moduleKeybinds == null) {
            return items;
        }

        for (ClickGuiScreen.ModuleEntry mod : allMods) {
            if (mod == null) continue;
            int key = config.getModuleKey(mod.name);
            if (key < 0) continue;

            boolean isMouse = config.isModuleKeyMouse(mod.name);
            boolean active = mod.getter.get();
            if (config.keybindsHudOnlyActive && !active) {
                continue;
            }

            String tag = "[" + ClickGuiScreen.getShortBindName(key, isMouse) + "]";
            items.add(new BoundDisplayItem(mod.name, tag, active));
        }
        return items;
    }

    public static int calculateWidth(Font font, List<BoundDisplayItem> items) {
        int maxW = MIN_WIDTH;
        if (items.isEmpty()) {
            int emptyW = font.width("No Binds Set") + 32;
            return Math.max(maxW, emptyW);
        }
        for (BoundDisplayItem item : items) {
            int w = font.width(item.name) + font.width(item.keyTag) + 26;
            if (w > maxW) {
                maxW = w;
            }
        }
        return maxW;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        ModConfig config = ModConfig.getInstance();
        if (!config.keybindsHudEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // Don't double render if ClickGuiScreen is already rendering it with drag handles
        if (mc.gui != null && ClickGuiScreen.isTargetScreen(mc.gui.screen())) {
            return;
        }

        List<BoundDisplayItem> items = getBoundItems();
        if (config.keybindsHudAutoHide && items.isEmpty()) {
            return;
        }

        renderBox(graphics, mc.font, config.keybindsHudX, config.keybindsHudY, -1, -1, false, false);
    }

    public static void renderBox(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY, boolean isHoveredOrDragging, boolean isGuiOpen) {
        ModConfig config = ModConfig.getInstance();
        List<BoundDisplayItem> items = getBoundItems();
        int width = calculateWidth(font, items);
        boolean collapsed = config.keybindsHudCollapsed;
        int rows = items.isEmpty() ? 1 : items.size();
        int bodyH = collapsed ? HEADER_HEIGHT : (HEADER_HEIGHT + rows * ROW_HEIGHT + 4);

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        x = Mth.clamp(x, 2, Math.max(2, screenW - width - 2));
        y = Mth.clamp(y, 2, Math.max(2, screenH - bodyH - 2));

        // 1. Drop shadow & glowing violet outline
        int shadowColor = isHoveredOrDragging ? 0x88E53935 : 0x55000000;
        JelloTheme.fill(graphics, x - 1, y - 1, x + width + 1, y + bodyH + 1, shadowColor);

        // 2. Background body (Dark Obsidian Crimson Glass)
        if (!collapsed) {
            JelloTheme.fill(graphics, x, y + HEADER_HEIGHT, x + width, y + bodyH, 0xD8120B0E);
        }

        // 3. Header background
        int headerBg = isHoveredOrDragging ? 0xFA261217 : 0xF0140B0E;
        JelloTheme.fill(graphics, x, y, x + width, y + HEADER_HEIGHT, headerBg);

        // 4. Dual-gradient accent line (Crimson -> Scarlet)
        int halfW = width / 2;
        JelloTheme.fill(graphics, x, y + HEADER_HEIGHT - 1, x + halfW, y + HEADER_HEIGHT, JelloTheme.ACCENT);
        JelloTheme.fill(graphics, x + halfW, y + HEADER_HEIGHT - 1, x + width, y + HEADER_HEIGHT, JelloTheme.ACCENT_HOVER);

        // 5. Header title
        JelloTheme.text(graphics, font, "§c⌨ §fKeybinds", x + 5, y + 4, 0xFFFFFFFF);

        // 6. Header interactive buttons (Collapse [-]/[+] and Close [X])
        int closeX = x + width - 14;
        boolean closeHov = isGuiOpen && mouseX >= closeX && mouseX <= closeX + 11 && mouseY >= y + 1 && mouseY <= y + 14;
        JelloTheme.text(graphics, font, "×", closeX + 2, y + 4, closeHov ? 0xFFFF3333 : 0x88FFFFFF);

        int collX = x + width - 26;
        boolean collHov = isGuiOpen && mouseX >= collX && mouseX <= collX + 11 && mouseY >= y + 1 && mouseY <= y + 14;
        String collSymbol = collapsed ? "+" : "−";
        JelloTheme.text(graphics, font, collSymbol, collX + 2, y + 4, collHov ? 0xFFFFFFFF : 0x88FFFFFF);

        // If collapsed, don't draw rows
        if (collapsed) {
            return;
        }

        // 7. Rows
        int curY = y + HEADER_HEIGHT + 3;
        if (items.isEmpty()) {
            String emptyMsg = config.keybindsHudOnlyActive ? "No Binds Active" : "No Binds Set";
            JelloTheme.textCentered(graphics, font, emptyMsg, x + width / 2, curY + 2, JelloTheme.TEXT_DIM);
        } else {
            for (BoundDisplayItem item : items) {
                // Status dot (bright green when active, subtle dim dot when inactive)
                int dotColor = item.active ? 0xFF00FF66 : 0x44FFFFFF;
                JelloTheme.fill(graphics, x + 5, curY + 4, x + 7, curY + 6, dotColor);

                // Module name
                int nameColor = item.active ? 0xFFFFFFFF : 0x88CCCCCC;
                JelloTheme.text(graphics, font, item.name, x + 11, curY + 2, nameColor);

                // Key tag
                int tagW = font.width(item.keyTag);
                int tagX = x + width - 5 - tagW;
                int tagColor = item.active ? JelloTheme.ACCENT : 0x66FFFFFF;
                JelloTheme.text(graphics, font, item.keyTag, tagX, curY + 2, tagColor);

                curY += ROW_HEIGHT;
            }
        }
    }
}
