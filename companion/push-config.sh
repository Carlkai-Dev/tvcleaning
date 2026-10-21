#!/usr/bin/env bash
# Put a config.json (server URLs, API keys, screensaver options) into the companion's private storage on the
# TV. The file passes through /data/local/tmp for a moment and is deleted there right after; the app keeps
# it where no other app, and no file browser, can read it.
#
# Usage: TV_HOST=192.168.1.50 companion/push-config.sh companion/config.json
#        TV_HOST=192.168.1.50 companion/push-config.sh --ca root.crt    # only the extra CA, settings untouched
#        TV_HOST=192.168.1.50 companion/push-config.sh --reset          # delete config.json from the TV
#        TV_HOST=192.168.1.50 companion/push-config.sh --reset-ca       # delete the extra CA from the TV
set -euo pipefail
USAGE="usage: push-config.sh <config.json> | --ca <root.crt> | --reset | --reset-ca"
MODE=config
case "${1:?$USAGE}" in
  --ca) MODE=ca; ARG="${2:?$USAGE}" ;;
  *) ARG="$1" ;;
esac
case "$ARG" in --reset|--reset-ca|/*) ;; *) ARG="$(pwd -W 2>/dev/null || pwd)/$ARG" ;; esac
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST to the TV IP address}"
PY=../tools/venv/bin/python
[ -x "$PY" ] || PY=../tools/venv/Scripts/python.exe
[ -x "$PY" ] || { echo "run: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
BASE=content://io.github.epochcoder.homelab.config
URI=$BASE/config.json
TMP=/data/local/tmp/homelab-config.json

if [ "$ARG" = --reset ]; then
  "$PY" -I ../tools/tv.py "content delete --uri $URI; echo deleted"
  exit 0
fi
if [ "$ARG" = --reset-ca ]; then
  "$PY" -I ../tools/tv.py "content delete --uri $BASE/ca.pem; echo deleted"
  exit 0
fi
[ -f "$ARG" ] || { echo "no such file: $ARG" >&2; exit 1; }

if [ "$MODE" = ca ]; then
  # Caddy's tls internal chain is root > intermediate > site. Only the root lasts (ten years); the
  # intermediate is replaced every few days, so trusting it would break within a week. Refuse anything that
  # is not a CA, and say which certificate this is so it can be checked against Caddy's.
  "$PY" -I - "$ARG" <<'EOF' || exit 1
import sys
from cryptography import x509
from cryptography.hazmat.primitives import hashes
data = open(sys.argv[1], "rb").read()
try:
    certs = x509.load_pem_x509_certificates(data)
except ValueError as e:
    sys.exit(f"{sys.argv[1]} is not a PEM certificate: {e}")
for c in certs:
    try:
        ca = c.extensions.get_extension_for_class(x509.BasicConstraints).value.ca
    except x509.ExtensionNotFound:
        ca = False
    print(f"  {c.subject.rfc4514_string()}")
    print(f"    valid until {c.not_valid_after_utc:%Y-%m-%d}, SHA-256 {c.fingerprint(hashes.SHA256()).hex(':')}")
    if not ca:
        sys.exit("  not a CA certificate: use Caddy's root.crt (/data/caddy/pki/authorities/local/root.crt)")
    if c.subject != c.issuer:
        sys.exit("  an intermediate, not the root: Caddy replaces it every few days. Use root.crt.")
EOF
  "$PY" -I ../tools/tv.py --push "$ARG" /data/local/tmp/homelab-ca.pem >/dev/null
  "$PY" -I ../tools/tv.py "content write --uri $BASE/ca.pem < /data/local/tmp/homelab-ca.pem; s=\$?; rm -f /data/local/tmp/homelab-ca.pem; [ \$s = 0 ] && echo pushed || echo 'content write failed: is the companion installed?'"
  exit 0
fi
# Refuse a file the app would only half understand: bad JSON, or a url that is not http(s).
"$PY" -I - "$ARG" <<'EOF' || exit 1
import json, sys
try:
    cfg = json.load(open(sys.argv[1], encoding="utf-8"))
except ValueError as e:
    sys.exit(f"{sys.argv[1]} is not valid JSON: {e}")
if not isinstance(cfg, dict):
    sys.exit("config.json must be an object")
for name in ("immich", "homepage", "sonarr", "radarr", "qbittorrent"):
    url = (cfg.get(name) or {}).get("url", "")
    if url and not url.lower().startswith(("http://", "https://")):
        sys.exit(f"{name}.url must start with http:// or https://, got {url!r}")
    print(f"  {name:9} {url or '(not set)'}")
pem = cfg.get("ca_pem", "")
if pem and "BEGIN CERTIFICATE" not in pem:
    sys.exit("ca_pem does not look like a PEM certificate")
EOF
"$PY" -I ../tools/tv.py --push "$ARG" "$TMP" >/dev/null
"$PY" -I ../tools/tv.py "content write --uri $URI < $TMP; s=\$?; rm -f $TMP; [ \$s = 0 ] && echo pushed || echo 'content write failed: is the companion installed?'"
