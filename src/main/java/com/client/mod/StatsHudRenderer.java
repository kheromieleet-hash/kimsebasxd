package com.client.mod;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Locale;

public class StatsHudRenderer {
    // Uses Minecraft's default font
    private static final Style PRIMARY_STYLE = Style.EMPTY.withColor(0xFFFFFFFF);
    private static final Style SECONDARY_STYLE = Style.EMPTY.withColor(0xFFD8D8D8);

    private static final float[] TICK_RATES = new float[20];
    private static int nextIndex = 0;
    private static long timeLastTimeUpdate = -1L;
    private static long timeGameJoined = -1L;

    public static void onGameJoin() {
        Arrays.fill(TICK_RATES, 0.0f);
        nextIndex = 0;
        long now = System.currentTimeMillis();
        timeLastTimeUpdate = now;
        timeGameJoined = now;
    }

    public static void onTimePacket() {
        long now = System.currentTimeMillis();
        if (timeLastTimeUpdate != -1L) {
            float delta = (float) (now - timeLastTimeUpdate) / 1000.0f;
            if (delta > 0.0f) {
                TICK_RATES[nextIndex] = Mth.clamp(20.0f / delta, 0.0f, 20.0f);
                nextIndex = (nextIndex + 1) % TICK_RATES.length;
            }
        }
        timeLastTimeUpdate = now;
    }

