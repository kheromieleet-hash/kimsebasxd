---
name: sestia-mc26-verify
description: Verify Minecraft 26.3 Fabric & Sodium Mixin bytecode targets with javap, compile and run standalone Java 25 unit tests, and build/publish Sestia Client via publish_update.ps1. Use whenever editing Mixins, adding Java features, debugging Prism Launcher crashes, or releasing updates.
when_to_use: "After adding or editing Java sources or Mixin configs in Sestia Client, when debugging Fabric/Mixin startup crashes, or before running publish_update.ps1 -Upload."
allowed-tools: Read, Grep, Glob, Bash
version: 1.0.0
---

# Sestia Client MC 26.3 Verification & Release Workflow

> This repository has no `build.gradle`. Compilation and packaging run through `publish_update.ps1` using Prism Launcher's Java 25 toolchain.

## 1. Verify Mixin Targets Against Real Bytecode (Before Packaging)

`javac` succeeds even if a Mixin `@At(target = ...)` descriptor is wrong (for example, `Lnet/minecraft/network/Packet;` instead of `Lnet/minecraft/network/protocol/Packet;`), which crashes Minecraft at launch.

```powershell
$javap = "C:\Users\PC\AppData\Roaming\PrismLauncher\java\java-runtime-epsilon\bin\javap.exe"
$mcJar = "C:\Users\PC\.gradle\caches\fabric-loom\26.3\minecraft-merged.jar"
$sodiumJar = Get-ChildItem "C:\Users\PC\AppData\Roaming\PrismLauncher\instances\26.3\minecraft\mods" -Filter "sodium-fabric*.jar" | Select-Object -First 1 -ExpandProperty FullName

# Inspect target class members & invoke descriptors (-c shows exact method calls inside methods)
& $javap -cp $mcJar -p -c net.minecraft.client.multiplayer.ClientPacketListener | Select-String "ensureRunningOnSameThread"
& $javap -cp $sodiumJar -p <sodium.class.Name>
```

After packaging `dist\sestia-core.jar`, confirm compiled Mixin annotations match:

```powershell
& $javap -cp dist\sestia-core.jar -v -p com.client.mod.mixin.<MixinClass>
```

## 2. Package Core + Loader Jars

```powershell
# Local build + PrismLauncher mods/sestia.jar sync (no GitHub upload):
.\publish_update.ps1 -Version "1.0.X" -VersionCode X -Changelog "..."

# Publish to GitHub Releases (`latest` manifest + assets):
.\publish_update.ps1 -Version "1.0.X" -VersionCode X -Changelog "..." -Upload
```

- Always state clearly whether `-Upload` was run; local-only builds update `dist/` and local PrismLauncher `mods/sestia.jar` without updating remote `SctUpdater` clients.

## 3. Compile & Run All Standalone Unit Tests (`src/test/java`)

After building `dist\sestia-core.jar` and `dist\sestia.jar`, compile every test under `src\test\java` against the same `26.3` classpath (`fabric-loom\26.3\minecraft-merged.jar`, Fabric Loader `0.20.1`, Fabric API `*26.3*.jar` / cached `fapi`, `modules-2`, Sodium) into a temporary directory, then run every `*Test` class (`com.client.loader.SctUpdaterTest` and `com.client.mod.*Test`) with `java.exe -ea` and confirm `FAILED=0`.
