// Minimal, dependency-free Discord RPC (IPC) client.
// Talks to the local Discord desktop client over its named pipe / unix socket.
'use strict';

const net = require('net');
const path = require('path');
const { EventEmitter } = require('events');

const OP = { HANDSHAKE: 0, FRAME: 1, CLOSE: 2, PING: 3, PONG: 4 };

function candidatePaths() {
  const out = [];
  if (process.platform === 'win32') {
    for (let i = 0; i < 10; i++) out.push(`\\\\?\\pipe\\discord-ipc-${i}`);
    return out;
  }
  const { XDG_RUNTIME_DIR, TMPDIR, TMP, TEMP } = process.env;
  const base = (XDG_RUNTIME_DIR || TMPDIR || TMP || TEMP || '/tmp').replace(/\/$/, '');
  // Regular install, Flatpak, and Snap locations.
  const dirs = [base, path.join(base, 'app/com.discordapp.Discord'), path.join(base, 'snap.discord'),
    path.join(base, 'app/dev.vencord.Vesktop'), path.join(base, '.flatpak/dev.vencord.Vesktop/xdg-run')];
  for (const d of dirs) for (let i = 0; i < 10; i++) out.push(path.join(d, `discord-ipc-${i}`));
  return out;
}

function encode(op, data) {
  const json = Buffer.from(JSON.stringify(data), 'utf8');
  const header = Buffer.alloc(8);
  header.writeInt32LE(op, 0);
  header.writeInt32LE(json.length, 4);
  return Buffer.concat([header, json]);
}

function tryConnect(p) {
  return new Promise((resolve, reject) => {
    const sock = net.createConnection(p);
    const onErr = (e) => { sock.destroy(); reject(e); };
    sock.once('connect', () => { sock.off('error', onErr); resolve(sock); });
    sock.once('error', onErr);
  });
}

class DiscordRPC extends EventEmitter {
  constructor() {
    super();
    this.clientId = null;
    this.sock = null;
    this.ready = false;
    this.user = null;
    this.buf = Buffer.alloc(0);
    this.reconnectTimer = null;
    this.stopped = true;
    this.pending = new Map();
    this.nonce = 0;
    this.supportsExtended = true; // activity.type / status_display_type
  }

  get status() {
    if (this.stopped) return 'disabled';
    if (!this.clientId) return 'no-client-id';
    if (this.ready) return 'connected';
    return 'connecting';
  }

  start(clientId) {
    this.clientId = clientId ? String(clientId).trim() : null;
    this.stopped = false;
    this._teardown();
    if (!this.clientId) { this.emit('status', this.status); return; }
    this._connect();
  }

  stop() {
    this.stopped = true;
    if (this.ready && this.sock) {
      // Clear the status, then close gracefully so the write isn't dropped.
      const s = this.sock;
      this.sock = null;
      this.ready = false;
      s.removeAllListeners();
      s.on('error', () => {});
      try { s.end(encode(OP.FRAME, { cmd: 'SET_ACTIVITY', args: { pid: process.pid }, nonce: this._nonce() })); } catch {}
    }
    this._teardown();
    this.emit('status', this.status);
  }

  async _connect() {
    clearTimeout(this.reconnectTimer);
    if (this.stopped || !this.clientId) return;
    this.emit('status', 'connecting');
    let sock = null;
    for (const p of candidatePaths()) {
      try { sock = await tryConnect(p); break; } catch { /* try next */ }
    }
    if (this.stopped) { if (sock) sock.destroy(); return; }
    if (!sock) return this._scheduleReconnect();

    this.sock = sock;
    this.buf = Buffer.alloc(0);
    sock.on('data', (d) => this._onData(d));
    sock.on('close', () => this._onClose());
    sock.on('error', () => {});
    this._send(OP.HANDSHAKE, { v: 1, client_id: this.clientId });
  }

  _scheduleReconnect() {
    clearTimeout(this.reconnectTimer);
    if (this.stopped) return;
    this.reconnectTimer = setTimeout(() => this._connect(), 15000);
  }

