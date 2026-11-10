package com.client.loader;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Rise 6 Luxury undecorated Swing desktop window displayed during startup updates.
 */
public final class SctUpdateWindow implements SctUpdateListener {
    private static final int WIN_W = 432;
    private static final int WIN_H = 198;

    private static final Color BG_TOP = new Color(0x18, 0x0C, 0x10);
    private static final Color BG_BOTTOM = new Color(0x0E, 0x08, 0x0B);
    private static final Color CARD_BG = new Color(34, 18, 22, 200);
    private static final Color CARD_BORDER = new Color(255, 255, 255, 22);
    private static final Color OUTER_BORDER = new Color(229, 57, 53, 120);
    private static final Color VIOLET = new Color(0xE5, 0x39, 0x35);
    private static final Color ROSE = new Color(0xFF, 0x52, 0x52);
    private static final Color EMERALD_A = new Color(0x34, 0xD3, 0x99);
    private static final Color EMERALD_B = new Color(0x10, 0xB9, 0x81);
    private static final Color AMBER_A = new Color(0xFB, 0xBF, 0x24);
    private static final Color AMBER_B = new Color(0xF5, 0x9E, 0x0B);
    private static final Color TEXT_MUTED = new Color(0x94, 0xA3, 0xB8);
    private static final Color TEXT_SOFT = new Color(0xFC, 0xA5, 0xA5);
    private static final Color TRACK_BG = new Color(42, 22, 28, 230);

    private final boolean headless;
    private JDialog dialog;
    private UpdateCanvas canvas;
    private Timer delayedShowTimer;
    private Timer animTimer;
    private volatile boolean shown = false;

    private volatile String badgeText = "CHECKING";
    private volatile String versionTag = "v" + SctUpdateConfig.BASELINE_VERSION;
    private volatile String statusTitle = "Checking for updates...";
    private volatile String statusSub = "Contacting GitHub Releases manifest";
    private volatile String pctText = "...";
    private volatile String leftMeta = "Connecting to release server";
    private volatile String rightMeta = "Timeout: 4s";
    private volatile double progress = 0.25;
    private volatile Color accentA = VIOLET;
    private volatile Color accentB = new Color(0xFB, 0x71, 0x85);
    private volatile boolean shimmerActive = true;
    private volatile float shimmerPhase = 0.0f;

