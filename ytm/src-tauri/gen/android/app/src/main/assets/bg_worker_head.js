// Jam for Android: runs before bg_bundle.js inside the BotGuard Web Worker. Arranges the worker's
// globals like the deno runtime rustypipe-botguard uses on desktop (deno_web + deno_url +
// deno_console, no fetch, no crypto, no DOM), since BotGuard's verdict depends on what it can see.
(function () {
  // deno: `location` exists but is unset; window/document/origin come from JSDOM.
  for (const k of ['location', 'origin', 'window', 'document', 'top', 'parent']) {
    try { Object.defineProperty(globalThis, k, { value: undefined, writable: true, configurable: true, enumerable: true }); } catch (e) {}
  }
  // Globals deno has and a worker lacks.
  globalThis.global = globalThis;
  globalThis.process = {};
  if (!globalThis.setImmediate) {
    globalThis.setImmediate = (f, ...a) => setTimeout(f, 0, ...a);
    globalThis.clearImmediate = (t) => clearTimeout(t);
  }
  if (!URL.parse) URL.parse = (u, b) => { try { return new URL(u, b); } catch (e) { return null; } };
  if (!URL.canParse) URL.canParse = (u, b) => { try { new URL(u, b); return true; } catch (e) { return false; } };
  // deno's navigator is an empty branded object.
  class Navigator {}
  try { Object.defineProperty(globalThis, 'navigator', { value: new Navigator(), writable: true, configurable: true }); } catch (e) {}
  // Worker APIs deno doesn't have (the extensions rustypipe-botguard loads have no fetch, crypto,
  // storage or workers).
  for (const k of ['fetch', 'Request', 'Response', 'Headers', 'XMLHttpRequest', 'WebSocket', 'EventSource',
    'indexedDB', 'IDBFactory', 'caches', 'CacheStorage', 'crypto', 'Crypto', 'CryptoKey', 'SubtleCrypto',
    'importScripts', 'Worker', 'SharedWorker', 'BroadcastChannel', 'Notification', 'FileReaderSync',
    'OffscreenCanvas', 'createImageBitmap', 'WorkerGlobalScope', 'DedicatedWorkerGlobalScope',
    'WorkerNavigator', 'WorkerLocation', 'webkitRequestFileSystem', 'webkitResolveLocalFileSystemURL']) {
    try { Object.defineProperty(globalThis, k, { value: undefined, writable: true, configurable: true, enumerable: false }); } catch (e) {}
  }
})();
