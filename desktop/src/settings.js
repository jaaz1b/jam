'use strict';

const fs = require('fs');
const path = require('path');
const { app } = require('electron');

// Everything the user can customise about their Discord status.
const PRESENCE_DEFAULTS = {
  rpcEnabled: true,
  showWhenPaused: false,
  hideDetails: false,
  statusDisplay: 'line1', // member list text: 'line1' | 'line2' | 'app'
  appName: '',            // empty = "Jam"
  line1: '{title}',
  line2: '{artist}',
  line3: '{album}',
  pausedLabel: 'Paused',
  progress: 'bar',        // 'bar' | 'elapsed' | 'off'
  showAlbumArt: true,
  fallbackImage: 'jam',
  button1Enabled: true,
  button1Label: 'Listen on {app}',
  button1Url: '',         // empty = the page you're on
  button2Enabled: false,
  button2Label: '',
  button2Url: '',
};

const DEFAULTS = {
  // Sources
  defaultSource: 'octave',
  openLastSource: false,
  lastSource: 'octave',
  keepWarmMinutes: 5,     // how long a switched-away source stays loaded (0 = free it now, -1 = until quit)
  customSources: [],      // your own sources: [{ id, name, url, color, extraDomains }]
  sourceUrls: {},         // changed links for built-in non-DRM sources: { octave: 'https://…' }
  discordClientId: '',
  ...PRESENCE_DEFAULTS,
  closeToTray: false,
  reduceMotion: false,
  windowBounds: { width: 1280, height: 800 },
  maximized: false,
  firstRunDone: false,
};

// Allowed values for settings the UI can change, used to validate input.
const sources = require('./sources');
const SOURCE_ID = /^(octave|monochrome|spotify|apple|ytm|c_[a-z0-9]{4,16})$/;
const SCHEMA = {
  defaultSource: (v) => (SOURCE_ID.test(String(v)) ? String(v) : 'octave'),
  lastSource: (v) => (SOURCE_ID.test(String(v)) ? String(v) : 'octave'),
  customSources: (v) => sources.sanitizeCustomList(v),
  sourceUrls: (v) => sources.sanitizeUrlOverrides(v),
  openLastSource: Boolean,
  keepWarmMinutes: (v) => ([0, 2, 5, 15, -1].includes(Number(v)) ? Number(v) : 5),
  discordClientId: (v) => String(v || '').replace(/\D/g, '').slice(0, 24),
  rpcEnabled: Boolean, showWhenPaused: Boolean, hideDetails: Boolean, showAlbumArt: Boolean,
  button1Enabled: Boolean, button2Enabled: Boolean, closeToTray: Boolean, reduceMotion: Boolean,
  statusDisplay: (v) => (['line1', 'line2', 'app'].includes(v) ? v : 'line1'),
  progress: (v) => (['bar', 'elapsed', 'off'].includes(v) ? v : 'bar'),
  appName: (v) => String(v ?? '').slice(0, 64),
  line1: (v) => String(v ?? '').slice(0, 200),
  line2: (v) => String(v ?? '').slice(0, 200),
  line3: (v) => String(v ?? '').slice(0, 200),
  pausedLabel: (v) => String(v ?? '').slice(0, 40),
  fallbackImage: (v) => String(v ?? '').trim().slice(0, 256),
  button1Label: (v) => String(v ?? '').slice(0, 64),
  button1Url: (v) => String(v ?? '').trim().slice(0, 512),
  button2Label: (v) => String(v ?? '').slice(0, 64),
  button2Url: (v) => String(v ?? '').trim().slice(0, 512),
};

let cache = null;
const file = () => path.join(app.getPath('userData'), 'settings.json');

function load() {
  if (cache) return cache;
  let saved = {};
  try { saved = JSON.parse(fs.readFileSync(file(), 'utf8')); } catch {}
  // v1.0 used a single "showButton" toggle.
  if ('showButton' in saved && !('button1Enabled' in saved)) saved.button1Enabled = !!saved.showButton;
  delete saved.showButton;
  // v1.4 renamed the app from Octave to Jam (and its Discord art asset with it).
  if (saved.fallbackImage === 'octave') saved.fallbackImage = 'jam';
  cache = { ...DEFAULTS, ...saved };
  return cache;
}

function save() {
  try {
    fs.mkdirSync(path.dirname(file()), { recursive: true });
    fs.writeFileSync(file(), JSON.stringify(cache, null, 2));
  } catch (e) {
    console.error('Failed to save settings', e);
  }
}

function get(key) { return load()[key]; }
function all() { return { ...load() }; }
function set(patch) { Object.assign(load(), patch); save(); return all(); }

// Validate a patch coming from the settings UI.
function sanitize(patch) {
  const out = {};
  for (const [k, fn] of Object.entries(SCHEMA)) if (patch && k in patch) out[k] = fn(patch[k]);
  return out;
}

function resetPresence() { return set({ ...PRESENCE_DEFAULTS }); }

module.exports = { get, set, all, sanitize, resetPresence, PRESENCE_DEFAULTS };
