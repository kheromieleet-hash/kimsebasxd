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

function sanitizeWings(raw) {
  if (!raw || typeof raw !== "object") {
    return null;
  }
  const num = (v, def) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : def;
  };
  const clamp = (v, min, max, def) => Math.max(min, Math.min(max, num(v, def)));
  return {
    enabled: Boolean(raw.enabled),
    mode: Math.max(0, Math.min(16, num(raw.mode, 0) | 0)),
    color: Math.max(0, Math.min(9, num(raw.color, 2) | 0)),
    rgb: Math.max(0, Math.min(0xFFFFFF, num(raw.rgb, 0x00D4FF) | 0)),
    length: Number(clamp(raw.length, 0.4, 2.5, 1.15).toFixed(3)),
    width: Number(clamp(raw.width, 0.02, 0.2, 0.06).toFixed(3)),
    lines: Math.max(2, Math.min(12, num(raw.lines, 5) | 0)),
    fade: Number(clamp(raw.fade, 0.1, 1.0, 0.5).toFixed(3)),
    height: Number(clamp(raw.height, -0.5, 0.6, 0.15).toFixed(3)),
    speed: Number(clamp(raw.speed, 0.0, 3.0, 1.0).toFixed(3)),
    damage: raw.damage !== false,
  };
}

