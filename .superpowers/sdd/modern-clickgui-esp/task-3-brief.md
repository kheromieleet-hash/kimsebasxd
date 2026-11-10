# Task 3 Brief: Modern Single-Panel ClickGUI Screen

**Files:**
- Create: `src/main/java/com/client/mod/ModernClickGuiScreen.java`

**Requirements:**
1. Screen Dimensions & Styling:
   - Centered window `860x540` (clamped to `width - 20`, `height - 20` on small screens).
   - Semi-transparent dark glass panel with crimson outline (`0xEE0A0B0E`, border `0x55E53935`).
   - Top Header (height 48): `✦ SCT CLIENT v1.0.0` logo on left, search `EditBox` on right.
2. Sidebar on Left (width 170):
   - Categories: `⚔ Combat`, `🏃 Movement`, `👁 Render`, `⭐ ESP Settings`, `🌍 World`, `👤 Player`, `💣 Exploit`, `⚙ Settings`.
   - Click to switch active category with smooth highlight indicator.
3. Content Area:
   - For `⭐ ESP Settings`:
     - Left column: Toggle cards for `2D Box`, `Health Bar`, `Nametags`, `Skeleton`, `Armor HUD`.
     - Color Palette: 5 clickable color circles (Red `0xFFE53935`, Cyan `0xFF00E5FF`, Green `0xFF00E676`, Purple `0xFFD500F9`, Gold `0xFFFFAB00`).
     - Right column: "LIVE PREVIEW" panel drawing an interactive stylized Minecraft character mannequin with the actual active ESP overlays rendered on top in real time!
   - For other categories:
     - Module cards for each module in that category (from `ClickGuiScreen.getAllModules()`).
     - Toggles and sliders with smooth visual feedback.
   - Search filtering: When text is entered in search box, filter modules across all categories!
4. Navigation:
   - ESC closes screen.
   - Settings are immediately saved via `ModConfig.getInstance().save()`.
5. Verification:
   Run: `powershell -ExecutionPolicy Bypass -File "C:\Users\PC\.gemini\antigravity\brain\57534eac-943f-403b-bc3c-8bec60cca594\scratch\build_test.ps1"`
   Expected: Javac exit code: 0

Commit with: `git commit -am "feat: implement ModernClickGuiScreen with live ESP preview"`
Report to: `.superpowers/sdd/modern-clickgui-esp/task-3-report.md`
