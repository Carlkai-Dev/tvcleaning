#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TV_HOST="${TV_HOST:-192.168.1.82}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=python3
export MSYS_NO_PATHCONV=1

echo "[+] Re-enabling packages on TV at $TV_HOST..."
{
echo "pm enable com.google.android.feedback"
echo "pm enable android.autoinstalls.config.skyworth"
echo "pm enable com.google.android.leanbacklauncher.partnercustomizer"
echo "pm enable com.google.android.tvrecommendations"
echo "pm enable com.smartdevice.common.demonow"
echo "pm enable fusion.android.tv.demo"
echo "pm enable com.coocaa.factory"
echo "pm enable com.mediatek.testpq"
echo "pm enable com.mediatek.tv.factory"
echo "pm enable com.airoha.ble.ota"
echo "pm enable com.Smartdevice.emanual"
echo "pm enable com.mstar.android.tv.disclaimercustomization"
echo "pm enable com.google.android.syncadapters.calendar"
echo "pm enable com.android.providers.calendar"
echo "pm enable com.google.android.boot.appsplashscreen"
echo "pm enable com.vodafone.vodafoneplay"
echo "pm enable com.viacom18.tv.voot"
echo "pm enable com.jio.media.stb.ondemand"
echo "pm enable com.google.android.play.games"
echo "pm enable com.google.android.videos"
  echo "pm enable com.google.android.tvlauncher || true"
  echo "pm enable com.oneplus.tv.launcher || true"
  echo "cmd package set-home-activity --user 0 \$(cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME com.google.android.tvlauncher | tail -n 1) || true"
  echo "pm list packages -d"
} | TV_HOST="$TV_HOST" "$PY" -I tools/tv.py --batch
echo "[✓] Restoration complete."
