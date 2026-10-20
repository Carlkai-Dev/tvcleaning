#!/usr/bin/env bash
# Capture a screenshot from the TV over ADB, or run the verification suite (Home, Grid, Inputs).
#
# Usage:
#   TV_HOST=192.168.1.50 tools/screenshot.sh [output.png]
#   TV_HOST=192.168.1.50 tools/screenshot.sh --suite [output_dir]
set -euo pipefail
cd "$(dirname "$0")/.."

: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }

capture_screen() {
  local dest="$1"
  local tmp="/sdcard/tmp_screen_$$.png"
  echo "[+] Capturing screen to $dest..."
  "$PY" -I tools/tv.py "screencap -p $tmp"
  "$PY" -I tools/tv.py --pull "$tmp" "$dest"
  "$PY" -I tools/tv.py "rm -f $tmp"
}

if [ "${1:-}" = "--suite" ]; then
  SUITE_DIR="${2:-screenshots}"
  mkdir -p "$SUITE_DIR"
  echo "[+] Running visual verification suite for TV at $TV_HOST..."

  echo "[+] 1. Navigating to Home..."
  "$PY" -I tools/tv.py 'input keyevent 3'
  sleep 2
  capture_screen "$SUITE_DIR/01_home.png"

  echo "[+] 2. Navigating Down to app grid..."
  "$PY" -I tools/tv.py 'input keyevent 20'
  sleep 2
  capture_screen "$SUITE_DIR/02_grid.png"

  echo "[+] 3. Opening Inputs dropdown..."
  "$PY" -I tools/tv.py 'input keyevent 19'
  sleep 1
  "$PY" -I tools/tv.py 'input keyevent 19'
  sleep 1
  "$PY" -I tools/tv.py 'input keyevent 23'
  sleep 2
  capture_screen "$SUITE_DIR/03_inputs.png"

  echo "[+] Dismissing Inputs popup..."
  "$PY" -I tools/tv.py 'input keyevent 4'

  echo "[✓] Visual verification suite complete: $SUITE_DIR/"
  ls -lh "$SUITE_DIR"
else
  OUTPUT="${1:-screenshot.png}"
  mkdir -p "$(dirname "$OUTPUT")"
  capture_screen "$OUTPUT"
  echo "[✓] Screenshot saved: $OUTPUT"
fi
