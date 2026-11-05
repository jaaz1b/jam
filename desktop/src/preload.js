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

// Only the bundled offline page gets this bridge, never a website.
if (location.protocol === 'file:') {
  contextBridge.exposeInMainWorld('octave', {
    retry: () => ipcRenderer.invoke('app:retry'),
    switchTo: (id) => ipcRenderer.invoke('app:switch', id),
  });
}
