#!/usr/bin/env bash
# Experimental TCL C6K/G08 Android 14 activation. Install and preview the APK first.
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${TV_HOST:?Set TV_HOST to the TV IP address}"
PY="${TV_PYTHON:-tools/venv/bin/python}"
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo 'Create tools/venv or set TV_PYTHON to its Python executable.' >&2; exit 1; }
export TV_PYTHON="$PY" MSYS_NO_PATHCONV=1

tv() { "$PY" -I tools/tv.py "$1"; }
resolve_home() { tv 'cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME'; }
foreground() {
    tv 'dumpsys activity activities' | awk '/topResumedActivity=|mResumedActivity:/'
}

# Limit this workaround to the device/OS combination inspected on a real TV.
[[ "$(tv 'getprop ro.product.device' | tr -d '\r\n')" == G08 ]] || { echo 'Expected TCL G08; no changes made.' >&2; exit 1; }
[[ "$(tv 'getprop ro.build.version.sdk' | tr -d '\r\n')" == 34 ]] || { echo 'Expected Android 14 / SDK 34; no changes made.' >&2; exit 1; }
[[ "$(tv 'am get-current-user' | tr -d '\r\n')" == 0 ]] || { echo 'Only user 0 has been tested; no changes made.' >&2; exit 1; }
for package in com.gathin.tvhome com.google.android.apps.tv.launcherx com.google.android.tungsten.setupwraith; do
    result=$(tv "pm path $package")
    [[ "$result" == package:* ]] || { echo "Missing $package; no changes made." >&2; exit 1; }
done

umask 077
BACKUP="backup/tcl-c6k/$(date +%Y%m%d-%H%M%S)-$$"
mkdir -p "$BACKUP"
tv getprop > "$BACKUP/getprop.txt"
tv 'pm list packages -f' > "$BACKUP/packages.txt"
tv 'pm list packages -d --user 0' > "$BACKUP/disabled-packages.txt"
for namespace in system secure global; do
    tv "settings list $namespace" > "$BACKUP/settings-$namespace.txt"
done
resolve_home > "$BACKUP/home.txt"
echo "Private baseline saved in $BACKUP (do not upload it)."

# A missing/broken APK must never leave the TV without a working Home.
result=$(tv 'am start -W -n com.gathin.tvhome/.MainActivity')
printf '%s\n' "$result"
[[ "$result" == *'Status: ok'* ]] || { echo 'Launcher did not start; no packages disabled.' >&2; exit 1; }
sleep 2
result=$(foreground)
[[ "$result" == *com.gathin.tvhome/* ]] || { echo 'Launcher is not foreground; no packages disabled.' >&2; exit 1; }

changed=0
complete=0
rollback() {
    local status=$?
    if [[ "$changed" == 1 && "$complete" == 0 ]]; then
        echo 'Activation failed; restoring Google TV.' >&2
        if ! bash examples/tcl-c6k/restore.sh; then
            echo 'Automatic restoration failed. Keep ADB connected and run examples/tcl-c6k/restore.sh again.' >&2
        fi
    fi
    return "$status"
}
trap rollback EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
changed=1
for package in com.google.android.tungsten.setupwraith com.google.android.apps.tv.launcherx; do
    result=$(tv "pm disable-user --user 0 $package")
    printf '%s\n' "$result"
    [[ "$result" == *'new state: disabled-user'* ]] || exit 1
done
result=$(tv 'cmd package set-home-activity --user 0 com.gathin.tvhome/.MainActivity')
printf '%s\n' "$result"
[[ "$result" == *Success* ]] || exit 1
for attempt in {1..10}; do
    result=$(resolve_home)
    [[ "$result" == *com.gathin.tvhome/* ]] && break
    sleep 1
done
printf '%s\n' "$result"
[[ "$result" == *com.gathin.tvhome/* ]] || exit 1
tv 'input keyevent KEYCODE_HOME'
sleep 2
result=$(foreground)
printf '%s\n' "$result"
[[ "$result" == *com.gathin.tvhome/* ]] || exit 1
complete=1
echo 'TCL launcher active. Restore with: TV_HOST=<tv-ip> bash examples/tcl-c6k/restore.sh'
