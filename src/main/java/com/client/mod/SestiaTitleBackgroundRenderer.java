package com.client.mod;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.Random;

/**
 * Renders Sestia Client's pure pitch-black background with small, organically swaying,
 * rotating, and fluttering emerald hemp leaves that begin falling gradually from top.
 */
public final class SestiaTitleBackgroundRenderer {

    private static final Identifier LEAF_TEX =
            Identifier.fromNamespaceAndPath("sestia", "textures/gui/title_leaf.png");
    private static final Identifier LEAF_MASK_TEX =
            Identifier.fromNamespaceAndPath("sestia", "textures/gui/title_leaf_mask.png");

    static final int LEAF_COUNT = 130;
    static final int MAX_LEAF_COUNT = 195;
    private static final long REENTRY_RESET_NANOS = 5_000_000_000L;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    // Pre-allocated flat primitive arrays (stratified far -> mid -> near painter's order, zero per-frame allocation)
    private static final float[] X = new float[MAX_LEAF_COUNT];
    private static final float[] Y = new float[MAX_LEAF_COUNT];
    private static final float[] DELAY = new float[MAX_LEAF_COUNT];
    private static final float[] AGE = new float[MAX_LEAF_COUNT];
    private static final float[] SIZE = new float[MAX_LEAF_COUNT];
    private static final float[] VY = new float[MAX_LEAF_COUNT];
    private static final float[] VX = new float[MAX_LEAF_COUNT];
    private static final float[] SWAY_AMP = new float[MAX_LEAF_COUNT];
    private static final float[] SWAY_FREQ = new float[MAX_LEAF_COUNT];
    private static final float[] SWAY_PHASE = new float[MAX_LEAF_COUNT];
    private static final float[] BASE_ANGLE = new float[MAX_LEAF_COUNT];
    private static final float[] SPIN_SPEED = new float[MAX_LEAF_COUNT];
    private static final float[] TILT_AMP = new float[MAX_LEAF_COUNT];
    private static final float[] FLUTTER_FREQ = new float[MAX_LEAF_COUNT];
    private static final float[] ALPHA = new float[MAX_LEAF_COUNT];
    private static final float[] PUSH_X = new float[MAX_LEAF_COUNT];
    private static final float[] PUSH_Y = new float[MAX_LEAF_COUNT];
    private static final byte[] TIER = new byte[MAX_LEAF_COUNT];

    private static final Random RUNTIME_RNG = new Random(26308L);
    private static boolean initialized = false;
    private static long lastRenderNanos = 0L;
    private static int lastWidth = 0;
    private static int lastHeight = 0;
    private static float pointerX = 0.0f;
    private static float pointerY = 0.0f;
    private static float smoothPointerX = 0.0f;
    private static float smoothPointerY = 0.0f;
    private static float prevSmoothPointerX = 0.0f;
    private static float prevSmoothPointerY = 0.0f;
    private static boolean pointerActive = false;

    // Dedicated zero-allocation ambient leaf state for ClickGUI windows (isolated from title-screen state)
    static final int GUI_LEAF_COUNT = 28;
    static final int MAX_GUI_LEAF_COUNT = 42;
    private static final float GUI_EDGE_FADE_PX = 14.0f;
    private static final float[] GUI_X = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_Y = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_AGE = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_SIZE = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_VY = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_VX = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_SWAY_AMP = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_SWAY_FREQ = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_SWAY_PHASE = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_BASE_ANGLE = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_SPIN_SPEED = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_TILT_AMP = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_FLUTTER_FREQ = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_ALPHA = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_PUSH_X = new float[MAX_GUI_LEAF_COUNT];
    private static final float[] GUI_PUSH_Y = new float[MAX_GUI_LEAF_COUNT];
    private static final Random GUI_RNG = new Random(26329L);
    private static boolean guiInitialized = false;
    private static long guiLastRenderNanos = 0L;
    private static int guiLastWidth = 0;
    private static int guiLastHeight = 0;
    private static float guiPointerX = 0.0f;
    private static float guiPointerY = 0.0f;
    private static float guiSmoothX = 0.0f;
    private static float guiSmoothY = 0.0f;
    private static float guiPrevSmoothX = 0.0f;
    private static float guiPrevSmoothY = 0.0f;
    private static boolean guiPointerActive = false;

