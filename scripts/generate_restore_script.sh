#!/usr/bin/env bash
# ==============================================================================
# Generate dynamic restore script from a specific debloat file or disabled list
# ==============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."

DEBLOAT_LIST="${1:-examples/oneplus-tv-y1/debloat.txt}"
OUTPUT_SCRIPT="${2:-tools/restore_oneplus_y1.sh}"

echo "[+] Generating customized restore script: $OUTPUT_SCRIPT from $DEBLOAT_LIST..."
cat << 'EOF' > "$OUTPUT_SCRIPT"
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TV_HOST="${TV_HOST:-192.168.1.82}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=python3
export MSYS_NO_PATHCONV=1

echo "[+] Re-enabling packages on TV at $TV_HOST..."
{
EOF

if [ -f "$DEBLOAT_LIST" ]; then
    grep -vE '^\s*(#|$)' "$DEBLOAT_LIST" | sed -E 's/\s+#.*$//' | awk '{print "echo \"pm enable " $1 "\""}' >> "$OUTPUT_SCRIPT"
fi

cat << 'EOF' >> "$OUTPUT_SCRIPT"
  echo "pm enable com.google.android.tvlauncher || true"
  echo "pm enable com.oneplus.tv.launcher || true"
  echo "cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME com.google.android.tvlauncher | tail -n 1) || true"
  echo "pm list packages -d"
} | TV_HOST="$TV_HOST" "$PY" -I tools/tv.py --batch
echo "[✓] Restoration complete."
EOF

chmod +x "$OUTPUT_SCRIPT"
echo "[✓] Restore script generated and marked executable: $OUTPUT_SCRIPT"
