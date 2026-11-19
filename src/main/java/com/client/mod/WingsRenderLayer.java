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
        if (cfg == null || !cfg.wingsEnabled || PanicHandler.isPanicActive()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        boolean isSelf = mc != null && mc.player != null && state.id == mc.player.getId();
        boolean isFriend = false;
        if (!isSelf && cfg.wingsTarget == WingsLogic.TARGET_FRIENDS && mc != null && mc.level != null) {
            Entity entity = mc.level.getEntity(state.id);
            if (entity instanceof Player player) {
                isFriend = cfg.isWheatFarmWhitelisted(player.getName().getString());
            }
        }

        if (!WingsLogic.shouldRenderTarget(
                cfg.wingsEnabled,
                false,
                state.isInvisible,
                state.isSpectator,
                cfg.wingsTarget,
                isSelf,
                isFriend
        )) {
            return;
        }

        boolean hasChestEquip = state.chestEquipment != null && !state.chestEquipment.isEmpty();
        float zAnchor = WingsLogic.computeBackZ(hasChestEquip);
        float yAnchor = WingsLogic.computeAnchorY(cfg.wingsHeight);
        boolean damageFlash = cfg.wingsShowDamage && state.hasRedOverlay;

        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        poseStack.translate(0.0f, yAnchor, zAnchor);

        int mode = WingsLogic.clampMode(cfg.wingsMode);
        if (mode == WingsLogic.MODE_DRAGON && this.dragonWingsModel != null) {
            poseStack.rotateDegrees(Axis.XP, WingsLogic.DRAGON_UPRIGHT_PITCH_DEG);
            float scale = 0.15f * WingsLogic.clampLength(cfg.wingsLength);
            poseStack.scale(scale, scale, scale);
            WingsLogic.DragonAnimState anim = WingsLogic.computeDragonAnim(
                    state.ageInTicks,
                    cfg.wingsFlapSpeed,
                    state.isFallFlying,
                    state.isCrouching
            );
            int rgb = WingsLogic.resolveRgb(cfg.wingsColorPreset, state.ageInTicks, 0.0f, damageFlash);
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
            final int linesCount = WingsLogic.clampLinesCount(cfg.wingsLinesCount);
            final float length = WingsLogic.clampLength(cfg.wingsLength);
            final float width = WingsLogic.clampWidth(cfg.wingsWidth);
            final float fadeStart = WingsLogic.clampFadeStart(cfg.wingsFadeStart);
            final float flapSpeed = WingsLogic.clampFlapSpeed(cfg.wingsFlapSpeed);
            final int colorPreset = WingsLogic.clampColorPreset(cfg.wingsColorPreset);
            final float ageInTicks = state.ageInTicks;
            final boolean isFallFlying = state.isFallFlying;

            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, vc) ->
                    renderLinesGeometry(
                            pose,
                            vc,
                            linesCount,
                            length,
                            width,
                            fadeStart,
                            flapSpeed,
                            colorPreset,
                            ageInTicks,
                            isFallFlying,
                            damageFlash
                    )
            );
        }

        poseStack.popPose();
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
                int rgb = WingsLogic.resolveRgb(colorPreset, ageInTicks, featherNorm * 0.18f, damageFlash);
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
