# Plan: macOS Exit Code 9 (SIGKILL) Crash Fix

## 1. Problem Overview
- **Symptom:** A friend launching Sestia Client on an **Apple Silicon M4 MacBook** receives `Exit code: 9` (`SIGKILL`) **after joining a multiplayer server** (title screen & launch succeed).
- **Context:** On macOS, `Exit code: 9` means the OS kernel (`launchd` / AMFI / Jetsam) or launcher forcibly killed the JVM process with `SIGKILL` (as opposed to a normal Java exception / Exit code 1). Because it triggers *after* joining a server, the trigger is either:
  1. **M4 Apple Silicon ARM64 vs x86_64 (Rosetta) Java / LWJGL mismatch or RAM (`-Xmx`) spike** during chunk/resource-pack loading on server join, OR
  2. **First-time in-game HUD / network / OS-specific calls** (`SmoothUiAtlas` GPU texture upload, `SestiaChatClient.onEnteredWorld()`, or Windows-only `ProcessBuilder("powershell.exe", ...)` calls).

## 2. Potential Root Causes to Investigate
1. **macOS Gatekeeper / Code Signing (`AMFI` / `com.apple.quarantine`) or File Locking:**
   - `SctUpdater` downloads `sestia-core.jar` into `<gameDir>/sestia/` at runtime before `FabricLauncherBase.getLauncher().addToClassPath(coreJar)`.
   - Also check `SctBootstrapAdapter` / `SctUpdater` fallback behavior and permissions on macOS.
2. **macOS Main Thread (`-XstartOnFirstThread`) & AWT / GLFW Conflicts:**
   - `SctBootstrapAdapter.createListenerSafely()` already returns `SctUpdateListener.NO_OP` when `os.name` contains `mac`, **but** verify no static initializer or transitive class load touches `java.awt.*`, `javax.swing.*`, or `GLFW` off the main Cocoa thread.
   - Check `AutoFishHandler` and `ChatMentionNotifier` (`ProcessBuilder("powershell.exe", ...)` — harmless on Windows, should guard with `os.contains("win")` so it never throws/spawns invalid processes on macOS/Linux).
   - Check `ConfigProfileManager.openProfilesFolder` (uses `Desktop.getDesktop()` or OS commands? Must be audited for macOS safety).
3. **Apple Silicon (ARM64) vs x86_64 Java / LWJGL / Memory (OOM / Jetsam):**
   - Running x86_64 Java under Rosetta 2 or mismatched `-Xmx` / native libraries on Apple Silicon can trigger `SIGKILL` (`Exit code: 9`).
   - `SmoothUiAtlas` / `DynamicTexture` / `NativeImage` GPU texture allocation on macOS OpenGL/Metal compatibility layer.

## 3. Clarifying Status (Answered)
- **Timing:** Happens **after joining a server** (game launches and reaches title screen fine).
- **Hardware:** **Apple Silicon M4** MacBook.
- **Remaining diagnostic (optional):** `latest.log` / launcher console log + Java architecture (`aarch64` vs `x86_64`) and `-Xmx` setting.

## 4. Task Breakdown

| Task ID | Task | Target Files | Agent | Verification |
|---------|------|--------------|-------|--------------|
| T1 | Audit & guard OS-specific calls (`powershell.exe`, `Desktop`, `GLFW.glfwRequestWindowAttention`) for macOS/Linux safety | `AutoFishHandler.java`, `ChatMentionNotifier.java`, `ConfigProfileManager.java` | `backend-specialist` | Compile & unit test; verify Windows-only `ProcessBuilder` never runs on macOS |
| T2 | Audit `SctBootstrapAdapter` & `SctUpdater` macOS classloading, path resolution, and JAR caching | `SctBootstrapAdapter.java`, `SctUpdater.java` | `backend-specialist` | Verify zero AWT/Swing classloading on macOS and clean diagnostic logging |
| T3 | Audit `SmoothUiAtlas` / `JelloTheme` / `ClientMod` OpenGL & GLFW calls on macOS Cocoa main thread | `SmoothUiAtlas.java`, `ClientMod.java` | `frontend-specialist` | Run `SmoothUiAtlasTest` & build JAR |
| T4 | Document macOS launcher setup (ARM64 Java + `-XstartOnFirstThread` / PrismLauncher notes) if environment-related | `README` / user instructions | `project-planner` | User confirmation from MacBook friend |

## 5. Verification Checklist (Phase X)
- [x] Zero `java.awt` / `javax.swing` classes loaded on `os.name` containing `mac` (`SctBootstrapAdapter`)
- [x] All `powershell.exe` / Windows-specific calls strictly guarded by OS check (`DesktopAlertHelper` with macOS `osascript` support)
- [x] Full compilation succeeds (`publish_update.ps1` / `javac`)
- [ ] Friend on M4 MacBook verifies ARM64 Java 25+ & 4 GB RAM (`-Xmx4096M`) and tests server join
