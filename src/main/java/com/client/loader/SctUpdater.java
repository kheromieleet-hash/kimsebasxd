package com.client.loader;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Resolves, downloads, SHA-256 verifies, and caches the active SCT Client Core JAR.
 */
public final class SctUpdater {
    @FunctionalInterface
    public interface FallbackSupplier {
        InputStream openFallbackStream() throws IOException;
    }

    public static final class RemoteManifest {
        public int versionCode;
        public String version;
        public String changelog;
        public String downloadUrl;
        public String sha256;
        public long sizeBytes;
        public int minLoaderCode = 1;
        public String loaderUrl;
    }

    public static final class LocalState {
        public int versionCode;
        public String version;
        public String coreFile;
        public String sha256;
    }

    public static final class PendingNotice {
        public String type; // "UPDATED" or "LOADER_OUTDATED"
        public String version;
        public String previousVersion;
        public String changelog;
        public String releaseUrl;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(2500);
    private static final Duration MANIFEST_TIMEOUT = Duration.ofMillis(4000);
    private static final Duration DOWNLOAD_TIMEOUT = Duration.ofSeconds(30);

    private final Path workDir;
    private final Path coreDir;
    private final Path stateFile;
    private final Path pendingNoticeFile;
    private final String manifestUrl;
    private final String releasesPageUrl;
    private final FallbackSupplier fallbackSupplier;
    private final SctUpdateListener listener;
    private final HttpClient http;

    public SctUpdater(
            Path workDir,
            String manifestUrl,
            String releasesPageUrl,
            FallbackSupplier fallbackSupplier,
            SctUpdateListener listener
    ) {
        this.workDir = workDir.toAbsolutePath().normalize();
        this.coreDir = this.workDir.resolve("core").normalize();
        this.stateFile = this.workDir.resolve("state.json");
        this.pendingNoticeFile = this.workDir.resolve("pending-notice.json");
        this.manifestUrl = manifestUrl;
        this.releasesPageUrl = releasesPageUrl;
        this.fallbackSupplier = fallbackSupplier;
        this.listener = listener != null ? listener : SctUpdateListener.NO_OP;
        this.http = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
    }

    public Path resolveAndEnsureCore() throws IOException {
        Files.createDirectories(this.coreDir);
        cleanupLeftoverParts();

        LocalState localState = readLocalState();
        Path localCore = validateLocalCore(localState);

        this.listener.onChecking();
        RemoteManifest remote = null;
        String failureReason = null;
        try {
            remote = fetchManifest();
        } catch (Exception ex) {
            failureReason = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        }

        if (remote == null || remote.downloadUrl == null || remote.downloadUrl.isBlank()) {
            Path safeCore = ensureBaselineIfMissing(localCore, localState);
            LocalState finalState = readLocalState();
            String ver = finalState != null && finalState.version != null ? finalState.version : SctUpdateConfig.BASELINE_VERSION;
            this.listener.onOffline(ver, failureReason != null ? failureReason : "Manifest unavailable");
            this.listener.close();
            return safeCore;
        }

        String targetVer = sanitizeVersionFileSegment(remote.version);
        if (remote.minLoaderCode > SctUpdateConfig.LOADER_CODE) {
            Path safeCore = ensureBaselineIfMissing(localCore, localState);
            String url = (remote.loaderUrl != null && !remote.loaderUrl.isBlank()) ? remote.loaderUrl : this.releasesPageUrl;
            writeNotice("LOADER_OUTDATED", targetVer, localState != null ? localState.version : SctUpdateConfig.BASELINE_VERSION,
                    "New SCTClient.jar loader required for v" + targetVer, url);
            this.listener.onLoaderOutdated(targetVer, url);
            this.listener.close();
            return safeCore;
        }

        if (!isValidSha256Hex(remote.sha256)) {
            Path safeCore = ensureBaselineIfMissing(localCore, localState);
            LocalState finalState = readLocalState();
            String ver = finalState != null && finalState.version != null ? finalState.version : SctUpdateConfig.BASELINE_VERSION;
            this.listener.onOffline(ver, "Manifest missing valid SHA-256 digest");
            this.listener.close();
            return safeCore;
        }

        boolean sameVersionAndValid = localCore != null
                && localState != null
                && localState.versionCode >= remote.versionCode
                && remote.sha256.trim().equalsIgnoreCase(localState.sha256);

        if (sameVersionAndValid) {
            this.listener.onReady(localState.version != null ? localState.version : targetVer, false);
            this.listener.close();
            return localCore;
        }

        // Needs update or initial download
        String prevVer = localState != null && localState.version != null ? localState.version : "initial";
        try {
            Path targetJar = safeCorePath(targetVer);
            Path partJar = this.coreDir.resolve(targetJar.getFileName().toString() + ".part").normalize();
            downloadAndVerify(remote, prevVer, targetVer, partJar, targetJar);

            LocalState newState = new LocalState();
            newState.versionCode = remote.versionCode;
            newState.version = targetVer;
            newState.coreFile = targetJar.getFileName().toString();
            newState.sha256 = remote.sha256.trim().toLowerCase(Locale.ROOT);
            writeLocalState(newState);

            pruneOldCoreJars(targetJar);
            writeNotice("UPDATED", targetVer, prevVer, remote.changelog != null ? remote.changelog : "Client updated", this.releasesPageUrl);

            this.listener.onReady(targetVer, true);
            this.listener.close();
            return targetJar;
        } catch (Exception ex) {
            cleanupLeftoverParts();
            Path safeCore = ensureBaselineIfMissing(localCore, localState);
            LocalState fallbackState = readLocalState();
            String ver = fallbackState != null && fallbackState.version != null ? fallbackState.version : SctUpdateConfig.BASELINE_VERSION;
            this.listener.onOffline(ver, ex.getMessage() != null ? ex.getMessage() : "Download failed");
            this.listener.close();
            return safeCore;
        }
    }

