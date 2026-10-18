#!/usr/bin/env bash
# ==============================================================================
# Automated Visual Verification Script
# Injects remote key events via ADB and pulls high-resolution screenshots.
# ==============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

TV_HOST="${TV_HOST:-192.168.1.82}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=python3
mkdir -p screenshots

echo "[+] Step 1: Navigating to Home screen..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'input keyevent 3'
sleep 2

echo "[+] Step 2: Capturing 01_home.png..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'screencap -p /sdcard/s1.png'
TV_HOST="$TV_HOST" "$PY" tools/tv.py --pull /sdcard/s1.png screenshots/01_home.png
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'rm -f /sdcard/s1.png'

echo "[+] Step 3: Navigating Down into 4-column application grid..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'input keyevent 20' # KEYCODE_DPAD_DOWN
sleep 2

echo "[+] Step 4: Capturing 02_grid.png..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'screencap -p /sdcard/s2.png'
TV_HOST="$TV_HOST" "$PY" tools/tv.py --pull /sdcard/s2.png screenshots/02_grid.png
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'rm -f /sdcard/s2.png'

echo "[+] Step 5: Navigating Up to top bar and opening Inputs dropdown..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'input keyevent 19' # KEYCODE_DPAD_UP
sleep 1
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'input keyevent 19' # KEYCODE_DPAD_UP
sleep 1
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'input keyevent 23' # KEYCODE_DPAD_CENTER
sleep 2

echo "[+] Step 6: Capturing 03_inputs.png..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'screencap -p /sdcard/s3.png'
TV_HOST="$TV_HOST" "$PY" tools/tv.py --pull /sdcard/s3.png screenshots/03_inputs.png
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'rm -f /sdcard/s3.png'

echo "[+] Step 7: Dismissing inputs popup (Back)..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'input keyevent 4' # KEYCODE_BACK

echo "[✓] Visual verification capture sequence complete! Check screenshots/ directory."
ls -lh screenshots/
