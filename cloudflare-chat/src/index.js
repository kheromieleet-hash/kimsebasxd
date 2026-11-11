import { DurableObject } from "cloudflare:workers";

const GLOBAL_ROOM_KEY = "sestia-global";
const MAX_CONNECTIONS = 250;
const MAX_PAYLOAD_BYTES = 2048;
const MAX_TEXT_LENGTH = 220;
const RATE_LIMIT_MS = 600;

function sanitizeNick(raw, fallback = "Player") {
  const cleaned = String(raw || "")
    .replace(/\u00a7./g, "")
    .replace(/[^A-Za-z0-9_]/g, "")
    .slice(0, 20);
  return cleaned.length >= 2 ? cleaned : fallback;
}

function sanitizeText(raw, maxLen = MAX_TEXT_LENGTH) {
  return String(raw || "")
    .replace(/\u00a7./g, "")
    .replace(/[\r\n\t\x00-\x1f\x7f]/g, " ")
    .trim()
    .slice(0, maxLen);
}

function sendJson(ws, obj) {
  try {
    ws.send(JSON.stringify(obj));
  } catch {
    // Ignore sockets closing mid-broadcast
  }
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);

    if (url.pathname === "/health") {
      return Response.json({ ok: true, service: "sestia-client-chat" });
    }

    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("Expected WebSocket upgrade", { status: 426 });
    }

    const id = env.CHAT_ROOM.idFromName(GLOBAL_ROOM_KEY);
    const stub = env.CHAT_ROOM.get(id);
    return stub.fetch(request);
  },
};

export class ChatRoom extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.ctx.setWebSocketAutoResponse(
      new WebSocketRequestResponsePair("ping", "pong")
    );
  }

  async fetch(request) {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("Expected WebSocket upgrade", { status: 426 });
    }

    const activeSockets = this.ctx.getWebSockets();
    if (activeSockets.length >= MAX_CONNECTIONS) {
      return new Response("Global chat is full", { status: 503 });
    }

    const url = new URL(request.url);
    const nick = sanitizeNick(url.searchParams.get("nick"));

    const pair = new WebSocketPair();
    const [client, server] = Object.values(pair);

    this.ctx.acceptWebSocket(server);
    server.serializeAttachment({
      nick,
      lastMsgAt: 0,
      joinedAt: Date.now(),
    });

    return new Response(null, {
      status: 101,
      webSocket: client,
    });
  }

  async webSocketMessage(ws, raw) {
    if (raw === "ping") {
      try { ws.send("pong"); } catch {}
      return;
    }
    if (raw === "pong") {
      return;
    }
    if (typeof raw !== "string" || raw.length > MAX_PAYLOAD_BYTES) {
      sendJson(ws, { type: "error", text: "Message exceeds size limit." });
      return;
    }

    let packet;
    try {
      packet = JSON.parse(raw);
    } catch {
      sendJson(ws, { type: "error", text: "Invalid JSON packet." });
      return;
    }

    if (!packet || typeof packet.type !== "string") {
      return;
    }

    const state = ws.deserializeAttachment() || {
      nick: "Player",
      lastMsgAt: 0,
      joinedAt: Date.now(),
    };

    switch (packet.type) {
      case "hello": {
        const nextNick = sanitizeNick(packet.nick, state.nick);
        const firstHello = !state.greeted;
        state.nick = nextNick;
        state.greeted = true;
        ws.serializeAttachment(state);

        const sockets = this.ctx.getWebSockets();
        sendJson(ws, {
          type: "welcome",
          nick: state.nick,
          online: sockets.length,
        });

        if (firstHello) {
          for (const client of sockets) {
            if (client !== ws) {
              sendJson(client, {
                type: "system",
                text: `${state.nick} joined global chat.`,
              });
            }
          }
        }
        break;
      }

      case "chat": {
        const now = Date.now();
        if (now - (state.lastMsgAt || 0) < RATE_LIMIT_MS) {
          sendJson(ws, { type: "error", text: "You are typing too fast, slow down." });
          return;
        }
        const text = sanitizeText(packet.text);
        if (!text) {
          return;
        }
        state.lastMsgAt = now;
        ws.serializeAttachment(state);

        const out = {
          type: "chat",
          from: state.nick,
          text,
          ts: now,
        };
        for (const client of this.ctx.getWebSockets()) {
          sendJson(client, out);
        }
        break;
      }

      case "dm": {
        const now = Date.now();
        if (now - (state.lastMsgAt || 0) < RATE_LIMIT_MS) {
          sendJson(ws, { type: "error", text: "You are sending messages too fast." });
          return;
        }
        const targetNick = sanitizeNick(packet.to, "");
        const text = sanitizeText(packet.text);
        if (!targetNick || !text) {
          return;
        }
        if (targetNick.toLowerCase() === state.nick.toLowerCase()) {
          sendJson(ws, { type: "error", text: "You cannot send a DM to yourself." });
          return;
        }

        const matches = [];
        for (const client of this.ctx.getWebSockets()) {
          const att = client.deserializeAttachment();
          if (att && att.nick && att.nick.toLowerCase() === targetNick.toLowerCase()) {
            matches.push({ client, nick: att.nick });
          }
        }
        if (matches.length === 0) {
          sendJson(ws, { type: "error", text: `${targetNick} is not online right now.` });
          return;
        }

        state.lastMsgAt = now;
        ws.serializeAttachment(state);

        const out = {
          type: "dm",
          from: state.nick,
          to: matches[0].nick,
          text,
          ts: now,
        };
        for (const m of matches) {
          sendJson(m.client, out);
        }
        sendJson(ws, out);
        break;
      }

      case "list": {
        const seen = new Set();
        const users = [];
        for (const client of this.ctx.getWebSockets()) {
          const att = client.deserializeAttachment();
          const name = att?.nick || "Player";
          const low = name.toLowerCase();
          if (!seen.has(low)) {
            seen.add(low);
            users.push(name);
          }
        }
        users.sort((a, b) => a.localeCompare(b, "en", { sensitivity: "base" }));
        sendJson(ws, {
          type: "list",
          users,
        });
        break;
      }

      default:
        break;
    }
  }

  async webSocketClose(ws, code, reason) {
    try {
      const safeCode = code === 1000 || (code >= 3000 && code <= 4999) ? code : 1000;
      ws.close(safeCode, reason || "");
    } catch {
      // Already closed
    }
  }

  async webSocketError(ws) {
    try {
      ws.close(1011, "WebSocket error");
    } catch {
      // Already closed
    }
  }
}
