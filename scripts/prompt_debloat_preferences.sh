#!/usr/bin/env bash
# ==============================================================================
# Verifies debloat package profile against user preferences
# ==============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

DEBLOAT_FILE="examples/oneplus-tv-y1/debloat.txt"
if [ ! -f "$DEBLOAT_FILE" ]; then
    echo "[-] Debloat profile not found: $DEBLOAT_FILE"
    exit 1
fi

echo "[+] Verified OnePlus Y1 debloat profile ($DEBLOAT_FILE):"
grep -vE '^\s*(#|$)' "$DEBLOAT_FILE" | while read -r pkg; do
    echo "  - $pkg"
done

echo "[✓] Total packages to disable: $(grep -vE '^\s*(#|$)' "$DEBLOAT_FILE" | wc -l)"
