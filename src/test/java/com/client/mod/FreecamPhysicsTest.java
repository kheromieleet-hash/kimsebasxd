package com.client.mod;

import net.minecraft.world.phys.Vec3;

import java.util.List;

public class FreecamPhysicsTest {

    private static final class StubInventory implements ModernModuleSettings.InventoryBridge {
        @Override public boolean isActive() { return false; }
        @Override public void setActive(boolean value) {}
        @Override public boolean isArmor() { return false; }
        @Override public void setArmor(boolean value) {}
        @Override public boolean isPotions() { return false; }
        @Override public void setPotions(boolean value) {}
        @Override public boolean isMini() { return false; }
        @Override public void setMini(boolean value) {}
        @Override public boolean isVert() { return false; }
        @Override public void setVert(boolean value) {}
        @Override public int getBgAlpha() { return 0; }
        @Override public void setBgAlpha(int value) {}
    }

    private static final class RecordingActions implements ModernModuleSettings.Actions {
        int persistCalls = 0;
        final StubInventory inv = new StubInventory();

        @Override public void persist(boolean reloadChunks) { persistCalls++; }
        @Override public void openInventoryMove() {}
        @Override public ModernModuleSettings.InventoryBridge inventory() { return inv; }
    }

    public static void main(String[] args) {
        testConfigAndSettingsControls();
        testCreativeFlySpeedAndAcceleration();
        testDiagonalNormalizationAndDamping();
        testScrollStepAndClamp();
        testSilentLookAnglesAndInteractionGuard();
        System.out.println("ALL FreecamPhysicsTest ASSERTIONS PASSED!");
    }

    private static void testConfigAndSettingsControls() {
        ModConfig cfg = new ModConfig();
        assert Math.abs(cfg.freecamSpeed - 1.0f) < 0.001f;
        assert cfg.freecamRotate : "freecamRotate should default to true";
        assert !cfg.freecamAllowInteract : "freecamAllowInteract should default to false";

        cfg.freecamRotate = false;
        cfg.freecamSpeed = 10.0f;
        cfg.sanitize();
        assert Math.abs(cfg.freecamSpeed - 5.0f) < 0.001f : "Expected sanitize clamp to 5.0f, got " + cfg.freecamSpeed;

        ModConfig copy = new ModConfig();
        copy.copyFrom(cfg);
        assert !copy.freecamRotate : "copyFrom must preserve freecamRotate";

        RecordingActions actions = new RecordingActions();
        List<ModernModuleSettings.Control> controls = ModernModuleSettings.getControls("Freecam", cfg, actions);
        assert controls.size() == 5 : "Expected 5 controls for Freecam, got " + controls.size();
        ModernModuleSettings.ToggleControl rotateToggle = (ModernModuleSettings.ToggleControl) controls.get(4);
        assert !rotateToggle.getter().get();
        rotateToggle.setter().accept(true);
        assert cfg.freecamRotate;
        assert actions.persistCalls == 1;
    }

    private static void testCreativeFlySpeedAndAcceleration() {
        Vec3 vel = Vec3.ZERO;
        // First tick should accelerate gently (not instant full jump)
        vel = FreecamHandler.stepVelocity(vel, 1.0, 0.0, 0.0, 0.0f, 1.0f, false);
        double firstTickSpeed = Math.hypot(vel.x, vel.z);
        assert firstTickSpeed > 0.05 && firstTickSpeed < FreecamHandler.CREATIVE_FLY_BASE_SPEED
                : "Expected smooth initial acceleration < 0.50, got " + firstTickSpeed;

        // Converge to steady vanilla-creative-fly cruise speed (~0.50 blocks/tick at 1.0x)
        for (int i = 0; i < 30; i++) {
            vel = FreecamHandler.stepVelocity(vel, 1.0, 0.0, 0.0, 0.0f, 1.0f, false);
        }
        double steadySpeed = Math.hypot(vel.x, vel.z);
        assert Math.abs(steadySpeed - FreecamHandler.CREATIVE_FLY_BASE_SPEED) < 0.01
                : "Expected steady 1.0x speed ~0.50 b/t, got " + steadySpeed;

        // Higher speed setting increases cruise speed proportionally
        Vec3 fastVel = Vec3.ZERO;
        for (int i = 0; i < 30; i++) {
            fastVel = FreecamHandler.stepVelocity(fastVel, 1.0, 0.0, 0.0, 0.0f, 3.0f, false);
        }
        double fastSpeed = Math.hypot(fastVel.x, fastVel.z);
        assert Math.abs(fastSpeed - 3.0 * FreecamHandler.CREATIVE_FLY_BASE_SPEED) < 0.02
                : "Expected 3.0x speed ~1.50 b/t, got " + fastSpeed;
    }

    private static void testDiagonalNormalizationAndDamping() {
        Vec3 straight = Vec3.ZERO;
        Vec3 diag = Vec3.ZERO;
        for (int i = 0; i < 30; i++) {
            straight = FreecamHandler.stepVelocity(straight, 1.0, 0.0, 0.0, 0.0f, 1.0f, false);
            diag = FreecamHandler.stepVelocity(diag, 1.0, 1.0, 0.0, 0.0f, 1.0f, false);
        }
        double straightMag = Math.hypot(straight.x, straight.z);
        double diagMag = Math.hypot(diag.x, diag.z);
        assert Math.abs(diagMag - straightMag) < 0.01
                : "Diagonal speed (" + diagMag + ") must match straight speed (" + straightMag + ")";

        // Releasing keys must damp velocity smoothly toward zero (no snap, no infinite drift)
        Vec3 coast = FreecamHandler.stepVelocity(straight, 0.0, 0.0, 0.0, 0.0f, 1.0f, false);
        double coastMag = Math.hypot(coast.x, coast.z);
        assert coastMag > 0.05 && coastMag < straightMag : "Expected smooth deceleration, got " + coastMag;
        for (int i = 0; i < 40; i++) {
            coast = FreecamHandler.stepVelocity(coast, 0.0, 0.0, 0.0, 0.0f, 1.0f, false);
        }
        assert coast.length() == 0.0 : "Expected deadzone clamp to 0 after damping, got " + coast;
    }

    private static void testScrollStepAndClamp() {
        float s = 1.0f;
        s = FreecamHandler.stepScrollSpeed(s, 1.0);
        assert Math.abs(s - 1.1f) < 0.001f : "Expected 1.1f after single scroll up, got " + s;
        s = FreecamHandler.stepScrollSpeed(s, -1.0);
        assert Math.abs(s - 1.0f) < 0.001f : "Expected 1.0f after scroll down, got " + s;
        assert Math.abs(FreecamHandler.stepScrollSpeed(0.2f, -1.0) - 0.2f) < 0.001f : "Expected min clamp 0.2f";
        assert Math.abs(FreecamHandler.stepScrollSpeed(5.0f, 1.0) - 5.0f) < 0.001f : "Expected max clamp 5.0f";
    }

    private static void testSilentLookAnglesAndInteractionGuard() {
        float[] angles = FreecamHandler.computeSilentLookAngles(new Vec3(0, 0, 0), new Vec3(0, 0, 5));
        assert Math.abs(angles[0] - 0.0f) < 0.01f && Math.abs(angles[1] - 0.0f) < 0.01f;

        assert !FreecamHandler.shouldAllowWorldInteraction(true, false) : "Allow Interact OFF must block interactions";
        assert FreecamHandler.shouldAllowWorldInteraction(true, true) : "Allow Interact ON must permit interactions";
        assert FreecamHandler.shouldAllowWorldInteraction(false, false) : "Inactive freecam must not block normal interactions";
    }
}
