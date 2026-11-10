package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ViewmodelConfigScreen extends Screen {
    private final Screen parent;
    private final ModConfig backupConfig;
    private final List<EntryRow> rows = new ArrayList<>();
    private double scrollAmount = 0.0;
    private int listTop;
    private int listBottom;
    private static final int ROW_HEIGHT = 24;

    public ViewmodelConfigScreen(Screen parent) {
        super(Component.literal("DulkirMod-Fabric"));
        this.parent = parent;
        this.backupConfig = ModConfig.getInstance().copy();
    }

    public ViewmodelConfigScreen(boolean inGame) {
        this((Screen) null);
    }

    @Override
    protected void init() {
        this.rows.clear();
        this.listTop = 44;
        this.listBottom = this.height - 32;

        ModConfig config = ModConfig.getInstance();
        int listLeft = this.width / 2 - 180;
        int controlX = listLeft + 158;
        int controlWidth = 150;
        int resetX = controlX + 154;
        int resetWidth = 48;

        // 1. posX (-150 to 150, default 0)
        addIntSliderRow("posX", null, controlX, controlWidth, resetX, resetWidth,
                -150, 150, 0, () -> config.posX, val -> {
                    config.posX = val;
                    config.save();
                });

        // 2. posY (-150 to 150, default 0)
        addIntSliderRow("posY", null, controlX, controlWidth, resetX, resetWidth,
                -150, 150, 0, () -> config.posY, val -> {
                    config.posY = val;
                    config.save();
                });

        // 3. posZ (-150 to 50, default 0)
        addIntSliderRow("posZ", null, controlX, controlWidth, resetX, resetWidth,
                -150, 50, 0, () -> config.posZ, val -> {
                    config.posZ = val;
                    config.save();
                });

        // 4. rotationX (-180 to 180, default 0)
        addIntSliderRow("rotationX", null, controlX, controlWidth, resetX, resetWidth,
                -180, 180, 0, () -> config.rotationX, val -> {
                    config.rotationX = val;
                    config.save();
                });

        // 5. rotationY (-180 to 180, default 0)
        addIntSliderRow("rotationY", null, controlX, controlWidth, resetX, resetWidth,
                -180, 180, 0, () -> config.rotationY, val -> {
                    config.rotationY = val;
                    config.save();
                });

        // 6. rotationZ (-180 to 180, default 0)
        addIntSliderRow("rotationZ", null, controlX, controlWidth, resetX, resetWidth,
                -180, 180, 0, () -> config.rotationZ, val -> {
                    config.rotationZ = val;
                    config.save();
                });

        // 7. Held Item Scale (0.1 to 2.0, default 1.0)
        addFloatSliderRow("Held Item Scale",
                Component.literal("Recommended range of .1 - 2"),
                controlX, controlWidth, resetX, resetWidth,
                0.10f, 2.00f, 1.00f, () -> config.itemScale, val -> {
                    config.itemScale = val;
                    config.save();
                });

        // 8. Swing Duration (2 to 20, default 6)
        addIntSliderRow("Swing Duration", null, controlX, controlWidth, resetX, resetWidth,
                2, 20, 6, () -> config.swingDuration, val -> {
                    config.swingDuration = val;
                    config.save();
                });

        // 9. Cancel Re-Equip Animation (default false)
        addBooleanRow("Cancel Re-Equip Animation", null, controlX, controlWidth, resetX, resetWidth,
                false, () -> config.cancelReEquip, val -> {
                    config.cancelReEquip = val;
                    config.save();
                });

        // 10. Rotationless Drink (default true)
        addBooleanRow("Rotationless Drink", null, controlX, controlWidth, resetX, resetWidth,
                true, () -> config.rotationlessDrink, val -> {
                    config.rotationlessDrink = val;
                    config.save();
                });

        // AirWalker Animation
        addBooleanRow("AirWalker",
                Component.literal("Havadayken ve zıplarken el/yürüme sallanma animasyonunu devam ettirir"),
                controlX, controlWidth, resetX, resetWidth,
                false, () -> config.dulkirAirWalker, val -> {
                    config.dulkirAirWalker = val;
                    config.save();
                });

        // 11. Player Scale (0.1 to 2.0, default 1.0)
        addFloatSliderRow("Player Scale",
                Component.literal("Client-side character size (Recommended range of .1 - 2)"),
                controlX, controlWidth, resetX, resetWidth,
                0.10f, 2.00f, 1.00f, () -> config.playerScale, val -> {
                    config.playerScale = val;
                    config.save();
                });

        // 12. HUD Info (FPS, TPS, Ping, Speed & Coords)
        addBooleanRow("HUD Info (FPS/Coords)",
                Component.literal("Sol üst (FPS, TPS, Ping, Speed) ve sağ alt (Yön, Nether, Pos) göstergesi"),
                controlX, controlWidth, resetX, resetWidth,
                true, () -> config.hudInfoEnabled, val -> {
                    config.hudInfoEnabled = val;
                    config.save();
                });

        // 13. Zoom (C Key)
        addBooleanRow("Zoom (C Key)",
                Component.literal("C tuşu ile akıcı kamera yakınlaştırması"),
                controlX, controlWidth, resetX, resetWidth,
                true, () -> config.zoomEnabled, val -> {
                    config.zoomEnabled = val;
                    config.save();
                });

        // 14. Zoom Factor
        addFloatSliderRow("Zoom Factor",
                Component.literal("Yakınlaştırma oranı (C basılıyken fare tekerleği ile de değişir)"),
                controlX, controlWidth, resetX, resetWidth,
                1.50f, 15.00f, 4.00f, () -> config.zoomFactor, val -> {
                    config.zoomFactor = val;
                    config.save();
                });

        // 15. Low Fire
        addBooleanRow("Low Fire",
                Component.literal("Yandığında ekrandaki ateşi aşağı indirir"),
                controlX, controlWidth, resetX, resetWidth,
                true, () -> config.lowFire, val -> {
                    config.lowFire = val;
                    config.save();
                });

        // 16. Low Fire Offset
        addFloatSliderRow("Low Fire Offset",
                Component.literal("Ateş yüksekliği indirgeme miktarı (0.10 - 0.60)"),
                controlX, controlWidth, resetX, resetWidth,
                0.10f, 0.60f, 0.35f, () -> config.lowFireOffset, val -> {
                    config.lowFireOffset = val;
                    config.save();
                });

        // 17. TNT & Creeper Timer
        addBooleanRow("TNT & Creeper Timer",
                Component.literal("Yanan TNT ve patlayan Creeper üzerinde kalan patlama süresi sayacı"),
                controlX, controlWidth, resetX, resetWidth,
                true, () -> config.tntTimer, val -> {
                    config.tntTimer = val;
                    config.save();
                });

        // Footer Buttons: Cancel Changes & Save & Quit
        int footerY = this.height - 26;
        this.addRenderableWidget(Button.builder(Component.literal("Cancel Changes"), btn -> {
            ModConfig.getInstance().copyFrom(this.backupConfig);
            ModConfig.getInstance().save();
            this.closeScreen();
        }).bounds(this.width / 2 - 154, footerY, 150, 20).build());

        this.addRenderableWidget(Button.builder(Component.literal("Save & Quit"), btn -> {
            ModConfig.getInstance().save();
            this.closeScreen();
        }).bounds(this.width / 2 + 4, footerY, 150, 20).build());

        updateRowPositions();
    }

    private void closeScreen() {
        Minecraft.getInstance().gui.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        ModConfig.getInstance().save();
        closeScreen();
    }

    private void addIntSliderRow(String label, Component tooltip, int controlX, int controlW, int resetX, int resetW,
                                 int min, int max, int defaultVal, Supplier<Integer> getter, Consumer<Integer> setter) {
        int index = this.rows.size();
        int initialY = this.listTop + 6 + index * ROW_HEIGHT;

        final Button[] resetBtnHolder = new Button[1];
        ClothIntSlider slider = new ClothIntSlider(controlX, initialY, controlW, 20, min, max, getter.get(), val -> {
            setter.accept(val);
            if (resetBtnHolder[0] != null) {
                resetBtnHolder[0].active = (val != defaultVal);
            }
        });

        Button resetBtn = Button.builder(Component.literal("Reset"), btn -> {
            slider.setIntValue(defaultVal);
            btn.active = false;
        }).bounds(resetX, initialY, resetW, 20).build();
        resetBtn.active = (getter.get() != defaultVal);
        resetBtnHolder[0] = resetBtn;

        this.addRenderableWidget(slider);
        this.addRenderableWidget(resetBtn);
        this.rows.add(new EntryRow(label, tooltip, slider, resetBtn));
    }

    private void addFloatSliderRow(String label, Component tooltip, int controlX, int controlW, int resetX, int resetW,
                                   float min, float max, float defaultVal, Supplier<Float> getter, Consumer<Float> setter) {
        int index = this.rows.size();
        int initialY = this.listTop + 6 + index * ROW_HEIGHT;

        final Button[] resetBtnHolder = new Button[1];
        ClothFloatSlider slider = new ClothFloatSlider(controlX, initialY, controlW, 20, min, max, getter.get(), val -> {
            setter.accept(val);
            if (resetBtnHolder[0] != null) {
                resetBtnHolder[0].active = (Math.abs(val - defaultVal) > 0.005f);
            }
        });

        Button resetBtn = Button.builder(Component.literal("Reset"), btn -> {
            slider.setFloatValue(defaultVal);
            btn.active = false;
        }).bounds(resetX, initialY, resetW, 20).build();
        resetBtn.active = (Math.abs(getter.get() - defaultVal) > 0.005f);
        resetBtnHolder[0] = resetBtn;

        this.addRenderableWidget(slider);
        this.addRenderableWidget(resetBtn);
        this.rows.add(new EntryRow(label, tooltip, slider, resetBtn));
    }

    private void addBooleanRow(String label, Component tooltip, int controlX, int controlW, int resetX, int resetW,
                               boolean defaultVal, Supplier<Boolean> getter, Consumer<Boolean> setter) {
        int index = this.rows.size();
        int initialY = this.listTop + 6 + index * ROW_HEIGHT;

        final Button[] resetBtnHolder = new Button[1];
        Button toggleBtn = Button.builder(getBoolText(getter.get()), btn -> {
            boolean newVal = !getter.get();
            setter.accept(newVal);
            btn.setMessage(getBoolText(newVal));
            if (resetBtnHolder[0] != null) {
                resetBtnHolder[0].active = (newVal != defaultVal);
            }
        }).bounds(controlX, initialY, controlW, 20).build();

        Button resetBtn = Button.builder(Component.literal("Reset"), btn -> {
            setter.accept(defaultVal);
            toggleBtn.setMessage(getBoolText(defaultVal));
            btn.active = false;
        }).bounds(resetX, initialY, resetW, 20).build();
        resetBtn.active = (getter.get() != defaultVal);
        resetBtnHolder[0] = resetBtn;

        this.addRenderableWidget(toggleBtn);
        this.addRenderableWidget(resetBtn);
        this.rows.add(new EntryRow(label, tooltip, toggleBtn, resetBtn));
    }

    private static Component getBoolText(boolean val) {
        return Component.literal(val ? "§aYes" : "§cNo");
    }

    private int getMaxScroll() {
        int contentHeight = this.rows.size() * ROW_HEIGHT + 12;
        int viewHeight = this.listBottom - this.listTop;
        return Math.max(0, contentHeight - viewHeight);
    }

    private void updateRowPositions() {
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0, getMaxScroll());
        for (int i = 0; i < this.rows.size(); i++) {
            EntryRow row = this.rows.get(i);
            int y = (int) (this.listTop + 6 + i * ROW_HEIGHT - this.scrollAmount);
            row.y = y;
            row.controlWidget.setY(y);
            row.resetButton.setY(y);
            boolean visible = (y >= this.listTop - 2 && y + 20 <= this.listBottom + 2);
            row.controlWidget.visible = visible;
            row.resetButton.visible = visible;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= this.listTop && mouseY <= this.listBottom) {
            this.scrollAmount = Mth.clamp(this.scrollAmount - scrollY * 16.0, 0.0, getMaxScroll());
            updateRowPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        updateRowPositions();

        graphics.fill(0, 0, this.width, this.height, 0xC8101010);
        graphics.fill(0, this.listTop, this.width, this.listBottom, 0x70000000);

        int listLeft = this.width / 2 - 180;
        int listRight = this.width / 2 + 180;
        Component hoveredTooltip = null;

        graphics.enableScissor(0, this.listTop, this.width, this.listBottom);
        for (EntryRow row : this.rows) {
            if (!row.controlWidget.visible) continue;
            boolean hovered = (mouseX >= listLeft && mouseX <= listRight && mouseY >= row.y && mouseY <= row.y + 20);
            if (hovered) {
                graphics.fill(listLeft - 4, row.y - 2, listRight + 4, row.y + 22, 0x24FFFFFF);
                if (row.tooltip != null) {
                    hoveredTooltip = row.tooltip;
                }
            }
            int textColor = hovered ? 0xFFFFFF55 : 0xFFE0E0E0;
            graphics.text(this.font, Component.literal(row.label), listLeft, row.y + 6, textColor);
        }
        graphics.disableScissor();

        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int barX = listRight + 8;
            int viewHeight = this.listBottom - this.listTop;
            int contentHeight = this.rows.size() * ROW_HEIGHT + 12;
            int thumbHeight = Math.max(24, (int) ((float) (viewHeight * viewHeight) / contentHeight));
            int thumbY = this.listTop + (int) ((this.scrollAmount / maxScroll) * (viewHeight - thumbHeight));
            graphics.fill(barX, this.listTop, barX + 6, this.listBottom, 0xFF000000);
            graphics.fill(barX, thumbY, barX + 6, thumbY + thumbHeight, 0xFF808080);
            graphics.fill(barX, thumbY, barX + 5, thumbY + thumbHeight - 1, 0xFFC0C0C0);
        }

        graphics.fill(0, 0, this.width, this.listTop, 0xE8161616);
        graphics.fill(0, this.listTop - 2, this.width, this.listTop - 1, 0xFF000000);
        graphics.fill(0, this.listTop - 1, this.width, this.listTop, 0xFF383838);

        graphics.centeredText(this.font, Component.literal("§lDulkirMod-Fabric"), this.width / 2, 7, 0xFFFFFFFF);

        int tabWidth = 96;
        int tabX = this.width / 2 - tabWidth / 2;
        int tabY = 21;
        graphics.fill(tabX, tabY, tabX + tabWidth, tabY + 20, 0xFF262626);
        graphics.fill(tabX, tabY, tabX + tabWidth, tabY + 1, 0xFF505050);
        graphics.fill(tabX, tabY, tabX + 1, tabY + 20, 0xFF505050);
        graphics.fill(tabX + tabWidth - 1, tabY, tabX + tabWidth, tabY + 20, 0xFF505050);
        graphics.fill(tabX + 2, tabY + 18, tabX + tabWidth - 2, tabY + 20, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("Animations"), this.width / 2, tabY + 6, 0xFFFFFFFF);

        graphics.fill(0, this.listBottom, this.width, this.height, 0xE8161616);
        graphics.fill(0, this.listBottom, this.width, this.listBottom + 1, 0xFF383838);
        graphics.fill(0, this.listBottom + 1, this.width, this.listBottom + 2, 0xFF000000);

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        if (hoveredTooltip != null) {
            graphics.setTooltipForNextFrame(hoveredTooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static class EntryRow {
        final String label;
        final Component tooltip;
        final AbstractWidget controlWidget;
        final Button resetButton;
        int y;

        EntryRow(String label, Component tooltip, AbstractWidget controlWidget, Button resetButton) {
            this.label = label;
            this.tooltip = tooltip;
            this.controlWidget = controlWidget;
            this.resetButton = resetButton;
        }
    }

    private static class ClothIntSlider extends AbstractSliderButton {
        private final int min;
        private final int max;
        private final Consumer<Integer> onValueChange;

        ClothIntSlider(int x, int y, int width, int height, int min, int max, int currentValue, Consumer<Integer> onValueChange) {
            super(x, y, width, height, Component.literal("Value: " + currentValue),
                    Mth.clamp((double) (currentValue - min) / (double) (max - min), 0.0, 1.0));
            this.min = min;
            this.max = max;
            this.onValueChange = onValueChange;
            updateMessage();
        }

        int getIntValue() {
            return (int) Math.round(this.min + this.value * (this.max - this.min));
        }

        void setIntValue(int val) {
            this.value = Mth.clamp((double) (val - this.min) / (double) (this.max - this.min), 0.0, 1.0);
            updateMessage();
            applyValue();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal("Value: " + getIntValue()));
        }

        @Override
        protected void applyValue() {
            this.onValueChange.accept(getIntValue());
        }
    }

    private static class ClothFloatSlider extends AbstractSliderButton {
        private final float min;
        private final float max;
        private final Consumer<Float> onValueChange;

        ClothFloatSlider(int x, int y, int width, int height, float min, float max, float currentValue, Consumer<Float> onValueChange) {
            super(x, y, width, height, Component.literal(String.format(Locale.US, "Value: %.2f", currentValue)),
                    Mth.clamp((double) (currentValue - min) / (double) (max - min), 0.0, 1.0));
            this.min = min;
            this.max = max;
            this.onValueChange = onValueChange;
            updateMessage();
        }

        float getFloatValue() {
            float raw = (float) (this.min + this.value * (this.max - this.min));
            return Math.round(raw * 20.0f) / 20.0f;
        }

        void setFloatValue(float val) {
            this.value = Mth.clamp((double) (val - this.min) / (double) (this.max - this.min), 0.0, 1.0);
            updateMessage();
            applyValue();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.format(Locale.US, "Value: %.2f", getFloatValue())));
        }

        @Override
        protected void applyValue() {
            this.onValueChange.accept(getFloatValue());
        }
    }
}
