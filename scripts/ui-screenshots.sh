#!/usr/bin/env bash
# Drives the app on a running emulator and saves screenshots to docs/screenshots.
# Never fails the job: a missing button just means a missing screenshot.
set -u
PKG=com.example.ordernotifier
OUT=docs/screenshots
APK=app/build/outputs/apk/debug/app-debug.apk
mkdir -p "$OUT"
rm -f "$OUT"/*.png

shot() { # name [delay-seconds]
  sleep "${2:-2}"
  adb exec-out screencap -p > "$OUT/$1.png" && echo "screenshot: $1"
}

# Tap the centre of the view with the given resource id (first match).
# The UI dump is retried because uiautomator sometimes returns nothing on its first call.
tap_id() {
  local xy="" try
  for try in 1 2 3 4; do
    rm -f /tmp/ui.xml
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
    if [ -f /tmp/ui.xml ]; then
      xy=$(python3 - "$1" <<'PY'
import re, sys
x = open('/tmp/ui.xml', encoding='utf-8').read()
m = re.search(r'resource-id="[^"]*:id/%s"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"' % sys.argv[1], x)
print('' if not m else '%d %d' % ((int(m.group(1)) + int(m.group(3))) // 2, (int(m.group(2)) + int(m.group(4))) // 2))
PY
)
    fi
    [ -n "$xy" ] && break
    sleep 1
  done
  if [ -n "$xy" ]; then adb shell input tap $xy; echo "tapped $1 at $xy"; else echo "NOT FOUND: $1"; fi
}

# Swipes start mid-screen so they never begin on a slider (a touch there would move it).
scroll_down() { adb shell input swipe 540 1150 540 250 400; sleep 0.5; }
scroll_up() { adb shell input swipe 540 600 540 1800 300; adb shell input swipe 540 600 540 1800 300; }

# Tidy status bar for nicer screenshots.
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1200 >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false >/dev/null
adb shell am broadcast -a com.android.systemui.demo -e command network -e wifi show -e level 4 >/dev/null

adb install -r -g "$APK"
adb logcat -c

if [ -f scripts/ui-flow.sh ]; then
  # shellcheck disable=SC1091
  source scripts/ui-flow.sh
fi

echo "===== CRASH LOG ====="
adb logcat -d -b crash
echo "===== APP ERRORS ====="
adb logcat -d '*:E' | grep -i -E "ordernotifier|AndroidRuntime" | tail -60
echo "===== END ====="
exit 0
