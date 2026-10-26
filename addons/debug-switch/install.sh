#!/usr/bin/env bash
# Build Debug Switch, install it on the TV and let its button switch Wireless debugging on and off.
#   TV_HOST=<tv ip> addons/debug-switch/install.sh
set -euo pipefail
: "${TV_HOST:?set TV_HOST to the TV IP address}"
cd "$(dirname "$0")"
TOOLS="$(cd ../../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
PKG=io.github.gkudva96.debugswitch
# Script paths stay relative: with MSYS_NO_PATHCONV, Git Bash would hand Windows Python a /c/... path.
tv() { "$PY" -I ../../tools/tv.py "$@"; }

[ -f local.properties ] || { [ -n "${ANDROID_HOME:-}" ] && echo "sdk.dir=${ANDROID_HOME//\\//}" > local.properties; } || true
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
./gradlew assembleRelease -q --console=plain

"$PY" -I ../../tools/b64push.py app/build/outputs/apk/release/app-release.apk /data/local/tmp/debug-switch.apk
OUT=$(tv 'pm install -r /data/local/tmp/debug-switch.apk; rm -f /data/local/tmp/debug-switch.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed" >&2; exit 1 ;; esac

# Only this lets the button switch Wireless debugging; without it the screen shows this command.
tv "pm grant $PKG android.permission.WRITE_SECURE_SETTINGS; echo permission granted"
tv "am start -n $PKG/.MainActivity >/dev/null; echo opened"
echo "Installed. Debug Switch is open on the TV; press Home and answer Show card for the card."