    private SestiaTitleBackgroundRenderer() {
    }

    public static Identifier getLeafMaskTexture() {
        return LEAF_MASK_TEX;
    }

    static int resolveActiveLeafCount(float density) {
        float clamped = Float.isFinite(density) ? clampRange(density, 0.25f, 1.50f) : 1.0f;
        return Math.max(1, Math.min(MAX_LEAF_COUNT, Math.round(LEAF_COUNT * clamped)));
    }

    static int resolveActiveGuiLeafCount(float density) {
        float clamped = Float.isFinite(density) ? clampRange(density, 0.25f, 1.50f) : 1.0f;
        return Math.max(1, Math.min(MAX_GUI_LEAF_COUNT, Math.round(GUI_LEAF_COUNT * clamped)));
    }

    static float resolveSpeedMultiplier(float speed) {
        return Float.isFinite(speed) ? clampRange(speed, 0.40f, 2.00f) : 1.0f;
    }

    static boolean isMenuLeavesEnabled(int leafMode) {
        return leafMode >= 1;
    }

    static boolean isGuiLeavesEnabled(int leafMode) {
        return leafMode >= 2;
    }

    private static ModConfig tryGetConfig() {
        try {
            return ModConfig.getInstance();
        } catch (Throwable ignored) {
            return null;
        }
    }

    /**
     * Updates fullscreen cursor coordinates for gentle breeze impulse and depth parallax.
     */
    public static void updatePointer(float mouseX, float mouseY, int width, int height) {
        if (!Float.isFinite(mouseX) || !Float.isFinite(mouseY) || width <= 0 || height <= 0
                || mouseX < 0.0f || mouseY < 0.0f || mouseX > width || mouseY > height) {
            pointerActive = false;
            return;
        }
        pointerX = clampRange(mouseX, 0.0f, width);
        pointerY = clampRange(mouseY, 0.0f, height);
        if (!pointerActive) {
            smoothPointerX = pointerX;
            smoothPointerY = pointerY;
            prevSmoothPointerX = pointerX;
            prevSmoothPointerY = pointerY;
            pointerActive = true;
        }
    }

    static void setGuiPointer(float mouseX, float mouseY, int width, int height) {
        if (!Float.isFinite(mouseX) || !Float.isFinite(mouseY) || width <= 0 || height <= 0
                || mouseX < 0.0f || mouseY < 0.0f || mouseX > width || mouseY > height) {
            guiPointerActive = false;
            return;
        }
        guiPointerX = clampRange(mouseX, 0.0f, width);
        guiPointerY = clampRange(mouseY, 0.0f, height);
        if (!guiPointerActive) {
            guiSmoothX = guiPointerX;
            guiSmoothY = guiPointerY;
            guiPrevSmoothX = guiPointerX;
            guiPrevSmoothY = guiPointerY;
            guiPointerActive = true;
        }
    }

    static void clearPointer() {
        pointerActive = false;
        guiPointerActive = false;
    }

    /**
     * Resets all leaves above the top edge so the screen starts 100% pure black and leaves trickle in gradually.
     */
    static void resetToBlackStart(int width, int height, long seed) {
        Random rng = new Random(seed);
        lastWidth = Math.max(1, width);
        lastHeight = Math.max(1, height);

        for (int i = 0; i < MAX_LEAF_COUNT; i++) {
            configureTier(i, rng);
            randomizeHorizontalAndMotion(i, lastWidth, rng);
            Y[i] = -SIZE[i] * 0.58f - 2.0f;
            AGE[i] = 0.0f;
            PUSH_X[i] = 0.0f;
            PUSH_Y[i] = 0.0f;

            if (i < LEAF_COUNT) {
                float t = i / (float) (LEAF_COUNT - 1);
                DELAY[i] = Math.max(0.16f, 0.16f + t * 7.2f + (rng.nextFloat() - 0.5f) * 0.14f);
            } else {
                float t = (i - LEAF_COUNT) / (float) Math.max(1, MAX_LEAF_COUNT - LEAF_COUNT - 1);
                DELAY[i] = Math.max(0.35f, 0.35f + t * 6.8f + (rng.nextFloat() - 0.5f) * 0.14f);
            }
        }

        // Shuffle entry delays within baseline and extended pools so 1.0x timing stays consistent
        for (int i = LEAF_COUNT - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            float tmp = DELAY[i];
            DELAY[i] = DELAY[j];
            DELAY[j] = tmp;
        }
        for (int i = MAX_LEAF_COUNT - 1; i > LEAF_COUNT; i--) {
            int j = LEAF_COUNT + rng.nextInt(i - LEAF_COUNT + 1);
            float tmp = DELAY[i];
            DELAY[i] = DELAY[j];
            DELAY[j] = tmp;
        }

        initialized = true;
    }

