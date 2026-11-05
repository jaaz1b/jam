'use strict';
// Preload for the YouTube Music UI (the Limusic interface, rebuilt into Jam). Sandboxed: the
// Tauri stand-in (tauri-shim.js) arrives as an argument and is installed in the page's main world
// before the UI's own scripts run. The bridge below is all the page can reach.
const { contextBridge, ipcRenderer, webFrame } = require('electron');

let onEvent = null;
ipcRenderer.on('ytm:event', (_e, event, payload) => { if (onEvent) onEvent(event, payload); });

contextBridge.exposeInMainWorld('__jamYtm', {
  invoke: (cmd, args) => ipcRenderer.invoke('ytm:invoke', String(cmd), args || {}),
  local: (cmd, args) => ipcRenderer.invoke('ytm:local', String(cmd), args || {}),
  subscribe: (event) => ipcRenderer.send('ytm:subscribe', String(event)),
  onEvent: (cb) => { onEvent = cb; },
  sep: process.platform === 'win32' ? '\\' : '/',
  delimiter: process.platform === 'win32' ? ';' : ':',
});

const arg = process.argv.find((a) => a.startsWith('--jam-tauri-shim='));
if (arg) {
  try { webFrame.executeJavaScript(Buffer.from(arg.slice('--jam-tauri-shim='.length), 'base64').toString('utf8')); } catch {}
}
