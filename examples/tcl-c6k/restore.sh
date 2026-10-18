#!/usr/bin/env bash
# Restore Google TV and its setup/recovery app, not a previous third-party Home.
set -euo pipefail
cd "$(dirname "$0")/../.."
: "${TV_HOST:?Set TV_HOST to the TV IP address}"
PY="${TV_PYTHON:-tools/venv/bin/python}"
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo 'Create tools/venv or set TV_PYTHON to its Python executable.' >&2; exit 1; }
export MSYS_NO_PATHCONV=1

tv() { "$PY" -I tools/tv.py "$1"; }
enable() {
    local result
    result=$(tv "pm enable --user 0 $1")
    printf '%s\n' "$result"
    [[ "$result" == *'new state: enabled'* ]]
}
# Attempt every recovery step even if one fails: setup must not block Home recovery.
failed=0
enable com.google.android.apps.tv.launcherx || failed=1
enable com.google.android.tungsten.setupwraith || failed=1
result=$(tv 'cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity') || failed=1
printf '%s\n' "$result"
[[ "$result" == *Success* ]] || failed=1
tv 'input keyevent KEYCODE_HOME' || failed=1
result=$(tv 'cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME') || failed=1
printf '%s\n' "$result"
[[ "$result" == *com.google.android.apps.tv.launcherx/* ]] || failed=1
sleep 2
result=$(tv 'dumpsys activity activities' | awk '/topResumedActivity=|mResumedActivity:/') || failed=1
printf '%s\n' "$result"
[[ "$result" == *com.google.android.apps.tv.launcherx/* ]] || failed=1
if [[ "$failed" != 0 ]]; then
    echo 'One or more recovery checks failed; keep ADB connected and review the output.' >&2
fi
exit "$failed"
