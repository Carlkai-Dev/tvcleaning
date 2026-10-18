#!/usr/bin/env bash
# Undo everything done to this TV: re-enable every package in debloat.txt (including the stock
# Google launcher and PatchWall), make the stock launcher the default home again, reset animations.
# Usage: TV_HOST=192.168.1.104 my-tv/restore.sh
set -euo pipefail
cd "$(dirname "$0")/.."
exec tools/restore.sh my-tv/debloat.txt com.google.android.tvlauncher
