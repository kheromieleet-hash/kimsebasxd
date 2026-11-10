package com.client.loader;

import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class SctUpdaterTest {
    private static final class RecordingListener implements SctUpdateListener {
        final List<String> events = new ArrayList<>();

        @Override
        public void onChecking() {
            events.add("checking");
        }

        @Override
        public void onDownloading(String fromVer, String toVer, String changelog, long bytesRead, long totalBytes, double speedMbPerSec) {
            if (events.isEmpty() || !events.get(events.size() - 1).startsWith("downloading:" + toVer)) {
                events.add("downloading:" + toVer);
            }
        }

        @Override
        public void onReady(String version, boolean updated) {
            events.add("ready:" + version + ":" + updated);
        }

        @Override
        public void onOffline(String cachedVersion, String reason) {
            events.add("offline:" + cachedVersion);
        }

        @Override
        public void onLoaderOutdated(String latestVersion, String releaseUrl) {
            events.add("loader_outdated:" + latestVersion);
        }

        @Override
        public void close() {
            events.add("closed");
        }
    }

    public static void main(String[] args) throws Exception {
        testFirstRunOfflineExtractsFallback();
        testHttpRedirectDownloadAndSha256AndNotice();
        testCacheHitSkipsDownload();
        testCorruptSha256PreservesCache();
        testEmptySha256Rejected();
        testNonLocalHttpRejected();
        testMinLoaderCodeTooHighWritesOutdatedNotice();
        testMaliciousVersionStringSanitized();
        System.out.println("ALL SctUpdaterTest ASSERTIONS PASSED!");
    }

    private static void testFirstRunOfflineExtractsFallback() throws Exception {
        Path workDir = Files.createTempDirectory("sct-test-offline-");
        try {
            byte[] fallbackBytes = "FAKE-CORE-JAR-1.0.0".getBytes(StandardCharsets.UTF_8);
            RecordingListener listener = new RecordingListener();
            SctUpdater updater = new SctUpdater(
                    workDir,
                    "http://127.0.0.1:1/version.json",
                    "https://github.com/kheromieallah/sestia/releases/latest",
                    () -> new ByteArrayInputStream(fallbackBytes),
                    listener
            );
            Path resolved = updater.resolveAndEnsureCore();
            assert resolved != null && Files.exists(resolved) : "Expected fallback core jar to be extracted";
            assert Files.readString(resolved).equals("FAKE-CORE-JAR-1.0.0") : "Fallback bytes mismatch";
            assert Files.exists(workDir.resolve("state.json")) : "Expected state.json to be written";
            assert !Files.exists(workDir.resolve("pending-notice.json")) : "First offline extract should not create update notice";
        } finally {
            deleteRecursive(workDir);
        }
    }

    private static void testHttpRedirectDownloadAndSha256AndNotice() throws Exception {
        Path workDir = Files.createTempDirectory("sct-test-redirect-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            byte[] oldFallback = "CORE-1.0.0".getBytes(StandardCharsets.UTF_8);
            byte[] newCoreBytes = "CORE-1.0.2-NEW-PAYLOAD".getBytes(StandardCharsets.UTF_8);
            String sha256 = sha256Hex(newCoreBytes);
            int port = server.getAddress().getPort();

            String manifest = String.format(
                    "{\"versionCode\":2,\"version\":\"1.0.2\",\"changelog\":\"New HUD + Fix\",\"downloadUrl\":\"http://127.0.0.1:%d/redirect\",\"sha256\":\"%s\",\"sizeBytes\":%d,\"minLoaderCode\":1}",
                    port, sha256, newCoreBytes.length
            );

            server.createContext("/version.json", ex -> {
                byte[] resp = manifest.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            });
            server.createContext("/redirect", ex -> {
                ex.getResponseHeaders().add("Location", "http://127.0.0.1:" + port + "/cdn/sestia-core.jar");
                ex.sendResponseHeaders(302, -1);
                ex.close();
            });
            server.createContext("/cdn/sestia-core.jar", ex -> {
                ex.sendResponseHeaders(200, newCoreBytes.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(newCoreBytes); }
            });
            server.start();

            RecordingListener listener = new RecordingListener();
            SctUpdater updater = new SctUpdater(
                    workDir,
                    "http://127.0.0.1:" + port + "/version.json",
                    "https://github.com/kheromieallah/sestia/releases/latest",
                    () -> new ByteArrayInputStream(oldFallback),
                    listener
            );

            Path resolved = updater.resolveAndEnsureCore();
            assert resolved.getFileName().toString().equals("sestia-core-1.0.2.jar") : "Unexpected filename: " + resolved;
            assert Files.readString(resolved).equals("CORE-1.0.2-NEW-PAYLOAD") : "Updated payload mismatch";
            assert !Files.exists(workDir.resolve("core").resolve("sestia-core-1.0.0.jar")) : "Old cached core should be pruned";
            assert Files.exists(workDir.resolve("pending-notice.json")) : "Expected pending-notice.json after update";
            String noticeJson = Files.readString(workDir.resolve("pending-notice.json"));
            assert noticeJson.contains("\"UPDATED\"") && noticeJson.contains("1.0.2") : "Invalid notice JSON: " + noticeJson;
            assert listener.events.contains("downloading:1.0.2") : "Listener missing downloading event: " + listener.events;
            assert listener.events.contains("ready:1.0.2:true") : "Listener missing ready event: " + listener.events;
        } finally {
            server.stop(0);
            deleteRecursive(workDir);
        }
    }

    private static void testCacheHitSkipsDownload() throws Exception {
        Path workDir = Files.createTempDirectory("sct-test-cachehit-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            byte[] coreBytes = "CORE-1.0.2-CACHED".getBytes(StandardCharsets.UTF_8);
            String sha256 = sha256Hex(coreBytes);
            int port = server.getAddress().getPort();
            AtomicInteger downloadCount = new AtomicInteger(0);

            String manifest = String.format(
                    "{\"versionCode\":2,\"version\":\"1.0.2\",\"changelog\":\"Same\",\"downloadUrl\":\"http://127.0.0.1:%d/core.jar\",\"sha256\":\"%s\",\"sizeBytes\":%d,\"minLoaderCode\":1}",
                    port, sha256, coreBytes.length
            );

            server.createContext("/version.json", ex -> {
                byte[] resp = manifest.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            });
            server.createContext("/core.jar", ex -> {
                downloadCount.incrementAndGet();
                ex.sendResponseHeaders(200, coreBytes.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(coreBytes); }
            });
            server.start();

            SctUpdater firstRun = new SctUpdater(
                    workDir, "http://127.0.0.1:" + port + "/version.json",
                    "https://github.com/kheromieallah/sestia/releases/latest",
                    () -> new ByteArrayInputStream("INIT".getBytes(StandardCharsets.UTF_8)),
                    new RecordingListener()
            );
            firstRun.resolveAndEnsureCore();
            assert downloadCount.get() == 1 : "Expected 1 download on first run";
            Files.deleteIfExists(workDir.resolve("pending-notice.json"));

            RecordingListener secondListener = new RecordingListener();
            SctUpdater secondRun = new SctUpdater(
                    workDir, "http://127.0.0.1:" + port + "/version.json",
                    "https://github.com/kheromieallah/sestia/releases/latest",
                    () -> new ByteArrayInputStream("INIT".getBytes(StandardCharsets.UTF_8)),
                    secondListener
            );
            Path secondPath = secondRun.resolveAndEnsureCore();
            assert downloadCount.get() == 1 : "Expected zero new downloads on cache hit, got " + downloadCount.get();
            assert Files.readString(secondPath).equals("CORE-1.0.2-CACHED");
            assert !Files.exists(workDir.resolve("pending-notice.json")) : "Cache hit must not emit notice";
            assert secondListener.events.contains("ready:1.0.2:false");
        } finally {
            server.stop(0);
            deleteRecursive(workDir);
        }
    }

    private static void testCorruptSha256PreservesCache() throws Exception {
        Path workDir = Files.createTempDirectory("sct-test-corrupt-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            byte[] baseline = "VALID-OLD-1.0.0".getBytes(StandardCharsets.UTF_8);
            byte[] tampered = "EVIL-TAMPERED-PAYLOAD".getBytes(StandardCharsets.UTF_8);
            String expectedSha = sha256Hex("VALID-NEW-1.0.3".getBytes(StandardCharsets.UTF_8));
            int port = server.getAddress().getPort();

            String manifest = String.format(
                    "{\"versionCode\":3,\"version\":\"1.0.3\",\"changelog\":\"Bad hash\",\"downloadUrl\":\"http://127.0.0.1:%d/core.jar\",\"sha256\":\"%s\",\"sizeBytes\":%d,\"minLoaderCode\":1}",
                    port, expectedSha, tampered.length
            );

            server.createContext("/version.json", ex -> {
                byte[] resp = manifest.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            });
            server.createContext("/core.jar", ex -> {
                ex.sendResponseHeaders(200, tampered.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(tampered); }
            });
            server.start();

            SctUpdater updater = new SctUpdater(
                    workDir, "http://127.0.0.1:" + port + "/version.json",
                    "https://github.com/kheromieallah/sestia/releases/latest",
                    () -> new ByteArrayInputStream(baseline),
                    new RecordingListener()
            );
            Path resolved = updater.resolveAndEnsureCore();
            assert Files.readString(resolved).equals("VALID-OLD-1.0.0") : "Must fall back to intact baseline on checksum mismatch";
            assert !Files.exists(workDir.resolve("core").resolve("sestia-core-1.0.3.jar")) : "Tampered file must not be kept";
            assert Files.list(workDir.resolve("core")).noneMatch(p -> p.toString().endsWith(".part")) : "Leftover .part file must be removed";
        } finally {
            server.stop(0);
            deleteRecursive(workDir);
        }
    }

    private static void testEmptySha256Rejected() throws Exception {
        Path workDir = Files.createTempDirectory("sct-test-emptysha-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            byte[] baseline = "VALID-OLD-1.0.0".getBytes(StandardCharsets.UTF_8);
            AtomicInteger downloads = new AtomicInteger(0);
            int port = server.getAddress().getPort();

            String manifest = String.format(
                    "{\"versionCode\":3,\"version\":\"1.0.3\",\"changelog\":\"Empty sha\",\"downloadUrl\":\"http://127.0.0.1:%d/core.jar\",\"sha256\":\"\",\"minLoaderCode\":1}",
                    port
            );

            server.createContext("/version.json", ex -> {
                byte[] resp = manifest.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            });
            server.createContext("/core.jar", ex -> {
                downloads.incrementAndGet();
                ex.sendResponseHeaders(200, 0);
                ex.close();
            });
            server.start();

            SctUpdater updater = new SctUpdater(
                    workDir, "http://127.0.0.1:" + port + "/version.json",
                    "https://github.com/kheromieallah/sestia/releases/latest",
                    () -> new ByteArrayInputStream(baseline),
                    new RecordingListener()
            );
            Path resolved = updater.resolveAndEnsureCore();
            assert downloads.get() == 0 : "Must refuse download when manifest lacks SHA-256";
            assert Files.readString(resolved).equals("VALID-OLD-1.0.0");
        } finally {
            server.stop(0);
            deleteRecursive(workDir);
        }
    }

    private static void testNonLocalHttpRejected() throws Exception {
        Path workDir = Files.createTempDirectory("sct-test-nohttps-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            byte[] baseline = "VALID-OLD-1.0.0".getBytes(StandardCharsets.UTF_8);
            String sha = sha256Hex("REMOTE".getBytes(StandardCharsets.UTF_8));
            int port = server.getAddress().getPort();

            String manifest = String.format(
                    "{\"versionCode\":4,\"version\":\"1.0.4\",\"changelog\":\"Cleartext\",\"downloadUrl\":\"http://example.com/core.jar\",\"sha256\":\"%s\",\"minLoaderCode\":1}",
                    sha
            );
            server.createContext("/version.json", ex -> {
                byte[] resp = manifest.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            });
            server.start();

            RecordingListener listener = new RecordingListener();
            SctUpdater updater = new SctUpdater(
                    workDir, "http://127.0.0.1:" + port + "/version.json",
                    "https://github.com/kheromieallah/sestia/releases/latest",
                    () -> new ByteArrayInputStream(baseline),
                    listener
            );
            Path resolved = updater.resolveAndEnsureCore();
            assert Files.readString(resolved).equals("VALID-OLD-1.0.0") : "Cleartext non-localhost downloadUrl must fail cleanly to baseline";
            assert listener.events.stream().anyMatch(e -> e.startsWith("offline:"));
        } finally {
            server.stop(0);
            deleteRecursive(workDir);
        }
    }

    private static void testMinLoaderCodeTooHighWritesOutdatedNotice() throws Exception {
        Path workDir = Files.createTempDirectory("sct-test-minloader-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            byte[] baseline = "VALID-OLD-1.0.0".getBytes(StandardCharsets.UTF_8);
            AtomicInteger downloadCount = new AtomicInteger(0);
            int port = server.getAddress().getPort();

            String manifest = String.format(
                    "{\"versionCode\":9,\"version\":\"2.0.0\",\"changelog\":\"Major\",\"downloadUrl\":\"http://127.0.0.1:%d/core.jar\",\"sha256\":\"deadbeef\",\"minLoaderCode\":99,\"loaderUrl\":\"https://github.com/kheromieallah/sestia/releases/latest\"}",
                    port
            );

            server.createContext("/version.json", ex -> {
                byte[] resp = manifest.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            });
            server.createContext("/core.jar", ex -> {
                downloadCount.incrementAndGet();
                ex.sendResponseHeaders(200, 0);
                ex.close();
            });
            server.start();

            RecordingListener listener = new RecordingListener();
            SctUpdater updater = new SctUpdater(
                    workDir, "http://127.0.0.1:" + port + "/version.json",
                    "https://github.com/kheromieallah/sestia/releases/latest",
                    () -> new ByteArrayInputStream(baseline),
                    listener
            );
            Path resolved = updater.resolveAndEnsureCore();
            assert downloadCount.get() == 0 : "Must not download incompatible core";
            assert Files.readString(resolved).equals("VALID-OLD-1.0.0") : "Must still boot baseline safely";
            assert listener.events.contains("loader_outdated:2.0.0");
            String notice = Files.readString(workDir.resolve("pending-notice.json"));
            assert notice.contains("LOADER_OUTDATED") && notice.contains("2.0.0") : "Missing LOADER_OUTDATED notice: " + notice;
        } finally {
            server.stop(0);
            deleteRecursive(workDir);
        }
    }

    private static void testMaliciousVersionStringSanitized() throws Exception {
        Path workDir = Files.createTempDirectory("sct-test-traversal-");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        try {
            byte[] payload = "SAFE-CONTAINED".getBytes(StandardCharsets.UTF_8);
            String sha = sha256Hex(payload);
            int port = server.getAddress().getPort();
            String manifest = String.format(
                    "{\"versionCode\":5,\"version\":\"../../mods/evil\",\"changelog\":\"Test\",\"downloadUrl\":\"http://127.0.0.1:%d/core.jar\",\"sha256\":\"%s\",\"minLoaderCode\":1}",
                    port, sha
            );
            server.createContext("/version.json", ex -> {
                byte[] resp = manifest.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, resp.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(resp); }
            });
            server.createContext("/core.jar", ex -> {
                ex.sendResponseHeaders(200, payload.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(payload); }
            });
            server.start();

            SctUpdater updater = new SctUpdater(
                    workDir, "http://127.0.0.1:" + port + "/version.json",
                    "https://github.com/kheromieallah/sestia/releases/latest",
                    () -> new ByteArrayInputStream("INIT".getBytes(StandardCharsets.UTF_8)),
                    new RecordingListener()
            );
            Path resolved = updater.resolveAndEnsureCore();
            Path coreDir = workDir.resolve("core").toAbsolutePath().normalize();
            assert resolved.toAbsolutePath().normalize().startsWith(coreDir) : "Path escaped coreDir: " + resolved;
        } finally {
            server.stop(0);
            deleteRecursive(workDir);
        }
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(data));
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
