# Tek-JAR Bootloader & GitHub Releases Auto-Updater Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a single-JAR Fabric `LanguageAdapter` bootloader (`SCTClient.jar`) that checks `kheromieallah/sestia` GitHub Releases on launch, downloads & SHA-256 verifies updated `SCTClient-Core.jar` builds into `.minecraft/sctclient/`, mounts them live with zero restart, shows an English Rise 6 Swing splash + post-launch in-game notification toast/chat message, and provides a one-command developer `publish_update.ps1` release script.

**Architecture:** `SCTClient.jar` (placed once in `mods/`) contains only `com.client.loader.*`, nested `IAS-9.0.8+26.3-fabric.jar`, and embedded `assets/sctclient/fallback-core.jar`. Inside `FabricLoaderImpl.setupLanguageAdapters()`—before `FabricMixinBootstrap.init()` runs—`SctBootstrapAdapter` invokes `SctUpdater` to ensure the newest verified `SCTClient-Core-<version>.jar` is cached under `.minecraft/sctclient/core/` and injects it into `KnotClassLoader` via `FabricLauncherBase.getLauncher().addToClassPath(...)`, while `CoreAssetsPackMixin` dynamically mounts its `assets/` inside Minecraft's `PackRepository`.

**Tech Stack:** Java 25 (`C:\Users\PC\AppData\Roaming\PrismLauncher\java\java-runtime-epsilon\bin\javac.exe`), Fabric Loader `>=0.19.3`, Minecraft `26.3`, `fabric-resource-loader-v1`, `java.net.http.HttpClient`, Gson, Java2D/Swing (`JDialog`), PowerShell (`publish_update.ps1`).

**Spec:** `docs/superpowers/specs/2026-11-10-auto-updater-design.md`

## Global Constraints
- Minecraft `~26.3`, Fabric Loader `>=0.19.3`, Java `>=25`.
- `SCTClient.jar` (`mods/` file) MUST NOT contain `com/client/mod/**`, `dlovin/**`, or raw `customclient.mixins.json` / `inventoryhud.mixins.json` at root (only `com/client/loader/**`, `fabric.mod.json`, `META-INF/jars/IAS-9.0.8+26.3-fabric.jar`, `assets/sctclient/fallback-core.jar`, `logo.png`, `pack.mcmeta`).
- `SCTClient-Core.jar` MUST contain `com/client/mod/**`, `dlovin/**`, `customclient.mixins.json`, `inventoryhud.mixins.json`, `META-INF/services/dlovin.*`, `sct-core-meta.properties`, `pack.mcmeta`, and `assets/**`.
- All UI copy in `SctUpdateWindow`, `UpdateNoticeManager`, and chat messages MUST be in English.
- Default manifest URL: `https://github.com/kheromieallah/sestia/releases/latest/download/version.json`, default releases page: `https://github.com/kheromieallah/sestia/releases/latest`, overridable via `-Dsct.manifestUrl` or `.minecraft/sctclient/updater.properties`.
- No secrets or GitHub tokens embedded in Java code, `.jar` files, or committed scripts.
- Code comments and identifiers in English; clean single-purpose helpers with no duplicated logic.

## Review Focus
- Path traversal or unsafe characters in remote `version` strings (`../../evil.jar`) must be rejected/sanitized inside `core/`.
- Corrupt download or SHA-256 mismatch must delete `.part`, preserve active cached JAR, and launch safely without crashing.
- First launch offline (`core/` empty, HTTP timeout/404) must extract embedded `fallback-core.jar` and boot safely.
- Remote `minLoaderCode > LOADER_CODE` must NOT download incompatible core, must boot existing cached core, and must write `"LOADER_OUTDATED"` to `pending-notice.json` (non-blocking warning).
- Headless execution (`GraphicsEnvironment.isHeadless() == true`) and leftover `.part` temp files must be handled cleanly without throwing AWT exceptions.

---

### Task 1: Bootloader Config & Updater Engine (`com.client.loader`)

**Files:**
- Create: `src/main/java/com/client/loader/SctUpdateConfig.java`
- Create: `src/main/java/com/client/loader/SctUpdateListener.java`
- Create: `src/main/java/com/client/loader/SctUpdater.java`
- Test: `src/test/java/com/client/loader/SctUpdaterTest.java`

