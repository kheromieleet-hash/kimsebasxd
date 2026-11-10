package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * LiquidBounce-style Version / Protocol Switcher screen.
 * Allows switching client handshake protocol version on the fly.
 */
public class VersionSwitcherScreen extends Screen {
    public static class VersionData {
        public final String name;
        public final int protocol;
        public final String tag;

        public VersionData(String name, int protocol, String tag) {
            this.name = name;
            this.protocol = protocol;
            this.tag = tag;
        }
    }

    private final Screen parent;
    private final List<VersionData> versions = new ArrayList<>();
    private final List<Button> rowButtons = new ArrayList<>();

    private EditBox customProtocolBox;
    private Button applyCustomButton;
    private Button resetAutoButton;
    private Button doneButton;

    private int listTop;
    private int listBottom;
    private double scrollAmount = 0.0;
    private static final int ROW_HEIGHT = 28;

    public VersionSwitcherScreen(Screen parent) {
        super(Component.literal("Version Switcher"));
        this.parent = parent;
        initVersions();
    }

    private void initVersions() {
        versions.clear();
        versions.add(new VersionData("Auto / Default", -1, "Native 26.3 Snapshot (Proto 777)"));
        versions.add(new VersionData("1.21.1 / 1.21", 767, "Tricky Trials & Mace"));
        versions.add(new VersionData("1.20.4 / 1.20.3", 765, "Armadillos, Bats & Pots"));
        versions.add(new VersionData("1.20.2", 764, "Network Protocol Overhaul"));
        versions.add(new VersionData("1.20.1 / 1.20", 763, "Trails & Tales, Armor Trims"));
        versions.add(new VersionData("1.19.4", 762, "Cherry Blossom & Display Entities"));
        versions.add(new VersionData("1.19.3", 761, "Chat System Updates"));
        versions.add(new VersionData("1.19.2 / 1.19", 760, "The Wild Update, Warden & Deep Dark"));
        versions.add(new VersionData("1.18.2 / 1.18", 758, "Caves & Cliffs Part II"));
        versions.add(new VersionData("1.17.1 / 1.17", 756, "Caves & Cliffs Part I"));
        versions.add(new VersionData("1.16.5 / 1.16.4", 754, "Nether Update (Modern PvP Base)"));
        versions.add(new VersionData("1.15.2", 578, "Buzzy Bees & Performance"));
        versions.add(new VersionData("1.14.4", 498, "Village & Pillage (Crossbows)"));
        versions.add(new VersionData("1.12.2", 340, "World of Color (Classic Modding)"));
        versions.add(new VersionData("1.8.9 / 1.8", 47, "Bountiful Update (PvP Standard)"));
        versions.add(new VersionData("1.7.10", 5, "Classic 1.7 Combat Era"));
    }

    @Override
    protected void init() {
        this.rowButtons.clear();
        this.listTop = 64;
        this.listBottom = this.height - 40;

        int listLeft = this.width / 2 - 230;
        int listRight = this.width / 2 + 230;
        int btnWidth = 76;
        int btnHeight = 20;

        // Custom protocol input in header
        int customBoxW = 90;
        int applyBtnW = 54;
        int customTotalW = customBoxW + 4 + applyBtnW;
        int customStartX = listRight - customTotalW;
        int customY = 36;

        this.customProtocolBox = new EditBox(this.font, customStartX, customY, customBoxW, 18, Component.literal("Custom Protocol"));
        this.customProtocolBox.setMaxLength(6);
        this.customProtocolBox.setHint(Component.literal("Proto (e.g. 765)"));
        this.addRenderableWidget(this.customProtocolBox);

        this.applyCustomButton = Button.builder(Component.literal("Apply"), btn -> {
            applyCustomProtocol();
        }).bounds(customStartX + customBoxW + 4, customY - 1, applyBtnW, 20).build();
        this.addRenderableWidget(this.applyCustomButton);

        // Version rows buttons
        for (int i = 0; i < versions.size(); i++) {
            final VersionData data = versions.get(i);
            int btnX = listRight - btnWidth;
            int btnY = this.listTop + 4 + i * ROW_HEIGHT;

            Button selectBtn = Button.builder(Component.literal("Select"), btn -> {
                selectVersion(data);
            }).bounds(btnX, btnY, btnWidth, btnHeight).build();

            this.rowButtons.add(selectBtn);
            this.addRenderableWidget(selectBtn);
        }

        // Footer buttons
        int footerY = this.height - 30;
        int actionBtnW = 120;
        int actionBtnH = 22;

        this.resetAutoButton = Button.builder(Component.literal("Reset to Auto"), btn -> {
            ModConfig cfg = ModConfig.getInstance();
            cfg.targetProtocol = -1;
            cfg.targetVersionName = "Auto";
            cfg.save();
            updateButtonStates();
        }).bounds(this.width / 2 - actionBtnW - 8, footerY, actionBtnW, actionBtnH).build();
        this.addRenderableWidget(this.resetAutoButton);

        this.doneButton = Button.builder(Component.literal("Done"), btn -> {
            onClose();
        }).bounds(this.width / 2 + 8, footerY, actionBtnW, actionBtnH).build();
        this.addRenderableWidget(this.doneButton);

        updateButtonStates();
        updateRowPositions();
    }

