'use strict';
// Saved states for music sites that work offline (Octave): the site's pages as they were when you
// saved, opened with no network at all, so the live site being down, broken or changed doesn't
// matter. The same feature as Jam for Android's SavedState.kt.
//
// Jam 3.2 (like Android 2.4) saves the pages only. The page itself (installed-app.js
// `__jamSaveState`) copies the site's offline caches (pages, page data, scripts, images; never
// audio or video) into an IndexedDB database in the site's own storage, and in the saved state
// writes them back over the caches once. The saved state is the live session with the network
// blocked (main.js `blockSession`), so downloaded songs play from where the site keeps them: they
// are never copied. Jam 3.1 copied the whole session (`persist:jam-<id>-saved`), downloads
// included, doubling the space they took; those copies are deleted at the next start (cleanUp).

const fs = require('fs');
const path = require('path');

const VERSION = 2; // a save made the 3.2 way
let file = null;
let state = { sources: {}, status: {} };

function load(userData) {
  file = path.join(userData, 'saved-states.json');
  try { state = { ...state, ...JSON.parse(fs.readFileSync(file, 'utf8')) }; } catch {}
  state.sources = state.sources || {};
  state.status = state.status || {};
}
function save() { try { fs.writeFileSync(file, JSON.stringify(state, null, 2)); } catch (e) { console.warn('saved states:', e.message); } }

/** At startup, before any session exists: delete Jam 3.1's copies of whole sessions. */
function cleanUp(userData) {
  load(userData);
  const parts = path.join(userData, 'Partitions');
  let freed = 0;
  try {
    for (const n of fs.readdirSync(parts)) {
      if (!/^jam-.+-saved$/.test(n)) continue;
      try { freed += du(path.join(parts, n)); fs.rmSync(path.join(parts, n), { recursive: true, force: true }); } catch {}
    }
  } catch {}
  let changed = 'pending' in state || 'deletePending' in state;
  delete state.pending; delete state.deletePending;
  for (const [id, s] of Object.entries(state.sources)) {
    if (s && s.v !== VERSION) { delete state.sources[id]; changed = true; }
  }
  if (changed) save();
  if (freed) console.log(`saved states: deleted Jam 3.1's session copies (${freed} bytes)`);
  return freed;
}
function du(p) {
  const st = fs.lstatSync(p);
  if (!st.isDirectory()) return st.nlink > 1 ? 0 : st.size; // hard links freed nothing
  return fs.readdirSync(p).reduce((a, n) => a + du(path.join(p, n)), 0);
}

const info = (id) => state.sources[id] || null;
const exists = (id) => !!(info(id) && info(id).savedAt);
const enabled = (id) => exists(id) && !!info(id).enabled;
function setEnabled(id, on) {
  if (!exists(id)) return;
  state.sources[id].enabled = !!on;
  save();
}
/** A save the page finished: from now on the source opens it. */
function recordSave(id, gen, pages, bytes) {
  state.sources[id] = { v: VERSION, gen, savedAt: Date.now(), pages, size: bytes, enabled: true, error: null };
  save();
}
function recordError(id, error) {
  state.sources[id] = { ...(state.sources[id] || {}), error: String(error || 'unknown error').slice(0, 300) };
  save();
}
function forget(id) { delete state.sources[id]; save(); }

// The offline-copy status a site's page reports (installed-app.js), for the "can't reach" page.
function setStatus(id, json) { state.status[id] = { ...(state.status[id] || {}), status: json }; save(); }
function appendLog(id, json) {
  const s = (state.status[id] = state.status[id] || {});
  s.log = [...(s.log || []), json].slice(-40);
  save();
}
const status = (id) => state.status[id] || {};

module.exports = {
  load, cleanUp, info, exists, enabled, setEnabled, recordSave, recordError, forget,
  setStatus, appendLog, status,
};
