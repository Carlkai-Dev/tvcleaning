#!/usr/bin/env bash
# Build the Homelab companion and install it on the TV. It sits next to the launcher as three grid tiles
# (Homelab, Media, Homelab Settings) plus a screensaver; it never touches Home or any other app.
#
# Usage: TV_HOST=192.168.1.50 companion/build-and-install.sh
#        TV_HOST=192.168.1.50 SCREENSAVER=1 companion/build-and-install.sh   # also select the Immich screensaver
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
TOOLS="$(cd ../tools && pwd)"
PY=$TOOLS/venv/bin/python
[ -x "$PY" ] || PY=$TOOLS/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=io.github.epochcoder.homelab

MIN_SDK=$(sed -n 's/^[[:space:]]*minSdk[[:space:]][[:space:]]*\([0-9]\{1,3\}\)[[:space:]]*$/\1/p' app/build.gradle | head -n 1)
TV_SDK=$("$PY" -I "$TOOLS/tv.py" 'getprop ro.build.version.sdk' 2>/dev/null | tr -dc '0-9' || true)
if [ -n "$TV_SDK" ] && [ "$TV_SDK" -lt "$MIN_SDK" ]; then
  echo "ERROR: the TV runs SDK $TV_SDK, the companion needs $MIN_SDK. Nothing was built or pushed." >&2
  exit 1
fi

# Same JDK and SDK discovery as launcher/build-and-install.sh, short form: JAVA_HOME and ANDROID_HOME win.
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(/usr/libexec/java_home -v 17 2>/dev/null || true)"
  [ -n "$JAVA_HOME" ] || [ ! -d /opt/homebrew/opt/openjdk@17 ] || JAVA_HOME=/opt/homebrew/opt/openjdk@17
  [ -n "$JAVA_HOME" ] || JAVA_HOME="$(java -XshowSettings:properties -version 2>&1 | awk '/java.home/ {print $3}')"
fi
export JAVA_HOME PATH="$JAVA_HOME/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$(for d in ~/Library/Android/sdk ~/Android/Sdk /opt/homebrew/share/android-commandlinetools; do [ -d "$d" ] && echo "$d" && break; done)}"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleRelease -q --console=plain

APK=app/build/outputs/apk/release/app-release.apk
"$PY" -I "$TOOLS/b64push.py" "$APK" /data/local/tmp/homelab.apk
OUT=$("$PY" -I "$TOOLS/tv.py" 'pm install -r /data/local/tmp/homelab.apk; rm -f /data/local/tmp/homelab.apk')
echo "$OUT"
case "$OUT" in *Success*) ;; *) echo "ERROR: pm install failed." >&2; exit 1 ;; esac

if [ "${SCREENSAVER:-0}" = 1 ]; then
  # Print the current choice first, so it can be put back by hand.
  OLD=$("$PY" -I "$TOOLS/tv.py" 'settings get secure screensaver_components' | tr -d '\r')
  echo "Previous screensaver: $OLD"
  echo "  undo: TV_HOST=$TV_HOST tools/tv.py 'settings put secure screensaver_components $OLD'"
  printf '%s\n' \
    "settings put secure screensaver_components $PKG/.PhotoDream" \
    "settings put secure screensaver_enabled 1" \
    "settings get secure screensaver_components" \
    | "$PY" -I "$TOOLS/tv.py" --batch
fi
echo "Installed. Open Homelab Settings on the TV, or push a config.json with companion/push-config.sh."
