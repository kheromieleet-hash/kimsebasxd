# Task 2 Brief: Advanced In-Game ESP Rendering Engine

**Files:**
- Create: `src/main/java/com/client/mod/EspRenderHandler.java`
- Modify: `src/main/java/com/client/mod/mixin/HudOverlayMixin.java`

**Requirements:**
1. Implement `EspRenderHandler.java`:
   - High-performance in-game 2D Box ESP, Health Bar, Nametags, Skeleton, Armor HUD.
   - Project entity bounding boxes or interpolate player positions to screen coordinates.
   - Only target valid alive players (`RemotePlayer` or other non-local `Player` instances) within `config.espMaxDistance`.
   - Render when `config.espEnabled` is true:
     - If `config.espBox`: Render 2D bounding box with `config.getEspColorHex()`.
     - If `config.espHealth`: Render vertical health bar (green -> red based on HP percentage).
     - If `config.espNametags`: Render clear overhead name badge with player name, distance, and health.
     - If `config.espArmor`: Render armor durability/icons.
   - Clean methods with zero memory allocations in the render tick (reusable vectors/matrices).
2. Hook into `HudOverlayMixin.java`:
   - In `onRender(GuiGraphicsExtractor graphics, float delta, CallbackInfo ci)`, call `EspRenderHandler.renderInGameEsp(graphics, delta)`.
3. Verification:
   Run: `powershell -ExecutionPolicy Bypass -File "C:\Users\PC\.gemini\antigravity\brain\57534eac-943f-403b-bc3c-8bec60cca594\scratch\build_test.ps1"`
   Expected: Javac exit code: 0

Commit with: `git commit -am "feat: implement high-performance in-game EspRenderHandler"`
Report to: `.superpowers/sdd/modern-clickgui-esp/task-2-report.md`