  _teardown() {
    clearTimeout(this.reconnectTimer);
    this.ready = false;
    if (this.sock) { const s = this.sock; this.sock = null; s.removeAllListeners(); s.on('error', () => {}); s.destroy(); }
  }

  _onClose() {
    const wasReady = this.ready;
    this.ready = false;
    this.sock = null;
    if (wasReady) this.emit('disconnected');
    this.emit('status', this.stopped ? 'disabled' : 'connecting');
    this._scheduleReconnect();
  }

  _onData(chunk) {
    this.buf = Buffer.concat([this.buf, chunk]);
    while (this.buf.length >= 8) {
      const op = this.buf.readInt32LE(0);
      const len = this.buf.readInt32LE(4);
      if (this.buf.length < 8 + len) break;
      const raw = this.buf.subarray(8, 8 + len).toString('utf8');
      this.buf = this.buf.subarray(8 + len);
      let msg = null;
      try { msg = JSON.parse(raw); } catch { continue; }
      this._handle(op, msg);
    }
  }

  _handle(op, msg) {
    if (op === OP.PING) { this._send(OP.PONG, msg); return; }
    if (op === OP.CLOSE) {
      // Discord rejected us (most often: invalid client id).
      this.emit('error', new Error(msg && msg.message ? msg.message : 'Discord closed the connection'));
      this._teardown();
      this.emit('status', 'error');
      this._scheduleReconnect();
      return;
    }
    if (op !== OP.FRAME) return;
    if (msg.cmd === 'DISPATCH' && msg.evt === 'READY') {
      this.ready = true;
      this.user = msg.data && msg.data.user;
      this.emit('ready', this.user);
      this.emit('status', 'connected');
      return;
    }
    if (msg.nonce && this.pending.has(msg.nonce)) {
      const { resolve } = this.pending.get(msg.nonce);
      this.pending.delete(msg.nonce);
      resolve(msg);
    }
  }

  _send(op, data) {
    if (!this.sock) return false;
    this.sock.write(encode(op, data));
    return true;
  }

  _nonce() { return `${Date.now()}-${++this.nonce}`; }

  _request(cmd, args) {
    return new Promise((resolve) => {
      if (!this.ready) return resolve(null);
      const nonce = this._nonce();
      const t = setTimeout(() => { this.pending.delete(nonce); resolve(null); }, 10000);
      this.pending.set(nonce, { resolve: (m) => { clearTimeout(t); resolve(m); } });
      this._send(OP.FRAME, { cmd, args, nonce });
    });
  }

  async setActivity(activity) {
    if (!this.ready) return false;
    if (!activity) return !!(await this._request('SET_ACTIVITY', { pid: process.pid }));

    const strip = (a) => { const c = { ...a }; delete c.type; delete c.status_display_type; return c; };
    const noArt = (a) => ({ ...a, assets: a.assets ? { ...a.assets, large_image: 'octave' } : undefined });

    // Try the full activity first, then progressively safer versions, so an
    // older Discord build or an image Discord can't load never kills the status.
    const attempts = [];
    if (this.supportsExtended) attempts.push({ act: activity, ext: true });
    attempts.push({ act: strip(activity), ext: false });
    if (activity.assets && /^https?:/i.test(activity.assets.large_image || '')) {
      attempts.push({ act: noArt(strip(activity)), ext: false });
    }

    let lastErr = null;
    for (let i = 0; i < attempts.length; i++) {
      const res = await this._request('SET_ACTIVITY', { pid: process.pid, activity: attempts[i].act });
      if (!res) return false; // disconnected / timed out
      if (res.evt !== 'ERROR') {
        // Only the newer fields differed between attempt 0 and 1: remember that.
        if (i === 1 && attempts[0].ext) this.supportsExtended = false;
        return true;
      }
      lastErr = (res.data && res.data.message) || 'SET_ACTIVITY failed';
    }
    this.emit('error', new Error(lastErr));
    return false;
  }

  clearActivity() { return this.setActivity(null); }
}

module.exports = { DiscordRPC };
