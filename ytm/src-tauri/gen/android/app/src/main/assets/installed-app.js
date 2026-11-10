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
  // A saved state (Jam's SavedState.kt): Jam blocks the network for it, and the site has to know
  // it is offline so it uses its downloads and saved pages rather than trying to stream.
  var saved = false;
  try { saved = !!(J && J.savedMode && J.savedMode()); } catch (e) {}
  if (saved) {
    try {
      Object.defineProperty(Navigator.prototype, 'onLine', { configurable: true, get: function () { return false; } });
      window.addEventListener('online', function (e) { e.stopImmediatePropagation(); }, true);
    } catch (e) {}
    window.__jamSavedState = true;
  }
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
      log('page', (saved ? 'saved state' : navigator.onLine ? 'online' : 'offline') + ' · controlled=' + !!(navigator.serviceWorker && navigator.serviceWorker.controller) + ' · ' + navigator.userAgent.slice(0, 120));
      if (!navigator.serviceWorker) { lastError = 'this WebView has no service workers'; report('no-sw'); return; }
      if (!navigator.onLine) { report('offline'); return; }
      // Whatever covers this page, at any scope: a site whose worker lives elsewhere keeps it.
      // A site registering its own worker on load gets a few seconds to do it first.
      var look = function () { return navigator.serviceWorker.getRegistration(); };
      look().then(function (r) {
        return r || new Promise(function (ok) { setTimeout(ok, 4000); }).then(look);
      }).then(function (r) {
        if (r) { log('jam-found', JSON.stringify(regInfo(r))); return r; }
        log('jam-register', '/sw.js');
        return navigator.serviceWorker.register('/sw.js', { scope: '/' });
      }).then(function (r) {
        try { if (r && r.waiting) r.waiting.postMessage({ type: 'SKIP_WAITING' }); } catch (e) {}
        // Navigation preload sends a second request for each page beside the worker's. Offline,
        // WebView reports that request failing as the page failing, even when the worker answers
        // from its offline copy, so Jam switches it off (the worker fetches the page itself).
        try {
          if (r && r.navigationPreload) {
            r.navigationPreload.getState().then(function (st) {
              if (st && st.enabled) return r.navigationPreload.disable().then(function () { log('preload-off'); });
            }).catch(function () {});
          }
        } catch (e) {}
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

  // --- Saved state: the site's saved pages, frozen (Jam's save button) -------------------------
  //
  // Saving copies the site's own offline caches (its pages, page data, scripts, images) into an
  // IndexedDB database of Jam's, "jam-saved-state", in the site's own storage. Nothing else is
  // copied: the downloaded songs stay where the site keeps them, and the saved state plays them
  // from there. In saved mode (no network), the saved pages are written back over the site's caches
  // once, so it opens exactly as saved even if the live site has changed since.
  // --- Covers (Jam's CoverStore.kt) ----------------------------------------------------------
  //
  // The site's worker keeps only its last few hundred images, so Jam keeps the covers itself: it is
  // told which ones the site shows (and, when saving, every one its saved pages and downloads refer
  // to), downloads each once, and answers for them whenever there's no network.
  var IMG = /\.(?:jpe?g|png|webp|avif|gif)(?:[?#]|$)|dzcdn\.net\/images\/|\/api\/img\?|\/_next\/image\?|mzstatic\.com\/image\/|resources\.tidal\.com\/images\//i;
  var URLS = /https?:\/\/[^\s"'<>()\\`]+/g;
  var coverSeen = {}, coverQueue = [];
  function wantCover(u) {
    if (!u || typeof u !== 'string' || u.length > 1500 || coverSeen[u]) return;
    if (!/^https?:/.test(u) || /^blob:|^data:/.test(u) || !IMG.test(u)) return;
    coverSeen[u] = 1;
    coverQueue.push(u);
  }
  function sendCovers() {
    var n = 0;
    while (coverQueue.length && J && J.coverUrls) {
      try { n += J.coverUrls(JSON.stringify(coverQueue.splice(0, 2000))) || 0; } catch (e) { break; }
    }
    return n;
  }
  function coversInText(t) {
    if (!t || t.length > 20 * 1024 * 1024) return;
    t = t.replace(/\\u002F/gi, '/').replace(/\\\//g, '/');
    var m = t.match(URLS);
    if (m) m.forEach(function (u) { wantCover(u.replace(/[.,;]+$/, '').replace(/&amp;/g, '&')); });
  }
  function coversOnPage() {
    try {
      var imgs = document.images;
      for (var i = 0; i < imgs.length; i++) wantCover(imgs[i].currentSrc || imgs[i].src);
      var srcs = document.querySelectorAll('source[srcset], img[srcset]');
      for (var k = 0; k < srcs.length; k++) coversInText(srcs[k].getAttribute('srcset'));
    } catch (e) {}
  }
  // The site's own databases (its downloads list, its library): the covers its records refer to.
  async function coversInDatabases(deadline) {
    if (!indexedDB.databases) return;
    var dbs = await indexedDB.databases();
    for (var d = 0; d < dbs.length && Date.now() < deadline; d++) {
      var name = dbs[d].name;
      if (!name || name === SNAP) continue;
      var db = await new Promise(function (ok) {
        var r = indexedDB.open(name);
        r.onsuccess = function () { ok(r.result); };
        r.onerror = r.onblocked = function () { ok(null); };
        r.onupgradeneeded = function () { try { r.transaction.abort(); } catch (e) {} };
      });
      if (!db) continue;
      try {
        var stores = Array.prototype.slice.call(db.objectStoreNames);
        for (var s = 0; s < stores.length && Date.now() < deadline; s++) {
          await new Promise(function (ok) {
            var seen = 0;
            var r = db.transaction(stores[s]).objectStore(stores[s]).openCursor();
            r.onsuccess = function () {
              var c = r.result;
              if (!c || ++seen > 50000 || Date.now() > deadline) return ok();
              walk(c.value, 0);
              c.continue();
            };
            r.onerror = function () { ok(); };
          });
        }
      } catch (e) {} finally { db.close(); }
    }
    function walk(v, depth) {
      if (v == null || depth > 6) return;
      if (typeof v === 'string') { if (v.length < 1500) { if (IMG.test(v)) wantCover(v); } else if (v.length < 200000) coversInText(v); return; }
      if (typeof v !== 'object' || v instanceof Blob || v instanceof ArrayBuffer || ArrayBuffer.isView(v)) return;
      if (Array.isArray(v)) { for (var i = 0; i < v.length && i < 5000; i++) walk(v[i], depth + 1); return; }
      for (var k in v) { try { walk(v[k], depth + 1); } catch (e) {} }
    }
  }
  if (!saved) {
    try { performance.setResourceTimingBufferSize(5000); } catch (e) {}
    try {
      new PerformanceObserver(function (list) {
        list.getEntries().forEach(function (e) { wantCover(e.name); });
      }).observe({ type: 'resource', buffered: true });
    } catch (e) {}
    setInterval(function () { coversOnPage(); sendCovers(); }, 8000);
  }

  var SNAP = 'jam-saved-state';
  var MARK = 'jam-saved-applied';
  var MAX_ENTRY = 8 * 1024 * 1024;
  function snapDb() {
    return new Promise(function (ok, no) {
      var r = indexedDB.open(SNAP, 1);
      r.onupgradeneeded = function () {
        var db = r.result;
        if (!db.objectStoreNames.contains('entries')) db.createObjectStore('entries', { autoIncrement: true });
        if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta');
      };
      r.onsuccess = function () { ok(r.result); };
      r.onerror = function () { no(r.error); };
    });
  }
  function done(tx) {
    return new Promise(function (ok, no) { tx.oncomplete = function () { ok(); }; tx.onerror = tx.onabort = function () { no(tx.error); }; });
  }
  function reply(o) { try { J && J.savedStateDone && J.savedStateDone(JSON.stringify(o)); } catch (e) {} }
  function suffix(name) { var m = /-([a-z]+)$/.exec(name); return m ? m[1] : name; }

  window.__jamSaveState = function () {
    if (saved) { reply({ ok: false, error: 'open the live site to save' }); return; }
    (async function () {
      var entries = [], bytes = 0, skipped = 0;
      var names = await caches.keys();
      for (var i = 0; i < names.length; i++) {
        var c = await caches.open(names[i]);
        var reqs = await c.keys();
        for (var j = 0; j < reqs.length; j++) {
          var req = reqs[j];
          var res = await c.match(req);
          // Songs and other media are never copied (they stay where the site keeps them), nor
          // anything opaque or oversized.
          var type = (res && res.headers.get('content-type')) || '';
          if (!res || res.type === 'opaque' || res.status === 0 || /^(audio|video)\//i.test(type)) { skipped++; continue; }
          var body = await res.blob();
          if (body.size > MAX_ENTRY) { skipped++; continue; }
          var rh = [], qh = [];
          res.headers.forEach(function (v, k) { rh.push([k, v]); });
          req.headers.forEach(function (v, k) { qh.push([k, v]); });
          entries.push({ cache: names[i], url: req.url, reqHeaders: qh, status: res.status, statusText: res.statusText, headers: rh, body: body });
          bytes += body.size;
          wantCover(req.url);
          if (/html|x-component|json|text\/plain/i.test(type)) { try { coversInText(await body.text()); } catch (e) {} }
        }
      }
      if (!entries.length) throw new Error('the site has no saved pages yet. Open it with internet first');
      coversOnPage();
      try { await coversInDatabases(Date.now() + 20000); } catch (e) { log('covers-db-failed', e && (e.message || e.name) || e); }
      var covers = sendCovers();
      var gen = Date.now();
      var db = await snapDb();
      var tx = db.transaction(['entries', 'meta'], 'readwrite');
      tx.objectStore('entries').clear();
      entries.forEach(function (e) { tx.objectStore('entries').add(e); });
      tx.objectStore('meta').put({ gen: gen, count: entries.length, bytes: bytes, skipped: skipped, page: location.pathname }, 'current');
      await done(tx);
      db.close();
      try { localStorage.removeItem(MARK); } catch (e) {}
      log('saved-state', entries.length + ' entries, ' + bytes + ' bytes, ' + skipped + ' skipped, ' + covers + ' new covers of ' + Object.keys(coverSeen).length);
      reply({ ok: true, gen: gen, count: entries.length, bytes: bytes, covers: covers });
    })().catch(function (e) {
      log('saved-state-failed', e && (e.message || e.name) || e);
      reply({ ok: false, error: String(e && (e.message || e)) });
    });
  };

  window.__jamDeleteSavedState = function () {
    try { localStorage.removeItem(MARK); } catch (e) {}
    try { indexedDB.deleteDatabase(SNAP); } catch (e) {}
    log('saved-state-deleted');
  };

  // Saved mode: put the saved pages back into the site's caches, once per save, then reload so the
  // site's worker answers from them.
  async function restoreSaved() {
    var db = await snapDb();
    var meta = await new Promise(function (ok) {
      var r = db.transaction('meta').objectStore('meta').get('current');
      r.onsuccess = function () { ok(r.result); };
      r.onerror = function () { ok(null); };
    });
    if (!meta) { db.close(); return; }
    var applied = null;
    try { applied = localStorage.getItem(MARK); } catch (e) {}
    if (applied === String(meta.gen)) { db.close(); return; }
    var entries = await new Promise(function (ok, no) {
      var r = db.transaction('entries').objectStore('entries').getAll();
      r.onsuccess = function () { ok(r.result || []); };
      r.onerror = function () { no(r.error); };
    });
    db.close();
    // The site may have moved to new cache names since (its worker renames them on an update):
    // write each entry into today's cache of the same kind.
    var live = await caches.keys();
    var bySuffix = {};
    live.forEach(function (n) { bySuffix[suffix(n)] = n; });
    var n = 0;
    for (var i = 0; i < entries.length; i++) {
      var e = entries[i];
      try {
        var c = await caches.open(bySuffix[suffix(e.cache)] || e.cache);
        await c.put(new Request(e.url, { headers: e.reqHeaders }), new Response(e.body, { status: e.status, statusText: e.statusText, headers: e.headers }));
        n++;
      } catch (err) {}
    }
    try { localStorage.setItem(MARK, String(meta.gen)); } catch (e) {}
    log('saved-state-restored', n + ' of ' + entries.length);
    var once = false;
    try { once = sessionStorage.getItem('jam-saved-reloaded') === String(meta.gen); sessionStorage.setItem('jam-saved-reloaded', String(meta.gen)); } catch (e) {}
    if (n && !once) location.reload();
  }
  if (saved) {
    restoreSaved().catch(function (e) { log('saved-state-restore-failed', e && (e.message || e.name) || e); });
  } else if (navigator.onLine) {
    // The live site may refresh its caches now: the next saved-mode visit writes the save back.
    try { localStorage.removeItem(MARK); } catch (e) {}
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', ensure, { once: true });
  else ensure();
  setTimeout(function () { report('after-20s'); }, 20000);
  setTimeout(function () { report('after-60s'); }, 60000);
  try {
    navigator.serviceWorker && navigator.serviceWorker.addEventListener('controllerchange', function () { log('controllerchange'); report('controllerchange'); });
  } catch (e) {}
})();
