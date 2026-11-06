'use strict';
// The music services Jam can play from. Each one gets its own saved login
// (separate browser storage), so switching never signs you out of anything.
//
// Built-in sources live here. You can also add your own (any non-DRM music
// site, like Monochrome) and change the link of the non-DRM built-ins from
// Settings → Sources — no reinstall needed. Those are stored in settings and
// merged in by refresh().

const BUILTIN = [
  {
    id: 'octave',
    name: 'Octave',
    url: 'https://music.octavestreaming.com/',
    domains: ['octavestreaming.com'],
    partition: null, // the default session: keeps the login from before Jam 2.0
    drm: false,
    color: '#8b5cf6',
    // Works offline once installed: Jam treats it as an installed app and offers a saved state
    // (savedstate.js, installed-app.js).
    offlineApp: true,
  },
  {
    id: 'monochrome',
    name: 'Monochrome',
    url: 'https://monochrome.st/',
    // Official instance plus its documented failover mirrors.
    domains: ['monochrome.st', 'monochrome.tf', 'lossless.wtf', 'if-it-runs-ship-it.lol'],
    authHosts: ['www.last.fm', 'last.fm'],
    partition: 'persist:jam-monochrome',
    drm: false,
    color: '#e8e8ee',
  },
  {
    id: 'spotify',
    name: 'Spotify',
    url: 'https://open.spotify.com/',
    domains: ['spotify.com', 'spotifycdn.com', 'scdn.co', 'spotify.link'],
    partition: 'persist:jam-spotify',
    drm: true,
    color: '#1ed760',
  },
  {
    id: 'apple',
    name: 'Apple Music',
    url: 'https://music.apple.com/',
    domains: ['music.apple.com', 'apple.com', 'icloud.com', 'mzstatic.com'],
    partition: 'persist:jam-apple',
    drm: true,
    color: '#fa2d48',
  },
  {
    // Not a website: Jam's built-in YouTube Music app (ytm/, based on Limusic). Its own engine
    // plays the audio; its interface runs in Jam's window. See engine.js.
    id: 'ytm',
    name: 'YouTube Music',
    kind: 'engine',
    url: 'jamytm://app/',
    domains: ['music.youtube.com', 'youtube.com', 'youtu.be', 'googleusercontent.com', 'ytimg.com'],
    partition: 'persist:jam-ytm',
    drm: false,
    color: '#ff0033',
  },
];

const MAX_CUSTOM = 12;
const PALETTE = ['#f97316', '#eab308', '#22c55e', '#06b6d4', '#3b82f6', '#a855f7', '#ec4899', '#f43f5e', '#94a3b8'];

// The live list. Mutated in place by refresh() so every holder of SOURCES sees updates.
const SOURCES = [];

const hostOf = (url) => { try { return new URL(url).hostname.toLowerCase(); } catch { return ''; } };
const matches = (host, domain) => host === domain || host.endsWith('.' + domain);
const baseHost = (url) => hostOf(url).replace(/^www\./, '');

function belongsTo(source, url) {
  const h = hostOf(url);
  return !!h && source.domains.some((d) => matches(h, d));
}

// "monochrome.tf" → "https://monochrome.tf/". Returns null if it isn't a usable web address.
function normalizeUrl(input) {
  let s = String(input || '').trim();
  if (!s) return null;
  if (!/^[a-z][a-z0-9+.-]*:\/\//i.test(s)) s = 'https://' + s;
  try {
    const u = new URL(s);
    if (!/^https?:$/.test(u.protocol) || !u.hostname || !u.hostname.includes('.') && u.hostname !== 'localhost') return null;
    return u.href;
  } catch { return null; }
}

function normalizeDomains(input) {
  const list = Array.isArray(input) ? input : String(input || '').split(/[\s,]+/);
  const out = [];
  for (const raw of list) {
    const d = String(raw).trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/.*$/, '').replace(/^\*\./, '').replace(/^www\./, '');
    if (/^[a-z0-9.-]+\.[a-z]{2,}$/.test(d) && !out.includes(d)) out.push(d);
  }
  return out.slice(0, 10);
}

