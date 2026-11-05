# Jam

A desktop music terminal for Windows with Discord Rich Presence. One window, many sources, instant switching:

| Source | Shortcut | What it is |
|---|---|---|
| Octave | Ctrl+1 | music.octavestreaming.com (default) |
| Monochrome | Ctrl+2 | monochrome.st (lossless / Hi-Res) |
| Spotify | Ctrl+3 | open.spotify.com (needs the one-time castLabs setup) |
| Apple Music | Ctrl+4 | music.apple.com (needs the one-time castLabs setup) |
| YouTube Music | Ctrl+5 | Built-in app, based on [Limusic](https://github.com/SimoHypers/limusic) |
| Your own | Ctrl+6… | Any non-DRM music site, added from Settings → Sources |

## Repository layout

- `desktop/`: the Jam app (Electron on castLabs' Widevine build). See `desktop/README.md` for features.
- `ytm/`: the YouTube Music engine, a fork of **Limusic** (Rust + Tauri + Svelte) rebuilt to run inside
  Jam. Upstream version in `ytm/UPSTREAM`. Jam-specific changes:
  - `ytm/src-tauri/src/jamhost.rs` plus small hooks: when Jam starts the engine it runs headless, and its UI is shown
    in Jam's window through `ytm/ui/static/jam-relay.html` and `desktop/src/{engine,tauri-shim,ytm-preload}.js`.
  - `ytm/jam-rebrand.py`: renames the app to Jam where you see it. Re-run it after pulling a newer Limusic.
- `.github/workflows/windows.yml`: builds both on Windows and publishes `Jam-Setup-*.exe` and `Jam-Portable-*.zip`
  as a release.

## Updating the YouTube Music engine from upstream Limusic

1. Replace `ytm/` with the new Limusic source, keeping `ytm/src-tauri/src/jamhost.rs`, `ytm/ui/static/jam-relay.html`,
   `ytm/jam-rebrand.py` and the `jamhost` hooks (search the old tree for `jamhost::`).
2. `python3 ytm/jam-rebrand.py`
3. Push. GitHub builds the installer.

## License and credits

Jam is licensed under the **GNU GPL v3.0 or later** (see `LICENSE`), because it includes Limusic.

- **Limusic** by SimoHypers and contributors (GPL-3.0-or-later): the whole YouTube Music app in `ytm/`.
  https://github.com/SimoHypers/limusic. It started as a desktop rebuild of [Metrolist](https://github.com/mostafaalagamy/Metrolist).
- **castLabs Electron for Content Security**: Widevine support for Spotify and Apple Music.
- libmpv (mpv project), Tauri, Electron, and the other open-source projects listed in each part's lockfile.
