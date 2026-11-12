# Jam for Android

Jam for Android 3 is **[Orpheon](https://github.com/kota-zo/Orpheon)** (formerly
[Myspoti](https://github.com/kota-zo/myspoti)) by kota-zo, rebranded as Jam, with Jam's
**YouTube Music mode** and **Discord Rich Presence** added. Both modes ship in one APK
(`app.jam.android`). It installs over Jam for Android 2.x and keeps its data.

- **Spotify mode** (the default) is Orpheon's app, with its extensions, downloads, AutoMix,
  Android TV and Android Auto. Only the brand (name, icon, colors) and the additions below are
  Jam's. Extensions are checked against the Orpheon version it is built on (`ORPHEON_VERSION`).
- **YouTube Music mode** (Settings ▸ Jam ▸ YouTube Music mode) is Jam for Android 2.x's YouTube
  Music app: the Rust engine and Svelte UI in `ytm/`, run by Tauri, playing through libmpv. It has
  no other sources. To switch back, open its Settings ▸ Jam ▸ Switch to Spotify mode. The app
  restarts in the mode you pick, and only that mode's code starts (`JamMode`).
- **Discord Rich Presence** works in both modes, with the same sign-in, lines and buttons (Settings ▸
  Jam ▸ Discord Rich Presence).

## Layout

| Path | What |
| --- | --- |
| `android/base` | Orpheon's Gradle project (kota-zo/Orpheon@70f323c, `base/`), rebranded. The Jam app is `:app`. |
| `android/base/ytm` | `:ytm`, YouTube Music mode as a library. Its sources are Jam for Android 2.x's, in `ytm/src-tauri/gen/android/app`. |
| `android/base/app/src/main/java/app/jam` | What Jam adds to Spotify mode: the settings rows, the presence feed, start-up timings. |
| `android/libmpv` | libmpv + FFmpeg for YouTube Music mode. |
| `android/ci/smoke.sh` | The emulator test: cold starts, both modes, YouTube Music playback and downloads. |
| `android/tools` | Orpheon's device tools. |

## Building

YouTube Music mode needs the Tauri build first. It compiles the engine and writes Wry's Kotlin and the
list of Tauri's Android sources:

```sh
cd ytm/src-tauri && tauri android build --apk --target aarch64
cd ../../android/base && ./gradlew :app:assembleRelease :app:collectApks
```

CI (`.github/workflows/android.yml`) does this for arm64 and x86_64 and publishes
`android-v<version>-build.<n>` with `Jam-Android-<version>.apk`. The app's update check reads those
releases.

## Credits and license

Jam is GPL-3.0, as Orpheon is. Orpheon's copyright and license notices stay in its files. Its
third-party notices are in `THIRD_PARTY_NOTICES.md` and its history in `ORPHEON-CHANGELOG.md`. The
app's About sheet and Jam settings credit Orpheon, and its support buttons go to Orpheon's author.
