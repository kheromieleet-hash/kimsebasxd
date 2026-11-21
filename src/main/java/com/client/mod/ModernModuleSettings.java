package com.client.mod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Declarative settings registry shared by ModernClickGuiScreen rendering,
 * hit-testing, height calculation, and unit tests. Modules without extra
 * configurable settings return an empty list so right-click and gear icons
 * stay inactive for toggle-only modules.
 */
public final class ModernModuleSettings {

    public static final int SLIDER_HEIGHT = 24;
    public static final int ROW_HEIGHT = 20;
    public static final int CHIP_HEIGHT = 17;
    public static final int COLOR_PALETTE_HEIGHT = 48;
    public static final int CHIP_GAP_Y = 3;
    public static final int CONTROL_GAP = 4;
    public static final int SECTION_TOP_PAD = 6;
    public static final int SECTION_BOTTOM_PAD = 6;

    @FunctionalInterface
    public interface FloatSupplier {
        float get();
    }

    @FunctionalInterface
    public interface FloatConsumer {
        void accept(float value);
    }

    @FunctionalInterface
    public interface BooleanSupplier {
        boolean get();
    }

    @FunctionalInterface
    public interface BooleanConsumer {
        void accept(boolean value);
    }

    public interface InventoryBridge {
        boolean isActive();
        void setActive(boolean value);
        boolean isArmor();
        void setArmor(boolean value);
        boolean isPotions();
        void setPotions(boolean value);
        boolean isMini();
        void setMini(boolean value);
        boolean isVert();
        void setVert(boolean value);
        int getBgAlpha();
        void setBgAlpha(int alpha);
    }

    public interface Actions {
        void persist(boolean reloadChunks);
        void openInventoryMove();
        InventoryBridge inventory();
    }

    public sealed interface Control permits SliderControl, ToggleControl, CycleControl, ActionControl, ChipsControl, ColorPaletteControl {
        int height();
    }

    public record SliderControl(
            String key,
            String label,
            FloatSupplier pctGetter,
            FloatConsumer pctSetter,
            Supplier<String> valueLabel,
            boolean needsChunkReload,
            Supplier<String> editText,
            Predicate<String> textCommitter
    ) implements Control {
        @Override
        public int height() {
            return SLIDER_HEIGHT;
        }
    }

    public record ToggleControl(
            String label,
            BooleanSupplier getter,
            BooleanConsumer setter
    ) implements Control {
        @Override
        public int height() {
            return ROW_HEIGHT;
        }
    }

    public record CycleControl(
            String label,
            Supplier<String> valueLabel,
            Runnable onCycle
    ) implements Control {
        @Override
        public int height() {
            return ROW_HEIGHT;
        }
    }

    public record ActionControl(
            Supplier<String> labelSupplier,
            Runnable onClick
    ) implements Control {
        @Override
        public int height() {
            return ROW_HEIGHT;
        }
    }

    public record ChipOption(
            String id,
            String label,
            ItemStack icon,
            BooleanSupplier activeGetter,
            Runnable onToggle
    ) {
        public ChipOption(String id, String label, BooleanSupplier activeGetter, Runnable onToggle) {
            this(id, label, ItemStack.EMPTY, activeGetter, onToggle);
        }
    }

    public record ChipsControl(List<ChipOption> options) implements Control {
        @Override
        public int height() {
            int rows = (this.options.size() + 1) / 2;
            return Math.max(CHIP_HEIGHT, rows * CHIP_HEIGHT + Math.max(0, rows - 1) * CHIP_GAP_Y);
        }
    }

    public record ColorPaletteControl(
            String key,
            BooleanSupplier chromaGetter,
            Runnable onSelectChroma,
            java.util.function.IntSupplier activeRgbGetter,
            java.util.function.IntConsumer onSelectSwatch,
            FloatSupplier hueGetter,
            FloatConsumer hueSetter,
            FloatSupplier satGetter,
            FloatConsumer satSetter,
            FloatSupplier valGetter,
            FloatConsumer valSetter,
            Runnable onReset
    ) implements Control {
        @Override
        public int height() {
            return COLOR_PALETTE_HEIGHT;
        }
    }

    private ModernModuleSettings() {
    }

    public static boolean hasSettings(String modName, ModConfig cfg, Actions actions) {
        return !getControls(modName, cfg, actions).isEmpty();
    }

    /**
     * Resets all configurable controls (sliders, toggles, cycle modes) of a module back to
     * fresh ModConfig defaults without per-module duplicate boilerplate.
     */
    public static boolean resetModuleToDefaults(String modName, ModConfig cfg, Actions actions) {
        if (modName == null || cfg == null) {
            return false;
        }
        final boolean[] needsReload = {false};
        Actions deferredActions = new Actions() {
            @Override
            public void persist(boolean reloadChunks) {
                if (reloadChunks) {
                    needsReload[0] = true;
                }
            }

            @Override
            public void openInventoryMove() {
            }

            @Override
            public InventoryBridge inventory() {
                return actions != null ? actions.inventory() : null;
            }
        };

        List<Control> defaultControls = getControls(modName, new ModConfig(), deferredActions);
        List<Control> liveControls = getControls(modName, cfg, deferredActions);
        if (defaultControls.isEmpty()) {
            return false;
        }

        boolean changed = false;
        // Pass 1: restore toggles & cycles first (these can reveal/hide dynamic child controls)
        for (Control def : defaultControls) {
            if (def instanceof ToggleControl dt) {
                for (Control live : liveControls) {
                    if (live instanceof ToggleControl lt && dt.label().equals(lt.label())) {
                        boolean defVal = dt.getter().get();
                        if (lt.getter().get() != defVal) {
                            lt.setter().accept(defVal);
                            changed = true;
                        }
                        break;
                    }
                }
            } else if (def instanceof CycleControl dc) {
                for (Control live : liveControls) {
                    if (live instanceof CycleControl lc && dc.label().equals(lc.label())) {
                        String target = dc.valueLabel().get();
                        if (target != null && !target.equals(lc.valueLabel().get())) {
                            for (int step = 0; step < 24; step++) {
                                lc.onCycle().run();
                                if (target.equals(lc.valueLabel().get())) {
                                    changed = true;
                                    break;
                                }
                            }
                        }
                        break;
                    }
                }
            }
        }

        // Pass 2: re-fetch live controls in case toggles revealed sliders, then restore all sliders
        liveControls = getControls(modName, cfg, deferredActions);
        for (Control def : defaultControls) {
            if (def instanceof SliderControl ds) {
                for (Control live : liveControls) {
                    if (live instanceof SliderControl ls && ds.key().equals(ls.key())) {
                        float defPct = ds.pctGetter().get();
                        if (Math.abs(ls.pctGetter().get() - defPct) > 0.0005f) {
                            ls.pctSetter().accept(defPct);
                            if (ls.needsChunkReload()) {
                                needsReload[0] = true;
                            }
                            changed = true;
                        }
                        break;
                    }
                }
            } else if (def instanceof ColorPaletteControl dc) {
                for (Control live : liveControls) {
                    if (live instanceof ColorPaletteControl lc && dc.key().equals(lc.key())) {
                        lc.onReset().run();
                        changed = true;
                        break;
                    }
                }
            }
        }
        if (actions != null) {
            actions.persist(needsReload[0]);
        } else {
            cfg.save();
        }
        return changed;
    }

    public static int computeExpandedHeight(List<Control> controls) {
        if (controls == null || controls.isEmpty()) {
            return 0;
        }
        int total = SECTION_TOP_PAD + SECTION_BOTTOM_PAD;
        for (int i = 0; i < controls.size(); i++) {
            total += controls.get(i).height();
            if (i + 1 < controls.size()) {
                total += CONTROL_GAP;
            }
        }
        return total;
    }

