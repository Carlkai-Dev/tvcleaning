# Windows version of tcl-keys.sh: hand TCL remote buttons to the TV's own key settings, so they work in any app
# with no key helper bound and a held volume key keeps ramping (#763). See tcl_keys.py.
#
# Usage: tools\tcl-keys.ps1 -TvHost 192.168.1.50 4062 4065 4099 83
#        tools\tcl-keys.ps1 -TvHost 192.168.1.50 -Off
param(
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)][string[]]$Keycodes,
    [switch]$Off,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$arguments = if ($Off) { @('--off') } else { @($Keycodes) }
& $py -I tcl_keys.py @arguments
exit $LASTEXITCODE
