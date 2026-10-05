# The screens to visit. Sourced by ui-screenshots.sh (shot, tap_id, scroll_* are available).
open_tab() { adb shell am start -W -n $PKG/.MainActivity --es tab "$1" >/dev/null; sleep 1; }

# Dark mode, like the phone this is built for.
adb shell cmd uimode night yes
sleep 2

open_tab home
shot 01_home_empty 3

# Real notifications: one chat-style ("one picture only"), one classic.
tap_id testNow
sleep 2
open_tab alerts
tap_id rowSinglePicture
open_tab home
tap_id testNow
sleep 2
adb shell cmd statusbar expand-notifications
shot 02_notification_shade 3
tap_id expand_button
shot 03_notification_expanded 2
adb shell cmd statusbar collapse
sleep 1
open_tab alerts
tap_id rowSinglePicture

# Pop-up banner over the home screen.
open_tab home
tap_id testDelayed
adb shell input keyevent KEYCODE_HOME
shot 04_heads_up 5.8

open_tab home
tap_id testNow
sleep 1.5
tap_id testNow
shot 05_home_orders 2
tap_id startStop
shot 06_home_live 2
tap_id startStop
sleep 1

open_tab customize
shot 07_customize 2
scroll_down
shot 08_customize_bottom 2
scroll_up
tap_id rowFromText
shot 09_edit_from_text 3
adb shell input keyevent KEYCODE_BACK
sleep 1
adb shell input keyevent KEYCODE_BACK
sleep 1
open_tab customize
tap_id editPicture
shot 10_picture_sheet 2
adb shell input keyevent KEYCODE_BACK
sleep 1

open_tab timing
shot 11_timing 2
scroll_down
shot 12_timing_bottom 2
scroll_up

open_tab alerts
shot 13_alerts 2
scroll_down
shot 14_alerts_bottom 2
scroll_up

adb shell cmd uimode night no
sleep 2
open_tab home
shot 15_home_light 3
