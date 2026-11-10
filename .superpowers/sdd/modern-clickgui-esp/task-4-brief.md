# Task 4 Brief: Input Hook & Integration

**Files:**
- Modify: `src/main/java/com/client/mod/ClientMod.java`
- Modify: `src/main/java/com/client/mod/ClickGuiScreen.java`

**Requirements:**
1. In `ClientMod.java`:
   - Update Right Shift key handler:
     - If `client.gui.screen() instanceof ModernClickGuiScreen || client.gui.screen() instanceof ClickGuiScreen`, close it (`client.gui.setScreen(null)`).
     - If `client.gui.screen() == null && client.player != null`:
       - If `ModConfig.getInstance().clickGuiStyle == 1`: open `new ModernClickGuiScreen()`.
       - Else: open `new ClickGuiScreen(null)`.
   - Also handle `openMenuNextTick`:
     - If `ModConfig.getInstance().clickGuiStyle == 1`: open `new ModernClickGuiScreen()`.
     - Else: open `new ClickGuiScreen(null)`.
2. In `ClickGuiScreen.java`:
   - Under `Category.MISC`, add a "GUI Style" option allowing the user to toggle between `Modern Dashboard` and `Classic Multi-Window`.
3. Verification:
   Run: `powershell -ExecutionPolicy Bypass -File "C:\Users\PC\.gemini\antigravity\brain\57534eac-943f-403b-bc3c-8bec60cca594\scratch\build_test.ps1"`
   Expected: Javac exit code: 0

Commit with: `git commit -am "feat: hook Right Shift to open ModernClickGuiScreen and add style toggle"`
Report to: `.superpowers/sdd/modern-clickgui-esp/task-4-report.md`
