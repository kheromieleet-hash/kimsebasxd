package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Reflection and dynamic-proxy bridge to the nested IAS (In-Game Account Switcher) runtime.
 * Keeps compile-time independence while delegating storage, crypto, and OAuth to IAS.
 */
public final class IasBridge {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sestia/IasBridge");
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Field> FIELD_CACHE = new ConcurrentHashMap<>();

    private IasBridge() {
    }

    private static Class<?> loadClass(String className) throws ClassNotFoundException {
        Class<?> cached = CLASS_CACHE.get(className);
        if (cached != null) {
            return cached;
        }
        Class<?> loaded = Class.forName(className);
        CLASS_CACHE.putIfAbsent(className, loaded);
        return loaded;
    }

    private static Method cachedMethod(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        String key;
        if (params.length == 0) {
            key = owner.getName() + '#' + name + ":0";
        } else {
            StringBuilder sb = new StringBuilder(owner.getName()).append('#').append(name);
            for (Class<?> param : params) {
                sb.append(':').append(param.getName());
            }
            key = sb.toString();
        }
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Method m = owner.getMethod(name, params);
        METHOD_CACHE.putIfAbsent(key, m);
        return m;
    }

    private static Field cachedField(Class<?> owner, String name, boolean declared) throws NoSuchFieldException {
        String key = owner.getName() + '#' + name + (declared ? "!d" : "");
        Field cached = FIELD_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        Field f = declared ? owner.getDeclaredField(name) : owner.getField(name);
        if (declared) {
            f.setAccessible(true);
        }
        FIELD_CACHE.putIfAbsent(key, f);
        return f;
    }

    public interface LoginCallback {
        boolean isCancelled();
        void onStage(String translationKey, Object... args);
        CompletableFuture<String> onRequestPassword();
        void onSuccess(Object loginData, boolean storageChanged);
        void onError(Throwable error);
    }

    public interface CreateCallback {
        boolean isCancelled();
        void onStage(String translationKey, Object... args);
        void onCreated(Object microsoftAccount);
        void onError(Throwable error);
    }

    public static boolean tryReplaceIasScreen(Screen screen) {
        if (screen == null) {
            return false;
        }
        String name = screen.getClass().getName();
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return false;
        }

