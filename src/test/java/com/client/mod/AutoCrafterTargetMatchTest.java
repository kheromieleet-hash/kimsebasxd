package com.client.mod;

import java.util.HashSet;
import java.util.Set;

public class AutoCrafterTargetMatchTest {

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError("Assertion failed (expected false): " + message);
        }
    }

    public static void main(String[] args) {
        ModConfig cfg = new ModConfig();
        cfg.autoCrafterTargets = new HashSet<>(Set.of(
                "iron_block",
                "glowstone",
                "decraft",
                "nuggets",
                "raw_blocks",
                "food_paper"
        ));

        cfg.sanitize();

        assertFalse(cfg.autoCrafterTargets.contains("decraft"), "sanitize should strip legacy decraft");
        assertFalse(cfg.autoCrafterTargets.contains("nuggets"), "sanitize should strip legacy nuggets");
        assertFalse(cfg.autoCrafterTargets.contains("raw_blocks"), "sanitize should strip legacy raw_blocks");
        assertFalse(cfg.autoCrafterTargets.contains("food_paper"), "sanitize should strip legacy food_paper");

        assertTrue(AutoCrafterHandler.isExplicitTargetEnabled("iron_block", cfg), "iron_block should stay enabled");
        assertTrue(AutoCrafterHandler.isExplicitTargetEnabled("glowstone", cfg), "glowstone should stay enabled");

        assertFalse(AutoCrafterHandler.isExplicitTargetEnabled("iron_ingot", cfg), "iron_ingot must not be enabled when unselected");
        assertFalse(AutoCrafterHandler.isExplicitTargetEnabled("quartz_block", cfg), "quartz_block must not be enabled via glowstone group");
        assertFalse(AutoCrafterHandler.isExplicitTargetEnabled("amethyst_block", cfg), "amethyst_block must not be enabled via glowstone group");
        assertFalse(AutoCrafterHandler.isExplicitTargetEnabled("", cfg), "empty id must be false");
        assertFalse(AutoCrafterHandler.isExplicitTargetEnabled(null, cfg), "null id must be false");

        System.out.println("AutoCrafterTargetMatchTest PASSED");
    }
}
