# Desktop: for the next big Windows update

- **Octave offline (fixed on Android in 2.0.x).** Octave works without internet once "installed":
  its service worker (`/sw.js`, caches `octave-v8-*`) serves the app shell, library pages and
  downloads. In Jam, Octave's offline mode needs the site treated as installed
  (`display-mode: standalone`, `navigator.standalone`), its service worker registered, and the
  page's user agent left as the browser's own (service-worker requests carry that one, and
  Cloudflare ties its clearance to it). Android: `assets/installed-app.js` + `Source.offlineApp`
  in `ytm/src-tauri/gen/android`. Desktop: do the same in `desktop/src/page-scripts.js` /
  `sources.js` for Octave's view, check Electron keeps the service worker for its partition, and
  make the offline page in `desktop/src/offline.html` not hide Octave when the service worker can
  answer.
