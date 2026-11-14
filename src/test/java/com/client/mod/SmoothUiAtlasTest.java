package com.client.mod;

import java.util.Arrays;

/**
 * Verifies {@link SmoothUiAtlas} shelf bounds, anti-aliased edge smoothing,
 * pure-white RGB preservation, and crisp coverage for all vector icons.
 */
public class SmoothUiAtlasTest {

    public static void main(String[] args) {
        for (int scale : new int[]{1, 2, 3, 4}) {
            verifyScale(scale);
        }
        System.out.println("ALL SmoothUiAtlasTest ASSERTIONS PASSED!");
    }

    private static void verifyScale(int guiScale) {
        int[] pixels = new int[SmoothUiAtlas.ATLAS_W * SmoothUiAtlas.ATLAS_H];
        Arrays.fill(pixels, 0x00FFFFFF);

        SmoothUiAtlas.AtlasLayout layout = SmoothUiAtlas.buildLayout(guiScale, (x, y, argb) -> {
            assert x >= 0 && x < SmoothUiAtlas.ATLAS_W : "X out of atlas bounds: " + x;
            assert y >= 0 && y < SmoothUiAtlas.ATLAS_H : "Y out of atlas bounds: " + y;
            assert (argb & 0x00FFFFFF) == 0x00FFFFFF : "Expected pure white RGB without dark edge fringe";
            pixels[y * SmoothUiAtlas.ATLAS_W + x] = argb;
        });

        for (int r = 1; r <= SmoothUiAtlas.MAX_RADIUS; r++) {
            SmoothUiAtlas.Region solid = layout.solid(r);
            SmoothUiAtlas.Region ring = layout.ring(r);
            assertInBounds(solid);
            assertInBounds(ring);

            int cx = solid.u() + solid.w() / 2;
            int cy = solid.v() + solid.h() / 2;
            int centerAlpha = (pixels[cy * SmoothUiAtlas.ATLAS_W + cx] >>> 24) & 0xFF;
            assert centerAlpha == 255 : "Expected solid center opaque for r=" + r + ", got " + centerAlpha;

            if (r >= 3) {
                int ringCenterX = ring.u() + ring.w() / 2;
                int ringCenterY = ring.v() + ring.h() / 2;
                int hollowAlpha = (pixels[ringCenterY * SmoothUiAtlas.ATLAS_W + ringCenterX] >>> 24) & 0xFF;
                assert hollowAlpha == 0 : "Expected hollow center for ring r=" + r + ", got " + hollowAlpha;
                assertHasAntiAliasedTransition(pixels, solid, "solid r=" + r + " scale=" + guiScale);
                assertHasAntiAliasedTransition(pixels, ring, "ring r=" + r + " scale=" + guiScale);
            }
        }

        for (SmoothUiAtlas.IconKind kind : SmoothUiAtlas.IconKind.values()) {
            SmoothUiAtlas.Region reg = layout.icon(kind);
            assertInBounds(reg);
            assertHasSolidAndAntiAliasedPixels(pixels, reg, "icon " + kind + " scale=" + guiScale);
        }

        SmoothUiAtlas.Region glow = layout.glow();
        assertInBounds(glow);
        int gCx = glow.u() + glow.w() / 2;
        int gCy = glow.v() + glow.h() / 2;
        int glowCenterAlpha = (pixels[gCy * SmoothUiAtlas.ATLAS_W + gCx] >>> 24) & 0xFF;
        int glowCornerAlpha = (pixels[glow.v() * SmoothUiAtlas.ATLAS_W + glow.u()] >>> 24) & 0xFF;
        assert glowCenterAlpha >= 240 : "Expected strong center alpha for radial glow, got " + glowCenterAlpha;
        assert glowCornerAlpha == 0 : "Expected zero corner alpha to prevent quad clipping, got " + glowCornerAlpha;
    }

    private static void assertInBounds(SmoothUiAtlas.Region r) {
        assert r != null;
        assert r.u() >= 0 && r.u() + r.w() <= SmoothUiAtlas.ATLAS_W : "Region X overflows atlas: " + r;
        assert r.v() >= 0 && r.v() + r.h() <= SmoothUiAtlas.ATLAS_H : "Region Y overflows atlas: " + r;
    }

    private static void assertHasAntiAliasedTransition(int[] pixels, SmoothUiAtlas.Region r, String label) {
        boolean foundAa = false;
        for (int y = r.v(); y < r.v() + r.h() && !foundAa; y++) {
            for (int x = r.u(); x < r.u() + r.w(); x++) {
                int a = (pixels[y * SmoothUiAtlas.ATLAS_W + x] >>> 24) & 0xFF;
                if (a > 0 && a < 255) {
                    foundAa = true;
                    break;
                }
            }
        }
        assert foundAa : "Expected partial anti-aliased edge pixels in " + label;
    }

    private static void assertHasSolidAndAntiAliasedPixels(int[] pixels, SmoothUiAtlas.Region r, String label) {
        boolean hasSolid = false;
        boolean hasAa = false;
        for (int y = r.v(); y < r.v() + r.h(); y++) {
            for (int x = r.u(); x < r.u() + r.w(); x++) {
                int a = (pixels[y * SmoothUiAtlas.ATLAS_W + x] >>> 24) & 0xFF;
                if (a >= 240) hasSolid = true;
                if (a > 0 && a < 255) hasAa = true;
            }
        }
        assert hasSolid : "Expected opaque strokes in " + label;
        assert hasAa : "Expected anti-aliased edge smoothing in " + label;
    }
}
