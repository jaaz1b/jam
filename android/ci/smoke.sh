#!/usr/bin/env bash
# Emulator test for Jam for Android: the web sources, then YouTube Music (the engine): its home
# screen, a song played from a music.youtube.com link, and that the song keeps playing with the app
# in the background and the screen off. Screenshots, the engine's log and logcat go to <out dir>.
# Fails only if Jam crashes. Usage: smoke.sh <apk> <output dir>
set -u
APK="$1"; OUT="$2"; PKG=app.jam.android; ACT=app.jam.android.MainActivity
SONG="${SMOKE_SONG:-dQw4w9WgXcQ}"
mkdir -p "$OUT"
adb wait-for-device
adb install -r -g "$APK" || { echo "install failed"; exit 1; }
adb logcat -c
summary "WebView: $(adb shell dumpsys webviewupdate | grep -m1 -oE 'Current WebView package[^)]*\)')"
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
summary() { echo "$*" | tee -a "$OUT/summary.txt"; }

timeout 90 adb shell am start -W -n "$PKG/$ACT"
sleep 25; shot 01-octave
adb shell am start -n "$PKG/$ACT" --es source monochrome; sleep 20; shot 02-monochrome

# YouTube Music: the engine's UI, on the phone layout.
adb shell am start -n "$PKG/$ACT" --es source ytm
sleep 40; shot 03-ytm-home
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
timeout 60 adb shell am start -W -n "$PKG/$ACT"; sleep 4; shot 07-back-in-app

JUID=$(adb shell stat -c %u /data/data/$PKG 2>/dev/null | tr -d '\r')
summary "Jam pid after 55s in background: ${PID:-not running}"
summary "Jam audio players:"; grep -E "u/pid:${JUID:-x}/" "$OUT/audio.txt" | grep -oE "state:[a-z]+" | sort | uniq -c | tee -a "$OUT/summary.txt"
summary "Jam media session:"; awk '/app.jam.android/{f=1} f&&/state=PlaybackState/{print; exit}' "$OUT/media_session.txt" | cut -c1-160 | tee -a "$OUT/summary.txt"
summary "Notification title: $(grep -A40 'pkg=app.jam.android' "$OUT/notification.txt" | grep -m1 -oE 'android.title=[^)]*' )"

# Jam's settings page, through the toolbar button.
adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 && adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
TAP=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys
try: x = open(sys.argv[1]).read()
except Exception: sys.exit()
m = re.search(r'content-desc="Settings"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', x)
if m:
    a, b, c, d = map(int, m.groups()); print((a + c) // 2, (b + d) // 2)
PY
)
if [ -n "$TAP" ]; then
  adb shell input tap $TAP; sleep 4; shot 08-settings
  adb shell input keyevent BACK; sleep 2