**Interfaces:**
- Produces:
  - `SctUpdateConfig`: `public static final int LOADER_CODE = 1;`, `public static String resolveManifestUrl(Path workDir)`, `public static String resolveReleasesPageUrl(Path workDir)`
  - `SctUpdateListener`: `onChecking()`, `onDownloading(String fromVer, String toVer, String changelog, long bytesRead, long totalBytes, double speedMbPerSec)`, `onReady(String version, boolean updated)`, `onOffline(String cachedVersion, String reason)`, `onLoaderOutdated(String latestVersion, String releaseUrl)`, `close()`
  - `SctUpdater`:
    - `public SctUpdater(Path workDir, String manifestUrl, String releasesPageUrl, FallbackSupplier fallbackSupplier, SctUpdateListener listener)`
    - `public Path resolveAndEnsureCore()` (never throws on network/corrupt update when cache/fallback exists)
    - `public static String computeSha256(Path file) throws IOException`
    - `public static String sanitizeVersionFileSegment(String version)`

- [ ] **Step 1: Write failing test suite `src/test/java/com/client/loader/SctUpdaterTest.java`**

Cover with `com.sun.net.httpserver.HttpServer` + `assert`:
1. `testFirstRunOfflineExtractsFallback()`: unreachable port `127.0.0.1:1`, empty `workDir` -> extracts fallback bytes, creates `state.json`, returns existing core path.
2. `testHttpRedirectDownloadAndSha256AndNotice()`: `/version.json` returns `versionCode=2, version="1.0.2", minLoaderCode=1, sha256=...`; `/download` returns `302 Location: /cdn/core.jar` serving real bytes -> downloads to `core/SCTClient-Core-1.0.2.jar`, writes `state.json`, writes `pending-notice.json` with `type="UPDATED"`, cleans older `SCTClient-Core-1.0.0.jar`, verifies `listener` saw `onChecking`, `onDownloading`, `onReady("1.0.2", true)`.
3. `testCacheHitSkipsDownload()`: calling again with same `versionCode=2` & matching `sha256` hits `0` downloads on `/cdn/core.jar` and leaves `pending-notice.json` untouched.
4. `testCorruptSha256PreservesCache()`: server sends tampered bytes while manifest announces valid sha256 -> deletes `.part`, keeps old `1.0.2` active, writes no broken state.
5. `testMinLoaderCodeTooHighWritesOutdatedNotice()`: server returns `minLoaderCode=99` -> does not hit download endpoint, boots `1.0.2`, writes `pending-notice.json` with `type="LOADER_OUTDATED"`.
6. `testMaliciousVersionStringSanitized()`: server sends `version="../evil/../../bad"`, verifier sanitizes filename strictly inside `core/` directory without escaping `workDir.resolve("core")`.

- [ ] **Step 2: Run test compile to verify it fails cleanly**

Run epsilon `javac` on test -> Expected: FAIL (`cannot find symbol class SctUpdater`).

- [ ] **Step 3: Implement `SctUpdateConfig.java`, `SctUpdateListener.java`, and `SctUpdater.java`**

- `SctUpdateConfig`: reads `-Dsct.manifestUrl`, else `workDir/updater.properties`, else `"https://github.com/kheromieallah/sestia/releases/latest/download/version.json"`.
- `SctUpdater`: uses `java.net.http.HttpClient` (`Redirect.NORMAL`, 2500ms connect, 4000ms manifest timeout, 30s jar read timeout), `Gson` for JSON state/manifest, `MessageDigest("SHA-256")`, cleans stale `*.part` files on start, deletes obsolete `SCTClient-Core-*.jar` files after verified switch.
- All user-visible listener messages/constants cleanly wired; strictly validates `coreDir.resolve(...).normalize().startsWith(coreDir)`.

- [ ] **Step 4: Compile and run `SctUpdaterTest` using epsilon `java.exe -ea`**

Run: compile loader + test classes with `cp.txt` dependencies and execute `java -ea -cp ... com.client.loader.SctUpdaterTest`.
Expected: All 6 test methods PASS (exit code 0).

- [ ] **Step 5: Commit Task 1**

