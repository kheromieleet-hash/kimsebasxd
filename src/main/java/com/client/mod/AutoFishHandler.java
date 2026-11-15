package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * CustomFishing / Title-bar minigame Auto Fish handler:
 * - Detects the 15-cell blue/green bar and moving bobber pointer in Title/Subtitle/ActionBar packets
 * - Triggers a genuine vanilla right-click (startUseItem) the instant the bobber enters the green zone
 * - Optionally recasts the fishing rod after the minigame finishes
 */
public final class AutoFishHandler {
    private static final long SESSION_TIMEOUT_MS = 1400L;

    private record GlyphKey(int codePoint, String font, int colorRgb) {}

    private record GlyphSpan(GlyphKey key, float xStart, float xEnd, float xCenter, int rawOrder) {}

    private static boolean pendingRightClick = false;
    private static int clickCooldownTicks = 0;
    private static int recastCountdownTicks = 0;
    private static boolean minigameWasActive = false;
    private static long lastBarPacketMs = 0L;

    private static GlyphKey cachedBlueKey = null;
    private static GlyphKey cachedGreenKey = null;
    private static int maxGreenCount = 0;
    private static int baselineBlueCount = 0;
    private static float cachedGreenMinX = 0.0f;
    private static float cachedGreenMaxX = 0.0f;
    private static int cachedGreenStartIdx = -1;
    private static int cachedGreenEndIdx = -1;
    private static boolean wasInGreen = false;

    // Anti-AFK camera look-around state ("ETRAFINA BAKMADIN / KAMERANI OYNAT")
    private static boolean hasAnchor = false;
    private static float anchorYaw = 0.0f;
    private static float anchorPitch = 0.0f;
    private static int lookAroundTicks = 0;
    private static int lookAroundTotalTicks = 0;
    private static float lookSwingYaw = 18.0f;
    private static float lookSwingPitch = 4.5f;
    private static boolean recastAfterLook = false;
    private static boolean yawPixelFlip = false;

    private AutoFishHandler() {
    }

    public static boolean consumePendingRightClick() {
        if (pendingRightClick) {
            pendingRightClick = false;
            return true;
        }
        return false;
    }

    public static void onLeftWorld() {
        resetSession();
        pendingRightClick = false;
        clickCooldownTicks = 0;
        recastCountdownTicks = 0;
        minigameWasActive = false;
        hasAnchor = false;
        lookAroundTicks = 0;
        recastAfterLook = false;
    }

    public static void onChatMessage(String rawText) {
        if (rawText == null || rawText.isEmpty()) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (cfg == null || !cfg.autoFishEnabled || mc == null || mc.player == null) {
            return;
        }
        if (!isHoldingFishingRod(mc.player)) {
            return;
        }
        String norm = normalizeChatForLookup(rawText);
        if (norm.contains("etrafina bakmadin")
                || norm.contains("kamerani oynat")
                || norm.contains("kamerayi oynat")
                || (norm.contains("balikci") && (norm.contains("kamera") || norm.contains("etraf")))) {
            startLookAround(mc.player, 26, 6.5f, true);
        }
    }

    public static void onTitlesCleared() {
        if (minigameWasActive) {
            scheduleRecastIfNeeded();
        }
        resetSession();
    }

    public static void onTitlePacket(Component text) {
        processBarComponent(text);
    }

    public static void onSubtitlePacket(Component text) {
        // Only inspect subtitle as a bar if title hasn't active-locked recently
        if (System.currentTimeMillis() - lastBarPacketMs > 600L) {
            processBarComponent(text);
        }
    }

    public static void onActionBarPacket(Component text) {
        if (System.currentTimeMillis() - lastBarPacketMs > 600L) {
            processBarComponent(text);
        }
    }

    public static void tick(Minecraft mc) {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg == null || !cfg.autoFishEnabled || mc == null || mc.player == null || mc.level == null) {
            resetSession();
            pendingRightClick = false;
            clickCooldownTicks = 0;
            recastCountdownTicks = 0;
            minigameWasActive = false;
            hasAnchor = false;
            lookAroundTicks = 0;
            recastAfterLook = false;
            return;
        }
        if (mc.gui != null && mc.gui.screen() != null) {
            pendingRightClick = false;
            return;
        }

        if (!isHoldingFishingRod(mc.player)) {
            hasAnchor = false;
            lookAroundTicks = 0;
            recastAfterLook = false;
        } else if (!hasAnchor && mc.player.fishing != null) {
            anchorYaw = mc.player.getYRot();
            anchorPitch = mc.player.getXRot();
            hasAnchor = true;
        }

        if (clickCooldownTicks > 0) {
            clickCooldownTicks--;
        }

        if (lookAroundTicks > 0) {
            tickLookAround(mc, mc.player);
            return;
        }

