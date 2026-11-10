package com.client.mod.mixin;

import com.client.mod.ViewmodelConfigScreen;
import dlovin.inventoryhud.gui.config.ConfigScreen;
import dlovin.inventoryhud.gui.widgets.CustomButton;
import dlovin.inventoryhud.gui.widgets.Widget;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ConfigScreen.class)
public class ConfigScreenMixin {
    @Shadow
    protected List<Widget> widgetList;

    @Inject(method = "init", at = @At("TAIL"))
    private void onInit(CallbackInfo ci) {
        ConfigScreen screen = (ConfigScreen) (Object) this;
        CustomButton vmBtn = new CustomButton(
                screen.width / 2 + 90,
                8,
                36,
                36,
                "Dulkir",
                Identifier.tryBuild("inventoryhud", "textures/gui/vm_btn.png"),
                false,
                (w, g, x, y) -> g.setTooltipForNextFrame(Component.literal("DulkirMod Animations"), x, y)
        );
        vmBtn.addListener(btn -> Minecraft.getInstance().gui.setScreen(new ViewmodelConfigScreen(screen)));
        if (this.widgetList != null) {
            this.widgetList.add(vmBtn);
        }
    }
}
