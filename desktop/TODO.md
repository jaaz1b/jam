# Desktop: for the next desktop release

- **Saved state: save the pages only, not the downloads.** Android 2.4 fixed a doubling bug: Jam
  2.2 copied Octave's whole WebView profile, downloaded songs included, so 10 GB of downloads
  became 20 GB. Desktop 3.1's `savedstate.js` has the same problem (it copies the default
  session's IndexedDB/File System, where Octave keeps downloads, into `persist:jam-octave-saved`).
  Port Android's approach: no session copy; the saved state is the live session with the network
  blocked (network emulation offline + webRequest cancel, toggled per view/session), and
  `installed-app.js` (copy the Android file, it has `__jamSaveState` / `__jamDeleteSavedState` /
  the saved-mode restore) snapshots only Octave's Cache Storage (pages, page data, scripts,
  images; never audio/video) into the `jam-saved-state` IndexedDB in Octave's own origin, and
  writes it back over the caches when the saved state opens. Expose `JamNative.savedStateDone` in
  `preload.js`; Save no longer relaunches Jam. On first start, delete `Partitions/jam-octave-saved`.

- **Saved state: covers.** Android 2.4.1: downloaded songs showed no art in the saved state
  (Octave's worker keeps only its last ~400 images, none opaque/cross-origin that it can't read).
  Android's `CoverStore.kt` keeps covers itself: `installed-app.js` reports image URLs the site
  loads (PerformanceObserver + `document.images`) and, on save, every image URL in the saved
  pages and in Octave's IndexedDB records (`JamNative.coverUrls`); Jam downloads each once and
  answers image requests from its folder when the network is blocked (saved state) or down. On
  desktop: `session.protocol.handle('https', …)` or `webRequest` redirect in the saved session.

Already in 3.1 (shipped on Android first): YouTube Music downloads and offline library, Octave
offline, the Octave saved-state switch (title-bar save button).
