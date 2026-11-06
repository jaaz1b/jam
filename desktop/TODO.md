# Desktop: for the next desktop release

Both of these shipped on Android first (2.1). Bring them to Windows in the next desktop version.

- **YouTube Music downloads (offline listening).** The download manager lives in the shared
  engine (`ytm/src-tauri/src/downloads.rs`), modeled on Meld: songs, albums, playlists and
  selections; a separate "Download quality" setting (High / Normal / Low); 3 downloads at a time;
  resumable ranged fetches that re-resolve on an expired URL (403/410/416); artwork saved next to
  the audio; playback prefers the downloaded file so it works with no internet; a Downloads view
  in the Library with storage used and "Remove all". On Android the UI is gated by `phone` (the
  `downloadsEnabled` check in `ui/src/lib/downloads.svelte.ts`) and progress shows in a foreground
  notification. Desktop: lift the gate, put download buttons in the desktop track menu, album
  and playlist headers, add the Downloads entry to the sidebar, show progress in the taskbar
  (`taskbar.rs`) instead of a notification, and choose a downloads folder in Settings (default
  `%APPDATA%/…/downloads`).
- **Octave offline.** Octave works without internet once "installed": its service worker
  (`/sw.js`, caches `octave-v8-*`) serves the app shell, library pages and downloads. In Jam,
  Octave's offline mode needs the site treated as installed (`display-mode: standalone`,
  `navigator.standalone`), its service worker registered, and the page's user agent left as the
  browser's own (service-worker requests carry that one, and Cloudflare ties its clearance to it).
  Android: `assets/installed-app.js` + `Source.offlineApp` in `ytm/src-tauri/gen/android`, with
  an event log shown on the offline page. Desktop: do the same in `desktop/src/page-scripts.js` /
  `sources.js` for Octave's view, check Electron keeps the service worker for its partition, and
  make the offline page in `desktop/src/offline.html` not hide Octave when the service worker can
  answer. The cause on Android (2.1.1): Octave's worker turns on navigation preload, and offline
  the failed preload request is reported as the page's own error, so Jam's error handler swapped
  in its offline page and cancelled the worker's answer. Electron's `did-fail-load` may do the
  same: check whether the page is service-worker controlled before replacing it, and turn
  navigation preload off for Octave (`registration.navigationPreload.disable()`).
