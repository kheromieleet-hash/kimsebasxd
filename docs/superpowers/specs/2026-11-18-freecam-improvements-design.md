# Freecam Improvements Design (v1.0.174)

## Intent & Why
Make Sestia's `Freecam` behave like Meteor Client's Freecam while fitting Minecraft 26.3 cleanly:
1. **Natural Creative-Fly starting speed & smooth scaling:** Currently `1.0` speed moves `1.0` block/tick (`20` blocks/sec, `36` blocks/sec with sprint), which feels overly twitchy and fast. Users want a default starting speed matching vanilla Creative Fly (`~10` blocks/sec horizontal cruise with smooth acceleration/damping) that can be gradually increased via Settings and scroll wheel.
2. **Strict interaction lock when `Allow Interact` is OFF:** Currently `MinecraftMixin` cancels `startAttack` and `startUseItem`, but leaves `continueAttack` (hold-left-click mining) and `AutoBreak` active, letting the player accidentally mine blocks while in Freecam.
3. **Camera-aimed silent interaction when `Allow Interact` is ON:** Allow targeting blocks/entities from the Freecam camera's crosshair (within the real player's server reach, e.g., around corners or behind obstacles where the player body isn't facing) and interacting/breaking via silent server packets **without** rotating the main character's visible head/body or showing arm swing on the main character.

## Goals & Non-Goals
- **Goals:**
  - Creative-fly baseline speed (`0.50` blocks/tick base at `1.0x`, sprint multiplier `1.6x`, smooth inertia/damping) with fine `0.10x` scroll steps and `0.2x–5.0x` settings slider.
  - Complete attack/break suppression (`startAttack`, `continueAttack`, `startUseItem`, `AutoBreak`) when `Allow Interact` is OFF.
  - Camera-originated block/entity raycast (clamped to real player reach from `player.getEyePosition`) + optional silent `Rotate` packet + visually suppressed client arm swing (`main karakterde gözükmesin`) when `Allow Interact` is ON.
- **Non-Goals / Constraints:**
  - Server-side reach limits (`player.blockInteractionRange()` / `entityInteractionRange()`) cannot be bypassed on multiplayer servers; camera targeting only interacts with targets physically within player reach.
  - No fake `CameraEntity` swap (`mc.setCameraEntity`), avoiding MC 26.3 chunk culling / OpenAL audio / mixin regressions.

## Approaches Considered
1. **Approach 1 (Selected): Virtual Camera Fly Physics + `LocalPlayerMixin.raycastHitResult` + Silent Server Packets**
   - Keeps the existing lightweight virtual camera (`FreecamHandler` + `CameraMixin`), adds Creative-Fly velocity interpolation, closes `continueAttack`/`AutoBreak` leaks in `MinecraftMixin`, injects camera-based hit selection in `LocalPlayerMixin` (reach-checked against the real player), and sends silent `ServerboundMovePlayerPacket.Rot` + silent `ServerboundSwingPacket` without mutating client player rotation/swing state.
   - *Trade-off:* Clean, minimal surface area, zero side effects on chunk/audio/ESP systems.
2. **Approach 2 (Rejected): Spawn Client-Only `CameraEntity` + `mc.setCameraEntity`**
   - Mimics older Meteor versions by swapping `cameraEntity`.
   - *Trade-off:* Higher risk of breaking MC 26.3 RenderPearl culling, sound positioning, and existing `player == cameraEntity` mixins for little benefit.

## Component Design & Settings
- **`ModConfig` & `ModernModuleSettings`:**
  - Keep `freecamSpeed` (default `1.0f`, `0.2f–5.0f`), `freecamScrollSpeed` (`true`), `freecamToggleOnDamage` (`true`), `freecamAllowInteract` (`false`).
  - Add `freecamRotate` (default `true`): sends silent rotation packets toward the Freecam crosshair target when `Allow Interact` triggers a break/use action.
- **`FreecamHandler`:**
  - Replace direct per-tick teleport step with smoothed horizontal/vertical velocity (`CREATIVE_FLY_BASE = 0.50`, smooth acceleration + exponential damping `~0.68` on release, sprint `1.6x`) and `0.10f` scroll increment.
  - Track `prevPos`/`pos` interpolation cleanly for high-FPS smoothness.
  - Provide helper `getViewVector()` and `raycastFromCamera(player, level, partialTick, hitFluid)`.
- **`MinecraftMixin`:**
  - `@Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)`: when `FreecamHandler.isActive() && !cfg.freecamAllowInteract`, stop active block breaking and cancel.
  - Guard `AutoBreak` in `onTickHead` when `FreecamHandler.isActive() && !cfg.freecamAllowInteract`.
- **`LocalPlayerMixin` & Silent Interaction:**
  - In `raycastHitResult`: when `FreecamHandler.isActive() && cfg.freecamAllowInteract`, compute block/entity hit from Freecam camera pose, verifying distance from `player.getEyePosition(partialTick)` <= player reach; otherwise return `MISS`.
  - On interact/attack when `FreecamHandler.isActive() && cfg.freecamAllowInteract`: if `cfg.freecamRotate`, emit silent `ServerboundMovePlayerPacket.Rot` before action without altering `player.getYRot()/getXRot()`. Suppress visible client arm swing on `LocalPlayer` (`LivingEntityMixin` / `ServerboundSwingPacket`) so the main character stands still visually.

## Verification Plan
- Unit tests (`FreecamPhysicsTest`): verify Creative-Fly baseline cruise speed (`~0.50` blocks/tick at `1.0x`), acceleration/damping decay, fine scroll clamping (`0.2f–5.0f`, `0.10f` step), reach validation from player eye vs camera origin, and interaction lock rules.
- Full build via `publish_update.ps1` + existing test suite (`AutoSpawnerTest`, `ModernModuleGridLayoutTest`).
