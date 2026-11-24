package com.client.mod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the single global Cloudflare Workers WebSocket session, 25s keepalive heartbeat,
 * transient reconnect queuing, and .c command routing for Sestia Client Chat.
 */
public final class SestiaChatClient {

    private static final String CHAT_PREFIX = "§8[§aSestiaChat§8] ";
    private static final long HEARTBEAT_INTERVAL_SEC = 25L;
    private static final long HEARTBEAT_TIMEOUT_MS = 65_000L;
    private static final int MAX_PENDING_OUTBOX = 3;

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
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Object LOCK = new Object();
    private static final Object SEND_LOCK = new Object();
    private static final AtomicInteger GENERATION = new AtomicInteger(0);
    private static final Deque<String> PENDING_OUTBOX = new ArrayDeque<>();
    private static final Set<String> KNOWN_USERS = Collections.synchronizedSet(new LinkedHashSet<>());

    private static CompletableFuture<Void> sendChain = CompletableFuture.completedFuture(null);

    private static volatile WebSocket activeSocket = null;
    private static volatile boolean loggedIn = false;
    private static volatile boolean connecting = false;
    private static volatile boolean silentAutoReconnect = false;
    private static volatile String activeNick = "Player";
    private static volatile int lastOnlineCount = 0;
    private static volatile long lastActivityAt = 0L;
    private static ScheduledFuture<?> pendingReconnect = null;
    private static ScheduledFuture<?> pendingWingsBroadcast = null;
    private static volatile long lastWingsBroadcastAt = 0L;
    private static volatile String lastBroadcastWingsJson = "";
    private static volatile String lastBroadcastCosmeticsJson = "";
    private static volatile long lastEmoteBroadcastAt = 0L;
    private static int reconnectAttempts = 0;

