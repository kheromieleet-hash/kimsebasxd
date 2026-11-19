# Freecam Improvements Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Sestia Client's `Freecam` to match Meteor Client's feel: vanilla Creative-Fly baseline speed (`~0.50` blocks/tick at `1.0x`) with smooth inertia/damping and fine `0.10x` scroll scaling, full interaction lock (`continueAttack` + `AutoBreak`) when `Allow Interact` is off, and camera-aimed reach-checked silent interactions (`Rotate` + suppressed main-character swing) when `Allow Interact` is on.

**Architecture:** Keep the existing virtual camera architecture (`FreecamHandler` + `CameraMixin` + `LocalPlayerMixin` + `MinecraftMixin` + `LivingEntityMixin`) without spawning a fake `CameraEntity`. Pure physics/math helpers live as testable static/package-private methods on `FreecamHandler`, while MC 26.3 mixins wire `continueAttack`, `AutoBreak`, `raycastHitResult`, silent `ServerboundMovePlayerPacket.Rot`, and visual `LivingEntity.swing` suppression.

**Tech Stack:** Java 25, Fabric Loader 0.20.1, SpongePowered Mixin 0.8.7, Minecraft 26.3 (`net.minecraft.*`), `publish_update.ps1` + standalone `-ea` unit tests.

**Spec:** [2026-11-18-freecam-improvements-design.md](docs/superpowers/specs/2026-11-18-freecam-improvements-design.md)

## Global Constraints
- Vanilla Creative-Fly speed floor: `1.0x` config must produce steady `0.50` blocks/tick (`10.0` blocks/sec); `freecamSpeed` clamped to `[0.2f, 5.0f]`.
- Scroll wheel increments speed in `0.10f` steps when `freecamScrollSpeed` is enabled.
- Strict interaction lock: when `FreecamHandler.isActive() && !cfg.freecamAllowInteract`, `startAttack`, `continueAttack`, `startUseItem`, and `AutoBreak` must never mine, attack, or use items.
- Multiplayer reach safety: camera raycast hits when `freecamAllowInteract` is true must still satisfy `player.isWithinBlockInteractionRange(...)` / `player.isWithinEntityInteractionRange(...)`.
- Silent main character: main player client `yRot`/`xRot` and arm swing animation must never visibly play from Freecam interactions.

## Review Focus
- Holding left-click while entering or using Freecam with `Allow Interact = false` must immediately stop active block breaking (`gameMode.stopDestroyBlock()`) rather than continuing to mine the block in front of the player.
- Releasing movement keys in Freecam must smoothly decelerate (`damping`) instead of drifting forever or snapping abruptly, and disabling Freecam must zero out velocity so re-enabling starts cleanly.
- Aiming through the Freecam camera at an entity or block > player reach must return `MISS` instead of sending invalid out-of-reach packets to the server.
- Diagonal movement normalization must normalize horizontal `(forward, strafe)` vectors properly (`Math.hypot` > `1.0`) so diagonal flying does not gain `sqrt(2)` speed boost.
- Mouse sensitivity compatibility: turning in Freecam must remain smooth and bounded to `[-90, 90]` pitch regardless of camera velocity.

---

### Task 1: Config Flag (`freecamRotate`), Creative-Fly Physics & Raycast Helpers

**Files:**
- Modify: `src/main/java/com/client/mod/ModConfig.java:219-225,751-756,890`
- Modify: `src/main/java/com/client/mod/ModernModuleSettings.java:285-291`
- Modify: `src/main/java/com/client/mod/FreecamHandler.java:1-157`
- Test: `src/test/java/com/client/mod/FreecamPhysicsTest.java`

**Interfaces:**
- Consumes: `ModConfig.freecamSpeed`, `ModConfig.freecamScrollSpeed`, `ModConfig.freecamToggleOnDamage`, `ModConfig.freecamAllowInteract`, `ModConfig.freecamRotate`
- Produces:
  - `FreecamHandler.CREATIVE_FLY_BASE_SPEED = 0.50`
  - `FreecamHandler.SCROLL_STEP = 0.10f`
  - `FreecamHandler.stepVelocity(Vec3 currentVel, double forward, double strafe, double vertical, float yawDeg, float speedSetting, boolean sprint)` -> `Vec3`
  - `FreecamHandler.stepScrollSpeed(float currentSpeed, double verticalDelta)` -> `float`
  - `FreecamHandler.getViewVector()` -> `Vec3`
  - `FreecamHandler.computeSilentLookAngles(Vec3 eyePos, Vec3 targetPos)` -> `float[]{yaw, pitch}`
  - `FreecamHandler.raycastFromCamera(LocalPlayer player, Level level, float partialTick)` -> `HitResult`

