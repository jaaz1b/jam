'use strict';
// Saved states for music sites that work offline (Octave): a frozen copy of everything the site
// keeps in Jam (its pages, its offline worker, its downloaded tracks, its login) that opens with no
// network at all, so the live site being down, broken or changed doesn't matter. The same feature
// as Jam for Android's SavedState.kt.
//
// The live site runs in its own session (Octave: Electron's default session, kept from before Jam
// 2.0; others: `persist:jam-<id>`); its saved state is the session `persist:jam-<id>-saved`.
// Saving copies the first session's folder over the second. Chromium keeps those folders open
// while a session exists, which in Electron is until the app quits, so the copy happens at the
// start of the next launch, before either is opened: Save marks it pending and relaunches Jam.
//
// Large files that Chromium never changes once written (database tables, stored blobs such as the
// downloaded tracks, cached responses) are hard-linked rather than copied: the saved state costs no
// extra space for them, and deleting a download on the live site doesn't take it out of the save.

const fs = require('fs');
const path = require('path');

let file = null;
let state = { sources: {}, pending: null, deletePending: null, status: {} };

// The parts of a session's folder that hold the site's data. For the default session the folder is
// the whole user-data folder, which also holds Jam's own files and every other session.
const SITE_DATA = ['Cookies', 'Cookies-journal', 'Local Storage', 'IndexedDB', 'Service Worker', 'Session Storage',
  'WebStorage', 'File System', 'blob_storage', 'databases', 'Network Persistent State', 'TransportSecurity',
  'Shared Dictionary', 'shared_proto_db', 'Trust Tokens', 'Trust Tokens-journal'];
const CACHES = new Set(['Cache', 'Code Cache', 'GPUCache', 'DawnGraphiteCache', 'DawnWebGPUCache']);

function load(userData) {
  file = path.join(userData, 'saved-states.json');
  try { state = { ...state, ...JSON.parse(fs.readFileSync(file, 'utf8')) }; } catch {}
  state.sources = state.sources || {};
  state.status = state.status || {};
}
function save() { try { fs.writeFileSync(file, JSON.stringify(state, null, 2)); } catch (e) { console.warn('saved states:', e.message); } }

const savedPartition = (id) => `persist:jam-${id}-saved`;
const partitionDir = (userData, partition) => path.join(userData, 'Partitions', partition.replace(/^persist:/, ''));
const liveDir = (userData, src) => (src.partition ? partitionDir(userData, src.partition) : userData);

const info = (id) => state.sources[id] || null;
const exists = (id) => !!(info(id) && info(id).savedAt);
const enabled = (id) => exists(id) && !!info(id).enabled;
function setEnabled(id, on) {
  if (!exists(id)) return;
  state.sources[id].enabled = !!on;
  save();
}

/** At startup, before any session exists: make a save or a delete asked for before the relaunch. */
function applyPending(userData, byId) {
  load(userData);
  const del = state.deletePending;
  const id = state.pending;
  if (!del && !id) return null;
  state.pending = null;
  state.deletePending = null;
  save();
  if (del) {
    try { fs.rmSync(partitionDir(userData, savedPartition(del)), { recursive: true, force: true }); } catch {}
    delete state.sources[del];
    save();
  }
  if (!id) return null;
  const t0 = Date.now();
  const src = byId(id);
  const from = liveDir(userData, src);
  const to = partitionDir(userData, savedPartition(id));
  const stats = { bytes: 0, linked: 0, files: 0 };
  try {
    fs.rmSync(to, { recursive: true, force: true });
    fs.mkdirSync(to, { recursive: true });
    const names = src.partition ? fs.readdirSync(from).filter((n) => !CACHES.has(n)) : SITE_DATA;
    for (const n of names) {
      const p = path.join(from, n);
      if (fs.existsSync(p)) copyTree(p, path.join(to, n), stats);
    }
    if (!stats.files) throw new Error(`Nothing of ${src.name} was found to save. Open it with internet first.`);
    state.sources[id] = { savedAt: Date.now(), size: stats.bytes, enabled: true, error: null };
    console.log(`saved state for ${id}: ${stats.files} files, ${stats.bytes} bytes (${stats.linked} linked) in ${Date.now() - t0} ms`);
  } catch (e) {
    console.warn('saved state failed:', e);
    state.sources[id] = { ...(state.sources[id] || {}), error: e.message };
  }
  save();
  return id;
}

function copyTree(from, to, stats) {
  const st = fs.statSync(from);
  if (st.isDirectory()) {
    fs.mkdirSync(to, { recursive: true });
    for (const n of fs.readdirSync(from)) copyTree(path.join(from, n), path.join(to, n), stats);
    return;
  }
  stats.bytes += st.size; stats.files++;
  if (st.size >= 256 * 1024 && immutable(from)) {
    try { fs.linkSync(from, to); stats.linked += st.size; return; } catch {}
  }
  fs.copyFileSync(from, to);
}

// Files Chromium writes once and never changes in place: safe to share between the two.
function immutable(p) {
  const n = path.basename(p);
  const norm = p.split(path.sep).join('/');
  return /\.(ldb|sst)$/.test(n) || norm.includes('.blob/') || (norm.includes('/CacheStorage/') && /^[0-9a-f]{16}_[0-9s]$/.test(n));
}

function requestSave(id) { state.pending = id; save(); }
function requestDelete(id) { state.deletePending = id; if (state.sources[id]) state.sources[id].enabled = false; save(); }

// The offline-copy status a site's page reports (installed-app.js), for the "can't reach" page.
function setStatus(id, json) { state.status[id] = { ...(state.status[id] || {}), status: json }; save(); }
function appendLog(id, json) {
  const s = (state.status[id] = state.status[id] || {});
  s.log = [...(s.log || []), json].slice(-40);
  save();
}
const status = (id) => state.status[id] || {};

module.exports = {
  load, applyPending, savedPartition, info, exists, enabled, setEnabled, requestSave, requestDelete,
  setStatus, appendLog, status,
};
