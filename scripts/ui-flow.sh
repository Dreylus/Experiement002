# The screens to visit. Sourced by ui-screenshots.sh (shot, tap_id, scroll_* are available).
open_tab() { adb shell am start -W -n $PKG/.MainActivity --es tab "$1" >/dev/null; sleep 1; }

# Dark mode, like the phone this is built for.
adb shell cmd uimode night yes
sleep 2

open_tab home
shot 01_home_empty 3

# Change the "from" text the way a user would: Customize -> From text -> type -> Save.
open_tab customize
tap_id rowFromText
sleep 1
for i in $(seq 1 15); do adb shell input keyevent KEYCODE_DEL; done
adb shell input text "Cool%sStore"
shot 02_edit_from_text 2
tap_id action_save
sleep 1
shot 03_customize 2
scroll_down
shot 04_customize_bottom 2
scroll_up

# Real notifications: chat style (the default), then classic.
open_tab home
tap_id testNow
sleep 2
open_tab alerts
tap_id rowSinglePicture
open_tab home
tap_id testNow
sleep 2
adb shell cmd statusbar expand-notifications
shot 05_notification_shade 3
tap_id expand_button
shot 06_notification_expanded 2
adb shell cmd statusbar collapse
sleep 1
open_tab alerts
tap_id rowSinglePicture

# Pop-up banner over the home screen.
open_tab home
tap_id testDelayed
adb shell input keyevent KEYCODE_HOME
shot 07_heads_up 5.8

open_tab home
tap_id testNow
sleep 1.5
tap_id testNow
shot 08_home_orders 7
tap_id startStop
shot 09_home_live 2
tap_id startStop
sleep 1

open_tab customize
tap_id editPicture
shot 10_picture_sheet 2
adb shell input keyevent KEYCODE_BACK
sleep 1

open_tab timing
shot 11_timing 2

open_tab alerts
shot 12_alerts 2
scroll_down
shot 13_alerts_bottom 2
scroll_up

# Light mode recreates the screen; every setting must survive that.
adb shell cmd uimode night no
sleep 3
open_tab home
shot 14_home_light 2
open_tab alerts
shot 15_alerts_light 2
