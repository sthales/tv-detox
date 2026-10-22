#!/bin/sh
# Checks the fork's additions on a TV that already runs this home. Unlike smoke-test.sh it never
# runs undo.sh, so it is safe on a real TV.
#   sh ci/extras-test.sh <adb serial or TV_IP> [section]
# Sections run in order: baseline keys wallpaper ambient bell hdmi. Name one to run only that.
set -e
cd "$(dirname "$0")/.."
TV="$1"; ONLY="$2"
[ -n "$TV" ] || { echo "Usage: sh ci/extras-test.sh <TV> [section]" >&2; exit 1; }
case "$TV" in *.*.*.*:*|*.*.*.*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
ME=com.ansub.tvhome

sh_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
focus() { sh_tv dumpsys window | grep -m1 mCurrentFocus; }
ui() { sh_tv "uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; cat /sdcard/ui.xml"; }
wait_home() { for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15; do focus | grep -q $ME && return 0; sleep 1; done; return 1; }
home() { sh_tv input keyevent KEYCODE_HOME; wait_home || fail "home screen not in front"; sleep 1; }
crashed() { sh_tv logcat -d -b crash | grep -q "Process: $ME"; }
fail() {
  echo "FAIL: $*" >&2
  echo "-- on screen: $(focus)" >&2
  sh_tv logcat -d -b crash | grep -A12 "FATAL EXCEPTION" | head -40 >&2
  exit 1
}

t_baseline() {
  h=$(sh_tv cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
  [ "$h" = "$ME/.MainActivity" ] || fail "default home is $h"
  home
  ui | grep -qE 'text="[0-9]{1,2}:[0-9]{2}"' || fail "no clock"
}

# Keys sent with "input keyevent" skip the accessibility filter, so only a real remote press proves
# the remap. This checks the part a script can see: the service is on, bound and filtering keys.
t_keys() {
  sh extras.sh "$TV" >/dev/null || fail "extras.sh failed"
  sh_tv settings get secure enabled_accessibility_services | grep -q "$ME/.KeyService" || fail "KeyService not enabled"
  sleep 2
  bound=$(sh_tv dumpsys accessibility | grep "Bound services")
  echo "$bound" | grep -q "label=Home" || fail "KeyService not bound"
  echo "$bound" | grep -q "capabilities=8" || fail "KeyService can't filter keys"
  sh extras.sh "$TV" "4021:com.not.installed" >/dev/null
  home
  sh extras.sh "$TV" >/dev/null
}

# A solid colour PNG of w x h, made with python3 so the test needs no image tools.
png() {
  python3 -c 'import sys,zlib,struct
w,h=int(sys.argv[1]),int(sys.argv[2])
c=lambda t,d:struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d))
raw=b"".join(b"\0"+b"\xff\x00\xff"*w for _ in range(h))
sys.stdout.buffer.write(b"\x89PNG\r\n\x1a\n"+c(b"IHDR",struct.pack(">IIBBBBB",w,h,8,2,0,0,0))+c(b"IDAT",zlib.compress(raw))+c(b"IEND",b""))' "$1" "$2"
}
shown() { ui | grep -o 'content-desc="wallpaper:[^"]*"' | head -1; }

t_wallpaper() {
  home; was=$(shown | sed 's/.*wallpaper:\([^"]*\)"/\1/'); was=${was:-Dusk}
  tmp=$(mktemp -d)
  png 1920 1080 > "$tmp/tvhome-test.png"
  sh wallpaper.sh "$TV" "$tmp/tvhome-test.png" >/dev/null || fail "wallpaper.sh failed"
  home
  shown | grep -q 'wallpaper:image:tvhome-test.png' || fail "image wallpaper not shown: $(shown)"
  sh_tv rm -f /sdcard/Pictures/tvhome/tvhome-test.png
  sh_tv am force-stop $ME; home
  shown | grep -q 'wallpaper:Dusk' || fail "missing image didn't fall back to Dusk: $(shown)"
  # The home's own PSS includes about 55 MB of 1080p GPU buffers whatever the wallpaper, so compare
  # a big picture with the gradient instead of using a fixed number.
  pss() { sh_tv am force-stop $ME; home; sleep 3; sh_tv dumpsys meminfo $ME | awk '/TOTAL PSS:/{print $3; exit}'; }
  base=$(pss)
  png 6000 4000 > "$tmp/tvhome-big.png"
  sh wallpaper.sh "$TV" "$tmp/tvhome-big.png" >/dev/null
  big=$(pss)
  shown | grep -q 'wallpaper:image:tvhome-big.png' || fail "big image not shown: $(shown)"
  [ $((big - base)) -lt 20000 ] || fail "a 6000x4000 image adds $((big - base)) KB over the gradient"
  echo "   6000x4000 image: $big KB vs gradient $base KB"
  sh_tv rm -f /sdcard/Pictures/tvhome/tvhome-big.png
  sh_tv am start -n $ME/.MainActivity --es wallpaper "\"$was\"" --es stamp "w$(date +%s)" >/dev/null
  rm -rf "$tmp"
}

