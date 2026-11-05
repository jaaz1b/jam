// Jam for Android, for web apps that work offline once installed (Octave): inside Jam the site
// is treated as an installed app, so it turns on its offline mode, and its own service worker
// (which keeps the app and your downloads available without internet) is always registered.
// What happened to that service worker is reported to Jam (JamNative.offlineStatus), which shows
// it on its "isn't loading" page.
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

  var lastError = '';
  function regInfo(r) {
    if (!r) return null;
    var w = r.active || r.waiting || r.installing;
    return {
      scope: r.scope,
      active: r.active ? r.active.state : null,
      waiting: r.waiting ? r.waiting.state : null,
      installing: r.installing ? r.installing.state : null,
      script: w ? w.scriptURL : null,
    };
  }

  function report(why) {
    try {
      if (!window.JamNative || !window.JamNative.offlineStatus) return;
      var out = { at: Date.now(), why: why, online: navigator.onLine, sw: !!navigator.serviceWorker, error: lastError };
      var done = function () { try { window.JamNative.offlineStatus(JSON.stringify(out)); } catch (e) {} };
      if (!navigator.serviceWorker) return done();
      out.controlled = !!navigator.serviceWorker.controller;
      Promise.resolve()
        .then(function () { return navigator.serviceWorker.getRegistrations(); })
        .then(function (rs) { out.registrations = (rs || []).map(regInfo); })
        .catch(function (e) { out.error = (out.error ? out.error + ' | ' : '') + 'getRegistrations: ' + e; })
        .then(function () {
          if (!window.caches) { out.caches = 'unavailable'; return; }
          return caches.keys().then(function (names) {
            return Promise.all(names.map(function (n) {
              return caches.open(n).then(function (c) { return c.keys(); }).then(function (k) { return [n, k.length]; });
            }));
          }).then(function (pairs) {
            out.caches = {};
            pairs.forEach(function (p) { out.caches[p[0]] = p[1]; });
          });
        })
        .catch(function (e) { out.error = (out.error ? out.error + ' | ' : '') + 'caches: ' + e; })
        .then(done, done);
    } catch (e) {}
  }

  // The app's service worker, as an installed copy of the site would have it. Only when online
  // (registering needs the network) and only if the site hasn't registered one itself.
  function ensure() {
    try {
      if (!navigator.serviceWorker) { lastError = 'this WebView has no service workers'; report('no-sw'); return; }
      if (!navigator.onLine || location.protocol !== 'https:') { report('offline'); return; }
      navigator.serviceWorker.getRegistration('/').then(function (r) {
        if (r) return r;
        return navigator.serviceWorker.register('/sw.js', { scope: '/' });
      }).then(function (r) {
        // Pick up a freshly installed worker at once, as the site's update flow would.
        try { if (r && r.waiting) r.waiting.postMessage({ type: 'SKIP_WAITING' }); } catch (e) {}
        report('registered');
      }, function (e) {
        lastError = 'register: ' + (e && (e.message || e.name) || e);
        report('register-failed');
      });
    } catch (e) {
      lastError = 'ensure: ' + e;
      report('error');
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ensure, { once: true });
  else ensure();
  // The worker saves the app in the background; report again once that has had time to finish.
  setTimeout(function () { report('after-20s'); }, 20000);
  setTimeout(function () { report('after-60s'); }, 60000);
  try { navigator.serviceWorker && navigator.serviceWorker.addEventListener('controllerchange', function () { report('controllerchange'); }); } catch (e) {}
})();
