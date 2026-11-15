package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Human-like wheat farm builder: walks to blocks inside a user-defined [pos1..pos2] box,
 * smoothly aims the camera, tills dirt/grass with a hoe, and plants wheat seeds.
 */
public final class WheatFarmBotHandler {
    private static final double INTERACT_RANGE = 3.4;

    private enum TaskType {
        TILL,
        PLANT
    }

    private record FarmTask(BlockPos pos, TaskType type) {}

    private static boolean holdingMoveKeys = false;
    private static int actionCooldown = 0;
    private static int missingWarnCooldown = 0;
    private static long lastStickRightMs = 0L;

    private WheatFarmBotHandler() {
    }

    public static boolean onStickLeftClickBlock(BlockPos pos) {
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (cfg == null || !cfg.wheatFarmStickWand || mc == null || mc.player == null || pos == null) {
            return false;
        }
        ItemStack mainHand = mc.player.getMainHandItem();
        if (mainHand.isEmpty() || !mainHand.is(Items.STICK)) {
            return false;
        }
        cfg.wheatFarmX1 = pos.getX();
        cfg.wheatFarmY1 = pos.getY();
        cfg.wheatFarmZ1 = pos.getZ();
        cfg.wheatFarmPos1Set = true;
        cfg.save();
        notify(mc, "§a[Sestia] §fFarm Pos 1 set to §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        return true;
    }

    public static boolean onStickRightClickBlock(net.minecraft.client.player.LocalPlayer player, BlockPos pos) {
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (cfg == null || !cfg.wheatFarmStickWand || player == null || mc == null || pos == null) {
            return false;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.isEmpty() || !mainHand.is(Items.STICK)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now - lastStickRightMs < 150L) {
            return true;
        }
        lastStickRightMs = now;
        cfg.wheatFarmX2 = pos.getX();
        cfg.wheatFarmY2 = pos.getY();
        cfg.wheatFarmZ2 = pos.getZ();
        cfg.wheatFarmPos2Set = true;
        cfg.save();
        String extra = cfg.wheatFarmPos1Set
                ? " §7(" + countRemainingTasks(mc.level, cfg) + " blocks, run §e.farm start§7)"
                : "";
        notify(mc, "§a[Sestia] §fFarm Pos 2 set to §e" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + extra);
        return true;
    }

    public static void onLeftWorld() {
        releaseMovementKeys(Minecraft.getInstance());
        actionCooldown = 0;
        missingWarnCooldown = 0;
    }

    public static void tick(Minecraft mc) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.wheatFarmEnabled || mc == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            releaseMovementKeys(mc);
            actionCooldown = 0;
            return;
        }
        if (mc.gui != null && mc.gui.screen() != null) {
            releaseMovementKeys(mc);
            return;
        }
        if (!cfg.wheatFarmPos1Set || !cfg.wheatFarmPos2Set) {
            releaseMovementKeys(mc);
            notify(mc, "§c[Sestia] §fSet both corners first: §e.farm pos1 §fand §e.farm pos2");
            cfg.wheatFarmEnabled = false;
            cfg.save();
            return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
        }
        if (missingWarnCooldown > 0) {
            missingWarnCooldown--;
        }

        int hoeSlot = findHotbarHoe(mc.player.getInventory());
        int seedSlot = findHotbarSeeds(mc.player.getInventory());

        FarmTask task = findNearestTask(mc, cfg, hoeSlot != -1, seedSlot != -1);
        if (task == null) {
            releaseMovementKeys(mc);
            if (countRemainingTasks(mc.level, cfg) == 0) {
                notify(mc, "§a[Sestia] §fWheat farm area complete!");
                cfg.wheatFarmEnabled = false;
                cfg.save();
            } else if (missingWarnCooldown == 0) {
                notify(mc, "§e[Sestia] §fWaiting for Hoe or Wheat Seeds in hotbar...");
                missingWarnCooldown = 60;
            }
            return;
        }

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 targetCenter = new Vec3(task.pos.getX() + 0.5, task.pos.getY() + 0.99, task.pos.getZ() + 0.5);
        double dist = Math.sqrt(targetCenter.distanceToSqr(eyePos));

        // 1. Walk closer if out of natural human reach
        if (dist > INTERACT_RANGE) {
            if (!cfg.wheatFarmWalk) {
                releaseMovementKeys(mc);
                return;
            }
            steerToward(mc, targetCenter, cfg.wheatFarmTurnSpeed);
            return;
        }

