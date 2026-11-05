'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('titlebar', {
  platform: process.platform,
  action: (name, arg) => ipcRenderer.send('titlebar:action', name, arg),
  onState: (cb) => ipcRenderer.on('titlebar:state', (_e, s) => cb(s)),
});