        if ("ru.vidtu.ias.screen.AccountScreen".equals(name)) {
            Screen parent = readScreenField(screen, "parent");
            mc.gui.setScreen(new SestiaAccountScreen(parent));
            return true;
        }
        if ("ru.vidtu.ias.screen.AddPopupScreen".equals(name)) {
            Screen parent = readScreenField(screen, "parent");
            Consumer<Object> handler = readConsumerField(screen, "handler");
            mc.gui.setScreen(new SestiaAddAccountScreen(parent, handler));
            return true;
        }
        if ("ru.vidtu.ias.screen.DeletePopupScreen".equals(name)) {
            Screen parent = readScreenField(screen, "parent");
            Component prompt = readField(screen, "prompt", Component.class);
            Runnable handler = readField(screen, "handler", Runnable.class);
            mc.gui.setScreen(new SestiaDeleteAccountScreen(parent, prompt, handler));
            return true;
        }
        if ("ru.vidtu.ias.screen.MicrosoftCryptPopupScreen".equals(name)) {
            Screen parent = readScreenField(screen, "parent");
            Consumer<Object> handler = readConsumerField(screen, "handler");
            mc.gui.setScreen(new SestiaMicrosoftCryptScreen(parent, handler));
            return true;
        }
        return false;
    }

    public static boolean isDisabled() {
        try {
            return (boolean) cachedMethod(loadClass("ru.vidtu.ias.IAS"), "disabled").invoke(null);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isGameDisclaimerShown() {
        try {
            return cachedField(loadClass("ru.vidtu.ias.config.IASStorage"), "gameDisclaimerShown", false).getBoolean(null);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void markGameDisclaimerShown() {
        try {
            cachedField(loadClass("ru.vidtu.ias.config.IASStorage"), "gameDisclaimerShown", false).setBoolean(null, true);
            cachedMethod(loadClass("ru.vidtu.ias.IAS"), "gameDisclaimerShownStorage").invoke(null);
        } catch (Throwable t) {
            LOGGER.warn("Unable to persist IAS disclaimer state", t);
        }
    }

    @SuppressWarnings("unchecked")
    public static List<Object> getAccounts() {
        try {
            Object list = cachedField(loadClass("ru.vidtu.ias.config.IASStorage"), "ACCOUNTS", false).get(null);
            if (list instanceof List<?>) {
                return (List<Object>) list;
            }
        } catch (Throwable t) {
            LOGGER.warn("Unable to read IAS accounts", t);
        }
        return Collections.emptyList();
    }

    public static void saveStorage() {
        try {
            Class<?> iasCls = loadClass("ru.vidtu.ias.IAS");
            cachedMethod(iasCls, "disclaimersStorage").invoke(null);
            cachedMethod(iasCls, "saveStorage").invoke(null);
        } catch (Throwable t) {
            LOGGER.error("Unable to save IAS storage", t);
        }
    }

    public static Executor getExecutor() {
        try {
            return (Executor) cachedMethod(loadClass("ru.vidtu.ias.IAS"), "executor").invoke(null);
        } catch (Throwable ignored) {
            return Runnable::run;
        }
    }

    public static boolean shouldCloseOnLogin() {
        return getConfigBool("closeOnLogin", true);
    }

    public static boolean allowNoCrypt() {
        return getConfigBool("allowNoCrypt", false);
    }

    public static boolean isPasswordEchoing() {
        return getConfigBool("passwordEchoing", true);
    }

    public static boolean useServerAuth() {
        try {
            return (boolean) cachedMethod(loadClass("ru.vidtu.ias.config.IASConfig"), "useServerAuth").invoke(null);
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static String accountName(Object account) {
        return invokeString(account, "name", "Unknown");
    }

    public static UUID accountUuid(Object account) {
        return invokeUuid(account, "uuid");
    }

    public static UUID accountSkinUuid(Object account) {
        UUID skin = invokeUuid(account, "skin");
        return skin != null ? skin : UUID.nameUUIDFromBytes(("OfflinePlayer:" + accountName(account)).getBytes());
    }

    public static String accountType(Object account) {
        return invokeString(account, "type", "offline");
    }

    public static String accountTypeTipKey(Object account) {
        return invokeString(account, "typeTipKey", "ias.accounts.tip.type.offline");
    }

    public static boolean canLogin(Object account) {
        if (account == null) return false;
        try {
            return (boolean) cachedMethod(account.getClass(), "canLogin").invoke(account);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isInsecure(Object account) {
        if (account == null) return false;
        try {
            return (boolean) cachedMethod(account.getClass(), "insecure").invoke(account);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isMicrosoftAccount(Object account) {
        return account != null && "ru.vidtu.ias.account.MicrosoftAccount".equals(account.getClass().getName());
    }

    public static Object createOfflineAccount(String name) {
        try {
            Class<?> offlineCls = loadClass("ru.vidtu.ias.account.OfflineAccount");
            UUID uuid = (UUID) cachedMethod(offlineCls, "uuid", String.class).invoke(null, name);
            return offlineCls.getConstructor(String.class, UUID.class).newInstance(name, uuid);
        } catch (Throwable t) {
            LOGGER.error("Failed to create OfflineAccount for {}", name, t);
            return null;
        }
    }

    public static Object createOfflineLoginData(String name) {
        try {
            Class<?> offlineCls = loadClass("ru.vidtu.ias.account.OfflineAccount");
            UUID uuid = (UUID) cachedMethod(offlineCls, "uuid", String.class).invoke(null, name);
            Class<?> dataCls = loadClass("ru.vidtu.ias.auth.LoginData");
            return dataCls.getConstructor(String.class, UUID.class, String.class, boolean.class)
                    .newInstance(name, uuid, "ias:offline", false);
        } catch (Throwable t) {
            LOGGER.error("Failed to create offline LoginData for {}", name, t);
            return null;
        }
    }

    public static void startAccountLogin(Object account, boolean online, LoginCallback callback, Runnable onDone) {
        if (account == null || callback == null) {
            return;
        }
        if (!online || !canLogin(account)) {
            Object offlineData = createOfflineLoginData(accountName(account));
            if (offlineData != null) {
                callback.onSuccess(offlineData, false);
                if (onDone != null) {
                    onDone.run();
                }
            } else {
                callback.onError(new IllegalStateException("Failed to build offline session"));
            }
            return;
        }

        getExecutor().execute(() -> {
            try {
                Class<?> handlerCls = Class.forName("ru.vidtu.ias.auth.handlers.LoginHandler");
                Object proxy = Proxy.newProxyInstance(
                        handlerCls.getClassLoader(),
                        new Class<?>[]{handlerCls},
                        (p, method, args) -> switch (method.getName()) {
                            case "cancelled" -> callback.isCancelled();
                            case "stage" -> {
                                String key = args != null && args.length > 0 && args[0] instanceof String s ? s : "ias.login.processing";
                                Object[] fmt = args != null && args.length > 1 && args[1] instanceof Object[] arr ? arr : new Object[0];
                                callback.onStage(key, fmt);
                                yield null;
                            }
                            case "password" -> callback.onRequestPassword();
                            case "success" -> {
                                Object data = args != null && args.length > 0 ? args[0] : null;
                                boolean changed = args != null && args.length > 1 && args[1] instanceof Boolean b && b;
                                callback.onSuccess(data, changed);
                                if (onDone != null) {
                                    onDone.run();
                                }
                                yield null;
                            }
                            case "error" -> {
                                Throwable err = args != null && args.length > 0 && args[0] instanceof Throwable t ? t : new RuntimeException("Login error");
                                callback.onError(err);
                                yield null;
                            }
                            default -> null;
                        }
                );
                Method loginMethod = account.getClass().getMethod("login", handlerCls, Runnable.class);
                loginMethod.invoke(account, proxy, (Runnable) () -> {});
            } catch (Throwable t) {
                callback.onError(t);
            }
        });
    }

    public static CompletableFuture<Void> applyLoginData(Minecraft mc, Object loginData) {
        try {
            Class<?> iasMc = Class.forName("ru.vidtu.ias.IASMinecraft");
            Class<?> dataCls = Class.forName("ru.vidtu.ias.auth.LoginData");
            Method accountMethod = iasMc.getMethod("account", Minecraft.class, dataCls);
            @SuppressWarnings("unchecked")
            CompletableFuture<Void> future = (CompletableFuture<Void>) accountMethod.invoke(null, mc, loginData);
            return future;
        } catch (Throwable t) {
            return CompletableFuture.failedFuture(t);
        }
    }

    public static Object dummyCrypt() {
        try {
            return Class.forName("ru.vidtu.ias.crypt.DummyCrypt").getField("INSTANCE").get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object hardwareCrypt() {
        try {
            return Class.forName("ru.vidtu.ias.crypt.HardwareCrypt").getField("INSTANCE_V2").get(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Object passwordCrypt(String password) {
        try {
            return Class.forName("ru.vidtu.ias.crypt.PasswordCrypt")
                    .getConstructor(String.class)
                    .newInstance(password);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Closeable startMicrosoftAuth(Object crypt, CreateCallback callback, Consumer<String[]> deviceLinkConsumer) {
        try {
            Class<?> handlerCls = Class.forName("ru.vidtu.ias.auth.handlers.CreateHandler");
            Object proxy = Proxy.newProxyInstance(
                    handlerCls.getClassLoader(),
                    new Class<?>[]{handlerCls},
                    (p, method, args) -> switch (method.getName()) {
                        case "cancelled" -> callback.isCancelled();
                        case "stage" -> {
                            String key = args != null && args.length > 0 && args[0] instanceof String s ? s : "ias.login.processing";
                            Object[] fmt = args != null && args.length > 1 && args[1] instanceof Object[] arr ? arr : new Object[0];
                            callback.onStage(key, fmt);
                            yield null;
                        }
                        case "success" -> {
                            Object acc = args != null && args.length > 0 ? args[0] : null;
                            callback.onCreated(acc);
                            yield null;
                        }
                        case "error" -> {
                            Throwable err = args != null && args.length > 0 && args[0] instanceof Throwable t ? t : new RuntimeException("Microsoft auth error");
                            callback.onError(err);
                            yield null;
                        }
                        default -> null;
                    }
            );
            Class<?> cryptCls = Class.forName("ru.vidtu.ias.crypt.Crypt");

            if (useServerAuth()) {
                String doneHtml = Component.translatable("ias.login.done").getString();
                Class<?> serverCls = Class.forName("ru.vidtu.ias.auth.microsoft.MSAuthServer");
                Constructor<?> ctor = serverCls.getConstructor(String.class, cryptCls, handlerCls);
                Object server = ctor.newInstance(doneHtml, crypt, proxy);
                getExecutor().execute(() -> {
                    try {
                        serverCls.getMethod("run").invoke(server);
                        String authUrl = (String) serverCls.getMethod("authUrl").invoke(server);
                        callback.onStage("ias.login.link");
                        openUrl(authUrl);
                        if (deviceLinkConsumer != null) {
                            deviceLinkConsumer.accept(new String[]{authUrl, ""});
                        }
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null && mc.keyboardHandler != null && authUrl != null) {
                            mc.execute(() -> mc.keyboardHandler.setClipboard(authUrl));
                        }
                    } catch (Throwable t) {
                        callback.onError(t);
                    }
                });
                return (Closeable) server;
            } else {
                Class<?> clientCls = Class.forName("ru.vidtu.ias.auth.microsoft.MSAuthClient");
                Constructor<?> ctor = clientCls.getConstructor(cryptCls, handlerCls);
                Object client = ctor.newInstance(crypt, proxy);
                @SuppressWarnings("unchecked")
                CompletableFuture<Object> startFuture = (CompletableFuture<Object>) clientCls.getMethod("start").invoke(client);
                startFuture.thenAccept(deviceAuth -> {
                    if (deviceAuth == null) return;
                    try {
                        String uri = String.valueOf(deviceAuth.getClass().getMethod("uri").invoke(deviceAuth));
                        String userCode = String.valueOf(deviceAuth.getClass().getMethod("user").invoke(deviceAuth));
                        callback.onStage("ias.login.linkClient", uri, userCode);
                        openUrl(uri);
                        if (deviceLinkConsumer != null) {
                            deviceLinkConsumer.accept(new String[]{uri, userCode});
                        }
                        Minecraft mc = Minecraft.getInstance();
                        if (mc != null && mc.keyboardHandler != null) {
                            mc.execute(() -> mc.keyboardHandler.setClipboard(userCode));
                        }
                    } catch (Throwable t) {
                        callback.onError(t);
                    }
                }).exceptionally(t -> {
                    callback.onError(t);
                    return null;
                });
                return (Closeable) client;
            }
        } catch (Throwable t) {
            callback.onError(t);
            return null;
        }
    }

    public static String extractFriendlyErrorKey(Throwable error) {
        if (error == null) {
            return "ias.error";
        }
        try {
            Class<?> friendlyCls = Class.forName("ru.vidtu.ias.utils.exceptions.FriendlyException");
            Object friendly = friendlyCls.getMethod("friendlyInChain", Throwable.class).invoke(null, error);
            if (friendly != null) {
                Object key = friendlyCls.getMethod("key").invoke(friendly);
                if (key instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
        }
        return "ias.error";
    }

    public static void openUrl(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        try {
            Class<?> scCls = Class.forName("ru.vidtu.ias.platform.IStonecutter");
            scCls.getMethod("openUrl", String.class).invoke(null, url);
        } catch (Throwable ignored) {
        }
    }

    public static void saveAutoLoginPassword(String username, String password) {
        if (username == null || username.isBlank()) return;
        ModConfig config = ModConfig.getInstance();
        String key = username.trim().toLowerCase(Locale.ROOT);
        if (password != null && !password.trim().isEmpty()) {
            config.accountPasswords.put(key, password.trim());
        } else {
            config.accountPasswords.remove(key);
        }
        config.save();
    }

    public static String getAutoLoginPassword(String username) {
        if (username == null || username.isBlank()) return "";
        ModConfig config = ModConfig.getInstance();
        if (config.accountPasswords == null) return "";
        String val = config.accountPasswords.get(username.trim().toLowerCase(Locale.ROOT));
        return val != null ? val : "";
    }

    private static boolean getConfigBool(String fieldName, boolean fallback) {
        try {
            return cachedField(loadClass("ru.vidtu.ias.config.IASConfig"), fieldName, false).getBoolean(null);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static String invokeString(Object target, String method, String fallback) {
        if (target == null) return fallback;
        try {
            Object val = cachedMethod(target.getClass(), method).invoke(target);
            return val != null ? val.toString() : fallback;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static UUID invokeUuid(Object target, String method) {
        if (target == null) return null;
        try {
            Object val = cachedMethod(target.getClass(), method).invoke(target);
            return val instanceof UUID u ? u : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Screen readScreenField(Object target, String fieldName) {
        return readField(target, fieldName, Screen.class);
    }

    @SuppressWarnings("unchecked")
    private static Consumer<Object> readConsumerField(Object target, String fieldName) {
        return (Consumer<Object>) readField(target, fieldName, Consumer.class);
    }

    private static <T> T readField(Object target, String fieldName, Class<T> type) {
        try {
            Field f = cachedField(target.getClass(), fieldName, true);
            Object val = f.get(target);
            return type.isInstance(val) ? type.cast(val) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
