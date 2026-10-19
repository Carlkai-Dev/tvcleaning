# Fire TV

Tested on a Fire TV Stick 4K (AFTKMST12), Fire OS 6.7.1.1 (Android 7.1, API 25).

Fire OS will not let anything replace Amazon's home screen. Each of these was tried on the device and refused or ignored:

| Attempt | Result |
|---|---|
| `pm disable-user com.amazon.tv.launcher` | `Cannot disable a protected package` |
| `pm disable com.amazon.tv.launcher/.ui.HomeActivity_vNext` | same error |
| `cmd package set-home-activity …` | "Success", but Home still resolves to Amazon (priority 950 vs 0) |
| Accessibility service that takes the Home key | Fire OS never binds a third-party accessibility service |
| `am kill com.amazon.tv.launcher` | `Cannot kill protected package` |

So the launcher works around it instead of replacing it:

- `HomeWatchService` follows ActivityManager's log. When the system starts Amazon's home activity, it opens this
  launcher on top. Android holds back that start for five seconds after a Home press, so the service also covers the
  screen with a snapshot of the launcher straight away (an overlay window is not held back). Remote presses are
  swallowed until the real launcher appears, so nothing happens on Amazon's home underneath.
- Apps opened from this launcher return to it with Back; Amazon's home is not involved.
- `BootReceiver` brings the launcher up after a reboot. It listens for `LOCKED_BOOT_COMPLETED` as well:
  `BOOT_COMPLETED` is handed to about 100 apps one at a time and reached this one about 65 s after Amazon's home
  appeared, while the locked-boot broadcast reached it in about 7 s.

Measured on the stick: the watcher sees Home in 28–70 ms (674 ms once while Netflix was closing). The cover is up before
Amazon's home finishes drawing, and the real launcher takes over 5–6 s after the press. After a reboot, Amazon's home
shows for about 10 s before the launcher replaces it.

What is left: after Home, the launcher snapshot is not interactive for about five seconds. Amazon's home shows for about
10 s after a reboot. Amazon's launcher stays in RAM (77–130 MB PSS measured), because Fire OS will not let it be killed.

## Install

```bash
cd launcher
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew assembleDebug
adb connect <tv-ip>:5555
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant com.gathin.tvhome android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.gathin.tvhome android.permission.READ_LOGS
adb shell appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow
adb shell am start -n com.gathin.tvhome/.MainActivity
```

Edit the dock in `launcher/app/src/main/res/values/config.xml` first. The defaults are for one particular stick.

## Remove

```bash
adb uninstall com.gathin.tvhome
```

Nothing of Amazon's is disabled or changed, so this puts the stick back exactly as it was.