    public static List<Control> getControls(String modName, ModConfig cfg, Actions actions) {
        if (modName == null || cfg == null || actions == null) {
            return List.of();
        }
        return switch (modName) {
            case "Viewmodel" -> buildViewmodelControls(cfg, actions);
            case "Inventory HUD+" -> buildInventoryHudControls(cfg, actions);
            case "Keybinds HUD" -> buildKeybindsHudControls(cfg, actions);
            case "Player List" -> buildPlayerListControls(cfg, actions);
            case "HUD Info" -> buildHudInfoControls(cfg, actions);
            case "Fast Place" -> List.of(
                    toggle("Auto Right Click", () -> cfg.fastPlaceAutoRightClick, v -> {
                        cfg.fastPlaceAutoRightClick = v;
                        if (!v) {
                            cfg.fastPlaceAutoRightClickResume = false;
                        }
                    }, actions, false),
                    intSlider("fastPlaceDelay", "Fast Place Delay", 0, 4, "t",
                            () -> cfg.fastPlaceDelay, v -> cfg.fastPlaceDelay = v)
            );
            case "Auto Break" -> List.of(
                    toggle("Only With Tool", () -> cfg.autoBreakOnlyTool, v -> cfg.autoBreakOnlyTool = v, actions, false)
            );
            case "FastBreak" -> buildFastBreakControls(cfg, actions);
            case "Inventory Tweaks" -> buildInventoryTweaksControls(cfg, actions);
            case "Auto Replenish" -> buildAutoReplenishControls(cfg, actions);
            case "Fast Use" -> List.of(
                    intSlider("fastUseCooldown", "Cooldown", 0, 4, "t",
                            () -> cfg.fastUseCooldown, v -> cfg.fastUseCooldown = v)
            );
            case "Auto Tool" -> List.of(
                    toggle("Swap Back", () -> cfg.autoToolSwapBack, v -> cfg.autoToolSwapBack = v, actions, false)
            );
            case "Auto Crafter" -> buildAutoCrafterControls(cfg, actions);
            case "Auto Sell" -> buildAutoSellControls(cfg, actions);
            case "Auto Drop" -> buildAutoDropControls(cfg, actions);
            case "No Strip" -> List.of(
                    toggle("Allow Sneaking", () -> cfg.noStripAllowSneak, v -> cfg.noStripAllowSneak = v, actions, false)
            );
            case "Better Place" -> List.of(
                    floatSlider("betterPlaceRange", "Place Range", 2.0f, 6.0f, 4.0f, "%.1fb",
                            () -> cfg.betterPlaceRange, v -> cfg.betterPlaceRange = v)
            );
            case "Auto Wheat Farm" -> buildWheatFarmControls(cfg, actions);
            case "Auto Fish" -> List.of(
                    toggle("Auto Recast", () -> cfg.autoFishRecast, v -> cfg.autoFishRecast = v, actions, false),
                    intSlider("autoFishClickDelay", "Click Delay", 1, 15, "t",
                            () -> cfg.autoFishClickDelay, v -> cfg.autoFishClickDelay = v),
                    intSlider("autoFishRecastDelay", "Recast Delay", 8, 40, "t",
                            () -> cfg.autoFishRecastDelay, v -> cfg.autoFishRecastDelay = v)
            );
            case "Auto Spawner" -> buildAutoSpawnerControls(cfg, actions);
            case "Spawner Mode" -> buildSpawnerAutoSellControls(cfg, actions);
            case "Auto Walk" -> List.of(
                    new CycleControl("Direction",
                            () -> switch (Math.floorMod(cfg.autoWalkDirection, 4)) {
                                case 1 -> "Backwards";
                                case 2 -> "Left";
                                case 3 -> "Right";
                                default -> "Forwards";
                            },
                            () -> {
                                cfg.autoWalkDirection = (Math.floorMod(cfg.autoWalkDirection, 4) + 1) % 4;
                                actions.persist(false);
                            })
            );
            case "New Velocity" -> buildNewVelocityControls(cfg, actions);
            case "Item Sucker" -> buildItemSuckerControls(cfg, actions);
            case "Backtrack" -> List.of(
                    intSlider("backtrackDelay", "Max Delay", 40, 300, "ms",
                            () -> cfg.backtrackDelay, v -> cfg.backtrackDelay = v)
            );
            case "Disabler" -> List.of(
                    new CycleControl("Mode",
                            () -> cfg.disablerMode == 1 ? "Grim" : "Vulcan",
                            () -> {
                                cfg.disablerMode = (cfg.disablerMode + 1) % 2;
                                actions.persist(false);
                            })
            );
            case "Exploit Guard" -> List.of(
                    intSlider("exploitGuardMaxParticles", "Max Particles", 100, 2000, "",
                            () -> cfg.exploitGuardMaxParticles, v -> cfg.exploitGuardMaxParticles = v)
            );
            case "No Mining Trace" -> List.of(
                    toggle("Tools Only", () -> cfg.noMiningTraceToolsOnly, v -> cfg.noMiningTraceToolsOnly = v, actions, false)
            );
            case "No Slowdown" -> List.of(
                    new CycleControl("Mode",
                            () -> switch (cfg.noSlowdownMode) {
                                case 1 -> "Vulcan";
                                case 2 -> "Grim";
                                default -> "Vanilla";
                            },
                            () -> {
                                cfg.noSlowdownMode = (cfg.noSlowdownMode + 1) % 3;
                                actions.persist(false);
                            })
            );
            case "Ping Spoof" -> List.of(
                    intSlider("pingSpoofDelay", "Delay", 50, 1000, "ms",
                            () -> cfg.pingSpoofDelay, v -> cfg.pingSpoofDelay = v)
            );
            case "Packet Mine" -> List.of(
                    toggle("Instant Rebreak", () -> cfg.packetMineRebreak, v -> cfg.packetMineRebreak = v, actions, false),
                    intSlider("packetMineDelay", "Delay", 0, 5, "t",
                            () -> cfg.packetMineDelay, v -> cfg.packetMineDelay = v),
                    toggle("Render Box", () -> cfg.packetMineRender, v -> cfg.packetMineRender = v, actions, false)
            );
            case "Chunk Borders" -> List.of(
                    toggle("Sub-Grid", () -> cfg.chunkBordersSubGrid, v -> cfg.chunkBordersSubGrid = v, actions, false)
            );
            case "Light Overlay" -> List.of(
                    intSlider("lightOverlayRange", "Range", 8, 24, "",
                            () -> cfg.lightOverlayRange, v -> cfg.lightOverlayRange = v),
                    toggle("Only Always", () -> cfg.lightOverlayOnlyAlways, v -> cfg.lightOverlayOnlyAlways = v, actions, false)
            );
            case "Trajectories" -> List.of(
                    toggle("Show Passive", () -> cfg.trajectoriesShowPassive, v -> cfg.trajectoriesShowPassive = v, actions, false),
                    toggle("Box Outline", () -> cfg.trajectoriesBoxOutline, v -> cfg.trajectoriesBoxOutline = v, actions, false)
            );
            case "Freecam" -> List.of(
                    floatSlider("freecamSpeed", "Speed", 0.2f, 5.0f, 20.0f, "%.2fx",
                            () -> cfg.freecamSpeed, v -> cfg.freecamSpeed = v),
                    toggle("Scroll Speed", () -> cfg.freecamScrollSpeed, v -> cfg.freecamScrollSpeed = v, actions, false),
                    toggle("Toggle on Damage", () -> cfg.freecamToggleOnDamage, v -> cfg.freecamToggleOnDamage = v, actions, false),
                    toggle("Allow Interact", () -> cfg.freecamAllowInteract, v -> cfg.freecamAllowInteract = v, actions, false),
                    toggle("Rotate", () -> cfg.freecamRotate, v -> cfg.freecamRotate = v, actions, false)
            );
            case "Wings" -> buildWingsControls(cfg, actions);
            case "Xray" -> buildXrayControls(cfg, actions);
            case "Block ESP" -> buildBlockEspControls(cfg, actions);
            default -> List.of();
        };
    }