    public SctUpdateWindow() {
        this.headless = GraphicsEnvironment.isHeadless();
        if (this.headless) {
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            try {
                initDialog();
            } catch (Throwable ignored) {
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void initDialog() {
        this.dialog = new JDialog();
        this.dialog.setTitle("SCT Client Auto Updater");
        this.dialog.setUndecorated(true);
        this.dialog.setAlwaysOnTop(true);
        this.dialog.setSize(WIN_W, WIN_H);
        this.dialog.setLocationRelativeTo(null);

        boolean perPixelOk = false;
        try {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            perPixelOk = gd.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        } catch (Throwable ignored) {
        }
        if (perPixelOk) {
            this.dialog.setBackground(new Color(0, 0, 0, 0));
        } else {
            this.dialog.setBackground(BG_BOTTOM);
            try {
                this.dialog.setShape(new RoundRectangle2D.Double(0, 0, WIN_W, WIN_H, 24, 24));
            } catch (Throwable ignored) {
            }
        }

        this.canvas = new UpdateCanvas();
        this.canvas.setPreferredSize(new Dimension(WIN_W, WIN_H));
        this.canvas.setOpaque(false);
        this.dialog.setContentPane(this.canvas);

        this.animTimer = new Timer(33, e -> {
            this.shimmerPhase += 0.035f;
            if (this.shimmerPhase > 1.4f) {
                this.shimmerPhase = -0.4f;
            }
            if (this.canvas != null && this.shown) {
                this.canvas.repaint();
            }
        });
    }

    private void ensureVisible() {
        if (this.headless || this.dialog == null) return;
        SwingUtilities.invokeLater(() -> {
            if (this.delayedShowTimer != null) {
                this.delayedShowTimer.stop();
            }
            if (!this.shown && this.dialog != null) {
                this.shown = true;
                this.dialog.setVisible(true);
                if (this.animTimer != null && !this.animTimer.isRunning()) {
                    this.animTimer.start();
                }
            }
            if (this.canvas != null) {
                this.canvas.repaint();
            }
        });
    }

    private void triggerRepaint() {
        if (this.headless || this.canvas == null) return;
        SwingUtilities.invokeLater(() -> {
            if (this.canvas != null) {
                this.canvas.repaint();
            }
        });
    }

    @Override
    public void onChecking() {
        if (this.headless) return;
        this.badgeText = "CHECKING";
        this.statusTitle = "Checking for updates...";
        this.statusSub = "Contacting GitHub Releases manifest";
        this.pctText = "...";
        this.leftMeta = "Connecting to release server";
        this.rightMeta = "Timeout: 4s";
        this.progress = 0.28;
        this.accentA = VIOLET;
        this.accentB = new Color(0xFB, 0x71, 0x85);
        this.shimmerActive = true;

        SwingUtilities.invokeLater(() -> {
            if (this.dialog != null && !this.shown) {
                this.delayedShowTimer = new Timer(350, e -> ensureVisible());
                this.delayedShowTimer.setRepeats(false);
                this.delayedShowTimer.start();
            }
        });
    }

    @Override
    public void onDownloading(String fromVer, String toVer, String changelog, long bytesRead, long totalBytes, double speedMbPerSec) {
        if (this.headless) return;
        double ratio = totalBytes > 0 ? Math.min(1.0, Math.max(0.02, (double) bytesRead / totalBytes)) : 0.45;
        int pct = (int) Math.round(ratio * 100.0);

        this.badgeText = "UPDATING";
        this.versionTag = "v" + toVer;
        this.statusTitle = "Downloading update...";
        this.statusSub = "v" + fromVer + " -> v" + toVer + " • " + (changelog != null ? changelog : "Latest build");
        this.pctText = totalBytes > 0 ? (pct + "%") : String.format(Locale.ROOT, "%.1f MB", bytesRead / (1024.0 * 1024.0));
        if (totalBytes > 0) {
            this.leftMeta = String.format(Locale.ROOT, "%.1f MB / %.1f MB • %.1f MB/s",
                    bytesRead / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0), speedMbPerSec);
        } else {
            this.leftMeta = String.format(Locale.ROOT, "%.1f MB downloaded • %.1f MB/s",
                    bytesRead / (1024.0 * 1024.0), speedMbPerSec);
        }
        this.rightMeta = "SHA-256 verification queued";
        this.progress = ratio;
        this.accentA = VIOLET;
        this.accentB = ROSE;
        this.shimmerActive = true;

        ensureVisible();
    }

    @Override
    public void onReady(String version, boolean updated) {
        if (this.headless) return;
        if (!updated && !this.shown) {
            cancelTimers();
            return;
        }
        this.badgeText = "READY";
        this.versionTag = "v" + version;
        this.statusTitle = updated ? "Update verified, launching Minecraft!" : "Up to date, launching Minecraft!";
        this.statusSub = updated ? "SHA-256 matched • Old cache cleaned" : "Latest verified build ready";
        this.pctText = "100%";
        this.leftMeta = updated ? "Update complete" : "Verified cache";
        this.rightMeta = "Loading game...";
        this.progress = 1.0;
        this.accentA = EMERALD_A;
        this.accentB = EMERALD_B;
        this.shimmerActive = false;

        if (updated) {
            ensureVisible();
            sleepQuietly(550);
        } else if (this.shown) {
            triggerRepaint();
            sleepQuietly(250);
        }
    }

    @Override
    public void onOffline(String cachedVersion, String reason) {
        if (this.headless || !this.shown) {
            cancelTimers();
            return;
        }
        this.badgeText = "OFFLINE";
        this.versionTag = "v" + cachedVersion;
        this.statusTitle = "Update server unreachable — using cache";
        this.statusSub = "Launching last verified build (v" + cachedVersion + ")";
        this.pctText = "CACHED";
        this.leftMeta = "Offline fallback active";
        this.rightMeta = "Safe launch";
        this.progress = 1.0;
        this.accentA = AMBER_A;
        this.accentB = AMBER_B;
        this.shimmerActive = false;
        triggerRepaint();
        sleepQuietly(650);
    }

    @Override
    public void onLoaderOutdated(String latestVersion, String releaseUrl) {
        if (this.headless || !this.shown) {
            cancelTimers();
            return;
        }
        this.badgeText = "LOADER";
        this.versionTag = "v" + latestVersion;
        this.statusTitle = "New sestia.jar loader required";
        this.statusSub = "Launching cached build — see in-game notice";
        this.pctText = "UPDATE";
        this.leftMeta = "Continuing safely with cached core";
        this.rightMeta = "Check GitHub Releases";
        this.progress = 1.0;
        this.accentA = AMBER_A;
        this.accentB = AMBER_B;
        this.shimmerActive = false;
        triggerRepaint();
        sleepQuietly(750);
    }

    @Override
    public void close() {
        if (this.headless) return;
        cancelTimers();
        SwingUtilities.invokeLater(() -> {
            if (this.dialog != null) {
                this.dialog.setVisible(false);
                this.dialog.dispose();
                this.dialog = null;
            }
        });
    }

    private void cancelTimers() {
        SwingUtilities.invokeLater(() -> {
            if (this.delayedShowTimer != null) this.delayedShowTimer.stop();
            if (this.animTimer != null) this.animTimer.stop();
        });
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private final class UpdateCanvas extends JPanel {
        private final Font brandFont = new Font("Segoe UI", Font.BOLD, 14);
        private final Font boldFont = new Font("Segoe UI", Font.BOLD, 12);
        private final Font regFont = new Font("Segoe UI", Font.PLAIN, 12);
        private final Font smallFont = new Font("Segoe UI", Font.PLAIN, 11);

        @Override
        protected void paintComponent(Graphics gRaw) {
            Graphics2D g = (Graphics2D) gRaw.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Window rounded card
                RoundRectangle2D.Double winShape = new RoundRectangle2D.Double(1, 1, w - 2, h - 2, 24, 24);
                g.setPaint(new GradientPaint(0, 0, BG_TOP, w, h, BG_BOTTOM));
                g.fill(winShape);

                // Top 2px gradient accent bar clipped to rounded frame
                g.setClip(winShape);
                g.setPaint(new GradientPaint(0, 0, accentA, w, 0, accentB));
                g.fillRect(0, 1, w, 3);
                g.setClip(null);

                g.setColor(OUTER_BORDER);
                g.setStroke(new BasicStroke(1.2f));
                g.draw(winShape);

                // Brand Emblem
                RoundRectangle2D.Double iconRect = new RoundRectangle2D.Double(20, 20, 34, 34, 16, 16);
                g.setPaint(new GradientPaint(20, 20, VIOLET, 54, 54, ROSE));
                g.fill(iconRect);
                g.setColor(BG_BOTTOM);
                g.setFont(new Font("Segoe UI Symbol", Font.BOLD, 16));
                FontMetrics starFm = g.getFontMetrics();
                String star = "\u2726";
                g.drawString(star, 20 + (34 - starFm.stringWidth(star)) / 2, 20 + ((34 - starFm.getHeight()) / 2) + starFm.getAscent());

                // Brand Title + Badge
                g.setFont(brandFont);
                g.setColor(Color.WHITE);
                g.drawString("SCT CLIENT", 64, 34);
                int brandW = g.getFontMetrics().stringWidth("SCT CLIENT");

                g.setFont(smallFont);
                FontMetrics badgeFm = g.getFontMetrics();
                String badge = badgeText;
                int badgeW = badgeFm.stringWidth(badge) + 14;
                int badgeX = 64 + brandW + 8;
                g.setColor(new Color(accentA.getRed(), accentA.getGreen(), accentA.getBlue(), 40));
                g.fillRoundRect(badgeX, 21, badgeW, 18, 18, 18);
                g.setColor(new Color(accentA.getRed(), accentA.getGreen(), accentA.getBlue(), 110));
                g.drawRoundRect(badgeX, 21, badgeW, 18, 18, 18);
                g.setColor(TEXT_SOFT);
                g.drawString(badge, badgeX + 7, 34);

                // Subtitle
                g.setColor(TEXT_MUTED);
                g.drawString("Fabric 26.3 • Auto Updater", 64, 50);

                // Top-right Version Pill
                String ver = versionTag;
                int verW = badgeFm.stringWidth(ver) + 24;
                int verX = w - 20 - verW;
                g.setColor(new Color(255, 255, 255, 14));
                g.fillRoundRect(verX, 24, verW, 22, 22, 22);
                g.setColor(new Color(255, 255, 255, 28));
                g.drawRoundRect(verX, 24, verW, 22, 22, 22);
                g.setColor(accentA);
                g.fillOval(verX + 8, 31, 7, 7);
                g.setColor(new Color(0xCB, 0xD5, 0xE1));
                g.drawString(ver, verX + 19, 39);

                // Center Info Card
                int cardX = 20;
                int cardY = 68;
                int cardW = w - 40;
                int cardH = 54;
                g.setColor(CARD_BG);
                g.fillRoundRect(cardX, cardY, cardW, cardH, 14, 14);
                g.setColor(CARD_BORDER);
                g.drawRoundRect(cardX, cardY, cardW, cardH, 14, 14);

                g.setFont(boldFont);
                g.setColor(Color.WHITE);
                g.drawString(fitText(statusTitle, cardW - 88, g.getFontMetrics()), cardX + 14, cardY + 22);

                g.setColor(accentB);
                FontMetrics pctFm = g.getFontMetrics();
                String pct = pctText;
                g.drawString(pct, cardX + cardW - 14 - pctFm.stringWidth(pct), cardY + 22);

                g.setFont(regFont);
                g.setColor(TEXT_MUTED);
                g.drawString(fitText(statusSub, cardW - 28, g.getFontMetrics()), cardX + 14, cardY + 41);

                // Progress Track
                int trackX = 20;
                int trackY = 136;
                int trackW = w - 40;
                int trackH = 10;
                g.setColor(TRACK_BG);
                g.fillRoundRect(trackX, trackY, trackW, trackH, trackH, trackH);

                int fillW = Math.max(trackH, (int) Math.round(trackW * Math.min(1.0, Math.max(0.0, progress))));
                RoundRectangle2D.Double fillRect = new RoundRectangle2D.Double(trackX, trackY, fillW, trackH, trackH, trackH);
                g.setPaint(new GradientPaint(trackX, trackY, accentA, trackX + fillW, trackY, accentB));
                g.fill(fillRect);

                if (shimmerActive && fillW > 20) {
                    g.setClip(fillRect);
                    int sx = trackX + (int) (shimmerPhase * fillW) - 30;
                    g.setPaint(new GradientPaint(sx, trackY, new Color(255, 255, 255, 0),
                            sx + 30, trackY, new Color(255, 255, 255, 110)));
                    g.fillRect(sx, trackY, 30, trackH);
                    g.setPaint(new GradientPaint(sx + 30, trackY, new Color(255, 255, 255, 110),
                            sx + 60, trackY, new Color(255, 255, 255, 0)));
                    g.fillRect(sx + 30, trackY, 30, trackH);
                    g.setClip(null);
                }

                // Bottom Meta Telemetry
                g.setFont(smallFont);
                g.setColor(TEXT_MUTED);
                g.drawString(leftMeta, 20, 170);

                g.setColor(TEXT_SOFT);
                FontMetrics rightFm = g.getFontMetrics();
                String rMeta = rightMeta;
                g.drawString(rMeta, w - 20 - rightFm.stringWidth(rMeta), 170);
            } finally {
                g.dispose();
            }
        }

        private String fitText(String text, int maxWidth, FontMetrics fm) {
            if (text == null) return "";
            if (fm.stringWidth(text) <= maxWidth) return text;
            String s = text;
            while (s.length() > 3 && fm.stringWidth(s + "...") > maxWidth) {
                s = s.substring(0, s.length() - 1);
            }
            return s + "...";
        }
    }
}