// Validate a user-entered custom source. Returns { source } or { error }.
function cleanCustom(input, existing = []) {
  const name = String(input.name || '').trim().slice(0, 40);
  if (!name) return { error: 'Give it a name.' };
  const url = normalizeUrl(input.url);
  if (!url) return { error: "That link doesn't look like a web address (e.g. https://monochrome.tf)." };
  let id = String(input.id || '');
  if (!/^c_[a-z0-9]{4,16}$/.test(id)) id = 'c_' + Math.random().toString(36).slice(2, 10);
  const taken = existing.filter((c) => c.id !== id);
  if (taken.length >= MAX_CUSTOM) return { error: `You can add up to ${MAX_CUSTOM} sources.` };
  if ([...BUILTIN, ...taken].some((s) => s.name.toLowerCase() === name.toLowerCase())) return { error: `There's already a source called “${name}”.` };
  const color = /^#[0-9a-f]{6}$/i.test(input.color || '') ? input.color.toLowerCase() : PALETTE[taken.length % PALETTE.length];
  return { source: { id, name, url, color, extraDomains: normalizeDomains(input.extraDomains) } };
}

function sanitizeCustomList(list) {
  if (!Array.isArray(list)) return [];
  const out = [];
  for (const item of list.slice(0, MAX_CUSTOM)) {
    const r = cleanCustom(item || {}, out);
    if (r.source) out.push(r.source);
  }
  return out;
}

function sanitizeUrlOverrides(obj) {
  const out = {};
  if (!obj || typeof obj !== 'object') return out;
  for (const b of BUILTIN) {
    if (b.drm || b.kind === 'engine' || !(b.id in obj)) continue;
    const u = normalizeUrl(obj[b.id]);
    if (u && u !== b.url) out[b.id] = u;
  }
  return out;
}

function refresh(settings = {}) {
  const overrides = settings.sourceUrls || {};
  const list = BUILTIN.map((b) => {
    const s = { ...b, domains: [...b.domains], defaultUrl: b.url, custom: false };
    if (!b.drm && b.kind !== 'engine' && overrides[b.id]) {
      s.url = overrides[b.id];
      s.customUrl = true;
      const h = baseHost(s.url);
      if (h && !s.domains.some((d) => matches(h, d))) s.domains.push(h);
    }
    return s;
  });
  for (const c of settings.customSources || []) {
    list.push({
      id: c.id,
      name: c.name,
      url: c.url,
      domains: [baseHost(c.url), ...(c.extraDomains || [])].filter(Boolean),
      extraDomains: c.extraDomains || [],
      authHosts: [],
      partition: 'persist:jam-' + c.id,
      drm: false,
      color: c.color,
      custom: true,
    });
  }
  // Local testing only: JAM_DEV_SOURCES='{"octave":"http://localhost:8765/"}'
  try {
    const over = JSON.parse(process.env.JAM_DEV_SOURCES || '{}');
    for (const s of list) if (over[s.id]) { s.url = over[s.id]; s.domains.push(hostOf(over[s.id])); }
    if (process.env.OCTAVE_DEV_URL) { s0(list).url = process.env.OCTAVE_DEV_URL; s0(list).domains.push(hostOf(process.env.OCTAVE_DEV_URL)); }
  } catch {}
  SOURCES.splice(0, SOURCES.length, ...list);
  return SOURCES;
}
const s0 = (list) => list[0];

refresh();

const byId = (id) => SOURCES.find((s) => s.id === id) || SOURCES[0];
const exists = (id) => SOURCES.some((s) => s.id === id);

module.exports = {
  SOURCES, BUILTIN, PALETTE, MAX_CUSTOM,
  byId, exists, belongsTo, hostOf, matches,
  refresh, cleanCustom, sanitizeCustomList, sanitizeUrlOverrides, normalizeUrl,
};