    private static List<Control> buildInventoryHudControls(ModConfig cfg, Actions actions) {
        InventoryBridge inv = actions.inventory();
        return List.of(
                toggle("Inventory HUD", inv::isActive, inv::setActive, actions, false),
                toggle("Armor HUD", inv::isArmor, inv::setArmor, actions, false),
                toggle("Potions HUD", inv::isPotions, inv::setPotions, actions, false),
                toggle("Mini Mode", inv::isMini, inv::setMini, actions, false),
                toggle("Vertical Mode", inv::isVert, inv::setVert, actions, false),
                floatSlider("invHudScale", "Inv Scale", 0.50f, 2.00f, 20.0f, "%.2fx",
                        () -> cfg.invHudScale, v -> cfg.invHudScale = v),
                intSlider("invBgAlpha", "Bg Opacity", 0, 100, "%", inv::getBgAlpha, inv::setBgAlpha)
        );
    }

    private static List<Control> buildKeybindsHudControls(ModConfig cfg, Actions actions) {
        return List.of(
                toggle("Only Active", () -> cfg.keybindsHudOnlyActive, v -> cfg.keybindsHudOnlyActive = v, actions, false),
                toggle("Auto-Hide", () -> cfg.keybindsHudAutoHide, v -> cfg.keybindsHudAutoHide = v, actions, false),
                new ActionControl(() -> "Reset Position", () -> {
                    cfg.keybindsHudX = 8;
                    cfg.keybindsHudY = 60;
                    actions.persist(false);
                })
        );
    }

    private static List<Control> buildPlayerListControls(ModConfig cfg, Actions actions) {
        return List.of(
                new CycleControl("Side",
                        () -> cfg.playerListAlign == 1 ? "Top Left" : "Top Right",
                        () -> {
                            cfg.playerListAlign = cfg.playerListAlign == 1 ? 0 : 1;
                            cfg.playerListX = -1;
                            cfg.playerListY = -1;
                            actions.persist(false);
                        }),
                toggle("Show Distance", () -> cfg.playerListShowDistance, v -> cfg.playerListShowDistance = v, actions, false),
                toggle("Show Health", () -> cfg.playerListShowHealth, v -> cfg.playerListShowHealth = v, actions, false),
                toggle("Ignore NPCs", () -> cfg.playerListIgnoreNpcs, v -> cfg.playerListIgnoreNpcs = v, actions, false),
                toggle("Auto-Hide", () -> cfg.playerListAutoHide, v -> cfg.playerListAutoHide = v, actions, false),
                intSlider("playerListMaxRows", "Visible Rows", 4, 40, "",
                        () -> cfg.playerListMaxRows, v -> cfg.playerListMaxRows = v),
                new ActionControl(() -> "Reset Position", () -> {
                    cfg.playerListX = -1;
                    cfg.playerListY = -1;
                    actions.persist(false);
                })
        );
    }

    private static List<Control> buildHudInfoControls(ModConfig cfg, Actions actions) {
        return List.of(
                toggle("Show FPS", () -> cfg.hudShowFps, v -> cfg.hudShowFps = v, actions, false),
                toggle("Show TPS", () -> cfg.hudShowTps, v -> cfg.hudShowTps = v, actions, false),
                toggle("Show Ping", () -> cfg.hudShowPing, v -> cfg.hudShowPing = v, actions, false),
                toggle("Show Speed", () -> cfg.hudShowSpeed, v -> cfg.hudShowSpeed = v, actions, false),
                toggle("Show Direction", () -> cfg.hudShowDir, v -> cfg.hudShowDir = v, actions, false),
                toggle("Show Nether", () -> cfg.hudShowNether, v -> cfg.hudShowNether = v, actions, false),
                toggle("Show Pos", () -> cfg.hudShowPos, v -> cfg.hudShowPos = v, actions, false),
                toggle("Show Clock", () -> cfg.hudShowClock, v -> cfg.hudShowClock = v, actions, false),
                toggle("Unified Glass", () -> cfg.hudUnifiedGlass, v -> cfg.hudUnifiedGlass = v, actions, false),
                toggle("Watermark", () -> cfg.hudWatermarkEnabled, v -> cfg.hudWatermarkEnabled = v, actions, false),
                toggle("Active Modules", () -> cfg.hudArrayListEnabled, v -> cfg.hudArrayListEnabled = v, actions, false)
        );
    }

    private static List<Control> buildFastBreakControls(ModConfig cfg, Actions actions) {
        return List.of(
                new CycleControl("Mode",
                        () -> cfg.fastBreakMode == 1 ? "Vulcan" : "Vanilla",
                        () -> {
                            cfg.fastBreakMode = (cfg.fastBreakMode == 1) ? 0 : 1;
                            if (cfg.fastBreakMode == 1 && cfg.fastBreakSpeed > 1.15f) {
                                cfg.fastBreakSpeed = 1.15f;
                            }
                            actions.persist(false);
                        }),
                new SliderControl("fastBreakSpeed", "Break Speed",
                        () -> {
                            if (cfg.fastBreakMode == 1) {
                                return (Math.min(cfg.fastBreakSpeed, 1.15f) - 1.0f) / 0.15f;
                            }
                            return (cfg.fastBreakSpeed - 0.5f) / 4.5f;
                        },
                        pct -> {
                            if (cfg.fastBreakMode == 1) {
                                cfg.fastBreakSpeed = roundToStep(1.0f + pct * 0.15f, 100.0f);
                            } else {
                                cfg.fastBreakSpeed = roundToStep(0.5f + pct * 4.5f, 10.0f);
                            }
                        },
                        () -> cfg.fastBreakMode == 1
                                ? String.format(Locale.ROOT, "%.2fx", Math.min(cfg.fastBreakSpeed, 1.15f))
                                : String.format(Locale.ROOT, "%.1fx", cfg.fastBreakSpeed),
                        false,
                        () -> cfg.fastBreakMode == 1
                                ? String.format(Locale.ROOT, "%.2f", Math.min(cfg.fastBreakSpeed, 1.15f))
                                : String.format(Locale.ROOT, "%.1f", cfg.fastBreakSpeed),
                        raw -> cfg.fastBreakMode == 1
                                ? parseAndApplyFloat(raw, 1.0f, 1.15f, v -> cfg.fastBreakSpeed = v)
                                : parseAndApplyFloat(raw, 0.5f, 5.0f, v -> cfg.fastBreakSpeed = v))
        );
    }

    private static List<Control> buildInventoryTweaksControls(ModConfig cfg, Actions actions) {
        return List.of(
                toggle("Mouse Drag Move", () -> cfg.invMouseDrag, v -> cfg.invMouseDrag = v, actions, false),
                toggle("Auto Sorting", () -> cfg.invSorting, v -> cfg.invSorting = v, actions, false),
                intSlider("invSortingDelay", "Sorting Delay", 0, 10, "t",
                        () -> cfg.invSortingDelay, v -> cfg.invSortingDelay = v),
                toggle("Protect Hotbar", () -> cfg.invProtectHotbar, v -> cfg.invProtectHotbar = v, actions, false),
                toggle("Xcarry (2x2)", () -> cfg.invXcarry, v -> cfg.invXcarry = v, actions, false),
                toggle("Skip In Creative", () -> cfg.invDisableInCreative, v -> cfg.invDisableInCreative = v, actions, false)
        );
    }