    static {
        SCHEDULER.scheduleAtFixedRate(
                SestiaChatClient::sendHeartbeatSafely,
                HEARTBEAT_INTERVAL_SEC,
                HEARTBEAT_INTERVAL_SEC,
                TimeUnit.SECONDS
        );
    }

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
            postChat(CHAT_PREFIX + "§aGlobal chat active §8(§7Online: §f" + Math.max(1, lastOnlineCount) + " §8| §f.c <message>§8)");
        } else if (!connecting) {
            connectAsync(0);
        }
    }

    public static void reconnect() {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.clientChatEnabled) {
            postChat(CHAT_PREFIX + "§cClient Chat is disabled. Enable the module first.");
            return;
        }
        if (SestiaChatProtocol.resolveEffectiveUrl(cfg.clientChatUrl).isEmpty()) {
            postChat(CHAT_PREFIX + "§7Worker URL is not configured.");
            return;
        }
        reconnectAttempts = 0;
        silentAutoReconnect = false;
        connectAsync(0);
    }

    public static void disconnect() {
        disconnect(false);
    }

    public static void onSessionAccountChanged() {
        ModConfig cfg = ModConfig.getInstance();
        if (cfg != null && cfg.clientChatEnabled) {
            synchronized (LOCK) {
                silentAutoReconnect = true;
                reconnectAttempts = 0;
            }
            connectAsync(0);
        }
    }

    public static List<String> getSuggestedPlayers() {
        Set<String> names = new LinkedHashSet<>();
        String self = activeNick != null ? activeNick : "";
        synchronized (KNOWN_USERS) {
            for (String user : KNOWN_USERS) {
                if (user != null && !user.isBlank() && !user.equalsIgnoreCase(self)) {
                    names.add(user);
                }
            }
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getConnection() != null) {
                String mcSelf = (mc.getUser() != null && mc.getUser().getName() != null) ? mc.getUser().getName() : self;
                for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
                    if (info != null && info.getProfile() != null && info.getProfile().name() != null) {
                        String n = info.getProfile().name();
                        if (!n.isBlank() && !n.equalsIgnoreCase(self) && !n.equalsIgnoreCase(mcSelf)) {
                            names.add(n);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(names);
    }

    public static boolean handleOutgoingChat(String rawMessage) {
        ModConfig cfg = ModConfig.getInstance();
        if (!cfg.clientChatEnabled || !SestiaChatProtocol.isClientChatCommand(rawMessage)) {
            return false;
        }

        SestiaChatProtocol.ParsedCommand cmd = SestiaChatProtocol.parseOutgoingCommand(rawMessage);
        switch (cmd.kind()) {
            case HELP -> printHelpInChat();
            case LIST -> sendOrQueue(SestiaChatProtocol.buildListJson());
            case DM -> {
                if (!cfg.clientChatAllowDms) {
                    postChat(CHAT_PREFIX + "§cDMs are disabled in settings (Allow DMs).");
                    return true;
                }
                sendOrQueue(SestiaChatProtocol.buildDmJson(cmd.arg1(), cmd.arg2()));
            }
            case CHAT -> {
                String clean = SestiaChatProtocol.sanitizeText(cmd.arg1());
                if (!clean.isEmpty()) {
                    sendOrQueue(SestiaChatProtocol.buildChatJson(clean));
                }
            }
            case NONE -> {
                return false;
            }
        }
        return true;
    }

    public static void printHelpInChat() {
        postChat(CHAT_PREFIX + "§fCommands:");
        postChat("  §e.c <message> §7- Send a message to global chat");
        postChat("  §e.c msg <player> <message> §7- Send a private message (DM)");
        postChat("  §e.c list §7- Show online Sestia users");
        postChat("  §e.c help §7- Show Sestia chat command help");
    }

    private static void sendOrQueue(String json) {
        ModConfig cfg = ModConfig.getInstance();
        if (SestiaChatProtocol.resolveEffectiveUrl(cfg.clientChatUrl).isEmpty()) {
            postChat(CHAT_PREFIX + "§cServer URL is not set.");
            return;
        }
        WebSocket ws = activeSocket;
        if (ws != null && loggedIn) {
            sendTextSerialized(ws, json, GENERATION.get(), true);
            return;
        }
        queuePending(json);
        postChat(CHAT_PREFIX + "§7Reconnecting, your message will send shortly...");
        if (!connecting) {
            connectAsync(0);
        }
    }

    private static void queuePending(String json) {
        synchronized (PENDING_OUTBOX) {
            while (PENDING_OUTBOX.size() >= MAX_PENDING_OUTBOX) {
                PENDING_OUTBOX.pollFirst();
            }
            PENDING_OUTBOX.addLast(json);
        }
    }

    private static void flushPendingOutbox() {
        WebSocket ws = activeSocket;
        if (ws == null || !loggedIn) {
            return;
        }
        final int gen = GENERATION.get();
        synchronized (PENDING_OUTBOX) {
            while (!PENDING_OUTBOX.isEmpty()) {
                String next = PENDING_OUTBOX.pollFirst();
                sendTextSerialized(ws, next, gen, true);
            }
        }
    }

    private static void sendTextSerialized(WebSocket ws, String payload, int gen, boolean requeueOnError) {
        synchronized (SEND_LOCK) {
            sendChain = sendChain.thenCompose(ignored -> {
                if (ws != activeSocket || gen != GENERATION.get()) {
                    return CompletableFuture.completedFuture(null);
                }
                return ws.sendText(payload, true).whenComplete((unused, err) -> {
                    if (err != null) {
                        if (requeueOnError) {
                            queuePending(payload);
                        }
                        if (gen == GENERATION.get()) {
                            loggedIn = false;
                            connecting = false;
                            ws.abort();
                            scheduleReconnect(gen, extractShortError(err));
                        }
                    }
                }).handle((unused, err) -> null);
            });
        }
    }

    private static void connectAsync(long delayMs) {
        synchronized (LOCK) {
            if (pendingReconnect != null) {
                pendingReconnect.cancel(false);
                pendingReconnect = null;
            }
            closeSocketOnly(true);
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
                .connectTimeout(Duration.ofSeconds(5));

        builder.buildAsync(URI.create(connectUrl), new ChatListener(gen, nick))
                .whenComplete((ws, err) -> {
                    synchronized (LOCK) {
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
                        closeSocketOnly(true);
                        activeSocket = ws;
                        lastActivityAt = System.currentTimeMillis();
                    }
                    ModConfig curCfg = ModConfig.getInstance();
                    WingsLogic.WingProfile wp = WingsLogic.fromConfig(curCfg);
                    CosmeticsLogic.CosmeticLoadout cl = (curCfg == null || curCfg.cosmeticSyncMine)
                            ? CosmeticsLogic.fromConfig(curCfg)
                            : null;
                    lastBroadcastWingsJson = SestiaChatProtocol.buildWingsJson(wp);
                    lastBroadcastCosmeticsJson = cl != null ? SestiaChatProtocol.buildCosmeticsJson(cl) : "";
                    lastWingsBroadcastAt = System.currentTimeMillis();
                    sendTextSerialized(ws, SestiaChatProtocol.buildHelloJson(nick, wp, cl), gen, false);
                });
    }

    public static void broadcastWingsUpdate() {
        broadcastCosmeticsUpdate();
    }

    public static void broadcastCosmeticsUpdate() {
        try {
            if (!loggedIn || activeSocket == null || PanicHandler.isPanicActive()) {
                return;
            }
            ModConfig cfg = ModConfig.getInstance();
            if (cfg == null || !cfg.clientChatEnabled) {
                return;
            }
            WingsLogic.WingProfile wp = cfg.cosmeticSyncMine
                    ? WingsLogic.fromConfig(cfg)
                    : new WingsLogic.WingProfile(false, cfg.wingsMode, cfg.wingsColorPreset, cfg.wingsCustomRgb, cfg.wingsLength, cfg.wingsWidth, cfg.wingsLinesCount, cfg.wingsFadeStart, cfg.wingsHeight, cfg.wingsFlapSpeed, cfg.wingsShowDamage).sanitized();
            CosmeticsLogic.CosmeticLoadout cl = cfg.cosmeticSyncMine
                    ? CosmeticsLogic.fromConfig(cfg)
                    : CosmeticsLogic.defaultLoadout();
            String wingsJson = SestiaChatProtocol.buildWingsJson(wp);
            String cosJson = SestiaChatProtocol.buildCosmeticsJson(cl);
            boolean wingsChanged = !wingsJson.equals(lastBroadcastWingsJson);
            boolean cosChanged = !cosJson.equals(lastBroadcastCosmeticsJson);
            if (!wingsChanged && !cosChanged) {
                return;
            }
            lastBroadcastWingsJson = wingsJson;
            lastBroadcastCosmeticsJson = cosJson;
            long now = System.currentTimeMillis();
            long elapsed = now - lastWingsBroadcastAt;
            if (elapsed >= 160L) {
                lastWingsBroadcastAt = now;
                WebSocket ws = activeSocket;
                if (ws != null && loggedIn) {
                    int gen = GENERATION.get();
                    sendTextSerialized(ws, cosJson, gen, false);
                    sendTextSerialized(ws, wingsJson, gen, false);
                }
            } else {
                synchronized (LOCK) {
                    if (pendingWingsBroadcast != null) {
                        pendingWingsBroadcast.cancel(false);
                    }
                    long wait = Math.max(25L, 160L - elapsed);
                    pendingWingsBroadcast = SCHEDULER.schedule(() -> {
                        lastWingsBroadcastAt = System.currentTimeMillis();
                        WebSocket ws = activeSocket;
                        String latestCos = lastBroadcastCosmeticsJson;
                        String latestWings = lastBroadcastWingsJson;
                        if (ws != null && loggedIn) {
                            int gen = GENERATION.get();
                            if (latestCos != null && !latestCos.isEmpty()) {
                                sendTextSerialized(ws, latestCos, gen, false);
                            }
                            if (latestWings != null && !latestWings.isEmpty()) {
                                sendTextSerialized(ws, latestWings, gen, false);
                            }
                        }
                    }, wait, TimeUnit.MILLISECONDS);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static void broadcastEmote(int emoteId) {
        try {
            if (!loggedIn || activeSocket == null || PanicHandler.isPanicActive()) {
                return;
            }
            ModConfig cfg = ModConfig.getInstance();
            if (cfg == null || !cfg.clientChatEnabled || !cfg.cosmeticSyncMine) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastEmoteBroadcastAt < 460L && emoteId >= 0) {
                return;
            }
            lastEmoteBroadcastAt = now;
            sendTextSerialized(activeSocket, SestiaChatProtocol.buildEmoteFxJson(emoteId), GENERATION.get(), false);
        } catch (Throwable ignored) {
        }
    }

    private static void sendHeartbeatSafely() {
        try {
            ModConfig cfg = ModConfig.getInstance();
            if (!cfg.clientChatEnabled || !loggedIn) {
                return;
            }
            WebSocket ws = activeSocket;
            int gen = GENERATION.get();
            if (ws == null) {
                return;
            }
            long now = System.currentTimeMillis();
            if (lastActivityAt > 0 && now - lastActivityAt > HEARTBEAT_TIMEOUT_MS) {
                loggedIn = false;
                connecting = false;
                ws.abort();
                scheduleReconnect(gen, "Heartbeat timeout");
                return;
            }
            sendTextSerialized(ws, "ping", gen, false);
        } catch (Throwable ignored) {
        }
    }

    private static void scheduleReconnect(int gen, String reason) {
        synchronized (LOCK) {
            if (gen != GENERATION.get() || !ModConfig.getInstance().clientChatEnabled) {
                return;
            }
            if (reconnectAttempts == 0) {
                silentAutoReconnect = true;
            } else if (reconnectAttempts == 3 && reason != null && !reason.isBlank()) {
                silentAutoReconnect = false;
                postChat(CHAT_PREFIX + "§cDisconnected (" + reason + "), reconnecting...");
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
            silentAutoReconnect = false;
            reconnectAttempts = 0;
            lastActivityAt = 0L;
            lastBroadcastWingsJson = "";
            lastBroadcastCosmeticsJson = "";
            synchronized (PENDING_OUTBOX) {
                PENDING_OUTBOX.clear();
            }
            KNOWN_USERS.clear();
            CosmeticsLogic.clearRemoteState();
            closeSocketOnly(false);
        }
        if (announce) {
            postChat(CHAT_PREFIX + "§7Connection closed.");
        }
    }

    private static void closeSocketOnly(boolean forceAbort) {
        WebSocket old = activeSocket;
        activeSocket = null;
        if (old != null) {
            if (forceAbort) {
                old.abort();
                return;
            }
            try {
                old.sendClose(WebSocket.NORMAL_CLOSURE, "closing");
            } catch (Exception ignored) {
                old.abort();
            }
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
                synchronized (LOCK) {
                    connecting = false;
                    loggedIn = true;
                    reconnectAttempts = 0;
                    lastActivityAt = System.currentTimeMillis();
                }
                activeNick = ev.from();
                lastOnlineCount = ev.onlineCount();
                if (!activeNick.isBlank()) {
                    CosmeticsLogic.markSestiaUserOnline(activeNick);
                }
                if (ev.users() != null && !ev.users().isEmpty()) {
                    KNOWN_USERS.addAll(ev.users());
                    for (String u : ev.users()) {
                        CosmeticsLogic.markSestiaUserOnline(u);
                    }
                }
                boolean wasSilent = silentAutoReconnect;
                silentAutoReconnect = false;
                if (!wasSilent) {
                    postChat(CHAT_PREFIX + "§aConnected to global chat! §8(§7Online: §f" + lastOnlineCount + "§8)");
                }
                flushPendingOutbox();
            }
            case CHAT -> {
                if (!ev.from().isBlank()) {
                    KNOWN_USERS.add(ev.from());
                    CosmeticsLogic.markSestiaUserOnline(ev.from());
                }
                if (!SestiaChatProtocol.isMuted(ev.from(), cfg.clientChatMuted)) {
                    postChat(CHAT_PREFIX + "§f" + ev.from() + "§7: §r" + ev.text());
                }
            }
            case DM -> {
                if (!ev.from().isBlank()) {
                    KNOWN_USERS.add(ev.from());
                    CosmeticsLogic.markSestiaUserOnline(ev.from());
                }
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
                synchronized (KNOWN_USERS) {
                    KNOWN_USERS.clear();
                    KNOWN_USERS.addAll(ev.users());
                }
                CosmeticsLogic.setOnlineSestiaUsers(ev.users());
                String names = ev.users().isEmpty() ? "(none)" : String.join(", ", ev.users());
                postChat(CHAT_PREFIX + "§7Online (§f" + lastOnlineCount + "§7): §f" + names);
            }
            case SYSTEM -> postChat(CHAT_PREFIX + "§7" + ev.text());
            case ERROR -> postChat(CHAT_PREFIX + "§c" + ev.text());
            case WINGS, COSMETICS, FX -> {
                if (!ev.from().isBlank()) {
                    KNOWN_USERS.add(ev.from());
                    CosmeticsLogic.markSestiaUserOnline(ev.from());
                }
            }
            case LEAVE -> {
                if (!ev.from().isBlank()) {
                    KNOWN_USERS.removeIf(u -> u != null && u.equalsIgnoreCase(ev.from()));
                    CosmeticsLogic.removeRemotePlayer(ev.from());
                }
            }
            case UNKNOWN -> {
            }
        }
    }

    private static void postChat(String text) {
        if (PanicHandler.isPanicActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            if (PanicHandler.isPanicActive()) {
                return;
            }
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
        private static final int MAX_WS_FRAME_CHARS = 262_144;
        private final int generation;
        private final StringBuilder buffer = new StringBuilder();

        ChatListener(int generation, String nick) {
            this.generation = generation;
            activeNick = nick;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            lastActivityAt = System.currentTimeMillis();
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            if (buffer.length() + data.length() > MAX_WS_FRAME_CHARS) {
                buffer.setLength(0);
                webSocket.abort();
                return null;
            }
            buffer.append(data);
            if (last) {
                String full = buffer.toString();
                buffer.setLength(0);
                lastActivityAt = System.currentTimeMillis();
                if (!SestiaChatProtocol.isHeartbeatFrame(full)) {
                    handleIncomingJson(generation, full);
                }
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
