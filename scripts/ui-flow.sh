# The screens to visit. Sourced by ui-screenshots.sh (shot, tap_id, scroll_* are available).
adb shell am start -W -n $PKG/.MainActivity
shot 01_main 4
tap_id testNow; sleep 1; tap_id testNow; sleep 1; tap_id testNow
shot 02_after_tests 2
adb shell cmd statusbar expand-notifications
shot 03_notification_shade 2
adb shell cmd statusbar collapse
tap_id testDelayed
adb shell input keyevent KEYCODE_HOME
shot 04_heads_up 5.7
scroll_down
