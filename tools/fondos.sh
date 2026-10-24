#!/usr/bin/env bash
# Fork de Natalia: envía a la TV los fondos de video ya preparados (1080p, en bucle, sin audio) sin necesitar adb:
# usa tools/tv.py y los escribe a través del ThemeProvider del launcher, así el archivo es del launcher y Android 14
# lo deja leerlo. La carpeta trae, por espacio del Centro de control, <id>.mp4 y opcionalmente <id>.jpg (la foto
# del selector). Los id son: gg-sunset, gg-night, sequoia, noche, utah.
#
# Uso: TV_HOST=192.168.1.50 tools/fondos.sh ~/fondos-natalia
set -euo pipefail
SRC="${1:?uso: fondos.sh <carpeta con gg-sunset.mp4, gg-night.mp4, sequoia.mp4, noche.mp4, utah.mp4>}"
SRC="$(cd "$SRC" && pwd)"
cd "$(dirname "$0")"
: "${TV_HOST:?pon TV_HOST con la IP de la TV, p. ej. TV_HOST=192.168.1.50 $0 ~/fondos-natalia}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
[ -x "$PY" ] || { echo "ejecuta: python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt" >&2; exit 1; }
export MSYS_NO_PATHCONV=1
PKG=com.gathin.tvhome
DIR=/sdcard/Android/data/$PKG/files
URI=content://$PKG.theme
TMP=/data/local/tmp/cyt-fondo
tv() { "$PY" -I tv.py "$@"; }
RESTART="a=\$(settings get secure enabled_accessibility_services); am force-stop $PKG; case \"\$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -n 1)\" in $PKG/*) am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null ;; *) am start -n $PKG/.MainActivity >/dev/null ;; esac; case \"\$a\" in *$PKG*) sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1; sleep 1; settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 1; settings put secure enabled_accessibility_services \"\$a\"; settings put secure accessibility_enabled 1 ;; esac"

tv "mkdir -p $DIR/videos" >/dev/null 2>&1 || true
N=0
for ID in gg-sunset gg-night sequoia noche utah; do
  for EXT in mp4 jpg; do
    F="$SRC/$ID.$EXT"
    [ -f "$F" ] || continue
    echo "enviando $ID.$EXT ($(du -h "$F" | cut -f1))..."
    tv --push "$F" "$TMP-$ID.$EXT" >/dev/null
    tv "rm -f $DIR/videos/$ID.$EXT 2>/dev/null; { o=\$(content write --uri $URI/videos/$ID.$EXT < $TMP-$ID.$EXT 2>&1) && case \"\$o\" in *'Unsupported operation'*) false ;; esac; } || cp $TMP-$ID.$EXT $DIR/videos/$ID.$EXT; rm -f $TMP-$ID.$EXT" >/dev/null
    [ "$EXT" = mp4 ] && N=$((N + 1))
  done
done
[ "$N" -gt 0 ] || { echo "no encontré gg-sunset.mp4, gg-night.mp4, sequoia.mp4, noche.mp4 ni utah.mp4 en $SRC" >&2; exit 1; }
tv "$RESTART; echo listo" | tail -n 1
echo "$N fondo(s) en la TV. Elígelos en el Centro de control, en Fondo."
