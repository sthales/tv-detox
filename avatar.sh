#!/bin/sh
# Puts your photo in the home's top-right chip and Control Center (cropped to a circle on the TV).
#   sh avatar.sh <TV_IP> me.jpg              your photo
#   sh avatar.sh <TV_IP> --name "Thales"     your name (and its initial, when there's no photo)
#   sh avatar.sh <TV_IP> --reset             back to the initial on a gradient
set -e
TARGET="$1"; ARG="$2"
[ -n "$TARGET" ] && [ -n "$ARG" ] || { echo "Usage: sh avatar.sh <TV_IP> <photo> | --name <name> | --reset" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 1; }
sh_tv() { adb -s "$TV" shell "$@" </dev/null | tr -d '\r'; }
ME=com.ansub.tvhome
DIR=/sdcard/Android/data/$ME/files
if [ "$ARG" = --name ]; then
  sh_tv am start -n $ME/.MainActivity --es name "\"$3\"" --es stamp "n$(date +%s)" >/dev/null
  echo "Name: $3"; exit 0
fi
sh_tv "[ -d $DIR ] || { am start -n $ME/.MainActivity >/dev/null; sleep 2; }"
sh_tv "rm -f $DIR/avatar.png $DIR/avatar.jpg"
if [ "$ARG" != --reset ]; then
  [ -f "$ARG" ] || { echo "No such file: $ARG" >&2; exit 1; }
  case "$(printf '%s' "${ARG##*.}" | tr 'A-Z' 'a-z')" in
    png) EXT=png ;; jpg|jpeg) EXT=jpg ;; *) echo "Use a JPG or PNG photo." >&2; exit 1 ;;
  esac
  adb -s "$TV" push "$ARG" "$DIR/avatar.$EXT" >/dev/null
  sh_tv chmod 666 "$DIR/avatar.$EXT"
fi
sh_tv am start -n $ME/.MainActivity --es avatar 1 --es stamp "a$(date +%s)" >/dev/null
[ "$ARG" = --reset ] && echo "Avatar: initial" || echo "Avatar: $(basename "$ARG")"
