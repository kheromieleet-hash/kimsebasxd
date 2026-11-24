package com.client.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * Consolidated zero-allocation 3D Cosmetics RenderLayer registered once in {@code AvatarRendererMixin.<init>}.
 * Renders Animated Cloaks, 3D Headwear, Shoulder Pets / Orbiting Companions, Back Gear & Articulated Tails,
 * and 3D Body/Foot Auras for local player, synced Sestia friends, and the Cosmetic Locker 3D preview avatar.
 */
public class AvatarCosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    public AvatarCosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int packedLight,
            AvatarRenderState state,
            float yRot,
            float xRot
    ) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || PanicHandler.isPanicActive() || state.isInvisible || state.isSpectator) {
            return;
        }

        CosmeticsLogic.CosmeticLoadout loadout;
        if (state.id == WingsLogic.PREVIEW_ENTITY_ID) {
            loadout = CosmeticsLogic.getPreviewLoadout();
            if (loadout == null) {
                return;
            }
        } else {
            Minecraft mc = Minecraft.getInstance();
            boolean isSelf = mc != null && mc.player != null && state.id == mc.player.getId();
            String remoteName = null;
            if (!isSelf) {
                if (!cfg.cosmeticShowFriends || !CosmeticsLogic.hasRemoteLoadouts() || mc == null || mc.level == null) {
                    return;
                }
                Entity entity = mc.level.getEntity(state.id);
                if (entity instanceof Player player) {
                    remoteName = player.getName().getString();
                } else {
                    return;
                }
            }
            loadout = CosmeticsLogic.resolveRenderLoadout(cfg, false, false, false, isSelf, remoteName);
            if (loadout == null) {
                return;
            }
        }

        boolean hasChest = state.chestEquipment != null && !state.chestEquipment.isEmpty();
        boolean hasElytra = hasChest && state.chestEquipment.has(DataComponents.GLIDER);
        boolean hasHelmet = state.headEquipment != null && !state.headEquipment.isEmpty();
        float age = Float.isFinite(state.ageInTicks) ? state.ageInTicks : 0.0f;
        float walkPos = Float.isFinite(state.walkAnimationPos) ? state.walkAnimationPos : 0.0f;
        float walkSpeed = Float.isFinite(state.walkAnimationSpeed) ? state.walkAnimationSpeed : 0.0f;

        // 1. Animated 3D Cloak (hidden automatically when wearing Elytra)
        if (loadout.cloakEnabled() && !hasElytra) {
            final int mode = loadout.cloakMode();
            final int colorPreset = loadout.cloakColorPreset();
            final int customRgb = loadout.cloakCustomRgb();
            final float flap = state.capeFlap;
            final float lean = state.capeLean;
            final float lean2 = state.capeLean2;
            final float cloakZ = CosmeticsLogic.computeCloakBackZ(hasChest);
            final boolean crouching = state.isCrouching;

            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
                    (pose, vc) -> renderCloak(pose, vc, mode, colorPreset, customRgb, age, flap, lean, lean2, cloakZ, crouching));
            poseStack.popPose();
        }

        // 2. 3D Headwear (anchored to head with automatic helmet clearance)
        if (loadout.hatEnabled()) {
            final int mode = loadout.hatMode();
            final int colorPreset = loadout.hatColorPreset();
            final int customRgb = loadout.hatCustomRgb();
            final float scale = loadout.hatScale();
            final float clearY = CosmeticsLogic.computeHelmetClearanceY(hasHelmet);

            poseStack.pushPose();
            getParentModel().head.translateAndRotate(poseStack);
            poseStack.translate(0.0f, clearY, 0.0f);
            if (scale != 1.0f) {
                poseStack.scale(scale, scale, scale);
            }
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
                    (pose, vc) -> renderHeadwear(pose, vc, mode, colorPreset, customRgb, age));
            poseStack.popPose();
        }

        // 3. Back Gear & Articulated Tails (anchored to body with armor & cloak clearance)
        if (loadout.gearEnabled()) {
            final int mode = loadout.gearMode();
            final int colorPreset = loadout.gearColorPreset();
            final int customRgb = loadout.gearCustomRgb();
            final float backZ = CosmeticsLogic.computeGearBackZ(hasChest, loadout.cloakEnabled() && !hasElytra);

            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
                    (pose, vc) -> renderBackGearOrTail(pose, vc, mode, colorPreset, customRgb, age, walkPos, walkSpeed, backZ));
            poseStack.popPose();
        }

        // 4. Shoulder Pet / Orbiting Companion (anchored to torso)
        if (loadout.petEnabled()) {
            final int mode = loadout.petMode();
            final int side = loadout.petSide();
            final int colorPreset = loadout.petColorPreset();
            final int customRgb = loadout.petCustomRgb();

            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
                    (pose, vc) -> renderCompanionPet(pose, vc, mode, side, colorPreset, customRgb, age));
            poseStack.popPose();
        }

        // 5. 3D Player Aura (anchored around avatar space, zero per-frame alloc)
        if (loadout.auraEnabled()) {
            final int mode = loadout.auraMode();
            final int colorPreset = loadout.auraColorPreset();
            final int customRgb = loadout.auraCustomRgb();
            final float radius = loadout.auraRadius();
            final float speed = loadout.auraSpeed();

            poseStack.pushPose();
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(),
                    (pose, vc) -> renderPlayerAura(pose, vc, mode, colorPreset, customRgb, radius, speed, age));
            poseStack.popPose();
        }
    }

    // =========================================================================
    // 1. ANIMATED 3D CLOAKS
    // =========================================================================
    private static void renderCloak(
            PoseStack.Pose pose, VertexConsumer vc,
            int mode, int colorPreset, int customRgb,
            float age, float capeFlap, float capeLean, float capeLean2,
            float backZ, boolean crouching
    ) {
        float halfW = 0.28f;
        float totalLen = 0.96f;
        int segs = 6;

        float leanRad = Math.max(0.08f, Math.min(1.25f, (6.0f + capeLean * 0.5f + capeFlap * 0.8f + (crouching ? 22.0f : 0.0f)) * DEG_TO_RAD));
        float sideSway = Math.max(-0.22f, Math.min(0.22f, capeLean2 * 0.008f));

        int primary = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, age, 0.0f);
        int darkCloth = switch (mode) {
            case CosmeticsLogic.CLOAK_OBSIDIAN_ROYAL -> 0x0E0D14;
            case CosmeticsLogic.CLOAK_CYBER_GRID -> 0x0A111A;
            case CosmeticsLogic.CLOAK_CHROMA_NEBULA -> WingsLogic.blendRgb(0x0D0A22, primary, 0.32f);
            default -> WingsLogic.blendRgb(0x071B14, primary, 0.28f);
        };
        int trimRgb = switch (mode) {
            case CosmeticsLogic.CLOAK_OBSIDIAN_ROYAL -> 0xFACC15;
            case CosmeticsLogic.CLOAK_CHROMA_NEBULA -> WingsLogic.resolveRgb(WingsLogic.COLOR_CHROMA, customRgb, age, 0.25f, false);
            default -> WingsLogic.blendRgb(primary, 0xFFFFFF, 0.25f);
        };

        float prevY = 0.03f;
        float prevZ = backZ;
        float prevXOff = 0.0f;

        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            float wave0 = (float) Math.sin(age * 0.14f - t0 * 2.8f) * (0.014f + t0 * 0.022f);
            float wave1 = (float) Math.sin(age * 0.14f - t1 * 2.8f) * (0.014f + t1 * 0.022f);
            float curLen = totalLen / segs;
            float segAngle = leanRad * (0.55f + 0.55f * t1);

            float nextY = prevY + (float) Math.cos(segAngle) * curLen;
            float nextZ = prevZ + (float) Math.sin(segAngle) * curLen + (wave1 - wave0);
            float nextXOff = sideSway * t1 * t1;

            float w0 = halfW * (1.0f + 0.10f * t0);
            float w1 = halfW * (1.0f + 0.10f * t1);

            int segRgb0;
            int segRgb1;
            if (mode == CosmeticsLogic.CLOAK_CHROMA_NEBULA) {
                segRgb0 = WingsLogic.resolveRgb(WingsLogic.COLOR_CHROMA, customRgb, age, t0 * 0.42f, false);
                segRgb1 = WingsLogic.resolveRgb(WingsLogic.COLOR_CHROMA, customRgb, age, t1 * 0.42f, false);
            } else {
                segRgb0 = WingsLogic.blendRgb(darkCloth, primary, 0.16f + 0.34f * t0);
                segRgb1 = WingsLogic.blendRgb(darkCloth, primary, 0.16f + 0.34f * t1);
            }

            int r0 = (segRgb0 >> 16) & 0xFF, g0 = (segRgb0 >> 8) & 0xFF, b0 = segRgb0 & 0xFF;
            int r1 = (segRgb1 >> 16) & 0xFF, g1 = (segRgb1 >> 8) & 0xFF, b1 = segRgb1 & 0xFF;
            int tr = (trimRgb >> 16) & 0xFF, tg = (trimRgb >> 8) & 0xFF, tb = trimRgb & 0xFF;

            // Main front & back cloth quad (double-sided for clean viewing from any angle)
            emitDoubleQuad(pose, vc,
                    prevXOff - w0, prevY, prevZ, r0, g0, b0, 240,
                    prevXOff + w0, prevY, prevZ, r0, g0, b0, 240,
                    nextXOff + w1, nextY, nextZ, r1, g1, b1, 232,
                    nextXOff - w1, nextY, nextZ, r1, g1, b1, 232);

            // Luminous side trim borders
            float trimW = 0.024f;
            for (int side = -1; side <= 1; side += 2) {
                float sx0 = prevXOff + side * w0;
                float sx1 = nextXOff + side * w1;
                float ix0 = prevXOff + side * (w0 - trimW);
                float ix1 = nextXOff + side * (w1 - trimW);
                emitDoubleQuad(pose, vc,
                        ix0, prevY, prevZ + 0.003f, tr, tg, tb, 245,
                        sx0, prevY, prevZ + 0.003f, tr, tg, tb, 245,
                        sx1, nextY, nextZ + 0.003f, tr, tg, tb, 235,
                        ix1, nextY, nextZ + 0.003f, tr, tg, tb, 235);
            }

            // Cyber Grid horizontal scanlines
            if (mode == CosmeticsLogic.CLOAK_CYBER_GRID) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(age * 0.22f - i * 0.9f);
                int alpha = Math.round(135 + 110 * pulse);
                emitDoubleQuad(pose, vc,
                        nextXOff - w1 + 0.02f, nextY - 0.010f, nextZ + 0.005f, tr, tg, tb, alpha,
                        nextXOff + w1 - 0.02f, nextY - 0.010f, nextZ + 0.005f, tr, tg, tb, alpha,
                        nextXOff + w1 - 0.02f, nextY + 0.008f, nextZ + 0.005f, tr, tg, tb, alpha,
                        nextXOff - w1 + 0.02f, nextY + 0.008f, nextZ + 0.005f, tr, tg, tb, alpha);
            }

            // Sestia Leaf 7-point glowing emblem on mid-back segments (i == 2)
            if (mode == CosmeticsLogic.CLOAK_SESTIA_LEAF && i == 2) {
                float cx = (prevXOff + nextXOff) * 0.5f;
                float cy = (prevY + nextY) * 0.5f + 0.04f;
                float cz = (prevZ + nextZ) * 0.5f + 0.007f;
                emitLeafEmblem(pose, vc, cx, cy, cz, 0.16f, trimRgb, primary);
            }

            // Bottom gold/accent hem
            if (i == segs - 1) {
                emitDoubleQuad(pose, vc,
                        nextXOff - w1, nextY - 0.024f, nextZ + 0.004f, tr, tg, tb, 245,
                        nextXOff + w1, nextY - 0.024f, nextZ + 0.004f, tr, tg, tb, 245,
                        nextXOff + w1, nextY + 0.004f, nextZ + 0.004f, tr, tg, tb, 245,
                        nextXOff - w1, nextY + 0.004f, nextZ + 0.004f, tr, tg, tb, 245);
            }

            prevY = nextY;
            prevZ = nextZ;
            prevXOff = nextXOff;
        }
    }

    private static void emitLeafEmblem(PoseStack.Pose pose, VertexConsumer vc, float cx, float cy, float cz, float size, int coreRgb, int outerRgb) {
        int cr = (coreRgb >> 16) & 0xFF, cg = (coreRgb >> 8) & 0xFF, cb = coreRgb & 0xFF;
        int or = (outerRgb >> 16) & 0xFF, og = (outerRgb >> 8) & 0xFF, ob = outerRgb & 0xFF;
        // 7 radiating diamond leaf leaflets
        float[] angles = {-72f, -46f, -22f, 0f, 22f, 46f, 72f};
        float[] lengths = {0.48f, 0.72f, 0.88f, 1.05f, 0.88f, 0.72f, 0.48f};
        for (int k = 0; k < 7; k++) {
            float rad = angles[k] * DEG_TO_RAD;
            float len = size * lengths[k];
            float hw = size * 0.14f;
            float dx = (float) Math.sin(rad);
            float dy = -(float) Math.cos(rad);
            float nx = -dy;
            float ny = dx;

            float mx = cx + dx * len * 0.52f;
            float my = cy + dy * len * 0.52f;
            float tx = cx + dx * len;
            float ty = cy + dy * len;

            emitDoubleQuad(pose, vc,
                    cx, cy, cz, cr, cg, cb, 245,
                    mx + nx * hw, my + ny * hw, cz, or, og, ob, 225,
                    tx, ty, cz, cr, cg, cb, 240,
                    mx - nx * hw, my - ny * hw, cz, or, og, ob, 225);
        }
    }

    // =========================================================================
    // 2. 3D HEADWEAR (6 MODELS)
    // =========================================================================
    private static void renderHeadwear(
            PoseStack.Pose pose, VertexConsumer vc,
            int mode, int colorPreset, int customRgb, float age
    ) {
        int rgb = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, age, 0.0f);
        int bright = WingsLogic.blendRgb(rgb, 0xFFFFFF, 0.45f);
        int dark = WingsLogic.blendRgb(0x10131C, rgb, 0.22f);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int br = (bright >> 16) & 0xFF, bg = (bright >> 8) & 0xFF, bb = bright & 0xFF;
        int dr = (dark >> 16) & 0xFF, dg = (dark >> 8) & 0xFF, db = dark & 0xFF;

        switch (mode) {
            case CosmeticsLogic.HAT_SERAPH_HALO -> {
                float bob = (float) Math.sin(age * 0.12f) * 0.025f;
                float y = -0.66f + bob;
                float spin = age * 0.04f;
                emitHorizontalRing(pose, vc, 0.0f, y, 0.0f, 0.24f, 0.30f, 0.024f, 18, spin, r, g, b, 235);
                emitHorizontalRing(pose, vc, 0.0f, y, 0.0f, 0.255f, 0.285f, 0.012f, 18, -spin, br, bg, bb, 250);
                emitHorizontalRing(pose, vc, 0.0f, y, 0.0f, 0.21f, 0.34f, 0.010f, 18, spin, r, g, b, 75);
            }
            case CosmeticsLogic.HAT_LEAF_WREATH -> {
                float y = -0.44f;
                float rad = 0.285f;
                // Dark botanical vine circlet
                emitHorizontalRing(pose, vc, 0.0f, y, 0.0f, rad - 0.018f, rad + 0.018f, 0.024f, 16, 0.0f, dr, dg, db, 245);
                // 8 glowing botanical leaves sprouting around the circlet
                for (int i = 0; i < 8; i++) {
                    float a = (i / 8.0f) * TWO_PI + (float) Math.sin(age * 0.09f + i) * 0.04f;
                    float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
                    float lx = cos * (rad + 0.01f), lz = sin * (rad + 0.01f);
                    float tx = cos * (rad + 0.095f), tz = sin * (rad + 0.095f);
                    float ty = y - 0.075f - (i % 2 == 0 ? 0.025f : 0.0f);
                    float px = -sin * 0.042f, pz = cos * 0.042f;
                    int leafRgb = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, age, i * 0.08f);
                    int lr = (leafRgb >> 16) & 0xFF, lg = (leafRgb >> 8) & 0xFF, lb = leafRgb & 0xFF;
                    emitDoubleQuad(pose, vc,
                            lx, y + 0.01f, lz, br, bg, bb, 240,
                            (lx + tx) * 0.5f + px, (y + ty) * 0.5f, (lz + tz) * 0.5f + pz, lr, lg, lb, 225,
                            tx, ty, tz, br, bg, bb, 235,
                            (lx + tx) * 0.5f - px, (y + ty) * 0.5f, (lz + tz) * 0.5f - pz, lr, lg, lb, 225);
                }
            }
            case CosmeticsLogic.HAT_DEMON_HORNS -> {
                for (int side = -1; side <= 1; side += 2) {
                    float x0 = side * 0.19f, y0 = -0.45f, z0 = -0.06f;
                    float x1 = side * 0.30f, y1 = -0.58f, z1 = -0.10f;
                    float x2 = side * 0.35f, y2 = -0.74f, z2 = 0.02f;
                    float x3 = side * 0.29f, y3 = -0.84f, z3 = 0.14f;
                    emitBoxSegment(pose, vc, x0, y0, z0, x1, y1, z1, 0.045f, 0.036f, dr, dg, db, 250);
                    emitBoxSegment(pose, vc, x1, y1, z1, x2, y2, z2, 0.036f, 0.024f, r, g, b, 245);
                    emitBoxSegment(pose, vc, x2, y2, z2, x3, y3, z3, 0.024f, 0.008f, br, bg, bb, 250);
                }
            }
            case CosmeticsLogic.HAT_ROYAL_CROWN -> {
                int gold = 0xF59E0B;
                int goldBright = 0xFDE047;
                int gr = (gold >> 16) & 0xFF, gg = (gold >> 8) & 0xFF, gb = gold & 0xFF;
                int gbr = (goldBright >> 16) & 0xFF, gbg = (goldBright >> 8) & 0xFF, gbb = goldBright & 0xFF;
                float y = -0.52f;
                float rIn = 0.255f, rOut = 0.285f;
                emitHorizontalRing(pose, vc, 0.0f, y, 0.0f, rIn, rOut, 0.075f, 12, 0.0f, gr, gg, gb, 250);
                // 6 regal crown spikes + front inset gemstone tinted with chosen color
                for (int i = 0; i < 6; i++) {
                    float a = (i / 6.0f) * TWO_PI - (float) (Math.PI * 0.5);
                    float aL = a - 0.20f, aR = a + 0.20f;
                    float xL = (float) Math.cos(aL) * rOut, zL = (float) Math.sin(aL) * rOut;
                    float xR = (float) Math.cos(aR) * rOut, zR = (float) Math.sin(aR) * rOut;
                    float xTip = (float) Math.cos(a) * (rOut + 0.01f), zTip = (float) Math.sin(a) * (rOut + 0.01f);
                    float tipY = y - (i == 0 ? 0.17f : 0.12f);
                    emitDoubleQuad(pose, vc,
                            xL, y - 0.03f, zL, gr, gg, gb, 250,
                            xR, y - 0.03f, zR, gr, gg, gb, 250,
                            xTip, tipY, zTip, gbr, gbg, gbb, 255,
                            xTip, tipY, zTip, gbr, gbg, gbb, 255);
                }
                // Center jewel on forehead (-Z)
                emitCube(pose, vc, 0.0f, y - 0.035f, -rOut - 0.015f, 0.036f, 0.044f, 0.022f, r, g, b, 255);
            }
            case CosmeticsLogic.HAT_CYBER_EARS -> {
                // Sleek headband + 2 angled holographic cat ears
                emitHorizontalRing(pose, vc, 0.0f, -0.49f, 0.0f, 0.255f, 0.278f, 0.032f, 16, 0.0f, dr, dg, db, 250);
                for (int side = -1; side <= 1; side += 2) {
                    float bx = side * 0.16f, by = -0.50f, bz = 0.02f;
                    float ox = side * 0.28f, oy = -0.52f;
                    float tx = side * 0.26f, ty = -0.76f;
                    // Outer dark tech frame
                    emitDoubleQuad(pose, vc,
                            bx, by, bz, dr, dg, db, 250,
                            ox, oy, bz, dr, dg, db, 250,
                            tx, ty, bz, r, g, b, 250,
                            tx, ty, bz, r, g, b, 250);
                    // Glowing inner neon triangle
                    float ix0 = side * 0.185f, iy0 = -0.52f;
                    float ix1 = side * 0.255f, iy1 = -0.535f;
                    float itx = side * 0.245f, ity = -0.70f;
                    emitDoubleQuad(pose, vc,
                            ix0, iy0, bz - 0.012f, br, bg, bb, 235,
                            ix1, iy1, bz - 0.012f, r, g, b, 235,
                            itx, ity, bz - 0.012f, br, bg, bb, 245,
                            itx, ity, bz - 0.012f, br, bg, bb, 245);
                }
            }
            case CosmeticsLogic.HAT_KITSUNE_VISOR -> {
                // Curved futuristic fox visor sitting across brow/eyes (-Z face)
                float z = -0.275f;
                float y = -0.30f;
                // Main white-tinted/obsidian brow shell
                emitDoubleQuad(pose, vc,
                        -0.25f, y - 0.08f, z, dr, dg, db, 245,
                        0.25f, y - 0.08f, z, dr, dg, db, 245,
                        0.21f, y + 0.06f, z - 0.02f, dr, dg, db, 240,
                        -0.21f, y + 0.06f, z - 0.02f, dr, dg, db, 240);
                // Glowing slanted neon eyes
                for (int side = -1; side <= 1; side += 2) {
                    emitDoubleQuad(pose, vc,
                            side * 0.04f, y - 0.01f, z - 0.012f, br, bg, bb, 255,
                            side * 0.17f, y - 0.05f, z - 0.012f, r, g, b, 255,
                            side * 0.15f, y + 0.015f, z - 0.016f, r, g, b, 245,
                            side * 0.04f, y + 0.020f, z - 0.016f, br, bg, bb, 245);
                }
                // Side ear fins on visor
                for (int side = -1; side <= 1; side += 2) {
                    emitDoubleQuad(pose, vc,
                            side * 0.25f, y - 0.08f, z, r, g, b, 240,
                            side * 0.28f, y - 0.02f, z + 0.14f, dr, dg, db, 240,
                            side * 0.30f, y - 0.20f, z + 0.05f, br, bg, bb, 245,
                            side * 0.30f, y - 0.20f, z + 0.05f, br, bg, bb, 245);
                }
            }
            default -> {
            }
        }
    }

    // =========================================================================
    // 3. BACK GEAR & ARTICULATED TAILS (5 MODELS)
    // =========================================================================
    private static void renderBackGearOrTail(
            PoseStack.Pose pose, VertexConsumer vc,
            int mode, int colorPreset, int customRgb,
            float age, float walkPos, float walkSpeed, float backZ
    ) {
        int rgb = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, age, 0.0f);
        int bright = WingsLogic.blendRgb(rgb, 0xFFFFFF, 0.45f);
        int dark = WingsLogic.blendRgb(0x11151F, rgb, 0.18f);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int br = (bright >> 16) & 0xFF, bg = (bright >> 8) & 0xFF, bb = bright & 0xFF;
        int dr = (dark >> 16) & 0xFF, dg = (dark >> 8) & 0xFF, db = dark & 0xFF;

        switch (mode) {
            case CosmeticsLogic.GEAR_KATANA_SHEATH -> {
                // Diagonal lacquered scabbard across back + golden tsuba + wrapped tsuka hilt
                float z = backZ + 0.035f;
                // Scabbard body (lower-left to upper-right)
                emitBoxSegment(pose, vc, -0.30f, 0.80f, z, 0.22f, 0.06f, z, 0.032f, 0.034f, dr, dg, db, 252);
                // Glowing neon edge inlay along scabbard
                emitBoxSegment(pose, vc, -0.28f, 0.78f, z + 0.024f, 0.20f, 0.08f, z + 0.024f, 0.011f, 0.011f, r, g, b, 245);
                // Gold tsuba guard
                emitCube(pose, vc, 0.23f, 0.04f, z, 0.058f, 0.058f, 0.022f, 245, 190, 42, 255);
                // Wrapped hilt extending above right shoulder
                emitBoxSegment(pose, vc, 0.24f, 0.02f, z, 0.39f, -0.20f, z, 0.024f, 0.022f, 32, 34, 42, 255);
                emitBoxSegment(pose, vc, 0.26f, -0.01f, z + 0.016f, 0.37f, -0.17f, z + 0.016f, 0.010f, 0.010f, br, bg, bb, 250);
            }
            case CosmeticsLogic.GEAR_TECH_PACK -> {
                float z = backZ + 0.065f;
                // Central armored backpack chassis
                emitCube(pose, vc, 0.0f, 0.30f, z, 0.16f, 0.22f, 0.068f, dr, dg, db, 252);
                // Glowing energy reactor core
                float pulse = 0.72f + 0.28f * (float) Math.sin(age * 0.20f);
                emitCube(pose, vc, 0.0f, 0.28f, z + 0.065f, 0.068f * pulse, 0.085f * pulse, 0.016f, br, bg, bb, 250);
                // Twin side thruster pods + animated ion jet plumes
                float flame = 0.14f + 0.07f * (float) Math.sin(age * 0.55f);
                for (int side = -1; side <= 1; side += 2) {
                    float px = side * 0.20f;
                    emitCube(pose, vc, px, 0.30f, z, 0.058f, 0.18f, 0.058f, 34, 40, 52, 252);
                    emitCube(pose, vc, px, 0.20f, z + 0.048f, 0.025f, 0.09f, 0.015f, r, g, b, 245);
                    emitBoxSegment(pose, vc, px, 0.49f, z, px, 0.49f + flame, z + 0.03f, 0.036f, 0.006f, r, g, b, 190);
                    emitBoxSegment(pose, vc, px, 0.49f, z, px, 0.49f + flame * 0.65f, z + 0.02f, 0.020f, 0.004f, br, bg, bb, 245);
                }
            }
            case CosmeticsLogic.GEAR_VINE_TAIL, CosmeticsLogic.GEAR_DEMON_TAIL, CosmeticsLogic.GEAR_FOX_TAIL -> {
                renderArticulatedTail(pose, vc, mode, r, g, b, br, bg, bb, dr, dg, db, age, walkPos, walkSpeed, backZ);
            }
            default -> {
            }
        }
    }

    private static void renderArticulatedTail(
            PoseStack.Pose pose, VertexConsumer vc,
            int mode, int r, int g, int b, int br, int bg, int bb, int dr, int dg, int db,
            float age, float walkPos, float walkSpeed, float backZ
    ) {
        int segs = 7;
        float swayAmp = 0.08f + Math.min(0.18f, walkSpeed * 0.22f);
        float swayFreq = age * 0.14f + walkPos * 0.45f;

        float px = 0.0f;
        float py = 0.68f;
        float pz = backZ + 0.01f;

        for (int i = 0; i < segs; i++) {
            float t = (float) (i + 1) / segs;
            float nx = (float) Math.sin(swayFreq - t * 2.2f) * swayAmp * t * 1.8f;
            float stepZ = 0.105f;
            float stepY = (mode == CosmeticsLogic.GEAR_FOX_TAIL)
                    ? (0.045f - 0.105f * t) // sweeps down then curls gracefully up
                    : (0.055f - 0.085f * t);
            float ny = py + stepY;
            float nz = pz + stepZ;

            float rad0;
            float rad1;
            int sr, sg, sb;
            if (mode == CosmeticsLogic.GEAR_FOX_TAIL) {
                float env0 = (float) Math.sin(((float) i / segs) * Math.PI * 0.85f + 0.25f);
                float env1 = (float) Math.sin(t * Math.PI * 0.85f + 0.25f);
                rad0 = 0.032f + 0.058f * env0;
                rad1 = (i == segs - 1) ? 0.014f : (0.032f + 0.058f * env1);
                boolean tip = i >= segs - 2;
                sr = tip ? 248 : r;
                sg = tip ? 250 : g;
                sb = tip ? 252 : b;
            } else if (mode == CosmeticsLogic.GEAR_VINE_TAIL) {
                rad0 = 0.026f * (1.0f - 0.35f * ((float) i / segs));
                rad1 = 0.026f * (1.0f - 0.45f * t);
                sr = r; sg = g; sb = b;
            } else {
                // Demon tail
                rad0 = 0.028f * (1.0f - 0.40f * ((float) i / segs));
                rad1 = 0.028f * (1.0f - 0.48f * t);
                sr = dr; sg = dg; sb = db;
            }

            emitBoxSegment(pose, vc, px, py, pz, nx, ny, nz, rad0, rad1, sr, sg, sb, 248);

            // Extra flourishes per tail type
            if (mode == CosmeticsLogic.GEAR_VINE_TAIL && (i == 2 || i == 4 || i == 6)) {
                float lx = (i % 2 == 0) ? 0.085f : -0.085f;
                emitDoubleQuad(pose, vc,
                        nx, ny, nz, br, bg, bb, 245,
                        nx + lx * 0.5f, ny - 0.05f, nz + 0.02f, r, g, b, 230,
                        nx + lx, ny - 0.02f, nz + 0.07f, br, bg, bb, 235,
                        nx + lx * 0.4f, ny + 0.03f, nz + 0.04f, r, g, b, 225);
            } else if (mode == CosmeticsLogic.GEAR_DEMON_TAIL && i == segs - 1) {
                // Glowing demon spade arrow tip
                emitDoubleQuad(pose, vc,
                        nx, ny, nz, r, g, b, 250,
                        nx + 0.095f, ny - 0.02f, nz + 0.04f, r, g, b, 240,
                        nx, ny - 0.04f, nz + 0.16f, br, bg, bb, 255,
                        nx - 0.095f, ny - 0.02f, nz + 0.04f, r, g, b, 240);
            }

            px = nx; py = ny; pz = nz;
        }
    }

    // =========================================================================
    // 4. SHOULDER PETS & ORBITING COMPANIONS (5 MODELS)
    // =========================================================================
    private static void renderCompanionPet(
            PoseStack.Pose pose, VertexConsumer vc,
            int mode, int side, int colorPreset, int customRgb, float age
    ) {
        float bob = (float) Math.sin(age * 0.16f) * 0.055f;
        float cx;
        float cy;
        float cz;
        if (side == CosmeticsLogic.PET_SIDE_ORBIT) {
            float orbit = age * 0.07f;
            cx = (float) Math.cos(orbit) * 0.72f;
            cz = (float) Math.sin(orbit) * 0.72f;
            cy = -0.18f + bob * 1.4f;
        } else {
            float sign = (side == CosmeticsLogic.PET_SIDE_LEFT) ? 1.0f : -1.0f;
            cx = sign * 0.46f;
            cy = -0.20f + bob;
            cz = -0.02f + (float) Math.cos(age * 0.11f) * 0.03f;
        }

        int rgb = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, age, 0.0f);
        int bright = WingsLogic.blendRgb(rgb, 0xFFFFFF, 0.55f);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int br = (bright >> 16) & 0xFF, bg = (bright >> 8) & 0xFF, bb = bright & 0xFF;

        switch (mode) {
            case CosmeticsLogic.PET_EMERALD_WISP -> {
                // Crystalline glowing wisp core + halo + 2 orbiting motes
                emitCube(pose, vc, cx, cy, cz, 0.075f, 0.095f, 0.075f, r, g, b, 230);
                emitCube(pose, vc, cx, cy, cz, 0.045f, 0.060f, 0.045f, br, bg, bb, 255);
                emitHorizontalRing(pose, vc, cx, cy, cz, 0.11f, 0.14f, 0.012f, 12, age * 0.12f, br, bg, bb, 180);
                for (int k = 0; k < 2; k++) {
                    float a = age * 0.22f + k * (float) Math.PI;
                    emitCube(pose, vc, cx + (float) Math.cos(a) * 0.16f, cy - 0.03f + k * 0.06f, cz + (float) Math.sin(a) * 0.16f,
                            0.022f, 0.022f, 0.022f, br, bg, bb, 235);
                }
            }
            case CosmeticsLogic.PET_CYBER_DRONE -> {
                // Sleek dark chassis + glowing cyclops optic + twin spinning rotors
                emitCube(pose, vc, cx, cy, cz, 0.105f, 0.055f, 0.085f, 24, 30, 42, 252);
                emitCube(pose, vc, cx, cy, cz - 0.088f, 0.045f, 0.024f, 0.012f, br, bg, bb, 255);
                emitCube(pose, vc, cx, cy + 0.055f, cz, 0.06f, 0.012f, 0.05f, r, g, b, 210);
                for (int s = -1; s <= 1; s += 2) {
                    float rx = cx + s * 0.135f;
                    emitBoxSegment(pose, vc, cx + s * 0.09f, cy, cz, rx, cy - 0.02f, cz, 0.012f, 0.010f, 55, 65, 82, 250);
                    emitHorizontalRing(pose, vc, rx, cy - 0.035f, cz, 0.02f, 0.065f, 0.008f, 8, age * 0.9f * s, r, g, b, 200);
                }
            }
            case CosmeticsLogic.PET_MINI_FOX -> {
                // Perched blocky fox (warm orange or custom tint)
                int fur = (colorPreset == WingsLogic.COLOR_CUSTOM || colorPreset == WingsLogic.COLOR_CHROMA) ? rgb : 0xEA6A1F;
                int fr = (fur >> 16) & 0xFF, fg = (fur >> 8) & 0xFF, fb = fur & 0xFF;
                float py = cy + 0.04f;
                // Body & head
                emitCube(pose, vc, cx, py + 0.04f, cz + 0.04f, 0.075f, 0.055f, 0.105f, fr, fg, fb, 252);
                emitCube(pose, vc, cx, py - 0.02f, cz - 0.07f, 0.070f, 0.055f, 0.065f, fr, fg, fb, 252);
                // White snout & dark nose
                emitCube(pose, vc, cx, py + 0.005f, cz - 0.15f, 0.036f, 0.024f, 0.028f, 245, 240, 235, 255);
                emitCube(pose, vc, cx, py - 0.002f, cz - 0.18f, 0.014f, 0.012f, 0.010f, 24, 24, 28, 255);
                // Pointed ears
                emitCube(pose, vc, cx - 0.048f, py - 0.09f, cz - 0.06f, 0.022f, 0.032f, 0.016f, 34, 28, 28, 255);
                emitCube(pose, vc, cx + 0.048f, py - 0.09f, cz - 0.06f, 0.022f, 0.032f, 0.016f, 34, 28, 28, 255);
                // Fluffy tail with white tip
                emitCube(pose, vc, cx, py + 0.03f, cz + 0.18f, 0.044f, 0.040f, 0.065f, fr, fg, fb, 252);
                emitCube(pose, vc, cx, py + 0.03f, cz + 0.25f, 0.036f, 0.034f, 0.030f, 250, 250, 252, 255);
            }
            case CosmeticsLogic.PET_MINI_ALLAY -> {
                // Luminous mini spirit + fluttering translucent wings
                emitCube(pose, vc, cx, cy - 0.055f, cz, 0.062f, 0.062f, 0.062f, br, bg, bb, 248);
                emitCube(pose, vc, cx, cy + 0.040f, cz, 0.042f, 0.058f, 0.036f, r, g, b, 235);
                // Eyes
                emitCube(pose, vc, cx - 0.022f, cy - 0.055f, cz - 0.064f, 0.012f, 0.018f, 0.006f, 255, 255, 255, 255);
                emitCube(pose, vc, cx + 0.022f, cy - 0.055f, cz - 0.064f, 0.012f, 0.018f, 0.006f, 255, 255, 255, 255);
                float flap = (float) Math.sin(age * 0.72f) * 0.065f;
                for (int s = -1; s <= 1; s += 2) {
                    emitDoubleQuad(pose, vc,
                            cx + s * 0.04f, cy + 0.01f, cz + 0.03f, br, bg, bb, 210,
                            cx + s * 0.16f, cy - 0.03f + flap, cz + 0.12f, r, g, b, 155,
                            cx + s * 0.13f, cy + 0.09f + flap, cz + 0.10f, r, g, b, 120,
                            cx + s * 0.04f, cy + 0.07f, cz + 0.03f, br, bg, bb, 185);
                }
            }
            case CosmeticsLogic.PET_MINI_BEE -> {
                // Striped cuboid bee + rapid buzzing translucent wings
                emitCube(pose, vc, cx, cy, cz, 0.072f, 0.065f, 0.095f, 245, 200, 40, 252);
                emitCube(pose, vc, cx, cy, cz - 0.02f, 0.076f, 0.068f, 0.022f, 38, 30, 24, 255);
                emitCube(pose, vc, cx, cy, cz + 0.045f, 0.076f, 0.068f, 0.022f, 38, 30, 24, 255);
                // Glowing accent eyes
                emitCube(pose, vc, cx - 0.035f, cy - 0.01f, cz - 0.098f, 0.015f, 0.018f, 0.008f, br, bg, bb, 255);
                emitCube(pose, vc, cx + 0.035f, cy - 0.01f, cz - 0.098f, 0.015f, 0.018f, 0.008f, br, bg, bb, 255);
                float buzz = (float) Math.sin(age * 1.45f) * 0.055f;
                for (int s = -1; s <= 1; s += 2) {
                    emitDoubleQuad(pose, vc,
                            cx + s * 0.02f, cy - 0.07f, cz - 0.02f, 235, 250, 255, 195,
                            cx + s * 0.16f, cy - 0.12f + buzz, cz + 0.03f, 220, 245, 255, 150,
                            cx + s * 0.14f, cy - 0.10f + buzz, cz + 0.09f, 220, 245, 255, 130,
                            cx + s * 0.02f, cy - 0.07f, cz + 0.05f, 235, 250, 255, 185);
                }
            }
            default -> {
            }
        }
    }

    // =========================================================================
    // 5. 3D PLAYER AURAS (4 MODELS)
    // =========================================================================
    private static void renderPlayerAura(
            PoseStack.Pose pose, VertexConsumer vc,
            int mode, int colorPreset, int customRgb,
            float radius, float speed, float age
    ) {
        int rgb = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, age, 0.0f);
        int bright = WingsLogic.blendRgb(rgb, 0xFFFFFF, 0.50f);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int br = (bright >> 16) & 0xFF, bg = (bright >> 8) & 0xFF, bb = bright & 0xFF;
        float t = age * 0.06f * speed;
        float footY = 1.46f; // In Mojang PlayerModel space, +Y is down, feet sit near y ~ 1.50

        switch (mode) {
            case CosmeticsLogic.AURA_GROUND_RUNE -> {
                // Outer & inner counter-rotating octagonal rune rings at player's feet
                emitHorizontalRing(pose, vc, 0.0f, footY, 0.0f, radius * 0.88f, radius, 0.010f, 16, t, r, g, b, 210);
                emitHorizontalRing(pose, vc, 0.0f, footY - 0.01f, 0.0f, radius * 0.56f, radius * 0.64f, 0.008f, 8, -t * 1.4f, br, bg, bb, 225);
                // 4 rotating sigil spokes
                for (int i = 0; i < 4; i++) {
                    float a = t + i * (float) (Math.PI * 0.5);
                    float c = (float) Math.cos(a), s = (float) Math.sin(a);
                    float nx = -s * 0.022f, nz = c * 0.022f;
                    emitDoubleQuad(pose, vc,
                            c * radius * 0.60f - nx, footY - 0.005f, s * radius * 0.60f - nz, br, bg, bb, 195,
                            c * radius * 0.60f + nx, footY - 0.005f, s * radius * 0.60f + nz, br, bg, bb, 195,
                            c * radius * 0.92f + nx, footY - 0.005f, s * radius * 0.92f + nz, r, g, b, 175,
                            c * radius * 0.92f - nx, footY - 0.005f, s * radius * 0.92f - nz, r, g, b, 175);
                }
            }
            case CosmeticsLogic.AURA_DOUBLE_HELIX -> {
                int steps = 20;
                for (int strand = 0; strand < 2; strand++) {
                    float phaseOff = strand * (float) Math.PI;
                    for (int i = 0; i < steps; i++) {
                        float u0 = (float) i / steps;
                        float u1 = (float) (i + 1) / steps;
                        float a0 = u0 * TWO_PI * 1.6f + t * 2.2f + phaseOff;
                        float a1 = u1 * TWO_PI * 1.6f + t * 2.2f + phaseOff;
                        float r0 = radius * (0.76f + 0.14f * (float) Math.sin(u0 * Math.PI));
                        float r1 = radius * (0.76f + 0.14f * (float) Math.sin(u1 * Math.PI));
                        float y0 = footY - u0 * 1.75f;
                        float y1 = footY - u1 * 1.75f;
                        int segRgb = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, age, u0 * 0.35f + strand * 0.15f);
                        int sr = (segRgb >> 16) & 0xFF, sg = (segRgb >> 8) & 0xFF, sb = segRgb & 0xFF;
                        int a = Math.round((float) Math.sin(u0 * Math.PI) * 215.0f);
                        if (a < 18) continue;
                        float hw = 0.032f;
                        emitDoubleQuad(pose, vc,
                                (float) Math.cos(a0) * r0, y0 - hw, (float) Math.sin(a0) * r0, sr, sg, sb, a,
                                (float) Math.cos(a0) * r0, y0 + hw, (float) Math.sin(a0) * r0, sr, sg, sb, a,
                                (float) Math.cos(a1) * r1, y1 + hw, (float) Math.sin(a1) * r1, br, bg, bb, a,
                                (float) Math.cos(a1) * r1, y1 - hw, (float) Math.sin(a1) * r1, br, bg, bb, a);
                    }
                }
            }
            case CosmeticsLogic.AURA_LEAF_ORBIT -> {
                for (int i = 0; i < 4; i++) {
                    float a = t * 1.5f + i * (float) (Math.PI * 0.5);
                    float lx = (float) Math.cos(a) * radius * 0.85f;
                    float lz = (float) Math.sin(a) * radius * 0.85f;
                    float ly = 0.45f + (float) Math.sin(t * 2.4f + i * 1.6f) * 0.36f;
                    int leafRgb = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, age, i * 0.15f);
                    int lr = (leafRgb >> 16) & 0xFF, lg = (leafRgb >> 8) & 0xFF, lb = leafRgb & 0xFF;
                    float tx = -(float) Math.sin(a) * 0.095f;
                    float tz = (float) Math.cos(a) * 0.095f;
                    emitDoubleQuad(pose, vc,
                            lx - tx, ly, lz - tz, br, bg, bb, 235,
                            lx, ly - 0.105f, lz, lr, lg, lb, 225,
                            lx + tx, ly, lz + tz, br, bg, bb, 235,
                            lx, ly + 0.085f, lz, lr, lg, lb, 200);
                }
            }
            case CosmeticsLogic.AURA_PULSE_HALO -> {
                for (int ring = 0; ring < 3; ring++) {
                    float p = (t * 0.55f + ring / 3.0f) % 1.0f;
                    float rr = radius * (0.45f + 0.70f * p);
                    float ry = footY - p * 1.55f;
                    int alpha = Math.round((float) Math.sin(p * Math.PI) * 215.0f);
                    if (alpha < 15) continue;
                    emitHorizontalRing(pose, vc, 0.0f, ry, 0.0f, rr - 0.025f, rr + 0.025f, 0.010f, 18, t * (ring + 1), r, g, b, alpha);
                }
            }
            default -> {
            }
        }
    }

    // =========================================================================
    // LOW-LEVEL ZERO-ALLOCATION GEOMETRY PRIMITIVES
    // =========================================================================
    private static void emitHorizontalRing(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz,
            float rIn, float rOut, float halfH,
            int segments, float phase,
            int r, int g, int b, int a
    ) {
        for (int i = 0; i < segments; i++) {
            float a0 = phase + ((float) i / segments) * TWO_PI;
            float a1 = phase + ((float) (i + 1) / segments) * TWO_PI;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            emitDoubleQuad(pose, vc,
                    cx + c0 * rIn, cy, cz + s0 * rIn, r, g, b, a,
                    cx + c0 * rOut, cy, cz + s0 * rOut, r, g, b, a,
                    cx + c1 * rOut, cy, cz + s1 * rOut, r, g, b, a,
                    cx + c1 * rIn, cy, cz + s1 * rIn, r, g, b, a);
            if (halfH > 0.002f) {
                emitDoubleQuad(pose, vc,
                        cx + c0 * rOut, cy - halfH, cz + s0 * rOut, r, g, b, a,
                        cx + c1 * rOut, cy - halfH, cz + s1 * rOut, r, g, b, a,
                        cx + c1 * rOut, cy + halfH, cz + s1 * rOut, r, g, b, a,
                        cx + c0 * rOut, cy + halfH, cz + s0 * rOut, r, g, b, a);
            }
        }
    }

    private static void emitCube(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz,
            float hx, float hy, float hz,
            int r, int g, int b, int a
    ) {
        float x0 = cx - hx, x1 = cx + hx;
        float y0 = cy - hy, y1 = cy + hy;
        float z0 = cz - hz, z1 = cz + hz;
        // Front / Back
        emitQuad4(pose, vc, x0, y0, z0, r, g, b, a, x1, y0, z0, r, g, b, a, x1, y1, z0, r, g, b, a, x0, y1, z0, r, g, b, a);
        emitQuad4(pose, vc, x1, y0, z1, r, g, b, a, x0, y0, z1, r, g, b, a, x0, y1, z1, r, g, b, a, x1, y1, z1, r, g, b, a);
        // Left / Right (slightly shaded for depth)
        int sr = (int) (r * 0.86f), sg = (int) (g * 0.86f), sb = (int) (b * 0.86f);
        emitQuad4(pose, vc, x0, y0, z1, sr, sg, sb, a, x0, y0, z0, sr, sg, sb, a, x0, y1, z0, sr, sg, sb, a, x0, y1, z1, sr, sg, sb, a);
        emitQuad4(pose, vc, x1, y0, z0, sr, sg, sb, a, x1, y0, z1, sr, sg, sb, a, x1, y1, z1, sr, sg, sb, a, x1, y1, z0, sr, sg, sb, a);
        // Top / Bottom
        int tr = Math.min(255, (int) (r * 1.08f)), tg = Math.min(255, (int) (g * 1.08f)), tb = Math.min(255, (int) (b * 1.08f));
        emitQuad4(pose, vc, x0, y0, z1, tr, tg, tb, a, x1, y0, z1, tr, tg, tb, a, x1, y0, z0, tr, tg, tb, a, x0, y0, z0, tr, tg, tb, a);
        emitQuad4(pose, vc, x0, y1, z0, sr, sg, sb, a, x1, y1, z0, sr, sg, sb, a, x1, y1, z1, sr, sg, sb, a, x0, y1, z1, sr, sg, sb, a);
    }

    private static void emitBoxSegment(
            PoseStack.Pose pose, VertexConsumer vc,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float r0, float r1,
            int cr, int cg, int cb, int ca
    ) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0e-4f) {
            return;
        }
        float inv = 1.0f / len;
        float ux = dx * inv, uy = dy * inv, uz = dz * inv;
        float refX = Math.abs(uy) < 0.9f ? 0.0f : 1.0f;
        float refY = Math.abs(uy) < 0.9f ? 1.0f : 0.0f;
        float ax = uy * 0.0f - uz * refY;
        float ay = uz * refX - ux * 0.0f;
        float az = ux * refY - uy * refX;
        float aLen = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (aLen < 1.0e-4f) return;
        ax /= aLen; ay /= aLen; az /= aLen;
        float bx = uy * az - uz * ay;
        float by = uz * ax - ux * az;
        float bz = ux * ay - uy * ax;

        float[] ox = {1f, -1f, -1f, 1f};
        float[] oy = {1f, 1f, -1f, -1f};
        for (int i = 0; i < 4; i++) {
            int n = (i + 1) & 3;
            float s0x = x0 + (ax * ox[i] + bx * oy[i]) * r0;
            float s0y = y0 + (ay * ox[i] + by * oy[i]) * r0;
            float s0z = z0 + (az * ox[i] + bz * oy[i]) * r0;
            float s1x = x0 + (ax * ox[n] + bx * oy[n]) * r0;
            float s1y = y0 + (ay * ox[n] + by * oy[n]) * r0;
            float s1z = z0 + (az * ox[n] + bz * oy[n]) * r0;

            float e0x = x1 + (ax * ox[i] + bx * oy[i]) * r1;
            float e0y = y1 + (ay * ox[i] + by * oy[i]) * r1;
            float e0z = z1 + (az * ox[i] + bz * oy[i]) * r1;
            float e1x = x1 + (ax * ox[n] + bx * oy[n]) * r1;
            float e1y = y1 + (ay * ox[n] + by * oy[n]) * r1;
            float e1z = z1 + (az * ox[n] + bz * oy[n]) * r1;

            int fr = (i & 1) == 0 ? cr : (int) (cr * 0.84f);
            int fg = (i & 1) == 0 ? cg : (int) (cg * 0.84f);
            int fb = (i & 1) == 0 ? cb : (int) (cb * 0.84f);
            emitDoubleQuad(pose, vc,
                    s0x, s0y, s0z, fr, fg, fb, ca,
                    s1x, s1y, s1z, fr, fg, fb, ca,
                    e1x, e1y, e1z, fr, fg, fb, ca,
                    e0x, e0y, e0z, fr, fg, fb, ca);
        }
    }

    private static void emitDoubleQuad(
            PoseStack.Pose pose, VertexConsumer vc,
            float x0, float y0, float z0, int r0, int g0, int b0, int a0,
            float x1, float y1, float z1, int r1, int g1, int b1, int a1,
            float x2, float y2, float z2, int r2, int g2, int b2, int a2,
            float x3, float y3, float z3, int r3, int g3, int b3, int a3
    ) {
        emitQuad4(pose, vc, x0, y0, z0, r0, g0, b0, a0, x1, y1, z1, r1, g1, b1, a1, x2, y2, z2, r2, g2, b2, a2, x3, y3, z3, r3, g3, b3, a3);
        emitQuad4(pose, vc, x3, y3, z3, r3, g3, b3, a3, x2, y2, z2, r2, g2, b2, a2, x1, y1, z1, r1, g1, b1, a1, x0, y0, z0, r0, g0, b0, a0);
    }

    private static void emitQuad4(
            PoseStack.Pose pose, VertexConsumer vc,
            float x0, float y0, float z0, int r0, int g0, int b0, int a0,
            float x1, float y1, float z1, int r1, int g1, int b1, int a1,
            float x2, float y2, float z2, int r2, int g2, int b2, int a2,
            float x3, float y3, float z3, int r3, int g3, int b3, int a3
    ) {
        vc.addVertex(pose, x0, y0, z0).setColor(r0, g0, b0, a0);
        vc.addVertex(pose, x1, y1, z1).setColor(r1, g1, b1, a1);
        vc.addVertex(pose, x2, y2, z2).setColor(r2, g2, b2, a2);
        vc.addVertex(pose, x3, y3, z3).setColor(r3, g3, b3, a3);
    }
}
