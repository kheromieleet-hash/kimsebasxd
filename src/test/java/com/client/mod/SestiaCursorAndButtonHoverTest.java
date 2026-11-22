package com.client.mod;

import com.mojang.blaze3d.platform.cursor.CursorType;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;

/**
 * Verifies custom SDL3 hemp-leaf cursor resource validity, hotspot alignment, mode mapping,
 * and hovered-button leaf fill math.
 */
public class SestiaCursorAndButtonHoverTest {

    public static void main(String[] args) throws Exception {
        testCursorHotspotAndResources();
        testCursorModeResolution();
        testTransparentButtonAndHoverFrameColors();
        testTransparentSliderMathAndColors();
        System.out.println("ALL SestiaCursorAndButtonHoverTest ASSERTIONS PASSED!");
    }

    private static void testCursorHotspotAndResources() throws Exception {
        assert SestiaCursorManager.HOTSPOT_X == 6 : "Expected HOTSPOT_X == 6";
        assert SestiaCursorManager.HOTSPOT_Y == 3 : "Expected HOTSPOT_Y == 3";

        String[] paths = {
                SestiaCursorManager.NORMAL_CURSOR_RESOURCE,
                SestiaCursorManager.HOVER_CURSOR_RESOURCE
        };
        for (String path : paths) {
            try (InputStream in = SestiaCursorManager.class.getResourceAsStream(path)) {
                assert in != null : "Missing cursor PNG resource: " + path;
                BufferedImage img = ImageIO.read(in);
                assert img != null : "Failed to decode cursor PNG: " + path;
                assert img.getWidth() == 32 && img.getHeight() == 32
                        : "Expected 32x32 cursor image for " + path + ", got " + img.getWidth() + "x" + img.getHeight();

                int argb = img.getRGB(SestiaCursorManager.HOTSPOT_X, SestiaCursorManager.HOTSPOT_Y);
                int alpha = (argb >>> 24) & 0xFF;
                int g = (argb >>> 8) & 0xFF;
                assert alpha >= 180 : "Hotspot pixel must be visibly opaque for " + path + ", got alpha=" + alpha;
                assert g >= 120 : "Hotspot pixel should sit on emerald leaf tip, got g=" + g;
            }
        }
    }

    private static void testCursorModeResolution() {
        assert SestiaCursorManager.resolveMode(null) == SestiaCursorManager.CursorMode.NORMAL;
        assert SestiaCursorManager.resolveMode(CursorType.DEFAULT) == SestiaCursorManager.CursorMode.NORMAL;
        assert SestiaCursorManager.resolveModeByName("arrow") == SestiaCursorManager.CursorMode.NORMAL;
        assert SestiaCursorManager.resolveModeByName("crosshair") == SestiaCursorManager.CursorMode.NORMAL;
        assert SestiaCursorManager.resolveModeByName("pointing_hand") == SestiaCursorManager.CursorMode.HOVER;
        assert SestiaCursorManager.resolveModeByName("ibeam") == SestiaCursorManager.CursorMode.SYSTEM_PASSTHROUGH;
        assert SestiaCursorManager.resolveModeByName("resize_ns") == SestiaCursorManager.CursorMode.SYSTEM_PASSTHROUGH;
        assert SestiaCursorManager.resolveModeByName("resize_ew") == SestiaCursorManager.CursorMode.SYSTEM_PASSTHROUGH;
        assert SestiaCursorManager.resolveModeByName("resize_all") == SestiaCursorManager.CursorMode.SYSTEM_PASSTHROUGH;
        assert SestiaCursorManager.resolveModeByName("not_allowed") == SestiaCursorManager.CursorMode.SYSTEM_PASSTHROUGH;
    }

