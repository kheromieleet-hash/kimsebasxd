package com.client.mod;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Optional;

/**
 * Custom Enchant Glint Colorizer for MC 26.3:
 * - Caches vanilla glint texture luminance once (item + armor)
 * - Uploads tinted {@link DynamicTexture}s directly to {@link ItemFeatureRenderer#ENCHANTED_GLINT_ITEM}
 *   and {@link ItemFeatureRenderer#ENCHANTED_GLINT_ARMOR} (Chroma throttled to <= 12 Hz)
 * - Restores vanilla glint textures cleanly on disable or {@code .panic}
 */
public final class CosmeticGlintManager {

    private static final long CHROMA_UPLOAD_INTERVAL_MS = 85L;

    private static final class GlintTarget {
        final Identifier id;
        final String label;
        int width = 0;
        int height = 0;
        byte[] baseLuma = null;
        DynamicTexture dynamicTexture = null;
        boolean customBound = false;

        GlintTarget(Identifier id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    private static final GlintTarget ITEM_TARGET = new GlintTarget(ItemFeatureRenderer.ENCHANTED_GLINT_ITEM, "sestia_glint_item");
    private static final GlintTarget ARMOR_TARGET = new GlintTarget(ItemFeatureRenderer.ENCHANTED_GLINT_ARMOR, "sestia_glint_armor");

    private static int lastAppliedRgb = -1;
    private static float lastAppliedIntensity = -1.0f;
    private static long lastUploadAtMs = 0L;
    private static int tickCounter = 0;

    private CosmeticGlintManager() {
    }

    public static void tick(Minecraft mc) {
        if (mc == null || mc.getTextureManager() == null) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        boolean active = cfg != null && cfg.cosmeticGlintEnabled && !PanicHandler.isPanicActive();
        if (!active) {
            if (ITEM_TARGET.customBound || ARMOR_TARGET.customBound) {
                restoreVanilla(mc);
            }
            return;
        }

        tickCounter++;
        boolean isChroma = cfg.cosmeticGlintColorPreset == WingsLogic.COLOR_CHROMA;
        long now = System.currentTimeMillis();
        if (isChroma && (now - lastUploadAtMs) < CHROMA_UPLOAD_INTERVAL_MS && ITEM_TARGET.customBound) {
            return;
        }

        int rgb = CosmeticsLogic.resolveColorRgb(cfg.cosmeticGlintColorPreset, cfg.cosmeticGlintCustomRgb, tickCounter, 0.0f);
        float intensity = CosmeticsLogic.clampGlintIntensity(cfg.cosmeticGlintIntensity);
        if (ITEM_TARGET.customBound && ARMOR_TARGET.customBound
                && rgb == lastAppliedRgb && Math.abs(intensity - lastAppliedIntensity) < 0.005f) {
            return;
        }

        applyTarget(mc, ITEM_TARGET, rgb, intensity);
        applyTarget(mc, ARMOR_TARGET, rgb, intensity);
        lastAppliedRgb = rgb;
        lastAppliedIntensity = intensity;
        lastUploadAtMs = now;
    }

    public static void restoreVanilla(Minecraft mc) {
        lastAppliedRgb = -1;
        lastAppliedIntensity = -1.0f;
        restoreTarget(mc, ITEM_TARGET);
        restoreTarget(mc, ARMOR_TARGET);
    }

    private static void restoreTarget(Minecraft mc, GlintTarget target) {
        if (!target.customBound) {
            return;
        }
        target.customBound = false;
        if (mc != null && mc.getTextureManager() != null) {
            try {
                // Release our dynamic texture so TextureManager reloads vanilla asset on next getTexture()
                mc.getTextureManager().release(target.id);
            } catch (Throwable ignored) {
            }
        }
        if (target.dynamicTexture != null) {
            try {
                target.dynamicTexture.close();
            } catch (Throwable ignored) {
            }
            target.dynamicTexture = null;
        }
    }

    private static void applyTarget(Minecraft mc, GlintTarget target, int rgb, float intensity) {
        if (!ensureBaseLuminanceLoaded(mc, target)) {
            return;
        }
        TextureManager tm = mc.getTextureManager();
        if (target.dynamicTexture == null || target.dynamicTexture.getPixels() == null) {
            target.dynamicTexture = new DynamicTexture(target.label, target.width, target.height, false);
            tm.register(target.id, target.dynamicTexture);
            target.customBound = true;
        } else if (!target.customBound) {
            tm.register(target.id, target.dynamicTexture);
            target.customBound = true;
        }

        NativeImage pixels = target.dynamicTexture.getPixels();
        if (pixels == null) {
            return;
        }
        int tr = (rgb >> 16) & 0xFF;
        int tg = (rgb >> 8) & 0xFF;
        int tb = rgb & 0xFF;
        int w = target.width;
        int h = target.height;
        byte[] luma = target.baseLuma;

        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                float lum = ((luma[row + x] & 0xFF) / 255.0f) * intensity;
                int pr = Math.min(255, Math.round(tr * lum));
                int pg = Math.min(255, Math.round(tg * lum));
                int pb = Math.min(255, Math.round(tb * lum));
                int argb = 0xFF000000 | (pr << 16) | (pg << 8) | pb;
                pixels.setPixel(x, y, argb);
            }
        }
        target.dynamicTexture.upload();
    }

    private static boolean ensureBaseLuminanceLoaded(Minecraft mc, GlintTarget target) {
        if (target.baseLuma != null && target.width > 0 && target.height > 0) {
            return true;
        }
        if (mc == null || mc.getResourceManager() == null) {
            return false;
        }
        try {
            Optional<Resource> res = mc.getResourceManager().getResource(target.id);
            if (res.isEmpty()) {
                return false;
            }
            try (InputStream is = res.get().open(); NativeImage src = NativeImage.read(is)) {
                int w = src.getWidth();
                int h = src.getHeight();
                if (w <= 0 || h <= 0) {
                    return false;
                }
                byte[] luma = new byte[w * h];
                for (int y = 0; y < h; y++) {
                    int row = y * w;
                    for (int x = 0; x < w; x++) {
                        int argb = src.getPixel(x, y);
                        int r = (argb >> 16) & 0xFF;
                        int g = (argb >> 8) & 0xFF;
                        int b = argb & 0xFF;
                        // Vanilla glint is purple-white; peak channel preserves full contrast cleanly
                        int peak = Math.max(r, Math.max(g, b));
                        luma[row + x] = (byte) peak;
                    }
                }
                target.width = w;
                target.height = h;
                target.baseLuma = luma;
                return true;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }
}