ambient_on() { ui | grep -q 'content-desc="ambient"'; }
apps_on() { ui | grep -q 'text="Settings"'; }

t_ambient() {
  home
  apps_on || fail "no Settings tile before Back"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
  ambient_on || fail "Back didn't show the ambient view"
  apps_on && fail "apps still showing in the ambient view"
  sh_tv input keyevent KEYCODE_DPAD_CENTER; sleep 1
  ambient_on && fail "OK didn't leave the ambient view"
  apps_on || fail "apps didn't come back"
  focus | grep -q $ME || fail "OK in the ambient view opened something"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
  ambient_on || fail "second Back didn't show the ambient view"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
  apps_on || fail "Back didn't leave the ambient view"
}

bell() { ui | grep -o 'content-desc="Notifications[^"]*"' | head -1; }

t_bell() {
  sh extras.sh "$TV" >/dev/null || fail "extras.sh failed"
  home
  bell | grep -qE 'Notifications [1-9]' || fail "no bell with notifications: '$(bell)'"
  sh_tv am start -n $ME/.MainActivity >/dev/null; sleep 1
  sh_tv cmd notification post -t "tvhome test" tvhome-test "hello" >/dev/null; sleep 2
  bell | grep -q ' new"' || fail "new notification didn't light the bell: '$(bell)'"
  sh_tv cmd notification post tvhome-empty " " >/dev/null; sleep 2
  focus | grep -q $ME || fail "home lost focus after an empty notification"
  sh_tv cmd notification disallow_listener $ME/.NotifService; sleep 2
  sh_tv am force-stop $ME; home
  [ -z "$(bell)" ] || fail "bell shown without notification access: '$(bell)'"
  sh_tv cmd notification allow_listener $ME/.NotifService; sleep 2
  sh_tv am force-stop $ME; home
  bell | grep -q 'Notifications' || fail "bell didn't come back with access"
}

tiles() { ui | grep -o 'focusable="true"' | wc -l | tr -d ' '; }
hide() { sh_tv am start -n $ME/.MainActivity --es hidden_inputs "\"$1\"" --es stamp "h$(date +%s)$2" >/dev/null; sleep 2; home; }

# Labels can be renamed (HDMI 1 -> PS5), so this counts tiles instead of reading names.
t_hdmi() {
  P=com.tcl.tvinput/.TvPassThroughService/HW
  hide none 0; all0=$(tiles)
  hide "${P}15,${P}16,${P}17,${P}18" 1; none_shown=$(tiles)
  [ "$none_shown" -lt "$all0" ] || fail "hiding every HDMI input removed no tiles ($all0 -> $none_shown)"
  hide "${P}16,${P}17,${P}18" 2
  [ "$(tiles)" -eq $((none_shown + 1)) ] || fail "with three hidden, expected one HDMI tile"
  hide "${P}15,${P}16,${P}17,${P}18,bogus/id" 3
  [ "$(tiles)" -eq "$none_shown" ] || fail "an unknown hidden id changed the layout"
  ui | grep -q 'text="Settings"' || fail "Settings gone with every input hidden"
  ui | grep -q 'text="Add app"' || fail "Add app gone with every input hidden"
  hide none 4
  [ "$(tiles)" -eq "$all0" ] || fail "'none' didn't bring the inputs back"
}

sh_tv logcat -b crash -c
for t in baseline keys wallpaper ambient bell hdmi; do
  [ -z "$ONLY" ] || [ "$ONLY" = "$t" ] || continue
  command -v "t_$t" >/dev/null 2>&1 || type "t_$t" >/dev/null 2>&1 || continue
  echo "== $t"
  "t_$t"
  crashed && fail "$ME crashed during $t"
  echo "   ok"
done
echo PASS
