package com.client.mod.mixin;

import com.mojang.blaze3d.platform.IconSet;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Replaces the default Minecraft grass block window/taskbar icon with Sestia Client's custom icon set.
 */
@Mixin(IconSet.class)
public class IconSetMixin {
    private static final String[] ICON_PATHS = {
            "/assets/sestia/icons/icon_16x16.png",
            "/assets/sestia/icons/icon_32x32.png",
            "/assets/sestia/icons/icon_48x48.png",
            "/assets/sestia/icons/icon_128x128.png",
            "/assets/sestia/icons/icon_256x256.png"
    };

    @Inject(method = "getStandardIcons", at = @At("HEAD"), cancellable = true)
    private void sestia$useCustomIcons(PackMetadataResources resources, CallbackInfoReturnable<List<IoSupplier<InputStream>>> cir) {
        List<IoSupplier<InputStream>> icons = new ArrayList<>(ICON_PATHS.length);
        for (String path : ICON_PATHS) {
            if (IconSetMixin.class.getResource(path) != null) {
                icons.add(() -> {
                    InputStream stream = IconSetMixin.class.getResourceAsStream(path);
                    if (stream == null) {
                        throw new FileNotFoundException(path);
                    }
                    return stream;
                });
            }
        }
        if (!icons.isEmpty()) {
            cir.setReturnValue(icons);
        }
    }
}
