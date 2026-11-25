package com.client.mod;

import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Safe local PNG Cape & Clan Emblem loader for Sestia Client ({@code config/sestia/capes/}).
 * Validates file names (rejecting path traversal), size (<= 2 MiB), and image dimensions (<= 1024x1024),
 * caches a single active {@link DynamicTexture} on the render thread, and releases GPU memory cleanly on swap or {@code .panic}.
 */
public final class CustomCapeManager {

    private static final long MAX_FILE_BYTES = 2L * 1024L * 1024L;
    private static final int MAX_DIMENSION = 1024;
    private static final Identifier CAPE_TEX_ID = Identifier.fromNamespaceAndPath("sestia", "custom_png_cape");

    private static volatile List<String> cachedFiles = List.of();
    private static String loadedFileName = "";
    private static long loadedFileMtime = -1L;
    private static DynamicTexture activeTexture = null;
    private static boolean textureRegistered = false;
    private static long lastScanAtMs = 0L;

    private CustomCapeManager() {
    }

    public static Path getCapesDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("sestia").resolve("capes");
    }

    /**
     * Pure validation helper rejecting traversal segments, path separators, and non-PNG extensions.
     */
    public static boolean isValidCapeFileName(String name) {
        if (name == null || name.isBlank() || name.length() > 80) {
            return false;
        }
        String trimmed = name.trim();
        if (trimmed.contains("/") || trimmed.contains("\\") || trimmed.contains("..") || trimmed.indexOf('\0') >= 0) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.endsWith(".png") && lower.length() > 4;
    }

    public static List<String> listCapeFiles(boolean forceScan) {
        long now = System.currentTimeMillis();
        if (!forceScan && (now - lastScanAtMs) < 3_000L && lastScanAtMs > 0L) {
            return cachedFiles;
        }
        lastScanAtMs = now;
        Path dir = getCapesDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
            cachedFiles = List.of();
            return cachedFiles;
        }
        List<String> out = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String fn = p.getFileName() != null ? p.getFileName().toString() : "";
                if (isValidCapeFileName(fn)) {
                    out.add(fn);
                }
            });
        } catch (IOException ignored) {
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        cachedFiles = Collections.unmodifiableList(out);
        return cachedFiles;
    }

    /**
     * Resolves and binds the configured PNG cape texture on the render thread.
     * Returns null if disabled, missing, invalid, or in {@code .panic} mode.
     */
    public static Identifier resolveActiveCapeTexture(Minecraft mc, ModConfig cfg) {
        if (mc == null || mc.getTextureManager() == null || cfg == null || PanicHandler.isPanicActive()) {
            if (PanicHandler.isPanicActive()) {
                releaseTexture(mc);
            }
            return null;
        }
        String selected = cfg.cosmeticCustomCapeFile != null ? cfg.cosmeticCustomCapeFile.trim() : "";
        if (selected.isEmpty()) {
            List<String> files = listCapeFiles(false);
            if (files.isEmpty()) {
                return null;
            }
            selected = files.get(0);
        }
        if (!isValidCapeFileName(selected)) {
            return null;
        }

        Path dir = getCapesDir().toAbsolutePath().normalize();
        Path file = dir.resolve(selected).normalize();
        if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
            return null;
        }

        long mtime;
        long size;
        try {
            size = Files.size(file);
            if (size <= 0L || size > MAX_FILE_BYTES) {
                return null;
            }
            mtime = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return null;
        }

        if (textureRegistered && activeTexture != null && selected.equals(loadedFileName) && mtime == loadedFileMtime) {
            return CAPE_TEX_ID;
        }

        NativeImage img = null;
        try (InputStream in = Files.newInputStream(file)) {
            img = NativeImage.read(in);
            if (img == null) {
                return null;
            }
            int w = img.getWidth();
            int h = img.getHeight();
            if (w <= 0 || h <= 0 || w > MAX_DIMENSION || h > MAX_DIMENSION) {
                img.close();
                return null;
            }
            releaseTexture(mc);
            DynamicTexture dyn = new DynamicTexture(() -> "sestia_custom_cape", img);
            img = null; // Ownership transferred to DynamicTexture
            TextureManager tm = mc.getTextureManager();
            tm.register(CAPE_TEX_ID, dyn);
            activeTexture = dyn;
            textureRegistered = true;
            loadedFileName = selected;
            loadedFileMtime = mtime;
            return CAPE_TEX_ID;
        } catch (Throwable ignored) {
            if (img != null) {
                try {
                    img.close();
                } catch (Throwable ignored2) {
                }
            }
            return null;
        }
    }

    public static void releaseTexture(Minecraft mc) {
        if (textureRegistered && mc != null && mc.getTextureManager() != null) {
            try {
                mc.getTextureManager().release(CAPE_TEX_ID);
            } catch (Throwable ignored) {
            }
        }
        textureRegistered = false;
        loadedFileName = "";
        loadedFileMtime = -1L;
        if (activeTexture != null) {
            try {
                activeTexture.close();
            } catch (Throwable ignored) {
            }
            activeTexture = null;
        }
    }

    public static void openCapesFolder() {
        Path dir = getCapesDir();
        try {
            Files.createDirectories(dir);
            String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("explorer.exe", dir.toAbsolutePath().toString());
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("open", dir.toAbsolutePath().toString());
            } else {
                pb = new ProcessBuilder("xdg-open", dir.toAbsolutePath().toString());
            }
            pb.start();
        } catch (Throwable ignored) {
        }
    }
}
