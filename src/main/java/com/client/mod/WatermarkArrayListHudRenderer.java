package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Renders the theme-synchronized Sestia Watermark and Active Modules ArrayList HUD elements.
 */
public final class WatermarkArrayListHudRenderer {
    private static final int WM_HEIGHT = 16;
    private static final int ARR_ROW_HEIGHT = 12;
    private static final int MAX_ARRAY_ROWS = 14;
    private static final String WATERMARK_VER = "v" + ClientBuildInfo.getMeta().version;
    private static final Set<String> EXCLUDED_FROM_ARRAYLIST = Set.of(
            "HUD Info", "Keybinds HUD", "Player List", "Item Notifier", "Inventory HUD+"
    );

    private record ArrayRow(String name, int textW) {
    }

    private WatermarkArrayListHudRenderer() {
    }

    public static void render(GuiGraphicsExtractor graphics) {
        if (PanicHandler.isPanicActive()) return;
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.level == null || mc.font == null) return;
        if (mc.gui != null && (ClickGuiScreen.isTargetScreen(mc.gui.screen()) || mc.gui.screen() instanceof HudLayoutEditorScreen)) {
            return;
        }

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        if (cfg.hudWatermarkEnabled) {
            renderWatermark(graphics, mc.font, screenW, screenH, cfg);
        }
        if (cfg.hudArrayListEnabled) {
            renderArrayList(graphics, mc.font, screenW, screenH, cfg);
        }
    }

    private static void renderWatermark(GuiGraphicsExtractor graphics, Font font, int screenW, int screenH, ModConfig cfg) {
        int sestiaW = font.width("SESTIA");
        int verW = font.width(WATERMARK_VER);
        int mcW = font.width("26.3");
        int w = 8 + sestiaW + 4 + verW + 5 + mcW + 6;
        int rawX = cfg != null ? cfg.hudWatermarkX : 4;
        int rawY = cfg != null ? cfg.hudWatermarkY : 4;
        int x = Mth.clamp(rawX, 2, Math.max(2, screenW - w - 2));
        int y = Mth.clamp(rawY, 2, Math.max(2, screenH - WM_HEIGHT - 2));
        int h = WM_HEIGHT;

        int bg = cfg.hudUnifiedGlass ? JelloTheme.hudCardBackground() : 0xD8120B0E;
        int border = cfg.hudUnifiedGlass ? JelloTheme.hudCardBorder(false) : 0x55242431;
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 4, bg, border);
        JelloTheme.fill(graphics, x + 3, y + 3, x + 5, y + h - 3, JelloTheme.ACCENT);

        JelloTheme.text(graphics, font, "SESTIA", x + 8, y + 4, 0xFFFFFFFF);
        int verX = x + 8 + sestiaW + 4;
        JelloTheme.text(graphics, font, WATERMARK_VER, verX, y + 4, 0xFFA8B3C5);
        int mcX = verX + verW + 5;
        JelloTheme.text(graphics, font, "26.3", mcX, y + 4, JelloTheme.ACCENT_HOVER);
    }

    private static void renderArrayList(GuiGraphicsExtractor graphics, Font font, int screenW, int screenH, ModConfig cfg) {
        List<ArrayRow> rows = collectSortedArrayRows(font, false);
        if (rows.isEmpty()) return;

        int[] bounds = resolveArrayListBoundsForRows(font, screenW, screenH, cfg, rows);
        int boxX = bounds[0];
        int boxY = bounds[1];
        int boxW = bounds[2];
        boolean alignRight = (cfg.hudArrayListX < 0) || (boxX + boxW / 2 > screenW / 2);

        int count = rows.size();
        for (int i = 0; i < count; i++) {
            ArrayRow row = rows.get(i);
            int rowW = row.textW() + 10;
            int rowX = alignRight ? (boxX + boxW - rowW) : boxX;
            int rowY = boxY + i * ARR_ROW_HEIGHT;
            float t = count <= 1 ? 0.0f : (float) i / (count - 1);
            int barColor = JelloTheme.lerpColor(JelloTheme.ACCENT, JelloTheme.ACCENT_HOVER, t);

            if (cfg.hudUnifiedGlass) {
                int bg = (0xB8 << 24) | (JelloTheme.CONTAINER_BG & 0x00FFFFFF);
                JelloTheme.fill(graphics, rowX, rowY, rowX + rowW, rowY + ARR_ROW_HEIGHT, bg);
                if (alignRight) {
                    JelloTheme.fill(graphics, rowX + rowW - 2, rowY, rowX + rowW, rowY + ARR_ROW_HEIGHT, barColor);
                } else {
                    JelloTheme.fill(graphics, rowX, rowY, rowX + 2, rowY + ARR_ROW_HEIGHT, barColor);
                }
            }
            int textX = alignRight ? (rowX + 4) : (rowX + 6);
            JelloTheme.text(graphics, font, row.name(), textX, rowY + 2, 0xFFFFFFFF);
        }
    }

    public static int[] resolveWatermarkBounds(Font font, int screenW, int screenH, ModConfig cfg) {
        int w = font != null
                ? (8 + font.width("SESTIA") + 4 + font.width(WATERMARK_VER) + 5 + font.width("26.3") + 6)
                : 112;
        int rawX = cfg != null ? cfg.hudWatermarkX : 4;
        int rawY = cfg != null ? cfg.hudWatermarkY : 4;
        int x = Mth.clamp(rawX, 2, Math.max(2, screenW - w - 2));
        int y = Mth.clamp(rawY, 2, Math.max(2, screenH - WM_HEIGHT - 2));
        return new int[]{x, y, w, WM_HEIGHT};
    }

    public static int[] resolveArrayListBounds(Font font, int screenW, int screenH, ModConfig cfg) {
        List<ArrayRow> rows = collectSortedArrayRows(font, true);
        return resolveArrayListBoundsForRows(font, screenW, screenH, cfg, rows);
    }

    private static List<ArrayRow> collectSortedArrayRows(Font font, boolean fallbackIfEmpty) {
        List<ClickGuiScreen.ModuleEntry> all = ClickGuiScreen.getAllModules();
        List<ArrayRow> rows = new ArrayList<>();
        if (all != null) {
            for (int i = 0; i < all.size(); i++) {
                ClickGuiScreen.ModuleEntry entry = all.get(i);
                if (entry == null || entry.getter == null || entry.name == null) continue;
                if (EXCLUDED_FROM_ARRAYLIST.contains(entry.name)) continue;
                if (Boolean.TRUE.equals(entry.getter.get())) {
                    int w = font != null ? font.width(entry.name) : entry.name.length() * 6;
                    rows.add(new ArrayRow(entry.name, w));
                }
            }
        }
        if (rows.isEmpty()) {
            if (!fallbackIfEmpty) {
                return rows;
            }
            String[] defaults = {"Auto Spawner", "Player List", "Fullbright"};
            for (String n : defaults) {
                rows.add(new ArrayRow(n, font != null ? font.width(n) : n.length() * 6));
            }
            return rows;
        }
        rows.sort(Comparator.comparingInt(ArrayRow::textW).reversed()
                .thenComparing(ArrayRow::name, String.CASE_INSENSITIVE_ORDER));
        if (rows.size() > MAX_ARRAY_ROWS) {
            return rows.subList(0, MAX_ARRAY_ROWS);
        }
        return rows;
    }

    private static int[] resolveArrayListBoundsForRows(Font font, int screenW, int screenH, ModConfig cfg, List<ArrayRow> rows) {
        int maxW = 72;
        for (int i = 0; i < rows.size(); i++) {
            int rowW = rows.get(i).textW() + 10;
            if (rowW > maxW) maxW = rowW;
        }
        int totalH = Math.max(ARR_ROW_HEIGHT, rows.size() * ARR_ROW_HEIGHT);

        boolean needsDefaultX = cfg == null || cfg.hudArrayListX < 0;
        boolean needsDefaultY = cfg == null || cfg.hudArrayListY < 0;
        int defaultX = screenW - maxW - 6;
        int defaultY = 6;
        if (needsDefaultX || needsDefaultY) {
            if (cfg != null && cfg.hudInfoEnabled && cfg.hudShowClock && cfg.clockHudY < 0) {
                defaultY = 24;
            }
            if (cfg != null && cfg.playerListEnabled && font != null) {
                int[] pl = PlayerListHudRenderer.resolveBounds(font, screenW, screenH, cfg);
                if (pl[0] > screenW / 2 && pl[1] < 40) {
                    defaultX = Math.max(4, pl[0] - maxW - 6);
                    defaultY = pl[1];
                }
            }
        }

        int rawX = needsDefaultX ? defaultX : cfg.hudArrayListX;
        int rawY = needsDefaultY ? defaultY : cfg.hudArrayListY;
        int x = Mth.clamp(rawX, 2, Math.max(2, screenW - maxW - 2));
        int y = Mth.clamp(rawY, 2, Math.max(2, screenH - totalH - 2));
        return new int[]{x, y, maxW, totalH};
    }
}
