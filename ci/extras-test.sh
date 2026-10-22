#!/bin/sh
# Checks the fork's additions on a TV that already runs this home. Unlike smoke-test.sh it never
# runs undo.sh, so it is safe on a real TV.
#   sh ci/extras-test.sh <adb serial or TV_IP> [section]
# Sections run in order: baseline keys wallpaper ambient bell hdmi boot apps banner info perf rotate delete control rows wallscreen wallmenu. Name one to run only that.
# The enabled accessibility services and the wallpaper are put back afterwards. The key map is reset to
# extras.sh's default and hidden HDMI inputs are shown again (the app's settings can't be read from adb).
#   sh ci/extras-test.sh <TV> postboot   right after a real restart, before any script: checks only
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
A11Y=enabled_accessibility_services
a11y() { sh_tv "settings get secure $A11Y"; }

t_keys() {
  # Android drops entries that aren't installed accessibility services, so no fake neighbour here:
  # the long form must be replaced, not doubled.
  sh_tv "settings put secure $A11Y com.ansub.tvhome/com.ansub.tvhome.KeyService"
  sh extras.sh "$TV" >/dev/null || fail "extras.sh failed"
  [ "$(a11y | tr ':' '\n' | grep -c '^com.ansub.tvhome/')" = 1 ] || fail "our service listed twice: $(a11y)"
  sh_tv settings get secure enabled_accessibility_services | grep -q "$ME/.KeyService" || fail "KeyService not enabled"
  sleep 2
  bound=$(sh_tv dumpsys accessibility | grep "Bound services")
  echo "$bound" | grep -q "label=Home" || fail "KeyService not bound"
  echo "$bound" | grep -q "capabilities=8" || fail "KeyService can't filter keys"
  sh extras.sh "$TV" "4021:com.not.installed" >/dev/null
  home
  sh extras.sh "$TV" "" >/dev/null
  a11y | grep -q "com.ansub.tvhome/" && fail "an empty map left the key service on: $(a11y)"
  sh_tv am force-stop $ME; home; sleep 2
  a11y | grep -q "com.ansub.tvhome/" && fail "the home turned the service back on with no map: $(a11y)"
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
  echo GIF89a > "$tmp/tvhome-test.gif"
  sh wallpaper.sh "$TV" "$tmp/tvhome-test.gif" >/dev/null 2>&1 && fail "wallpaper.sh accepted a .gif"
  png 1920 1080 > "$tmp/tvhome-test.png"
  sh wallpaper.sh "$TV" "$tmp/tvhome-test.png" >/dev/null || fail "wallpaper.sh failed"
  home
  shown | grep -q 'wallpaper:image:tvhome-test.png' || fail "image wallpaper not shown: $(shown)"
  sh_tv rm -f /sdcard/Android/data/com.ansub.tvhome/files/wallpapers/tvhome-test.png
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
  sh_tv rm -f /sdcard/Android/data/com.ansub.tvhome/files/wallpapers/tvhome-big.png
  sh_tv am start -n $ME/.MainActivity --es wallpaper "\"$was\"" --es stamp "w$(date +%s)" >/dev/null
  rm -rf "$tmp"
}

ambient_on() { ui | grep -q 'content-desc="ambient"'; }
apps_on() { ui | grep -q 'content-desc="chip"'; } # the page is showing (Settings sits in the peek now)

t_ambient() {
  home
  apps_on || fail "the home page isn't showing before Back"
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

# The bell lives in Control Center's Alerts tile: open it with the cog (83), read, close.
bell() {
  sh_tv input keyevent 83; sleep 3 # the first open after a start blurs the wallpaper for the glass
  d=$(ui | grep -o 'content-desc="Notifications[^"]*"' | head -1)
  sh_tv input keyevent KEYCODE_BACK; sleep 1
  echo "$d"
}

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
  ui | grep -q 'text="Apps"' || fail "Apps tile gone with every input hidden"
  hide none 4
  [ "$(tiles)" -eq "$all0" ] || fail "'none' didn't bring the inputs back"
}

# At boot this TCL wipes the accessibility setting and its boot manager won't bind the notification
# listener, so the home heals both when it starts. This fakes that state without restarting the TV.
t_boot() {
  sh extras.sh "$TV" >/dev/null || fail "extras.sh failed"
  sh_tv settings put secure $A11Y null
  sh_tv settings put secure accessibility_enabled 0
  sh_tv am force-stop $ME
  home; sleep 3
  sh_tv settings get secure enabled_accessibility_services | grep -q "$ME/.KeyService" || fail "home didn't re-enable KeyService"
  sh_tv dumpsys accessibility | grep "Bound services" | grep -q "label=Home" || fail "KeyService not bound after healing"
  bell | grep -q 'Notifications' || fail "bell missing after a restart of the home"
}

