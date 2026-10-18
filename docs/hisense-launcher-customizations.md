# Hisense launcher customizations (2.4)

This branch adds an app-management menu and persistent Home ordering to the lightweight launcher.
It was built and tested on a Hisense SmartTV 4K FFM (`lushan`) running Android 12.

## Remote control

- Hold **OK** on an app: the tile zooms and a contextual menu opens.
- **Move**: use the arrows to swap positions; press OK to save or Back to cancel.
- The first four visible apps make up the dock. Apps can move between the dock and the grid.
- **Uninstall**: opens Android's uninstall confirmation. System apps are kept intact and show an explanation instead.
- **Hide**: removes an app from Home without disabling or uninstalling it.
- Focus **Settings** and press **Right five separate times** to open the hidden-app dropdown.
- Press OK on a hidden app to open it while keeping it hidden from Home.
- Hold OK on a dropdown item to show it on Home again. Back closes the list.

The order and hidden-app list are saved locally. Newly installed apps appear after the saved order.
Leaving Home cancels a pending move. English and Spanish resources are included; the system language selects them.
The contextual menu uses dimming, rounded panels, vector icons and scale animation without real-time blur or new dependencies.

## Visual corrections in 2.3

Inputs, Wi-Fi and Settings use equally sized pills, equal gaps and centered icon/label groups.
The greeting and clock share their starting edge. Each app card now owns its inset glass highlight,
so the artwork and rim share clipping, rounded corners and scale animations. The contextual preview
uses the same highlight and continues from the scale reached while holding OK.

![Aligned header and fitted focus highlight](launcher-home-2.3.png)
![Contextual menu with the highlight retained](launcher-menu-2.3.png)

Verified on the TV: the three header pills each measured 224 by 84 screen pixels,
with 20-pixel gaps and the same vertical position. Checked the input menu, Wi-Fi,
Settings, focused cards and contextual previews. The app order was unchanged.

### Edge clipping fix in 2.3.1

The grid viewport extends 24 dp farther on each horizontal side, with matching internal padding.
This preserves card positions, column spacing and dock geometry while leaving room for the 1.2x long-press scale.
Vertical clipping remains enabled to keep scrolling apps out of the header.
Verified first and last columns in the dock and app grid, including the long-press menus;
the saved app order was unchanged.

### Menu opening fix in 2.3.2

The contextual panel starts transparent and receives its final bounds synchronously in `onLayout`,
before its opening animation starts. Its position no longer depends on a posted callback and a second
layout pass, preventing an initial flash at the screen origin. The preview also starts at the held tile's scale.
Recorded long-press openings at both dock edges on the TV and checked all 238 recorded frames;
the menu did not appear in the empty top-left region beside the clock.

## Hidden-app dropdown in 2.4

The minimal, scrollable list opens below Settings and is aligned with its right edge.
Five consecutive Right key presses while Settings is focused replace the previous Settings long-press shortcut.
Key-repeat events from holding Right do not advance the counter. Other keys, focus changes,
leaving Home and closing the dropdown reset the sequence.
Opening an app from the list does not change its hidden status. Holding OK on a list item restores it to Home.

![Hidden-app dropdown anchored to Settings](hidden-apps-2.4.png)

Verified on the TV: four presses keep the list closed, the fifth opens it, key repeats do not count as
additional presses, and changing buttons resets the count. Confirmed a hidden app opens while remaining hidden
and can be restored using the dropdown. After testing, the app order and hidden-app list matched their prior state.

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
