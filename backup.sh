#!/bin/sh
set -e
cd "$(dirname "$0")"
TARGET="$1"; OUT="${2:-backups/$(date +%Y-%m-%d-%H%M%S)}"
[ -n "$TARGET" ] || { echo "Usage: sh backup.sh <TV_IP> [folder]" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 1; }
adb -s "$TV" shell pm path com.ansub.tvhome >/dev/null 2>&1 || { echo "The home isn't installed on $TV; nothing to back up." >&2; exit 1; }

FILES=/sdcard/Android/data/com.ansub.tvhome/files
if adb -s "$TV" shell dumpsys power | grep -q "mWakefulness=Dreaming"; then
  adb -s "$TV" shell input keyevent KEYCODE_WAKEUP
  sleep 2
fi
adb -s "$TV" shell rm -f $FILES/settings.json
adb -s "$TV" shell am start -n com.ansub.tvhome/.MainActivity --es settings export --es stamp "b$(date +%s)" >/dev/null
for _ in 1 2 3 4 5 6 7 8 9 10; do adb -s "$TV" shell ls $FILES/settings.json >/dev/null 2>&1 && break; sleep 1; done
mkdir -p "$OUT"
adb -s "$TV" pull $FILES/settings.json "$OUT/" >/dev/null || { echo "The home didn't export its settings (update it first)." >&2; exit 1; }
for d in wallpapers banners sounds; do
  adb -s "$TV" shell ls $FILES/$d >/dev/null 2>&1 && adb -s "$TV" pull $FILES/$d "$OUT/" >/dev/null
done
for a in $(adb -s "$TV" shell "ls $FILES/avatar.* 2>/dev/null" | tr -d '\r'); do adb -s "$TV" pull "$a" "$OUT/" >/dev/null; done
echo "Backed up to $OUT"