function sanitizeCosmetics(raw) {
  if (!raw || typeof raw !== "object") {
    return null;
  }
  const num = (v, def) => {
    const n = Number(v);
    return Number.isFinite(n) ? n : def;
  };
  const clamp = (v, min, max, def) => Math.max(min, Math.min(max, num(v, def)));
  const clampInt = (v, min, max, def) => Math.max(min, Math.min(max, num(v, def) | 0));
  const arr = (k) => (Array.isArray(raw[k]) ? raw[k] : []);

  const w = arr("w");
  const cl = arr("cl");
  const h = arr("h");
  const p = arr("p");
  const g = arr("g");
  const a = arr("a");
  const t = arr("t");
  const hf = arr("hf");
  const kf = arr("kf");
  const tf = arr("tf");
  const gl = arr("gl");
  const ha = arr("ha");
  const sa = arr("sa");
  const gr = arr("gr");

  return {
    v: 2,
    w: [
      Boolean(w[0]),
      clampInt(w[1], 0, 16, 16),
      clampInt(w[2], 0, 9, 6),
      clampInt(w[3], 0, 0xFFFFFF, 0x34D399),
      Number(clamp(w[4], 0.4, 2.5, 1.15).toFixed(3)),
      Number(clamp(w[5], 0.02, 0.2, 0.06).toFixed(3)),
      clampInt(w[6], 2, 12, 5),
      Number(clamp(w[7], 0.1, 1.0, 0.5).toFixed(3)),
      Number(clamp(w[8], -0.5, 0.6, 0.15).toFixed(3)),
      Number(clamp(w[9], 0.0, 3.0, 1.0).toFixed(3)),
      w[10] !== false,
    ],
    cl: [Boolean(cl[0]), clampInt(cl[1], 0, 4, 0), clampInt(cl[2], 0, 9, 6), clampInt(cl[3], 0, 0xFFFFFF, 0x34D399)],
    h: [Boolean(h[0]), clampInt(h[1], 0, 6, 0), clampInt(h[2], 0, 9, 6), clampInt(h[3], 0, 0xFFFFFF, 0x34D399), Number(clamp(h[4], 0.7, 1.45, 1.0).toFixed(3))],
    p: [Boolean(p[0]), clampInt(p[1], 0, 7, 0), clampInt(p[2], 0, 2, 0), clampInt(p[3], 0, 9, 6), clampInt(p[4], 0, 0xFFFFFF, 0x34D399)],
    g: [Boolean(g[0]), clampInt(g[1], 0, 4, 0), clampInt(g[2], 0, 9, 6), clampInt(g[3], 0, 0xFFFFFF, 0x34D399)],
    a: [Boolean(a[0]), clampInt(a[1], 0, 3, 0), clampInt(a[2], 0, 9, 6), clampInt(a[3], 0, 0xFFFFFF, 0x34D399), Number(clamp(a[4], 0.55, 1.65, 0.95).toFixed(3)), Number(clamp(a[5], 0.2, 2.5, 1.0).toFixed(3))],
    t: [Boolean(t[0]), clampInt(t[1], 0, 3, 1), clampInt(t[2], 0, 9, 6), clampInt(t[3], 0, 0xFFFFFF, 0x34D399)],
    hf: [Boolean(hf[0]), clampInt(hf[1], 0, 2, 0), clampInt(hf[2], 0, 9, 6), clampInt(hf[3], 0, 0xFFFFFF, 0x34D399)],
    kf: [Boolean(kf[0]), clampInt(kf[1], 0, 2, 0), clampInt(kf[2], 0, 9, 6), clampInt(kf[3], 0, 0xFFFFFF, 0x34D399)],
    tf: [Boolean(tf[0]), Number(clamp(tf[1], 0.4, 1.35, 0.78).toFixed(3)), tf[2] !== false],
    gl: [Boolean(gl[0]), clampInt(gl[1], 0, 9, 6), clampInt(gl[2], 0, 0xFFFFFF, 0x34D399), Number(clamp(gl[3], 0.25, 1.35, 0.85).toFixed(3))],
    ha: [Boolean(ha[0]), clampInt(ha[1], 0, 1, 0)],
    sa: [Boolean(sa[0]), clampInt(sa[1], 0, 1, 0), clampInt(sa[2], 0, 9, 6), clampInt(sa[3], 0, 0xFFFFFF, 0x34D399)],
    gr: [clampInt(gr[0], 0, 3, 0), clampInt(gr[1], 0, 0xFFFFFF, 0x00D4FF)],
  };
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
        const incomingWings = sanitizeWings(packet.wings);
        if (incomingWings) {
          state.wings = incomingWings;
        }
        const incomingCosmetics = sanitizeCosmetics(packet.c);
        if (incomingCosmetics) {
          state.cosmetics = incomingCosmetics;
        }
        ws.serializeAttachment(state);

        const sockets = this.ctx.getWebSockets();
        const wingsMap = [];
        const cosmeticsMap = [];
        const users = [];
        const seenUsers = new Set();
        for (const client of sockets) {
          const att = client.deserializeAttachment();
          if (att && att.nick) {
            const low = att.nick.toLowerCase();
            if (!seenUsers.has(low)) {
              seenUsers.add(low);
              users.push(att.nick);
            }
            if (att.wings) {
              wingsMap.push({ nick: att.nick, wings: att.wings });
            }
            if (att.cosmetics) {
              cosmeticsMap.push({ nick: att.nick, c: att.cosmetics });
            }
          }
        }
        sendJson(ws, {
          type: "welcome",
          nick: state.nick,
          online: sockets.length,
          users,
          wingsMap,
          cosmeticsMap,
        });

        for (const client of sockets) {
          if (client !== ws) {
            if (firstHello) {
              sendJson(client, {
                type: "system",
                text: `${state.nick} joined global chat.`,
              });
            }
            if (state.cosmetics) {
              sendJson(client, {
                type: "cosmetics",
                from: state.nick,
                c: state.cosmetics,
              });
            } else if (state.wings) {
              sendJson(client, {
                type: "wings",
                from: state.nick,
                wings: state.wings,
              });
            }
          }
        }
        break;
      }

      case "cosmetics": {
        const now = Date.now();
        if (now - (state.lastCosmeticsAt || 0) < 120) {
          return;
        }
        const c = sanitizeCosmetics(packet.c);
        if (!c) {
          return;
        }
        state.cosmetics = c;
        state.lastCosmeticsAt = now;
        ws.serializeAttachment(state);

        const out = {
          type: "cosmetics",
          from: state.nick,
          c,
        };
        for (const client of this.ctx.getWebSockets()) {
          if (client !== ws) {
            sendJson(client, out);
          }
        }
        break;
      }

      case "fx": {
        const now = Date.now();
        if (now - (state.lastFxAt || 0) < 450) {
          return;
        }
        if (packet.k !== "emote") {
          return;
        }
        const idNum = Number(packet.id);
        const id = Number.isFinite(idNum) ? Math.max(-1, Math.min(10, idNum | 0)) : -1;
        state.lastFxAt = now;
        ws.serializeAttachment(state);

        const out = {
          type: "fx",
          from: state.nick,
          k: "emote",
          id,
        };
        for (const client of this.ctx.getWebSockets()) {
          if (client !== ws) {
            sendJson(client, out);
          }
        }
        break;
      }

      case "wings": {
        const now = Date.now();
        if (now - (state.lastWingsAt || 0) < 120) {
          return;
        }
        const wings = sanitizeWings(packet.wings);
        if (!wings) {
          return;
        }
        state.wings = wings;
        state.lastWingsAt = now;
        ws.serializeAttachment(state);

        const out = {
          type: "wings",
          from: state.nick,
          wings,
        };
        for (const client of this.ctx.getWebSockets()) {
          if (client !== ws) {
            sendJson(client, out);
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

  broadcastLeave(closedWs) {
    try {
      const att = closedWs.deserializeAttachment();
      const leftNick = att?.nick;
      if (!leftNick) return;
      const sockets = this.ctx.getWebSockets();
      let stillOnline = false;
      for (const client of sockets) {
        if (client === closedWs) continue;
        const other = client.deserializeAttachment();
        if (other && other.nick && other.nick.toLowerCase() === leftNick.toLowerCase()) {
          stillOnline = true;
          break;
        }
      }
      if (!stillOnline) {
        const out = { type: "leave", from: leftNick };
        for (const client of sockets) {
          if (client !== closedWs) {
            sendJson(client, out);
          }
        }
      }
    } catch {
      // Ignore socket inspection errors during close
    }
  }

  async webSocketClose(ws, code, reason) {
    this.broadcastLeave(ws);
    try {
      const safeCode = code === 1000 || (code >= 3000 && code <= 4999) ? code : 1000;
      ws.close(safeCode, reason || "");
    } catch {
      // Already closed
    }
  }

  async webSocketError(ws) {
    this.broadcastLeave(ws);
    try {
      ws.close(1011, "WebSocket error");
    } catch {
      // Already closed
    }
  }
}
