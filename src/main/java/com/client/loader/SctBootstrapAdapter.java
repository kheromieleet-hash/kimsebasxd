package com.client.loader;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.LanguageAdapterException;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.impl.launch.FabricLauncher;
import net.fabricmc.loader.impl.launch.FabricLauncherBase;

import java.lang.reflect.Constructor;
import java.nio.file.Path;

/**
 * Fabric LanguageAdapter initialized during FabricLoaderImpl.setupLanguageAdapters(),
 * before Mixin bootstrapping. Ensures the newest verified SCTClient-Core JAR is cached
 * and added to KnotClassLoader before Minecraft or Mixins load.
 */
public final class SctBootstrapAdapter implements LanguageAdapter {
    private static final Object INIT_LOCK = new Object();
    private static volatile boolean bootstrapped = false;

    public SctBootstrapAdapter() {
        bootstrapOnce();
    }

    private static void bootstrapOnce() {
        if (bootstrapped) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (bootstrapped) {
                return;
            }
            Path workDir = resolveWorkDir();
            String manifestUrl = SctUpdateConfig.resolveManifestUrl(workDir);
            String releasesUrl = SctUpdateConfig.resolveReleasesPageUrl(workDir);

            try (SctUpdateWindow window = new SctUpdateWindow()) {
                SctUpdater updater = new SctUpdater(
                        workDir,
                        manifestUrl,
                        releasesUrl,
                        () -> SctBootstrapAdapter.class.getResourceAsStream("/assets/sctclient/fallback-core.jar"),
                        window
                );
                Path coreJar = updater.resolveAndEnsureCore();
                if (coreJar != null) {
                    System.setProperty("sct.core.jar.path", coreJar.toAbsolutePath().toString());
                    System.setProperty("sct.work.dir", workDir.toAbsolutePath().toString());
                    FabricLauncher launcher = FabricLauncherBase.getLauncher();
                    if (launcher != null) {
                        launcher.addToClassPath(coreJar);
                    }
                }
            } catch (Throwable ex) {
                System.err.println("[SCTClient Bootloader] Bootstrap error: " + ex.getMessage());
                ex.printStackTrace(System.err);
            } finally {
                bootstrapped = true;
            }
        }
    }

    private static Path resolveWorkDir() {
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            if (gameDir != null) {
                return gameDir.resolve("sctclient");
            }
        } catch (Throwable ignored) {
        }
        return Path.of("sctclient");
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T create(ModContainer mod, String value, Class<T> type) throws LanguageAdapterException {
        try {
            ClassLoader targetCl = FabricLauncherBase.getLauncher().getTargetClassLoader();
            Class<?> cls = Class.forName(value, true, targetCl);
            Constructor<?> ctor = cls.getDeclaredConstructor();
            ctor.setAccessible(true);
            Object instance = ctor.newInstance();
            return (T) instance;
        } catch (ReflectiveOperationException ex) {
            throw new LanguageAdapterException("Failed to instantiate entrypoint: " + value, ex);
        }
    }
}
