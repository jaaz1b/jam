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

# Background: Jam should stay alive (music keeps playing).
adb shell input keyevent HOME; sleep 5
adb shell pidof "$PKG" > "$OUT/pid.txt" || echo "not running" > "$OUT/pid.txt"
adb shell dumpsys package "$PKG" | grep -E "versionName|versionCode" > "$OUT/version.txt"
adb shell dumpsys webviewupdate > "$OUT/webview.txt" 2>&1
adb logcat -d -v time > "$OUT/logcat-full.txt"
grep -E "AndroidRuntime|FATAL|Jam|chromium|cr_" "$OUT/logcat-full.txt" | tail -600 > "$OUT/logcat.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat-full.txt"; then
  echo "Jam crashed:"; grep -A40 "FATAL EXCEPTION" "$OUT/logcat-full.txt" | head -80; exit 1
fi
echo "smoke test passed"
