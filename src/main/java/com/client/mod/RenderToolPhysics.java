package com.client.mod;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure-Java math and rules for Chunk Borders, Light Overlay spawn checks,
 * and Minecraft 26.3 projectile trajectory simulation.
 */
public final class RenderToolPhysics {

    public static final float DEG_TO_RAD = 0.017453292f;
    public static final double EYE_SPAWN_Y_OFFSET = 0.10000000149011612;

    public static final ProjectileSpec SPEC_BOW = new ProjectileSpec(3.0f, 0.05f, 0.0f, 0.99f, 0.6f, false);
    public static final ProjectileSpec SPEC_CROSSBOW_ARROW = new ProjectileSpec(3.15f, 0.05f, 0.0f, 0.99f, 0.6f, false);
    public static final ProjectileSpec SPEC_CROSSBOW_ROCKET = new ProjectileSpec(1.6f, 0.0f, 0.0f, 1.0f, 1.0f, true);
    public static final ProjectileSpec SPEC_TRIDENT = new ProjectileSpec(2.5f, 0.05f, 0.0f, 0.99f, 0.99f, false);
    public static final ProjectileSpec SPEC_PEARL_OR_SNOWBALL = new ProjectileSpec(1.5f, 0.03f, 0.0f, 0.99f, 0.8f, false);
    public static final ProjectileSpec SPEC_POTION = new ProjectileSpec(0.5f, 0.05f, -20.0f, 0.99f, 0.8f, false);
    public static final ProjectileSpec SPEC_EXP_BOTTLE = new ProjectileSpec(0.7f, 0.07f, -20.0f, 0.99f, 0.8f, false);

    private RenderToolPhysics() {
    }

    public record Vec3d(double x, double y, double z) {
        public Vec3d add(double dx, double dy, double dz) {
            return new Vec3d(x + dx, y + dy, z + dz);
        }
    }

    public record ProjectileSpec(
            float speed,
            float gravity,
            float pitchOffsetDeg,
            float airDrag,
            float waterDrag,
            boolean constantSpeed) {
    }

    public enum HitKind {
        NONE,
        BLOCK,
        ENTITY
    }

    public enum SpawnLightRisk {
        SAFE,
        NIGHT_ONLY,
        ALWAYS_DANGER
    }

    public record SegmentHit(HitKind kind, Vec3d hitPoint, int blockX, int blockY, int blockZ, boolean inWater) {
        public static SegmentHit none(boolean inWater) {
            return new SegmentHit(HitKind.NONE, null, 0, 0, 0, inWater);
        }
    }

    public record TrajectoryResult(
            List<Vec3d> points,
            HitKind hitKind,
            Vec3d hitPoint,
            int hitBlockX,
            int hitBlockY,
            int hitBlockZ) {
    }

    @FunctionalInterface
    public interface SegmentCollider {
        SegmentHit testSegment(Vec3d from, Vec3d to);
    }

    /**
     * Computes MC 26.3 BowItem.getPowerForTime(int useTicks).
     */
    public static float computeBowChargePower(int chargeTicks) {
        float f = Math.max(0, chargeTicks) / 20.0f;
        f = (f * f + f * 2.0f) / 3.0f;
        return Math.min(1.0f, f);
    }

    /**
     * Computes normalized initial launch direction matching Projectile.shootFromRotation.
     */
    public static Vec3d computeShootDirection(float xRotDeg, float yRotDeg, float pitchOffsetDeg) {
        double yawRad = yRotDeg * DEG_TO_RAD;
        double pitchRad = xRotDeg * DEG_TO_RAD;
        double pitchOffRad = (xRotDeg + pitchOffsetDeg) * DEG_TO_RAD;

        double dx = -Math.sin(yawRad) * Math.cos(pitchRad);
        double dy = -Math.sin(pitchOffRad);
        double dz = Math.cos(yawRad) * Math.cos(pitchRad);
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-7) {
            return new Vec3d(0.0, 0.0, 0.0);
        }
        return new Vec3d(dx / len, dy / len, dz / len);
    }

    /**
     * Simulates projectile path tick-by-tick up to maxTicks or first collision.
     */
    public static TrajectoryResult simulateTrajectory(
            Vec3d eyePos,
            float xRotDeg,
            float yRotDeg,
            Vec3d shooterVel,
            boolean shooterOnGround,
            float effectiveSpeed,
            ProjectileSpec spec,
            SegmentCollider collider,
            int maxTicks) {
        List<Vec3d> points = new ArrayList<>();
        Vec3d pos = new Vec3d(eyePos.x(), eyePos.y() - EYE_SPAWN_Y_OFFSET, eyePos.z());
        points.add(pos);

        Vec3d dir = computeShootDirection(xRotDeg, yRotDeg, spec.pitchOffsetDeg());
        double vx = dir.x() * effectiveSpeed + (shooterVel != null ? shooterVel.x() : 0.0);
        double vy = dir.y() * effectiveSpeed + ((shooterVel != null && !shooterOnGround) ? shooterVel.y() : 0.0);
        double vz = dir.z() * effectiveSpeed + (shooterVel != null ? shooterVel.z() : 0.0);

        int steps = Math.max(1, Math.min(200, maxTicks));
        for (int i = 0; i < steps; i++) {
            Vec3d next = new Vec3d(pos.x() + vx, pos.y() + vy, pos.z() + vz);
            SegmentHit hit = collider != null ? collider.testSegment(pos, next) : SegmentHit.none(false);
            if (hit != null && hit.kind() != HitKind.NONE && hit.hitPoint() != null) {
                points.add(hit.hitPoint());
                return new TrajectoryResult(
                        points,
                        hit.kind(),
                        hit.hitPoint(),
                        hit.blockX(),
                        hit.blockY(),
                        hit.blockZ());
            }

            points.add(next);
            pos = next;
            if (!spec.constantSpeed()) {
                double drag = (hit != null && hit.inWater()) ? spec.waterDrag() : spec.airDrag();
                vx *= drag;
                vy *= drag;
                vz *= drag;
                vy -= spec.gravity();
            }
        }
        return new TrajectoryResult(points, HitKind.NONE, pos, (int) Math.floor(pos.x()), (int) Math.floor(pos.y()), (int) Math.floor(pos.z()));
    }

    /**
     * Classifies hostile mob spawn risk at a spawnable block top position.
     */
    public static SpawnLightRisk classifySpawnLight(
            int blockLight,
            int skyLight,
            boolean spawnableSurface,
            boolean onlyAlways) {
        if (!spawnableSurface || blockLight > 0) {
            return SpawnLightRisk.SAFE;
        }
        if (skyLight <= 0) {
            return SpawnLightRisk.ALWAYS_DANGER;
        }
        return onlyAlways ? SpawnLightRisk.SAFE : SpawnLightRisk.NIGHT_ONLY;
    }

    /**
     * Computes world block coordinate origin (min X/Z) for the chunk containing (blockX, blockZ).
     */
    public static int chunkMinCoord(int blockCoord) {
        return Math.floorDiv(blockCoord, 16) * 16;
    }
}
