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

Already in 3.1 (shipped on Android first): YouTube Music downloads and offline library, Octave
offline, the Octave saved-state switch (title-bar save button).