    private void applyCustomProtocol() {
        if (this.customProtocolBox == null) return;
        String text = this.customProtocolBox.getValue().trim();
        try {
            int proto = Integer.parseInt(text);
            if (proto > 0) {
                ModConfig config = ModConfig.getInstance();
                config.targetProtocol = proto;
                config.targetVersionName = "Custom (" + proto + ")";
                config.save();
                this.customProtocolBox.setValue("");
                updateButtonStates();
            }
        } catch (NumberFormatException ignored) {
        }
    }

    private void selectVersion(VersionData data) {
        ModConfig config = ModConfig.getInstance();
        config.targetProtocol = data.protocol;
        config.targetVersionName = data.name;
        config.save();
        updateButtonStates();
    }

    private void updateButtonStates() {
        ModConfig config = ModConfig.getInstance();
        int activeProtocol = config != null ? config.targetProtocol : -1;

        for (int i = 0; i < versions.size() && i < rowButtons.size(); i++) {
            VersionData data = versions.get(i);
            Button btn = rowButtons.get(i);

            boolean isActive;
            if (activeProtocol <= 0) {
                isActive = (data.protocol <= 0);
            } else {
                isActive = (data.protocol == activeProtocol);
            }

            if (isActive) {
                btn.setMessage(Component.literal("§a* Active"));
            } else {
                btn.setMessage(Component.literal("§7Select"));
            }
        }
    }

    private int getMaxScroll() {
        int contentHeight = this.versions.size() * ROW_HEIGHT + 8;
        int viewHeight = this.listBottom - this.listTop;
        return Math.max(0, contentHeight - viewHeight);
    }

