// Jam for Android, for web apps that work offline once installed (Octave): inside Jam the site
// is treated as an installed app, so it turns on its offline mode, and its own service worker
// (which keeps the app and your downloads available without internet) is always registered.
(function () {
  if (window.__jamInstalledApp) return;
  window.__jamInstalledApp = true;
  try {
    var mm = window.matchMedia.bind(window);
    window.matchMedia = function (q) {
      if (/display-mode\s*:\s*(standalone|fullscreen|minimal-ui|window-controls-overlay)/i.test(String(q))) {
        var standalone = /standalone/i.test(String(q));
        return mm(standalone ? '(min-width: 0px)' : '(max-width: -1px)');
      }
      return mm(q);
    };
    Object.defineProperty(Navigator.prototype, 'standalone', { configurable: true, get: function () { return true; } });
  } catch (e) {}
  // The app's service worker, as an installed copy of the site would have it. Only when online
  // (registering needs the network) and only if the site hasn't registered one itself.
  window.addEventListener('load', function () {
    try {
      if (!navigator.serviceWorker || !navigator.onLine || location.protocol !== 'https:') return;
      navigator.serviceWorker.getRegistration('/').then(function (r) {
        if (!r) navigator.serviceWorker.register('/sw.js', { scope: '/' }).catch(function () {});
      });
    } catch (e) {}
  });
})();