    private static void configureTier(int i, Random rng) {
        int m = i % 10;
        if (m < 4) {
            TIER[i] = 0; // Far tier (smallest, slowest, dimmest)
            SIZE[i] = 20.0f + rng.nextFloat() * 8.0f;
            VY[i] = 18.0f + rng.nextFloat() * 7.0f;
            ALPHA[i] = 0.38f + rng.nextFloat() * 0.18f;
        } else if (m < 7) {
            TIER[i] = 1; // Mid tier
            SIZE[i] = 27.0f + rng.nextFloat() * 8.0f;
            VY[i] = 23.0f + rng.nextFloat() * 9.0f;
            ALPHA[i] = 0.56f + rng.nextFloat() * 0.18f;
        } else {
            TIER[i] = 2; // Near tier
            SIZE[i] = 34.0f + rng.nextFloat() * 10.0f;
            VY[i] = 28.0f + rng.nextFloat() * 12.0f;
            ALPHA[i] = 0.76f + rng.nextFloat() * 0.18f;
        }
        SWAY_AMP[i] = 10.0f + rng.nextFloat() * 16.0f;
        SWAY_FREQ[i] = 0.32f + rng.nextFloat() * 0.40f;
        TILT_AMP[i] = 0.22f + rng.nextFloat() * 0.26f;
        FLUTTER_FREQ[i] = 0.45f + rng.nextFloat() * 0.50f;
    }

    private static void randomizeHorizontalAndMotion(int i, int width, Random rng) {
        // 10 stratified horizontal lanes prevent blank black vertical gaps while preserving random jitter
        X[i] = ((i % 10) + rng.nextFloat()) * (Math.max(1, width) / 10.0f);
        VX[i] = -9.0f + rng.nextFloat() * 21.0f;
        SWAY_PHASE[i] = rng.nextFloat() * TWO_PI;
        BASE_ANGLE[i] = (rng.nextFloat() - 0.5f) * TWO_PI;
        SPIN_SPEED[i] = (rng.nextFloat() - 0.5f) * 1.10f;
    }

    /**
     * Advances simulation by a clamped delta step without per-frame allocations.
     */
    static void stepSimulation(float rawDt, int width, int height) {
        stepSimulation(rawDt, width, height, 1.0f);
    }

    static void stepSimulation(float rawDt, int width, int height, float speedMultiplier) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (lastWidth > 0 && lastWidth != width) {
            float sx = width / (float) lastWidth;
            for (int i = 0; i < MAX_LEAF_COUNT; i++) {
                X[i] *= sx;
            }
        }
        lastWidth = width;
        lastHeight = height;

        float baseDt = Float.isFinite(rawDt) ? Math.max(0.0f, Math.min(0.1f, rawDt)) : 0.0f;
        if (baseDt <= 0.0f) {
            return;
        }
        float dt = baseDt * resolveSpeedMultiplier(speedMultiplier);

        if (pointerActive) {
            float follow = Math.min(1.0f, baseDt * 12.0f);
            smoothPointerX += (pointerX - smoothPointerX) * follow;
            smoothPointerY += (pointerY - smoothPointerY) * follow;
            float mvx = clampRange((smoothPointerX - prevSmoothPointerX) / baseDt, -600.0f, 600.0f);
            float mvy = clampRange((smoothPointerY - prevSmoothPointerY) / baseDt, -600.0f, 600.0f);
            prevSmoothPointerX = smoothPointerX;
            prevSmoothPointerY = smoothPointerY;
            applyBreezeImpulse(X, Y, PUSH_X, PUSH_Y, DELAY, MAX_LEAF_COUNT, smoothPointerX, smoothPointerY, mvx, mvy, 76.0f, 28.0f, dt);
        }

