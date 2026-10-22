#!/bin/sh
# Sends a picture to the TV and makes it the home screen's wallpaper. It also shows up in
# Settings > Wallpaper next to the gradients. JPG, PNG or WebP.
#   sh wallpaper.sh <TV_IP> my-art.jpg
set -e
TARGET="$1"; IMG="$2"
[ -n "$TARGET" ] && [ -f "$IMG" ] || { echo "Usage: sh wallpaper.sh <TV_IP> <image file>" >&2; exit 1; }
case "$(printf '%s' "$IMG" | tr 'A-Z' 'a-z')" in
  *.jpg|*.jpeg|*.png|*.webp) ;;
  *) echo "Use a JPG, PNG or WebP picture; the home can't show $(basename "$IMG")." >&2; exit 1 ;;
esac
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 1; }

NAME=$(basename "$IMG" | tr -c 'A-Za-z0-9._\n-' '_')
# The home's own folder: it creates it when it starts, and may delete pictures in it from the TV.
DIR=/sdcard/Android/data/com.ansub.tvhome/files/wallpapers
adb -s "$TV" shell "[ -d $DIR ] || { am start -n com.ansub.tvhome/.MainActivity >/dev/null; sleep 2; }"
adb -s "$TV" push "$IMG" "$DIR/$NAME" >/dev/null
adb -s "$TV" shell chmod 666 "$DIR/$NAME"
adb -s "$TV" shell am start -n com.ansub.tvhome/.MainActivity --es wallpaper "\"image:$NAME\"" --es stamp "w$(date +%s)" >/dev/null
echo "Wallpaper: $NAME"
