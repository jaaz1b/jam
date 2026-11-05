'use strict';
// Preload for the YouTube Music UI (the Limusic interface, rebuilt into Jam).
//
// It provides the small `window.__TAURI_INTERNALS__` surface that @tauri-apps/api is built on (the
// same surface Tauri's own `@tauri-apps/api/mocks` replaces), backed by Jam: commands and events go
// to the engine through Jam's relay (engine.js); window, dialog, clipboard, updater and process calls
// are answered by Jam itself (main.js, `ytm:local`). The UI's code runs unmodified.
//
const { contextBridge, ipcRenderer } = require('electron');

// ---- callbacks (Tauri's transformCallback / runCallback)
const callbacks = new Map();
let nextCb = 1;
function registerCallback(cb, once) {
  const id = nextCb++;
  callbacks.set(id, { cb, once: !!once });
  return id;
}
function unregisterCallback(id) { callbacks.delete(id); }
function runCallback(id, data) {
  const c = callbacks.get(id);
  if (!c) return;
  if (c.once) callbacks.delete(id);
  try { c.cb(data); } catch (e) { console.error(e); }
}

// ---- events: one engine subscription per name (main dedupes), fanned out to every UI listener
const listeners = new Map(); // eventId -> { event, handler }
const byEvent = new Map();   // event -> Set<eventId>
let nextEvent = 1;
function unregisterListener(event, eventId) {
  const l = listeners.get(eventId);
  if (!l) return;
  listeners.delete(eventId);
  const set = byEvent.get(event);
  if (set) set.delete(eventId);
  unregisterCallback(l.handler);
}
ipcRenderer.on('ytm:event', (_e, event, payload) => {
  const ids = byEvent.get(event);
  if (!ids) return;
  for (const id of [...ids]) {
    const l = listeners.get(id);
    if (l) runCallback(l.handler, { event, id, payload });
  }
});

// Raw (binary) command results travel as base64.
function decode(v) {
  if (v && typeof v === 'object' && typeof v.__jamB64 === 'string') {
    const b = Buffer.from(v.__jamB64, 'base64');
    return b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);
  }
  return v;
}

const LOCAL = /^plugin:(window|webview|dialog|clipboard-manager|updater|process)\|/;

async function invoke(cmd, args = {}, options) {
  cmd = String(cmd);
  if (cmd === 'plugin:event|listen') {
    const id = nextEvent++;
    listeners.set(id, { event: args.event, handler: args.handler });
    if (!byEvent.has(args.event)) byEvent.set(args.event, new Set());
    byEvent.get(args.event).add(id);
    ipcRenderer.send('ytm:subscribe', String(args.event));
    return id;
  }
  if (cmd === 'plugin:event|unlisten') { unregisterListener(args.event, args.eventId); return null; }
  const r = await ipcRenderer.invoke(LOCAL.test(cmd) ? 'ytm:local' : 'ytm:invoke', cmd, args, options || null);
  if (!r || !r.ok) throw (r ? r.error : 'Jam: no response');
  return decode(r.value);
}

const sep = process.platform === 'win32' ? '\\' : '/';
contextBridge.exposeInMainWorld('__jamTauriBridge', {
  invoke,
  transformCallback: registerCallback,
  unregisterCallback,
  runCallback,
  unregisterListener,
});

// The page-facing objects are built in the page's own world, synchronously, before any of its
// scripts run. They must live there because Tauri sends command arguments as JSON, and the UI
// relies on that: Svelte's reactive state is made of Proxy objects, which JSON handles but
// cross-world copying refuses ("An object could not be cloned"). So arguments are turned into
// plain JSON data here first, exactly as Tauri itself would. Raw bytes (a dropped import file)
// pass through as bytes, with their request headers.
contextBridge.executeInMainWorld({
  func: (sep, delimiter) => {
    const B = window.__jamTauriBridge;
    const plain = (a) => {
      if (a === undefined || a === null) return {};
      if (a instanceof ArrayBuffer) return new Uint8Array(a);
      if (ArrayBuffer.isView(a)) return new Uint8Array(a.buffer, a.byteOffset, a.byteLength);
      return JSON.parse(JSON.stringify(a));
    };
    const plainOptions = (o) => {
      if (!o || !o.headers) return undefined;
      const h = o.headers;
      const obj = typeof Headers !== 'undefined' && h instanceof Headers ? Object.fromEntries(h.entries()) : h;
      return { headers: JSON.parse(JSON.stringify(obj)) };
    };
    window.__TAURI_INTERNALS__ = {
      __jam: true,
      invoke: (cmd, args, options) => B.invoke(cmd, plain(args), plainOptions(options)),
      transformCallback: (cb, once) => B.transformCallback(cb, once),
      unregisterCallback: (id) => B.unregisterCallback(id),
      runCallback: (id, data) => B.runCallback(id, data),
      convertFileSrc: (filePath) => 'jamytm://asset/' + encodeURIComponent(String(filePath)),
      metadata: {
        currentWindow: { label: 'main' },
        currentWebview: { windowLabel: 'main', label: 'main' },
      },
      plugins: { path: { sep, delimiter } },
    };
    window.__TAURI_EVENT_PLUGIN_INTERNALS__ = { unregisterListener: (event, id) => B.unregisterListener(event, id) };
    window.isTauri = true;
  },
  args: [sep, process.platform === 'win32' ? ';' : ':'],
});
