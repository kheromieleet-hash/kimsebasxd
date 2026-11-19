package com.client.mod;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.Map;

/**
 * Renders LiquidBounce-style cosmetic wings on player/avatar backs:
 * - {@code Lines}: Multi-feather glowing translucent ribbons with per-feather chroma gradient & tip fade
 * - {@code Dragon}: Articulated 3D Ender Dragon wing limbs tinted by color preset & damage state
 */
public class WingsRenderLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final Identifier DRAGON_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/enderdragon/dragon.png");
    private static final RenderType DRAGON_RENDER_TYPE = RenderTypes.entityCutout(DRAGON_TEXTURE);

    private static final float DEG_TO_RAD = (float) (Math.PI / 180.0);

    // Zero-allocation normalized (u, v) contour tables for custom 3D wing models
    private static final float[] BUTTERFLY_FORE_UV = {
            0.05f,  0.00f,
            0.18f, -0.32f,
            0.42f, -0.64f,
            0.72f, -0.82f,
            0.96f, -0.78f,
            1.04f, -0.58f,
            0.98f, -0.34f,
            0.84f, -0.14f,
            0.62f,  0.02f,
            0.34f,  0.08f,
            0.12f,  0.06f
    };

    private static final float[] BUTTERFLY_HIND_UV = {
            0.08f,  0.05f,
            0.36f,  0.04f,
            0.68f,  0.08f,
            0.84f,  0.24f,
            0.78f,  0.48f,
            0.64f,  0.76f,
            0.54f,  0.90f,
            0.42f,  0.66f,
            0.22f,  0.48f,
            0.08f,  0.28f
    };

    // Normalized Demon wing ribs: [midU, midV, tipU, tipV, sweepLag]
    private static final float[] DEMON_RIBS = {
            0.76f, -0.58f, 1.12f, -0.44f, 0.25f,
            0.68f, -0.34f, 0.98f, -0.04f, 0.45f,
            0.55f, -0.14f, 0.74f,  0.26f, 0.65f,
            0.40f,  0.02f, 0.48f,  0.40f, 0.85f,
            0.22f,  0.16f, 0.10f,  0.34f, 0.95f
    };

    // Normalized Cyber holo-blades: [uStart, vStart, uEnd, vEnd, halfWidth, wOffset]
    private static final float[] CYBER_BLADES = {
            0.36f, -0.44f, 1.06f, -0.78f, 0.055f, 0.025f,
            0.40f, -0.26f, 1.12f, -0.42f, 0.060f, 0.020f,
            0.38f, -0.08f, 1.04f, -0.06f, 0.055f, 0.015f,
            0.32f,  0.10f, 0.88f,  0.24f, 0.048f, 0.010f,
            0.24f,  0.26f, 0.68f,  0.50f, 0.040f, 0.005f
    };

    // Normalized Crystal shards: [uRoot, vRoot, uTip, vTip, halfWidth, ridgeDepth]
    private static final float[] CRYSTAL_SHARDS = {
            0.14f, -0.18f, 0.94f, -0.82f, 0.072f, 0.036f,
            0.18f, -0.10f, 1.14f, -0.52f, 0.082f, 0.042f,
            0.18f, -0.02f, 1.16f, -0.18f, 0.078f, 0.038f,
            0.16f,  0.06f, 1.02f,  0.14f, 0.070f, 0.034f,
            0.14f,  0.12f, 0.82f,  0.40f, 0.062f, 0.030f,
            0.10f,  0.18f, 0.56f,  0.60f, 0.054f, 0.026f,
            0.08f,  0.22f, 0.32f,  0.70f, 0.045f, 0.022f
    };

    private final DragonWingsModel dragonWingsModel;

    public WingsRenderLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, EntityModelSet modelSet) {
        super(parent);
        DragonWingsModel baked = null;
        if (modelSet != null) {
            try {
                ModelPart dragonRoot = modelSet.bakeLayer(ModelLayers.ENDER_DRAGON);
                if (dragonRoot != null && dragonRoot.hasChild("body")) {
                    ModelPart body = dragonRoot.getChild("body");
                    if (body.hasChild("left_wing") && body.hasChild("right_wing")) {
                        ModelPart leftWing = body.getChild("left_wing");
                        ModelPart rightWing = body.getChild("right_wing");
                        if (leftWing.hasChild("left_wing_tip") && rightWing.hasChild("right_wing_tip")) {
                            baked = new DragonWingsModel(
                                    leftWing,
                                    leftWing.getChild("left_wing_tip"),
                                    rightWing,
                                    rightWing.getChild("right_wing_tip")
                            );
                        }
                    }
                }
            } catch (Throwable ignored) {
                baked = null;
            }
        }
        this.dragonWingsModel = baked;
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
        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc != null && mc.player != null && state.id == mc.player.getId();
        String remoteName = null;
        if (!isSelf) {
            if (!WingsLogic.hasRemoteProfiles() || mc == null || mc.level == null) {
                return;
            }
            Entity entity = mc.level.getEntity(state.id);
            if (entity instanceof Player player) {
                remoteName = player.getName().getString();
            } else {
                return;
            }
        }
        WingsLogic.WingProfile profile = WingsLogic.resolveRenderProfile(
                cfg,
                false,
                false,
                false,
                isSelf,
                remoteName
        );
        if (profile == null) {
            return;
        }

        boolean hasChestEquip = state.chestEquipment != null && !state.chestEquipment.isEmpty();
        float zAnchor = WingsLogic.computeBackZ(hasChestEquip);
        float yAnchor = WingsLogic.computeAnchorY(profile.height());
        boolean damageFlash = profile.showDamage() && state.hasRedOverlay;

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.translate(0.0f, yAnchor, zAnchor);

        int mode = profile.mode();
        if (mode == WingsLogic.MODE_DRAGON && this.dragonWingsModel != null) {
            poseStack.rotateDegrees(Axis.XP, WingsLogic.DRAGON_UPRIGHT_PITCH_DEG);
            float scale = 0.15f * profile.length();
            poseStack.scale(scale, scale, scale);
            WingsLogic.DragonAnimState anim = WingsLogic.computeDragonAnim(
                    state.ageInTicks,
                    profile.flapSpeed(),
                    state.isFallFlying,
                    state.isCrouching
            );
            int rgb = WingsLogic.resolveRgb(profile.colorPreset(), profile.customRgb(), state.ageInTicks, 0.0f, damageFlash);
            int argb = 0xFF000000 | rgb;
            int overlay = OverlayTexture.pack(0.0f, damageFlash);
            collector.submitModel(
                    this.dragonWingsModel,
                    anim,
                    poseStack,
                    DRAGON_RENDER_TYPE,
                    packedLight,
                    overlay,
                    argb,
                    null,
                    state.outlineColor
            );
        } else {
            final int linesCount = profile.linesCount();
            final float length = profile.length();
            final float width = profile.width();
            final float fadeStart = profile.fadeStart();
            final float flapSpeed = profile.flapSpeed();
            final int colorPreset = profile.colorPreset();
            final int customRgb = profile.customRgb();
            final float ageInTicks = state.ageInTicks;
            final boolean isFallFlying = state.isFallFlying;
            final boolean isCrouching = state.isCrouching;

            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) -> {
                switch (mode) {
                    case WingsLogic.MODE_ANGEL -> renderAngelGeometry(
                            pose, vc, length, flapSpeed, colorPreset, customRgb, ageInTicks, isFallFlying, isCrouching, damageFlash);
                    case WingsLogic.MODE_DEMON -> renderDemonGeometry(
                            pose, vc, length, flapSpeed, colorPreset, customRgb, ageInTicks, isFallFlying, isCrouching, damageFlash);
                    case WingsLogic.MODE_BUTTERFLY -> renderButterflyGeometry(
                            pose, vc, length, flapSpeed, colorPreset, customRgb, ageInTicks, isFallFlying, isCrouching, damageFlash);
                    case WingsLogic.MODE_CYBER -> renderCyberGeometry(
                            pose, vc, length, flapSpeed, colorPreset, customRgb, ageInTicks, isFallFlying, isCrouching, damageFlash);
                    case WingsLogic.MODE_PHOENIX -> renderPhoenixGeometry(
                            pose, vc, length, flapSpeed, colorPreset, customRgb, ageInTicks, isFallFlying, isCrouching, damageFlash);
                    case WingsLogic.MODE_CRYSTAL -> renderCrystalGeometry(
                            pose, vc, length, flapSpeed, colorPreset, customRgb, ageInTicks, isFallFlying, isCrouching, damageFlash);
                    case WingsLogic.MODE_SERAPH -> renderSeraphGeometry(
                            pose, vc, length, flapSpeed, colorPreset, customRgb, ageInTicks, isFallFlying, isCrouching, damageFlash);
                    case WingsLogic.MODE_VOID -> renderVoidGeometry(
                            pose, vc, length, flapSpeed, colorPreset, customRgb, ageInTicks, isFallFlying, isCrouching, damageFlash);
                    default -> renderLinesGeometry(
                            pose, vc, linesCount, length, width, fadeStart, flapSpeed,
                            colorPreset, customRgb, ageInTicks, isFallFlying, damageFlash);
                }
            });
        }

        poseStack.popPose();
    }

    private static void renderAngelGeometry(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float length,
            float flapSpeed,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean isFallFlying,
            boolean isCrouching,
            boolean damageFlash
    ) {
        float phase = WingsLogic.computeFlapPhase(ageInTicks, flapSpeed, 0.11f, isFallFlying, 1.85f);
        for (int s = 0; s < 2; s++) {
            float side = (s == 0) ? -1.0f : 1.0f;
            // Tier 0: Primary flight feathers (long upper arch)
            emitAngelTier(pose, vc, side, length, 8, phase, isFallFlying, isCrouching,
                    colorPreset, customRgb, ageInTicks, damageFlash,
                    0.09f * length, -0.06f * length, 0.015f * length, 0.032f * length,
                    1.05f * length, 0.56f * length, -0.58f * length, 0.36f * length,
                    0.068f * length, 0.00f * length, 0.0f);
            // Tier 1: Secondary mid-layer feathers
            emitAngelTier(pose, vc, side, length, 6, phase, isFallFlying, isCrouching,
                    colorPreset, customRgb, ageInTicks, damageFlash,
                    0.08f * length, -0.03f * length, 0.012f * length, 0.028f * length,
                    0.76f * length, 0.38f * length, -0.34f * length, 0.28f * length,
                    0.062f * length, 0.018f * length, 0.35f);
            // Tier 2: Upper shoulder covert plumes
            emitAngelTier(pose, vc, side, length, 4, phase, isFallFlying, isCrouching,
                    colorPreset, customRgb, ageInTicks, damageFlash,
                    0.06f * length, 0.00f * length, 0.010f * length, 0.025f * length,
                    0.46f * length, 0.24f * length, -0.18f * length, 0.16f * length,
                    0.056f * length, 0.034f * length, 0.65f);
        }
    }

    private static void emitAngelTier(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float side,
            float length,
            int count,
            float phase,
            boolean isFallFlying,
            boolean isCrouching,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean damageFlash,
            float rootU,
            float rootStartV,
            float rootStepU,
            float rootStepV,
            float tipStartU,
            float tipEndU,
            float tipStartV,
            float tipEndV,
            float maxHalfW,
            float wBase,
            float tierLag
    ) {
        int denom = Math.max(1, count - 1);
        float baseDeg = 22.0f + wBase * 180.0f;
        for (int i = 0; i < count; i++) {
            float fn = (count == 1) ? 0.0f : ((float) i / denom);
            int presetRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, fn * 0.16f + tierLag * 0.08f, damageFlash);
            int coreRgb = WingsLogic.blendRgb(0xFFFFFF, presetRgb, damageFlash ? 0.85f : 0.24f);
            int tipRgb = WingsLogic.blendRgb(0xFFFFFF, presetRgb, damageFlash ? 0.95f : 0.88f);

            int cr = (coreRgb >> 16) & 0xFF;
            int cg = (coreRgb >> 8) & 0xFF;
            int cb = coreRgb & 0xFF;
            int tr = (tipRgb >> 16) & 0xFF;
            int tg = (tipRgb >> 8) & 0xFF;
            int tb = tipRgb & 0xFF;

            float sweepRad = WingsLogic.computeWingSweepRad(
                    baseDeg, 17.0f, phase, tierLag + fn * 0.6f, isFallFlying, isCrouching);
            float cosS = (float) Math.cos(sweepRad);
            float sinS = (float) Math.sin(sweepRad);

            float u0 = rootU + i * rootStepU;
            float v0 = rootStartV + i * rootStepV;
            float u2 = tipStartU + (tipEndU - tipStartU) * fn;
            float v2 = tipStartV + (tipEndV - tipStartV) * fn;
            float archLift = (0.10f * (1.0f - 0.55f * fn)) * length;
            float u1 = u0 + (u2 - u0) * 0.54f;
            float v1 = v0 + (v2 - v0) * 0.46f - archLift;

            float w0 = wBase;
            float w1 = wBase + 0.028f * length;
            float w2 = wBase + 0.012f * length;

            float du = u2 - u0;
            float dv = v2 - v0;
            float invLen = 1.0f / Math.max(0.001f, (float) Math.sqrt(du * du + dv * dv));
            float nu = -dv * invLen;
            float nv = du * invLen;

            float hw0 = maxHalfW * 0.55f;
            float hw1 = maxHalfW * (1.0f - 0.22f * fn);
            float hw2 = maxHalfW * 0.18f;

            // Project spine points (root, mid-camber, tip)
            float x0 = side * (u0 * cosS - w0 * sinS), y0 = v0, z0 = u0 * sinS + w0 * cosS;
            float x1 = side * (u1 * cosS - w1 * sinS), y1 = v1, z1 = u1 * sinS + w1 * cosS;
            float x2 = side * (u2 * cosS - w2 * sinS), y2 = v2, z2 = u2 * sinS + w2 * cosS;

            float dxN = side * nu * cosS;
            float dyN = nv;
            float dzN = nu * sinS;

            // 3D cambered feather vanes (+normal and -normal halves)
            for (int dir = -1; dir <= 1; dir += 2) {
                float sx0 = x0 + dir * hw0 * dxN, sy0 = y0 + dir * hw0 * dyN, sz0 = z0 + dir * hw0 * dzN;
                float sx1 = x1 + dir * hw1 * dxN, sy1 = y1 + dir * hw1 * dyN, sz1 = z1 + dir * hw1 * dzN;
                float sx2 = x2 + dir * hw2 * dxN, sy2 = y2 + dir * hw2 * dyN, sz2 = z2 + dir * hw2 * dzN;

                // Inner luminous plume
                emitQuad4(pose, vc,
                        x0, y0, z0, cr, cg, cb, 235,
                        sx0, sy0, sz0, tr, tg, tb, 175,
                        sx1, sy1, sz1, tr, tg, tb, 185,
                        x1, y1, z1, cr, cg, cb, 240);
                emitQuad4(pose, vc,
                        x1, y1, z1, cr, cg, cb, 240,
                        sx1, sy1, sz1, tr, tg, tb, 185,
                        sx2, sy2, sz2, tr, tg, tb, 35,
                        x2, y2, z2, cr, cg, cb, 85);

                // Soft ethereal outer feather halo
                float g1 = hw1 * 1.45f;
                float gx0 = x0 + dir * hw0 * 1.45f * dxN, gy0 = y0 + dir * hw0 * 1.45f * dyN, gz0 = z0 + dir * hw0 * 1.45f * dzN;
                float gx1 = x1 + dir * g1 * dxN, gy1 = y1 + dir * g1 * dyN, gz1 = z1 + dir * g1 * dzN;
                float gx2 = x2 + dir * hw2 * 1.65f * dxN, gy2 = y2 + dir * hw2 * 1.65f * dyN, gz2 = z2 + dir * hw2 * 1.65f * dzN;
                emitQuad4(pose, vc,
                        sx0, sy0, sz0, tr, tg, tb, 80,
                        gx0, gy0, gz0, tr, tg, tb, 0,
                        gx1, gy1, gz1, tr, tg, tb, 0,
                        sx1, sy1, sz1, tr, tg, tb, 85);
                emitQuad4(pose, vc,
                        sx1, sy1, sz1, tr, tg, tb, 85,
                        gx1, gy1, gz1, tr, tg, tb, 0,
                        gx2, gy2, gz2, tr, tg, tb, 0,
                        sx2, sy2, sz2, tr, tg, tb, 0);
            }
        }
    }

    private static void renderDemonGeometry(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float length,
            float flapSpeed,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean isFallFlying,
            boolean isCrouching,
            boolean damageFlash
    ) {
        float phase = WingsLogic.computeFlapPhase(ageInTicks, flapSpeed, 0.12f, isFallFlying, 1.9f);
        float baseSweep = WingsLogic.computeWingSweepRad(28.0f, 16.0f, phase, 0.0f, isFallFlying, isCrouching);
        float tipSweep = WingsLogic.computeWingSweepRad(38.0f, 20.0f, phase, 0.70f, isFallFlying, isCrouching);
        float midSweep = baseSweep * 0.55f + tipSweep * 0.45f;

        float cosB = (float) Math.cos(baseSweep), sinB = (float) Math.sin(baseSweep);
        float cosM = (float) Math.cos(midSweep), sinM = (float) Math.sin(midSweep);
        float cosT = (float) Math.cos(tipSweep), sinT = (float) Math.sin(tipSweep);

        int mainRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, 0.0f, damageFlash);
        int boneRgb = WingsLogic.blendRgb(0x140E1A, mainRgb, 0.16f);
        int br = (boneRgb >> 16) & 0xFF, bg = (boneRgb >> 8) & 0xFF, bb = boneRgb & 0xFF;

        float boneThick = 0.026f * length;
        float rootU = 0.06f * length, rootV = -0.03f * length;
        float elbowU = 0.42f * length, elbowV = -0.52f * length;
        float hornU = 0.45f * length, hornV = -0.66f * length;

        for (int s = 0; s < 2; s++) {
            float side = (s == 0) ? -1.0f : 1.0f;

            float rx = side * rootU * cosB, ry = rootV, rz = rootU * sinB;
            float ex = side * elbowU * cosB, ey = elbowV, ez = elbowU * sinB;
            float hx = side * hornU * cosB, hy = hornV, hz = hornU * sinB;

            // Main obsidian skeletal arm & sharp upper elbow claw horn
            emitTaperedPrism(pose, vc, rx, ry, rz, ex, ey, ez, boneThick * 1.15f, boneThick * 0.85f, br, bg, bb, 250);
            emitTaperedPrism(pose, vc, ex, ey, ez, hx, hy, hz, boneThick * 0.75f, boneThick * 0.15f, br, bg, bb, 250);

            // 4 articulated 3D finger ribs + 4 concave scalloped webbed bays
            for (int i = 0; i < 4; i++) {
                int rIdx = i * 5;
                float mU = DEMON_RIBS[rIdx] * length, mV = DEMON_RIBS[rIdx + 1] * length;
                float tU = DEMON_RIBS[rIdx + 2] * length, tV = DEMON_RIBS[rIdx + 3] * length;

                float mx = side * mU * cosM, my = mV, mz = mU * sinM;
                float tx = side * tU * cosT, ty = tV, tz = tU * sinT;

                float fingerR = boneThick * (0.72f - i * 0.10f);
                emitTaperedPrism(pose, vc, ex, ey, ez, mx, my, mz, fingerR, fingerR * 0.78f, br, bg, bb, 250);
                emitTaperedPrism(pose, vc, mx, my, mz, tx, ty, tz, fingerR * 0.78f, fingerR * 0.25f, br, bg, bb, 250);

                // Neighbor rib / waist edge for webbed bay i -> i + 1
                int nIdx = (i + 1) * 5;
                float nmU = DEMON_RIBS[nIdx] * length, nmV = DEMON_RIBS[nIdx + 1] * length;
                float ntU = DEMON_RIBS[nIdx + 2] * length, ntV = DEMON_RIBS[nIdx + 3] * length;
                float anchorX = (i == 3) ? rx : ex;
                float anchorY = (i == 3) ? ry : ey;
                float anchorZ = (i == 3) ? rz : ez;
                float nmx = side * nmU * cosM, nmy = nmV, nmz = nmU * sinM;
                float ntx = side * ntU * cosT, nty = ntV, ntz = ntU * sinT;

                int bayRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, i * 0.12f, damageFlash);
                int darkWebRgb = WingsLogic.blendRgb(0x180914, bayRgb, 0.34f);
                int rimWebRgb = WingsLogic.blendRgb(bayRgb, 0xFFFFFF, 0.20f);
                int dr = (darkWebRgb >> 16) & 0xFF, dg = (darkWebRgb >> 8) & 0xFF, db = darkWebRgb & 0xFF;
                int wr = (bayRgb >> 16) & 0xFF, wg = (bayRgb >> 8) & 0xFF, wb = bayRgb & 0xFF;
                int tr = (rimWebRgb >> 16) & 0xFF, tg = (rimWebRgb >> 8) & 0xFF, tb = rimWebRgb & 0xFF;

                // Subdivide web bay into 4 curved strips for concave scalloped bottom edge
                final int steps = 4;
                float prevTopX = mx, prevTopY = my, prevTopZ = mz;
                float prevBotX = tx, prevBotY = ty, prevBotZ = tz;
                for (int k = 1; k <= steps; k++) {
                    float f = (float) k / steps;
                    float curTopX = mx + (nmx - mx) * f;
                    float curTopY = my + (nmy - my) * f;
                    float curTopZ = mz + (nmz - mz) * f;

                    float scallop = 0.13f * length * 4.0f * f * (1.0f - f);
                    float curBotX = tx + (ntx - tx) * f;
                    float curBotY = ty + (nty - ty) * f - scallop;
                    float curBotZ = tz + (ntz - tz) * f;

                    // Upper fan from elbow/root anchor to mid bridge
                    emitQuad4(pose, vc,
                            ex, ey, ez, dr, dg, db, 215,
                            anchorX, anchorY, anchorZ, dr, dg, db, 215,
                            curTopX, curTopY, curTopZ, dr, dg, db, 195,
                            prevTopX, prevTopY, prevTopZ, dr, dg, db, 195);
                    // Main scalloped wing membrane strip
                    emitQuad4(pose, vc,
                            prevTopX, prevTopY, prevTopZ, dr, dg, db, 195,
                            curTopX, curTopY, curTopZ, dr, dg, db, 195,
                            curBotX, curBotY, curBotZ, wr, wg, wb, 170,
                            prevBotX, prevBotY, prevBotZ, wr, wg, wb, 170);
                    // Glowing thin scalloped outer trim
                    float trim = 0.015f * length;
                    emitQuad4(pose, vc,
                            prevBotX, prevBotY - trim, prevBotZ, wr, wg, wb, 225,
                            curBotX, curBotY - trim, curBotZ, wr, wg, wb, 225,
                            curBotX, curBotY + trim, curBotZ, tr, tg, tb, 240,
                            prevBotX, prevBotY + trim, prevBotZ, tr, tg, tb, 240);

                    prevTopX = curTopX; prevTopY = curTopY; prevTopZ = curTopZ;
                    prevBotX = curBotX; prevBotY = curBotY; prevBotZ = curBotZ;
                }
            }
        }
    }

    private static void renderButterflyGeometry(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float length,
            float flapSpeed,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean isFallFlying,
            boolean isCrouching,
            boolean damageFlash
    ) {
        float phase = WingsLogic.computeFlapPhase(ageInTicks, flapSpeed, 0.14f, isFallFlying, 1.85f);
        float foreSweep = WingsLogic.computeWingSweepRad(28.0f, 22.0f, phase, 0.0f, isFallFlying, isCrouching);
        float hindSweep = WingsLogic.computeWingSweepRad(34.0f, 20.0f, phase, 0.55f, isFallFlying, isCrouching);

        int foreRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, 0.0f, damageFlash);
        int hindRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, 0.16f, damageFlash);

        for (int s = 0; s < 2; s++) {
            float side = (s == 0) ? -1.0f : 1.0f;
            emitButterflyLobe(pose, vc, side, length, BUTTERFLY_FORE_UV, 0.54f, -0.34f,
                    0.014f * length, foreSweep, foreRgb, 0.72f, -0.52f);
            emitButterflyLobe(pose, vc, side, length, BUTTERFLY_HIND_UV, 0.42f, 0.34f,
                    0.000f, hindSweep, hindRgb, 0.52f, 0.48f);
        }
    }

    private static void emitButterflyLobe(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float side,
            float length,
            float[] uvTable,
            float centerNormU,
            float centerNormV,
            float wDepth,
            float sweepRad,
            int lobeRgb,
            float spotNormU,
            float spotNormV
    ) {
        float cosS = (float) Math.cos(sweepRad);
        float sinS = (float) Math.sin(sweepRad);

        float cu = (0.05f + centerNormU * 1.02f) * length;
        float cv = centerNormV * 0.85f * length;
        float cx = side * (cu * cosS - wDepth * sinS);
        float cy = cv;
        float cz = cu * sinS + wDepth * cosS;

        int centerRgb = WingsLogic.blendRgb(0xFFFFFF, lobeRgb, 0.36f);
        int darkRimRgb = WingsLogic.blendRgb(0x140E1C, lobeRgb, 0.24f);
        int cr = (centerRgb >> 16) & 0xFF, cg = (centerRgb >> 8) & 0xFF, cb = centerRgb & 0xFF;
        int lr = (lobeRgb >> 16) & 0xFF, lg = (lobeRgb >> 8) & 0xFF, lb = lobeRgb & 0xFF;
        int rr = (darkRimRgb >> 16) & 0xFF, rg = (darkRimRgb >> 8) & 0xFF, rb = darkRimRgb & 0xFF;

        int count = uvTable.length / 2;
        for (int i = 0; i < count; i++) {
            int next = (i + 1) % count;
            float au = (0.05f + uvTable[i * 2] * 1.02f) * length;
            float av = uvTable[i * 2 + 1] * 0.85f * length;
            float bu = (0.05f + uvTable[next * 2] * 1.02f) * length;
            float bv = uvTable[next * 2 + 1] * 0.85f * length;

            float iau = cu + (au - cu) * 0.88f, iav = cv + (av - cv) * 0.88f;
            float ibu = cu + (bu - cu) * 0.88f, ibv = cv + (bv - cv) * 0.88f;

            float iax = side * (iau * cosS - wDepth * sinS), iay = iav, iaz = iau * sinS + wDepth * cosS;
            float ibx = side * (ibu * cosS - wDepth * sinS), iby = ibv, ibz = ibu * sinS + wDepth * cosS;
            float ax = side * (au * cosS - wDepth * sinS), ay = av, az = au * sinS + wDepth * cosS;
            float bx = side * (bu * cosS - wDepth * sinS), by = bv, bz = bu * sinS + wDepth * cosS;

            // Stained-glass translucent wing fill (center -> inner rim)
            emitQuad4(pose, vc,
                    cx, cy, cz, cr, cg, cb, 200,
                    cx, cy, cz, cr, cg, cb, 200,
                    ibx, iby, ibz, lr, lg, lb, 165,
                    iax, iay, iaz, lr, lg, lb, 165);

            // Crisp velvety dark outer rim border (88% -> 100%)
            emitQuad4(pose, vc,
                    iax, iay, iaz, lr, lg, lb, 210,
                    ibx, iby, ibz, lr, lg, lb, 210,
                    bx, by, bz, rr, rg, rb, 240,
                    ax, ay, az, rr, rg, rb, 240);

            // Delicate glowing inner vein every 3rd segment
            if ((i & 3) == 1) {
                float vw = 0.008f * length;
                emitQuad4(pose, vc,
                        cx, cy - vw, cz + 0.002f, cr, cg, cb, 225,
                        cx, cy + vw, cz + 0.002f, cr, cg, cb, 225,
                        iax, iay + vw * 0.6f, iaz + 0.002f, lr, lg, lb, 180,
                        iax, iay - vw * 0.6f, iaz + 0.002f, lr, lg, lb, 180);
            }
        }

        // Luminous iridescent eye-spot diamond
        float su = (0.05f + spotNormU * 1.02f) * length;
        float sv = spotNormV * 0.85f * length;
        float sr = 0.065f * length;
        float sw = wDepth + 0.003f;
        float sxN = side * (su * cosS - sw * sinS), syN = sv - sr, szN = su * sinS + sw * cosS;
        float sxS = sxN, syS = sv + sr, szS = szN;
        float sxW = side * ((su - sr) * cosS - sw * sinS), syW = sv, szW = (su - sr) * sinS + sw * cosS;
        float sxE = side * ((su + sr) * cosS - sw * sinS), syE = sv, szE = (su + sr) * sinS + sw * cosS;
        emitQuad4(pose, vc,
                sxN, syN, szN, 255, 255, 255, 235,
                sxE, syE, szE, cr, cg, cb, 215,
                sxS, syS, szS, lr, lg, lb, 200,
                sxW, syW, szW, cr, cg, cb, 215);
    }

    private static void renderCyberGeometry(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float length,
            float flapSpeed,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean isFallFlying,
            boolean isCrouching,
            boolean damageFlash
    ) {
        float phase = WingsLogic.computeFlapPhase(ageInTicks, flapSpeed, 0.10f, isFallFlying, 1.75f);
        float pylonSweep = WingsLogic.computeWingSweepRad(22.0f, 8.0f, phase, 0.0f, isFallFlying, isCrouching);
        float cosP = (float) Math.cos(pylonSweep), sinP = (float) Math.sin(pylonSweep);

        int baseRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, 0.0f, damageFlash);
        int gunmetal = WingsLogic.blendRgb(0x1E242E, baseRgb, 0.14f);
        int gr = (gunmetal >> 16) & 0xFF, gg = (gunmetal >> 8) & 0xFF, gb = gunmetal & 0xFF;
        int nr = (baseRgb >> 16) & 0xFF, ng = (baseRgb >> 8) & 0xFF, nb = baseRgb & 0xFF;

        float thick = 0.025f * length;
        float rootU = 0.07f * length, rootV = -0.02f * length;
        float midU = 0.25f * length, midV = -0.32f * length;
        float topU = 0.34f * length, topV = -0.54f * length;
        float stabU = 0.22f * length, stabV = 0.18f * length;

        for (int s = 0; s < 2; s++) {
            float side = (s == 0) ? -1.0f : 1.0f;

            float rx = side * rootU * cosP, ry = rootV, rz = rootU * sinP;
            float mx = side * midU * cosP, my = midV, mz = midU * sinP;
            float tx = side * topU * cosP, ty = topV, tz = topU * sinP;
            float stx = side * stabU * cosP, sty = stabV, stz = stabU * sinP;

            // Dark metallic 3D shoulder pylon & stabilizer strut
            emitTaperedPrism(pose, vc, rx, ry, rz, mx, my, mz, thick * 1.1f, thick * 0.9f, gr, gg, gb, 250);
            emitTaperedPrism(pose, vc, mx, my, mz, tx, ty, tz, thick * 0.9f, thick * 0.45f, gr, gg, gb, 250);
            emitTaperedPrism(pose, vc, rx, ry, rz, stx, sty, stz, thick * 0.85f, thick * 0.35f, gr, gg, gb, 250);

            // Neon power conduit along pylon
            emitTaperedPrism(pose, vc, rx, ry, rz + thick, mx, my, mz + thick,
                    thick * 0.35f, thick * 0.30f, nr, ng, nb, 245);

            // 5 Detached floating neon plasma holo-blades with traveling pulse
            for (int k = 0; k < 5; k++) {
                int idx = k * 6;
                float bladeNorm = k / 4.0f;
                float pulse = WingsLogic.computeCyberPulse(ageInTicks, flapSpeed, bladeNorm);
                float bobV = (pulse - 0.5f) * 0.025f * length;
                float bladeSweep = WingsLogic.computeWingSweepRad(
                        24.0f, 11.0f, phase, k * 0.35f, isFallFlying, isCrouching);
                float cosB = (float) Math.cos(bladeSweep), sinB = (float) Math.sin(bladeSweep);

                float u0 = CYBER_BLADES[idx] * length;
                float v0 = CYBER_BLADES[idx + 1] * length + bobV;
                float u2 = CYBER_BLADES[idx + 2] * length;
                float v2 = CYBER_BLADES[idx + 3] * length + bobV;
                float hw = CYBER_BLADES[idx + 4] * length;
                float wOff = CYBER_BLADES[idx + 5] * length;

                float u1 = u0 + (u2 - u0) * 0.62f;
                float v1 = v0 + (v2 - v0) * 0.56f;

                float du = u2 - u0, dv = v2 - v0;
                float invL = 1.0f / Math.max(0.001f, (float) Math.sqrt(du * du + dv * dv));
                float nu = -dv * invL, nv = du * invL;

                int bladeRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, bladeNorm * 0.22f, damageFlash);
                int brightRgb = WingsLogic.blendRgb(bladeRgb, 0xFFFFFF, 0.28f + 0.45f * pulse);
                int hr = (bladeRgb >> 16) & 0xFF, hg = (bladeRgb >> 8) & 0xFF, hb = bladeRgb & 0xFF;
                int pr = (brightRgb >> 16) & 0xFF, pg = (brightRgb >> 8) & 0xFF, pb = brightRgb & 0xFF;

                float x0 = side * (u0 * cosB - wOff * sinB), y0 = v0, z0 = u0 * sinB + wOff * cosB;
                float x1 = side * (u1 * cosB - wOff * sinB), y1 = v1, z1 = u1 * sinB + wOff * cosB;
                float x2 = side * (u2 * cosB - wOff * sinB), y2 = v2, z2 = u2 * sinB + wOff * cosB;
                float dxN = side * nu * cosB, dyN = nv, dzN = nu * sinB;

                int auraAlpha = Math.round(45 + 65 * pulse);
                int bodyAlpha = Math.round(180 + 55 * pulse);

                // Outer pulsing hologram aura
                float ahw0 = hw * 1.15f, ahw1 = hw * 1.45f;
                emitQuad4(pose, vc,
                        x0 - ahw0 * dxN, y0 - ahw0 * dyN, z0 - ahw0 * dzN, hr, hg, hb, auraAlpha,
                        x0 + ahw0 * dxN, y0 + ahw0 * dyN, z0 + ahw0 * dzN, hr, hg, hb, auraAlpha,
                        x1 + ahw1 * dxN, y1 + ahw1 * dyN, z1 + ahw1 * dzN, hr, hg, hb, auraAlpha,
                        x1 - ahw1 * dxN, y1 - ahw1 * dyN, z1 - ahw1 * dzN, hr, hg, hb, auraAlpha);
                emitQuad4(pose, vc,
                        x1 - ahw1 * dxN, y1 - ahw1 * dyN, z1 - ahw1 * dzN, hr, hg, hb, auraAlpha,
                        x1 + ahw1 * dxN, y1 + ahw1 * dyN, z1 + ahw1 * dzN, hr, hg, hb, auraAlpha,
                        x2, y2, z2, hr, hg, hb, 0,
                        x2, y2, z2, hr, hg, hb, 0);

                // Main beveled neon plasma blade
                float bhw0 = hw * 0.65f, bhw1 = hw * 0.92f;
                emitQuad4(pose, vc,
                        x0 - bhw0 * dxN, y0 - bhw0 * dyN, z0 - bhw0 * dzN, hr, hg, hb, bodyAlpha,
                        x0 + bhw0 * dxN, y0 + bhw0 * dyN, z0 + bhw0 * dzN, hr, hg, hb, bodyAlpha,
                        x1 + bhw1 * dxN, y1 + bhw1 * dyN, z1 + bhw1 * dzN, pr, pg, pb, bodyAlpha,
                        x1 - bhw1 * dxN, y1 - bhw1 * dyN, z1 - bhw1 * dzN, pr, pg, pb, bodyAlpha);
                emitQuad4(pose, vc,
                        x1 - bhw1 * dxN, y1 - bhw1 * dyN, z1 - bhw1 * dzN, pr, pg, pb, bodyAlpha,
                        x1 + bhw1 * dxN, y1 + bhw1 * dyN, z1 + bhw1 * dzN, pr, pg, pb, bodyAlpha,
                        x2, y2, z2, pr, pg, pb, 220,
                        x2, y2, z2, pr, pg, pb, 220);

                // White-hot inner energy filament
                float fhw = hw * 0.25f;
                emitQuad4(pose, vc,
                        x0 - fhw * dxN, y0 - fhw * dyN, z0 - fhw * dzN + 0.002f, 255, 255, 255, 245,
                        x0 + fhw * dxN, y0 + fhw * dyN, z0 + fhw * dzN + 0.002f, 255, 255, 255, 245,
                        x1 + fhw * dxN, y1 + fhw * dyN, z1 + fhw * dzN + 0.002f, 255, 255, 255, 245,
                        x1 - fhw * dxN, y1 - fhw * dyN, z1 - fhw * dzN + 0.002f, 255, 255, 255, 245);
            }
        }
    }

    private static void renderPhoenixGeometry(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float length,
            float flapSpeed,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean isFallFlying,
            boolean isCrouching,
            boolean damageFlash
    ) {
        float safeTicks = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
        float phase = WingsLogic.computeFlapPhase(ageInTicks, flapSpeed, 0.13f, isFallFlying, 1.9f);
        for (int s = 0; s < 2; s++) {
            float side = (s == 0) ? -1.0f : 1.0f;
            // Blazing outer primary flame plumes + inner ember layer
            for (int tier = 0; tier < 2; tier++) {
                int count = (tier == 0) ? 9 : 6;
                float scaleL = (tier == 0) ? 1.0f : 0.66f;
                float wBase = (tier == 0) ? 0.00f : 0.022f * length;
                int denom = Math.max(1, count - 1);

                for (int i = 0; i < count; i++) {
                    float fn = (float) i / denom;
                    int baseRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, fn * 0.14f, damageFlash);
                    int hotCoreRgb = WingsLogic.blendRgb(0xFFF7B8, baseRgb, damageFlash ? 0.80f : 0.28f);
                    int emberTipRgb = WingsLogic.blendRgb(baseRgb, 0xFF3300, damageFlash ? 0.90f : 0.52f);

                    int cr = (hotCoreRgb >> 16) & 0xFF, cg = (hotCoreRgb >> 8) & 0xFF, cb = hotCoreRgb & 0xFF;
                    int mr = (baseRgb >> 16) & 0xFF, mg = (baseRgb >> 8) & 0xFF, mb = baseRgb & 0xFF;
                    int er = (emberTipRgb >> 16) & 0xFF, eg = (emberTipRgb >> 8) & 0xFF, eb = emberTipRgb & 0xFF;

                    float sweepRad = WingsLogic.computeWingSweepRad(
                            23.0f + tier * 4.0f, 19.0f, phase, fn * 0.55f + tier * 0.25f, isFallFlying, isCrouching);
                    float cosS = (float) Math.cos(sweepRad), sinS = (float) Math.sin(sweepRad);

                    float flicker = (float) Math.sin(safeTicks * 0.28f + i * 1.35f + tier) * 0.045f * length;
                    float u0 = (0.07f + fn * 0.12f) * length * scaleL;
                    float v0 = (-0.08f + fn * 0.26f) * length * scaleL;
                    float u2 = (1.14f - fn * 0.52f) * length * scaleL + flicker * 0.4f;
                    float v2 = (-0.72f + fn * 1.12f) * length * scaleL;

                    // S-curve phoenix flame arch: dips at mid, licks UPWARD at tip
                    float u1 = u0 + (u2 - u0) * 0.56f;
                    float v1 = v0 + (v2 - v0) * 0.62f + 0.06f * length * scaleL;
                    float vTipUp = v2 - (0.14f * (1.0f - 0.5f * fn)) * length * scaleL + flicker;

                    float du = u2 - u0, dv = vTipUp - v0;
                    float invL = 1.0f / Math.max(0.001f, (float) Math.sqrt(du * du + dv * dv));
                    float nu = -dv * invL, nv = du * invL;
                    float hw0 = 0.042f * length * scaleL;
                    float hw1 = 0.072f * length * scaleL;

                    float x0 = side * (u0 * cosS - wBase * sinS), y0 = v0, z0 = u0 * sinS + wBase * cosS;
                    float x1 = side * (u1 * cosS - wBase * sinS), y1 = v1, z1 = u1 * sinS + wBase * cosS;
                    float x2 = side * (u2 * cosS - wBase * sinS), y2 = vTipUp, z2 = u2 * sinS + wBase * cosS;
                    float dxN = side * nu * cosS, dyN = nv, dzN = nu * sinS;

                    // Flame core + outer fire halo
                    emitQuad4(pose, vc,
                            x0 - hw0 * dxN, y0 - hw0 * dyN, z0 - hw0 * dzN, cr, cg, cb, 240,
                            x0 + hw0 * dxN, y0 + hw0 * dyN, z0 + hw0 * dzN, cr, cg, cb, 240,
                            x1 + hw1 * dxN, y1 + hw1 * dyN, z1 + hw1 * dzN, mr, mg, mb, 215,
                            x1 - hw1 * dxN, y1 - hw1 * dyN, z1 - hw1 * dzN, mr, mg, mb, 215);
                    emitQuad4(pose, vc,
                            x1 - hw1 * dxN, y1 - hw1 * dyN, z1 - hw1 * dzN, mr, mg, mb, 215,
                            x1 + hw1 * dxN, y1 + hw1 * dyN, z1 + hw1 * dzN, mr, mg, mb, 215,
                            x2, y2, z2, er, eg, eb, 235,
                            x2, y2, z2, er, eg, eb, 235);

                    float ghw = hw1 * 1.65f;
                    emitQuad4(pose, vc,
                            x0 - hw0 * 1.5f * dxN, y0 - hw0 * 1.5f * dyN, z0 - hw0 * 1.5f * dzN, mr, mg, mb, 85,
                            x0 + hw0 * 1.5f * dxN, y0 + hw0 * 1.5f * dyN, z0 + hw0 * 1.5f * dzN, mr, mg, mb, 85,
                            x1 + ghw * dxN, y1 + ghw * dyN, z1 + ghw * dzN, er, eg, eb, 65,
                            x1 - ghw * dxN, y1 - ghw * dyN, z1 - ghw * dzN, er, eg, eb, 65);
                    emitQuad4(pose, vc,
                            x1 - ghw * dxN, y1 - ghw * dyN, z1 - ghw * dzN, er, eg, eb, 65,
                            x1 + ghw * dxN, y1 + ghw * dyN, z1 + ghw * dzN, er, eg, eb, 65,
                            x2, y2 - 0.05f * length, z2, er, eg, eb, 0,
                            x2, y2 - 0.05f * length, z2, er, eg, eb, 0);
                }
            }

            // Flowing Phoenix tail flame streamer ribbon
            int tailRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, 0.25f, damageFlash);
            int tipTailRgb = WingsLogic.blendRgb(tailRgb, 0xFF2A00, 0.6f);
            int tr = (tailRgb >> 16) & 0xFF, tg = (tailRgb >> 8) & 0xFF, tb = tailRgb & 0xFF;
            int er = (tipTailRgb >> 16) & 0xFF, eg = (tipTailRgb >> 8) & 0xFF, eb = tipTailRgb & 0xFF;
            float tailSweep = WingsLogic.computeWingSweepRad(32.0f, 14.0f, phase, 0.8f, isFallFlying, isCrouching);
            float cosT = (float) Math.cos(tailSweep), sinT = (float) Math.sin(tailSweep);
            float prevX = side * 0.12f * length * cosT, prevY = 0.18f * length, prevZ = 0.12f * length * sinT;
            float prevW = 0.036f * length;
            for (int seg = 1; seg <= 4; seg++) {
                float f = seg / 4.0f;
                float wave = (float) Math.sin(safeTicks * 0.18f - seg * 0.9f) * 0.07f * length * f;
                float curU = (0.12f + f * 0.34f) * length + wave;
                float curX = side * curU * cosT;
                float curY = (0.18f + f * 0.72f) * length;
                float curZ = curU * sinT + f * 0.12f * length;
                float curW = prevW * 0.72f;
                int a0 = Math.round(215 * (1.0f - (seg - 1) * 0.22f));
                int a1 = Math.round(215 * (1.0f - seg * 0.22f));
                emitQuad4(pose, vc,
                        prevX - prevW, prevY, prevZ, tr, tg, tb, a0,
                        prevX + prevW, prevY, prevZ, tr, tg, tb, a0,
                        curX + curW, curY, curZ, er, eg, eb, a1,
                        curX - curW, curY, curZ, er, eg, eb, a1);
                prevX = curX; prevY = curY; prevZ = curZ; prevW = curW;
            }
        }
    }

    private static void renderCrystalGeometry(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float length,
            float flapSpeed,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean isFallFlying,
            boolean isCrouching,
            boolean damageFlash
    ) {
        float safeTicks = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
        float phase = WingsLogic.computeFlapPhase(ageInTicks, flapSpeed, 0.10f, isFallFlying, 1.75f);
        int shardCount = CRYSTAL_SHARDS.length / 6;

        for (int s = 0; s < 2; s++) {
            float side = (s == 0) ? -1.0f : 1.0f;
            for (int i = 0; i < shardCount; i++) {
                int idx = i * 6;
                float fn = (float) i / Math.max(1, shardCount - 1);
                float bob = (float) Math.sin(safeTicks * 0.11f + i * 0.95f) * 0.028f * length;
                float sweepRad = WingsLogic.computeWingSweepRad(
                        24.0f + i * 2.0f, 14.0f, phase, fn * 0.55f, isFallFlying, isCrouching);
                float cosS = (float) Math.cos(sweepRad), sinS = (float) Math.sin(sweepRad);

                float u0 = CRYSTAL_SHARDS[idx] * length;
                float v0 = CRYSTAL_SHARDS[idx + 1] * length + bob * 0.5f;
                float u2 = CRYSTAL_SHARDS[idx + 2] * length;
                float v2 = CRYSTAL_SHARDS[idx + 3] * length + bob;
                float hw = CRYSTAL_SHARDS[idx + 4] * length;
                float ridgeW = CRYSTAL_SHARDS[idx + 5] * length;

                float u1 = u0 + (u2 - u0) * 0.46f;
                float v1 = v0 + (v2 - v0) * 0.46f;

                float du = u2 - u0, dv = v2 - v0;
                float invL = 1.0f / Math.max(0.001f, (float) Math.sqrt(du * du + dv * dv));
                float nu = -dv * invL, nv = du * invL;

                int gemRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, fn * 0.20f, damageFlash);
                int topFacetRgb = WingsLogic.blendRgb(0xFFFFFF, gemRgb, 0.34f);
                int botFacetRgb = WingsLogic.blendRgb(0x0B1324, gemRgb, 0.64f);
                int tr = (topFacetRgb >> 16) & 0xFF, tg = (topFacetRgb >> 8) & 0xFF, tb = topFacetRgb & 0xFF;
                int br = (botFacetRgb >> 16) & 0xFF, bg = (botFacetRgb >> 8) & 0xFF, bb = botFacetRgb & 0xFF;
                int gr = (gemRgb >> 16) & 0xFF, gg = (gemRgb >> 8) & 0xFF, gb = gemRgb & 0xFF;

                // Root point, 3D raised center ridge point, and sharp crystal tip
                float rx = side * u0 * cosS, ry = v0, rz = u0 * sinS;
                float tx = side * u2 * cosS, ty = v2, tz = u2 * sinS;
                float mxF = side * (u1 * cosS - ridgeW * sinS), myF = v1, mzF = u1 * sinS + ridgeW * cosS;
                float dxN = side * nu * cosS, dyN = nv, dzN = nu * sinS;
                float exU = side * u1 * cosS + hw * dxN, eyU = v1 + hw * dyN, ezU = u1 * sinS + hw * dzN;
                float exL = side * u1 * cosS - hw * dxN, eyL = v1 - hw * dyN, ezL = u1 * sinS - hw * dzN;

                // Upper specular crystal facet & Lower refractive gem facet
                emitQuad4(pose, vc,
                        rx, ry, rz, 255, 255, 255, 240,
                        exU, eyU, ezU, tr, tg, tb, 215,
                        tx, ty, tz, tr, tg, tb, 235,
                        mxF, myF, mzF, 255, 255, 255, 245);
                emitQuad4(pose, vc,
                        rx, ry, rz, tr, tg, tb, 230,
                        mxF, myF, mzF, tr, tg, tb, 240,
                        tx, ty, tz, br, bg, bb, 225,
                        exL, eyL, ezL, br, bg, bb, 210);

                // Crystalline outer shimmer aura
                float ahw = hw * 1.55f;
                emitQuad4(pose, vc,
                        rx, ry, rz, gr, gg, gb, 75,
                        side * u1 * cosS + ahw * dxN, v1 + ahw * dyN, u1 * sinS + ahw * dzN, gr, gg, gb, 45,
                        tx, ty, tz, gr, gg, gb, 0,
                        side * u1 * cosS - ahw * dxN, v1 - ahw * dyN, u1 * sinS - ahw * dzN, gr, gg, gb, 45);
            }
        }
    }

    private static void renderSeraphGeometry(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float length,
            float flapSpeed,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean isFallFlying,
            boolean isCrouching,
            boolean damageFlash
    ) {
        float safeTicks = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
        float phase = WingsLogic.computeFlapPhase(ageInTicks, flapSpeed, 0.11f, isFallFlying, 1.85f);

        // Sacred rotating octagonal halo behind shoulder blades
        int haloRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, 0.0f, damageFlash);
        int brightHalo = WingsLogic.blendRgb(0xFFF6C8, haloRgb, 0.38f);
        int hr = (brightHalo >> 16) & 0xFF, hg = (brightHalo >> 8) & 0xFF, hb = brightHalo & 0xFF;
        float haloR = 0.25f * length;
        float haloThick = 0.022f * length;
        float haloZ = 0.055f * length;
        float haloY = -0.10f * length;
        float rot = safeTicks * 0.035f;
        for (int k = 0; k < 8; k++) {
            float a0 = rot + k * (float) (Math.PI / 4.0);
            float a1 = rot + (k + 1) * (float) (Math.PI / 4.0);
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            emitQuad4(pose, vc,
                    c0 * (haloR - haloThick), haloY + s0 * (haloR - haloThick), haloZ, hr, hg, hb, 235,
                    c0 * (haloR + haloThick), haloY + s0 * (haloR + haloThick), haloZ, hr, hg, hb, 165,
                    c1 * (haloR + haloThick), haloY + s1 * (haloR + haloThick), haloZ, hr, hg, hb, 165,
                    c1 * (haloR - haloThick), haloY + s1 * (haloR - haloThick), haloZ, hr, hg, hb, 235);
        }

        // 6-Winged Seraphim: Upper Ascendant Pair, Middle Sovereign Pair, Lower Guardian Pair
        for (int s = 0; s < 2; s++) {
            float side = (s == 0) ? -1.0f : 1.0f;
            // Upper Ascendant Pair (sweeps high above head)
            emitAngelTier(pose, vc, side, length, 5, phase, isFallFlying, isCrouching,
                    colorPreset, customRgb, ageInTicks, damageFlash,
                    0.07f * length, -0.14f * length, 0.012f * length, 0.020f * length,
                    0.88f * length, 0.62f * length, -0.84f * length, -0.32f * length,
                    0.058f * length, 0.00f, 0.0f);
            // Middle Sovereign Pair (wide horizontal span)
            emitAngelTier(pose, vc, side, length, 6, phase, isFallFlying, isCrouching,
                    colorPreset, customRgb, ageInTicks, damageFlash,
                    0.09f * length, -0.02f * length, 0.014f * length, 0.024f * length,
                    1.16f * length, 0.78f * length, -0.22f * length, 0.22f * length,
                    0.064f * length, 0.018f * length, 0.45f);
            // Lower Guardian Pair (sweeps downward framing waist/legs)
            emitAngelTier(pose, vc, side, length, 5, phase, isFallFlying, isCrouching,
                    colorPreset, customRgb, ageInTicks, damageFlash,
                    0.07f * length, 0.10f * length, 0.012f * length, 0.024f * length,
                    0.82f * length, 0.42f * length, 0.28f * length, 0.78f * length,
                    0.056f * length, 0.034f * length, 0.90f);
        }
    }

    private static void renderVoidGeometry(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float length,
            float flapSpeed,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean isFallFlying,
            boolean isCrouching,
            boolean damageFlash
    ) {
        float safeTicks = Float.isFinite(ageInTicks) ? ageInTicks : 0.0f;
        float phase = WingsLogic.computeFlapPhase(ageInTicks, flapSpeed, 0.11f, isFallFlying, 1.85f);
        float baseSweep = WingsLogic.computeWingSweepRad(26.0f, 15.0f, phase, 0.0f, isFallFlying, isCrouching);
        float cosB = (float) Math.cos(baseSweep), sinB = (float) Math.sin(baseSweep);

        int mainRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, 0.0f, damageFlash);
        int abyssRgb = WingsLogic.blendRgb(0x08060D, mainRgb, 0.10f);
        int ar = (abyssRgb >> 16) & 0xFF, ag = (abyssRgb >> 8) & 0xFF, ab = abyssRgb & 0xFF;

        for (int s = 0; s < 2; s++) {
            float side = (s == 0) ? -1.0f : 1.0f;

            // Dark abyssal shoulder crescent spine
            float c0x = side * 0.08f * length * cosB, c0y = -0.04f * length, c0z = 0.08f * length * sinB;
            float c1x = side * 0.34f * length * cosB, c1y = -0.34f * length, c1z = 0.34f * length * sinB;
            float c2x = side * 0.24f * length * cosB, c2y = 0.22f * length, c2z = 0.24f * length * sinB;
            emitTaperedPrism(pose, vc, c0x, c0y, c0z, c1x, c1y, c1z, 0.030f * length, 0.018f * length, ar, ag, ab, 250);
            emitTaperedPrism(pose, vc, c0x, c0y, c0z, c2x, c2y, c2z, 0.026f * length, 0.014f * length, ar, ag, ab, 250);

            // 6 Undulating dark-matter tendrils wrapped in glowing neon plasma sheath
            int tendrils = 6;
            for (int i = 0; i < tendrils; i++) {
                float fn = (float) i / (tendrils - 1);
                int rimRgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, fn * 0.20f, damageFlash);
                int rr = (rimRgb >> 16) & 0xFF, rg = (rimRgb >> 8) & 0xFF, rb = rimRgb & 0xFF;

                float startU = (0.14f + (1.0f - Math.abs(fn - 0.35f)) * 0.18f) * length;
                float startV = (-0.30f + fn * 0.52f) * length;
                float endU = (1.18f - fn * 0.48f) * length;
                float endV = (-0.64f + fn * 1.24f) * length;

                float du = endU - startU, dv = endV - startV;
                float invL = 1.0f / Math.max(0.001f, (float) Math.sqrt(du * du + dv * dv));
                float nu = -dv * invL, nv = du * invL;

                float prevX = side * startU * cosB, prevY = startV, prevZ = startU * sinB;
                float prevCoreW = 0.030f * length;
                float prevGlowW = 0.068f * length;
                float prevDxN = side * nu * cosB, prevDyN = nv, prevDzN = nu * sinB;

                final int segs = 4;
                for (int seg = 1; seg <= segs; seg++) {
                    float f = (float) seg / segs;
                    float whip = (float) Math.sin(safeTicks * 0.17f - seg * 0.85f - i * 0.55f) * 0.065f * length * f;
                    float segSweep = WingsLogic.computeWingSweepRad(
                            26.0f + seg * 3.0f, 16.0f, phase, fn * 0.4f + seg * 0.18f, isFallFlying, isCrouching);
                    float cosS = (float) Math.cos(segSweep), sinS = (float) Math.sin(segSweep);

                    float u = startU + (endU - startU) * f + nu * whip;
                    float v = startV + (endV - startV) * f + nv * whip;
                    float curX = side * u * cosS, curY = v, curZ = u * sinS;
                    float curDxN = side * nu * cosS, curDyN = nv, curDzN = nu * sinS;
                    float curCoreW = prevCoreW * 0.72f;
                    float curGlowW = prevGlowW * 0.74f;

                    int glowA0 = Math.round(190 * (1.0f - (seg - 1) * 0.22f));
                    int glowA1 = Math.round(190 * (1.0f - seg * 0.22f));
                    int coreA0 = Math.round(245 * (1.0f - (seg - 1) * 0.18f));
                    int coreA1 = Math.round(245 * (1.0f - seg * 0.18f));

                    // Outer neon void energy sheath
                    emitQuad4(pose, vc,
                            prevX - prevGlowW * prevDxN, prevY - prevGlowW * prevDyN, prevZ - prevGlowW * prevDzN, rr, rg, rb, glowA0,
                            prevX + prevGlowW * prevDxN, prevY + prevGlowW * prevDyN, prevZ + prevGlowW * prevDzN, rr, rg, rb, glowA0,
                            curX + curGlowW * curDxN, curY + curGlowW * curDyN, curZ + curGlowW * curDzN, rr, rg, rb, glowA1,
                            curX - curGlowW * curDxN, curY - curGlowW * curDyN, curZ - curGlowW * curDzN, rr, rg, rb, glowA1);

                    // Pitch-black abyssal inner core
                    emitQuad4(pose, vc,
                            prevX - prevCoreW * prevDxN, prevY - prevCoreW * prevDyN, prevZ - prevCoreW * prevDzN + 0.002f, ar, ag, ab, coreA0,
                            prevX + prevCoreW * prevDxN, prevY + prevCoreW * prevDyN, prevZ + prevCoreW * prevDzN + 0.002f, ar, ag, ab, coreA0,
                            curX + curCoreW * curDxN, curY + curCoreW * curDyN, curZ + curCoreW * curDzN + 0.002f, ar, ag, ab, coreA1,
                            curX - curCoreW * curDxN, curY - curCoreW * curDyN, curZ - curCoreW * curDzN + 0.002f, ar, ag, ab, coreA1);

                    prevX = curX; prevY = curY; prevZ = curZ;
                    prevDxN = curDxN; prevDyN = curDyN; prevDzN = curDzN;
                    prevCoreW = curCoreW; prevGlowW = curGlowW;
                }
            }
        }
    }

    private static void emitTaperedPrism(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float r0, float r1,
            int r, int g, int b,
            int a
    ) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.0005f) {
            return;
        }
        float inv = 1.0f / len;
        float nx = dx * inv, ny = dy * inv, nz = dz * inv;

        // Pick non-parallel up axis to build an orthonormal (u, v) frame without allocations
        float ax = (Math.abs(ny) > 0.9f) ? 1.0f : 0.0f;
        float ay = (Math.abs(ny) > 0.9f) ? 0.0f : 1.0f;
        float az = 0.0f;

        float ux = ny * az - nz * ay;
        float uy = nz * ax - nx * az;
        float uz = nx * ay - ny * ax;
        float uInv = 1.0f / Math.max(0.0005f, (float) Math.sqrt(ux * ux + uy * uy + uz * uz));
        ux *= uInv; uy *= uInv; uz *= uInv;

        float vx = ny * uz - nz * uy;
        float vy = nz * ux - nx * uz;
        float vz = nx * uy - ny * ux;

        float p00x = x0 + (ux + vx) * r0, p00y = y0 + (uy + vy) * r0, p00z = z0 + (uz + vz) * r0;
        float p01x = x0 + (-ux + vx) * r0, p01y = y0 + (-uy + vy) * r0, p01z = z0 + (-uz + vz) * r0;
        float p02x = x0 + (-ux - vx) * r0, p02y = y0 + (-uy - vy) * r0, p02z = z0 + (-uz - vz) * r0;
        float p03x = x0 + (ux - vx) * r0, p03y = y0 + (uy - vy) * r0, p03z = z0 + (uz - vz) * r0;

        float p10x = x1 + (ux + vx) * r1, p10y = y1 + (uy + vy) * r1, p10z = z1 + (uz + vz) * r1;
        float p11x = x1 + (-ux + vx) * r1, p11y = y1 + (-uy + vy) * r1, p11z = z1 + (-uz + vz) * r1;
        float p12x = x1 + (-ux - vx) * r1, p12y = y1 + (-uy - vy) * r1, p12z = z1 + (-uz - vz) * r1;
        float p13x = x1 + (ux - vx) * r1, p13y = y1 + (uy - vy) * r1, p13z = z1 + (uz - vz) * r1;

        int r86 = Math.round(r * 0.86f), g86 = Math.round(g * 0.86f), b86 = Math.round(b * 0.86f);
        int r72 = Math.round(r * 0.72f), g72 = Math.round(g * 0.72f), b72 = Math.round(b * 0.72f);

        emitQuad4(pose, vc, p00x, p00y, p00z, r, g, b, a, p01x, p01y, p01z, r, g, b, a,
                p11x, p11y, p11z, r, g, b, a, p10x, p10y, p10z, r, g, b, a);
        emitQuad4(pose, vc, p01x, p01y, p01z, r86, g86, b86, a, p02x, p02y, p02z, r86, g86, b86, a,
                p12x, p12y, p12z, r86, g86, b86, a, p11x, p11y, p11z, r86, g86, b86, a);
        emitQuad4(pose, vc, p02x, p02y, p02z, r72, g72, b72, a, p03x, p03y, p03z, r72, g72, b72, a,
                p13x, p13y, p13z, r72, g72, b72, a, p12x, p12y, p12z, r72, g72, b72, a);
        emitQuad4(pose, vc, p03x, p03y, p03z, r86, g86, b86, a, p00x, p00y, p00z, r86, g86, b86, a,
                p10x, p10y, p10z, r86, g86, b86, a, p13x, p13y, p13z, r86, g86, b86, a);
        emitQuad4(pose, vc, p10x, p10y, p10z, r, g, b, a, p11x, p11y, p11z, r, g, b, a,
                p12x, p12y, p12z, r, g, b, a, p13x, p13y, p13z, r, g, b, a);
    }

    private static void emitQuad4(
            PoseStack.Pose pose,
            VertexConsumer vc,
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

    private static void renderLinesGeometry(
            PoseStack.Pose pose,
            VertexConsumer vc,
            int linesCount,
            float length,
            float width,
            float fadeStart,
            float flapSpeed,
            int colorPreset,
            int customRgb,
            float ageInTicks,
            boolean isFallFlying,
            boolean damageFlash
    ) {
        int denom = Math.max(1, linesCount - 1);
        float coreHalfW = width * 0.50f;
        float glowHalfW = width * 1.15f;

        for (int s = 0; s < 2; s++) {
            float side = (s == 0) ? -1.0f : 1.0f;
            for (int i = 0; i < linesCount; i++) {
                float featherNorm = (linesCount == 1) ? 0.0f : ((float) i / denom);
                int rgb = WingsLogic.resolveRgb(colorPreset, customRgb, ageInTicks, featherNorm * 0.18f, damageFlash);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                float lenScale = WingsLogic.computeFeatherLengthScale(featherNorm);
                float totalLen = length * lenScale;
                float pitchRad = WingsLogic.computeFeatherPitchDeg(featherNorm) * DEG_TO_RAD;
                float sweepRad = WingsLogic.computeFeatherSweepDeg(featherNorm, ageInTicks, flapSpeed, isFallFlying) * DEG_TO_RAD;

                float cosPitch = (float) Math.cos(pitchRad);
                float sinPitch = (float) Math.sin(pitchRad);
                float cosSweep = (float) Math.cos(sweepRad);
                float sinSweep = (float) Math.sin(sweepRad);

                // Unit outward direction for this feather (side flips X symmetrically)
                float dirX = side * cosPitch * cosSweep;
                float dirY = -sinPitch;
                float dirZ = cosPitch * sinSweep;

                float x0 = side * 0.07f;
                float y0 = i * 0.03f;
                float z0 = 0.0f;

                float midFrac = fadeStart;
                float midLen = totalLen * midFrac;
                float archLift = 0.07f * totalLen * (1.0f - 0.5f * featherNorm);
                float x1 = x0 + dirX * midLen;
                float y1 = y0 + dirY * midLen - archLift;
                float z1 = z0 + dirZ * midLen;

                float tipCurlZ = 0.10f * totalLen;
                float tipDropY = 0.06f * totalLen;
                float x2 = x0 + dirX * totalLen;
                float y2 = y0 + dirY * totalLen + tipDropY;
                float z2 = z0 + dirZ * totalLen + tipCurlZ;

                // Soft outer glow layer (two crossed ribbons for volumetric visibility from all angles)
                emitCrossedRibbon(pose, vc, x0, y0, z0, x1, y1, z1, glowHalfW, glowHalfW * 0.85f, r, g, b, 75, 60);
                emitCrossedRibbon(pose, vc, x1, y1, z1, x2, y2, z2, glowHalfW * 0.85f, glowHalfW * 0.15f, r, g, b, 60, 0);

                // Bright inner energy core
                emitCrossedRibbon(pose, vc, x0, y0, z0, x1, y1, z1, coreHalfW, coreHalfW * 0.85f, r, g, b, 235, 220);
                emitCrossedRibbon(pose, vc, x1, y1, z1, x2, y2, z2, coreHalfW * 0.85f, coreHalfW * 0.15f, r, g, b, 220, 0);
            }
        }
    }

    private static void emitCrossedRibbon(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float halfWStart,
            float halfWEnd,
            int r, int g, int b,
            int aStart, int aEnd
    ) {
        // Horizontal ribbon (spread in Z) + Vertical ribbon (spread in Y) -> '+' cross-section
        emitQuad(pose, vc,
                x0, y0 - halfWStart, z0,
                x0, y0 + halfWStart, z0,
                x1, y1 + halfWEnd, z1,
                x1, y1 - halfWEnd, z1,
                r, g, b, aStart, aEnd);
        emitQuad(pose, vc,
                x0, y0, z0 - halfWStart,
                x0, y0, z0 + halfWStart,
                x1, y1, z1 + halfWEnd,
                x1, y1, z1 - halfWEnd,
                r, g, b, aStart, aEnd);
    }

    private static void emitQuad(
            PoseStack.Pose pose,
            VertexConsumer vc,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            int r, int g, int b,
            int aStart, int aEnd
    ) {
        vc.addVertex(pose, x0, y0, z0).setColor(r, g, b, aStart);
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, aStart);
        vc.addVertex(pose, x2, y2, z2).setColor(r, g, b, aEnd);
        vc.addVertex(pose, x3, y3, z3).setColor(r, g, b, aEnd);
    }

    private static final class DragonWingsModel extends Model<WingsLogic.DragonAnimState> {
        private final ModelPart leftWing;
        private final ModelPart leftWingTip;
        private final ModelPart rightWing;
        private final ModelPart rightWingTip;

        DragonWingsModel(ModelPart leftWing, ModelPart leftWingTip, ModelPart rightWing, ModelPart rightWingTip) {
            super(
                    new ModelPart(List.of(), Map.of("left_wing", leftWing, "right_wing", rightWing)),
                    RenderTypes::entityCutout
            );
            this.leftWing = leftWing;
            this.leftWingTip = leftWingTip;
            this.rightWing = rightWing;
            this.rightWingTip = rightWingTip;
        }

        @Override
        public void setupAnim(WingsLogic.DragonAnimState anim) {
            super.setupAnim(anim);
            this.leftWing.setPos(2.0f, 0.0f, 0.0f);
            this.rightWing.setPos(-2.0f, 0.0f, 0.0f);

            this.leftWing.xRot = anim.wingXRot();
            this.leftWing.yRot = anim.wingYRot();
            this.leftWing.zRot = anim.wingZRot();
            this.leftWingTip.yRot = anim.tipYRot();
            this.leftWingTip.zRot = anim.tipZRot();

            this.rightWing.xRot = anim.wingXRot();
            this.rightWing.yRot = -anim.wingYRot();
            this.rightWing.zRot = -anim.wingZRot();
            this.rightWingTip.yRot = -anim.tipYRot();
            this.rightWingTip.zRot = -anim.tipZRot();
        }
    }
}
