'use strict';
// Runs before each music site's own scripts, so the media hook is in place
// before anything starts playing. (Sandboxed preload: no local requires, so
// the hook is passed in from the main process via an argument.)
const { webFrame, contextBridge, ipcRenderer } = require('electron');

const arg = process.argv.find((a) => a.startsWith('--octave-hook='));
if (arg && /^https?:$/.test(location.protocol)) {
  try {
    const code = Buffer.from(arg.slice('--octave-hook='.length), 'base64').toString('utf8');
    webFrame.executeJavaScript(code);
  } catch {}
  // The page reports now-playing changes; forward them to the app.
  window.addEventListener('__octave-now-playing', (e) => {
    if (typeof e.detail !== 'string' || e.detail.length > 20000) return;
    try { ipcRenderer.send('np:update', JSON.parse(e.detail)); } catch {}
  });
}

// Sites that work offline once installed (Octave): the bridge installed-app.js reports to. The
// script itself is appended to this file for those sites (main.js `offlinePreload`), because a
// sandboxed preload can neither load it nor evaluate it from a string.
const offlineApp = process.argv.includes('--jam-offline-app=1') && /^https?:$/.test(location.protocol);
if (offlineApp) {
  const saved = process.argv.includes('--jam-saved=1');
  contextBridge.exposeInMainWorld('JamNative', {
    offlineStatus: (json) => { if (typeof json === 'string' && json.length < 8000) ipcRenderer.send('offline:status', json); },
    offlineLog: (json) => { if (typeof json === 'string' && json.length < 1000) ipcRenderer.send('offline:log', json); },
    savedMode: () => saved,
    // The saved state the page made (installed-app.js `__jamSaveState`), and the covers it shows or
    // refers to (main.js, covers.js): answered with how many of those are new.
    savedStateDone: (json) => { if (typeof json === 'string' && json.length < 8000) ipcRenderer.send('saved:done', json); },
    coverUrls: (json) => (typeof json === 'string' && json.length < 4000000 ? ipcRenderer.sendSync('covers:want', json) : 0),
  });
}

// Only the bundled offline page gets this bridge, never a website.
if (location.protocol === 'file:') {
  contextBridge.exposeInMainWorld('octave', {
    retry: () => ipcRenderer.invoke('app:retry'),
    switchTo: (id) => ipcRenderer.invoke('app:switch', id),
    // A site's offline copy and its saved state (savedstate.js).
    offlineInfo: (id) => ipcRenderer.invoke('app:offline-info', id),
    useSaved: (id, on) => ipcRenderer.invoke('app:use-saved', id, on),
  });
}
