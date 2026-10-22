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
