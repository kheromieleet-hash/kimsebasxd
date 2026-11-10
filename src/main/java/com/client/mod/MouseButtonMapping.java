package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Shared mouse button helper aligned with Minecraft 26.3's 1-based {@link InputConstants}
 * ({@code MOUSE_BUTTON_LEFT = 1}, {@code MOUSE_BUTTON_MIDDLE = 2},
 * {@code MOUSE_BUTTON_RIGHT = 3}, {@code MOUSE_BUTTON_4 = 4}, {@code MOUSE_BUTTON_5 = 5}).
 */
public final class MouseButtonMapping {

    public static final int MOUSE_LEFT = InputConstants.MOUSE_BUTTON_LEFT;
    public static final int MOUSE_MIDDLE = InputConstants.MOUSE_BUTTON_MIDDLE;
    public static final int MOUSE_RIGHT = InputConstants.MOUSE_BUTTON_RIGHT;
    public static final int MOUSE_BUTTON_4 = InputConstants.MOUSE_BUTTON_4;
    public static final int MOUSE_BUTTON_5 = InputConstants.MOUSE_BUTTON_5;

    private MouseButtonMapping() {
    }

    public static boolean isLeftClick(int button) {
        return button == MOUSE_LEFT;
    }

    public static boolean isRightClick(int button) {
        return button == MOUSE_RIGHT;
    }

    public static boolean isMiddleClick(int button) {
        return button == MOUSE_MIDDLE;
    }

    /** Returns true for middle and extra side buttons safe to bind without overriding LMB/RMB. */
    public static boolean isExtraMouseButton(int button) {
        return button == MOUSE_MIDDLE || button >= MOUSE_BUTTON_4;
    }

    public static String getDisplayName(int button) {
        return switch (button) {
            case MOUSE_LEFT -> "Left Button";
            case MOUSE_MIDDLE -> "Middle Button";
            case MOUSE_RIGHT -> "Right Button";
            case MOUSE_BUTTON_4 -> "Button 4";
            case MOUSE_BUTTON_5 -> "Button 5";
            default -> "Button " + button;
        };
    }

    public static String getShortName(int button) {
        return switch (button) {
            case MOUSE_LEFT -> "M1";
            case MOUSE_RIGHT -> "M2";
            case MOUSE_MIDDLE -> "M3";
            case MOUSE_BUTTON_4 -> "M4";
            case MOUSE_BUTTON_5 -> "M5";
            default -> "M" + button;
        };
    }
}
