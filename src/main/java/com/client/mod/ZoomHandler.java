package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public class ZoomHandler {
    private static KeyMapping zoomKey;
    private static float currentZoom = 1.0f;
    private static float targetZoom = 1.0f;
    private static long lastFrameNanos = -1L;
    private static boolean configDirty = false;

    public static void setZoomKey(KeyMapping key) {
        zoomKey = key;
    }

    private static boolean isZoomKeyPressed() {
        ModConfig config = ModConfig.getInstance();
        if (!config.zoomEnabled) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || (mc.gui != null && mc.gui.screen() != null)) {
            return false;
        }
        if (zoomKey != null) {
            return zoomKey.isDown();
        }
        return InputConstants.isKeyDown(InputConstants.KEY_C);
    }

    public static boolean isZooming() {
        updateFrame();
        return currentZoom > 1.005f;
    }

    public static float getCurrentZoomFactor() {
        return currentZoom;
    }

    public static void update() {
        if (configDirty && !isZoomKeyPressed()) {
            configDirty = false;
            ModConfig.getInstance().save();
        }
    }

    private static void updateFrame() {
        long now = System.nanoTime();
        if (lastFrameNanos == -1L) {
            lastFrameNanos = now;
        }
        long elapsedNanos = now - lastFrameNanos;
        if (elapsedNanos < 200_000L) {
            // Already updated within the last 0.2ms (same render frame)
            return;
        }
        lastFrameNanos = now;

        float dt = Mth.clamp(elapsedNanos / 1_000_000_000.0f, 0.0f, 0.1f);
        ModConfig config = ModConfig.getInstance();
        targetZoom = isZoomKeyPressed() ? config.zoomFactor : 1.0f;

        // Frame-rate independent smooth exponential interpolation
        float alpha = 1.0f - (float) Math.exp(-18.0f * dt);
        currentZoom += (targetZoom - currentZoom) * alpha;

        if (Math.abs(currentZoom - targetZoom) < 0.004f) {
            currentZoom = targetZoom;
        }
    }

    public static boolean onMouseScrolled(double scrollY) {
        if (isZoomKeyPressed() && scrollY != 0.0) {
            ModConfig config = ModConfig.getInstance();
            float step = Math.max(0.25f, config.zoomFactor * 0.12f);
            config.zoomFactor = Mth.clamp(config.zoomFactor + (scrollY > 0 ? step : -step), 1.5f, 20.0f);
            configDirty = true;
            return true;
        }
        return false;
    }
}
