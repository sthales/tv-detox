#!/bin/sh
# Sends a picture to the TV and makes it the home screen's wallpaper. It also shows up in
# Settings > Wallpaper next to the gradients. JPG, PNG or WebP.
#   sh wallpaper.sh <TV_IP> my-art.jpg
set -e
TARGET="$1"; IMG="$2"
[ -n "$TARGET" ] && [ -f "$IMG" ] || { echo "Usage: sh wallpaper.sh <TV_IP> <image file>" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 1; }

NAME=$(basename "$IMG" | tr -c 'A-Za-z0-9._\n-' '_')
DIR=/sdcard/Pictures/tvhome
adb -s "$TV" shell "mkdir -p $DIR"
adb -s "$TV" push "$IMG" "$DIR/$NAME" >/dev/null
if [ "$(adb -s "$TV" shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]; then
  adb -s "$TV" shell pm grant com.ansub.tvhome android.permission.READ_MEDIA_IMAGES
else
  adb -s "$TV" shell pm grant com.ansub.tvhome android.permission.READ_EXTERNAL_STORAGE
fi
adb -s "$TV" shell am start -n com.ansub.tvhome/.MainActivity --es wallpaper "\"image:$NAME\"" --es stamp "w$(date +%s)" >/dev/null
echo "Wallpaper: $NAME"
