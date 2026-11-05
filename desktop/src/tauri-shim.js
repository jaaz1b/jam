'use strict';
// Runs in the YouTube Music UI's main world, before its own scripts. It provides the small
// `window.__TAURI_INTERNALS__` surface that @tauri-apps/api is built on (the same one Tauri's own
// `@tauri-apps/api/mocks` replaces), backed by Jam: commands and events go to the engine through
// Jam's relay; window, dialog, clipboard, updater and process calls are answered by Jam itself.
// The UI's code is therefore used unmodified — it can't tell it isn't inside Tauri.

const SHIM = `(() => {
  if (window.__TAURI_INTERNALS__ && window.__TAURI_INTERNALS__.__jam) return;
  const B = window.__jamYtm;
  if (!B) return;

  const callbacks = new Map();
  let nextCb = 1;
  const registerCallback = (cb, once) => {
    const id = nextCb++;
    callbacks.set(id, (data) => { if (once) callbacks.delete(id); return cb(data); });
    return id;
  };
  const unregisterCallback = (id) => { callbacks.delete(id); };
  const runCallback = (id, data) => { const cb = callbacks.get(id); if (cb) cb(data); };

  // Events: one engine subscription per name (Jam dedupes), fanned out to every UI listener.
  const listeners = new Map(); // eventId -> { event, handler }
  const byEvent = new Map();   // event -> Set<eventId>
  let nextEvent = 1;
  const unregisterListener = (event, eventId) => {
    const l = listeners.get(eventId);
    if (!l) return;
    listeners.delete(eventId);
    const set = byEvent.get(event); if (set) set.delete(eventId);
    unregisterCallback(l.handler);
  };
  B.onEvent((event, payload) => {
    const ids = byEvent.get(event);
    if (!ids) return;
    for (const id of [...ids]) {
      const l = listeners.get(id);
      if (l) { try { runCallback(l.handler, { event, id, payload }); } catch (e) { console.error(e); } }
    }
  });

  // Raw (binary) command results travel as base64.
  const decode = (v) => {
    if (v && typeof v === 'object' && typeof v.__jamB64 === 'string') {
      const s = atob(v.__jamB64); const u = new Uint8Array(s.length);
      for (let i = 0; i < s.length; i++) u[i] = s.charCodeAt(i);
      return u.buffer;
    }
    return v;
  };

  const LOCAL = /^plugin:(window|webview|dialog|clipboard-manager|updater|process)\\|/;

  async function invoke(cmd, args = {}, options) {
    if (cmd === 'plugin:event|listen') {
      const id = nextEvent++;
      listeners.set(id, { event: args.event, handler: args.handler });
      if (!byEvent.has(args.event)) byEvent.set(args.event, new Set());
      byEvent.get(args.event).add(id);
      B.subscribe(args.event);
      return id;
    }
    if (cmd === 'plugin:event|unlisten') { unregisterListener(args.event, args.eventId); return null; }
    const r = LOCAL.test(cmd) ? await B.local(cmd, args) : await B.invoke(cmd, args);
    if (!r || !r.ok) throw (r ? r.error : 'Jam: no response');
    return decode(r.value);
  }

  window.__TAURI_INTERNALS__ = {
    __jam: true,
    invoke,
    transformCallback: registerCallback,
    unregisterCallback,
    runCallback,
    callbacks,
    convertFileSrc: (filePath) => 'jamytm://asset/' + encodeURIComponent(filePath),
    metadata: {
      currentWindow: { label: 'main' },
      currentWebview: { windowLabel: 'main', label: 'main' },
    },
    plugins: { path: { sep: B.sep, delimiter: B.delimiter } },
  };
  window.__TAURI_EVENT_PLUGIN_INTERNALS__ = { unregisterListener };
  window.isTauri = true;
})();`;

module.exports = { SHIM };
