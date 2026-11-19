package com.client.mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for scrollable Player List HUD sorting, visible row calculation, scroll clamping, and ModConfig.
 */
public final class PlayerListHudTest {

    public static void main(String[] args) {
        testSortAndScrollMath();
        testDefaultAlignment();
        testModConfigDefaultsAndCopy();
        System.out.println("ALL PlayerListHudTest ASSERTIONS PASSED!");
    }

    private static void testSortAndScrollMath() {
        List<PlayerListHudRenderer.NearbyPlayerInfo> input = List.of(
                new PlayerListHudRenderer.NearbyPlayerInfo("Steve", 45, 20),
                new PlayerListHudRenderer.NearbyPlayerInfo("Alex", 12, 18),
                new PlayerListHudRenderer.NearbyPlayerInfo("Zed", 12, 14),
                new PlayerListHudRenderer.NearbyPlayerInfo("Bob", 80, 20)
        );
        List<PlayerListHudRenderer.NearbyPlayerInfo> result = PlayerListHudRenderer.sortPlayers(input);
        assert result.size() == 4 : "All players should remain in sorted list";
        assert "Alex".equals(result.get(0).name()) : "Closest alphabetical tie-breaker failed";
        assert "Zed".equals(result.get(1).name()) : "Second player mismatch";
        assert "Steve".equals(result.get(2).name()) : "Third player mismatch";

        List<PlayerListHudRenderer.NearbyPlayerInfo> crowd = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            crowd.add(new PlayerListHudRenderer.NearbyPlayerInfo("Player" + i, i + 1, 20));
        }
        int visible = PlayerListHudRenderer.computeVisibleRowCount(crowd.size(), 20, 600, 6);
        assert visible == 20 : "Expected 20 visible rows out of 100 players, got " + visible;

        int off = 0;
        off = PlayerListHudRenderer.stepScrollOffset(off, -1.0, 100, visible);
        assert off == 1 : "Scroll down should advance by 1 row";
        off = PlayerListHudRenderer.stepScrollOffset(off, 1.0, 100, visible);
        assert off == 0 : "Scroll up should return to 0";
        off = PlayerListHudRenderer.stepScrollOffset(off, 1.0, 100, visible);
        assert off == 0 : "Scroll up must clamp at 0";
        off = PlayerListHudRenderer.clampScrollOffset(999, 100, visible);
        assert off == 80 : "Max scroll offset for 100/20 must be 80, got " + off;
    }

    private static void testDefaultAlignment() {
        int[] right = PlayerListHudRenderer.resolveDefaultCoords(0, 800, 140);
        assert right[0] == 800 - 140 - 6 : "Top Right X mismatch: " + right[0];
        assert right[1] == 6 : "Top Right Y mismatch";

        int[] left = PlayerListHudRenderer.resolveDefaultCoords(1, 800, 140);
        assert left[0] == 6 : "Top Left X mismatch: " + left[0];
        assert left[1] == 6 : "Top Left Y mismatch";
    }

    private static void testModConfigDefaultsAndCopy() {
        ModConfig a = new ModConfig();
        a.playerListEnabled = true;
        a.playerListAlign = 1;
        a.playerListShowDistance = false;
        a.playerListShowHealth = false;
        a.playerListIgnoreNpcs = true;
        a.playerListAutoHide = true;
        a.playerListMaxRows = 16;
        a.playerListX = 24;
        a.playerListY = 18;

        ModConfig b = a.copy();
        assert b.playerListEnabled : "copy failed for playerListEnabled";
        assert b.playerListAlign == 1 : "copy failed for playerListAlign";
        assert !b.playerListShowDistance : "copy failed for playerListShowDistance";
        assert !b.playerListShowHealth : "copy failed for playerListShowHealth";
        assert b.playerListIgnoreNpcs : "copy failed for playerListIgnoreNpcs";
        assert b.playerListAutoHide : "copy failed for playerListAutoHide";
        assert b.playerListMaxRows == 16 : "copy failed for playerListMaxRows";
        assert b.playerListX == 24 && b.playerListY == 18 : "copy failed for playerList coords";
    }
}
