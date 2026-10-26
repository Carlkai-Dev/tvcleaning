# Epson EH-TW6250: Android TV module and first-session checks

Observed on one owner-identified Epson EH-TW6250 projector on 26 October 2026.
Its Askey Android module reports model `STI6110`, Android 12 / SDK 31, build
`SC.20241210 R-4.06S-0004-UN`. These are observations from one setup session,
not a several-day tested debloat list. The [Epson LS800 profile](../../examples/epson-ls800/README.md)
uses a different module and Android version; do not apply its package list blindly.

## Connection and installation

Developer options offered **USB debugging**; no separate Wireless debugging option
was visible. Port 5555 refused connections before USB debugging was enabled and
accepted the repository's Python ADB client afterwards. `ro.adb.secure=1` was
reported. The session used Wi-Fi and no USB Ethernet adapter.

A preflight reinstall of a Play-installed app initially returned
`INSTALL_FAILED_VERIFICATION_FAILURE: Package Verification Result`. This was not
a firmware sideload block: the app had a base APK plus three configuration splits,
but the first attempt submitted only the base APK. A complete install session
including all four parts succeeded. No package-verifier or Play Protect setting
was disabled. A locally built single-APK CleanYourTV launcher also installed.

## Home handover

Stock Home was `com.google.android.tvlauncher/.MainActivity`, with priority 2.
Before installation, the only other HOME claimant was Android Settings'
`FallbackHome`; `com.google.android.tungsten.setupwraith` did not claim HOME and
was left enabled.

The new launcher started successfully, and both `mFocusedApp` and
`mResumedActivity` identified `com.gathin.tvhome/.MainActivity`. With stock Home
still enabled, `cmd package set-home-activity` printed Success but HOME resolution
continued to name the stock launcher. After the guarded stock-launcher disable,
the HOME key opened CleanYourTV. Following an Android-module reboot, CleanYourTV
was again the focused/resumed activity and the stock launcher stayed disabled.
Use the repository's installer and Home guards, and verify the actual foreground
activity rather than trusting the setter's Success message.

The four-column dock, greeting, app grid, top-bar navigation and returning Home
were checked with screenshots. The owner confirmed streaming picture/sound and
physical Home, Settings, app-button, input and held-volume checks after the
handover. No accessibility key helper was enabled. This is not a keycode mapping
or evidence that every button on every remote revision works.

The display reported 1920x1080 and density 320, with no overrides; those settings
were left alone.

## Inputs and tiles

Android's TV-input framework exposed app preview inputs, but no physical HDMI
input. The launcher's Inputs pill therefore hid itself. Use the projector's native
source control; the session did not establish an Android-framework route for
switching its HDMI ports.

`com.droidlogic.otaupgrade` appeared as **Update**, and
`com.droidlogic.tv.settings` as **Droid Settings**. Both packages stayed enabled;
their large grid tiles can be hidden using the theme's `hidden_apps` array.
Firmware updates were retained.

## Package observations and limits

Original packages, disabled packages, properties and settings were backed up
before changes. The existing safety tools were used in small batches, with
screenshots and disabled-package checks between them. The following packages
were disabled during the session, but have not been lived with for several days:

- `com.google.android.feedback`
- `com.android.printspooler`
- `com.amazon.amazonvideo.livingroom`
- `com.disney.disneyplus`
- `com.apple.atve.androidtv.appletv`
- `com.spotify.tv.android`
- `com.zumobi.msnbc`
- `com.google.android.play.games`
- `com.google.android.katniss`
- `com.esaba.downloader`
- `com.google.android.tvlauncher`, only after the replacement was running

The changed package states survived the reboot checks. These are owner-selected
observations, not an apply-ready list or a recommendation to disable streaming
apps other people use.

`com.google.android.videos` was initially disabled, then re-enabled at the owner's
request to retain Google movie access. Its TV activity displayed the Google Play
Movies retirement notice and directed users to Android TV's **Shop** tab; restoring
this package alone does not restore the Shop tab of a disabled stock launcher.
Google also supports purchases/rentals in the YouTube app on supported devices;
actual purchase checkout was not tested in this session.

Keep `com.epson.visualization`, `com.epson.setupcustomizer`,
`com.epson.logcollection`, the Askey setup customizer and the Amlogic settings/update
stack. `dumpsys package com.epson.logcollection` showed that its
`OperationLogSettingsActivity` handles `com.epson.intent.action.INFORMATION`, so it
is not merely a telemetry package. No logging-consent setting was changed.

The repository guard skipped `android.autoinstalls.config.askey.tvbox` because its
permission blocks could not be verified; no override was used.
`com.google.android.tvrecommendations` remained enabled, as did Chromecast,
Play Store, Play Services, the keyboard and Bluetooth/remote services. YouTube Music
was retained because of the dependency reported on other Epson firmware; that
dependency was not independently established on this module. Camera was already
disabled before the session and was not part of the cleanup.

No factory/test activity was launched, no system package was uninstalled, and the
restore plan uses the original recovery records. A full live restoration was not
exercised. The local changes need several days of normal use before promotion to
an `examples/` debloat list under the [TV-list workflow](../workflows/tv-list.md).
