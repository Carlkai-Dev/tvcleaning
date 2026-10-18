#!/usr/bin/env bash
# Rebuild the launcher after editing launcher/app/src/main/res/values/config.xml, then install it.
# Usage: TV_HOST=192.168.1.104 my-tv/build-and-install.sh
set -euo pipefail
cd "$(dirname "$0")/.."
exec launcher/build-and-install.sh
