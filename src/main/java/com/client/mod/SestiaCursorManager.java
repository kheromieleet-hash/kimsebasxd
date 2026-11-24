package com.client.mod;

import com.mojang.blaze3d.platform.cursor.CursorType;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDL_Surface;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Manages Sestia Client's custom 32x32 hardware SDL3 mouse cursors (normal and button-hover states),
 * automatically restoring system IBEAM/resize cursors and falling back cleanly on Panic Mode.
 */
public final class SestiaCursorManager {

    public static final int HOTSPOT_X = 6;
    public static final int HOTSPOT_Y = 3;

    public static final String NORMAL_CURSOR_RESOURCE = "/assets/sestia/textures/gui/cursor_leaf.png";
    public static final String HOVER_CURSOR_RESOURCE = "/assets/sestia/textures/gui/cursor_leaf_hover.png";

    public enum CursorMode {
        NORMAL,
        HOVER,
        SYSTEM_PASSTHROUGH
    }

    private static boolean loadAttempted = false;
    private static int loadedTintRgb = -1;
    private static long normalCursorHandle = 0L;
    private static long hoverCursorHandle = 0L;
    private static long activeHandle = 0L;
    private static boolean customActive = false;

    private SestiaCursorManager() {
    }

    public static CursorMode resolveMode(CursorType requested) {
        return resolveModeByName(requested != null ? requested.toString() : null);
    }

    /**
     * Resolves cursor mode from MC 26.3 cursor name without triggering native SDL CursorTypes init in tests.
     */
    public static CursorMode resolveModeByName(String cursorName) {
        if (cursorName == null) {
            return CursorMode.NORMAL;
        }
        return switch (cursorName) {
            case "pointing_hand" -> CursorMode.HOVER;
            case "ibeam", "resize_ns", "resize_ew", "resize_all", "not_allowed" -> CursorMode.SYSTEM_PASSTHROUGH;
            default -> CursorMode.NORMAL;
        };
    }

    /**
     * Applies Sestia's custom SDL3 hardware cursor or yields control back to vanilla Window.selectCursor.
     *
     * @return {@code true} if the custom hardware cursor was selected and vanilla selection should be skipped.
     */
    public static boolean applyCustomCursor(CursorType requested, boolean allowCursorChanges) {
        if (PanicHandler.isPanicActive()) {
            if (customActive) {
                long def = SDLMouse.SDL_GetDefaultCursor();
                if (def != 0L) {
                    SDLMouse.SDL_SetCursor(def);
                }
                customActive = false;
                activeHandle = 0L;
            }
            return false;
        }

        if (!allowCursorChanges) {
            if (customActive) {
                customActive = false;
                activeHandle = 0L;
            }
            return false;
        }

        CursorMode mode = resolveMode(requested);
        if (mode == CursorMode.SYSTEM_PASSTHROUGH) {
            if (customActive) {
                customActive = false;
                activeHandle = 0L;
            }
            return false;
        }

        int desiredTint = JelloTheme.isLeafTheme()
                ? (JelloTheme.getLeafTintRgb() & 0x00FFFFFF)
                : -1;
        if (loadAttempted && desiredTint != loadedTintRgb) {
            destroy();
        }
        ensureLoaded(desiredTint);
        long target = (mode == CursorMode.HOVER && hoverCursorHandle != 0L)
                ? hoverCursorHandle
                : normalCursorHandle;
        if (target == 0L) {
            return false;
        }

        if (!customActive || activeHandle != target) {
            SDLMouse.SDL_SetCursor(target);
            activeHandle = target;
            customActive = true;
        }
        return true;
    }

    static int tintCursorPixel(int argb, int targetRgb, boolean applyTint) {
        if (!applyTint) {
            return argb;
        }
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
            return argb;
        }
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int peak = Math.max(r, Math.max(g, b));
        float lum = Math.max(0.0f, Math.min(1.0f, (0.55f * peak + 0.45f * (0.25f * r + 0.65f * g + 0.10f * b)) / 230.0f));
        float highlight = Math.max(0.0f, Math.min(1.0f, (lum - 0.84f) / 0.16f)) * 0.28f;

