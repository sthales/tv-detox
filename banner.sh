#!/bin/sh
# Gives an app's tile on the home screen your own picture (16:9 looks best). JPG, PNG or WebP.
#   sh banner.sh <TV_IP> my-art.png com.stremio.one
#   sh banner.sh <TV_IP> --reset com.stremio.one        back to the app's own banner
set -e
TARGET="$1"; IMG="$2"; PKG="$3"
usage() { echo "Usage: sh banner.sh <TV_IP> <image file> <package>  |  sh banner.sh <TV_IP> --reset <package>" >&2; exit 1; }
[ -n "$TARGET" ] && [ -n "$PKG" ] || usage
case "$PKG" in *[!A-Za-z0-9._]*) echo "Not a package name: $PKG" >&2; exit 1 ;; esac
if [ "$IMG" != --reset ]; then
  [ -f "$IMG" ] || usage
  EXT=$(printf '%s' "${IMG##*.}" | tr 'A-Z' 'a-z')
  case "$EXT" in jpg|jpeg|png|webp) ;; *) echo "Use a JPG, PNG or WebP picture." >&2; exit 1 ;; esac
fi
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 1; }
sh_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
sh_tv pm list packages "$PKG" | grep -qx "package:$PKG" || { echo "$PKG isn't installed on the TV." >&2; exit 1; }

DIR=/sdcard/Pictures/tvhome/banners
sh_tv "mkdir -p $DIR; rm -f $DIR/$PKG.jpg $DIR/$PKG.jpeg $DIR/$PKG.png $DIR/$PKG.webp"
if [ "$IMG" != --reset ]; then
  adb -s "$TV" push "$IMG" "$DIR/$PKG.$EXT" >/dev/null
  if [ "$(sh_tv getprop ro.build.version.sdk)" -ge 33 ]; then
    sh_tv pm grant com.ansub.tvhome android.permission.READ_MEDIA_IMAGES
  else
    sh_tv pm grant com.ansub.tvhome android.permission.READ_EXTERNAL_STORAGE
  fi
fi
sh_tv am start -n com.ansub.tvhome/.MainActivity --es banner "$PKG" --es stamp "b$(date +%s)" >/dev/null
[ "$IMG" = --reset ] && echo "Banner for $PKG: the app's own" || echo "Banner for $PKG: $(basename "$IMG")"
