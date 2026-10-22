#!/bin/sh
set -e
cd "$(dirname "$0")"
TARGET="$1"; IN="$2"
[ -n "$TARGET" ] && [ -f "$IN/settings.json" ] || { echo "Usage: sh restore.sh <TV_IP> <backup folder with settings.json>" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 1; }

FILES=/sdcard/Android/data/com.ansub.tvhome/files
adb -s "$TV" shell "[ -d $FILES ] || { am start -n com.ansub.tvhome/.MainActivity >/dev/null; sleep 2; }"
for d in wallpapers banners sounds; do
  [ -d "$IN/$d" ] && adb -s "$TV" push "$IN/$d" $FILES/ >/dev/null
done
for a in "$IN"/avatar.*; do [ -f "$a" ] && adb -s "$TV" push "$a" $FILES/ >/dev/null; done
adb -s "$TV" push "$IN/settings.json" $FILES/settings.json >/dev/null
adb -s "$TV" shell "find $FILES -maxdepth 2 -type f ! -path '*/online/*' -exec chmod 666 {} +"
adb -s "$TV" shell am start -n com.ansub.tvhome/.MainActivity --es settings import --es stamp "r$(date +%s)" >/dev/null
echo "Restored $IN"
