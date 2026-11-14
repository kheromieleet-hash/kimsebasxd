package com.client.mod;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Consumes and cleans up any pending bootloader update notice file on startup
 * without displaying in-game toast banners or chat messages.
 */
public final class UpdateNoticeManager {
    public static final class Notice {
        public String type; // "UPDATED" or "LOADER_OUTDATED"
        public String version;
        public String previousVersion;
        public String changelog;
        public String releaseUrl;
    }

    private static final Gson GSON = new Gson();

    private UpdateNoticeManager() {
    }

    public static void loadOnStartup() {
        String workDirProp = System.getProperty("sct.work.dir");
        Path noticeFile = (workDirProp != null && !workDirProp.isBlank())
                ? Path.of(workDirProp).resolve("pending-notice.json")
                : Path.of("sestia", "pending-notice.json");
        consumePendingNotice(noticeFile);
    }

    public static Notice consumePendingNotice(Path noticeFile) {
        if (noticeFile == null || !Files.isRegularFile(noticeFile)) {
            return null;
        }
        try {
            String raw = Files.readString(noticeFile, StandardCharsets.UTF_8);
            Files.deleteIfExists(noticeFile);
            Notice parsed = GSON.fromJson(raw, Notice.class);
            if (parsed == null || parsed.type == null || parsed.type.isBlank()) {
                return null;
            }
            return parsed;
        } catch (Exception ignored) {
            try {
                Files.deleteIfExists(noticeFile);
            } catch (IOException ignoredDelete) {
            }
            return null;
        }
    }
}
