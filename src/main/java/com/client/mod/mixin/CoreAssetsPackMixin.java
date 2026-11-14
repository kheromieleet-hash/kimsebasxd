package com.client.mod.mixin;

import com.client.mod.ModConfig;
import net.fabricmc.fabric.impl.resource.pack.FabricPack;
import net.fabricmc.fabric.impl.resource.pack.ModResourcePackCreator;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.KnownPack;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.resources.IoSupplier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Mounts the active SCTClient-Core JAR using Fabric's hidden mod resource pack model
 * so asset updates (textures, fonts, languages) apply cleanly without cluttering UI
 * or blocking user-installed texture packs.
 */
@Mixin(value = ModResourcePackCreator.class, remap = false)
public class CoreAssetsPackMixin {
    @Shadow
    @Final
    private PackType type;

    @Shadow
    @Final
    private PackSelectionConfig activationInfo;

    @Shadow
    @Final
    private boolean forKnownPacksManager;

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
                "sestia-core",
                Component.literal("Sestia Client Core"),
                ModResourcePackCreator.RESOURCE_PACK_SOURCE,
                Optional.empty()
        );
        Pack.ResourcesSupplier baseSupplier = new FilePackResources.FileResourcesSupplier(corePath);
        Pack.ResourcesSupplier filteredSupplier = new Pack.ResourcesSupplier() {
            @Override
            public PackMetadataResources openMetadata(PackLocationInfo locInfo) {
                return baseSupplier.openMetadata(locInfo);
            }

            @Override
            public Stream<PackResources> openResources(PackLocationInfo locInfo, Pack.Metadata metadata) {
                return baseSupplier.openResources(locInfo, metadata).map(FilteringPackResources::new);
            }
        };
        Pack pack = Pack.readMetaAndCreate(
                info,
                filteredSupplier,
                this.type,
                this.activationInfo
        );
        if (pack != null) {
            if (!this.forKnownPacksManager) {
                ((FabricPack) pack).fabric$setParentsPredicate(ModResourcePackCreator.BASE_PARENT);
            }
            consumer.accept(pack);
        }
    }

    @Unique
    private static final class FilteringPackResources implements PackResources {
        private final PackResources delegate;

        FilteringPackResources(PackResources delegate) {
            this.delegate = delegate;
        }

        private static boolean isBlocked(PackType type, Identifier id) {
            if (type != PackType.CLIENT_RESOURCES || id == null || !"minecraft".equals(id.getNamespace())) {
                return false;
            }
            String path = id.getPath();
            ModConfig cfg = ModConfig.getInstance();
            if (!cfg.customCrosshair && "textures/gui/sprites/hud/crosshair.png".equals(path)) {
                return true;
            }
            if (!cfg.customFont && (path.startsWith("font/") || path.startsWith("textures/font/"))) {
                return true;
            }
            return false;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier id) {
            if (isBlocked(type, id)) {
                return null;
            }
            return this.delegate.getResource(type, id);
        }

        @Override
        public void listResources(PackType type, String namespace, String pathPrefix, PackResources.ResourceOutput output) {
            this.delegate.listResources(type, namespace, pathPrefix, (id, supplier) -> {
                if (!isBlocked(type, id)) {
                    output.accept(id, supplier);
                }
            });
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return this.delegate.getNamespaces(type);
        }

        @Override
        public String packId() {
            return this.delegate.packId();
        }

        @Override
        public Optional<KnownPack> knownPackInfo() {
            return this.delegate.knownPackInfo();
        }

        @Override
        public PackLocationInfo location() {
            return this.delegate.location();
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... paths) {
            return this.delegate.getRootResource(paths);
        }

        @Override
        public <T> T getMetadataSection(MetadataSectionType<T> type) throws IOException {
            return this.delegate.getMetadataSection(type);
        }

        @Override
        public void close() {
            this.delegate.close();
        }
    }
}