        for (int i = 0; i < MAX_LEAF_COUNT; i++) {
            if (DELAY[i] > 0.0f) {
                DELAY[i] -= dt;
                if (DELAY[i] > 0.0f) {
                    continue;
                }
                dtStepLeaf(i, -DELAY[i], width, height);
                DELAY[i] = 0.0f;
            } else {
                dtStepLeaf(i, dt, width, height);
            }
        }

        integrateAndDampPush(X, Y, SIZE, PUSH_X, PUSH_Y, DELAY, MAX_LEAF_COUNT, width, dt);
    }

    private static void dtStepLeaf(int i, float dt, int width, int height) {
        AGE[i] += dt;
        Y[i] += VY[i] * dt;
        X[i] = wrapHorizontal(X[i] + VX[i] * dt, SIZE[i], width);

        if (Y[i] > height + SIZE[i] * 0.65f + 4.0f) {
            randomizeHorizontalAndMotion(i, width, RUNTIME_RNG);
            Y[i] = -SIZE[i] * 0.58f - 4.0f - RUNTIME_RNG.nextFloat() * 10.0f;
            AGE[i] = 0.0f;
            DELAY[i] = RUNTIME_RNG.nextFloat() * 0.16f;
            PUSH_X[i] = 0.0f;
            PUSH_Y[i] = 0.0f;
        }
    }

    private static void applyBreezeImpulse(
            float[] xArr,
            float[] yArr,
            float[] pushX,
            float[] pushY,
            float[] delayOrNull,
            int count,
            float mx,
            float my,
            float mvx,
            float mvy,
            float radius,
            float strength,
            float dt
    ) {
        float radiusSq = radius * radius;
        for (int i = 0; i < count; i++) {
            if (delayOrNull != null && delayOrNull[i] > 0.0f) {
                continue;
            }
            float dx = xArr[i] - mx;
            float dy = yArr[i] - my;
            float distSq = dx * dx + dy * dy;
            if (distSq >= radiusSq) {
                continue;
            }
            float nx;
            float ny;
            float falloff;
            if (distSq > 1.0e-4f) {
                float dist = (float) Math.sqrt(distSq);
                falloff = 1.0f - dist / radius;
                nx = dx / dist;
                ny = dy / dist;
            } else {
                falloff = 1.0f;
                nx = (i & 1) == 0 ? 0.94f : -0.94f;
                ny = -0.34f;
            }
            float impX = (nx * strength + mvx * 0.18f) * falloff * dt * 10.0f;
            float impY = (ny * strength + mvy * 0.18f) * falloff * dt * 10.0f;
            pushX[i] = clampRange(pushX[i] + impX, -120.0f, 120.0f);
            pushY[i] = clampRange(pushY[i] + impY, -120.0f, 120.0f);
        }
    }

    private static void integrateAndDampPush(
            float[] xArr,
            float[] yArr,
            float[] sizeArr,
            float[] pushX,
            float[] pushY,
            float[] delayOrNull,
            int count,
            int width,
            float dt
    ) {
        float damp = Math.max(0.0f, 1.0f - 3.4f * dt);
        for (int i = 0; i < count; i++) {
            if (delayOrNull != null && delayOrNull[i] > 0.0f) {
                continue;
            }
            if (Math.abs(pushX[i]) > 1.0e-3f || Math.abs(pushY[i]) > 1.0e-3f) {
                xArr[i] = wrapHorizontal(xArr[i] + pushX[i] * dt, sizeArr[i], width);
                yArr[i] += pushY[i] * dt;
                pushX[i] *= damp;
                pushY[i] *= damp;
            } else {
                pushX[i] = 0.0f;
                pushY[i] = 0.0f;
            }
        }
    }

    private static float computeParallaxOffset(float pointerCoord, int span, float maxShift) {
        if (span <= 0 || maxShift <= 0.0f) {
            return 0.0f;
        }
        float norm = clampRange((pointerCoord / span - 0.5f) * 2.0f, -1.0f, 1.0f);
        return -norm * maxShift;
    }

    private static float getTitleParallaxRange(int i) {
        if (TIER[i] == 0) {
            return 4.0f;
        }
        if (TIER[i] == 1) {
            return 8.0f;
        }
        return 13.0f;
    }

    private static float getGuiParallaxRange(int i) {
        return 3.0f + clamp01((GUI_SIZE[i] - 14.0f) / 12.0f) * 4.0f;
    }

    private static float wrapHorizontal(float x, float pad, int width) {
        float wrapSpan = width + 2.0f * pad;
        if (x < -pad) {
            return x + wrapSpan;
        } else if (x > width + pad) {
            return x - wrapSpan;
        }
        return x;
    }

    private static float clampRange(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private static float computeSwayedX(float x, float age, float swayAmp, float swayFreq, float swayPhase) {
        return x + (float) Math.sin(age * TWO_PI * swayFreq + swayPhase) * swayAmp;
    }

    private static float computeLeafAngle(float baseAngle, float spinSpeed, float age, float tiltAmp, float swayFreq, float swayPhase) {
        return baseAngle + spinSpeed * age
                + tiltAmp * (float) Math.sin(age * TWO_PI * swayFreq + swayPhase * 0.7f);
    }

    private static float computeLeafFlutterScaleX(float age, float flutterFreq, float swayPhase) {
        return 0.70f + 0.30f * (float) Math.cos(age * TWO_PI * flutterFreq + swayPhase);
    }

    private static void drawLeafQuad(
            GuiGraphicsExtractor graphics,
            float drawX,
            float drawY,
            float angle,
            float scaleX,
            float size,
            int alphaByte
    ) {
        int szInt = Math.max(1, Math.round(size));
        int half = szInt / 2;
        boolean useBaseEmerald = !JelloTheme.isLeafTheme();
        Identifier tex = useBaseEmerald ? LEAF_TEX : LEAF_MASK_TEX;
        int tintRgb = useBaseEmerald ? 0x00FFFFFF : (JelloTheme.getLeafTintRgb() & 0x00FFFFFF);
        int color = (alphaByte << 24) | tintRgb;

        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(drawX, drawY);
            graphics.pose().rotate(angle);
            graphics.pose().scale(scaleX, 1.0f);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    tex,
                    -half, -half,
                    0.0f, 0.0f,
                    szInt, szInt,
                    szInt, szInt,
                    color
            );
        } finally {
            graphics.pose().popMatrix();
        }
    }

    static int countVisibleLeaves(int height) {
        int visible = 0;
        for (int i = 0; i < LEAF_COUNT; i++) {
            float half = SIZE[i] * 0.5f;
            if (DELAY[i] <= 0.0f && Y[i] + half > 0.0f && Y[i] - half < height) {
                visible++;
            }
        }
        return visible;
    }

    static float getLeafX(int i) {
        return X[i];
    }

    static float getLeafY(int i) {
        return Y[i];
    }

    static float getLeafSize(int i) {
        return SIZE[i];
    }

    static float getLeafPushX(int i) {
        return PUSH_X[i];
    }

    static float getLeafPushY(int i) {
        return PUSH_Y[i];
    }

    static float computeDrawX(int i) {
        float px = pointerActive ? computeParallaxOffset(smoothPointerX, lastWidth, getTitleParallaxRange(i)) : 0.0f;
        return computeSwayedX(X[i], AGE[i], SWAY_AMP[i], SWAY_FREQ[i], SWAY_PHASE[i]) + px;
    }

    static float computeDrawY(int i) {
        float py = pointerActive ? computeParallaxOffset(smoothPointerY, lastHeight, getTitleParallaxRange(i) * 0.75f) : 0.0f;
        return Y[i] + py;
    }

    static float computeAngle(int i) {
        return computeLeafAngle(BASE_ANGLE[i], SPIN_SPEED[i], AGE[i], TILT_AMP[i], SWAY_FREQ[i], SWAY_PHASE[i]);
    }

    static float computeFlutterScaleX(int i) {
        return computeLeafFlutterScaleX(AGE[i], FLUTTER_FREQ[i], SWAY_PHASE[i]);
    }

    static int computeLeafAlpha(int i, int height, boolean isTitleScreen) {
        float fadeIn = clamp01(AGE[i] / 0.45f);
        float bottomStart = Math.max(0.0f, height - SIZE[i] * 0.5f);
        float bottomFade = 1.0f;
        if (Y[i] > bottomStart && SIZE[i] > 0.0f) {
            bottomFade = clamp01(1.0f - (Y[i] - bottomStart) / (SIZE[i] * 1.15f));
        }
        float screenMul = isTitleScreen ? 1.0f : 0.55f;
        int a = Math.round(ALPHA[i] * fadeIn * bottomFade * screenMul * 255.0f);
        return Math.max(0, Math.min(255, a));
    }

    /**
     * Renders pure black backdrop and gradually falling, randomly swaying emerald hemp leaves.
     */
    public static void render(GuiGraphicsExtractor graphics, int width, int height, boolean isTitleScreen) {
        if (graphics == null || width <= 0 || height <= 0) {
            return;
        }

        // Pure pitch-black background matching Sestia OLED style
        graphics.fill(0, 0, width, height, 0xFF000000);

        ModConfig cfg = tryGetConfig();
        int leafMode = cfg != null ? cfg.leafMode : 2;
        if (!isMenuLeavesEnabled(leafMode)) {
            return;
        }
        int activeCount = cfg != null ? resolveActiveLeafCount(cfg.leafDensity) : LEAF_COUNT;
        float speedMul = cfg != null ? resolveSpeedMultiplier(cfg.leafSpeed) : 1.0f;

        long now = System.nanoTime();
        if (!initialized || lastRenderNanos == 0L || (now - lastRenderNanos) > REENTRY_RESET_NANOS) {
            resetToBlackStart(width, height, 26308L);
            lastRenderNanos = now;
            return;
        }

        float dt = (now - lastRenderNanos) * 1.0e-9f;
        lastRenderNanos = now;
        stepSimulation(dt, width, height, speedMul);

        for (int tier = 0; tier < 3; tier++) {
            for (int i = 0; i < activeCount; i++) {
                if (TIER[i] != tier || DELAY[i] > 0.0f) {
                    continue;
                }
                float sz = SIZE[i];
                float drawY = computeDrawY(i);
                if (drawY + sz < -4.0f || drawY - sz > height + 4.0f) {
                    continue;
                }
                int alphaByte = computeLeafAlpha(i, height, isTitleScreen);
                if (alphaByte <= 1) {
                    continue;
                }

                drawLeafQuad(graphics, computeDrawX(i), drawY, computeAngle(i), computeFlutterScaleX(i), sz, alphaByte);
            }
        }
    }

    /**
     * Pre-seeds subtle drifting leaves across the ClickGUI window so leaves are immediately visible behind glass cards.
     */
    static void resetGuiAmbientLeaves(int width, int height, long seed) {
        Random rng = new Random(seed);
        guiLastWidth = Math.max(1, width);
        guiLastHeight = Math.max(1, height);

        for (int i = 0; i < MAX_GUI_LEAF_COUNT; i++) {
            GUI_SIZE[i] = 14.0f + rng.nextFloat() * 12.0f;
            GUI_VY[i] = 12.0f + rng.nextFloat() * 12.0f;
            GUI_VX[i] = -6.0f + rng.nextFloat() * 14.0f;
            GUI_ALPHA[i] = 0.22f + rng.nextFloat() * 0.22f;
            GUI_SWAY_AMP[i] = 7.0f + rng.nextFloat() * 8.0f;
            GUI_SWAY_FREQ[i] = 0.28f + rng.nextFloat() * 0.34f;
            GUI_SWAY_PHASE[i] = rng.nextFloat() * TWO_PI;
            GUI_BASE_ANGLE[i] = (rng.nextFloat() - 0.5f) * TWO_PI;
            GUI_SPIN_SPEED[i] = (rng.nextFloat() - 0.5f) * 0.85f;
            GUI_TILT_AMP[i] = 0.18f + rng.nextFloat() * 0.22f;
            GUI_FLUTTER_FREQ[i] = 0.40f + rng.nextFloat() * 0.45f;
            GUI_X[i] = ((i % 7) + rng.nextFloat()) * (guiLastWidth / 7.0f);
            GUI_Y[i] = rng.nextFloat() * guiLastHeight;
            GUI_AGE[i] = 1.0f + rng.nextFloat() * 8.0f;
            GUI_PUSH_X[i] = 0.0f;
            GUI_PUSH_Y[i] = 0.0f;
        }

        guiInitialized = true;
    }

    static void stepGuiAmbientLeaves(float rawDt, int width, int height) {
        stepGuiAmbientLeaves(rawDt, width, height, 1.0f);
    }

    static void stepGuiAmbientLeaves(float rawDt, int width, int height, float speedMultiplier) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (guiLastWidth > 0 && guiLastWidth != width) {
            float sx = width / (float) guiLastWidth;
            for (int i = 0; i < MAX_GUI_LEAF_COUNT; i++) {
                GUI_X[i] *= sx;
            }
        }
        if (guiLastHeight > 0 && guiLastHeight != height) {
            float sy = height / (float) guiLastHeight;
            for (int i = 0; i < MAX_GUI_LEAF_COUNT; i++) {
                GUI_Y[i] *= sy;
            }
        }
        guiLastWidth = width;
        guiLastHeight = height;

        float baseDt = Float.isFinite(rawDt) ? Math.max(0.0f, Math.min(0.1f, rawDt)) : 0.0f;
        if (baseDt <= 0.0f) {
            return;
        }
        float dt = baseDt * resolveSpeedMultiplier(speedMultiplier);

        if (guiPointerActive) {
            float follow = Math.min(1.0f, baseDt * 12.0f);
            guiSmoothX += (guiPointerX - guiSmoothX) * follow;
            guiSmoothY += (guiPointerY - guiSmoothY) * follow;
            float mvx = clampRange((guiSmoothX - guiPrevSmoothX) / baseDt, -600.0f, 600.0f);
            float mvy = clampRange((guiSmoothY - guiPrevSmoothY) / baseDt, -600.0f, 600.0f);
            guiPrevSmoothX = guiSmoothX;
            guiPrevSmoothY = guiSmoothY;
            applyBreezeImpulse(GUI_X, GUI_Y, GUI_PUSH_X, GUI_PUSH_Y, null, MAX_GUI_LEAF_COUNT, guiSmoothX, guiSmoothY, mvx, mvy, 58.0f, 22.0f, dt);
        }

        for (int i = 0; i < MAX_GUI_LEAF_COUNT; i++) {
            GUI_AGE[i] += dt;
            GUI_Y[i] += GUI_VY[i] * dt;
            GUI_X[i] = wrapHorizontal(GUI_X[i] + GUI_VX[i] * dt, GUI_SIZE[i], width);

            if (GUI_Y[i] > height + GUI_SIZE[i] * 0.6f + 2.0f) {
                GUI_X[i] = ((i % 7) + GUI_RNG.nextFloat()) * (width / 7.0f);
                GUI_VX[i] = -6.0f + GUI_RNG.nextFloat() * 14.0f;
                GUI_SWAY_PHASE[i] = GUI_RNG.nextFloat() * TWO_PI;
                GUI_BASE_ANGLE[i] = (GUI_RNG.nextFloat() - 0.5f) * TWO_PI;
                GUI_Y[i] = -GUI_SIZE[i] * 0.6f;
                GUI_AGE[i] = 0.0f;
                GUI_PUSH_X[i] = 0.0f;
                GUI_PUSH_Y[i] = 0.0f;
            }
        }

        integrateAndDampPush(GUI_X, GUI_Y, GUI_SIZE, GUI_PUSH_X, GUI_PUSH_Y, null, MAX_GUI_LEAF_COUNT, width, dt);
    }

    static int countVisibleGuiLeaves(int width, int height) {
        int visible = 0;
        for (int i = 0; i < GUI_LEAF_COUNT; i++) {
            float half = GUI_SIZE[i] * 0.5f;
            float drawX = computeGuiDrawX(i);
            float drawY = computeGuiDrawY(i);
            if (drawX + half > 0.0f && drawX - half < width && drawY + half > 0.0f && drawY - half < height) {
                visible++;
            }
        }
        return visible;
    }

    static float getGuiLeafSize(int i) {
        return GUI_SIZE[i];
    }

    static float getGuiLeafX(int i) {
        return GUI_X[i];
    }

    static float getGuiLeafY(int i) {
        return GUI_Y[i];
    }

    static float getGuiLeafPushX(int i) {
        return GUI_PUSH_X[i];
    }

    static float getGuiLeafPushY(int i) {
        return GUI_PUSH_Y[i];
    }

    static float computeGuiDrawX(int i) {
        float px = guiPointerActive ? computeParallaxOffset(guiSmoothX, guiLastWidth, getGuiParallaxRange(i)) : 0.0f;
        return computeSwayedX(GUI_X[i], GUI_AGE[i], GUI_SWAY_AMP[i], GUI_SWAY_FREQ[i], GUI_SWAY_PHASE[i]) + px;
    }

    static float computeGuiDrawY(int i) {
        float py = guiPointerActive ? computeParallaxOffset(guiSmoothY, guiLastHeight, getGuiParallaxRange(i) * 0.75f) : 0.0f;
        return GUI_Y[i] + py;
    }

    static int computeGuiLeafAlpha(int i, int width, int height) {
        float fadeIn = clamp01(GUI_AGE[i] / 0.45f);
        float drawX = computeGuiDrawX(i);
        float drawY = computeGuiDrawY(i);
        float edgeX = Math.min(clamp01(drawX / GUI_EDGE_FADE_PX), clamp01((width - drawX) / GUI_EDGE_FADE_PX));
        float edgeY = Math.min(clamp01(drawY / GUI_EDGE_FADE_PX), clamp01((height - drawY) / GUI_EDGE_FADE_PX));
        float edgeFade = Math.min(edgeX, edgeY);
        int a = Math.round(GUI_ALPHA[i] * fadeIn * edgeFade * 255.0f);
        return Math.max(0, Math.min(255, a));
    }

    /**
     * Renders clipped, subtle ambient falling leaves behind ClickGUI panels when Sestia Leaf theme is active.
     */
    public static void renderGuiAmbientLeaves(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        renderGuiAmbientLeaves(graphics, x, y, width, height, -1, -1);
    }

    /**
     * Renders clipped, cursor-reactive ambient falling leaves inside ClickGUI/picker windows.
     */
    public static void renderGuiAmbientLeaves(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int mouseX, int mouseY) {
        if (graphics == null || width <= 8 || height <= 8) {
            return;
        }
        ModConfig cfg = tryGetConfig();
        if (cfg != null && !isGuiLeavesEnabled(cfg.leafMode)) {
            return;
        }
        int activeCount = cfg != null ? resolveActiveGuiLeafCount(cfg.leafDensity) : GUI_LEAF_COUNT;
        float speedMul = cfg != null ? resolveSpeedMultiplier(cfg.leafSpeed) : 1.0f;

        setGuiPointer(mouseX - x, mouseY - y, width, height);

        long now = System.nanoTime();
        if (!guiInitialized || guiLastRenderNanos == 0L || (now - guiLastRenderNanos) > REENTRY_RESET_NANOS) {
            resetGuiAmbientLeaves(width, height, 26329L);
            guiLastRenderNanos = now;
        } else {
            float dt = (now - guiLastRenderNanos) * 1.0e-9f;
            guiLastRenderNanos = now;
            stepGuiAmbientLeaves(dt, width, height, speedMul);
        }

        graphics.enableScissor(x, y, x + width, y + height);
        try {
            for (int i = 0; i < activeCount; i++) {
                float sz = GUI_SIZE[i];
                float localDrawY = computeGuiDrawY(i);
                if (localDrawY + sz < -2.0f || localDrawY - sz > height + 2.0f) {
                    continue;
                }
                int alphaByte = computeGuiLeafAlpha(i, width, height);
                if (alphaByte <= 1) {
                    continue;
                }

                float drawX = x + computeGuiDrawX(i);
                float drawY = y + localDrawY;
                float angle = computeLeafAngle(GUI_BASE_ANGLE[i], GUI_SPIN_SPEED[i], GUI_AGE[i],
                        GUI_TILT_AMP[i], GUI_SWAY_FREQ[i], GUI_SWAY_PHASE[i]);
                float scaleX = computeLeafFlutterScaleX(GUI_AGE[i], GUI_FLUTTER_FREQ[i], GUI_SWAY_PHASE[i]);
                drawLeafQuad(graphics, drawX, drawY, angle, scaleX, sz, alphaByte);
            }
        } finally {
            graphics.disableScissor();
        }
    }
}