# The text of the focused tile or row (its own text, or its first child's).
focused() {
  ui | python3 -c 'import sys,xml.etree.ElementTree as E
r=E.fromstring(sys.stdin.read().split("?>",1)[-1])
for n in r.iter("node"):
  if n.get("focused")=="true":
    t=[m.get("text") for m in n.iter("node") if m.get("text")]
    print(t[0] if t else ""); break'
}

# Bottom row, then right until the Apps tile has focus.
to_apps() {
  home
  for _ in 1 2 3; do sh_tv input keyevent KEYCODE_DPAD_DOWN; done
  for _ in 1 2 3 4 5 6 7; do [ "$(focused)" = Apps ] && return 0; sh_tv input keyevent KEYCODE_DPAD_RIGHT; done
  [ "$(focused)" = Apps ] || fail "couldn't reach the Apps tile"
}

t_apps() {
  to_apps
  sh_tv input keyevent KEYCODE_DPAD_CENTER; sleep 2
  ui | grep -q 'text="Apps"' || fail "Apps panel didn't open"
  ui | grep -q 'text="Disney+"' || fail "Apps panel doesn't list an app that isn't on the home (Disney+)"
  sh_tv input keyevent --longpress KEYCODE_DPAD_CENTER; sleep 2
  ui | grep -qE 'text="(Add to home|Remove from home)"' || fail "hold OK on an app didn't open its menu"
  ui | grep -q 'text="App info"' || fail "app menu has no App info"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
  focus | grep -q $ME || fail "Back from the app menu left the home"
}

# How many pixels of the screen are pure green (the test banner's colour).
green() {
  adb -s "$TV" exec-out screencap | python3 -c 'import sys,struct
d=sys.stdin.buffer.read(); w,h=struct.unpack("<II",d[:8]); px=d[len(d)-w*h*4:]
print(sum(1 for i in range(0,len(px),16) if px[i]<40 and px[i+1]>215 and px[i+2]<40)*4)'
}

t_banner() {
  tmp=$(mktemp -d)
  python3 -c 'import sys,zlib,struct
w,h=640,360
c=lambda t,d:struct.pack(">I",len(d))+t+d+struct.pack(">I",zlib.crc32(t+d))
raw=b"".join(b"\0"+b"\x00\xff\x00"*w for _ in range(h))
sys.stdout.buffer.write(b"\x89PNG\r\n\x1a\n"+c(b"IHDR",struct.pack(">IIBBBBB",w,h,8,2,0,0,0))+c(b"IDAT",zlib.compress(raw))+c(b"IEND",b""))' > "$tmp/b.png"
  sh banner.sh "$TV" "$tmp/b.png" com.google.android.youtube.tv >/dev/null || fail "banner.sh failed"
  home; sleep 2
  [ "$(green)" -gt 20000 ] || fail "the custom banner isn't on screen"
  sh banner.sh "$TV" --reset com.google.android.youtube.tv >/dev/null
  home; sleep 2
  [ "$(green)" -lt 2000 ] || fail "--reset didn't bring back YouTube's own banner"
  sh banner.sh "$TV" "$tmp/b.png" com.not.installed >/dev/null 2>&1 && fail "banner.sh accepted an app that isn't installed"
  sh banner.sh "$TV" "$tmp/b.png" 'x;rm' >/dev/null 2>&1 && fail "banner.sh accepted a bad package name"
  rm -rf "$tmp"
}

# Info on the home opens TCL's signal panel (a TV Settings window over the home).
t_info() {
  home
  sh_tv input keyevent KEYCODE_INFO; sleep 3
  sh_tv dumpsys window | grep -E 'mCurrentFocus|Window #' | grep -q 'com.android.tv.settings' || fail "Info on the home opened no TCL panel"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
}

# Moving through the home must stay smooth: under 10% janky frames and a median frame under 20 ms.
t_perf() {
  home; sleep 3
  sh_tv dumpsys gfxinfo $ME reset >/dev/null
  for _ in 1 2; do
    for _ in 1 2 3 4 5 6; do sh_tv input keyevent KEYCODE_DPAD_RIGHT; sleep 0.35; done
    for _ in 1 2 3 4 5 6; do sh_tv input keyevent KEYCODE_DPAD_LEFT; sleep 0.35; done
  done
  stats=$(sh_tv dumpsys gfxinfo $ME | grep -m1 -A12 'Total frames rendered')
  jank=$(echo "$stats" | sed -n 's/^Janky frames: [0-9]* (\([0-9]*\)\..*/\1/p')
  p50=$(echo "$stats" | sed -n 's/^50th percentile: \([0-9]*\)ms/\1/p')
  echo "   janky ${jank}%, median ${p50} ms"
  [ "${jank:-100}" -lt 10 ] && [ "${p50:-999}" -lt 20 ] || fail "home not smooth: janky ${jank}%, median ${p50} ms"
}

