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
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
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
    private static final float[] BOX_SEG_OX = {1.0f, -1.0f, -1.0f, 1.0f};
    private static final float[] BOX_SEG_OY = {1.0f, 1.0f, -1.0f, -1.0f};

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
        boolean isPreview = state.id == WingsLogic.PREVIEW_ENTITY_ID;
        boolean isSelf = false;
        String remoteName = null;
        Minecraft mc = Minecraft.getInstance();
        if (isPreview) {
            loadout = CosmeticsLogic.getPreviewLoadout();
            if (loadout == null) {
                return;
            }
        } else {
            isSelf = mc != null && mc.player != null && state.id == mc.player.getId();
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

        final int gradMode = loadout.gradientMode();
        final int gradSec = loadout.gradientSecondaryRgb();
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
            final float cloakTopY = CosmeticsLogic.computeCloakTopY(hasChest);
            final boolean crouching = state.isCrouching;
            final Identifier customTex = (mode == CosmeticsLogic.CLOAK_CUSTOM_PNG && (isPreview || isSelf))
                    ? CustomCapeManager.resolveActiveCapeTexture(mc, cfg)
                    : null;
            final boolean borderGlow = (mode != CosmeticsLogic.CLOAK_CUSTOM_PNG) || cfg.cosmeticCustomCapeBorderGlow;

            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
                CosmeticsLogic.pushRenderGradient(gradMode, gradSec);
                try {
                    renderCloak(pose, vc, mode, colorPreset, customRgb, gradMode, gradSec, age, flap, lean, lean2,
                            walkSpeed, cloakZ, cloakTopY, crouching, customTex != null, borderGlow);
                } finally {
                    CosmeticsLogic.clearRenderGradient();
                }
            });
            if (customTex != null) {
                final int light = packedLight;
                collector.submitCustomGeometry(poseStack, RenderTypes.entityTranslucent(customTex), (pose, vc) ->
                        renderCustomPngCloakOverlay(pose, vc, light, age, flap, lean, lean2, walkSpeed, cloakZ, cloakTopY, crouching));
            }
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
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
                CosmeticsLogic.pushRenderGradient(gradMode, gradSec);
                try {
                    renderHeadwear(pose, vc, mode, colorPreset, customRgb, age);
                } finally {
                    CosmeticsLogic.clearRenderGradient();
                }
            });
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
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
                CosmeticsLogic.pushRenderGradient(gradMode, gradSec);
                try {
                    renderBackGearOrTail(pose, vc, mode, colorPreset, customRgb, age, walkPos, walkSpeed, backZ);
                } finally {
                    CosmeticsLogic.clearRenderGradient();
                }
            });
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
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
                CosmeticsLogic.pushRenderGradient(gradMode, gradSec);
                try {
                    renderCompanionPet(pose, vc, mode, side, colorPreset, customRgb, age);
                } finally {
                    CosmeticsLogic.clearRenderGradient();
                }
            });
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
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
                CosmeticsLogic.pushRenderGradient(gradMode, gradSec);
                try {
                    renderPlayerAura(pose, vc, mode, colorPreset, customRgb, radius, speed, age);
                } finally {
                    CosmeticsLogic.clearRenderGradient();
                }
            });
            poseStack.popPose();
        }

        // 6. 3D Emote Props (Floating Arcane Rune Book for Rune Meditate emote)
        int activeEmote = CosmeticsLogic.resolveActiveEmote(isPreview, isSelf, remoteName, System.currentTimeMillis());
        if (CosmeticsLogic.hasEmoteProp(activeEmote)) {
            final int accentRgb = (JelloTheme.ACCENT != 0 ? (JelloTheme.ACCENT & 0xFFFFFF) : 0x34D399);
            poseStack.pushPose();
            getParentModel().body.translateAndRotate(poseStack);
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
                CosmeticsLogic.pushRenderGradient(gradMode, gradSec);
                try {
                    renderEmoteRuneBook(pose, vc, accentRgb, gradSec, age);
                } finally {
                    CosmeticsLogic.clearRenderGradient();
                }
            });
            poseStack.popPose();
        }
    }

    // =========================================================================
    // 1. ANIMATED 3D CLOAKS (INCLUDING CUSTOM PNG CAPE + 2-COLOR GRADIENT)
    // =========================================================================
    private static void renderCloak(
            PoseStack.Pose pose, VertexConsumer vc,
            int mode, int colorPreset, int customRgb,
            int gradMode, int gradSec,
            float age, float capeFlap, float capeLean, float capeLean2,
            float walkSpeed, float backZ, float topY, boolean crouching,
            boolean hasCustomTexture, boolean borderGlow
    ) {
        float halfW = 0.28f;
        float totalLen = 0.96f;
        float thickness = 0.016f;
        int segs = 6;

        float leanRad = CosmeticsLogic.computeCloakLeanRad(capeLean, capeFlap, walkSpeed, crouching);
        float safeLean2 = Float.isFinite(capeLean2) ? capeLean2 : 0.0f;
        float sideSway = Math.max(-0.18f, Math.min(0.18f, safeLean2 * 0.007f));

        int primary = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, age, 0.0f);
        int darkCloth = switch (mode) {
            case CosmeticsLogic.CLOAK_OBSIDIAN_ROYAL -> 0x0E0D14;
            case CosmeticsLogic.CLOAK_CYBER_GRID -> 0x0A111A;
            case CosmeticsLogic.CLOAK_CUSTOM_PNG -> 0x0C1018;
            case CosmeticsLogic.CLOAK_CHROMA_NEBULA -> WingsLogic.blendRgb(0x0D0A22, primary, 0.32f);
            default -> WingsLogic.blendRgb(0x071B14, primary, 0.28f);
        };
        int trimRgb = switch (mode) {
            case CosmeticsLogic.CLOAK_OBSIDIAN_ROYAL -> 0xFACC15;
            case CosmeticsLogic.CLOAK_CHROMA_NEBULA -> WingsLogic.resolveRgb(WingsLogic.COLOR_CHROMA, customRgb, age, 0.25f, false);
            default -> WingsLogic.blendRgb(CosmeticsLogic.resolveGradientRgb(primary, gradSec, gradMode, age, 0.65f), 0xFFFFFF, 0.22f);
        };
        int dr = (darkCloth >> 16) & 0xFF, dg = (darkCloth >> 8) & 0xFF, db = darkCloth & 0xFF;
        int tr = (trimRgb >> 16) & 0xFF, tg = (trimRgb >> 8) & 0xFF, tb = trimRgb & 0xFF;

        float prevY = topY;
        float prevZ = backZ;
        float prevXOff = 0.0f;
        float prevRipple = 0.0f;

        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            float curLen = totalLen / segs;
            float segAngle = leanRad * (0.78f + 0.34f * t1);
            float baseStepZ = (float) Math.sin(segAngle) * curLen;
            float wave = 0.5f + 0.5f * (float) Math.sin(age * 0.16f - t1 * 3.0f);
            float ripple = wave * (0.008f + t1 * 0.014f);
            float stepZ = Math.max(0.018f, baseStepZ + (ripple - prevRipple) * 0.65f);

            float nextY = prevY + (float) Math.cos(segAngle) * curLen;
            float nextZ = prevZ + stepZ;
            float nextXOff = sideSway * t1 * t1;

            float w0 = halfW * (1.0f + 0.10f * t0);
            float w1 = halfW * (1.0f + 0.10f * t1);

            int segRgb0;
            int segRgb1;
            if (mode == CosmeticsLogic.CLOAK_CHROMA_NEBULA) {
                segRgb0 = WingsLogic.resolveRgb(WingsLogic.COLOR_CHROMA, customRgb, age, t0 * 0.42f, false);
                segRgb1 = WingsLogic.resolveRgb(WingsLogic.COLOR_CHROMA, customRgb, age, t1 * 0.42f, false);
            } else {
                int g0Rgb = CosmeticsLogic.resolveGradientRgb(primary, gradSec, gradMode, age, t0);
                int g1Rgb = CosmeticsLogic.resolveGradientRgb(primary, gradSec, gradMode, age, t1);
                segRgb0 = WingsLogic.blendRgb(darkCloth, g0Rgb, 0.20f + 0.42f * t0);
                segRgb1 = WingsLogic.blendRgb(darkCloth, g1Rgb, 0.20f + 0.42f * t1);
            }

            int r0 = (segRgb0 >> 16) & 0xFF, g0 = (segRgb0 >> 8) & 0xFF, b0 = segRgb0 & 0xFF;
            int r1 = (segRgb1 >> 16) & 0xFF, g1 = (segRgb1 >> 8) & 0xFF, b1 = segRgb1 & 0xFF;

            float oz0 = prevZ + thickness;
            float oz1 = nextZ + thickness;

            // Inner dark cloth lining & outer patterned cloth face with solid 3D separation
            emitDoubleQuad(pose, vc,
                    prevXOff - w0, prevY, prevZ, dr, dg, db, 240,
                    prevXOff + w0, prevY, prevZ, dr, dg, db, 240,
                    nextXOff + w1, nextY, nextZ, dr, dg, db, 234,
                    nextXOff - w1, nextY, nextZ, dr, dg, db, 234);

            if (!hasCustomTexture) {
                emitDoubleQuad(pose, vc,
                        prevXOff - w0, prevY, oz0, r0, g0, b0, 248,
                        prevXOff + w0, prevY, oz0, r0, g0, b0, 248,
                        nextXOff + w1, nextY, oz1, r1, g1, b1, 240,
                        nextXOff - w1, nextY, oz1, r1, g1, b1, 240);
            }

            if (i == 0 && borderGlow) {
                emitDoubleQuad(pose, vc,
                        prevXOff - w0, prevY, prevZ, tr, tg, tb, 242,
                        prevXOff + w0, prevY, prevZ, tr, tg, tb, 242,
                        prevXOff + w0, prevY, oz0, tr, tg, tb, 245,
                        prevXOff - w0, prevY, oz0, tr, tg, tb, 245);
            }

            // Left & right side thickness caps + luminous outer side trim borders
            float trimW = 0.024f;
            for (int side = -1; side <= 1; side += 2) {
                float sx0 = prevXOff + side * w0;
                float sx1 = nextXOff + side * w1;
                float ix0 = prevXOff + side * (w0 - trimW);
                float ix1 = nextXOff + side * (w1 - trimW);
                int er = borderGlow ? tr : dr, eg = borderGlow ? tg : dg, eb = borderGlow ? tb : db;
                emitDoubleQuad(pose, vc,
                        sx0, prevY, prevZ, er, eg, eb, 220,
                        sx0, prevY, oz0, er, eg, eb, 235,
                        sx1, nextY, oz1, er, eg, eb, 230,
                        sx1, nextY, nextZ, er, eg, eb, 215);
                if (borderGlow) {
                    emitDoubleQuad(pose, vc,
                            ix0, prevY, oz0 + 0.005f, tr, tg, tb, 245,
                            sx0, prevY, oz0 + 0.005f, tr, tg, tb, 245,
                            sx1, nextY, oz1 + 0.005f, tr, tg, tb, 235,
                            ix1, nextY, oz1 + 0.005f, tr, tg, tb, 235);
                }
            }

            // Cyber Grid horizontal scanlines
            if (mode == CosmeticsLogic.CLOAK_CYBER_GRID) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(age * 0.22f - i * 0.9f);
                int alpha = Math.round(135 + 110 * pulse);
                emitDoubleQuad(pose, vc,
                        nextXOff - w1 + 0.02f, nextY - 0.010f, oz1 + 0.006f, tr, tg, tb, alpha,
                        nextXOff + w1 - 0.02f, nextY - 0.010f, oz1 + 0.006f, tr, tg, tb, alpha,
                        nextXOff + w1 - 0.02f, nextY + 0.008f, oz1 + 0.006f, tr, tg, tb, alpha,
                        nextXOff - w1 + 0.02f, nextY + 0.008f, oz1 + 0.006f, tr, tg, tb, alpha);
            }

            // Sestia Leaf 7-point glowing emblem on mid-back segments (i == 2)
            if (mode == CosmeticsLogic.CLOAK_SESTIA_LEAF && i == 2) {
                float cx = (prevXOff + nextXOff) * 0.5f;
                float cy = (prevY + nextY) * 0.5f + 0.04f;
                float cz = (oz0 + oz1) * 0.5f + 0.008f;
                emitLeafEmblem(pose, vc, cx, cy, cz, 0.16f, trimRgb, primary);
            }

            // Bottom gold/accent hem + thickness end cap
            if (i == segs - 1) {
                if (borderGlow) {
                    emitDoubleQuad(pose, vc,
                            nextXOff - w1, nextY - 0.024f, oz1 + 0.005f, tr, tg, tb, 245,
                            nextXOff + w1, nextY - 0.024f, oz1 + 0.005f, tr, tg, tb, 245,
                            nextXOff + w1, nextY + 0.004f, oz1 + 0.005f, tr, tg, tb, 245,
                            nextXOff - w1, nextY + 0.004f, oz1 + 0.005f, tr, tg, tb, 245);
                }
                int er = borderGlow ? tr : dr, eg = borderGlow ? tg : dg, eb = borderGlow ? tb : db;
                emitDoubleQuad(pose, vc,
                        nextXOff - w1, nextY, nextZ, er, eg, eb, 235,
                        nextXOff + w1, nextY, nextZ, er, eg, eb, 235,
                        nextXOff + w1, nextY, oz1, er, eg, eb, 245,
                        nextXOff - w1, nextY, oz1, er, eg, eb, 245);
            }

            prevY = nextY;
            prevZ = nextZ;
            prevXOff = nextXOff;
            prevRipple = ripple;
        }
    }

    private static void renderCustomPngCloakOverlay(
            PoseStack.Pose pose, VertexConsumer vc, int packedLight,
            float age, float capeFlap, float capeLean, float capeLean2,
            float walkSpeed, float backZ, float topY, boolean crouching
    ) {
        float halfW = 0.28f;
        float totalLen = 0.96f;
        float thickness = 0.018f;
        int segs = 6;
        float leanRad = CosmeticsLogic.computeCloakLeanRad(capeLean, capeFlap, walkSpeed, crouching);
        float safeLean2 = Float.isFinite(capeLean2) ? capeLean2 : 0.0f;
        float sideSway = Math.max(-0.18f, Math.min(0.18f, safeLean2 * 0.007f));

        float prevY = topY;
        float prevZ = backZ;
        float prevXOff = 0.0f;
        float prevRipple = 0.0f;

        for (int i = 0; i < segs; i++) {
            float t0 = (float) i / segs;
            float t1 = (float) (i + 1) / segs;
            float curLen = totalLen / segs;
            float segAngle = leanRad * (0.78f + 0.34f * t1);
            float baseStepZ = (float) Math.sin(segAngle) * curLen;
            float wave = 0.5f + 0.5f * (float) Math.sin(age * 0.16f - t1 * 3.0f);
            float ripple = wave * (0.008f + t1 * 0.014f);
            float stepZ = Math.max(0.018f, baseStepZ + (ripple - prevRipple) * 0.65f);

            float nextY = prevY + (float) Math.cos(segAngle) * curLen;
            float nextZ = prevZ + stepZ;
            float nextXOff = sideSway * t1 * t1;
            float w0 = halfW * (1.0f + 0.10f * t0);
            float w1 = halfW * (1.0f + 0.10f * t1);
            float oz0 = prevZ + thickness;
            float oz1 = nextZ + thickness;

            // Back-facing textured outer face (+Z normal) + mirrored front side so visible from any orbit angle
            vc.addVertex(pose, prevXOff - w0, prevY, oz0).setColor(255, 255, 255, 255).setUv(0.0f, t0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 0.0f, 1.0f);
            vc.addVertex(pose, prevXOff + w0, prevY, oz0).setColor(255, 255, 255, 255).setUv(1.0f, t0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 0.0f, 1.0f);
            vc.addVertex(pose, nextXOff + w1, nextY, oz1).setColor(255, 255, 255, 255).setUv(1.0f, t1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 0.0f, 1.0f);
            vc.addVertex(pose, nextXOff - w1, nextY, oz1).setColor(255, 255, 255, 255).setUv(0.0f, t1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 0.0f, 1.0f);

            vc.addVertex(pose, nextXOff - w1, nextY, oz1).setColor(255, 255, 255, 255).setUv(0.0f, t1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 0.0f, -1.0f);
            vc.addVertex(pose, nextXOff + w1, nextY, oz1).setColor(255, 255, 255, 255).setUv(1.0f, t1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 0.0f, -1.0f);
            vc.addVertex(pose, prevXOff + w0, prevY, oz0).setColor(255, 255, 255, 255).setUv(1.0f, t0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 0.0f, -1.0f);
            vc.addVertex(pose, prevXOff - w0, prevY, oz0).setColor(255, 255, 255, 255).setUv(0.0f, t0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(packedLight).setNormal(pose, 0.0f, 0.0f, -1.0f);

            prevY = nextY;
            prevZ = nextZ;
            prevXOff = nextXOff;
            prevRipple = ripple;
        }
    }

    private static void renderEmoteRuneBook(
            PoseStack.Pose pose, VertexConsumer vc, int primaryRgb, int secondaryRgb, float age
    ) {
        float bob = (float) Math.sin(age * 0.14f) * 0.045f;
        float bx = 0.0f;
        float by = 0.24f + bob;
        float bz = -0.52f;

        int r = (primaryRgb >> 16) & 0xFF, g = (primaryRgb >> 8) & 0xFF, b = primaryRgb & 0xFF;
        int sr = (secondaryRgb >> 16) & 0xFF, sg = (secondaryRgb >> 8) & 0xFF, sb = secondaryRgb & 0xFF;

        // Dark leather rune-book spine & open angled covers
        emitCube(pose, vc, bx, by + 0.015f, bz, 0.025f, 0.016f, 0.145f, 24, 18, 34, 255);
        for (int side = -1; side <= 1; side += 2) {
            float ix = bx + side * 0.022f;
            float ox = bx + side * 0.205f;
            float oy = by - 0.045f;
            // Cover slab
            emitDoubleQuad(pose, vc,
                    ix, by + 0.015f, bz - 0.14f, 38, 26, 56, 250,
                    ox, oy + 0.015f, bz - 0.14f, 38, 26, 56, 250,
                    ox, oy + 0.015f, bz + 0.14f, 28, 20, 44, 250,
                    ix, by + 0.015f, bz + 0.14f, 28, 20, 44, 250);
            // Parchment open page
            emitDoubleQuad(pose, vc,
                    ix, by - 0.002f, bz - 0.125f, 242, 236, 218, 252,
                    ox - side * 0.014f, oy - 0.002f, bz - 0.125f, 235, 228, 206, 252,
                    ox - side * 0.014f, oy - 0.002f, bz + 0.125f, 235, 228, 206, 252,
                    ix, by - 0.002f, bz + 0.125f, 242, 236, 218, 252);
            // Glowing text rune lines on pages
            for (int ln = 0; ln < 3; ln++) {
                float lz = bz - 0.075f + ln * 0.075f;
                emitDoubleQuad(pose, vc,
                        ix + side * 0.024f, by - 0.010f, lz - 0.010f, r, g, b, 230,
                        ox - side * 0.042f, oy - 0.010f, lz - 0.010f, sr, sg, sb, 230,
                        ox - side * 0.042f, oy - 0.010f, lz + 0.010f, sr, sg, sb, 230,
                        ix + side * 0.024f, by - 0.010f, lz + 0.010f, r, g, b, 230);
            }
        }
        // Floating arcane ring & 4 orbiting rune motes above the open tome
        emitHorizontalRing(pose, vc, bx, by - 0.11f, bz, 0.22f, 0.255f, 0.010f, 16, age * 0.09f, r, g, b, 215);
        for (int k = 0; k < 4; k++) {
            float a = age * 0.16f + k * (TWO_PI * 0.25f);
            float mx = bx + (float) Math.cos(a) * 0.27f;
            float mz = bz + (float) Math.sin(a) * 0.27f;
            float my = by - 0.13f + (float) Math.sin(age * 0.24f + k * 1.4f) * 0.035f;
            emitCube(pose, vc, mx, my, mz, 0.020f, 0.020f, 0.020f, (k % 2 == 0) ? r : sr, (k % 2 == 0) ? g : sg, (k % 2 == 0) ? b : sb, 245);
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
    // 2. 3D HEADWEAR (7 MODELS)
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
            case CosmeticsLogic.HAT_CAT_EARS -> renderPlushCatEars(pose, vc, rgb, bright, dark, age);
            default -> {
            }
        }
    }

    private static void renderPlushCatEars(
            PoseStack.Pose pose, VertexConsumer vc,
            int rgb, int bright, int dark, float age
    ) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int br = (bright >> 16) & 0xFF, bg = (bright >> 8) & 0xFF, bb = bright & 0xFF;

        int sideRgb = WingsLogic.blendRgb(dark, rgb, 0.72f);
        int sr = (sideRgb >> 16) & 0xFF, sg = (sideRgb >> 8) & 0xFF, sb = sideRgb & 0xFF;

        int backRgb = WingsLogic.blendRgb(dark, rgb, 0.56f);
        int bkr = (backRgb >> 16) & 0xFF, bkg = (backRgb >> 8) & 0xFF, bkb = backRgb & 0xFF;

        int cuffRgb = WingsLogic.blendRgb(rgb, bright, 0.38f);
        int cr = (cuffRgb >> 16) & 0xFF, cg = (cuffRgb >> 8) & 0xFF, cb = cuffRgb & 0xFF;

        int fluffRgb = WingsLogic.blendRgb(rgb, 0xFFFFFF, 0.62f);
        int fr = (fluffRgb >> 16) & 0xFF, fg = (fluffRgb >> 8) & 0xFF, fb = fluffRgb & 0xFF;

        int pinkRgb = WingsLogic.blendRgb(0xF9A8D4, bright, 0.25f);
        int pr = (pinkRgb >> 16) & 0xFF, pg = (pinkRgb >> 8) & 0xFF, pb = pinkRgb & 0xFF;

        int pinkDeep = WingsLogic.blendRgb(0xDB70A8, rgb, 0.22f);
        int pdr = (pinkDeep >> 16) & 0xFF, pdg = (pinkDeep >> 8) & 0xFF, pdb = pinkDeep & 0xFF;

        int pinkTip = WingsLogic.blendRgb(pinkRgb, 0xFFF0F7, 0.30f);
        int ptr = (pinkTip >> 16) & 0xFF, ptg = (pinkTip >> 8) & 0xFF, ptb = pinkTip & 0xFF;

        // Periodic alternating ear twitch (~4.5s between left and right ears, crisp double-flick)
        final float period = 180.0f;
        final float twitchDur = 9.0f;

        for (int side = -1; side <= 1; side += 2) {
            float phase = (side < 0) ? 28.0f : 118.0f;
            float cyc = (age + phase) % period;
            if (cyc < 0.0f) {
                cyc += period;
            }
            float twitch = 0.0f;
            if (cyc < twitchDur) {
                float u = cyc / twitchDur;
                float env = (float) Math.sin(u * Math.PI);
                float flick = 0.5f - 0.5f * (float) Math.cos(u * TWO_PI * 2.0f);
                twitch = env * flick * (1.15f - 0.35f * u);
            }
            float idle = (float) Math.sin(age * 0.07f + side * 0.8f) * 0.004f;
            float tx = side * (twitch * 0.044f + idle);
            float ty = twitch * 0.036f;
            float tz = twitch * 0.028f;

            float tx1 = tx * 0.42f, ty1 = ty * 0.42f, tz1 = tz * 0.42f;
            float tx2 = tx * 0.90f, ty2 = ty * 0.90f, tz2 = tz * 0.90f;

            // Tier 0: Base anchored cleanly into top of head (y = -0.485f, z: -0.056f .. +0.065f)
            float fi0x = side * 0.110f, fi0y = -0.485f, fi0z = -0.054f;
            float fo0x = side * 0.262f, fo0y = -0.485f, fo0z = -0.056f;
            float bi0x = side * 0.114f, bi0y = -0.485f, bi0z = 0.036f;
            float bm0x = side * 0.188f, bm0y = -0.485f, bm0z = 0.065f;
            float bo0x = side * 0.258f, bo0y = -0.485f, bo0z = 0.040f;

            // Tier 1: Mid-ear plump rounded contour
            float fi1x = side * 0.136f + tx1, fi1y = -0.625f + ty1, fi1z = -0.050f + tz1;
            float fo1x = side * 0.270f + tx1, fo1y = -0.620f + ty1, fo1z = -0.050f + tz1;
            float bi1x = side * 0.142f + tx1, bi1y = -0.625f + ty1, bi1z = 0.026f + tz1;
            float bm1x = side * 0.205f + tx1, bm1y = -0.622f + ty1, bm1z = 0.052f + tz1;
            float bo1x = side * 0.264f + tx1, bo1y = -0.620f + ty1, bo1z = 0.028f + tz1;

            // Tier 2: Upper rounded shoulder
            float fi2x = side * 0.194f + tx2, fi2y = -0.734f + ty2, fi2z = -0.032f + tz2;
            float fo2x = side * 0.246f + tx2, fo2y = -0.730f + ty2, fo2z = -0.032f + tz2;
            float bi2x = side * 0.197f + tx2, bi2y = -0.734f + ty2, bi2z = 0.012f + tz2;
            float bm2x = side * 0.221f + tx2, bm2y = -0.732f + ty2, bm2z = 0.024f + tz2;
            float bo2x = side * 0.243f + tx2, bo2y = -0.730f + ty2, bo2z = 0.012f + tz2;

            // Tier 3: Pointed-rounded plush apex
            float fa3x = side * 0.221f + tx, fa3y = -0.755f + ty, fa3z = -0.014f + tz;
            float ba3x = side * 0.221f + tx, ba3y = -0.755f + ty, ba3z = 0.008f + tz;

            // Recessed blush-pink inner ear boundary (~0.026f behind front bevel, ~0.050f ahead of back)
            float pi0x = side * 0.138f, pi0y = -0.516f, pi0z = -0.024f;
            float po0x = side * 0.236f, po0y = -0.516f, po0z = -0.024f;
            float pi1x = side * 0.158f + tx1, pi1y = -0.614f + ty1, pi1z = -0.022f + tz1;
            float po1x = side * 0.244f + tx1, po1y = -0.612f + ty1, po1z = -0.022f + tz1;
            float pt2x = side * 0.214f + tx * 0.80f, pt2y = -0.706f + ty * 0.80f, pt2z = -0.014f + tz * 0.80f;

            // 1. Rounded 3D Back Shell (two angled strips meeting along central plush ridge)
            emitDoubleQuad(pose, vc,
                    bi0x, bi0y, bi0z, bkr, bkg, bkb, 255,
                    bm0x, bm0y, bm0z, sr, sg, sb, 255,
                    bm1x, bm1y, bm1z, r, g, b, 255,
                    bi1x, bi1y, bi1z, sr, sg, sb, 255);
            emitDoubleQuad(pose, vc,
                    bm0x, bm0y, bm0z, sr, sg, sb, 255,
                    bo0x, bo0y, bo0z, bkr, bkg, bkb, 255,
                    bo1x, bo1y, bo1z, sr, sg, sb, 255,
                    bm1x, bm1y, bm1z, r, g, b, 255);
            emitDoubleQuad(pose, vc,
                    bi1x, bi1y, bi1z, sr, sg, sb, 255,
                    bm1x, bm1y, bm1z, r, g, b, 255,
                    bm2x, bm2y, bm2z, r, g, b, 255,
                    bi2x, bi2y, bi2z, sr, sg, sb, 255);
            emitDoubleQuad(pose, vc,
                    bm1x, bm1y, bm1z, r, g, b, 255,
                    bo1x, bo1y, bo1z, sr, sg, sb, 255,
                    bo2x, bo2y, bo2z, sr, sg, sb, 255,
                    bm2x, bm2y, bm2z, r, g, b, 255);
            emitDoubleQuad(pose, vc,
                    bi2x, bi2y, bi2z, sr, sg, sb, 255,
                    bm2x, bm2y, bm2z, r, g, b, 255,
                    ba3x, ba3y, ba3z, br, bg, bb, 255,
                    ba3x, ba3y, ba3z, br, bg, bb, 255);
            emitDoubleQuad(pose, vc,
                    bm2x, bm2y, bm2z, r, g, b, 255,
                    bo2x, bo2y, bo2z, sr, sg, sb, 255,
                    ba3x, ba3y, ba3z, br, bg, bb, 255,
                    ba3x, ba3y, ba3z, br, bg, bb, 255);

            // 2. Inner Side Wall
            emitDoubleQuad(pose, vc,
                    fi0x, fi0y, fi0z, r, g, b, 255,
                    bi0x, bi0y, bi0z, sr, sg, sb, 255,
                    bi1x, bi1y, bi1z, sr, sg, sb, 255,
                    fi1x, fi1y, fi1z, r, g, b, 255);
            emitDoubleQuad(pose, vc,
                    fi1x, fi1y, fi1z, r, g, b, 255,
                    bi1x, bi1y, bi1z, sr, sg, sb, 255,
                    bi2x, bi2y, bi2z, sr, sg, sb, 255,
                    fi2x, fi2y, fi2z, br, bg, bb, 255);
            emitDoubleQuad(pose, vc,
                    fi2x, fi2y, fi2z, br, bg, bb, 255,
                    bi2x, bi2y, bi2z, sr, sg, sb, 255,
                    ba3x, ba3y, ba3z, br, bg, bb, 255,
                    fa3x, fa3y, fa3z, br, bg, bb, 255);

            // 3. Outer Side Wall
            emitDoubleQuad(pose, vc,
                    fo0x, fo0y, fo0z, r, g, b, 255,
                    bo0x, bo0y, bo0z, sr, sg, sb, 255,
                    bo1x, bo1y, bo1z, r, g, b, 255,
                    fo1x, fo1y, fo1z, r, g, b, 255);
            emitDoubleQuad(pose, vc,
                    fo1x, fo1y, fo1z, r, g, b, 255,
                    bo1x, bo1y, bo1z, sr, sg, sb, 255,
                    bo2x, bo2y, bo2z, r, g, b, 255,
                    fo2x, fo2y, fo2z, br, bg, bb, 255);
            emitDoubleQuad(pose, vc,
                    fo2x, fo2y, fo2z, br, bg, bb, 255,
                    bo2x, bo2y, bo2z, r, g, b, 255,
                    ba3x, ba3y, ba3z, br, bg, bb, 255,
                    fa3x, fa3y, fa3z, br, bg, bb, 255);

            // 4. Front Plush Bevel Rim framing the recessed pink cavity (zero overlap / zero Z-fighting)
            emitDoubleQuad(pose, vc,
                    fi0x, fi0y, fi0z, r, g, b, 255,
                    fo0x, fo0y, fo0z, r, g, b, 255,
                    po0x, po0y, po0z, sr, sg, sb, 255,
                    pi0x, pi0y, pi0z, sr, sg, sb, 255);
            emitDoubleQuad(pose, vc,
                    fi0x, fi0y, fi0z, br, bg, bb, 255,
                    pi0x, pi0y, pi0z, sr, sg, sb, 255,
                    pi1x, pi1y, pi1z, r, g, b, 255,
                    fi1x, fi1y, fi1z, br, bg, bb, 255);
            emitDoubleQuad(pose, vc,
                    fi1x, fi1y, fi1z, br, bg, bb, 255,
                    pi1x, pi1y, pi1z, r, g, b, 255,
                    pt2x, pt2y, pt2z, r, g, b, 255,
                    fi2x, fi2y, fi2z, br, bg, bb, 255);
            emitDoubleQuad(pose, vc,
                    fo0x, fo0y, fo0z, br, bg, bb, 255,
                    po0x, po0y, po0z, sr, sg, sb, 255,
                    po1x, po1y, po1z, r, g, b, 255,
                    fo1x, fo1y, fo1z, br, bg, bb, 255);
            emitDoubleQuad(pose, vc,
                    fo1x, fo1y, fo1z, br, bg, bb, 255,
                    po1x, po1y, po1z, r, g, b, 255,
                    pt2x, pt2y, pt2z, r, g, b, 255,
                    fo2x, fo2y, fo2z, br, bg, bb, 255);
            emitDoubleQuad(pose, vc,
                    fi2x, fi2y, fi2z, br, bg, bb, 255,
                    pt2x, pt2y, pt2z, r, g, b, 255,
                    fo2x, fo2y, fo2z, br, bg, bb, 255,
                    fa3x, fa3y, fa3z, br, bg, bb, 255);

            // 5. Recessed Soft Blush-Pink Inner Ear Pad
            emitDoubleQuad(pose, vc,
                    pi0x, pi0y, pi0z, pdr, pdg, pdb, 255,
                    po0x, po0y, po0z, pdr, pdg, pdb, 255,
                    po1x, po1y, po1z, pr, pg, pb, 255,
                    pi1x, pi1y, pi1z, pr, pg, pb, 255);
            emitDoubleQuad(pose, vc,
                    pi1x, pi1y, pi1z, pr, pg, pb, 255,
                    po1x, po1y, po1z, pr, pg, pb, 255,
                    pt2x, pt2y, pt2z, ptr, ptg, ptb, 255,
                    pt2x, pt2y, pt2z, ptr, ptg, ptb, 255);

            // 6. Ear-Base Plush Cuff & Cute Side/Inner Fluff Tufts (blends cleanly into head/helmet seam)
            emitCube(pose, vc, side * 0.186f, -0.492f, -0.004f, 0.082f, 0.016f, 0.056f, cr, cg, cb, 255);
            emitCube(pose, vc, side * 0.260f, -0.502f, -0.018f, 0.018f, 0.016f, 0.032f, fr, fg, fb, 252);

            // Soft inner-ear fur wisps sprouting cleanly ahead of the recessed pink pad
            emitDoubleQuad(pose, vc,
                    side * 0.138f, -0.528f, -0.040f, fr, fg, fb, 248,
                    side * 0.158f + tx * 0.15f, -0.550f + ty * 0.15f, -0.042f + tz * 0.15f, fr, fg, fb, 242,
                    side * 0.184f + tx * 0.18f, -0.548f + ty * 0.18f, -0.041f + tz * 0.18f, br, bg, bb, 232,
                    side * 0.160f + tx * 0.12f, -0.530f + ty * 0.12f, -0.042f + tz * 0.12f, fr, fg, fb, 242);
            emitDoubleQuad(pose, vc,
                    side * 0.148f + tx * 0.22f, -0.560f + ty * 0.22f, -0.038f + tz * 0.22f, fr, fg, fb, 244,
                    side * 0.166f + tx * 0.28f, -0.578f + ty * 0.28f, -0.040f + tz * 0.28f, fr, fg, fb, 236,
                    side * 0.186f + tx * 0.30f, -0.574f + ty * 0.30f, -0.039f + tz * 0.30f, br, bg, bb, 224,
                    side * 0.168f + tx * 0.26f, -0.562f + ty * 0.26f, -0.040f + tz * 0.26f, fr, fg, fb, 236);
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
    // 4. SHOULDER PETS & ORBITING COMPANIONS (8 MODELS)
    // =========================================================================
    private static void renderCompanionPet(
            PoseStack.Pose pose, VertexConsumer vc,
            int mode, int side, int colorPreset, int customRgb, float age
    ) {
        float safeAge = Float.isFinite(age) ? age : 0.0f;
        int rgb = CosmeticsLogic.resolveColorRgb(colorPreset, customRgb, safeAge, 0.0f);
        int bright = WingsLogic.blendRgb(rgb, 0xFFFFFF, 0.55f);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        int br = (bright >> 16) & 0xFF, bg = (bright >> 8) & 0xFF, bb = bright & 0xFF;

        float cx;
        float cy;
        float cz;
        if (side == CosmeticsLogic.PET_SIDE_ORBIT) {
            float orbit = safeAge * 0.075f;
            float radius = 0.88f;
            cx = (float) Math.cos(orbit) * radius;
            cz = (float) Math.sin(orbit) * radius;
            cy = -0.26f + (float) Math.sin(safeAge * 0.14f) * 0.11f;
            emitOrbitWake(pose, vc, orbit, radius, safeAge, r, g, b, br, bg, bb);
        } else {
            float bob = (float) Math.sin(safeAge * 0.16f) * 0.065f;
            float sign = (side == CosmeticsLogic.PET_SIDE_LEFT) ? 1.0f : -1.0f;
            cx = sign * 0.56f;
            cy = -0.34f + bob;
            cz = -0.04f + (float) Math.cos(safeAge * 0.11f) * 0.035f;
        }

        switch (mode) {
            case CosmeticsLogic.PET_EMERALD_WISP ->
                    renderPetEmeraldWisp(pose, vc, cx, cy, cz, safeAge, r, g, b, br, bg, bb);
            case CosmeticsLogic.PET_CYBER_DRONE ->
                    renderPetCyberDrone(pose, vc, cx, cy, cz, safeAge, colorPreset, rgb);
            case CosmeticsLogic.PET_MINI_FOX ->
                    renderPetMiniFox(pose, vc, cx, cy, cz, safeAge, colorPreset, rgb);
            case CosmeticsLogic.PET_MINI_ALLAY ->
                    renderPetMiniAllay(pose, vc, cx, cy, cz, safeAge, colorPreset, rgb, r, g, b);
            case CosmeticsLogic.PET_MINI_BEE ->
                    renderPetMiniBee(pose, vc, cx, cy, cz, safeAge, colorPreset, rgb, bright);
            case CosmeticsLogic.PET_BABY_DRAGON ->
                    renderPetBabyDragon(pose, vc, cx, cy, cz, safeAge, colorPreset, rgb);
            case CosmeticsLogic.PET_MINI_CAT ->
                    renderPetMiniCat(pose, vc, cx, cy, cz, safeAge, colorPreset, rgb);
            case CosmeticsLogic.PET_PHANTOM_OWL ->
                    renderPetPhantomOwl(pose, vc, cx, cy, cz, safeAge, colorPreset, rgb, bright);
            default -> {
            }
        }
    }

    private static void emitOrbitWake(
            PoseStack.Pose pose, VertexConsumer vc,
            float orbit, float radius, float age,
            int r, int g, int b, int br, int bg, int bb
    ) {
        final int steps = 7;
        final float stepRad = 0.105f;
        float prevA = orbit - 0.08f;
        float prevX = (float) Math.cos(prevA) * radius;
        float prevZ = (float) Math.sin(prevA) * radius;
        float prevY = -0.26f + (float) Math.sin((age - 1.1f) * 0.14f) * 0.11f;
        float prevH = 0.042f;
        int prevAlpha = 148;

        for (int i = 1; i <= steps; i++) {
            float t = (float) i / steps;
            float nextA = orbit - 0.08f - i * stepRad;
            float nextX = (float) Math.cos(nextA) * radius;
            float nextZ = (float) Math.sin(nextA) * radius;
            float nextY = -0.26f + (float) Math.sin((age - (1.1f + i * 1.4f)) * 0.14f) * 0.11f;
            float nextH = 0.042f * (1.0f - 0.80f * t);
            int nextAlpha = Math.max(12, Math.round(148.0f * (1.0f - t) * (1.0f - 0.35f * t)));

            int sr0 = (i <= 2) ? br : r;
            int sg0 = (i <= 2) ? bg : g;
            int sb0 = (i <= 2) ? bb : b;
            emitDoubleQuad(pose, vc,
                    prevX, prevY - prevH, prevZ, sr0, sg0, sb0, prevAlpha,
                    prevX, prevY + prevH, prevZ, sr0, sg0, sb0, prevAlpha,
                    nextX, nextY + nextH, nextZ, r, g, b, nextAlpha,
                    nextX, nextY - nextH, nextZ, r, g, b, nextAlpha);

            if (i == 2 || i == 4 || i == 6) {
                float sparkOffY = (i == 4) ? -0.055f : 0.048f;
                float sz = 0.018f - i * 0.0018f;
                emitCube(pose, vc, nextX, nextY + sparkOffY, nextZ, sz, sz, sz, br, bg, bb, Math.min(255, nextAlpha + 35));
            }

            prevX = nextX;
            prevY = nextY;
            prevZ = nextZ;
            prevH = nextH;
            prevAlpha = nextAlpha;
        }
    }

    private static void emitTiltedRing(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz,
            float rIn, float rOut, float halfH,
            int segments, float spin, float tiltX, float tiltZ,
            int r, int g, int b, int a
    ) {
        if (segments < 3) {
            return;
        }
        float cosX = (float) Math.cos(tiltX), sinX = (float) Math.sin(tiltX);
        float cosZ = (float) Math.cos(tiltZ), sinZ = (float) Math.sin(tiltZ);

        for (int i = 0; i < segments; i++) {
            float a0 = spin + ((float) i / segments) * TWO_PI;
            float a1 = spin + ((float) (i + 1) / segments) * TWO_PI;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);

            // Inner/outer rim points rotated by tiltX then tiltZ (zero allocations)
            float i0x0 = c0 * rIn, i0z0 = s0 * rIn;
            float i0y1 = -i0z0 * sinX, i0z1 = i0z0 * cosX;
            float i0x = cx + i0x0 * cosZ - i0y1 * sinZ;
            float i0y = cy + i0x0 * sinZ + i0y1 * cosZ;
            float i0z = cz + i0z1;

            float o0x0 = c0 * rOut, o0z0 = s0 * rOut;
            float o0y1 = -o0z0 * sinX, o0z1 = o0z0 * cosX;
            float o0x = cx + o0x0 * cosZ - o0y1 * sinZ;
            float o0y = cy + o0x0 * sinZ + o0y1 * cosZ;
            float o0z = cz + o0z1;

            float o1x0 = c1 * rOut, o1z0 = s1 * rOut;
            float o1y1 = -o1z0 * sinX, o1z1 = o1z0 * cosX;
            float o1x = cx + o1x0 * cosZ - o1y1 * sinZ;
            float o1y = cy + o1x0 * sinZ + o1y1 * cosZ;
            float o1z = cz + o1z1;

            float i1x0 = c1 * rIn, i1z0 = s1 * rIn;
            float i1y1 = -i1z0 * sinX, i1z1 = i1z0 * cosX;
            float i1x = cx + i1x0 * cosZ - i1y1 * sinZ;
            float i1y = cy + i1x0 * sinZ + i1y1 * cosZ;
            float i1z = cz + i1z1;

            emitDoubleQuad(pose, vc,
                    i0x, i0y, i0z, r, g, b, a,
                    o0x, o0y, o0z, r, g, b, a,
                    o1x, o1y, o1z, r, g, b, a,
                    i1x, i1y, i1z, r, g, b, a);

            if (halfH > 0.002f) {
                // Rotated local +Y normal for rim thickness
                float nx = -cosX * sinZ * halfH;
                float ny = cosX * cosZ * halfH;
                float nz = sinX * halfH;
                emitDoubleQuad(pose, vc,
                        o0x - nx, o0y - ny, o0z - nz, r, g, b, a,
                        o1x - nx, o1y - ny, o1z - nz, r, g, b, a,
                        o1x + nx, o1y + ny, o1z + nz, r, g, b, a,
                        o0x + nx, o0y + ny, o0z + nz, r, g, b, a);
            }
        }
    }

    private static void emitBipyramidCrystal(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz,
            float rx, float ryTop, float ryBot, float rz,
            int facets, float spin,
            int r, int g, int b, int br, int bg, int bb, int alpha
    ) {
        if (facets < 3) {
            return;
        }
        float topY = cy - ryTop;
        float botY = cy + ryBot;
        for (int i = 0; i < facets; i++) {
            float a0 = spin + ((float) i / facets) * TWO_PI;
            float a1 = spin + ((float) (i + 1) / facets) * TWO_PI;
            float x0 = cx + (float) Math.cos(a0) * rx;
            float z0 = cz + (float) Math.sin(a0) * rz;
            float x1 = cx + (float) Math.cos(a1) * rx;
            float z1 = cz + (float) Math.sin(a1) * rz;

            // Alternate facet shading for sharp crystalline specular read
            boolean lit = (i & 1) == 0;
            int ur = lit ? Math.min(255, (r + br) >> 1) : r;
            int ug = lit ? Math.min(255, (g + bg) >> 1) : g;
            int ub = lit ? Math.min(255, (b + bb) >> 1) : b;
            int dr = lit ? (int) (r * 0.84f) : (int) (r * 0.68f);
            int dg = lit ? (int) (g * 0.84f) : (int) (g * 0.68f);
            int db = lit ? (int) (b * 0.84f) : (int) (b * 0.68f);

            // Upper crystal spire facet
            emitDoubleQuad(pose, vc,
                    x0, cy, z0, ur, ug, ub, alpha,
                    x1, cy, z1, ur, ug, ub, alpha,
                    cx, topY, cz, br, bg, bb, alpha,
                    cx, topY, cz, br, bg, bb, alpha);

            // Lower crystal spire facet
            emitDoubleQuad(pose, vc,
                    x1, cy, z1, dr, dg, db, alpha,
                    x0, cy, z0, dr, dg, db, alpha,
                    cx, botY, cz, r, g, b, alpha,
                    cx, botY, cz, r, g, b, alpha);
        }
    }

    // Pet 0: Sculpted faceted diamond/bipyramid spirit crystal + dual tilted halos + spirit crown + 3 flame motes
    private static void renderPetEmeraldWisp(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz, float age,
            int r, int g, int b, int br, int bg, int bb
    ) {
        float pulse = 0.94f + 0.08f * (float) Math.sin(age * 0.24f);
        float spin = age * 0.085f;

        // Outer translucent 6-facet diamond crystal spire
        emitBipyramidCrystal(pose, vc, cx, cy, cz,
                0.116f * pulse, 0.178f * pulse, 0.158f * pulse, 0.116f * pulse,
                6, spin, r, g, b, br, bg, bb, 205);

        // Blazing bright counter-rotating inner core crystal
        emitBipyramidCrystal(pose, vc, cx, cy, cz,
                0.060f, 0.104f, 0.092f, 0.060f,
                4, -spin * 1.45f, br, bg, bb, 255, 255, 255, 255);

        // Equatorial floating crystal facets (offset cleanly outside outer shell)
        for (int i = 0; i < 6; i++) {
            float a = spin + ((float) i / 6.0f) * TWO_PI + 0.52f;
            float sx = cx + (float) Math.cos(a) * 0.136f;
            float sz = cz + (float) Math.sin(a) * 0.136f;
            float sy = cy + (float) Math.sin(age * 0.22f + i * 1.1f) * 0.012f;
            emitCube(pose, vc, sx, sy, sz, 0.014f, 0.026f, 0.014f, br, bg, bb, 232);
        }

        // Two angled/tilted counter-rotating arcane halo rings
        emitTiltedRing(pose, vc, cx, cy, cz, 0.168f, 0.198f, 0.011f, 16,
                age * 0.14f, 0.52f, 0.34f, br, bg, bb, 205);
        emitTiltedRing(pose, vc, cx, cy, cz, 0.188f, 0.214f, 0.009f, 16,
                -age * 0.18f, -0.58f, -0.38f, r, g, b, 175);

        // Delicate floating top spirit crown (circlet + 4 mini crystal points)
        float crownY = cy - 0.222f + (float) Math.sin(age * 0.20f) * 0.014f;
        float crownSpin = -age * 0.10f;
        emitHorizontalRing(pose, vc, cx, crownY, cz, 0.044f, 0.060f, 0.007f, 10, crownSpin, br, bg, bb, 225);
        for (int i = 0; i < 4; i++) {
            float a = crownSpin + i * (float) (Math.PI * 0.5);
            float px = cx + (float) Math.cos(a) * 0.052f;
            float pz = cz + (float) Math.sin(a) * 0.052f;
            emitBipyramidCrystal(pose, vc, px, crownY - 0.018f, pz,
                    0.013f, 0.036f, 0.014f, 0.013f, 4, a, br, bg, bb, 255, 255, 255, 240);
        }

        // 3 orbiting spirit flame motes
        for (int k = 0; k < 3; k++) {
            float a = age * 0.19f + k * (TWO_PI / 3.0f);
            float mx = cx + (float) Math.cos(a) * 0.248f;
            float mz = cz + (float) Math.sin(a) * 0.248f;
            float my = cy - 0.015f + (float) Math.sin(age * 0.26f + k * 2.1f) * 0.072f;
            emitBipyramidCrystal(pose, vc, mx, my, mz,
                    0.025f, 0.048f, 0.024f, 0.025f, 4, age * 0.3f, r, g, b, br, bg, bb, 225);
            emitCube(pose, vc, mx, my, mz, 0.012f, 0.016f, 0.012f, 255, 255, 255, 250);
        }
    }

    // Pet 1: Armored twin-rotor cyber drone + sweeping scanner visor + holo rotor rings & blades + rear thrusters
    private static void renderPetCyberDrone(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz, float age,
            int colorPreset, int rgb
    ) {
        int accRgb = (colorPreset == WingsLogic.COLOR_EMERALD) ? 0x10F0D8 : rgb;
        int accBright = WingsLogic.blendRgb(accRgb, 0xFFFFFF, 0.55f);
        int ar = (accRgb >> 16) & 0xFF, ag = (accRgb >> 8) & 0xFF, ab = accRgb & 0xFF;
        int abr = (accBright >> 16) & 0xFF, abg = (accBright >> 8) & 0xFF, abb = accBright & 0xFF;

        // Bevel-layered dark titanium chassis
        emitCube(pose, vc, cx, cy, cz, 0.145f, 0.066f, 0.116f, 28, 34, 48, 255);
        emitCube(pose, vc, cx, cy - 0.084f, cz + 0.006f, 0.112f, 0.018f, 0.090f, 46, 56, 74, 255);
        emitCube(pose, vc, cx, cy + 0.082f, cz + 0.004f, 0.104f, 0.016f, 0.086f, 20, 25, 36, 255);

        // Side armor cheek plates + top twin micro-antennae
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.157f, cy, cz + 0.008f, 0.012f, 0.042f, 0.082f, 44, 54, 72, 255);
            emitCube(pose, vc, cx + s * 0.171f, cy, cz + 0.006f, 0.004f, 0.012f, 0.058f, ar, ag, ab, 235);
            emitBoxSegment(pose, vc,
                    cx + s * 0.068f, cy - 0.102f, cz + 0.056f,
                    cx + s * 0.082f, cy - 0.168f, cz + 0.072f,
                    0.009f, 0.006f, 64, 76, 98, 255);
            emitCube(pose, vc, cx + s * 0.082f, cy - 0.174f, cz + 0.072f, 0.011f, 0.011f, 0.011f, abr, abg, abb, 255);
        }

        // Front dark visor housing + sweeping cyan/tinted scanner optic (proud >= 0.010f)
        emitCube(pose, vc, cx, cy - 0.004f, cz - 0.128f, 0.118f, 0.036f, 0.012f, 12, 16, 24, 255);
        emitCube(pose, vc, cx, cy - 0.004f, cz - 0.145f, 0.102f, 0.018f, 0.005f, ar / 3, ag / 3, ab / 3, 242);
        float scanX = (float) Math.sin(age * 0.24f) * 0.072f;
        emitCube(pose, vc, cx + scanX, cy - 0.004f, cz - 0.156f, 0.028f, 0.018f, 0.005f, ar, ag, ab, 250);
        emitCube(pose, vc, cx + scanX, cy - 0.004f, cz - 0.165f, 0.013f, 0.012f, 0.004f, abr, abg, abb, 255);

        // Glowing under-core reactor + hover projection ring
        float corePulse = 0.88f + 0.14f * (float) Math.sin(age * 0.30f);
        emitCube(pose, vc, cx, cy + 0.108f, cz, 0.060f * corePulse, 0.010f, 0.054f * corePulse, abr, abg, abb, 248);
        emitHorizontalRing(pose, vc, cx, cy + 0.128f, cz, 0.044f, 0.078f * corePulse, 0.005f, 12, age * 0.25f, ar, ag, ab, 135);

        // Twin angled arms + wide rapid-spinning holo rotor rings + cross spinning rotor blades
        for (int s = -1; s <= 1; s += 2) {
            float rx = cx + s * 0.248f;
            float ry = cy - 0.054f;
            emitBoxSegment(pose, vc,
                    cx + s * 0.148f, cy - 0.012f, cz,
                    rx, ry, cz,
                    0.020f, 0.016f, 56, 66, 86, 255);
            emitCube(pose, vc, rx, ry, cz, 0.022f, 0.022f, 0.022f, 36, 44, 60, 255);

            float rotorY = ry - 0.030f;
            float rotorSpin = age * 1.15f * s;
            emitHorizontalRing(pose, vc, rx, rotorY, cz, 0.074f, 0.106f, 0.009f, 12, rotorSpin, ar, ag, ab, 205);
            emitHorizontalRing(pose, vc, rx, rotorY, cz, 0.024f, 0.072f, 0.002f, 10, -rotorSpin, abr, abg, abb, 88);

            // Cross spinning rotor blades (offset cleanly above holo rings & staggered in Y)
            float bladeY = rotorY - 0.010f;
            float bladeA = age * 1.55f * s;
            for (int k = 0; k < 2; k++) {
                float by = bladeY - k * 0.008f;
                float a = bladeA + k * (float) (Math.PI * 0.5);
                float dx = (float) Math.cos(a) * 0.092f;
                float dz = (float) Math.sin(a) * 0.092f;
                float px = -(float) Math.sin(a) * 0.013f;
                float pz = (float) Math.cos(a) * 0.013f;
                emitDoubleQuad(pose, vc,
                        rx - dx - px, by, cz - dz - pz, abr, abg, abb, 235,
                        rx - dx + px, by, cz - dz + pz, abr, abg, abb, 235,
                        rx + dx + px, by, cz + dz + pz, ar, ag, ab, 225,
                        rx + dx - px, by, cz + dz - pz, ar, ag, ab, 225);
            }
        }

        // Rear twin thruster pods & animated ion jet glow
        float jetLen = 0.072f + 0.030f * (float) Math.sin(age * 0.65f);
        for (int s = -1; s <= 1; s += 2) {
            float tx = cx + s * 0.068f;
            float ty = cy + 0.008f;
            emitCube(pose, vc, tx, ty, cz + 0.128f, 0.026f, 0.024f, 0.012f, 34, 42, 58, 255);
            emitBoxSegment(pose, vc, tx, ty, cz + 0.140f, tx, ty, cz + 0.140f + jetLen, 0.020f, 0.004f, ar, ag, ab, 195);
            emitBoxSegment(pose, vc, tx, ty, cz + 0.140f, tx, ty, cz + 0.140f + jetLen * 0.62f, 0.011f, 0.003f, abr, abg, abb, 250);
        }
    }

    // Pet 2: Larger expressive sitting fox + white chest bib & cheeks + angled dark-tipped ears + 3-segment wagging tail
    private static void renderPetMiniFox(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz, float age,
            int colorPreset, int rgb
    ) {
        boolean customTint = (colorPreset == WingsLogic.COLOR_CUSTOM || colorPreset == WingsLogic.COLOR_CHROMA);
        int furRgb = customTint ? rgb : 0xEC6518;
        int furDark = WingsLogic.blendRgb(furRgb, 0x3D1605, 0.34f);
        int fr = (furRgb >> 16) & 0xFF, fg = (furRgb >> 8) & 0xFF, fb = furRgb & 0xFF;
        int fdr = (furDark >> 16) & 0xFF, fdg = (furDark >> 8) & 0xFF, fdb = furDark & 0xFF;

        float headBob = (float) Math.sin(age * 0.18f) * 0.010f;

        // Sitting torso & haunches
        emitCube(pose, vc, cx, cy + 0.068f, cz + 0.036f, 0.096f, 0.092f, 0.114f, fr, fg, fb, 255);
        emitCube(pose, vc, cx, cy + 0.116f, cz + 0.092f, 0.104f, 0.054f, 0.072f, fdr, fdg, fdb, 255);

        // Big crisp white chest bib (proud of torso front cz - 0.078f)
        emitCube(pose, vc, cx, cy + 0.072f, cz - 0.094f, 0.066f, 0.072f, 0.014f, 250, 248, 244, 255);

        // Expressive head + fluffy white cheeks
        float hy = cy - 0.074f + headBob;
        emitCube(pose, vc, cx, hy, cz - 0.056f, 0.106f, 0.084f, 0.092f, fr, fg, fb, 255);
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.080f, hy + 0.040f, cz - 0.104f, 0.038f, 0.036f, 0.052f, 250, 248, 244, 255);
        }

        // White snout, orange muzzle bridge & glossy black nose
        emitCube(pose, vc, cx, hy + 0.022f, cz - 0.172f, 0.042f, 0.020f, 0.024f, fr, fg, fb, 255);
        emitCube(pose, vc, cx, hy + 0.050f, cz - 0.174f, 0.046f, 0.018f, 0.028f, 252, 250, 246, 255);
        emitCube(pose, vc, cx, hy + 0.032f, cz - 0.212f, 0.018f, 0.015f, 0.010f, 20, 18, 22, 255);

        // Dark glossy eyes with white highlights (proud of head front cz - 0.148f)
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.054f, hy - 0.008f, cz - 0.157f, 0.022f, 0.024f, 0.008f, 18, 16, 22, 255);
            emitCube(pose, vc, cx + s * 0.047f, hy - 0.016f, cz - 0.170f, 0.008f, 0.008f, 0.004f, 255, 255, 255, 255);
        }

        // Angled dark-tipped tall ears with pale inner ear (proud of ear front cz - 0.068f)
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.068f, hy - 0.114f, cz - 0.046f, 0.032f, 0.032f, 0.022f, fr, fg, fb, 255);
            emitCube(pose, vc, cx + s * 0.082f, hy - 0.164f, cz - 0.042f, 0.022f, 0.026f, 0.018f, 26, 22, 26, 255);
            emitCube(pose, vc, cx + s * 0.068f, hy - 0.118f, cz - 0.077f, 0.018f, 0.024f, 0.006f, 255, 226, 200, 255);
        }

        // Tiny tucked dark paws
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.056f, cy + 0.168f, cz - 0.078f, 0.026f, 0.022f, 0.042f, 28, 22, 24, 255);
        }

        // Articulated 3-segment curved wagging fluffy tail with big snow-white tip
        float wag = (float) Math.sin(age * 0.24f) * 0.085f;
        float t0x = cx, t0y = cy + 0.115f, t0z = cz + 0.145f;
        float t1x = cx + wag * 0.38f, t1y = cy + 0.102f, t1z = cz + 0.258f;
        float t2x = cx + wag * 0.82f, t2y = cy + 0.026f, t2z = cz + 0.348f;
        float t3x = cx + wag * 1.18f, t3y = cy - 0.056f, t3z = cz + 0.398f;
        emitBoxSegment(pose, vc, t0x, t0y, t0z, t1x, t1y, t1z, 0.046f, 0.066f, fr, fg, fb, 255);
        emitBoxSegment(pose, vc, t1x, t1y, t1z, t2x, t2y, t2z, 0.066f, 0.062f, fr, fg, fb, 255);
        emitBoxSegment(pose, vc, t2x, t2y, t2z, t3x, t3y, t3z, 0.060f, 0.022f, 252, 252, 255, 255);
        emitCube(pose, vc, t3x, t3y, t3z, 0.026f, 0.026f, 0.026f, 255, 255, 255, 255);
    }

    // Pet 3: Radiant fairy spirit Allay + waist aura + outstretched arms cradling glowing orb + dual-pair fairy wings
    private static void renderPetMiniAllay(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz, float age,
            int colorPreset, int rgb, int orbR, int orbG, int orbB
    ) {
        int spiritRgb = (colorPreset == WingsLogic.COLOR_EMERALD) ? 0x36D7FF : rgb;
        int spiritBright = WingsLogic.blendRgb(spiritRgb, 0xFFFFFF, 0.52f);
        int spiritDeep = WingsLogic.blendRgb(spiritRgb, 0x0A345A, 0.32f);
        int sr = (spiritRgb >> 16) & 0xFF, sg = (spiritRgb >> 8) & 0xFF, sb = spiritRgb & 0xFF;
        int sbr = (spiritBright >> 16) & 0xFF, sbg = (spiritBright >> 8) & 0xFF, sbb = spiritBright & 0xFF;
        int sdr = (spiritDeep >> 16) & 0xFF, sdg = (spiritDeep >> 8) & 0xFF, sdb = spiritDeep & 0xFF;

        // Luminous head + spirit hair crest
        emitCube(pose, vc, cx, cy - 0.084f, cz, 0.092f, 0.088f, 0.088f, sbr, sbg, sbb, 248);
        emitCube(pose, vc, cx, cy - 0.180f, cz + 0.018f, 0.064f, 0.016f, 0.056f, sr, sg, sb, 232);
        emitBoxSegment(pose, vc, cx, cy - 0.140f, cz + 0.082f, cx, cy - 0.060f, cz + 0.156f, 0.036f, 0.012f, sr, sg, sb, 220);

        // Bright glowing cyan/white eyes (proud of head front cz - 0.088f)
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.038f, cy - 0.078f, cz - 0.098f, 0.020f, 0.032f, 0.008f, 175, 250, 255, 255);
            emitCube(pose, vc, cx + s * 0.038f, cy - 0.080f, cz - 0.110f, 0.011f, 0.022f, 0.004f, 255, 255, 255, 255);
        }

        // Tapered spirit torso & swaying wispy tail
        emitCube(pose, vc, cx, cy + 0.046f, cz + 0.006f, 0.060f, 0.054f, 0.050f, sr, sg, sb, 240);
        float tailSway = (float) Math.sin(age * 0.22f) * 0.026f;
        emitBoxSegment(pose, vc,
                cx, cy + 0.096f, cz + 0.008f,
                cx + tailSway, cy + 0.198f, cz + 0.052f,
                0.050f, 0.012f, sdr, sdg, sdb, 212);

        // Soft floating waist aura ring
        emitHorizontalRing(pose, vc, cx, cy + 0.068f, cz + 0.006f, 0.098f, 0.128f, 0.009f, 14, age * 0.15f, sbr, sbg, sbb, 168);

        // Two outstretched arms cradling a floating glowing crystal/leaf orb in front
        for (int s = -1; s <= 1; s += 2) {
            emitBoxSegment(pose, vc,
                    cx + s * 0.066f, cy + 0.020f, cz - 0.014f,
                    cx + s * 0.054f, cy + 0.038f, cz - 0.118f,
                    0.020f, 0.015f, sbr, sbg, sbb, 244);
        }
        float orbY = cy + 0.018f + (float) Math.sin(age * 0.30f) * 0.015f;
        float orbZ = cz - 0.146f;
        int orbBright = WingsLogic.blendRgb((orbR << 16) | (orbG << 8) | orbB, 0xFFFFFF, 0.50f);
        emitBipyramidCrystal(pose, vc, cx, orbY, orbZ,
                0.036f, 0.048f, 0.044f, 0.036f, 4, age * 0.18f,
                orbR, orbG, orbB, (orbBright >> 16) & 0xFF, (orbBright >> 8) & 0xFF, orbBright & 0xFF, 235);
        emitCube(pose, vc, cx, orbY, orbZ, 0.016f, 0.016f, 0.016f, 255, 255, 255, 255);

        // Large dual-pair translucent flapping fairy wings
        float upperLift = (float) Math.sin(age * 0.68f) * 0.098f;
        float lowerLift = (float) Math.sin(age * 0.68f - 0.55f) * 0.072f;
        for (int s = -1; s <= 1; s += 2) {
            float wx0 = cx + s * 0.054f;
            float wz0 = cz + 0.048f;
            // Upper wide swept fairy wing (inner + outer sculpted facets)
            float umx = cx + s * 0.182f;
            float umy = cy - 0.088f + upperLift * 0.55f;
            float umz = cz + 0.135f;
            float utx = cx + s * 0.288f;
            float uty = cy - 0.132f + upperLift;
            float utz = cz + 0.185f;
            emitDoubleQuad(pose, vc,
                    wx0, cy - 0.015f, wz0, sbr, sbg, sbb, 212,
                    umx, umy, umz, sr, sg, sb, 168,
                    umx - s * 0.015f, cy + 0.068f + upperLift * 0.45f, umz + 0.010f, sr, sg, sb, 138,
                    wx0, cy + 0.056f, wz0, sbr, sbg, sbb, 188);
            emitDoubleQuad(pose, vc,
                    umx, umy, umz, sbr, sbg, sbb, 182,
                    utx, uty, utz, 255, 255, 255, 148,
                    cx + s * 0.254f, cy + 0.032f + upperLift * 0.85f, cz + 0.168f, sr, sg, sb, 118,
                    umx - s * 0.015f, cy + 0.068f + upperLift * 0.45f, umz + 0.010f, sr, sg, sb, 138);

            // Lower teardrop fairy wing
            emitDoubleQuad(pose, vc,
                    wx0, cy + 0.046f, wz0, sbr, sbg, sbb, 190,
                    cx + s * 0.208f, cy + 0.068f + lowerLift, cz + 0.152f, sr, sg, sb, 148,
                    cx + s * 0.172f, cy + 0.175f + lowerLift, cz + 0.134f, sdr, sdg, sdb, 110,
                    wx0, cy + 0.102f, wz0, sr, sg, sb, 162);
        }
    }

    // Pet 4: Chunky voxel Bumblebee + 3 bold stripes + big glossy eyes + curling antennae + 4 legs + stinger + dual buzzing wings
    private static void renderPetMiniBee(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz, float age,
            int colorPreset, int rgb, int bright
    ) {
        boolean customTint = (colorPreset == WingsLogic.COLOR_CUSTOM || colorPreset == WingsLogic.COLOR_CHROMA);
        int bodyRgb = customTint ? WingsLogic.blendRgb(0xF8C828, rgb, 0.55f) : 0xF8C828;
        int yr = (bodyRgb >> 16) & 0xFF, yg = (bodyRgb >> 8) & 0xFF, yb = bodyRgb & 0xFF;

        // Chunky golden-yellow cuboid body
        emitCube(pose, vc, cx, cy + 0.008f, cz, 0.112f, 0.100f, 0.148f, yr, yg, yb, 255);

        // 3 bold dark-brown stripes (proud by 0.009f on X/Y to prevent Z-fighting)
        emitCube(pose, vc, cx, cy + 0.008f, cz - 0.044f, 0.121f, 0.109f, 0.022f, 42, 28, 20, 255);
        emitCube(pose, vc, cx, cy + 0.008f, cz + 0.028f, 0.121f, 0.109f, 0.024f, 42, 28, 20, 255);
        emitCube(pose, vc, cx, cy + 0.008f, cz + 0.104f, 0.121f, 0.109f, 0.025f, 42, 28, 20, 255);

        // Cute big dark eyes with pale cyan-white highlights + rosy cheeks (proud of front cz - 0.148f)
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.056f, cy - 0.012f, cz - 0.158f, 0.028f, 0.036f, 0.009f, 22, 18, 22, 255);
            emitCube(pose, vc, cx + s * 0.048f, cy - 0.026f, cz - 0.175f, 0.012f, 0.014f, 0.004f, 215, 245, 255, 255);
            emitCube(pose, vc, cx + s * 0.076f, cy + 0.026f, cz - 0.157f, 0.013f, 0.009f, 0.006f, 248, 156, 168, 255);
        }

        // Tiny curling bobbing antennae
        for (int s = -1; s <= 1; s += 2) {
            float antBob = (float) Math.sin(age * 0.42f + s * 0.8f) * 0.015f;
            float ax0 = cx + s * 0.044f, ay0 = cy - 0.068f, az0 = cz - 0.150f;
            float ax1 = cx + s * 0.060f, ay1 = cy - 0.122f + antBob, az1 = cz - 0.214f;
            float ax2 = cx + s * 0.066f, ay2 = cy - 0.096f + antBob * 1.3f, az2 = cz - 0.244f;
            emitBoxSegment(pose, vc, ax0, ay0, az0, ax1, ay1, az1, 0.012f, 0.009f, 36, 26, 20, 255);
            emitBoxSegment(pose, vc, ax1, ay1, az1, ax2, ay2, az2, 0.009f, 0.008f, 36, 26, 20, 255);
        }

        // 4 little dangling legs underneath
        float legSwing = (float) Math.sin(age * 0.36f) * 0.018f;
        for (int s = -1; s <= 1; s += 2) {
            float lx = cx + s * 0.064f;
            emitBoxSegment(pose, vc, lx, cy + 0.106f, cz - 0.056f, lx + s * 0.006f, cy + 0.158f, cz - 0.032f + legSwing, 0.014f, 0.011f, 38, 28, 22, 255);
            emitBoxSegment(pose, vc, lx, cy + 0.106f, cz + 0.056f, lx + s * 0.006f, cy + 0.158f, cz + 0.084f - legSwing, 0.014f, 0.011f, 38, 28, 22, 255);
        }

        // Sharp rear stinger
        emitBoxSegment(pose, vc, cx, cy + 0.016f, cz + 0.146f, cx, cy + 0.024f, cz + 0.226f, 0.022f, 0.004f, 32, 24, 20, 255);

        // Large rapid-buzzing dual translucent white/tinted wings on top
        float buzz1 = (float) Math.sin(age * 1.65f) * 0.096f;
        float buzz2 = (float) Math.sin(age * 1.65f - 0.6f) * 0.072f;
        int wingRgb = WingsLogic.blendRgb(0xECFAFF, bright, 0.30f);
        int wr = (wingRgb >> 16) & 0xFF, wg = (wingRgb >> 8) & 0xFF, wb = wingRgb & 0xFF;
        for (int s = -1; s <= 1; s += 2) {
            float rootX = cx + s * 0.032f;
            float rootY = cy - 0.112f;
            // Primary forewing
            emitDoubleQuad(pose, vc,
                    rootX, rootY, cz - 0.048f, 245, 252, 255, 212,
                    cx + s * 0.278f, rootY - 0.086f + buzz1, cz - 0.008f, wr, wg, wb, 160,
                    cx + s * 0.246f, rootY - 0.054f + buzz1, cz + 0.086f, wr, wg, wb, 138,
                    rootX, rootY, cz + 0.042f, 245, 252, 255, 195);
            // Secondary hindwing (offset cleanly behind & below forewing root)
            emitDoubleQuad(pose, vc,
                    rootX, rootY + 0.008f, cz + 0.032f, 240, 250, 255, 185,
                    cx + s * 0.206f, rootY - 0.048f + buzz2, cz + 0.078f, wr, wg, wb, 138,
                    cx + s * 0.180f, rootY - 0.022f + buzz2, cz + 0.142f, wr, wg, wb, 114,
                    rootX, rootY + 0.008f, cz + 0.098f, 240, 250, 255, 172);
        }
    }

    // Pet 5: Miniature 3D Ender/Elemental Baby Dragon + horned head + dorsal spikes + wagging barbed tail + flapping bat wings
    private static void renderPetBabyDragon(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz, float age,
            int colorPreset, int rgb
    ) {
        int accRgb = (colorPreset == WingsLogic.COLOR_EMERALD) ? 0xC44DFF : rgb;
        int accBright = WingsLogic.blendRgb(accRgb, 0xFFFFFF, 0.50f);
        int scaleDark = WingsLogic.blendRgb(0x161322, accRgb, 0.12f);
        int scaleMid = WingsLogic.blendRgb(0x2B2540, accRgb, 0.18f);
        int webRgb = WingsLogic.blendRgb(0x1D1630, accRgb, 0.34f);

        int ar = (accRgb >> 16) & 0xFF, ag = (accRgb >> 8) & 0xFF, ab = accRgb & 0xFF;
        int abr = (accBright >> 16) & 0xFF, abg = (accBright >> 8) & 0xFF, abb = accBright & 0xFF;
        int sdr = (scaleDark >> 16) & 0xFF, sdg = (scaleDark >> 8) & 0xFF, sdb = scaleDark & 0xFF;
        int smr = (scaleMid >> 16) & 0xFF, smg = (scaleMid >> 8) & 0xFF, smb = scaleMid & 0xFF;
        int wbr = (webRgb >> 16) & 0xFF, wbg = (webRgb >> 8) & 0xFF, wbb = webRgb & 0xFF;

        float lift = (float) Math.sin(age * 0.48f) * 0.118f;
        float headNod = (float) Math.sin(age * 0.24f) * 0.012f;

        // Sculpted obsidian torso + armored underbelly plate
        emitCube(pose, vc, cx, cy + 0.028f, cz + 0.016f, 0.086f, 0.068f, 0.136f, sdr, sdg, sdb, 255);
        emitCube(pose, vc, cx, cy + 0.106f, cz + 0.012f, 0.060f, 0.010f, 0.108f, smr, smg, smb, 255);

        // Arched dragon neck
        emitBoxSegment(pose, vc,
                cx, cy + 0.004f, cz - 0.104f,
                cx, cy - 0.068f + headNod * 0.5f, cz - 0.168f,
                0.052f, 0.044f, sdr, sdg, sdb, 255);

        // Horned dragon head, snout, jaw, nostrils & glowing eyes
        float hy = cy - 0.092f + headNod;
        emitCube(pose, vc, cx, hy, cz - 0.186f, 0.074f, 0.060f, 0.074f, sdr, sdg, sdb, 255);
        emitCube(pose, vc, cx, hy + 0.022f, cz - 0.296f, 0.048f, 0.030f, 0.038f, smr, smg, smb, 255);
        emitCube(pose, vc, cx, hy + 0.056f, cz - 0.288f, 0.038f, 0.012f, 0.034f, sdr, sdg, sdb, 255);

        for (int s = -1; s <= 1; s += 2) {
            // Glowing nostrils on snout ridge
            emitCube(pose, vc, cx + s * 0.022f, hy - 0.016f, cz - 0.316f, 0.009f, 0.008f, 0.009f, ar, ag, ab, 255);
            // Glowing eyes cleanly proud of head front cz - 0.260f
            emitCube(pose, vc, cx + s * 0.046f, hy - 0.020f, cz - 0.270f, 0.022f, 0.016f, 0.008f, ar, ag, ab, 255);
            emitCube(pose, vc, cx + s * 0.046f, hy - 0.020f, cz - 0.286f, 0.010f, 0.012f, 0.004f, abr, abg, abb, 255);
            // Swept-back curved horns
            float h1x = cx + s * 0.090f, h1y = hy - 0.124f, h1z = cz - 0.112f;
            float h2x = cx + s * 0.108f, h2y = hy - 0.168f, h2z = cz - 0.064f;
            emitBoxSegment(pose, vc, cx + s * 0.052f, hy - 0.054f, cz - 0.168f, h1x, h1y, h1z, 0.020f, 0.014f, smr, smg, smb, 255);
            emitBoxSegment(pose, vc, h1x, h1y, h1z, h2x, h2y, h2z, 0.014f, 0.006f, ar, ag, ab, 255);
        }

        // 4 dorsal crystalline spine spikes along neck & back
        emitCube(pose, vc, cx, cy - 0.088f + headNod * 0.4f, cz - 0.132f, 0.014f, 0.028f, 0.016f, ar, ag, ab, 255);
        emitCube(pose, vc, cx, cy - 0.064f, cz - 0.052f, 0.015f, 0.026f, 0.018f, abr, abg, abb, 255);
        emitCube(pose, vc, cx, cy - 0.062f, cz + 0.024f, 0.014f, 0.024f, 0.018f, ar, ag, ab, 255);
        emitCube(pose, vc, cx, cy - 0.058f, cz + 0.098f, 0.013f, 0.020f, 0.016f, abr, abg, abb, 255);

        // Tucked little dragon legs/claws
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.066f, cy + 0.108f, cz - 0.066f, 0.020f, 0.028f, 0.024f, sdr, sdg, sdb, 255);
            emitCube(pose, vc, cx + s * 0.066f, cy + 0.110f, cz + 0.082f, 0.022f, 0.030f, 0.026f, sdr, sdg, sdb, 255);
        }

        // Animated wagging barbed dragon tail
        float wag = (float) Math.sin(age * 0.26f) * 0.095f;
        float t0x = cx, t0y = cy + 0.026f, t0z = cz + 0.146f;
        float t1x = cx + wag * 0.35f, t1y = cy + 0.012f, t1z = cz + 0.246f;
        float t2x = cx + wag * 0.78f, t2y = cy - 0.014f, t2z = cz + 0.340f;
        float t3x = cx + wag * 1.18f, t3y = cy - 0.036f, t3z = cz + 0.422f;
        emitBoxSegment(pose, vc, t0x, t0y, t0z, t1x, t1y, t1z, 0.042f, 0.032f, sdr, sdg, sdb, 255);
        emitBoxSegment(pose, vc, t1x, t1y, t1z, t2x, t2y, t2z, 0.032f, 0.022f, sdr, sdg, sdb, 255);
        emitBoxSegment(pose, vc, t2x, t2y, t2z, t3x, t3y, t3z, 0.022f, 0.012f, smr, smg, smb, 255);
        // Glowing barbed tail spade
        emitDoubleQuad(pose, vc,
                t3x, t3y, t3z - 0.010f, ar, ag, ab, 250,
                t3x + 0.052f, t3y - 0.010f, t3z + 0.028f, ar, ag, ab, 240,
                t3x + wag * 0.15f, t3y - 0.018f, t3z + 0.094f, abr, abg, abb, 255,
                t3x - 0.052f, t3y - 0.010f, t3z + 0.028f, ar, ag, ab, 240);

        // Large flapping multi-quad bat/dragon wings with glowing wing-web accents
        for (int s = -1; s <= 1; s += 2) {
            float rx = cx + s * 0.082f, ry = cy - 0.016f, rz = cz - 0.022f;
            float ex = cx + s * 0.214f, ey = cy - 0.118f + lift * 0.56f, ez = cz + 0.026f;
            float tx = cx + s * 0.356f, ty = cy - 0.086f + lift, tz = cz + 0.108f;
            float f1x = cx + s * 0.184f, f1y = cy + 0.058f + lift * 0.44f, f1z = cz + 0.144f;
            float f2x = cx + s * 0.312f, f2y = cy + 0.036f + lift * 0.86f, f2z = cz + 0.174f;
            float rbx = cx + s * 0.082f, rby = cy + 0.044f, rbz = cz + 0.092f;

            // Leading-edge wing bone spars
            emitBoxSegment(pose, vc, rx, ry, rz, ex, ey, ez, 0.016f, 0.013f, sdr, sdg, sdb, 255);
            emitBoxSegment(pose, vc, ex, ey, ez, tx, ty, tz, 0.013f, 0.008f, smr, smg, smb, 255);
            emitBoxSegment(pose, vc, ex, ey, ez, f1x, f1y, f1z, 0.009f, 0.005f, ar, ag, ab, 245);
            emitBoxSegment(pose, vc, ex, ey, ez, f2x, f2y, f2z, 0.008f, 0.005f, ar, ag, ab, 245);

            // Inner & outer dark bat-wing membranes
            emitDoubleQuad(pose, vc,
                    rx, ry, rz, sdr, sdg, sdb, 232,
                    ex, ey, ez, wbr, wbg, wbb, 224,
                    f1x, f1y, f1z, wbr, wbg, wbb, 206,
                    rbx, rby, rbz, sdr, sdg, sdb, 226);
            emitDoubleQuad(pose, vc,
                    ex, ey, ez, wbr, wbg, wbb, 226,
                    tx, ty, tz, ar, ag, ab, 212,
                    f2x, f2y, f2z, wbr, wbg, wbb, 198,
                    f1x, f1y, f1z, wbr, wbg, wbb, 206);

            // Glowing wing-web accent fringe extending cleanly past trailing edge (zero coplanar overlap)
            emitDoubleQuad(pose, vc,
                    f1x, f1y, f1z, ar, ag, ab, 232,
                    f2x, f2y, f2z, abr, abg, abb, 238,
                    f2x + s * 0.014f, f2y + 0.028f, f2z + 0.032f, ar, ag, ab, 175,
                    f1x + s * 0.006f, f1y + 0.026f, f1z + 0.028f, ar, ag, ab, 165);
            emitDoubleQuad(pose, vc,
                    f2x, f2y, f2z, abr, abg, abb, 238,
                    tx, ty, tz, abr, abg, abb, 242,
                    tx + s * 0.022f, ty + 0.016f, tz + 0.026f, ar, ag, ab, 180,
                    f2x + s * 0.014f, f2y + 0.028f, f2z + 0.032f, ar, ag, ab, 175);
        }
    }

    // Pet 6: Cute 3D Perched Kitten + blush-pink inner ears + whiskers + collar & golden bell + tall curling swaying tail
    private static void renderPetMiniCat(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz, float age,
            int colorPreset, int rgb
    ) {
        boolean customTint = (colorPreset == WingsLogic.COLOR_CUSTOM || colorPreset == WingsLogic.COLOR_CHROMA);
        int furRgb = customTint ? WingsLogic.blendRgb(0xFFF6EA, rgb, 0.58f) : 0xF5E7D6;
        int furAccent = WingsLogic.blendRgb(furRgb, 0xB88A62, 0.38f);
        int collarRgb = (colorPreset == WingsLogic.COLOR_EMERALD) ? 0xE11D48 : rgb;
        int eyeRgb = (colorPreset == WingsLogic.COLOR_EMERALD) ? 0x22D3A8 : rgb;

        int fr = (furRgb >> 16) & 0xFF, fg = (furRgb >> 8) & 0xFF, fb = furRgb & 0xFF;
        int far = (furAccent >> 16) & 0xFF, fag = (furAccent >> 8) & 0xFF, fab = furAccent & 0xFF;
        int cr = (collarRgb >> 16) & 0xFF, cg = (collarRgb >> 8) & 0xFF, cb = collarRgb & 0xFF;
        int er = (eyeRgb >> 16) & 0xFF, eg = (eyeRgb >> 8) & 0xFF, eb = eyeRgb & 0xFF;

        float headTilt = (float) Math.sin(age * 0.14f) * 0.010f;

        // Perched plush sitting torso + bright chest fluff
        emitCube(pose, vc, cx, cy + 0.074f, cz + 0.024f, 0.092f, 0.094f, 0.100f, fr, fg, fb, 255);
        emitCube(pose, vc, cx, cy + 0.076f, cz - 0.086f, 0.056f, 0.052f, 0.009f, 255, 252, 248, 255);

        // Colored collar around neck + gleaming golden bell on chest
        emitCube(pose, vc, cx, cy - 0.018f, cz + 0.002f, 0.102f, 0.015f, 0.106f, cr, cg, cb, 255);
        emitCube(pose, vc, cx, cy + 0.006f, cz - 0.118f, 0.022f, 0.022f, 0.018f, 252, 206, 46, 255);
        emitCube(pose, vc, cx, cy + 0.012f, cz - 0.144f, 0.010f, 0.006f, 0.004f, 92, 62, 16, 255);

        // Rounded plush head + chubby cheeks + soft muzzle pad
        float hy = cy - 0.104f + headTilt;
        emitCube(pose, vc, cx, hy, cz - 0.022f, 0.104f, 0.086f, 0.094f, fr, fg, fb, 255);
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.074f, hy + 0.036f, cz - 0.072f, 0.040f, 0.032f, 0.052f, 255, 252, 248, 255);
        }
        emitCube(pose, vc, cx, hy + 0.036f, cz - 0.128f, 0.038f, 0.022f, 0.012f, 255, 252, 248, 255);

        // Expressive bright cat eyes + tiny pink nose (cleanly offset >= 0.008f)
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.048f, hy - 0.004f, cz - 0.126f, 0.024f, 0.028f, 0.008f, er, eg, eb, 255);
            emitCube(pose, vc, cx + s * 0.048f, hy - 0.004f, cz - 0.142f, 0.009f, 0.022f, 0.004f, 18, 18, 24, 255);
            emitCube(pose, vc, cx + s * 0.041f, hy - 0.014f, cz - 0.151f, 0.008f, 0.008f, 0.003f, 255, 255, 255, 255);

            // Cute side whiskers (2 crisp strands per cheek)
            float wx0 = cx + s * 0.102f;
            float wz = cz - 0.114f;
            emitDoubleQuad(pose, vc,
                    wx0, hy + 0.026f, wz, 225, 215, 205, 238,
                    cx + s * 0.186f, hy + 0.014f, wz - 0.010f, 215, 205, 195, 210,
                    cx + s * 0.186f, hy + 0.021f, wz - 0.010f, 215, 205, 195, 210,
                    wx0, hy + 0.033f, wz, 225, 215, 205, 238);
            emitDoubleQuad(pose, vc,
                    wx0, hy + 0.044f, wz, 225, 215, 205, 238,
                    cx + s * 0.184f, hy + 0.056f, wz - 0.008f, 215, 205, 195, 210,
                    cx + s * 0.184f, hy + 0.063f, wz - 0.008f, 215, 205, 195, 210,
                    wx0, hy + 0.051f, wz, 225, 215, 205, 238);
        }
        emitCube(pose, vc, cx, hy + 0.028f, cz - 0.149f, 0.014f, 0.010f, 0.007f, 244, 126, 174, 255);

        // Tall pointed cat ears with soft blush-pink inner ears (proud of ear front cz - 0.042f)
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.068f, hy - 0.110f, cz - 0.018f, 0.034f, 0.030f, 0.024f, fr, fg, fb, 255);
            emitCube(pose, vc, cx + s * 0.076f, hy - 0.154f, cz - 0.016f, 0.022f, 0.022f, 0.018f, far, fag, fab, 255);
            emitCube(pose, vc, cx + s * 0.068f, hy - 0.112f, cz - 0.052f, 0.021f, 0.024f, 0.006f, 248, 168, 204, 255);
        }

        // Tucked plush paws
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.052f, cy + 0.174f, cz - 0.062f, 0.027f, 0.020f, 0.038f, far, fag, fab, 255);
        }

        // Tall curling animated swaying cat tail (4 articulated S-curve segments)
        float sway = (float) Math.sin(age * 0.19f) * 0.080f;
        float t0x = cx, t0y = cy + 0.124f, t0z = cz + 0.118f;
        float t1x = cx + sway * 0.25f, t1y = cy + 0.072f, t1z = cz + 0.208f;
        float t2x = cx + sway * 0.65f, t2y = cy - 0.038f, t2z = cz + 0.240f;
        float t3x = cx + sway * 1.05f, t3y = cy - 0.148f, t3z = cz + 0.214f;
        float t4x = cx + sway * 1.30f, t4y = cy - 0.196f, t4z = cz + 0.162f;
        emitBoxSegment(pose, vc, t0x, t0y, t0z, t1x, t1y, t1z, 0.028f, 0.025f, fr, fg, fb, 255);
        emitBoxSegment(pose, vc, t1x, t1y, t1z, t2x, t2y, t2z, 0.025f, 0.024f, fr, fg, fb, 255);
        emitBoxSegment(pose, vc, t2x, t2y, t2z, t3x, t3y, t3z, 0.024f, 0.022f, fr, fg, fb, 255);
        emitBoxSegment(pose, vc, t3x, t3y, t3z, t4x, t4y, t4z, 0.022f, 0.016f, far, fag, fab, 255);
    }

    // Pet 7: Regal 3D Arcane/Astral Owl + ear tufts + golden-amber eyes & beak + speckled chest + wide feathered wings + star halo
    private static void renderPetPhantomOwl(
            PoseStack.Pose pose, VertexConsumer vc,
            float cx, float cy, float cz, float age,
            int colorPreset, int rgb, int bright
    ) {
        boolean customTint = (colorPreset == WingsLogic.COLOR_CUSTOM || colorPreset == WingsLogic.COLOR_CHROMA);
        int plumeMain = customTint ? WingsLogic.blendRgb(0x34235E, rgb, 0.50f) : 0x3D2A6E;
        int plumeLight = WingsLogic.blendRgb(plumeMain, 0xA78BFA, 0.44f);
        int plumeDark = WingsLogic.blendRgb(plumeMain, 0x140D28, 0.42f);
        int haloRgb = (colorPreset == WingsLogic.COLOR_EMERALD) ? 0xC084FC : bright;

        int pmr = (plumeMain >> 16) & 0xFF, pmg = (plumeMain >> 8) & 0xFF, pmb = plumeMain & 0xFF;
        int plr = (plumeLight >> 16) & 0xFF, plg = (plumeLight >> 8) & 0xFF, plb = plumeLight & 0xFF;
        int pdr = (plumeDark >> 16) & 0xFF, pdg = (plumeDark >> 8) & 0xFF, pdb = plumeDark & 0xFF;
        int hr = (haloRgb >> 16) & 0xFF, hg = (haloRgb >> 8) & 0xFF, hb = haloRgb & 0xFF;

        float wingSweep = (float) Math.sin(age * 0.34f) * 0.074f;

        // Feathered plump owl torso
        emitCube(pose, vc, cx, cy + 0.056f, cz + 0.012f, 0.106f, 0.112f, 0.092f, pmr, pmg, pmb, 255);

        // Speckled pale chest feathers (proud of front cz - 0.080f)
        emitCube(pose, vc, cx, cy + 0.062f, cz - 0.092f, 0.072f, 0.084f, 0.010f, 236, 228, 244, 255);
        emitCube(pose, vc, cx - 0.032f, cy + 0.022f, cz - 0.110f, 0.014f, 0.009f, 0.004f, pmr, pmg, pmb, 255);
        emitCube(pose, vc, cx + 0.032f, cy + 0.022f, cz - 0.110f, 0.014f, 0.009f, 0.004f, pmr, pmg, pmb, 255);
        emitCube(pose, vc, cx, cy + 0.062f, cz - 0.110f, 0.016f, 0.009f, 0.004f, pmr, pmg, pmb, 255);
        emitCube(pose, vc, cx - 0.030f, cy + 0.104f, cz - 0.110f, 0.014f, 0.009f, 0.004f, pmr, pmg, pmb, 255);
        emitCube(pose, vc, cx + 0.030f, cy + 0.104f, cz - 0.110f, 0.014f, 0.009f, 0.004f, pmr, pmg, pmb, 255);

        // Layered tail feather fan at lower back (center feather offset proud in Z to avoid coplanar overlap)
        for (int f = -1; f <= 1; f++) {
            float fx = cx + f * 0.038f;
            float fzOff = (f == 0) ? 0.008f : 0.0f;
            emitDoubleQuad(pose, vc,
                    fx - 0.024f, cy + 0.118f, cz + 0.104f + fzOff, pdr, pdg, pdb, 250,
                    fx + 0.024f, cy + 0.118f, cz + 0.104f + fzOff, pdr, pdg, pdb, 250,
                    fx + f * 0.016f + 0.018f, cy + 0.214f, cz + 0.172f + fzOff, plr, plg, plb, 235,
                    fx + f * 0.016f - 0.018f, cy + 0.214f, cz + 0.172f + fzOff, plr, plg, plb, 235);
        }

        // Round regal owl head + pale facial disc
        float hy = cy - 0.096f;
        emitCube(pose, vc, cx, hy, cz - 0.006f, 0.102f, 0.084f, 0.090f, pmr, pmg, pmb, 255);
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.044f, hy, cz - 0.106f, 0.042f, 0.044f, 0.008f, 218, 206, 236, 255);
            // Big luminous golden-amber eyes (proud of facial disc cz - 0.114f)
            emitCube(pose, vc, cx + s * 0.046f, hy - 0.002f, cz - 0.123f, 0.028f, 0.030f, 0.008f, 255, 202, 36, 255);
            emitCube(pose, vc, cx + s * 0.046f, hy - 0.002f, cz - 0.139f, 0.013f, 0.016f, 0.004f, 18, 12, 26, 255);
            emitCube(pose, vc, cx + s * 0.039f, hy - 0.011f, cz - 0.149f, 0.008f, 0.008f, 0.003f, 255, 255, 255, 255);

            // Prominent angled regal ear tufts
            emitBoxSegment(pose, vc,
                    cx + s * 0.058f, hy - 0.068f, cz - 0.030f,
                    cx + s * 0.136f, hy - 0.150f, cz - 0.006f,
                    0.026f, 0.008f, pdr, pdg, pdb, 255);
            emitBoxSegment(pose, vc,
                    cx + s * 0.064f, hy - 0.072f, cz - 0.042f,
                    cx + s * 0.124f, hy - 0.136f, cz - 0.022f,
                    0.012f, 0.005f, plr, plg, plb, 255);
        }

        // Sharp golden hooked beak
        emitCube(pose, vc, cx, hy + 0.030f, cz - 0.124f, 0.020f, 0.019f, 0.022f, 248, 166, 26, 255);
        emitBoxSegment(pose, vc, cx, hy + 0.034f, cz - 0.142f, cx, hy + 0.064f, cz - 0.168f, 0.016f, 0.004f, 232, 138, 18, 255);

        // Tiny perched golden-bronze talons
        for (int s = -1; s <= 1; s += 2) {
            emitCube(pose, vc, cx + s * 0.050f, cy + 0.180f, cz - 0.018f, 0.024f, 0.014f, 0.036f, 204, 152, 54, 255);
            emitCube(pose, vc, cx + s * 0.064f, cy + 0.184f, cz - 0.058f, 0.009f, 0.008f, 0.014f, 232, 182, 74, 255);
        }

        // Wide sweeping feathered owl wings + splayed astral wingtip feathers
        for (int s = -1; s <= 1; s += 2) {
            // Shoulder covert hugging torso
            emitCube(pose, vc, cx + s * 0.122f, cy + 0.012f, cz + 0.016f, 0.026f, 0.066f, 0.070f, pdr, pdg, pdb, 255);

            float wx0 = cx + s * 0.114f, wy0 = cy - 0.036f, wz0 = cz - 0.012f;
            float wmx = cx + s * 0.258f, wmy = cy - 0.058f + wingSweep * 0.72f, wmz = cz + 0.058f;
            float wbx = cx + s * 0.228f, wby = cy + 0.098f + wingSweep * 0.65f, wbz = cz + 0.106f;
            emitDoubleQuad(pose, vc,
                    wx0, wy0, wz0, pmr, pmg, pmb, 248,
                    wmx, wmy, wmz, plr, plg, plb, 242,
                    wbx, wby, wbz, pdr, pdg, pdb, 238,
                    wx0, cy + 0.102f, cz + 0.062f, pdr, pdg, pdb, 246);

            // 3 splayed primary astral wingtip feathers
            for (int f = 0; f < 3; f++) {
                float ft = f * 0.34f;
                float fy0 = wmy + f * 0.048f;
                float ftx = cx + s * (0.332f - f * 0.026f);
                float fty = fy0 - 0.026f + wingSweep + ft * 0.08f;
                float ftz = wmz + 0.044f + f * 0.026f;
                emitDoubleQuad(pose, vc,
                        wmx - s * 0.014f, fy0 - 0.014f, wmz, plr, plg, plb, 238,
                        ftx, fty, ftz, hr, hg, hb, 232,
                        ftx - s * 0.018f, fty + 0.036f, ftz + 0.012f, plr, plg, plb, 212,
                        wmx - s * 0.024f, fy0 + 0.034f, wmz + 0.018f, pmr, pmg, pmb, 232);
            }
        }

        // Subtle floating arcane star-ring halo above head + 4 orbiting star points
        float haloY = cy - 0.264f + (float) Math.sin(age * 0.18f) * 0.012f;
        float haloSpin = age * 0.11f;
        emitTiltedRing(pose, vc, cx, haloY, cz - 0.006f, 0.096f, 0.118f, 0.007f, 14, haloSpin, 0.16f, 0.0f, hr, hg, hb, 195);
        for (int i = 0; i < 4; i++) {
            float a = haloSpin + i * (float) (Math.PI * 0.5);
            float sx = cx + (float) Math.cos(a) * 0.132f;
            float sz = cz - 0.006f + (float) Math.sin(a) * 0.132f;
            float sy = haloY - (float) Math.sin(a) * 0.020f;
            emitBipyramidCrystal(pose, vc, sx, sy, sz,
                    0.012f, 0.022f, 0.022f, 0.012f, 4, a, hr, hg, hb, 255, 255, 255, 235);
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

        for (int i = 0; i < 4; i++) {
            int n = (i + 1) & 3;
            float oxI = BOX_SEG_OX[i], oyI = BOX_SEG_OY[i];
            float oxN = BOX_SEG_OX[n], oyN = BOX_SEG_OY[n];
            float s0x = x0 + (ax * oxI + bx * oyI) * r0;
            float s0y = y0 + (ay * oxI + by * oyI) * r0;
            float s0z = z0 + (az * oxI + bz * oyI) * r0;
            float s1x = x0 + (ax * oxN + bx * oyN) * r0;
            float s1y = y0 + (ay * oxN + by * oyN) * r0;
            float s1z = z0 + (az * oxN + bz * oyN) * r0;

            float e0x = x1 + (ax * oxI + bx * oyI) * r1;
            float e0y = y1 + (ay * oxI + by * oyI) * r1;
            float e0z = z1 + (az * oxI + bz * oyI) * r1;
            float e1x = x1 + (ax * oxN + bx * oyN) * r1;
            float e1y = y1 + (ay * oxN + by * oyN) * r1;
            float e1z = z1 + (az * oxN + bz * oyN) * r1;

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
