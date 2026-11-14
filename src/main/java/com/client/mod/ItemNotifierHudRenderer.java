package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks inventory item count changes and renders recent pickup/drop alerts on the HUD
 * (e.g. "-1 [Dirt icon] Dirt" or "+5 [Diamond icon] Diamond").
 */
public final class ItemNotifierHudRenderer {
    private static final int ROW_HEIGHT = 18;
    private static final int ICON_SIZE = 16;
    private static final int GAP = 4;
    private static final long FADE_OUT_MS = 500L;

    private static final Map<Item, Integer> PREVIOUS_COUNTS = new HashMap<>();
    private static final Map<Item, ItemStack> SAMPLE_STACKS = new HashMap<>();
    private static final List<NotificationEntry> ENTRIES = new ArrayList<>();
    private static int trackedPlayerId = -1;

    private ItemNotifierHudRenderer() {
    }

    private static final class NotificationEntry {
        final Item item;
        ItemStack icon;
        String displayName;
        int delta;
        long updatedAtMs;

        NotificationEntry(Item item, ItemStack icon, int delta, long nowMs) {
            this.item = item;
            this.icon = icon;
            this.displayName = icon.getHoverName().getString();
            this.delta = delta;
            this.updatedAtMs = nowMs;
        }
    }

    public static void reset() {
        PREVIOUS_COUNTS.clear();
        SAMPLE_STACKS.clear();
        ENTRIES.clear();
        trackedPlayerId = -1;
    }

    public static void tick(Minecraft client) {
        ModConfig cfg = ModConfig.getInstance();
        if (client == null || client.player == null || !cfg.itemNotifierEnabled) {
            reset();
            return;
        }

        long now = System.currentTimeMillis();
        long ttlMs = Math.max(500L, Math.round(cfg.itemNotifierDuration * 1000.0f));
        pruneExpired(now, ttlMs);

        Map<Item, Integer> currentCounts = new HashMap<>();
        Map<Item, ItemStack> currentSamples = new HashMap<>();

        Inventory inv = client.player.getInventory();
        int size = inv.getContainerSize();
        for (int i = 0; i < size; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                currentCounts.merge(item, stack.getCount(), Integer::sum);
                currentSamples.putIfAbsent(item, stack.copyWithCount(1));
            }
        }

        if (client.player.containerMenu != null) {
            ItemStack carried = client.player.containerMenu.getCarried();
            if (!carried.isEmpty()) {
                Item item = carried.getItem();
                currentCounts.merge(item, carried.getCount(), Integer::sum);
                currentSamples.putIfAbsent(item, carried.copyWithCount(1));
            }
        }

        int playerId = client.player.getId();
        if (trackedPlayerId != playerId) {
            PREVIOUS_COUNTS.clear();
            PREVIOUS_COUNTS.putAll(currentCounts);
            SAMPLE_STACKS.clear();
            SAMPLE_STACKS.putAll(currentSamples);
            ENTRIES.clear();
            trackedPlayerId = playerId;
            return;
        }

        Set<Item> allItems = new HashSet<>(PREVIOUS_COUNTS.keySet());
        allItems.addAll(currentCounts.keySet());

        for (Item item : allItems) {
            int prev = PREVIOUS_COUNTS.getOrDefault(item, 0);
            int cur = currentCounts.getOrDefault(item, 0);
            int diff = cur - prev;
            if (diff != 0) {
                ItemStack icon = currentSamples.get(item);
                if (icon == null) {
                    icon = SAMPLE_STACKS.get(item);
                }
                if (icon == null) {
                    icon = new ItemStack(item);
                }
                recordChange(item, icon, diff, now, cfg.itemNotifierMaxItems);
            }
        }

