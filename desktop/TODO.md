# Desktop: for the next desktop release

Nothing pending. Jam 3.1 brought over everything that shipped on Android first (2.1–2.3):

- **YouTube Music downloads** (shared engine `ytm/src-tauri/src/downloads.rs`, shared UI): track
  menus, album/playlist Download buttons, the selection bar, a Downloads page (sidebar entry),
  Settings ▸ Playback ▸ Downloads (quality, storage, remove all), progress on the taskbar button
  (`main.js`, `download-progress`).
- **Offline library for YouTube Music**: offline, the Library and every playlist/album open from
  their last copy (`page_snapshots`), undownloaded songs are greyed, Play/Shuffle take only the
  downloaded ones.
- **Octave offline**: `installed-app.js` (the same file as Android's) runs in Octave's page through
  a generated preload (`main.js` `offlinePreload`), a failed page load waits to see whether
  Octave's worker answered (`SERVED_PROBE`), and the "can't reach" page shows the offline copy's
  status.
- **Octave saved state**: `savedstate.js` copies the live session to `persist:jam-octave-saved`
  at the next launch (Save relaunches Jam), and the saved session runs with network emulation
  offline plus every request blocked. The save button in the title bar holds the switch, Save and
  Delete. Test: `xvfb-run -a node desktop/test/saved-state.mjs` (with puppeteer-core).
