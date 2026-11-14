package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Full-screen drag-and-drop HUD layout editor modeled after the Inventory HUD+
 * grid editor, allowing Keybinds HUD, Stats HUD (FPS/TPS/Ping/Speed), and
 * Position HUD (Direction/Nether/Pos) to be positioned anywhere on screen.
 */
public class HudLayoutEditorScreen extends Screen {

    private enum HudTarget {
        NONE,
        KEYBINDS,
        STATS,
        POSITION
    }

    private final Screen parent;
    private HudTarget dragging = HudTarget.NONE;
    private int dragOffsetX = 0;
    private int dragOffsetY = 0;

    public HudLayoutEditorScreen(Screen parent) {
        super(Component.literal("HUD Layout Editor"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        ModConfig.getInstance().save();
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // Dimmed backdrop
        graphics.fill(0, 0, this.width, this.height, 0x8807090E);

        // 3x3 dashed alignment grid (1/3 & 2/3 guides)
        drawDashedGrid(graphics);

        ModConfig config = ModConfig.getInstance();

        int[] kbBounds = getKeybindsBounds(config);
        int[] statsBounds = StatsHudRenderer.resolveStatsBounds(this.font, this.width, this.height, config);
        int[] posBounds = StatsHudRenderer.resolvePositionBounds(this.font, this.width, this.height, config);

        // Render live Keybinds HUD preview underneath its drag handle
        KeybindsHudRenderer.renderBox(
                graphics, this.font,
                kbBounds[0], kbBounds[1],
                mouseX, mouseY,
                this.dragging == HudTarget.KEYBINDS || isInside(mouseX, mouseY, kbBounds),
                true
        );

        drawDraggableBox(
                graphics, mouseX, mouseY,
                kbBounds[0], kbBounds[1], kbBounds[2], kbBounds[3],
                "Keybinds",
                config.keybindsHudEnabled,
                this.dragging == HudTarget.KEYBINDS,
                0x36FF1E2D, 0xCCFF4B5C
        );

        drawDraggableBox(
                graphics, mouseX, mouseY,
                statsBounds[0], statsBounds[1], statsBounds[2], statsBounds[3],
                "FPS / TPS / Ping",
                config.hudInfoEnabled,
                this.dragging == HudTarget.STATS,
                0x4436D37C, 0xDD4CF094
        );

        drawDraggableBox(
                graphics, mouseX, mouseY,
                posBounds[0], posBounds[1], posBounds[2], posBounds[3],
                "Position",
                config.hudInfoEnabled,
                this.dragging == HudTarget.POSITION,
                0x484F6BFF, 0xDD7B92FF
        );

        // Bottom-center floating toolbar (Reset All + Done)
        int[] resetBtn = getResetButtonBounds();
        int[] doneBtn = getDoneButtonBounds();
        int barX = resetBtn[0] - 8;
        int barY = resetBtn[1] - 6;
        int barW = (doneBtn[0] + doneBtn[2]) - resetBtn[0] + 16;
        int barH = resetBtn[3] + 12;

        JelloTheme.fillRoundedCard(graphics, barX, barY, barX + barW, barY + barH, 8, 0xE610131C, 0x99FF1E2D);

        boolean resetHover = isInside(mouseX, mouseY, resetBtn);
        boolean doneHover = isInside(mouseX, mouseY, doneBtn);
        JelloTheme.fillRoundedCard(
                graphics, resetBtn[0], resetBtn[1], resetBtn[0] + resetBtn[2], resetBtn[1] + resetBtn[3],
                5, resetHover ? 0x55FF3D4E : 0x28FFFFFF, 0x66FFFFFF
        );
        JelloTheme.fillRoundedCard(
                graphics, doneBtn[0], doneBtn[1], doneBtn[0] + doneBtn[2], doneBtn[1] + doneBtn[3],
                5, doneHover ? 0xFFFF3D4E : 0xDDFF1E2D, 0xFFFF6674
        );

        String resetLabel = "Reset";
        String doneLabel = "Done (ESC)";
        graphics.text(this.font, Component.literal(resetLabel),
                resetBtn[0] + (resetBtn[2] - this.font.width(resetLabel)) / 2,
                resetBtn[1] + 6, 0xFFFFFFFF, false);
        graphics.text(this.font, Component.literal(doneLabel),
                doneBtn[0] + (doneBtn[2] - this.font.width(doneLabel)) / 2,
                doneBtn[1] + 6, 0xFFFFFFFF, false);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean wasHandled) {
        if (event.button() == 0) {
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            ModConfig config = ModConfig.getInstance();

            if (isInside(mouseX, mouseY, getResetButtonBounds())) {
                resetAllHudPositions(config);
                return true;
            }
            if (isInside(mouseX, mouseY, getDoneButtonBounds())) {
                onClose();
                return true;
            }

            int[] kb = getKeybindsBounds(config);
            if (isInside(mouseX, mouseY, kb)) {
                this.dragging = HudTarget.KEYBINDS;
                this.dragOffsetX = mouseX - kb[0];
                this.dragOffsetY = mouseY - kb[1];
                return true;
            }

            int[] stats = StatsHudRenderer.resolveStatsBounds(this.font, this.width, this.height, config);
            if (isInside(mouseX, mouseY, stats)) {
                this.dragging = HudTarget.STATS;
                this.dragOffsetX = mouseX - stats[0];
                this.dragOffsetY = mouseY - stats[1];
                return true;
            }

            int[] pos = StatsHudRenderer.resolvePositionBounds(this.font, this.width, this.height, config);
            if (isInside(mouseX, mouseY, pos)) {
                this.dragging = HudTarget.POSITION;
                this.dragOffsetX = mouseX - pos[0];
                this.dragOffsetY = mouseY - pos[1];
                return true;
            }
        }
        return super.mouseClicked(event, wasHandled);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() == 0 && this.dragging != HudTarget.NONE) {
            int mouseX = (int) event.x();
            int mouseY = (int) event.y();
            ModConfig config = ModConfig.getInstance();

            switch (this.dragging) {
                case KEYBINDS -> {
                    int[] b = getKeybindsBounds(config);
                    config.keybindsHudX = Mth.clamp(mouseX - this.dragOffsetX, 2, Math.max(2, this.width - b[2] - 2));
                    config.keybindsHudY = Mth.clamp(mouseY - this.dragOffsetY, 2, Math.max(2, this.height - b[3] - 2));
                }
                case STATS -> {
                    int[] b = StatsHudRenderer.resolveStatsBounds(this.font, this.width, this.height, config);
                    config.statsHudX = Mth.clamp(mouseX - this.dragOffsetX, 2, Math.max(2, this.width - b[2] - 2));
                    config.statsHudY = Mth.clamp(mouseY - this.dragOffsetY, 2, Math.max(2, this.height - b[3] - 2));
                }
                case POSITION -> {
                    int[] b = StatsHudRenderer.resolvePositionBounds(this.font, this.width, this.height, config);
                    config.posHudX = Mth.clamp(mouseX - this.dragOffsetX, 2, Math.max(2, this.width - b[2] - 2));
                    config.posHudY = Mth.clamp(mouseY - this.dragOffsetY, 2, Math.max(2, this.height - b[3] - 2));
                }
                case NONE -> {
                }
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && this.dragging != HudTarget.NONE) {
            this.dragging = HudTarget.NONE;
            ModConfig.getInstance().save();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    public static void resetAllHudPositions(ModConfig config) {
        config.keybindsHudX = 8;
        config.keybindsHudY = 60;
        config.statsHudX = 4;
        config.statsHudY = 4;
        config.posHudX = -1;
        config.posHudY = -1;
        config.save();
    }

    private int[] getKeybindsBounds(ModConfig config) {
        List<KeybindsHudRenderer.BoundDisplayItem> items = KeybindsHudRenderer.getBoundItems();
        int w = KeybindsHudRenderer.calculateWidth(this.font, items);
        int rows = items.isEmpty() ? 1 : items.size();
        int h = KeybindsHudRenderer.HEADER_HEIGHT + rows * KeybindsHudRenderer.ROW_HEIGHT + 4;
        int x = Mth.clamp(config.keybindsHudX, 2, Math.max(2, this.width - w - 2));
        int y = Mth.clamp(config.keybindsHudY, 2, Math.max(2, this.height - h - 2));
        return new int[]{x, y, w, h};
    }

    private void drawDraggableBox(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            int x,
            int y,
            int w,
            int h,
            String label,
            boolean enabled,
            boolean activeDrag,
            int fillColor,
            int borderColor
    ) {
        boolean hovered = isInside(mouseX, mouseY, x, y, w, h);
        int bg = activeDrag ? ((fillColor & 0x00FFFFFF) | 0x77000000)
                : (hovered ? ((fillColor & 0x00FFFFFF) | 0x5A000000) : fillColor);
        if (!enabled) {
            bg = 0x3320242E;
            borderColor = 0x8864748B;
        }

        graphics.fill(x, y, x + w, y + h, bg);
        graphics.fill(x, y, x + w, y + 1, borderColor);
        graphics.fill(x, y + h - 1, x + w, y + h, borderColor);
        graphics.fill(x, y, x + 1, y + h, borderColor);
        graphics.fill(x + w - 1, y, x + w, y + h, borderColor);

        String title = enabled ? label : (label + " (OFF)");
        int textX = x + Math.max(3, (w - this.font.width(title)) / 2);
        int textY = y + Math.max(2, (h - 8) / 2);
        graphics.text(this.font, Component.literal(title), textX, textY, enabled ? 0xFFFFFFFF : 0xFF94A3B8, true);

        if (hovered || activeDrag) {
            String coord = x + ", " + y;
            int coordY = y > 14 ? y - 11 : y + h + 3;
            graphics.text(this.font, Component.literal(coord), x, coordY, 0xFFE2E8F0, true);
        }
    }

    private void drawDashedGrid(GuiGraphicsExtractor graphics) {
        int lineColor = 0x44CBD5E1;
        int x1 = this.width / 3;
        int x2 = (this.width * 2) / 3;
        int y1 = this.height / 3;
        int y2 = (this.height * 2) / 3;

        for (int y = 0; y < this.height; y += 6) {
            graphics.fill(x1, y, x1 + 1, Math.min(this.height, y + 3), lineColor);
            graphics.fill(x2, y, x2 + 1, Math.min(this.height, y + 3), lineColor);
        }
        for (int x = 0; x < this.width; x += 6) {
            graphics.fill(x, y1, Math.min(this.width, x + 3), y1 + 1, lineColor);
            graphics.fill(x, y2, Math.min(this.width, x + 3), y2 + 1, lineColor);
        }

        if (this.dragging != HudTarget.NONE) {
            int cx = this.width / 2;
            int cy = this.height / 2;
            for (int y = 0; y < this.height; y += 8) {
                graphics.fill(cx, y, cx + 1, Math.min(this.height, y + 4), 0x33FF4B5C);
            }
            for (int x = 0; x < this.width; x += 8) {
                graphics.fill(x, cy, Math.min(this.width, x + 4), cy + 1, 0x33FF4B5C);
            }
        }
    }

    private int[] getResetButtonBounds() {
        int btnW = 68;
        int btnH = 20;
        int gap = 8;
        int totalW = btnW + gap + 92;
        int startX = (this.width - totalW) / 2;
        int btnY = this.height - 34;
        return new int[]{startX, btnY, btnW, btnH};
    }

    private int[] getDoneButtonBounds() {
        int[] reset = getResetButtonBounds();
        return new int[]{reset[0] + reset[2] + 8, reset[1], 92, reset[3]};
    }

    private static boolean isInside(int mx, int my, int[] box) {
        return isInside(mx, my, box[0], box[1], box[2], box[3]);
    }

    private static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
