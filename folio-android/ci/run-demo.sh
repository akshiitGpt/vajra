#!/usr/bin/env bash
# Installs Folio on the running emulator, plays the automated demo (DemoDriver) and
# captures screenshots at every SHOT marker plus a screen recording of the whole run.
set -u
APK="$1"; OUT="$2"
mkdir -p "$OUT/screens"
PKG=com.akshit.folio

shot() { adb exec-out screencap -p > "$OUT/screens/$1.png"; echo "captured $1"; }

adb wait-for-device
adb shell settings put secure immersive_mode_confirmations confirmed
adb shell settings put global window_animation_scale 0
adb shell wm size 1800x1125
adb shell wm density 240
adb shell settings put system user_rotation 1 || true
adb install -r -g "$APK"
adb shell getprop ro.build.version.release > "$OUT/android_version.txt"

# Gallery (empty)
adb shell am start -W -n $PKG/.GalleryActivity
sleep 4; shot 00_gallery_empty

# Screen recording in 170 s segments until the demo finishes
rm -f "$OUT/.done"
(
  i=0
  while [ ! -f "$OUT/.done" ]; do
    adb shell screenrecord --bit-rate 6000000 --time-limit 170 /sdcard/folio_$i.mp4
    i=$((i+1))
  done
) &
REC=$!
sleep 2

adb logcat -c
adb shell am start -n $PKG/.CanvasActivity --es demo all --es doc_name "Demo"
taken=" "
end=$((SECONDS+900))
while [ $SECONDS -lt $end ]; do
  adb logcat -d -v raw -s FolioDemo:I > "$OUT/demo.log" 2>/dev/null
  for s in $(grep -o 'SHOT:[A-Za-z0-9_]*' "$OUT/demo.log" | cut -d: -f2); do
    case "$taken" in *" $s "*) ;; *) shot "$s"; taken="$taken$s " ;; esac
  done
  if grep -q "DEMO DONE" "$OUT/demo.log"; then break; fi
  if adb logcat -d -b crash | grep -q "$PKG"; then echo "APP CRASHED"; break; fi
  sleep 0.4
done
sleep 2
touch "$OUT/.done"
adb shell pkill -INT screenrecord || true
sleep 4
kill $REC 2>/dev/null || true
for f in $(adb shell ls /sdcard/ | tr -d '\r' | grep '^folio_.*\.mp4$'); do adb pull "/sdcard/$f" "$OUT/" ; done

# Gallery with the saved drawing, settings, pen lab
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell am start -W -n $PKG/.GalleryActivity
sleep 3; shot 27_gallery
adb shell am start -W -n $PKG/.SettingsActivity
sleep 3; shot 28_settings_pen_palm
adb shell input swipe 900 1000 900 150 400; sleep 1
adb shell input swipe 900 1000 900 150 400; sleep 2; shot 29_settings_gestures
adb shell input swipe 900 1000 900 150 400; sleep 1
adb shell input swipe 900 1000 900 150 400; sleep 2; shot 30_settings_more
adb shell am start -W -n $PKG/.PenLabActivity
sleep 2
adb shell input stylus swipe 200 400 900 700 600 || adb shell input swipe 200 400 900 700 600
adb shell input stylus swipe 200 700 900 400 600 || true
sleep 1; shot 31_pen_lab

adb logcat -d > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/crash.txt"
{
  echo "Android $(cat "$OUT/android_version.txt")"
  grep -E "ASSERT|DEMO" "$OUT/demo.log"
  echo "PASS: $(grep -c 'ASSERT PASS' "$OUT/demo.log")  FAIL: $(grep -c 'ASSERT FAIL' "$OUT/demo.log")"
  if [ -s "$OUT/crash.txt" ]; then echo "CRASH LOG:"; cat "$OUT/crash.txt"; fi
} > "$OUT/summary.txt"
cat "$OUT/summary.txt"
exit 0
