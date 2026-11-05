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

# Logs: the engine's own (limusic.log) and logcat.
timeout 30 adb root >/dev/null 2>&1; sleep 3; timeout 60 adb wait-for-device
for f in $(adb shell "find /data/data/$PKG /data/user/0/$PKG -name 'limusic.log*' 2>/dev/null" | tr -d '\r' | sort -u); do
  adb pull "$f" "$OUT/$(basename "$f")" >/dev/null 2>&1
done
adb logcat -d -v time > "$OUT/logcat-full.txt"
grep -iE "AndroidRuntime|FATAL|panicked|RustStdoutStderr|Tauri|JamPlugin|JamPlayback|mpv|chromium.*CONSOLE" "$OUT/logcat-full.txt" | tail -800 > "$OUT/logcat.txt"
cat "$OUT/summary.txt"
if grep -qE "FATAL EXCEPTION|panicked at" "$OUT/logcat-full.txt"; then
  echo "Jam crashed:"; grep -B2 -A40 -E "FATAL EXCEPTION|panicked at" "$OUT/logcat-full.txt" | head -100; exit 1
fi
echo "smoke test finished"
