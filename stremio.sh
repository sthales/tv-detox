#!/bin/sh
set -e
cd "$(dirname "$0")"
TARGET="$1"
[ -n "$TARGET" ] || { echo "Usage: sh stremio.sh <TV_IP> [off]" >&2; exit 1; }
case "$TARGET" in
  *.*.*.*:*) TV=$TARGET ;;
  *.*.*.*)   TV=$TARGET:5555 ;;
  *)         TV=$TARGET ;;
esac
case "$TV" in *:*) adb connect "$TV" >/dev/null 2>&1 || true ;; esac
[ "$(adb -s "$TV" get-state 2>/dev/null)" = device ] || { echo "Can't reach $TV." >&2; exit 1; }

send() { adb -s "$TV" shell am start -n com.ansub.tvhome/.MainActivity --es stremio_key "$1" --es stamp "st$(date +%s)" >/dev/null; }

if [ "$2" = off ]; then
  send off
  echo "Stremio unlinked; the shelf shows Popular only."
  exit 0
fi

printf "Stremio email: "; read -r STREMIO_EMAIL
trap 'stty echo' EXIT INT
printf "Stremio password: "; stty -echo; read -r STREMIO_PASSWORD; stty echo; echo
export STREMIO_EMAIL STREMIO_PASSWORD
KEY=$(python3 -I -c '
import json, os, sys, urllib.request
body = json.dumps({"type": "Login", "email": os.environ["STREMIO_EMAIL"], "password": os.environ["STREMIO_PASSWORD"], "facebook": False}).encode()
req = urllib.request.Request("https://api.strem.io/api/login", body, {"Content-Type": "application/json"})
r = json.load(urllib.request.urlopen(req, timeout=20))
if "error" in r: sys.exit("Stremio said: " + r["error"].get("message", "login failed"))
print(r["result"]["authKey"])
')
unset STREMIO_PASSWORD
[ -n "$KEY" ] || exit 1
send "$KEY"
echo "Linked. Press Up on the Stremio tile to see Continue watching."
