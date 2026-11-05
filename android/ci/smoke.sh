#!/usr/bin/env bash
# Emulator smoke test: installs the APK, opens every built-in source and Settings, takes screenshots,
# backgrounds the app, and fails if Jam crashed. Usage: smoke.sh <apk> <output dir>
set -u
APK="$1"; OUT="$2"; PKG=app.jam.android; ACT=app.jam.MainActivity
mkdir -p "$OUT"
adb wait-for-device
adb install -r -g "$APK" || { echo "install failed"; exit 1; }
adb logcat -c
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

adb shell am start -W -n "$PKG/$ACT"
sleep 30; shot 1-octave
n=2
for s in monochrome ytm spotify apple; do
  adb shell am start -n "$PKG/$ACT" --es source "$s"
  sleep 30; shot "$n-$s"; n=$((n+1))
done

# Settings, through the toolbar button.
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
  adb shell input tap $TAP; sleep 5; shot 7-settings
  for i in 1 2 3 4; do adb shell input swipe 540 1700 540 500 250; sleep 1; shot "8-settings-$i"; done
  adb shell input keyevent BACK; sleep 2
else
  echo "Settings button not found"
fi

# Background playback: add a test source (a page that plays a quiet tone) straight into Jam's settings,
# start playing, leave the app, turn the screen off, and check the audio keeps going.
if [ -n "${GITHUB_SHA:-}" ]; then
  TEST_URL="https://raw.githack.com/${GITHUB_REPOSITORY}/${GITHUB_SHA}/android/ci/test-player.html"
  adb shell am force-stop "$PKG"
  adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
  python3 - "$TEST_URL" > "$OUT/jam.xml" <<'PY'
import json, sys
from xml.sax.saxutils import escape
s = {"customSources": [{"id": "c_smoketest", "name": "Test tone", "url": sys.argv[1], "color": "#22c55e", "desktopSite": False, "extraDomains": []}],
     "defaultSource": "c_smoketest", "openLastSource": False}
print("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"settings\">" + escape(json.dumps(s), {'"': "&quot;"}) + "</string>\n</map>")
PY
  adb push "$OUT/jam.xml" /data/local/tmp/jam.xml >/dev/null
  adb shell 'D=/data/data/app.jam.android; U=$(stat -c %u $D); mkdir -p $D/shared_prefs; cp /data/local/tmp/jam.xml $D/shared_prefs/jam.xml; chown $U:$U $D/shared_prefs $D/shared_prefs/jam.xml; chmod 660 $D/shared_prefs/jam.xml; restorecon -R $D/shared_prefs'
  adb shell am start -W -n "$PKG/$ACT"
  sleep 25; shot 9-test-playing
  adb shell input keyevent HOME; sleep 20
  adb shell input keyevent KEYCODE_SLEEP; sleep 40
  adb shell dumpsys audio > "$OUT/audio.txt"
  adb shell dumpsys media_session > "$OUT/media_session.txt"
  adb shell dumpsys notification --noredact > "$OUT/notification.txt"
  adb shell pidof "$PKG" > "$OUT/pid.txt" || echo "not running" > "$OUT/pid.txt"
  adb shell input keyevent KEYCODE_WAKEUP; sleep 2
  adb shell wm dismiss-keyguard 2>/dev/null; adb shell input keyevent 82; sleep 2
  shot 10-notification-shade-off
  adb shell cmd statusbar expand-notifications; sleep 3; shot 11-notifications
  adb shell cmd statusbar collapse; sleep 1
  adb shell am start -W -n "$PKG/$ACT"; sleep 4; shot 12-back-in-app
  echo "--- background playback ---"
  echo "Jam pid: $(cat "$OUT/pid.txt")"
  grep -iE "state:(started|paused)" "$OUT/audio.txt" | head -5
  grep -iE "app.jam|state=PlaybackState" "$OUT/media_session.txt" | head -6
fi
adb shell dumpsys package "$PKG" | grep -E "versionName|versionCode" > "$OUT/version.txt"
adb shell dumpsys webviewupdate > "$OUT/webview.txt" 2>&1
adb logcat -d -v time > "$OUT/logcat-full.txt"
grep -E "AndroidRuntime|FATAL|Jam|chromium|cr_" "$OUT/logcat-full.txt" | tail -600 > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat-full.txt"; then
  echo "Jam crashed:"; grep -A40 "FATAL EXCEPTION" "$OUT/logcat-full.txt" | head -80; exit 1
fi
echo "smoke test passed"
