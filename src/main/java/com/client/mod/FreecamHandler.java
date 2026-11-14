package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Meteor-style smooth interpolated virtual camera + LiquidBounce-style state safety & interaction guards.
 */
public final class FreecamHandler {
    private static boolean active = false;
    private static Vec3 pos = Vec3.ZERO;
    private static Vec3 prevPos = Vec3.ZERO;
    private static float yaw = 0.0f;
    private static float pitch = 0.0f;
    private static float lastHealth = -1.0f;
    private static boolean savedSmartCull = true;

    private FreecamHandler() {}

    public static boolean isActive() {
        return active && ModConfig.getInstance().freecamEnabled;
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getPitch() {
        return pitch;
    }

    public static Vec3 getInterpolatedPos(float partialTick) {
        double x = Mth.lerp(partialTick, prevPos.x, pos.x);
        double y = Mth.lerp(partialTick, prevPos.y, pos.y);
        double z = Mth.lerp(partialTick, prevPos.z, pos.z);
        return new Vec3(x, y, z);
    }

    public static void enable(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        LocalPlayer player = mc.player;
        Vec3 eye = player.getEyePosition(1.0f);
        pos = eye;
        prevPos = eye;
        yaw = player.getYRot();
        pitch = player.getXRot();
        lastHealth = player.getHealth();
        savedSmartCull = mc.smartCull;
        mc.smartCull = false;
        active = true;
    }

    public static void disable(Minecraft mc) {
        if (active && mc != null) {
            mc.smartCull = savedSmartCull;
        }
        active = false;
        lastHealth = -1.0f;
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.freecamEnabled) {
            cfg.freecamEnabled = false;
            cfg.save();
        }
    }

    public static void tick(Minecraft mc) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.freecamEnabled) {
            if (active) {
                disable(mc);
            }
            return;
        }

        if (mc == null || mc.player == null || mc.level == null || !mc.player.isAlive()) {
            disable(mc);
            return;
        }

        if (!active) {
            enable(mc);
        }

        if (cfg.freecamToggleOnDamage && lastHealth >= 0.0f && mc.player.getHealth() < lastHealth) {
            disable(mc);
            return;
        }
        lastHealth = mc.player.getHealth();
        mc.smartCull = false;

        prevPos = pos;

        if (mc.gui == null || mc.gui.screen() != null) {
            return;
        }

        Options opts = mc.options;
        double forward = 0.0;
        double strafe = 0.0;
        double vertical = 0.0;

        if (opts.keyUp.isDown()) forward += 1.0;
        if (opts.keyDown.isDown()) forward -= 1.0;
        if (opts.keyLeft.isDown()) strafe -= 1.0;
        if (opts.keyRight.isDown()) strafe += 1.0;
        if (opts.keyJump.isDown()) vertical += 1.0;
        if (opts.keyShift.isDown()) vertical -= 1.0;

        double len = Math.sqrt(forward * forward + strafe * strafe);
        if (len > 1.0) {
            forward /= len;
            strafe /= len;
        }

        double speed = cfg.freecamSpeed * (opts.keySprint.isDown() ? 1.8 : 1.0);
        double radYaw = Math.toRadians(yaw);
        double sinYaw = Math.sin(radYaw);
        double cosYaw = Math.cos(radYaw);

        double dx = (-sinYaw * forward - cosYaw * strafe) * speed;
        double dz = (cosYaw * forward - sinYaw * strafe) * speed;
        double dy = vertical * speed;

        pos = pos.add(dx, dy, dz);
    }

    public static void onMouseTurn(double dx, double dy) {
        if (!isActive()) {
            return;
        }
        yaw += (float) dx * 0.15f;
        pitch = Mth.clamp(pitch + (float) dy * 0.15f, -90.0f, 90.0f);
    }

    public static boolean onMouseScroll(double vertical) {
        if (!isActive() || vertical == 0.0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui == null || mc.gui.screen() != null) {
            return false;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.freecamScrollSpeed) {
            return false;
        }
        float step = vertical > 0 ? 0.25f : -0.25f;
        cfg.freecamSpeed = Mth.clamp(Math.round((cfg.freecamSpeed + step) * 20.0f) / 20.0f, 0.2f, 5.0f);
        cfg.save();
        return true;
    }
}
