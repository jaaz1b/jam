# Jam

Jam is a desktop music terminal with Discord Rich Presence. It plays from four services, and you can switch
between them instantly:

| Source | Ctrl+ | Notes |
|---|---|---|
| Octave (default) | 1 | |
| Monochrome | 2 | Lossless / Hi-Res by default |
| Spotify | 3 | Needs the one-time setup below |
| Apple Music | 4 | Needs the one-time setup below |
| YouTube Music | 5 | Built-in app (based on Limusic): lyrics, scrobbling, video, speed/pitch/reverb, Listen Together… |

Jam always loads each live site, so you get every update automatically. Each service keeps its own saved login.

## Install (Windows)

- **Jam-Setup-2.1.0.exe**: installer. It installs over earlier versions and keeps your settings and logins.
- **Jam-Portable-2.1.0.zip**: no install. Unzip it to a folder you'll keep (e.g. `Documents\Jam`) and run
  `Jam.exe`. Keep it in that folder: the Spotify/Apple Music setup signs that copy. If you move it, run
  the setup again.

The app isn't code-signed, so Windows SmartScreen may say "Windows protected your PC".
Click **More info → Run anyway**.

## Switching sources

- Click the source pill in the title bar, or press **Ctrl+1–4**.
- If a service is down, Jam says so in the title bar ("looks down"), or shows a page with buttons to switch
  to another service.
- **Settings → Sources**: pick the service Jam opens with (default Octave), or start where you left off.
  You can also choose how long a switched-away service stays ready for an instant switch back, and sign
  out of any one service. The service you switch away from is paused automatically.

## YouTube Music (built-in app)

YouTube Music isn't a website in Jam. It's a full app, **Limusic** rebuilt into Jam, with its own engine
(ad-free audio through libmpv) and its interface shown right in Jam's window. Everything Limusic does works
here: search and library, sign-in (several accounts), synced word-by-word lyrics, music videos, mini player,
speed/pitch/reverb, local music, Last.fm and ListenBrainz scrobbling, Listen Together, themes, and 18+ languages.

- Sign in from its account menu (top right inside YouTube Music). The login is saved by its engine.
- Jam's Discord status, title bar, tray, media keys and pause-on-switch all follow it like any other source.
- Its own settings (gear icon inside YouTube Music) cover playback, lyrics, scrobbling, themes and hotkeys.
  Discord is configured in Jam's Settings for every source at once.
- Last.fm needs an API key at build time (repo secrets `LASTFM_API_KEY` / `LASTFM_API_SECRET`).
  ListenBrainz works without one.

## Adding your own sources

**Settings → Sources → ＋ Add a source**: give it a name and a link (e.g. `monochrome.tf`), and pick a color.
It works right away, with no reinstall. It gets its own saved login, a spot in the switcher (Ctrl+5, 6, …),
Discord status, and failover like the built-ins.

- Works with any music website that plays **without DRM**: Monochrome-style sites, internet radio, Bandcamp, etc.
  DRM services (like Spotify) need the castLabs-signed setup and can't be added this way.
- **Change link** on Octave or Monochrome points them at a mirror or a new domain. **Use original link** undoes it.
- If a site sends you to a login or player page on another domain and that opens in your browser, add the domain under
  **Advanced: other domains this site uses**.
- **Edit → Remove** deletes a source and its saved data.

## Spotify & Apple Music: one-time setup

Spotify and Apple Music use copy protection (Widevine DRM). Jam is built on castLabs' Electron, which
includes Widevine. On Windows, these services also require the app to carry a castLabs signature,
which castLabs provides for free with your own account.

