package com.client.mod;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/**
 * High-performance, zero garbage allocation in-game 2D ESP rendering engine.
 * Renders 2D Box outlines, vertical health bars, nametag badges, armor items,
 * and player skeleton wireframes in screen coordinates.
 */
public final class EspRenderHandler {
    private static final Matrix4f VIEW_PROJ_MATRIX = new Matrix4f();
    private static final Vector4f SCRATCH_VEC4 = new Vector4f();

    private static final EquipmentSlot[] ARMOR_SLOTS = {
        EquipmentSlot.MAINHAND,
        EquipmentSlot.HEAD,
        EquipmentSlot.CHEST,
        EquipmentSlot.LEGS,
        EquipmentSlot.FEET,
        EquipmentSlot.OFFHAND
    };

    private EspRenderHandler() {
    }

    /**
     * Entrypoint called from HudOverlayMixin onExtractRenderState.
     */
    public static void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        float delta = deltaTracker != null ? deltaTracker.getGameTimeDeltaPartialTick(false) : 1.0f;
        renderInGameEsp(graphics, delta);
    }

    /**
     * Renders in-game ESP overlay for all valid players within max distance.
     */
    public static void renderInGameEsp(GuiGraphicsExtractor graphics, float delta) {
        ModConfig config = ModConfig.getInstance();
        if (!config.espEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.gameRenderer == null) {
            return;
        }

        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) {
            return;
        }

        Vec3 camPos = camera.position();
        double camX = camPos.x;
        double camY = camPos.y;
        double camZ = camPos.z;

        camera.getViewRotationProjectionMatrix(VIEW_PROJ_MATRIX);

        int guiWidth = graphics.guiWidth();
        int guiHeight = graphics.guiHeight();
        double maxDistSq = (double) config.espMaxDistance * config.espMaxDistance;
        int espColor = config.getEspColorHex();

        List<AbstractClientPlayer> players = mc.level.players();
        int playerCount = players.size();

        for (int i = 0; i < playerCount; i++) {
            AbstractClientPlayer player = players.get(i);
            if (player == null || player == mc.player) {
                continue;
            }
            if (!player.isAlive() || player.isRemoved()) {
                continue;
            }

            double distSq = mc.player.distanceToSqr(player);
            if (distSq > maxDistSq) {
                continue;
            }

            double entX = Mth.lerp(delta, player.xo, player.getX());
            double entY = Mth.lerp(delta, player.yo, player.getY());
            double entZ = Mth.lerp(delta, player.zo, player.getZ());

            double relX = entX - camX;
            double relY = entY - camY;
            double relZ = entZ - camZ;

            float halfW = player.getBbWidth() * 0.5f;
            float bbH = player.getBbHeight();

            float minX = Float.POSITIVE_INFINITY;
            float maxX = Float.NEGATIVE_INFINITY;
            float minY = Float.POSITIVE_INFINITY;
            float maxY = Float.NEGATIVE_INFINITY;
            boolean anyBehind = false;

            // 8 bounding box corners projected to 2D
            for (int c = 0; c < 8; c++) {
                float dx = (c & 1) == 0 ? -halfW : halfW;
                float dy = (c & 4) == 0 ? 0.0f : bbH;
                float dz = (c & 2) == 0 ? -halfW : halfW;

                VIEW_PROJ_MATRIX.transform((float) (relX + dx), (float) (relY + dy), (float) (relZ + dz), 1.0f, SCRATCH_VEC4);
                if (SCRATCH_VEC4.w <= 0.05f) {
                    anyBehind = true;
                    break;
                }
                float invW = 1.0f / SCRATCH_VEC4.w;
                float sx = (SCRATCH_VEC4.x * invW + 1.0f) * 0.5f * guiWidth;
                float sy = (1.0f - SCRATCH_VEC4.y * invW) * 0.5f * guiHeight;

                if (sx < minX) minX = sx;
                if (sx > maxX) maxX = sx;
                if (sy < minY) minY = sy;
                if (sy > maxY) maxY = sy;
            }

            if (anyBehind) {
                // Near-plane fallback: project bottom center & top center
                VIEW_PROJ_MATRIX.transform((float) relX, (float) relY, (float) relZ, 1.0f, SCRATCH_VEC4);
                if (SCRATCH_VEC4.w <= 0.05f) {
                    continue;
                }
                float invW1 = 1.0f / SCRATCH_VEC4.w;
                float botX = (SCRATCH_VEC4.x * invW1 + 1.0f) * 0.5f * guiWidth;
                float botY = (1.0f - SCRATCH_VEC4.y * invW1) * 0.5f * guiHeight;

                VIEW_PROJ_MATRIX.transform((float) relX, (float) (relY + bbH), (float) relZ, 1.0f, SCRATCH_VEC4);
                if (SCRATCH_VEC4.w <= 0.05f) {
                    continue;
                }
                float invW2 = 1.0f / SCRATCH_VEC4.w;
                float topX = (SCRATCH_VEC4.x * invW2 + 1.0f) * 0.5f * guiWidth;
                float topY = (1.0f - SCRATCH_VEC4.y * invW2) * 0.5f * guiHeight;

                float height = Math.abs(botY - topY);
                float width = height * 0.55f;
                float centerX = (botX + topX) * 0.5f;

                minX = centerX - width * 0.5f;
                maxX = centerX + width * 0.5f;
                minY = Math.min(topY, botY);
                maxY = Math.max(topY, botY);
            }

            // Viewport culling
            if (maxX < 0.0f || minX > guiWidth || maxY < 0.0f || minY > guiHeight) {
                continue;
            }
            if (maxX - minX < 2.0f || maxY - minY < 2.0f) {
                continue;
            }

            int left = (int) Math.floor(minX);
            int right = (int) Math.ceil(maxX);
            int top = (int) Math.floor(minY);
            int bottom = (int) Math.ceil(maxY);

            // 1. Box ESP Outline
            if (config.espBox) {
                renderBoxOutline(graphics, left, top, right, bottom, espColor);
            }

            // 2. Health Bar
            float health = player.getHealth();
            float maxHealth = player.getMaxHealth();
            if (maxHealth <= 0.0f) maxHealth = 20.0f;
            float hpPercent = Mth.clamp(health / maxHealth, 0.0f, 1.0f);

            if (config.espHealth) {
                renderHealthBar(graphics, mc, left, top, bottom, health, hpPercent);
            }

            // 3. Skeleton Wireframe
            if (config.espSkeleton) {
                renderSkeleton(graphics, left, top, right, bottom, espColor);
            }

            // 4. Overhead Nametag Badge
            if (config.espNametags) {
                renderNametag(graphics, mc, player, left, top, right, distSq, health, hpPercent, espColor);
            }

            // 5. Armor & Item Durability HUD
            if (config.espArmor) {
                renderArmor(graphics, mc, player, right, top);
            }
        }
    }

    private static void renderBoxOutline(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        // Translucent background
        int boxBg = (0x1F << 24) | (color & 0x00FFFFFF);
        graphics.fill(left, top, right, bottom, boxBg);

        // Crisp outer black shadow
        graphics.fill(left - 1, top - 1, right + 1, top, 0xFF000000);
        graphics.fill(left - 1, bottom, right + 1, bottom + 1, 0xFF000000);
        graphics.fill(left - 1, top, left, bottom, 0xFF000000);
        graphics.fill(right, top, right + 1, bottom, 0xFF000000);

        // Inner black shadow if large enough
        if (right - left > 5 && bottom - top > 5) {
            graphics.fill(left + 1, top + 1, right - 1, top + 2, 0xFF000000);
            graphics.fill(left + 1, bottom - 2, right - 1, bottom - 1, 0xFF000000);
            graphics.fill(left + 1, top + 2, left + 2, bottom - 2, 0xFF000000);
            graphics.fill(right - 2, top + 2, right - 1, bottom - 2, 0xFF000000);
        }

        // Colored main frame
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top + 1, left + 1, bottom - 1, color);
        graphics.fill(right - 1, top + 1, right, bottom - 1, color);

        // Corner accents
        int cornerLen = Math.max(3, Math.min(8, (right - left) / 4));
        graphics.fill(left - 1, top - 1, left + cornerLen, top + 1, color);
        graphics.fill(left - 1, top - 1, left + 1, top + cornerLen, color);

        graphics.fill(right - cornerLen, top - 1, right + 1, top + 1, color);
        graphics.fill(right - 1, top - 1, right + 1, top + cornerLen, color);

        graphics.fill(left - 1, bottom - 1, left + cornerLen, bottom + 1, color);
        graphics.fill(left - 1, bottom - cornerLen, left + 1, bottom + 1, color);

        graphics.fill(right - cornerLen, bottom - 1, right + 1, bottom + 1, color);
        graphics.fill(right - 1, bottom - cornerLen, right + 1, bottom + 1, color);
    }

    private static void renderHealthBar(GuiGraphicsExtractor graphics, Minecraft mc, int left, int top, int bottom, float health, float hpPercent) {
        int barW = 2;
        int barRight = left - 2;
        int barLeft = barRight - barW;
        int barTop = top;
        int barBottom = bottom;

        // Black background
        graphics.fill(barLeft - 1, barTop - 1, barRight + 1, barBottom + 1, 0xFF000000);

        int totalH = barBottom - barTop;
        int filledH = Math.max(1, (int) (totalH * hpPercent));
        int hpTop = barBottom - filledH;
        int hpColor = getHealthColor(hpPercent);

        // Filled dynamic health
        graphics.fill(barLeft, hpTop, barRight, barBottom, hpColor);

        // Numeric HP indicator when damaged
        if (hpPercent < 1.0f && totalH > 15) {
            int hpInt = (int) Math.ceil(health);
            String hpStr = String.valueOf(hpInt);
            int hpStrW = mc.font.width(hpStr);
            graphics.text(mc.font, hpStr, barLeft - hpStrW - 1, hpTop - 2, 0xFFFFFFFF, true);
        }
    }

    private static void renderSkeleton(GuiGraphicsExtractor graphics, int left, int top, int right, int bottom, int color) {
        int boxH = bottom - top;
        int boxW = right - left;
        int midX = (left + right) / 2;
        int headBottom = top + (int) (boxH * 0.18f);
        int spineBottom = top + (int) (boxH * 0.60f);

        // Spine
        drawLine(graphics, midX, headBottom, midX, spineBottom, color);

        // Shoulders to Hands
        int shoulderY = top + (int) (boxH * 0.25f);
        int handY = top + (int) (boxH * 0.55f);
        drawLine(graphics, midX, shoulderY, left + 2, handY, color);
        drawLine(graphics, midX, shoulderY, right - 2, handY, color);

        // Pelvis to Feet
        int kneeY = top + (int) (boxH * 0.80f);
        int legSpan = Math.max(2, (int) (boxW * 0.25f));
        drawLine(graphics, midX, spineBottom, midX - legSpan, kneeY, color);
        drawLine(graphics, midX - legSpan, kneeY, midX - legSpan, bottom, color);
        drawLine(graphics, midX, spineBottom, midX + legSpan, kneeY, color);
        drawLine(graphics, midX + legSpan, kneeY, midX + legSpan, bottom, color);

        // Head outline box
        int headRadius = Math.max(2, (int) (boxH * 0.08f));
        graphics.fill(midX - headRadius, top, midX + headRadius, headBottom, (0x33 << 24) | (color & 0x00FFFFFF));
        graphics.fill(midX - headRadius, top, midX + headRadius, top + 1, color);
        graphics.fill(midX - headRadius, headBottom - 1, midX + headRadius, headBottom, color);
        graphics.fill(midX - headRadius, top, midX - headRadius + 1, headBottom, color);
        graphics.fill(midX + headRadius - 1, top, midX + headRadius, headBottom, color);
    }

    private static void renderNametag(GuiGraphicsExtractor graphics, Minecraft mc, AbstractClientPlayer player,
                                      int left, int top, int right, double distSq, float health, float hpPercent, int espColor) {
        double dist = Math.sqrt(distSq);
        int distInt = (int) Math.round(dist);
        int hpInt = (int) Math.ceil(health);

        String hpColorCode = hpPercent > 0.6f ? "§a" : (hpPercent > 0.25f ? "§e" : "§c");
        String nameStr = player.getName().getString();
        String badge = nameStr + " §7[" + distInt + "m] " + hpColorCode + hpInt + "❤";

        int badgeW = mc.font.width(badge);
        int midX = (left + right) / 2;
        int badgeX = midX - badgeW / 2;
        int badgeY = Math.max(2, top - 13);

        // Background pill
        graphics.fill(badgeX - 4, badgeY - 2, badgeX + badgeW + 4, badgeY + 10, 0xAA0D0D11);
        // Left accent strip
        graphics.fill(badgeX - 4, badgeY - 2, badgeX - 2, badgeY + 10, espColor);

        graphics.text(mc.font, badge, badgeX, badgeY, 0xFFFFFFFF, true);
    }

    private static void renderArmor(GuiGraphicsExtractor graphics, Minecraft mc, AbstractClientPlayer player, int right, int top) {
        int itemX = right + 3;
        int itemY = top;

        for (int i = 0; i < ARMOR_SLOTS.length; i++) {
            ItemStack stack = player.getItemBySlot(ARMOR_SLOTS[i]);
            if (!stack.isEmpty()) {
                graphics.item(stack, itemX, itemY);
                graphics.itemDecorations(mc.font, stack, itemX, itemY);

                if (stack.isDamageableItem()) {
                    int maxDmg = stack.getMaxDamage();
                    int curDmg = stack.getDamageValue();
                    int pct = (int) Math.round(((double) (maxDmg - curDmg) / maxDmg) * 100.0);
                    String pctStr = pct + "%";
                    int durColor = pct > 60 ? 0xFF00FF66 : (pct > 25 ? 0xFFFFAA00 : 0xFFFF3333);
                    graphics.text(mc.font, pctStr, itemX + 17, itemY + 4, durColor, true);
                }
                itemY += 16;
            }
        }
    }

    public static void drawLine(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;

        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    public static int getHealthColor(float hpPercent) {
        hpPercent = Mth.clamp(hpPercent, 0.0f, 1.0f);
        int r, g;
        if (hpPercent > 0.5f) {
            float t = (hpPercent - 0.5f) * 2.0f;
            r = (int) ((1.0f - t) * 255.0f);
            g = 255;
        } else {
            float t = hpPercent * 2.0f;
            r = 255;
            g = (int) (t * 255.0f);
        }
        return 0xFF000000 | (r << 16) | (g << 8) | 0x22;
    }

    /**
     * Projects any 3D world position to 2D screen coordinates.
     */
    public static boolean projectPointToScreen(double worldX, double worldY, double worldZ, int guiWidth, int guiHeight, float[] outXY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null) return false;
        Camera camera = mc.gameRenderer.mainCamera();
        if (camera == null || !camera.isInitialized()) return false;

        Vec3 camPos = camera.position();
        camera.getViewRotationProjectionMatrix(VIEW_PROJ_MATRIX);

        VIEW_PROJ_MATRIX.transform((float) (worldX - camPos.x), (float) (worldY - camPos.y), (float) (worldZ - camPos.z), 1.0f, SCRATCH_VEC4);
        if (SCRATCH_VEC4.w <= 0.05f) {
            return false;
        }
        float invW = 1.0f / SCRATCH_VEC4.w;
        outXY[0] = (SCRATCH_VEC4.x * invW + 1.0f) * 0.5f * guiWidth;
        outXY[1] = (1.0f - SCRATCH_VEC4.y * invW) * 0.5f * guiHeight;
        return true;
    }
}