- [ ] **Step 1: Write the failing test (`FreecamPhysicsTest.java`)**
  Test `ModConfig.freecamRotate` default (`true`), copy/sanitize clamping of `freecamSpeed` `[0.2f, 5.0f]`, `ModernModuleSettings.getControls("Freecam", ...)` returning 5 controls, `FreecamHandler.stepVelocity` converging toward `0.50` blocks/tick at `1.0x` speed (and smoothly decaying toward zero on release, normalizing diagonals), and `FreecamHandler.stepScrollSpeed` incrementing by `0.10f` within `[0.2f, 5.0f]`.
- [ ] **Step 2: Run test to verify it fails**
  Run `javac` + `java -ea com.client.mod.FreecamPhysicsTest` -> FAIL (`cannot find symbol: freecamRotate`, `stepVelocity`, `stepScrollSpeed`).
- [ ] **Step 3: Implement `ModConfig`, `ModernModuleSettings`, and `FreecamHandler` updates**
  - Add `public boolean freecamRotate = true;` + copy in `ModConfig.java`.
  - Add `toggle("Rotate", () -> cfg.freecamRotate, ...)` to `ModernModuleSettings.java` under `"Freecam"`.
  - Implement Creative-Fly velocity smoothing (`ACCEL = 0.35`, `DAMPING = 0.65`, `CREATIVE_FLY_BASE_SPEED = 0.50`, `SPRINT_MULT = 1.6`), `SCROLL_STEP = 0.10f`, `getViewVector()`, `computeSilentLookAngles(...)`, and `raycastFromCamera(...)` (checking `player.isWithinBlockInteractionRange` / `player.isWithinEntityInteractionRange` from the real player body!).
  - Reset velocity on `enable`, `disable`, and GUI-open ticks.
- [ ] **Step 4: Run `FreecamPhysicsTest` to verify pass**

### Task 2: Full Interaction Lock (`continueAttack` / `AutoBreak`) & Silent Camera Interact Mixins

**Files:**
- Modify: `src/main/java/com/client/mod/mixin/MinecraftMixin.java:83-106,208-225`
- Modify: `src/main/java/com/client/mod/mixin/LocalPlayerMixin.java:58-75`
- Modify: `src/main/java/com/client/mod/mixin/LivingEntityMixin.java`
- Modify: `src/main/java/com/client/mod/FreecamHandler.java` (add `sendSilentInteractRotationIfEnabled(LocalPlayer player, HitResult hitResult)`)

**Interfaces:**
- Consumes: `FreecamHandler.isActive()`, `FreecamHandler.raycastFromCamera(...)`, `FreecamHandler.sendSilentInteractRotationIfEnabled(...)`

- [ ] **Step 1: Inspect `LivingEntityMixin.java` to see existing injections**
- [ ] **Step 2: Implement interaction lock & silent interact mixins**
  - In `MinecraftMixin.java`:
    - Add `@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)` stopping `gameMode.stopDestroyBlock()` if active and cancelling when `FreecamHandler.isActive() && !ModConfig.getInstance().freecamAllowInteract`.
    - When `FreecamHandler.isActive() && ModConfig.getInstance().freecamAllowInteract`: right before `startAttack`, `continueAttack(true)`, or `startUseItem` executes with a non-MISS `this.hitResult`, call `FreecamHandler.sendSilentInteractRotationIfEnabled(this.player, this.hitResult)`.
    - Guard `AutoBreak` block check with `(!FreecamHandler.isActive() || cfg.freecamAllowInteract)`.
  - In `LocalPlayerMixin.java`:
    - At `@At("HEAD")` / `@At("RETURN")` of `raycastHitResult(float partialTick, Entity cameraEntity, CallbackInfoReturnable<HitResult> cir)`: when `self == mc.player && FreecamHandler.isActive()`, if `!cfg.freecamAllowInteract` return `BlockHitResult.miss(...)`? Wait: if `Allow Interact` is TRUE, return `FreecamHandler.raycastFromCamera(self, self.level(), partialTick)` (and if `noMiningTrace` applies on entities, fall back to camera block raycast!).
  - In `LivingEntityMixin.java`:
    - Inject at `@At("HEAD")` of `swing(InteractionHand, SwingAnimation, boolean)` cancellable: if `(Object) this == Minecraft.getInstance().player && FreecamHandler.isActive()` -> `cir.setReturnValue(false);` so client player arm never visibly swings!
- [ ] **Step 3: Build `v1.0.174` via `publish_update.ps1` and run `FreecamPhysicsTest` + existing test suite (`AutoSpawnerTest`, `ModernModuleGridLayoutTest`) to verify everything compiles and passes cleanly.**
