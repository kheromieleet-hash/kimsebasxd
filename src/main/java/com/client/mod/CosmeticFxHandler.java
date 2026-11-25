package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Fixed-capacity zero-allocation world VFX engine for:
 * - Sprint / Jump / Elytra Trails (Step Ripples, Leaf Ribbon, Neon Sparks, Elytra Contrail)
 * - Client-side Combat Visual FX (Hit Burst, Kill Finisher: Soul Beacon / Emerald Nova / Voxel Dissolve)
 * - 3D Totem Pop Shockwave Ring
 * - Local 3D Pose Emote movement auto-cancel
 */
public final class CosmeticFxHandler {

    private static final int TRAIL_CAPACITY = 96;
    private static final int BURST_CAPACITY = 32;
    private static final int PARTICLE_CAPACITY = 128;

    private static final double[] TRAIL_X = new double[TRAIL_CAPACITY];
    private static final double[] TRAIL_Y = new double[TRAIL_CAPACITY];
    private static final double[] TRAIL_Z = new double[TRAIL_CAPACITY];
    private static final double[] TRAIL_LX = new double[TRAIL_CAPACITY];
    private static final double[] TRAIL_LZ = new double[TRAIL_CAPACITY];
    private static final long[] TRAIL_BIRTH = new long[TRAIL_CAPACITY];
    private static final int[] TRAIL_RGB = new int[TRAIL_CAPACITY];
    private static final int[] TRAIL_MODE = new int[TRAIL_CAPACITY];
    private static int trailCursor = 0;

    private static final int BURST_KIND_HIT = 0;
    private static final int BURST_KIND_KILL = 1;
    private static final int BURST_KIND_TOTEM = 2;
    private static final int BURST_KIND_PARTNER = 3;

    private static final double[] BURST_X = new double[BURST_CAPACITY];
    private static final double[] BURST_Y = new double[BURST_CAPACITY];
    private static final double[] BURST_Z = new double[BURST_CAPACITY];
    private static final long[] BURST_BIRTH = new long[BURST_CAPACITY];
    private static final long[] BURST_LIFE = new long[BURST_CAPACITY];
    private static final int[] BURST_KIND = new int[BURST_CAPACITY];
    private static final int[] BURST_SUBMODE = new int[BURST_CAPACITY];
    private static final int[] BURST_RGB = new int[BURST_CAPACITY];
    private static int burstCursor = 0;

    private static final double[] PART_X = new double[PARTICLE_CAPACITY];
    private static final double[] PART_Y = new double[PARTICLE_CAPACITY];
    private static final double[] PART_Z = new double[PARTICLE_CAPACITY];
    private static final double[] PART_VX = new double[PARTICLE_CAPACITY];
    private static final double[] PART_VY = new double[PARTICLE_CAPACITY];
    private static final double[] PART_VZ = new double[PARTICLE_CAPACITY];
    private static final long[] PART_BIRTH = new long[PARTICLE_CAPACITY];
    private static final long[] PART_LIFE = new long[PARTICLE_CAPACITY];
    private static final int[] PART_RGB = new int[PARTICLE_CAPACITY];
    private static final float[] PART_SIZE = new float[PARTICLE_CAPACITY];
    private static int partCursor = 0;

    // Track last recently damaged target by local player so handleRemoveEntities / death can trigger Kill Finisher
    private static int lastHitEntityId = -1;
    private static double lastHitX = 0.0;
    private static double lastHitY = 0.0;
    private static double lastHitZ = 0.0;
    private static long lastHitAtMs = 0L;

    private static double lastTrailSampleX = Double.NaN;
    private static double lastTrailSampleY = Double.NaN;
    private static double lastTrailSampleZ = Double.NaN;
    private static boolean wasOnGround = true;
    private static int tickCounter = 0;
    private static int pseudoRand = 0x5E571A01;
    private static long lastPartnerBurstAtMs = 0L;
    private static long lastCombatAtMs = 0L;
    private static String autoOutfitActiveName = null;
    private static CosmeticsLogic.CosmeticLoadout autoOutfitBaseLoadout = null;
    private static List<CosmeticOutfitManager.OutfitSummary> cachedAutoOutfits = List.of();
    private static long lastAutoOutfitsRefreshMs = 0L;

