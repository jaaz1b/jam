// Keeps music going when Jam is in the background or the screen is off: some sites pause
// themselves when the page reports it's hidden.
(function () {
  if (window.__jamKeepAlive) return;
  window.__jamKeepAlive = true;
  try {
    var def = function (name, value) { Object.defineProperty(Document.prototype, name, { configurable: true, get: function () { return value; } }); };
    def('hidden', false); def('visibilityState', 'visible'); def('webkitHidden', false); def('webkitVisibilityState', 'visible');
    var stop = function (e) { e.stopImmediatePropagation(); };
    ['visibilitychange', 'webkitvisibilitychange'].forEach(function (t) { window.addEventListener(t, stop, true); });
  } catch (e) {}
})();