```bash
git add src/main/java/com/client/loader/SctUpdateConfig.java src/main/java/com/client/loader/SctUpdateListener.java src/main/java/com/client/loader/SctUpdater.java src/test/java/com/client/loader/SctUpdaterTest.java
git commit -m "feat(updater): implement SctUpdater bootloader engine with SHA-256 and offline fallback"
```

---

### Task 2: Rise 6 Luxury Swing Window (`SctUpdateWindow`) & Fabric `SctBootstrapAdapter`

**Files:**
- Create: `src/main/java/com/client/loader/SctUpdateWindow.java`
- Create: `src/main/java/com/client/loader/SctBootstrapAdapter.java`
- Modify: `fabric.mod.json`

**Interfaces:**
- Consumes: `SctUpdateConfig`, `SctUpdateListener`, `SctUpdater`
- Produces:
  - `SctUpdateWindow implements SctUpdateListener`: headless-safe Swing `JDialog` (undecorated, `RoundRectangle2D` transparent rounded shape, `#0B0D19` dark indigo card, top `#C084FC -> #F472B6` gradient accent bar, `Segoe UI` typography, rounded animated pill progress bar + telemetry, delayed show on checking so <350ms fast checks show zero flicker, clean `SwingUtilities.invokeLater(dialog::dispose)`)
  - `SctBootstrapAdapter implements net.fabricmc.loader.api.LanguageAdapter`: initializes updater once in static/constructor phase, adds resolved `coreJar` to `FabricLauncherBase.getLauncher().addToClassPath(coreJar)`, sets `System.setProperty("sct.core.jar.path", coreJar.toAbsolutePath().toString())`, implements `create(...)` matching `DefaultLanguageAdapter`.
  - `fabric.mod.json`: registers `"languageAdapters": { "sct_bootstrap": "com.client.loader.SctBootstrapAdapter" }` while preserving mod id `"inventoryhud"`, all `"entrypoints"`, `"mixins"`, `"jars"`, and `"depends"`.

- [ ] **Step 1: Implement `SctUpdateWindow.java`**

Build crisp Java2D custom-painted `JDialog`:
- English copy matching approved mockup: `"SCT CLIENT"`, `"AUTO UPDATER"`, `"Fabric 26.3 • Bootloader"`, `"Checking for updates..."`, `"Downloading update..."`, `"Update verified, launching Minecraft!"`, `"Update server unreachable — using cache"`, `"New SCTClient.jar loader required"`, `"SHA-256 verified"`.
- Headless-safe (`GraphicsEnvironment.isHeadless()` returns safely as no-op); checking timer waits 350ms before making dialog visible, while `onDownloading` opens immediately.

- [ ] **Step 2: Implement `SctBootstrapAdapter.java` & update `fabric.mod.json`**

- Use lock object so initialization runs strictly once even if called multiple times.
- Resolve `gameDir` via `FabricLoaderImpl.INSTANCE.getGameDir()` (fallback `Path.of(".")`), run `SctUpdater` extracting `assets/sctclient/fallback-core.jar` from `SctBootstrapAdapter.class.getResourceAsStream("/assets/sctclient/fallback-core.jar")`.
- Call `FabricLauncherBase.getLauncher().addToClassPath(coreJar)`.

- [ ] **Step 3: Compile full bootloader classes + smoke test `SctUpdateWindow` in headless/UI instantiation**

Verify `javac` succeeds cleanly across all `com/client/loader/*.java` and validate JSON syntax of `fabric.mod.json`.

- [ ] **Step 4: Commit Task 2**

```bash
git add src/main/java/com/client/loader/SctUpdateWindow.java src/main/java/com/client/loader/SctBootstrapAdapter.java fabric.mod.json
git commit -m "feat(loader): add Rise 6 Swing splash window and Fabric LanguageAdapter bootstrap"
```

---

### Task 3: Core Version Info, Dynamic Resource Pack & In-Game Update Notification (`com.client.mod`)

