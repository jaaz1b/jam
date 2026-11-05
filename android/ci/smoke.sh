#!/usr/bin/env bash
# Emulator smoke test: installs the APK, opens every built-in source and Settings, takes screenshots,
# checks background playback, and fails if Jam crashed.
# Usage: smoke.sh <release apk> <output dir> [debug apk, for the background-playback test]
set -u
APK="$1"; OUT="$2"; DEBUG_APK="${3:-}"; PKG=app.jam.android; ACT=app.jam.MainActivity
mkdir -p "$OUT"
adb wait-for-device
adb install -r -g "$APK" || { echo "install failed"; exit 1; }
adb logcat -c
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }

timeout 60 adb shell am start -W -n "$PKG/$ACT"
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

# Background playback (debug build, which may load http://10.0.2.2 = this machine): add a test source
# that plays a quiet tone straight into Jam's settings, start it, leave the app, turn the screen off,
# then check the audio kept going.
if [ -n "$DEBUG_APK" ]; then
  python3 -m http.server 8000 --directory "$(dirname "$0")" </dev/null >/dev/null 2>&1 &
  SERVER=$!
  adb install -r -g "$DEBUG_APK" || echo "debug install failed"
  adb shell am force-stop "$PKG"
  timeout 30 adb root </dev/null >/dev/null 2>&1; sleep 3; timeout 60 adb wait-for-device
  python3 - "http://10.0.2.2:8000/test-player.html" > "$OUT/jam.xml" <<'PY'
import json, sys
from xml.sax.saxutils import escape
s = {"customSources": [{"id": "c_smoketest", "name": "Test tone", "url": sys.argv[1], "color": "#22c55e", "desktopSite": False, "extraDomains": []}],
     "defaultSource": "c_smoketest", "openLastSource": False}
print("<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n    <string name=\"settings\">" + escape(json.dumps(s), {'"': "&quot;"}) + "</string>\n</map>")
PY
  adb push "$OUT/jam.xml" /data/local/tmp/jam.xml >/dev/null
  adb shell 'D=/data/data/app.jam.android; U=$(stat -c %u $D); mkdir -p $D/shared_prefs; cp /data/local/tmp/jam.xml $D/shared_prefs/jam.xml; chown $U:$U $D/shared_prefs $D/shared_prefs/jam.xml; chmod 660 $D/shared_prefs/jam.xml; restorecon -R $D/shared_prefs'
  timeout 60 adb shell am start -W -n "$PKG/$ACT"
  sleep 20; shot 9-test-playing
  adb shell input keyevent HOME; sleep 20
  adb shell input keyevent KEYCODE_SLEEP; sleep 45
  adb shell dumpsys audio > "$OUT/audio.txt"
  adb shell dumpsys media_session > "$OUT/media_session.txt"
  adb shell dumpsys notification --noredact > "$OUT/notification.txt"
  adb shell pidof "$PKG" > "$OUT/pid.txt" || echo "not running" > "$OUT/pid.txt"
  adb shell input keyevent KEYCODE_WAKEUP; sleep 2
  timeout 10 adb shell wm dismiss-keyguard </dev/null 2>/dev/null; sleep 2
  adb shell cmd statusbar expand-notifications; sleep 3; shot 10-notifications
  adb shell cmd statusbar collapse; sleep 1
  timeout 60 adb shell am start -W -n "$PKG/$ACT"; sleep 4; shot 11-back-in-app
  echo "--- background playback ---"
  echo "Jam pid: $(cat "$OUT/pid.txt")"
  JUID=$(adb shell stat -c %u /data/data/$PKG | tr -d '\r')
  echo "Jam uid: $JUID"
  grep -E "u/pid:$JUID/" "$OUT/audio.txt" | cut -c1-200 | head -5
  awk '/app.jam.android/{f=1} f&&/state=PlaybackState/{print; exit}' "$OUT/media_session.txt" | cut -c1-200
  grep -c "pkg=app.jam.android" "$OUT/notification.txt" | sed 's/^/Jam notifications: /'
  kill "$SERVER" 2>/dev/null
fi

adb shell dumpsys package "$PKG" | grep -E "versionName|versionCode" > "$OUT/version.txt"
adb shell dumpsys webviewupdate > "$OUT/webview.txt" 2>&1
adb logcat -d -v time > "$OUT/logcat-full.txt"
grep -E "AndroidRuntime|FATAL|Jam|chromium|cr_" "$OUT/logcat-full.txt" | tail -600 > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat-full.txt"; then
  echo "Jam crashed:"; grep -A40 "FATAL EXCEPTION" "$OUT/logcat-full.txt" | head -80; exit 1
fi
echo "smoke test passed"
