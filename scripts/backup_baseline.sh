#!/usr/bin/env bash
# ==============================================================================
# Automated Baseline Backup Script for Android TV
# Exports full package inventory, disabled packages, getprop, and settings.
# ==============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

TV_HOST="${TV_HOST:-192.168.1.82}"
PY="tools/venv/bin/python"
[ -x "$PY" ] || PY="python3"

echo "[+] Starting baseline backup for TV at $TV_HOST..."
mkdir -p backup

# 1. Package lists
echo "[+] Exporting full package inventory..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'pm list packages -f -u' > backup/packages_all.txt

echo "[+] Exporting initially disabled packages..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'pm list packages -d' > backup/packages_disabled.txt

# 2. System properties
echo "[+] Exporting system properties (getprop)..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'getprop' > backup/getprop.txt

# 3. Settings tables
echo "[+] Exporting system, secure, and global settings..."
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'settings list global' > backup/settings_global.txt || true
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'settings list secure' > backup/settings_secure.txt || true
TV_HOST="$TV_HOST" "$PY" tools/tv.py 'settings list system' > backup/settings_system.txt || true

# Summary check
COUNT_ALL=$(wc -l < backup/packages_all.txt)
COUNT_DIS=$(wc -l < backup/packages_disabled.txt)
echo "[✓] Backup complete! Total packages found: $COUNT_ALL (Disabled: $COUNT_DIS)"
ls -lh backup/
