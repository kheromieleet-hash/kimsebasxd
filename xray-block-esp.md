# Xray & Block ESP (Meteor Parity) Implementation Plan

## Goal
Add Meteor Client's full **Xray** (opacity `0–255`, exposed-only filter, auto fullbright/AO bypass, Vanilla MC 26.3 + Sodium `0.9.2+mc26.3` compatibility) and **Block ESP** (chunk-cached block scanner, per-block `Lines`/`Sides`/`Both` colors & tracers, default fallback) modules under the **Render** category, complete with a dedicated **Block Picker** UI (`Ores`, `Storage`, `Nether` presets + per-block color/shape editor).

---

## Scope & Decisions (Confirmed)
- **Xray Pipeline:** Vanilla MC 26.3 chunk builder (`SectionCompiler`, `ModelBlockRenderer`, `Block.shouldRenderFace`, `BlockStateBaseMixin`, `LightCoordsUtilMixin`) **plus** conditional Sodium `0.9.2+mc26.3` mixin compatibility via a Mixin plugin so it runs safely with or without Sodium installed.
- **Block ESP Engine:** Spatial chunk-cache scanner (`BlockEspHandler`) with incremental scanning and packet-driven invalidation (`BlockUpdate`, chunk load/unload, world switch), rendering via `WorldRenderToolsHandler` (`Gizmos`).
- **UI & Presets:** Dedicated block-only `CatalogBlockPickerScreen` (`Ores`, `Storage`, `Nether` presets) with inline per-block override editor (preset color swatches + RGB sliders, `ShapeMode`, `Tracer`, or `Inherit Default`) for Block ESP.

---

## Affected Files
### New Files
- `src/main/java/com/client/mod/XrayHandler.java` — Block match, `isExposed` 6-neighbor check, `getOpacityAlpha()`, default ore preset list, chunk reload trigger.
- `src/main/java/com/client/mod/BlockEspHandler.java` — `BlockEspStyle` (`ShapeMode`, RGBA colors, `tracer`), chunk-keyed target cache, incremental scanner, packet dirty-mark hooks, Gizmo renderer.
- `src/main/java/com/client/mod/CatalogBlockPickerScreen.java` — Block-only searchable picker screen (`All`/`Selected` filter, `Ores`/`Storage`/`Nether` preset buttons, Block ESP per-block style popover/panel).
- `src/main/java/com/client/mod/SodiumContextAccess.java` — Public accessor interface (outside mixin package) exposing `AbstractBlockRenderContext#level`.
- `src/main/java/com/client/mod/mixin/SestiaMixinPlugin.java` — `IMixinConfigPlugin` enabling `sodium.*` mixins only when `FabricLoader.getInstance().isModLoaded("sodium")` is true.
- `src/main/java/com/client/mod/mixin/sodium/SodiumBlockRendererMixin.java` — Conditional Sodium `BlockRenderer` / `DefaultMaterials.TRANSLUCENT` + alpha & culling hook.
- `src/main/java/com/client/mod/mixin/sodium/SodiumFluidRendererMixin.java` — Conditional Sodium `DefaultFluidRenderer` Xray hide/opacity hook.
- `src/test/java/com/client/mod/XrayBlockEspLogicTest.java` — Unit tests (`-ea`) for Xray preset/exposure/opacity math, `BlockEspHandler` config resolution & chunk-cache dirty invalidation, and `ModConfig` serialization.

### Modified Files
- `src/main/java/com/client/mod/ModConfig.java` — Add `xrayEnabled`, `xrayOpacity` (`0–255`), `xrayExposedOnly`, `xrayBlocks`, `blockEspEnabled`, `blockEspBlocks`, `blockEspDefaultStyle`, `blockEspPerBlockStyles`, `blockEspRange`, tracer/shape defaults; copy in `copyFrom()` & Gson persistence.
- `src/main/java/com/client/mod/ClickGuiScreen.java` — Register `"Xray"` and `"Block ESP"` under `Category.RENDER` (triggers chunk reload for Xray, cache clear/scan for Block ESP).
- `src/main/java/com/client/mod/ModernModuleCardUi.java` — Add module descriptions for `"Xray"` and `"Block ESP"`.
- `src/main/java/com/client/mod/ModernModuleSettings.java` — Add settings controls for `"Xray"` (`Select Blocks...`, `Opacity`, `Exposed Only`) and `"Block ESP"` (`Select Blocks...`, `Range`, `Shape Mode`, `Default Line/Side Color`, `Tracers`).
- `src/main/java/com/client/mod/WorldRenderToolsHandler.java` — Call `BlockEspHandler.render(mc)` from `renderAll()`.
- `src/main/java/com/client/mod/mixin/BlockStateBaseMixin.java` — Force shade brightness `1.0f` / disable AO shadow during Xray.
- `src/main/java/com/client/mod/mixin/LightCoordsUtilMixin.java` — Force max lightmap (`15728880`) when `cfg.xrayEnabled` is true.
- `src/main/java/com/client/mod/mixin/ClientPlayPacketListenerMixin.java` — Invalidate `BlockEspHandler` dirty chunks on block update, multi-block update, chunk load/unload, and disconnect.
- `src/main/java/com/client/mod/mixin/SectionCompilerMixin.java` (new or added vanilla block mixin) + `BlockMixin.java` — Vanilla `Block.shouldRenderFace` + `SectionCompiler` translucent/alpha & cave-culling (`VisGraph`) bypass during Xray.
- `customclient.mixins.json` — Register `"plugin": "com.client.mod.mixin.SestiaMixinPlugin"` and new Vanilla + Sodium mixins.
- `publish_update.ps1` — Include `sodium-fabric` jar (if present locally in PrismLauncher/cache) in compile-only `-cp` so `mixin.sodium.*` compiles cleanly without bundling Sodium into `sestia-core.jar`.
- `MODULLER.txt` — Document `"Xray"` and `"Block ESP"` in Turkish under `RENDER KATEGORISI`.

