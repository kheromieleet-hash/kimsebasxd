# Modern ClickGUI & Advanced ESP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a sleek, single-panel modern ClickGUI (Rise / Lunar style) with live interactive ESP previews and an advanced in-game ESP rendering engine (2D Box, Health Bar, Nametags, Skeleton, Armor HUD, Color Palettes).

**Architecture:** Center-aligned dashboard screen (`ModernClickGuiScreen.java`) with category sidebar (`Combat`, `Movement`, `Render`, `⭐ ESP Settings`, `World`, `Player`, `Exploit`, `Settings`), smooth switches, search bar, and interactive preview. An independent `EspRenderHandler.java` renders 2D/3D ESP boxes, health bars, nametags, and skeletons in-game on entities. State is persisted in `ModConfig.java`.

**Tech Stack:** Java 25, Fabric Loader 0.18.x, Minecraft 26.3 snapshot / Mojang mappings, Netty, GuiGraphicsExtractor, Mixin 0.8.

**Spec:** `docs/superpowers/specs/2026-11-10-modern-clickgui-esp-design.md`

## Global Constraints

- Must run on Java 25 and Minecraft 26.3 snapshot without crashing or class loading conflicts.
- Zero external heavyweight dependencies; must use native Minecraft `GuiGraphicsExtractor` & level rendering.
- Must preserve all existing modules: Vulcan FastBreak, AbortBreaking, NoPitchLimit, Version Switcher, Keybinds HUD Table, Dulkir animations.
- Must compile cleanly with `build_test.ps1` (Javac exit code 0).
- Must package and deploy cleanly via `clean_rebuild_jar.ps1` to `C:\Users\PC\AppData\Roaming\PrismLauncher\instances\26.3\minecraft\mods\ClientMod-1.0.0.jar`.

## Review Focus

1. **Screen Resize / Aspect Ratio:** When Minecraft window is resized, the centered panel must remain properly clamped within viewport bounds and not clip outside the screen.
2. **Search Filter Case-Insensitivity:** Searching "esp", "ESP", or "fastbreak" in the search box must match modules regardless of case.
3. **ESP Performance & Distance Culling:** Players further than `espDistance` (e.g. 64m) or dead entities must not consume render cycles.
4. **Color Synchronization:** Changing the ESP color in the Modern ClickGUI must immediately update both the in-GUI live preview mannequin and the in-game world ESP rendering.
5. **Keybind Interaction:** Opening and closing via Right-Shift or ESC must cleanly save config and release mouse focus without sticking.

---

### Task 1: ModConfig State & Data Model for Modern ClickGUI and ESP

**Files:**
- Modify: `src/main/java/com/client/mod/ModConfig.java`

**Interfaces:**
- Produces:
  - `public boolean espEnabled = true;`
  - `public boolean espBox = true;`
  - `public boolean espHealth = true;`
  - `public boolean espNametags = true;`
  - `public boolean espSkeleton = false;`
  - `public boolean espArmor = true;`
  - `public int espColorIndex = 0; // 0=Red, 1=Cyan, 2=Green, 3=Purple, 4=Gold`
  - `public int espMaxDistance = 64;`
  - `public int clickGuiStyle = 1; // 0=Classic Multi-Window, 1=Modern Single-Panel`
  - `public int getEspColorHex()` helper method.

- [ ] **Step 1: Add ESP configuration fields and helper methods in `ModConfig.java`**
- [ ] **Step 2: Update `copyFrom(ModConfig other)` to copy all ESP fields**
- [ ] **Step 3: Update `load()` to sanitize and set defaults**
- [ ] **Step 4: Run `build_test.ps1` to verify compilation passes**

---

### Task 2: Advanced In-Game ESP Rendering Engine

**Files:**
- Create: `src/main/java/com/client/mod/EspRenderHandler.java`
- Modify: `src/main/java/com/client/mod/mixin/HudOverlayMixin.java` or `MinecraftMixin.java`

**Interfaces:**
- Consumes: `ModConfig.getInstance()`, Minecraft level entities (`LocalPlayer`, `RemotePlayer`).
- Produces: `EspRenderHandler.renderInGameEsp(GuiGraphicsExtractor graphics, float delta)`

- [ ] **Step 1: Create `EspRenderHandler.java`**
  - Implement world-to-screen projection for entity bounding boxes.
  - Implement 2D bounding box drawing with selected accent color (`espBox`).
  - Implement vertical dynamic health bar next to the box (`espHealth`).
  - Implement overhead nametag with player name, distance, and health percentage (`espNametags`).
  - Implement bone/joint rendering when `espSkeleton` is enabled.
- [ ] **Step 2: Hook `EspRenderHandler.renderInGameEsp` into the in-game render loop in `HudOverlayMixin.java`**
- [ ] **Step 3: Run `build_test.ps1` to verify zero compile errors**

---

### Task 3: Modern Single-Panel ClickGUI Screen

**Files:**
- Create: `src/main/java/com/client/mod/ModernClickGuiScreen.java`

**Interfaces:**
- Consumes: `ModConfig.getInstance()`, `ClickGuiScreen.getAllModules()`.
- Produces: `public class ModernClickGuiScreen extends Screen`
  - Sidebar with category tabs: Combat, Movement, Render, ⭐ ESP Settings, World, Player, Exploit, Settings.
  - Search bar filtering module list in real time.
  - Dedicated interactive "ESP Settings" view:
    - Switches for 2D Box, Health Bar, Nametags, Skeleton, Armor HUD.
    - Color palette chips (Red, Cyan, Green, Purple, Gold).
    - Live animated preview mannequin displaying the actual active ESP overlays in real time.
  - Standard module cards with animated toggle switches and sliders for all other categories.

- [ ] **Step 1: Create `ModernClickGuiScreen.java` with responsive layout (860x540 centered)**
- [ ] **Step 2: Implement category sidebar navigation and tab switching**
- [ ] **Step 3: Implement dedicated ESP settings panel with live preview mannequin**
- [ ] **Step 4: Implement search box with case-insensitive filtering**
- [ ] **Step 5: Run `build_test.ps1` to verify compilation passes**

---

### Task 4: Input Hook & Integration

**Files:**
- Modify: `src/main/java/com/client/mod/mixin/KeyboardHandlerMixin.java`
- Modify: `src/main/java/com/client/mod/ClickGuiScreen.java`

**Interfaces:**
- Hook Right-Shift (and keybind) to open `ModernClickGuiScreen` when `config.clickGuiStyle == 1`.
- Provide toggle in Settings to switch between Modern Dashboard and Classic Multi-Window.

- [ ] **Step 1: Update `KeyboardHandlerMixin.java` to open `ModernClickGuiScreen` on Right Shift**
- [ ] **Step 2: Update ClickGUI / ModConfig setting toggle**
- [ ] **Step 3: Run `build_test.ps1` to verify compilation passes**

---

### Task 5: Compilation, Packaging & Verification

**Files:**
- Modify: `customclient.mixins.json` (if new mixins added)
- Build: `clean_rebuild_jar.ps1`
- Target: `C:\Users\PC\AppData\Roaming\PrismLauncher\instances\26.3\minecraft\mods\ClientMod-1.0.0.jar`

- [ ] **Step 1: Compile all sources with `build_test.ps1` (Javac exit code 0)**
- [ ] **Step 2: Build clean jar and deploy with `clean_rebuild_jar.ps1`**
- [ ] **Step 3: Verify classes inside deployed jar**
- [ ] **Step 4: Confirm ready for user testing in PrismLauncher**
