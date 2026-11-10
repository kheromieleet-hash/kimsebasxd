package com.client.loader;

/**
 * Callback interface decoupling bootloader update logic from desktop UI rendering.
 */
public interface SctUpdateListener extends AutoCloseable {
    void onChecking();

    void onDownloading(String fromVer, String toVer, String changelog, long bytesRead, long totalBytes, double speedMbPerSec);

    void onReady(String version, boolean updated);

    void onOffline(String cachedVersion, String reason);

    void onLoaderOutdated(String latestVersion, String releaseUrl);

    @Override
    void close();

    SctUpdateListener NO_OP = new SctUpdateListener() {
        @Override
        public void onChecking() {}

        @Override
        public void onDownloading(String fromVer, String toVer, String changelog, long bytesRead, long totalBytes, double speedMbPerSec) {}

        @Override
        public void onReady(String version, boolean updated) {}

        @Override
        public void onOffline(String cachedVersion, String reason) {}

        @Override
        public void onLoaderOutdated(String latestVersion, String releaseUrl) {}

        @Override
        public void close() {}
    };
}
