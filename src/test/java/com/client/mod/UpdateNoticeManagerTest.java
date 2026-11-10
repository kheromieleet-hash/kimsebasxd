package com.client.mod;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class UpdateNoticeManagerTest {
    public static void main(String[] args) throws Exception {
        testBuildInfoParsingAndFallback();
        testConsumePendingNoticeUpdatedAndSingleConsumption();
        testConsumePendingNoticeOutdatedLoader();
        System.out.println("ALL UpdateNoticeManagerTest ASSERTIONS PASSED!");
    }

    private static void testBuildInfoParsingAndFallback() throws Exception {
        ClientBuildInfo.Meta parsed = ClientBuildInfo.parseProperties(
                new ByteArrayInputStream("version=1.2.4\nversionCode=4\n".getBytes(StandardCharsets.UTF_8))
        );
        assert parsed.version.equals("1.2.4") : "Expected 1.2.4, got " + parsed.version;
        assert parsed.versionCode == 4 : "Expected versionCode 4, got " + parsed.versionCode;

        ClientBuildInfo.Meta fallback = ClientBuildInfo.parseProperties(null);
        assert fallback.version.equals("1.0.0") : "Expected fallback 1.0.0";
        assert fallback.versionCode == 1 : "Expected fallback versionCode 1";
    }

    private static void testConsumePendingNoticeUpdatedAndSingleConsumption() throws Exception {
        Path tmp = Files.createTempDirectory("sct-notice-test-");
        try {
            Path noticeFile = tmp.resolve("pending-notice.json");
            String json = "{\"type\":\"UPDATED\",\"version\":\"1.0.2\",\"previousVersion\":\"1.0.0\",\"changelog\":\"New ClickGUI & HUD\",\"releaseUrl\":\"https://github.com/kheromieallah/sestia/releases/latest\"}";
            Files.writeString(noticeFile, json, StandardCharsets.UTF_8);

            UpdateNoticeManager.Notice first = UpdateNoticeManager.consumePendingNotice(noticeFile);
            assert first != null : "Expected first consume to return notice";
            assert "UPDATED".equals(first.type) : "Expected UPDATED type, got " + first.type;
            assert "1.0.2".equals(first.version) : "Expected 1.0.2, got " + first.version;
            assert "1.0.0".equals(first.previousVersion);
            assert "New ClickGUI & HUD".equals(first.changelog);
            assert !Files.exists(noticeFile) : "Notice file should be removed after consumption";

            UpdateNoticeManager.Notice second = UpdateNoticeManager.consumePendingNotice(noticeFile);
            assert second == null : "Second consume should return null";
        } finally {
            deleteRecursive(tmp);
        }
    }

    private static void testConsumePendingNoticeOutdatedLoader() throws Exception {
        Path tmp = Files.createTempDirectory("sct-notice-outdated-");
        try {
            Path noticeFile = tmp.resolve("pending-notice.json");
            String json = "{\"type\":\"LOADER_OUTDATED\",\"version\":\"2.0.0\",\"previousVersion\":\"1.0.0\",\"changelog\":\"New loader needed\",\"releaseUrl\":\"https://github.com/kheromieallah/sestia/releases/latest\"}";
            Files.writeString(noticeFile, json, StandardCharsets.UTF_8);

            UpdateNoticeManager.Notice notice = UpdateNoticeManager.consumePendingNotice(noticeFile);
            assert notice != null && "LOADER_OUTDATED".equals(notice.type);
            assert notice.releaseUrl.contains("kheromieallah/sestia");
        } finally {
            deleteRecursive(tmp);
        }
    }

    private static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        }
    }
}
