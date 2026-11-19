package com.client.mod;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Procedural Signed Distance Field (SDF) texture atlas providing smooth anti-aliased
 * rounded corners, 1px borders, circles, and vector icons matched to current GUI scale.
 */
final class SmoothUiAtlas {

    static final Identifier ATLAS_ID = Identifier.fromNamespaceAndPath("inventoryhud", "smooth_ui_atlas");
    static final int ATLAS_W = 512;
    static final int ATLAS_H = 1024;
    static final int MAX_RADIUS = 12;
    static final int ICON_TEX_SIZE = 40;
    static final int GLOW_TEX_SIZE = 80;
    private static final int SLOT_PAD = 2;

    enum IconKind {
        GRID,
        BOLT,
        SLIDERS,
        TUNE,
        SEARCH,
        CLOSE,
        BRAND,
        CHECK,
        CHEVRON_RIGHT,
        KEYBOARD,
        STAR_FILLED,
        STAR_OUTLINE
    }

    record Region(int u, int v, int w, int h) {
    }

    static final class AtlasLayout {
        final int guiScale;
        final int atlasScale;
        final Region[] solids = new Region[MAX_RADIUS + 1];
        final Region[] rings = new Region[MAX_RADIUS + 1];
        final Region[] icons = new Region[IconKind.values().length];
        Region glow;

        AtlasLayout(int guiScale, int atlasScale) {
            this.guiScale = guiScale;
            this.atlasScale = atlasScale;
        }

        Region solid(int radius) {
            return this.solids[Mth.clamp(radius, 1, MAX_RADIUS)];
        }

        Region ring(int radius) {
            return this.rings[Mth.clamp(radius, 1, MAX_RADIUS)];
        }

        Region icon(IconKind kind) {
            return this.icons[kind.ordinal()];
        }

        Region glow() {
            return this.glow;
        }
    }

    @FunctionalInterface
    interface PixelSink {
        void accept(int x, int y, int argb);
    }

    private static final class LinearDynamicTexture extends DynamicTexture {
        LinearDynamicTexture(NativeImage pixels) {
            super(() -> "SestiaSmoothUiAtlas", pixels);
            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        }
    }

    private static AtlasLayout activeLayout;

    private SmoothUiAtlas() {
    }

