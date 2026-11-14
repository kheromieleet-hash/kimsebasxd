package com.client.mod;

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

    public sealed interface Control permits SliderControl, ToggleControl, CycleControl, ActionControl, ChipsControl {
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
            BooleanSupplier activeGetter,
            Runnable onToggle
    ) {}

    public record ChipsControl(List<ChipOption> options) implements Control {
        @Override
        public int height() {
            int rows = (this.options.size() + 1) / 2;
            return Math.max(CHIP_HEIGHT, rows * CHIP_HEIGHT + Math.max(0, rows - 1) * CHIP_GAP_Y);
        }
    }

    private ModernModuleSettings() {
    }

    public static boolean hasSettings(String modName, ModConfig cfg, Actions actions) {
        return !getControls(modName, cfg, actions).isEmpty();
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
            case "Fullbright" -> buildFullbrightControls(cfg, actions);
            case "Keybinds HUD" -> buildKeybindsHudControls(cfg, actions);
            case "Item Notifier" -> buildItemNotifierControls(cfg, actions);
            case "HUD Info" -> buildHudInfoControls(cfg, actions);
            case "Fast Place" -> List.of(
                    toggle("Auto Right Click", () -> cfg.fastPlaceAutoRightClick, v -> cfg.fastPlaceAutoRightClick = v, actions, false),
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
            case "Better Chat" -> buildBetterChatControls(cfg, actions);
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

    private static List<Control> buildFullbrightControls(ModConfig cfg, Actions actions) {
        return List.of(
                new CycleControl("Mode",
                        () -> switch (cfg.fullbrightMode) {
                            case 0 -> "Natural";
                            case 1 -> "Gamma";
                            default -> "Ultra";
                        },
                        () -> {
                            cfg.fullbrightMode = (cfg.fullbrightMode + 1) % 3;
                            actions.persist(true);
                        }),
                new SliderControl("fullbrightBrightness", "Brightness",
                        () -> (cfg.fullbrightBrightness - 0.20f) / 0.80f,
                        pct -> {
                            cfg.fullbrightBrightness = roundToStep(0.20f + pct * 0.80f, 100.0f);
                            if (cfg.fullbrightMode == 0) {
                                cfg.fullbrightMode = 1;
                            }
                        },
                        () -> Math.round(cfg.fullbrightBrightness * 100.0f) + "%",
                        true,
                        () -> Integer.toString(Math.round(cfg.fullbrightBrightness * 100.0f)),
                        raw -> parseAndApplyFloat(raw, 20.0f, 100.0f, pctVal -> {
                            cfg.fullbrightBrightness = Math.round(pctVal) / 100.0f;
                            if (cfg.fullbrightMode == 0) {
                                cfg.fullbrightMode = 1;
                            }
                        })),
                toggle("3D Shading", () -> cfg.fullbright3DShading, v -> cfg.fullbright3DShading = v, actions, true),
                toggle("No Dark Corners", () -> cfg.noBlockShadows, v -> cfg.noBlockShadows = v, actions, true)
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

    private static List<Control> buildItemNotifierControls(ModConfig cfg, Actions actions) {
        return List.of(
                floatSlider("itemNotifierDuration", "Duration", 1.0f, 10.0f, 0.5f, "s",
                        () -> cfg.itemNotifierDuration, v -> cfg.itemNotifierDuration = v),
                intSlider("itemNotifierMaxItems", "Max Items", 1, 10, "",
                        () -> cfg.itemNotifierMaxItems, v -> cfg.itemNotifierMaxItems = v),
                new ActionControl(() -> "Reset Position", () -> {
                    cfg.itemNotifierX = -1;
                    cfg.itemNotifierY = -1;
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
                toggle("Show Clock", () -> cfg.hudShowClock, v -> cfg.hudShowClock = v, actions, false)
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
                toggle("Skip In Creative", () -> cfg.invDisableInCreative, v -> cfg.invDisableInCreative = v, actions, false),
                toggle("Shulker Preview", () -> cfg.invShulkerPreview, v -> cfg.invShulkerPreview = v, actions, false)
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
        List<ChipOption> chips = new ArrayList<>(AutoCrafterHandler.TARGETS.size());
        for (AutoCrafterHandler.CraftTarget target : AutoCrafterHandler.TARGETS) {
            chips.add(new ChipOption(
                    target.id(),
                    target.label(),
                    () -> cfg.isAutoCraftTargetEnabled(target.id()),
                    () -> {
                        cfg.toggleAutoCraftTarget(target.id());
                        actions.persist(false);
                    }
            ));
        }

        return List.of(
                intSlider("autoCrafterDelay", "Delay", 0, 5, "t",
                        () -> cfg.autoCrafterDelay, v -> cfg.autoCrafterDelay = v),
                toggle("Close When Done", () -> cfg.autoCrafterCloseWhenDone, v -> cfg.autoCrafterCloseWhenDone = v, actions, false),
                new ActionControl(
                        () -> {
                            boolean allSelected = cfg.autoCrafterTargets != null
                                    && cfg.autoCrafterTargets.size() >= AutoCrafterHandler.TARGETS.size();
                            return allSelected ? "Deselect All" : "Select All";
                        },
                        () -> {
                            boolean allSelected = cfg.autoCrafterTargets != null
                                    && cfg.autoCrafterTargets.size() >= AutoCrafterHandler.TARGETS.size();
                            cfg.setAllAutoCraftTargets(!allSelected);
                            actions.persist(false);
                        }),
                new ChipsControl(chips)
        );
    }

    private static List<Control> buildBetterChatControls(ModConfig cfg, Actions actions) {
        return List.of(
                toggle("Infinite History", () -> cfg.chatInfinite, v -> cfg.chatInfinite = v, actions, false),
                toggle("Anti Clear", () -> cfg.chatAntiClear, v -> cfg.chatAntiClear = v, actions, false),
                toggle("Click to Copy", () -> cfg.chatClickCopy, v -> cfg.chatClickCopy = v, actions, false),
                toggle("Timestamps", () -> cfg.chatTimestamps, v -> cfg.chatTimestamps = v, actions, false),
                toggle("Compact Chat", () -> cfg.compactChat, v -> cfg.compactChat = v, actions, false)
        );
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
                floatSlider("vmSwingZ", "Swing Z", 0.0f, 2.0f, 100.0f, "%.2fx", () -> cfg.vmSwingZ, v -> cfg.vmSwingZ = v),
                new ActionControl(() -> "Reset Defaults", () -> {
                    cfg.vmNoHaste = false;
                    cfg.vmNoEquip = false;
                    cfg.vmApplyToHand = false;
                    cfg.vmNoBowSwing = false;
                    cfg.vmNoSwingReset = false;
                    cfg.vmAirWalker = false;
                    cfg.itemScale = 1.0f;
                    cfg.playerScale = 1.0f;
                    cfg.vmSwingSpeed = 0;
                    cfg.vmOffsetX = 0.0f;
                    cfg.vmOffsetY = 0.0f;
                    cfg.vmOffsetZ = 0.0f;
                    cfg.vmScaleX = 1.0f;
                    cfg.vmScaleY = 1.0f;
                    cfg.vmScaleZ = 1.0f;
                    cfg.vmRotX = 0.0f;
                    cfg.vmRotY = 0.0f;
                    cfg.vmRotZ = 0.0f;
                    cfg.vmSwingX = 1.0f;
                    cfg.vmSwingY = 1.0f;
                    cfg.vmSwingZ = 1.0f;
                    actions.persist(false);
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
        float span = Math.max(1, max - min);
        return new SliderControl(
                key,
                label,
                () -> (getter.getAsInt() - min) / span,
                pct -> setter.accept(clampInt(Math.round(min + pct * span), min, max)),
                () -> getter.getAsInt() + unit,
                false,
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
            if (last == 'x' || last == '°' || last == 't' || last == '%') {
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
            if (last == 'x' || last == 't' || last == '%' || last == '°' || last == ' ') {
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

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
