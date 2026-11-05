'use strict';

const electron = require('electron');
const { app, BrowserWindow, WebContentsView, Menu, Tray, shell, ipcMain, nativeImage, nativeTheme, session, protocol, net, dialog, clipboard } = electron;
const components = electron.components; // castLabs Electron (Widevine DRM); undefined on stock Electron
const fs = require('fs');
const path = require('path');
const settings = require('./settings');
const { DiscordRPC } = require('./discord-rpc');
const { HOOK, control, REDUCE_MOTION_CSS } = require('./page-scripts');
const presence = require('./presence');
const sources = require('./sources');
const { Engine, locate: locateEngine } = require('./engine');
const { SOURCES, byId, belongsTo, hostOf, matches } = sources;

// Sign-in providers that should open as an in-app popup (sharing the source's saved login).
const AUTH_HOSTS = ['accounts.google.com', 'discord.com', 'appleid.apple.com', 'idmsa.apple.com', 'github.com',
  'login.microsoftonline.com', 'www.facebook.com', 'facebook.com', 'api.twitter.com', 'x.com', 'checkout.stripe.com'];
const ICON_PATH = path.join(__dirname, '..', 'build', 'icon.png');
const SMALL_ICON_PATH = path.join(__dirname, '..', 'build', 'icon-small.png');
const MIN_RPC_GAP_MS = 4000; // Discord allows ~5 presence updates per 20s.
const TITLEBAR_H = 36;
const BG = '#0f0f14';

let mainWindow = null;
let settingsWindow = null;
let tray = null;
let lastNowPlaying = null;
const rpc = new DiscordRPC();
let rpcState = { state: 'disabled', user: null };

app.setAppUserModelId('com.octavestreaming.desktop');

// Octave → Jam rename: bring settings and your login over from the old data folder on first launch.
(function migrateFromOctave() {
  try {
    const appData = app.getPath('appData');
    const oldDir = path.join(appData, 'Octave');
    const newDir = app.getPath('userData'); // …/Jam
    // Electron creates the new folder at startup, so check for our own settings file instead.
    if (oldDir === newDir || !fs.existsSync(path.join(oldDir, 'settings.json'))) return;
    if (fs.existsSync(path.join(newDir, 'settings.json')) || fs.existsSync(path.join(newDir, '.migrated'))) return;
    const SKIP = new Set(['Cache', 'Code Cache', 'GPUCache', 'DawnGraphiteCache', 'DawnWebGPUCache', 'Crashpad', 'logs', 'SingletonLock', 'SingletonCookie', 'SingletonSocket', 'lockfile']);
    fs.cpSync(oldDir, newDir, { recursive: true, force: true, filter: (src) => !SKIP.has(path.basename(src)) || path.dirname(src) !== oldDir });
    fs.writeFileSync(path.join(newDir, '.migrated'), 'from Octave\n');
  } catch (e) { console.warn('Octave → Jam migration skipped:', e.message); }
})();

// The YouTube Music UI is served from jamytm://app/…, its local files (covers, fonts) from jamytm://asset/….
protocol.registerSchemesAsPrivileged([{
  scheme: 'jamytm',
  privileges: { standard: true, secure: true, supportFetchAPI: true, corsEnabled: true, stream: true, codeCache: true },
}]);

app.commandLine.appendSwitch('autoplay-policy', 'no-user-gesture-required');
// Lower memory: don't keep a spare renderer process warm in the background.
app.commandLine.appendSwitch('disable-features', 'SpareRendererForSitePerProcess');
// Some sites block Electron's default user agent; present as plain Chrome.
app.userAgentFallback = app.userAgentFallback
  .replace(/\s?Electron\/[\d.]+(\+\w+)?/i, '')
  .replace(new RegExp(`\\s?${app.getName()}\\/[\\d.]+`, 'i'), '');

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => showMain());
  app.whenReady().then(init);
}

// ---------------------------------------------------------------- helpers

const isHttp = (url) => /^https?:\/\//i.test(url || '');
const isAuth = (src, url) => {
  const h = hostOf(url);
  return AUTH_HOSTS.some((d) => matches(h, d)) || (src.authHosts || []).some((d) => matches(h, d));
};

function showMain() {
  if (!mainWindow) { createMainWindow(); return; }
  if (mainWindow.isMinimized()) mainWindow.restore();
  mainWindow.show();
  mainWindow.focus();
}

function appIcon(size) {
  const img = nativeImage.createFromPath(ICON_PATH);
  return size ? img.resize({ width: size, height: size }) : img;
}

const quit = () => { app.isQuitting = true; app.quit(); };

// ---------------------------------------------------------------- protected playback (Spotify, Apple Music)

let widevinePromise = null;
let widevineState = components ? 'idle' : 'unsupported'; // idle | loading | ready | failed | unsupported

function ensureWidevine() {
  if (!components) return Promise.resolve(false);
  if (!widevinePromise) {
    widevineState = 'loading';
    widevinePromise = components.whenReady()
      .then(() => { widevineState = 'ready'; return true; })
      .catch((e) => { console.warn('Widevine not ready:', e && e.message); widevineState = 'failed'; return false; })
      .finally(() => { sendTitlebarState(); sendSources(); });
  }
  return widevinePromise;
}

// Windows (and macOS) only play protected music from a castLabs-signed app ("VMP").
// The one-time setup writes Jam.exe.sig plus a marker naming the signed version.
const vmpMarker = () => path.join(path.dirname(process.execPath), 'jam-vmp.json');
function vmpStatus() {
  if (!components) return 'unsupported';
  if (process.platform === 'linux') return 'not-needed';
  if (!app.isPackaged) return 'dev';
  try {
    const m = JSON.parse(fs.readFileSync(vmpMarker(), 'utf8').replace(/^\uFEFF/, ''));
    if (m.version === app.getVersion() && fs.existsSync(process.execPath + '.sig')) return 'signed';
  } catch {}
  return 'needs-setup';
}

let setupLaunchedAt = 0;

