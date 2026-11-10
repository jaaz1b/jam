'use strict';
// Album and track covers for sites that work offline (Octave), kept by Jam itself. The same feature
// as Jam for Android's CoverStore.kt.
//
// The site's own offline worker keeps only its last few hundred images, so a saved state showed
// downloaded songs without their art. installed-app.js tells Jam which covers the site shows, and,
// when saving, every cover its saved pages and its downloads refer to; Jam downloads each once into
// its own folder (<user data>/covers), and answers the site's requests for them from there in the
// saved state, where the site has no network (main.js `blockSession`).

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const MAX_FILE = 4 << 20;
const MAX_TOTAL = 600 << 20;
const PARALLEL = 3;

let dir = null;
let fetcher = null; // (url) => Promise<Response>, a session of its own: never the blocked one
let total = -1;
const pending = new Set();
const failed = new Set();
const queue = [];
let running = 0;

function init(userData, fetchFn) {
  dir = path.join(userData, 'covers');
  fetcher = fetchFn;
  try { fs.mkdirSync(dir, { recursive: true }); } catch {}
}

const key = (url) => crypto.createHash('sha1').update(url).digest('hex');

/** The image a URL stands for: the URL itself and, for image proxies (`/api/img?u=…`, `/_next/image?url=…`), the image behind it. */
function candidates(url) {
  const out = [];
  if (/^https?:/.test(url)) out.push(url);
  try {
    const u = new URL(url);
    for (const p of ['u', 'url']) {
      const inner = u.searchParams.get(p);
      if (!inner) continue;
      if (/^https?:/.test(inner)) out.push(inner);
      else if (inner.startsWith('/')) out.push(u.origin + inner);
    }
  } catch {}
  return out;
}

function file(url) {
  if (!dir || !url) return null;
  for (const c of candidates(url)) {
    const f = path.join(dir, key(c));
    try { if (fs.statSync(f).size > 0) return f; } catch {}
  }
  return null;
}

function count() { try { return fs.readdirSync(dir).filter((n) => !n.endsWith('.part')).length; } catch { return 0; } }
function size() {
  if (total < 0) {
    total = 0;
    try { for (const n of fs.readdirSync(dir)) total += fs.statSync(path.join(dir, n)).size; } catch {}
  }
  return total;
}

/** Covers to keep: each missing one is downloaded once, in the background. Returns how many are new. */
function want(urls) {
  if (!dir || !fetcher) return 0;
  let queued = 0;
  for (const url of urls) {
    if (typeof url !== 'string' || url.length > 2000 || file(url)) continue;
    const c = candidates(url);
    const target = c[c.length - 1]; // the image itself rather than a resized copy from a proxy
    if (!target || failed.has(target) || pending.has(target)) continue;
    pending.add(target);
    queue.push([target, url]);
    queued++;
  }
  pump();
  return queued;
}

function pump() {
  while (running < PARALLEL && queue.length) {
    const [target, original] = queue.shift();
    running++;
    fetchOne(target, original).catch(() => {}).finally(() => { running--; pending.delete(target); pump(); });
  }
}

async function fetchOne(target, original) {
  if (size() >= MAX_TOTAL) return;
  for (const u of new Set([target, original])) {
    if (!/^https?:/.test(u)) continue;
    let buf = null;
    try {
      const r = await fetcher(u);
      const len = Number(r.headers.get('content-length') || -1);
      if (r.ok && len <= MAX_FILE) {
        const b = Buffer.from(await r.arrayBuffer());
        if (b.length <= MAX_FILE) buf = b;
      }
    } catch {}
    if (!buf || !mime(buf)) continue;
    const f = path.join(dir, key(target));
    try {
      fs.writeFileSync(f + '.part', buf);
      fs.renameSync(f + '.part', f);
      if (total >= 0) total += buf.length;
    } catch { try { fs.rmSync(f + '.part', { force: true }); } catch {} }
    return;
  }
  failed.add(target);
}

/** What an image is, from its first bytes (null: not an image, e.g. an error page). */
function mime(b) {
  const at = (i, s) => b.length >= i + s.length && b.toString('latin1', i, i + s.length) === s;
  if (b.length > 3 && b[0] === 0xff && b[1] === 0xd8) return 'image/jpeg';
  if (b.length > 8 && b[0] === 0x89 && at(1, 'PNG')) return 'image/png';
  if (at(0, 'RIFF') && at(8, 'WEBP')) return 'image/webp';
  if (at(0, 'GIF8')) return 'image/gif';
  if (at(4, 'ftypavif') || at(4, 'ftypavis')) return 'image/avif';
  if (at(0, '<svg') || at(0, '<?xml')) return 'image/svg+xml';
  return null;
}

/** The saved cover for a request, as a Response (any origin may use it), or null. */
function serve(url) {
  const f = file(url);
  if (!f) return null;
  try {
    const b = fs.readFileSync(f);
    const type = mime(b);
    if (!type) return null;
    return new Response(b, { status: 200, headers: { 'content-type': type, 'access-control-allow-origin': '*', 'cache-control': 'max-age=31536000' } });
  } catch { return null; }
}

function clear() {
  try { fs.rmSync(dir, { recursive: true, force: true }); fs.mkdirSync(dir, { recursive: true }); } catch {}
  total = 0;
  failed.clear();
}

module.exports = { init, want, file, serve, count, size, clear };