        releaseMovementKeys(mc);

        // 2. Smoothly aim camera at target block top
        float[] targetAngles = computeAngles(eyePos, targetCenter);
        float maxStep = Mth.clamp(cfg.wheatFarmTurnSpeed, 5.0f, 45.0f);
        float yawDiff = Mth.wrapDegrees(targetAngles[0] - mc.player.getYRot());
        float pitchDiff = targetAngles[1] - mc.player.getXRot();

        float nextYaw = mc.player.getYRot() + Mth.clamp(yawDiff, -maxStep, maxStep);
        float nextPitch = Mth.clamp(mc.player.getXRot() + Mth.clamp(pitchDiff, -maxStep, maxStep), -89.9f, 89.9f);
        mc.player.setYRot(nextYaw);
        mc.player.setXRot(nextPitch);

        if (Math.abs(yawDiff) > 4.5f || Math.abs(pitchDiff) > 4.5f) {
            return;
        }

        // 3. Switch hotbar slot to Hoe or Wheat Seeds
        int requiredSlot = task.type == TaskType.TILL ? hoeSlot : seedSlot;
        if (requiredSlot == -1) {
            return;
        }
        if (mc.player.getInventory().getSelectedSlot() != requiredSlot) {
            mc.player.getInventory().setSelectedSlot(requiredSlot);
            actionCooldown = Math.max(actionCooldown, 2);
            return;
        }

        if (actionCooldown > 0) {
            return;
        }

        // 4. Raycast line-of-sight & interact naturally
        BlockHitResult ray = mc.level.clip(new ClipContext(
                eyePos,
                targetCenter,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        BlockHitResult hitResult = (ray.getType() == HitResult.Type.BLOCK && ray.getBlockPos().equals(task.pos))
                ? ray
                : new BlockHitResult(targetCenter, Direction.UP, task.pos, false);

        ItemStack held = mc.player.getMainHandItem();
        InteractionResult res = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        if (res instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            mc.player.swing(InteractionHand.MAIN_HAND, held.getInteractAnimation(), false);
        }

        int baseDelay = Mth.clamp(cfg.wheatFarmDelay, 2, 12);
        actionCooldown = baseDelay + ThreadLocalRandom.current().nextInt(3);
    }

    private static void steerToward(Minecraft mc, Vec3 target, float turnSpeed) {
        if (mc.options == null || mc.player == null) {
            return;
        }
        Vec3 pos = mc.player.position();
        double dx = target.x - pos.x;
        double dz = target.z - pos.z;
        float desiredYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float yawDiff = Mth.wrapDegrees(desiredYaw - mc.player.getYRot());
        float maxStep = Mth.clamp(turnSpeed, 5.0f, 45.0f);
        mc.player.setYRot(mc.player.getYRot() + Mth.clamp(yawDiff, -maxStep, maxStep));

        float desiredPitch = Mth.clamp(mc.player.getXRot() + Mth.clamp(12.0f - mc.player.getXRot(), -6.0f, 6.0f), -89.0f, 89.0f);
        mc.player.setXRot(desiredPitch);

        mc.options.keyUp.setDown(Math.abs(yawDiff) < 55.0f);
        mc.options.keyJump.setDown(mc.player.horizontalCollision);
        holdingMoveKeys = true;
    }

    private static void releaseMovementKeys(Minecraft mc) {
        if (!holdingMoveKeys || mc == null || mc.options == null) {
            holdingMoveKeys = false;
            return;
        }
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        holdingMoveKeys = false;
    }

    private static float[] computeAngles(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0);
        float pitch = (float) (-Math.toDegrees(Math.atan2(dy, horiz)));
        return new float[]{yaw, pitch};
    }