        int tr = (targetRgb >>> 16) & 0xFF;
        int tg = (targetRgb >>> 8) & 0xFF;
        int tb = targetRgb & 0xFF;
        int outR = Math.max(0, Math.min(255, Math.round(tr * lum + (255 - tr) * highlight)));
        int outG = Math.max(0, Math.min(255, Math.round(tg * lum + (255 - tg) * highlight)));
        int outB = Math.max(0, Math.min(255, Math.round(tb * lum + (255 - tb) * highlight)));
        return (a << 24) | (outR << 16) | (outG << 8) | outB;
    }

    private static void ensureLoaded(int desiredTintRgb) {
        if (loadAttempted) {
            return;
        }
        loadAttempted = true;
        loadedTintRgb = desiredTintRgb;
        boolean applyTint = desiredTintRgb >= 0;
        normalCursorHandle = createCursorFromResource(NORMAL_CURSOR_RESOURCE, desiredTintRgb, applyTint);
        hoverCursorHandle = createCursorFromResource(HOVER_CURSOR_RESOURCE, desiredTintRgb, applyTint);
    }

    private static long createCursorFromResource(String resourcePath, int targetRgb, boolean applyTint) {
        ByteBuffer pixels = null;
        SDL_Surface surface = null;
        try (InputStream in = SestiaCursorManager.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                return 0L;
            }
            BufferedImage img = ImageIO.read(in);
            if (img == null) {
                return 0L;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w <= 0 || h <= 0) {
                return 0L;
            }

            pixels = MemoryUtil.memAlloc(w * h * 4);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = tintCursorPixel(img.getRGB(x, y), targetRgb, applyTint);
                    // RGBA32 on little-endian stores R, G, B, A bytes sequentially
                    pixels.put((byte) ((argb >>> 16) & 0xFF));
                    pixels.put((byte) ((argb >>> 8) & 0xFF));
                    pixels.put((byte) (argb & 0xFF));
                    pixels.put((byte) ((argb >>> 24) & 0xFF));
                }
            }
            pixels.flip();

            surface = SDLSurface.SDL_CreateSurfaceFrom(w, h, SDLPixels.SDL_PIXELFORMAT_RGBA32, pixels, w * 4);
            if (surface == null) {
                return 0L;
            }
            return SDLMouse.SDL_CreateColorCursor(surface, HOTSPOT_X, HOTSPOT_Y);
        } catch (Throwable ignored) {
            return 0L;
        } finally {
            if (surface != null) {
                try {
                    SDLSurface.SDL_DestroySurface(surface);
                } catch (Throwable ignored) {
                }
            }
            if (pixels != null) {
                MemoryUtil.memFree(pixels);
            }
        }
    }

    /**
     * Frees native SDL3 cursor handles on window shutdown.
     */
    public static void destroy() {
        if (customActive) {
            try {
                long def = SDLMouse.SDL_GetDefaultCursor();
                if (def != 0L) {
                    SDLMouse.SDL_SetCursor(def);
                }
            } catch (Throwable ignored) {
            }
        }
        if (normalCursorHandle != 0L) {
            try {
                SDLMouse.SDL_DestroyCursor(normalCursorHandle);
            } catch (Throwable ignored) {
            }
            normalCursorHandle = 0L;
        }
        if (hoverCursorHandle != 0L) {
            try {
                SDLMouse.SDL_DestroyCursor(hoverCursorHandle);
            } catch (Throwable ignored) {
            }
            hoverCursorHandle = 0L;
        }
        activeHandle = 0L;
        customActive = false;
        loadAttempted = false;
        loadedTintRgb = -1;
    }
}
