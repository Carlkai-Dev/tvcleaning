# Letv X3-65 (retail label X65-168CQ, "guava" board)

Report for the mainland-China LeTV/EUI firmware (`com.stv.*` / `com.letv.*`) on an MStar set.
Tested on the owner's TV over network ADB, 2026-10-24. Second unit of the same model pending —
this file doubles as the runbook for it.

> **Warning:** `pm disable-user` is REFUSED on this firmware (SecurityException: Permission Denial —
> the vendor removed `CHANGE_COMPONENT_ENABLED_STATE` from the shell). The debloat mechanism here is
> **`pm hide` / `pm unhide`**, verified working and reboot-persistent. Do not use tools/disable.sh
> (it runs disable-user); apply `hide-all.txt` with `tools/tv.py --batch` instead.

| File | What it is |
|---|---|
| `debloat.txt` | The 25 packages that were hidden with pm hide, grouped and commented |
| `hide-all.txt` | Ready-to-run batch: applies the list with `tools/tv.py --batch` |
| `unhide-all.txt` | Ready-to-run batch: undoes every hide |

## Device

| Field | Value |
| :--- | :--- |
| Retail model | 乐视 X65-168CQ (back label) / 超3 X65 generation |
| `ro.product.model` | Letv X3-65 (`net.hostname` = X65-168c) |
| SoC / board | MStar 6A928, board `guava` |
| Android / SDK | 5.0.1 / 21, EUI, build V2301RCN02C080080B06141S, user build, `ro.debuggable=0` |
| RAM | 3 GB |
| Packages | 92 visible at stock; 67 after the full hide list |

## ADB

- Enable: stay on the 信号源 (input source) screen, press **2580** on the remote → factory menu →
  **Other Options → ADB Debug** → on. The toggle survives reboots on this unit.
- `ro.adb.secure=1`: the first connection shows the on-screen RSA prompt — accept it.
- `adb install`/`pm install` pops a screen confirm ("主屏幕安装提示", 8 s countdown) — press
  继续安装 on the remote or the install reports `INSTALL_FAILED_USER_RESTRICTED`.
- **Boots are slow: ADB comes back 2.5–8 min after a reboot.** Poll before assuming a hang.
- Shell has no `head`; toolbox-era coreutils. Either use `grep -m` on the TV or pull output and
  filter locally. `/data/local` is not listable by shell; `/data/local/tmp` works (b64push OK).

## Boot chain — read before hiding anything

Power-on sequence on this set: `/tvconfig/video.ts` (15 s MStar-level boot-video ad, played before
Android) → vendor logo → Android framework → `com.stv.appdaemon/.ui.DaemonActivity` →
`com.stv.deskplatform` (HOME) renders.

- **`com.stv.appdaemon` is in the critical boot path. Hiding it makes boots take 2–3x longer and
  can break the launch chain (stuck at logo). Never hide it.** It also hosts
  `InstallBroadcastReceiver` (silent-install path) — leave it, the hide of `stvpush`/`cdeapk`
  already cut the install channel.
- Only change package state while the system is fully up (`getprop sys.boot_completed` = 1).
  An `pm unhide` issued during boot puts the package into Android's **stopped state**, and a
  stopped app misses the boot trigger: the chain never fires and the TV sits at the logo with
  ADB answering and *no activity ever created* (`logcat -b events` shows zero
  `am_create_activity`). Recovery that worked:
  `am start -n com.stv.appdaemon/.ui.DaemonActivity` (explicit start clears the stopped state and
  jumps to the desktop). Then reboot to confirm auto-start again.
- The CIBN 播控认证 splash ("正在进行播控认证...") is firmware/desktop-level. Hiding
  `com.kuyun.common.androidtv` does not remove it. Not removable without root.
- The 15 s boot video is `/tvconfig/video.ts` on a **read-only** ext4 partition — not removable
  without root.
- Desktop tabs are part of `com.stv.deskplatform` (HOME, cannot hide). Hiding `letvlive` removed
  the 首页 tab outright; 精选 stays as an empty-ish shell.

## App identity (label verified by pulling the APK and grepping UTF-8/UTF-16 for the name)

| Package | Label | Note |
| :--- | :--- | :--- |
| `o.k` | FongMi TV (蜂蜜影视/OK影视) | obfuscated package name; `com.fongini.android.tv` classes inside; owner's series app — keep |
| `com.letv.leso` | 万象搜索 | LeTV platform universal search — hidden |
| `com.stv.ucenter` | 乐范 | user-center/membership; location + boot + overlay — hidden |
| `com.cibn.tv` | 优酷TV (CIBN 酷喵) | boot receiver — hidden on this owner's set |
| `com.gitvvideo.letv` | 银河爱奇艺 (GITV) | hidden |
| `com.stv.screenflyter` | 飞屏 | NOT casting — it draws the full-screen movie promos; hidden. Real casting is `com.stv.miracast` |
| `com.stv.bootadmanager` | AdManager | boot/exit/JD+Ali shopping/screensaver ads in one package — hidden |

## What was hidden (25) and the result

See `debloat.txt` for the annotated list, `hide-all.txt` to apply, `unhide-all.txt` to undo:

```
TV_HOST=<tv-ip> tools/venv/Scripts/python.exe tools/tv.py --batch < examples/letv-x3-65/hide-all.txt    # apply
TV_HOST=<tv-ip> tools/venv/Scripts/python.exe tools/tv.py --batch < examples/letv-x3-65/unhide-all.txt  # undo
```

Verified: two reboots with all 25 hidden; desktop up; TVBox (kept) working. Full-screen promo
overlays gone, 首页 tab gone, silent-install path cut, telemetry SDKs (kuyun ×2, macscanner,
reportService/reportlog) gone.

## Runbook for the second unit

1. Enable ADB (2580 method above), accept the RSA prompt, note the IP.
2. `TV_HOST=<ip> tools/tv.py --probe` — confirm it is the same platform (Letv X3-65, Android
   5.0.1). If it is a different generation (超4 = Android 6, or newer), the package list will
   differ: re-run discovery, do not reuse the list blindly.
3. Read-only backup first: `pm list packages`, `-s`, `-3`, `-d`, `getprop`, `dumpsys package`.
4. Check every line of `hide-all.txt` against the live `pm list packages` output; comment out
   absent ones.
5. Apply in two halves (system packages, then apps), checking `pm list packages` count and a
   screenshot between.
6. Reboot once, wait for ADB (up to 8 min), confirm `sys.boot_completed` = 1 and that the
   desktop auto-starts (`dumpsys activity activities | grep mFocusedActivity`) before calling it
   done.
7. When finished, close ADB from the factory menu and verify the port refuses connections
   (`tools/adb-closed.py <ip>`).
