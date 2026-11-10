package dlovin.inventoryhud.gui;

import com.client.mod.ModConfig;
import dlovin.inventoryhud.InventoryHUDCommon;
import dlovin.inventoryhud.gui.renderers.ArmorRenderer;
import dlovin.inventoryhud.gui.renderers.BlockArmorRenderer;
import dlovin.inventoryhud.gui.renderers.EachArmorRenderer;
import dlovin.inventoryhud.gui.renderers.HorizontalMiniPotionRenderer;
import dlovin.inventoryhud.gui.renderers.HorizontalPotionRenderer;
import dlovin.inventoryhud.gui.renderers.HotBarArmorRenderer;
import dlovin.inventoryhud.gui.renderers.PotionRenderer;
import dlovin.inventoryhud.gui.renderers.VerticalMiniPotionRenderer;
import dlovin.inventoryhud.gui.renderers.VerticalPotionRenderer;
import dlovin.inventoryhud.platform.ConfigAccess;
import dlovin.inventoryhud.utils.EachItem;
import dlovin.inventoryhud.utils.RenderHelper;
import dlovin.inventoryhud.utils.WidgetAligns;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class InventoryGui {
    private final Font fontRenderer;
    private final Minecraft minecraft;

    public static int invX;
    public static int invY;
    public static WidgetAligns InvAligns;
    public static float invAlpha;
    public static boolean invMini;
    public static boolean invVert;
    public static boolean animated;
    public static boolean hideBackground;
    public static boolean withDebug;

    private static final Identifier INVBG = Identifier.tryBuild("inventoryhud", "textures/gui/inv_bg.png");

    public ArmorRenderer armorRenderer;
    public PotionRenderer potionRenderer;

    public InventoryGui() {
        this.minecraft = Minecraft.getInstance();
        this.fontRenderer = this.minecraft.font;
        ConfigAccess cfg = InventoryHUDCommon.getConfig();
        WidgetAligns armAligns = new WidgetAligns(cfg.getArmHalign(), cfg.getArmValign());
        int armX = cfg.getXArmPos();
        int armY = cfg.getYArmPos();
        if (!cfg.getMoveAll()) {
            this.armorRenderer = new EachArmorRenderer(this.minecraft);
        } else if (armAligns.HorAlign == WidgetAligns.HAlign.MIDDLE
                && armAligns.VertAlign == WidgetAligns.VAlign.BOTTOM
                && armY <= 90 && Math.abs(armX) <= 90) {
            this.armorRenderer = new HotBarArmorRenderer(this.minecraft);
        } else {
            this.armorRenderer = new BlockArmorRenderer(this.minecraft);
        }
        this.setupArmorRenderer();

        if (cfg.getPotHor()) {
            this.potionRenderer = cfg.getPotMini()
                    ? new HorizontalMiniPotionRenderer(this.minecraft)
                    : new HorizontalPotionRenderer(this.minecraft);
        } else {
            this.potionRenderer = cfg.getPotMini()
                    ? new VerticalMiniPotionRenderer(this.minecraft)
                    : new VerticalPotionRenderer(this.minecraft);
        }
        this.setupPotionRenderer();

        InventoryHUDCommon.isActive = cfg.getByDefault();
        InventoryHUDCommon.armorHUD = cfg.getArmorDamage();
        InventoryHUDCommon.potionHUD = cfg.getPotions();
        invX = cfg.getXPos();
        invY = cfg.getYPos();
        InvAligns = new WidgetAligns(cfg.getInvHalign(), cfg.getInvValign());
        invMini = cfg.getInvMini();
        invVert = cfg.getInvVert();
        animated = cfg.getAnimatedInv();
        hideBackground = cfg.getHideBackground();
        invAlpha = cfg.getInvAlpha() / 100.0f;
        withDebug = cfg.getInvWithDebug();

        InventoryHUDCommon.getInstance().setInventoryGui(this);
    }

    private void setupPotionRenderer() {
        ConfigAccess cfg = InventoryHUDCommon.getConfig();
        this.potionRenderer.potX = cfg.getXPotionPos();
        this.potionRenderer.potY = cfg.getYPotionPos();
        this.potionRenderer.PotAligns = new WidgetAligns(cfg.getPotHalign(), cfg.getPotValign());
        this.potionRenderer.potMini = cfg.getPotMini();
        this.potionRenderer.potGap = cfg.getPotGap();
        this.potionRenderer.barDuration = cfg.getBarDuration() * 20;
        this.potionRenderer.PotPosChanged();

        switch (this.potionRenderer.PotAligns.VertAlign) {
            case TOP -> this.potionRenderer.potVert = 1;
            case CENTER -> {
                if (this.potionRenderer.potY - 12 < this.minecraft.getWindow().getGuiScaledHeight() / 2) {
                    this.potionRenderer.potVert = -1;
                } else {
                    this.potionRenderer.potVert = 1;
                }
            }
            case BOTTOM -> this.potionRenderer.potVert = -1;
        }

        this.potionRenderer.potAlpha = cfg.getPotAlpha() / 100.0f;
        this.potionRenderer.showHidden = cfg.getShowHiddenEffects();
        this.potionRenderer.withDebug = cfg.getPotWithDebug();
        this.potionRenderer.potionLevels = cfg.getPotionLevels();
    }

    private void setupArmorRenderer() {
        ConfigAccess cfg = InventoryHUDCommon.getConfig();
        this.armorRenderer.armX = cfg.getXArmPos();
        this.armorRenderer.armY = cfg.getYArmPos();
        this.armorRenderer.ArmAligns = new WidgetAligns(cfg.getArmHalign(), cfg.getArmValign());
        this.armorRenderer.armAbove = cfg.getArmAbove();
        this.armorRenderer.armScale = cfg.getArmScale() * 0.01f;
        this.armorRenderer.showArmor = cfg.getShowArmor();
        this.armorRenderer.showMain = cfg.getShowMain();
        this.armorRenderer.showOff = cfg.getShowOff();
        this.armorRenderer.showArrows = cfg.getShowArrows();
        this.armorRenderer.showInv = cfg.getShowInv();
        this.armorRenderer.showEmpty = cfg.getShowEmpty();
        this.armorRenderer.showCount = cfg.getShowCount();
        this.armorRenderer.armView = cfg.getArmView();
        this.armorRenderer.armBars = cfg.getArmBars();
        this.armorRenderer.showArrowsWithoutWeapon = cfg.getShowArrowsWithoutWeapon();
        this.armorRenderer.withDebug = cfg.getArmWithDebug();

        this.armorRenderer.items[0] = new EachItem(cfg.getHelmPosX(), cfg.getHelmPosY(), new WidgetAligns(cfg.getHelmHal(), cfg.getHelmVal()));
        this.armorRenderer.items[1] = new EachItem(cfg.getChestPosX(), cfg.getChestPosY(), new WidgetAligns(cfg.getChestHal(), cfg.getChestVal()));
        this.armorRenderer.items[2] = new EachItem(cfg.getLegPosX(), cfg.getLegPosY(), new WidgetAligns(cfg.getLegHal(), cfg.getLegVal()));
        this.armorRenderer.items[3] = new EachItem(cfg.getBootPosX(), cfg.getBootPosY(), new WidgetAligns(cfg.getBootHal(), cfg.getBootVal()));
        this.armorRenderer.items[4] = new EachItem(cfg.getOffPosX(), cfg.getOffPosY(), new WidgetAligns(cfg.getOffHal(), cfg.getOffVal()));
        this.armorRenderer.items[5] = new EachItem(cfg.getMainPosX(), cfg.getMainPosY(), new WidgetAligns(cfg.getMainHal(), cfg.getMainVal()));
        this.armorRenderer.items[6] = new EachItem(cfg.getInvPosX(), cfg.getInvPosY(), new WidgetAligns(cfg.getInvHal(), cfg.getInvVal()));
        this.armorRenderer.items[7] = new EachItem(cfg.getArrPosX(), cfg.getArrPosY(), new WidgetAligns(cfg.getArrHal(), cfg.getArrVal()));

        this.changeArmorOffset();
    }

    public void renderArmor(GuiGraphicsExtractor graphics) {
        if (!InventoryHUDCommon.armorHUD) {
            return;
        }
        if (this.debugEnabled() && !this.armorRenderer.withDebug) {
            return;
        }
        this.armorRenderer.render(graphics, graphics.guiWidth(), graphics.guiHeight());
    }

    public void renderPotions(GuiGraphicsExtractor graphics) {
        if (!InventoryHUDCommon.potionHUD) {
            return;
        }
        if (this.debugEnabled() && !this.potionRenderer.withDebug) {
            return;
        }
        this.potionRenderer.render(graphics, graphics.guiWidth(), graphics.guiHeight());
    }

    public void renderInventory(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!InventoryHUDCommon.isActive) {
            return;
        }
        if (this.debugEnabled() && !withDebug) {
            return;
        }
        if (this.minecraft.gui.screen() instanceof AbstractContainerScreen) {
            return;
        }

        // Always keep live config synced with static fields so Mini/Vertical/Alpha changes apply immediately
        ConfigAccess cfg = InventoryHUDCommon.getConfig();
        if (cfg != null) {
            invMini = cfg.getInvMini();
            invVert = cfg.getInvVert();
            invAlpha = cfg.getInvAlpha() / 100.0f;
            hideBackground = cfg.getHideBackground();
        }

        int cols = invVert ? 3 : 9;
        int rows = invVert ? 9 : 3;
        float baseScale = invMini ? 0.75f : 1.0f;
        float scale = baseScale * ModConfig.getInstance().invHudScale;

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int x = this.getX(screenW, (int) ((cols * 18 - 2) * scale), invX, InvAligns.HorAlign);
        int y = this.getY(screenH, (int) ((rows * 18 - 2) * scale), invY, InvAligns.VertAlign);

        List<Runnable> drawCalls = new ArrayList<>();
        int zIndex = 10;

        for (int col = 0; col < cols; col++) {
            for (int row = 0; row < rows; row++) {
                int slotIndex = invVert ? ((3 - col) * 9 + row) : (col + (row + 1) * 9);
                ItemStack stack = this.minecraft.player.getInventory().getItem(slotIndex);
                if (!stack.isEmpty()) {
                    final int drawCol = col;
                    final int drawRow = row;
                    final int drawZ = zIndex++;
                    drawCalls.add(() -> renderSingleSlotItem(stack, deltaTracker, graphics, drawCol, drawRow, drawZ));
                }
            }
        }

        if (!drawCalls.isEmpty() || (!hideBackground && invAlpha > 0.0f)) {
            graphics.pose().pushMatrix();
            graphics.pose().translate((float) x, (float) y);
            graphics.pose().scale(scale, scale);

            if (invAlpha > 0.0f) {
                RenderHelper.drawTexture(
                        graphics,
                        INVBG,
                        -8,
                        -8,
                        0,
                        rows == 3 ? 0 : 68,
                        cols * 18 + 14,
                        rows * 18 + 14,
                        256,
                        256,
                        ARGB.white(invAlpha)
                );
            }

            drawCalls.forEach(Runnable::run);
            graphics.pose().popMatrix();
        }
    }

    private void renderSingleSlotItem(ItemStack stack, DeltaTracker deltaTracker, GuiGraphicsExtractor graphics, int col, int row, int zIndex) {
        float pop = (float) stack.getPopTime() - deltaTracker.getGameTimeDeltaPartialTick(false);
        graphics.pose().pushMatrix();
        if (animated && pop > 0.0f) {
            graphics.pose().pushMatrix();
            float f = 1.0f + pop / 5.0f;
            graphics.pose().translate((float) (col * 18 + 12), (float) (row * 18 + 12));
            graphics.pose().scale((f + 1.0f) / 2.0f, (f + 1.0f) / 2.0f);
            graphics.pose().translate((float) (-(col * 18 + 12)), (float) (-(row * 18 + 12)));
        }
        graphics.item(this.minecraft.player, stack, col * 18, row * 18, zIndex);
        if (animated && pop > 0.0f) {
            graphics.pose().popMatrix();
        }
        graphics.itemDecorations(this.fontRenderer, stack, col * 18, row * 18);
        graphics.pose().popMatrix();
    }

    public int getX(int screenW, int widgetW, int offset, WidgetAligns.HAlign align) {
        int raw = switch (align) {
            case LEFT -> offset;
            case MIDDLE -> screenW / 2 - widgetW / 2 - offset;
            case RIGHT -> screenW - offset;
        };
        return Mth.clamp(raw, 0, Math.max(0, screenW - widgetW));
    }

    public int getY(int screenH, int widgetH, int offset, WidgetAligns.VAlign align) {
        int raw = switch (align) {
            case TOP -> offset;
            case CENTER -> screenH / 2 - widgetH / 2 - offset;
            case BOTTOM -> screenH - offset;
        };
        return Mth.clamp(raw, 0, Math.max(0, screenH - widgetH));
    }

    public void changeArmorPos(WidgetAligns aligns, int x, int y) {
        if (aligns.HorAlign == WidgetAligns.HAlign.MIDDLE
                && aligns.VertAlign == WidgetAligns.VAlign.BOTTOM
                && (float) y <= 70.0f * this.armorRenderer.armScale + 5.0f
                && Math.abs(x) <= 90) {
            if (!(this.armorRenderer instanceof HotBarArmorRenderer)) {
                this.armorRenderer = new HotBarArmorRenderer(this.minecraft);
                this.setupArmorRenderer();
            }
        } else if (this.armorRenderer instanceof HotBarArmorRenderer) {
            this.armorRenderer = new BlockArmorRenderer(this.minecraft);
            this.setupArmorRenderer();
        }

        this.armorRenderer.ArmAligns = aligns;
        this.armorRenderer.armY = y;
        this.armorRenderer.armX = x;
        if (this.armorRenderer instanceof BlockArmorRenderer blockArmorRenderer) {
            blockArmorRenderer.changeOffset();
        }
    }

    public void changeArmorOffset() {
        if (this.armorRenderer instanceof BlockArmorRenderer blockArmorRenderer) {
            blockArmorRenderer.changeOffset();
        }
    }

    public void changeMoveAll(Boolean moveAll) {
        ConfigAccess cfg = InventoryHUDCommon.getConfig();
        WidgetAligns armAligns = new WidgetAligns(cfg.getArmHalign(), cfg.getArmValign());
        int armX = cfg.getXArmPos();
        int armY = cfg.getYArmPos();
        if (!moveAll) {
            this.armorRenderer = new EachArmorRenderer(this.minecraft);
        } else if (armAligns.HorAlign == WidgetAligns.HAlign.MIDDLE
                && armAligns.VertAlign == WidgetAligns.VAlign.BOTTOM
                && armY <= 90 && Math.abs(armX) <= 90) {
            this.armorRenderer = new HotBarArmorRenderer(this.minecraft);
        } else {
            this.armorRenderer = new BlockArmorRenderer(this.minecraft);
        }
        this.setupArmorRenderer();
    }

    public void changePotionMini(boolean mini) {
        this.changePotion(mini, InventoryHUDCommon.getConfig().getPotHor());
        this.setupPotionRenderer();
    }

    public void changePotionHor(boolean hor) {
        this.changePotion(InventoryHUDCommon.getConfig().getPotMini(), hor);
    }

    private void changePotion(boolean mini, boolean hor) {
        if (hor) {
            this.potionRenderer = mini
                    ? new HorizontalMiniPotionRenderer(this.minecraft)
                    : new HorizontalPotionRenderer(this.minecraft);
        } else {
            this.potionRenderer = mini
                    ? new VerticalMiniPotionRenderer(this.minecraft)
                    : new VerticalPotionRenderer(this.minecraft);
        }
        this.setupPotionRenderer();
    }

    public void resetBlackList() {
        this.potionRenderer.resetBlacklist();
    }

    private boolean debugEnabled() {
        return this.minecraft.debugEntries.isOverlayVisible();
    }
}
