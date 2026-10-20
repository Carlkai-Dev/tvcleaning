#!/usr/bin/env bash
# Validate a TV debloat profile against CONTRIBUTING.md standards.
#
# Usage: tools/check_debloat.sh <path/to/debloat.txt>
set -euo pipefail
cd "$(dirname "$0")/.."

FILE="${1:?usage: check_debloat.sh <path/to/debloat.txt>}"

if [ ! -f "$FILE" ]; then
  echo "[-] Error: file not found: $FILE" >&2
  exit 1
fi

echo "=== Checking Debloat Profile: $FILE ==="
ERRORS=0
WARNINGS=0

# 1. Header checks
echo "[+] Checking header metadata..."
if ! grep -qiE "Android" "$FILE"; then
  echo "  [!] Missing Android version in header comments"
  WARNINGS=$((WARNINGS + 1))
fi

if ! grep -qiE "Live TV" "$FILE"; then
  echo "  [!] Missing 'Live TV app:' identifier in header comments"
  WARNINGS=$((WARNINGS + 1))
fi

if ! grep -qiE "Stock launcher" "$FILE"; then
  echo "  [!] Missing 'Stock launcher:' identifier in header comments"
  WARNINGS=$((WARNINGS + 1))
fi

# 2. Safety checks (no uninstalls)
echo "[+] Checking for forbidden operations..."
if grep -qiE "pm[[:space:]]+uninstall" "$FILE"; then
  echo "  [-] ERROR: 'pm uninstall' found! Profiles must only disable packages."
  ERRORS=$((ERRORS + 1))
fi

if grep -qiE "rm[[:space:]]+-r" "$FILE"; then
  echo "  [-] ERROR: 'rm' command found in debloat profile."
  ERRORS=$((ERRORS + 1))
fi

# 3. Package listing
echo "[+] Active packages in profile:"
TOTAL_PKGS=0
CURRENT_CATEGORY="General"

while IFS= read -r line || [ -n "$line" ]; do
  # Check for category header
  if [[ "$line" =~ ^#[[:space:]]*---?[[:space:]]*(.+)[[:space:]]*---? ]] || [[ "$line" =~ ^#[[:space:]]+([A-Za-z0-9].+) ]]; then
    header_text="${BASH_REMATCH[1]}"
    if ! [[ "$header_text" =~ ^(Apply|Undo|Everything|Kept|For|Seen|Live|Stock|=|-) ]]; then
      CURRENT_CATEGORY="$header_text"
      echo "  [$CURRENT_CATEGORY]"
    fi
  elif [[ "$line" =~ ^[[:space:]]*([a-zA-Z0-9_\.]+)[[:space:]]*(#.*)?$ ]]; then
    pkg="${BASH_REMATCH[1]}"
    comment="${BASH_REMATCH[2]:-}"
    echo "    - $pkg $comment"
    TOTAL_PKGS=$((TOTAL_PKGS + 1))
  fi
done < "$FILE"

echo "--------------------------------------------------------"
echo "Total packages to disable: $TOTAL_PKGS"
echo "Errors: $ERRORS | Warnings: $WARNINGS"

if [ "$ERRORS" -gt 0 ]; then
  echo "[-] Profile validation FAILED with $ERRORS error(s)."
  exit 1
fi

echo "[✓] Profile conforms to repository contribution standards."
