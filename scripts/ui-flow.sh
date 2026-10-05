# The screens to visit. Sourced by ui-screenshots.sh (shot, tap_id, scroll_* are available).
open_tab() { adb shell am start -W -n $PKG/.MainActivity --es tab "$1" >/dev/null; sleep 1; }

# Dark mode, like the phone this is built for.
adb shell cmd uimode night yes
sleep 2
demo_bar

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

# Pop-up banner over the home screen. The order is posted 5 s after the tap; grab a burst of
# frames around then and keep the one whose top part differs most from the plain home screen.
open_tab home
tap_id testDelayed
t0=$(date +%s%N)
adb shell input keyevent KEYCODE_HOME
while [ $(( ($(date +%s%N) - t0) / 1000000 )) -lt 4200 ]; do sleep 0.05; done
adb exec-out screencap -p > /tmp/hu_ref.png
for i in $(seq 0 11); do
  while [ $(( ($(date +%s%N) - t0) / 1000000 )) -lt $((4800 + i * 450)) ]; do sleep 0.05; done
  adb exec-out screencap -p > "/tmp/hu_$i.png"
done
python3 - "$OUT/07_heads_up.png" <<'PY'
import glob, shutil, sys
from PIL import Image, ImageChops
ref = Image.open('/tmp/hu_ref.png').convert('L')
box = (0, 0, ref.width, ref.height // 5)
def diff(f):
    im = Image.open(f).convert('L')
    h = ImageChops.difference(ref.crop(box), im.crop(box)).histogram()
    return sum(i * n for i, n in enumerate(h))
frames = sorted(glob.glob('/tmp/hu_*.png'))
frames = [f for f in frames if not f.endswith('ref.png')]
best = max(frames, key=diff)
print('heads-up frame:', best, diff(best))
shutil.copy(best, sys.argv[1])
PY
echo "screenshot: 07_heads_up"

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
demo_bar
open_tab home
shot 14_home_light 2
open_tab alerts
shot 15_alerts_light 2
