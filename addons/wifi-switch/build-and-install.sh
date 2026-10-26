#!/usr/bin/env bash
# Build Wi-Fi switch and install it on the TV. It lets the launcher's Wi-Fi drop-down (wifi_menu) really switch
# Wi-Fi and join networks; turn that on with "wifi_helper": "io.github.cleanyourtv.wifiswitch" in a theme's
# config.json (see this folder's README).
#
# Usage: TV_HOST=192.168.1.50 addons/wifi-switch/build-and-install.sh
#        TV_HOST=192.168.1.50 addons/wifi-switch/build-and-install.sh --uninstall
# Try it without the launcher (adb only; switching Wi-Fi off also drops ADB over Wi-Fi):
#        TV_HOST=192.168.1.50 tools/tv.py 'am broadcast -n io.github.cleanyourtv.wifiswitch/.SwitchReceiver -a io.github.cleanyourtv.wifi.SET --ez on true'
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$HERE/../../tools"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY="$TOOLS/venv/bin/python"
[ -x "$PY" ] || PY="$TOOLS/venv/Scripts/python.exe"
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.cleanyourtv.wifiswitch
tv() { "$PY" -I "$TOOLS/tv.py" "$@"; }

if [ "${1:-}" = --uninstall ]; then
  tv "pm uninstall $PKG"
  exit 0
fi

(cd "$HERE" && ./gradlew assembleRelease -q) || { echo "build failed (JDK 17 and Android SDK 34 needed; see launcher/README)" >&2; exit 1; }
"$PY" -I "$TOOLS/b64push.py" "$HERE/app/build/outputs/apk/release/app-release.apk" /data/local/tmp/wifiswitch.apk >/dev/null
tv "pm install -r /data/local/tmp/wifiswitch.apk; rm -f /data/local/tmp/wifiswitch.apk"
echo "Installed. Set \"wifi_menu\": true and \"wifi_helper\": \"$PKG\" in your theme, then push it with tools/theme.sh."