# Rotation moves through the chosen wallpapers only (a 10 s interval, which the TV menu doesn't offer).
t_rotate() {
  home; was=$(shown | sed 's/.*wallpaper:\([^"]*\)"/\1/')
  sh_tv am start -n $ME/.MainActivity --es rotation Dusk,Ocean --es rotate 10s --es stamp "r$(date +%s)" >/dev/null
  home; sleep 12
  shown | grep -q 'wallpaper:Dusk' || fail "rotation didn't move to Dusk: $(shown)"
  sleep 11
  shown | grep -q 'wallpaper:Ocean' || fail "rotation didn't move to Ocean: $(shown)"
  sh_tv am start -n $ME/.MainActivity --es rotate off --es rotation none --es wallpaper "\"$was\"" --es stamp "r0$(date +%s)" >/dev/null
  home; sleep 12
  shown | grep -q "wallpaper:$was" || fail "rotation kept going after Off: $(shown)"
}

# The Wallpaper screen: rotation chips, a thumbnail grid and an action bar, no hidden hold-OK menus.
t_wallscreen() {
  home
  sh_tv am start -n $ME/.MainActivity --es open wallpapers >/dev/null; sleep 3
  x=$(ui)
  echo "$x" | grep -q 'content-desc="wallpaper-screen"' || fail "open=wallpapers didn't show the Wallpaper screen"
  for t in Off "5 min" "10 min" "30 min" "1 hour" "1 week"; do echo "$x" | grep -q "text=\"$t" || fail "Wallpaper screen has no '$t'"; done
  n=$(echo "$x" | grep -o 'content-desc="thumb:' | wc -l | tr -d ' ')
  [ "$n" -ge 10 ] || fail "only $n thumbnails"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
  ui | grep -q 'content-desc="wallpaper-screen"' && fail "Back didn't close the Wallpaper screen"
  true
}

# The real flow, with arrows from the first row: OK on a thumbnail opens its menu, Add to rotation
# puts ↻ on that same thumbnail.
focused_thumb() {
  ui | python3 -c 'import sys,xml.etree.ElementTree as E
r=E.fromstring(sys.stdin.read().split("?>",1)[-1])
for n in r.iter("node"):
  if n.get("focused")=="true" and n.get("content-desc","").startswith("thumb:"):
    print(n.get("content-desc")[6:]); break'
}
badge_of() {
  ui | python3 -c 'import sys,xml.etree.ElementTree as E
r=E.fromstring(sys.stdin.read().split("?>",1)[-1])
for n in r.iter("node"):
  if n.get("content-desc")=="thumb:"+sys.argv[1]:
    print(" ".join(m.get("text","") for m in n.iter("node") if m.get("text"))); break' "$1"
}
t_wallmenu() {
  sh_tv am start -n $ME/.MainActivity --es rotation none --es stamp "wm$(date +%s)" >/dev/null; sleep 1
  sh_tv am start -n $ME/.MainActivity --es open wallpapers >/dev/null; sleep 3
  for _ in 1 2 3 4 5 6 7 8; do sh_tv input keyevent KEYCODE_DPAD_UP; done; sleep 1
  sh_tv input keyevent KEYCODE_DPAD_DOWN; sleep 1
  t=$(focused_thumb); [ -n "$t" ] || fail "Down from the chips didn't land on a thumbnail"
  sh_tv input keyevent KEYCODE_DPAD_CENTER; sleep 1
  ui | grep -q 'text="Add to rotation"' || fail "OK on $t didn't open its menu"
  for _ in 1 2 3; do [ "$(focused)" = "Add to rotation" ] && break; sh_tv input keyevent KEYCODE_DPAD_DOWN; sleep 0.5; done
  [ "$(focused)" = "Add to rotation" ] || fail "couldn't reach Add to rotation"
  sh_tv input keyevent KEYCODE_DPAD_CENTER; sleep 1
  badge_of "$t" | grep -q '↻' || fail "$t didn't get the rotation mark (badge: '$(badge_of "$t")')"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
  sh_tv am start -n $ME/.MainActivity --es rotation none --es stamp "wn$(date +%s)" >/dev/null
}

