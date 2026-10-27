# 切回新 launcher（cleanyourtv，com.gathin.tvhome）
# 中嘉 launcher 的 HOME priority 是 2，新 launcher 是 0；只要中嘉 launcher 還啟用著，Android 就會忽略指向新 launcher 的
# Home 偏好。所以順序是：確認新 launcher 可啟動 → 停用中嘉 launcher → 設 Home 偏好 → 驗證；驗證失敗就把中嘉 launcher 啟用回來。
# 反向：switch-to-cns.ps1
# 用法：switch-to-tvhome.ps1 -TvHost <box ip> [-Adb <adb.exe 路徑>]，或先設定 $env:TV_HOST / $env:ADB
# 本檔含中文，必須存成 UTF-8 with BOM，Windows PowerShell 5.1 才不會讀錯。
param(
    [string]$TvHost = $env:TV_HOST,
    [string]$Adb = $(if ($env:ADB) { $env:ADB } else { 'adb' })
)
$ErrorActionPreference = 'Continue'
$stock = 'com.inspur.isoc.launcher'
$stockHome = "$stock/com.inspur.atvlauncher.mvp.ui.activity.HomeActivity"
$new = 'com.gathin.tvhome'
$newHome = "$new/.MainActivity"

function Step($msg) { Write-Host "`n== $msg" -ForegroundColor Cyan }
function Sh($cmd) { & $Adb -s $box shell $cmd 2>&1 | ForEach-Object { "$_" } }
function HomeNow { (Sh 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' | Select-Object -Last 1).Trim() }
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

Step "確認新 launcher 在機上盒上而且能啟動"
$installed = (Sh "pm list packages $new" | Out-String)
if ($installed -notmatch [regex]::Escape("package:$new")) {
    Write-Host "找不到 $new，不做任何變更。" -ForegroundColor Red
    Finish 1
}
Sh "pm enable --user 0 $new" | Out-Null
$start = (Sh "am start -W -n $newHome" | Out-String)
if ($start -notmatch 'Status: ok') {
    Write-Host "新 launcher 啟動失敗，不做任何變更：`n$start" -ForegroundColor Red
    Finish 1
}

Step "停用中嘉 launcher"
Sh "pm disable-user --user 0 $stock"

Step "把 Home 指向新 launcher"
Sh "cmd package set-home-activity --user 0 $newHome"
Sh "am start -a android.intent.action.MAIN -c android.intent.category.HOME -n $newHome >/dev/null 2>&1; echo ok"
Start-Sleep -Seconds 3

Step "確認"
$homeNow = HomeNow
Write-Host "Home 現在是：$homeNow"
if ($homeNow -like "$new/*") {
    Write-Host "完成：已切回新 launcher，中嘉 launcher 已停用。" -ForegroundColor Green
    Finish 0
}

Step "Home 沒有切成功，把中嘉 launcher 還回去"
Sh "pm enable --user 0 $stock"
Sh "cmd package set-home-activity --user 0 $stockHome"
Write-Host "已還回中嘉桌面。Home 現在是：$(HomeNow)" -ForegroundColor Yellow
Write-Host "請把上面的輸出貼出來。" -ForegroundColor Red
Finish 1
