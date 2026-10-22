#!/bin/sh
# Turns on the fork's extras on a TV that runs this home: remote buttons that open apps, and the
# notification bell.
#   sh extras.sh <TV_IP>                           TCL button opens Stremio; Netflix, Prime Video,
#                                                  YouTube and TV (Live TV) work again, the cog opens
#                                                  Control Center
#                                                  work again with Google TV's home off
#   sh extras.sh <TV_IP> "4020:com.stremio.one"    key code:package, comma separated
#   sh extras.sh <TV_IP> ""                        no remapped buttons, the service is turned off
# Run it again after reinstalling the home.
set -e
TARGET="$1"; KEYS="${2-4020:com.stremio.one,4062:com.netflix.ninja,4099:com.amazon.amazonvideo.livingroom,190:com.google.android.youtube.tv,4001:com.tcl.channelplus/com.tcl.iptv.service.InputService,83:@control}"
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
# service gets no keys until the setting changes. Our entry may be in short or long form; other
# apps' entries are kept exactly, so the values are single-quoted for the TV's shell ($ in names).
others=$(sh_tv settings get secure enabled_accessibility_services | tr ':' '\n' | grep -v '^com\.ansub\.tvhome/' | grep -vx null | paste -sd: -)
sh_tv "settings put secure enabled_accessibility_services '${others:-null}'"
[ -z "$KEYS" ] || sh_tv "settings put secure enabled_accessibility_services '${others:+$others:}$SVC'"
[ -z "$KEYS" ] || sh_tv settings put secure accessibility_enabled 1
# TCL's boot manager stops apps without "Auto Launch" from starting services at boot (Android 14
# names the op AUTO_START, older TCLs APP_AUTO_START; other TVs have neither, which is fine).
sh_tv appops set com.ansub.tvhome AUTO_START allow >/dev/null 2>&1 || sh_tv appops set com.ansub.tvhome APP_AUTO_START allow >/dev/null 2>&1 || true
# This TCL wipes the accessibility setting at boot; this lets the home turn the service back on.
sh_tv pm grant com.ansub.tvhome android.permission.WRITE_SECURE_SETTINGS
# The bell on the home screen reads notifications.
sh_tv cmd notification allow_listener com.ansub.tvhome/.NotifService

sh_tv am start -n com.ansub.tvhome/.MainActivity --es keys "\"$KEYS\"" --es stamp "k$(date +%s)" >/dev/null
echo "Remote buttons: ${KEYS:-none}"