# Delete from the TV: open the Wallpaper screen on the picture, Down to Delete, confirm.
t_delete() {
  home; was=$(shown | sed 's/.*wallpaper:\([^"]*\)"/\1/')
  tmp=$(mktemp -d); png 640 360 > "$tmp/tvhome-del.png"
  sh wallpaper.sh "$TV" "$tmp/tvhome-del.png" >/dev/null || fail "wallpaper.sh failed"
  home
  sh_tv am start -n $ME/.MainActivity --es open wallpapers >/dev/null; sleep 3
  ui | grep -q 'content-desc="thumb:image:tvhome-del.png' || fail "the pushed picture isn't in the grid"
  [ "$(focused_thumb)" = "image:tvhome-del.png" ] || fail "the Wallpaper screen didn't open on the shown picture ($(focused_thumb))"
  sh_tv input keyevent KEYCODE_DPAD_CENTER; sleep 1
  for _ in 1 2 3 4; do [ "$(focused)" = Delete ] && break; sh_tv input keyevent KEYCODE_DPAD_DOWN; sleep 0.5; done
  [ "$(focused)" = Delete ] || fail "couldn't reach Delete in the picture's menu (focused: $(focused))"
  sh_tv input keyevent KEYCODE_DPAD_CENTER; sleep 2
  ui | grep -q 'Delete tvhome-del.png?' || fail "Delete didn't ask to confirm"
  sh_tv input keyevent KEYCODE_DPAD_CENTER; sleep 2
  sh_tv ls /sdcard/Android/data/$ME/files/wallpapers/ | grep -q tvhome-del.png && fail "the picture is still on the TV"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
  home
  shown | grep -q 'wallpaper:Dusk' || fail "deleting the shown picture didn't fall back to Dusk: $(shown)"
  sh_tv am start -n $ME/.MainActivity --es wallpaper "\"$was\"" --es stamp "d$(date +%s)" >/dev/null
  rm -rf "$tmp"
}

# Apple TV style top: no big clock, one chip (time + avatar); the cog opens Control Center.
t_control() {
  home
  ui | grep -q 'content-desc="chip"' || fail "no chip at the top right"
  ui | grep -q 'content-desc="Network"' && fail "the old icon row is still there"
  sh_tv input keyevent 83; sleep 2
  x=$(ui)
  echo "$x" | grep -q 'content-desc="control-center"' || fail "the cog didn't open Control Center"
  for t in Wi-Fi Bluetooth Sound Picture Inputs Screensaver Alerts Wallpaper Settings Apps; do
    echo "$x" | grep -q "text=\"$t" || fail "Control Center has no $t"
  done
  echo "$x" | grep -qE 'text="[A-Za-z]+, [0-9]{1,2} [A-Za-z]+ 20[0-9]{2}"' || fail "Control Center shows no full date"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
  ui | grep -q 'content-desc="control-center"' && fail "Back didn't close Control Center"
  sh_tv am start -n $ME/.MainActivity --es open control >/dev/null; sleep 2
  ui | grep -q 'content-desc="control-center"' || fail "open=control didn't open Control Center"
  sh_tv input keyevent KEYCODE_BACK; sleep 1
}

# Apple TV rows: the second row only peeks at the bottom edge until Down reveals it; Up hides it again.
settings_top() {
  ui | python3 -c 'import sys,re
x=sys.stdin.read(); m=re.search(r"text=\"Settings\"[^>]*bounds=\"\[\d+,(\d+)\]\[\d+,(\d+)\]", x)
print(m.group(1) if m else -1)'
}
t_rows() {
  home; sleep 1
  top=$(settings_top); [ "$top" -ge 980 ] || [ "$top" -lt 0 ] || fail "second row isn't tucked at the bottom (Settings label at y=$top)"
  sh_tv input keyevent KEYCODE_DPAD_DOWN; sleep 1
  top=$(settings_top); [ "$top" -gt 0 ] && [ "$top" -lt 1000 ] || fail "Down didn't reveal the second row (Settings label at y=$top)"
  sh_tv input keyevent KEYCODE_DPAD_UP; sleep 1
  top=$(settings_top); [ "$top" -ge 980 ] || [ "$top" -lt 0 ] || fail "Up didn't tuck the second row away (Settings label at y=$top)"
}

# Right after a real restart: the remap service and the bell must be back on their own.
t_postboot() {
  a11y | grep -q "com.ansub.tvhome/" || fail "KeyService not enabled after the restart: $(a11y)"
  sh_tv dumpsys accessibility | grep "Bound services" | grep -q "label=Home" || fail "KeyService not bound after the restart"
  home
  bell | grep -q 'Notifications' || fail "bell missing after the restart"
}
if [ "$ONLY" = postboot ]; then echo "== postboot"; t_postboot; echo PASS; exit 0; fi

saved=$(a11y)
trap 'sh_tv "settings put secure $A11Y '"'"'$saved'"'"'" >/dev/null 2>&1' EXIT
sh_tv logcat -b crash -c
for t in baseline keys wallpaper ambient bell hdmi boot apps banner info perf rotate delete control rows wallscreen wallmenu; do
  [ -z "$ONLY" ] || [ "$ONLY" = "$t" ] || continue
  command -v "t_$t" >/dev/null 2>&1 || type "t_$t" >/dev/null 2>&1 || continue
  echo "== $t"
  "t_$t"
  crashed && fail "$ME crashed during $t"
  echo "   ok"
done
echo PASS