        PREVIOUS_COUNTS.clear();
        PREVIOUS_COUNTS.putAll(currentCounts);
        for (Map.Entry<Item, ItemStack> e : currentSamples.entrySet()) {
            SAMPLE_STACKS.put(e.getKey(), e.getValue());
        }
    }

    private static void recordChange(Item item, ItemStack icon, int diff, long now, int maxItems) {
        for (int i = 0; i < ENTRIES.size(); i++) {
            NotificationEntry existing = ENTRIES.get(i);
            if (existing.item == item) {
                existing.delta += diff;
                existing.icon = icon;
                existing.displayName = icon.getHoverName().getString();
                existing.updatedAtMs = now;
                if (existing.delta == 0) {
                    ENTRIES.remove(i);
                } else if (i > 0) {
                    ENTRIES.remove(i);
                    ENTRIES.add(0, existing);
                }
                return;
            }
        }

        ENTRIES.add(0, new NotificationEntry(item, icon, diff, now));
        while (ENTRIES.size() > maxItems) {
            ENTRIES.remove(ENTRIES.size() - 1);
        }
    }

    private static void pruneExpired(long now, long ttlMs) {
        Iterator<NotificationEntry> it = ENTRIES.iterator();
        while (it.hasNext()) {
            NotificationEntry entry = it.next();
            if (now - entry.updatedAtMs >= ttlMs) {
                it.remove();
            }
        }
    }

    public static void render(GuiGraphicsExtractor graphics) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.itemNotifierEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        if (mc.gui.screen() instanceof HudLayoutEditorScreen) {
            return;
        }
        if (ENTRIES.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        long ttlMs = Math.max(500L, Math.round(cfg.itemNotifierDuration * 1000.0f));
        pruneExpired(now, ttlMs);
        if (ENTRIES.isEmpty()) {
            return;
        }

        Font font = mc.font;
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int[] bounds = resolveBounds(font, screenW, screenH, cfg);
        int x = bounds[0];
        int y = bounds[1];

        int count = Math.min(ENTRIES.size(), cfg.itemNotifierMaxItems);
        for (int i = 0; i < count; i++) {
            NotificationEntry entry = ENTRIES.get(i);
            long age = now - entry.updatedAtMs;
            float alpha = 1.0f;
            if (age > ttlMs - FADE_OUT_MS) {
                alpha = Math.max(0.0f, Math.min(1.0f, (ttlMs - age) / (float) FADE_OUT_MS));
            }
            int alphaByte = Math.round(alpha * 255.0f);
            if (alphaByte <= 8) {
                continue;
            }

            String countText = (entry.delta > 0 ? "+" : "") + entry.delta;
            int baseCountColor = entry.delta > 0 ? 0x55FF55 : 0xFF5555;
            int countColor = (alphaByte << 24) | baseCountColor;
            int nameColor = (alphaByte << 24) | 0xFFFFFF;

            int rowY = y + i * ROW_HEIGHT;
            int textY = rowY + 4;
            int countW = font.width(countText);

            graphics.text(font, countText, x, textY, countColor, true);
            int iconX = x + countW + GAP;
            graphics.item(entry.icon, iconX, rowY);
            int nameX = iconX + ICON_SIZE + GAP;
            graphics.text(font, entry.displayName, nameX, textY, nameColor, true);
        }
    }

    public static int[] resolveBounds(Font font, int screenW, int screenH, ModConfig cfg) {
        int maxW = font.width("-16") + GAP + ICON_SIZE + GAP + font.width("Dirt");
        int rows = 1;
        if (!ENTRIES.isEmpty()) {
            rows = Math.min(ENTRIES.size(), cfg.itemNotifierMaxItems);
            maxW = 0;
            for (int i = 0; i < rows; i++) {
                NotificationEntry e = ENTRIES.get(i);
                String countText = (e.delta > 0 ? "+" : "") + e.delta;
                int rowW = font.width(countText) + GAP + ICON_SIZE + GAP + font.width(e.displayName);
                if (rowW > maxW) {
                    maxW = rowW;
                }
            }
        }
        int totalH = rows * ROW_HEIGHT;

        int x = cfg.itemNotifierX >= 0 ? cfg.itemNotifierX : Math.max(8, screenW / 2 + 105);
        int y = cfg.itemNotifierY >= 0 ? cfg.itemNotifierY : Math.max(8, screenH - 54);
        x = Math.max(2, Math.min(Math.max(2, screenW - maxW - 2), x));
        y = Math.max(2, Math.min(Math.max(2, screenH - totalH - 2), y));
        return new int[]{x, y, maxW, totalH};
    }
}
