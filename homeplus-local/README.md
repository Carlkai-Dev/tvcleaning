# homeplus-local（只放在這個 fork，不送回原專案）

這個資料夾與同一分支上的其他客製，都是針對 Home+ STB-6252C 這一台的個人設定。它們改變了所有電視的預設值，
違反原專案「不為每台電視改預設值」與「清單資料夾不放安裝或還原腳本」的規則，所以只留在 fork 的
`homeplus-personal` 分支。可以送回原專案的部分是 `examples/homeplus-stb-6252c/`，在 `homeplus-stb-6252c` 分支。

## 這個分支多了什麼

| 檔案 | 內容 |
|---|---|
| `launcher/app/src/main/res/values/config.xml` | dock：Live TV、YouTube、Disney+、Play 商店；隱藏中嘉 launcher、兩個 ODM 工具與 DVB 說明頁；`live_tv_package` = `com.android.tv` |
| `launcher/app/src/main/res/values-zh-rTW/strings.xml` | 頂欄膠囊 `inputs` 從「輸入來源」改成「有線電視」 |
| `themes/homeplus/` | glass 外觀 + App library 抽屜、長按選單、抽屜內 OK 直接開啟；Live TV 列的修正（`live_tv_input_id: none`、`input_sources: HW28=-`） |
| `homeplus-local/` | 一鍵切換桌面的兩支腳本 |

主題沒有 `screenshot.jpg`（這台的 `screencap` 抓不到畫面），所以原專案的主題測試在這個分支上會失敗，這是預期的。

## 一鍵切換桌面

```powershell
$env:TV_HOST = '<box ip>'
$env:ADB = 'C:\path\to\platform-tools\adb.exe'   # adb 已在 PATH 就不用設
homeplus-local\switch-to-cns.ps1       # 切回中嘉預設桌面，停用的 App 不動
homeplus-local\switch-to-tvhome.ps1    # 切回 cleanyourtv launcher，並停用中嘉 launcher
```

或設好 `TV_HOST` 後雙擊 `切回中嘉桌面.bat`、`切回新launcher.bat`。

- 切回新 launcher 一定要先停用中嘉 launcher：它的 HOME priority 是 2，新 launcher 是 0，
  中嘉 launcher 啟用時 `set-home-activity` 會回報 Success 但不生效。
- 兩支 `.ps1` 含中文，必須存成 UTF-8 with BOM，Windows PowerShell 5.1 才不會把中文讀成 Big5 而語法錯誤。
- 切到中嘉桌面期間，中嘉 launcher 常駐約 200 MB（在 daemon 白名單內，不會被清掉）。
