package com.client.mod;

import java.util.HashSet;
import java.util.Set;

/**
 * Verifies theme palette registration, Sestia Leaf default tokens, legacy Sestia Crimson preservation,
 * and out-of-range fallback behavior in {@link JelloTheme}.
 */
public class JelloThemeTest {

    public static void main(String[] args) {
        testThemeRegistryAndDefaults();
        testSestiaLeafAndCrimsonTokens();
        testLeafStrainsAndGlassOpacity();
        testOutOfRangeFallback();
        testAllThemesCompleteAndUnique();
        testTitleThemePillGeometryAndSelection();
        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA, JelloTheme.STRAIN_NEON_EMERALD, JelloTheme.DEFAULT_GLASS_OPACITY);
        System.out.println("ALL JelloThemeTest ASSERTIONS PASSED!");
    }

    private static void testThemeRegistryAndDefaults() {
        assert JelloTheme.THEME_COUNT == 2 : "Expected 2 registered themes, got " + JelloTheme.THEME_COUNT;
        assert JelloTheme.THEME_SESTIA == 0 : "Default Sestia Leaf index must stay 0";
        assert JelloTheme.THEME_SESTIA_CRIMSON == 1 : "Sestia Crimson must be index 1";
        assert "Sestia Leaf".equals(JelloTheme.getThemeName(JelloTheme.THEME_SESTIA))
                : "Expected Sestia Leaf name for index 0";
        assert "Crimson".equals(JelloTheme.getThemeName(JelloTheme.THEME_SESTIA_CRIMSON))
                : "Expected Crimson name for index 1";
    }

    private static void testSestiaLeafAndCrimsonTokens() {
        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA);
        assert JelloTheme.isLeafTheme() : "isLeafTheme() must return true on THEME_SESTIA";
        assert JelloTheme.getCurrentTheme() == JelloTheme.THEME_SESTIA;
        assert JelloTheme.BORDER_ACTIVE_SOFT == 0xFF5AFCA8 : "Expected bright pastel-emerald active border for Sestia Leaf";
        int cardAlpha = (JelloTheme.CARD_INACTIVE >>> 24) & 0xFF;
        assert cardAlpha > 0 && cardAlpha < 0xFF
                : "Sestia Leaf module cards must be translucent glass, got alpha=" + cardAlpha;

        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA_CRIMSON, JelloTheme.STRAIN_NEON_EMERALD, JelloTheme.DEFAULT_GLASS_OPACITY);
        assert JelloTheme.isLeafTheme() : "isLeafTheme() must return true on translucent Crimson Leaf theme";
        assert JelloTheme.getCurrentTheme() == JelloTheme.THEME_SESTIA_CRIMSON;
        assert JelloTheme.ACCENT == 0xFFFF2E43 : "Expected vivid crimson accent 0xFFFF2E43";
        assert JelloTheme.getLeafTintRgb() == JelloTheme.CRIMSON_LEAF_TINT_RGB
                : "Expected crimson leaf tint RGB 0xFF4757";
        int crimsonCardAlpha = (JelloTheme.CARD_INACTIVE >>> 24) & 0xFF;
        assert crimsonCardAlpha > 0 && crimsonCardAlpha < 0xFF
                : "Crimson module cards must be translucent glass, got alpha=" + crimsonCardAlpha;

        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA_CRIMSON, JelloTheme.STRAIN_NEON_EMERALD, 0.55f);
        int crimsonLowWinA = (JelloTheme.WINDOW_BG >>> 24) & 0xFF;
        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA_CRIMSON, JelloTheme.STRAIN_NEON_EMERALD, 1.00f);
        int crimsonHighWinA = (JelloTheme.WINDOW_BG >>> 24) & 0xFF;
        assert crimsonLowWinA < crimsonHighWinA
                : "Expected Crimson WINDOW_BG alpha to scale with glassOpacity";
    }

    private static void testLeafStrainsAndGlassOpacity() {
        assert JelloTheme.STRAIN_COUNT == 5 : "Expected 5 Leaf Strains";
        Set<String> strainNames = new HashSet<>();
        Set<Integer> tints = new HashSet<>();
        for (int s = 0; s < JelloTheme.STRAIN_COUNT; s++) {
            String name = JelloTheme.getLeafStrainName(s);
            assert name != null && !name.isBlank() : "Blank Leaf Strain name at index " + s;
            assert strainNames.add(name) : "Duplicate Leaf Strain name: " + name;

            int tintRgb = JelloTheme.getLeafStrainTintRgb(s);
            assert tintRgb != 0 && tints.add(tintRgb) : "Duplicate or zero tint RGB for strain " + name;
            int[] dots = JelloTheme.getLeafStrainPalette(s);
            assert dots != null && dots.length == 3 : "Expected 3 swatch dots for strain " + name;

            JelloTheme.applyTheme(JelloTheme.THEME_SESTIA, s, JelloTheme.DEFAULT_GLASS_OPACITY);
            assert JelloTheme.isLeafTheme() : "Strain must keep Sestia Leaf theme active";
            assert JelloTheme.getCurrentLeafStrain() == s : "Expected currentLeafStrain=" + s;
            assert JelloTheme.getLeafTintRgb() == tintRgb : "Expected active leaf tint RGB to match strain";
            assert (JelloTheme.BORDER_ACTIVE_SOFT & 0x00FFFFFF) == tintRgb
                    : "BORDER_ACTIVE_SOFT RGB must match strain tint for " + name;
        }

        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA, JelloTheme.STRAIN_GOLD_LEAF, 0.55f);
        int lowWinA = (JelloTheme.WINDOW_BG >>> 24) & 0xFF;
        int lowCardA = (JelloTheme.CARD_INACTIVE >>> 24) & 0xFF;

        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA, JelloTheme.STRAIN_GOLD_LEAF, JelloTheme.DEFAULT_GLASS_OPACITY);
        int midWinA = (JelloTheme.WINDOW_BG >>> 24) & 0xFF;
        int midCardA = (JelloTheme.CARD_INACTIVE >>> 24) & 0xFF;

        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA, JelloTheme.STRAIN_GOLD_LEAF, 1.00f);
        int highWinA = (JelloTheme.WINDOW_BG >>> 24) & 0xFF;
        int highCardA = (JelloTheme.CARD_INACTIVE >>> 24) & 0xFF;

        assert lowWinA < midWinA && midWinA < highWinA
                : "Expected WINDOW_BG alpha to scale monotonically: " + lowWinA + " < " + midWinA + " < " + highWinA;
        assert lowCardA < midCardA && midCardA < highCardA
                : "Expected CARD_INACTIVE alpha to scale monotonically: " + lowCardA + " < " + midCardA + " < " + highCardA;

        // Invalid strain & NaN glass opacity fall back safely
        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA, -3, Float.NaN);
        assert JelloTheme.getCurrentLeafStrain() == JelloTheme.STRAIN_NEON_EMERALD;
        assert Math.abs(JelloTheme.getCurrentGlassOpacity() - JelloTheme.DEFAULT_GLASS_OPACITY) < 1e-4f;
    }

    private static void testOutOfRangeFallback() {
        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA_CRIMSON);
        JelloTheme.applyTheme(-1);
        assert JelloTheme.isLeafTheme() && JelloTheme.getCurrentTheme() == JelloTheme.THEME_SESTIA
                : "Negative theme index must fall back to THEME_SESTIA";

        JelloTheme.applyTheme(JelloTheme.THEME_SESTIA_CRIMSON);
        JelloTheme.applyTheme(999);
        assert JelloTheme.isLeafTheme() && JelloTheme.getCurrentTheme() == JelloTheme.THEME_SESTIA
                : "Out-of-bounds theme index must fall back to THEME_SESTIA";

        ModConfig legacyCrimson = new ModConfig();
        legacyCrimson.guiTheme = 10;
        legacyCrimson.sanitize();
        assert legacyCrimson.guiTheme == JelloTheme.THEME_SESTIA_CRIMSON
                : "Legacy guiTheme=10 should migrate to THEME_SESTIA_CRIMSON (1)";

        ModConfig removedTheme = new ModConfig();
        removedTheme.guiTheme = 5;
        removedTheme.sanitize();
        assert removedTheme.guiTheme == JelloTheme.THEME_SESTIA
                : "Deleted legacy theme indices should reset to THEME_SESTIA (0)";
    }

    private static void testAllThemesCompleteAndUnique() {
        Set<String> names = new HashSet<>();
        for (int i = 0; i < JelloTheme.THEME_COUNT; i++) {
            String name = JelloTheme.getThemeName(i);
            assert name != null && !name.isBlank() : "Blank theme name at index " + i;
            assert names.add(name) : "Duplicate theme name: " + name;

            JelloTheme.applyTheme(i);
            assert JelloTheme.getCurrentTheme() == i : "Current theme mismatch for index " + i;
            assert JelloTheme.TEXT != 0 && JelloTheme.ACCENT != 0 && JelloTheme.WINDOW_BG != 0
                    : "Theme tokens must all be non-zero at index " + i;
        }
    }

    private static void testTitleThemePillGeometryAndSelection() {
        int wx = 100;
        int wy = 200;
        assert TitleThemePillWidget.hitTestSlot(wx, wy, 50, 210) == -2 : "Outside left should return -2";
        assert TitleThemePillWidget.hitTestSlot(wx, wy, wx + 10, wy + 12) == -1 : "Leaf icon zone should return -1";
        assert TitleThemePillWidget.hitTestSlot(wx, wy, wx + 39, wy + 12) == 0 : "First dot should hit slot 0 (Emerald)";
        assert TitleThemePillWidget.hitTestSlot(wx, wy, wx + 39 + 5 * 19, wy + 12) == 5 : "Sixth dot should hit slot 5 (Crimson)";

        ModConfig cfg = new ModConfig();
        cfg.guiTheme = JelloTheme.THEME_SESTIA;
        cfg.leafStrain = JelloTheme.STRAIN_FROST_MINT;
        assert TitleThemePillWidget.getActiveSlot(cfg) == JelloTheme.STRAIN_FROST_MINT;

        cfg.guiTheme = JelloTheme.THEME_SESTIA_CRIMSON;
        assert TitleThemePillWidget.getActiveSlot(cfg) == JelloTheme.STRAIN_COUNT;
        assert (TitleThemePillWidget.getDotColorArgb(JelloTheme.STRAIN_COUNT) & 0x00FFFFFF) == JelloTheme.CRIMSON_LEAF_TINT_RGB;
    }
}
