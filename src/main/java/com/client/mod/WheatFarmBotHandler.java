package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Taunahi-style human-like wheat farm builder:
 * - Serpentine row-by-row path starting from Pos 1
 * - Fluid walk-while-working movement (no jerky stop-and-go)
 * - Ease-out mouse-like camera rotation with natural hit-point variance
 * - Supports Hoe in main hand + Wheat Seeds in offhand or hotbar
 * - Works both on foot and while flying
 */
public final class WheatFarmBotHandler {
    private static final double INTERACT_RANGE = 3.35;
    private static final double PAUSE_BEFORE_OVERSHOOT_HORIZ = 1.15;

    private enum TaskType {
        TILL,
        PLANT
    }

    private record FarmTask(BlockPos pos, TaskType type) {}

    private static boolean holdingMoveKeys = false;
    private static int actionCooldown = 0;
    private static int missingWarnCooldown = 0;
    private static long lastStickRightMs = 0L;
    private static BlockPos jitterForPos = null;
    private static double jitterX = 0.0;
    private static double jitterZ = 0.0;

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

    public static boolean onStickRightClickBlock(LocalPlayer player, BlockPos pos) {
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
        jitterForPos = null;
    }

    public static void tick(Minecraft mc) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.wheatFarmEnabled || mc == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            releaseMovementKeys(mc);
            actionCooldown = 0;
            jitterForPos = null;
            return;
        }
        if (mc.gui != null && mc.gui.screen() != null) {
            releaseMovementKeys(mc);
            return;
        }
        if (!cfg.wheatFarmPos1Set || !cfg.wheatFarmPos2Set) {
            releaseMovementKeys(mc);
            notify(mc, "§c[Sestia] §fSet both corners first with Stick or §e.farm pos1 §f/ §e.farm pos2");
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
        boolean offhandSeeds = !mc.player.getOffhandItem().isEmpty() && mc.player.getOffhandItem().is(Items.WHEAT_SEEDS);
        int hotbarSeedSlot = findHotbarSeeds(mc.player.getInventory());
        boolean hasSeeds = offhandSeeds || hotbarSeedSlot != -1;

        FarmTask task = findNextSerpentineTask(mc, cfg, hoeSlot != -1, hasSeeds);
        if (task == null) {
            releaseMovementKeys(mc);
            if (countRemainingTasks(mc.level, cfg) == 0) {
                notify(mc, "§a[Sestia] §fWheat farm area complete!");
                cfg.wheatFarmEnabled = false;
                cfg.save();
            } else if (missingWarnCooldown == 0) {
                notify(mc, "§e[Sestia] §fWaiting for Hoe in hotbar or Wheat Seeds in offhand/hotbar...");
                missingWarnCooldown = 60;
            }
            return;
        }

        updateBlockJitter(task.pos);
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 targetCenter = new Vec3(task.pos.getX() + 0.5 + jitterX, task.pos.getY() + 0.99, task.pos.getZ() + 0.5 + jitterZ);
        double dist = Math.sqrt(targetCenter.distanceToSqr(eyePos));
        double horizDist = Math.hypot(targetCenter.x - mc.player.getX(), targetCenter.z - mc.player.getZ());

        // 1. Ease-out mouse-like camera rotation toward target block (every tick, even while walking!)
        float[] targetAngles = computeAngles(eyePos, targetCenter);
        float yawDiff = easeRotatePlayer(mc.player, targetAngles[0], targetAngles[1], cfg.wheatFarmTurnSpeed);

        // Auto-enable flight if Fly Mode is active and player can fly
        if (cfg.wheatFarmFly && mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        }

        // 2. Fluid walk/fly-while-working movement
        if (cfg.wheatFarmWalk || cfg.wheatFarmFly) {
            boolean shouldWalk = dist > INTERACT_RANGE || horizDist > PAUSE_BEFORE_OVERSHOOT_HORIZ;
            mc.options.keyUp.setDown(shouldWalk && Math.abs(yawDiff) < 42.0f);
            if (mc.player.getAbilities().flying) {
                double hoverMinY = task.pos.getY() + 1.65;
                double hoverMaxY = task.pos.getY() + 2.35;
                mc.options.keyJump.setDown(mc.player.getY() < hoverMinY);
                mc.options.keyShift.setDown(mc.player.getY() > hoverMaxY);
            } else {
                mc.options.keyJump.setDown(shouldWalk && mc.player.horizontalCollision);
                mc.options.keyShift.setDown(false);
            }
            holdingMoveKeys = true;
        } else {
            releaseMovementKeys(mc);
        }

        // Wait until within interaction reach & aimed closely enough
        if (dist > INTERACT_RANGE) {
            return;
        }
        float pitchDiff = targetAngles[1] - mc.player.getXRot();
        if (Math.abs(yawDiff) > 4.0f || Math.abs(pitchDiff) > 4.0f) {
            return;
        }

        // 3. Select hand & slot (Offhand seeds supported without swapping main hand!)
        InteractionHand useHand;
        if (task.type == TaskType.PLANT && offhandSeeds) {
            useHand = InteractionHand.OFF_HAND;
        } else {
            int requiredSlot = task.type == TaskType.TILL ? hoeSlot : hotbarSeedSlot;
            if (requiredSlot == -1) {
                return;
            }
            if (mc.player.getInventory().getSelectedSlot() != requiredSlot) {
                mc.player.getInventory().setSelectedSlot(requiredSlot);
                actionCooldown = Math.max(actionCooldown, 2);
                return;
            }
            useHand = InteractionHand.MAIN_HAND;
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

        ItemStack held = mc.player.getItemInHand(useHand);
        InteractionResult res = mc.gameMode.useItemOn(mc.player, useHand, hitResult);
        if (res instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            mc.player.swing(useHand, held.getInteractAnimation(), false);
        }

        int baseDelay = Mth.clamp(cfg.wheatFarmDelay, 3, 20);
        int extraTill = (task.type == TaskType.TILL) ? 2 : 0;
        actionCooldown = baseDelay + extraTill + ThreadLocalRandom.current().nextInt(3);
    }

    private static void updateBlockJitter(BlockPos pos) {
        if (!pos.equals(jitterForPos)) {
            jitterForPos = pos;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            jitterX = rnd.nextDouble(-0.09, 0.09);
            jitterZ = rnd.nextDouble(-0.09, 0.09);
        }
    }

    private static float easeRotatePlayer(LocalPlayer player, float targetYaw, float targetPitch, float turnSpeedSetting) {
        float yawDiff = Mth.wrapDegrees(targetYaw - player.getYRot());
        float pitchDiff = Mth.clamp(targetPitch, -89.5f, 89.5f) - player.getXRot();

        // Ease-out curve: fast when far, decelerates smoothly near target like a human hand
        float factor = Mth.clamp(turnSpeedSetting / 42.0f, 0.14f, 0.50f);
        float maxStep = Mth.clamp(turnSpeedSetting, 5.0f, 30.0f);

        float yawStep = Math.abs(yawDiff) < 0.2f ? yawDiff : Mth.clamp(yawDiff * factor, -maxStep, maxStep);
        float pitchStep = Math.abs(pitchDiff) < 0.2f ? pitchDiff : Mth.clamp(pitchDiff * (factor * 0.85f), -maxStep * 0.7f, maxStep * 0.7f);

        // Subtle micro-jitter when actively turning
        if (Math.abs(yawDiff) > 1.5f) {
            yawStep += (float) ThreadLocalRandom.current().nextDouble(-0.18, 0.18);
        }

        player.setYRot(player.getYRot() + yawStep);
        player.setXRot(Mth.clamp(player.getXRot() + pitchStep, -89.5f, 89.5f));
        return Mth.wrapDegrees(targetYaw - player.getYRot());
    }

    private static void releaseMovementKeys(Minecraft mc) {
        if (!holdingMoveKeys || mc == null || mc.options == null) {
            holdingMoveKeys = false;
            return;
        }
        mc.options.keyUp.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
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

    private static List<BlockPos> buildSerpentinePath(ModConfig cfg) {
        int x1 = cfg.wheatFarmX1;
        int y1 = cfg.wheatFarmY1;
        int z1 = cfg.wheatFarmZ1;
        int x2 = cfg.wheatFarmX2;
        int y2 = cfg.wheatFarmY2;
        int z2 = cfg.wheatFarmZ2;

        int stepX = x2 >= x1 ? 1 : -1;
        int stepY = y2 >= y1 ? 1 : -1;
        int stepZ = z2 >= z1 ? 1 : -1;

        int spanX = Math.abs(x2 - x1);
        int spanZ = Math.abs(z2 - z1);

        List<BlockPos> path = new ArrayList<>((spanX + 1) * (spanZ + 1));
        for (int y = y1; y != y2 + stepY; y += stepY) {
            if (spanZ >= spanX) {
                int row = 0;
                for (int x = x1; x != x2 + stepX; x += stepX) {
                    boolean forward = (row % 2 == 0);
                    int zStart = forward ? z1 : z2;
                    int zEnd = forward ? z2 : z1;
                    int zDir = forward ? stepZ : -stepZ;
                    for (int z = zStart; z != zEnd + zDir; z += zDir) {
                        path.add(new BlockPos(x, y, z));
                    }
                    row++;
                }
            } else {
                int row = 0;
                for (int z = z1; z != z2 + stepZ; z += stepZ) {
                    boolean forward = (row % 2 == 0);
                    int xStart = forward ? x1 : x2;
                    int xEnd = forward ? x2 : x1;
                    int xDir = forward ? stepX : -stepX;
                    for (int x = xStart; x != xEnd + xDir; x += xDir) {
                        path.add(new BlockPos(x, y, z));
                    }
                    row++;
                }
            }
        }
        return path;
    }

    private static FarmTask findNextSerpentineTask(Minecraft mc, ModConfig cfg, boolean hasHoe, boolean hasSeeds) {
        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        for (BlockPos pos : buildSerpentinePath(cfg)) {
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
            if (!cfg.wheatFarmWalk) {
                double distSq = new Vec3(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5).distanceToSqr(eyePos);
                if (distSq > INTERACT_RANGE * INTERACT_RANGE) {
                    continue;
                }
            }
            return new FarmTask(pos, type);
        }
        return null;
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
        String lower = trimmedInput.toLowerCase(Locale.ROOT);
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
            notify(mc, "§e.farm pos1 §7[x y z] §f- Set corner 1 (or Left-Click with Stick)");
            notify(mc, "§e.farm pos2 §7[x y z] §f- Set corner 2 (or Right-Click with Stick)");
            notify(mc, "§e.farm set <x1> <y1> <z1> <x2> <y2> <z2> §f- Set both corners");
            notify(mc, "§e.farm start §f/ §e.farm stop §f/ §e.farm status §f/ §e.farm clear");
            return true;
        }

        String sub = parts[1].toLowerCase(Locale.ROOT);
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
                    notify(mc, "§c[Sestia] §fSet both corners first (Left/Right click with Stick).");
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
