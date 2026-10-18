# Hisense launcher customizations (2.2)

This branch adds an app-management menu and persistent Home ordering to the lightweight launcher.
It was built and tested on a Hisense SmartTV 4K FFM (`lushan`) running Android 12.

## Remote control

- Hold **OK** on an app: the tile zooms and a contextual menu opens.
- **Move**: use the arrows to swap positions; press OK to save or Back to cancel.
- The first four visible apps make up the dock. Apps can move between the dock and the grid.
- **Uninstall**: opens Android's uninstall confirmation. System apps are kept intact and show an explanation instead.
- **Hide**: removes an app from Home without disabling or uninstalling it.
- Hold OK on **Settings** to list hidden apps and show them again.

The order and hidden-app list are saved locally. Newly installed apps appear after the saved order.
Leaving Home cancels a pending move. English and Spanish resources are included; the system language selects them.
The contextual menu uses dimming, rounded panels, vector icons and scale animation without real-time blur or new dependencies.

## Hisense configuration

`launcher/app/src/main/res/values/config.xml` sets the initial dock to YouTube TV, Apple TV, Prime Video and Netflix.
The Live TV package is `com.vt.tv.tvcenter` for the tested device. Change it for other TV models.

The tested TV also exposed a higher-priority Home recovery activity from `com.google.android.tungsten.setupwraith`.
Selecting the custom Home alone was insufficient on this device. After installing and opening the new launcher,
both `com.google.android.apps.tv.launcherx` and `com.google.android.tungsten.setupwraith` were disabled for user 0.
The latter contains Google TV setup features: restore it before using those features. This is a device-specific observation,
not an instruction to disable the package on every TV.

To restore Google TV on the tested model, with an authorized ADB connection:

```sh
adb shell pm enable --user 0 com.google.android.apps.tv.launcherx
adb shell pm default-state --user 0 com.google.android.tungsten.setupwraith
adb shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
adb shell input keyevent KEYCODE_HOME
```

## Validation

- `assembleDebug` succeeded using JDK 17, Android SDK 34 and build-tools 34.0.0.
- Installed on the real TV; verified Home, the four dock apps, Settings and the HDMI/AV/Live TV menu.
- Verified move, cancel, save and order persistence after stopping and reopening the launcher.
- Verified hide persistence after process restart and recovery through Settings.
- Verified Android's uninstall confirmation and cancelled it; no apps were removed during testing.
- Confirmed the saved order matched the pre-update order after the tests.

Network addresses, ADB keys, device dumps, accounts and local build paths are not part of this branch.
