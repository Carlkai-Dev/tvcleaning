# Build Stream TV browser APK and install on the Android TV.
# Usage: streambrowser\build-and-install.ps1 -TvHost 192.168.1.50
param(
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
Set-Location $PSScriptRoot

if (-not $env:JAVA_HOME -or -not (Test-Path "$env:JAVA_HOME\bin\java.exe")) {
    $jbr = Get-ChildItem 'C:\Program Files\Android' -Directory -ErrorAction SilentlyContinue |
        ForEach-Object { Join-Path $_.FullName 'jbr' } | Where-Object { Test-Path "$_\bin\java.exe" } | Select-Object -First 1
    if (-not $jbr) {
        $jbr = Get-ChildItem 'C:\Program Files\Microsoft' -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like 'jdk-17*' -and (Test-Path (Join-Path $_.FullName 'bin\java.exe')) } |
            Select-Object -First 1 -ExpandProperty FullName
    }
    if (-not $jbr) { throw 'Set JAVA_HOME to a JDK 17+ (Android Studio jbr or Microsoft OpenJDK 17)' }
    $env:JAVA_HOME = $jbr
}
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (-not (Test-Path local.properties)) {
    "sdk.dir=$($sdk -replace '\\', '\\')" | Out-File -Encoding ascii local.properties
}

.\gradlew.bat assembleRelease -q --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed' }

$py = '..\tools\venv\Scripts\python.exe'
$apk = 'app\build\outputs\apk\release\app-release.apk'
& $py -I ..\tools\b64push.py $apk /data/local/tmp/streambrowser.apk
if ($LASTEXITCODE -ne 0) { throw 'APK upload failed' }
$out = & $py -I ..\tools\tv.py 'pm uninstall com.gathin.streambrowser >/dev/null 2>&1; pm install -r /data/local/tmp/streambrowser.apk; rm -f /data/local/tmp/streambrowser.apk'
Write-Host $out
if ("$out" -notmatch 'Success') { throw 'pm install failed' }
& $py -I ..\tools\tv.py 'am start -n com.patilweb.streambrowser/.HomeActivity'
Write-Host 'Installed and launched Stream TV.'
