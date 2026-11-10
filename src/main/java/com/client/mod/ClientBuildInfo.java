package com.client.mod;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Exposes the active SCT Client Core version metadata embedded during packaging.
 */
public final class ClientBuildInfo {
    public static final class Meta {
        public final String version;
        public final int versionCode;

        public Meta(String version, int versionCode) {
            this.version = version;
            this.versionCode = versionCode;
        }
    }

    private static volatile Meta cachedMeta;

    private ClientBuildInfo() {
    }

    public static Meta getMeta() {
        Meta m = cachedMeta;
        if (m == null) {
            try (InputStream in = ClientBuildInfo.class.getResourceAsStream("/sct-core-meta.properties")) {
                m = parseProperties(in);
            } catch (IOException ignored) {
                m = new Meta("1.0.0", 1);
            }
            cachedMeta = m;
        }
        return m;
    }

    public static Meta parseProperties(InputStream in) {
        String ver = "1.0.0";
        int code = 1;
        if (in != null) {
            Properties props = new Properties();
            try {
                props.load(in);
                String rawVer = props.getProperty("version");
                if (rawVer != null && !rawVer.isBlank()) {
                    ver = rawVer.trim();
                }
                String rawCode = props.getProperty("versionCode");
                if (rawCode != null && !rawCode.isBlank()) {
                    code = Integer.parseInt(rawCode.trim());
                }
            } catch (Exception ignored) {
            }
        }
        return new Meta(ver, code);
    }

    public static String getVersion() {
        return getMeta().version;
    }

    public static String getDisplayVersion() {
        String ver = getVersion();
        return ver.startsWith("v") ? ver : "v" + ver;
    }

    public static int getVersionCode() {
        return getMeta().versionCode;
    }
}
