#!/usr/bin/env bash
# Re-apply the debloat list (safe to run again). Usage: TV_HOST=192.168.1.104 my-tv/disable.sh
set -euo pipefail
cd "$(dirname "$0")/.."
exec tools/disable.sh my-tv/debloat.txt
