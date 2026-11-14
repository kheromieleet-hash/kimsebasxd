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
 * Clean, minimal Minecraft-style startup updater window.
 */
public final class SctUpdateWindow implements SctUpdateListener {
    private static final int WIN_W = 388;
    private static final int WIN_H = 128;

    private static final Color BG_MAIN = new Color(0x1E, 0x1E, 0x1E, 248);
    private static final Color OUTER_BORDER = new Color(0x4A, 0x4A, 0x4A);
    private static final Color INNER_BORDER = new Color(0x2D, 0x2D, 0x2D);
    private static final Color MC_GREEN = new Color(0x3C, 0x85, 0x27);
    private static final Color MC_GREEN_TOP = new Color(0x52, 0xA5, 0x35);
    private static final Color MC_GOLD = new Color(0xE0, 0xA8, 0x2E);
    private static final Color TEXT_PRIMARY = new Color(0xF0, 0xF0, 0xF0);
    private static final Color TEXT_MUTED = new Color(0xA0, 0xA0, 0xA0);
    private static final Color TRACK_BG = new Color(0x11, 0x11, 0x11);
    private static final Color TRACK_BORDER = new Color(0x38, 0x38, 0x38);

    private final boolean headless;
    private JDialog dialog;
    private UpdateCanvas canvas;
    private Timer delayedShowTimer;
    private Timer animTimer;
    private volatile boolean shown = false;

