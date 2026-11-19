package com.client.mod.mixin;

import com.client.mod.AutoSpawnerModule;
import com.client.mod.SpawnerAutoSellModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputOverrideMixin extends ClientInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickTail(CallbackInfo ci) {
        if (com.client.mod.PanicHandler.isPanicActive()) {
            return;
        }
        AutoSpawnerModule mod = AutoSpawnerModule.getInstance();
        SpawnerAutoSellModule sellMod = SpawnerAutoSellModule.getInstance();
        boolean useMain = mod != null && mod.isControllingMovement();
        boolean useSell = !useMain && sellMod != null && sellMod.isControllingMovement();
        if (!useMain && !useSell) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.gui != null && mc.gui.screen() != null) {
            // Strict InventoryMove prevention
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
            return;
        }

        boolean fwd = useMain ? mod.wantsMoveForward() : sellMod.wantsMoveForward();
        boolean back = useMain ? mod.wantsMoveBackward() : sellMod.wantsMoveBackward();
        boolean left = useMain ? mod.wantsMoveLeft() : sellMod.wantsMoveLeft();
        boolean right = useMain ? mod.wantsMoveRight() : sellMod.wantsMoveRight();
        boolean jmp = useMain ? mod.wantsJump() : sellMod.wantsJump();
        boolean sprint = useMain ? mod.wantsSprint() : sellMod.wantsSprint();
        float fVal = (fwd ? 1.0f : 0.0f) - (back ? 1.0f : 0.0f);
        float sVal = (left ? 1.0f : 0.0f) - (right ? 1.0f : 0.0f);
        Vec2 vec = (fVal != 0.0f && sVal != 0.0f) ? new Vec2(sVal, fVal).normalized() : new Vec2(sVal, fVal);
        // Force sneak=false and pass natural sprint bursts
        this.keyPresses = new Input(fwd, back, left, right, jmp, false, sprint);
        this.moveVector = vec;
    }
}
