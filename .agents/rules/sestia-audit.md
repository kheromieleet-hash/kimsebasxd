---
name: sestia-audit
version: 1.0.0
priority: P0
trigger: model_decision
description: Apply whenever a new module, UI/HUD screen, Mixin, external mod JAR, crash log, or feature is uploaded, added, or modified in Sestia Client (MC 26.3 Fabric). Enforces the 4-category audit report, Mixin bytecode verification, and MODULLER.txt sync.
---

# Sestia Client Audit & Quality Guardrails

> Project-specific guardrail for Sestia Client (Java 25 / Fabric 26.3 / Sodium).

## 1. Mandatory 4-Category Audit on Additions or Uploads

Whenever the user uploads an external mod/log or asks to inspect or add new code, modules, screens, HUDs, or Mixins, audit the target/changes and report findings across these 4 categories (with file path and line numbers):

1. **Kritik Bug & Çökme (NPE / Mixin / Thread / Bellek) Riskleri**
   - Null/thread safety (`Minecraft.execute` vs Netty packet thread, GL texture / `NativeImage` close & bounds checks, cross-OS guards for macOS/Linux/Windows).
   - Every new or edited Mixin `@Inject`, `@Redirect`, `@At(target = ...)`, `@Shadow`, or `@Accessor` signature MUST be verified against compiled `MC 26.3` / Sodium bytecode (`javap`) — never trust `javac` alone for Mixin descriptor strings.
2. **Performans & FPS Düşürebilecek Yerler**
   - Avoid per-frame / render-loop / chunk-step allocations (`new ArrayList`, `String.format`, lambda/stream boxing, repeated reflection lookups or texture uploads).
3. **UI / HUD / ClickGUI Tutarsızlıkları & Kullanılabilirlik Sorunları**
   - Align with `ModernClickGuiScreen`, `HudLayoutEditorScreen`, `HudLayoutPresets`, and project color/spacing tokens; check hitboxes, scroll clipping, focus traps, narrow-window overflow, and HUD overlaps.
4. **Ölü Kod & Gereksiz Tekrarlar (Temizlik)**
   - Flag unused helpers, stale mixins/assets, duplicated layout math, or leftover debug code.

## 2. Documentation & Release Discipline

- Keep `MODULLER.txt` at the repo root updated in Turkish whenever any module, setting, or feature is added, removed, or changed.
- For build, `javap` bytecode verification, standalone `java -ea` unit tests, and release uploads (`publish_update.ps1`), load `@[skills/sestia-mc26-verify]`.
