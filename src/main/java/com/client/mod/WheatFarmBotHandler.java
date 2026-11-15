package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
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
    private static final double INTERACT_RANGE = 3.85;
    private static final double PAUSE_BEFORE_OVERSHOOT_HORIZ = 1.35;
    private static final double FAILSAFE_PLAYER_RANGE_SQ = 14.0 * 14.0;
    private static final double FAILSAFE_TELEPORT_DIST_SQ = 6.0 * 6.0;

    private enum TaskType {
        TILL,
        PLANT
    }

    private record FarmTask(BlockPos pos, TaskType type) {}

    private static boolean holdingMoveKeys = false;
    private static int actionCooldown = 0;
    private static long lastStickRightMs = 0L;
    private static BlockPos jitterForPos = null;
    private static double jitterX = 0.0;
    private static double jitterZ = 0.0;
    private static Vec3 lastPlayerPos = null;
    private static int alarmTicksRemaining = 0;
    private static int isFlyRetryTicks = 0;
    private static int isFlyAttempts = 0;
    private static int takeoffTicks = 0;
    private static BlockPos aimedAtPos = null;
    private static int aimedTicks = 0;
    private static boolean yawPixelFlip = false;

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
        jitterForPos = null;
        lastPlayerPos = null;
        alarmTicksRemaining = 0;
    }

    public static void onChatMessage(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (cfg == null || !cfg.wheatFarmEnabled || !cfg.wheatFarmFailsafe || mc == null || mc.player == null) {
            return;
        }
        String clean = rawText.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        if (clean.contains("[sestia]")) {
            return;
        }
        String selfName = mc.player.getName().getString().toLowerCase(Locale.ROOT);
        boolean mentioned = !selfName.isEmpty() && clean.contains(selfName);
        boolean checkKeyword = clean.contains("macro")
                || clean.contains("makro")
                || clean.contains("afk check")
                || clean.contains("bot check")
                || clean.contains("burada misin")
                || clean.contains("burada mısın")
                || clean.contains("are you there");
        if (mentioned || checkKeyword) {
            triggerFailsafe(mc, "Sohbette isim/kontrol mesaji algilandi! Bot durduruldu.");
        }
    }

    public static void tick(Minecraft mc) {
        tickAlarmSound(mc);

        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.wheatFarmEnabled || mc == null || mc.player == null || mc.level == null || mc.gameMode == null) {
            releaseMovementKeys(mc);
            actionCooldown = 0;
            jitterForPos = null;
            lastPlayerPos = null;
            isFlyRetryTicks = 0;
            isFlyAttempts = 0;
            takeoffTicks = 0;
            aimedAtPos = null;
            aimedTicks = 0;
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

        if (cfg.wheatFarmFailsafe && checkFailsafe(mc)) {
            return;
        }

        if (actionCooldown > 0) {
            actionCooldown--;
        }

        int hoeSlot = findHotbarHoe(mc.player.getInventory());
        boolean offhandSeeds = !mc.player.getOffhandItem().isEmpty() && mc.player.getOffhandItem().is(Items.WHEAT_SEEDS);
        int hotbarSeedSlot = findHotbarSeeds(mc.player.getInventory());
        boolean hasSeeds = offhandSeeds || hotbarSeedSlot != -1;

        FarmTask task = findNextSerpentineTask(mc, cfg, hoeSlot != -1, hasSeeds);
        if (task == null) {
            releaseMovementKeys(mc);
            lastPlayerPos = null;
            if (countRemainingTasks(mc.level, cfg) == 0) {
                notify(mc, "§a[Sestia] §fWheat farm area complete!");
                if (mc.getSoundManager() != null) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.0f, 1.0f));
                }
                cfg.wheatFarmEnabled = false;
                cfg.save();
            } else {
                triggerFailsafe(mc, "Capa veya Bugday Tohumu eksik! Bot durduruldu.");
            }
            return;
        }

        Vec3 eyePos = mc.player.getEyePosition(1.0f);
        Vec3 targetCenter = new Vec3(task.pos.getX() + 0.5, task.pos.getY() + 0.90, task.pos.getZ() + 0.5);
        double dist = Math.sqrt(targetCenter.distanceToSqr(eyePos));
        double horizDist = Math.hypot(targetCenter.x - mc.player.getX(), targetCenter.z - mc.player.getZ());

        int[] rowDir = computeRowDirection(cfg, task.pos);
        int dirX = rowDir[0];
        int dirZ = rowDir[1];
        double latError = (dirX == 0)
                ? Math.abs((task.pos.getX() + 0.5) - mc.player.getX())
                : Math.abs((task.pos.getZ() + 0.5) - mc.player.getZ());
        double forwardDist = ((task.pos.getX() + 0.5) - mc.player.getX()) * dirX
                + ((task.pos.getZ() + 0.5) - mc.player.getZ()) * dirZ;
        boolean inRowColumn = latError <= 0.65;

        double aheadX = (dirX == 0) ? (task.pos.getX() + 0.5) : (mc.player.getX() + dirX * 4.5);
        double aheadZ = (dirZ == 0) ? (task.pos.getZ() + 0.5) : (mc.player.getZ() + dirZ * 4.5);
        Vec3 aheadCenterline = new Vec3(aheadX, targetCenter.y, aheadZ);

        // 0. Immediately select hoe (or seeds) in hotbar as soon as bot runs
        InteractionHand useHand;
        if (task.type == TaskType.PLANT && offhandSeeds) {
            if (hoeSlot != -1 && mc.player.getInventory().getSelectedSlot() != hoeSlot) {
                mc.player.getInventory().setSelectedSlot(hoeSlot);
                actionCooldown = Math.max(actionCooldown, 2);
                return;
            }
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

        // 1. Lock Yaw straight along the row centerline (4.5 blocks ahead) so player stays centered on the block and never turns left/right
        float[] rawAngles = computeAngles(eyePos, targetCenter);
        float[] aheadAngles = computeAngles(eyePos, aheadCenterline);
        float desiredYaw = inRowColumn ? aheadAngles[0] : rawAngles[0];
        float desiredPitch = rawAngles[1];
        float yawDiff = easeRotatePlayer(mc, mc.player, desiredYaw, desiredPitch, cfg.wheatFarmTurnSpeed);

        // Automatically run /is fly if Fly Mode is active and server hasn't granted flight yet
        if (isFlyRetryTicks > 0) {
            isFlyRetryTicks--;
        }
        if (mc.player.getAbilities().mayfly) {
            isFlyAttempts = 0;
        } else if (cfg.wheatFarmFly && mc.player.connection != null) {
            if (isFlyRetryTicks <= 0 && isFlyAttempts < 3) {
                mc.player.connection.sendCommand("is fly");
                isFlyRetryTicks = 60;
                isFlyAttempts++;
            }
            if (isFlyAttempts == 1 && isFlyRetryTicks > 42) {
                releaseMovementKeys(mc);
                return;
            }
        }

        // Natural double-jump takeoff using pure key inputs (no setDeltaMovement!)
        if (cfg.wheatFarmFly && mc.player.getAbilities().mayfly && !mc.player.getAbilities().flying) {
            takeoffTicks++;
            int phase = takeoffTicks % 8;
            // Tick 1-2: hold jump (jump off ground), Tick 3-4: release jump, Tick 5-7: hold jump again (double-jump fly!)
            boolean pressJump = (phase == 1 || phase == 2 || phase == 5 || phase == 6 || phase == 7);
            mc.options.keyJump.setDown(pressJump);
            mc.options.keyUp.setDown(false);
            mc.options.keyShift.setDown(false);
            holdingMoveKeys = true;
            if (!mc.player.onGround() && phase == 6) {
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
            }
            return;
        } else {
            takeoffTicks = 0;
        }

        // 2. Pure forward movement along the row without turning left/right
        if (cfg.wheatFarmWalk || cfg.wheatFarmFly) {
            boolean needForward = inRowColumn
                    ? (forwardDist > PAUSE_BEFORE_OVERSHOOT_HORIZ)
                    : (dist > INTERACT_RANGE || horizDist > PAUSE_BEFORE_OVERSHOOT_HORIZ);
            boolean needBack = inRowColumn && forwardDist < 0.65;
            if (mc.player.getAbilities().flying) {
                double hoverMinY = task.pos.getY() + 1.35;
                double hoverMaxY = task.pos.getY() + 1.95;
                mc.options.keyJump.setDown(mc.player.getY() < hoverMinY);
                mc.options.keyShift.setDown(mc.player.getY() > hoverMaxY);

                Vec3 dm = mc.player.getDeltaMovement();
                double hSpeed = Math.hypot(dm.x, dm.z);
                boolean tapForward = needForward && Math.abs(yawDiff) < 15.0f && (horizDist > 2.8 || hSpeed < 0.16);
                mc.options.keyUp.setDown(tapForward);
                mc.options.keyDown.setDown(needBack && hSpeed < 0.14);
            } else {
                mc.options.keyUp.setDown(needForward && Math.abs(yawDiff) < 25.0f);
                mc.options.keyDown.setDown(needBack);
                mc.options.keyJump.setDown(needForward && mc.player.horizontalCollision);
                mc.options.keyShift.setDown(false);
            }
            holdingMoveKeys = true;
        } else {
            releaseMovementKeys(mc);
        }

        // Wait until within interaction reach & aimed closely enough
        if (dist > INTERACT_RANGE) {
            aimedAtPos = null;
            aimedTicks = 0;
            return;
        }
        float pitchDiff = desiredPitch - mc.player.getXRot();
        if (Math.abs(yawDiff) > 4.5f || Math.abs(pitchDiff) > 4.5f) {
            aimedAtPos = null;
            aimedTicks = 0;
            return;
        }

        // 4. Raycast strictly along player's real view vector (never send synthetic BlockHitResult)
        Vec3 lookVec = mc.player.getViewVector(1.0f);
        Vec3 reachEnd = eyePos.add(lookVec.scale(INTERACT_RANGE));
        BlockHitResult ray = mc.level.clip(new ClipContext(
                eyePos,
                reachEnd,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                mc.player
        ));
        if (ray.getType() != HitResult.Type.BLOCK || !ray.getBlockPos().equals(task.pos)) {
            aimedAtPos = null;
            aimedTicks = 0;
            return;
        }

        // Wait 1 full tick after crosshair lands on the block so PosRot packet reaches server BEFORE UseItemOn
        if (!task.pos.equals(aimedAtPos)) {
            aimedAtPos = task.pos;
            aimedTicks = 1;
            return;
        }
        aimedTicks++;
        if (aimedTicks < 2 || actionCooldown > 0) {
            return;
        }

        ItemStack held = mc.player.getItemInHand(useHand);
        InteractionResult res = mc.gameMode.useItemOn(mc.player, useHand, ray);
        if (res instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.PREDICTED) {
            mc.player.swing(useHand, held.getInteractAnimation(), false);
        }

        int baseDelay = Mth.clamp(cfg.wheatFarmDelay, 3, 20);
        actionCooldown = baseDelay + ThreadLocalRandom.current().nextInt(3);
    }

    private static void updateBlockJitter(BlockPos pos) {
        if (!pos.equals(jitterForPos)) {
            jitterForPos = pos;
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            jitterX = rnd.nextDouble(-0.09, 0.09);
            jitterZ = rnd.nextDouble(-0.09, 0.09);
        }
    }

    private static float easeRotatePlayer(Minecraft mc, LocalPlayer player, float targetYaw, float targetPitch, float turnSpeedSetting) {
        float yawDiff = Mth.wrapDegrees(targetYaw - player.getYRot());
        float pitchDiff = Mth.clamp(targetPitch, -89.5f, 89.5f) - player.getXRot();

        // Ease-out curve: fast when far, decelerates smoothly near target like a human hand
        float factor = Mth.clamp(turnSpeedSetting / 38.0f, 0.16f, 0.50f);
        float maxStep = Mth.clamp(turnSpeedSetting, 5.0f, 26.0f);

        float yawStep = Math.abs(yawDiff) < 0.15f ? 0.0f : Mth.clamp(yawDiff * factor, -maxStep, maxStep);
        float pitchStep = Math.abs(pitchDiff) < 0.15f ? 0.0f : Mth.clamp(pitchDiff * (factor * 0.85f), -maxStep * 0.75f, maxStep * 0.75f);

        // Quantize to real Minecraft mouse sensitivity GCD step
        double sens = (mc != null && mc.options != null) ? mc.options.sensitivity().get() : 0.5;
        double d = sens * 0.6 + 0.2;
        float gcd = (float) (d * d * d * 1.2);
        if (gcd > 1.0e-4f) {
            int yawPixels = Math.round(yawStep / gcd);
            int pitchPixels = Math.round(pitchStep / gcd);
            // Never rotate pitch with exact 0 horizontal mouse pixels (prevents robotic 0-deltaYaw detection while keeping net yaw drift 0)
            if (pitchPixels != 0 && yawPixels == 0) {
                yawPixelFlip = !yawPixelFlip;
                yawPixels = yawPixelFlip ? 1 : -1;
            }
            yawStep = yawPixels * gcd;
            pitchStep = pitchPixels * gcd;
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
        mc.options.keyDown.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
        holdingMoveKeys = false;
    }

    private static int[] computeRowDirection(ModConfig cfg, BlockPos pos) {
        int stepX = cfg.wheatFarmX2 >= cfg.wheatFarmX1 ? 1 : -1;
        int stepZ = cfg.wheatFarmZ2 >= cfg.wheatFarmZ1 ? 1 : -1;
        int spanX = Math.abs(cfg.wheatFarmX2 - cfg.wheatFarmX1);
        int spanZ = Math.abs(cfg.wheatFarmZ2 - cfg.wheatFarmZ1);
        if (spanZ >= spanX) {
            int row = Math.abs(pos.getX() - cfg.wheatFarmX1);
            int zDir = (row % 2 == 0) ? stepZ : -stepZ;
            return new int[]{0, zDir};
        } else {
            int row = Math.abs(pos.getZ() - cfg.wheatFarmZ1);
            int xDir = (row % 2 == 0) ? stepX : -stepX;
            return new int[]{xDir, 0};
        }
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
            if (!cfg.wheatFarmWalk && !cfg.wheatFarmFly) {
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
        boolean isFriendCmd = lower.equals(".friend") || lower.startsWith(".friend ")
                || lower.equals(".frined") || lower.startsWith(".frined ")
                || lower.equals(".frinmed") || lower.startsWith(".frinmed ");
        if (isFriendCmd) {
            if (mc == null || mc.player == null) {
                return true;
            }
            return handleFriendCommand(mc, trimmedInput);
        }
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
                cfg.wheatFarmFly = true;
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
                int wl = cfg.wheatFarmWhitelist != null ? cfg.wheatFarmWhitelist.size() : 0;
                notify(mc, "§a[Sestia] §fFarm: " + (cfg.wheatFarmEnabled ? "§aON" : "§cOFF")
                        + " §7| Pos1: §e" + p1 + " §7| Pos2: §e" + p2 + " §7| Remaining: §b" + rem
                        + " §7| Friends: §e" + wl);
            }
            default -> notify(mc, "§c[Sestia] §fUnknown .farm subcommand. Try §e.farm help");
        }
        return true;
    }

    private static boolean handleFriendCommand(Minecraft mc, String trimmedInput) {
        ModConfig cfg = ModConfig.getInstance();
        String[] parts = trimmedInput.split("\\s+");
        if (parts.length == 1 || parts[1].equalsIgnoreCase("help") || parts[1].equals("?")) {
            notify(mc, "§a[Sestia] §f.friend komutlari:");
            notify(mc, "§e.friend <oyuncu> §f- Oyuncuyu friend/whitelist'e ekle");
            notify(mc, "§e.friend list §f- Friend listesini goster");
            notify(mc, "§e.friend remove <oyuncu> §f- Oyuncuyu listeden cikar");
            return true;
        }

        String arg1 = parts[1].trim();
        String arg1Lower = arg1.toLowerCase(Locale.ROOT);
        if (arg1Lower.equals("list") || arg1Lower.equals("liste")) {
            if (cfg.wheatFarmWhitelist == null || cfg.wheatFarmWhitelist.isEmpty()) {
                notify(mc, "§e[Sestia] §fFriend listesi bos. Eklemek icin: §e.friend <oyuncu>");
            } else {
                notify(mc, "§a[Sestia] §fFriend Listesi (§e" + cfg.wheatFarmWhitelist.size() + "§f): §b"
                        + String.join("§7, §b", cfg.wheatFarmWhitelist));
            }
            return true;
        }
        if (arg1Lower.equals("remove") || arg1Lower.equals("del") || arg1Lower.equals("delete") || arg1Lower.equals("rm")) {
            if (parts.length < 3) {
                notify(mc, "§c[Sestia] §fKullanim: §e.friend remove <oyuncu>");
                return true;
            }
            String name = parts[2].trim();
            boolean removed = cfg.removeWheatFarmWhitelist(name);
            cfg.save();
            notify(mc, removed
                    ? "§e[Sestia] §e" + name + " §ffriend listesinden cikarildi."
                    : "§c[Sestia] §e" + name + " §flistede bulunamadi.");
            return true;
        }

        String name = (arg1Lower.equals("add") && parts.length >= 3) ? parts[2].trim() : arg1;
        boolean added = cfg.addWheatFarmWhitelist(name);
        cfg.save();
        notify(mc, added
                ? "§a[Sestia] §e" + name + " §ffriend listesine eklendi (failsafe tetiklenmez)."
                : "§e[Sestia] §e" + name + " §fzaten friend listesinde.");
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

    private static boolean checkFailsafe(Minecraft mc) {
        Vec3 currentPos = mc.player.position();
        if (lastPlayerPos != null && currentPos.distanceToSqr(lastPlayerPos) > FAILSAFE_TELEPORT_DIST_SQ) {
            triggerFailsafe(mc, "Ani isinlanma / konum degisikligi algilandi! Bot durduruldu.");
            return true;
        }
        lastPlayerPos = currentPos;

        if (mc.getConnection() != null) {
            ModConfig cfg = ModConfig.getInstance();
            for (Player other : mc.level.players()) {
                if (other == mc.player || other.isSpectator()) {
                    continue;
                }
                if (mc.getConnection().getPlayerInfo(other.getUUID()) == null) {
                    continue;
                }
                if (cfg != null && cfg.isWheatFarmWhitelisted(other.getName().getString())) {
                    continue;
                }
                if (other.distanceToSqr(mc.player) <= FAILSAFE_PLAYER_RANGE_SQ) {
                    triggerFailsafe(mc, "Yakinda oyuncu algilandi (" + other.getName().getString() + ")! Bot durduruldu.");
                    return true;
                }
            }
        }
        return false;
    }

    private static void triggerFailsafe(Minecraft mc, String reason) {
        ModConfig cfg = ModConfig.getInstance();
        cfg.wheatFarmEnabled = false;
        cfg.save();
        releaseMovementKeys(mc);
        lastPlayerPos = null;
        alarmTicksRemaining = 40;
        notify(mc, "§c§l[Sestia] [FAILSAFE] §f" + reason);
    }

    private static void tickAlarmSound(Minecraft mc) {
        if (alarmTicksRemaining <= 0) {
            return;
        }
        if (mc != null && mc.getSoundManager() != null && alarmTicksRemaining % 4 == 0) {
            float pitch = (alarmTicksRemaining % 8 == 0) ? 1.55f : 1.15f;
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, 1.0f));
        }
        alarmTicksRemaining--;
    }

    private static void notify(Minecraft mc, String msg) {
        if (mc != null && mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
            mc.gui.hud.getChat().addClientSystemMessage(Component.literal(msg));
        }
    }
}