    private static List<Control> buildAutoReplenishControls(ModConfig cfg, Actions actions) {
        return List.of(
                intSlider("autoReplenishMinCount", "Min Count", 1, 63, "",
                        () -> cfg.autoReplenishMinCount, v -> cfg.autoReplenishMinCount = v),
                intSlider("autoReplenishTickDelay", "Tick Delay", 0, 10, "t",
                        () -> cfg.autoReplenishTickDelay, v -> cfg.autoReplenishTickDelay = v),
                toggle("Offhand", () -> cfg.autoReplenishOffhand, v -> cfg.autoReplenishOffhand = v, actions, false),
                toggle("Unstackable", () -> cfg.autoReplenishUnstackable, v -> cfg.autoReplenishUnstackable = v, actions, false),
                toggle("Same Enchants", () -> cfg.autoReplenishSameEnchants, v -> cfg.autoReplenishSameEnchants = v, actions, false),
                toggle("Search Hotbar", () -> cfg.autoReplenishSearchHotbar, v -> cfg.autoReplenishSearchHotbar = v, actions, false)
        );
    }

    private static List<Control> buildAutoCrafterControls(ModConfig cfg, Actions actions) {
        List<String> selectedIds = new ArrayList<>();
        if (cfg.autoCrafterTargets != null) {
            selectedIds.addAll(cfg.autoCrafterTargets);
            selectedIds.sort(String.CASE_INSENSITIVE_ORDER);
        }

        List<ChipOption> chips = new ArrayList<>(selectedIds.size());
        for (String id : selectedIds) {
            ItemStack icon = resolveItemStack(id);
            String label = !icon.isEmpty() ? icon.getHoverName().getString() : resolveItemShortLabel(id);
            chips.add(new ChipOption(
                    id,
                    label,
                    icon,
                    () -> cfg.isAutoCraftTargetEnabled(id),
                    () -> {
                        cfg.toggleAutoCraftTarget(id);
                        actions.persist(false);
                    }
            ));
        }

        List<Control> controls = new ArrayList<>();
        controls.add(intSlider("autoCrafterDelay", "Delay", 0, 5, "t",
                () -> cfg.autoCrafterDelay, v -> cfg.autoCrafterDelay = v));
        controls.add(toggle("Close When Done", () -> cfg.autoCrafterCloseWhenDone, v -> cfg.autoCrafterCloseWhenDone = v, actions, false));
        controls.add(new ActionControl(
                () -> {
                    int n = cfg.autoCrafterTargets != null ? cfg.autoCrafterTargets.size() : 0;
                    return "Select Items (" + n + ")...";
                },
                () -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    mc.gui.setScreen(new AutoCrafterItemPickerScreen(mc.gui.screen()));
                }));
        if (!chips.isEmpty()) {
            controls.add(new ChipsControl(chips));
        }
        return controls;
    }

    private static List<Control> buildAutoSellControls(ModConfig cfg, Actions actions) {
        List<ChipOption> chips = buildItemChips(cfg.autoSellTargets, cfg::isAutoSellTargetEnabled, id -> {
            cfg.toggleAutoSellTarget(id);
            actions.persist(false);
        });

        List<Control> controls = new ArrayList<>();
        controls.add(intSlider("autoSellDelay", "Delay", 1, 60, "s",
                () -> cfg.autoSellDelay, v -> cfg.autoSellDelay = v));
        controls.add(toggle("Safety Pause", () -> cfg.autoSellSafetyPause, v -> cfg.autoSellSafetyPause = v, actions, false));
        controls.add(toggle("Only Selected Items", () -> cfg.autoSellOnlySelected, v -> cfg.autoSellOnlySelected = v, actions, false));
        controls.add(new ActionControl(
                () -> {
                    int n = cfg.autoSellTargets != null ? cfg.autoSellTargets.size() : 0;
                    return "Select Items (" + n + ")...";
                },
                () -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    mc.gui.setScreen(new CatalogItemPickerScreen(
                            mc.gui.screen(),
                            "AUTO SELL — ITEM PICKER",
                            "Triggers /sell all when in inventory",
                            cfg::isAutoSellTargetEnabled,
                            id -> {
                                cfg.toggleAutoSellTarget(id);
                                cfg.save();
                            },
                            () -> {
                                if (cfg.autoSellTargets != null) {
                                    cfg.autoSellTargets.clear();
                                }
                                cfg.save();
                            },
                            () -> cfg.autoSellTargets != null ? cfg.autoSellTargets.size() : 0
                    ));
                }));
        if (!chips.isEmpty()) {
            controls.add(new ChipsControl(chips));
        }
        return controls;
    }

    private static List<Control> buildAutoDropControls(ModConfig cfg, Actions actions) {
        List<ChipOption> chips = buildItemChips(cfg.autoDropTargets, cfg::isAutoDropTargetEnabled, id -> {
            cfg.toggleAutoDropTarget(id);
            actions.persist(false);
        });

        List<Control> controls = new ArrayList<>();
        controls.add(intSlider("autoDropDelay", "Delay", 0, 20, "t",
                () -> cfg.autoDropDelay, v -> cfg.autoDropDelay = v));
        controls.add(toggle("Exclude Hotbar", () -> cfg.autoDropExcludeHotbar, v -> cfg.autoDropExcludeHotbar = v, actions, false));
        controls.add(new ActionControl(
                () -> {
                    int n = cfg.autoDropTargets != null ? cfg.autoDropTargets.size() : 0;
                    return "Select Items (" + n + ")...";
                },
                () -> {
                    net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                    mc.gui.setScreen(new CatalogItemPickerScreen(
                            mc.gui.screen(),
                            "AUTO DROP — ITEM PICKER",
                            "Click any item to toggle auto-dropping",
                            cfg::isAutoDropTargetEnabled,
                            id -> {
                                cfg.toggleAutoDropTarget(id);
                                cfg.save();
                            },
                            () -> {
                                if (cfg.autoDropTargets != null) {
                                    cfg.autoDropTargets.clear();
                                }
                                cfg.save();
                            },
                            () -> cfg.autoDropTargets != null ? cfg.autoDropTargets.size() : 0
                    ));
                }));
        if (!chips.isEmpty()) {
            controls.add(new ChipsControl(chips));
        }
        return controls;
    }

    private static List<Control> buildWheatFarmControls(ModConfig cfg, Actions actions) {
        List<Control> controls = new ArrayList<>(List.of(
                toggle("Stick Wand", () -> cfg.wheatFarmStickWand, v -> cfg.wheatFarmStickWand = v, actions, false),
                toggle("Walk To Blocks", () -> cfg.wheatFarmWalk, v -> cfg.wheatFarmWalk = v, actions, false),
                toggle("Fly Mode", () -> cfg.wheatFarmFly, v -> cfg.wheatFarmFly = v, actions, false),
                toggle("Failsafe Alert", () -> cfg.wheatFarmFailsafe, v -> cfg.wheatFarmFailsafe = v, actions, false),
                intSlider("wheatFarmDelay", "Action Delay", 1, 20, "t",
                        () -> cfg.wheatFarmDelay, v -> cfg.wheatFarmDelay = v),
                floatSlider("wheatFarmTurnSpeed", "Turn Speed", 5.0f, 30.0f, 1.0f, "%.0f°",
                        () -> cfg.wheatFarmTurnSpeed, v -> cfg.wheatFarmTurnSpeed = v),
                new ActionControl(
                        () -> cfg.wheatFarmPos1Set
                                ? "Set Pos 1 (" + cfg.wheatFarmX1 + "," + cfg.wheatFarmY1 + "," + cfg.wheatFarmZ1 + ")"
                                : "Set Pos 1 (Here)",
                        () -> {
                            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                            if (mc.player != null) {
                                net.minecraft.core.BlockPos p = mc.player.blockPosition().below();
                                cfg.wheatFarmX1 = p.getX();
                                cfg.wheatFarmY1 = p.getY();
                                cfg.wheatFarmZ1 = p.getZ();
                                cfg.wheatFarmPos1Set = true;
                                actions.persist(false);
                            }
                        }),
                new ActionControl(
                        () -> cfg.wheatFarmPos2Set
                                ? "Set Pos 2 (" + cfg.wheatFarmX2 + "," + cfg.wheatFarmY2 + "," + cfg.wheatFarmZ2 + ")"
                                : "Set Pos 2 (Here)",
                        () -> {
                            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                            if (mc.player != null) {
                                net.minecraft.core.BlockPos p = mc.player.blockPosition().below();
                                cfg.wheatFarmX2 = p.getX();
                                cfg.wheatFarmY2 = p.getY();
                                cfg.wheatFarmZ2 = p.getZ();
                                cfg.wheatFarmPos2Set = true;
                                actions.persist(false);
                            }
                        })
        ));
        if (cfg.wheatFarmWhitelist != null && !cfg.wheatFarmWhitelist.isEmpty()) {
            List<ChipOption> chips = new ArrayList<>();
            for (String name : cfg.wheatFarmWhitelist) {
                chips.add(new ChipOption("wl_" + name, "× " + name,
                        () -> cfg.isWheatFarmWhitelisted(name),
                        () -> {
                            cfg.removeWheatFarmWhitelist(name);
                            actions.persist(false);
                        }));
            }
            controls.add(new ChipsControl(chips));
        }
        return controls;
    }

    private static List<Control> buildNewVelocityControls(ModConfig cfg, Actions actions) {
        return List.of(
                new CycleControl("Mode",
                        () -> switch (cfg.newVelocityMode) {
                            case 1 -> "Jump Reset";
                            case 2 -> "Grim";
                            default -> "Normal";
                        },
                        () -> {
                            cfg.newVelocityMode = (cfg.newVelocityMode + 1) % 3;
                            actions.persist(false);
                        }),
                intSlider("newVelocityHorizontal", "Horizontal", 0, 100, "%",
                        () -> cfg.newVelocityHorizontal, v -> cfg.newVelocityHorizontal = v),
                intSlider("newVelocityVertical", "Vertical", 0, 100, "%",
                        () -> cfg.newVelocityVertical, v -> cfg.newVelocityVertical = v),
                intSlider("newVelocityChance", "Chance", 0, 100, "%",
                        () -> cfg.newVelocityChance, v -> cfg.newVelocityChance = v),
                toggle("Explosions", () -> cfg.newVelocityExplosions, v -> cfg.newVelocityExplosions = v, actions, false)
        );
    }

    private static List<Control> buildItemSuckerControls(ModConfig cfg, Actions actions) {
        return List.of(
                new CycleControl("Mode",
                        () -> cfg.itemSuckerMode == 1 ? "TP" : "Move",
                        () -> {
                            cfg.itemSuckerMode = (cfg.itemSuckerMode + 1) % 2;
                            actions.persist(false);
                        }),
                floatSlider("itemSuckerRange", "Range", 2.0f, 16.0f, 2.0f, "%.1fm",
                        () -> cfg.itemSuckerRange, v -> cfg.itemSuckerRange = v),
                floatSlider("itemSuckerSpeed", "Move Speed", 0.15f, 1.00f, 20.0f, "%.2f",
                        () -> cfg.itemSuckerSpeed, v -> cfg.itemSuckerSpeed = v),
                toggle("Only Pickable", () -> cfg.itemSuckerOnlyPickable, v -> cfg.itemSuckerOnlyPickable = v, actions, false),
                toggle("Only On Ground", () -> cfg.itemSuckerOnlyOnGround, v -> cfg.itemSuckerOnlyOnGround = v, actions, false)
        );
    }

    private static List<Control> buildAutoSpawnerControls(ModConfig cfg, Actions actions) {
        return List.of(
                new SliderControl("autoSpawnerCount", "Spawner Count",
                        () -> cfg.autoSpawnerCount / 256.0f,
                        pct -> cfg.autoSpawnerCount = clampInt(Math.round(pct * 256.0f), 0, 256),
                        () -> cfg.autoSpawnerCount <= 0 ? "Auto" : Integer.toString(cfg.autoSpawnerCount),
                        false,
                        () -> Integer.toString(cfg.autoSpawnerCount),
                        raw -> {
                            String trimmed = raw == null ? "" : raw.trim();
                            if ("auto".equalsIgnoreCase(trimmed)) {
                                cfg.autoSpawnerCount = 0;
                                return true;
                            }
                            return parseAndApplyInt(trimmed, 0, 256, v -> cfg.autoSpawnerCount = v);
                        }),
                intSlider("autoSpawnerSearchRadius", "Search Radius", 12, 40, "b",
                        () -> cfg.autoSpawnerSearchRadius, v -> cfg.autoSpawnerSearchRadius = v),
                intSlider("autoSpawnerClickDelay", "Click Delay", 2, 8, "t",
                        () -> cfg.autoSpawnerClickDelay, v -> cfg.autoSpawnerClickDelay = v),
                toggle("Lock Mouse", () -> cfg.autoSpawnerLockMouse, v -> cfg.autoSpawnerLockMouse = v, actions, false),
                toggle("Render ESP", () -> cfg.autoSpawnerRenderEsp, v -> cfg.autoSpawnerRenderEsp = v, actions, false),
                toggle("Detailed HUD", () -> cfg.autoSpawnerHudDetailed, v -> cfg.autoSpawnerHudDetailed = v, actions, false),
                new CycleControl("ESP Detail",
                        () -> switch (cycleIndex(cfg.autoSpawnerEspDetail, 3, 2)) {
                            case 0 -> "Minimal";
                            case 1 -> "Standard";
                            default -> "Tactical";
                        },
                        () -> {
                            cfg.autoSpawnerEspDetail = (cycleIndex(cfg.autoSpawnerEspDetail, 3, 2) + 1) % 3;
                            actions.persist(false);
                        }),
                toggle("Reach Batching", () -> cfg.autoSpawnerReachBatch, v -> cfg.autoSpawnerReachBatch = v, actions, false),
                toggle("Legit Kinematics", () -> cfg.autoSpawnerLegitMode, v -> cfg.autoSpawnerLegitMode = v, actions, false),
                toggle("Safety Guard", () -> cfg.autoSpawnerSafetyEnabled, v -> cfg.autoSpawnerSafetyEnabled = v, actions, false),
                intSlider("autoSpawnerSafetyRadius", "Safety Radius", 6, 48, "b",
                        () -> cfg.autoSpawnerSafetyRadius, v -> cfg.autoSpawnerSafetyRadius = v),
                new CycleControl("Safety Action",
                        () -> switch (cycleIndex(cfg.autoSpawnerSafetyAction, 3, 1)) {
                            case 0 -> "Pause";
                            case 2 -> "Disconnect";
                            default -> "Auto-Resume";
                        },
                        () -> {
                            cfg.autoSpawnerSafetyAction = (cycleIndex(cfg.autoSpawnerSafetyAction, 3, 1) + 1) % 3;
                            actions.persist(false);
                        }),
                intSlider("autoSpawnerSafetyResumeSec", "Safe Resume Sec", 5, 120, "s",
                        () -> cfg.autoSpawnerSafetyResumeSec, v -> cfg.autoSpawnerSafetyResumeSec = v),
                toggle("TPS Adaptive", () -> cfg.autoSpawnerTpsAdaptive, v -> cfg.autoSpawnerTpsAdaptive = v, actions, false),
                toggle("Reconnect Resume", () -> cfg.autoSpawnerAutoResume, v -> cfg.autoSpawnerAutoResume = v, actions, false)
        );
    }

    private static List<Control> buildSpawnerAutoSellControls(ModConfig cfg, Actions actions) {
        return List.of(
                new CycleControl("Target",
                        () -> cfg.spawnerAutoSellTargetGoldBlock ? "Gold Block (ON)" : "Gold Nugget (OFF)",
                        () -> {
                            cfg.spawnerAutoSellTargetGoldBlock = !cfg.spawnerAutoSellTargetGoldBlock;
                            actions.persist(false);
                        }),
                intSlider("spawnerAutoSellSearchRadius", "Search Radius", 12, 40, "b",
                        () -> cfg.spawnerAutoSellSearchRadius, v -> cfg.spawnerAutoSellSearchRadius = v),
                toggle("Lock Mouse", () -> cfg.spawnerAutoSellLockMouse, v -> cfg.spawnerAutoSellLockMouse = v, actions, false),
                toggle("Render ESP", () -> cfg.spawnerAutoSellRenderEsp, v -> cfg.spawnerAutoSellRenderEsp = v, actions, false)
        );
    }

    private static List<ChipOption> buildItemChips(
            java.util.Set<String> targetSet,
            java.util.function.Predicate<String> isEnabled,
            java.util.function.Consumer<String> onToggle
    ) {
        List<String> selectedIds = new ArrayList<>();
        if (targetSet != null) {
            selectedIds.addAll(targetSet);
            selectedIds.sort(String.CASE_INSENSITIVE_ORDER);
        }
        List<ChipOption> chips = new ArrayList<>(selectedIds.size());
        for (String id : selectedIds) {
            ItemStack icon = resolveItemStack(id);
            String label = !icon.isEmpty() ? icon.getHoverName().getString() : resolveItemShortLabel(id);
            chips.add(new ChipOption(id, label, icon, () -> isEnabled.test(id), () -> onToggle.accept(id)));
        }
        return chips;
    }

    private static ItemStack resolveItemStack(String itemId) {
        try {
            Identifier key = Identifier.withDefaultNamespace(itemId);
            if (BuiltInRegistries.ITEM.containsKey(key)) {
                Item item = BuiltInRegistries.ITEM.getValue(key);
                if (item != null && item != Items.AIR) {
                    return new ItemStack(item);
                }
            }
        } catch (Throwable ignored) {
        }
        return ItemStack.EMPTY;
    }

    private static String resolveItemShortLabel(String itemId) {
        try {
            net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.withDefaultNamespace(itemId);
            if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(key)) {
                net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(key);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    return new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
                }
            }
        } catch (Throwable ignored) {
        }
        for (AutoCrafterHandler.CraftTarget target : AutoCrafterHandler.TARGETS) {
            if (target.id().equals(itemId)) {
                return target.label();
            }
        }
        return itemId.replace('_', ' ');
    }

    private static List<Control> buildViewmodelControls(ModConfig cfg, Actions actions) {
        return List.of(
                toggle("No Haste", () -> cfg.vmNoHaste, v -> cfg.vmNoHaste = v, actions, false),
                toggle("No Equip", () -> cfg.vmNoEquip, v -> cfg.vmNoEquip = v, actions, false),
                toggle("No Bow Swing", () -> cfg.vmNoBowSwing, v -> cfg.vmNoBowSwing = v, actions, false),
                toggle("No Swing Reset", () -> cfg.vmNoSwingReset, v -> cfg.vmNoSwingReset = v, actions, false),
                toggle("Apply To Hand", () -> cfg.vmApplyToHand, v -> cfg.vmApplyToHand = v, actions, false),
                toggle("AirWalker", () -> cfg.vmAirWalker, v -> cfg.vmAirWalker = v, actions, false),
                new SliderControl("vmSwingSpeed", "Swing Speed",
                        () -> cfg.vmSwingSpeed / 100.0f,
                        pct -> cfg.vmSwingSpeed = clampInt(Math.round(pct * 100.0f), 0, 100),
                        () -> cfg.vmSwingSpeed <= 0 ? "Off" : cfg.vmSwingSpeed + "t",
                        false,
                        () -> Integer.toString(cfg.vmSwingSpeed),
                        raw -> {
                            String trimmed = raw == null ? "" : raw.trim();
                            if ("off".equalsIgnoreCase(trimmed)) {
                                cfg.vmSwingSpeed = 0;
                                return true;
                            }
                            return parseAndApplyInt(trimmed, 0, 100, v -> cfg.vmSwingSpeed = v);
                        }),
                floatSlider("itemScale", "Item Scale", 0.10f, 2.00f, 20.0f, "%.2fx", () -> cfg.itemScale, v -> cfg.itemScale = v),
                floatSlider("playerScale", "Player Scale", 0.10f, 2.00f, 20.0f, "%.2fx", () -> cfg.playerScale, v -> cfg.playerScale = v),
                floatSlider("vmOffsetX", "Offset X", -2.0f, 2.0f, 100.0f, "%.2f", () -> cfg.vmOffsetX, v -> cfg.vmOffsetX = v),
                floatSlider("vmOffsetY", "Offset Y", -2.0f, 2.0f, 100.0f, "%.2f", () -> cfg.vmOffsetY, v -> cfg.vmOffsetY = v),
                floatSlider("vmOffsetZ", "Offset Z", -2.0f, 2.0f, 100.0f, "%.2f", () -> cfg.vmOffsetZ, v -> cfg.vmOffsetZ = v),
                floatSlider("vmScaleX", "Scale X", 0.0f, 5.0f, 100.0f, "%.2fx", () -> cfg.vmScaleX, v -> cfg.vmScaleX = v),
                floatSlider("vmScaleY", "Scale Y", 0.0f, 5.0f, 100.0f, "%.2fx", () -> cfg.vmScaleY, v -> cfg.vmScaleY = v),
                floatSlider("vmScaleZ", "Scale Z", 0.0f, 5.0f, 100.0f, "%.2fx", () -> cfg.vmScaleZ, v -> cfg.vmScaleZ = v),
                floatSlider("vmRotX", "Rotation X", -180.0f, 180.0f, 2.0f, "%.1f°", () -> cfg.vmRotX, v -> cfg.vmRotX = v),
                floatSlider("vmRotY", "Rotation Y", -180.0f, 180.0f, 2.0f, "%.1f°", () -> cfg.vmRotY, v -> cfg.vmRotY = v),
                floatSlider("vmRotZ", "Rotation Z", -180.0f, 180.0f, 2.0f, "%.1f°", () -> cfg.vmRotZ, v -> cfg.vmRotZ = v),
                floatSlider("vmSwingX", "Swing X", 0.0f, 2.0f, 100.0f, "%.2fx", () -> cfg.vmSwingX, v -> cfg.vmSwingX = v),
                floatSlider("vmSwingY", "Swing Y", 0.0f, 2.0f, 100.0f, "%.2fx", () -> cfg.vmSwingY, v -> cfg.vmSwingY = v),
                floatSlider("vmSwingZ", "Swing Z", 0.0f, 2.0f, 100.0f, "%.2fx", () -> cfg.vmSwingZ, v -> cfg.vmSwingZ = v)
        );
    }

    private static List<Control> buildWingsControls(ModConfig cfg, Actions actions) {
        List<Control> controls = new ArrayList<>();
        controls.add(new CycleControl("Mode",
                () -> WingsLogic.modeName(cfg.wingsMode),
                () -> {
                    cfg.wingsMode = (WingsLogic.clampMode(cfg.wingsMode) + 1) % WingsLogic.modeCount();
                    actions.persist(false);
                }));
        controls.add(new CycleControl("Color",
                () -> {
                    int preset = WingsLogic.clampColorPreset(cfg.wingsColorPreset);
                    if (preset == WingsLogic.COLOR_CUSTOM) {
                        return "Custom " + WingsLogic.formatHexRgb(cfg.wingsCustomRgb);
                    }
                    return WingsLogic.colorPresetName(preset);
                },
                () -> {
                    cfg.wingsColorPreset = (WingsLogic.clampColorPreset(cfg.wingsColorPreset) + 1) % WingsLogic.colorCount();
                    if (cfg.wingsColorPreset != WingsLogic.COLOR_CHROMA && cfg.wingsColorPreset != WingsLogic.COLOR_CUSTOM) {
                        int presetRgb = WingsLogic.resolveRgb(cfg.wingsColorPreset, cfg.wingsCustomRgb, 0.0f, 0.0f, false);
                        float[] hsv = WingsLogic.rgbToHsv(presetRgb);
                        cfg.wingsCustomHue = hsv[0];
                        cfg.wingsCustomSat = hsv[1];
                        cfg.wingsCustomVal = Math.max(0.10f, hsv[2]);
                        cfg.wingsCustomRgb = presetRgb;
                    }
                    actions.persist(false);
                }));
        controls.add(new ColorPaletteControl(
                "wingsColorPalette",
                () -> WingsLogic.clampColorPreset(cfg.wingsColorPreset) == WingsLogic.COLOR_CHROMA,
                () -> {
                    cfg.wingsColorPreset = WingsLogic.COLOR_CHROMA;
                    actions.persist(false);
                },
                () -> WingsLogic.resolveRgb(cfg.wingsColorPreset, cfg.wingsCustomRgb, 0.0f, 0.0f, false),
                rgb -> {
                    int clean = WingsLogic.clampRgb(rgb);
                    float[] hsv = WingsLogic.rgbToHsv(clean);
                    cfg.wingsCustomHue = hsv[0];
                    cfg.wingsCustomSat = hsv[1];
                    cfg.wingsCustomVal = Math.max(0.10f, hsv[2]);
                    cfg.wingsCustomRgb = clean;
                    cfg.wingsColorPreset = WingsLogic.COLOR_CUSTOM;
                    actions.persist(false);
                },
                () -> cfg.wingsCustomHue,
                h -> {
                    cfg.wingsCustomHue = Math.max(0.0f, Math.min(1.0f, h));
                    cfg.wingsCustomRgb = WingsLogic.hsvToRgb(cfg.wingsCustomHue, cfg.wingsCustomSat, cfg.wingsCustomVal);
                    cfg.wingsColorPreset = WingsLogic.COLOR_CUSTOM;
                },
                () -> cfg.wingsCustomSat,
                s -> {
                    cfg.wingsCustomSat = Math.max(0.0f, Math.min(1.0f, s));
                    cfg.wingsCustomRgb = WingsLogic.hsvToRgb(cfg.wingsCustomHue, cfg.wingsCustomSat, cfg.wingsCustomVal);
                    cfg.wingsColorPreset = WingsLogic.COLOR_CUSTOM;
                },
                () -> cfg.wingsCustomVal,
                v -> {
                    cfg.wingsCustomVal = Math.max(0.10f, Math.min(1.0f, v));
                    cfg.wingsCustomRgb = WingsLogic.hsvToRgb(cfg.wingsCustomHue, cfg.wingsCustomSat, cfg.wingsCustomVal);
                    cfg.wingsColorPreset = WingsLogic.COLOR_CUSTOM;
                },
                () -> {
                    cfg.wingsColorPreset = WingsLogic.COLOR_CYAN;
                    cfg.wingsCustomRgb = 0x00D4FF;
                    cfg.wingsCustomHue = 0.528f;
                    cfg.wingsCustomSat = 1.00f;
                    cfg.wingsCustomVal = 1.00f;
                }
        ));
        controls.add(floatSlider("wingsLength", "Length", 0.40f, 2.50f, 20.0f, "%.2fx",
                () -> cfg.wingsLength, v -> cfg.wingsLength = WingsLogic.clampLength(v)));
        if (WingsLogic.clampMode(cfg.wingsMode) == WingsLogic.MODE_LINES) {
            controls.add(floatSlider("wingsWidth", "Width", 0.02f, 0.20f, 100.0f, "%.2f",
                    () -> cfg.wingsWidth, v -> cfg.wingsWidth = WingsLogic.clampWidth(v)));
            controls.add(intSlider("wingsLinesCount", "Lines Count", 2, 12, "",
                    () -> cfg.wingsLinesCount, v -> cfg.wingsLinesCount = WingsLogic.clampLinesCount(v)));
            controls.add(floatSlider("wingsFadeStart", "Fade Start", 0.10f, 1.00f, 20.0f, "%.2f",
                    () -> cfg.wingsFadeStart, v -> cfg.wingsFadeStart = WingsLogic.clampFadeStart(v)));
        }
        controls.add(floatSlider("wingsHeight", "Height", -0.50f, 0.60f, 20.0f, "%.2f",
                () -> cfg.wingsHeight, v -> cfg.wingsHeight = WingsLogic.clampHeight(v)));
        controls.add(floatSlider("wingsFlapSpeed", "Flap Speed", 0.0f, 3.0f, 10.0f, "%.1fx",
                () -> cfg.wingsFlapSpeed, v -> cfg.wingsFlapSpeed = WingsLogic.clampFlapSpeed(v)));
        controls.add(toggle("Show Damage", () -> cfg.wingsShowDamage, v -> cfg.wingsShowDamage = v, actions, false));
        return controls;
    }

    private static List<Control> buildXrayControls(ModConfig cfg, Actions actions) {
        return List.of(
                intSlider("xrayOpacity", "Opacity", 0, 255, "",
                        () -> cfg.xrayOpacity, v -> cfg.xrayOpacity = XrayHandler.clampOpacity(v), true),
                toggle("Exposed Only", () -> cfg.xrayExposedOnly, v -> cfg.xrayExposedOnly = v, actions, true),
                new ActionControl(
                        () -> {
                            int n = cfg.xrayBlocks != null ? cfg.xrayBlocks.size() : 0;
                            return "Select Blocks (" + n + ")...";
                        },
                        () -> {
                            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                            mc.gui.setScreen(CatalogBlockPickerScreen.forXray(mc.gui.screen(), cfg));
                        })
        );
    }

    private static final int[] ESP_COLOR_VALUES = {0x00D4FF, 0x22C55E, 0xEF4444, 0xF59E0B, 0xA855F7, 0xEC4899, 0x3B82F6, 0xFFFFFF};
    private static final String[] ESP_COLOR_NAMES = {"Cyan", "Green", "Red", "Amber", "Violet", "Pink", "Blue", "White"};

    private static int findEspColorIndex(int rgb) {
        for (int i = 0; i < ESP_COLOR_VALUES.length; i++) {
            if (ESP_COLOR_VALUES[i] == rgb) {
                return i;
            }
        }
        return 0;
    }

    private static List<Control> buildBlockEspControls(ModConfig cfg, Actions actions) {
        if (cfg.blockEspDefaultStyle == null) {
            cfg.blockEspDefaultStyle = new BlockEspHandler.BlockEspStyle();
        }
        return List.of(
                intSlider("blockEspRange", "Range", 16, 160, "b",
                        () -> cfg.blockEspRange, v -> cfg.blockEspRange = v),
                new CycleControl("Shape Mode",
                        () -> cfg.blockEspDefaultStyle.resolveShapeMode().label,
                        () -> {
                            cfg.blockEspDefaultStyle.shapeMode = cfg.blockEspDefaultStyle.resolveShapeMode().next().label;
                            actions.persist(false);
                        }),
                new CycleControl("Default Color",
                        () -> ESP_COLOR_NAMES[findEspColorIndex(cfg.blockEspDefaultStyle.rgb())],
                        () -> {
                            int next = (findEspColorIndex(cfg.blockEspDefaultStyle.rgb()) + 1) % ESP_COLOR_VALUES.length;
                            cfg.blockEspDefaultStyle.setColorRGB(ESP_COLOR_VALUES[next]);
                            actions.persist(false);
                        }),
                toggle("Tracers", () -> cfg.blockEspDefaultStyle.tracer, v -> cfg.blockEspDefaultStyle.tracer = v, actions, false),
                new ActionControl(
                        () -> {
                            int n = cfg.blockEspBlocks != null ? cfg.blockEspBlocks.size() : 0;
                            return "Select & Style Blocks (" + n + ")...";
                        },
                        () -> {
                            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                            mc.gui.setScreen(CatalogBlockPickerScreen.forBlockEsp(mc.gui.screen(), cfg));
                        })
        );
    }

    private static ToggleControl toggle(String label, BooleanSupplier getter, BooleanConsumer setter,
                                        Actions actions, boolean reloadChunks) {
        return new ToggleControl(label, getter, value -> {
            setter.accept(value);
            actions.persist(reloadChunks);
        });
    }

    private static SliderControl intSlider(String key, String label, int min, int max, String unit,
                                           java.util.function.IntSupplier getter,
                                           java.util.function.IntConsumer setter) {
        return intSlider(key, label, min, max, unit, getter, setter, false);
    }

    private static SliderControl intSlider(String key, String label, int min, int max, String unit,
                                           java.util.function.IntSupplier getter,
                                           java.util.function.IntConsumer setter,
                                           boolean needsChunkReload) {
        float span = Math.max(1, max - min);
        return new SliderControl(
                key,
                label,
                () -> (getter.getAsInt() - min) / span,
                pct -> setter.accept(clampInt(Math.round(min + pct * span), min, max)),
                () -> getter.getAsInt() + unit,
                needsChunkReload,
                () -> Integer.toString(getter.getAsInt()),
                raw -> parseAndApplyInt(raw, min, max, setter)
        );
    }

    private static SliderControl floatSlider(String key, String label, float min, float max, float stepScale, String fmt,
                                             FloatSupplier getter, FloatConsumer setter) {
        float span = Math.max(0.0001f, max - min);
        String editFmt = numericFormat(fmt);
        return new SliderControl(
                key,
                label,
                () -> Math.max(0.0f, Math.min(1.0f, (getter.get() - min) / span)),
                pct -> {
                    float raw = min + Math.max(0.0f, Math.min(1.0f, pct)) * span;
                    float rounded = roundToStep(raw, stepScale);
                    setter.accept(Math.max(min, Math.min(max, rounded)));
                },
                () -> String.format(Locale.ROOT, fmt, getter.get()),
                false,
                () -> String.format(Locale.ROOT, editFmt, getter.get()),
                raw -> parseAndApplyFloat(raw, min, max, setter)
        );
    }

    static boolean matchesSubSetting(String modName, String lowerQuery, ModConfig cfg, Actions actions) {
        if (modName == null || lowerQuery == null || lowerQuery.isEmpty()) {
            return false;
        }
        List<Control> controls = getControls(modName, cfg, actions);
        for (Control c : controls) {
            if (c instanceof ToggleControl t && t.label() != null
                    && t.label().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                return true;
            }
            if (c instanceof SliderControl s && s.label() != null
                    && s.label().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                return true;
            }
            if (c instanceof CycleControl cy && cy.label() != null
                    && cy.label().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                return true;
            }
            if (c instanceof ActionControl a && a.labelSupplier() != null) {
                String lbl = a.labelSupplier().get();
                if (lbl != null && lbl.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                    return true;
                }
            }
            if (c instanceof ChipsControl ch && ch.options() != null) {
                for (ChipOption opt : ch.options()) {
                    if (opt.label() != null && opt.label().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    static boolean isInsideSliderValueBox(int mouseX, int mouseY, int cx, int cy, int cw, int valW) {
        int hitW = Math.max(valW + 8, 28);
        int hitX = cx + cw - hitW;
        return mouseX >= hitX && mouseX <= cx + cw + 2 && mouseY >= cy - 2 && mouseY < cy + 12;
    }

    static String appendSliderInputChar(String current, boolean selectAll, char ch, int maxLength) {
        String base = selectAll || current == null ? "" : current;
        if (base.length() >= maxLength) {
            return base;
        }
        char normalized = (ch == ',') ? '.' : ch;
        if (normalized >= '0' && normalized <= '9') {
            return base + normalized;
        }
        if (normalized == '.' && base.indexOf('.') < 0) {
            return base + '.';
        }
        if (normalized == '-' && base.isEmpty()) {
            return "-";
        }
        return current == null ? "" : current;
    }

    static String sanitizeSliderInputString(String raw, int maxLength) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String out = "";
        for (int i = 0; i < raw.length(); i++) {
            out = appendSliderInputChar(out, false, raw.charAt(i), maxLength);
        }
        return out;
    }

    /**
     * Maps layout-invariant GLFW numeric/numpad key codes to slider input characters
     * as a fallback for systems where {@code charTyped} does not fire reliably.
     * Returns {@code 0} when the key is not a layout-invariant numeric/decimal/minus key.
     */
    static char mapKeyCodeToSliderChar(int keyCode, boolean shiftDown, boolean ctrlOrAltDown) {
        if (ctrlOrAltDown) {
            return 0;
        }
        if (!shiftDown && keyCode >= 48 && keyCode <= 57) {
            return (char) ('0' + (keyCode - 48));
        }
        if (keyCode >= 320 && keyCode <= 329) {
            return (char) ('0' + (keyCode - 320));
        }
        if (keyCode == 330) { // GLFW_KEY_KP_DECIMAL
            return '.';
        }
        if (keyCode == 333) { // GLFW_KEY_KP_SUBTRACT
            return '-';
        }
        return 0;
    }

    private static String numericFormat(String fmt) {
        if (fmt == null || fmt.isEmpty()) {
            return "%.2f";
        }
        String res = fmt.trim();
        if (res.endsWith("ms")) {
            res = res.substring(0, res.length() - 2);
        }
        while (!res.isEmpty()) {
            char last = res.charAt(res.length() - 1);
            if (isUnitSuffixChar(last)) {
                res = res.substring(0, res.length() - 1);
            } else {
                break;
            }
        }
        return res.isEmpty() ? "%.2f" : res;
    }

    private static boolean parseAndApplyInt(String raw, int min, int max, java.util.function.IntConsumer setter) {
        Double parsed = parseNormalizedNumber(raw);
        if (parsed == null) {
            return false;
        }
        int rounded = (int) Math.round(parsed);
        setter.accept(clampInt(rounded, min, max));
        return true;
    }

    private static boolean parseAndApplyFloat(String raw, float min, float max, FloatConsumer setter) {
        Double parsed = parseNormalizedNumber(raw);
        if (parsed == null) {
            return false;
        }
        float clamped = Math.max(min, Math.min(max, parsed.floatValue()));
        setter.accept(clamped);
        return true;
    }

    private static Double parseNormalizedNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().replace(',', '.').toLowerCase(Locale.ROOT);
        s = s.replace("ms", "");
        while (!s.isEmpty()) {
            char last = s.charAt(s.length() - 1);
            if (isUnitSuffixChar(last) || last == ' ') {
                s = s.substring(0, s.length() - 1);
            } else {
                break;
            }
        }
        if (s.isEmpty() || "-".equals(s) || ".".equals(s) || "-.".equals(s)) {
            return null;
        }
        try {
            double value = Double.parseDouble(s);
            return Double.isFinite(value) ? value : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static float roundToStep(float value, float scale) {
        return Math.round(value * scale) / scale;
    }

    private static boolean isUnitSuffixChar(char ch) {
        return ch == 'x' || ch == '°' || ch == 't' || ch == '%' || ch == 'm' || ch == 'b' || ch == 's';
    }

    private static int cycleIndex(int value, int count, int fallback) {
        return (value >= 0 && value < count) ? value : fallback;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
