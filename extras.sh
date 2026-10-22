#!/bin/sh
# Turns on the fork's extras on a TV that runs this home: remote buttons that open apps.
#   sh extras.sh <TV_IP>                           TCL button opens Stremio; Netflix, Prime Video and
#                                                  YouTube buttons work again with Google TV's home off
#   sh extras.sh <TV_IP> "4020:com.stremio.one"    key code:package, comma separated
# Run it again after reinstalling the home.
set -e
TARGET="$1"; KEYS="${2:-4020:com.stremio.one,4062:com.netflix.ninja,4099:com.amazon.amazonvideo.livingroom,190:com.google.android.youtube.tv}"
[ -n "$TARGET" ] || { echo "Usage: sh extras.sh <TV_IP> [\"keycode:package,...\"]" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 1; }
sh_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }

SVC=com.ansub.tvhome/.KeyService
# Written fresh every time: after the home is reinstalled Android keeps the old binding and the
# service gets no keys until the setting changes.
others=$(sh_tv settings get secure enabled_accessibility_services | tr ':' '\n' | grep -vx "$SVC" | grep -vx null | paste -sd: -)
sh_tv settings put secure enabled_accessibility_services "${others:-null}"
sh_tv settings put secure enabled_accessibility_services "${others:+$others:}$SVC"
sh_tv settings put secure accessibility_enabled 1

sh_tv am start -n com.ansub.tvhome/.MainActivity --es keys "\"$KEYS\"" --es stamp "$(date +%s)" >/dev/null
echo "Remote buttons: $KEYS"