    private void updateRowPositions() {
        this.scrollAmount = Mth.clamp(this.scrollAmount, 0.0, getMaxScroll());
        int btnWidth = 76;
        int listRight = this.width / 2 + 230;

        for (int i = 0; i < this.rowButtons.size(); i++) {
            Button btn = this.rowButtons.get(i);
            int y = (int) (this.listTop + 4 + i * ROW_HEIGHT - this.scrollAmount);
            btn.setX(listRight - btnWidth);
            btn.setY(y + 2);
            boolean visible = (y >= this.listTop && y + ROW_HEIGHT <= this.listBottom);
            btn.visible = visible;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseY >= this.listTop && mouseY <= this.listBottom) {
            this.scrollAmount = Mth.clamp(this.scrollAmount - scrollY * 20.0, 0.0, getMaxScroll());
            updateRowPositions();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        updateRowPositions();

        // Dark dim background
        graphics.fill(0, 0, this.width, this.height, 0xD40C0D11);

        int listLeft = this.width / 2 - 230;
        int listRight = this.width / 2 + 230;

        // List frame background
        graphics.fill(listLeft - 8, this.listTop, listRight + 8, this.listBottom, 0x8808080C);
        graphics.fill(listLeft - 8, this.listTop, listRight + 8, this.listTop + 1, 0x33E53935);
        graphics.fill(listLeft - 8, this.listBottom - 1, listRight + 8, this.listBottom, 0x33E53935);
        graphics.fill(listLeft - 9, this.listTop, listLeft - 8, this.listBottom, 0x33E53935);
        graphics.fill(listRight + 8, this.listTop, listRight + 9, this.listBottom, 0x33E53935);

        ModConfig config = ModConfig.getInstance();
        int activeProtocol = config != null ? config.targetProtocol : -1;

        // Scissored row rendering
        graphics.enableScissor(listLeft - 8, this.listTop, listRight + 8, this.listBottom);
        for (int i = 0; i < versions.size(); i++) {
            VersionData data = versions.get(i);
            int y = (int) (this.listTop + 4 + i * ROW_HEIGHT - this.scrollAmount);

            if (y + ROW_HEIGHT < this.listTop || y > this.listBottom) continue;

            boolean isActive;
            if (activeProtocol <= 0) {
                isActive = (data.protocol <= 0);
            } else {
                isActive = (data.protocol == activeProtocol);
            }

            boolean hovered = (mouseX >= listLeft - 6 && mouseX <= listRight + 6 && mouseY >= y && mouseY <= y + ROW_HEIGHT - 2);

            if (isActive) {
                graphics.fill(listLeft - 6, y, listRight + 6, y + ROW_HEIGHT - 2, 0x2400E5FF);
                graphics.fill(listLeft - 6, y, listLeft - 4, y + ROW_HEIGHT - 2, 0xFF00E5FF);
            } else if (hovered) {
                graphics.fill(listLeft - 6, y, listRight + 6, y + ROW_HEIGHT - 2, 0x1EFFFFFF);
            }

            // Separator line
            graphics.fill(listLeft - 6, y + ROW_HEIGHT - 2, listRight + 6, y + ROW_HEIGHT - 1, 0x18FFFFFF);

            // Version name
            int titleColor = isActive ? 0xFF00E5FF : 0xFFFFFFFF;
            graphics.text(this.font, Component.literal(data.name), listLeft, y + 4, titleColor);

            // Protocol tag
            String protoText = data.protocol > 0 ? "Proto " + data.protocol : "Default";
            int protoColor = isActive ? 0xFF55FF55 : 0xFFE53935;
            graphics.text(this.font, Component.literal(protoText), listLeft + 120, y + 4, protoColor);

            // Era / feature info
            graphics.text(this.font, Component.literal(data.tag), listLeft + 185, y + 5, 0xFF888E99);
        }
        graphics.disableScissor();

        // Scrollbar
        int maxScroll = getMaxScroll();
        if (maxScroll > 0) {
            int barX = listRight + 12;
            int viewHeight = this.listBottom - this.listTop;
            int contentHeight = this.versions.size() * ROW_HEIGHT + 8;
            int thumbHeight = Math.max(24, (int) ((float) (viewHeight * viewHeight) / contentHeight));
            int thumbY = this.listTop + (int) ((this.scrollAmount / maxScroll) * (viewHeight - thumbHeight));
            graphics.fill(barX, this.listTop, barX + 4, this.listBottom, 0xFF141418);
            graphics.fill(barX, thumbY, barX + 4, thumbY + thumbHeight, 0xFFE53935);
        }

        // Header background & title
        graphics.fill(0, 0, this.width, this.listTop, 0xF2090A0E);
        graphics.fill(0, this.listTop - 1, this.width, this.listTop, 0x66E53935);

        graphics.centeredText(this.font, Component.literal("§l§cVERSION SWITCHER"), this.width / 2, 8, 0xFFFFFFFF);

        String currentStatus = "Current: §b" + (config != null ? config.getDisplayVersion() : "Auto") +
                (config != null && config.targetProtocol > 0 ? " §7(Protocol " + config.targetProtocol + ")" : " §7(Native 26.3, Protocol 777)");
        graphics.text(this.font, Component.literal(currentStatus), listLeft, 40, 0xFFE0E0E0);

        // Footer background
        graphics.fill(0, this.listBottom, this.width, this.height, 0xF2090A0E);
        graphics.fill(0, this.listBottom, this.width, this.listBottom + 1, 0x66E53935);

        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(this.parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