1. Switch to Spotify or Apple Music and click **Set up** in the title bar (or Settings → Sources → Set up).
2. A setup window opens. It installs Python if needed, installs castLabs' signing tool, and creates or
   signs in to your free castLabs EVS account (you'll get an emailed code). Then it signs Jam and restarts it.
3. Sign in to Spotify / Apple Music inside Jam. Your login is saved.

Run the setup again after installing a new Jam version. It's quick the second time.

## Audio quality

Jam passes each service's audio through untouched. Quality is set by the service:
- **Monochrome**: Hi-Res / lossless (its default: Settings → Streaming quality → Auto/Hi-Res).
- **Spotify**: in Spotify's settings, set audio quality to the highest available for your plan.
- **Apple Music on the web**: 256 kbps AAC. Apple doesn't offer lossless in its web player.
- **Windows**: for Hi-Res, set your output device to 24-bit / 96 or 192 kHz under Sound settings →
  your device → Format. That way Windows doesn't downsample it.

## Turn on Discord Rich Presence (one-time, about 1 minute)

Discord needs an "Application" to show a status. Its name is what your friends see.

1. Go to https://discord.com/developers/applications and click **New Application**. Name it **Jam**.
2. On **General Information**, copy the **Application ID**.
3. In the app, open **Settings** (`Ctrl + ,`, or press `Alt` → File → Settings). The window also opens on first launch.
   Paste the ID and click **Save**. The status dot should turn green ("Connected as …").
4. Optional: in the Developer Portal go to **Rich Presence → Art Assets**, upload `discord-asset-jam.png`,
   and name the asset **`jam`**. It's shown when a song has no cover art.

The Discord **desktop app** must be running. The browser version of Discord doesn't support Rich Presence.

### Customising your status

Settings (`Ctrl + ,`) has a live preview of your Discord profile card and member-list entry. Changes apply immediately.

- **Rich Presence / Show when paused / Hide details**: turn the status on or off, keep it up while paused,
  or share only that music is playing (no song info leaves your PC).
- **Status text**: what shows next to your name in the member list: song title, artist, or app name.
- **App name**: fills `{app}` everywhere (default "Jam"). The "Listening to …" header is your Discord application's name,
  which you can rename in the Developer Portal.
- **Paused label**: text added in front of line 2 while paused (default "Paused").
- **Lines 1–3**: templates using `{title}` `{artist}` `{album}` `{source}` `{app}`, e.g. `{artist} — {album}`.
  Separators left dangling by an empty field are cleaned up. Line 3 is also the artwork hover text.
- **Progress**: full progress bar, elapsed time only, or nothing.
- **Artwork**: song cover on or off, plus a fallback image (an Art Asset key or an https URL).
- **Buttons**: up to two, each with its own label and link. Button 1 links to the page you're on by default.
  Discord hides buttons on your own profile, so only other people see them.
- **What gets sent to Discord** shows the exact data being sent. **Reset to defaults** restores the look
  but keeps your Application ID.

## Shortcuts

| Action | Shortcut |
|---|---|
| Settings | `Ctrl + ,` |
| Switch source | `Ctrl + 1` … `Ctrl + 4` |
| Play / Pause | `Ctrl + Shift + Space` (keyboard media keys work too) |
| Next / Previous | `Ctrl + Shift + →` / `Ctrl + Shift + ←` |
| Back / Forward | `Alt + ←` / `Alt + →` |
| Home | `Ctrl + Shift + H` |
| Reload | `Ctrl + R` / `F5` |
| Menu | `⋯` button in the title bar |
| Full screen | `F11` |

Other features: you stay logged in between launches, the window remembers its size and position,
the taskbar title shows the current song, an optional tray mode keeps music playing when the window
is closed, and an offline screen reconnects automatically.

## Performance

- When the window is minimized, hidden in the tray, or covered by other windows, Chromium pauses the
  site's animations and slows its timers. Music and your Discord status keep working.
- Song detection is event-driven. The page tells the app when you play, pause, seek or change tracks,
  and nothing checks it on a timer.
- **Settings → App → Reduce animations** makes the site's animations instant, for even lower CPU/GPU use.
- Services load only when you open them. Switched-away services are paused, hidden, and freed after the
  time set in Settings → Sources.

## How now-playing detection works

The app reads the page's Media Session metadata, which is the same data Windows uses for its media
overlay, together with the page's audio element for position and duration. It doesn't depend on
Octave's page layout, so a site redesign shouldn't break it.

## Build from source (Windows / macOS / Linux)

Requires Node.js 18 or newer.

```bash
npm install
npm start            # run it
npm run dist:win     # Windows installer + portable  -> dist/
npm run dist:mac     # macOS .dmg (build this on a Mac)
npm run dist:linux   # Linux AppImage
```

Settings are stored in `%APPDATA%\Jam\settings.json` (Windows), `~/Library/Application Support/Jam`
(macOS), or `~/.config/Jam` (Linux). On first launch, Jam copies settings and your login over from the
old `Octave` folder.
