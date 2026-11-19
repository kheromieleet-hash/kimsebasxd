package com.client.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Configuration constants and override resolution for the SCT Client bootloader.
 */
public final class SctUpdateConfig {
    public static final int LOADER_CODE = 1;
    public static final String REPO_SLUG = "kheromieleet-hash/kimsebasxd";
    public static final String DEFAULT_MANIFEST_URL =
            "https://github.com/" + REPO_SLUG + "/releases/latest/download/version.json";
    public static final String DEFAULT_RELEASES_URL =
            "https://github.com/" + REPO_SLUG + "/releases/latest";

    public static final String BASELINE_VERSION = "1.0.0";
    public static final int BASELINE_VERSION_CODE = 1;

    private SctUpdateConfig() {
    }

    public static String resolveManifestUrl(Path workDir) {
        String jvmProp = System.getProperty("sct.manifestUrl");
        if (jvmProp != null && !jvmProp.isBlank()) {
            return jvmProp.trim();
        }
        String fileProp = readUpdaterProperty(workDir, "manifestUrl");
        return (fileProp != null && !fileProp.isBlank()) ? fileProp.trim() : DEFAULT_MANIFEST_URL;
    }

    public static String resolveReleasesPageUrl(Path workDir) {
        String jvmProp = System.getProperty("sct.releasesUrl");
        if (jvmProp != null && !jvmProp.isBlank()) {
            return jvmProp.trim();
        }
        String fileProp = readUpdaterProperty(workDir, "releasesUrl");
        return (fileProp != null && !fileProp.isBlank()) ? fileProp.trim() : DEFAULT_RELEASES_URL;
    }

    private static String readUpdaterProperty(Path workDir, String key) {
        if (workDir == null) {
            return null;
        }
        Path propsPath = workDir.resolve("updater.properties");
        if (!Files.isRegularFile(propsPath)) {
            return null;
        }
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(propsPath)) {
            props.load(in);
            return props.getProperty(key);
        } catch (IOException ignored) {
            return null;
        }
    }
}
