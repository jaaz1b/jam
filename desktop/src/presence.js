'use strict';
// Turns "what's playing" + the user's customisation settings into a Discord activity.
// Pure functions, shared by the RPC sender and the settings preview, so the
// preview always shows exactly what Discord receives.

const DEFAULT_APP_NAME = 'Jam';

// Sample track for the preview when nothing is playing yet.
const SAMPLE = {
  title: 'Midnight Drive',
  artist: 'Neon Coast',
  album: 'Afterglow',
  artwork: null,
  playing: true,
  currentTime: 96,
  readAt: null,
  duration: 214,
  url: 'https://music.octavestreaming.com/',
  sourceName: 'Octave',
};

const STATUS_DISPLAY = { app: 0, line2: 1, line1: 2 }; // Discord: NAME=0, STATE=1, DETAILS=2

function appName(s) {
  return (s.appName || '').trim() || DEFAULT_APP_NAME;
}

// Fill {title} {artist} {album} {app}, then tidy separators left dangling by empty tokens.
function render(template, np, s) {
  const vals = { title: np.title || '', artist: np.artist || '', album: np.album || '', app: appName(s), source: np.sourceName || '' };
  let hadEmpty = false;
  let out = String(template || '').replace(/\{(title|artist|album|app|source)\}/gi, (_, k) => {
    const v = vals[k.toLowerCase()];
    if (!v) hadEmpty = true;
    return v;
  });
  if (!hadEmpty) return out.trim();
  // A token was empty: tidy separators it left dangling ("Song — " → "Song").
  const SEP = '\\s*[-–—|·•:,/]\\s*';
  out = out
    .replace(new RegExp(`^(?:${SEP})+`, 'i'), '')
    .replace(new RegExp(`(?:${SEP})+$`, 'i'), '')
    .replace(/\s*([-–—|·•])\s*(?:[-–—|·•]\s*)+/g, ' $1 ')
    .replace(/\(\s*\)|\[\s*\]/g, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
  return out;
}

function clamp(str, max = 128) {
  let s = String(str || '').trim();
  if (!s) return undefined;
  if (s.length < 2) s = s.padEnd(2, '\u200b');
  return s.length > max ? s.slice(0, max - 1) + '…' : s;
}

const isHttps = (u) => /^https:\/\/\S+$/i.test(String(u || '').trim());
const isHttp = (u) => /^https?:\/\/\S+$/i.test(String(u || '').trim());

function fallbackImage(s) {
  const f = String(s.fallbackImage || '').trim();
  if (isHttps(f) && f.length <= 256) return f;
  if (/^[a-z0-9_-]{1,32}$/i.test(f)) return f.toLowerCase();
  return 'jam';
}

/**
 * @param np   now-playing info (or null)
 * @param s    settings
 * @param now  Date.now() (injectable for tests)
 * @returns Discord activity object, or null to clear the status
 */
function buildActivity(np, s, now = Date.now()) {
  if (!np) return null;
  if (!np.playing && !s.showWhenPaused) return null;

  const name = appName(s);

  if (s.hideDetails) {
    return {
      type: 2,
      status_display_type: 0,
      details: clamp(np.playing ? 'Listening to music' : 'Paused'),
      assets: { large_image: fallbackImage(s), large_text: clamp(name) },
      instance: false,
    };
  }

  let details = render(s.line1, np, s);
  let state = render(s.line2, np, s);
  const largeText = render(s.line3, np, s);

  if (!np.playing && s.pausedLabel && s.pausedLabel.trim()) {
    const label = s.pausedLabel.trim();
    state = state ? `${label} · ${state}` : label;
  }
  if (!details) details = np.title || name;

  const activity = {
    type: 2, // "Listening to"
    status_display_type: STATUS_DISPLAY[s.statusDisplay] ?? 2,
    details: clamp(details),
    state: clamp(state),
    assets: {
      large_image: (s.showAlbumArt && np.artwork) || fallbackImage(s),
      large_text: clamp(largeText),
    },
    instance: false,
  };
  if (!activity.state) delete activity.state;
  if (!activity.assets.large_text) delete activity.assets.large_text;

  if (np.playing && s.progress !== 'off' && np.currentTime != null) {
    // Position is reported only on play/seek events, so extrapolate from when it was read.
    const elapsedMs = np.currentTime * 1000 + (np.readAt ? now - np.readAt : 0);
    const start = now - Math.round(elapsedMs);
    if (s.progress === 'elapsed') activity.timestamps = { start };
    else if (np.duration && np.duration > 0) activity.timestamps = { start, end: start + Math.round(np.duration * 1000) };
    else activity.timestamps = { start };
  }

  const buttons = [];
  if (s.button1Enabled) {
    const label = render(s.button1Label, np, s) || `Listen on ${name}`;
    const custom = String(s.button1Url || '').trim();
    const url = isHttp(custom) ? custom : np.url;
    if (isHttp(url) && url.length <= 512) buttons.push({ label: label.slice(0, 32).trim(), url });
  }
  if (s.button2Enabled) {
    const label = render(s.button2Label, np, s);
    const url = String(s.button2Url || '').trim();
    if (label && isHttp(url) && url.length <= 512) buttons.push({ label: label.slice(0, 32).trim(), url });
  }
  if (buttons.length) activity.buttons = buttons;

  return activity;
}

// What Discord shows next to your name in the member list.
function memberListText(activity, s) {
  if (!activity) return null;
  switch (activity.status_display_type) {
    case 1: return activity.state || appName(s);
    case 2: return activity.details || appName(s);
    default: return appName(s);
  }
}

module.exports = { buildActivity, memberListText, render, SAMPLE, DEFAULT_APP_NAME, appName };
