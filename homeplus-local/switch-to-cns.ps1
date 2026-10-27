# 切回中嘉預設桌面（Home+ STB-6252C）
# 只做兩件事：啟用中嘉 launcher、把 Home 的預設指回它。停用中的其他 App 一律不動。
# 反向：switch-to-tvhome.ps1
# 用法：switch-to-cns.ps1 -TvHost <box ip> [-Adb <adb.exe 路徑>]，或先設定 $env:TV_HOST / $env:ADB
# 本檔含中文，必須存成 UTF-8 with BOM，Windows PowerShell 5.1 才不會讀錯。
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Adb = $(if ($env:ADB) { $env:ADB } else { 'adb' })
)
$ErrorActionPreference = 'Continue'
$stock = 'com.inspur.isoc.launcher'
$stockHome = "$stock/com.inspur.atvlauncher.mvp.ui.activity.HomeActivity"

function Step($msg) { Write-Host "`n== $msg" -ForegroundColor Cyan }
function Sh($cmd) { & $Adb -s $box shell $cmd 2>&1 | ForEach-Object { "$_" } }
function Finish($code) { try { Read-Host '按 Enter 關閉' } catch { }; exit $code }

if (-not $TvHost) { Write-Host '請用 -TvHost <box ip>，或先設定 $env:TV_HOST' -ForegroundColor Red; Finish 1 }
$box = if ($TvHost -match ':') { $TvHost } else { "${TvHost}:5555" }

Step "連線機上盒 $box"
& $Adb connect $box | Out-Null
$state = (& $Adb -s $box get-state 2>&1 | Out-String).Trim()
if ($state -ne 'device') {
    Write-Host "連不上機上盒（狀態：$state）。請確認機上盒開著、同一個網路、USB 偵錯仍開啟。" -ForegroundColor Red
    Finish 1
}

Step "啟用中嘉 launcher"
Sh "pm enable --user 0 $stock"

Step "把 Home 指回中嘉 launcher"
Sh "cmd package set-home-activity --user 0 $stockHome"

Step "回到主畫面"
Sh "am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; echo ok"
Start-Sleep -Seconds 3

Step "確認"
$homeNow = (Sh 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | Select-Object -Last 1).Trim()
Write-Host "Home 現在是：$homeNow"
if ($homeNow -like "$stock/*") {
    Write-Host "完成：已切回中嘉桌面。停用的 App 維持停用。" -ForegroundColor Green
    Finish 0
}
Write-Host "Home 沒有切成功，請把上面的輸出貼出來。" -ForegroundColor Red
Finish 1
