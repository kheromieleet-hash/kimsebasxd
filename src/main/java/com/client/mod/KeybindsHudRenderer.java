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
        // Don't double render if ClickGuiScreen or HudLayoutEditorScreen is open
        if (mc.gui != null && (ClickGuiScreen.isTargetScreen(mc.gui.screen()) || mc.gui.screen() instanceof HudLayoutEditorScreen)) {
            return;
        }

        List<BoundDisplayItem> items = getBoundItems();
        if (config.keybindsHudAutoHide && items.isEmpty()) {
            return;
        }

        renderBoxWithItems(graphics, mc.font, config.keybindsHudX, resolveDefaultY(config), -1, -1, false, false, config, items);
    }

    public static int resolveDefaultY(ModConfig config) {
        if (config != null && config.keybindsHudX == 8 && config.keybindsHudY == 60
                && config.hudWatermarkEnabled && config.hudInfoEnabled && config.hudTelemetryEnabled) {
            return 74;
        }
        return config != null ? config.keybindsHudY : 60;
    }

    public static void renderBox(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY, boolean isHoveredOrDragging, boolean isGuiOpen) {
        renderBoxWithItems(graphics, font, x, y, mouseX, mouseY, isHoveredOrDragging, isGuiOpen, ModConfig.getInstance(), getBoundItems());
    }

    private static void renderBoxWithItems(
            GuiGraphicsExtractor graphics,
            Font font,
            int x,
            int y,
            int mouseX,
            int mouseY,
            boolean isHoveredOrDragging,
            boolean isGuiOpen,
            ModConfig config,
            List<BoundDisplayItem> items
    ) {
        int width = calculateWidth(font, items);
        int rows = items.isEmpty() ? 1 : items.size();
        int bodyH = HEADER_HEIGHT + rows * ROW_HEIGHT + 4;

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        x = Mth.clamp(x, 2, Math.max(2, screenW - width - 2));
        y = Mth.clamp(y, 2, Math.max(2, screenH - bodyH - 2));

        // 1. Soft rounded glass frame & outline
        boolean unified = config.hudUnifiedGlass;
        int cardBg = unified ? JelloTheme.hudCardBackground() : 0xD8120B0E;
        int shadowColor = isHoveredOrDragging
                ? (unified ? JelloTheme.hudCardBorder(true) : 0x88E53935)
                : (unified ? JelloTheme.hudCardBorder(false) : 0x55242431);
        JelloTheme.fillRoundedCard(graphics, x, y, x + width, y + bodyH, 5, cardBg, shadowColor);

        // 2. Header fill + dual-gradient accent line (Theme Accent -> Hover)
        int headerBg = unified ? JelloTheme.hudHeaderBackground(isHoveredOrDragging) : (isHoveredOrDragging ? 0xFA261217 : 0xF0140B0E);
        JelloTheme.fillRoundedTop(graphics, x + 1, y + 1, x + width - 1, y + HEADER_HEIGHT, 4, headerBg);
        int halfW = width / 2;
        JelloTheme.fill(graphics, x + 1, y + HEADER_HEIGHT - 1, x + halfW, y + HEADER_HEIGHT, JelloTheme.ACCENT);
        JelloTheme.fill(graphics, x + halfW, y + HEADER_HEIGHT - 1, x + width - 1, y + HEADER_HEIGHT, JelloTheme.ACCENT_HOVER);

        // 3. Centered header title with crisp vector keyboard icon
        int titleW = font.width("Keybinds");
        int groupW = 12 + titleW;
        int groupX = x + (width - groupW) / 2;
        ModernIcons.drawKeyboardIcon(graphics, groupX, y + 3, JelloTheme.ACCENT);
        int headerTextCol = unified ? JelloTheme.TEXT : 0xFFFFFFFF;
        JelloTheme.text(graphics, font, "Keybinds", groupX + 12, y + 4, headerTextCol);

        // 7. Rows
        int curY = y + HEADER_HEIGHT + 3;
        if (items.isEmpty()) {
            String emptyMsg = config.keybindsHudOnlyActive ? "No Binds Active" : "No Binds Set";
            JelloTheme.textCentered(graphics, font, emptyMsg, x + width / 2, curY + 2, unified ? JelloTheme.TEXT_MUTED : JelloTheme.TEXT_DIM);
        } else {
            for (BoundDisplayItem item : items) {
                // Status dot (bright green when active, subtle dim dot when inactive)
                int dotColor = item.active ? 0xFF00FF66 : (unified ? JelloTheme.TEXT_MUTED : 0x44FFFFFF);
                JelloTheme.fill(graphics, x + 5, curY + 4, x + 7, curY + 6, dotColor);

                // Module name
                int nameColor = item.active
                        ? (unified ? JelloTheme.TEXT : 0xFFFFFFFF)
                        : (unified ? JelloTheme.TEXT_MUTED : 0x88CCCCCC);
                JelloTheme.text(graphics, font, item.name, x + 11, curY + 2, nameColor);

                // Key tag
                int tagW = font.width(item.keyTag);
                int tagX = x + width - 5 - tagW;
                int tagColor = item.active
                        ? JelloTheme.ACCENT
                        : (unified ? JelloTheme.TEXT_MUTED : 0x66FFFFFF);
                JelloTheme.text(graphics, font, item.keyTag, tagX, curY + 2, tagColor);

                curY += ROW_HEIGHT;
            }
        }
    }
}
