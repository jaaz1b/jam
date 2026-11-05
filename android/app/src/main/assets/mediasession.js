// Android's WebView has no Media Session API, so music sites can't say what's playing.
// This stand-in gives them navigator.mediaSession + MediaMetadata; Jam reads them for the
// notification, lock screen and Discord, and uses the sites' own play/pause/next handlers.
(function () {
  if ('mediaSession' in navigator || window.__jamMediaSessionShim) return;
  window.__jamMediaSessionShim = true;
  var abs = function (u) { try { return new URL(String(u), location.href).href; } catch (e) { return ''; } };
  var art = function (list) {
    if (!list || typeof list.length !== 'number') return Object.freeze([]);
    return Object.freeze(Array.prototype.map.call(list, function (a) {
      return Object.freeze({ src: abs(a && a.src), sizes: String((a && a.sizes) || ''), type: String((a && a.type) || '') });
    }));
  };
  function MediaMetadata(init) {
    if (!(this instanceof MediaMetadata)) throw new TypeError("Failed to construct 'MediaMetadata': Please use the 'new' operator.");
    init = init || {};
    this._title = String(init.title || '');
    this._artist = String(init.artist || '');
    this._album = String(init.album || '');
    this._artwork = art(init.artwork);
  }
  ['title', 'artist', 'album'].forEach(function (p) {
    Object.defineProperty(MediaMetadata.prototype, p, {
      configurable: true, enumerable: true,
      get: function () { return this['_' + p]; },
      set: function (v) { this['_' + p] = String(v == null ? '' : v); },
    });
  });
  Object.defineProperty(MediaMetadata.prototype, 'artwork', {
    configurable: true, enumerable: true,
    get: function () { return this._artwork; },
    set: function (v) { this._artwork = art(v); },
  });

  function MediaSession() { throw new TypeError('Illegal constructor'); }
  Object.defineProperty(MediaSession.prototype, 'metadata', {
    configurable: true, enumerable: true,
    get: function () { return this._metadata || null; },
    set: function (v) {
      if (v != null && !(v instanceof MediaMetadata)) throw new TypeError("Failed to set the 'metadata' property on 'MediaSession'");
      this._metadata = v || null;
    },
  });
  Object.defineProperty(MediaSession.prototype, 'playbackState', {
    configurable: true, enumerable: true,
    get: function () { return this._state || 'none'; },
    set: function (v) { if (v === 'none' || v === 'paused' || v === 'playing') this._state = v; },
  });
  MediaSession.prototype.setActionHandler = function (action, handler) {
    (this._handlers || (this._handlers = {}))[action] = handler || null;
  };
  MediaSession.prototype.setPositionState = function () {};
  MediaSession.prototype.setMicrophoneActive = function () {};
  MediaSession.prototype.setCameraActive = function () {};

  var session = Object.create(MediaSession.prototype);
  try {
    Object.defineProperty(window, 'MediaMetadata', { configurable: true, writable: true, value: MediaMetadata });
    Object.defineProperty(window, 'MediaSession', { configurable: true, writable: true, value: MediaSession });
    Object.defineProperty(Navigator.prototype, 'mediaSession', { configurable: true, enumerable: true, get: function () { return session; } });
  } catch (e) {}
})();