    private RemoteManifest fetchManifest() throws IOException, InterruptedException {
        URI manifestUri = validateTransportUrl(this.manifestUrl);
        HttpRequest req = HttpRequest.newBuilder(manifestUri)
                .timeout(MANIFEST_TIMEOUT)
                .header("User-Agent", "SCTClient-Bootloader/" + SctUpdateConfig.LOADER_CODE)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> resp = this.http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " fetching manifest");
        }
        RemoteManifest manifest = GSON.fromJson(resp.body(), RemoteManifest.class);
        if (manifest == null || manifest.version == null || manifest.version.isBlank()) {
            throw new IOException("Invalid version manifest payload");
        }
        return manifest;
    }

    private void downloadAndVerify(RemoteManifest remote, String fromVer, String toVer, Path partJar, Path targetJar)
            throws IOException, InterruptedException {
        if (!isValidSha256Hex(remote.sha256)) {
            throw new IOException("Refusing unverified download without valid 64-hex SHA-256");
        }
        URI downloadUri = validateTransportUrl(remote.downloadUrl);

        Files.deleteIfExists(partJar);
        HttpRequest req = HttpRequest.newBuilder(downloadUri)
                .timeout(DOWNLOAD_TIMEOUT)
                .header("User-Agent", "SCTClient-Bootloader/" + SctUpdateConfig.LOADER_CODE)
                .GET()
                .build();
        HttpResponse<InputStream> resp = this.http.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IOException("HTTP " + resp.statusCode() + " downloading core jar");
        }

        long contentLength = resp.headers().firstValueAsLong("Content-Length").orElse(remote.sizeBytes);
        String changelog = remote.changelog != null ? remote.changelog : "Updating client build";
        MessageDigest digest = sha256Digest();

        try (InputStream body = new DigestInputStream(resp.body(), digest);
             OutputStream out = Files.newOutputStream(partJar)) {
            byte[] buf = new byte[16384];
            long totalRead = 0L;
            long startNs = System.nanoTime();
            long lastNotifyNs = 0L;
            int n;
            while ((n = body.read(buf)) != -1) {
                out.write(buf, 0, n);
                totalRead += n;
                long nowNs = System.nanoTime();
                if (nowNs - lastNotifyNs >= 45_000_000L || (contentLength > 0 && totalRead >= contentLength)) {
                    double elapsedSec = Math.max(0.05, (nowNs - startNs) / 1.0e9);
                    double mbPerSec = (totalRead / (1024.0 * 1024.0)) / elapsedSec;
                    this.listener.onDownloading(fromVer, toVer, changelog, totalRead, contentLength, mbPerSec);
                    lastNotifyNs = nowNs;
                }
            }
            if (totalRead == 0) {
                throw new IOException("Empty download payload");
            }
        } catch (IOException ex) {
            Files.deleteIfExists(partJar);
            throw ex;
        }

        String actualHash = HexFormat.of().formatHex(digest.digest());
        if (!actualHash.equalsIgnoreCase(remote.sha256.trim())) {
            Files.deleteIfExists(partJar);
            throw new IOException("SHA-256 verification failed");
        }

        Files.move(partJar, targetJar, StandardCopyOption.REPLACE_EXISTING);
    }

    public static boolean isValidSha256Hex(String sha256) {
        if (sha256 == null) return false;
        String trimmed = sha256.trim();
        if (trimmed.length() != 64) return false;
        for (int i = 0; i < 64; i++) {
            char c = trimmed.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) return false;
        }
        return true;
    }

    public static URI validateTransportUrl(String rawUrl) throws IOException {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IOException("Empty URL");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        } catch (IllegalArgumentException ex) {
            throw new IOException("Malformed URL: " + rawUrl, ex);
        }
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        String host = uri.getHost() != null ? uri.getHost().toLowerCase(Locale.ROOT) : "";
        if ("https".equals(scheme)) {
            return uri;
        }
        if ("http".equals(scheme) && ("127.0.0.1".equals(host) || "localhost".equals(host) || "[::1]".equals(host))) {
            return uri;
        }
        throw new IOException("Insecure non-HTTPS URL rejected: " + rawUrl);
    }

    private Path ensureBaselineIfMissing(Path localCore, LocalState localState) throws IOException {
        if (localCore != null && Files.isRegularFile(localCore)) {
            return localCore;
        }
        if (this.fallbackSupplier == null) {
            throw new IOException("No cached core jar and no embedded fallback available");
        }
        Path baselinePath = safeCorePath(SctUpdateConfig.BASELINE_VERSION);
        Path baselinePart = this.coreDir.resolve(baselinePath.getFileName().toString() + ".part").normalize();
        try (InputStream in = this.fallbackSupplier.openFallbackStream()) {
            if (in == null) {
                throw new IOException("Embedded fallback core stream is null");
            }
            Files.copy(in, baselinePart, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.move(baselinePart, baselinePath, StandardCopyOption.REPLACE_EXISTING);

        LocalState fallbackState = new LocalState();
        fallbackState.versionCode = SctUpdateConfig.BASELINE_VERSION_CODE;
        fallbackState.version = SctUpdateConfig.BASELINE_VERSION;
        fallbackState.coreFile = baselinePath.getFileName().toString();
        fallbackState.sha256 = computeSha256(baselinePath);
        writeLocalState(fallbackState);
        return baselinePath;
    }

    private Path validateLocalCore(LocalState state) {
        if (state == null || state.coreFile == null || state.coreFile.isBlank()) {
            return null;
        }
        String cleanName = sanitizeFileName(state.coreFile);
        Path candidate = this.coreDir.resolve(cleanName).normalize();
        if (!candidate.startsWith(this.coreDir) || !Files.isRegularFile(candidate)) {
            return null;
        }
        try {
            if (!isValidSha256Hex(state.sha256)) {
                return null;
            }
            String actual = computeSha256(candidate);
            if (!actual.equalsIgnoreCase(state.sha256.trim())) {
                return null;
            }
            return candidate;
        } catch (IOException ignored) {
            return null;
        }
    }

    private Path safeCorePath(String sanitizedVersion) throws IOException {
        String safeSeg = sanitizeVersionFileSegment(sanitizedVersion);
        Path resolved = this.coreDir.resolve("SCTClient-Core-" + safeSeg + ".jar").normalize();
        if (!resolved.startsWith(this.coreDir)) {
            throw new IOException("Refusing unsafe core path outside core directory");
        }
        return resolved;
    }

    public static String sanitizeVersionFileSegment(String version) {
        if (version == null) {
            return "unknown";
        }
        String replaced = version.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
        while (replaced.contains("..")) {
            replaced = replaced.replace("..", ".");
        }
        replaced = replaced.replaceAll("^[._-]+|[._-]+$", "");
        return replaced.isEmpty() ? "unknown" : replaced;
    }

    private static String sanitizeFileName(String fileName) {
        String nameOnly = fileName.replace('\\', '/');
        int slash = nameOnly.lastIndexOf('/');
        if (slash >= 0) {
            nameOnly = nameOnly.substring(slash + 1);
        }
        String clean = nameOnly.replaceAll("[^a-zA-Z0-9._-]", "_");
        while (clean.contains("..")) {
            clean = clean.replace("..", ".");
        }
        return clean.isEmpty() ? "SCTClient-Core.jar" : clean;
    }

    private void cleanupLeftoverParts() {
        if (!Files.isDirectory(this.coreDir)) return;
        try (var stream = Files.list(this.coreDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".part")).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
        }
    }

    private void pruneOldCoreJars(Path activeJar) {
        if (!Files.isDirectory(this.coreDir)) return;
        Path normActive = activeJar.toAbsolutePath().normalize();
        try (var stream = Files.list(this.coreDir)) {
            stream.filter(p -> p.getFileName().toString().startsWith("SCTClient-Core-") && p.getFileName().toString().endsWith(".jar"))
                    .filter(p -> !p.toAbsolutePath().normalize().equals(normActive))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {
        }
    }

    private LocalState readLocalState() {
        if (!Files.isRegularFile(this.stateFile)) return null;
        try {
            return GSON.fromJson(Files.readString(this.stateFile, StandardCharsets.UTF_8), LocalState.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeLocalState(LocalState state) throws IOException {
        Files.createDirectories(this.workDir);
        Files.writeString(this.stateFile, GSON.toJson(state), StandardCharsets.UTF_8);
    }

    private void writeNotice(String type, String version, String previousVersion, String changelog, String releaseUrl) {
        try {
            PendingNotice notice = new PendingNotice();
            notice.type = type;
            notice.version = version;
            notice.previousVersion = previousVersion;
            notice.changelog = changelog;
            notice.releaseUrl = releaseUrl;
            Files.createDirectories(this.workDir);
            Files.writeString(this.pendingNoticeFile, GSON.toJson(notice), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static String computeSha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm missing", ex);
        }
    }
}
