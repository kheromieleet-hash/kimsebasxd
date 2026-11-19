package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.EggItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.ExperienceBottleItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SnowballItem;
import net.minecraft.world.item.ThrowablePotionItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Renders Chunk Borders, Light Overlay spawn markers, and Trajectories via MC 26.3 Gizmos.
 */
public final class WorldRenderToolsHandler {

    private static final long LIGHT_SCAN_INTERVAL_MS = 150L;
    private static final int LIGHT_SCAN_VERTICAL_RADIUS = 6;
    private static final int MAX_TRAJECTORY_TICKS = 100;

    private record LightMarker(int x, int y, int z, boolean alwaysDanger) {
    }

    private static final List<LightMarker> CACHED_LIGHT_MARKERS = new ArrayList<>();
    private static long lastLightScanMs = 0L;
    private static BlockPos lastLightCenter = null;
    private static int lastLightRange = -1;
    private static boolean lastLightOnlyAlways = false;

    private WorldRenderToolsHandler() {
    }

    public static void renderAll() {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }

        if (cfg.chunkBorders) {
            renderChunkBorders(mc.level, mc.player, cfg.chunkBordersSubGrid);
        }
        if (cfg.lightOverlay) {
            renderLightOverlay(mc.level, mc.player, cfg.lightOverlayRange, cfg.lightOverlayOnlyAlways);
        } else if (!CACHED_LIGHT_MARKERS.isEmpty()) {
            clearLightCache();
        }
        if (cfg.trajectories) {
            renderTrajectory(mc, mc.level, mc.player, cfg.trajectoriesShowPassive, cfg.trajectoriesBoxOutline);
        }
        BlockEspHandler.render(mc, cfg);
        renderAutoSpawnerEsp(cfg);
        renderSpawnerAutoSellEsp(cfg);
    }

    private static final GizmoStyle SPAWNER_DONE_STYLE =
            GizmoStyle.strokeAndFill(0xFF22C55E, 2.0f, 0x3022C55E);
    private static final GizmoStyle SPAWNER_PENDING_STYLE =
            GizmoStyle.strokeAndFill(0xFFEF4444, 2.0f, 0x28EF4444);

    /**
     * Populates a color-coded countdown name tag above primed TNT and TNT minecart render states.
     */
    public static void applyTntTimerTag(EntityRenderState state, float fuseTicks, double yOffset) {
        float clampedTicks = Math.max(0.0f, fuseTicks);
        float seconds = clampedTicks / 20.0f;
        String color;
        if (seconds > 2.5f) {
            color = "§a";
        } else if (seconds > 1.5f) {
            color = "§e";
        } else if (seconds > 0.75f) {
            color = "§6";
        } else {
            color = "§c§l";
        }
        state.nameTag = Component.literal(color + String.format(Locale.US, "%.2fs", seconds));
        state.nameTagAttachment = new Vec3(0.0, yOffset, 0.0);
    }

    private static void renderSpawnerAutoSellEsp(ModConfig cfg) {
        SpawnerAutoSellModule mod = SpawnerAutoSellModule.getInstance();
        if (mod == null || !mod.isEnabled() || !cfg.spawnerAutoSellRenderEsp || PanicHandler.isPanicActive()) {
            return;
        }
        renderSpawnerBoxesAndPath(mod.getScannedSpawners(), mod.getCompletedSpawners(), mod.getActivePath());
    }

    private static void renderSpawnerBoxesAndPath(List<BlockPos> scanned, Set<BlockPos> done, List<BlockPos> path) {
        for (BlockPos spPos : scanned) {
            Gizmos.cuboid(spPos, 0.005f, done.contains(spPos) ? SPAWNER_DONE_STYLE : SPAWNER_PENDING_STYLE);
        }
        if (path != null && path.size() >= 2) {
            for (int i = 0; i < path.size() - 1; i++) {
                BlockPos a = path.get(i);
                BlockPos b = path.get(i + 1);
                Gizmos.line(
                        new Vec3(a.getX() + 0.5, a.getY() + 0.08, a.getZ() + 0.5),
                        new Vec3(b.getX() + 0.5, b.getY() + 0.08, b.getZ() + 0.5),
                        0xFF06B6D4,
                        2.2f
                );
            }
        }
    }

    private static void renderAutoSpawnerEsp(ModConfig cfg) {
        AutoSpawnerModule spawner = AutoSpawnerModule.getInstance();
        if (spawner == null || !spawner.isEnabled() || !cfg.autoSpawnerRenderEsp || PanicHandler.isPanicActive()) {
            return;
        }
        renderSpawnerBoxesAndPath(spawner.getScannedSpawners(), spawner.getOpenedSpawners(), spawner.getActivePath());
        for (BlockPos fullChest : spawner.getFullSpawnerChests()) {
            Gizmos.cuboid(fullChest, 0.005f, SPAWNER_PENDING_STYLE);
        }
    }

    public static void clearLightCache() {
        CACHED_LIGHT_MARKERS.clear();
        lastLightCenter = null;
        lastLightScanMs = 0L;
    }

    private static void renderChunkBorders(ClientLevel level, LocalPlayer player, boolean subGrid) {
        int px = player.blockPosition().getX();
        int py = player.blockPosition().getY();
        int pz = player.blockPosition().getZ();

        int minX = RenderToolPhysics.chunkMinCoord(px);
        int minZ = RenderToolPhysics.chunkMinCoord(pz);
        int maxX = minX + 16;
        int maxZ = minZ + 16;

        double yLow = Math.max(level.getMinY(), py - 16);
        double yHigh = Math.min(level.getMaxY() + 1, py + 32);

        int cornerColor = 0xFFFF3333;
        int edgeColor = 0xFFFFFF44;
        int gridColor = 0x9938BDF8;

        // Vertical corner lines around current chunk
        Gizmos.line(new Vec3(minX, yLow, minZ), new Vec3(minX, yHigh, minZ), cornerColor, 2.0f);
        Gizmos.line(new Vec3(maxX, yLow, minZ), new Vec3(maxX, yHigh, minZ), cornerColor, 2.0f);
        Gizmos.line(new Vec3(minX, yLow, maxZ), new Vec3(minX, yHigh, maxZ), cornerColor, 2.0f);
        Gizmos.line(new Vec3(maxX, yLow, maxZ), new Vec3(maxX, yHigh, maxZ), cornerColor, 2.0f);

        // Horizontal Y rings every 4 blocks inside local window, highlighting eye/feet band
        for (int y = ((int) yLow / 4) * 4; y <= yHigh; y += 4) {
            int col = (Math.abs(y - py) <= 2) ? 0xFF38BDF8 : edgeColor;
            drawHorizontalRect(minX, minZ, maxX, maxZ, y + 0.01, col, 1.5f);
        }

        if (subGrid) {
            double yFloor = py + 0.02;
            for (int i = 2; i < 16; i += 2) {
                int col = (i % 4 == 0) ? edgeColor : gridColor;
                float w = (i % 4 == 0) ? 1.5f : 1.0f;
                Gizmos.line(new Vec3(minX + i, yFloor, minZ), new Vec3(minX + i, yFloor, maxZ), col, w);
                Gizmos.line(new Vec3(minX, yFloor, minZ + i), new Vec3(maxX, yFloor, minZ + i), col, w);
            }
            for (int y = (int) yLow; y <= yHigh; y += 16) {
                drawHorizontalRect(minX, minZ, maxX, maxZ, y + 0.015, 0xFFFFA500, 2.0f);
            }
        }
    }

    private static void drawHorizontalRect(double minX, double minZ, double maxX, double maxZ, double y, int argb, float width) {
        Gizmos.line(new Vec3(minX, y, minZ), new Vec3(maxX, y, minZ), argb, width);
        Gizmos.line(new Vec3(maxX, y, minZ), new Vec3(maxX, y, maxZ), argb, width);
        Gizmos.line(new Vec3(maxX, y, maxZ), new Vec3(minX, y, maxZ), argb, width);
        Gizmos.line(new Vec3(minX, y, maxZ), new Vec3(minX, y, minZ), argb, width);
    }

    private static void renderLightOverlay(ClientLevel level, LocalPlayer player, int range, boolean onlyAlways) {
        BlockPos center = player.blockPosition();
        int clampedRange = Math.max(8, Math.min(24, range));
        long now = System.currentTimeMillis();
        boolean moved = lastLightCenter == null || !center.equals(lastLightCenter);
        boolean configChanged = lastLightRange != clampedRange || lastLightOnlyAlways != onlyAlways;

        if (configChanged || moved || (now - lastLightScanMs) >= LIGHT_SCAN_INTERVAL_MS) {
            rescanLightOverlay(level, center, clampedRange, onlyAlways);
            lastLightCenter = center;
            lastLightRange = clampedRange;
            lastLightOnlyAlways = onlyAlways;
            lastLightScanMs = now;
        }

        for (LightMarker m : CACHED_LIGHT_MARKERS) {
            double y = m.y() + 0.025;
            int color = m.alwaysDanger() ? 0xE6FF3333 : 0xD9FACC15;
            Gizmos.line(new Vec3(m.x() + 0.15, y, m.z() + 0.15), new Vec3(m.x() + 0.85, y, m.z() + 0.85), color, 2.0f);
            Gizmos.line(new Vec3(m.x() + 0.85, y, m.z() + 0.15), new Vec3(m.x() + 0.15, y, m.z() + 0.85), color, 2.0f);
        }
    }

    private static void rescanLightOverlay(ClientLevel level, BlockPos center, int range, boolean onlyAlways) {
        CACHED_LIGHT_MARKERS.clear();
        int minY = Math.max(level.getMinY() + 1, center.getY() - LIGHT_SCAN_VERTICAL_RADIUS);
        int maxY = Math.min(level.getMaxY() - 2, center.getY() + LIGHT_SCAN_VERTICAL_RADIUS);
        BlockPos.MutableBlockPos floorPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos airPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos headPos = new BlockPos.MutableBlockPos();

        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                int bx = center.getX() + dx;
                int bz = center.getZ() + dz;
                for (int y = minY; y <= maxY; y++) {
                    floorPos.set(bx, y - 1, bz);
                    BlockState floor = level.getBlockState(floorPos);
                    if (floor.isAir() || !floor.isFaceSturdy(level, floorPos, Direction.UP)) {
                        continue;
                    }
                    airPos.set(bx, y, bz);
                    BlockState above = level.getBlockState(airPos);
                    if (!above.getFluidState().isEmpty() || !above.getCollisionShape(level, airPos).isEmpty()) {
                        continue;
                    }
                    headPos.set(bx, y + 1, bz);
                    BlockState head = level.getBlockState(headPos);
                    if (!head.getCollisionShape(level, headPos).isEmpty()) {
                        continue;
                    }

                    int blockLight = level.getBrightness(LightLayer.BLOCK, airPos);
                    int skyLight = level.getBrightness(LightLayer.SKY, airPos);
                    RenderToolPhysics.SpawnLightRisk risk =
                            RenderToolPhysics.classifySpawnLight(blockLight, skyLight, true, onlyAlways);
                    if (risk == RenderToolPhysics.SpawnLightRisk.ALWAYS_DANGER) {
                        CACHED_LIGHT_MARKERS.add(new LightMarker(bx, y, bz, true));
                    } else if (risk == RenderToolPhysics.SpawnLightRisk.NIGHT_ONLY) {
                        CACHED_LIGHT_MARKERS.add(new LightMarker(bx, y, bz, false));
                    }
                }
            }
        }
    }

    private static void renderTrajectory(
            Minecraft mc,
            ClientLevel level,
            LocalPlayer player,
            boolean showPassive,
            boolean boxOutline) {
        ActiveSpec active = resolveHeldProjectile(player, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), showPassive);
        if (active == null) {
            return;
        }

        Vec3 eyePos = player.getEyePosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        Vec3 motion = player.getDeltaMovement();
        RenderToolPhysics.TrajectoryResult result = RenderToolPhysics.simulateTrajectory(
                new RenderToolPhysics.Vec3d(eyePos.x, eyePos.y, eyePos.z),
                player.getXRot(),
                player.getYRot(),
                new RenderToolPhysics.Vec3d(motion.x, motion.y, motion.z),
                player.onGround(),
                active.effectiveSpeed(),
                active.spec(),
                (from, to) -> testSegment(level, player, from, to),
                MAX_TRAJECTORY_TICKS
        );

        List<RenderToolPhysics.Vec3d> pts = result.points();
        if (pts.size() < 2) {
            return;
        }

        boolean entityHit = result.hitKind() == RenderToolPhysics.HitKind.ENTITY;
        int lineColor = entityHit ? 0xFFFF3B3B : 0xFF38BDF8;
        for (int i = 0; i < pts.size() - 1; i++) {
            RenderToolPhysics.Vec3d a = pts.get(i);
            RenderToolPhysics.Vec3d b = pts.get(i + 1);
            Gizmos.line(new Vec3(a.x(), a.y(), a.z()), new Vec3(b.x(), b.y(), b.z()), lineColor, 2.2f);
        }

        if (boxOutline && result.hitKind() != RenderToolPhysics.HitKind.NONE) {
            if (entityHit) {
                RenderToolPhysics.Vec3d p = result.hitPoint();
                AABB targetBox = new AABB(p.x() - 0.35, p.y() - 0.35, p.z() - 0.35, p.x() + 0.35, p.y() + 0.35, p.z() + 0.35);
                Gizmos.cuboid(targetBox, GizmoStyle.strokeAndFill(0xFFFF3B3B, 2.0f, 0x38FF3333));
            } else {
                BlockPos hitBlock = new BlockPos(result.hitBlockX(), result.hitBlockY(), result.hitBlockZ());
                Gizmos.cuboid(hitBlock, 0.004f, GizmoStyle.strokeAndFill(0xFF34D399, 2.0f, 0x2834D399));
            }
        }
    }

    private record ActiveSpec(RenderToolPhysics.ProjectileSpec spec, float effectiveSpeed) {
    }

    private static ActiveSpec resolveHeldProjectile(LocalPlayer player, float partialTick, boolean showPassive) {
        ItemStack activeUse = player.isUsingItem() ? player.getUseItem() : ItemStack.EMPTY;
        if (!activeUse.isEmpty()) {
            ActiveSpec fromUse = matchItemSpec(player, activeUse, true, partialTick, showPassive);
            if (fromUse != null) {
                return fromUse;
            }
        }
        ItemStack main = player.getItemInHand(InteractionHand.MAIN_HAND);
        ActiveSpec fromMain = matchItemSpec(player, main, false, partialTick, showPassive);
        if (fromMain != null) {
            return fromMain;
        }
        ItemStack off = player.getItemInHand(InteractionHand.OFF_HAND);
        return matchItemSpec(player, off, false, partialTick, showPassive);
    }

    private static ActiveSpec matchItemSpec(
            LocalPlayer player,
            ItemStack stack,
            boolean activelyDrawing,
            float partialTick,
            boolean showPassive) {
        if (stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof BowItem) {
            if (!activelyDrawing) {
                return new ActiveSpec(RenderToolPhysics.SPEC_BOW, 3.0f);
            }
            int chargeTicks = Math.max(1, (int) Math.round(player.getTicksUsingItem(partialTick)));
            float power = RenderToolPhysics.computeBowChargePower(chargeTicks);
            if (power < 0.1f) {
                return null;
            }
            return new ActiveSpec(RenderToolPhysics.SPEC_BOW, power * 3.0f);
        }
        if (item instanceof CrossbowItem && CrossbowItem.isCharged(stack)) {
            ChargedProjectiles charged = stack.get(DataComponents.CHARGED_PROJECTILES);
            if (charged != null && charged.contains(Items.FIREWORK_ROCKET)) {
                return new ActiveSpec(RenderToolPhysics.SPEC_CROSSBOW_ROCKET, RenderToolPhysics.SPEC_CROSSBOW_ROCKET.speed());
            }
            return new ActiveSpec(RenderToolPhysics.SPEC_CROSSBOW_ARROW, RenderToolPhysics.SPEC_CROSSBOW_ARROW.speed());
        }
        if (item instanceof TridentItem) {
            if (EnchantmentHelper.getTridentSpinAttackStrength(stack, player) > 0.0f) {
                return null;
            }
            if (activelyDrawing && player.getTicksUsingItem() < 10) {
                return null;
            }
            return new ActiveSpec(RenderToolPhysics.SPEC_TRIDENT, RenderToolPhysics.SPEC_TRIDENT.speed());
        }
        if (item instanceof EnderpearlItem) {
            return new ActiveSpec(RenderToolPhysics.SPEC_PEARL_OR_SNOWBALL, RenderToolPhysics.SPEC_PEARL_OR_SNOWBALL.speed());
        }
        if (item instanceof ThrowablePotionItem) {
            return new ActiveSpec(RenderToolPhysics.SPEC_POTION, RenderToolPhysics.SPEC_POTION.speed());
        }
        if (showPassive && (item instanceof SnowballItem || item instanceof EggItem)) {
            return new ActiveSpec(RenderToolPhysics.SPEC_PEARL_OR_SNOWBALL, RenderToolPhysics.SPEC_PEARL_OR_SNOWBALL.speed());
        }
        if (showPassive && item instanceof ExperienceBottleItem) {
            return new ActiveSpec(RenderToolPhysics.SPEC_EXP_BOTTLE, RenderToolPhysics.SPEC_EXP_BOTTLE.speed());
        }
        return null;
    }

    private static RenderToolPhysics.SegmentHit testSegment(
            ClientLevel level,
            LocalPlayer shooter,
            RenderToolPhysics.Vec3d from,
            RenderToolPhysics.Vec3d to) {
        Vec3 vFrom = new Vec3(from.x(), from.y(), from.z());
        Vec3 vTo = new Vec3(to.x(), to.y(), to.z());

        BlockHitResult blockHit = level.clip(new ClipContext(
                vFrom, vTo, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));

        Vec3 effectiveEnd = blockHit.getType() == HitResult.Type.BLOCK ? blockHit.getLocation() : vTo;
        AABB sweepBox = new AABB(vFrom, effectiveEnd).inflate(0.5);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                level,
                shooter,
                vFrom,
                effectiveEnd,
                sweepBox,
                e -> EntitySelector.CAN_BE_PICKED.test(e) && e != shooter && !shooter.isPassengerOfSameVehicle(e),
                0.3f
        );

        boolean inWater = level.getFluidState(BlockPos.containing(vFrom)).is(FluidTags.WATER);
        if (entityHit != null) {
            Vec3 loc = entityHit.getLocation();
            return new RenderToolPhysics.SegmentHit(
                    RenderToolPhysics.HitKind.ENTITY,
                    new RenderToolPhysics.Vec3d(loc.x, loc.y, loc.z),
                    0, 0, 0, inWater);
        }
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            Vec3 loc = blockHit.getLocation();
            BlockPos bp = blockHit.getBlockPos();
            return new RenderToolPhysics.SegmentHit(
                    RenderToolPhysics.HitKind.BLOCK,
                    new RenderToolPhysics.Vec3d(loc.x, loc.y, loc.z),
                    bp.getX(), bp.getY(), bp.getZ(), inWater);
        }
        return RenderToolPhysics.SegmentHit.none(inWater);
    }
}
