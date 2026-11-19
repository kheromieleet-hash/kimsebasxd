package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Meteor-style Simple AutoWalk: holds the configured movement key while enabled
 * and cleanly releases it when disabled, direction changes, panic activates, or leaving world.
 */
public final class AutoWalkHandler {
    private static boolean wasAutoWalking = false;
    private static int lastDirection = -1;

    private AutoWalkHandler() {
    }

    public static void tick(Minecraft client) {
        if (client == null || client.options == null) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        boolean shouldWalk = cfg.autoWalkEnabled
                && !PanicHandler.isPanicActive()
                && client.player != null;

        if (!shouldWalk) {
            releaseIfHeld(client.options);
            return;
        }

        int dir = Math.floorMod(cfg.autoWalkDirection, 4);
        if (wasAutoWalking && lastDirection != dir) {
            setDirectionDown(client.options, lastDirection, false);
        }
        setDirectionDown(client.options, dir, true);
        wasAutoWalking = true;
        lastDirection = dir;
    }

    public static void onLeftWorld(Minecraft client) {
        if (client != null && client.options != null) {
            releaseIfHeld(client.options);
        } else {
            wasAutoWalking = false;
            lastDirection = -1;
        }
    }

    private static void releaseIfHeld(Options options) {
        if (wasAutoWalking) {
            setDirectionDown(options, lastDirection, false);
            wasAutoWalking = false;
            lastDirection = -1;
        }
    }

    private static void setDirectionDown(Options options, int dir, boolean down) {
        switch (dir) {
            case 0 -> options.keyUp.setDown(down);
            case 1 -> options.keyDown.setDown(down);
            case 2 -> options.keyLeft.setDown(down);
            case 3 -> options.keyRight.setDown(down);
            default -> {
            }
        }
    }
}
