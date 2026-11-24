package com.client.mod.mixin;

import com.client.mod.CosmeticsLogic;
import com.client.mod.PanicHandler;
import com.client.mod.WingsLogic;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies procedural 3D Pose Emotes (and Locker Hit-preview swing) to {@code PlayerModel}
 * in {@code setupAnim(AvatarRenderState)} TAIL, keeping sleeves/jacket/pants/hat synced.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL")
    )
    private void sestia$onSetupAnimTail(AvatarRenderState state, CallbackInfo ci) {
        if (PanicHandler.isPanicActive() || state == null) {
            return;
        }
        boolean isPreview = state.id == WingsLogic.PREVIEW_ENTITY_ID;
        boolean isSelf = false;
        String remoteName = null;
        if (!isPreview) {
            Minecraft mc = Minecraft.getInstance();
            isSelf = mc != null && mc.player != null && state.id == mc.player.getId();
            if (!isSelf && mc != null && mc.level != null) {
                Entity e = mc.level.getEntity(state.id);
                if (e instanceof Player p) {
                    remoteName = p.getName().getString();
                }
            }
        }

        int emoteId = CosmeticsLogic.resolveActiveEmote(isPreview, isSelf, remoteName, System.currentTimeMillis());
        PlayerModel model = (PlayerModel) (Object) this;

        if (emoteId >= 0) {
            CosmeticsLogic.EmotePose pose = CosmeticsLogic.computeEmotePose(emoteId, state.ageInTicks);
            model.head.xRot = pose.headX();
            model.head.yRot = pose.headY();
            model.head.zRot = pose.headZ();
            model.body.xRot = pose.bodyX();
            model.body.yRot = pose.bodyY();
            if (pose.bodyOffsetY() != 0.0f) {
                model.head.y += pose.bodyOffsetY();
                model.body.y += pose.bodyOffsetY();
                model.leftArm.y += pose.bodyOffsetY();
                model.rightArm.y += pose.bodyOffsetY();
                model.leftLeg.y += pose.bodyOffsetY();
                model.rightLeg.y += pose.bodyOffsetY();
            }
            model.leftArm.xRot = pose.leftArmX();
            model.leftArm.yRot = pose.leftArmY();
            model.leftArm.zRot = pose.leftArmZ();
            model.rightArm.xRot = pose.rightArmX();
            model.rightArm.yRot = pose.rightArmY();
            model.rightArm.zRot = pose.rightArmZ();
            model.leftLeg.xRot = pose.leftLegX();
            model.leftLeg.zRot = pose.leftLegZ();
            model.rightLeg.xRot = pose.rightLegX();
            model.rightLeg.zRot = pose.rightLegZ();

            model.hat.copyFrom(model.head);
            model.jacket.copyFrom(model.body);
            model.leftSleeve.copyFrom(model.leftArm);
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftPants.copyFrom(model.leftLeg);
            model.rightPants.copyFrom(model.rightLeg);
        } else if (isPreview && CosmeticsLogic.getPreviewPose() == CosmeticsLogic.PREVIEW_POSE_HIT) {
            // Smooth cyclical sword-swing pose for the Cosmetic Locker's "Hit" preview button
            float cycle = (state.ageInTicks * 0.14f) % 1.0f;
            float swing = (float) Math.sin(cycle * Math.PI);
            model.rightArm.xRot = -0.35f - swing * 1.45f;
            model.rightArm.yRot = -0.15f - swing * 0.42f;
            model.leftArm.xRot = -0.25f + swing * 0.25f;
            model.body.yRot = -swing * 0.22f;
            model.rightSleeve.copyFrom(model.rightArm);
            model.leftSleeve.copyFrom(model.leftArm);
            model.jacket.copyFrom(model.body);
        }
    }
}