    private CosmeticFxHandler() {
    }

    public static void clearAll() {
        for (int i = 0; i < TRAIL_CAPACITY; i++) TRAIL_BIRTH[i] = 0L;
        for (int i = 0; i < BURST_CAPACITY; i++) BURST_BIRTH[i] = 0L;
        for (int i = 0; i < PARTICLE_CAPACITY; i++) PART_BIRTH[i] = 0L;
        trailCursor = 0;
        burstCursor = 0;
        partCursor = 0;
        lastHitEntityId = -1;
        lastHitAtMs = 0L;
        lastCombatAtMs = 0L;
        lastPartnerBurstAtMs = 0L;
        autoOutfitActiveName = null;
        autoOutfitBaseLoadout = null;
        lastTrailSampleX = Double.NaN;
        wasOnGround = true;
    }

    public static void invalidateAutoOutfitCache() {
        lastAutoOutfitsRefreshMs = 0L;
    }

    public static void tick(Minecraft mc) {
        if (mc == null || mc.player == null || mc.level == null || PanicHandler.isPanicActive()) {
            if (PanicHandler.isPanicActive()) {
                clearAll();
                CosmeticsLogic.clearLocalEmote();
                CustomCapeManager.releaseTexture(mc);
                CosmeticWheelController.forceClose();
            }
            return;
        }
        tickCounter++;
        LocalPlayer p = mc.player;
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null) {
            return;
        }

        // 1. Local 3D Pose Emote movement/action auto-cancel + Partner Emote Synced Spark/Heart Burst
        int activeEmote = CosmeticsLogic.getLocalActiveEmote();
        if (activeEmote >= 0) {
            Vec3 delta = p.getDeltaMovement();
            boolean moved = (delta.x * delta.x + delta.z * delta.z) > 0.0008;
            boolean jumped = !p.onGround() && delta.y > 0.08;
            boolean cancel = CosmeticsLogic.shouldCancelLocalEmote(
                    moved,
                    jumped,
                    p.isCrouching(),
                    p.isFallFlying(),
                    p.isSwimming(),
                    p.isSwinging(),
                    false
            );
            if (cancel) {
                CosmeticsLogic.clearLocalEmote();
                SestiaChatClient.broadcastEmote(CosmeticsLogic.EMOTE_NONE);
            } else if (CosmeticsLogic.isPartnerEmote(activeEmote) && (tickCounter % 3 == 0)) {
                checkPartnerEmoteBurst(mc, p, activeEmote);
            }
        }

        // 2. Smart Auto-Outfit Rules (PvP > Elytra > Mining > Night, throttled every 10 ticks)
        if (tickCounter % 10 == 0) {
            tickAutoOutfit(mc, p, cfg);
        }

