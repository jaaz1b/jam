'use strict';
// Scripts that run inside the Octave page's main world.

// Reads what's currently playing. Prefers the Media Session API (what the OS
// media overlay uses), falls back to the media element and the page title.
const READ_STATE_FN = `function __octaveReadState() {
  const abs = (u) => { try { return new URL(u, location.href).href; } catch { return null; } };
  const els = Array.from(document.querySelectorAll('audio,video'));
  let el = window.__octaveMedia || null;
  const live = els.find((e) => !e.paused && !e.ended);
  if (live) el = live;
  else if (!el) el = els.find((e) => e.currentSrc) || null;

  const ms = navigator.mediaSession;
  const md = ms && ms.metadata;
  let artwork = null;
  if (md && md.artwork && md.artwork.length) {
    const size = (a) => { const m = /(\\d+)x(\\d+)/.exec(a.sizes || ''); return m ? +m[1] * +m[2] : 0; };
    const best = [...md.artwork].sort((a, b) => size(b) - size(a))[0];
    artwork = best && abs(best.src);
  }
  if (!artwork && el && el.poster) artwork = abs(el.poster);

  const num = (n) => (typeof n === 'number' && isFinite(n) ? n : null);
  return {
    title: (md && md.title) || '',
    artist: (md && md.artist) || '',
    album: (md && md.album) || '',
    artwork,
    playbackState: (ms && ms.playbackState) || 'none',
    hasMedia: !!(el && (el.currentSrc || el.src)),
    paused: el ? (el.paused || el.ended) : null,
    currentTime: el ? num(el.currentTime) : null,
    duration: el ? num(el.duration) : null,
    docTitle: document.title || '',
    url: location.href,
  };
}`;

// Event-driven reporter: instead of the app asking the page "what's playing?"
// every second, the page tells the app only when something actually changes
// (play, pause, seek, new track, new metadata). Safe to run twice.
const HOOK = `(() => {
  if (window.__octaveDesktopHooked) return true;
  window.__octaveDesktopHooked = true;
  ${READ_STATE_FN}
  window.__octaveReadState = __octaveReadState;

  let timer = null, lastKey = '';
  const report = (force) => {
    timer = null;
    let st;
    try { st = __octaveReadState(); } catch (e) { return; }
    // Position changes constantly; only resend it when the user seeks or the track changes.
    const key = JSON.stringify({ ...st, currentTime: null, docTitle: st.title ? '' : st.docTitle });
    if (!force && key === lastKey) return;
    lastKey = key;
    // A private event name, so the site's own message handlers never see it.
    window.dispatchEvent(new CustomEvent('__octave-now-playing', { detail: JSON.stringify(st) }));
  };
  const schedule = (force) => {
    if (force) { clearTimeout(timer); timer = setTimeout(() => report(true), 120); return; }
    if (!timer) timer = setTimeout(() => report(false), 120);
  };
  window.__octaveReport = () => schedule(true);

  const EVENTS = ['play', 'playing', 'pause', 'ended', 'seeked', 'loadedmetadata', 'durationchange', 'emptied', 'ratechange'];
  const FORCE = new Set(['seeked', 'playing', 'ratechange']);
  const onMedia = (e) => {
    if (!(e.target instanceof HTMLMediaElement)) return;
    if (e.type === 'play' || e.type === 'playing') window.__octaveMedia = e.target;
    schedule(FORCE.has(e.type));
  };
  // In-page elements: one capturing listener on the document catches them all.
  for (const ev of EVENTS) document.addEventListener(ev, onMedia, true);
  // Detached elements (new Audio()): attach listeners the first time they play.
  const watched = new WeakSet();
  const watch = (el) => {
    if (watched.has(el) || el.isConnected) return;
    watched.add(el);
    for (const ev of EVENTS) el.addEventListener(ev, onMedia);
  };
  try {
    const origPlay = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function (...args) {
      window.__octaveMedia = this;
      watch(this);
      schedule(true);
      return origPlay.apply(this, args);
    };
  } catch (e) {}

  // Notice new track info the moment the site sets it.
  const wrapSetter = (proto, prop) => {
    try {
      const d = Object.getOwnPropertyDescriptor(proto, prop);
      if (!d || !d.set) return;
      Object.defineProperty(proto, prop, {
        configurable: true, enumerable: d.enumerable,
        get() { return d.get.call(this); },
        set(v) { d.set.call(this, v); schedule(false); },
      });
    } catch (e) {}
  };
  if (window.MediaSession) { wrapSetter(MediaSession.prototype, 'metadata'); wrapSetter(MediaSession.prototype, 'playbackState'); }

  // Remember the site's own play/pause/next/previous handlers (the ones media keys
  // use), so Jam can drive each service the way it expects and its UI stays in sync.
  const handlers = {};
  try {
    const origSet = MediaSession.prototype.setActionHandler;
    MediaSession.prototype.setActionHandler = function (action, fn) {
      handlers[action] = fn;
      return origSet.call(this, action, fn);
    };
  } catch (e) {}
  const mediaEls = () => {
    const els = Array.from(document.querySelectorAll('audio,video'));
    if (window.__octaveMedia && !els.includes(window.__octaveMedia)) els.push(window.__octaveMedia);
    return els;
  };
  window.__jamControl = (action) => {
    const els = mediaEls();
    const playing = els.some((e) => !e.paused && !e.ended);
    if (action === 'toggle') action = playing ? 'pause' : 'play';
    if (action === 'pause' && !playing) return true;
    const h = handlers[action];
    if (typeof h === 'function') { try { h({ action }); return true; } catch (e) {} }
    if (action === 'pause') { els.forEach((e) => { try { e.pause(); } catch (x) {} }); return true; }
    if (action === 'play') { const el = window.__octaveMedia || els.find((e) => e.currentSrc); if (el) { el.play(); return true; } }
    return false;
  };
  if (window.MediaMetadata) for (const p of ['title', 'artist', 'album', 'artwork']) wrapSetter(MediaMetadata.prototype, p);

  // Cheap safety net for sites that do something unusual: compares state
  // in-page and only messages the app if it changed. Paused when hidden.
  setInterval(() => schedule(false), 5000);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', () => schedule(true), { once: true });
  else schedule(true);
  return true;
})();`;

const READ_STATE = `(window.__octaveReadState ? window.__octaveReadState() : null)`;

const control = (action) => `(window.__jamControl ? window.__jamControl(${JSON.stringify(action)}) : false)`;

// Optional "Reduce animations": makes CSS animations/transitions finish
// instantly (end events still fire, so the site keeps working).
const REDUCE_MOTION_CSS = `*, *::before, *::after {
  animation-duration: 0.001ms !important; animation-delay: 0s !important; animation-iteration-count: 1 !important;
  transition-duration: 0.001ms !important; transition-delay: 0s !important; scroll-behavior: auto !important;
}`;

module.exports = { HOOK, READ_STATE, control, REDUCE_MOTION_CSS };
