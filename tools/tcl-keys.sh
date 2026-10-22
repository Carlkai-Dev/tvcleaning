#!/usr/bin/env bash
# Hand TCL remote buttons to the TV's own key settings, so they work in any app with no key helper bound and a
# held volume key keeps ramping (#763). The launcher must be installed first; see tcl_keys.py for how it works.
#
# Usage: TV_HOST=192.168.1.50 tools/tcl-keys.sh 4062 4065 4099 83   # the TV answers these from now on
#        TV_HOST=192.168.1.50 tools/tcl-keys.sh --off               # take them back (before uninstalling)
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address, e.g. TV_HOST=192.168.1.50 $0 4062 4065}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
exec "$PY" -I tcl_keys.py "$@"
