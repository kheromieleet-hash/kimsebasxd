package com.client.mod;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Cross-platform desktop notification and taskbar/dock attention helper.
 * Uses PowerShell NotifyIcon on Windows and osascript notification on macOS,
 * avoiding spawning Windows-only binaries on macOS/Linux.
 */
final class DesktopAlertHelper {
    private static final String OS_NAME = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    private static final boolean IS_WINDOWS = OS_NAME.contains("win");
    private static final boolean IS_MAC = OS_NAME.contains("mac");
    private static final long NOTIFY_COOLDOWN_MS = 2000L;
    private static final AtomicLong LAST_NOTIFY_MS = new AtomicLong(0L);

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
                if (IS_WINDOWS) {
                    String iconType = warning ? "Warning" : "Information";
                    String tipIcon = warning ? "Warning" : "Info";
                    String psScript =
                            "Add-Type -AssemblyName System.Windows.Forms,System.Drawing; "
                            + "[System.Media.SystemSounds]::Exclamation.Play(); "
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
                    String script = "display notification \"" + safeBody + "\" with title \"" + safeTitle + "\" sound name \"Glass\"";
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
