# Philco Android TV / CleanYourTV — Package Classification

## Device Information

- **Brand:** Philco
- **OEM / Platform:** Skyworth
- **Device codename:** `sunnyvale`
- **Android version:** 14
- **SDK version:** 34
- **Build ID:** `UKNV.260821.001`
- **Build fingerprint:** `Philco/SW5_Philco/sunnyvale:14/UKNV.260821.001/16143353:user/release-keys`
- **SoC:** MediaTek T56

## 1. Optional Packages Disabled

The following packages were disabled for Android user 0 and are part of the tested configuration.

### A. Original launcher and manufacturer features

- `com.google.android.tvlauncher` — Original launcher, replaced by CleanYourTV.
- `com.smartdevice.recommendation` — Manufacturer recommendations.
- `fusion.android.tv.demo` — Factory demonstration app.
- `com.Smartdevice.emanual` — Electronic user manual.

### B. Apps and features not used

- `com.google.android.play.games` — Google Play Games.
- `com.google.android.backdrop` — Background images.
- `com.android.dreams.basic` — Basic screensaver.
- `com.netflix.ninja` — Netflix app; disabled by user choice.
- `com.google.android.marvin.talkback` — TalkBack screen reader.
- `com.google.android.tts` — Text-to-speech.
- `com.google.android.syncadapters.calendar` — Calendar synchronization.
- `com.android.health.connect.backuprestore` — Health Connect backup and restore.
- `com.android.healthconnect.controller` — Health Connect interface.
- `com.android.nearby.halfsheet` — Nearby-device discovery interface.
- `com.android.htmlviewer` — Basic HTML viewer.

**Note:** `com.google.android.youtube.tvmusic` was already disabled before this work and is not included in [`debloat.txt`](./debloat.txt).

TalkBack and text-to-speech should be re-enabled if accessibility features are needed.

### C. Setup, feedback, and services not needed for the intended use

- `com.google.android.feedback` — Google feedback.
- `com.google.android.partnersetup` — Partner setup.
- `com.google.android.katniss` — Google TV search/assistant component.
- `com.android.ondevicepersonalization.services` — On-device personalization.
- `com.android.federatedcompute.services` — Federated computing.
- `com.android.adservices.api` — Android advertising services.

These components were disabled in the tested configuration. Compatibility with secondary features related to search, recommendations, advertising, or Google services may vary.

The complete list of packages disabled during this work is provided in [`debloat.txt`](./debloat.txt).

## 2. Critical Components That Should Remain Enabled

### A. TV interface and operation

- `com.gathin.tvhome` — CleanYourTV, the primary launcher.
- `com.cltv.fast` — Live TV and channel integration.
- `com.mediatek.wwtv.tvcenter` — Manufacturer's TV center.
- `com.mediatek.tvinput` — TV input and signal integration.
- `com.android.providers.tv` — Channel and EPG data.
- `com.android.tv.settings` — Android TV settings.
- `com.smartdevice.global.service` — Manufacturer's global service with broad, privileged functionality.
- `com.smartdevice.leanbacklauncher.customizer` — Manufacturer interface configuration and integration.

### B. Casting, networking, and connectivity

- `com.google.android.apps.mediashell` — Google Cast and phone screen mirroring.
- `com.android.tv.mdnsoffloadmanager` — Network service and device discovery.
- `com.android.bluetooth` — Bluetooth and peripherals.
- `com.android.networkstack.process` — Network infrastructure.
- `com.android.inputdevices` — Remote control, keyboard, and input devices.
- `com.mediatek.autopair` — Bluetooth pairing and reconnection.

### C. Android system, security, and data

Keep core Android providers and services enabled, including:

- `com.android.providers.settings`
- `com.android.providers.media`
- `com.android.providers.media.module`
- `com.android.providers.downloads`
- `com.android.providers.contacts`
- `com.android.providers.calendar`
- `com.android.location.fused`
- `com.android.keychain`
- `com.android.proxyhandler`
- `com.android.pacprocessor`
- `com.android.captiveportallogin`
- `com.android.wifi.dialog`
- `com.android.permissioncontroller` / `com.google.android.permissioncontroller`
- `com.google.android.gms` and Google services required by apps
- `com.android.systemui`
- `com.android.se`

MediaTek and Skyworth services for audio, video, image scaling, HDMI, picture processing, and the TV tuner should also remain enabled, as should privileged components whose roles have not been confirmed.

The package `com.tianci.localmedia` must remain enabled at the user's explicit request.

## 3. Components to Treat as “Do Not Disable Without Investigation”

These packages are not necessarily required in every configuration, but should be treated as critical until their roles are clarified:

- `com.android.devicelockcontroller`
- `com.google.android.tungsten.setupwraith`
- `com.android.managedprovisioning`
- `com.smartdevice.factory`
- `com.smartdevice.firewall`
- `com.coocaa.demonow.lightining`
- `mediatek.factorymenu.ui`
- `com.mediatek.wwtv.setupwizard`
- `com.android.rkpdapp`
- `com.android.dynsystem`
- `com.android.sdksandbox`
- `com.android.statementservice`

Leave these enabled because they may provide setup, security, provisioning, diagnostics, or manufacturer integration functions, or because there is insufficient evidence that disabling them is safe.

## 4. Confirmed Functional Results

After disabling the listed packages, the following functions were tested:

- Booting into CleanYourTV.
- YouTube playback.
- Prime Video playback.
- Live TV and electronic program guide (EPG).
- HDMI inputs.
- Rebooting the TV.
- Google Cast and phone screen mirroring.

### Post-change resource observations

- **CPU:** 5.3% total, comprising 1.2% user time, 4.0% kernel time, and 0% I/O wait during the reported interval.
- **Total RAM:** 935,348 kB, approximately 913 MiB.
- **Available RAM:** 359,188 kB, approximately 351 MiB.
- **Free RAM:** 29,400 kB, approximately 29 MiB.
- **Total swap:** 716,796 kB, approximately 700 MiB.
- **Free swap:** 487,932 kB, approximately 477 MiB.

These measurements describe the state after the changes. No equivalent pre-change benchmark was recorded, so they should not be used to claim a specific percentage performance improvement.

## 5. Scope and Compatibility

This classification reflects the configuration and tests performed on this Philco Android TV running Android 14, build `UKNV.260821.001`.

Package availability and behavior may vary across models, regional variants, and firmware versions. Before applying this list to another device, validate each package and confirm that the functions you need continue to work.

Keep a record of disabled packages so they can be restored if necessary.
