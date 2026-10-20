#!/usr/bin/env bash
# Export full package inventory, disabled packages, getprop, and settings tables from the TV.
# Dumps are saved to backup/ (or specified directory) and kept local.
#
# Usage: TV_HOST=192.168.1.50 tools/backup.sh [output_dir]
set -euo pipefail
cd "$(dirname "$0")/.."

: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0}"
BACKUP_DIR="${1:-backup}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
export MSYS_NO_PATHCONV=1
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt"; exit 1; }

echo "[+] Starting baseline backup for TV at $TV_HOST into $BACKUP_DIR/..."
mkdir -p "$BACKUP_DIR"

# 1. Package inventory
echo "[+] Exporting full package inventory..."
"$PY" -I tools/tv.py 'pm list packages -f -u' > "$BACKUP_DIR/packages_all.txt"

echo "[+] Exporting initially disabled packages..."
"$PY" -I tools/tv.py 'pm list packages -d' > "$BACKUP_DIR/packages_disabled.txt"

# 2. System properties
echo "[+] Exporting system properties (getprop)..."
"$PY" -I tools/tv.py 'getprop' > "$BACKUP_DIR/getprop.txt"

# 3. Settings tables
echo "[+] Exporting system, secure, and global settings..."
"$PY" -I tools/tv.py 'settings list global' > "$BACKUP_DIR/settings_global.txt" || true
"$PY" -I tools/tv.py 'settings list secure' > "$BACKUP_DIR/settings_secure.txt" || true
"$PY" -I tools/tv.py 'settings list system' > "$BACKUP_DIR/settings_system.txt" || true

# Summary check
COUNT_ALL=$(wc -l < "$BACKUP_DIR/packages_all.txt")
COUNT_DIS=$(wc -l < "$BACKUP_DIR/packages_disabled.txt")
echo "[✓] Backup complete! Total packages found: $COUNT_ALL (Disabled: $COUNT_DIS)"
ls -lh "$BACKUP_DIR/"
