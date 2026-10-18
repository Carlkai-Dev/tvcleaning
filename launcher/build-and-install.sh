#!/usr/bin/env bash
# Build the launcher APK and install it on the TV as the default home screen.
# The stock launcher is disabled only after the install and the home switch are both verified,
# so the Home button can never fall through to a factory test app.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
STOCK_LAUNCHER="${STOCK_LAUNCHER:-com.google.android.tvlauncher}"
HOME_ACTIVITY=com.gathin.tvhome/.MainActivity
export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17)}"
export ANDROID_HOME="${ANDROID_HOME:-$([ -d ~/Library/Android/sdk ] && echo ~/Library/Android/sdk || echo /opt/homebrew/share/android-commandlinetools)}"
export PATH="$JAVA_HOME/bin:$PATH"
[ -f local.properties ] || echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug -q --console=plain
PY=../tools/venv/bin/python
[ -x "$PY" ] || PY=../tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
APK=app/build/outputs/apk/debug/app-debug.apk
tv() { "$PY" -I ../tools/tv.py "$@"; }

"$PY" -I ../tools/b64push.py "$APK" /data/local/tmp/tvhome.apk

echo "installing..."
tv 'pm install -r /data/local/tmp/tvhome.apk'
tv 'rm -f /data/local/tmp/tvhome.apk' >/dev/null
tv "pm list packages com.gathin.tvhome" | grep -q com.gathin.tvhome || {
  echo "ERROR: the launcher did not install; stock launcher left alone." >&2; exit 1; }

tv 'pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION' >/dev/null 2>&1 || true

echo "setting it as home..."
tv "cmd package set-home-activity --user 0 $HOME_ACTIVITY" >/dev/null
tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' \
  | grep -q com.gathin.tvhome || {
  echo "ERROR: home did not switch to the new launcher; stock launcher left enabled." >&2; exit 1; }

echo "disabling $STOCK_LAUNCHER..."
tv "pm disable-user --user 0 $STOCK_LAUNCHER"
echo "Installed. Press Home on the remote to see it."