    public static float getTickRate() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0.0f;
        }
        if (System.currentTimeMillis() - timeGameJoined < 4000L) {
            return 20.0f;
        }
        int count = 0;
        float sum = 0.0f;
        for (float rate : TICK_RATES) {
            if (rate > 0.0f) {
                sum += rate;
                count++;
            }
        }
        return count > 0 ? (sum / (float) count) : 20.0f;
    }

    public static void render(GuiGraphicsExtractor graphics) {
        ModConfig config = ModConfig.getInstance();
        if (!config.hudInfoEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int lineSpacing = 11;

        int fps = mc.getFps();
        float tps = getTickRate();
        int ping = getPing(mc);
        double speed = getPlayerSpeed(mc);

        MutableComponent fpsComp = buildPair("FPS: ", Integer.toString(fps));
        MutableComponent tpsComp = buildPair("TPS: ", formatRound1(tps));
        MutableComponent pingComp = buildPair("Ping: ", Integer.toString(ping));
        MutableComponent speedComp = buildPair("Speed: ", formatRound1(speed));

        // Sestia Crimson Watermark Pill at top-left (smooth rounded card + vector crest, no boxy Unicode glyphs)
        String wmLabel = "§c§lSCT CLIENT §7| §f" + ClientBuildInfo.getDisplayVersion() + " §7| §f" + fps + " FPS";
        int wmW = mc.font.width(wmLabel) + 23;
        int wmH = 16;
        JelloTheme.fillRoundedCard(graphics, 4, 4, 4 + wmW, 4 + wmH, 4, 0xD8120B0E, 0x66E53935);
        ModernIcons.drawBrandEmblem(graphics, 12, 12, JelloTheme.ACCENT, 0xFFFFCDD2);
        graphics.text(mc.font, Component.literal(wmLabel), 20, 8, 0xFFFFFFFF, true);

        int x = 4;
        int y = 24;
        graphics.text(mc.font, fpsComp, x, y, -1, true);
        graphics.text(mc.font, tpsComp, x, y + lineSpacing, -1, true);
        graphics.text(mc.font, pingComp, x, y + lineSpacing * 2, -1, true);
        graphics.text(mc.font, speedComp, x, y + lineSpacing * 3, -1, true);

        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 pos = camera.position();
        int posX = (int) Math.floor(pos.x);
        int posY = (int) Math.floor(pos.y);
        int posZ = (int) Math.floor(pos.z);

        float yaw = Mth.wrapDegrees(camera.yRot());
        float pitch = camera.xRot();
        String dirName = getDirectionName(yaw);
        String dirAxis = getDirectionAxis(yaw);

        MutableComponent dirComp = Component.literal(dirName + " (").setStyle(PRIMARY_STYLE)
                .append(Component.literal(dirAxis + " " + formatRound1(yaw) + " / " + formatRound1(pitch) + ")").setStyle(SECONDARY_STYLE));

        MutableComponent posComp = buildPair("Pos: ", posX + ", " + posY + ", " + posZ);

        ResourceKey<Level> dim = mc.level.dimension();
        MutableComponent otherDimComp = null;
        if (dim == Level.OVERWORLD) {
            int netherX = (int) Math.floor(pos.x / 8.0);
            int netherZ = (int) Math.floor(pos.z / 8.0);
            otherDimComp = buildPair("Nether: ", netherX + ", " + posY + ", " + netherZ);
        } else if (dim == Level.NETHER) {
            int overX = (int) Math.floor(pos.x * 8.0);
            int overZ = (int) Math.floor(pos.z * 8.0);
            otherDimComp = buildPair("Overworld: ", overX + ", " + posY + ", " + overZ);
        }

        int rightX = screenW - 4;
        int botY = screenH - 14;

        drawRightAligned(graphics, mc, posComp, rightX, botY);
        if (otherDimComp != null) {
            drawRightAligned(graphics, mc, otherDimComp, rightX, botY - lineSpacing);
            drawRightAligned(graphics, mc, dirComp, rightX, botY - lineSpacing * 2);
        } else {
            drawRightAligned(graphics, mc, dirComp, rightX, botY - lineSpacing);
        }
    }

    private static void drawRightAligned(GuiGraphicsExtractor graphics, Minecraft mc, Component comp, int x, int y) {
        int w = mc.font.width(comp);
        graphics.text(mc.font, comp, x - w, y, -1, true);
    }

    private static MutableComponent buildPair(String label, String value) {
        return Component.literal(label).setStyle(PRIMARY_STYLE)
                .append(Component.literal(value).setStyle(SECONDARY_STYLE));
    }

    private static int getPing(Minecraft mc) {
        ClientPacketListener conn = mc.getConnection();
        if (conn != null && mc.player != null) {
            PlayerInfo info = conn.getPlayerInfo(mc.player.getUUID());
            return info != null ? info.getLatency() : 0;
        }
        return 0;
    }

    private static double getPlayerSpeed(Minecraft mc) {
        if (mc.player == null) {
            return 0.0;
        }
        double dx = (mc.player.getX() - mc.player.xo) * 20.0;
        double dy = (mc.player.getY() - mc.player.yo) * 20.0;
        double dz = (mc.player.getZ() - mc.player.zo) * 20.0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static String formatRound1(double val) {
        double rounded = (double) Math.round(val * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 1.0E-9) {
            return Long.toString(Math.round(rounded));
        }
        return String.format(Locale.US, "%.1f", rounded);
    }

    private static float normalizeYaw(float yaw) {
        float y = yaw % 360.0f;
        if (y < 0.0f) {
            y += 360.0f;
        }
        return y;
    }

    private static String getDirectionName(float yaw) {
        float y = normalizeYaw(yaw);
        if (y >= 337.5f || y < 22.5f) return "South";
        if (y < 67.5f) return "South West";
        if (y < 112.5f) return "West";
        if (y < 157.5f) return "North West";
        if (y < 202.5f) return "North";
        if (y < 247.5f) return "North East";
        if (y < 292.5f) return "East";
        return "South East";
    }

    private static String getDirectionAxis(float yaw) {
        float y = normalizeYaw(yaw);
        if (y >= 337.5f || y < 22.5f) return "Z+";
        if (y < 67.5f) return "X- Z+";
        if (y < 112.5f) return "X-";
        if (y < 157.5f) return "X- Z-";
        if (y < 202.5f) return "Z-";
        if (y < 247.5f) return "X+ Z-";
        if (y < 292.5f) return "X+";
        return "X+ Z+";
    }
}