    private static FarmTask findNearestTask(Minecraft mc, ModConfig cfg, boolean hasHoe, boolean hasSeeds) {
        int minX = Math.min(cfg.wheatFarmX1, cfg.wheatFarmX2);
        int maxX = Math.max(cfg.wheatFarmX1, cfg.wheatFarmX2);
        int minY = Math.min(cfg.wheatFarmY1, cfg.wheatFarmY2);
        int maxY = Math.max(cfg.wheatFarmY1, cfg.wheatFarmY2);
        int minZ = Math.min(cfg.wheatFarmZ1, cfg.wheatFarmZ2);
        int maxZ = Math.max(cfg.wheatFarmZ1, cfg.wheatFarmZ2);

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        FarmTask best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    TaskType type = classifyBlock(mc.level, pos);
                    if (type == null) {
                        continue;
                    }
                    if (type == TaskType.TILL && !hasHoe) {
                        continue;
                    }
                    if (type == TaskType.PLANT && !hasSeeds) {
                        continue;
                    }
                    double distSq = new Vec3(x + 0.5, y + 1.0, z + 0.5).distanceToSqr(eyePos);
                    if (!cfg.wheatFarmWalk && distSq > INTERACT_RANGE * INTERACT_RANGE) {
                        continue;
                    }
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = new FarmTask(pos, type);
                    }
                }
            }
        }
        return best;
    }

    public static int countRemainingTasks(Level level, ModConfig cfg) {
        if (level == null || cfg == null || !cfg.wheatFarmPos1Set || !cfg.wheatFarmPos2Set) {
            return 0;
        }
        int minX = Math.min(cfg.wheatFarmX1, cfg.wheatFarmX2);
        int maxX = Math.max(cfg.wheatFarmX1, cfg.wheatFarmX2);
        int minY = Math.min(cfg.wheatFarmY1, cfg.wheatFarmY2);
        int maxY = Math.max(cfg.wheatFarmY1, cfg.wheatFarmY2);
        int minZ = Math.min(cfg.wheatFarmZ1, cfg.wheatFarmZ2);
        int maxZ = Math.max(cfg.wheatFarmZ1, cfg.wheatFarmZ2);

        int count = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (classifyBlock(level, new BlockPos(x, y, z)) != null) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static TaskType classifyBlock(Level level, BlockPos pos) {
        if (!level.getBlockState(pos.above()).isAir()) {
            return null;
        }
        Block block = level.getBlockState(pos).getBlock();
        if (block == Blocks.FARMLAND) {
            return TaskType.PLANT;
        }
        if (block == Blocks.DIRT
                || block == Blocks.GRASS_BLOCK
                || block == Blocks.DIRT_PATH
                || block == Blocks.COARSE_DIRT
                || block == Blocks.ROOTED_DIRT) {
            return TaskType.TILL;
        }
        return null;
    }

    private static int findHotbarHoe(Inventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(ItemTags.HOES)) {
                return i;
            }
        }
        return -1;
    }

    private static int findHotbarSeeds(Inventory inv) {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(Items.WHEAT_SEEDS)) {
                return i;
            }
        }
        return -1;
    }

    public static boolean handleChatCommand(Minecraft mc, String trimmedInput) {
        if (trimmedInput == null) {
            return false;
        }
        String lower = trimmedInput.toLowerCase(java.util.Locale.ROOT);
        if (!lower.equals(".farm") && !lower.startsWith(".farm ")) {
            return false;
        }
        if (mc == null || mc.player == null) {
            return true;
        }
        ModConfig cfg = ModConfig.getInstance();
        String[] parts = trimmedInput.split("\\s+");
        if (parts.length == 1 || parts[1].equalsIgnoreCase("help") || parts[1].equals("?")) {
            notify(mc, "§a[Sestia] §f.farm commands:");
            notify(mc, "§e.farm pos1 §7[x y z] §f- Set corner 1");
            notify(mc, "§e.farm pos2 §7[x y z] §f- Set corner 2");
            notify(mc, "§e.farm set <x1> <y1> <z1> <x2> <y2> <z2> §f- Set both corners");
            notify(mc, "§e.farm start §f/ §e.farm stop §f/ §e.farm status §f/ §e.farm clear");
            return true;
        }

        String sub = parts[1].toLowerCase(java.util.Locale.ROOT);
        switch (sub) {
            case "pos1" -> {
                int[] xyz = parseOptionalXyz(mc, parts, 2);
                if (xyz == null) {
                    notify(mc, "§c[Sestia] §fUsage: .farm pos1 [x y z]");
                    return true;
                }
                cfg.wheatFarmX1 = xyz[0];
                cfg.wheatFarmY1 = xyz[1];
                cfg.wheatFarmZ1 = xyz[2];
                cfg.wheatFarmPos1Set = true;
                cfg.save();
                notify(mc, "§a[Sestia] §fFarm Pos 1 set to §e" + xyz[0] + ", " + xyz[1] + ", " + xyz[2]);
            }
            case "pos2" -> {
                int[] xyz = parseOptionalXyz(mc, parts, 2);
                if (xyz == null) {
                    notify(mc, "§c[Sestia] §fUsage: .farm pos2 [x y z]");
                    return true;
                }
                cfg.wheatFarmX2 = xyz[0];
                cfg.wheatFarmY2 = xyz[1];
                cfg.wheatFarmZ2 = xyz[2];
                cfg.wheatFarmPos2Set = true;
                cfg.save();
                notify(mc, "§a[Sestia] §fFarm Pos 2 set to §e" + xyz[0] + ", " + xyz[1] + ", " + xyz[2]);
            }
            case "set" -> {
                if (parts.length < 8) {
                    notify(mc, "§c[Sestia] §fUsage: .farm set <x1> <y1> <z1> <x2> <y2> <z2>");
                    return true;
                }
                try {
                    cfg.wheatFarmX1 = Integer.parseInt(parts[2]);
                    cfg.wheatFarmY1 = Integer.parseInt(parts[3]);
                    cfg.wheatFarmZ1 = Integer.parseInt(parts[4]);
                    cfg.wheatFarmX2 = Integer.parseInt(parts[5]);
                    cfg.wheatFarmY2 = Integer.parseInt(parts[6]);
                    cfg.wheatFarmZ2 = Integer.parseInt(parts[7]);
                    cfg.wheatFarmPos1Set = true;
                    cfg.wheatFarmPos2Set = true;
                    cfg.save();
                    notify(mc, "§a[Sestia] §fFarm area set: §e("
                            + cfg.wheatFarmX1 + "," + cfg.wheatFarmY1 + "," + cfg.wheatFarmZ1 + ") -> ("
                            + cfg.wheatFarmX2 + "," + cfg.wheatFarmY2 + "," + cfg.wheatFarmZ2 + ")");
                } catch (NumberFormatException e) {
                    notify(mc, "§c[Sestia] §fInvalid coordinates.");
                }
            }
            case "start" -> {
                if (!cfg.wheatFarmPos1Set || !cfg.wheatFarmPos2Set) {
                    notify(mc, "§c[Sestia] §fSet both corners first (.farm pos1 & .farm pos2).");
                    return true;
                }
                cfg.wheatFarmEnabled = true;
                cfg.save();
                notify(mc, "§a[Sestia] §fAuto Wheat Farm §astarted§f (" + countRemainingTasks(mc.level, cfg) + " blocks queued).");
            }
            case "stop" -> {
                cfg.wheatFarmEnabled = false;
                cfg.save();
                releaseMovementKeys(mc);
                notify(mc, "§c[Sestia] §fAuto Wheat Farm stopped.");
            }
            case "clear" -> {
                cfg.wheatFarmEnabled = false;
                cfg.wheatFarmPos1Set = false;
                cfg.wheatFarmPos2Set = false;
                cfg.save();
                releaseMovementKeys(mc);
                notify(mc, "§e[Sestia] §fFarm coordinates cleared.");
            }
            case "status" -> {
                String p1 = cfg.wheatFarmPos1Set ? (cfg.wheatFarmX1 + "," + cfg.wheatFarmY1 + "," + cfg.wheatFarmZ1) : "not set";
                String p2 = cfg.wheatFarmPos2Set ? (cfg.wheatFarmX2 + "," + cfg.wheatFarmY2 + "," + cfg.wheatFarmZ2) : "not set";
                int rem = countRemainingTasks(mc.level, cfg);
                notify(mc, "§a[Sestia] §fFarm: " + (cfg.wheatFarmEnabled ? "§aON" : "§cOFF")
                        + " §7| Pos1: §e" + p1 + " §7| Pos2: §e" + p2 + " §7| Remaining: §b" + rem);
            }
            default -> notify(mc, "§c[Sestia] §fUnknown .farm subcommand. Try §e.farm help");
        }
        return true;
    }

    private static int[] parseOptionalXyz(Minecraft mc, String[] parts, int startIndex) {
        if (parts.length == startIndex) {
            BlockPos below = mc.player.blockPosition().below();
            return new int[]{below.getX(), below.getY(), below.getZ()};
        }
        if (parts.length >= startIndex + 3) {
            try {
                return new int[]{
                        Integer.parseInt(parts[startIndex]),
                        Integer.parseInt(parts[startIndex + 1]),
                        Integer.parseInt(parts[startIndex + 2])
                };
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static void notify(Minecraft mc, String msg) {
        if (mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
        }
    }
}
