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
 * Manages the single global Cloudflare Workers WebSocket session and .c command routing for Sestia Client Chat.
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
    private static volatile String activeNick = "Player";
    private static volatile int lastOnlineCount = 0;
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
        String url = SestiaChatProtocol.resolveEffectiveUrl(cfg.clientChatUrl);
        if (url.isEmpty()) {
            disconnect(false);
            return;
        }
        connectAsync(0);
    }

    public static void onEnteredWorld() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.clientChatEnabled) {
            return;
        }
        String url = SestiaChatProtocol.resolveEffectiveUrl(cfg.clientChatUrl);
        if (url.isEmpty()) {
            return;
        }
        if (loggedIn && activeSocket != null) {
            postChat("§8[§cSestiaChat§8] §aGlobal chat active §8(§7Online: §f" + Math.max(1, lastOnlineCount) + " §8| §f.c <message>§8)");
        } else if (!connecting) {
            connectAsync(0);
        }
    }

    public static void reconnect() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.clientChatEnabled) {
            postChat("§8[§cSestiaChat§8] §cClient Chat is disabled. Enable the module first.");
            return;
        }
        if (SestiaChatProtocol.resolveEffectiveUrl(cfg.clientChatUrl).isEmpty()) {
            postChat("§8[§cSestiaChat§8] §7Worker URL is not configured: §f.c url wss://...");
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
                String rawArg = cmd.arg1().trim();
                if (rawArg.equalsIgnoreCase("default") || rawArg.equalsIgnoreCase("reset")) {
                    cfg.clientChatUrl = "";
                    cfg.save();
                    postChat("§8[§cSestiaChat§8] §aReverted to the default chat server URL.");
                    reconnect();
                    return true;
                }
                String normalized = SestiaChatProtocol.normalizeWebSocketUrl(rawArg);
                if (normalized.isEmpty()) {
                    postChat("§8[§cSestiaChat§8] §cInvalid URL. Example: §f.c url wss://sestia-client-chat.example.workers.dev");
                    return true;
                }
                cfg.clientChatUrl = normalized;
                cfg.save();
                postChat("§8[§cSestiaChat§8] §aCustom chat server saved: §f" + normalized);
                reconnect();
            }
            case MUTE -> {
                if (cmd.arg1().isBlank()) {
                    postChat("§8[§cSestiaChat§8] §7Usage: §f.c mute <player>");
                    return true;
                }
                cfg.clientChatMuted = SestiaChatProtocol.addMute(cmd.arg1(), cfg.clientChatMuted);
                cfg.save();
                postChat("§8[§cSestiaChat§8] §eMuted: §f" + SestiaChatProtocol.sanitizeNick(cmd.arg1()));
            }
            case UNMUTE -> {
                if (cmd.arg1().isBlank()) {
                    postChat("§8[§cSestiaChat§8] §7Usage: §f.c unmute <player>");
                    return true;
                }
                cfg.clientChatMuted = SestiaChatProtocol.removeMute(cmd.arg1(), cfg.clientChatMuted);
                cfg.save();
                postChat("§8[§cSestiaChat§8] §aUnmuted: §f" + SestiaChatProtocol.sanitizeNick(cmd.arg1()));
            }
            case LIST -> {
                if (!ensureConnected()) {
                    return true;
                }
                sendRaw(SestiaChatProtocol.buildListJson());
            }
            case DM -> {
                if (!cfg.clientChatAllowDms) {
                    postChat("§8[§cSestiaChat§8] §cDMs are disabled in settings (Allow DMs).");
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
        postChat("§8[§cSestiaChat§8] §fCommands:");
        postChat("  §e.c <message> §7- Send a message to global chat");
        postChat("  §e.c msg <player> <message> §7- Send a private message (DM)");
        postChat("  §e.c list §7- Show online Sestia users");
        postChat("  §e.c mute/unmute <player> §7| §e.c status");
        postChat("  §e.c url <wss://...|default> §7- Set custom/default server");
    }

    private static void printStatusInChat(ModConfig cfg) {
        String stateLabel = loggedIn ? "§aConnected (" + Math.max(1, lastOnlineCount) + " online)" : (connecting ? "§eConnecting..." : "§cDisconnected");
        String effectiveUrl = SestiaChatProtocol.resolveEffectiveUrl(cfg.clientChatUrl);
        String urlDisplay = effectiveUrl.isEmpty() ? "Not set" : effectiveUrl;
        postChat("§8[§cSestiaChat§8] §7Status: " + stateLabel + " §8| §7Nick: §f" + activeNick);
        postChat("§8[§cSestiaChat§8] §7URL: §f" + urlDisplay);
    }

    private static boolean ensureConnected() {
        ModConfig cfg = ModConfig.getInstance();
        if (SestiaChatProtocol.resolveEffectiveUrl(cfg.clientChatUrl).isEmpty()) {
            postChat("§8[§cSestiaChat§8] §cServer URL is not set: §f.c url wss://...");
            return false;
        }
        if (activeSocket != null && loggedIn) {
            return true;
        }
        postChat("§8[§cSestiaChat§8] §7Connecting to global chat, please try again shortly...");
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
            String baseUrl = SestiaChatProtocol.resolveEffectiveUrl(cfg.clientChatUrl);
            if (!cfg.clientChatEnabled || baseUrl.isEmpty()) {
                return;
            }
            connecting = true;
            loggedIn = false;

            Runnable connectTask = () -> openWebSocket(gen, baseUrl);
            if (delayMs <= 0) {
                HTTP_EXECUTOR.execute(connectTask);
            } else {
                pendingReconnect = SCHEDULER.schedule(connectTask, delayMs, TimeUnit.MILLISECONDS);
            }
        }
    }

    private static void openWebSocket(int gen, String baseUrl) {
        if (gen != GENERATION.get() || !ModConfig.getInstance().clientChatEnabled) {
            connecting = false;
            return;
        }
        String nick = resolvePlayerNick();
        String connectUrl = SestiaChatProtocol.buildConnectUri(baseUrl, nick);

        WebSocket.Builder builder = HTTP_CLIENT.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10));

        builder.buildAsync(URI.create(connectUrl), new ChatListener(gen, nick))
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
                    ws.sendText(SestiaChatProtocol.buildHelloJson(nick), true);
                });
    }

    private static void scheduleReconnect(int gen, String reason) {
        synchronized (LOCK) {
            if (gen != GENERATION.get() || !ModConfig.getInstance().clientChatEnabled) {
                return;
            }
            if (reconnectAttempts == 0 && reason != null && !reason.isBlank()) {
                postChat("§8[§cSestiaChat§8] §cDisconnected (" + reason + "), reconnecting...");
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
            postChat("§8[§cSestiaChat§8] §7Connection closed.");
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
                lastOnlineCount = ev.onlineCount();
                postChat("§8[§cSestiaChat§8] §aConnected to global chat! §8(§7Online: §f" + lastOnlineCount + "§8)");
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
                    postChat(SestiaChatProtocol.formatDmMessage(ev.from(), ev.to(), true, ev.text()));
                } else if (!SestiaChatProtocol.isMuted(ev.from(), cfg.clientChatMuted)) {
                    postChat(SestiaChatProtocol.formatDmMessage(ev.from(), ev.to(), false, ev.text()));
                }
            }
            case LIST -> {
                lastOnlineCount = ev.users().size();
                String names = ev.users().isEmpty() ? "(none)" : String.join(", ", ev.users());
                postChat("§8[§cSestiaChat§8] §7Online (§f" + lastOnlineCount + "§7): §f" + names);
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

        ChatListener(int generation, String nick) {
            this.generation = generation;
            activeNick = nick;
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