    private volatile String versionTag = "v" + SctUpdateConfig.BASELINE_VERSION;
    private volatile String statusTitle = "Checking for updates...";
    private volatile String statusSub = "Connecting to release server";
    private volatile String pctText = "";
    private volatile String leftMeta = "";
    private volatile double progress = 0.25;
    private volatile Color accentTop = MC_GREEN_TOP;
    private volatile Color accentBottom = MC_GREEN;
    private volatile boolean indeterminate = true;

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
            this.dialog.setBackground(BG_MAIN);
            try {
                this.dialog.setShape(new RoundRectangle2D.Double(0, 0, WIN_W, WIN_H, 8, 8));
            } catch (Throwable ignored) {
            }
        }

        this.canvas = new UpdateCanvas();
        this.canvas.setPreferredSize(new Dimension(WIN_W, WIN_H));
        this.canvas.setOpaque(false);
        this.dialog.setContentPane(this.canvas);

        this.animTimer = new Timer(33, e -> {
            if (this.canvas != null && this.shown && this.indeterminate) {
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
        this.statusTitle = "Checking for updates...";
        this.statusSub = "Connecting to GitHub Releases";
        this.pctText = "";
        this.leftMeta = "Minecraft 26.3 • Sestia";
        this.progress = 0.25;
        this.accentTop = MC_GREEN_TOP;
        this.accentBottom = MC_GREEN;
        this.indeterminate = true;

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

        this.versionTag = "v" + toVer;
        this.statusTitle = "Updating Sestia Client...";
        this.statusSub = "v" + fromVer + " -> v" + toVer + ((changelog != null && !changelog.isBlank()) ? (" • " + changelog) : "");
        this.pctText = totalBytes > 0 ? (pct + "%") : String.format(Locale.ROOT, "%.1f MB", bytesRead / (1024.0 * 1024.0));
        if (totalBytes > 0) {
            this.leftMeta = String.format(Locale.ROOT, "%.1f / %.1f MB (%.1f MB/s)",
                    bytesRead / (1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0), speedMbPerSec);
        } else {
            this.leftMeta = String.format(Locale.ROOT, "%.1f MB (%.1f MB/s)",
                    bytesRead / (1024.0 * 1024.0), speedMbPerSec);
        }
        this.progress = ratio;
        this.accentTop = MC_GREEN_TOP;
        this.accentBottom = MC_GREEN;
        this.indeterminate = false;

        ensureVisible();
    }

    @Override
    public void onReady(String version, boolean updated) {
        if (this.headless) return;
        if (!updated && !this.shown) {
            cancelTimers();
            return;
        }
        this.versionTag = "v" + version;
        this.statusTitle = updated ? "Update ready, launching game..." : "Up to date, launching game...";
        this.statusSub = "Minecraft 26.3 • Sestia v" + version;
        this.pctText = "100%";
        this.leftMeta = updated ? "Verified & ready" : "Cache verified";
        this.progress = 1.0;
        this.accentTop = MC_GREEN_TOP;
        this.accentBottom = MC_GREEN;
        this.indeterminate = false;

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
        this.versionTag = "v" + cachedVersion;
        this.statusTitle = "Offline — launching cached build";
        this.statusSub = "Minecraft 26.3 • Sestia v" + cachedVersion;
        this.pctText = "";
        this.leftMeta = "Cached version";
        this.progress = 1.0;
        this.accentTop = MC_GOLD;
        this.accentBottom = MC_GOLD.darker();
        this.indeterminate = false;
        triggerRepaint();
        sleepQuietly(650);
    }

    @Override
    public void onLoaderOutdated(String latestVersion, String releaseUrl) {
        if (this.headless || !this.shown) {
            cancelTimers();
            return;
        }
        this.versionTag = "v" + latestVersion;
        this.statusTitle = "Loader update recommended";
        this.statusSub = "Launching cached build — see notice in game";
        this.pctText = "";
        this.leftMeta = "New sestia.jar available";
        this.progress = 1.0;
        this.accentTop = MC_GOLD;
        this.accentBottom = MC_GOLD.darker();
        this.indeterminate = false;
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
        private final Font titleFont = new Font("Segoe UI", Font.BOLD, 13);
        private final Font subFont = new Font("Segoe UI", Font.PLAIN, 11);
        private final Font monoFont = new Font("Consolas", Font.BOLD, 11);

        @Override
        protected void paintComponent(Graphics gRaw) {
            Graphics2D g = (Graphics2D) gRaw.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                // Classic clean dark Minecraft launcher panel
                RoundRectangle2D.Double winShape = new RoundRectangle2D.Double(0.5, 0.5, w - 1.0, h - 1.0, 8, 8);
                g.setColor(BG_MAIN);
                g.fill(winShape);
                g.setColor(OUTER_BORDER);
                g.setStroke(new BasicStroke(1.0f));
                g.draw(winShape);

                // Subtle inner bevel line
                g.setColor(INNER_BORDER);
                g.draw(new RoundRectangle2D.Double(1.5, 1.5, w - 3.0, h - 3.0, 7, 7));

                // Title + Version (no logo, clean left alignment with progress track)
                g.setFont(titleFont);
                g.setColor(TEXT_PRIMARY);
                g.drawString(fitText(statusTitle, w - 88, g.getFontMetrics()), 18, 31);

                g.setFont(monoFont);
                g.setColor(TEXT_MUTED);
                FontMetrics verFm = g.getFontMetrics();
                String ver = versionTag;
                g.drawString(ver, w - 18 - verFm.stringWidth(ver), 31);

                // Clean Minecraft-style green progress bar
                int trackX = 18;
                int trackY = 54;
                int trackW = w - 36;
                int trackH = 14;
                g.setColor(TRACK_BG);
                g.fillRect(trackX, trackY, trackW, trackH);
                g.setColor(TRACK_BORDER);
                g.drawRect(trackX, trackY, trackW, trackH);

                if (indeterminate) {
                    int segW = Math.max(48, trackW / 4);
                    long t = System.currentTimeMillis() % 1400L;
                    int segX = trackX + 2 + (int) ((trackW - segW - 4) * (t / 1400.0));
                    g.setPaint(new GradientPaint(segX, trackY + 2, accentTop, segX, trackY + trackH - 2, accentBottom));
                    g.fillRect(segX, trackY + 2, segW, trackH - 3);
                } else {
                    int innerW = Math.max(4, (int) Math.round((trackW - 3) * Math.min(1.0, Math.max(0.0, progress))));
                    g.setPaint(new GradientPaint(trackX + 2, trackY + 2, accentTop, trackX + 2, trackY + trackH - 2, accentBottom));
                    g.fillRect(trackX + 2, trackY + 2, innerW, trackH - 3);
                }

                // Subtitle & percentage row
                g.setFont(subFont);
                g.setColor(TEXT_MUTED);
                g.drawString(fitText(statusSub, w - 90, g.getFontMetrics()), 18, 88);

                g.setFont(monoFont);
                g.setColor(TEXT_PRIMARY);
                FontMetrics pctFm = g.getFontMetrics();
                String pct = pctText;
                g.drawString(pct, w - 18 - pctFm.stringWidth(pct), 88);

                // Quiet bottom footer
                g.setFont(subFont);
                g.setColor(new Color(0x78, 0x78, 0x78));
                g.drawString(fitText(leftMeta, w - 36, g.getFontMetrics()), 18, 109);
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
