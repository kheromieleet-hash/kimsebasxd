package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * Shared base screen for searchable multi-column item/block catalog pickers.
 * Handles window chrome, header Done button, search input box, cell rendering,
 * scrollbar interaction, wheel scrolling, and ESC dismissal.
 */
abstract class AbstractCatalogPickerScreen extends Screen {

    protected static final int HEADER_H = 42;
    protected static final int PAD = 14;
    protected static final int TOOLBAR_BTN_H = 22;
    protected static final int CELL_H = 26;
    protected static final int CELL_GAP = 6;
    protected static final int COLS = 3;

    protected final Screen parent;
    protected int windowX;
    protected int windowY;
    protected int windowW;
    protected int windowH;

    protected EditBox searchBox;
    protected String searchQuery = "";

    protected double scrollAmount = 0.0;
    protected int contentTotalHeight = 0;
    protected boolean draggingScrollbar = false;
    protected int scrollbarDragOffsetY = 0;

    protected AbstractCatalogPickerScreen(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    protected abstract void rebuildFilteredList();

    protected abstract int gridTop();

    protected abstract int gridBottom();

    protected void initWindowBounds(int maxW, int minW, int maxH, int minH, float pct) {
        this.windowW = Math.min(maxW, Math.max(minW, (int) (this.width * pct)));
        this.windowH = Math.min(maxH, Math.max(minH, (int) (this.height * pct)));
        this.windowX = (this.width - this.windowW) / 2;
        this.windowY = (this.height - this.windowH) / 2;
    }

    protected void initSearchWidget(int searchX, int searchY, int searchW, String hint) {
        String prev = this.searchQuery;
        this.searchBox = new EditBox(this.font, searchX + 24, searchY + 6, Math.max(40, searchW - 30), 14, Component.literal("Search"));
        this.searchBox.setHint(Component.literal(hint));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(40);
        this.searchBox.setTextColor(JelloTheme.TEXT);
        this.searchBox.setValue(prev);
        this.searchBox.setResponder(text -> {
            this.searchQuery = text == null ? "" : text.trim().toLowerCase(java.util.Locale.ROOT);
            this.scrollAmount = 0.0;
            rebuildFilteredList();
        });
        this.addRenderableWidget(this.searchBox);
    }

    protected void renderWindowFrame(GuiGraphicsExtractor graphics, int mouseX, int mouseY, String headerTitle, String subtitle) {
        graphics.fill(0, 0, this.width, this.height, JelloTheme.BACKDROP_DIM);
        JelloTheme.drawGlowShadow(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, 0x66000000, 4);
        JelloTheme.fillRoundedCard(graphics, this.windowX, this.windowY, this.windowX + this.windowW, this.windowY + this.windowH, 10, JelloTheme.WINDOW_BG, JelloTheme.WINDOW_BORDER);

        if (JelloTheme.isLeafTheme() && !PanicHandler.isPanicActive()) {
            SestiaTitleBackgroundRenderer.renderGuiAmbientLeaves(
                    graphics,
                    this.windowX + 4,
                    this.windowY + 4,
                    this.windowW - 8,
                    this.windowH - 8,
                    mouseX,
                    mouseY
            );
        }

        graphics.text(this.font, Component.literal("§l" + headerTitle), this.windowX + PAD, this.windowY + 11, JelloTheme.TEXT, false);
        graphics.text(this.font, Component.literal(subtitle), this.windowX + PAD, this.windowY + 24, JelloTheme.TEXT_MUTED, false);

        int[] done = getDoneButtonBounds();
        boolean hovDone = isInside(mouseX, mouseY, done[0], done[1], done[2], done[3]);
        if (hovDone && !PanicHandler.isPanicActive()) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
        JelloTheme.fillRoundedCard(graphics, done[0], done[1], done[0] + done[2], done[1] + done[3], 5,
                hovDone ? JelloTheme.ACCENT_HOVER : JelloTheme.ACCENT, JelloTheme.BORDER_ACTIVE_SOFT);
        graphics.centeredText(this.font, Component.literal("Done"), done[0] + done[2] / 2, done[1] + 7, 0xFFFFFFFF);

        graphics.fill(this.windowX + 1, this.windowY + HEADER_H, this.windowX + this.windowW - 1, this.windowY + HEADER_H + 1, JelloTheme.BORDER_SUBTLE);
    }

    protected void renderSearchFieldBox(GuiGraphicsExtractor graphics, int searchX, int toolY, int searchW) {
        boolean searchFocused = this.searchBox != null && this.searchBox.isFocused();
        JelloTheme.fillRoundedCard(graphics, searchX, toolY, searchX + searchW, toolY + TOOLBAR_BTN_H, 5,
                JelloTheme.CONTAINER_BG, searchFocused ? JelloTheme.BORDER_ACTIVE_SOFT : JelloTheme.BORDER_SUBTLE);
        ModernIcons.drawSearchIcon(graphics, searchX + 8, toolY + 7, searchFocused ? JelloTheme.ACCENT_HOVER : JelloTheme.TEXT_MUTED);
    }

    protected void drawToolbarBtn(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int x, int y, int w, int h, String label) {
        boolean hov = isInside(mouseX, mouseY, x, y, w, h);
        if (hov && !PanicHandler.isPanicActive()) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
        JelloTheme.fillRoundedCard(graphics, x, y, x + w, y + h, 5,
                hov ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CONTAINER_BG,
                hov ? JelloTheme.BORDER_ACTIVE_SOFT : JelloTheme.BORDER_SUBTLE);
        graphics.centeredText(this.font, Component.literal(fitText(label, w - 8)), x + w / 2, y + (h - 8) / 2, JelloTheme.TEXT);
    }

    protected void renderPickerCell(
            GuiGraphicsExtractor graphics,
            int cx,
            int cy,
            int cellW,
            ItemStack iconStack,
            String displayName,
            boolean selected,
            boolean inspecting,
            boolean hovered,
            int rightReserve,
            int checkRightOffset
    ) {
        if (hovered && !PanicHandler.isPanicActive()) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
        int bg = selected
                ? (hovered ? JelloTheme.CARD_ACTIVE_HOVER : JelloTheme.CARD_ACTIVE)
                : (hovered ? JelloTheme.CARD_INACTIVE_HOVER : JelloTheme.CARD_INACTIVE);
        int border = inspecting
                ? 0xFFFFFFFF
                : (selected ? JelloTheme.BORDER_ACTIVE_SOFT : (hovered ? JelloTheme.BORDER_SUBTLE : JelloTheme.DIVIDER));

        JelloTheme.fillRoundedCard(graphics, cx, cy, cx + cellW, cy + CELL_H, 5, bg, border);
        if (iconStack != null && !iconStack.isEmpty()) {
            graphics.item(iconStack, cx + 5, cy + (CELL_H - 16) / 2);
        } else {
            graphics.fill(cx + 7, cy + 7, cx + 19, cy + 19, JelloTheme.ACCENT);
        }

        int maxTextW = cellW - 28 - rightReserve;
        String label = fitText(displayName, maxTextW);
        graphics.text(this.font, Component.literal(label), cx + 25, cy + (CELL_H - 8) / 2,
                selected ? JelloTheme.TEXT : JelloTheme.TEXT_MUTED, false);

        if (selected) {
            ModernIcons.drawCheckIcon(graphics, cx + cellW - checkRightOffset, cy + (CELL_H - 8) / 2, JelloTheme.ACCENT_HOVER);
        }
    }

    protected void renderScrollbar(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int top, int bottom, int gridH) {
        if (this.contentTotalHeight <= gridH) {
            return;
        }
        int trackX = this.windowX + this.windowW - 8;
        JelloTheme.fillRounded(graphics, trackX, top, trackX + 3, bottom, 1, JelloTheme.SCROLLBAR_TRACK);
        int thumbH = Math.max(24, (int) ((float) gridH * gridH / this.contentTotalHeight));
        int maxScroll = Math.max(1, this.contentTotalHeight - gridH);
        int thumbY = top + (int) ((gridH - thumbH) * (this.scrollAmount / maxScroll));
        boolean thumbHov = this.draggingScrollbar || (mouseX >= trackX - 4 && mouseX <= trackX + 7 && mouseY >= thumbY && mouseY <= thumbY + thumbH);
        if (thumbHov && !PanicHandler.isPanicActive()) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
        JelloTheme.fillRounded(graphics, trackX, thumbY, trackX + 3, thumbY + thumbH, 1, thumbHov ? JelloTheme.SCROLLBAR_THUMB_HOVER : JelloTheme.SCROLLBAR_THUMB);
    }

    protected void updateContentScrollBounds(int itemCount, int gridH) {
        int rows = (itemCount + COLS - 1) / COLS;
        this.contentTotalHeight = Math.max(gridH, rows * (CELL_H + CELL_GAP));
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0, Math.max(0, this.contentTotalHeight - gridH));
    }

