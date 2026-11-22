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

        ensureLoaded();
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

    private static void ensureLoaded() {
        if (loadAttempted) {
            return;
        }
        loadAttempted = true;
        normalCursorHandle = createCursorFromResource(NORMAL_CURSOR_RESOURCE);
        hoverCursorHandle = createCursorFromResource(HOVER_CURSOR_RESOURCE);
    }

    private static long createCursorFromResource(String resourcePath) {
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
                    int argb = img.getRGB(x, y);
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
    }
}
