# MXQ Pro 4K, Amlogic S905X (Android 6.0.1)

Owner-tested on 2026-10-21, the first Android 6 set to run the launcher (minSdk 23).

- `ro.product.model` `MXQ PRO`, device and board `s905x011`, platform `gxl`, device tree `gxl_s905x011_1g`.
- Build `s905x011-userdebug 6.0.1 MHC19J 20170908 test-keys`; `ro.debuggable=1`, so `adb root` works.
- 1 GB RAM (839 MB usable), 8 GB eMMC, Realtek RTL8189ES Wi-Fi (SDIO `024c:8179`), 1280x720 at 160 dpi.
- `ro.adb.secure=1` (the box asks to allow each computer), storage unencrypted.
- No newer firmware for this board turned up: images sold as "MXQ Pro" are for 2 GB S905X boards or for
  S905W (`p281`) boards, so the box stayed on its own Android 6.

## Stuck on "Android is starting… Optimizing app 1 of 1"

Every boot took about 9 minutes. The app being optimised was a Google Play services update (26.26.x):
on this CPU its compile never finishes, and logcat shows `dex2oat did not finish after 570 seconds`
before the boot carries on, only to start again on the next one. Disabling the package does not help,
Android 6 compiles disabled packages too. Without a factory reset:

```
adb shell pm uninstall com.google.android.gms     # drops the update, back to the system 10.0.84
adb shell pm uninstall com.android.vending        # same for the Play Store update
```

Boot went from about 9 minutes to about 40 seconds. Nothing on this box needed Play services, so they
were then switched off. Uninstalling the updates had reset them to enabled, and Play services
crash-looped ("Unknown URI content://com.google.android.gsf.gservices/main") against a disabled
`com.google.android.gsf`. `pm hide`, as root, kept all four off across reboots:

```
adb root
adb shell pm hide com.google.android.gms
adb shell pm hide com.android.vending
adb shell pm hide com.google.android.gsf
adb shell pm hide com.google.android.gsf.login
```

`pm unhide` brings them back. With Google off, available memory at Home went from 134 MB to about
530 MB, and the box no longer swaps while an app plays.

## Launcher

- `pm install` from `/data/local/tmp` worked; check the md5 of the pushed APK first, the Wi-Fi on this
  box drops long transfers.
- There is no `cmd package` on Android 6, so `build-and-install.sh` cannot hand Home over. As root,
  `pm disable-user --user 0 com.droidlogic.mboxlauncher` (the shell user is refused for system packages)
  left the launcher as the only Home: no chooser, and the box boots straight into it. Undo with
  `pm enable com.droidlogic.mboxlauncher`.
- The box has no TV input service: Android 6 still hands out a `TvInputManager`, whose
  `getTvInputList()` threw `NullPointerException` and crashed Home. The launcher now treats that as no
  inputs.
- Home starts in touch mode on Android 6, where a tile cannot take focus, so none showed as focused until
  a key was pressed. The launcher now leaves touch mode when it focuses the first tile.
- `tools/tv.py` (adb-shell) timed out connecting to this box while the stock `adb` worked, and
  `tools/theme.ps1` still printed "is on the TV". Themes went over with the stock `adb` instead: push
  `config.json` (and `wallpaper.jpg`) to `/sdcard/Android/data/com.gathin.tvhome/files/`, write a new
  `theme_revision` there, and restart `com.gathin.tvhome`.
- Speed against the stock `mboxlauncher`: Home cold start 0.6-1.6 s against 0.7-1.1 s, 58 MB against
  56 MB. No gain on its own; the gain on this box came from Play services.

## Packages disabled

With `pm disable-user --user 0` (as root), not uninstalled: `com.google.android.partnersetup`,
`com.google.android.feedback`, `com.google.android.backuptransport`, `com.google.android.configupdater`,
`com.google.android.syncadapters.calendar`, `com.google.android.syncadapters.contacts`,
`com.google.android.tts`, `com.android.galaxy4`, `com.android.noisefield`, `com.android.phasebeam`,
`com.android.wallpaper.holospiral`, `eu.chainfire.supersu`, `com.droidlogic.mboxlauncher`, and the apps
the owner did not keep on Home: `com.android.deskclock`, `com.android.gallery3d`,
`com.droidlogic.mediacenter`, `com.droidlogic.miracast`, `com.droidlogic.videoplayer`,
`com.android.providers.downloads.ui` and a preinstalled file manager. `com.android.camera2`,
`com.android.music` and `com.android.webview` came disabled. Kept: the setup apps (`com.boot.setup`,
`com.cyx.setupwizard`), `com.droidlogic.dig` (system restore), `com.example.shutdownfix`,
`com.droidlogic.PPPoE`, the keyboards and the browser.
