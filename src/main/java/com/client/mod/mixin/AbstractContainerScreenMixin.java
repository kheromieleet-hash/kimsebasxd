package com.client.mod.mixin;

import com.client.mod.AutoCrafterHandler;
import com.client.mod.ModConfig;
import com.client.mod.inventory.InventoryTweaksHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {
    @Shadow protected Slot hoveredSlot;
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected abstract Slot getHoveredSlot(double x, double y);
    @Shadow protected abstract void slotClicked(Slot slot, int slotId, int mouseButton, ContainerInput containerInput);

    @Unique private static Slot lastDraggedSlot = null;

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
    private void onExtractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        ItemStack carried = this.menu != null ? this.menu.getCarried() : ItemStack.EMPTY;
        if (InventoryTweaksHandler.renderShulkerTooltip(graphics, this.hoveredSlot, carried, mouseX, mouseY)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"))
    private void onMouseDragged(MouseButtonEvent event, double deltaX, double deltaY, CallbackInfoReturnable<Boolean> cir) {
        ModConfig config = ModConfig.getInstance();
        if (config.invTweaksEnabled && config.invMouseDrag) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.hasShiftDown()) {
                Slot slot = this.getHoveredSlot(event.x(), event.y());
                if (slot != null && slot.hasItem() && slot != lastDraggedSlot) {
                    lastDraggedSlot = slot;
                    this.slotClicked(slot, slot.index, 0, ContainerInput.QUICK_MOVE);
                }
            }
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void onMouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        ModConfig config = ModConfig.getInstance();
        // Middle button click (button 2) triggers instant sorting
        if (config.invTweaksEnabled && config.invSorting && event.button() == 2) {
            Slot hovered = this.getHoveredSlot(event.x(), event.y());
            InventoryTweaksHandler.sort((AbstractContainerScreen<?>) (Object) this, hovered);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void onMouseReleased(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        lastDraggedSlot = null;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void onScreenInit(CallbackInfo ci) {
        AutoCrafterHandler.reset();
    }

    @Inject(method = "containerTick", at = @At("HEAD"))
    private void onContainerTick(CallbackInfo ci) {
        InventoryTweaksHandler.tick();
        AutoCrafterHandler.tick(this.menu);
    }

    @Inject(method = "onClose", at = @At("HEAD"))
    private void onScreenClose(CallbackInfo ci) {
        InventoryTweaksHandler.reset();
        AutoCrafterHandler.reset();
        lastDraggedSlot = null;
    }
}