    private static void testTransparentButtonAndHoverFrameColors() {
        int normalFill = SestiaButtonHoverRenderer.resolveFillColor(true, false, 1.0f);
        int hoveredFill = SestiaButtonHoverRenderer.resolveFillColor(true, true, 1.0f);
        int disabledFill = SestiaButtonHoverRenderer.resolveFillColor(false, true, 1.0f);

        int normalFillA = (normalFill >>> 24) & 0xFF;
        int hoveredFillA = (hoveredFill >>> 24) & 0xFF;
        int disabledFillA = (disabledFill >>> 24) & 0xFF;
        assert normalFillA > 0 && normalFillA <= 0x40 : "Normal fill must be sheer transparent: " + normalFillA;
        assert hoveredFillA > normalFillA && hoveredFillA <= 0x50 : "Hovered fill must stay transparent: " + hoveredFillA;
        assert disabledFillA > 0 && disabledFillA < normalFillA : "Disabled fill should be dimmer than normal";

        int normalBorder = SestiaButtonHoverRenderer.resolveBorderColor(true, false, 1.0f);
        int hoveredBorder = SestiaButtonHoverRenderer.resolveBorderColor(true, true, 1.0f);
        int disabledHoverBorder = SestiaButtonHoverRenderer.resolveBorderColor(false, true, 1.0f);

        assert (hoveredBorder >>> 24) == 0xFF : "Hovered active border should have full alpha";
        assert (hoveredBorder & 0x00FFFFFF) == 0x0010E88A : "Hovered active border should be neon emerald";
        assert (normalBorder & 0x00FFFFFF) != 0x0010E88A : "Non-hovered border must not show emerald hover frame";
        assert (disabledHoverBorder & 0x00FFFFFF) != 0x0010E88A : "Disabled hovered border must not glow emerald";

        int halfBorder = SestiaButtonHoverRenderer.resolveBorderColor(true, true, 0.5f);
        int zeroBorder = SestiaButtonHoverRenderer.resolveBorderColor(true, true, 0.0f);
        assert ((halfBorder >>> 24) & 0xFF) == 128 : "Expected 50% scaled border alpha";
        assert (halfBorder & 0x00FFFFFF) == 0x0010E88A : "RGB channels must remain unchanged";
        assert ((zeroBorder >>> 24) & 0xFF) == 0 : "Expected zero alpha when widgetAlpha is 0";
    }

    private static void testTransparentSliderMathAndColors() {
        assert SestiaButtonHoverRenderer.computeSliderFillWidth(0, 0.5) == 0;
        assert SestiaButtonHoverRenderer.computeSliderFillWidth(150, Double.NaN) == 0;
        assert SestiaButtonHoverRenderer.computeSliderFillWidth(150, -0.2) == 0;
        assert SestiaButtonHoverRenderer.computeSliderFillWidth(150, 0.0) == 0;
        assert SestiaButtonHoverRenderer.computeSliderFillWidth(150, 0.5) == 74;
        assert SestiaButtonHoverRenderer.computeSliderFillWidth(150, 1.0) == 148;
        assert SestiaButtonHoverRenderer.computeSliderFillWidth(150, 1.5) == 148;

        int normProgress = SestiaButtonHoverRenderer.resolveSliderProgressColor(true, false, 1.0f);
        int hoverProgress = SestiaButtonHoverRenderer.resolveSliderProgressColor(true, true, 1.0f);
        int normMarker = SestiaButtonHoverRenderer.resolveSliderMarkerColor(true, false, 1.0f);
        int hoverMarker = SestiaButtonHoverRenderer.resolveSliderMarkerColor(true, true, 1.0f);
        assert ((normProgress >>> 24) & 0xFF) <= 0x40 : "Normal slider fill must stay translucent";
        assert ((hoverProgress >>> 24) & 0xFF) <= 0x55 : "Hovered slider fill must keep label sharp";
        assert ((normMarker >>> 24) & 0xFF) >= 0xAA : "Slider marker should remain clearly visible";
        assert ((hoverMarker >>> 24) & 0xFF) == 0xFF : "Hovered slider marker should be crisp bright emerald";
    }
}