// Opens the one-time setup in a normal console window. (Node's `detached`
// spawn gives a console program *no* window on Windows, so the script would
// run invisibly and exit at its first question. Opening a .cmd file through
// the shell, like a double-click, always shows a window.)
async function runDrmSetup() {
  if (process.platform !== 'win32' || !app.isPackaged) {
    shell.openExternal('https://github.com/castlabs/electron-releases/wiki/EVS');
    return;
  }
  const script = path.join(process.resourcesPath, 'drm-setup.ps1');
  const q = (v) => '"' + String(v).replace(/%/g, '%%').replace(/"/g, '') + '"'; // cmd-safe quoting
  const launcher = path.join(app.getPath('temp'), 'jam-drm-setup.cmd');
  const lines = [
    '@echo off',
    'title Jam - Spotify ^& Apple Music setup',
    `powershell.exe -NoProfile -ExecutionPolicy Bypass -File ${q(script)} -AppDir ${q(path.dirname(process.execPath))} -ExePath ${q(process.execPath)} -Version ${q(app.getVersion())}`,
    'if errorlevel 1 pause',
  ];
  try {
    if (!fs.existsSync(script)) throw new Error(`Setup script missing: ${script}`);
    fs.writeFileSync(launcher, lines.join('\r\n') + '\r\n');
    const err = await shell.openPath(launcher);
    if (err) throw new Error(err);
    setupLaunchedAt = Date.now();
    sendTitlebarState();
    setTimeout(sendTitlebarState, 60000);
  } catch (e) {
    console.warn('DRM setup launch failed:', e.message);
    electron.dialog.showMessageBox(mainWindow || undefined, {
      type: 'error',
      title: 'Jam',
      message: "Couldn't open the setup window",
      detail: `${e.message}\n\nYou can run it yourself: right-click Start → Terminal, then paste:\n\n` +
        `powershell -ExecutionPolicy Bypass -File "${script}" -AppDir "${path.dirname(process.execPath)}" -ExePath "${process.execPath}" -Version "${app.getVersion()}"`,
    });
  }
}

// ---------------------------------------------------------------- sources (one view + saved login each)

const views = new Map(); // id -> { id, src, view, discardTimer, httpError, waitingDrm, reduceMotionKey }
let activeId = null;
const active = () => views.get(activeId) || null;
const site = () => { const e = active(); return e && !e.view.webContents.isDestroyed() ? e.view.webContents : null; };
const activeSource = () => byId(activeId || settings.get('defaultSource'));

const preparedSessions = new Set();
function sessionFor(src) {
  const ses = src.partition ? session.fromPartition(src.partition) : session.defaultSession;
  if (!preparedSessions.has(ses)) {
    preparedSessions.add(ses);
    // Grant what a music site needs (incl. protected playback); deny the rest.
    const ALLOWED = new Set(['media', 'mediaKeySystem', 'notifications', 'fullscreen', 'clipboard-sanitized-write', 'speaker-selection']);
    ses.setPermissionRequestHandler((_wc, perm, cb) => cb(ALLOWED.has(perm)));
  }
  return ses;
}

function layoutViews() {
  if (!mainWindow) return;
  const [w, h] = mainWindow.getContentSize();
  const top = mainWindow.isFullScreen() ? 0 : TITLEBAR_H;
  const bounds = { x: 0, y: top, width: w, height: Math.max(0, h - top) };
  for (const e of views.values()) e.view.setBounds(bounds);
}

function createView(id) {
  const src = byId(id);
  if (src.kind === 'engine') return createEngineView(src);
  const entry = { id: src.id, src, view: null, discardTimer: null, httpError: null, waitingDrm: false, reduceMotionKey: null };
  entry.view = new WebContentsView({
    webPreferences: {
      session: sessionFor(src),
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      // Chromium pauses animations and slows timers while hidden/minimised. Audio
      // keeps playing; now-playing is event-driven so nothing depends on timers.
      backgroundThrottling: true,
      spellcheck: false,
      additionalArguments: ['--octave-hook=' + Buffer.from(HOOK).toString('base64')],
    },
  });
  entry.view.setBackgroundColor(BG);
  entry.view.setVisible(false);
  views.set(src.id, entry);
  wireView(entry);
  mainWindow.contentView.addChildView(entry.view);
  layoutViews();

  const load = () => {
    if (!entry.view.webContents.isDestroyed()) entry.view.webContents.loadURL(src.url).catch(() => {}); // errors → did-fail-load
  };
  if (src.drm && components) {
    entry.waitingDrm = true;
    sendTitlebarState();
    ensureWidevine().then(() => { entry.waitingDrm = false; sendTitlebarState(); load(); });
  } else {
    load();
  }
  return entry;
}

function wireView(entry) {
  const wc = entry.view.webContents;
  const isEngine = entry.src.kind === 'engine';
  const isActive = () => entry.id === activeId;

  wc.on('before-input-event', handleShortcut);
  wc.on('did-navigate', (_e, url, code) => {
    entry.httpError = code >= 500 && isHttp(url) ? code : null; // 5xx: the service is down / in maintenance
    if (isActive()) sendTitlebarState();
  });
  wc.on('did-navigate-in-page', () => { if (isActive()) sendTitlebarState(); });
  // A full page load stops playback; the new page reports its own state once loaded.
  wc.on('did-start-navigation', (d) => {
    if (isActive() && d.isMainFrame && !d.isSameDocument && lastNowPlaying) onNowPlaying(null);
  });

  // Links: keep the service and its sign-in popups in the app (sharing its saved
  // login), send everything else to the default browser.
  wc.setWindowOpenHandler(({ url }) => {
    const src = entry.src;
    if (!isEngine && (belongsTo(src, url) || isAuth(src, url))) {
      return {
        action: 'allow',
        overrideBrowserWindowOptions: {
          parent: mainWindow, width: 520, height: 720, autoHideMenuBar: true, icon: appIcon(), backgroundColor: BG,
          webPreferences: { partition: src.partition || undefined, contextIsolation: true, nodeIntegration: false, sandbox: true },
        },
      };
    }
    if (isHttp(url) || url.startsWith('mailto:')) shell.openExternal(url);
    return { action: 'deny' };
  });

  wc.on('dom-ready', () => {
    if (!isEngine) wc.executeJavaScript(HOOK).catch(() => {});
    entry.reduceMotionKey = null;
    applyReduceMotion(entry);
  });

  wc.on('did-fail-load', (_e, code, desc, url, isMainFrame) => {
    if (!isMainFrame || code === -3) return; // -3 = aborted (normal during redirects)
    showOfflinePage(entry, `${desc} (${code})`);
  });
  wc.on('render-process-gone', (_e, d) => {
    if (d.reason !== 'clean-exit') showOfflinePage(entry, `The page stopped (${d.reason})`);
  });

  // Only the visible source may play: if a background one starts, pause it.
  wc.on('media-started-playing', () => {
    if (!isActive() && !isEngine) wc.executeJavaScript(control('pause')).catch(() => {});
  });
  if (isEngine) wc.on('will-navigate', (e, url) => { if (!url.startsWith('jamytm:')) { e.preventDefault(); if (isHttp(url)) shell.openExternal(url); } });
}

function showOfflinePage(entry, err) {
  const others = SOURCES.filter((s) => s.id !== entry.id).map((s) => ({ id: s.id, name: s.name, color: s.color }));
  entry.view.webContents.loadFile(path.join(__dirname, 'offline.html'), {
    query: { err, name: entry.src.name, id: entry.id, others: JSON.stringify(others) },
  }).catch(() => {});
}

function discardView(entry) {
  if (!entry || entry.id === activeId) return;
  clearTimeout(entry.discardTimer);
  views.delete(entry.id);
  if (entry.src.kind === 'engine' && engine) engine.stop();
  try { mainWindow && mainWindow.contentView.removeChildView(entry.view); } catch {}
  if (!entry.view.webContents.isDestroyed()) entry.view.webContents.close();
}

// Switched-away sources stay loaded for a while (instant switch back), then are
// freed so they don't hold memory/CPU. Settings → Sources controls how long.
function scheduleDiscard(entry) {
  clearTimeout(entry.discardTimer);
  const minutes = settings.get('keepWarmMinutes');
  if (minutes < 0) return;
  entry.discardTimer = setTimeout(() => discardView(entry), minutes === 0 ? 400 : minutes * 60000);
}

function switchSource(id) {
  if (!mainWindow) return;
  const src = byId(id);
  const prev = active();
  if (prev && prev.id === src.id) { prev.view.webContents.focus(); return; }

  activeId = src.id;
  settings.set({ lastSource: src.id });
  if (prev) {
    // Use the service's own pause (keeps its UI in sync), then hide it.
    controlEntry(prev, 'pause');
    prev.view.setVisible(false);
    if (prev.src.kind === 'engine') sendYtmEvent('ui-visible', false);
    scheduleDiscard(prev);
  }
  let entry = views.get(src.id);
  if (entry) { clearTimeout(entry.discardTimer); entry.discardTimer = null; }
  else entry = createView(src.id);
  entry.view.setVisible(true);
  layoutViews();
  if (mainWindow.isFocused()) entry.view.webContents.focus();
  if (src.kind === 'engine') sendYtmEvent('ui-visible', true);

  onNowPlaying(null);
  if (src.kind === 'engine') reportEngine(true);
  // An already-loaded source re-reports what it's playing.
  entry.view.webContents.executeJavaScript('window.__octaveReport && window.__octaveReport()').catch(() => {});
  sendTitlebarState();
  buildMenu(); // refresh the ✓ next to the active source
  updateTray();
  sendSources();
}

async function clearSourceLogin(id) {
  const src = byId(id);
  const ses = sessionFor(src);
  await ses.clearStorageData();
  await ses.clearCache();
  const entry = views.get(src.id);
  if (entry && !entry.view.webContents.isDestroyed()) entry.view.webContents.loadURL(src.url).catch(() => {});
}

// ---------------------------------------------------------------- YouTube Music (built-in app, ytm/)

let engine = null;
let engineLoc = null;
const ytm = { np: null, playing: false, position: 0, duration: null, posAt: 0, lastKey: '' };

function engineInstalled() {
  if (!engineLoc) engineLoc = locateEngine(app);
  return fs.existsSync(engineLoc.bin);
}

function getEngine() {
  if (!engineLoc) engineLoc = locateEngine(app);
  if (!engine) {
    engine = new Engine({ bin: engineLoc.bin, cwd: engineLoc.cwd });
    // Jam's own presence, title bar, tray and pause-on-switch follow the engine's player.
    for (const ev of ['now-playing', 'playback-state', 'position', 'duration']) engine.subscribe(ev);
    engine.on('event', onEngineEvent);
    engine.on('event', (name, payload) => sendYtmEvent(name, payload)); // → the UI's own listeners
    engine.on('down', (intentional) => {
      ytm.np = null; ytm.playing = false;
      if (activeId === 'ytm') onNowPlaying(null);
      const e = views.get('ytm');
      if (!intentional && e && !e.view.webContents.isDestroyed()) showOfflinePage(e, 'The YouTube Music engine stopped');
    });
  }
  return engine;
}

const parseClock = (v) => {
  if (typeof v === 'number') return v;
  const parts = String(v || '').split(':').map(Number);
  if (!parts.length || parts.some((n) => !Number.isFinite(n))) return null;
  return parts.reduce((a, n) => a * 60 + n, 0);
};

function onEngineEvent(event, payload) {
  const now = Date.now();
  let force = false;
  if (event === 'now-playing') {
    ytm.np = payload || null;
    ytm.position = 0; ytm.posAt = now;
    ytm.duration = payload ? parseClock(payload.duration) : null;
    force = true;
  } else if (event === 'playback-state') {
    // Keep the extrapolated position when pausing/resuming.
    if (ytm.playing) ytm.position += (now - ytm.posAt) / 1000;
    ytm.posAt = now;
    ytm.playing = payload === 'playing';
    force = true;
  } else if (event === 'duration') {
    const d = Number(payload);
    if (Number.isFinite(d) && d > 0 && Math.abs((ytm.duration || 0) - d) > 1) { ytm.duration = d; force = true; }
  } else if (event === 'position') {
    const p = Number(payload);
    if (!Number.isFinite(p)) return;
    const expected = ytm.position + (ytm.playing ? (now - ytm.posAt) / 1000 : 0);
    ytm.position = p; ytm.posAt = now;
    if (Math.abs(expected - p) > 2.5) force = true; // a seek
  }
  if (force && activeId === 'ytm') reportEngine();
}

// Feed the engine's player into the same pipeline web sources use (Discord, title bar, tray).
function reportEngine(always) {
  const np = ytm.np;
  if (!np) { if (always) onNowPlaying(null, byId('ytm')); return; }
  const position = ytm.position + (ytm.playing ? (Date.now() - ytm.posAt) / 1000 : 0);
  // YouTube thumbnails come sized for the list (e.g. =w60-h60); ask for a crisp one.
  let art = np.thumbnail || null;
  if (art) art = art.replace(/=w\d+-h\d+[^/?#]*$/, '=w544-h544-l90-rj').replace(/\/(default|mqdefault|hqdefault)\.jpg/, '/maxresdefault.jpg');
  onNowPlaying({
    title: np.title || '',
    artist: np.artists || '',
    album: np.album || '',
    artwork: art,
    playbackState: ytm.playing ? 'playing' : 'paused',
    hasMedia: true,
    paused: !ytm.playing,
    currentTime: position,
    duration: ytm.duration,
    docTitle: '',
    // Only real YouTube ids (11 chars) get a watch link; local files and podcasts link to the home page.
    url: /^[A-Za-z0-9_-]{11}$/.test(np.videoId || '') ? `https://music.youtube.com/watch?v=${np.videoId}` : 'https://music.youtube.com/',
  }, byId('ytm'));
}

function sendYtmEvent(event, payload) {
  const e = views.get('ytm');
  if (e && !e.view.webContents.isDestroyed()) e.view.webContents.send('ytm:event', event, payload);
}

const MIME = {
  html: 'text/html; charset=utf-8', js: 'text/javascript; charset=utf-8', mjs: 'text/javascript; charset=utf-8',
  css: 'text/css; charset=utf-8', json: 'application/json', svg: 'image/svg+xml', png: 'image/png', jpg: 'image/jpeg',
  jpeg: 'image/jpeg', webp: 'image/webp', gif: 'image/gif', avif: 'image/avif', ico: 'image/x-icon', jxl: 'image/jxl',
  woff: 'font/woff', woff2: 'font/woff2', ttf: 'font/ttf', otf: 'font/otf', wasm: 'application/wasm', txt: 'text/plain; charset=utf-8',
  mp3: 'audio/mpeg', flac: 'audio/flac', m4a: 'audio/mp4', ogg: 'audio/ogg', opus: 'audio/ogg', wav: 'audio/wav', mp4: 'video/mp4', webm: 'video/webm',
};
async function serveFile(file) {
  try {
    const st = await fs.promises.stat(file);
    if (!st.isFile()) throw new Error('not a file');
    const ext = path.extname(file).slice(1).toLowerCase();
    const body = await fs.promises.readFile(file);
    return new Response(body, { status: 200, headers: { 'content-type': MIME[ext] || 'application/octet-stream', 'cache-control': 'no-cache' } });
  } catch {
    return new Response('Not found', { status: 404 });
  }
}

// Serves the UI (jamytm://app/…, single-page app with index.html fallback) and the local files
// it shows through Tauri's convertFileSrc (jamytm://asset/<path>): covers, fonts, custom icons.
const preparedYtmSessions = new Set();
function prepareYtmSession(ses) {
  if (preparedYtmSessions.has(ses)) return;
  preparedYtmSessions.add(ses);
  ses.protocol.handle('jamytm', async (req) => {
    const u = new URL(req.url);
    if (u.host === 'asset') {
      const file = decodeURIComponent(u.pathname.slice(1));
      if (!path.isAbsolute(file)) return new Response('Not found', { status: 404 });
      return serveFile(file);
    }
    if (u.host !== 'app') return new Response('Not found', { status: 404 });
    const root = path.resolve(engineLoc.ui);
    const rel = decodeURIComponent(u.pathname).replace(/^\/+/, '');
    const file = path.resolve(root, rel || 'index.html');
    if (file !== root && !file.startsWith(root + path.sep)) return new Response('Forbidden', { status: 403 });
    const r = await serveFile(file);
    // Client-side routes (no extension) fall back to the app shell; missing real files stay 404.
    if (r.status === 404 && !/\.[a-z0-9]+$/i.test(rel)) return serveFile(path.join(root, 'index.html'));
    return r;
  });
  const ALLOWED = new Set(['media', 'notifications', 'fullscreen', 'clipboard-sanitized-write']);
  ses.setPermissionRequestHandler((_wc, perm, cb) => cb(ALLOWED.has(perm)));
}

function createEngineView(src) {
  const eng = getEngine();
  const ses = session.fromPartition(src.partition);
  prepareYtmSession(ses);
  const entry = { id: src.id, src, view: null, discardTimer: null, httpError: null, waitingDrm: false, reduceMotionKey: null };
  entry.view = new WebContentsView({
    webPreferences: {
      session: ses,
      preload: path.join(__dirname, 'ytm-preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      backgroundThrottling: true,
      spellcheck: false,
    },
  });
  entry.view.setBackgroundColor(BG);
  entry.view.setVisible(false);
  views.set(src.id, entry);
  wireView(entry);
  mainWindow.contentView.addChildView(entry.view);
  layoutViews();
  entry.starting = true;
  sendTitlebarState();
  eng.start()
    .then(() => { if (!entry.view.webContents.isDestroyed()) entry.view.webContents.loadURL(src.url).catch(() => {}); })
    .catch((err) => { if (!entry.view.webContents.isDestroyed()) showOfflinePage(entry, err.message); })
    .finally(() => { entry.starting = false; sendTitlebarState(); });
  return entry;
}

const ytmSender = (e) => { const v = views.get('ytm'); return !!v && !v.view.webContents.isDestroyed() && e.sender === v.view.webContents; };

ipcMain.handle('ytm:invoke', async (e, cmd, args) => {
  if (!ytmSender(e)) return { ok: false, error: 'forbidden' };
  return getEngine().invoke(cmd, args);
});
ipcMain.on('ytm:subscribe', (e, event) => { if (ytmSender(e)) getEngine().subscribe(event); });

// Calls the UI makes to its own window / OS, answered by Jam (Jam's window is the window).
ipcMain.handle('ytm:local', async (e, cmd, args) => {
  if (!ytmSender(e)) return { ok: false, error: 'forbidden' };
  const ok = (value = null) => ({ ok: true, value });
  const win = mainWindow;
  const [plugin, name] = cmd.slice('plugin:'.length).split('|');
  const opts = (args && args.options) || {};
  const filters = Array.isArray(opts.filters) ? opts.filters.map((f) => ({ name: f.name, extensions: f.extensions })) : undefined;
  switch (plugin) {
    case 'window':
      if (!win) return ok();
      switch (name) {
        case 'minimize': win.minimize(); return ok();
        case 'toggle_maximize': case 'internal_toggle_maximize': win.isMaximized() ? win.unmaximize() : win.maximize(); return ok();
        case 'maximize': win.maximize(); return ok();
        case 'unmaximize': win.unmaximize(); return ok();
        case 'is_maximized': return ok(win.isMaximized());
        case 'set_fullscreen': win.setFullScreen(!!(args && args.value)); return ok();
        case 'is_fullscreen': return ok(win.isFullScreen());
        case 'is_visible': return ok(win.isVisible());
        case 'is_focused': return ok(win.isFocused());
        case 'close': win.close(); return ok();
        default: return ok(); // dragging, resizing, show/focus: Jam's window handles these itself
      }
    case 'webview':
      if (name === 'set_webview_zoom') { e.sender.setZoomFactor(Number(args && args.value) || 1); }
      return ok();
    case 'dialog': {
      if (name === 'open') {
        const props = [opts.directory ? 'openDirectory' : 'openFile'];
        if (opts.multiple) props.push('multiSelections');
        const r = await dialog.showOpenDialog(win, { title: opts.title, defaultPath: opts.defaultPath, filters, properties: props });
        if (r.canceled || !r.filePaths.length) return ok(null);
        return ok(opts.multiple ? r.filePaths : r.filePaths[0]);
      }
      if (name === 'save') {
        const r = await dialog.showSaveDialog(win, { title: opts.title, defaultPath: opts.defaultPath, filters });
        return ok(r.canceled ? null : r.filePath);
      }
      return ok(null);
    }
    case 'clipboard-manager':
      if (name === 'write_text') clipboard.writeText(String((args && (args.text ?? (args.data && args.data.plainText && args.data.plainText.text))) || ''));
      return ok();
    case 'updater':
      return ok(null); // Jam updates arrive as new Jam builds
    case 'process':
      if (name === 'restart') {
        getEngine().stop();
        setTimeout(() => { const v = views.get('ytm'); if (v) { views.delete('ytm'); try { mainWindow.contentView.removeChildView(v.view); } catch {} v.view.webContents.close(); } if (activeId === 'ytm') { activeId = null; switchSource('ytm'); } }, 800);
      }
      return ok();
  }
  return ok();
});

// ---------------------------------------------------------------- main window

function titlebarNotice() {
  const e = active();
  const src = activeSource();
  if (!e) return null;
  if (e.waitingDrm) return { text: `Getting protected playback ready for ${src.name}…`, kind: 'info' };
  if (e.starting) return { text: `Starting ${src.name}…`, kind: 'info' };
  if (e.httpError) return { text: `${src.name} looks down (error ${e.httpError})`, kind: 'error', action: 'switcher', button: 'Switch source' };
  if (src.drm) {
    if (widevineState === 'unsupported') return { text: `This build can't play ${src.name}`, kind: 'error' };
    if (widevineState === 'failed') return { text: `Protected playback couldn't start`, kind: 'error', action: 'reload', button: 'Retry' };
    if (vmpStatus() === 'needs-setup') {
      if (Date.now() - setupLaunchedAt < 60000) return { text: 'Setup window opened — follow the steps there', kind: 'info' };
      return { text: `${src.name} needs a one-time setup to play`, kind: 'warn', action: 'drm-setup', button: 'Set up' };
    }
  }
  return null;
}

function sendTitlebarState() {
  if (!mainWindow || mainWindow.isDestroyed()) return;
  const wc = site();
  const nh = wc && wc.navigationHistory;
  const np = lastNowPlaying;
  const src = activeSource();
  mainWindow.webContents.send('titlebar:state', {
    canGoBack: !!(nh && nh.canGoBack()),
    canGoForward: !!(nh && nh.canGoForward()),
    np: np ? { title: np.title, artist: np.artist, playing: np.playing } : null,
    source: { id: src.id, name: src.name, color: src.color },
    notice: titlebarNotice(),
  });
}

function createMainWindow() {
  const s = settings.all();
  const isMac = process.platform === 'darwin';
  mainWindow = new BrowserWindow({
    ...s.windowBounds,
    minWidth: 520,
    minHeight: 500,
    title: 'Jam',
    icon: appIcon(),
    backgroundColor: BG,
    show: false,
    // Our own dark title bar instead of the system's grey one. The native
    // minimise / maximise / close buttons are drawn over it in matching colours.
    titleBarStyle: isMac ? 'hiddenInset' : 'hidden',
    ...(isMac
      ? { trafficLightPosition: { x: 14, y: 11 } }
      : { titleBarOverlay: { color: BG, symbolColor: '#d4d4dc', height: TITLEBAR_H } }),
    webPreferences: {
      preload: path.join(__dirname, 'titlebar-preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  if (!isMac) mainWindow.setMenu(null); // the menu lives behind the ⋯ button instead

  if (s.maximized) mainWindow.maximize();
  mainWindow.once('ready-to-show', () => { mainWindow.show(); layoutViews(); });

  for (const ev of ['resize', 'maximize', 'unmaximize', 'enter-full-screen', 'leave-full-screen', 'restore']) {
    mainWindow.on(ev, () => { layoutViews(); setImmediate(layoutViews); });
  }
  mainWindow.on('focus', () => { sendTitlebarState(); if (site()) site().focus(); });
  for (const [ev, vis] of [['minimize', false], ['hide', false], ['restore', true], ['show', true]]) {
    mainWindow.on(ev, () => { if (activeSource().kind === 'engine') sendYtmEvent('ui-visible', vis); });
  }
  mainWindow.webContents.on('did-finish-load', sendTitlebarState);
  mainWindow.webContents.on('before-input-event', handleShortcut);
  // We own the window title (shows the current song in the taskbar).
  mainWindow.on('page-title-updated', (e) => e.preventDefault());

  const saveBounds = () => {
    if (!mainWindow || mainWindow.isDestroyed()) return;
    const maximized = mainWindow.isMaximized();
    if (!maximized && !mainWindow.isMinimized() && !mainWindow.isFullScreen()) {
      settings.set({ windowBounds: mainWindow.getBounds(), maximized });
    } else {
      settings.set({ maximized });
    }
  };
  let boundsTimer = null;
  const queueSave = () => { clearTimeout(boundsTimer); boundsTimer = setTimeout(saveBounds, 500); };
  mainWindow.on('resize', queueSave);
  mainWindow.on('move', queueSave);

  mainWindow.on('close', (e) => {
    saveBounds();
    if (settings.get('closeToTray') && !app.isQuitting) {
      e.preventDefault();
      mainWindow.hide();
    }
  });
  mainWindow.on('closed', () => {
    for (const e of views.values()) if (!e.view.webContents.isDestroyed()) e.view.webContents.close();
    views.clear();
    activeId = null;
    mainWindow = null;
  });

  const start = s.openLastSource ? s.lastSource : s.defaultSource;
  switchSource(start);
  mainWindow.loadFile(path.join(__dirname, 'titlebar.html'));
}

// Optional "Reduce animations" (Settings → App): less work for the CPU/GPU.
async function applyReduceMotion(entry) {
  if (!entry) { for (const e of views.values()) applyReduceMotion(e); return; }
  const wc = entry.view.webContents;
  if (wc.isDestroyed() || !(isHttp(wc.getURL()) || wc.getURL().startsWith('jamytm:'))) return;
  const want = settings.get('reduceMotion');
  if (want && !entry.reduceMotionKey) {
    entry.reduceMotionKey = 'pending';
    try { entry.reduceMotionKey = await wc.insertCSS(REDUCE_MOTION_CSS, { cssOrigin: 'user' }); } catch { entry.reduceMotionKey = null; }
  } else if (!want && entry.reduceMotionKey && entry.reduceMotionKey !== 'pending') {
    const k = entry.reduceMotionKey; entry.reduceMotionKey = null;
    wc.removeInsertedCSS(k).catch(() => {});
  }
}

const goHome = () => { const wc = site(); if (wc) wc.loadURL(activeSource().url).catch(() => {}); };
const media = (action) => { const e = active(); if (e) controlEntry(e, action); };

// play / pause / toggle / nexttrack / previoustrack, for a web source or the YouTube Music engine.
function controlEntry(entry, action) {
  if (entry.src.kind === 'engine') {
    if (!engine || !engine.running) return;
    const playing = ytm.playing;
    if (action === 'nexttrack') engine.invoke('next_track');
    else if (action === 'previoustrack') engine.invoke('prev_track');
    else if (action === 'toggle' || (action === 'pause' && playing) || (action === 'play' && !playing)) engine.invoke('toggle_pause');
    return;
  }
  if (!entry.view.webContents.isDestroyed()) entry.view.webContents.executeJavaScript(control(action)).catch(() => {});
}
const togglePlay = () => media('toggle');

// Keyboard shortcuts work no matter which part of the window has focus.
function handleShortcut(event, input) {
  if (input.type !== 'keyDown') return;
  const mod = process.platform === 'darwin' ? input.meta : input.control;
  const key = input.key.toLowerCase();
  const wc = site();
  let handled = true;
  if (mod && !input.shift && !input.alt && /^[1-9]$/.test(key) && SOURCES[+key - 1]) switchSource(SOURCES[+key - 1].id);
  else if (mod && key === ',') openSettings();
  else if ((mod && !input.shift && key === 'r') || key === 'f5') wc && wc.reload();
  else if (mod && input.shift && key === 'r') wc && wc.reloadIgnoringCache();
  else if (mod && input.shift && key === 'h') goHome();
  else if (input.alt && key === 'arrowleft') wc && wc.navigationHistory.canGoBack() && wc.navigationHistory.goBack();
  else if (input.alt && key === 'arrowright') wc && wc.navigationHistory.canGoForward() && wc.navigationHistory.goForward();
  else if (mod && input.shift && key === ' ') togglePlay();
  else if (mod && input.shift && key === 'arrowright') media('nexttrack');
  else if (mod && input.shift && key === 'arrowleft') media('previoustrack');
  else if (key === 'f11') mainWindow && mainWindow.setFullScreen(!mainWindow.isFullScreen());
  else if (mod && input.shift && key === 'i') wc && wc.toggleDevTools();
  else if (mod && (key === '=' || key === '+')) wc && wc.setZoomLevel(wc.getZoomLevel() + 0.5);
  else if (mod && key === '-') wc && wc.setZoomLevel(wc.getZoomLevel() - 0.5);
  else if (mod && key === '0') wc && wc.setZoomLevel(0);
  else if (mod && key === 'q' && process.platform !== 'darwin') quit();
  else handled = false;
  if (handled) event.preventDefault();
}

function sourceMenuItems() {
  const mod = process.platform === 'darwin' ? 'Cmd' : 'Ctrl';
  const items = SOURCES.map((s, i) => ({
    label: s.name,
    type: 'radio',
    checked: s.id === activeId,
    accelerator: i < 9 ? `${mod}+${i + 1}` : undefined,
    registerAccelerator: false, // handled in handleShortcut
    click: () => switchSource(s.id),
  }));
  if (SOURCES.some((s) => s.drm) && vmpStatus() === 'needs-setup') {
    items.push({ type: 'separator' }, { label: 'Set up Spotify & Apple Music…', click: runDrmSetup });
  }
  items.push({ type: 'separator' }, { label: 'Source settings…', click: () => openSettings('sources') });
  return items;
}

ipcMain.on('titlebar:action', (_e, name, arg) => {
  if (!mainWindow) return;
  const wc = site();
  switch (name) {
    case 'back': if (wc && wc.navigationHistory.canGoBack()) wc.navigationHistory.goBack(); break;
    case 'forward': if (wc && wc.navigationHistory.canGoForward()) wc.navigationHistory.goForward(); break;
    case 'reload': if (wc) wc.reload(); break;
    case 'home': goHome(); break;
    case 'settings': openSettings(); return;
    case 'drm-setup': runDrmSetup(); return;
    case 'zoom': mainWindow.isMaximized() ? mainWindow.unmaximize() : mainWindow.maximize(); return;
    case 'switcher': {
      const x = arg && Number.isFinite(arg.x) ? Math.round(arg.x) : 120;
      Menu.buildFromTemplate(sourceMenuItems()).popup({ window: mainWindow, x, y: TITLEBAR_H - 2 });
      return;
    }
    case 'menu': {
      const [w] = mainWindow.getContentSize();
      Menu.getApplicationMenu().popup({ window: mainWindow, x: Math.max(0, w - 260), y: TITLEBAR_H });
      return;
    }
  }
  if (wc) wc.focus();
});

function openSettings(section) {
  if (settingsWindow) {
    settingsWindow.focus();
    if (section) settingsWindow.webContents.send('settings:goto', section);
    return;
  }
  settingsWindow = new BrowserWindow({
    width: 1060,
    height: 780,
    minWidth: 860,
    minHeight: 560,
    resizable: true,
    minimizable: false,
    maximizable: false,
    title: 'Jam Settings',
    titleBarStyle: process.platform === 'darwin' ? 'hiddenInset' : 'hidden',
    ...(process.platform === 'darwin'
      ? { trafficLightPosition: { x: 16, y: 18 } }
      : { titleBarOverlay: { color: '#131318', symbolColor: '#d4d4dc', height: 40 } }),
    icon: appIcon(),
    backgroundColor: '#131318',
    parent: mainWindow || undefined,
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'settings-preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
    },
  });
  settingsWindow.setMenu(null);
  settingsWindow.once('ready-to-show', () => settingsWindow.show());
  settingsWindow.on('closed', () => { settingsWindow = null; });
  settingsWindow.loadFile(path.join(__dirname, 'settings.html'), {
    query: { platform: process.platform, section: typeof section === 'string' ? section : '' },
  });
}

// ---------------------------------------------------------------- menu + tray

function buildMenu() {
  const nav = (fn) => () => { if (site()) fn(site()); };
  const template = [
    ...(process.platform === 'darwin' ? [{
      label: 'Jam',
      submenu: [
        { role: 'about' },
        { type: 'separator' },
        { label: 'Settings…', accelerator: 'Cmd+,', click: () => openSettings() },
        { type: 'separator' },
        { role: 'hide' }, { role: 'hideOthers' }, { role: 'unhide' },
        { type: 'separator' },
        { role: 'quit' },
      ],
    }] : [{
      label: '&File',
      submenu: [
        { label: 'Settings…', accelerator: 'Ctrl+,', click: () => openSettings() },
        { type: 'separator' },
        { label: 'Quit', accelerator: 'Ctrl+Q', click: quit },
      ],
    }]),
    ...(process.platform === 'darwin' ? [{ role: 'editMenu' }] : []),
    { label: '&Source', submenu: sourceMenuItems() },
    {
      label: '&Navigate',
      submenu: [
        { label: 'Back', accelerator: process.platform === 'darwin' ? 'Cmd+[' : 'Alt+Left', click: nav((wc) => wc.navigationHistory.canGoBack() && wc.navigationHistory.goBack()) },
        { label: 'Forward', accelerator: process.platform === 'darwin' ? 'Cmd+]' : 'Alt+Right', click: nav((wc) => wc.navigationHistory.canGoForward() && wc.navigationHistory.goForward()) },
        { label: 'Home', accelerator: 'CmdOrCtrl+Shift+H', click: goHome },
        { type: 'separator' },
        { label: 'Reload', accelerator: 'CmdOrCtrl+R', click: nav((wc) => wc.reload()) },
        { label: 'Hard Reload', accelerator: 'CmdOrCtrl+Shift+R', click: nav((wc) => wc.reloadIgnoringCache()) },
        { type: 'separator' },
        { label: 'Copy Page Link', click: nav((wc) => electron.clipboard.writeText(wc.getURL())) },
        { label: 'Open in Browser', click: nav((wc) => isHttp(wc.getURL()) && shell.openExternal(wc.getURL())) },
      ],
    },
    {
      label: '&View',
      submenu: [
        { role: 'resetZoom' }, { role: 'zoomIn' }, { role: 'zoomOut' },
        { type: 'separator' },
        { role: 'togglefullscreen' },
        { label: 'Developer Tools', accelerator: 'CmdOrCtrl+Shift+I', click: nav((wc) => wc.toggleDevTools()) },
      ],
    },
    {
      label: '&Playback',
      submenu: [
        { label: 'Play / Pause', accelerator: 'CmdOrCtrl+Shift+Space', click: togglePlay },
        { label: 'Next', accelerator: 'CmdOrCtrl+Shift+Right', click: () => media('nexttrack') },
        { label: 'Previous', accelerator: 'CmdOrCtrl+Shift+Left', click: () => media('previoustrack') },
      ],
    },
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(template));
}

function updateTray() {
  const want = settings.get('closeToTray');
  if (!want) { if (tray) { tray.destroy(); tray = null; } return; }
  if (!tray) {
    // Pixel art: scale the 64px sprite by whole steps so it stays crisp.
    tray = new Tray(nativeImage.createFromPath(SMALL_ICON_PATH).resize({ width: process.platform === 'darwin' ? 16 : 32, quality: 'good' }));
    tray.on('click', showMain);
  }
  const np = lastNowPlaying;
  const label = np && np.title ? `${np.title}${np.artist ? ' — ' + np.artist : ''}` : 'Jam';
  tray.setToolTip(label.slice(0, 127));
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: label.slice(0, 60), enabled: false },
    { type: 'separator' },
    { label: 'Previous', click: () => media('previoustrack') },
    { label: np && np.playing ? 'Pause' : 'Play', click: togglePlay },
    { label: 'Next', click: () => media('nexttrack') },
    { type: 'separator' },
    { label: `Source: ${activeSource().name}`, submenu: sourceMenuItems() },
    { label: 'Show Jam', click: showMain },
    { label: 'Settings…', click: () => openSettings() },
    { type: 'separator' },
    { label: 'Quit', click: quit },
  ]));
}

// ---------------------------------------------------------------- now playing → Discord

const PLACEHOLDER = /^unknown( (title|artist|album|track))?$/i; // e.g. Monochrome's "Unknown Album"
const clean = (v) => { const s = String(v || '').trim(); return PLACEHOLDER.test(s) ? '' : s; };

function cleanDocTitle(t, src) {
  if (!t) return '';
  const names = [src.name, 'Octave Streaming', 'Monochrome Music', 'Apple Music', 'Spotify', 'Jam']
    .map((n) => n.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|');
  const s = t.replace(new RegExp(`\\s*[-|•·–—]\\s*(${names})\\s*$`, 'i'), '').trim();
  return new RegExp(`^(${names})$`, 'i').test(s) ? '' : s;
}

function toNowPlaying(st, src) {
  if (!st) return null;
  const playing = st.hasMedia ? !st.paused : st.playbackState === 'playing';
  const title = clean(st.title) || (st.hasMedia ? cleanDocTitle(st.docTitle, src) : '');
  if (!title && !st.hasMedia) return null;
  return {
    title: title || 'Unknown track',
    artist: clean(st.artist),
    album: clean(st.album),
    artwork: st.artwork && /^https:\/\//i.test(st.artwork) && st.artwork.length <= 256 ? st.artwork : null,
    playing,
    currentTime: st.currentTime,
    duration: st.duration,
    url: belongsTo(src, st.url) ? st.url : src.url,
    sourceId: src.id,
    sourceName: src.name,
  };
}

// Identity of an activity for "did anything change?". The exact start time drifts
// slightly between reads, so it's compared separately (seek detection) — but the
// *kind* of timer (progress bar vs elapsed) and the song length must count as a change,
// or a bar that arrives once the length is known would never be sent.
function activityKey(a) {
  if (!a) return 'null';
  const ts = a.timestamps;
  const timer = !ts ? 'none' : ts.end ? `bar:${Math.round((ts.end - ts.start) / 1000)}` : 'elapsed';
  return JSON.stringify({ ...a, timestamps: timer });
}

// Only push to Discord when something meaningful changed, and never faster than Discord allows.
const push = { desired: null, lastKey: undefined, lastStart: null, lastAt: 0, timer: null, waited: false };

function queueActivity(activity) {
  push.desired = activity;
  const key = activityKey(activity);
  const start = activity && activity.timestamps ? activity.timestamps.start : null;
  const seeked = start && push.lastStart && Math.abs(start - push.lastStart) > 3000;
  if (key === push.lastKey && !seeked) return;
  if (push.timer) return; // a send is already scheduled; it will pick up `desired`
  const wait = Math.max(0, push.lastAt + MIN_RPC_GAP_MS - Date.now());
  push.timer = setTimeout(flushActivity, wait);
}

async function flushActivity() {
  push.timer = null;
  if (!rpc.ready) return;
  const a = push.desired;
  // New song in progress-bar mode but its length isn't known yet: give the site a
  // moment to load it rather than flashing "elapsed" first. (Live streams with no
  // length fall through to elapsed after the wait.)
  if (a && a.timestamps && !a.timestamps.end && settings.get('progress') === 'bar' && !push.waited) {
    push.waited = true;
    push.timer = setTimeout(flushActivity, 1500);
    return;
  }
  push.waited = false;
  push.lastAt = Date.now();
  push.lastKey = activityKey(a);
  push.lastStart = a && a.timestamps ? a.timestamps.start : null;
  await rpc.setActivity(a);
}

// Called by the page whenever playback actually changes (no polling).
function onNowPlaying(st, src = activeSource()) {
  const np = toNowPlaying(st, src);
  if (np) np.readAt = Date.now();
  const prev = lastNowPlaying;
  lastNowPlaying = np;

  const changed = !prev !== !np || (np && (np.title !== prev.title || np.artist !== prev.artist || np.playing !== prev.playing));
  if (changed) {
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.setTitle(np && np.playing ? `${np.title}${np.artist ? ' — ' + np.artist : ''} · Jam` : 'Jam');
    }
    sendTitlebarState();
    if (tray) updateTray();
  }
  if (rpc.ready) queueActivity(currentActivity());
  sendPreview();
}

ipcMain.on('np:update', (e, st) => {
  const entry = [...views.values()].find((v) => !v.view.webContents.isDestroyed() && v.view.webContents === e.sender);
  if (!entry || entry.id !== activeId) return; // only the visible source reports
  onNowPlaying(st && typeof st === 'object' ? st : null, entry.src);
});

function currentActivity() {
  return presence.buildActivity(lastNowPlaying, settings.all());
}

// Live preview for the settings window: exactly what Discord would receive.
function previewPayload() {
  const s = settings.all();
  const sample = !lastNowPlaying;
  const np = lastNowPlaying || { ...presence.SAMPLE, sourceName: activeSource().name, url: activeSource().url };
  const activity = presence.buildActivity(np, s);
  return {
    activity,
    sample,
    playing: !!np.playing,
    enabled: !!s.rpcEnabled,
    appName: presence.appName(s),
    memberText: presence.memberListText(activity, s),
  };
}

function sendPreview() {
  if (settingsWindow && !settingsWindow.isDestroyed()) settingsWindow.webContents.send('preview', previewPayload());
}

function sourcesPayload() {
  return {
    active: activeId,
    sources: SOURCES.map((s) => ({
      id: s.id, name: s.name, color: s.color, drm: s.drm, url: s.url, loaded: views.has(s.id),
      custom: !!s.custom, customUrl: !!s.customUrl, defaultUrl: s.defaultUrl || null, extraDomains: s.extraDomains || [],
      kind: s.kind || 'web', installed: s.kind === 'engine' ? engineInstalled() : true,
    })),
    palette: sources.PALETTE,
    max: sources.MAX_CUSTOM,
    drm: { widevine: widevineState, vmp: vmpStatus(), platform: process.platform },
  };
}
function sendSources() {
  if (settingsWindow && !settingsWindow.isDestroyed()) settingsWindow.webContents.send('sources:update', sourcesPayload());
}

// ---------------------------------------------------------------- RPC lifecycle

function broadcastStatus() {
  if (settingsWindow && !settingsWindow.isDestroyed()) settingsWindow.webContents.send('rpc:status', rpcState);
}

function applyRpcSettings() {
  const s = settings.all();
  push.lastKey = undefined;
  if (s.rpcEnabled && s.discordClientId) {
    if (rpc.clientId !== s.discordClientId || rpc.stopped) rpc.start(s.discordClientId);
    else if (rpc.ready) queueActivity(currentActivity());
  } else {
    rpc.stop();
    rpcState = { state: s.rpcEnabled ? 'no-client-id' : 'disabled', user: null };
    broadcastStatus();
  }
}

rpc.on('status', (state) => {
  if (state === 'connecting' && rpcState.state === 'error') return; // keep the error visible while retrying
  rpcState = { state, user: state === 'connected' ? rpc.user : null };
  broadcastStatus();
});
rpc.on('ready', () => { push.lastKey = undefined; queueActivity(currentActivity()); });
rpc.on('error', (e) => {
  console.warn('[discord-rpc]', e.message);
  if (!rpc.ready) { rpcState = { state: 'error', user: null }; broadcastStatus(); }
});

// ---------------------------------------------------------------- IPC

ipcMain.handle('settings:get', () => settings.all());
ipcMain.handle('settings:set', (_e, patch) => {
  const clean = settings.sanitize(patch);
  const idChanged = 'discordClientId' in clean && clean.discordClientId !== settings.get('discordClientId');
  const out = settings.set(clean);
  if (idChanged && rpcState.state === 'error') rpcState.state = 'connecting';
  applyRpcSettings();
  if ('closeToTray' in clean) updateTray();
  if ('reduceMotion' in clean) applyReduceMotion();
  if ('keepWarmMinutes' in clean) for (const e of views.values()) if (e.id !== activeId) scheduleDiscard(e);
  sendPreview();
  return out;
});
ipcMain.handle('settings:reset', () => {
  const out = settings.resetPresence();
  applyRpcSettings();
  sendPreview();
  return out;
});
ipcMain.handle('preview:get', () => previewPayload());
ipcMain.handle('rpc:status', () => rpcState);
ipcMain.handle('sources:get', () => sourcesPayload());
ipcMain.handle('sources:switch', (_e, id) => { switchSource(String(id)); return sourcesPayload(); });
ipcMain.handle('sources:clear', async (_e, id) => { await clearSourceLogin(String(id)); return sourcesPayload(); });
ipcMain.handle('drm:setup', () => { runDrmSetup(); });

// ---- your own sources (Settings → Sources): add / edit / remove, change links. No rebuild needed.
function applySourceChanges() {
  const before = new Map(SOURCES.map((s) => [s.id, s.url]));
  sources.refresh(settings.all());
  const s = settings.all();
  if (!sources.exists(s.defaultSource)) settings.set({ defaultSource: 'octave' });
  if (!sources.exists(s.lastSource)) settings.set({ lastSource: settings.get('defaultSource') });
  for (const entry of [...views.values()]) {
    if (!sources.exists(entry.id)) {
      // Removed while loaded: leave it, and free its saved data.
      if (entry.id === activeId) switchSource(settings.get('defaultSource'));
      discardView(entry);
      continue;
    }
    entry.src = byId(entry.id);
    if (before.get(entry.id) !== entry.src.url && !entry.view.webContents.isDestroyed()) {
      entry.httpError = null;
      entry.view.webContents.loadURL(entry.src.url).catch(() => {}); // link changed → go there now
    }
  }
  buildMenu();
  updateTray();
  sendTitlebarState();
  sendSources();
}

ipcMain.handle('sources:save', (_e, input) => {
  input = input || {};
  const builtin = sources.BUILTIN.find((b) => b.id === input.id);
  if (builtin) {
    if (builtin.drm) return { error: `${builtin.name}'s link can't be changed.` };
    const url = sources.normalizeUrl(input.url);
    if (!url) return { error: "That link doesn't look like a web address." };
    settings.set({ sourceUrls: sources.sanitizeUrlOverrides({ ...settings.get('sourceUrls'), [builtin.id]: url }) });
  } else {
    const list = settings.get('customSources') || [];
    const r = sources.cleanCustom(input, list);
    if (r.error) return { error: r.error };
    const i = list.findIndex((c) => c.id === r.source.id);
    const next = i >= 0 ? list.map((c, j) => (j === i ? r.source : c)) : [...list, r.source];
    settings.set({ customSources: sources.sanitizeCustomList(next) });
    input.id = r.source.id;
  }
  applySourceChanges();
  return { ok: true, id: input.id, payload: sourcesPayload() };
});
ipcMain.handle('sources:resetUrl', (_e, id) => {
  const o = { ...settings.get('sourceUrls') };
  delete o[String(id)];
  settings.set({ sourceUrls: o });
  applySourceChanges();
  return sourcesPayload();
});
ipcMain.handle('sources:remove', async (_e, id) => {
  id = String(id);
  const src = SOURCES.find((s) => s.id === id && s.custom);
  if (!src) return sourcesPayload();
  settings.set({ customSources: (settings.get('customSources') || []).filter((c) => c.id !== id) });
  applySourceChanges();
  try { const ses = session.fromPartition(src.partition); await ses.clearStorageData(); await ses.clearCache(); } catch {}
  return sourcesPayload();
});
ipcMain.handle('open-external', (_e, url) => { if (isHttp(url)) shell.openExternal(url); });
// From the offline page (it runs inside the source's view).
ipcMain.handle('app:retry', () => { const e = active(); if (e) e.view.webContents.loadURL(e.src.url).catch(() => {}); });
ipcMain.handle('app:switch', (_e, id) => switchSource(String(id)));

// ---------------------------------------------------------------- app lifecycle

function init() {
  nativeTheme.themeSource = 'dark';
  sources.refresh(settings.all());
  const s = settings.all();
  const first = byId(s.openLastSource ? s.lastSource : s.defaultSource);
  // Start the network handshake immediately, in parallel with window creation.
  try { sessionFor(first).preconnect({ url: first.url, numSockets: 2 }); } catch {}
  // Spotify / Apple Music need the Widevine module; fetch it early if one of them opens first.
  if (first.drm) ensureWidevine();

  buildMenu();
  createMainWindow();
  updateTray();
  applyRpcSettings();

  if (!settings.get('discordClientId') && !settings.get('firstRunDone')) {
    settings.set({ firstRunDone: true });
    const wc = site();
    if (wc) wc.once('did-finish-load', () => setTimeout(() => openSettings(), 1200));
  }
}

app.on('activate', showMain);
app.on('before-quit', () => { app.isQuitting = true; rpc.stop(); if (engine) engine.stop(); });
app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});
