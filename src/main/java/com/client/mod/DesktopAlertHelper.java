package com.client.mod;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cross-platform desktop notification and taskbar/dock attention helper.
 * Plays embedded bubble WAV alert sounds and uses PowerShell NotifyIcon on Windows
 * or osascript notification on macOS, avoiding spawning Windows-only binaries on macOS/Linux.
 */
final class DesktopAlertHelper {
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");
    private static final boolean IS_MAC = OS_NAME.contains("mac");
    private static final long NOTIFY_COOLDOWN_MS = 2000L;
    private static final AtomicLong LAST_NOTIFY_MS = new AtomicLong(0L);
    private static final String MENTION_SOUND_RESOURCE = "/assets/sestia/sounds/bubble_mention.wav";
    private static final String FAILSAFE_SOUND_RESOURCE = "/assets/sestia/sounds/bubble_failsafe.wav";

    private DesktopAlertHelper() {
    }

    static void requestWindowAttention(Minecraft mc) {
        try {
            if (mc != null && mc.getWindow() != null) {
                GLFW.glfwRequestWindowAttention(mc.getWindow().handle());
            }
        } catch (Throwable ignored) {
        }
    }

    static boolean playEmbeddedBubbleSound(boolean warning) {
        String resourcePath = warning ? FAILSAFE_SOUND_RESOURCE : MENTION_SOUND_RESOURCE;
        Clip clip = null;
        try (InputStream raw = DesktopAlertHelper.class.getResourceAsStream(resourcePath)) {
            if (raw == null) {
                return false;
            }
            try (BufferedInputStream bis = new BufferedInputStream(raw);
                 AudioInputStream ais = AudioSystem.getAudioInputStream(bis)) {
                clip = AudioSystem.getClip();
                final Clip openedClip = clip;
                openedClip.addLineListener(event -> {
                    if (event.getType() == LineEvent.Type.STOP) {
                        openedClip.close();
                    }
                });
                openedClip.open(ais);
                openedClip.start();
                return true;
            }
        } catch (Throwable ignored) {
            if (clip != null) {
                try {
                    clip.close();
                } catch (Throwable ignoredClose) {
                }
            }
            return false;
        }
    }

    static void showDesktopNotification(String title, String body, boolean warning, String threadName) {
        if (!IS_WINDOWS && !IS_MAC) {
            return;
        }
        long now = System.currentTimeMillis();
        long prev = LAST_NOTIFY_MS.get();
        if (now - prev < NOTIFY_COOLDOWN_MS || !LAST_NOTIFY_MS.compareAndSet(prev, now)) {
            return;
        }
        final String safeTitle = sanitize(title, 64);
        final String safeBody = sanitize(body, 140);

        Thread alertThread = new Thread(() -> {
            try {
                boolean playedCustom = playEmbeddedBubbleSound(warning);
                if (IS_WINDOWS) {
                    String iconType = warning ? "Warning" : "Information";
                    String tipIcon = warning ? "Warning" : "Info";
                    String soundCmd = playedCustom ? "" : "[System.Media.SystemSounds]::Exclamation.Play(); ";
                    String psScript =
                            "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; "
                            + soundCmd
                            + "$n = New-Object System.Windows.Forms.NotifyIcon; "
                            + "$n.Icon = [System.Drawing.SystemIcons]::" + iconType + "; "
                            + "$n.Visible = $true; "
                            + "$n.ShowBalloonTip(5000, '" + safeTitle + "', '" + safeBody + "', [System.Windows.Forms.ToolTipIcon]::" + tipIcon + "); "
                            + "Start-Sleep -Milliseconds 5200; "
                            + "$n.Dispose()";
                    new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-Command", psScript)
                            .redirectErrorStream(true)
                            .start();
                } else if (IS_MAC) {
                    String soundPart = playedCustom ? "" : " sound name \"Glass\"";
                    String script = "display notification \"" + safeBody + "\" with title \"" + safeTitle + "\"" + soundPart;
                    new ProcessBuilder("osascript", "-e", script)
                            .redirectErrorStream(true)
                            .start();
                }
            } catch (Throwable ignored) {
            }
        }, threadName);
        alertThread.setDaemon(true);
        alertThread.start();
    }

    private static String sanitize(String raw, int maxLen) {
        if (raw == null) {
            return "";
        }
        String clean = raw
                .replace("'", "")
                .replace("\"", "")
                .replace("`", "")
                .replace("\\", "")
                .replace("\r", " ")
                .replace("\n", " ")
                .trim();
        if (clean.length() > maxLen) {
            return clean.substring(0, maxLen - 3) + "...";
        }
        return clean;
    }
}
