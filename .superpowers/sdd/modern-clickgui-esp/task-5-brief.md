# Task 5 Brief: Compilation, Packaging & Verification

**Files:**
- Scripts: `clean_rebuild_jar.ps1`, `build_test.ps1`
- Target: `C:\Users\PC\AppData\Roaming\PrismLauncher\instances\26.3\minecraft\mods\ClientMod-1.0.0.jar`

**Requirements:**
1. Run `build_test.ps1` to ensure all 54 sources compile with Javac exit code 0.
2. Run `clean_rebuild_jar.ps1` to extract, bundle, and generate clean jar.
3. Verify that `C:\Users\PC\AppData\Roaming\PrismLauncher\instances\26.3\minecraft\mods\ClientMod-1.0.0.jar` contains:
   - `com/client/mod/ModernClickGuiScreen.class`
   - `com/client/mod/EspRenderHandler.class`
   - `com/client/mod/ModConfig.class`
4. Report back the verification results to `.superpowers/sdd/modern-clickgui-esp/task-5-report.md`.