---

## Task Breakdown

| # | Task | Depends On | Agent | Output / Verify |
|---|------|------------|-------|-----------------|
| 1 | **Config & Core Data Model:** Add `ModConfig` fields/defaults for `Xray` & `Block ESP`, `XrayHandler` pure helpers (default ores, filter, alpha, neighbor exposure check), and `BlockEspHandler` style/cache data structures. | — | `backend-specialist` / `self` | `XrayBlockEspLogicTest` fails RED before impl, compiles & passes on pure logic after Task 1. |
| 2 | **Block Picker UI & ClickGUI Registration:** Build `CatalogBlockPickerScreen` (`Ores`/`Storage`/`Nether` presets + Block ESP per-block `ShapeMode`/Color/Tracer panel), register `"Xray"` & `"Block ESP"` in `ClickGuiScreen`, `ModernModuleSettings`, and `ModernModuleCardUi`. | Task 1 | `frontend-specialist` | Modules visible in `Render` tab; `Select Blocks...` opens block-only catalog with working presets & persistence. |
| 3 | **Block ESP Scanner & Gizmo Renderer:** Implement chunk-cached incremental scanner + `ClientPlayPacketListenerMixin` block/chunk invalidation + `WorldRenderToolsHandler` `Gizmos` (`Lines`/`Sides`/`Both` + `Tracers` from camera eye vector). | Task 1 | `backend-specialist` / `self` | Unit test verifies dirty chunk invalidation & range culling; renders boxes/tracers in-world. |
| 4 | **Xray Vanilla Render Hooks:** Add `BlockMixin` (`shouldRenderFace`), `SectionCompilerMixin` / `VisGraph` bypass, and Xray light/shade hooks in `BlockStateBaseMixin` & `LightCoordsUtilMixin` (`Opacity` `0–255` + `Exposed Only`). | Task 1 | `backend-specialist` / `self` | Vanilla MC 26.3 renders whitelisted ores at full brightness; `Opacity` fades non-selected blocks; `Exposed Only` hides enclosed ores. |
| 5 | **Sodium 0.9.2+mc26.3 Conditional Xray Support:** Update `publish_update.ps1` compile classpath, add `SestiaMixinPlugin` (`isModLoaded("sodium")`), `SodiumBlockRendererMixin` (`DefaultMaterials.TRANSLUCENT` + alpha/cull), and `SodiumFluidRendererMixin`, wired in `customclient.mixins.json`. | Task 4 | `backend-specialist` / `self` | Launches cleanly both WITH Sodium `0.9.2+mc26.3` and WITHOUT Sodium; Xray `Opacity` + `Exposed Only` work under Sodium. |
| 6 | **Docs & Verification (Phase X):** Update `MODULLER.txt`, write & run `XrayBlockEspLogicTest` + existing test suite (`-ea`), and run local build (`publish_update.ps1` without `-Upload`). | Tasks 2, 3, 5 | `test-engineer` | Local build `exit 0`; all unit assertions pass; in-game PrismLauncher sanity check complete. |

---

## Dependency Flow
```
Task 1 (Config + Handlers)
   ├──▶ Task 2 (Block Picker UI + GUI Settings)
   ├──▶ Task 3 (Block ESP Scanner + Gizmos)
   └──▶ Task 4 (Xray Vanilla Mixins) ──▶ Task 5 (Sodium Mixin Plugin + Hooks)
                                                │
Tasks 2 + 3 + 5 ────────────────────────▶ Task 6 (Phase X: Docs + Build + Tests)
```

---

## Phase X: Verification Checklist
- [x] Pure logic & cache unit tests (`XrayBlockEspLogicTest`: presets, `exposedOnly`, `opacity` math, `BlockEspHandler` per-block override fallback, chunk invalidation) pass with `java -ea`.
- [x] Regression suites (`ModernModuleGridLayoutTest`, `FreecamPhysicsTest`, `AutoSpawnerTest`, `RenderToolPhysicsTest`) pass with zero new failures.
- [x] `publish_update.ps1` (local build, no `-Upload`) compiles all Java sources cleanly with zero errors and updates PrismLauncher `mods/sestia.jar`.
- [ ] Manual in-game verification (MC 26.3 + Sodium `0.9.2+mc26.3`):
  - [ ] `Xray`: toggle rebuilds chunks, `Opacity = 0` hides stone, `Opacity = 128` shows translucent stone, `Exposed Only` hides cave-enclosed ores, ores stay lit even with `Fullbright` OFF.
  - [ ] `Block ESP`: custom block selection via `Select Blocks...`, per-block color/shape (`Lines`/`Sides`/`Both`) & `Tracers`, placing/breaking a watched block updates ESP immediately without lag.
- [x] `MODULLER.txt` updated in Turkish with `Xray` and `Block ESP`.
