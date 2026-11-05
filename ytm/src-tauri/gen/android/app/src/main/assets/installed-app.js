// Jam for Android, for web apps that work offline once installed (Octave): inside Jam the site
// is treated as an installed app, so it turns on its offline mode, and its own service worker
// (which keeps the app and your downloads available without internet) is kept registered.
// What happens to that service worker is reported to Jam (JamNative.offlineStatus/offlineLog),
// which shows it on its "isn't loading" page.
(function () {
  // Only the site's own pages: Jam's offline page also runs in this WebView, and has no service
  // worker of its own to report on.
  if (window.__jamInstalledApp || !/^https?:$/.test(location.protocol)) return;
  window.__jamInstalledApp = true;

  var J = window.JamNative;
  function log(ev, detail) {
    try {
      if (J && J.offlineLog) J.offlineLog(JSON.stringify({ t: Date.now(), ev: ev, d: detail == null ? '' : String(detail).slice(0, 300), p: location.pathname }));
    } catch (e) {}
  }

  // Installed-app mode.
  try {
    var mm = window.matchMedia.bind(window);
    var asked = {};
    window.matchMedia = function (q) {
      var s = String(q);
      if (/display-mode\s*:\s*(standalone|fullscreen|minimal-ui|window-controls-overlay)/i.test(s)) {
        if (!asked[s]) { asked[s] = 1; log('site-asked', s); }
        return mm(/standalone/i.test(s) ? '(min-width: 0px)' : '(max-width: -1px)');
      }
      return mm(q);
    };
    Object.defineProperty(Navigator.prototype, 'standalone', { configurable: true, get: function () { return true; } });
  } catch (e) {}

  // What the site itself does with service workers.
  try {
    if (window.ServiceWorkerContainer) {
      var P = ServiceWorkerContainer.prototype, reg0 = P.register;
      P.register = function (url, opts) {
        log('site-register', url + (opts && opts.scope ? ' scope ' + opts.scope : ''));
        return reg0.apply(this, arguments).then(function (r) { log('site-register-ok', r && r.scope); return r; },
          function (e) { log('site-register-failed', e && (e.message || e.name) || e); throw e; });
      };
    }
    if (window.ServiceWorkerRegistration) {
      var R = ServiceWorkerRegistration.prototype, unreg0 = R.unregister;
      R.unregister = function () { log('site-unregister', this.scope); return unreg0.apply(this, arguments); };
    }
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
      if (!J || !J.offlineStatus) return;
      var out = { at: Date.now(), why: why, page: location.pathname, online: navigator.onLine, sw: !!navigator.serviceWorker, error: lastError };
      var done = function () { try { J.offlineStatus(JSON.stringify(out)); } catch (e) {} };
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

  // The app's service worker, as an installed copy of the site would have it, if the site hasn't
  // registered one itself. Registering needs the network.
  function ensure() {
    try {
      log('page', (navigator.onLine ? 'online' : 'offline') + ' · controlled=' + !!(navigator.serviceWorker && navigator.serviceWorker.controller) + ' · ' + navigator.userAgent.slice(0, 120));
      if (!navigator.serviceWorker) { lastError = 'this WebView has no service workers'; report('no-sw'); return; }
      if (!navigator.onLine) { report('offline'); return; }
      // Whatever covers this page, at any scope: a site whose worker lives elsewhere keeps it.
      navigator.serviceWorker.getRegistration().then(function (r) {
        if (r) { log('jam-found', JSON.stringify(regInfo(r))); return r; }
        log('jam-register', '/sw.js');
        return navigator.serviceWorker.register('/sw.js', { scope: '/' });
      }).then(function (r) {
        try { if (r && r.waiting) r.waiting.postMessage({ type: 'SKIP_WAITING' }); } catch (e) {}
        report('registered');
      }, function (e) {
        lastError = 'register: ' + (e && (e.message || e.name) || e);
        log('jam-register-failed', lastError);
        report('register-failed');
      });
    } catch (e) {
      lastError = 'ensure: ' + e;
      report('error');
    }
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ensure, { once: true });
  else ensure();
  setTimeout(function () { report('after-20s'); }, 20000);
  setTimeout(function () { report('after-60s'); }, 60000);
  try {
    navigator.serviceWorker && navigator.serviceWorker.addEventListener('controllerchange', function () { log('controllerchange'); report('controllerchange'); });
  } catch (e) {}
})();