    static AtlasLayout getOrUpload() {
        Minecraft mc;
        try {
            mc = Minecraft.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
        if (mc == null || mc.getWindow() == null || mc.getTextureManager() == null) {
            return null;
        }

        int currentGuiScale = Math.max(1, mc.getWindow().getGuiScale());
        if (activeLayout != null && activeLayout.guiScale == currentGuiScale) {
            return activeLayout;
        }

        NativeImage image = null;
        try {
            image = new NativeImage(ATLAS_W, ATLAS_H, false);
            for (int y = 0; y < ATLAS_H; y++) {
                for (int x = 0; x < ATLAS_W; x++) {
                    // Pure white RGB prevents dark edge halos during bilinear filtering
                    image.setPixel(x, y, 0x00FFFFFF);
                }
            }
            final NativeImage targetImg = image;
            AtlasLayout built = buildLayout(currentGuiScale, (px, py, argb) -> {
                if (px >= 0 && px < ATLAS_W && py >= 0 && py < ATLAS_H) {
                    targetImg.setPixel(px, py, argb);
                }
            });
            mc.getTextureManager().register(ATLAS_ID, new LinearDynamicTexture(image));
            image = null; // owned by LinearDynamicTexture now
            activeLayout = built;
            return activeLayout;
        } catch (Throwable ignored) {
            if (image != null) {
                try {
                    image.close();
                } catch (Throwable ignoredClose) {
                }
            }
            return null;
        }
    }

    static AtlasLayout buildLayout(int guiScale, PixelSink sink) {
        int clampedGuiScale = Math.max(1, guiScale);
        int atlasScale = Math.max(2, Math.min(4, clampedGuiScale));
        float aaHalf = 0.55f * ((float) atlasScale / (float) clampedGuiScale);
        AtlasLayout layout = new AtlasLayout(clampedGuiScale, atlasScale);

        int curX = SLOT_PAD;
        int curY = SLOT_PAD;
        int rowH = 0;

        for (int r = 1; r <= MAX_RADIUS; r++) {
            int diam = r * 2 * atlasScale;
            if (curX + diam + SLOT_PAD > ATLAS_W) {
                curX = SLOT_PAD;
                curY += rowH + SLOT_PAD;
                rowH = 0;
            }
            Region region = new Region(curX, curY, diam, diam);
            layout.solids[r] = region;
            rasterizeCircle(region.u(), region.v(), r * atlasScale, aaHalf, sink);
            curX += diam + SLOT_PAD;
            rowH = Math.max(rowH, diam);
        }

        curX = SLOT_PAD;
        curY += rowH + SLOT_PAD;
        rowH = 0;

        for (int r = 1; r <= MAX_RADIUS; r++) {
            int diam = r * 2 * atlasScale;
            if (curX + diam + SLOT_PAD > ATLAS_W) {
                curX = SLOT_PAD;
                curY += rowH + SLOT_PAD;
                rowH = 0;
            }
            Region region = new Region(curX, curY, diam, diam);
            layout.rings[r] = region;
            rasterizeRing(region.u(), region.v(), r * atlasScale, atlasScale, aaHalf, sink);
            curX += diam + SLOT_PAD;
            rowH = Math.max(rowH, diam);
        }

        curX = SLOT_PAD;
        curY += rowH + SLOT_PAD;

        for (IconKind kind : IconKind.values()) {
            if (curX + ICON_TEX_SIZE + SLOT_PAD > ATLAS_W) {
                curX = SLOT_PAD;
                curY += ICON_TEX_SIZE + SLOT_PAD;
            }
            Region region = new Region(curX, curY, ICON_TEX_SIZE, ICON_TEX_SIZE);
            layout.icons[kind.ordinal()] = region;
            rasterizeIcon(kind, region.u(), region.v(), sink);
            curX += ICON_TEX_SIZE + SLOT_PAD;
        }

        curX = SLOT_PAD;
        curY += ICON_TEX_SIZE + SLOT_PAD;
        Region glowRegion = new Region(curX, curY, GLOW_TEX_SIZE, GLOW_TEX_SIZE);
        layout.glow = glowRegion;
        rasterizeRadialGlow(glowRegion.u(), glowRegion.v(), GLOW_TEX_SIZE, sink);

        return layout;
    }

    private static void rasterizeRadialGlow(int u, int v, int size, PixelSink sink) {
        float half = size * 0.5f;
        for (int py = 0; py < size; py++) {
            float dy = (py + 0.5f - half) / (half - 1.5f);
            for (int px = 0; px < size; px++) {
                float dx = (px + 0.5f - half) / (half - 1.5f);
                float r = Mth.clamp((float) Math.hypot(dx, dy), 0.0f, 1.0f);
                float smooth = 1.0f - r;
                float cov = smooth * smooth * (0.4f + 0.6f * smooth);
                sink.accept(u + px, v + py, packWhiteAlpha(cov));
            }
        }
    }

    private static void rasterizeCircle(int u, int v, float rTex, float aaHalf, PixelSink sink) {
        int diam = Math.round(rTex * 2.0f);
        float invWidth = 1.0f / (2.0f * aaHalf);
        for (int py = 0; py < diam; py++) {
            float dy = (py + 0.5f) - rTex;
            for (int px = 0; px < diam; px++) {
                float dx = (px + 0.5f) - rTex;
                float dist = (float) Math.hypot(dx, dy) - rTex;
                float cov = Mth.clamp(0.5f - dist * invWidth, 0.0f, 1.0f);
                sink.accept(u + px, v + py, packWhiteAlpha(cov));
            }
        }
    }

    private static void rasterizeRing(int u, int v, float rTex, float thickness, float aaHalf, PixelSink sink) {
        int diam = Math.round(rTex * 2.0f);
        float rIn = Math.max(0.0f, rTex - thickness);
        float invWidth = 1.0f / (2.0f * aaHalf);
        for (int py = 0; py < diam; py++) {
            float dy = (py + 0.5f) - rTex;
            for (int px = 0; px < diam; px++) {
                float dx = (px + 0.5f) - rTex;
                float rad = (float) Math.hypot(dx, dy);
                float outerCov = Mth.clamp(0.5f - (rad - rTex) * invWidth, 0.0f, 1.0f);
                float innerCov = (rIn <= 0.0f) ? 0.0f : Mth.clamp(0.5f - (rad - rIn) * invWidth, 0.0f, 1.0f);
                float cov = Mth.clamp(outerCov - innerCov, 0.0f, 1.0f);
                sink.accept(u + px, v + py, packWhiteAlpha(cov));
            }
        }
    }

    private static void rasterizeIcon(IconKind kind, int u, int v, PixelSink sink) {
        float step = 10.0f / (float) ICON_TEX_SIZE;
        for (int py = 0; py < ICON_TEX_SIZE; py++) {
            float y = (py + 0.5f) * step;
            for (int px = 0; px < ICON_TEX_SIZE; px++) {
                float x = (px + 0.5f) * step;
                float d = iconSignedDistance(kind, x, y);
                float cov = Mth.clamp(0.5f - d * 2.6f, 0.0f, 1.0f);
                sink.accept(u + px, v + py, packWhiteAlpha(cov));
            }
        }
    }

    static float iconSignedDistance(IconKind kind, float x, float y) {
        return switch (kind) {
            case GRID -> {
                float tl = sdRoundedBox(x, y, 2.5f, 2.5f, 1.75f, 1.75f, 0.7f);
                float tr = sdRoundedBox(x, y, 7.5f, 2.5f, 1.75f, 1.75f, 0.7f);
                float bl = sdRoundedBox(x, y, 2.5f, 7.5f, 1.75f, 1.75f, 0.7f);
                float br = sdRoundedBox(x, y, 7.5f, 7.5f, 1.75f, 1.75f, 0.7f);
                yield Math.min(Math.min(tl, tr), Math.min(bl, br));
            }
            case BOLT -> {
                float s1 = sdSegment(x, y, 5.8f, 0.9f, 2.2f, 5.2f) - 0.78f;
                float s2 = sdSegment(x, y, 2.2f, 5.2f, 7.7f, 4.8f) - 0.72f;
                float s3 = sdSegment(x, y, 7.7f, 4.8f, 4.1f, 9.1f) - 0.78f;
                yield Math.min(s1, Math.min(s2, s3));
            }
            case SLIDERS -> {
                float l1 = sdSegment(x, y, 1.0f, 2.0f, 9.0f, 2.0f) - 0.48f;
                float k1 = sdCircle(x, y, 3.2f, 2.0f, 1.2f);
                float l2 = sdSegment(x, y, 1.0f, 5.0f, 9.0f, 5.0f) - 0.48f;
                float k2 = sdCircle(x, y, 6.8f, 5.0f, 1.2f);
                float l3 = sdSegment(x, y, 1.0f, 8.0f, 9.0f, 8.0f) - 0.48f;
                float k3 = sdCircle(x, y, 4.3f, 8.0f, 1.2f);
                yield Math.min(Math.min(Math.min(l1, k1), Math.min(l2, k2)), Math.min(l3, k3));
            }
            case TUNE -> {
                float l1 = sdSegment(x, y, 1.1f, 3.1f, 8.9f, 3.1f) - 0.58f;
                float k1 = sdCircle(x, y, 6.8f, 3.1f, 1.45f);
                float l2 = sdSegment(x, y, 1.1f, 6.9f, 8.9f, 6.9f) - 0.58f;
                float k2 = sdCircle(x, y, 3.2f, 6.9f, 1.45f);
                yield Math.min(Math.min(l1, k1), Math.min(l2, k2));
            }
            case SEARCH -> {
                float ring = Math.abs(sdCircle(x, y, 4.1f, 4.1f, 2.55f)) - 0.62f;
                float handle = sdSegment(x, y, 6.0f, 6.0f, 8.7f, 8.7f) - 0.72f;
                yield Math.min(ring, handle);
            }
            case CLOSE -> {
                float d1 = sdSegment(x, y, 2.3f, 2.3f, 7.7f, 7.7f) - 0.68f;
                float d2 = sdSegment(x, y, 7.7f, 2.3f, 2.3f, 7.7f) - 0.68f;
                yield Math.min(d1, d2);
            }
            case BRAND -> {
                float ax = Math.abs(x - 5.0f);
                float ay = Math.abs(y - 5.0f);
                float diamond = (ax + ay - 4.5f) * 0.70710677f;
                float carve = (float) Math.hypot(ax - 3.9f, ay - 3.9f) - 3.15f;
                yield Math.max(diamond, -carve);
            }
            case CHECK -> {
                float c1 = sdSegment(x, y, 1.9f, 5.2f, 4.2f, 7.5f) - 0.74f;
                float c2 = sdSegment(x, y, 4.2f, 7.5f, 8.2f, 2.5f) - 0.74f;
                yield Math.min(c1, c2);
            }
            case CHEVRON_RIGHT -> {
                float u1 = sdSegment(x, y, 3.2f, 2.0f, 6.6f, 5.0f) - 0.7f;
                float u2 = sdSegment(x, y, 6.6f, 5.0f, 3.2f, 8.0f) - 0.7f;
                yield Math.min(u1, u2);
            }
            case KEYBOARD -> {
                float frame = Math.abs(sdRoundedBox(x, y, 5.0f, 5.0f, 4.0f, 2.8f, 1.1f)) - 0.56f;
                float row1 = sdSegment(x, y, 2.5f, 4.1f, 7.5f, 4.1f) - 0.45f;
                float space = sdSegment(x, y, 3.2f, 6.0f, 6.8f, 6.0f) - 0.45f;
                yield Math.min(frame, Math.min(row1, space));
            }
            case STAR_FILLED -> sdStar5(x, y, 5.0f, 5.2f, 4.15f, 0.45f) - 0.12f;
            case STAR_OUTLINE -> Math.abs(sdStar5(x, y, 5.0f, 5.2f, 4.05f, 0.45f)) - 0.52f;
        };
    }

    private static float sdStar5(float px, float py, float cx, float cy, float r, float rf) {
        float x = Math.abs(px - cx);
        float y = cy - py;
        final float k1x = 0.80901699f;
        final float k1y = -0.58778525f;
        final float k2x = -0.80901699f;
        final float k2y = -0.58778525f;

        float d1 = Math.max(k1x * x + k1y * y, 0.0f) * 2.0f;
        x -= d1 * k1x;
        y -= d1 * k1y;

        float d2 = Math.max(k2x * x + k2y * y, 0.0f) * 2.0f;
        x -= d2 * k2x;
        y -= d2 * k2y;

        x = Math.abs(x);
        y -= r;

        float bax = rf * 0.58778525f;
        float bay = rf * 0.80901699f - 1.0f;
        float baDot = bax * bax + bay * bay;
        float h = Mth.clamp((x * bax + y * bay) / baDot, 0.0f, r);
        float dx = x - bax * h;
        float dy = y - bay * h;
        float dist = (float) Math.hypot(dx, dy);
        return (y * bax - x * bay < 0.0f) ? -dist : dist;
    }

    private static int packWhiteAlpha(float cov) {
        int a = Mth.clamp(Math.round(cov * 255.0f), 0, 255);
        return (a << 24) | 0x00FFFFFF;
    }

    private static float sdCircle(float px, float py, float cx, float cy, float r) {
        return (float) Math.hypot(px - cx, py - cy) - r;
    }

    private static float sdRoundedBox(float px, float py, float cx, float cy, float hw, float hh, float r) {
        float qx = Math.abs(px - cx) - hw + r;
        float qy = Math.abs(py - cy) - hh + r;
        return Math.min(Math.max(qx, qy), 0.0f) + (float) Math.hypot(Math.max(qx, 0.0f), Math.max(qy, 0.0f)) - r;
    }

    private static float sdSegment(float px, float py, float ax, float ay, float bx, float by) {
        float pax = px - ax;
        float pay = py - ay;
        float bax = bx - ax;
        float bay = by - ay;
        float denom = bax * bax + bay * bay;
        float h = denom <= 1e-6f ? 0.0f : Mth.clamp((pax * bax + pay * bay) / denom, 0.0f, 1.0f);
        return (float) Math.hypot(pax - bax * h, pay - bay * h);
    }
}
