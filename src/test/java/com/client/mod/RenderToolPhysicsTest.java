package com.client.mod;

/**
 * Pure-Java verification of Chunk Borders coordinate math, Light Overlay spawn classification,
 * and Minecraft 26.3 projectile trajectory physics.
 */
public class RenderToolPhysicsTest {

    public static void main(String[] args) {
        testBowPowerAndDirectionMath();
        testTrajectorySimulationAndCollisions();
        testSpawnLightClassification();
        testChunkBoundaryNegativeAndPositiveCoords();
        System.out.println("ALL RenderToolPhysicsTest ASSERTIONS PASSED!");
    }

    private static void testBowPowerAndDirectionMath() {
        assert RenderToolPhysics.computeBowChargePower(0) == 0.0f;
        assert Math.abs(RenderToolPhysics.computeBowChargePower(20) - 1.0f) < 0.0001f;
        assert RenderToolPhysics.computeBowChargePower(80) == 1.0f : "Bow power must clamp to 1.0";

        RenderToolPhysics.Vec3d straightSouth = RenderToolPhysics.computeShootDirection(0.0f, 0.0f, 0.0f);
        assert Math.abs(straightSouth.x()) < 1e-6 && Math.abs(straightSouth.y()) < 1e-6
                && Math.abs(straightSouth.z() - 1.0) < 1e-6;

        RenderToolPhysics.Vec3d potionLift = RenderToolPhysics.computeShootDirection(0.0f, 0.0f, -20.0f);
        assert potionLift.y() > 0.30 : "Splash/linger potion -20deg pitch offset must throw upward, got dy=" + potionLift.y();
    }

    private static void testTrajectorySimulationAndCollisions() {
        // Horizontal bow shot drops under gravity and stops at floor plane y=64
        RenderToolPhysics.TrajectoryResult floorHit = RenderToolPhysics.simulateTrajectory(
                new RenderToolPhysics.Vec3d(0.5, 70.0, 0.5),
                0.0f, 0.0f,
                new RenderToolPhysics.Vec3d(0.0, 0.0, 0.0),
                true,
                RenderToolPhysics.SPEC_BOW.speed(),
                RenderToolPhysics.SPEC_BOW,
                (from, to) -> {
                    if (to.y() <= 64.0) {
                        return new RenderToolPhysics.SegmentHit(
                                RenderToolPhysics.HitKind.BLOCK,
                                new RenderToolPhysics.Vec3d(to.x(), 64.0, to.z()),
                                (int) Math.floor(to.x()), 63, (int) Math.floor(to.z()), false);
                    }
                    return RenderToolPhysics.SegmentHit.none(false);
                },
                100
        );
        assert floorHit.hitKind() == RenderToolPhysics.HitKind.BLOCK;
        assert floorHit.hitPoint().y() == 64.0;
        assert floorHit.points().size() > 3;

        // Crossbow rocket flies straight horizontally when gravity == 0
        RenderToolPhysics.TrajectoryResult rocketPath = RenderToolPhysics.simulateTrajectory(
                new RenderToolPhysics.Vec3d(0.5, 70.0, 0.5),
                0.0f, 0.0f,
                null,
                true,
                RenderToolPhysics.SPEC_CROSSBOW_ROCKET.speed(),
                RenderToolPhysics.SPEC_CROSSBOW_ROCKET,
                null,
                10
        );
        RenderToolPhysics.Vec3d last = rocketPath.points().get(rocketPath.points().size() - 1);
        assert Math.abs(last.y() - (70.0 - RenderToolPhysics.EYE_SPAWN_Y_OFFSET)) < 1e-5
                : "Rocket path must not drop vertically, got y=" + last.y();

        // Entity hit terminates early
        RenderToolPhysics.TrajectoryResult entityHit = RenderToolPhysics.simulateTrajectory(
                new RenderToolPhysics.Vec3d(0.0, 65.0, 0.0),
                0.0f, 0.0f,
                null,
                true,
                1.5f,
                RenderToolPhysics.SPEC_PEARL_OR_SNOWBALL,
                (from, to) -> to.z() >= 4.0
                        ? new RenderToolPhysics.SegmentHit(
                        RenderToolPhysics.HitKind.ENTITY,
                        new RenderToolPhysics.Vec3d(0.0, to.y(), 4.2),
                        0, 0, 0, false)
                        : RenderToolPhysics.SegmentHit.none(false),
                50
        );
        assert entityHit.hitKind() == RenderToolPhysics.HitKind.ENTITY;
        assert Math.abs(entityHit.hitPoint().z() - 4.2) < 1e-6;
    }

    private static void testSpawnLightClassification() {
        assert RenderToolPhysics.classifySpawnLight(0, 0, true, false) == RenderToolPhysics.SpawnLightRisk.ALWAYS_DANGER;
        assert RenderToolPhysics.classifySpawnLight(0, 0, true, true) == RenderToolPhysics.SpawnLightRisk.ALWAYS_DANGER;
        assert RenderToolPhysics.classifySpawnLight(0, 12, true, false) == RenderToolPhysics.SpawnLightRisk.NIGHT_ONLY;
        assert RenderToolPhysics.classifySpawnLight(0, 12, true, true) == RenderToolPhysics.SpawnLightRisk.SAFE;
        assert RenderToolPhysics.classifySpawnLight(1, 0, true, false) == RenderToolPhysics.SpawnLightRisk.SAFE;
        assert RenderToolPhysics.classifySpawnLight(0, 0, false, false) == RenderToolPhysics.SpawnLightRisk.SAFE;
    }

    private static void testChunkBoundaryNegativeAndPositiveCoords() {
        assert RenderToolPhysics.chunkMinCoord(0) == 0;
        assert RenderToolPhysics.chunkMinCoord(15) == 0;
        assert RenderToolPhysics.chunkMinCoord(16) == 16;
        assert RenderToolPhysics.chunkMinCoord(-1) == -16 : "Negative coord -1 must align to -16";
        assert RenderToolPhysics.chunkMinCoord(-16) == -16;
        assert RenderToolPhysics.chunkMinCoord(-17) == -32;
    }
}