fi

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
tap() { # tap <text or content-desc> [first|last]
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui-tap.xml" >/dev/null 2>&1
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
    if want.lower() in {(html.unescape(t.group(1)) if t else '').lower(), (html.unescape(d.group(1)) if d else '').lower()}:
        a, c, e, f = map(int, b.groups())
        if e > a and f > c: hits.append(((a + e) // 2, (c + f) // 2))
if hits:
    h = hits[-1] if which == 'last' else hits[0]; print(h[0], h[1])
PY
)
  if [ -n "$xy" ]; then adb shell input tap $xy; return 0; fi
  summary "tap: '$1' not found"; return 1
}
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
  tap More last && sleep 2 && tap Downloads && sleep 5
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
  adb shell cmd connectivity airplane-mode disable; adb shell svc wifi enable; adb shell svc data enable; sleep 8
fi

# Offline web app (what Octave relies on): a test site with a service worker, served from this
# machine at http://localhost:8000 (adb reverse), opened as a Jam source marked "works offline",
# then opened again with its server gone. It should still load, from the service worker.
python3 -m http.server 8000 --directory "$(dirname "$0")" </dev/null >/dev/null 2>&1 &
SERVER=$!
adb shell am force-stop "$PKG"
timeout 30 adb root </dev/null >/dev/null 2>&1; sleep 3; timeout 60 adb wait-for-device
adb reverse tcp:8000 tcp:8000 >/dev/null
python3 - > "$OUT/jam-offline.xml" <<'PY'
import json
from xml.sax.saxutils import escape
s = {"customSources": [{"id": "c_offlinetest", "name": "Offline test", "url": "http://localhost:8000/offline-pwa/",
                        "color": "#22c55e", "desktopSite": False, "offlineApp": True, "extraDomains": []}],
     "defaultSource": "c_offlinetest", "openLastSource": False}
print("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"settings\">" + escape(json.dumps(s), {'"': "&quot;"}) + "</string>\n</map>")
PY
adb push "$OUT/jam-offline.xml" /data/local/tmp/jam.xml >/dev/null
adb shell 'D=/data/data/app.jam.android; U=$(stat -c %u $D); mkdir -p $D/shared_prefs; cp /data/local/tmp/jam.xml $D/shared_prefs/jam.xml; chown $U:$U $D/shared_prefs $D/shared_prefs/jam.xml; chmod 660 $D/shared_prefs/jam.xml; restorecon -R $D/shared_prefs'
timeout 60 adb shell am start -W -n "$PKG/$ACT" >/dev/null
sleep 15; shot "$(printf %02d $n)-offline-app-online"; n=$((n+1))
kill "$SERVER" 2>/dev/null; sleep 1
adb shell am force-stop "$PKG"
timeout 60 adb shell am start -W -n "$PKG/$ACT" >/dev/null
sleep 12; shot "$(printf %02d $n)-offline-app-server-gone"; n=$((n+1))
# And with the phone offline, the way Octave is used.
adb shell am force-stop "$PKG"
adb shell cmd connectivity airplane-mode enable; adb shell svc wifi disable; adb shell svc data disable; sleep 6
timeout 60 adb shell am start -W -n "$PKG/$ACT" >/dev/null
sleep 14; shot "$(printf %02d $n)-offline-app-airplane"; n=$((n+1))
adb shell cmd connectivity airplane-mode disable; adb shell svc wifi enable; adb shell svc data enable; sleep 4
adb shell "cat /data/data/$PKG/shared_prefs/jam.xml" 2>/dev/null > "$OUT/jam-prefs.xml"
python3 - "$OUT/jam-prefs.xml" <<'PY' | tee -a "$OUT/summary.txt"
import html, json, re, sys
x = open(sys.argv[1]).read()
for key in ('secret.offline.c_offlinetest', 'secret.offlinelog.c_offlinetest'):
    m = re.search(r'name="' + re.escape(key) + r'">([^<]*)<', x)
    if not m: print(key + ': (none)'); continue
    v = json.loads(html.unescape(m.group(1)))
    if isinstance(v, list): print('Offline web app events: ' + ' | '.join(e.get('ev', '') + (' ' + e.get('d', '')[:60] if e.get('d') else '') for e in v[-14:]))
    else: print('Offline web app status: why=%s controlled=%s regs=%d error=%s' % (v.get('why'), v.get('controlled'), len(v.get('registrations') or []), (v.get('error') or '')[:120]))
PY

# Saved state (the save button for web apps that work offline): save the test site, then check it
# opens from the save without asking the server for anything, and that the switch goes back live.
python3 -m http.server 8000 --directory "$(dirname "$0")" </dev/null >/dev/null 2>"$OUT/server.log" &
SERVER=$!
adb reverse tcp:8000 tcp:8000 >/dev/null
adb shell am force-stop "$PKG"
timeout 60 adb shell am start -W -n "$PKG/$ACT" >/dev/null; sleep 12
shot "$(printf %02d $n)-saved-live"; n=$((n+1))
if tap "Saved state"; then
  sleep 2; shot "$(printf %02d $n)-saved-dialog"; n=$((n+1))
  tap "Save now" && sleep 2 && tap "Save" && sleep 30
  shot "$(printf %02d $n)-saved-after-restart"; n=$((n+1))
  BEFORE=$(grep -c 'GET /offline-pwa' "$OUT/server.log")
  adb shell am force-stop "$PKG"; timeout 60 adb shell am start -W -n "$PKG/$ACT" >/dev/null; sleep 14
  shot "$(printf %02d $n)-saved-reopened"; n=$((n+1))
  AFTER=$(grep -c 'GET /offline-pwa' "$OUT/server.log")
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/ui-saved.xml" >/dev/null 2>&1
  summary "Saved state: requests to the site while it was open: $((AFTER - BEFORE)); page says: $(grep -oE 'Served with the service worker[^"]*' "$OUT/ui-saved.xml" | head -1); chip: $(grep -oE 'Offline test · Saved' "$OUT/ui-saved.xml" | head -1)"
  # Back to the live site with the switch.
  tap "Saved state, on" && sleep 2 && tap "Open my saved state" && sleep 12
  shot "$(printf %02d $n)-saved-switched-live"; n=$((n+1))
  summary "Live again: requests to the site: $(( $(grep -c 'GET /offline-pwa' "$OUT/server.log") - AFTER ))"
fi
kill "$SERVER" 2>/dev/null

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
cat "$OUT/summary.txt"
if grep -qE "FATAL EXCEPTION|panicked at" "$OUT/logcat-full.txt"; then
  echo "Jam crashed:"; grep -B2 -A40 -E "FATAL EXCEPTION|panicked at" "$OUT/logcat-full.txt" | head -100; exit 1
fi
echo "smoke test finished"
