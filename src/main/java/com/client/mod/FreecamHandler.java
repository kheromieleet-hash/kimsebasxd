package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Meteor-style smooth Creative-Fly virtual camera + camera-aimed reach-checked silent interaction helpers.
 */
public final class FreecamHandler {
    public static final double CREATIVE_FLY_BASE_SPEED = 0.50;
    public static final double SPRINT_MULTIPLIER = 1.60;
    public static final double FLY_ACCEL = 0.35;
    public static final double FLY_DAMPING = 0.65;
    public static final float SCROLL_STEP = 0.10f;
    public static final float MIN_SPEED = 0.2f;
    public static final float MAX_SPEED = 5.0f;
    private static final double VELOCITY_EPSILON = 1.0e-3;
    private static final double MAX_CAMERA_RAY_RANGE = 16.0;

    private static boolean active = false;
    private static Vec3 pos = Vec3.ZERO;
    private static Vec3 prevPos = Vec3.ZERO;
    private static Vec3 velocity = Vec3.ZERO;
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

    public static Vec3 getViewVector() {
        return Vec3.directionFromRotation(pitch, yaw);
    }

    public static boolean shouldAllowWorldInteraction(boolean freecamActive, boolean allowInteract) {
        return !freecamActive || allowInteract;
    }

    public static Vec3 stepVelocity(
            Vec3 currentVel,
            double forward,
            double strafe,
            double vertical,
            float yawDeg,
            float speedSetting,
            boolean sprint
    ) {
        double len = Math.hypot(forward, strafe);
        double normFwd = len > 1.0 ? forward / len : forward;
        double normStrafe = len > 1.0 ? strafe / len : strafe;

        double maxSpeed = Mth.clamp(speedSetting, MIN_SPEED, MAX_SPEED)
                * CREATIVE_FLY_BASE_SPEED
                * (sprint ? SPRINT_MULTIPLIER : 1.0);
        double radYaw = Math.toRadians(yawDeg);
        double sinYaw = Math.sin(radYaw);
        double cosYaw = Math.cos(radYaw);

        double targetVx = (-sinYaw * normFwd - cosYaw * normStrafe) * maxSpeed;
        double targetVz = (cosYaw * normFwd - sinYaw * normStrafe) * maxSpeed;
        double targetVy = Mth.clamp(vertical, -1.0, 1.0) * maxSpeed * 0.85;

        boolean hasHoriz = Math.abs(normFwd) > 1.0e-4 || Math.abs(normStrafe) > 1.0e-4;
        boolean hasVert = Math.abs(vertical) > 1.0e-4;

        double nextVx = hasHoriz ? Mth.lerp(FLY_ACCEL, currentVel.x, targetVx) : currentVel.x * FLY_DAMPING;
        double nextVz = hasHoriz ? Mth.lerp(FLY_ACCEL, currentVel.z, targetVz) : currentVel.z * FLY_DAMPING;
        double nextVy = hasVert ? Mth.lerp(FLY_ACCEL, currentVel.y, targetVy) : currentVel.y * FLY_DAMPING;

        if (!hasHoriz && Math.hypot(nextVx, nextVz) < VELOCITY_EPSILON) {
            nextVx = 0.0;
            nextVz = 0.0;
        }
        if (!hasVert && Math.abs(nextVy) < VELOCITY_EPSILON) {
            nextVy = 0.0;
        }
        return new Vec3(nextVx, nextVy, nextVz);
    }

    public static float stepScrollSpeed(float currentSpeed, double verticalDelta) {
        if (verticalDelta == 0.0) {
            return Mth.clamp(currentSpeed, MIN_SPEED, MAX_SPEED);
        }
        float step = verticalDelta > 0.0 ? SCROLL_STEP : -SCROLL_STEP;
        float rounded = Math.round((currentSpeed + step) * 20.0f) / 20.0f;
        return Mth.clamp(rounded, MIN_SPEED, MAX_SPEED);
    }

