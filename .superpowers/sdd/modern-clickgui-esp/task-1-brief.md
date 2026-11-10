# Task 1 Brief: ModConfig State & Data Model for Modern ClickGUI and ESP

**Files:**
- Modify: `src/main/java/com/client/mod/ModConfig.java`

**Requirements:**
Add the following fields and helper methods to `ModConfig.java`:
```java
    // ESP Configuration
    public boolean espEnabled = true;
    public boolean espBox = true;
    public boolean espHealth = true;
    public boolean espNametags = true;
    public boolean espSkeleton = false;
    public boolean espArmor = true;
    public int espColorIndex = 0; // 0=Red, 1=Cyan, 2=Green, 3=Purple, 4=Gold
    public int espMaxDistance = 64; // 16 to 128 blocks

    // ClickGUI Style: 0 = Classic Multi-Window, 1 = Modern Single-Panel
    public int clickGuiStyle = 1;

    public int getEspColorHex() {
        return switch (espColorIndex) {
            case 1 -> 0xFF00E5FF; // Cyan
            case 2 -> 0xFF00E676; // Green
            case 3 -> 0xFFD500F9; // Purple
            case 4 -> 0xFFFFAB00; // Gold
            default -> 0xFFE53935; // Red (Default)
        };
    }
```
In `copyFrom(ModConfig other)`:
Copy all ESP fields and `clickGuiStyle`.

In `load()`:
Add bounds checking for `espColorIndex` (0 to 4), `espMaxDistance` (16 to 256), `clickGuiStyle` (0 to 1).

**Verification:**
Run: `powershell -ExecutionPolicy Bypass -File "C:\Users\PC\.gemini\antigravity\brain\57534eac-943f-403b-bc3c-8bec60cca594\scratch\build_test.ps1"`
Expected: Javac exit code: 0

Commit with: `git commit -am "feat: add ESP and Modern ClickGUI configuration fields to ModConfig"`
Report to: `.superpowers/sdd/modern-clickgui-esp/task-1-report.md`
