# Contract: Launcher Configuration Schema

**Feature Branch**: `001-debloat-apple-tv-launcher`  
**Date**: 2026-10-18  
**Target File**: `launcher/app/src/main/res/values/config.xml`

---

## 1. XML Schema Specification

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!--
      dock_apps: Ordered list of exactly 4 application package names.
      These appear in the persistent bottom dock band from left to right.
      If an app is not installed, it is omitted and remaining apps shift left.
    -->
    <string-array name="dock_apps">
        <item>[PACKAGE_NAME_1]</item>
        <item>[PACKAGE_NAME_2]</item>
        <item>[PACKAGE_NAME_3]</item>
        <item>[PACKAGE_NAME_4]</item>
    </string-array>

    <!--
      hidden_apps: List of packages that must NOT appear in the scrollable grid.
      Used for system apps, background utilities, or apps handled in top-bar menus.
    -->
    <string-array name="hidden_apps">
        <item>[PACKAGE_NAME_TO_HIDE]</item>
    </string-array>

    <!--
      live_tv_package: Component package for the device's native TV tuner.
      Shown as 'Live TV' entry at the bottom of the Inputs dropdown.
      Leave empty string to omit from menu.
    -->
    <string name="live_tv_package" translatable="false">[TUNER_PACKAGE_OR_EMPTY]</string>

    <!--
      columns: Number of grid columns for the scrollable application section.
      Must be an integer between 3 and 6 (standard: 4).
    -->
    <integer name="columns">4</integer>
</resources>
```

---

## 2. Configuration Validation Rules

1. **`dock_apps` Array**:
   - Must contain between 1 and 4 valid package ID strings.
   - Defaults to 4 apps.
   - Ordering in array matches visual left-to-right order on screen.
2. **`hidden_apps` Array**:
   - Accepts 0 or more valid package ID strings.
3. **`live_tv_package`**:
   - Must match the OnePlus/MediaTek TV tuner component (`com.mediatek.wwtv.tvcenter` or `com.heytap.tv.livetv`).
4. **`columns`**:
   - Must be `4` to conform to Apple TV 4K grid layout.
