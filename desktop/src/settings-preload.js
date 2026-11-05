'use strict';
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('octave', {
  getSettings: () => ipcRenderer.invoke('settings:get'),
  saveSettings: (patch) => ipcRenderer.invoke('settings:set', patch),
  resetPresence: () => ipcRenderer.invoke('settings:reset'),
  getStatus: () => ipcRenderer.invoke('rpc:status'),
  onStatus: (cb) => ipcRenderer.on('rpc:status', (_e, s) => cb(s)),
  getPreview: () => ipcRenderer.invoke('preview:get'),
  onPreview: (cb) => ipcRenderer.on('preview', (_e, p) => cb(p)),
  getSources: () => ipcRenderer.invoke('sources:get'),
  onSources: (cb) => ipcRenderer.on('sources:update', (_e, p) => cb(p)),
  switchSource: (id) => ipcRenderer.invoke('sources:switch', id),
  clearSource: (id) => ipcRenderer.invoke('sources:clear', id),
  saveSource: (src) => ipcRenderer.invoke('sources:save', src),
  removeSource: (id) => ipcRenderer.invoke('sources:remove', id),
  resetSourceUrl: (id) => ipcRenderer.invoke('sources:resetUrl', id),
  setupDrm: () => ipcRenderer.invoke('drm:setup'),
  onGoto: (cb) => ipcRenderer.on('settings:goto', (_e, s) => cb(s)),
  openExternal: (url) => ipcRenderer.invoke('open-external', url),
  close: () => window.close(),
});