    public static float[] computeSilentLookAngles(Vec3 eyePos, Vec3 targetPos) {
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double horiz = Math.hypot(dx, dz);
        float targetYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(1.0e-6, horiz)));
        return new float[]{targetYaw, Mth.clamp(targetPitch, -90.0f, 90.0f)};
    }

    public static HitResult raycastFromCamera(LocalPlayer player, Level level, float partialTick) {
        Vec3 camPos = getInterpolatedPos(partialTick);
        Vec3 viewVec = getViewVector();
        Vec3 playerEye = player.getEyePosition(partialTick);

        double baseRange = Math.max(player.blockInteractionRange(), player.entityInteractionRange());
        double rayDist = Math.min(MAX_CAMERA_RAY_RANGE, baseRange + camPos.distanceTo(playerEye) + 1.0);
        Vec3 rayEnd = camPos.add(viewVec.scale(rayDist));

        BlockHitResult blockHit = level.clip(new ClipContext(
                camPos, rayEnd, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player
        ));
        double blockDistSqr = blockHit.getType() != HitResult.Type.MISS
                ? camPos.distanceToSqr(blockHit.getLocation())
                : rayDist * rayDist;

        ModConfig cfg = ModConfig.getInstance();
        boolean ignoreEntities = cfg.noMiningTrace
                && (!cfg.noMiningTraceToolsOnly || player.getMainHandItem().has(DataComponents.TOOL));
        if (!ignoreEntities) {
            AABB searchBox = new AABB(camPos, rayEnd).inflate(1.0);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    player, camPos, rayEnd, searchBox, EntitySelector.CAN_BE_PICKED, blockDistSqr
            );
            if (entityHit != null && player.isWithinEntityInteractionRange(entityHit.getEntity(), 0.0)) {
                return entityHit;
            }
        }

        if (blockHit.getType() == HitResult.Type.BLOCK
                && player.isWithinBlockInteractionRange(blockHit.getBlockPos(), 0.0)) {
            return blockHit;
        }
        Direction missDir = Direction.getApproximateNearest(viewVec.x, viewVec.y, viewVec.z);
        return BlockHitResult.miss(rayEnd, missDir, BlockPos.containing(rayEnd));
    }

    public static void sendSilentInteractRotationIfEnabled(LocalPlayer player, HitResult hitResult) {
        if (player == null || player.connection == null || hitResult == null || hitResult.getType() == HitResult.Type.MISS) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (!isActive() || !cfg.freecamAllowInteract || !cfg.freecamRotate) {
            return;
        }
        float[] angles = computeSilentLookAngles(player.getEyePosition(1.0f), hitResult.getLocation());
        player.connection.send(new ServerboundMovePlayerPacket.Rot(
                angles[0],
                angles[1],
                player.onGround(),
                player.horizontalCollision
        ));
    }

    public static void enable(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null) {
            return;
        }
        LocalPlayer player = mc.player;
        Vec3 eye = player.getEyePosition(1.0f);
        pos = eye;
        prevPos = eye;
        velocity = Vec3.ZERO;
        yaw = player.getYRot();
        pitch = player.getXRot();
        lastHealth = player.getHealth();
        savedSmartCull = mc.smartCull;
        mc.smartCull = false;
        active = true;
    }

    private static long pendingConfigSaveDeadlineMs = 0L;

    public static void disable(Minecraft mc) {
        if (active && mc != null) {
            mc.smartCull = savedSmartCull;
        }
        active = false;
        velocity = Vec3.ZERO;
        lastHealth = -1.0f;
        ModConfig cfg = ModConfig.getInstance();
        if (cfg.freecamEnabled) {
            cfg.freecamEnabled = false;
            pendingConfigSaveDeadlineMs = 0L;
            cfg.save();
        } else {
            flushPendingConfigSave(true);
        }
    }

    public static void tick(Minecraft mc) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.freecamEnabled) {
            if (active) {
                disable(mc);
            } else {
                flushPendingConfigSave(true);
            }
            return;
        }
        flushPendingConfigSave(false);

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
            velocity = Vec3.ZERO;
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

        velocity = stepVelocity(velocity, forward, strafe, vertical, yaw, cfg.freecamSpeed, opts.keySprint.isDown());
        pos = pos.add(velocity);
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
        cfg.freecamSpeed = stepScrollSpeed(cfg.freecamSpeed, vertical);
        pendingConfigSaveDeadlineMs = System.currentTimeMillis() + 350L;
        return true;
    }

    private static void flushPendingConfigSave(boolean force) {
        if (pendingConfigSaveDeadlineMs == 0L) {
            return;
        }
        if (force || System.currentTimeMillis() >= pendingConfigSaveDeadlineMs) {
            pendingConfigSaveDeadlineMs = 0L;
            ModConfig cfg = ModConfig.getInstance();
            if (cfg != null) {
                cfg.save();
            }
        }
    }
}
