package com.client.mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        void openInventorySettings();
        void openInventoryMove();
        void openVersionSwitcher();
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
            boolean needsChunkReload
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
            case "Dulkir" -> buildDulkirControls(cfg, actions);
            case "Inventory HUD+" -> buildInventoryHudControls(cfg, actions);
            case "Fullbright" -> buildFullbrightControls(cfg, actions);
            case "Keybinds HUD" -> buildKeybindsHudControls(cfg, actions);
            case "Fast Place" -> List.of(
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
                    toggle("Auto Right Click", () -> cfg.fastUseAutoRightClick, v -> cfg.fastUseAutoRightClick = v, actions, false),
                    intSlider("fastUseCooldown", "Cooldown", 0, 4, "t",
                            () -> cfg.fastUseCooldown, v -> cfg.fastUseCooldown = v)
            );
            case "Auto Tool" -> List.of(
                    toggle("Swap Back", () -> cfg.autoToolSwapBack, v -> cfg.autoToolSwapBack = v, actions, false)
            );
            case "Auto Crafter" -> buildAutoCrafterControls(cfg, actions);
            case "Better Chat" -> buildBetterChatControls(cfg, actions);
            case "Version Switcher" -> List.of(
                    new ActionControl(() -> "Open Menu (" + cfg.getDisplayVersion() + ")", actions::openVersionSwitcher)
            );
            default -> List.of();
        };
    }

    private static List<Control> buildDulkirControls(ModConfig cfg, Actions actions) {
        return List.of(
                new SliderControl("itemScale", "Item Scale",
                        () -> (cfg.itemScale - 0.10f) / 1.90f,
                        pct -> cfg.itemScale = roundToStep(0.10f + pct * 1.90f, 20.0f),
                        () -> String.format(Locale.ROOT, "%.2fx", cfg.itemScale),
                        false),
                new SliderControl("playerScale", "Player Scale",
                        () -> (cfg.playerScale - 0.10f) / 1.90f,
                        pct -> cfg.playerScale = roundToStep(0.10f + pct * 1.90f, 20.0f),
                        () -> String.format(Locale.ROOT, "%.2fx", cfg.playerScale),
                        false),
                intSlider("posX", "Pos X", -150, 150, "", () -> cfg.posX, v -> cfg.posX = v),
                intSlider("posY", "Pos Y", -150, 150, "", () -> cfg.posY, v -> cfg.posY = v),
                intSlider("posZ", "Pos Z", -150, 50, "", () -> cfg.posZ, v -> cfg.posZ = v),
                intSlider("swingDuration", "Swing Duration", 2, 20, "t", () -> cfg.swingDuration, v -> cfg.swingDuration = v),
                toggle("Cancel Re-Equip", () -> cfg.cancelReEquip, v -> cfg.cancelReEquip = v, actions, false),
                toggle("Rotationless Drink", () -> cfg.rotationlessDrink, v -> cfg.rotationlessDrink = v, actions, false),
                toggle("AirWalker", () -> cfg.dulkirAirWalker, v -> cfg.dulkirAirWalker = v, actions, false),
                new ActionControl(() -> "Reset Dulkir Defaults", () -> {
                    cfg.itemScale = 1.0f;
                    cfg.playerScale = 1.0f;
                    cfg.posX = 0;
                    cfg.posY = 0;
                    cfg.posZ = 0;
                    cfg.rotationX = 0;
                    cfg.rotationY = 0;
                    cfg.rotationZ = 0;
                    cfg.swingDuration = 6;
                    cfg.cancelReEquip = false;
                    cfg.rotationlessDrink = true;
                    cfg.dulkirAirWalker = false;
                    actions.persist(false);
                })
        );
    }

    private static List<Control> buildInventoryHudControls(ModConfig cfg, Actions actions) {
        InventoryBridge inv = actions.inventory();
        return List.of(
                toggle("Inventory HUD", inv::isActive, inv::setActive, actions, false),
                toggle("Armor HUD", inv::isArmor, inv::setArmor, actions, false),
                toggle("Potions HUD", inv::isPotions, inv::setPotions, actions, false),
                toggle("Mini Mode", inv::isMini, inv::setMini, actions, false),
                toggle("Vertical Mode", inv::isVert, inv::setVert, actions, false),
                new SliderControl("invHudScale", "Inv Scale",
                        () -> (cfg.invHudScale - 0.50f) / 1.50f,
                        pct -> cfg.invHudScale = roundToStep(0.50f + pct * 1.50f, 20.0f),
                        () -> String.format(Locale.ROOT, "%.2fx", cfg.invHudScale),
                        false),
                intSlider("invBgAlpha", "Bg Opacity", 0, 100, "%", inv::getBgAlpha, inv::setBgAlpha),
                new ActionControl(() -> "Open Full Settings...", actions::openInventorySettings),
                new ActionControl(() -> "Move HUD Positions...", actions::openInventoryMove)
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
                        true),
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

    private static List<Control> buildFastBreakControls(ModConfig cfg, Actions actions) {
        return List.of(
                new CycleControl("Mode",
                        () -> cfg.fastBreakMode == 1 ? "Vulcan (Bypass)" : "Vanilla (0 Delay)",
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
                                ? String.format(Locale.ROOT, "%.2fx (Safe)", Math.min(cfg.fastBreakSpeed, 1.15f))
                                : String.format(Locale.ROOT, "%.1fx", cfg.fastBreakSpeed),
                        false)
        );
    }

    private static List<Control> buildInventoryTweaksControls(ModConfig cfg, Actions actions) {
        return List.of(
                toggle("Mouse Drag Move", () -> cfg.invMouseDrag, v -> cfg.invMouseDrag = v, actions, false),
                toggle("Auto Sorting", () -> cfg.invSorting, v -> cfg.invSorting = v, actions, false),
                intSlider("invSortingDelay", "Sorting Delay", 0, 10, "t",
                        () -> cfg.invSortingDelay, v -> cfg.invSortingDelay = v),
                toggle("Protect Hotbar", () -> cfg.invProtectHotbar, v -> cfg.invProtectHotbar = v, actions, false),
                toggle("Xcarry (2x2 Crafting)", () -> cfg.invXcarry, v -> cfg.invXcarry = v, actions, false),
                toggle("Disable In Creative", () -> cfg.invDisableInCreative, v -> cfg.invDisableInCreative = v, actions, false),
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
                new SliderControl("autoCrafterDelay", "Delay",
                        () -> cfg.autoCrafterDelay / 5.0f,
                        pct -> cfg.autoCrafterDelay = clampInt(Math.round(pct * 5.0f), 0, 5),
                        () -> cfg.autoCrafterDelay == 0 ? "0t (Instant)" : (cfg.autoCrafterDelay + "t"),
                        false),
                toggle("Close When Done", () -> cfg.autoCrafterCloseWhenDone, v -> cfg.autoCrafterCloseWhenDone = v, actions, false),
                new ActionControl(
                        () -> {
                            boolean allSelected = cfg.autoCrafterTargets != null
                                    && cfg.autoCrafterTargets.size() >= AutoCrafterHandler.TARGETS.size();
                            return allSelected ? "Deselect All Targets" : "Select All Targets";
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
                false
        );
    }

    private static float roundToStep(float value, float scale) {
        return Math.round(value * scale) / scale;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
