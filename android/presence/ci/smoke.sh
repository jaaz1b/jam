#!/usr/bin/env bash
# Emulator test for Jam Presence: a stand-in "Apple Music" (the fakeplayer module, Apple Music's
# package name) plays a known song; with notification access allowed, Jam Presence must pick it up,
# find its cover and link in Apple's catalog, and show it in the preview. Discord itself can't be
# signed in to here. Screenshots, the UI text and logcat go to <out dir>.
# Usage: smoke.sh <jam presence apk> <fake player apk> <out dir>
set -u
APK="$1"; FAKE="$2"; OUT="$3"; PKG=app.jam.presence
mkdir -p "$OUT"
adb wait-for-device
adb install -r -g "$APK" || { echo "install failed"; exit 1; }
adb install -r -g "$FAKE" || { echo "fake player install failed"; exit 1; }
adb logcat -c
shot() { adb exec-out screencap -p > "$OUT/$1.png"; }
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb pull /sdcard/ui.xml "$OUT/$1.xml" >/dev/null 2>&1; }
fail=0

# First start: nothing allowed yet.
adb shell am start -W -n $PKG/.SettingsActivity | tee "$OUT/start.txt"
sleep 4; shot 1-first-start

# Notification access (what the Allow button leads to), then the "song".
adb shell cmd notification allow_listener $PKG/$PKG.MediaListener
sleep 2
adb shell am start -W -n com.apple.android.music/app.jam.presence.fakeplayer.PlayerActivity
sleep 6
adb shell am start -W -n $PKG/.SettingsActivity
sleep 3; shot 2-setup-and-players; ui 2-setup
adb shell am start -n $PKG/.SettingsActivity --es section preview
sleep 3; shot 3-preview; ui 3-preview

adb logcat -d > "$OUT/logcat.txt"
grep "JamPresence" "$OUT/logcat.txt" | tail -20 | tee "$OUT/presence-log.txt"
if grep -q "FATAL EXCEPTION" "$OUT/logcat.txt" && grep -A3 "FATAL EXCEPTION" "$OUT/logcat.txt" | grep -q "$PKG"; then
  echo "CRASH"; fail=1
fi
grep -q "Apple Music: Espresso · Sabrina Carpenter (playing)" "$OUT/presence-log.txt" && echo "OK: song picked up" || { echo "MISSING: song not picked up"; fail=1; }
grep -q "art=https://" "$OUT/presence-log.txt" && echo "OK: cover found" || echo "WARN: no cover (catalog lookup)"
grep -q "link=https://music.apple.com/" "$OUT/presence-log.txt" && echo "OK: Apple Music link" || echo "WARN: no song link"
grep -qi "Espresso" "$OUT/3-preview.xml" && echo "OK: preview shows the song" || echo "WARN: preview text not in the UI dump"
exit $fail