        // 3. World Trails (Step Ripples / Leaf Ribbon / Neon Sparks / Elytra Contrail)
        if (cfg.cosmeticTrailEnabled) {
            samplePlayerTrail(p, cfg);
        }
        wasOnGround = p.onGround();
    }

    private static void checkPartnerEmoteBurst(Minecraft mc, LocalPlayer p, int localEmote) {
        long now = System.currentTimeMillis();
        if ((now - lastPartnerBurstAtMs) < 2_200L || mc.level == null) {
            return;
        }
        for (Player other : mc.level.players()) {
            if (other == null || other == p) {
                continue;
            }
            if (other.distanceToSqr(p) > 20.25) { // 4.5 blocks max
                continue;
            }
            int remoteEmote = CosmeticsLogic.resolveActiveEmote(false, false, other.getName().getString(), now);
            if (CosmeticsLogic.isPartnerEmote(remoteEmote)) {
                double midX = (p.getX() + other.getX()) * 0.5;
                double midY = (p.getY() + other.getY()) * 0.5 + 1.38;
                double midZ = (p.getZ() + other.getZ()) * 0.5;
                int accent = (JelloTheme.ACCENT != 0 ? (JelloTheme.ACCENT & 0xFFFFFF) : 0x34D399);
                triggerPartnerBurst(midX, midY, midZ, localEmote == CosmeticsLogic.EMOTE_HIGH_FIVE, accent, now);
                lastPartnerBurstAtMs = now;
                return;
            }
        }
    }

    public static void triggerPartnerBurst(double x, double y, double z, boolean isHighFive, int primaryRgb, long now) {
        int goldRgb = 0xFACC15;
        int heartRgb = 0xF43F5E;
        pushBurst(x, y, z, BURST_KIND_PARTNER, isHighFive ? 1 : 0, isHighFive ? goldRgb : primaryRgb, 950L, now);
        for (int i = 0; i < 20; i++) {
            double a = (i / 20.0) * Math.PI * 2.0;
            double spd = 0.06 + nextUnit() * 0.09;
            double vy = 0.03 + nextUnit() * 0.09;
            int col = (i % 3 == 0) ? heartRgb : ((i % 2 == 0) ? goldRgb : primaryRgb);
            spawnParticle(x, y, z, Math.cos(a) * spd, vy, Math.sin(a) * spd, 850L, col, 4.6f, now);
        }
    }

    private static void tickAutoOutfit(Minecraft mc, LocalPlayer p, ModConfig cfg) {
        long now = System.currentTimeMillis();
        if (!cfg.cosmeticAutoOutfitEnabled) {
            if (autoOutfitActiveName != null && autoOutfitBaseLoadout != null) {
                CosmeticsLogic.applyLoadoutToConfig(autoOutfitBaseLoadout, cfg, cfg.cosmeticLockedSlotMask);
                SestiaChatClient.broadcastCosmeticsUpdate();
            }
            autoOutfitActiveName = null;
            autoOutfitBaseLoadout = null;
            return;
        }
        boolean inCombat = (now - lastCombatAtMs) < 12_000L;
        boolean fallFlying = p.isFallFlying();
        ItemStack held = p.getMainHandItem();
        boolean miningTool = held != null && !held.isEmpty() && (held.is(ItemTags.PICKAXES) || held.is(ItemTags.SHOVELS));
        boolean mining = !inCombat && !fallFlying && miningTool && (p.getY() < 56.0 || p.isSwinging());
        boolean nightOrDark = !mc.level.dimensionType().hasSkyLight() || mc.level.isDarkOutside();

        String target = CosmeticsLogic.resolveAutoOutfitTarget(
                true, inCombat, fallFlying, mining, nightOrDark,
                cfg.cosmeticAutoOutfitPvp,
                cfg.cosmeticAutoOutfitElytra,
                cfg.cosmeticAutoOutfitMining,
                cfg.cosmeticAutoOutfitNight
        );

        if (target == null) {
            if (autoOutfitActiveName != null) {
                if (autoOutfitBaseLoadout != null) {
                    CosmeticsLogic.applyLoadoutToConfig(autoOutfitBaseLoadout, cfg, cfg.cosmeticLockedSlotMask);
                    SestiaChatClient.broadcastCosmeticsUpdate();
                }
                autoOutfitActiveName = null;
                autoOutfitBaseLoadout = null;
            }
            return;
        }

        if (target.equalsIgnoreCase(autoOutfitActiveName)) {
            return;
        }

        if ((now - lastAutoOutfitsRefreshMs) > 5_000L) {
            cachedAutoOutfits = CosmeticOutfitManager.listOutfits(CosmeticOutfitManager.getDefaultOutfitsDir());
            lastAutoOutfitsRefreshMs = now;
        }
        for (CosmeticOutfitManager.OutfitSummary summary : cachedAutoOutfits) {
            if (summary != null && summary.name().equalsIgnoreCase(target)) {
                if (autoOutfitBaseLoadout == null) {
                    autoOutfitBaseLoadout = CosmeticsLogic.fromConfig(cfg);
                }
                CosmeticsLogic.applyLoadoutToConfig(summary.loadout(), cfg, cfg.cosmeticLockedSlotMask);
                autoOutfitActiveName = summary.name();
                SestiaChatClient.broadcastCosmeticsUpdate();
                return;
            }
        }
    }

    private static void samplePlayerTrail(LocalPlayer p, ModConfig cfg) {
        double x = p.getX();
        double y = p.getY();
        double z = p.getZ();
        long now = System.currentTimeMillis();
        int mode = cfg.cosmeticTrailMode;
        int rgb = CosmeticsLogic.resolveColorRgb(cfg.cosmeticTrailColorPreset, cfg.cosmeticTrailCustomRgb, tickCounter, 0.0f);

        boolean jumpedJustNow = wasOnGround && !p.onGround() && p.getDeltaMovement().y > 0.1;
        boolean landedJustNow = !wasOnGround && p.onGround();

        double distSq = Double.isNaN(lastTrailSampleX)
                ? 99.0
                : (x - lastTrailSampleX) * (x - lastTrailSampleX)
                + (y - lastTrailSampleY) * (y - lastTrailSampleY)
                + (z - lastTrailSampleZ) * (z - lastTrailSampleZ);

        if (mode == CosmeticsLogic.TRAIL_ELYTRA_CONTRAIL) {
            if (!p.isFallFlying()) {
                return;
            }
            if (distSq >= 0.08) {
                double yawRad = Math.toRadians(p.getYRot());
                double rightX = Math.cos(yawRad) * 0.55;
                double rightZ = Math.sin(yawRad) * 0.55;
                pushTrailNode(x, y + 0.55, z, rightX, rightZ, now, rgb, mode);
                lastTrailSampleX = x; lastTrailSampleY = y; lastTrailSampleZ = z;
            }
            return;
        }

        if (mode == CosmeticsLogic.TRAIL_STEP_RIPPLES) {
            if (jumpedJustNow || landedJustNow || (p.onGround() && distSq >= 0.65)) {
                pushTrailNode(x, y + 0.04, z, 0.0, 0.0, now, rgb, mode);
                lastTrailSampleX = x; lastTrailSampleY = y; lastTrailSampleZ = z;
            }
            return;
        }

        if (distSq >= (p.isSprinting() ? 0.09 : 0.18) || jumpedJustNow) {
            double yawRad = Math.toRadians(p.getYRot());
            double rightX = Math.cos(yawRad) * 0.22;
            double rightZ = Math.sin(yawRad) * 0.22;
            pushTrailNode(x, y + 0.12, z, rightX, rightZ, now, rgb, mode);
            if (mode == CosmeticsLogic.TRAIL_NEON_SPARKS) {
                spawnParticle(x, y + 0.15, z, nextSigned() * 0.04, 0.03 + nextUnit() * 0.05, nextSigned() * 0.04, 520L, rgb, 3.6f, now);
            }
            lastTrailSampleX = x; lastTrailSampleY = y; lastTrailSampleZ = z;
        }
    }

    public static void onDamageEvent(Minecraft mc, int targetEntityId, int sourceCauseId, int sourceDirectId) {
        if (mc == null || mc.player == null || mc.level == null || PanicHandler.isPanicActive()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null) {
            return;
        }
        int selfId = mc.player.getId();
        boolean causedBySelf = (sourceCauseId == selfId || sourceDirectId == selfId);
        if (causedBySelf || targetEntityId == selfId) {
            lastCombatAtMs = System.currentTimeMillis();
        }
        if (!causedBySelf || targetEntityId == selfId) {
            return;
        }
        Entity target = mc.level.getEntity(targetEntityId);
        if (!(target instanceof LivingEntity living)) {
            return;
        }
        long now = System.currentTimeMillis();
        double tx = living.getX();
        double ty = living.getY() + living.getBbHeight() * 0.55;
        double tz = living.getZ();

        lastHitEntityId = targetEntityId;
        lastHitX = tx;
        lastHitY = living.getY();
        lastHitZ = tz;
        lastHitAtMs = now;

        if (cfg.cosmeticHitFxEnabled) {
            int rgb = CosmeticsLogic.resolveColorRgb(cfg.cosmeticHitFxColorPreset, cfg.cosmeticHitFxCustomRgb, tickCounter, 0.0f);
            pushBurst(tx, ty, tz, BURST_KIND_HIT, cfg.cosmeticHitFxMode, rgb, 460L, now);
            int sparks = cfg.cosmeticHitFxMode == CosmeticsLogic.HIT_FX_SHOCKWAVE_RING ? 6 : 12;
            for (int i = 0; i < sparks; i++) {
                double a = (i / (double) sparks) * Math.PI * 2.0 + nextSigned() * 0.25;
                double spd = 0.06 + nextUnit() * 0.08;
                double vy = cfg.cosmeticHitFxMode == CosmeticsLogic.HIT_FX_LEAF_PETALS
                        ? (0.04 + nextUnit() * 0.06)
                        : (nextSigned() * 0.07 + 0.03);
                spawnParticle(tx, ty, tz, Math.cos(a) * spd, vy, Math.sin(a) * spd, 440L, rgb, 4.2f, now);
            }
        }
    }

    public static void onEntityEvent(Minecraft mc, Entity entity, byte eventId) {
        if (mc == null || mc.player == null || entity == null || PanicHandler.isPanicActive()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // Event 35 = Totem of Undying activation
        if (eventId == 35 && cfg.cosmeticTotemFxEnabled && cfg.cosmeticTotemFxRing) {
            if (entity.getId() == mc.player.getId() || entity.distanceToSqr(mc.player) <= 400.0) {
                int rgb = CosmeticsLogic.resolveColorRgb(cfg.cosmeticAuraColorPreset, cfg.cosmeticAuraCustomRgb, tickCounter, 0.0f);
                pushBurst(entity.getX(), entity.getY() + 0.85, entity.getZ(), BURST_KIND_TOTEM, 0, rgb, 820L, now);
                for (int i = 0; i < 16; i++) {
                    double a = (i / 16.0) * Math.PI * 2.0;
                    spawnParticle(entity.getX(), entity.getY() + 0.9, entity.getZ(),
                            Math.cos(a) * 0.14, 0.06 + nextUnit() * 0.08, Math.sin(a) * 0.14, 720L, rgb, 4.8f, now);
                }
            }
        }
        // Event 3 = Entity death
        if (eventId == 3 && cfg.cosmeticKillFxEnabled && entity.getId() == lastHitEntityId && (now - lastHitAtMs) <= 4500L) {
            triggerKillFinisher(cfg, entity.getX(), entity.getY(), entity.getZ(), now);
            lastHitEntityId = -1;
        }
    }

    public static void onEntityRemoved(Minecraft mc, int entityId) {
        if (mc == null || mc.player == null || PanicHandler.isPanicActive()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.cosmeticKillFxEnabled) {
            return;
        }
        long now = System.currentTimeMillis();
        if (entityId == lastHitEntityId && (now - lastHitAtMs) <= 2500L) {
            triggerKillFinisher(cfg, lastHitX, lastHitY, lastHitZ, now);
            lastHitEntityId = -1;
        }
    }

    private static void triggerKillFinisher(ModConfig cfg, double x, double y, double z, long now) {
        int rgb = CosmeticsLogic.resolveColorRgb(cfg.cosmeticKillFxColorPreset, cfg.cosmeticKillFxCustomRgb, tickCounter, 0.0f);
        int mode = cfg.cosmeticKillFxMode;
        pushBurst(x, y, z, BURST_KIND_KILL, mode, rgb, 1150L, now);
        int count = mode == CosmeticsLogic.KILL_FX_VOXEL_DISSOLVE ? 20 : 14;
        for (int i = 0; i < count; i++) {
            double a = (i / (double) count) * Math.PI * 2.0;
            double rad = 0.25 + nextUnit() * 0.35;
            double vx = Math.cos(a) * (0.04 + nextUnit() * 0.07);
            double vz = Math.sin(a) * (0.04 + nextUnit() * 0.07);
            double vy = mode == CosmeticsLogic.KILL_FX_SOUL_BEACON ? (0.10 + nextUnit() * 0.16) : (0.05 + nextUnit() * 0.10);
            spawnParticle(x + Math.cos(a) * rad, y + 0.35 + nextUnit() * 1.1, z + Math.sin(a) * rad,
                    vx, vy, vz, 980L, rgb, mode == CosmeticsLogic.KILL_FX_VOXEL_DISSOLVE ? 6.0f : 4.6f, now);
        }
    }

    /**
     * Renders all active world trails, hit bursts, kill finishers, and totem 3D shockwaves via MC 26.3 Gizmos.
     */
    public static void renderWorldGizmos() {
        if (PanicHandler.isPanicActive()) {
            return;
        }
        long now = System.currentTimeMillis();
        renderTrails(now);
        renderBursts(now);
        renderParticles(now);
    }

    private static void renderTrails(long now) {
        long maxAgeMs = 1150L;
        int prevIdx = -1;
        for (int offset = 0; offset < TRAIL_CAPACITY; offset++) {
            int idx = (trailCursor + offset) % TRAIL_CAPACITY;
            long birth = TRAIL_BIRTH[idx];
            if (birth <= 0L) {
                prevIdx = -1;
                continue;
            }
            long ageMs = now - birth;
            if (ageMs < 0L || ageMs > maxAgeMs) {
                TRAIL_BIRTH[idx] = 0L;
                prevIdx = -1;
                continue;
            }
            float progress = (float) ageMs / maxAgeMs;
            float fade = 1.0f - progress;
            int alpha = Math.max(12, Math.min(235, Math.round(fade * 215.0f)));
            int argb = (alpha << 24) | (TRAIL_RGB[idx] & 0xFFFFFF);
            int mode = TRAIL_MODE[idx];

            if (mode == CosmeticsLogic.TRAIL_STEP_RIPPLES) {
                float radius = 0.18f + progress * 0.85f;
                Gizmos.circle(new Vec3(TRAIL_X[idx], TRAIL_Y[idx], TRAIL_Z[idx]), radius, GizmoStyle.stroke(argb, 2.2f * fade + 0.6f));
            } else if (prevIdx >= 0 && TRAIL_MODE[prevIdx] == mode && (birth - TRAIL_BIRTH[prevIdx]) <= 450L) {
                if (mode == CosmeticsLogic.TRAIL_ELYTRA_CONTRAIL) {
                    Vec3 aL = new Vec3(TRAIL_X[prevIdx] - TRAIL_LX[prevIdx], TRAIL_Y[prevIdx], TRAIL_Z[prevIdx] - TRAIL_LZ[prevIdx]);
                    Vec3 bL = new Vec3(TRAIL_X[idx] - TRAIL_LX[idx], TRAIL_Y[idx], TRAIL_Z[idx] - TRAIL_LZ[idx]);
                    Vec3 aR = new Vec3(TRAIL_X[prevIdx] + TRAIL_LX[prevIdx], TRAIL_Y[prevIdx], TRAIL_Z[prevIdx] + TRAIL_LZ[prevIdx]);
                    Vec3 bR = new Vec3(TRAIL_X[idx] + TRAIL_LX[idx], TRAIL_Y[idx], TRAIL_Z[idx] + TRAIL_LZ[idx]);
                    Gizmos.line(aL, bL, argb, 2.6f * fade + 0.8f);
                    Gizmos.line(aR, bR, argb, 2.6f * fade + 0.8f);
                } else {
                    Vec3 a = new Vec3(TRAIL_X[prevIdx], TRAIL_Y[prevIdx], TRAIL_Z[prevIdx]);
                    Vec3 b = new Vec3(TRAIL_X[idx], TRAIL_Y[idx], TRAIL_Z[idx]);
                    Gizmos.line(a, b, argb, (mode == CosmeticsLogic.TRAIL_LEAF_RIBBON ? 3.2f : 2.0f) * fade + 0.7f);
                    if (mode == CosmeticsLogic.TRAIL_LEAF_RIBBON) {
                        Vec3 a2 = new Vec3(TRAIL_X[prevIdx] + TRAIL_LX[prevIdx], TRAIL_Y[prevIdx] + 0.08, TRAIL_Z[prevIdx] + TRAIL_LZ[prevIdx]);
                        Vec3 b2 = new Vec3(TRAIL_X[idx] + TRAIL_LX[idx], TRAIL_Y[idx] + 0.08, TRAIL_Z[idx] + TRAIL_LZ[idx]);
                        int softArgb = ((alpha / 2) << 24) | (TRAIL_RGB[idx] & 0xFFFFFF);
                        Gizmos.line(a2, b2, softArgb, 1.8f * fade + 0.5f);
                    }
                }
            }
            prevIdx = idx;
        }
    }

    private static void renderBursts(long now) {
        for (int i = 0; i < BURST_CAPACITY; i++) {
            long birth = BURST_BIRTH[i];
            if (birth <= 0L) continue;
            long life = BURST_LIFE[i];
            long ageMs = now - birth;
            if (ageMs < 0L || ageMs > life) {
                BURST_BIRTH[i] = 0L;
                continue;
            }
            float t = (float) ageMs / life;
            float fade = 1.0f - t;
            int alpha = Math.max(15, Math.min(245, Math.round(fade * 240.0f)));
            int rgb = BURST_RGB[i] & 0xFFFFFF;
            int argb = (alpha << 24) | rgb;
            int fillArgb = (Math.max(8, alpha / 4) << 24) | rgb;
            double x = BURST_X[i], y = BURST_Y[i], z = BURST_Z[i];
            int kind = BURST_KIND[i];
            int sub = BURST_SUBMODE[i];

            if (kind == BURST_KIND_HIT) {
                float rad = 0.25f + t * (sub == CosmeticsLogic.HIT_FX_SHOCKWAVE_RING ? 1.45f : 0.95f);
                Gizmos.circle(new Vec3(x, y, z), rad, GizmoStyle.stroke(argb, 2.8f * fade + 0.6f));
                if (sub == CosmeticsLogic.HIT_FX_EMERALD_BURST) {
                    float s = 0.14f + t * 0.30f;
                    Gizmos.line(new Vec3(x - s, y, z), new Vec3(x + s, y, z), argb, 2.0f);
                    Gizmos.line(new Vec3(x, y - s, z), new Vec3(x, y + s, z), argb, 2.0f);
                    Gizmos.line(new Vec3(x, y, z - s), new Vec3(x, y, z + s), argb, 2.0f);
                }
            } else if (kind == BURST_KIND_TOTEM) {
                float rad1 = 0.45f + t * 2.6f;
                float rad2 = 0.25f + t * 1.8f;
                Gizmos.circle(new Vec3(x, y, z), rad1, GizmoStyle.stroke(argb, 3.2f * fade + 0.8f));
                Gizmos.circle(new Vec3(x, y + t * 0.85, z), rad2, GizmoStyle.stroke(argb, 2.2f * fade + 0.5f));
            } else if (kind == BURST_KIND_PARTNER) {
                float rad = 0.20f + t * 1.35f;
                int goldArgb = (alpha << 24) | 0xFACC15;
                int roseArgb = (alpha << 24) | 0xF43F5E;
                Gizmos.circle(new Vec3(x, y, z), rad, GizmoStyle.stroke(argb, 3.0f * fade + 0.6f));
                Gizmos.circle(new Vec3(x, y + t * 0.42, z), rad * 0.65f, GizmoStyle.stroke(goldArgb, 2.2f * fade + 0.5f));
                float star = 0.16f + t * 0.45f;
                Gizmos.line(new Vec3(x - star, y, z), new Vec3(x + star, y, z), roseArgb, 2.2f);
                Gizmos.line(new Vec3(x, y - star, z), new Vec3(x, y + star, z), goldArgb, 2.2f);
                Gizmos.line(new Vec3(x, y, z - star), new Vec3(x, y, z + star), roseArgb, 2.2f);
            } else if (kind == BURST_KIND_KILL) {
                if (sub == CosmeticsLogic.KILL_FX_SOUL_BEACON) {
                    float beamW = 0.34f * fade + 0.04f;
                    double beamH = 2.0 + t * 11.0;
                    AABB beam = new AABB(x - beamW, y, z - beamW, x + beamW, y + beamH, z + beamW);
                    Gizmos.cuboid(beam, GizmoStyle.strokeAndFill(argb, 2.0f, fillArgb));
                    Gizmos.circle(new Vec3(x, y + 0.08, z), 0.5f + t * 2.1f, GizmoStyle.stroke(argb, 2.8f * fade + 0.6f));
                } else if (sub == CosmeticsLogic.KILL_FX_EMERALD_NOVA) {
                    Gizmos.circle(new Vec3(x, y + 0.15, z), 0.35f + t * 2.8f, GizmoStyle.stroke(argb, 3.2f * fade + 0.8f));
                    Gizmos.circle(new Vec3(x, y + 0.90 + t * 0.7, z), 0.25f + t * 1.9f, GizmoStyle.stroke(argb, 2.4f * fade + 0.5f));
                    Gizmos.circle(new Vec3(x, y + 1.65 + t * 1.2, z), 0.18f + t * 1.1f, GizmoStyle.stroke(argb, 1.8f * fade + 0.4f));
                } else {
                    // Voxel Dissolve expanding wireframe cubes
                    for (int c = 0; c < 4; c++) {
                        double ang = c * (Math.PI * 0.5) + t * 2.5;
                        double d = 0.25 + t * 1.25;
                        double cy = y + 0.35 + c * 0.32 + t * 0.9;
                        float hs = 0.16f * fade + 0.03f;
                        double cx = x + Math.cos(ang) * d;
                        double cz = z + Math.sin(ang) * d;
                        Gizmos.cuboid(new AABB(cx - hs, cy - hs, cz - hs, cx + hs, cy + hs, cz + hs),
                                GizmoStyle.strokeAndFill(argb, 1.8f, fillArgb));
                    }
                }
            }
        }
    }

    private static void renderParticles(long now) {
        for (int i = 0; i < PARTICLE_CAPACITY; i++) {
            long birth = PART_BIRTH[i];
            if (birth <= 0L) continue;
            long life = PART_LIFE[i];
            long ageMs = now - birth;
            if (ageMs < 0L || ageMs > life) {
                PART_BIRTH[i] = 0L;
                continue;
            }
            float t = (float) ageMs / life;
            float fade = 1.0f - t;
            float ticks = ageMs / 50.0f;
            double px = PART_X[i] + PART_VX[i] * ticks;
            double py = PART_Y[i] + PART_VY[i] * ticks - 0.004 * ticks * ticks;
            double pz = PART_Z[i] + PART_VZ[i] * ticks;
            int alpha = Math.max(15, Math.min(245, Math.round(fade * 240.0f)));
            int argb = (alpha << 24) | (PART_RGB[i] & 0xFFFFFF);
            Gizmos.point(new Vec3(px, py, pz), argb, PART_SIZE[i] * (0.45f + 0.55f * fade));
        }
    }

    private static void pushTrailNode(double x, double y, double z, double lx, double lz, long now, int rgb, int mode) {
        int idx = trailCursor;
        trailCursor = (trailCursor + 1) % TRAIL_CAPACITY;
        TRAIL_X[idx] = x;
        TRAIL_Y[idx] = y;
        TRAIL_Z[idx] = z;
        TRAIL_LX[idx] = lx;
        TRAIL_LZ[idx] = lz;
        TRAIL_BIRTH[idx] = now;
        TRAIL_RGB[idx] = rgb & 0xFFFFFF;
        TRAIL_MODE[idx] = mode;
    }

    private static void pushBurst(double x, double y, double z, int kind, int submode, int rgb, long lifeMs, long now) {
        int idx = burstCursor;
        burstCursor = (burstCursor + 1) % BURST_CAPACITY;
        BURST_X[idx] = x;
        BURST_Y[idx] = y;
        BURST_Z[idx] = z;
        BURST_BIRTH[idx] = now;
        BURST_LIFE[idx] = lifeMs;
        BURST_KIND[idx] = kind;
        BURST_SUBMODE[idx] = submode;
        BURST_RGB[idx] = rgb & 0xFFFFFF;
    }

    private static void spawnParticle(double x, double y, double z, double vx, double vy, double vz, long lifeMs, int rgb, float size, long now) {
        int idx = partCursor;
        partCursor = (partCursor + 1) % PARTICLE_CAPACITY;
        PART_X[idx] = x;
        PART_Y[idx] = y;
        PART_Z[idx] = z;
        PART_VX[idx] = vx;
        PART_VY[idx] = vy;
        PART_VZ[idx] = vz;
        PART_BIRTH[idx] = now;
        PART_LIFE[idx] = lifeMs;
        PART_RGB[idx] = rgb & 0xFFFFFF;
        PART_SIZE[idx] = size;
    }

    private static double nextUnit() {
        int x = pseudoRand;
        x ^= (x << 13);
        x ^= (x >>> 17);
        x ^= (x << 5);
        pseudoRand = x;
        return ((x & 0x7FFFFFFF) % 10000) / 10000.0;
    }

    private static double nextSigned() {
        return nextUnit() * 2.0 - 1.0;
    }
}
