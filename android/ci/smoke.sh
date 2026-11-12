#!/usr/bin/env bash
# Emulator test for Jam for Android 3 (Orpheon's app with YouTube Music mode): Spotify mode's cold
# start (timed) and first screen; then YouTube Music mode, switched on the way its setting does it:
# its home screen, a song played from a music.youtube.com link, that it keeps playing with the app
# in the background and the screen off, and its downloads played offline. Screenshots, start times,
# the engine's log and logcat go to <out dir>. Fails only if Jam crashes. With a third APK (the same
# build without the app's Baseline Profile), ends with a speed comparison of the two.
# Usage: smoke.sh <apk> <out dir> [comparison apk]
set -u
APK="$1"; OUT="$2"; AB_APK="${3:-}"; PKG=app.jam.android; ACT=app.jam.android.MainActivity; LAUNCHER=app.myspoti.MainActivity
SONG="${SMOKE_SONG:-dQw4w9WgXcQ}"
mkdir -p "$OUT"
adb wait-for-device
adb install -r -g "$APK" || { echo "install failed"; exit 1; }
adb logcat -c
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
summary() { echo "$*" | tee -a "$OUT/summary.txt"; }
# Taps by label: tap <text or content-desc, or ~regex> [first|last]. Defined before use.
tap() { # tap <text or content-desc> [first|last]
  # A dump that fails (the screen never idle) leaves the last one behind, so clear it first.
  rm -f "$OUT/ui-tap.xml"
  for try in 1 2 3 4 5 6; do
    adb shell rm -f /sdcard/ui.xml; adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml "$OUT/ui-tap.xml" >/dev/null 2>&1 && break
    sleep 3
  done
  local xy
  xy=$(python3 - "$OUT/ui-tap.xml" "$1" "${2:-first}" <<'PY'
import html, re, sys
try: x = open(sys.argv[1]).read()
except Exception: sys.exit()
want, which, hits = sys.argv[2], sys.argv[3], []
for m in re.finditer(r'<node [^>]*>', x):
    n = m.group(0)
    t = re.search(r' text="([^"]*)"', n); d = re.search(r' content-desc="([^"]*)"', n)
    b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
    if not b: continue
    labels = [(html.unescape(t.group(1)) if t else ''), (html.unescape(d.group(1)) if d else '')]
    if any((re.fullmatch(want[1:], l, re.I) if want.startswith('~') else want.lower() == l.lower()) for l in labels):
        a, c, e, f = map(int, b.groups())
        if e > a and f > c: hits.append(((a + e) // 2, (c + f) // 2))
if hits:
    h = hits[-1] if which == 'last' else hits[0]; print(h[0], h[1])
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; return 0; fi
  summary "tap: '$1' not found"; return 1
}

summary "WebView: $(adb shell dumpsys webviewupdate | grep -m1 -oE 'Current WebView package[^)]*\)')"

# Cold starts of the launcher (am start -W: until the first frame), median of five, after one
# warm-up launch (the first run after install also compiles the baseline profile's code).
cold() { # cold <label>
  local times=()
  timeout 90 adb shell am start -W -n "$PKG/$LAUNCHER" >/dev/null 2>&1; sleep 6
  for i in 1 2 3 4 5; do
    adb shell am force-stop "$PKG"; sleep 3
    t=$(timeout 90 adb shell am start -W -n "$PKG/$LAUNCHER" 2>/dev/null | tr -d '\r' | awk -F': ' '/TotalTime/{print $2}')
    times+=("${t:-0}"); sleep 5
  done
  summary "Cold start, $1 (ms, launcher to first frame): ${times[*]} → median $(printf '%s\n' "${times[@]}" | sort -n | sed -n 3p)"
}

# 1. Spotify mode, the default: Orpheon's first screen.
cold "Spotify mode"
# Where a Spotify-mode start goes (builds made with -PjamStartupTrace): the last start's steps.
adb logcat -d -s JamStartup:W > "$OUT/startup-trace.txt"
summary "Start-up steps over 3 ms, last start: $(awk '/Application.onCreate at/{buf=""} {buf=buf"\n"$0} END{print buf}' "$OUT/startup-trace.txt" | grep -oE '(App|Graph)\.[A-Za-z.]+ [0-9.]+ ms' | awk '$2>3' | tr '\n' ';')"
summary "Last start: $(grep -oE '(Application.onCreate|MainActivity.onCreate|MainActivity first frame posted) at [0-9]+ ms' "$OUT/startup-trace.txt" | tail -3 | tr '\n' ';')"
# The same after the system compiled the app from its profile (as a phone does overnight).
adb shell cmd package compile -f -m speed-profile "$PKG" >/dev/null 2>&1
cold "Spotify mode, compiled (speed-profile)"
sleep 8; shot 01-spotify-first-screen
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/ui-spotify.xml" >/dev/null 2>&1

# 1b. Spotify mode without an account (the onboarding's "None"), to its Settings: the Jam section,
#     then YouTube Music mode switched on from there, the way a user does it.
SWITCHED=""
if tap "Get started"; then
  sleep 4; shot 02a-spotify-setup
  tap None && sleep 2 && shot 02a-spotify-none && tap Continue && sleep 4
  for step in 1 2 3 4 5 6; do
    shot "02b-spotify-setup-$step"
    tap "~(Continue|Next|Skip|Skip to Home|Not now|Later|Done|Finish|Start listening|Allow)" last || break
    sleep 4
  done
  sleep 6; shot 02c-spotify-home
  # The profile menu (the avatar), then Settings; the Classic look's About tab holds the Jam rows.
  # Settings: in the More tab (or the profile menu).
  tap More last || tap "~.*(profile|account|avatar).*" || true; sleep 3; shot 02c-spotify-more
  if tap Settings; then
    sleep 4; shot 02d-spotify-settings
    tap About; sleep 2
    for s in 1 2 3; do tap "YouTube Music mode" && break; adb shell input swipe 540 1600 540 600 300; sleep 2; done
    sleep 2; shot 02e-ytm-mode-confirm
    if tap Switch; then SWITCHED=yes; summary "Switched to YouTube Music mode from Spotify mode's Settings"; sleep 20; fi
  fi
fi
[ -n "$SWITCHED" ] || summary "Spotify mode's Settings not reached by the test: YouTube Music mode set the way the switch saves it"

# 2. YouTube Music mode, as Settings ▸ YouTube Music mode leaves it (JamMode: jam_mode.xml, then a
#    restart). The launcher must hand over to YouTube Music.
adb shell am force-stop "$PKG"
timeout 30 adb root </dev/null >/dev/null 2>&1; sleep 3; timeout 60 adb wait-for-device
printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <boolean name=\"ytm\" value=\"true\" />\n</map>\n" > "$OUT/jam_mode.xml"
adb push "$OUT/jam_mode.xml" /data/local/tmp/jam_mode.xml >/dev/null
[ -n "$SWITCHED" ] || adb shell 'D=/data/data/app.jam.android; U=$(stat -c %u $D); mkdir -p $D/shared_prefs; cp /data/local/tmp/jam_mode.xml $D/shared_prefs/jam_mode.xml; chown $U:$U $D/shared_prefs $D/shared_prefs/jam_mode.xml; chmod 660 $D/shared_prefs/jam_mode.xml; restorecon -R $D/shared_prefs'
cold "YouTube Music mode (launcher → YouTube Music)"
summary "On screen in YouTube Music mode: $(adb shell dumpsys activity activities | grep -m1 -oE 'topResumedActivity=[^ ]+ [^ ]+ [^ ]+' )"
summary "Spotify mode's entry points in YouTube Music mode: $(adb shell pm dump $PKG | grep -A12 'disabledComponents:' | grep -cE 'PlaybackService|MediaButtonReceiver|NowPlayingWidget|WearLinkService') turned off (of 4)"

# YouTube Music: the engine's UI, on the phone layout.
sleep 30; shot 03-ytm-home
adb shell input swipe 540 1700 540 700 300; sleep 3; shot 04-ytm-home-scrolled

# Play a song from a link, the way a shared link opens Jam.
adb shell am start -a android.intent.action.VIEW -d "https://music.youtube.com/watch?v=$SONG" -n "$PKG/$ACT"
sleep 35; shot 05-ytm-playing
adb shell dumpsys media_session > "$OUT/media_session-foreground.txt"

# Background + screen off.
adb shell input keyevent HOME; sleep 15
adb shell input keyevent KEYCODE_SLEEP; sleep 40
adb shell dumpsys audio > "$OUT/audio.txt"
adb shell dumpsys media_session > "$OUT/media_session.txt"
adb shell dumpsys notification --noredact > "$OUT/notification.txt"
PID=$(adb shell pidof "$PKG" | tr -d '\r')
adb shell input keyevent KEYCODE_WAKEUP; sleep 2
timeout 10 adb shell wm dismiss-keyguard </dev/null 2>/dev/null; sleep 2
adb shell cmd statusbar expand-notifications; sleep 3; shot 06-notifications
adb shell cmd statusbar collapse; sleep 1
timeout 60 adb shell am start -W -n "$PKG/$LAUNCHER"; sleep 4; shot 07-back-in-app

JUID=$(adb shell stat -c %u /data/data/$PKG 2>/dev/null | tr -d '\r')
summary "Jam pid after 55s in background: ${PID:-not running}"
summary "Jam audio players:"; grep -E "u/pid:${JUID:-x}/" "$OUT/audio.txt" | grep -oE "state:[a-z]+" | sort | uniq -c | tee -a "$OUT/summary.txt"
summary "Jam media session:"; awk '/app.jam.android/{f=1} f&&/state=PlaybackState/{print; exit}' "$OUT/media_session.txt" | cut -c1-160 | tee -a "$OUT/summary.txt"
summary "Notification title: $(grep -A40 'pkg=app.jam.android' "$OUT/notification.txt" | grep -m1 -oE 'android.title=[^)]*' )"

# More of YouTube Music, opened from links: an album, an artist and a playlist.
n=9
for link in "browse/MPREb_K8qWMWVqXGi album" "channel/UClYV6hHlupm_S_ObS1W-DYw artist" "playlist?list=RDCLAK5uy_n20FRYQXNt1p1wS55Nj2r14IouO5weaYU playlist"; do
  set -- $link
  adb shell "am start -a android.intent.action.VIEW -d 'https://music.youtube.com/$1' -n $PKG/$ACT" >/dev/null
  sleep 14; shot "$(printf %02d $n)-ytm-$2"; n=$((n+1))
  adb shell input swipe 540 1600 540 700 300; sleep 2; shot "$(printf %02d $n)-ytm-$2-scrolled"; n=$((n+1))
done

# YouTube Music downloads: download an album from its page, watch the notification, then play the
# downloads with the phone in airplane mode. Taps go through uiautomator, which sees the page's
# buttons by their labels; playback is paused first, since a moving progress bar keeps uiautomator
# from ever finding the screen idle.
adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 1
# 1. A whole album, from the Download button in its header. (CI's datacenter IP is refused some
#    tracks without an account; the failures show on the Downloads page.)
adb shell "am start -a android.intent.action.VIEW -d 'https://music.youtube.com/browse/MPREb_K8qWMWVqXGi' -n $PKG/$ACT" >/dev/null
sleep 16
if tap Download; then
  sleep 6; shot "$(printf %02d $n)-dl-started"; n=$((n+1))
  adb shell cmd statusbar expand-notifications; sleep 3; shot "$(printf %02d $n)-dl-notification"; n=$((n+1))
  adb shell dumpsys notification --noredact > "$OUT/notification-downloads.txt"
  adb shell cmd statusbar collapse; sleep 1
  sleep 40; shot "$(printf %02d $n)-dl-album-after"; n=$((n+1))
fi
# 2. The song CI can play, from the player's menu.
adb shell am start -a android.intent.action.VIEW -d "https://music.youtube.com/watch?v=$SONG" -n "$PKG/$ACT" >/dev/null
sleep 25; adb shell input keyevent KEYCODE_MEDIA_PAUSE; sleep 2
# The full-screen player opens on play; if it didn't, the mini player opens it.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui-player.xml" >/dev/null 2>&1
grep -q '="Minimise player"' "$OUT/ui-player.xml" || { tap "Never Gonna Give You Up" last; sleep 3; }
tap "Track options" last
sleep 2; shot "$(printf %02d $n)-dl-song-menu"; n=$((n+1))
if tap Download last; then
  sleep 45
  tap "Minimise player"; sleep 2
  tap More last && sleep 3 && tap Downloads && sleep 5
  shot "$(printf %02d $n)-dl-downloads"; n=$((n+1))
  # 3. Offline: airplane mode, then Play on the Downloads page.
  adb shell cmd connectivity airplane-mode enable; adb shell svc wifi disable; adb shell svc data disable; sleep 8
  shot "$(printf %02d $n)-dl-offline"; n=$((n+1))
  if tap Play; then
    sleep 20; shot "$(printf %02d $n)-dl-offline-playing"; n=$((n+1))
    adb shell dumpsys media_session > "$OUT/media_session-offline.txt"
    summary "Offline media session: $(awk '/app.jam.android/{f=1} f&&/state=PlaybackState/{print; exit}' "$OUT/media_session-offline.txt" | cut -c1-120)"
    adb shell input keyevent KEYCODE_MEDIA_PAUSE
  fi
  tap "Minimise player"; sleep 2
  tap Home last; sleep 10; shot "$(printf %02d $n)-dl-offline-home"; n=$((n+1))
  # A playlist opened online earlier, opened again offline: its saved copy, undownloaded songs greyed.
  adb shell "am start -a android.intent.action.VIEW -d 'https://music.youtube.com/playlist?list=RDCLAK5uy_n20FRYQXNt1p1wS55Nj2r14IouO5weaYU' -n $PKG/$ACT" >/dev/null
  sleep 10; shot "$(printf %02d $n)-dl-offline-playlist"; n=$((n+1))
  adb shell cmd connectivity airplane-mode disable; adb shell svc wifi enable; adb shell svc data enable; sleep 8
fi

# Logs: the engine's own (limusic.log) and logcat.
timeout 30 adb root >/dev/null 2>&1; sleep 3; timeout 60 adb wait-for-device
for f in $(adb shell "find /data/data/$PKG /data/user/0/$PKG -name 'limusic.log*' 2>/dev/null" | tr -d '\r' | sort -u); do
  adb pull "$f" "$OUT/$(basename "$f")" >/dev/null 2>&1
done
adb shell "find /data/data/$PKG -path '*downloads*' -type f -exec ls -l {} + 2>/dev/null" > "$OUT/downloads-dir.txt" 2>&1
summary "Downloaded files on the device: $(grep -cE '\.(webm|m4a|audio)$' "$OUT/downloads-dir.txt"), covers: $(grep -c '\.jpg$' "$OUT/downloads-dir.txt")"
adb logcat -d -v time > "$OUT/logcat-full.txt"
grep -iE "AndroidRuntime|FATAL|panicked|RustStdoutStderr|Tauri|JamPlugin|JamPlayback|mpv|chromium.*CONSOLE" "$OUT/logcat-full.txt" | tail -800 > "$OUT/logcat.txt"
summary "Engine log (last two app starts only): $(cat "$OUT"/limusic.log* 2>/dev/null | grep -c 'downloads: downloaded video_id') downloaded, $(cat "$OUT"/limusic.log* 2>/dev/null | grep -c 'download failed') failed, played from a download $(cat "$OUT"/limusic.log* 2>/dev/null | grep -c 'playing the downloaded file') times"
cat "$OUT"/limusic.log* 2>/dev/null | grep -E 'download' | tail -40 > "$OUT/downloads-log.txt"
# Speed: the app with and without its own Baseline Profile, the same build otherwise, alternated on
# this emulator. Each: installed fresh, launched once (its profiles installed), compiled the way a
# phone does overnight (speed-profile), then five cold starts.
if [ -n "$AB_APK" ] && [ -f "$AB_APK" ]; then
  ab() { # ab <apk> <label>
    adb uninstall "$PKG" >/dev/null 2>&1; adb install -r -g "$1" >/dev/null 2>&1 || { summary "install failed: $2"; return; }
    timeout 90 adb shell am start -W -n "$PKG/$LAUNCHER" >/dev/null 2>&1; sleep 12
    adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE "$PKG/androidx.profileinstaller.ProfileInstallReceiver" >/dev/null 2>&1; sleep 3
    adb shell cmd package compile -f -m speed-profile "$PKG" >/dev/null 2>&1
    local times=()
    for i in 1 2 3 4 5; do
      adb shell am force-stop "$PKG"; sleep 3
      t=$(timeout 90 adb shell am start -W -n "$PKG/$LAUNCHER" 2>/dev/null | tr -d '\r' | awk -F': ' '/TotalTime/{print $2}')
      times+=("${t:-0}"); sleep 4
    done
    echo "$2 ${times[*]}" >> "$OUT/speed-ab.txt"
  }
  for round in 1 2; do ab "$AB_APK" "without"; ab "$APK" "with"; done
  med() { grep "^$1 " "$OUT/speed-ab.txt" | cut -d' ' -f2- | tr ' ' '\n' | grep -v '^0$' | sort -n | awk '{a[NR]=$1} END{print (NR%2 ? a[(NR+1)/2] : int((a[NR/2]+a[NR/2+1])/2))}'; }
  summary "Speed, cold start to first frame after the overnight compile (ms, median of 10): without the app's Baseline Profile $(med without), with it $(med with)"
  adb logcat -d -v time > "$OUT/logcat-ab.txt"
fi
cat "$OUT/summary.txt"
# Jam's own crashes only: uiautomator (the test's tapping tool) sometimes dies on its own.
if cat "$OUT/logcat-full.txt" "$OUT/logcat-ab.txt" 2>/dev/null | grep -A2 "FATAL EXCEPTION" | grep -q "Process: $PKG" || grep -q "panicked at" "$OUT/logcat-full.txt"; then
  echo "Jam crashed:"; cat "$OUT/logcat-full.txt" "$OUT/logcat-ab.txt" 2>/dev/null | grep -B2 -A40 -E "FATAL EXCEPTION|panicked at" | head -100; exit 1
fi
echo "smoke test finished"
