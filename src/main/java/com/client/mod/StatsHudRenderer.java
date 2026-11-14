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
import java.util.List;
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
        if (mc.gui != null && mc.gui.screen() instanceof HudLayoutEditorScreen) {
            return;
        }

        int screenW = graphics.guiWidth();
        int screenH = graphics.guiHeight();
        int lineSpacing = 11;

        List<Component> statsLines = config.hudTelemetryEnabled ? buildStatsLines(mc, config) : List.of();
        if (!statsLines.isEmpty()) {
            int statsW = measureMaxWidth(mc.font, statsLines);
            int statsH = statsLines.size() * lineSpacing;
            int statsX = Mth.clamp(config.statsHudX, 2, Math.max(2, screenW - statsW - 2));
            int statsY = Mth.clamp(config.statsHudY, 2, Math.max(2, screenH - statsH - 2));
            for (int i = 0; i < statsLines.size(); i++) {
                graphics.text(mc.font, statsLines.get(i), statsX, statsY + i * lineSpacing, -1, true);
            }
        }

        List<Component> posLines = config.hudPositionEnabled ? buildPositionLines(mc, config) : List.of();
        if (!posLines.isEmpty()) {
            int posW = measureMaxWidth(mc.font, posLines);
            int posH = posLines.size() * lineSpacing;
            int posX = config.posHudX < 0
                    ? Math.max(2, screenW - 4 - posW)
                    : Mth.clamp(config.posHudX, 2, Math.max(2, screenW - posW - 2));
            int posY = config.posHudY < 0
                    ? Math.max(2, screenH - 4 - posH)
                    : Mth.clamp(config.posHudY, 2, Math.max(2, screenH - posH - 2));
            boolean alignRight = config.posHudX < 0 || (posX + posW / 2) > (screenW / 2);
            for (int i = 0; i < posLines.size(); i++) {
                Component line = posLines.get(i);
                int lineX = alignRight ? (posX + posW - mc.font.width(line)) : posX;
                graphics.text(mc.font, line, lineX, posY + i * lineSpacing, -1, true);
            }
        }
    }

    public static int[] resolveStatsBounds(Font font, int screenW, int screenH, ModConfig config) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = (mc.player != null && mc.level != null)
                ? buildStatsLines(mc, config)
                : buildDummyStatsLines(config);
        int w = Math.max(74, measureMaxWidth(font, lines));
        int h = Math.max(11, lines.size() * 11);
        int x = Mth.clamp(config.statsHudX, 2, Math.max(2, screenW - w - 2));
        int y = Mth.clamp(config.statsHudY, 2, Math.max(2, screenH - h - 2));
        return new int[]{x, y, w, h};
    }

    public static int[] resolvePositionBounds(Font font, int screenW, int screenH, ModConfig config) {
        Minecraft mc = Minecraft.getInstance();
        List<Component> lines = (mc.player != null && mc.level != null)
                ? buildPositionLines(mc, config)
                : buildDummyPositionLines(config);
        int w = Math.max(96, measureMaxWidth(font, lines));
        int h = Math.max(11, lines.size() * 11);
        int x = config.posHudX < 0
                ? Math.max(2, screenW - 4 - w)
                : Mth.clamp(config.posHudX, 2, Math.max(2, screenW - w - 2));
        int y = config.posHudY < 0
                ? Math.max(2, screenH - 4 - h)
                : Mth.clamp(config.posHudY, 2, Math.max(2, screenH - h - 2));
        return new int[]{x, y, w, h};
    }

    private static int measureMaxWidth(Font font, List<Component> lines) {
        int max = 0;
        for (Component c : lines) {
            int w = font.width(c);
            if (w > max) max = w;
        }
        return max;
    }

    private static List<Component> buildStatsLines(Minecraft mc, ModConfig config) {
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>(4);
        if (config.hudShowFps) {
            lines.add(buildPair("FPS: ", Integer.toString(mc.getFps())));
        }
        if (config.hudShowTps) {
            lines.add(buildPair("TPS: ", formatRound1(getTickRate())));
        }
        if (config.hudShowPing) {
            lines.add(buildPair("Ping: ", Integer.toString(getPing(mc))));
        }
        if (config.hudShowSpeed) {
            lines.add(buildPair("Speed: ", formatRound1(getPlayerSpeed(mc))));
        }
        return lines;
    }

    private static List<Component> buildDummyStatsLines(ModConfig config) {
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>(4);
        if (config.hudShowFps) lines.add(buildPair("FPS: ", "144"));
        if (config.hudShowTps) lines.add(buildPair("TPS: ", "20"));
        if (config.hudShowPing) lines.add(buildPair("Ping: ", "24"));
        if (config.hudShowSpeed) lines.add(buildPair("Speed: ", "4.3"));
        return lines;
    }

    private static List<Component> buildPositionLines(Minecraft mc, ModConfig config) {
        Camera camera = mc.gameRenderer.mainCamera();
        Vec3 pos = camera.position();
        int posX = (int) Math.floor(pos.x);
        int posY = (int) Math.floor(pos.y);
        int posZ = (int) Math.floor(pos.z);

        java.util.ArrayList<Component> lines = new java.util.ArrayList<>(3);

        if (config.hudShowDir) {
            float yaw = Mth.wrapDegrees(camera.yRot());
            float pitch = camera.xRot();
            String dirName = getDirectionName(yaw);
            String dirAxis = getDirectionAxis(yaw);
            MutableComponent dirComp = Component.literal(dirName + " (").setStyle(PRIMARY_STYLE)
                    .append(Component.literal(dirAxis + " " + formatRound1(yaw) + " / " + formatRound1(pitch) + ")").setStyle(SECONDARY_STYLE));
            lines.add(dirComp);
        }

        if (config.hudShowNether) {
            ResourceKey<Level> dim = mc.level.dimension();
            if (dim == Level.OVERWORLD) {
                int netherX = (int) Math.floor(pos.x / 8.0);
                int netherZ = (int) Math.floor(pos.z / 8.0);
                lines.add(buildPair("Nether: ", netherX + ", " + posY + ", " + netherZ));
            } else if (dim == Level.NETHER) {
                int overX = (int) Math.floor(pos.x * 8.0);
                int overZ = (int) Math.floor(pos.z * 8.0);
                lines.add(buildPair("Overworld: ", overX + ", " + posY + ", " + overZ));
            }
        }

        if (config.hudShowPos) {
            lines.add(buildPair("Pos: ", posX + ", " + posY + ", " + posZ));
        }
        return lines;
    }

    private static List<Component> buildDummyPositionLines(ModConfig config) {
        java.util.ArrayList<Component> lines = new java.util.ArrayList<>(3);
        if (config.hudShowDir) lines.add(Component.literal("South (Z+ 0 / 0)").setStyle(PRIMARY_STYLE));
        if (config.hudShowNether) lines.add(buildPair("Nether: ", "0, 64, 0"));
        if (config.hudShowPos) lines.add(buildPair("Pos: ", "0, 64, 0"));
        return lines;
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
