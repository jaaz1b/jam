'use strict';
// The YouTube Music engine (ytm/: Limusic, rebuilt as part of Jam) runs as a background process.
// Jam starts it with JAM_HOST/JAM_TOKEN; its hidden window loads jam-relay.html, connects back
// here over a local WebSocket, and from then on performs every invoke/listen Jam forwards to it.
// See ytm/src-tauri/src/jamhost.rs for the engine side.

const { EventEmitter } = require('events');
const { spawn } = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { WebSocketServer } = require('ws');

const START_TIMEOUT_MS = 30000;

class Engine extends EventEmitter {
  constructor({ bin, cwd }) {
    super();
    this.bin = bin;
    this.cwd = cwd;
    this.child = null;
    this.wss = null;
    this.sock = null;
    this.ready = null;      // Promise resolved once the relay says hello
    this.pending = new Map(); // invoke id -> resolve
    this.nextId = 1;
    this.subs = new Map();  // event name -> listen id
    this.nextLid = 1;
    this.stopping = false;
  }

  get installed() { return !!this.bin && fs.existsSync(this.bin); }
  get running() { return !!this.sock; }

  start() {
    if (this.ready) return this.ready;
    if (!this.installed) return Promise.reject(new Error('The YouTube Music engine is not included in this build.'));
    this.stopping = false;
    this.ready = new Promise((resolve, reject) => {
      const token = crypto.randomBytes(24).toString('hex');
      const wss = new WebSocketServer({ host: '127.0.0.1', port: 0, maxPayload: 64 * 1024 * 1024 });
      this.wss = wss;
      const timer = setTimeout(() => fail(new Error('The YouTube Music engine did not start in time.')), START_TIMEOUT_MS);
      const fail = (err) => { clearTimeout(timer); this._teardown(); reject(err); };

      wss.on('connection', (ws) => {
        let authed = false;
        ws.on('message', (raw) => {
          let m; try { m = JSON.parse(raw.toString()); } catch { return; }
          if (!authed) {
            if (m.t === 'hello' && m.token === token && !this.sock) {
              authed = true;
              this.sock = ws;
              clearTimeout(timer);
              // Re-establish subscriptions (e.g. after an engine restart).
              for (const [event, lid] of this.subs) this._send({ t: 'listen', lid, event });
              this.emit('up');
              resolve(this);
            } else ws.close();
            return;
          }
          this._onMessage(m);
        });
        ws.on('close', () => { if (ws === this.sock) this._onDown(); });
      });
      wss.on('listening', () => {
        const port = wss.address().port;
        try {
          this.child = spawn(this.bin, [], {
            cwd: this.cwd,
            env: { ...process.env, JAM_HOST: `ws://127.0.0.1:${port}`, JAM_TOKEN: token },
            // JAM_YTM_DEBUG=1 shows the engine's own output (for troubleshooting).
            stdio: process.env.JAM_YTM_DEBUG ? ['ignore', 'inherit', 'inherit'] : 'ignore',
            windowsHide: true,
          });
        } catch (e) { fail(e); return; }
        this.child.on('error', fail);
        this.child.on('exit', (code) => {
          this.child = null;
          if (!this.sock) fail(new Error(`The YouTube Music engine exited (code ${code}).`));
          else this._onDown();
        });
      });
      wss.on('error', fail);
    });
    this.ready.catch(() => { this.ready = null; });
    return this.ready;
  }

  _send(m) { if (this.sock && this.sock.readyState === 1) this.sock.send(JSON.stringify(m)); }

  _onMessage(m) {
    if (m.t === 'result') {
      const done = this.pending.get(m.id);
      if (done) { this.pending.delete(m.id); done(m.ok ? { ok: true, value: m.value } : { ok: false, error: m.error }); }
    } else if (m.t === 'event') {
      const event = [...this.subs].find(([, lid]) => lid === m.lid);
      if (event && m.data) this.emit('event', event[0], m.data.payload);
    } else if (m.t === 'log') {
      console.warn('[ytm-engine]', m.msg);
    }
  }

  _onDown() {
    const wasUp = !!this.sock;
    this.sock = null;
    for (const done of this.pending.values()) done({ ok: false, error: 'The YouTube Music engine stopped.' });
    this.pending.clear();
    this._teardown();
    if (wasUp) this.emit('down', this.stopping);
  }

  _teardown() {
    this.ready = null;
    if (this.wss) { try { this.wss.close(); } catch {} this.wss = null; }
    if (this.child && !this.stopping) { try { this.child.kill(); } catch {} }
  }

  // → { ok, value } | { ok: false, error }. Never throws.
  async invoke(cmd, args, options) {
    try { await this.start(); } catch (e) { return { ok: false, error: e.message }; }
    return new Promise((resolve) => {
      const id = this.nextId++;
      this.pending.set(id, resolve);
      this._send({ t: 'invoke', id, cmd, args: args || {}, options: options || undefined });
    });
  }

  // Subscribe once per event name; everything fans out through the 'event' emitter.
  subscribe(event) {
    event = String(event);
    if (this.subs.has(event)) return;
    const lid = this.nextLid++;
    this.subs.set(event, lid);
    if (this.sock) this._send({ t: 'listen', lid, event });
  }

  stop() {
    if (!this.child && !this.sock) { this._teardown(); return; }
    this.stopping = true;
    this._send({ t: 'quit' });
    const child = this.child;
    setTimeout(() => { if (child && child.exitCode === null) { try { child.kill(); } catch {} } }, 3000);
    this.sock = null;
    this._teardown();
  }
}

// Where the engine and its UI live: inside the app (packaged), or overridden for development.
function locate(app) {
  const exe = process.platform === 'win32' ? 'jam-ytm.exe' : 'jam-ytm';
  const base = app.isPackaged ? path.join(process.resourcesPath, 'ytm') : path.join(__dirname, '..', '..', 'ytm-dist');
  const bin = process.env.JAM_YTM_BIN || path.join(base, exe);
  const ui = process.env.JAM_YTM_UI || path.join(base, 'ui');
  return { bin, ui, cwd: path.dirname(bin) };
}

module.exports = { Engine, locate };
