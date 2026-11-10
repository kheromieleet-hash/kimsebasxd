package com.client.mod.mixin;

import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Mounts the active SCTClient-Core JAR as a top-priority built-in client resource pack
 * so asset updates (textures, fonts, languages) apply in the same launch as code updates.
 */
@Mixin(value = ModResourcePackCreator.class, remap = false)
public class CoreAssetsPackMixin {
    @Shadow
    @Final
    private PackType type;

    @Inject(method = "loadPacks", at = @At("TAIL"))
    private void sct$mountCoreAssetsPack(Consumer<Pack> consumer, CallbackInfo ci) {
        if (this.type != PackType.CLIENT_RESOURCES) {
            return;
        }
        String corePathProp = System.getProperty("sct.core.jar.path");
        if (corePathProp == null || corePathProp.isBlank()) {
            return;
        }
        Path corePath = Path.of(corePathProp);
        if (!Files.isRegularFile(corePath)) {
            return;
        }
        PackLocationInfo info = new PackLocationInfo(
                "sctclient-core",
                Component.literal("SCT Client Core Assets"),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        PackSelectionConfig selection = new PackSelectionConfig(true, Pack.Position.TOP, true);
        Pack pack = Pack.readMetaAndCreate(
                info,
                new FilePackResources.FileResourcesSupplier(corePath),
                PackType.CLIENT_RESOURCES,
                selection
        );
        if (pack != null) {
            consumer.accept(pack);
        }
    }
}
