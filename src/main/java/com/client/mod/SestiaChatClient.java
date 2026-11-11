package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the Cloudflare Workers WebSocket session and .c command routing for Sestia Client Chat.
 */
public final class SestiaChatClient {

    private static final ExecutorService HTTP_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SestiaChat-HTTP");
        t.setDaemon(true);
        return t;
    });

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SestiaChat-Reconnect");
        t.setDaemon(true);
        return t;
    });

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .executor(HTTP_EXECUTOR)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Object LOCK = new Object();
    private static final AtomicInteger GENERATION = new AtomicInteger(0);

    private static volatile WebSocket activeSocket = null;
    private static volatile boolean loggedIn = false;
    private static volatile boolean connecting = false;
    private static volatile String activeRoom = "global";
    private static volatile String activeNick = "Player";
    private static ScheduledFuture<?> pendingReconnect = null;
    private static int reconnectAttempts = 0;

    private SestiaChatClient() {
    }

    public static void onConfigChanged() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.clientChatEnabled) {
            disconnect(false);
            return;
        }
        String url = SestiaChatProtocol.normalizeWebSocketUrl(cfg.clientChatUrl);
        if (url.isEmpty()) {
            disconnect(false);
            postChat("§8[§cSestiaChat§8] §7Cloudflare Worker adresi ayarli degil. Ayarlamak icin: §f.c url wss://...");
            return;
        }
        connectAsync(0);
    }

    public static void reconnect() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.clientChatEnabled) {
            postChat("§8[§cSestiaChat§8] §cClient Chat kapali. Once modulu acin.");
            return;
        }
        if (SestiaChatProtocol.normalizeWebSocketUrl(cfg.clientChatUrl).isEmpty()) {
            postChat("§8[§cSestiaChat§8] §7Once Worker adresini ayarlayin: §f.c url wss://...");
            return;
        }
        reconnectAttempts = 0;
        connectAsync(0);
    }

    public static void disconnect() {
        disconnect(false);
    }

    public static boolean handleOutgoingChat(String rawMessage) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.clientChatEnabled || !SestiaChatProtocol.isClientChatCommand(rawMessage)) {
            return false;
        }

        SestiaChatProtocol.ParsedCommand cmd = SestiaChatProtocol.parseOutgoingCommand(rawMessage);
        switch (cmd.kind()) {
            case HELP -> printHelpInChat();
            case STATUS -> printStatusInChat(cfg);
            case URL -> {
                String normalized = SestiaChatProtocol.normalizeWebSocketUrl(cmd.arg1());
                if (normalized.isEmpty()) {
                    postChat("§8[§cSestiaChat§8] §cGecersiz URL. Ornek: §f.c url wss://sestia-client-chat.ornek.workers.dev");
                    return true;
                }
                cfg.clientChatUrl = normalized;
                cfg.save();
                postChat("§8[§cSestiaChat§8] §aSunucu kaydedildi: §f" + normalized);
                reconnect();
            }
            case ROOM -> {
                String room = SestiaChatProtocol.normalizeRoom(cmd.arg1());
                cfg.clientChatRoom = room;
                cfg.save();
                postChat("§8[§cSestiaChat§8] §aOda degistirildi: §f" + room);
                reconnect();
            }
            case KEY -> {
                String keyVal = cmd.arg1().trim();
                if (keyVal.equalsIgnoreCase("off") || keyVal.equalsIgnoreCase("none") || keyVal.equalsIgnoreCase("clear")) {
                    cfg.clientChatKey = "";
                    postChat("§8[§cSestiaChat§8] §7Oda sifresi temizlendi.");
                } else {
                    cfg.clientChatKey = keyVal;
                    postChat("§8[§cSestiaChat§8] §aErisim anahtari kaydedildi.");
                }
                cfg.save();
                reconnect();
            }
            case MUTE -> {
                if (cmd.arg1().isBlank()) {
                    postChat("§8[§cSestiaChat§8] §7Kullanim: §f.c mute <kullanici>");
                    return true;
                }
                cfg.clientChatMuted = SestiaChatProtocol.addMute(cmd.arg1(), cfg.clientChatMuted);
                cfg.save();
                postChat("§8[§cSestiaChat§8] §eSusturuldu: §f" + SestiaChatProtocol.sanitizeNick(cmd.arg1()));
            }
            case UNMUTE -> {
                if (cmd.arg1().isBlank()) {
                    postChat("§8[§cSestiaChat§8] §7Kullanim: §f.c unmute <kullanici>");
                    return true;
                }
                cfg.clientChatMuted = SestiaChatProtocol.removeMute(cmd.arg1(), cfg.clientChatMuted);
                cfg.save();
                postChat("§8[§cSestiaChat§8] §aSusturma kaldirildi: §f" + SestiaChatProtocol.sanitizeNick(cmd.arg1()));
            }
            case LIST -> {
                if (!ensureConnected()) {
                    return true;
                }
                sendRaw(SestiaChatProtocol.buildListJson());
            }
            case DM -> {
                if (!cfg.clientChatAllowDms) {
                    postChat("§8[§cSestiaChat§8] §cDM ayarlardan kapali (Allow DMs).");
                    return true;
                }
                if (!ensureConnected()) {
                    return true;
                }
                sendRaw(SestiaChatProtocol.buildDmJson(cmd.arg1(), cmd.arg2()));
            }
            case CHAT -> {
                if (!ensureConnected()) {
                    return true;
                }
                String clean = SestiaChatProtocol.sanitizeText(cmd.arg1());
                if (!clean.isEmpty()) {
                    sendRaw(SestiaChatProtocol.buildChatJson(clean));
                }
            }
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    public static void printHelpInChat() {
        postChat("§8[§cSestiaChat§8] §fKomutlar:");
        postChat("  §e.c <mesaj> §7- Odaya mesaj gonder");
        postChat("  §e.c msg <isim> <mesaj> §7- Ozel mesaj (DM)");
        postChat("  §e.c list §7- Cevrimici kullanicilari goster");
        postChat("  §e.c url <wss://...> §7- Cloudflare Worker adresi ayarla");
        postChat("  §e.c room <oda> §7| §e.c key <sifre|off>");
        postChat("  §e.c mute/unmute <isim> §7| §e.c status");
    }

    private static void printStatusInChat(ModConfig cfg) {
        String stateLabel = loggedIn ? "§aBagli (" + activeRoom + ")" : (connecting ? "§eBaglaniyor..." : "§cBagli Degil");
        String urlDisplay = (cfg.clientChatUrl == null || cfg.clientChatUrl.isBlank()) ? "Ayarlanmadi" : cfg.clientChatUrl;
        postChat("§8[§cSestiaChat§8] §7Durum: " + stateLabel + " §8| §7Nick: §f" + activeNick);
        postChat("§8[§cSestiaChat§8] §7URL: §f" + urlDisplay);
    }

    private static boolean ensureConnected() {
        ModConfig cfg = ModConfig.getInstance();
        if (SestiaChatProtocol.normalizeWebSocketUrl(cfg.clientChatUrl).isEmpty()) {
            postChat("§8[§cSestiaChat§8] §cOnce Worker URL ayarlayin: §f.c url wss://...");
            return false;
        }
        if (activeSocket != null && loggedIn) {
            return true;
        }
        postChat("§8[§cSestiaChat§8] §7Sunucuya baglaniyor, biraz sonra tekrar deneyin...");
        if (!connecting) {
            connectAsync(0);
        }
        return false;
    }

    private static void connectAsync(long delayMs) {
        synchronized (LOCK) {
            if (pendingReconnect != null) {
                pendingReconnect.cancel(false);
                pendingReconnect = null;
            }
            closeSocketOnly();
            final int gen = GENERATION.incrementAndGet();
            ModConfig cfg = ModConfig.getInstance();
            String baseUrl = SestiaChatProtocol.normalizeWebSocketUrl(cfg.clientChatUrl);
            if (!cfg.clientChatEnabled || baseUrl.isEmpty()) {
                return;
            }
            connecting = true;
            loggedIn = false;

            Runnable connectTask = () -> openWebSocket(gen, baseUrl, cfg.clientChatRoom, cfg.clientChatKey);
            if (delayMs <= 0) {
                HTTP_EXECUTOR.execute(connectTask);
            } else {
                pendingReconnect = SCHEDULER.schedule(connectTask, delayMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    private static void openWebSocket(int gen, String baseUrl, String room, String accessKey) {
        if (gen != GENERATION.get() || !ModConfig.getInstance().clientChatEnabled) {
            connecting = false;
            return;
        }
        String nick = resolvePlayerNick();
        String roomNorm = SestiaChatProtocol.normalizeRoom(room);
        String connectUrl = SestiaChatProtocol.buildConnectUri(baseUrl, roomNorm, nick);

        WebSocket.Builder builder = HTTP_CLIENT.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10));
        if (accessKey != null && !accessKey.isBlank()) {
            builder.header("x-sestia-key", accessKey.trim());
        }

        builder.buildAsync(URI.create(connectUrl), new ChatListener(gen, nick, roomNorm))
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        if (gen == GENERATION.get()) {
                            connecting = false;
                            loggedIn = false;
                            scheduleReconnect(gen, extractShortError(err));
                        } else if (ws != null) {
                            ws.abort();
                        }
                        return;
                    }
                    if (gen != GENERATION.get()) {
                        ws.abort();
                        return;
                    }
                    activeSocket = ws;
                    ws.sendText(SestiaChatProtocol.buildHelloJson(nick, roomNorm), true);
                });
    }

    private static void scheduleReconnect(int gen, String reason) {
        synchronized (LOCK) {
            if (gen != GENERATION.get() || !ModConfig.getInstance().clientChatEnabled) {
                return;
            }
            if (reconnectAttempts == 0 && reason != null && !reason.isBlank()) {
                postChat("§8[§cSestiaChat§8] §cBaglanti kesildi (" + reason + "), yeniden deneniyor...");
            }
            long waitMs = SestiaChatProtocol.computeBackoffMs(reconnectAttempts);
            reconnectAttempts++;
            if (pendingReconnect != null) {
                pendingReconnect.cancel(false);
            }
            pendingReconnect = SCHEDULER.schedule(() -> connectAsync(0), waitMs, TimeUnit.MILLISECONDS);
        }
    }

    private static void disconnect(boolean announce) {
        synchronized (LOCK) {
            GENERATION.incrementAndGet();
            if (pendingReconnect != null) {
                pendingReconnect.cancel(false);
                pendingReconnect = null;
            }
            connecting = false;
            loggedIn = false;
            reconnectAttempts = 0;
            closeSocketOnly();
        }
        if (announce) {
            postChat("§8[§cSestiaChat§8] §7Baglanti kapatildi.");
        }
    }

    private static void closeSocketOnly() {
        WebSocket old = activeSocket;
        activeSocket = null;
        if (old != null) {
            try {
                old.sendClose(WebSocket.NORMAL_CLOSURE, "closing");
            } catch (Exception ignored) {
                old.abort();
            }
        }
    }

    private static void sendRaw(String json) {
        WebSocket ws = activeSocket;
        if (ws != null) {
            ws.sendText(json, true);
        }
    }

    private static String resolvePlayerNick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getUser() != null && mc.getUser().getName() != null) {
            return SestiaChatProtocol.sanitizeNick(mc.getUser().getName());
        }
        return "Player";
    }

    private static void handleIncomingJson(int gen, String payload) {
        if (gen != GENERATION.get()) {
            return;
        }
        SestiaChatProtocol.ServerEvent ev = SestiaChatProtocol.parseServerEvent(payload);
        ModConfig cfg = ModConfig.getInstance();

        switch (ev.type()) {
            case WELCOME -> {
                connecting = false;
                loggedIn = true;
                reconnectAttempts = 0;
                activeNick = ev.from();
                activeRoom = ev.room();
                postChat("§8[§cSestiaChat§8] §aBaglandi! Oda: §f" + activeRoom + " §8(§7Cevrimici: §f" + ev.onlineCount() + "§8)");
            }
            case CHAT -> {
                if (!SestiaChatProtocol.isMuted(ev.from(), cfg.clientChatMuted)) {
                    postChat("§8[§cSestiaChat§8] §f" + ev.from() + "§7: §r" + ev.text());
                }
            }
            case DM -> {
                if (!cfg.clientChatAllowDms) {
                    return;
                }
                if (ev.from().equalsIgnoreCase(activeNick)) {
                    postChat("§8[§dSestiaDM §7-> §f" + ev.to() + "§8] §r" + ev.text());
                } else if (!SestiaChatProtocol.isMuted(ev.from(), cfg.clientChatMuted)) {
                    postChat("§8[§dSestiaDM §f" + ev.from() + " §7-> Sana§8] §r" + ev.text());
                }
            }
            case LIST -> {
                String names = ev.users().isEmpty() ? "(kimse yok)" : String.join(", ", ev.users());
                postChat("§8[§cSestiaChat§8] §7Oda §f" + ev.room() + " §8(§f" + ev.users().size() + "§8): §7" + names);
            }
            case SYSTEM -> postChat("§8[§cSestiaChat§8] §7" + ev.text());
            case ERROR -> postChat("§8[§cSestiaChat§8] §c" + ev.text());
            case UNKNOWN -> {
            }
        }
    }

    private static void postChat(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            if (mc.gui != null && mc.gui.hud != null && mc.gui.hud.getChat() != null) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal(text));
            }
        });
    }

    private static String extractShortError(Throwable err) {
        Throwable root = err;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg != null && !msg.isBlank()) ? msg : root.getClass().getSimpleName();
    }

    private static final class ChatListener implements WebSocket.Listener {
        private final int generation;
        private final StringBuilder buffer = new StringBuilder();

        ChatListener(int generation, String nick, String room) {
            this.generation = generation;
            activeNick = nick;
            activeRoom = room;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String full = buffer.toString();
                buffer.setLength(0);
                handleIncomingJson(generation, full);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            if (generation == GENERATION.get()) {
                connecting = false;
                loggedIn = false;
                if (ModConfig.getInstance().clientChatEnabled) {
                    scheduleReconnect(generation, reason == null || reason.isBlank() ? ("HTTP/WS " + statusCode) : reason);
                }
            }
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (generation == GENERATION.get()) {
                connecting = false;
                loggedIn = false;
                if (ModConfig.getInstance().clientChatEnabled) {
                    scheduleReconnect(generation, extractShortError(error));
                }
            }
        }
    }
}
