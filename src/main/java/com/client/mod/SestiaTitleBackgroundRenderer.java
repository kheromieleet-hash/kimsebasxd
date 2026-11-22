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

    static final int LEAF_COUNT = 130;
    private static final long REENTRY_RESET_NANOS = 5_000_000_000L;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    // Pre-allocated flat primitive arrays (far -> mid -> near painter's order, zero per-frame allocation)
    private static final float[] X = new float[LEAF_COUNT];
    private static final float[] Y = new float[LEAF_COUNT];
    private static final float[] DELAY = new float[LEAF_COUNT];
    private static final float[] AGE = new float[LEAF_COUNT];
    private static final float[] SIZE = new float[LEAF_COUNT];
    private static final float[] VY = new float[LEAF_COUNT];
    private static final float[] VX = new float[LEAF_COUNT];
    private static final float[] SWAY_AMP = new float[LEAF_COUNT];
    private static final float[] SWAY_FREQ = new float[LEAF_COUNT];
    private static final float[] SWAY_PHASE = new float[LEAF_COUNT];
    private static final float[] BASE_ANGLE = new float[LEAF_COUNT];
    private static final float[] SPIN_SPEED = new float[LEAF_COUNT];
    private static final float[] TILT_AMP = new float[LEAF_COUNT];
    private static final float[] FLUTTER_FREQ = new float[LEAF_COUNT];
    private static final float[] ALPHA = new float[LEAF_COUNT];

    private static final Random RUNTIME_RNG = new Random(26308L);
    private static boolean initialized = false;
    private static long lastRenderNanos = 0L;
    private static int lastWidth = 0;
    private static int lastHeight = 0;

    private SestiaTitleBackgroundRenderer() {
    }

    /**
     * Resets all leaves above the top edge so the screen starts 100% pure black and leaves trickle in gradually.
     */
    static void resetToBlackStart(int width, int height, long seed) {
        Random rng = new Random(seed);
        lastWidth = Math.max(1, width);
        lastHeight = Math.max(1, height);

        for (int i = 0; i < LEAF_COUNT; i++) {
            configureTier(i, rng);
            randomizeHorizontalAndMotion(i, lastWidth, rng);
            Y[i] = -SIZE[i] * 0.58f - 2.0f;
            AGE[i] = 0.0f;

            float t = i / (float) (LEAF_COUNT - 1);
            DELAY[i] = Math.max(0.16f, 0.16f + t * 7.2f + (rng.nextFloat() - 0.5f) * 0.14f);
        }

        // Shuffle entry delays across depth tiers so far/mid/near leaves enter organically together
        for (int i = LEAF_COUNT - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            float tmp = DELAY[i];
            DELAY[i] = DELAY[j];
            DELAY[j] = tmp;
        }

        initialized = true;
    }

    private static void configureTier(int i, Random rng) {
        if (i < 52) {
            // Far tier (smallest, slowest, dimmest)
            SIZE[i] = 20.0f + rng.nextFloat() * 8.0f;
            VY[i] = 18.0f + rng.nextFloat() * 7.0f;
            ALPHA[i] = 0.38f + rng.nextFloat() * 0.18f;
        } else if (i < 94) {
            // Mid tier
            SIZE[i] = 27.0f + rng.nextFloat() * 8.0f;
            VY[i] = 23.0f + rng.nextFloat() * 9.0f;
            ALPHA[i] = 0.56f + rng.nextFloat() * 0.18f;
        } else {
            // Near tier
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
        if (width <= 0 || height <= 0) {
            return;
        }
        if (lastWidth > 0 && lastWidth != width) {
            float sx = width / (float) lastWidth;
            for (int i = 0; i < LEAF_COUNT; i++) {
                X[i] *= sx;
            }
        }
        lastWidth = width;
        lastHeight = height;

        float dt = Float.isFinite(rawDt) ? Math.max(0.0f, Math.min(0.1f, rawDt)) : 0.0f;
        if (dt <= 0.0f) {
            return;
        }

        for (int i = 0; i < LEAF_COUNT; i++) {
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
    }

    private static void dtStepLeaf(int i, float dt, int width, int height) {
        AGE[i] += dt;
        Y[i] += VY[i] * dt;
        X[i] += VX[i] * dt;

        float pad = SIZE[i];
        float wrapSpan = width + 2.0f * pad;
        if (X[i] < -pad) {
            X[i] += wrapSpan;
        } else if (X[i] > width + pad) {
            X[i] -= wrapSpan;
        }

        if (Y[i] > height + SIZE[i] * 0.65f + 4.0f) {
            randomizeHorizontalAndMotion(i, width, RUNTIME_RNG);
            Y[i] = -SIZE[i] * 0.58f - 4.0f - RUNTIME_RNG.nextFloat() * 10.0f;
            AGE[i] = 0.0f;
            DELAY[i] = RUNTIME_RNG.nextFloat() * 0.16f;
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

    static float computeDrawX(int i) {
        return X[i] + (float) Math.sin(AGE[i] * TWO_PI * SWAY_FREQ[i] + SWAY_PHASE[i]) * SWAY_AMP[i];
    }

    static float computeAngle(int i) {
        return BASE_ANGLE[i] + SPIN_SPEED[i] * AGE[i]
                + TILT_AMP[i] * (float) Math.sin(AGE[i] * TWO_PI * SWAY_FREQ[i] + SWAY_PHASE[i] * 0.7f);
    }

    static float computeFlutterScaleX(int i) {
        return 0.70f + 0.30f * (float) Math.cos(AGE[i] * TWO_PI * FLUTTER_FREQ[i] + SWAY_PHASE[i]);
    }

    static int computeLeafAlpha(int i, int height, boolean isTitleScreen) {
        float fadeIn = Math.min(1.0f, Math.max(0.0f, AGE[i] / 0.45f));
        float bottomStart = Math.max(0.0f, height - SIZE[i] * 0.5f);
        float bottomFade = 1.0f;
        if (Y[i] > bottomStart && SIZE[i] > 0.0f) {
            bottomFade = Math.max(0.0f, 1.0f - (Y[i] - bottomStart) / (SIZE[i] * 1.15f));
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

        long now = System.nanoTime();
        if (!initialized || lastRenderNanos == 0L || (now - lastRenderNanos) > REENTRY_RESET_NANOS) {
            resetToBlackStart(width, height, 26308L);
            lastRenderNanos = now;
            return;
        }

        float dt = (now - lastRenderNanos) * 1.0e-9f;
        lastRenderNanos = now;
        stepSimulation(dt, width, height);

        for (int i = 0; i < LEAF_COUNT; i++) {
            if (DELAY[i] > 0.0f) {
                continue;
            }
            float sz = SIZE[i];
            if (Y[i] + sz < -4.0f || Y[i] - sz > height + 4.0f) {
                continue;
            }
            int alphaByte = computeLeafAlpha(i, height, isTitleScreen);
            if (alphaByte <= 1) {
                continue;
            }

            float drawX = computeDrawX(i);
            float drawY = Y[i];
            float angle = computeAngle(i);
            float scaleX = computeFlutterScaleX(i);
            int szInt = Math.max(1, Math.round(sz));
            int half = szInt / 2;
            int color = (alphaByte << 24) | 0x00FFFFFF;

            graphics.pose().pushMatrix();
            try {
                graphics.pose().translate(drawX, drawY);
                graphics.pose().rotate(angle);
                graphics.pose().scale(scaleX, 1.0f);
                graphics.blit(
                        RenderPipelines.GUI_TEXTURED,
                        LEAF_TEX,
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
    }
}