**Files:**
- Create: `src/main/java/com/client/mod/ClientBuildInfo.java`
- Create: `src/main/java/com/client/mod/UpdateNoticeManager.java`
- Create: `src/main/java/com/client/mod/mixin/CoreAssetsPackMixin.java`
- Modify: `customclient.mixins.json`
- Modify: `src/main/java/com/client/mod/mixin/ScreenMixin.java`
- Modify: `src/main/java/com/client/mod/mixin/HudOverlayMixin.java`
- Modify: `src/main/java/com/client/mod/ClientMod.java`
- Modify: `src/main/java/com/client/mod/ModernClickGuiScreen.java:256`
- Modify: `src/main/java/com/client/mod/StatsHudRenderer.java:94-103`
- Test: `src/test/java/com/client/mod/UpdateNoticeManagerTest.java`

**Interfaces:**
- Produces:
  - `ClientBuildInfo`: `getVersion()` (e.g., `"1.0.0"`), `getDisplayVersion()` (e.g., `"v1.0.0"`), `getVersionCode()`
  - `UpdateNoticeManager`:
    - `public static Notice consumePendingNotice(Path noticeFile)`
    - `public static void loadOnStartup()`
    - `public static void renderToast(GuiGraphicsExtractor graphics)`
    - `public static void onClientTick(Minecraft client)` (emits one-time English chat notice when `client.player != null && client.level != null`)

- [ ] **Step 1: Write failing test `src/test/java/com/client/mod/UpdateNoticeManagerTest.java`**

Test:
1. `consumePendingNotice` parses `"UPDATED"` JSON, returns populated `Notice`, and deletes `pending-notice.json` so subsequent calls return `null`.
2. `consumePendingNotice` parses `"LOADER_OUTDATED"` JSON with `releaseUrl`.
3. `ClientBuildInfo.parseProperties(...)` parses version metadata cleanly and falls back gracefully when absent.

- [ ] **Step 2: Run test compile/run to verify failure before implementation**

- [ ] **Step 3: Implement `ClientBuildInfo.java`, `UpdateNoticeManager.java`, and `CoreAssetsPackMixin.java`**

- `CoreAssetsPackMixin`: `@Mixin(value = ModResourcePackCreator.class, remap = false)` (check remap=false / signature on `loadPacks(Consumer<Pack>)`) — reads `System.getProperty("sct.core.jar.path")`, creates `Pack.readMetaAndCreate(new PackLocationInfo("sctclient-core", Component.literal("SCT Client Core"), PackSource.BUILT_IN, Optional.empty()), new FilePackResources.FileResourcesSupplier(corePath), PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, true))`.
- `UpdateNoticeManager.renderToast`: renders animated Rise 6 dark indigo floating card (`#0F1222`, violet glow border, rounded accent bar) for 7 seconds across Screen & HUD showing `"✦ SCT Client Updated to vX.X.X"` + changelog or `"⚠ New SCTClient.jar Loader Required"`!

- [ ] **Step 4: Wire `UpdateNoticeManager` and `ClientBuildInfo` into `ClientMod`, `ScreenMixin`, `HudOverlayMixin`, `ModernClickGuiScreen`, `StatsHudRenderer`, `customclient.mixins.json`**

- [ ] **Step 5: Compile all sources (`build_test.ps1`) and run `UpdateNoticeManagerTest` + `SctUpdaterTest`**

Expected: `Javac exit code: 0` and all tests PASS.

- [ ] **Step 6: Commit Task 3**

```bash
git add src/main/java/com/client/mod/ClientBuildInfo.java src/main/java/com/client/mod/UpdateNoticeManager.java src/main/java/com/client/mod/mixin/CoreAssetsPackMixin.java customclient.mixins.json src/main/java/com/client/mod/mixin/ScreenMixin.java src/main/java/com/client/mod/mixin/HudOverlayMixin.java src/main/java/com/client/mod/ClientMod.java src/main/java/com/client/mod/ModernClickGuiScreen.java src/main/java/com/client/mod/StatsHudRenderer.java src/test/java/com/client/mod/UpdateNoticeManagerTest.java
git commit -m "feat(core): add dynamic CoreAssetsPackMixin, build version info, and in-game update toast"
```

---

### Task 4: Split-JAR Packaging & GitHub Releases Publisher (`publish_update.ps1`) + End-to-End Test

**Files:**
- Create: `publish_update.ps1`
- Test: local end-to-end simulation script verifying real produced JARs

- [ ] **Step 1: Write `publish_update.ps1`**