    protected int gridInnerX() {
        return this.windowX + PAD;
    }

    protected int gridInnerW() {
        return this.windowW - PAD * 2 - 8;
    }

    protected int gridCellW(int innerW) {
        return (innerW - CELL_GAP * (COLS - 1)) / COLS;
    }

    protected int[] getDoneButtonBounds() {
        int doneW = 58;
        int doneH = 22;
        return new int[]{
                this.windowX + this.windowW - PAD - doneW,
                this.windowY + (HEADER_H - doneH) / 2,
                doneW,
                doneH
        };
    }

    protected boolean handleDoneButtonClick(int mouseX, int mouseY) {
        int[] done = getDoneButtonBounds();
        if (isInside(mouseX, mouseY, done[0], done[1], done[2], done[3])) {
            playClickSound();
            onClose();
            return true;
        }
        return false;
    }

    protected boolean handleSearchFocusClick(int mouseX, int mouseY, int searchX, int toolY, int searchW, MouseButtonEvent event, boolean wasHandled) {
        if (isInside(mouseX, mouseY, searchX, toolY, searchW, TOOLBAR_BTN_H) && this.searchBox != null) {
            this.setFocused(this.searchBox);
            this.searchBox.setFocused(true);
            super.mouseClicked(event, wasHandled);
            return true;
        }
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }
        return false;
    }

    protected boolean handleScrollbarClick(int mouseX, int mouseY) {
        int top = gridTop();
        int bottom = gridBottom();
        int gridH = Math.max(40, bottom - top);
        if (this.contentTotalHeight <= gridH) {
            return false;
        }
        int trackX = this.windowX + this.windowW - 8;
        if (mouseX >= trackX - 4 && mouseX <= trackX + 7 && mouseY >= top && mouseY <= bottom) {
            int thumbH = Math.max(24, (int) ((float) gridH * gridH / this.contentTotalHeight));
            int maxScroll = Math.max(1, this.contentTotalHeight - gridH);
            int thumbY = top + (int) ((gridH - thumbH) * (this.scrollAmount / maxScroll));
            if (mouseY >= thumbY && mouseY <= thumbY + thumbH) {
                this.draggingScrollbar = true;
                this.scrollbarDragOffsetY = mouseY - thumbY;
            } else {
                int targetThumbY = Mth.clamp(mouseY - thumbH / 2, top, bottom - thumbH);
                double ratio = (double) (targetThumbY - top) / Math.max(1, gridH - thumbH);
                this.scrollAmount = Mth.clamp(ratio * maxScroll, 0.0, maxScroll);
                this.draggingScrollbar = true;
                this.scrollbarDragOffsetY = thumbH / 2;
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingScrollbar && (MouseButtonMapping.isLeftClick(event.button()) || event.button() == 0)) {
            int top = gridTop();
            int bottom = gridBottom();
            int gridH = Math.max(40, bottom - top);
            if (this.contentTotalHeight > gridH) {
                int thumbH = Math.max(24, (int) ((float) gridH * gridH / this.contentTotalHeight));
                int maxScroll = Math.max(1, this.contentTotalHeight - gridH);
                int targetThumbY = Mth.clamp((int) event.y() - this.scrollbarDragOffsetY, top, bottom - thumbH);
                double ratio = (double) (targetThumbY - top) / Math.max(1, gridH - thumbH);
                this.scrollAmount = Mth.clamp(ratio * maxScroll, 0.0, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (this.draggingScrollbar && (MouseButtonMapping.isLeftClick(event.button()) || event.button() == 0)) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int gridH = Math.max(40, gridBottom() - gridTop());
        int maxScroll = Math.max(0, this.contentTotalHeight - gridH);
        this.scrollAmount = Mth.clamp(this.scrollAmount - verticalAmount * 28.0, 0.0, maxScroll);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    protected String fitText(String text, int maxW) {
        if (text == null || maxW <= 8 || this.font.width(text) <= maxW) {
            return text != null ? text : "";
        }
        String trimmed = text;
        while (trimmed.length() > 2 && this.font.width(trimmed + "..") > maxW) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }

    protected static boolean isInside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    protected static void playClickSound() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
            }
        } catch (Throwable ignored) {
        }
    }
}
