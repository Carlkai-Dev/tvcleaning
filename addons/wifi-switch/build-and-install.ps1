# Windows version of build-and-install.sh: build Wi-Fi switch and install it on the TV. It lets the launcher's
# Wi-Fi drop-down (wifi_menu) really switch Wi-Fi and join networks; turn that on with
# "wifi_helper": "io.github.cleanyourtv.wifiswitch" in a theme's config.json (see this folder's README).
#
# Usage: addons\wifi-switch\build-and-install.ps1 -TvHost 192.168.1.50
#        addons\wifi-switch\build-and-install.ps1 -TvHost 192.168.1.50 -Uninstall
param(
    [string]$TvHost = $env:TV_HOST,
    [switch]$Uninstall
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
$here = $PSScriptRoot
$tools = Join-Path (Split-Path (Split-Path $here -Parent) -Parent) 'tools'
$py = Join-Path $tools 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'io.github.cleanyourtv.wifiswitch'
function Tv([string]$cmd) { & $py -I (Join-Path $tools 'tv.py') $cmd; if ($LASTEXITCODE) { throw "tv.py failed: $cmd" } }

if ($Uninstall) {
    Tv "pm uninstall $pkg"
    exit 0
}

Push-Location $here
# javac notes go to stderr, which Windows PowerShell 5.1 turns into errors under 'Stop'.
$ErrorActionPreference = 'Continue'
try {
    & .\gradlew.bat assembleRelease -q
} finally {
    Pop-Location
}
if ($LASTEXITCODE) { throw 'build failed (JDK 17 and Android SDK 34 needed; see launcher\README)' }
$ErrorActionPreference = 'Stop'
& $py -I (Join-Path $tools 'b64push.py') (Join-Path $here 'app\build\outputs\apk\release\app-release.apk') /data/local/tmp/wifiswitch.apk | Out-Null
if ($LASTEXITCODE) { throw 'sending the APK failed; run this again' }
Tv 'pm install -r /data/local/tmp/wifiswitch.apk; rm -f /data/local/tmp/wifiswitch.apk'
Write-Host "Installed. Set wifi_menu true and wifi_helper $pkg in your theme, then push it with tools\theme.ps1."