Parameters:
- `[string]$Version = "1.0.0"`
- `[int]$VersionCode = 1`
- `[string]$Changelog = "Initial release"`
- `[switch]$Upload`
- `[string]$Repo = "kheromieallah/sestia"`

Behavior:
1. Clean staging dirs `build_tmp/classes`, `build_tmp/core`, `build_tmp/loader`, `dist/`.
2. Compile all `src/main/java/**/*.java` into `build_tmp/classes`.
3. Copy base resources & assets (from previous `ClientMod-1.0.0.jar` or workspace scratch + repo assets, cleaning obsolete files just like `clean_rebuild_jar.ps1`) into `build_tmp/core`:
   - Copy compiled `com/client/mod/**` and `dlovin/**` classes (NEVER `com/client/loader/**`).
   - Copy `customclient.mixins.json`, `inventoryhud.mixins.json`, `META-INF/services/dlovin.*`, `pack.mcmeta`, `assets/`.
   - Generate `sct-core-meta.properties` (`version=$Version`, `versionCode=$VersionCode`).
   - Package `dist/SCTClient-Core.jar` using `jar cf`.
4. Compute SHA-256 of `dist/SCTClient-Core.jar` and `Length`, write `dist/version.json` with `downloadUrl = "https://github.com/$Repo/releases/latest/download/SCTClient-Core.jar"`, `minLoaderCode = 1`.
5. Populate `build_tmp/loader`:
   - Copy compiled `com/client/loader/**` classes ONLY (zero `com/client/mod` or `dlovin` classes!).
   - Copy `fabric.mod.json`, `META-INF/jars/IAS-9.0.8+26.3-fabric.jar`, `logo.png`, `pack.mcmeta`.
   - Embed `dist/SCTClient-Core.jar` as `build_tmp/loader/assets/sctclient/fallback-core.jar`.
   - Package `dist/SCTClient.jar` using `jar cf`.
6. Deploy `dist/SCTClient.jar` to Desktop, Downloads, and PrismLauncher `instances/26.3/minecraft/mods/` (and remove legacy `ClientMod-1.0.0.jar` from `mods/` if desired or keep clean single `SCTClient.jar`—check carefully so mod ID `"inventoryhud"` doesn't duplicate!).
   - IMPORTANT: Delete any existing `ClientMod-1.0.0.jar` from `PrismLauncher\instances\26.3\minecraft\mods\` when installing `SCTClient.jar`, because both declare mod id `"inventoryhud"` and Fabric refuses duplicate mod IDs in `mods/`!
7. If `-Upload`: check `gh` CLI (if authenticated) or `$env:GITHUB_TOKEN` to create release `v$Version` on `kheromieallah/sestia` with `SCTClient-Core.jar`, `version.json`, and `SCTClient.jar` attached.

- [ ] **Step 2: Run `publish_update.ps1 -Version "1.0.0" -VersionCode 1 -Changelog "Initial Rise 6 Luxury release"`**

- [ ] **Step 3: Inspect generated JARs and run End-to-End Local HTTP Update Simulation**

Verify:
1. `jar tf dist/SCTClient.jar` has ZERO `com/client/mod/` or `dlovin/` class entries, HAS `com/client/loader/`, HAS `assets/sctclient/fallback-core.jar`, HAS `META-INF/jars/IAS-9.0.8+26.3-fabric.jar`.
2. `jar tf dist/SCTClient-Core.jar` HAS `com/client/mod/`, `dlovin/`, `customclient.mixins.json`, `inventoryhud.mixins.json`, `sct-core-meta.properties`, `assets/`.
3. End-to-end test: build `v1.0.0` + `v1.0.1`, serve `v1.0.1` (`dist/version.json` + `dist/SCTClient-Core.jar`) over localhost HTTP server, run `SctUpdater` with fallback from `SCTClient.jar`, assert updated `v1.0.1` downloads, SHA-256 matches, `pending-notice.json` triggers `UpdateNoticeManager.consumePendingNotice`, and `ClientBuildInfo` reads `"1.0.1"` from the downloaded JAR!

- [ ] **Step 4: Commit Task 4 & Merge/Push Prep**

```bash
git add publish_update.ps1
git commit -m "feat(build): add split bootloader/core packaging and GitHub Releases publisher"
```
