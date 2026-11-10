package com.client.mod;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Verifies 1-based Minecraft 26.3 mouse button mapping consistency for ClickGUI click handling and binds.
 */
public class MouseButtonMappingTest {

    public static void main(String[] args) {
        assertLeftRightMiddleIdentity();
        assertExtraMouseButtonsExcludePrimaryClicks();
        assertDisplayAndShortBindLabels();
        System.out.println("ALL MouseButtonMappingTest ASSERTIONS PASSED!");
    }

    private static void assertLeftRightMiddleIdentity() {
        assert InputConstants.MOUSE_BUTTON_LEFT == 1 : "Expected MC MOUSE_BUTTON_LEFT == 1";
        assert InputConstants.MOUSE_BUTTON_MIDDLE == 2 : "Expected MC MOUSE_BUTTON_MIDDLE == 2";
        assert InputConstants.MOUSE_BUTTON_RIGHT == 3 : "Expected MC MOUSE_BUTTON_RIGHT == 3";
        assert InputConstants.MOUSE_BUTTON_4 == 4 : "Expected MC MOUSE_BUTTON_4 == 4";
        assert InputConstants.MOUSE_BUTTON_5 == 5 : "Expected MC MOUSE_BUTTON_5 == 5";

        assert MouseButtonMapping.isLeftClick(InputConstants.MOUSE_BUTTON_LEFT);
        assert !MouseButtonMapping.isLeftClick(0);
        assert !MouseButtonMapping.isLeftClick(InputConstants.MOUSE_BUTTON_MIDDLE);
        assert !MouseButtonMapping.isLeftClick(InputConstants.MOUSE_BUTTON_RIGHT);

        assert MouseButtonMapping.isRightClick(InputConstants.MOUSE_BUTTON_RIGHT);
        assert !MouseButtonMapping.isRightClick(1);
        assert !MouseButtonMapping.isRightClick(InputConstants.MOUSE_BUTTON_MIDDLE);

        assert MouseButtonMapping.isMiddleClick(InputConstants.MOUSE_BUTTON_MIDDLE);
        assert !MouseButtonMapping.isMiddleClick(InputConstants.MOUSE_BUTTON_RIGHT);
    }

    private static void assertExtraMouseButtonsExcludePrimaryClicks() {
        assert !MouseButtonMapping.isExtraMouseButton(-1);
        assert !MouseButtonMapping.isExtraMouseButton(0);
        assert !MouseButtonMapping.isExtraMouseButton(InputConstants.MOUSE_BUTTON_LEFT);
        assert MouseButtonMapping.isExtraMouseButton(InputConstants.MOUSE_BUTTON_MIDDLE);
        assert !MouseButtonMapping.isExtraMouseButton(InputConstants.MOUSE_BUTTON_RIGHT);
        assert MouseButtonMapping.isExtraMouseButton(InputConstants.MOUSE_BUTTON_4);
        assert MouseButtonMapping.isExtraMouseButton(InputConstants.MOUSE_BUTTON_5);
        assert MouseButtonMapping.isExtraMouseButton(6);
    }

    private static void assertDisplayAndShortBindLabels() {
        assert "Left Button".equals(MouseButtonMapping.getDisplayName(InputConstants.MOUSE_BUTTON_LEFT));
        assert "Middle Button".equals(MouseButtonMapping.getDisplayName(InputConstants.MOUSE_BUTTON_MIDDLE));
        assert "Right Button".equals(MouseButtonMapping.getDisplayName(InputConstants.MOUSE_BUTTON_RIGHT));
        assert "Button 4".equals(MouseButtonMapping.getDisplayName(InputConstants.MOUSE_BUTTON_4));
        assert "Button 5".equals(MouseButtonMapping.getDisplayName(InputConstants.MOUSE_BUTTON_5));
        assert "Button 6".equals(MouseButtonMapping.getDisplayName(6));

        assert "M1".equals(MouseButtonMapping.getShortName(InputConstants.MOUSE_BUTTON_LEFT));
        assert "M3".equals(MouseButtonMapping.getShortName(InputConstants.MOUSE_BUTTON_MIDDLE));
        assert "M2".equals(MouseButtonMapping.getShortName(InputConstants.MOUSE_BUTTON_RIGHT));
        assert "M4".equals(MouseButtonMapping.getShortName(InputConstants.MOUSE_BUTTON_4));
        assert "M5".equals(MouseButtonMapping.getShortName(InputConstants.MOUSE_BUTTON_5));
        assert "M6".equals(MouseButtonMapping.getShortName(6));
    }
}