        long now = System.currentTimeMillis();
        if (minigameWasActive && now - lastBarPacketMs > SESSION_TIMEOUT_MS) {
            minigameWasActive = false;
            resetSession();
            scheduleRecastIfNeeded();
        }

        if (recastCountdownTicks > 0) {
            recastCountdownTicks--;
            if (recastCountdownTicks == 0 && cfg.autoFishRecast && !minigameWasActive
                    && isHoldingFishingRod(mc.player) && mc.player.fishing == null) {
                pendingRightClick = true;
            }
        }
    }

    private static void scheduleRecastIfNeeded() {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg != null && cfg.autoFishEnabled && cfg.autoFishRecast) {
            int base = Math.max(8, Math.min(40, cfg.autoFishRecastDelay));
            recastCountdownTicks = base + ThreadLocalRandom.current().nextInt(5);
        }
    }

    private static void startLookAround(LocalPlayer player, int durationTicks, float maxYawDeg, boolean recastWhenDone) {
        if (!hasAnchor) {
            anchorYaw = player.getYRot();
            anchorPitch = player.getXRot();
            hasAnchor = true;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        lookSwingYaw = maxYawDeg + (float) rnd.nextDouble(-1.5, 1.5);
        lookSwingPitch = (rnd.nextBoolean() ? 1.0f : -1.0f) * (float) rnd.nextDouble(1.2, 2.4);
        lookAroundTotalTicks = Math.max(12, durationTicks);
        lookAroundTicks = lookAroundTotalTicks;
        recastAfterLook = recastWhenDone;
    }

    private static void tickLookAround(Minecraft mc, LocalPlayer player) {
        int elapsed = lookAroundTotalTicks - lookAroundTicks + 1;
        float progress = (float) elapsed / (float) lookAroundTotalTicks;

        // Gentle human-like sine wave: slight right (+yaw), slight left (-yaw), then return to exact anchorYaw/anchorPitch
        float wave = (lookAroundTicks <= 2) ? 0.0f : Mth.sin(progress * (float) (Math.PI * 2.0));
        float desiredYaw = anchorYaw + lookSwingYaw * wave;
        float desiredPitch = Mth.clamp(anchorPitch + lookSwingPitch * wave, -88.0f, 88.0f);

        float yawDiff = Mth.wrapDegrees(desiredYaw - player.getYRot());
        float pitchDiff = desiredPitch - player.getXRot();

        float gain = (lookAroundTicks == 1) ? 1.0f : 0.55f;
        float yawStep = Mth.clamp(yawDiff * gain, -8.0f, 8.0f);
        float pitchStep = Mth.clamp(pitchDiff * gain, -4.0f, 4.0f);

        double sens = (mc != null && mc.options != null) ? mc.options.sensitivity().get() : 0.5;
        double d = sens * 0.6 + 0.2;
        float gcd = (float) (d * d * d * 1.2);
        if (gcd > 1.0e-4f) {
            int yawPixels = Math.round(yawStep / gcd);
            int pitchPixels = Math.round(pitchStep / gcd);
            if (lookAroundTicks > 1) {
                if (pitchPixels != 0 && yawPixels == 0) {
                    yawPixelFlip = !yawPixelFlip;
                    yawPixels = yawPixelFlip ? 1 : -1;
                }
                if (yawPixels != 0 && pitchPixels == 0) {
                    yawPixelFlip = !yawPixelFlip;
                    pitchPixels = yawPixelFlip ? 1 : -1;
                }
            }
            yawStep = yawPixels * gcd;
            pitchStep = pitchPixels * gcd;
        }

        player.setYRot(player.getYRot() + yawStep);
        player.setXRot(Mth.clamp(player.getXRot() + pitchStep, -89.5f, 89.5f));

        lookAroundTicks--;
        if (lookAroundTicks <= 0 && recastAfterLook) {
            recastAfterLook = false;
            ModConfig cfg = ModConfig.getInstance();
            int base = (cfg != null) ? Math.max(6, Math.min(40, cfg.autoFishRecastDelay / 2)) : 8;
            recastCountdownTicks = base + ThreadLocalRandom.current().nextInt(4);
        }
    }

    private static String normalizeChatForLookup(String raw) {
        String clean = raw.replaceAll("§.", "").toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(clean.length());
        for (int i = 0; i < clean.length(); i++) {
            char c = clean.charAt(i);
            char mapped = switch (c) {
                case 'ı', 'і', 'ɪ', 'i', 'İ' -> 'i';
                case 'ü', 'ᴜ' -> 'u';
                case 'ö', 'ᴏ' -> 'o';
                case 'ş', 's', 'ꜱ' -> 's';
                case 'ç', 'ᴄ' -> 'c';
                case 'ğ', 'ɢ' -> 'g';
                case 'ᴀ' -> 'a';
                case 'ʙ' -> 'b';
                case 'ᴅ' -> 'd';
                case 'ᴇ' -> 'e';
                case 'ғ', 'ꜰ' -> 'f';
                case 'ʜ' -> 'h';
                case 'ᴊ' -> 'j';
                case 'ᴋ' -> 'k';
                case 'ʟ' -> 'l';
                case 'ᴍ' -> 'm';
                case 'ɴ' -> 'n';
                case 'ᴘ' -> 'p';
                case 'ʀ' -> 'r';
                case 'ᴛ' -> 't';
                case 'ᴠ' -> 'v';
                case 'ᴡ' -> 'w';
                case 'ʏ' -> 'y';
                case 'ᴢ' -> 'z';
                default -> c;
            };
            sb.append(mapped);
        }
        return sb.toString();
    }

    private static void resetSession() {
        cachedBlueKey = null;
        cachedGreenKey = null;
        maxGreenCount = 0;
        baselineBlueCount = 0;
        cachedGreenMinX = 0.0f;
        cachedGreenMaxX = 0.0f;
        cachedGreenStartIdx = -1;
        cachedGreenEndIdx = -1;
        wasInGreen = false;
    }

    private static boolean isHoldingFishingRod(LocalPlayer player) {
        if (player == null) {
            return false;
        }
        return player.getMainHandItem().is(Items.FISHING_ROD)
                || player.getOffhandItem().is(Items.FISHING_ROD);
    }

    private static void processBarComponent(Component text) {
        if (text == null) {
            return;
        }
        ModConfig cfg = ModConfig.getInstance();
        Minecraft mc = Minecraft.getInstance();
        if (cfg == null || !cfg.autoFishEnabled || mc == null || mc.player == null || mc.font == null) {
            return;
        }
        if (!isHoldingFishingRod(mc.player)) {
            return;
        }

        StringSplitter splitter = mc.font.getSplitter();
        List<GlyphSpan> visible = new ArrayList<>();
        float[] cursorX = new float[]{0.0f};
        int[] order = new int[]{0};

        text.getVisualOrderText().accept((index, style, codePoint) -> {
            float w = splitter.stringWidth(sink -> sink.accept(0, style, codePoint));
            float start = cursorX[0];
            float end = start + w;
            if (!Character.isWhitespace(codePoint) && w > 1.5f) {
                String fontId = style.getFont() != null ? style.getFont().toString() : "";
                int color = style.getColor() != null ? style.getColor().getValue() : -1;
                GlyphKey key = new GlyphKey(codePoint, fontId, color);
                visible.add(new GlyphSpan(key, start, end, start + w * 0.5f, order[0]++));
            }
            cursorX[0] = end;
            return true;
        });

        // A 15-slot fishing bar has at least 12 visible glyphs (endcaps + 15 slots)
        if (visible.size() < 11) {
            return;
        }

        List<GlyphSpan> byX = new ArrayList<>(visible);
        byX.sort(Comparator.comparingDouble(GlyphSpan::xCenter));

        // Count overall frequencies to see if first/last glyphs are endcaps
        Map<GlyphKey, Integer> totalCounts = new HashMap<>();
        for (GlyphSpan span : byX) {
            totalCounts.merge(span.key(), 1, Integer::sum);
        }

        GlyphKey firstKey = byX.get(0).key();
        GlyphKey lastKey = byX.get(byX.size() - 1).key();
        boolean leftIsCap = totalCounts.getOrDefault(firstKey, 0) == 1 || firstKey.equals(lastKey);
        boolean rightIsCap = totalCounts.getOrDefault(lastKey, 0) == 1 || firstKey.equals(lastKey);
        int startIdx = leftIsCap ? 1 : 0;
        int endIdx = rightIsCap ? byX.size() - 1 : byX.size();
        if (endIdx - startIdx < 9) {
            return;
        }
        List<GlyphSpan> inner = byX.subList(startIdx, endIdx);

        Map<GlyphKey, Integer> innerCounts = new HashMap<>();
        for (GlyphSpan span : inner) {
            innerCounts.merge(span.key(), 1, Integer::sum);
        }

        // Find the top two cell glyph candidates (count >= 2) and their [minIdx..maxIdx] spans
        List<Map.Entry<GlyphKey, Integer>> candidates = new ArrayList<>();
        for (Map.Entry<GlyphKey, Integer> entry : innerCounts.entrySet()) {
            if (entry.getValue() >= 2) {
                candidates.add(entry);
            }
        }
        if (candidates.isEmpty()) {
            return;
        }
        candidates.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        GlyphKey blueKey;
        GlyphKey greenKey = null;
        if (candidates.size() >= 2) {
            GlyphKey k1 = candidates.get(0).getKey();
            int c1 = candidates.get(0).getValue();
            int span1 = computeIndexSpan(inner, k1);

            GlyphKey k2 = candidates.get(1).getKey();
            int c2 = candidates.get(1).getValue();
            int span2 = computeIndexSpan(inner, k2);

            // Green target zone is a single contiguous block (3..7 slots, span <= 8),
            // whereas Blue background wraps around it or has the larger span/count.
            if (span1 > span2 && span2 <= 8) {
                blueKey = k1;
                greenKey = k2;
            } else if (span2 > span1 && span1 <= 8) {
                blueKey = k2;
                greenKey = k1;
            } else if (c1 >= c2) {
                blueKey = k1;
                greenKey = k2;
            } else {
                blueKey = k2;
                greenKey = k1;
            }
        } else {
            blueKey = candidates.get(0).getKey();
            if (cachedGreenKey != null && !cachedGreenKey.equals(blueKey)) {
                greenKey = cachedGreenKey;
            }
        }

        int blueCount = innerCounts.getOrDefault(blueKey, 0);
        int greenCount = greenKey != null ? innerCounts.getOrDefault(greenKey, 0) : 0;
        if (blueCount < 4) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastBarPacketMs > SESSION_TIMEOUT_MS) {
            resetSession();
        }
        lastBarPacketMs = now;
        minigameWasActive = true;
        recastCountdownTicks = 0;

        if (greenKey != null && greenCount >= 2) {
            float gMinX = Float.POSITIVE_INFINITY;
            float gMaxX = Float.NEGATIVE_INFINITY;
            int gStart = Integer.MAX_VALUE;
            int gEnd = Integer.MIN_VALUE;
            for (int i = 0; i < inner.size(); i++) {
                GlyphSpan s = inner.get(i);
                if (s.key().equals(greenKey)) {
                    gMinX = Math.min(gMinX, s.xStart());
                    gMaxX = Math.max(gMaxX, s.xEnd());
                    gStart = Math.min(gStart, i);
                    gEnd = Math.max(gEnd, i);
                }
            }

            boolean zoneMoved = cachedGreenStartIdx != -1 && (gStart > cachedGreenEndIdx || gEnd < cachedGreenStartIdx);
            if (cachedGreenKey == null || !greenKey.equals(cachedGreenKey) || zoneMoved) {
                cachedBlueKey = blueKey;
                cachedGreenKey = greenKey;
                maxGreenCount = greenCount;
                baselineBlueCount = blueCount;
                cachedGreenMinX = gMinX;
                cachedGreenMaxX = gMaxX;
                cachedGreenStartIdx = gStart;
                cachedGreenEndIdx = gEnd;
                wasInGreen = false;
            } else if (greenCount >= maxGreenCount) {
                maxGreenCount = greenCount;
                baselineBlueCount = blueCount;
                cachedGreenMinX = gMinX;
                cachedGreenMaxX = gMaxX;
                cachedGreenStartIdx = gStart;
                cachedGreenEndIdx = gEnd;
            }
        }

        if (cachedGreenStartIdx == -1 || cachedGreenKey == null) {
            return;
        }

        // Find the moving Bobber glyph inside inner slots
        GlyphSpan bobberSpan = null;
        int bobberIdx = -1;
        for (int i = 0; i < inner.size(); i++) {
            GlyphSpan s = inner.get(i);
            if (!s.key().equals(blueKey) && !s.key().equals(cachedGreenKey)) {
                bobberSpan = s;
                bobberIdx = i;
                break;
            }
        }

        boolean condIndex = bobberSpan != null
                && bobberIdx >= cachedGreenStartIdx
                && bobberIdx <= cachedGreenEndIdx;
        float cellWidth = bobberSpan != null ? Math.max(2.0f, bobberSpan.xEnd() - bobberSpan.xStart()) : 6.0f;
        boolean condPixel = bobberSpan != null
                && bobberSpan.xCenter() >= cachedGreenMinX - cellWidth * 0.20f
                && bobberSpan.xCenter() <= cachedGreenMaxX + cellWidth * 0.20f;
        boolean condReplaced = maxGreenCount >= 2
                && greenCount == maxGreenCount - 1
                && blueCount == baselineBlueCount + 1;

        boolean inGreen = condIndex || condPixel || condReplaced;
        if (inGreen) {
            if (!wasInGreen || clickCooldownTicks <= 0) {
                pendingRightClick = true;
                clickCooldownTicks = Math.max(1, cfg.autoFishClickDelay);
            }
            wasInGreen = true;
        } else {
            wasInGreen = false;
        }
    }

    private static int computeIndexSpan(List<GlyphSpan> inner, GlyphKey key) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < inner.size(); i++) {
            if (inner.get(i).key().equals(key)) {
                min = Math.min(min, i);
                max = Math.max(max, i);
            }
        }
        return min <= max ? (max - min + 1) : 0;
    }
}
