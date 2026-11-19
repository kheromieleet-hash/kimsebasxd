package com.client.mod.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Enables Sodium chunk-pipeline mixins only when Sodium is present at runtime.
 */
public final class SestiaMixinPlugin implements IMixinConfigPlugin {

    private boolean sodiumLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        boolean modLoaded = FabricLoader.getInstance().isModLoaded("sodium");
        boolean classPresent = SestiaMixinPlugin.class.getClassLoader()
                .getResource("net/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer.class") != null;
        this.sodiumLoaded = modLoaded || classPresent;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName != null && mixinClassName.contains(".mixin.sodium.")) {
            return this.sodiumLoaded;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
